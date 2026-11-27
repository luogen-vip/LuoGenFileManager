package com.luogen.filehub.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.luogen.filehub.model.FileItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 别的 App 通过「用其他应用打开」把文件交给罗根文件管家时，
 * content:// 只能临时读一次，必须先落到自己的缓存里再交给查看器。
 */
object ExternalOpen {
    fun dir(ctx: Context): File = File(ctx.cacheDir, "opened").apply { runCatching { mkdirs() } }

    fun nameOf(ctx: Context, uri: Uri, fallback: String): String {
        var name = ""
        try {
            ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) name = c.getString(0) ?: ""
            }
        } catch (_: Throwable) {
        }
        if (name.isBlank()) {
            name = uri.lastPathSegment?.substringAfterLast('/')?.substringAfterLast(':') ?: ""
        }
        return name.ifBlank { fallback.ifBlank { "外部文件" } }
    }

    suspend fun materialize(ctx: Context, uriString: String, name: String, mime: String): FileItem? =
        withContext(Dispatchers.IO) {
            try {
                val uri = Uri.parse(uriString)
                if (uri.scheme == "file") {
                    val f = File(uri.path ?: return@withContext null)
                    if (!f.exists() || !f.isFile) return@withContext null
                    return@withContext FileItem(
                        path = f.absolutePath, name = f.name, size = f.length(),
                        modified = f.lastModified(), mime = mime.ifEmpty { TypeDetect.mimeOf(f.name) }
                    )
                }
                prune(ctx)
                val safeName = name.replace('/', '_').replace('\\', '_').ifBlank { "外部文件" }
                val target = unique(dir(ctx), safeName)
                val input = ctx.contentResolver.openInputStream(uri) ?: return@withContext null
                input.use { ins ->
                    target.outputStream().use { out -> ins.copyTo(out, 64 * 1024) }
                }
                if (!target.exists() || target.length() == 0L) return@withContext null
                FileItem(
                    path = target.absolutePath, name = target.name, size = target.length(),
                    modified = target.lastModified(), mime = mime.ifEmpty { TypeDetect.mimeOf(target.name) }
                )
            } catch (_: Throwable) {
                null
            }
        }

    /** 只保留最近 7 天从外部打开过的临时副本，避免缓存越积越大 */
    private fun prune(ctx: Context) {
        try {
            val cutoff = System.currentTimeMillis() - 7L * 24 * 3600 * 1000
            dir(ctx).listFiles()?.forEach { if (it.isFile && it.lastModified() < cutoff) it.delete() }
        } catch (_: Throwable) {
        }
    }

    private fun unique(dir: File, name: String): File {
        var f = File(dir, name)
        if (!f.exists()) return f
        val dot = name.lastIndexOf('.')
        val base = if (dot > 0) name.substring(0, dot) else name
        val ext = if (dot > 0) name.substring(dot) else ""
        var i = 2
        while (f.exists() && i < 500) {
            f = File(dir, "$base ($i)$ext")
            i++
        }
        return f
    }
}
