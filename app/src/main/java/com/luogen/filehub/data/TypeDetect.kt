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
        "cr3", "crw", "dc2", "dcr", "k25", "kdc", "mos", "mrw", "pef", "srw", "x3f", "rw2", "erf", "mef", "iiq", "3fr"
    )

    val VIDEO_EXT = setOf(
        "mp4", "mkv", "avi", "mov", "wmv", "flv", "webm", "m4v", "3gp", "ts",
        "rmvb", "rm", "mpg", "mpeg", "vob", "asf", "ogv", "m2ts", "f4v", "divx", "mts",
        "mxf", "m2v", "m1v", "3g2", "3gpp", "qt", "yuv", "h264", "h265", "hevc", "vp9", "av1", "ts",
        "m3u8", "mpd", "dv", "f4p", "ogm", "rmhd", "swf", "viv", "amv", "cavs"
    )

    val AUDIO_EXT = setOf(
        "mp3", "wav", "flac", "aac", "m4a", "ogg", "oga", "opus", "wma", "amr",
        "ape", "aiff", "aif", "mid", "midi", "mka", "ac3", "dts", "m4b", "caf", "ra",
        "m4r", "amr-wb", "awb", "tta", "wv", "mpc", "spx", "gsm", "voc", "au", "snd", "3ga", "aob",
        "aifc", "mp4a", "dsf", "dff", "m3u", "pls", "xspf", "wpl", "cue", "kar", "smf", "mod", "xm", "it", "s3m"
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
        "diff", "patch", "rej", "orig", "bak", "tmp", "part", "crdownload", "download"
    )

    val ARCHIVE_EXT = setOf(
        "zip", "rar", "7z", "tar", "gz", "tgz", "bz2", "tbz", "xz", "txz", "zst",
        "iso", "cab", "jar", "war", "apks", "lz", "lzma", "arj", "cpio", "whl", "crx",
        "xz", "txz", "z", "lzh", "lha", "xar", "pkg", "deb", "rpm", "apkg", "xapk", "obb",
        "tar.gz", "tar.bz2", "tar.xz", "tgz", "tbz2", "zipx", "alz", "egg",
        "tar.zst", "tzst", "tar.lz", "tar.lzma", "zstd", "lz4", "br", "rar5", "part1", "r00", "r01",
        "001", "7z.001", "zip.001", "bzip2", "gzip", "compress", "zoo", "ace", "pea", "sit", "sitx", "hqx"
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
            e in VIDEO_EXT || mime.startsWith("video/") -> Cat.VIDEO
            e in AUDIO_EXT || mime.startsWith("audio/") -> Cat.AUDIO
            e in ARCHIVE_EXT || mime.contains("zip") || mime.contains("compressed") || mime.contains("x-7z") -> Cat.ARCHIVE
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
        "img" to "磁盘/系统镜像 · IMG", "dmg" to "macOS 磁盘映像", "toast" to "macOS 光盘映像",
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

    /** 人类可读的文件类型标签，一眼看懂；未知扩展给出“未知类型”而不是原样吐 tx2 */
    fun labelOf(name: String, mime: String = "", file: File? = null): String {
        val e = extOf(name)
        EXT_LABEL[e]?.let { return it }
        val known = when {
            e == "apk" || e == "xapk" -> "APK 安装包"
            e == "apks" -> "APKS 安装包"
            e in IMAGE_EXT -> "图片 · ${e.uppercase()}"
            e in VIDEO_EXT -> "视频 · ${e.uppercase()}"
            e in AUDIO_EXT -> "音频 · ${e.uppercase()}"
            e in ARCHIVE_EXT || e.contains("tar.") -> "压缩包 · ${e.uppercase()}"
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
        "odt", "ods", "odp", "whl", "crx", "zipx", "egg"
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
            "pdf" -> e == "pdf"
            "jpg" -> e in setOf("jpg", "jpeg", "jfif", "jpe")
            "png" -> e == "png"
            "gif" -> e == "gif"
            "rar" -> e == "rar" || e == "cbr"
            "7z" -> e == "7z"
            "flac" -> e == "flac"
            "ogg" -> e in setOf("ogg", "oga", "opus", "spx")
            "gz" -> e in setOf("gz", "tgz")
            "xz" -> e in setOf("xz", "txz")
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
