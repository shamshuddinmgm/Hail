package com.aistra.hail.app

import android.content.pm.ApplicationInfo
import com.aistra.hail.HailApp.Companion.app
import com.aistra.hail.utils.AppMetaCache
import com.aistra.hail.utils.HPackages

class AppInfo(
    val packageName: String,
    var pinned: Boolean = false,
    /** Manual order among pinned apps (lower = earlier). Ignored when not pinned. */
    var pinOrder: Int = 0,
    var whitelisted: Boolean = false,
    val tagIdList: MutableList<Int> = mutableListOf(0),
    var addToHomeScreen: Boolean = false,
    var prereqPackage: String? = null,
    var prereqLaunch: Boolean = false,
    var prereqEnable: Boolean = false,
    /** Working mode used when this app was last frozen; required for correct unfreeze. */
    var frozenMode: String? = null
) {
    enum class State { NOT_FOUND, UNFROZEN, FROZEN }

    @Volatile
    private var cachedApplicationInfo: ApplicationInfo? = null

    @Volatile
    private var applicationInfoResolved = false

    @Volatile
    private var cachedName: CharSequence? = null

    @Volatile
    private var cachedState: State? = null

    @Volatile
    private var cachedIsSystem: Boolean? = null

    @Volatile
    private var cachedExists: Boolean? = null

    /**
     * Cached PackageManager lookup. Call [invalidateCaches] after freeze/unfreeze
     * or package changes so flags/labels refresh.
     */
    val applicationInfo: ApplicationInfo?
        get() {
            if (!applicationInfoResolved) {
                cachedApplicationInfo = HPackages.getApplicationInfoOrNull(packageName)
                applicationInfoResolved = true
                cachedExists = cachedApplicationInfo != null
                cachedApplicationInfo?.let {
                    cachedIsSystem = it.flags and ApplicationInfo.FLAG_SYSTEM != 0
                }
            }
            return cachedApplicationInfo
        }

    val name: CharSequence
        get() {
            cachedName?.let { return it }
            // Disk meta before Binder — keeps sort/filter fast on cold start
            AppMetaCache.get(packageName)?.label?.let {
                cachedName = it
                return it
            }
            val label = applicationInfo?.loadLabel(app.packageManager) ?: packageName
            cachedName = label
            return label
        }

    val state: State
        get() {
            cachedState?.let { return it }
            // Prefer live PM once resolved; else disk snapshot for first paint
            if (!applicationInfoResolved) {
                AppMetaCache.get(packageName)?.state?.let {
                    cachedState = it
                    return it
                }
            }
            val resolved = when {
                applicationInfo == null -> State.NOT_FOUND
                AppManager.isAppFrozen(this) -> State.FROZEN
                else -> State.UNFROZEN
            }
            cachedState = resolved
            return resolved
        }

    /** System app hint without forcing a PM round-trip when disk cache has it. */
    val isSystemHint: Boolean
        get() {
            cachedIsSystem?.let { return it }
            AppMetaCache.get(packageName)?.let {
                cachedIsSystem = it.isSystem
                return it.isSystem
            }
            return applicationInfo?.let {
                val sys = it.flags and ApplicationInfo.FLAG_SYSTEM != 0
                cachedIsSystem = sys
                sys
            } ?: false
        }

    /** Installed hint — disk cache first for cold start filters. */
    val isInstalledHint: Boolean
        get() {
            cachedExists?.let { return it }
            if (applicationInfoResolved) return cachedApplicationInfo != null
            AppMetaCache.get(packageName)?.let {
                cachedExists = it.exists
                return it.exists
            }
            return applicationInfo != null
        }

    /** Apply disk snapshot without PackageManager (cold start seed). */
    fun seedFromCache(label: String, state: State, isSystem: Boolean, exists: Boolean) {
        if (cachedName == null) cachedName = label
        if (cachedState == null) cachedState = state
        if (cachedIsSystem == null) cachedIsSystem = isSystem
        if (cachedExists == null) cachedExists = exists
    }

    /** Full live refresh from PackageManager; updates disk meta. */
    fun refreshFromPackageManager() {
        cachedApplicationInfo = HPackages.getApplicationInfoOrNull(packageName)
        applicationInfoResolved = true
        cachedExists = cachedApplicationInfo != null
        cachedIsSystem = cachedApplicationInfo?.let {
            it.flags and ApplicationInfo.FLAG_SYSTEM != 0
        }
        cachedName = cachedApplicationInfo?.loadLabel(app.packageManager) ?: packageName
        cachedState = null
        // recompute state with fresh AI
        cachedState = when {
            cachedApplicationInfo == null -> State.NOT_FOUND
            AppManager.isAppFrozen(this) -> State.FROZEN
            else -> State.UNFROZEN
        }
        AppMetaCache.put(this)
    }

    /** Drop cached PM/label/state. Use after freeze, install, or uninstall changes. */
    fun invalidateCaches() {
        cachedApplicationInfo = null
        applicationInfoResolved = false
        cachedName = null
        cachedState = null
        cachedIsSystem = null
        cachedExists = null
    }

    /** Drop only frozen/unfrozen state (e.g. after list-wide refresh without re-querying labels). */
    fun invalidateState() {
        cachedState = null
    }

    override fun equals(other: Any?): Boolean = other is AppInfo && other.packageName == packageName
    override fun hashCode(): Int = packageName.hashCode()
}
