package com.aistra.hail.utils

import com.aistra.hail.HailApp.Companion.app
import com.aistra.hail.app.AppInfo
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Persistent metadata cache so cold starts can paint Home without waiting on PackageManager.
 * Stores label, install/system flags, and last known freeze [AppInfo.State].
 */
object AppMetaCache {
    private const val FILE = "app_meta_cache.json"
    private const val KEY_PKG = "p"
    private const val KEY_LABEL = "l"
    private const val KEY_STATE = "s"
    private const val KEY_SYSTEM = "sys"
    private const val KEY_EXISTS = "e"

    data class Entry(
        val label: String,
        val state: AppInfo.State,
        val isSystem: Boolean,
        val exists: Boolean
    )

    private val map = ConcurrentHashMap<String, Entry>()
    private val loaded = AtomicBoolean(false)
    private val dirty = AtomicBoolean(false)
    private val saveLock = Any()
    private val saveExecutor = Executors.newSingleThreadExecutor { r ->
        Thread(r, "hail-meta-save").apply {
            isDaemon = true
            priority = Thread.NORM_PRIORITY - 1
        }
    }

    private val path: String
        get() = "${app.filesDir.path}/v1/$FILE"

    fun ensureLoaded() {
        if (loaded.get()) return
        synchronized(this) {
            if (loaded.get()) return
            runCatching {
                val text = HFiles.read(path) ?: return@runCatching
                val arr = JSONArray(text)
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    val pkg = o.optString(KEY_PKG)
                    if (pkg.isEmpty()) continue
                    val stateOrd = o.optInt(KEY_STATE, AppInfo.State.UNFROZEN.ordinal)
                    val state = AppInfo.State.entries.getOrElse(stateOrd) { AppInfo.State.UNFROZEN }
                    map[pkg] = Entry(
                        label = o.optString(KEY_LABEL, pkg),
                        state = state,
                        isSystem = o.optBoolean(KEY_SYSTEM, false),
                        exists = o.optBoolean(KEY_EXISTS, true)
                    )
                }
            }
            loaded.set(true)
        }
    }

    fun get(packageName: String): Entry? {
        ensureLoaded()
        return map[packageName]
    }

    /** Seed in-memory AppInfo fields from disk (no PackageManager). */
    fun applyTo(info: AppInfo) {
        ensureLoaded()
        map[info.packageName]?.let { info.seedFromCache(it.label, it.state, it.isSystem, it.exists) }
    }

    fun applyToAll(apps: List<AppInfo>) {
        ensureLoaded()
        apps.forEach { applyTo(it) }
    }

    fun put(info: AppInfo) {
        ensureLoaded()
        val label = info.name.toString()
        val state = info.state
        val exists = info.isInstalledHint
        val system = info.isSystemHint
        map[info.packageName] = Entry(label, state, system, exists)
        dirty.set(true)
    }

    fun putAll(apps: Iterable<AppInfo>) {
        apps.forEach { put(it) }
        scheduleSave()
    }

    fun remove(packageName: String) {
        ensureLoaded()
        if (map.remove(packageName) != null) {
            dirty.set(true)
            scheduleSave()
        }
    }

    fun scheduleSave() {
        if (!dirty.get()) return
        saveExecutor.execute { flush() }
    }

    fun flush() {
        if (!dirty.getAndSet(false)) return
        synchronized(saveLock) {
            runCatching {
                val dir = "${app.filesDir.path}/v1"
                if (!HFiles.exists(dir)) HFiles.createDirectories(dir)
                val arr = JSONArray()
                map.forEach { (pkg, e) ->
                    arr.put(
                        JSONObject()
                            .put(KEY_PKG, pkg)
                            .put(KEY_LABEL, e.label)
                            .put(KEY_STATE, e.state.ordinal)
                            .put(KEY_SYSTEM, e.isSystem)
                            .put(KEY_EXISTS, e.exists)
                    )
                }
                HFiles.write(path, arr.toString())
            }.onFailure {
                dirty.set(true)
            }
        }
    }
}
