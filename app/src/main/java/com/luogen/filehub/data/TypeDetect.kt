package com.luogen.filehub.data

import com.luogen.filehub.model.Cat
import com.luogen.filehub.model.extOf
import java.io.File
import java.io.RandomAccessFile

object TypeDetect {

    val IMAGE_EXT = setOf(
        "jpg", "jpeg", "png", "gif", "webp", "bmp", "heic", "heif", "avif",
        "tif", "tiff", "ico", "jfif", "dng", "raw", "cr2", "nef", "arw", "orf", "raf", "svg",
        "psd", "psb", "xcf", "tga", "webp2", "hdr", "exr", "jxl", "jp2", "j2k", "pbm", "pgm", "ppm", "xbm", "wbmp",
        "apng", "jpe", "cur", "ani", "icns", "heics", "avifs", "dds",
        "cr3", "crw", "dc2", "dcr", "k25", "kdc", "mos", "mrw", "pef", "srw", "x3f", "rw2", "erf", "mef", "iiq", "3fr",
        "pnm", "pcx", "sgi", "rgba", "dpx", "cin", "qoi", "hdr", "exr"
    )

    val VIDEO_EXT = setOf(
        "mp4", "mkv", "avi", "mov", "wmv", "flv", "webm", "m4v", "3gp", "ts",
        "rmvb", "rm", "mpg", "mpeg", "vob", "asf", "ogv", "m2ts", "f4v", "divx", "mts",
        "mxf", "m2v", "m1v", "3g2", "3gpp", "qt", "yuv", "h264", "h265", "hevc", "vp9", "av1", "ts",
        "m3u8", "mpd", "dv", "f4p", "ogm", "rmhd", "swf", "viv", "amv", "cavs",
        "mpv", "y4m", "ivf", "264", "265", "vp8", "vvc"
    )

    val AUDIO_EXT = setOf(
        "mp3", "wav", "flac", "aac", "m4a", "ogg", "oga", "opus", "wma", "amr",
        "ape", "aiff", "aif", "mid", "midi", "mka", "ac3", "dts", "m4b", "caf", "ra",
        "m4r", "amr-wb", "awb", "tta", "wv", "mpc", "spx", "gsm", "voc", "au", "snd", "3ga", "aob",
        "aifc", "mp4a", "dsf", "dff", "m3u", "pls", "xspf", "wpl", "cue", "kar", "smf", "mod", "xm", "it", "s3m",
        "eac3", "thd", "mlp", "m4b", "m4r"
    )

    val DOC_EXT = setOf(
        "pdf", "txt", "md", "markdown", "doc", "docx", "xls", "xlsx", "ppt", "pptx",
        "csv", "tsv", "rtf", "odt", "ods", "odp", "epub", "mobi", "azw3", "json",
        "xml", "html", "htm", "log", "ini", "yaml", "yml", "toml", "conf", "cfg",
        "java", "kt", "py", "js", "ts", "css", "sh", "bat", "c", "cpp", "h", "go",
        "rs", "php", "sql", "properties", "gradle", "gitignore", "license",
        "tex", "latex", "xps", "oxps", "djvu", "chm", "pages", "numbers", "key", "wps", "et", "dps",
        "uof", "srt", "vtt", "lrc", "ass", "ssa", "sub", "nfo", "rst", "textile", "org", "adoc",
        "env", "dockerfile", "makefile", "cmake", "mk", "babelrc", "editorconfig", "lock",
        "kts", "dart", "swift", "m", "mm", "scala", "groovy", "lua", "pl", "pm", "r", "jl", "vb", "cs", "fs",
        "asm", "s", "v", "sv", "vh", "vlog", "proto", "graphql", "gql", "vue", "svelte", "astro", "jsx", "tsx",
        "json5", "jsonc", "hjson", "ndjson", "geojson", "csv", "tsv", "ps1", "psm1", "vbs", "vbe", "wsf", "reg",
        "url", "webloc", "ics", "vcf", "eml", "msg", "mbox", "oft", "emlx", "nws",
        "vsd", "vsdx", "pub", "one", "onetoc2", "mpp", "xmind", "drawio", "mmd", "plantuml", "puml",
        "fb2", "lit", "pdb", "cbz", "cbr", "cb7", "kepub", "ibooks",
        "psb", "kra", "clip", "sai", "ai", "indd", "sketch", "fig", "xd", "cdr", "eps", "afdesign", "afphoto", "afpub",
        "sqlite", "sqlite3", "db", "db3", "mdb", "accdb", "dbf", "parquet", "avro", "orc",
        "npmrc", "yarnrc", "gemfile", "podfile", "vagrantfile", "jenkinsfile", "htaccess", "htpasswd",
        "repo", "service", "timer", "socket", "desktop", "plist", "strings", "storyboard", "xib", "resx", "rc",
        "diff", "patch", "rej", "orig", "bak", "tmp", "part", "crdownload", "download",
        "docm", "dotm", "docxf", "xlsm", "xltm", "xlam", "pptm", "potm", "ppam", "ppsx", "ppsm",
        "sldx", "sldm", "pages", "numbers", "key", "hwp", "hwpx", "wps", "et", "dps", "ofd",
        "xps", "oxps", "one", "onetoc2", "epub", "mobi", "azw", "azw3", "fb2", "cbz", "cbr",
        "cb7", "djvu", "lit", "tex", "ltx", "bib", "typ", "cls", "sty", "ris", "enw", "nbib",
        "sla", "scd", "kepub", "ibooks", "pdb", "xmind", "mmd", "puml", "drawio", "mpp", "gantt", "mermaid",
        "d.ts", "min.js", "min.css", "user.js", "user.css", "schema.json"

    )

    val ARCHIVE_EXT = setOf(
        "zip", "rar", "7z", "tar", "gz", "tgz", "bz2", "tbz", "xz", "txz", "zst",
        "iso", "cab", "jar", "war", "apks", "lz", "lzma", "arj", "cpio", "whl", "crx",
        "xz", "txz", "z", "lzh", "lha", "xar", "pkg", "deb", "rpm", "apkg", "xapk", "obb",
        "tar.gz", "tar.bz2", "tar.xz", "tgz", "tbz2", "zipx", "alz", "egg",
        "tar.zst", "tzst", "tar.lz4", "tlz4", "tar.br", "tbr", "tar.lz", "tar.lzma", "zstd", "lz4", "br", "rar5", "part1", "r00", "r01",
        "001", "7z.001", "zip.001", "bzip2", "gzip", "compress", "zoo", "ace", "pea", "sit", "sitx", "hqx",
        "aab", "apkm", "jar", "war", "ear", "aar", "bundle", "framework", "toast", "b5t", "b6t",
        "ccd", "mds", "mdf", "nrg", "cdi", "daa", "uif", "isz", "vhd", "vhdx", "vmdk", "qcow",
        "qcow2", "ova", "ovf", "hdd", "vbox", "pvm", "cpio", "ar", "deb", "lzma", "tlz", "z", "sfx",
        "tar.gz", "tar.bz2", "tar.xz", "tar.zst", "tar.lz4", "tar.lzma", "tar.lz", "tar.z", "tar.br",
        "7z.001", "zip.001", "rar.001", "tar.001", "tar.gz.001",
        "tar.zst.001", "tar.lz4.001", "tar.br.001",
        "iso.001", "esd.001", "wim.001", "img.001", "bin.001", "dmg.001",
        "qcow2.001", "vmdk.001", "vhd.001", "vhdx.001", "exe.001"

    )

