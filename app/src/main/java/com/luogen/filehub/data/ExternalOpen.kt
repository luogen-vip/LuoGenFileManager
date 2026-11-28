package com.luogen.filehub.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
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

    /** 文件大概多大：拿不到就返回 null */
    fun sizeOf(ctx: Context, uri: Uri): Long? {
        try {
            if (uri.scheme == "file") return File(uri.path ?: return null).length()
            ctx.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { c ->
                if (c.moveToFirst() && !c.isNull(0)) return c.getLong(0)
            }
        } catch (_: Throwable) {
        }
        return null
    }

    /** 在某个公开目录里取一个不重名的文件（a.mp4 → a (2).mp4） */
    fun uniquePublic(dir: File, name: String): File {
        val base = name.substringBeforeLast('.', name)
        val ext = name.substringAfterLast('.', "")
        var f = File(dir, name)
        var i = 2
        while (f.exists()) {
            val n = if (ext.isEmpty()) "$base ($i)" else "$base ($i).$ext"
            f = File(dir, n)
            i++
            if (i > 999) break
        }
        return f
    }

    /** 传进来的 mime 可能为空，用后缀兜底 */
    fun guessMime(name: String, mime: String): String {
        if (mime.isNotBlank() && mime != "application/octet-stream") return mime
        val ext = name.substringAfterLast('.', "").lowercase()
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: mime
    }

    /** 同步版本：小文件直接在调用线程复制，保证一进来就能打开，不依赖任何界面/协程时序 */
    fun materializeNow(ctx: Context, uri: Uri, name: String, mime: String): FileItem? {
        return try {
            if (uri.scheme == "file") {
                val f = File(uri.path ?: return null)
                if (!f.exists() || !f.isFile) return null
                return FileItem(
                    path = f.absolutePath, name = f.name, size = f.length(),
                    modified = f.lastModified(), mime = guessMime(f.name, mime)
                )
            }
            val safeName = name.replace('/', '_').replace('\\', '_').ifBlank { "外部文件" }
            val target = unique(dir(ctx), safeName)
            if (!copyTo(ctx, uri, target)) return null
            FileItem(
                path = target.absolutePath, name = target.name, size = target.length(),
                modified = target.lastModified(), mime = guessMime(target.name, mime)
            )
        } catch (_: Throwable) {
            null
        }
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
                        modified = f.lastModified(), mime = guessMime(f.name, mime)
                    )
                }
                prune(ctx)
                val safeName = name.replace('/', '_').replace('\\', '_').ifBlank { "外部文件" }
                val target = unique(dir(ctx), safeName)
                if (!copyTo(ctx, uri, target)) return@withContext null
                FileItem(
                    path = target.absolutePath, name = target.name, size = target.length(),
                    modified = target.lastModified(), mime = guessMime(target.name, mime)
                )
            } catch (_: Throwable) {
                null
            }
        }


    /** 把 uri 的内容写进 target：先试 openInputStream，不行再用 ParcelFileDescriptor 兜底 */
    private fun copyTo(ctx: Context, uri: Uri, target: File): Boolean {
        try {
            val input = ctx.contentResolver.openInputStream(uri)
            if (input != null) {
                input.use { ins -> target.outputStream().use { out -> ins.copyTo(out, 64 * 1024) } }
                if (target.exists() && target.length() > 0L) return true
            }
        } catch (_: Throwable) {
        }
        try {
            val pfd = ctx.contentResolver.openFileDescriptor(uri, "r") ?: return false
            pfd.use { d ->
                java.io.FileInputStream(d.fileDescriptor).use { ins ->
                    target.outputStream().use { out -> ins.copyTo(out, 64 * 1024) }
                }
            }
            return target.exists() && target.length() > 0L
        } catch (_: Throwable) {
            return false
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
