package com.aistra.hail.utils

import android.annotation.SuppressLint
import android.content.res.Resources
import android.content.res.XmlResourceParser
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.aistra.hail.HailApp.Companion.app
import com.aistra.hail.app.HailData
import java.util.concurrent.ConcurrentHashMap

object IconPack {
    @Volatile
    private var cachedPackId: String? = null

    /** packageName → drawable resource name from appfilter.xml */
    private var appFilterMap: Map<String, String> = emptyMap()

    @SuppressLint("DiscouragedApi")
    fun loadIcon(packageName: String): Bitmap? {
        val pack = HailData.iconPack
        if (pack.isEmpty() || pack == HailData.ACTION_NONE) return null
        return runCatching {
            ensureAppFilterLoaded(pack)
            val resName = appFilterMap[packageName] ?: return null
            val resources = app.packageManager.getResourcesForApplication(pack)
            val id = resources.getIdentifier(resName, "drawable", pack)
            if (id == 0) null else BitmapFactory.decodeResource(resources, id)
        }.getOrNull()
    }

    @SuppressLint("DiscouragedApi")
    private fun ensureAppFilterLoaded(pack: String) {
        if (cachedPackId == pack && appFilterMap.isNotEmpty()) return
        synchronized(this) {
            if (cachedPackId == pack && appFilterMap.isNotEmpty()) return
            val resources = app.packageManager.getResourcesForApplication(pack)
            appFilterMap = parseAppFilter(resources, pack)
            cachedPackId = pack
        }
    }

    @SuppressLint("DiscouragedApi")
    private fun parseAppFilter(resources: Resources, resPackage: String): Map<String, String> {
        val out = ConcurrentHashMap<String, String>()
        val parser = resources.getXml(resources.getIdentifier("appfilter", "xml", resPackage))
        while (parser.eventType != XmlResourceParser.END_DOCUMENT) {
            runCatching {
                if (parser.eventType == XmlResourceParser.START_TAG) {
                    // Typical: <item component="ComponentInfo{pkg/...}" drawable="icon_name" />
                    val component = parser.getAttributeValue(null, "component")
                        ?: parser.getAttributeValue(0)
                    val drawable = parser.getAttributeValue(null, "drawable")
                        ?: parser.getAttributeValue(1)
                    if (component != null && drawable != null) {
                        // Store under package name substring for Hail's packageName lookups
                        val pkg = component.substringAfter('{', missingDelimiterValue = "")
                            .substringBefore('/', missingDelimiterValue = "")
                            .ifEmpty { component }
                        if (pkg.isNotEmpty()) out[pkg] = drawable
                        out[component] = drawable
                    }
                }
            }
            parser.next()
        }
        return out
    }

    fun clearCache() {
        synchronized(this) {
            cachedPackId = null
            appFilterMap = emptyMap()
        }
    }
}
