package com.aistra.hail.utils

import android.content.Context
import android.net.Uri
import androidx.preference.PreferenceManager
import com.aistra.hail.app.HailData
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter

object SettingsBackupManager {

    private const val KEY_VERSION = "version"
    private const val KEY_APPS = "apps"
    private const val KEY_TAGS = "tags"
    private const val KEY_HIDDEN_APPS = "hidden_apps"
    private const val KEY_PREFERENCES = "preferences"
    private const val BACKUP_VERSION = 2

    private val FLOAT_PREF_KEYS = setOf(HailData.HOME_FONT_SIZE, HailData.AUTO_FREEZE_DELAY)

    /** Keys we export/import — never write arbitrary attacker keys. */
    private val EXPORT_PREF_KEYS = listOf(
        HailData.WORKING_MODE,
        HailData.BIOMETRIC_LOGIN,
        HailData.APP_THEME,
        HailData.ICON_PACK,
        HailData.GRAYSCALE_ICON,
        HailData.COMPACT_ICON,
        HailData.SYNTHESIZE_ADAPTIVE_ICONS,
        HailData.HOME_FONT_SIZE,
        HailData.FUZZY_SEARCH,
        HailData.NINE_KEY_SEARCH,
        HailData.TILE_ACTION,
        HailData.AUTO_FREEZE_AFTER_LOCK,
        HailData.AUTO_FREEZE_DELAY,
        HailData.SKIP_WHILE_CHARGING,
        HailData.SKIP_FOREGROUND_APP,
        HailData.SKIP_NOTIFYING_APP,
        HailData.SHOW_UNINSTALLED,
        HailData.DYNAMIC_SHORTCUT_ACTION,
        HailData.FILTER_USER_APPS,
        HailData.FILTER_SYSTEM_APPS,
        HailData.FILTER_FROZEN_APPS,
        HailData.FILTER_UNFROZEN_APPS,
        HailData.FILTER_ADDED_APPS,
        HailData.FILTER_UNADDED_APPS,
        HailData.FILTER_ADDED_USER_APPS,
        HailData.FILTER_UNADDED_USER_APPS,
        HailData.FILTER_ADDED_SYSTEM_APPS,
        HailData.FILTER_UNADDED_SYSTEM_APPS,
        HailData.SHORTCUT_LAUNCH_PROMPT,
        HailData.SHIZUKU_REQUIRED_NOTIFICATION,
        "sort_by",
    )

    /** Device-local / security-sensitive — never overwrite from a backup file. */
    private val SKIP_IMPORT_PREF_KEYS = setOf(
        HailData.WORKING_MODE,
        HailData.BIOMETRIC_LOGIN,
    )

    private val STRING_PREF_KEYS = setOf(
        HailData.WORKING_MODE,
        HailData.APP_THEME,
        HailData.ICON_PACK,
        HailData.TILE_ACTION,
        HailData.DYNAMIC_SHORTCUT_ACTION,
        "sort_by",
    )

