package com.aistra.hail.utils

import android.app.Activity
import android.os.Build
import android.view.WindowManager
import com.aistra.hail.R
import com.aistra.hail.app.HailData
import com.google.android.material.color.DynamicColors

object HTheme {
    fun applyActivityTheme(activity: Activity) {
        when (HailData.appTheme) {
            HailData.THEME_AMOLED -> activity.setTheme(R.style.Theme_Hail_Amoled)
            HailData.THEME_NEON_HACKER -> activity.setTheme(R.style.Theme_Hail_NeonHacker)
            HailData.THEME_NEON_CYBER -> activity.setTheme(R.style.Theme_Hail_NeonCyber)
            HailData.THEME_NEON_PLASMA -> activity.setTheme(R.style.Theme_Hail_NeonPlasma)
            HailData.THEME_NEON_ICE -> activity.setTheme(R.style.Theme_Hail_NeonIce)
            else -> {
                activity.setTheme(R.style.Theme_Hail)
                if (HailData.appTheme == HailData.FOLLOW_SYSTEM && HTarget.S) {
                    DynamicColors.applyToActivityIfAvailable(activity)
                }
            }
        }
    }

    fun isForcedDark(theme: String = HailData.appTheme): Boolean = when (theme) {
        HailData.THEME_DARK,
        HailData.THEME_AMOLED,
        HailData.THEME_NEON_HACKER,
        HailData.THEME_NEON_CYBER,
        HailData.THEME_NEON_PLASMA,
        HailData.THEME_NEON_ICE -> true
        else -> false
    }

    /** Prefer the highest refresh rate the display supports (e.g. 120 Hz). */
    fun enableHighRefreshRate(activity: Activity) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return
        val display = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            activity.display
        } else {
            @Suppress("DEPRECATION")
            activity.windowManager.defaultDisplay
        } ?: return
        val best = display.supportedModes.maxByOrNull { it.refreshRate } ?: return
        val lp = activity.window.attributes
        if (lp.preferredDisplayModeId != best.modeId) {
            lp.preferredDisplayModeId = best.modeId
            activity.window.attributes = lp
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            activity.window.addFlags(WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED)
        }
    }
}
