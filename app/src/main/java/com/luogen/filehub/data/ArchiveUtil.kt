package com.luogen.filehub.data

import com.luogen.filehub.model.extOf
import org.apache.commons.compress.archivers.ar.ArArchiveEntry
import org.apache.commons.compress.archivers.ar.ArArchiveInputStream
import org.apache.commons.compress.archivers.cpio.CpioArchiveEntry
import org.apache.commons.compress.archivers.cpio.CpioArchiveInputStream
import org.apache.commons.compress.archivers.sevenz.SevenZArchiveEntry
import org.apache.commons.compress.archivers.sevenz.SevenZFile
import org.apache.commons.compress.archivers.sevenz.SevenZOutputFile
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream
import org.apache.commons.compress.compressors.lzma.LZMACompressorInputStream
import org.apache.commons.compress.compressors.z.ZCompressorInputStream
import org.apache.commons.compress.compressors.xz.XZCompressorInputStream
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

data class ArchiveEntry(val name: String, val size: Long, val isDir: Boolean)

data class ArchiveResult(
    val ok: Boolean,
    val message: String = "",
    val entries: List<ArchiveEntry> = emptyList(),
    val count: Int = 0
)

object ArchiveUtil {

    private val ZIPLIKE = setOf(
        "zip", "jar", "war", "ear", "aar", "apk", "apks", "aab", "apkm", "xapk", "ipa", "apkg",
        "whl", "crx", "vsix", "nupkg", "xpi", "oxt", "sb3", "love", "rpa", "hap", "hsp",
        "epub", "cbz", "cba", "docx", "xlsx", "pptx", "odt", "ods", "odp"
    )
    private val RARLIKE = setOf("rar", "cbr")
    private val SEVENZ = setOf("7z", "cb7")
    private val TARLIKE = setOf("tar", "cbt")
    private val GZLIKE = setOf("gz", "tgz")
    private val BZ2LIKE = setOf("bz2", "tbz", "tbz2")
    private val XZLIKE = setOf("xz", "txz")
    private val ISOLIKE = setOf("iso")
    private val CPIO = setOf("cpio")
    private val ARLIKE = setOf("ar", "deb")
    private val LZMALIKE = setOf("lzma", "tlz")
    private val ZLIKE = setOf("z")

    /** 后缀不可靠时用文件头兜底：没有后缀/被改名的 zip、7z、rar 也能解 */
    private fun effExt(file: File): String {
        val e = extOf(file.name)
        if (e in ZIPLIKE || e in RARLIKE || e in SEVENZ || e in TARLIKE || e in GZLIKE || e in BZ2LIKE ||
            e in XZLIKE || e in ISOLIKE || e in CPIO || e in ARLIKE || e in LZMALIKE || e in ZLIKE
        ) return e
        val m = try { TypeDetect.detectByMagic(file) } catch (_: Throwable) { null }
        return when (m) {
            "zip" -> "zip"; "rar" -> "rar"; "7z" -> "7z"; "gz" -> "gz"; "bz2" -> "bz2"; "xz" -> "xz"
            else -> e
        }
    }

    fun isSupported(file: File): Boolean {
        val e = effExt(file)
        return e in ZIPLIKE || e in RARLIKE || e in SEVENZ || e in TARLIKE ||
            e in GZLIKE || e in BZ2LIKE || e in XZLIKE || e in ISOLIKE ||
            e in CPIO || e in ARLIKE || e in LZMALIKE || e in ZLIKE
    }

    fun formatName(file: File): String = effExt(file).uppercase().ifEmpty { "未知" }

    // ---------------- 列表 ----------------

    fun list(file: File): ArchiveResult {
        val e = effExt(file)
        return try {
            when {
                e in ZIPLIKE -> listZip(file)
                e in SEVENZ -> list7z(file)
                e in TARLIKE -> listTar(file)
                e in GZLIKE || e in BZ2LIKE || e in XZLIKE -> listCompressed(file)
                e in RARLIKE -> listRar(file)
                e in ISOLIKE -> listIso(file)
                e in CPIO -> listCpio(file)
                e in ARLIKE -> listAr(file)
                e in LZMALIKE || e in ZLIKE -> listSingleStream(file, e)
                else -> ArchiveResult(false, "暂不支持直接浏览该压缩格式（${formatName(file)}）")
            }
        } catch (t: Throwable) {
            ArchiveResult(false, "读取失败：${t.message ?: t.javaClass.simpleName}")
        }
    }

