package com.luogen.filehub.data

import com.github.junrar.Archive
import com.github.junrar.rarfile.FileHeader
import java.io.File
import java.io.FileOutputStream

/** junrar 包一层，避免上层直接依赖其 API 细节 */
object Junrar {

    data class Entry(val name: String, val size: Long, val isDir: Boolean)

    private fun nameOf(fh: FileHeader): String {
        val n = try { fh.fileNameString } catch (_: Throwable) { fh.fileName }
        return n ?: "unknown"
    }

    fun list(file: File): List<Entry> {
        val out = ArrayList<Entry>()
        val archive = Archive(file)
        try {
            var fh: FileHeader? = archive.nextFileHeader()
            while (fh != null) {
                out.add(Entry(nameOf(fh), fh.fullUnpackSize.coerceAtLeast(0), fh.isDirectory))
                fh = archive.nextFileHeader()
            }
        } finally {
            try { archive.close() } catch (_: Throwable) {}
        }
        return out
    }

    fun extract(file: File, destDir: File, onBytes: (String, Long) -> Unit): Int {
        var count = 0
        val archive = Archive(file)
        try {
            var fh: FileHeader? = archive.nextFileHeader()
            while (fh != null) {
                val name = nameOf(fh)
                val clean = name.replace('\\', '/').trimStart('/')
                val target = File(destDir, clean)
                if (fh.isDirectory) {
                    target.mkdirs()
                } else {
                    target.parentFile?.mkdirs()
                    FileOutputStream(target).use { fos ->
                        archive.extractFile(fh, fos)
                    }
                    count++
                    onBytes(clean, target.length())
                }
                fh = archive.nextFileHeader()
            }
        } finally {
            try { archive.close() } catch (_: Throwable) {}
        }
        return count
    }
}
