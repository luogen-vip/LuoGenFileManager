package com.luogen.filehub.data

import com.luogen.filehub.model.Cat
import com.luogen.filehub.model.extOf
import java.io.File
import java.io.RandomAccessFile

object TypeDetect {

    val IMAGE_EXT = setOf(
        "jpg", "jpeg", "png", "gif", "webp", "bmp", "heic", "heif", "avif",
        "tif", "tiff", "ico", "jfif", "dng", "raw", "cr2", "nef", "arw", "orf", "raf", "svg"
    )

    val VIDEO_EXT = setOf(
        "mp4", "mkv", "avi", "mov", "wmv", "flv", "webm", "m4v", "3gp", "ts",
        "rmvb", "rm", "mpg", "mpeg", "vob", "asf", "ogv", "m2ts", "f4v", "divx", "mts"
    )

    val AUDIO_EXT = setOf(
        "mp3", "wav", "flac", "aac", "m4a", "ogg", "oga", "opus", "wma", "amr",
        "ape", "aiff", "aif", "mid", "midi", "mka", "ac3", "dts", "m4b", "caf", "ra"
    )

    val DOC_EXT = setOf(
        "pdf", "txt", "md", "markdown", "doc", "docx", "xls", "xlsx", "ppt", "pptx",
        "csv", "tsv", "rtf", "odt", "ods", "odp", "epub", "mobi", "azw3", "json",
        "xml", "html", "htm", "log", "ini", "yaml", "yml", "toml", "conf", "cfg",
        "java", "kt", "py", "js", "ts", "css", "sh", "bat", "c", "cpp", "h", "go",
        "rs", "php", "sql", "properties", "gradle", "gitignore", "license"
    )

    val ARCHIVE_EXT = setOf(
        "zip", "rar", "7z", "tar", "gz", "tgz", "bz2", "tbz", "xz", "txz", "zst",
        "iso", "cab", "jar", "war", "apks", "lz", "lzma", "arj", "cpio", "whl", "crx"
    )

    val TEXT_EXT = setOf(
        "txt", "md", "markdown", "json", "xml", "html", "htm", "log", "ini", "yaml",
        "yml", "toml", "conf", "cfg", "java", "kt", "kts", "py", "js", "ts", "css",
        "sh", "bat", "c", "cpp", "h", "go", "rs", "php", "sql", "properties",
        "gradle", "csv", "tsv", "gitignore", "license", "readme", "srt", "vtt", "lrc"
    )

    fun catOf(fileName: String, mime: String = "", isDir: Boolean = false): Cat {
        if (isDir) return Cat.FILE
        val e = extOf(fileName)
        return when {
            e in IMAGE_EXT || mime.startsWith("image/") -> Cat.IMAGE
            e in VIDEO_EXT || mime.startsWith("video/") -> Cat.VIDEO
            e in AUDIO_EXT || mime.startsWith("audio/") -> Cat.AUDIO
            e in ARCHIVE_EXT || mime.contains("zip") || mime.contains("compressed") || mime.contains("x-7z") -> Cat.ARCHIVE
            e == "apk" || mime == "application/vnd.android.package-archive" -> Cat.APP
            e in DOC_EXT || mime.startsWith("text/") || mime.contains("pdf") ||
                mime.contains("word") || mime.contains("sheet") || mime.contains("presentation") ||
                mime.contains("officedocument") -> Cat.DOC
            else -> Cat.FILE
        }
    }

    private fun head(f: File, len: Int = 16): ByteArray {
        val out = ByteArray(len)
        try {
            RandomAccessFile(f, "r").use { raf ->
                val n = raf.read(out)
                if (n < len) return out.copyOf(n.coerceAtLeast(0))
            }
        } catch (_: Exception) {
        }
        return out
    }

    private fun ascii(b: ByteArray, from: Int, to: Int): String {
        val sb = StringBuilder()
        for (i in from until minOf(to, b.size)) {
            val c = b[i].toInt() and 0xFF
            if (c in 32..126) sb.append(c.toChar())
        }
        return sb.toString()
    }