    private fun listZip(file: File): ArchiveResult {
        val entries = ArrayList<ArchiveEntry>()
        ZipFile(file).use { zf ->
            val en = zf.entries()
            while (en.hasMoreElements()) {
                val z = en.nextElement()
                entries.add(ArchiveEntry(z.name, z.size.coerceAtLeast(0), z.isDirectory))
            }
        }
        return ArchiveResult(true, entries = entries, count = entries.size)
    }

    private fun list7z(file: File): ArchiveResult {
        val entries = ArrayList<ArchiveEntry>()
        val zf = SevenZFile.builder().setFile(file).get()
        try {
            var e: SevenZArchiveEntry? = zf.nextEntry
            while (e != null) {
                entries.add(ArchiveEntry(e.name, e.size.coerceAtLeast(0), e.isDirectory))
                e = zf.nextEntry
            }
        } finally {
            try { zf.close() } catch (_: Throwable) {}
        }
        return ArchiveResult(true, entries = entries, count = entries.size)
    }

    private fun listTar(file: File): ArchiveResult {
        val entries = ArrayList<ArchiveEntry>()
        FileInputStream(file).use { fis ->
            TarArchiveInputStream(BufferedInputStream(fis)).use { tis ->
                var e: TarArchiveEntry? = tis.nextEntry
                while (e != null) {
                    entries.add(ArchiveEntry(e.name, e.size.coerceAtLeast(0), e.isDirectory))
                    e = tis.nextEntry
                }
            }
        }
        return ArchiveResult(true, entries = entries, count = entries.size)
    }

    private fun listCompressed(file: File): ArchiveResult {
        val e = effExt(file)
        val stream = openCompressed(file, e) ?: return ArchiveResult(false, "无法打开压缩流")
        stream.use { ins ->
            // gz 里可能是 tar
            if (e in GZLIKE || e in BZ2LIKE || e in XZLIKE) {
                val name = file.name.substringBeforeLast('.')
                if (name.endsWith(".tar", true) || e == "tgz" || e == "tbz" || e == "txz") {
                    val entries = ArrayList<ArchiveEntry>()
                    TarArchiveInputStream(ins).use { tis ->
                        var t: TarArchiveEntry? = tis.nextEntry
                        while (t != null) {
                            entries.add(ArchiveEntry(t.name, t.size.coerceAtLeast(0), t.isDirectory))
                            t = tis.nextEntry
                        }
                    }
                    return ArchiveResult(true, entries = entries, count = entries.size)
                }
            }
            return ArchiveResult(
                true,
                entries = listOf(ArchiveEntry(file.name.substringBeforeLast('.'), file.length(), false)),
                count = 1
            )
        }
    }

    private fun listRar(file: File): ArchiveResult {
        val entries = ArrayList<ArchiveEntry>()
        Junrar.list(file).forEach { (name, size, isDir) ->
            entries.add(ArchiveEntry(name, size, isDir))
        }
        return ArchiveResult(true, entries = entries, count = entries.size)
    }

    private fun listIso(file: File): ArchiveResult {
        val entries = Iso9660(file).listAll()
        return ArchiveResult(
            true,
            entries = entries.map { ArchiveEntry(it.path, it.size, it.isDir) },
            count = entries.size
        )
    }

    private fun openCompressed(file: File, e: String): InputStream? {
        val raw = BufferedInputStream(FileInputStream(file))
        return try {
            when (e) {
                "gz", "tgz" -> GzipCompressorInputStream(raw, true)
                "bz2", "tbz", "tbz2" -> BZip2CompressorInputStream(raw, true)
                "xz", "txz" -> XZCompressorInputStream(raw)
                else -> null
            }
        } catch (t: Throwable) {
            try { raw.close() } catch (_: Throwable) {}
            throw t
        }
    }

    // ---------------- 解压 ----------------

