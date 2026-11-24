package com.luogen.filehub.data

import android.content.Context
import com.luogen.filehub.model.FileItem
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** 回收站：删除的文件先移动到应用私有目录 .trash，可还原 / 彻底删除 */
object TrashStore {

    fun dir(ctx: Context): File {
        val d = File(ctx.getExternalFilesDir(null), ".trash")
        if (!d.exists()) d.mkdirs()
        return d
    }

    private fun metaFile(ctx: Context) = File(dir(ctx), "trash.json")

    data class Entry(
        val id: String,
        val name: String,
        val origPath: String,
        val size: Long,
        val time: Long,
        val isDir: Boolean
    ) {
        fun toItem(ctx: Context): FileItem {
            val f = File(dir(ctx), id)
            val mime = TypeDetect.mimeOf(name)
            return FileItem(
                path = f.absolutePath, name = name, size = size, modified = time,
                isDir = isDir, mime = mime, id = id, extra = origPath
            )
        }
    }

    fun list(ctx: Context): List<Entry> {
        val f = metaFile(ctx)
        if (!f.exists()) return emptyList()
        val out = ArrayList<Entry>()
        try {
            val arr = JSONArray(f.readText())
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                out.add(
                    Entry(
                        id = o.optString("id"),
                        name = o.optString("name"),
                        origPath = o.optString("orig"),
                        size = o.optLong("size"),
                        time = o.optLong("time"),
                        isDir = o.optBoolean("isDir")
                    )
                )
            }
        } catch (_: Exception) {}
        return out.sortedByDescending { it.time }
    }

    private fun save(ctx: Context, list: List<Entry>) {
        val arr = JSONArray()
        list.forEach { e ->
            arr.put(
                JSONObject()
                    .put("id", e.id)
                    .put("name", e.name)
                    .put("orig", e.origPath)
                    .put("size", e.size)
                    .put("time", e.time)
                    .put("isDir", e.isDir)
            )
        }
        try { metaFile(ctx).writeText(arr.toString()) } catch (_: Exception) {}
    }

    /** 把文件移入回收站 */
    fun moveToTrash(ctx: Context, src: File): Boolean {
        if (!src.exists()) return false
        val list = list(ctx).toMutableList()
        val id = "t_" + System.currentTimeMillis() + "_" + (Math.random() * 1000).toInt() + (if (src.isDirectory) ".dir" else ".bin")
        val target = File(dir(ctx), id)
        val size = if (src.isDirectory) 0L else src.length()
        val moved = try {
            if (src.renameTo(target)) true
            else copyRecursive(src, target).also { if (it) deleteRecursive(src) }
        } catch (_: Throwable) { false }
        if (!moved) return false
        list.add(
            Entry(
                id = id, name = src.name, origPath = src.absolutePath,
                size = size, time = System.currentTimeMillis(), isDir = src.isDirectory
            )
        )
        save(ctx, list)
        return true
    }

    private fun copyRecursive(src: File, dst: File): Boolean = try {
        if (src.isDirectory) {
            dst.mkdirs()
            (src.listFiles() ?: emptyArray()).forEach { copyRecursive(it, File(dst, it.name)) }
            true
        } else {
            dst.parentFile?.mkdirs()
            src.inputStream().use { ins -> dst.outputStream().use { fos -> ins.copyTo(fos) } }
            true
        }
    } catch (_: Throwable) { false }

    private fun deleteRecursive(f: File) {
        if (f.isDirectory) f.listFiles()?.forEach { deleteRecursive(it) }
        f.delete()
    }

    /** 还原到原位置；返回 null 表示成功，否则返回失败原因 */
    fun restore(ctx: Context, entry: Entry): String? {
        val src = File(dir(ctx), entry.id)
        if (!src.exists()) {
            removeMeta(ctx, entry.id)
            return "回收站里的文件已经不在了"
        }
        val dest = File(entry.origPath)
        val parent = dest.parentFile
        if (parent == null) return "原路径无效：${entry.origPath}"
        try { parent.mkdirs() } catch (_: Throwable) {}
        if (!parent.canWrite()) {
            // 没有写入权限时仍尝试一次（有的机型 canWrite 判断不准）
        }
        val target = if (dest.exists()) uniqueFile(parent, dest.name) else dest
        var err = ""
        val ok = try {
            if (src.renameTo(target)) true
            else {
                copyRecursive(src, target).also { done ->
                    if (done) {
                        deleteRecursive(src)
                    } else {
                        // 直写被系统拦下时，走 MediaStore 兜底（Android 10+ 公共目录）
                        if (target.exists()) { try { target.delete() } catch (_: Throwable) {} }
                        if (copyViaMediaStore(ctx, src, entry.origPath, entry.name)) {
                            deleteRecursive(src)
                            true
                        } else false
                    }
                }
            }
        } catch (t: Throwable) {
            err = t.message ?: t.javaClass.simpleName
            false
        }
        if (ok) {
            removeMeta(ctx, entry.id)
            return null
        }
        return "还原失败" + if (err.isNotEmpty()) "：$err" else "（可能没有该位置写入权限）"
    }

    /** 通过 MediaStore 写入公共目录，绕开文件级写入限制 */
    private fun copyViaMediaStore(ctx: Context, src: File, origPath: String, name: String): Boolean {
        if (src.isDirectory) return false
        return try {
            val storageRoot = android.os.Environment.getExternalStorageDirectory().absolutePath
            val relDir = File(origPath).parentFile?.absolutePath?.removePrefix(storageRoot)?.trimStart('/') ?: ""
            val mime = TypeDetect.mimeOf(name).ifEmpty { "application/octet-stream" }
            val collection = when {
                relDir.startsWith("DCIM") -> MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                relDir.startsWith("Pictures") -> MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                relDir.startsWith("Movies") -> MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                relDir.startsWith("Music") -> MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
                relDir.startsWith("Download") && android.os.Build.VERSION.SDK_INT >= 29 -> MediaStore.Downloads.EXTERNAL_CONTENT_URI
                else -> return false
            }
            val values = android.content.ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                put(MediaStore.MediaColumns.MIME_TYPE, mime)
                if (android.os.Build.VERSION.SDK_INT >= 29) {
                    put(MediaStore.MediaColumns.RELATIVE_PATH, relDir.ifEmpty { "Download" })
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }
            }
            val uri = ctx.contentResolver.insert(collection, values) ?: return false
            ctx.contentResolver.openOutputStream(uri)?.use { out ->
                src.inputStream().use { ins -> ins.copyTo(out) }
            } ?: return false
            if (android.os.Build.VERSION.SDK_INT >= 29) {
                val done = android.content.ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }
                ctx.contentResolver.update(uri, done, null, null)
            }
            true
        } catch (_: Throwable) {
            false
        }
    }

    fun uniqueFile(dir: File, name: String): File {
        var f = File(dir, name)
        if (!f.exists()) return f
        val base = name.substringBeforeLast('.', name)
        val ext = if (name.contains('.')) "." + name.substringAfterLast('.') else ""
        var i = 1
        while (f.exists()) {
            f = File(dir, "$base($i)$ext")
            i++
        }
        return f
    }

    fun deletePermanent(ctx: Context, entry: Entry) {
        val f = File(dir(ctx), entry.id)
        deleteRecursive(f)
        removeMeta(ctx, entry.id)
    }

    private fun removeMeta(ctx: Context, id: String) {
        val list = list(ctx).filterNot { it.id == id }
        save(ctx, list)
    }

    fun clear(ctx: Context) {
        list(ctx).forEach { deleteRecursive(File(dir(ctx), it.id)) }
        save(ctx, emptyList())
    }

    fun totalSize(ctx: Context): Long = list(ctx).sumOf { if (it.isDir) 0L else it.size }
}
