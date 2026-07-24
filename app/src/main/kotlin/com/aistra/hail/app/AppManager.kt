package com.aistra.hail.app

import android.content.Intent
import com.aistra.hail.BuildConfig
import com.aistra.hail.utils.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object AppManager {
    /** Package name of the first app that returned a permission denial in the last setListFrozen call. */
    var lastDeniedPackage: String? = null
        private set

    val lockScreen: Boolean
        get() = when {
            HailData.workingMode.startsWith(HailData.OWNER) -> HPolicy.lockScreen
            HailData.workingMode.startsWith(HailData.DHIZUKU) -> HDhizuku.lockScreen
            HailData.workingMode.startsWith(HailData.SU) -> HShell.lockScreen
            HailData.workingMode.startsWith(HailData.SHIZUKU) -> HShizuku.lockScreen
            else -> false
        }

    fun isAppFrozen(packageName: String, mode: String? = null): Boolean {
        val effective = mode?.takeIf { it.isNotEmpty() } ?: run {
            HailData.checkedList.find { it.packageName == packageName }?.frozenMode
                ?.takeIf { it.isNotEmpty() }
        }
        return when {
            effective == null || effective == HailData.MODE_DEFAULT ->
                HPackages.isAppDisabled(packageName)
                        || HPackages.isAppHidden(packageName)
                        || HPackages.isAppSuspended(packageName)
                        || HPackages.isAppStopped(packageName)

            effective.endsWith(HailData.STOP) -> HPackages.isAppStopped(packageName)
            effective.endsWith(HailData.DISABLE) -> HPackages.isAppDisabled(packageName)
            effective.endsWith(HailData.HIDE) -> HPackages.isAppHidden(packageName)
            effective.endsWith(HailData.SUSPEND) -> HPackages.isAppSuspended(packageName)
            else -> HPackages.isAppDisabled(packageName)
                    || HPackages.isAppHidden(packageName)
                    || HPackages.isAppSuspended(packageName)
        }
    }

    /**
     * Freeze/unfreeze apps. Each pair is (app, workingMode to use for that app).
     * On successful freeze, [AppInfo.frozenMode] is updated; cleared on unfreeze.
     */
    fun setListFrozen(frozen: Boolean, appsWithModes: List<Pair<AppInfo, String>>): String? {
        lastDeniedPackage = null
        val excludeMe = appsWithModes.filter { it.first.packageName != BuildConfig.APPLICATION_ID }
        var i = 0
        var denied = false
        var name = String()
        excludeMe.forEach { (info, mode) ->
            when {
                setAppFrozen(info.packageName, frozen, mode) -> {
                    i++
                    name = info.name.toString()
                    if (frozen) info.frozenMode = mode
                    else info.frozenMode = null
                }

                info.applicationInfo != null -> {
                    denied = true
                    if (lastDeniedPackage == null) lastDeniedPackage = info.packageName
                }
            }
        }
        if (i > 0) HailData.saveApps()
        return if (denied && i == 0) null else if (i == 1) name else i.toString()
    }

    /** Convenience: same [mode] for every app. */
    fun setListFrozen(frozen: Boolean, mode: String, vararg appInfo: AppInfo): String? =
        setListFrozen(frozen, appInfo.map { it to mode })

    fun setAppFrozen(packageName: String, frozen: Boolean, mode: String = HailData.workingMode): Boolean =
        packageName != BuildConfig.APPLICATION_ID && when (mode) {
            HailData.MODE_OWNER_HIDE -> HPolicy.setAppHidden(packageName, frozen)
            HailData.MODE_OWNER_SUSPEND -> HPolicy.setAppSuspended(packageName, frozen)
            HailData.MODE_DHIZUKU_HIDE -> HDhizuku.setAppHidden(packageName, frozen)
            HailData.MODE_DHIZUKU_SUSPEND -> HDhizuku.setAppSuspended(packageName, frozen)
            HailData.MODE_SU_STOP -> !frozen || HShell.forceStopApp(packageName)
            HailData.MODE_SU_DISABLE -> HShell.setAppDisabled(packageName, frozen)
            HailData.MODE_SU_HIDE -> HShell.setAppHidden(packageName, frozen)
            HailData.MODE_SU_SUSPEND -> HShell.setAppSuspended(packageName, frozen)
            HailData.MODE_SHIZUKU_STOP -> !frozen || HShizuku.forceStopApp(packageName)
            HailData.MODE_SHIZUKU_DISABLE -> HShizuku.setAppDisabled(packageName, frozen)
            HailData.MODE_SHIZUKU_HIDE -> HShizuku.setAppHidden(packageName, frozen)
            HailData.MODE_SHIZUKU_SUSPEND -> HShizuku.setAppSuspended(packageName, frozen)
            HailData.MODE_ISLAND_HIDE -> HIsland.setAppHidden(packageName, frozen)
            HailData.MODE_ISLAND_SUSPEND -> HIsland.setAppSuspended(packageName, frozen)
            HailData.MODE_PRIVAPP_STOP -> !frozen || HPackages.forceStopApp(packageName)
            HailData.MODE_PRIVAPP_DISABLE -> HPackages.setAppDisabled(packageName, frozen)
            else -> false
        }

    fun uninstallApp(packageName: String): Boolean {
        when {
            HailData.workingMode.startsWith(HailData.OWNER) ->
                if (HPolicy.uninstallApp(packageName)) return true

            HailData.workingMode.startsWith(HailData.DHIZUKU) ->
                if (HDhizuku.uninstallApp(packageName)) return true

            HailData.workingMode.startsWith(HailData.SU) ->
                if (HShell.uninstallApp(packageName)) return true

            HailData.workingMode.startsWith(HailData.SHIZUKU) ->
                if (HShizuku.uninstallApp(packageName)) return true
        }
        HUI.startActivity(Intent.ACTION_DELETE, HPackages.packageUri(packageName))
        return false
    }

    fun reinstallApp(packageName: String): Boolean = when {
        HailData.workingMode.startsWith(HailData.SU) -> HShell.reinstallApp(packageName)
        HailData.workingMode.startsWith(HailData.SHIZUKU) -> HShizuku.reinstallApp(packageName)
        else -> false
    }

    suspend fun execute(command: String): Pair<Int, String?> = withContext(Dispatchers.IO) {
        when {
            HailData.workingMode.startsWith(HailData.SU) -> HShell.execute(command, true)
            HailData.workingMode.startsWith(HailData.SHIZUKU) -> HShizuku.execute(command)
            else -> 0 to null
        }
    }
}