    fun extract(
        file: File,
        destDir: File,
        skipBroken: Boolean = true,
        onProgress: (doneBytes: Long, totalBytes: Long, current: String) -> Unit = { _, _, _ -> }
    ): ArchiveResult {
        val e = effExt(file)
        if (!destDir.exists()) destDir.mkdirs()
        val total = when {
            e in ZIPLIKE || e in SEVENZ || e in TARLIKE || e in RARLIKE || e in ISOLIKE || e in GZLIKE ||
                e in BZ2LIKE || e in XZLIKE || e in CPIO || e in ARLIKE || e in LZMALIKE || e in ZLIKE -> estimateTotal(file)
            else -> file.length()
        }
        val done = LongArray(1)
        return try {
            val n = when {
                e in ZIPLIKE -> extractZip(file, destDir, skipBroken, total, done, onProgress)
                e in SEVENZ -> extract7z(file, destDir, total, done, onProgress)
                e in TARLIKE -> extractTar(FileInputStream(file), destDir, total, done, onProgress)
                e in GZLIKE -> extractGz(file, destDir, total, done, onProgress)
                e in BZ2LIKE || e in XZLIKE -> extractSimpleCompressed(file, destDir, e, total, done, onProgress)
                e in RARLIKE -> extractRar(file, destDir, total, done, onProgress)
                e in ISOLIKE -> extractIso(file, destDir, total, done, onProgress)
                e in CPIO -> extractCpio(file, destDir, total, done, onProgress)
                e in ARLIKE -> extractAr(file, destDir, total, done, onProgress)
                e in LZMALIKE || e in ZLIKE -> extractSingleStream(file, destDir, e, total, done, onProgress)
                else -> throw IllegalArgumentException("暂不支持解压该格式（${formatName(file)}）")
            }
            ArchiveResult(true, "解压完成，共 $n 个文件", count = n)
        } catch (t: Throwable) {
            ArchiveResult(false, "解压失败：${t.message ?: t.javaClass.simpleName}")
        }
    }

    /** 只解压其中一个条目（zip/7z 支持精确解压，其余格式回退为整体解压） */
    fun extractOne(file: File, entryName: String, destDir: File): ArchiveResult {
        return try {
            if (!destDir.exists()) destDir.mkdirs()
            val target = File(destDir, entryName)
            val e = effExt(file)
            if (e in ZIPLIKE) {
                ZipFile(file).use { zf ->
                    val z = zf.getEntry(entryName)
                        ?: return ArchiveResult(false, "压缩包里没有这个文件")
                    if (z.isDirectory) {
                        target.mkdirs()
                        return ArchiveResult(true, "已创建目录", count = 0)
                    }
                    target.parentFile?.mkdirs()
                    zf.getInputStream(z).use { ins ->
                        target.outputStream().use { outs -> ins.copyTo(outs) }
                    }
                }
                ArchiveResult(true, "已解压 $entryName", count = 1)
            } else {
                extract(file, destDir)
            }
        } catch (t: Throwable) {
            ArchiveResult(false, "解压失败：${t.message ?: t.javaClass.simpleName}")
        }
    }

    private fun listCpio(file: File): ArchiveResult {
        val entries = ArrayList<ArchiveEntry>()
        CpioArchiveInputStream(BufferedInputStream(FileInputStream(file))).use { cis ->
            var e: CpioArchiveEntry? = cis.nextEntry
            while (e != null) {
                entries.add(ArchiveEntry(e.name, e.size.coerceAtLeast(0), e.isDirectory))
                e = cis.nextEntry
            }
        }
        return ArchiveResult(true, entries = entries, count = entries.size)
    }

    private fun listAr(file: File): ArchiveResult {
        val entries = ArrayList<ArchiveEntry>()
        ArArchiveInputStream(BufferedInputStream(FileInputStream(file))).use { ais ->
            var e: ArArchiveEntry? = ais.nextEntry
            while (e != null) {
                entries.add(ArchiveEntry(e.name, e.length.coerceAtLeast(0), e.isDirectory))
                e = ais.nextEntry
            }
        }
        return ArchiveResult(true, entries = entries, count = entries.size)
    }

    private fun listSingleStream(file: File, e: String): ArchiveResult {
        val inner = innerNameOf(file, e)
        return ArchiveResult(true, entries = listOf(ArchiveEntry(inner, file.length(), false)), count = 1)
    }

