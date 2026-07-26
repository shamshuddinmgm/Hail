package com.aistra.hail.utils

import android.app.Activity
import android.os.Build
import android.util.Log
import android.view.View
import android.view.WindowManager
import com.aistra.hail.R
import com.aistra.hail.app.HailData
import com.google.android.material.color.DynamicColors

object HTheme {
    private const val TAG = "HTheme"

    fun applyActivityTheme(activity: Activity) {
        try {
            when (HailData.appTheme) {
                HailData.THEME_AMOLED -> activity.setTheme(R.style.Theme_Hail_Amoled)
                HailData.THEME_DARK_GRAY -> activity.setTheme(R.style.Theme_Hail_DarkGray)
                HailData.THEME_CHOCOLATE -> activity.setTheme(R.style.Theme_Hail_Chocolate)
                HailData.THEME_MIDNIGHT -> activity.setTheme(R.style.Theme_Hail_Midnight)
                HailData.THEME_EMBER -> activity.setTheme(R.style.Theme_Hail_Ember)
                HailData.THEME_NEON_HACKER -> activity.setTheme(R.style.Theme_Hail_NeonHacker)
                HailData.THEME_NEON_CYBER -> activity.setTheme(R.style.Theme_Hail_NeonCyber)
                HailData.THEME_NEON_PLASMA -> activity.setTheme(R.style.Theme_Hail_NeonPlasma)
                HailData.THEME_NEON_ICE -> activity.setTheme(R.style.Theme_Hail_NeonIce)
                else -> {
                    activity.setTheme(R.style.Theme_Hail)
                    if (HailData.appTheme == HailData.FOLLOW_SYSTEM && HTarget.S) {
                        runCatching { DynamicColors.applyToActivityIfAvailable(activity) }
                    }
                }
            }
        } catch (t: Throwable) {
            Log.e(TAG, "applyActivityTheme failed, falling back to Theme.Hail", t)
            runCatching { activity.setTheme(R.style.Theme_Hail) }
        }
    }

    fun isForcedDark(theme: String = HailData.appTheme): Boolean = when (theme) {
        HailData.THEME_DARK,
        HailData.THEME_AMOLED,
        HailData.THEME_DARK_GRAY,
        HailData.THEME_CHOCOLATE,
        HailData.THEME_MIDNIGHT,
        HailData.THEME_EMBER,
        HailData.THEME_NEON_HACKER,
        HailData.THEME_NEON_CYBER,
        HailData.THEME_NEON_PLASMA,
        HailData.THEME_NEON_ICE -> true
        else -> false
    }

    fun isCustomPremium(theme: String = HailData.appTheme): Boolean =
        theme != HailData.FOLLOW_SYSTEM && theme != HailData.THEME_LIGHT && theme != HailData.THEME_DARK

    /** Prefer the highest refresh rate the display supports (e.g. 120 Hz). Never crash the app. */
    fun enableHighRefreshRate(activity: Activity) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return
        try {
            val display = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                activity.display
            } else {
                @Suppress("DEPRECATION")
                activity.windowManager.defaultDisplay
            } ?: return
            val best = display.supportedModes.maxByOrNull { it.refreshRate } ?: return
            val lp = activity.window.attributes
            var changed = false
            if (lp.preferredDisplayModeId != best.modeId) {
                lp.preferredDisplayModeId = best.modeId
                changed = true
            }
            // Also hint preferred Hz (helps vendors that ignore mode id alone)
            @Suppress("DEPRECATION")
            if (lp.preferredRefreshRate != best.refreshRate) {
                lp.preferredRefreshRate = best.refreshRate
                changed = true
            }
            if (changed) activity.window.attributes = lp

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                activity.window.addFlags(WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED)
            }
            // Surface-level 120 Hz request — critical on Android 15 ARR (normal=60 / high=90)
            activity.window.decorView.post {
                requestSurfaceFrameRate(activity.window.decorView, best.refreshRate)
            }
        } catch (t: Throwable) {
            Log.w(TAG, "enableHighRefreshRate skipped", t)
        }
    }

    /** Ask the compositor to keep this view's surface at [fps] while visible. */
    fun requestSurfaceFrameRate(view: View, fps: Float = 120f) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        fun apply() {
            runCatching {
                val control = view.rootSurfaceControl ?: return@runCatching
                // Reflect: AttachedSurfaceControl.setFrameRate is @SystemApi on some stubs
                val method = control.javaClass.getMethod(
                    "setFrameRate",
                    Float::class.javaPrimitiveType,
                    Int::class.javaPrimitiveType
                )
                method.invoke(control, fps, 0 /* FRAME_RATE_COMPATIBILITY_DEFAULT */)
            }.onFailure { Log.w(TAG, "setFrameRate skipped", it) }
        }
        apply()
        view.post { apply() }
    }
}
