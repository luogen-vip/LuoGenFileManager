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
        val moved = try {
            if (src.renameTo(target)) true
            else copyRecursive(src, target).also { if (it) deleteRecursive(src) }
        } catch (_: Throwable) { false }
        if (!moved) return false
        val size = if (src.isDirectory) 0L else src.length()
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

    fun restore(ctx: Context, entry: Entry): Boolean {
        val src = File(dir(ctx), entry.id)
        if (!src.exists()) {
            removeMeta(ctx, entry.id)
            return false
        }
        val dest = File(entry.origPath)
        val parent = dest.parentFile
        parent?.mkdirs()
        val target = if (dest.exists()) uniqueFile(parent, entry.name) else dest
        val ok = try {
            if (src.renameTo(target)) true
            else copyRecursive(src, target).also { if (it) deleteRecursive(src) }
        } catch (_: Throwable) { false }
        if (ok) removeMeta(ctx, entry.id)
        return ok
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