    val TEXT_EXT = setOf(
        "txt", "md", "markdown", "json", "xml", "html", "htm", "log", "ini", "yaml",
        "yml", "toml", "conf", "cfg", "java", "kt", "kts", "py", "js", "ts", "css",
        "sh", "bat", "c", "cpp", "h", "go", "rs", "php", "sql", "properties",
        "gradle", "csv", "tsv", "gitignore", "license", "readme", "srt", "vtt", "lrc",
        "kts", "dart", "swift", "scala", "groovy", "lua", "pl", "r", "jl", "vb", "cs", "fs", "asm", "s",
        "proto", "graphql", "gql", "vue", "svelte", "astro", "jsx", "tsx", "json5", "jsonc", "hjson", "ndjson",
        "geojson", "ps1", "psm1", "vbs", "vbe", "wsf", "reg", "url", "webloc", "ics", "vcf", "eml", "mbox",
        "diff", "patch", "desktop", "plist", "strings", "npmrc", "yarnrc", "gemfile", "podfile",
        "vagrantfile", "jenkinsfile", "htaccess", "htpasswd", "repo", "service", "timer", "socket",
        "sol", "move", "cairo", "nim", "zig", "ex", "exs", "erl", "hrl", "clj", "cljs", "hs", "lhs",
        "ml", "mli", "fsx", "pas", "pp", "dpr", "bas", "cob", "ada", "adb", "ads", "forth", "f", "for",
        "tcl", "awk", "sed", "smali", "smi"
    )

    fun catOf(fileName: String, mime: String = "", isDir: Boolean = false): Cat {
        if (isDir) return Cat.FILE
        val e = extOf(fileName)
        return when {
            // apk / xapk 本质是 zip，但类型必须显示成应用，不能被当成压缩包
            e == "apk" || e == "xapk" || e == "apks" || mime == "application/vnd.android.package-archive" -> Cat.APP
            e in IMAGE_EXT || mime.startsWith("image/") -> Cat.IMAGE
            // .ts 既是 MPEG-TS 视频也是 TypeScript；只有明确视频 MIME 才当视频
            e == "ts" && isTsCode(mime) -> Cat.DOC
            e in VIDEO_EXT || mime.startsWith("video/") -> Cat.VIDEO
            e in AUDIO_EXT || mime.startsWith("audio/") -> Cat.AUDIO
            e in ARCHIVE_EXT || e.matches(Regex("(\\d{3}|r\\d{2}|z\\d{2})")) ||
                mime.contains("zip") || mime.contains("compressed") || mime.contains("x-7z") -> Cat.ARCHIVE
            e in DOC_EXT || mime.startsWith("text/") || mime.contains("pdf") ||
                mime.contains("word") || mime.contains("sheet") || mime.contains("presentation") ||
                mime.contains("officedocument") -> Cat.DOC
            else -> Cat.FILE
        }
    }

