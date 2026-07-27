package com.aistra.hail.utils

import android.os.FileUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.*
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.Paths
import kotlin.io.path.exists
import kotlin.io.copyTo as ktCopyTo

object HFiles {
    fun exists(path: String): Boolean = when {
        HTarget.O -> Files.exists(Paths.get(path))
        else -> File(path).exists()
    }

    fun createDirectories(dir: String): Boolean = when {
        HTarget.O -> Files.createDirectories(Paths.get(dir)).exists()
        else -> File(dir).mkdirs()
    }

    /* suspend fun copy(source: String, target: String): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            when {
                HTarget.O -> Files.copy(
                    Paths.get(source), Paths.get(target), StandardCopyOption.REPLACE_EXISTING
                )

                else -> FileInputStream(source).channel.use {
                    FileOutputStream(target).channel.use { out ->
                        out.transferFrom(it, 0, it.size())
                    }
                }
            }
            true
        }.getOrDefault(false)
    } */

    suspend fun copy(source: InputStream, target: OutputStream) = withContext(Dispatchers.IO) {
        if (HTarget.Q) {
            FileUtils.copy(source, target)
        } else if (source is FileInputStream && target is FileOutputStream) {
            copy(source, target)
        } else {
            source.ktCopyTo(target)
        }
    }

    private suspend fun copy(source: FileInputStream, target: FileOutputStream) = withContext(Dispatchers.IO) {
        if (HTarget.Q) {
            FileUtils.copy(source.fd, target.fd)
        } else {
            source.channel.use {
                target.channel.use { outputChannel ->
                    copy(it, outputChannel)
                }
            }
        }
    }

    private suspend fun copy(source: FileChannel, out: FileChannel) = withContext(Dispatchers.IO) {
        val size = source.size()
        var left = size
        while (left > 0) {
            left -= source.transferTo(size - left, left, out)
        }
    }

    /**
     * This method is not recommended on huge files. It has an internal limitation of 2 GB file size.
     */
    fun read(source: String): String? = runCatching {
        File(source).readText()
    }.getOrNull()

    fun write(target: String, text: String): Boolean = runCatching {
        val file = File(target)
        val parent = file.parentFile ?: return@runCatching false
        if (!parent.exists()) parent.mkdirs()
        val tmp = File.createTempFile("${file.name}.", ".tmp", parent)
        try {
            tmp.writeText(text)
            // Prefer atomic replace; never truncate the destination on failure
            if (tmp.renameTo(file)) return@runCatching true
            if (HTarget.O) {
                Files.move(
                    tmp.toPath(),
                    file.toPath(),
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                    java.nio.file.StandardCopyOption.ATOMIC_MOVE
                )
                return@runCatching true
            }
            false
        } finally {
            if (tmp.exists()) tmp.delete()
        }
    }.getOrDefault(false)
}