    private fun innerNameOf(file: File, e: String): String {
        val n = file.name
        return when (e) {
            "tlz" -> n.removeSuffix(".tlz").removeSuffix(".tar") + ".tar"
            else -> n.substringBeforeLast('.', n)
        }.ifEmpty { n }
    }

    private fun extractCpio(
        file: File, dest: File, total: Long, done: LongArray,
        onProgress: (Long, Long, String) -> Unit
    ): Int {
        var count = 0
        CpioArchiveInputStream(BufferedInputStream(FileInputStream(file))).use { cis ->
            var e: CpioArchiveEntry? = cis.nextEntry
            val buf = ByteArray(64 * 1024)
            while (e != null) {
                val target = safeTarget(dest, e.name)
                if (e.isDirectory) target.mkdirs()
                else {
                    target.parentFile?.mkdirs()
                    FileOutputStream(target).use { fos ->
                        var r = cis.read(buf)
                        while (r > 0) {
                            fos.write(buf, 0, r)
                            done[0] += r
                            onProgress(done[0], total, e.name)
                            r = cis.read(buf)
                        }
                    }
                    count++
                }
                e = cis.nextEntry
            }
        }
        return count
    }

    private fun extractAr(
        file: File, dest: File, total: Long, done: LongArray,
        onProgress: (Long, Long, String) -> Unit
    ): Int {
        var count = 0
        ArArchiveInputStream(BufferedInputStream(FileInputStream(file))).use { ais ->
            var e: ArArchiveEntry? = ais.nextEntry
            val buf = ByteArray(64 * 1024)
            while (e != null) {
                val target = safeTarget(dest, e.name)
                target.parentFile?.mkdirs()
                FileOutputStream(target).use { fos ->
                    var r = ais.read(buf)
                    while (r > 0) {
                        fos.write(buf, 0, r)
                        done[0] += r
                        onProgress(done[0], total, e.name)
                        r = ais.read(buf)
                    }
                }
                count++
                e = ais.nextEntry
            }
        }
        return count
    }

    private fun extractSingleStream(
        file: File, dest: File, e: String, total: Long, done: LongArray,
        onProgress: (Long, Long, String) -> Unit
    ): Int {
        val raw = BufferedInputStream(FileInputStream(file))
        val stream: InputStream = when (e) {
            "lzma", "tlz" -> LZMACompressorInputStream(raw)
            "z" -> ZCompressorInputStream(raw)
            else -> raw
        }
        val inner = innerNameOf(file, e)
        if (inner.endsWith(".tar", true) || e == "tlz") {
            return extractTar(stream, dest, total, done, onProgress)
        }
        stream.use { ins ->
            val target = safeTarget(dest, inner)
            target.parentFile?.mkdirs()
            FileOutputStream(target).use { fos ->
                val buf = ByteArray(64 * 1024)
                var r = ins.read(buf)
                while (r > 0) {
                    fos.write(buf, 0, r)
                    done[0] += r
                    onProgress(done[0], total, inner)
                    r = ins.read(buf)
                }
            }
        }
        return 1
    }


    private fun estimateTotal(file: File): Long {
        return try {
            val e = effExt(file)
            when {
                e in ZIPLIKE -> {
                    var t = 0L
                    ZipFile(file).use { zf ->
                        val en = zf.entries()
                        while (en.hasMoreElements()) t += en.nextElement().size.coerceAtLeast(0)
                    }
                    t
                }
                e in SEVENZ -> {
                    var t = 0L
                    val zf = SevenZFile.builder().setFile(file).get()
                    try {
                        var x: SevenZArchiveEntry? = zf.nextEntry
                        while (x != null) { t += x.size.coerceAtLeast(0); x = zf.nextEntry }
                    } finally { try { zf.close() } catch (_: Throwable) {} }
                    t
                }
                else -> file.length() * 3
            }
        } catch (_: Throwable) {
            file.length() * 3
        }
    }

    private fun safeTarget(destDir: File, name: String): File {
        val clean = name.replace('\\', '/').trimStart('/')
        val out = File(destDir, clean)
        val canonDest = destDir.canonicalPath + File.separator
        if (!out.canonicalPath.startsWith(canonDest)) return File(destDir, out.name)
        return out
    }