    /** 结合文件头判断真实类型：没有后缀、后缀被改过的文件也能识别 */
    fun catOfFile(f: File, mime: String = ""): Cat {
        val byName = catOf(f.name, mime)
        if (byName != Cat.FILE) return byName
        val magic = try { detectByMagic(f) } catch (_: Throwable) { null } ?: return Cat.FILE
        if (magic == "txt") return Cat.DOC
        return catOf("x." + magic, mime)
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

    /** 常见但不是“图片/视频/文档”系统的文件，也给它一个看得懂的名字 */
    private val EXT_LABEL = mapOf(
        "exe" to "Windows 可执行程序", "com" to "DOS 可执行程序", "scr" to "Windows 屏保程序",
        "msi" to "Windows 安装包", "msix" to "Windows 应用包", "msixbundle" to "Windows 应用包合集",
        "appx" to "Windows 应用包", "appxbundle" to "Windows 应用包合集", "appinstaller" to "Windows 应用安装器",
        "bat" to "Windows 批处理脚本", "cmd" to "Windows 命令脚本", "ps1" to "PowerShell 脚本",
        "psm1" to "PowerShell 模块", "vbs" to "VBScript 脚本", "vbe" to "VBScript 编码脚本", "wsf" to "Windows 脚本",
        "dll" to "动态链接库", "sys" to "系统驱动文件", "drv" to "设备驱动", "ocx" to "ActiveX 控件",
        "cpl" to "控制面板项", "inf" to "驱动安装信息", "reg" to "注册表文件", "cab" to "Windows 压缩包",
        "iso" to "光盘镜像 · ISO", "esd" to "Windows 系统镜像 · ESD", "wim" to "Windows 系统镜像 · WIM",
        "img" to "磁盘镜像 / 图片", "dmg" to "macOS 磁盘映像", "toast" to "macOS 光盘映像",
        "deb" to "Debian/Ubuntu 安装包", "rpm" to "Linux RPM 安装包", "appimage" to "Linux AppImage 程序",
        "flatpak" to "Linux Flatpak 包", "snap" to "Linux Snap 包", "ipa" to "iOS 应用包",
        "apkg" to "Anki 卡包", "xapk" to "安卓 XAPK 安装包", "apks" to "安卓 APKS 安装包", "obb" to "安卓 OBB 数据包",
        "vhd" to "虚拟硬盘", "vhdx" to "虚拟硬盘 · VHDX", "vmdk" to "VMware 虚拟磁盘",
        "qcow2" to "QEMU 虚拟磁盘", "ova" to "虚拟机导出包", "ovf" to "虚拟机描述文件",
        "nrg" to "光盘镜像 · NRG", "mdf" to "光盘镜像 · MDF", "mds" to "光盘镜像索引", "cue" to "光盘音轨索引",
        "vcd" to "虚拟光盘", "bwi" to "BlindWrite 镜像", "ccd" to "CloneCD 镜像",
        "torrent" to "BT 种子文件", "magnet" to "磁力链接文件",
        "cer" to "证书文件", "crt" to "证书文件", "pem" to "证书/密钥", "p12" to "证书密钥库", "pfx" to "证书密钥库",
        "der" to "证书文件", "key" to "密钥/Keynote", "csr" to "证书请求", "asc" to "签名文件", "sig" to "签名文件",
        "ttf" to "字体文件", "otf" to "字体文件", "woff" to "网页字体", "woff2" to "网页字体", "ttc" to "字体合集",
        "fon" to "点阵字体", "pfb" to "PostScript 字体",
        "psd" to "Photoshop 源文件", "psb" to "Photoshop 大文件", "ai" to "Illustrator 源文件",
        "indd" to "InDesign 排版文件", "sketch" to "Sketch 设计文件", "fig" to "Figma 设计文件",
        "xd" to "Adobe XD 设计文件", "cdr" to "CorelDRAW 文件", "eps" to "EPS 矢量图",
        "afdesign" to "Affinity Designer 文件", "afphoto" to "Affinity Photo 文件", "afpub" to "Affinity Publisher 文件",
        "kra" to "Krita 绘画文件", "clip" to "Clip Studio 绘画文件", "sai" to "SAI 绘画文件",
        "blend" to "Blender 工程", "stl" to "3D 打印模型", "obj" to "3D 模型", "fbx" to "3D 模型",
        "3ds" to "3D 模型", "dae" to "3D 模型", "glb" to "glTF 3D 模型", "gltf" to "glTF 3D 模型",
        "dwg" to "AutoCAD 图纸", "dxf" to "CAD 交换图纸", "step" to "STEP 三维模型", "stp" to "STEP 三维模型",
        "iges" to "IGES 三维模型", "igs" to "IGES 三维模型", "skp" to "SketchUp 模型", "max" to "3ds Max 工程",
        "c4d" to "Cinema 4D 工程", "ma" to "Maya 工程", "mb" to "Maya 工程",
        "apk" to "APK 安装包", "aab" to "Android App Bundle",
        "sqlite" to "SQLite 数据库", "sqlite3" to "SQLite 数据库", "db" to "数据库文件", "db3" to "数据库文件",
        "mdb" to "Access 数据库", "accdb" to "Access 数据库", "dbf" to "dBASE 数据库",
        "parquet" to "Parquet 数据文件", "avro" to "Avro 数据文件", "orc" to "ORC 数据文件",
        "bak" to "备份文件", "tmp" to "临时文件", "part" to "未完成下载", "crdownload" to "Chrome 未完成下载",
        "download" to "未完成下载", "lock" to "锁文件",
        "lnk" to "Windows 快捷方式", "url" to "网页快捷方式", "webloc" to "网页快捷方式",
        "msu" to "Windows 更新包", "msp" to "Windows 补丁包", "msc" to "Windows 管理控制台",
        "mui" to "Windows 多语言资源", "mun" to "Windows 资源文件", "msstyles" to "Windows 主题样式",
        "theme" to "Windows 主题", "themepack" to "Windows 主题包", "deskthemepack" to "Windows 桌面主题包",
        "diagcab" to "Windows 诊断包", "job" to "计划任务", "scf" to "Windows 资源管理器命令",
        "so" to "Linux 动态库", "ko" to "Linux 内核模块", "o" to "编译目标文件", "a" to "静态库",
        "la" to "Libtool 库", "lib" to "静态库", "exp" to "导出库", "ilk" to "编译中间文件",
        "dex" to "Dalvik 可执行文件", "smali" to "Smali 代码", "aab" to "Android App Bundle",
        "mobileprovision" to "iOS 描述文件", "provisioningprofile" to "iOS 描述文件",
        "mobileconfig" to "Apple 配置描述文件", "ipsw" to "iOS 固件", "car" to "iOS 资源包",
        "pkg" to "macOS 安装包", "mpkg" to "macOS 安装包", "flatpakref" to "Flatpak 引用",
        "flatpakrepo" to "Flatpak 仓库", "run" to "Linux 可执行安装包", "app" to "macOS 应用程序",
        "appimage" to "Linux AppImage 程序", "vdi" to "VirtualBox 虚拟硬盘", "vbox" to "VirtualBox 配置",
        "vmx" to "VMware 虚拟机配置", "vag" to "VirtualBox 配置",
        "gba" to "GBA 游戏 ROM", "gb" to "Game Boy ROM", "gbc" to "GBC 游戏 ROM", "nds" to "NDS 游戏 ROM",
        "3ds" to "3DS 游戏 ROM", "nes" to "FC 游戏 ROM", "sfc" to "SFC 游戏 ROM", "smc" to "SMC 游戏 ROM",
        "z64" to "N64 游戏 ROM", "n64" to "N64 游戏 ROM", "cso" to "PSP 压缩镜像", "chd" to "MAME 硬盘镜像",
        "pbp" to "PSP 游戏包", "azw" to "Kindle 电子书", "azw4" to "Kindle 电子书", "prc" to "电子书",
        "tcr" to "电子书", "sup" to "蓝光字幕", "idx" to "字幕索引", "smi" to "SAMI 字幕",
        "m4p" to "iTunes 音频", "aa" to "Audible 有声书", "aax" to "Audible 有声书",
        "ifo" to "DVD 信息文件", "bup" to "DVD 备份文件",
        "odg" to "OpenDocument 图形", "ott" to "OpenDocument 模板", "ots" to "OpenDocument 表格模板",
        "otp" to "OpenDocument 演示模板", "fodt" to "Flat ODF 文本", "fods" to "Flat ODF 表格",
        "fodp" to "Flat ODF 演示", "sxw" to "OpenOffice 文本", "sxc" to "OpenOffice 表格",
        "sxi" to "OpenOffice 演示", "wpd" to "WordPerfect 文档",
        "sol" to "Solidity 合约", "move" to "Move 合约", "cairo" to "Cairo 代码", "nim" to "Nim 代码",
        "zig" to "Zig 代码", "ex" to "Elixir 代码", "exs" to "Elixir 脚本", "erl" to "Erlang 代码",
        "hrl" to "Erlang 头文件", "clj" to "Clojure 代码", "cljs" to "ClojureScript 代码",
        "hs" to "Haskell 代码", "lhs" to "Literate Haskell", "ml" to "OCaml 代码", "mli" to "OCaml 接口",
        "fsx" to "F# 脚本", "pas" to "Pascal 代码", "pp" to "Pascal 代码", "dpr" to "Delphi 工程",
        "bas" to "Basic 代码", "cob" to "COBOL 代码", "ada" to "Ada 代码", "adb" to "Ada 代码",
        "ads" to "Ada 规范", "forth" to "Forth 代码", "f" to "Fortran 代码", "for" to "Fortran 代码",
        "pro" to "Pro 工程/脚本", "tcl" to "Tcl 脚本", "awk" to "AWK 脚本", "sed" to "sed 脚本"
    )

    /** 第二批扩展名标签，覆盖办公、电子书、设计、三维、镜像、数据等更细的类型 */
    private val EXT_LABEL_MORE = mapOf(
        "docm" to "Word 启用宏文档", "dotm" to "Word 启用宏模板", "docxf" to "ONLYOFFICE 表单",
        "xlsm" to "Excel 启用宏工作簿", "xltm" to "Excel 启用宏模板", "xlam" to "Excel 加载项",
        "pptm" to "PowerPoint 启用宏演示", "potm" to "PowerPoint 启用宏模板", "ppam" to "PowerPoint 加载项",
        "ppsx" to "PowerPoint 放映文件", "ppsm" to "PowerPoint 启用宏放映", "sldx" to "PowerPoint 幻灯片",
        "sldm" to "PowerPoint 启用宏幻灯片", "pages" to "Apple Pages 文档", "numbers" to "Apple Numbers 表格",
        "key" to "Apple Keynote 演示", "hwp" to "韩文 HWP 文档", "hwpx" to "韩文 HWPX 文档",
        "wps" to "WPS 文字文档", "et" to "WPS 表格", "dps" to "WPS 演示", "ofd" to "OFD 版式文档",
        "xps" to "XPS 版式文档", "oxps" to "OpenXPS 版式文档", "one" to "OneNote 笔记",
        "onetoc2" to "OneNote 目录", "epub" to "EPUB 电子书", "mobi" to "Kindle MOBI 电子书",
        "azw" to "Kindle 电子书", "azw3" to "Kindle KF8 电子书", "fb2" to "FictionBook 电子书",
        "cbz" to "漫画包 · CBZ", "cbr" to "漫画包 · CBR", "cb7" to "漫画包 · CB7",
        "djvu" to "DjVu 扫描文档", "lit" to "Microsoft Reader 电子书", "tex" to "LaTeX 源文件",
        "ltx" to "LaTeX 文档", "bib" to "BibTeX 参考文献", "typ" to "Typst 文档", "cls" to "LaTeX 类文件",
        "sty" to "LaTeX 样式", "ris" to "RIS 文献数据", "enw" to "EndNote 文献", "nbib" to "PubMed 文献",
        "vhd" to "虚拟硬盘 · VHD", "vhdx" to "虚拟硬盘 · VHDX", "vmdk" to "VMware 虚拟磁盘",
        "qcow" to "QEMU 虚拟磁盘", "qcow2" to "QEMU 虚拟磁盘 · QCOW2", "ova" to "虚拟机导出包 · OVA",
        "ovf" to "虚拟机描述 · OVF", "hdd" to "虚拟机硬盘 · HDD", "vbox" to "VirtualBox 配置",
        "pvm" to "Parallels 虚拟机", "sparseimage" to "macOS 稀疏磁盘映像",
        "aab" to "Android App Bundle", "apkm" to "APKM 拆分安装包", "ipa" to "iOS 应用包",
        "jar" to "Java 归档/JAR", "war" to "Java Web 归档", "ear" to "Java 企业归档",
        "aar" to "Android 库归档", "bundle" to "macOS 插件包", "framework" to "macOS 框架",
        "msu" to "Windows 更新包", "msp" to "Windows 补丁包", "mst" to "Windows 安装变换",
        "appref-ms" to "ClickOnce 应用引用", "website" to "网站快捷方式", "url" to "网址快捷方式",
        "webloc" to "macOS 网址快捷方式", "psb" to "Photoshop 大文档", "xcf" to "GIMP 工程",
        "kra" to "Krita 绘画工程", "ora" to "OpenRaster 绘画工程", "clip" to "Clip Studio 工程",
        "csp" to "Clip Studio 绘画", "sai" to "SAI 绘画工程", "sai2" to "SAI2 绘画工程",
        "afdesign" to "Affinity Designer 工程", "afphoto" to "Affinity Photo 工程",
        "afpub" to "Affinity Publisher 工程", "sketch" to "Sketch 设计稿", "fig" to "Figma 设计稿",
        "xd" to "Adobe XD 设计稿", "indd" to "InDesign 排版文档", "idml" to "InDesign 交换文档",
        "qxd" to "QuarkXPress 文档", "cdr" to "CorelDRAW 图形", "vsd" to "Visio 绘图",
        "vsdx" to "Visio 绘图 · VSDX", "dwg" to "CAD 图纸 · DWG", "dxf" to "CAD 交换图纸",
        "dwf" to "CAD 轻量图纸", "skp" to "SketchUp 模型", "rvt" to "Revit 建筑模型",
        "rfa" to "Revit 族文件", "ifc" to "BIM 建筑模型 · IFC", "step" to "STEP 三维模型",
        "stp" to "STEP 三维模型", "iges" to "IGES 三维模型", "igs" to "IGES 三维模型",
        "3ds" to "3ds Max 场景", "max" to "3ds Max 工程", "blend" to "Blender 工程",
        "blend1" to "Blender 备份工程", "ma" to "Maya 场景", "mb" to "Maya 二进制场景",
        "c4d" to "Cinema 4D 工程", "lxo" to "Modo 场景", "fbx" to "FBX 三维模型",
        "obj" to "OBJ 三维模型", "dae" to "Collada 三维模型", "gltf" to "glTF 三维模型",
        "glb" to "GLB 三维模型", "stl" to "STL 三维模型", "parquet" to "Parquet 数据表",
        "avro" to "Avro 数据文件", "orc" to "ORC 列式数据", "feather" to "Feather 数据表",
        "arrow" to "Arrow 数据表", "hdf5" to "HDF5 科学数据", "h5" to "HDF5 科学数据",
        "nc" to "NetCDF 气象数据", "mat" to "MATLAB 数据", "sav" to "SPSS 数据 / 游戏存档",
        "dta" to "Stata 数据", "sas7bdat" to "SAS 数据", "xpt" to "SAS 传输数据",
        "rdata" to "R 数据", "rds" to "R 数据 · RDS", "otf" to "OpenType 字体",
        "woff" to "Web 字体 · WOFF", "woff2" to "Web 字体 · WOFF2", "eot" to "嵌入式字体 · EOT",
        "fon" to "Windows 点阵字体", "fnt" to "点阵字体", "ttc" to "字体合集 · TTC",
        "pfb" to "Type1 字体", "pfm" to "Type1 字体度量", "afm" to "字体度量文件",
        "cer" to "X.509 证书", "crt" to "安全证书", "der" to "DER 编码证书", "p7b" to "PKCS#7 证书链",
        "p7c" to "PKCS#7 证书", "p12" to "PKCS#12 证书库", "pfx" to "PFX 证书库",
        "jks" to "Java 密钥库", "keystore" to "密钥库", "gpg" to "GnuPG 加密文件",
        "asc" to "ASCII 签名/密钥", "sig" to "数字签名", "pem" to "PEM 证书/密钥",
        "toast" to "Toast 光盘映像", "b5t" to "BlindWrite 光盘映像", "b6t" to "BlindWrite 光盘映像",
        "ccd" to "CloneCD 光盘描述", "cue" to "光盘轨道表 · CUE", "mds" to "Alcohol 光盘描述",
        "mdf" to "Alcohol 光盘镜像 / SQL 数据库", "nrg" to "Nero 光盘映像", "cdi" to "DiscJuggler 光盘映像",
        "daa" to "PowerISO 光盘映像", "uif" to "MagicISO 光盘映像", "isz" to "压缩光盘映像"
    )

    private fun moreLabel(e: String): String? = EXT_LABEL_MORE[e]


    /** 第三批：影音、图像、系统、虚拟机、数据库等细分类标签 */
    private val EXT_LABEL_MORE2 = mapOf(
        "jp2" to "JPEG 2000 图片",
        "j2k" to "JPEG 2000 图片",
        "jpx" to "JPEG 2000 图片",
        "jpm" to "JPEG 2000 图片",
        "pnm" to "PNM 位图",
        "pbm" to "PBM 黑白位图",
        "pgm" to "PGM 灰度位图",
        "ppm" to "PPM 彩色位图",
        "pcx" to "PCX 位图",
        "tga" to "TGA 位图",
        "sgi" to "SGI 位图",
        "rgba" to "SGI RGBA 位图",
        "dpx" to "DPX 电影帧",
        "cin" to "Cineon 电影帧",
        "exr" to "OpenEXR 高动态图片",
        "hdr" to "Radiance HDR 图片",
        "m2v" to "MPEG-2 视频",
        "m1v" to "MPEG-1 视频",
        "mpv" to "MPEG 视频流",
        "y4m" to "YUV4MPEG 视频",
        "h264" to "H.264 视频流",
        "264" to "H.264 视频流",
        "h265" to "H.265 视频流",
        "265" to "H.265 视频流",
        "hevc" to "H.265 视频流",
        "av1" to "AV1 视频流",
        "ivf" to "AV1/VP 视频流",
        "mka" to "Matroska 音频",
        "ac3" to "Dolby AC-3 音频",
        "eac3" to "Dolby E-AC-3 音频",
        "dts" to "DTS 音频",
        "thd" to "Dolby TrueHD 音频",
        "mlp" to "MLP 无损音频",
        "m4b" to "M4B 有声书",
        "m4r" to "iPhone 铃声",
        "opus" to "Opus 音频",
        "ape" to "Monkey's Audio 无损",
        "wv" to "WavPack 无损",
        "tta" to "TTA 无损",
        "aif" to "AIFF 音频",
        "aiff" to "AIFF 音频",
        "amr" to "AMR 语音",
        "spx" to "Speex 语音",
        "dsf" to "DSD 音频",
        "dff" to "DSD 音频",
        "har" to "HTTP 抓包数据",
        "ipynb" to "Jupyter 笔记本",
        "rmd" to "R Markdown 文档",
        "qmd" to "Quarto 文档",
        "xopp" to "Xournal++ 手写笔记",
        "torrent" to "BT 种子",
        "nzb" to "Usenet 下载任务",
        "sfx" to "自解压压缩包",
        "theme" to "Windows 主题",
        "themepack" to "Windows 主题包",
        "deskthemepack" to "Windows 桌面主题包",
        "mui" to "Windows 多语言资源",
        "mun" to "Windows 资源",
        "acl" to "Windows 权限模板",
        "pol" to "Windows 组策略模板",
        "mobileconfig" to "Apple 配置描述文件",
        "mobileprovision" to "iOS 描述文件",
        "provisionprofile" to "Apple 描述文件",
        "sla" to "Scribus 排版文档",
        "scd" to "Scribus 文档",
        "pub" to "Publisher 出版物",
        "oxps" to "OpenXPS 文档",
        "djvu" to "DjVu 扫描文档",
        "kepub" to "Kobo 电子书",
        "ibooks" to "Apple 电子书",
        "pdb" to "Palm 文档 / 调试符号",
        "xmind" to "XMind 思维导图",
        "mmd" to "Mermaid 图表",
        "puml" to "PlantUML 图表",
        "drawio" to "draw.io 图表",
        "vsdx" to "Visio 绘图",
        "mpp" to "Project 项目计划",
        "gantt" to "甘特图",
        "mermaid" to "Mermaid 图表",
        "ldb" to "LevelDB 数据",
        "mdb" to "Access 数据库",
        "accdb" to "Access 数据库",
        "db3" to "SQLite 数据库",
        "sqlite-wal" to "SQLite 日志",
        "sqlite-shm" to "SQLite 共享内存",
        "frm" to "MySQL 表结构",
        "ibd" to "MySQL 表数据",
        "myd" to "MySQL 数据",
        "myi" to "MySQL 索引",
        "ndf" to "SQL Server 数据",
        "ldf" to "SQL Server 日志",
        "ova" to "虚拟应用包",
        "ovf" to "虚拟系统描述",
        "vmem" to "虚拟机内存快照",
        "vmsn" to "VMware 快照",
        "vmsd" to "VMware 快照配置",
        "nvram" to "虚拟机 NVRAM",
        "vmss" to "VMware 挂起状态",
        "vmx" to "VMware 配置",
        "vbox-prev" to "VirtualBox 备份配置",
        "pvi" to "Parallels 虚拟磁盘",
        "hdd" to "Parallels 硬盘"
    )

    private fun more2Label(e: String): String? = EXT_LABEL_MORE2[e]


    /** 第四批：游戏 ROM、AI 模型、CAD/打印、字体、资源包、抓包与转储等 */
    private val EXT_LABEL_MORE3 = mapOf(
        "gba" to "Game Boy Advance 游戏",
        "gbc" to "Game Boy Color 游戏",
        "gb" to "Game Boy 游戏",
        "nds" to "NDS 游戏",
        "3ds" to "3DS 游戏",
        "cia" to "3DS CIA 安装包",
        "nsp" to "Switch 游戏包",
        "xci" to "Switch 卡带镜像",
        "wad" to "WAD 资源包",
        "wbfs" to "Wii 备份镜像",
        "cso" to "PSP 压缩镜像",
        "pbp" to "PSP 游戏包",
        "vpk" to "PS Vita 应用包 / Valve 资源包",
        "xex" to "Xbox 360 可执行",
        "god" to "Xbox 360 GOD 包",
        "rpx" to "Wii U 可执行",
        "wud" to "Wii U 光盘镜像",
        "wux" to "Wii U 压缩镜像",
        "rvz" to "Dolphin RVZ 镜像",
        "gcm" to "GameCube 镜像",
        "nes" to "FC/NES 游戏",
        "snes" to "SFC/SNES 游戏",
        "smc" to "SNES 游戏",
        "sfc" to "SNES 游戏",
        "n64" to "N64 游戏",
        "z64" to "N64 游戏",
        "v64" to "N64 游戏",
        "md" to "Markdown 文档",
        "gen" to "Genesis 游戏",
        "sms" to "Master System 游戏",
        "gg" to "Game Gear 游戏",
        "pce" to "PC Engine 游戏",
        "sgx" to "SuperGrafx 游戏",
        "32x" to "32X 游戏",
        "a26" to "Atari 2600 游戏",
        "a52" to "Atari 5200 游戏",
        "a78" to "Atari 7800 游戏",
        "j64" to "Atari Jaguar 游戏",
        "jag" to "Atari Jaguar 游戏",
        "col" to "ColecoVision 游戏",
        "int" to "Intellivision 游戏",
        "vec" to "Vectrex 游戏",
        "d64" to "Commodore 64 磁盘",
        "t64" to "C64 磁带镜像",
        "tap" to "磁带镜像 / 数控代码",
        "prg" to "C64 程序",
        "crt" to "C64 卡带",
        "d81" to "C64 磁盘",
        "adf" to "Amiga 磁盘",
        "ipf" to "Amiga IPF 磁盘",
        "st" to "Atari ST 磁盘",
        "stx" to "Atari ST 镜像",
        "msa" to "Atari ST 镜像",
        "f3d" to "Fusion 360 工程",
        "fcstd" to "FreeCAD 工程",
        "ipt" to "Inventor 零件",
        "iam" to "Inventor 装配",
        "prt" to "CAD 零件",
        "asm" to "CAD 装配",
        "sldprt" to "SolidWorks 零件",
        "sldasm" to "SolidWorks 装配",
        "slddrw" to "SolidWorks 工程图",
        "catpart" to "CATIA 零件",
        "catproduct" to "CATIA 装配",
        "x_t" to "Parasolid 模型",
        "x_b" to "Parasolid 模型",
        "3mf" to "3D 打印模型",
        "bgcode" to "Bambu G-code",
        "gx" to "GX 打印文件",
        "lbrn" to "LightBurn 工程",
        "lbrn2" to "LightBurn 工程",
        "art" to "ArtCAM 工程 / ART 运行时",
        "nc" to "数控 G-code / NetCDF 数据",
        "ncc" to "数控代码",
        "apt" to "APT 数控代码",
        "dfx" to "DFX 模型",
        "amf" to "AMF 3D 模型",
        "gcode" to "3D 打印 G-code",
        "dfont" to "macOS 数据字体",
        "suit" to "macOS 字体 suitcase",
        "pfa" to "PostScript 字体",
        "bdf" to "BDF 位图字体",
        "pcf" to "PCF 位图字体",
        "psf" to "PSF 控制台字体",
        "otc" to "OpenType 字体合集",
        "arc" to "ARC 压缩包",
        "pak" to "游戏资源包",
        "pk3" to "Quake III 资源包",
        "pk4" to "Quake 4 资源包",
        "mpq" to "Blizzard MPQ 资源包",
        "scs" to "SCS 资源包",
        "bsa" to "Bethesda 资源包",
        "ba2" to "Bethesda 资源包",
        "big" to "EA BIG 资源包",
        "rpa" to "Ren'Py 资源包",
        "love" to "LÖVE 游戏包",
        "dat" to "通用数据文件",
        "sav" to "游戏存档",
        "srm" to "模拟器存档",
        "state" to "模拟器状态",
        "kdb" to "KeePass 1.x 密码库",
        "opvault" to "1Password 密码库",
        "keychain" to "macOS 钥匙串",
        "psafe3" to "Password Safe 密码库",
        "sxf" to "SafeExchange 加密文件",
        "realm" to "Realm 数据库",
        "couch" to "CouchDB 数据",
        "lmdb" to "LMDB 数据库",
        "mdbx" to "MDBX 数据库",
        "dmp" to "Windows 内存转储",
        "minidump" to "迷你内存转储",
        "hprof" to "Java 堆转储",
        "heap" to "堆快照",
        "jfr" to "Java 飞行记录",
        "trace" to "性能跟踪",
        "perf" to "性能采样",
        "etl" to "Windows 事件跟踪",
        "pcap" to "网络抓包 · PCAP",
        "pcapng" to "网络抓包 · PCAPNG",
        "cap" to "网络抓包",
        "saz" to "Fiddler 抓包",
        "onnx" to "ONNX 模型",
        "pt" to "PyTorch 模型",
        "pth" to "PyTorch 模型",
        "ckpt" to "模型检查点",
        "safetensors" to "SafeTensors 模型",
        "tflite" to "TensorFlow Lite 模型",
        "mlmodel" to "Core ML 模型",
        "gguf" to "GGUF 大模型",
        "ggml" to "GGML 模型",
        "keras" to "Keras 模型",
        "joblib" to "Joblib 模型",
        "pkl" to "Python 序列化数据",
        "pickle" to "Python 序列化数据",
        "npy" to "NumPy 数组",
        "npz" to "NumPy 压缩数组",
        "pb" to "TensorFlow 图模型",
        "meta" to "模型元数据",
        "index" to "模型索引",
        "vocab" to "模型词表",
        "sentinel" to "模型分片标记",
        "ot" to "OpenType 字体",
        "ttc" to "字体合集",
        "woff2" to "Web 字体"
    )

    private fun more3Label(e: String): String? = EXT_LABEL_MORE3[e]


    /** 第五批：剪辑/音频工程、地图、取证、系统日志、代码产物等 */
    private val EXT_LABEL_MORE4 = mapOf(
        "aep" to "After Effects 工程",
        "aepx" to "After Effects XML 工程",
        "prproj" to "Premiere 工程",
        "mogrt" to "Premiere 动态图形模板",
        "veg" to "Vegas 工程",
        "vf" to "Vegas 工程",
        "drp" to "DaVinci Resolve 工程",
        "fcpxml" to "Final Cut Pro XML 工程",
        "fcpxmld" to "Final Cut Pro 资源包",
        "imovieproj" to "iMovie 工程",
        "wlmp" to "Windows Live 影音项目",
        "camproj" to "Camtasia 工程",
        "camrec" to "Camtasia 录制",
        "vpj" to "VideoPad 工程",
        "tvpp" to "TVPaint 工程",
        "mlt" to "MLT 视频工程",
        "kdenlive" to "Kdenlive 工程",
        "osp" to "OpenShot 工程",
        "sfk" to "Sound Forge 峰值",
        "aup" to "Audacity 工程",
        "sesx" to "Audition 会话",
        "sf2" to "SoundFont 音色库",
        "sfz" to "SFZ 音色",
        "dls" to "DLS 音色库",
        "gig" to "GigaStudio 音色",
        "rx2" to "Reason 音色",
        "rex" to "REX 循环",
        "wproj" to "Wavosaur 工程",
        "vrm" to "VRM 虚拟形象",
        "vrml" to "VRML 模型",
        "wrl" to "VRML 模型",
        "x3d" to "X3D 模型",
        "mbtiles" to "离线地图包",
        "pmtiles" to "PMTiles 地图包",
        "osm" to "OpenStreetMap 数据",
        "pbf" to "地图 PBF 数据",
        "shp" to "Shapefile 地图",
        "shx" to "Shapefile 索引",
        "sbn" to "Shapefile 空间索引",
        "sbx" to "Shapefile 索引",
        "prj" to "坐标投影文件",
        "cpg" to "Shapefile 编码",
        "qgs" to "QGIS 工程",
        "qgz" to "QGIS 压缩工程",
        "mxd" to "ArcMap 文档",
        "aprx" to "ArcGIS Pro 工程",
        "lyr" to "ArcGIS 图层",
        "lyrx" to "ArcGIS Pro 图层",
        "style" to "地图样式",
        "hap" to "HarmonyOS 应用包",
        "hsp" to "HarmonyOS 共享包",
        "vsix" to "VS Code 扩展包",
        "nupkg" to "NuGet 包",
        "xpi" to "Firefox 扩展包",
        "oxt" to "LibreOffice 扩展",
        "sb3" to "Scratch 3 项目",
        "love" to "LÖVE 游戏",
        "cba" to "漫画包 · CBA",
        "cbt" to "漫画包 · CBT",
        "cb7" to "漫画包 · CB7",
        "cbr" to "漫画包 · CBR",
        "cbz" to "漫画包 · CBZ",
        "evtx" to "Windows 事件日志",
        "evt" to "Windows 事件日志",
        "etl" to "Windows 跟踪日志",
        "bkf" to "Windows 备份",
        "bkp" to "备份文件",
        "backup" to "备份文件",
        "ab" to "Android 备份",
        "adb" to "Android 调试备份",
        "crypt12" to "WhatsApp 加密备份",
        "crypt14" to "WhatsApp 加密备份",
        "crypt15" to "WhatsApp 加密备份",
        "e01" to "EnCase 取证镜像",
        "aff" to "AFF 取证镜像",
        "aff4" to "AFF4 取证镜像",
        "dd" to "DD 磁盘镜像",
        "pkpass" to "Apple 钱包卡券",
        "p7s" to "分离式数字签名",
        "p7m" to "S/MIME 加密邮件",
        "rpy" to "Ren'Py 脚本",
        "rpyc" to "Ren'Py 编译脚本",
        "renpy" to "Ren'Py 工程",
        "pyc" to "Python 字节码",
        "pyo" to "Python 优化字节码",
        "pyd" to "Python 扩展模块",
        "class" to "Java 字节码",
        "dex" to "Dalvik 字节码",
        "odex" to "优化 Dalvik 字节码",
        "vdex" to "VDEX 字节码",
        "art" to "ART 运行时镜像",
        "wasm" to "WebAssembly 模块",
        "wat" to "WebAssembly 文本",
        "so" to "共享库",
        "dylib" to "macOS 动态库",
        "lib" to "静态库",
        "a" to "静态库"
    )

    private fun more4Label(e: String): String? = EXT_LABEL_MORE4[e]

    /** 第六批：压缩/镜像/模型/医学影像/工程/邮件/电子书/统计/芯片固件等补充 */
    private val EXT_LABEL_MORE5 = mapOf(
        "zst" to "Zstandard 压缩文件", "zstd" to "Zstandard 压缩文件", "tzst" to "TAR.ZST 压缩包",
        "lz4" to "LZ4 压缩文件", "tlz4" to "TAR.LZ4 压缩包",
        "br" to "Brotli 压缩文件", "tbr" to "TAR.BR 压缩包",
        "lzo" to "LZO 压缩文件", "lzop" to "LZOP 压缩文件", "dz" to "Dzip 压缩文件",
        "zpaq" to "ZPAQ 压缩包", "arc" to "ARC 压缩包", "ark" to "ARK 压缩包",
        "pak" to "PAK 资源包", "pk3" to "Quake III PAK", "pk4" to "Quake IV PAK",
        "wad" to "WAD 游戏资源包", "bsa" to "Bethesda BSA 资源包", "ba2" to "Bethesda BA2 资源包",
        "vpk" to "PS Vita 应用包 / Valve 资源包",
        "gguf" to "GGUF 大模型权重", "ggml" to "GGML 大模型权重", "safetensors" to "SafeTensors 模型权重",
        "ckpt" to "TensorFlow/PyTorch 检查点", "pt" to "PyTorch 模型", "pth" to "PyTorch 模型权重",
        "onnx" to "ONNX 模型", "tflite" to "TensorFlow Lite 模型", "mlmodel" to "Core ML 模型",
        "npy" to "NumPy 数组", "npz" to "NumPy 压缩数组", "pkl" to "Python 序列化数据", "joblib" to "Joblib 模型",
        "h5" to "HDF5 数据", "hdf5" to "HDF5 数据", "pb" to "TensorFlow 冻结模型 / 协议数据",
        "dcm" to "DICOM 医学影像", "dicom" to "DICOM 医学影像", "nii" to "NIfTI 医学影像",
        "nii.gz" to "NIfTI 压缩医学影像", "mha" to "MetaImage 医学影像", "mhd" to "MetaImage 头文件",
        "nrrd" to "NRRD 医学影像", "fits" to "FITS 天文数据", "fts" to "FITS 天文数据",
        "las" to "LAS 点云数据", "laz" to "LAZ 压缩点云", "dem" to "DEM 高程数据",
        "asc" to "ASCII 网格 / 高程数据", "hgt" to "SRTM 高程瓦片", "grib" to "GRIB 气象数据",
        "grib2" to "GRIB2 气象数据", "zarr" to "Zarr 数组数据", "pmtiles" to "PMTiles 离线地图",
        "osm.pbf" to "OpenStreetMap PBF 数据",
        "pst" to "Outlook 邮件存档", "ost" to "Outlook 离线邮件", "olm" to "Outlook for Mac 邮件",
        "nsf" to "Lotus Notes 邮件库", "mbx" to "邮箱数据文件",
        "kdbx" to "KeePass 密码数据库", "keychain" to "macOS 钥匙串", "1pif" to "1Password 导出",
        "opvault" to "1Password 保险库",
        "vdi" to "VirtualBox 虚拟硬盘", "vhd" to "虚拟硬盘", "vhdx" to "虚拟硬盘 · VHDX",
        "vmdk" to "VMware 虚拟磁盘", "qcow2" to "QEMU 虚拟磁盘", "qco" to "QEMU 虚拟磁盘",
        "hdd" to "Parallels 虚拟硬盘", "vbox" to "VirtualBox 配置", "pvm" to "Parallels 虚拟机",
        "rom" to "ROM 固件/游戏镜像", "uf2" to "UF2 固件", "hex" to "Intel HEX 固件",
        "s19" to "Motorola S-record 固件", "mot" to "Motorola S-record 固件", "elf" to "ELF 可执行/固件",
        "cap" to "网络抓包文件",
        "ipynb" to "Jupyter Notebook", "rmd" to "R Markdown 文档", "qmd" to "Quarto 文档",
        "rdata" to "R 数据文件", "rds" to "R 数据文件", "rda" to "R 数据文件",
        "mat" to "MATLAB 数据文件", "dta" to "Stata 数据文件", "por" to "SPSS 便携数据",
        "flp" to "FL Studio 工程", "als" to "Ableton Live 工程", "aup3" to "Audacity 3 工程",
        "rpp" to "REAPER 工程", "ptx" to "Pro Tools 工程", "ptf" to "Pro Tools 会话",
        "omf" to "OMF 音频交换工程", "aaf" to "AAF 音视频交换工程",
        "cpr" to "Cubase 工程", "npr" to "Nuendo 工程", "band" to "GarageBand 工程",
        "logicx" to "Logic Pro 工程", "sesx" to "Adobe Audition 会话",
        "3mf" to "3MF 3D 打印模型", "amf" to "AMF 3D 打印模型", "ply" to "PLY 3D 模型/点云",
        "usd" to "Universal Scene Description", "usda" to "USD 文本场景", "usdc" to "USD 二进制场景",
        "usdz" to "USDZ AR 模型", "abc" to "Alembic 三维缓存",
        "gcode" to "3D 打印 G-code", "gco" to "3D 打印 G-code", "ngc" to "数控 G-code",
        "dgn" to "MicroStation 图纸", "rvt" to "Revit 工程", "rfa" to "Revit 族文件",
        "ifc" to "IFC 建筑模型", "sat" to "ACIS 三维模型", "sldprt" to "SolidWorks 零件",
        "sldasm" to "SolidWorks 装配体", "ipt" to "Inventor 零件", "iam" to "Inventor 装配体",
        "catpart" to "CATIA 零件", "catproduct" to "CATIA 装配体",
        "3dm" to "Rhino 3D 模型", "3dmbak" to "Rhino 备份模型",
        "pkg" to "macOS 安装包", "mpkg" to "macOS 多包安装器", "xip" to "macOS 签名归档",
        "flatpakref" to "Flatpak 引用文件", "snap" to "Snap 安装包",
        "ttml" to "TTML 字幕", "dfxp" to "DFXP 字幕", "sbv" to "SBV 字幕", "rt" to "RealText 字幕",
        "fodt" to "Flat ODF 文本文档", "fods" to "Flat ODF 表格", "fodp" to "Flat ODF 演示",
        "kdb" to "Keypass/KeePass 数据库", "age" to "age 加密文件"
    )

    private fun more5Label(e: String): String? = EXT_LABEL_MORE5[e]

    /** 两段/三段组合后缀的完整说明，详情和列表都用这个 */
    private val COMPOUND_LABEL = mapOf(
        "tar.gz" to "TAR.GZ 压缩包", "tar.bz2" to "TAR.BZ2 压缩包", "tar.xz" to "TAR.XZ 压缩包",
        "tar.zst" to "TAR.ZST 压缩包", "tar.lz4" to "TAR.LZ4 压缩包", "tar.lzma" to "TAR.LZMA 压缩包",
        "tar.lz" to "TAR.LZ 压缩包", "tar.z" to "TAR.Z 压缩包", "tar.br" to "TAR.BR 压缩包",
        "7z.001" to "7Z 分卷压缩包（第 1 卷）", "zip.001" to "ZIP 分卷压缩包（第 1 卷）",
        "rar.001" to "RAR 分卷压缩包（第 1 卷）", "tar.001" to "TAR 分卷压缩包（第 1 卷）",
        "tar.gz.001" to "TAR.GZ 分卷压缩包（第 1 卷）",
        "tar.zst.001" to "TAR.ZST 分卷压缩包（第 1 卷）",
        "tar.lz4.001" to "TAR.LZ4 分卷压缩包（第 1 卷）",
        "tar.br.001" to "TAR.BR 分卷压缩包（第 1 卷）",
        "iso.001" to "ISO 光盘镜像分卷（第 1 卷）",
        "esd.001" to "Windows ESD 系统镜像分卷（第 1 卷）",
        "wim.001" to "Windows WIM 系统镜像分卷（第 1 卷）",
        "img.001" to "磁盘镜像分卷（第 1 卷）",
        "bin.001" to "二进制数据分卷（第 1 卷）",
        "dmg.001" to "macOS 磁盘映像分卷（第 1 卷）",
        "qcow2.001" to "QEMU 虚拟磁盘分卷（第 1 卷）",
        "vmdk.001" to "VMware 虚拟磁盘分卷（第 1 卷）",
        "vhd.001" to "虚拟硬盘分卷（第 1 卷）",
        "vhdx.001" to "虚拟硬盘分卷（第 1 卷）",
        "exe.001" to "Windows 程序分卷（第 1 卷）",
        "nii.gz" to "NIfTI 压缩医学影像",
        "osm.pbf" to "OpenStreetMap PBF 数据",
        "d.ts" to "TypeScript 声明文件", "min.js" to "压缩版 JavaScript", "min.css" to "压缩版 CSS",
        "user.js" to "浏览器用户脚本", "user.css" to "浏览器用户样式", "schema.json" to "JSON Schema 描述文件"
    ) + (1..99).associate { "%03d".format(it) to "分卷 / 拆分文件（第 $it 卷）" } +
        (0..99).associate { "r%02d".format(it) to "RAR 分卷（第 $it 卷）" } +
        (1..99).associate { "z%02d".format(it) to "ZIP 分卷（第 $it 卷）" }

    /** .ts 双含义：只有明确的 MPEG-TS 视频 MIME 才当视频，其余按 TypeScript 源码处理 */
    private fun isTsCode(mime: String): Boolean {
        val m = mime.lowercase()
        return !(m.contains("mp2t") || m.contains("mpegts") || m.contains("m2ts"))
    }


    /** 任意第 N 卷的显示：ISO.002 也要显示“ISO 光盘镜像分卷（第 2 卷）” */
    private fun splitVolumeLabel(name: String): String? {
        val m = Regex("(.+)\\.(\\d{3})").matchEntire(name) ?: return null
        val base = m.groupValues[1]
        val index = m.groupValues[2].toIntOrNull() ?: return null
        val inner = extOf(base)
        if (inner.isEmpty()) return null
        val kind = when (inner) {
            "iso" -> "ISO 光盘镜像"
            "esd" -> "Windows ESD 系统镜像"
            "wim" -> "Windows WIM 系统镜像"
            "img" -> "磁盘镜像"
            "dmg" -> "macOS 磁盘映像"
            "qcow2" -> "QEMU 虚拟磁盘"
            "vmdk" -> "VMware 虚拟磁盘"
            "vhd", "vhdx" -> "虚拟硬盘"
            "exe" -> "Windows 程序"
            "zip" -> "ZIP 压缩包"
            "rar" -> "RAR 压缩包"
            "7z" -> "7Z 压缩包"
            "tar" -> "TAR 归档"
            "tar.gz", "gz", "tgz" -> "TAR.GZ / GZIP 压缩包"
            "tar.bz2", "bz2", "tbz", "tbz2" -> "TAR.BZ2 / BZIP2 压缩包"
            "tar.xz", "xz", "txz" -> "TAR.XZ / XZ 压缩包"
            "tar.zst", "zst", "tzst" -> "TAR.ZST / Zstandard 压缩包"
            "tar.lz4", "lz4", "tlz4" -> "TAR.LZ4 / LZ4 压缩包"
            "tar.br", "br", "tbr" -> "TAR.BR / Brotli 压缩包"
            "tar.lzma", "lzma", "tlz" -> "TAR.LZMA 压缩包"
            "tar.z", "z" -> "TAR.Z / Unix 压缩包"
            else -> null
        } ?: return null
        return "${kind}分卷（第 $index 卷）"
    }

    /** 人类可读的文件类型标签，一眼看懂；未知扩展给出“未知类型”而不是原样吐 tx2 */
    fun labelOf(name: String, mime: String = "", file: File? = null): String {
        val e = extOf(name)
        splitVolumeLabel(name)?.let { return it }
        COMPOUND_LABEL[e]?.let { return it }
        if (e == "ts" && isTsCode(mime)) return "TypeScript 源码"
        EXT_LABEL[e]?.let { return it }
        moreLabel(e)?.let { return it }
        more2Label(e)?.let { return it }
        more3Label(e)?.let { return it }
        more4Label(e)?.let { return it }
        more5Label(e)?.let { return it }
        val known = when {
            e == "apk" || e == "xapk" -> "APK 安装包"
            e == "apks" -> "APKS 安装包"
            e in IMAGE_EXT -> "图片 · ${e.uppercase()}"
            e in VIDEO_EXT -> "视频 · ${e.uppercase()}"
            e in AUDIO_EXT -> "音频 · ${e.uppercase()}"
            e in ARCHIVE_EXT || e.contains("tar.") || e.matches(Regex("(\\d{3}|r\\d{2}|z\\d{2})")) -> "压缩包 · ${e.uppercase()}"
            e == "pdf" -> "PDF 文档"
            e == "doc" || e == "docx" -> "Word 文档"
            e == "xls" || e == "xlsx" || e == "csv" -> "表格 · ${e.uppercase()}"
            e == "ppt" || e == "pptx" -> "幻灯片 · ${e.uppercase()}"
            e in TEXT_EXT -> "文本 · ${e.uppercase()}"
            e.isEmpty() && mime.startsWith("text/") -> "文本文件"
            else -> ""
        }
        if (known.isNotEmpty()) return known
        if (e.isEmpty() && file != null && file.isFile) {
            val magic = try { detectByMagic(file) } catch (_: Throwable) { null }
            if (magic != null) {
                val c = when {
                    magic in IMAGE_EXT -> "图片"
                    magic in VIDEO_EXT -> "视频"
                    magic in AUDIO_EXT -> "音频"
                    magic in ARCHIVE_EXT -> "压缩包"
                    magic == "pdf" -> "PDF 文档"
                    magic == "txt" -> "文本"
                    else -> "文件"
                }
                return "$c（按内容识别 · ${magic.uppercase()}）"
            }
        }
        return if (e.isEmpty()) "无后缀 · 未知类型" else "文件 · ${e.uppercase()}"
    }

    private val ZIP_FAMILY = setOf(
        "zip", "apk", "xapk", "apks", "jar", "war", "docx", "xlsx", "pptx", "epub",
        "odt", "ods", "odp", "whl", "crx", "zipx", "egg", "vsix", "nupkg", "xpi", "oxt",
        "sb3", "love", "rpa", "hap", "hsp", "cba"
    )

    /** 读文件头判断真实类型，和扩展名对不上就返回提示，对的返回 null */
    fun mismatchHint(f: File): String? {
        if (!f.isFile || f.length() < 12) return null
        val head = ByteArray(16)
        try {
            java.io.FileInputStream(f).use { ins ->
                val n = ins.read(head)
                if (n < 4) return null
            }
        } catch (_: Throwable) { return null }
        val e = extOf(f.name)
        fun sig(vararg b: Int): Boolean {
            for (i in b.indices) if (head[i].toInt() and 0xFF != b[i]) return false
            return true
        }
        val real = when {
            sig(0x50, 0x4B, 0x03, 0x04) || sig(0x50, 0x4B, 0x05, 0x06) -> "zip"
            sig(0x25, 0x50, 0x44, 0x46) -> "pdf"
            sig(0xFF, 0xD8, 0xFF) -> "jpg"
            sig(0x89, 0x50, 0x4E, 0x47) -> "png"
            sig(0x47, 0x49, 0x46, 0x38) -> "gif"
            sig(0x52, 0x61, 0x72, 0x21, 0x1A, 0x07) -> "rar"
            sig(0x37, 0x7A, 0xBC, 0xAF) -> "7z"
            sig(0x66, 0x4C, 0x61, 0x43) -> "flac"
            sig(0x4F, 0x67, 0x67, 0x53) -> "ogg"
            sig(0x1F, 0x8B) -> "gz"
            sig(0xFD, 0x37, 0x7A, 0x58, 0x5A) -> "xz"
            else -> null
        } ?: return null
        val same = when (real) {
            "zip" -> e in ZIP_FAMILY || e == "cbz" || e == "kepub"
            "7z" -> e == "7z" || e == "cb7"
            "pdf" -> e == "pdf"
            "jpg" -> e in setOf("jpg", "jpeg", "jfif", "jpe")
            "png" -> e == "png"
            "gif" -> e == "gif"

            "flac" -> e == "flac"
            "ogg" -> e in setOf("ogg", "oga", "opus", "spx")
            "gz" -> e in setOf("gz", "tgz", "tar.gz", "tar.gz.001")
            "xz" -> e in setOf("xz", "txz", "tar.xz")
            "rar" -> e in setOf("rar", "cbr")
            else -> true
        }
        if (same) return null
        val pretty = when (real) {
            "zip" -> if (e == "apk") "APK" else "ZIP/压缩包或 Office 文档"
            else -> real.uppercase()
        }
        return "注意：这个文件内容其实是 $pretty（扩展名 .$e 与实际内容不符，可能是被改过后缀）"
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
            "exe" -> "application/vnd.microsoft.portable-executable"
            "msi" -> "application/x-msi"
            "dmg" -> "application/x-apple-diskimage"
            "deb" -> "application/vnd.debian.binary-package"
            "rpm" -> "application/x-rpm"
            "iso" -> "application/x-iso9660-image"
            "tar.gz" -> "application/gzip"
            "tar.bz2" -> "application/x-bzip2"
            "tar.xz" -> "application/x-xz"
            "tar.zst" -> "application/zstd"
            "tar.lz4" -> "application/x-lz4"
            "tar.lzma" -> "application/x-lzma"
            "d.ts" -> "text/typescript"
            "min.js", "user.js" -> "application/javascript"
            "min.css", "user.css" -> "text/css"
            "schema.json" -> "application/schema+json"
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
