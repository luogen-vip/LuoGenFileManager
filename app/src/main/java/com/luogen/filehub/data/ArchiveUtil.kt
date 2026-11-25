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
import org.apache.commons.compress.compressors.brotli.BrotliCompressorInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream
import org.apache.commons.compress.compressors.lz4.FramedLZ4CompressorInputStream
import org.apache.commons.compress.compressors.lzma.LZMACompressorInputStream
import org.apache.commons.compress.compressors.z.ZCompressorInputStream
import org.apache.commons.compress.compressors.xz.XZCompressorInputStream
import io.airlift.compress.zstd.ZstdInputStream
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.RandomAccessFile
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
    private val ZSTD = setOf("zst", "tzst", "zstd")
    private val LZ4 = setOf("lz4", "tlz4")
    private val BROTLI = setOf("br", "tbr")
    private val ZLIKE = setOf("z")

    /** tar.gz / tar.bz2 / tar.xz 等两段后缀归一成底层压缩算法，方便复用解压流程 */
    private fun normalizeExt(e: String): String = when (e) {
        "tar.gz" -> "gz"
        "tar.bz2" -> "bz2"
        "tar.xz" -> "xz"
        "tar.lzma" -> "lzma"
        "tar.zst" -> "zst"
        "tar.lz4" -> "lz4"
        "tar.br" -> "br"
        "tar.z" -> "z"
        else -> e
    }

    /** 后缀不可靠时用文件头兜底：没有后缀/被改名的 zip、7z、rar 也能解 */
    private fun effExt(file: File): String {
        val e = normalizeExt(extOf(file.name))
        if (e in ZIPLIKE || e in RARLIKE || e in SEVENZ || e in TARLIKE || e in GZLIKE || e in BZ2LIKE ||
            e in XZLIKE || e in ZSTD || e in LZ4 || e in BROTLI || e in ISOLIKE ||
            e in CPIO || e in ARLIKE || e in LZMALIKE || e in ZLIKE
        ) return e
        val m = try { TypeDetect.detectByMagic(file) } catch (_: Throwable) { null }
        return when (m) {
            "zip" -> "zip"; "rar" -> "rar"; "7z" -> "7z"; "gz" -> "gz"; "bz2" -> "bz2"; "xz" -> "xz"
            "zst" -> "zst"; "lz4" -> "lz4"; "iso" -> "iso"
            else -> e
        }
    }

    fun isSupported(file: File): Boolean {
        if (splitGroupOf(file) != null) return true
        val e = effExt(file)
        return e in ZIPLIKE || e in RARLIKE || e in SEVENZ || e in TARLIKE ||
            e in GZLIKE || e in BZ2LIKE || e in XZLIKE || e in ZSTD || e in LZ4 || e in BROTLI ||
            e in ISOLIKE || e in CPIO || e in ARLIKE || e in LZMALIKE || e in ZLIKE
    }

    fun formatName(file: File): String {
        val g = splitGroupOf(file)
        if (g != null) return "${splitLabel(g)} · ${g.parts.size} 卷"
        return extOf(file.name).uppercase().ifEmpty { "未知" }
    }

    // ---------------- 列表 ----------------

    fun list(file: File): ArchiveResult {
        splitGroupOf(file)?.let { return listSplit(file, it) }
        val e = effExt(file)
        return try {
            when {
                e in ZIPLIKE -> listZip(file)
                e in SEVENZ -> list7z(file)
                e in TARLIKE -> listTar(file)
                e in GZLIKE || e in BZ2LIKE || e in XZLIKE || e in ZSTD || e in LZ4 || e in BROTLI -> listCompressed(file)
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
            if (e in GZLIKE || e in BZ2LIKE || e in XZLIKE || e in ZSTD || e in LZ4 || e in BROTLI) {
                val name = file.name.substringBeforeLast('.')
                if (name.endsWith(".tar", true) || e == "tgz" || e == "tbz" || e == "txz" ||
                    e == "tzst" || e == "tlz4" || e == "tbr") {
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
                "zst", "tzst", "zstd" -> ZstdInputStream(raw)
                "lz4", "tlz4" -> FramedLZ4CompressorInputStream(raw)
                "br", "tbr" -> BrotliCompressorInputStream(raw)
                else -> null
            }
        } catch (t: Throwable) {
            try { raw.close() } catch (_: Throwable) {}
            throw t
        }
    }

    // ---------------- 分卷（.001/.002/...） ----------------

    private data class SplitGroup(val parts: List<File>, val baseName: String) {
        val first: File get() = parts.first()
        val total: Long get() = parts.sumOf { p -> try { p.length() } catch (_: Throwable) { 0L } }
    }

    private val SPLIT_NAME = Regex("^(.+)\\.(\\d{3})$")

    /** 找到同一目录里连续的 .001/.002/... 分卷；点 .002 也会回到整组分卷 */
    private fun splitGroupOf(file: File): SplitGroup? {
        if (!file.isFile) return null
        val m = SPLIT_NAME.matchEntire(file.name) ?: return null
        val base = m.groupValues[1]
        val dir = file.parentFile ?: return null
        val start = if (File(dir, "$base.000").isFile) 0 else 1
        val parts = ArrayList<File>()
        var i = start
        while (i <= 999) {
            val f = File(dir, "$base.${"%03d".format(i)}")
            if (!f.isFile) break
            parts.add(f)
            i++
        }
        if (parts.size < 2) return null
        return SplitGroup(parts, base)
    }

    private fun splitKind(g: SplitGroup): String {
        val inner = extOf(g.baseName).lowercase()
        if (inner.isNotEmpty()) {
            val n = normalizeExt(inner)
            if (n in ZIPLIKE || n in RARLIKE || n in SEVENZ || n in TARLIKE || n in GZLIKE ||
                n in BZ2LIKE || n in XZLIKE || n in ZSTD || n in LZ4 || n in BROTLI ||
                n in ISOLIKE || n in CPIO || n in ARLIKE || n in LZMALIKE || n in ZLIKE
            ) return n
            if (inner in setOf("esd", "wim", "img", "bin", "dmg", "qcow2", "vmdk", "vhd", "vhdx", "exe", "elf")) {
                return inner
            }
            if (inner == "tgz" || inner == "tbz" || inner == "txz" || inner == "tzst" ||
                inner == "tlz4" || inner == "tbr") {
                return when (inner) {
                    "tgz" -> "gz"; "tbz" -> "bz2"; "txz" -> "xz"
                    "tzst" -> "zst"; "tlz4" -> "lz4"; else -> "br"
                }
            }
        }
        return magicKind(g.first) ?: "raw"
    }

    private fun magicKind(f: File): String? {
        val b = bytesAt(f, 0, 512) ?: return null
        fun ascii(from: Int, to: Int): String {
            val sb = StringBuilder()
            for (i in from until minOf(to, b.size)) {
                if (b[i].toInt() in 32..126) sb.append(b[i].toChar())
            }
            return sb.toString()
        }
        val m0 = b.getOrNull(0)?.toInt()?.and(0xFF) ?: -1
        val m1 = b.getOrNull(1)?.toInt()?.and(0xFF) ?: -1
        val m2 = b.getOrNull(2)?.toInt()?.and(0xFF) ?: -1
        val m3 = b.getOrNull(3)?.toInt()?.and(0xFF) ?: -1
        if (m0 == 0x50 && m1 == 0x4B) return "zip"
        if (ascii(0, 4) == "Rar!") return "rar"
        if (m0 == 0x37 && m1 == 0x7A && m2 == 0xBC && m3 == 0xAF) return "7z"
        if (m0 == 0x1F && m1 == 0x8B) return "gz"
        if (ascii(0, 3) == "BZh") return "bz2"
        if (m0 == 0xFD && m1 == 0x37 && m2 == 0x7A && m3 == 0x58 && b.getOrNull(4)?.toInt()?.and(0xFF) == 0x5A) return "xz"
        if (m0 == 0x28 && m1 == 0xB5 && m2 == 0x2F && m3 == 0xFD) return "zst"
        if (m0 == 0x04 && m1 == 0x22 && m2 == 0x4D && m3 == 0x18) return "lz4"
        if (b.size > 262 && ascii(257, 262) == "ustar") return "tar"
        if (ascii(0, 5) == "MSWIM" || ascii(0, 4) == "MSCW" || ascii(0, 4) == "WLPW") return "wim"
        if (ascii(0, 3) == "QFI") return "qcow2"
        if (ascii(0, 4) == "KDMV" || ascii(0, 4) == "COWD") return "vmdk"
        if (m0 == 0x7F && ascii(1, 4) == "ELF") return "elf"
        if (m0 == 0x4D && m1 == 0x5A) return "exe"
        val iso = bytesAt(f, 32769, 5)
        if (iso != null && iso.size >= 5) {
            val s = iso.map { it.toInt().toChar() }.joinToString("")
            if (s == "CD001") return "iso"
        }
        return null
    }

    private fun bytesAt(f: File, off: Long, len: Int): ByteArray? {
        return try {
            RandomAccessFile(f, "r").use { raf ->
                raf.seek(off)
                val out = ByteArray(len)
                val n = raf.read(out)
                if (n <= 0) null else out.copyOf(n)
            }
        } catch (_: Throwable) { null }
    }

    private fun splitStreaming(kind: String) = kind in TARLIKE || kind in GZLIKE || kind in BZ2LIKE ||
        kind in XZLIKE || kind in ZSTD || kind in LZ4 || kind in BROTLI

    private fun splitRandom(kind: String) = kind in ZIPLIKE || kind in RARLIKE || kind in SEVENZ || kind in ISOLIKE

    private fun splitInnerName(g: SplitGroup, kind: String): String {
        val n = g.baseName
        if (kind in TARLIKE) return n
        for (s in listOf(".tar.gz", ".tar.bz2", ".tar.xz", ".tar.zst", ".tar.lz4", ".tar.br")) {
            if (n.endsWith(s, true)) return n.dropLast(s.length) + ".tar"
        }
        return when {
            n.endsWith(".tgz", true) || n.endsWith(".tbz", true) || n.endsWith(".tbz2", true) ||
                n.endsWith(".txz", true) || n.endsWith(".tzst", true) || n.endsWith(".tlz4", true) ||
                n.endsWith(".tbr", true) -> n.substringBeforeLast('.') + ".tar"
            else -> n.substringBeforeLast('.', n)
        }.ifEmpty { n }
    }

    private fun splitLabel(g: SplitGroup): String {
        val kind = splitKind(g)
        val ext = extOf(g.baseName).uppercase()
        return when {
            kind == "iso" -> "ISO 光盘镜像分卷"
            kind == "esd" -> "Windows ESD 系统镜像分卷"
            kind == "wim" -> "Windows WIM/ESD 系统镜像分卷"
            kind == "zip" -> "ZIP 压缩包分卷"
            kind == "rar" -> "RAR 压缩包分卷"
            kind == "7z" -> "7Z 压缩包分卷"
            kind == "tar" -> "TAR 归档分卷"
            kind == "gz" -> if (ext == "TGZ" || ext == "TAR.GZ") "TAR.GZ 压缩包分卷" else "GZIP 压缩包分卷"
            kind == "bz2" -> if (ext == "TBZ" || ext == "TBZ2" || ext == "TAR.BZ2") "TAR.BZ2 压缩包分卷" else "BZIP2 压缩包分卷"
            kind == "xz" -> if (ext == "TXZ" || ext == "TAR.XZ") "TAR.XZ 压缩包分卷" else "XZ 压缩包分卷"
            kind == "zst" -> if (ext == "TAR.ZST") "TAR.ZST 压缩包分卷" else "Zstandard 压缩包分卷"
            kind == "lz4" -> if (ext == "TAR.LZ4") "TAR.LZ4 压缩包分卷" else "LZ4 压缩包分卷"
            kind == "br" -> if (ext == "TAR.BR") "TAR.BR 压缩包分卷" else "Brotli 压缩包分卷"
            kind == "img" -> "磁盘镜像分卷"
            kind == "qcow2" -> "QEMU 虚拟磁盘分卷"
            kind == "vmdk" -> "VMware 虚拟磁盘分卷"
            kind == "vhd" || kind == "vhdx" -> "虚拟硬盘分卷"
            kind == "dmg" -> "macOS 磁盘映像分卷"
            kind == "exe" -> "Windows 程序分卷"
            kind == "elf" -> "Linux 可执行文件分卷"
            kind == "bin" -> "二进制数据分卷"
            else -> "分卷 / 拆分文件"
        }
    }

    private fun splitOutputName(g: SplitGroup, kind: String): String {
        val base = g.baseName
        if (extOf(base).isNotEmpty()) return base
        val suffix = when (kind) {
            "iso" -> ".iso"; "esd" -> ".esd"; "wim" -> ".wim"; "img" -> ".img"
            "qcow2" -> ".qcow2"; "vmdk" -> ".vmdk"; "vhd" -> ".vhd"; "vhdx" -> ".vhdx"
            "dmg" -> ".dmg"; "exe" -> ".exe"; "elf" -> ""; else -> ".bin"
        }
        return base + suffix
    }

    private fun uniqueOutput(dir: File, name: String): File {
        var out = File(dir, name)
        if (!out.exists()) return out
        val dot = name.lastIndexOf('.')
        val stem = if (dot > 0) name.substring(0, dot) else name
        val ext = if (dot > 0) name.substring(dot) else ""
        var i = 1
        while (out.exists()) {
            out = File(dir, "$stem ($i)$ext")
            i++
        }
        return out
    }

    private fun hasEnoughSpace(dir: File, need: Long): Boolean {
        return try { dir.usableSpace > need + 8L * 1024 * 1024 } catch (_: Throwable) { true }
    }

    private fun tempExt(g: SplitGroup, kind: String): String {
        val baseExt = extOf(g.baseName)
        return when (kind) {
            "zip" -> "zip"; "rar" -> "rar"; "7z" -> "7z"; "iso" -> "iso"
            else -> if (baseExt.isNotEmpty()) baseExt else kind
        }
    }

    private fun mergeGroupTo(
        g: SplitGroup, out: File, onProgress: (Long, Long, String) -> Unit
    ) {
        val total = g.total
        var done = 0L
        try {
            FileOutputStream(out).use { fos ->
                val buf = ByteArray(128 * 1024)
                for (p in g.parts) {
                    FileInputStream(p).use { ins ->
                        while (true) {
                            val r = ins.read(buf)
                            if (r <= 0) break
                            fos.write(buf, 0, r)
                            done += r
                            onProgress(done, total, p.name)
                        }
                    }
                }
            }
        } catch (t: Throwable) {
            try { out.delete() } catch (_: Throwable) {}
            throw t
        }
    }

    private fun mergeGroupTemp(
        g: SplitGroup, kind: String,
        onProgress: (Long, Long, String) -> Unit = { _, _, _ -> }
    ): File {
        val dir = g.first.parentFile ?: throw IOException("找不到分卷所在目录")
        if (!hasEnoughSpace(dir, g.total)) throw IOException("空间不足：合并这组分卷需要约 ${g.total / 1024 / 1024} MB 临时空间")
        val tmp = File(dir, ".lgfm_split_${System.nanoTime()}.${tempExt(g, kind)}")
        mergeGroupTo(g, tmp, onProgress)
        return tmp
    }

    private fun listSplit(file: File, g: SplitGroup): ArchiveResult {
        val kind = splitKind(g)
        return try {
            when {
                splitStreaming(kind) -> listSplitStream(g, kind)
                splitRandom(kind) -> {
                    val tmp = mergeGroupTemp(g, kind)
                    try { list(tmp) } finally { try { tmp.delete() } catch (_: Throwable) {} }
                }
                else -> ArchiveResult(
                    true,
                    entries = listOf(ArchiveEntry(splitOutputName(g, kind), g.total, false)),
                    count = 1,
                    message = "这是 ${splitLabel(g)}，共 ${g.parts.size} 卷；解压时会自动合并"
                )
            }
        } catch (t: Throwable) {
            ArchiveResult(false, "读取分卷失败：${t.message ?: t.javaClass.simpleName}")
        }
    }

    private fun openSplitStream(g: SplitGroup): InputStream = BufferedInputStream(VolumeInputStream(g.parts))

    private fun wrapSplitStream(raw: InputStream, kind: String): InputStream {
        val ins = when (kind) {
            "gz" -> GzipCompressorInputStream(raw, true)
            "bz2" -> BZip2CompressorInputStream(raw, true)
            "xz" -> XZCompressorInputStream(raw)
            "zst" -> ZstdInputStream(raw)
            "lz4" -> FramedLZ4CompressorInputStream(raw)
            "br" -> BrotliCompressorInputStream(raw)
            else -> raw
        }
        return ins
    }

    private fun listSplitStream(g: SplitGroup, kind: String): ArchiveResult {
        val inner = splitInnerName(g, kind)
        val ins = wrapSplitStream(openSplitStream(g), kind)
        val isTar = kind in TARLIKE || inner.endsWith(".tar", true)
        if (isTar) {
            val entries = ArrayList<ArchiveEntry>()
            ins.use { stream ->
                TarArchiveInputStream(stream).use { tis ->
                    var t: TarArchiveEntry? = tis.nextEntry
                    while (t != null) {
                        entries.add(ArchiveEntry(t.name, t.size.coerceAtLeast(0), t.isDirectory))
                        t = tis.nextEntry
                    }
                }
            }
            return ArchiveResult(true, entries = entries, count = entries.size)
        }
        ins.close()
        return ArchiveResult(
            true,
            entries = listOf(ArchiveEntry(inner, g.total, false)),
            count = 1,
            message = "这是 ${splitLabel(g)}，共 ${g.parts.size} 卷；解压时会自动合并"
        )
    }

    private fun extractSplit(
        file: File, g: SplitGroup, destDir: File,
        onProgress: (Long, Long, String) -> Unit
    ): ArchiveResult {
        val kind = splitKind(g)
        return try {
            if (!destDir.exists()) destDir.mkdirs()
            when {
                splitRandom(kind) -> {
                    val tmp = mergeGroupTemp(g, kind, onProgress)
                    try { extract(tmp, destDir, onProgress = onProgress) }
                    finally { try { tmp.delete() } catch (_: Throwable) {} }
                }
                splitStreaming(kind) -> {
                    val n = extractSplitStream(g, kind, destDir, onProgress)
                    ArchiveResult(true, "分卷解压完成，共 $n 个文件", count = n)
                }
                else -> {
                    val out = uniqueOutput(destDir, splitOutputName(g, kind))
                    if (!hasEnoughSpace(destDir, g.total)) {
                        return ArchiveResult(false, "空间不足：合并这组分卷还需要约 ${g.total / 1024 / 1024} MB")
                    }
                    mergeGroupTo(g, out, onProgress)
                    ArchiveResult(
                        true,
                        "分卷合并完成：${out.name}（${splitLabel(g)}）",
                        count = 1
                    )
                }
            }
        } catch (t: Throwable) {
            ArchiveResult(false, "分卷处理失败：${t.message ?: t.javaClass.simpleName}")
        }
    }

    private fun extractSplitStream(
        g: SplitGroup, kind: String, dest: File,
        onProgress: (Long, Long, String) -> Unit
    ): Int {
        val inner = splitInnerName(g, kind)
        val ins = wrapSplitStream(openSplitStream(g), kind)
        val total = g.total
        val done = LongArray(1)
        if (kind in TARLIKE || inner.endsWith(".tar", true)) {
            return extractTar(ins, dest, total, done, onProgress)
        }
        ins.use { stream ->
            val target = safeTarget(dest, inner)
            target.parentFile?.mkdirs()
            FileOutputStream(target).use { fos ->
                val buf = ByteArray(64 * 1024)
                var r = stream.read(buf)
                while (r > 0) {
                    fos.write(buf, 0, r)
                    done[0] += r
                    onProgress(done[0], total, inner)
                    r = stream.read(buf)
                }
            }
        }
        return 1
    }

    private class VolumeInputStream(private val parts: List<File>) : InputStream() {
        private var idx = 0
        private var cur: InputStream? = null
        private fun ensure(): InputStream? {
            while (true) {
                val c = cur
                if (c != null) return c
                if (idx >= parts.size) return null
                cur = FileInputStream(parts[idx++])
            }
        }
        override fun read(): Int {
            val ins = ensure() ?: return -1
            val v = try { ins.read() } catch (_: Throwable) { -1 }
            if (v < 0) {
                try { ins.close() } catch (_: Throwable) {}
                cur = null
                return read()
            }
            return v
        }
        override fun read(b: ByteArray, off: Int, len: Int): Int {
            if (len == 0) return 0
            var ins = ensure() ?: return -1
            var r = try { ins.read(b, off, len) } catch (_: Throwable) { -1 }
            while (r < 0 && idx < parts.size) {
                try { ins.close() } catch (_: Throwable) {}
                cur = null
                ins = ensure() ?: return -1
                r = ins.read(b, off, len)
            }
            if (r < 0) {
                try { ins.close() } catch (_: Throwable) {}
                cur = null
            }
            return r
        }
        override fun close() {
            try { cur?.close() } catch (_: Throwable) {}
            cur = null
            idx = parts.size
        }
    }

    // ---------------- 解压 ----------------

    fun extract(
        file: File,
        destDir: File,
        skipBroken: Boolean = true,
        onProgress: (doneBytes: Long, totalBytes: Long, current: String) -> Unit = { _, _, _ -> }
    ): ArchiveResult {
        splitGroupOf(file)?.let { return extractSplit(file, it, destDir, onProgress) }
        val e = effExt(file)
        if (!destDir.exists()) destDir.mkdirs()
        val total = when {
            e in ZIPLIKE || e in SEVENZ || e in TARLIKE || e in RARLIKE || e in ISOLIKE || e in GZLIKE ||
                e in BZ2LIKE || e in XZLIKE || e in ZSTD || e in LZ4 || e in BROTLI ||
                e in CPIO || e in ARLIKE || e in LZMALIKE || e in ZLIKE -> estimateTotal(file)
            else -> file.length()
        }
        val done = LongArray(1)
        return try {
            val n = when {
                e in ZIPLIKE -> extractZip(file, destDir, skipBroken, total, done, onProgress)
                e in SEVENZ -> extract7z(file, destDir, total, done, onProgress)
                e in TARLIKE -> extractTar(FileInputStream(file), destDir, total, done, onProgress)
                e in GZLIKE -> extractGz(file, destDir, total, done, onProgress)
                e in BZ2LIKE || e in XZLIKE || e in ZSTD || e in LZ4 || e in BROTLI ->
                    extractSimpleCompressed(file, destDir, e, total, done, onProgress)
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
        if (inner.endsWith(".tar", true) || e == "tlz") {
            val raw = BufferedInputStream(FileInputStream(file))
            val stream: InputStream = when (e) {
                "lzma", "tlz" -> LZMACompressorInputStream(raw)
                "z" -> ZCompressorInputStream(raw)
                else -> raw
            }
            val entries = ArrayList<ArchiveEntry>()
            stream.use { ins ->
                TarArchiveInputStream(ins).use { tis ->
                    var t: TarArchiveEntry? = tis.nextEntry
                    while (t != null) {
                        entries.add(ArchiveEntry(t.name, t.size.coerceAtLeast(0), t.isDirectory))
                        t = tis.nextEntry
                    }
                }
            }
            return ArchiveResult(true, entries = entries, count = entries.size)
        }
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
        val isTar = e == "tbz" || e == "txz" || e == "tzst" || e == "tlz4" || e == "tbr" ||
            innerName.endsWith(".tar", true)
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