    private fun extractZip(
        file: File, dest: File, skipBroken: Boolean, total: Long, done: LongArray,
        onProgress: (Long, Long, String) -> Unit
    ): Int {
        var count = 0
        ZipFile(file).use { zf ->
            val en = zf.entries()
            while (en.hasMoreElements()) {
                val z: ZipEntry = en.nextElement()
                val target = safeTarget(dest, z.name)
                if (z.isDirectory) {
                    target.mkdirs()
                    continue
                }
                target.parentFile?.mkdirs()
                try {
                    zf.getInputStream(z).use { ins ->
                        FileOutputStream(target).use { fos ->
                            val buf = ByteArray(64 * 1024)
                            while (true) {
                                val r = ins.read(buf)
                                if (r <= 0) break
                                fos.write(buf, 0, r)
                                done[0] += r
                                onProgress(done[0], total, z.name)
                            }
                        }
                    }
                    count++
                } catch (t: Throwable) {
                    if (!skipBroken) throw t
                }
            }
        }
        return count
    }

    private fun extract7z(
        file: File, dest: File, total: Long, done: LongArray,
        onProgress: (Long, Long, String) -> Unit
    ): Int {
        var count = 0
        val zf = SevenZFile.builder().setFile(file).get()
        try {
            var e: SevenZArchiveEntry? = zf.nextEntry
            val buf = ByteArray(64 * 1024)
            while (e != null) {
                val target = safeTarget(dest, e.name)
                if (e.isDirectory) {
                    target.mkdirs()
                } else {
                    target.parentFile?.mkdirs()
                    FileOutputStream(target).use { fos ->
                        while (true) {
                            val r = zf.read(buf)
                            if (r <= 0) break
                            fos.write(buf, 0, r)
                            done[0] += r
                            onProgress(done[0], total, e.name)
                        }
                    }
                    count++
                }
                e = zf.nextEntry
            }
        } finally {
            try { zf.close() } catch (_: Throwable) {}
        }
        return count
    }

    private fun extractTar(
        stream: InputStream, dest: File, total: Long, done: LongArray,
        onProgress: (Long, Long, String) -> Unit
    ): Int {
        var count = 0
        TarArchiveInputStream(stream).use { tis ->
            var t: TarArchiveEntry? = tis.nextEntry
            val buf = ByteArray(64 * 1024)
            while (t != null) {
                val target = safeTarget(dest, t.name)
                if (t.isDirectory) {
                    target.mkdirs()
                } else {
                    target.parentFile?.mkdirs()
                    FileOutputStream(target).use { fos ->
                        var r = tis.read(buf)
                        while (r > 0) {
                            fos.write(buf, 0, r)
                            done[0] += r
                            onProgress(done[0], total, t.name)
                            r = tis.read(buf)
                        }
                    }
                    count++
                }
                t = tis.nextEntry
            }
        }
        return count
    }

    private fun extractGz(
        file: File, dest: File, total: Long, done: LongArray,
        onProgress: (Long, Long, String) -> Unit
    ): Int {
        val innerName = file.name.removeSuffix(".gz").removeSuffix(".tgz")
        val isTar = file.name.endsWith(".tgz", true) || innerName.endsWith(".tar", true)
        GzipCompressorInputStream(BufferedInputStream(FileInputStream(file))).use { gz ->
            if (isTar) return extractTar(gz, dest, total, done, onProgress)
            val target = safeTarget(dest, innerName)
            target.parentFile?.mkdirs()
            FileOutputStream(target).use { fos ->
                val buf = ByteArray(64 * 1024)
                var r = gz.read(buf)
                while (r > 0) {
                    fos.write(buf, 0, r)
                    done[0] += r
                    onProgress(done[0], total, innerName)
                    r = gz.read(buf)
                }
            }
            return 1
        }
    }

