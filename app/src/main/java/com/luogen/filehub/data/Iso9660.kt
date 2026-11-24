package com.luogen.filehub.data

import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile

data class IsoEntry(
    val path: String,
    val size: Long,
    val isDir: Boolean,
    val lba: Long,
    val dataLen: Long
)

/** 轻量 ISO9660 / Joliet 读取器，用于浏览与解压 .iso 镜像 */
class Iso9660(private val file: File) {

    private val raf = RandomAccessFile(file, "r")
    private val sectorSize = 2048
    private var joliet = false
    private var rootLba = 0L
    private var rootLen = 0L

    init {
        parseVolume()
    }

    private fun u8(b: Byte): Int = b.toInt() and 0xFF
    private fun u32le(b: ByteArray, off: Int): Long =
        (b[off].toLong() and 0xFF) or ((b[off + 1].toLong() and 0xFF) shl 8) or
            ((b[off + 2].toLong() and 0xFF) shl 16) or ((b[off + 3].toLong() and 0xFF) shl 24)

    private fun parseVolume() {
        var primarySet = false
        var jolietSet = false
        for (s in 16..32) {
            val buf = ByteArray(sectorSize)
            raf.seek(s.toLong() * sectorSize)
            val n = raf.read(buf)
            if (n < sectorSize) break
            val type = u8(buf[0])
            val id = String(buf, 1, 5, Charsets.US_ASCII)
            if (id != "CD001") break
            if (type == 1 && !primarySet) {
                rootLba = u32le(buf, 156 + 2)
                rootLen = u32le(buf, 156 + 10)
                primarySet = true
            }
            if (type == 2) {
                val esc = "${u8(buf[88]).toChar()}${u8(buf[89]).toChar()}${u8(buf[90]).toChar()}"
                if (esc.startsWith("%") || esc.startsWith("/")) {
                    rootLba = u32le(buf, 156 + 2)
                    rootLen = u32le(buf, 156 + 10)
                    jolietSet = true
                }
            }
            if (type == 255) break
        }
        if (jolietSet) joliet = true else if (!primarySet) throw IllegalArgumentException("不是有效的 ISO 镜像")
    }

    private fun readDirData(lba: Long, len: Long): ByteArray {
        val out = ByteArray(len.toInt().coerceAtLeast(0))
        raf.seek(lba * sectorSize)
        var read = 0
        while (read < out.size) {
            val n = raf.read(out, read, out.size - read)
            if (n <= 0) break
            read += n
        }
        return out
    }

    private fun decodeName(buf: ByteArray, off: Int, len: Int): String {
        if (joliet) {
            val sb = StringBuilder()
            var i = 0
            while (i + 1 < len) {
                val c = ((buf[off + i].toInt() and 0xFF) shl 8) or (buf[off + i + 1].toInt() and 0xFF)
                if (c != 0) sb.append(c.toChar())
                i += 2
            }
            return sb.toString()
        }
        return String(buf, off, len, Charsets.US_ASCII)
    }

    fun listAll(): List<IsoEntry> {
        val out = ArrayList<IsoEntry>()
        val visited = HashSet<Long>()
        walk(rootLba, rootLen, "", out, visited, 0)
        return out
    }

    private fun walk(lba: Long, len: Long, prefix: String, out: MutableList<IsoEntry>, visited: MutableSet<Long>, depth: Int) {
        if (depth > 12 || !visited.add(lba)) return
        val data = readDirData(lba, len)
        var pos = 0
        val children = ArrayList<Triple<String, Triple<Long, Long, Boolean>, Long>>()
        while (pos + 33 < data.size) {
            val recLen = u8(data[pos])
            if (recLen == 0) {
                // 跳到下一个扇区边界
                val next = ((pos / sectorSize) + 1) * sectorSize
                if (next >= data.size) break
                pos = next
                continue
            }
            if (pos + recLen > data.size) break
            val ext = u32le(data, pos + 2)
            val dLen = u32le(data, pos + 10)
            val flags = u8(data[pos + 25])
            val nameLen = u8(data[pos + 32])
            val nameStart = pos + 33
            if (nameStart + nameLen <= data.size) {
                var name = decodeName(data, nameStart, nameLen)
                if (name != "\u0000" && name != "\u0001") {
                    name = name.substringBefore(';')
                    if (name.endsWith(".")) name = name.dropLast(1)
                    if (name.isNotEmpty()) {
                        val isDir = (flags and 0x02) != 0
                        children.add(Triple(name, Triple(ext, dLen, isDir), dLen))
                    }
                }
            }
            pos += recLen
        }
        children.forEach { (name, info, _) ->
            val ext = info.first
            val dLen = info.second
            val isDir = info.third
            val path = if (prefix.isEmpty()) name else "$prefix/$name"
            out.add(IsoEntry(path, if (isDir) 0L else dLen, isDir, ext, dLen))
            if (isDir) walk(ext, dLen, path, out, visited, depth + 1)
        }
    }

    fun extractTo(entry: IsoEntry, target: File) {
        raf.seek(entry.lba * sectorSize)
        FileOutputStream(target).use { fos ->
            var left = entry.dataLen
            val buf = ByteArray(64 * 1024)
            while (left > 0) {
                val want = minOf(left, buf.size.toLong()).toInt()
                val n = raf.read(buf, 0, want)
                if (n <= 0) break
                fos.write(buf, 0, n)
                left -= n
            }
        }
    }

    fun close() {
        try { raf.close() } catch (_: Throwable) {}
    }
}