    private val BOOLEAN_PREF_KEYS = setOf(
        HailData.BIOMETRIC_LOGIN,
        HailData.GRAYSCALE_ICON,
        HailData.COMPACT_ICON,
        HailData.SYNTHESIZE_ADAPTIVE_ICONS,
        HailData.FUZZY_SEARCH,
        HailData.NINE_KEY_SEARCH,
        HailData.AUTO_FREEZE_AFTER_LOCK,
        HailData.SKIP_WHILE_CHARGING,
        HailData.SKIP_FOREGROUND_APP,
        HailData.SKIP_NOTIFYING_APP,
        HailData.SHOW_UNINSTALLED,
        HailData.FILTER_USER_APPS,
        HailData.FILTER_SYSTEM_APPS,
        HailData.FILTER_FROZEN_APPS,
        HailData.FILTER_UNFROZEN_APPS,
        HailData.FILTER_ADDED_APPS,
        HailData.FILTER_UNADDED_APPS,
        HailData.FILTER_ADDED_USER_APPS,
        HailData.FILTER_UNADDED_USER_APPS,
        HailData.FILTER_ADDED_SYSTEM_APPS,
        HailData.FILTER_UNADDED_SYSTEM_APPS,
        HailData.SHORTCUT_LAUNCH_PROMPT,
        HailData.SHIZUKU_REQUIRED_NOTIFICATION,
    )
    /**
     * Exports all settings (checked apps, tags, hidden apps, and shared preferences) to a JSON file at [uri].
     */
    fun exportToUri(context: Context, uri: Uri): Boolean = runCatching {
        val root = JSONObject()
        root.put(KEY_VERSION, BACKUP_VERSION)

        // --- Checked apps ---
        val appsArray = JSONArray()
        HailData.checkedList.forEach { appInfo ->
            val obj = JSONObject()
            obj.put(HailData.KEY_PACKAGE, appInfo.packageName)
            obj.put("pinned", appInfo.pinned)
            obj.put(HailData.KEY_PIN_ORDER, appInfo.pinOrder)
            obj.put("whitelisted", appInfo.whitelisted)
            obj.put("tags", JSONArray(appInfo.tagIdList))
            obj.put("add_to_home_screen", appInfo.addToHomeScreen)
            obj.put(HailData.KEY_PREREQ_PACKAGE, appInfo.prereqPackage ?: "")
            obj.put(HailData.KEY_PREREQ_LAUNCH, appInfo.prereqLaunch)
            obj.put(HailData.KEY_PREREQ_ENABLE, appInfo.prereqEnable)
            obj.put(HailData.KEY_FROZEN_MODE, appInfo.frozenMode ?: "")
            appsArray.put(obj)
        }
        root.put(KEY_APPS, appsArray)

        // --- Tags (order preserved — list maintains insertion order) ---
        val tagsArray = JSONArray()
        HailData.tags.forEach { tag ->
            val obj = JSONObject()
            obj.put(HailData.KEY_TAG, tag.name)
            obj.put("id", tag.id)
            obj.put(HailData.WORKING_MODE, tag.workingMode ?: "")
            tagsArray.put(obj)
        }
        root.put(KEY_TAGS, tagsArray)

        // --- Hidden apps ---
        val hiddenArray = JSONArray()
        HailData.hiddenApps.forEach { pkg -> hiddenArray.put(pkg) }
        root.put(KEY_HIDDEN_APPS, hiddenArray)

        // --- Shared preferences (all known keys) ---
        val sp = PreferenceManager.getDefaultSharedPreferences(context)
        val prefsObj = JSONObject()
        val allPrefs = sp.all
        EXPORT_PREF_KEYS.forEach { key ->
            allPrefs[key]?.let { value ->
                when (value) {
                    is Boolean -> prefsObj.put(key, value)
                    is Float -> prefsObj.put(key, value)
                    is Int -> prefsObj.put(key, value)
                    is Long -> prefsObj.put(key, value)
                    is String -> prefsObj.put(key, value)
                    else -> {} // skip
                }
            }
        }
        root.put(KEY_PREFERENCES, prefsObj)

        context.contentResolver.openOutputStream(uri)?.use { stream ->
            OutputStreamWriter(stream, Charsets.UTF_8).use { writer ->
                writer.write(root.toString(2))
            }
            true
        } ?: false
    }.getOrElse { it.printStackTrace(); false }

    /**
     * Imports settings from a JSON file at [uri].
     * Parse+validate fully first; only then mutate live state (all-or-nothing).
     */
    fun importFromUri(context: Context, uri: Uri): Boolean = runCatching {
        val text = context.contentResolver.openInputStream(uri)?.use { stream ->
            BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).readText()
        } ?: return false

        val root = JSONObject(text)

        // --- Parse tags into a staging list ---
        val stagedTags = mutableListOf<com.aistra.hail.app.TagInfo>()
        root.optJSONArray(KEY_TAGS)?.let { tagsArray ->
            for (i in 0 until tagsArray.length()) {
                val obj = tagsArray.getJSONObject(i)
                val name = obj.getString(HailData.KEY_TAG).trim()
                if (name.isEmpty()) continue
                stagedTags.add(
                    com.aistra.hail.app.TagInfo(
                        name = name,
                        id = obj.getInt("id"),
                        workingMode = obj.optString(HailData.WORKING_MODE).ifEmpty { null }
                    )
                )
            }
        }