    private fun extractSimpleCompressed(
        file: File, dest: File, e: String, total: Long, done: LongArray,
        onProgress: (Long, Long, String) -> Unit
    ): Int {
        val innerName = file.name.substringBeforeLast('.')
        val isTar = e == "tbz" || e == "txz" || innerName.endsWith(".tar", true)
        val ins = openCompressed(file, e) ?: return 0
        if (isTar) return extractTar(ins, dest, total, done, onProgress)
        ins.use { stream ->
            val target = safeTarget(dest, innerName)
            target.parentFile?.mkdirs()
            FileOutputStream(target).use { fos ->
                val buf = ByteArray(64 * 1024)
                var r = stream.read(buf)
                while (r > 0) {
                    fos.write(buf, 0, r)
                    done[0] += r
                    onProgress(done[0], total, innerName)
                    r = stream.read(buf)
                }
            }
            return 1
        }
    }

    private fun extractRar(
        file: File, dest: File, total: Long, done: LongArray,
        onProgress: (Long, Long, String) -> Unit
    ): Int {
        return Junrar.extract(file, dest) { name, bytes ->
            done[0] += bytes
            onProgress(done[0], total, name)
        }
    }

    private fun extractIso(
        file: File, dest: File, total: Long, done: LongArray,
        onProgress: (Long, Long, String) -> Unit
    ): Int {
        val iso = Iso9660(file)
        val entries = iso.listAll()
        var count = 0
        entries.forEach { ent ->
            if (!ent.isDir) {
                val target = safeTarget(dest, ent.path)
                target.parentFile?.mkdirs()
                iso.extractTo(ent, target)
                done[0] += ent.size
                onProgress(done[0], total, ent.path)
                count++
            } else {
                safeTarget(dest, ent.path).mkdirs()
            }
        }
        iso.close()
        return count
    }

    // ---------------- 压缩 ----------------

    fun compress(
        files: List<File>,
        out: File,
        format: String = "zip",
        onProgress: (doneBytes: Long, totalBytes: Long, current: String) -> Unit = { _, _, _ -> }
    ): ArchiveResult {
        val total = files.sumOf { if (it.isFile) it.length() else 0L }
        val done = LongArray(1)
        return try {
            when (format.lowercase()) {
                "7z" -> {
                    SevenZOutputFile(out).use { zof ->
                        files.forEach { f -> addTo7z(zof, f, f.name, total, done, onProgress) }
                    }
                }
                else -> {
                    ZipOutputStream(FileOutputStream(out)).use { zos ->
                        files.forEach { f -> addToZip(zos, f, f.name, total, done, onProgress) }
                    }
                }
            }
            ArchiveResult(true, "压缩完成：${out.name}", count = files.size)
        } catch (t: Throwable) {
            try { out.delete() } catch (_: Throwable) {}
            ArchiveResult(false, "压缩失败：${t.message ?: t.javaClass.simpleName}")
        }
    }

    private fun addToZip(
        zos: ZipOutputStream, f: File, entryName: String, total: Long, done: LongArray,
        onProgress: (Long, Long, String) -> Unit
    ) {
        if (f.isDirectory) {
            val children = f.listFiles() ?: return
            if (children.isEmpty()) {
                zos.putNextEntry(ZipEntry("$entryName/"))
                zos.closeEntry()
            } else {
                children.forEach { c -> addToZip(zos, c, "$entryName/${c.name}", total, done, onProgress) }
            }
            return
        }
        zos.putNextEntry(ZipEntry(entryName))
        FileInputStream(f).use { ins ->
            val buf = ByteArray(64 * 1024)
            var r = ins.read(buf)
            while (r > 0) {
                zos.write(buf, 0, r)
                done[0] += r
                onProgress(done[0], total, entryName)
                r = ins.read(buf)
            }
        }
        zos.closeEntry()
    }

    private fun addTo7z(
        zof: SevenZOutputFile, f: File, entryName: String, total: Long, done: LongArray,
        onProgress: (Long, Long, String) -> Unit
    ) {
        if (f.isDirectory) {
            (f.listFiles() ?: return).forEach { c -> addTo7z(zof, c, "$entryName/${c.name}", total, done, onProgress) }
            return
        }
        val entry = zof.createArchiveEntry(f, entryName)
        zof.putArchiveEntry(entry)
        FileInputStream(f).use { ins ->
            val buf = ByteArray(64 * 1024)
            var r = ins.read(buf)
            while (r > 0) {
                zof.write(buf, 0, r)
                done[0] += r
                onProgress(done[0], total, entryName)
                r = ins.read(buf)
            }
        }
        zof.closeArchiveEntry()
    }
}