    /** 通过文件头（magic bytes）识别真实类型，返回推测扩展名，识别不出返回 null */
    fun detectByMagic(f: File): String? {
        if (!f.isFile || f.length() < 8) return null
        val b = head(f, 16)
        fun m(i: Int) = b[i].toInt() and 0xFF

        // 图片
        if (m(0) == 0xFF && m(1) == 0xD8 && m(2) == 0xFF) return "jpg"
        if (m(0) == 0x89 && m(1) == 0x50 && m(2) == 0x4E && m(3) == 0x47) return "png"
        if (m(0) == 0x47 && m(1) == 0x49 && m(2) == 0x46) return "gif"
        if (m(0) == 0x42 && m(1) == 0x4D) return "bmp"
        if ((m(0) == 0x49 && m(1) == 0x49 && m(2) == 0x2A) || (m(0) == 0x4D && m(1) == 0x4D && m(3) == 0x2A)) return "tiff"
        // RIFF 家族
        if (m(0) == 0x52 && m(1) == 0x49 && m(2) == 0x46 && m(3) == 0x46) {
            val tag = ascii(b, 8, 12)
            return when (tag) {
                "WEBP" -> "webp"
                "WAVE" -> "wav"
                "AVI " -> "avi"
                "ACON" -> "ani"
                else -> null
            }
        }
        // ISO BMFF: [size][ftyp]
        if (ascii(b, 4, 8) == "ftyp") {
            val brand = ascii(b, 8, 12)
            return when {
                brand.startsWith("heic") || brand.startsWith("heix") || brand.startsWith("mif1") -> "heic"
                brand.startsWith("avif") -> "avif"
                brand.startsWith("qt") -> "mov"
                brand.startsWith("M4A") -> "m4a"
                brand.startsWith("3gp") -> "3gp"
                else -> "mp4"
            }
        }
        if (m(0) == 0x1A && m(1) == 0x45 && m(2) == 0xDF && m(3) == 0xA3) return "mkv"
        if (m(0) == 0x4F && m(1) == 0x67 && m(2) == 0x67 && m(3) == 0x53) return "ogg"
        if (ascii(b, 0, 4) == "fLaC") return "flac"
        if (ascii(b, 0, 3) == "ID3") return "mp3"
        // 文档
        if (ascii(b, 0, 4) == "%PDF") return "pdf"
        if (ascii(b, 0, 4) == "Rar!") return "rar"
        if (m(0) == 0x37 && m(1) == 0x7A && m(2) == 0xBC && m(3) == 0xAF) return "7z"
        if (m(0) == 0x50 && m(1) == 0x4B) {
            // zip 家族（可能是 docx/xlsx/apk）
            if (f.length() > 30) {
                val tail = StringBuilder()
                try {
                    RandomAccessFile(f, "r").use { raf ->
                        val len = 16
                        raf.seek(f.length() - len)
                        val buf = ByteArray(len)
                        raf.read(buf)
                        for (i in buf.indices) {
                            val c = buf[i].toInt() and 0xFF
                            if (c in 32..126) tail.append(c.toChar())
                        }
                    }
                } catch (_: Exception) {
                }
                // 无法可靠区分，默认 zip
            }
            return "zip"
        }
        if (m(0) == 0x1F && m(1) == 0x8B) return "gz"
        if (m(0) == 0x42 && m(1) == 0x5A && m(2) == 0x68) return "bz2"
        if (m(0) == 0xFD && m(1) == 0x37 && m(2) == 0x7A && m(3) == 0x58 && m(4) == 0x5A && m(5) == 0x00) return "xz"
        // 纯文本：前 64 字节可打印比例
        return if (looksText(f)) "txt" else null
    }

    fun looksText(f: File): Boolean {
        if (f.length() == 0L) return true
        if (f.length() > 8L * 1024 * 1024) return false
        return try {
            f.bufferedReader().use { r ->
                val sample = StringBuilder()
                var c = r.read()
                var n = 0
                while (c != -1 && n < 4096) {
                    sample.append(c.toChar())
                    c = r.read()
                    n++
                }
                val s = sample.toString()
                if (s.isEmpty()) return true
                val bad = s.count { it.code < 9 || (it.code in 14..31) }
                bad.toDouble() / s.length < 0.02
            }
        } catch (_: Exception) {
            false
        }
    }

    fun mimeOf(name: String): String {
        val e = extOf(name)
        return when (e) {
            "jpg", "jpeg", "jfif" -> "image/jpeg"
            "png" -> "image/png"
            "gif" -> "image/gif"
            "webp" -> "image/webp"
            "bmp" -> "image/bmp"
            "heic", "heif" -> "image/heic"
            "svg" -> "image/svg+xml"
            "mp4", "m4v" -> "video/mp4"
            "mkv" -> "video/x-matroska"
            "avi" -> "video/x-msvideo"
            "mov" -> "video/quicktime"
            "webm" -> "video/webm"
            "3gp" -> "video/3gpp"
            "mp3" -> "audio/mpeg"
            "wav" -> "audio/wav"
            "flac" -> "audio/flac"
            "aac", "m4a" -> "audio/mp4"
            "ogg", "opus" -> "audio/ogg"
            "pdf" -> "application/pdf"
            "txt", "md", "log" -> "text/plain"
            "json" -> "application/json"
            "xml" -> "text/xml"
            "html", "htm" -> "text/html"
            "apk" -> "application/vnd.android.package-archive"
            "zip" -> "application/zip"
            "rar" -> "application/x-rar-compressed"
            "7z" -> "application/x-7z-compressed"
            "doc" -> "application/msword"
            "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
            "xls" -> "application/vnd.ms-excel"
            "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
            "ppt" -> "application/vnd.ms-powerpoint"
            "pptx" -> "application/vnd.openxmlformats-officedocument.presentationml.presentation"
            else -> "application/octet-stream"
        }
    }

    fun isAudioLike(name: String): Boolean = extOf(name) in AUDIO_EXT
    fun isVideoLike(name: String): Boolean = extOf(name) in VIDEO_EXT
}
