package com.aistra.hail

import android.app.Application
import android.app.UiModeManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import com.aistra.hail.app.AppManager
import com.aistra.hail.app.HailData
import com.aistra.hail.services.AutoFreezeService
import com.aistra.hail.utils.HDhizuku
import com.aistra.hail.utils.AppMetaCache
import com.aistra.hail.utils.HTarget
import com.aistra.hail.utils.HTheme

class HailApp : Application() {
    override fun onCreate() {
        super.onCreate()
        app = this
        // DirtyDataUpdater.update(app)
        if (!HTarget.S) setAppTheme(HailData.appTheme)
        if (HailData.workingMode.startsWith(HailData.DHIZUKU)) {
            // Binder init can wait until after first frame
            android.os.Handler(mainLooper).post { runCatching { HDhizuku.init() } }
        }
        // Load meta + apps JSON, seed labels/state from disk, then refresh PM in background
        Thread({
            runCatching {
                AppMetaCache.ensureLoaded()
                HailData.tags.size
                val apps = HailData.checkedList
                AppMetaCache.applyToAll(apps)
                // Live refresh PackageManager → rewrite disk cache for next cold start
                apps.forEach { it.refreshFromPackageManager() }
                AppMetaCache.flush()
            }
        }, "hail-data-warm").apply {
            priority = Thread.NORM_PRIORITY - 1
            start()
        }
    }

    fun setAutoFreezeService(autoFreezeAfterLock: Boolean = HailData.autoFreezeAfterLock, context: Context = app) {
        val start = autoFreezeAfterLock && HailData.checkedList.any {
            it.packageName != packageName && !it.whitelisted &&
                it.applicationInfo != null && !AppManager.isAppFrozen(it)
        }
        val intent = Intent(app, AutoFreezeService::class.java)
        if (start) {
            setAutoFreezeServiceEnabled(true)
            ContextCompat.startForegroundService(context, intent)
        } else {
            stopService(intent)
            setAutoFreezeServiceEnabled(false)
        }
    }

    fun setAutoFreezeServiceEnabled(enabled: Boolean) {
        packageManager.setComponentEnabledSetting(
            ComponentName(app, AutoFreezeService::class.java),
            if (enabled) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            PackageManager.DONT_KILL_APP
        )
    }

    fun setAppTheme(theme: String) {
        val nightMode = when {
            theme == HailData.THEME_LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
            HTheme.isForcedDark(theme) -> AppCompatDelegate.MODE_NIGHT_YES
            else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        }
        if (HTarget.S) {
            getSystemService<UiModeManager>()!!.setApplicationNightMode(
                when (nightMode) {
                    AppCompatDelegate.MODE_NIGHT_NO -> UiModeManager.MODE_NIGHT_NO
                    AppCompatDelegate.MODE_NIGHT_YES -> UiModeManager.MODE_NIGHT_YES
                    else -> UiModeManager.MODE_NIGHT_AUTO
                }
            )
        } else {
            AppCompatDelegate.setDefaultNightMode(nightMode)
        }
    }


    companion object {
        lateinit var app: HailApp private set
    }
}