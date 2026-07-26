package com.aistra.hail.utils

import android.content.pm.ApplicationInfo

/** Short-lived cache so Home↔Apps switches don't rescan PackageManager every time. */
object InstalledAppsCache {
    @Volatile
    private var cached: List<ApplicationInfo>? = null

    @Volatile
    private var cachedAt = 0L

    fun get(maxAgeMs: Long = 60_000L): List<ApplicationInfo>? {
        val list = cached ?: return null
        if (System.currentTimeMillis() - cachedAt > maxAgeMs) return null
        return list
    }

    fun put(list: List<ApplicationInfo>) {
        cached = list
        cachedAt = System.currentTimeMillis()
    }

    fun clear() {
        cached = null
        cachedAt = 0L
    }
}