        // --- Parse apps (drop invalid package names — shell injection guard) ---
        val stagedApps = mutableListOf<com.aistra.hail.app.AppInfo>()
        root.optJSONArray(KEY_APPS)?.let { appsArray ->
            for (i in 0 until appsArray.length()) {
                val obj = appsArray.getJSONObject(i)
                val packageName = obj.getString(HailData.KEY_PACKAGE)
                if (!HPackages.isValidPackageName(packageName)) continue
                val prereqPackage = obj.optString(HailData.KEY_PREREQ_PACKAGE).ifEmpty { null }
                if (prereqPackage != null && !HPackages.isValidPackageName(prereqPackage)) continue
                val tagsJsonArray = obj.optJSONArray("tags")
                val tagIdList: MutableList<Int> = if (tagsJsonArray != null) {
                    MutableList(tagsJsonArray.length()) { idx -> tagsJsonArray.getInt(idx) }
                } else {
                    mutableListOf(0)
                }
                stagedApps.add(
                    com.aistra.hail.app.AppInfo(
                        packageName = packageName,
                        pinned = obj.optBoolean("pinned", false),
                        pinOrder = obj.optInt(HailData.KEY_PIN_ORDER, i),
                        whitelisted = obj.optBoolean("whitelisted", false),
                        tagIdList = tagIdList,
                        addToHomeScreen = obj.optBoolean("add_to_home_screen", false),
                        prereqPackage = prereqPackage,
                        prereqLaunch = obj.optBoolean(HailData.KEY_PREREQ_LAUNCH, false),
                        prereqEnable = obj.optBoolean(HailData.KEY_PREREQ_ENABLE, false),
                        frozenMode = obj.optString(HailData.KEY_FROZEN_MODE).ifEmpty { null }
                    )
                )
            }
        }

        // --- Parse hidden apps ---
        val stagedHidden = mutableListOf<String>()
        root.optJSONArray(KEY_HIDDEN_APPS)?.let { hiddenArray ->
            for (i in 0 until hiddenArray.length()) {
                val pkg = hiddenArray.getString(i)
                if (HPackages.isValidPackageName(pkg)) stagedHidden.add(pkg)
            }
        }

        // --- Parse preferences (allowlisted keys + expected types only) ---
        data class PrefWrite(val key: String, val apply: (android.content.SharedPreferences.Editor) -> Unit)
        val stagedPrefs = mutableListOf<PrefWrite>()
        root.optJSONObject(KEY_PREFERENCES)?.let { prefsObj ->
            for (key in EXPORT_PREF_KEYS) {
                if (key in SKIP_IMPORT_PREF_KEYS) continue
                if (!prefsObj.has(key)) continue
                val value = prefsObj.get(key)
                when {
                    key in FLOAT_PREF_KEYS -> {
                        val f = when (value) {
                            is Number -> value.toFloat()
                            else -> continue
                        }
                        stagedPrefs.add(PrefWrite(key) { it.putFloat(key, f) })
                    }
                    key in BOOLEAN_PREF_KEYS -> {
                        if (value !is Boolean) continue
                        stagedPrefs.add(PrefWrite(key) { it.putBoolean(key, value) })
                    }
                    key in STRING_PREF_KEYS -> {
                        if (value !is String) continue
                        stagedPrefs.add(PrefWrite(key) { it.putString(key, value) })
                    }
                }
            }
        }

        // --- Commit only after full parse succeeded ---
        if (root.has(KEY_TAGS)) {
            HailData.tags.clear()
            HailData.tags.addAll(stagedTags)
            HailData.ensureDefaultTag()
            HailData.saveTags()
        }
        if (root.has(KEY_APPS)) {
            HailData.checkedList.clear()
            HailData.checkedList.addAll(stagedApps)
            HailData.saveApps()
        }
        if (root.has(KEY_HIDDEN_APPS)) {
            HailData.hiddenApps.clear()
            HailData.hiddenApps.addAll(stagedHidden)
            HailData.saveHiddenApps()
        }
        if (stagedPrefs.isNotEmpty()) {
            val editor = PreferenceManager.getDefaultSharedPreferences(context).edit()
            stagedPrefs.forEach { it.apply(editor) }
            editor.apply()
        }

        true
    }.getOrElse { it.printStackTrace(); false }
}
