package com.aistra.hail.utils

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.ApplicationInfo
import android.graphics.Bitmap
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.widget.ImageView
import androidx.collection.LruCache
import com.aistra.hail.HailApp.Companion.app
import com.aistra.hail.R
import com.aistra.hail.app.HailData
import kotlinx.coroutines.*
import me.zhanghai.android.appiconloader.AppIconLoader
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import kotlin.coroutines.CoroutineContext

/**
 * @author Rikka
 * Source
 * https://raw.githubusercontent.com/RikkaApps/Shizuku/master/manager/src/main/java/moe/shizuku/manager/utils/AppIconCache.kt
 */
object AppIconCache : CoroutineScope {

    private class AppIconLruCache constructor(maxSize: Int) :
        LruCache<Triple<String, Int, Int>, Bitmap>(maxSize) {

        override fun sizeOf(key: Triple<String, Int, Int>, value: Bitmap): Int {
            return value.byteCount / 1024
        }
    }

    override val coroutineContext: CoroutineContext get() = Dispatchers.Main.immediate

    private val lruCache: LruCache<Triple<String, Int, Int>, Bitmap>

    private val dispatcher: CoroutineDispatcher

    private val preloadDispatcher: CoroutineDispatcher by lazy {
        Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "icon-preload").apply { priority = Thread.NORM_PRIORITY - 1 }
        }.asCoroutineDispatcher()
    }

    private var appIconLoaders = mutableMapOf<Int, AppIconLoader>()

    private var shrinkNonAdaptiveIcons: Boolean

    private val cf by lazy { ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(0f) }) }

    private val tagKey = R.id.app_icon

    init {
        val maxMemory = Runtime.getRuntime().maxMemory() / 1024
        val availableCacheSize = (maxMemory / 4).toInt()
        lruCache = AppIconLruCache(availableCacheSize)

        val availableProcessorsCount = try {
            Runtime.getRuntime().availableProcessors()
        } catch (ignored: Exception) {
            1
        }
        val threadCount = 2.coerceAtLeast(availableProcessorsCount / 2)
        val loadIconExecutor: Executor = Executors.newFixedThreadPool(threadCount)
        dispatcher = loadIconExecutor.asCoroutineDispatcher()
        shrinkNonAdaptiveIcons = HailData.synthesizeAdaptiveIcons
    }

    private fun getMemory(packageName: String, userId: Int, size: Int): Bitmap? =
        lruCache[Triple(packageName, userId, size)]

    private fun put(packageName: String, userId: Int, size: Int, bitmap: Bitmap) {
        if (lruCache[Triple(packageName, userId, size)] == null) {
            lruCache.put(Triple(packageName, userId, size), bitmap)
        }
        saveToDiskAsync(packageName, userId, size, bitmap)
    }

    private fun iconDiskFile(packageName: String, userId: Int, size: Int): File {
        val dir = File(app.cacheDir, "icons")
        if (!dir.exists()) dir.mkdirs()
        return File(dir, "${packageName}_u${userId}_s${size}.png")
    }

    private fun loadFromDisk(packageName: String, userId: Int, size: Int): Bitmap? = runCatching {
        val f = iconDiskFile(packageName, userId, size)
        if (!f.isFile || f.length() == 0L) return null
        android.graphics.BitmapFactory.decodeFile(f.absolutePath)
    }.getOrNull()

    private fun saveToDiskAsync(packageName: String, userId: Int, size: Int, bitmap: Bitmap) {
        launch(preloadDispatcher) {
            runCatching {
                val f = iconDiskFile(packageName, userId, size)
                if (f.exists() && f.length() > 0) return@runCatching
                FileOutputStream(f).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 90, out)
                }
            }
        }
    }

    fun clear() {
        lruCache.evictAll()
        runCatching { File(app.cacheDir, "icons").deleteRecursively() }
    }

    @SuppressLint("NewApi")
    fun getOrLoadBitmap(context: Context, info: ApplicationInfo, userId: Int, size: Int): Bitmap {
        // Memory only on the hot path — disk decode must stay off the main thread
        getMemory(info.packageName, userId, size)?.let { return it }
        loadFromDisk(info.packageName, userId, size)?.let {
            lruCache.put(Triple(info.packageName, userId, size), it)
            return it
        }
        var loader = appIconLoaders[size]
        if (loader == null || shrinkNonAdaptiveIcons != HailData.synthesizeAdaptiveIcons) {
            shrinkNonAdaptiveIcons = HailData.synthesizeAdaptiveIcons
            loader = AppIconLoader(size, shrinkNonAdaptiveIcons, context)
            appIconLoaders[size] = loader
        }
        val bitmap = IconPack.loadIcon(info.packageName) ?: loader.loadIcon(info, false)
        put(info.packageName, userId, size, bitmap)
        return bitmap
    }

    fun preloadIconsAsync(context: Context, apps: List<ApplicationInfo>, userId: Int) {
        val size = context.resources.getDimensionPixelSize(R.dimen.app_icon_size)
        launch(preloadDispatcher) {
            for (info in apps) {
                if (getMemory(info.packageName, userId, size) != null) continue
                try {
                    getOrLoadBitmap(context, info, userId, size)
                } catch (e: CancellationException) {
                    return@launch
                } catch (_: Throwable) {
                }
            }
        }
    }

    /** Resolve ApplicationInfo on a background thread, then decode icons — never on the UI thread. */
    fun preloadPackagesAsync(context: Context, packageNames: List<String>, userId: Int) {
        val size = context.resources.getDimensionPixelSize(R.dimen.app_icon_size)
        launch(preloadDispatcher) {
            for (pkg in packageNames) {
                if (getMemory(pkg, userId, size) != null) continue
                try {
                    val info = HPackages.getApplicationInfoOrNull(pkg) ?: continue
                    getOrLoadBitmap(context, info, userId, size)
                } catch (e: CancellationException) {
                    return@launch
                } catch (_: Throwable) {
                }
            }
        }
    }

    @JvmStatic
    fun loadIconBitmapAsync(
        context: Context,
        info: ApplicationInfo,
        userId: Int,
        view: ImageView,
        setColorFilter: Boolean = false
    ): Job {
        // Token + filter applied synchronously so recycle/rebind races cannot leave stale grayscale.
        val token = info.packageName to setColorFilter
        view.setTag(tagKey, token)
        view.colorFilter = if (setColorFilter) cf else null

        return launch {
            val size = view.measuredWidth.let {
                if (it > 0) it else context.resources.getDimensionPixelSize(R.dimen.app_icon_size)
            }
            if (shrinkNonAdaptiveIcons != HailData.synthesizeAdaptiveIcons) {
                lruCache.evictAll()
            } else {
                // Memory hit only on Main — never decode disk here
                getMemory(info.packageName, userId, size)?.let { cachedBitmap ->
                    if (view.getTag(tagKey) != token) return@launch
                    view.setImageBitmap(cachedBitmap)
                    view.colorFilter = if (setColorFilter) cf else null
                    return@launch
                }
            }

            val bitmap = try {
                withContext(dispatcher) {
                    getOrLoadBitmap(context, info, userId, size)
                }
            } catch (e: CancellationException) {
                return@launch
            } catch (e: Throwable) {
                null
            }

            if (view.getTag(tagKey) != token) return@launch
            if (bitmap != null) {
                view.setImageBitmap(bitmap)
            } else {
                view.setImageDrawable(if (HTarget.O) context.packageManager.defaultActivityIcon else null)
            }
            view.colorFilter = if (setColorFilter) cf else null
        }
    }
}
