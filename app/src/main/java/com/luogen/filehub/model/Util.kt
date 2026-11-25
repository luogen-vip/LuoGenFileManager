package com.luogen.filehub.model

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

fun Long.toSizeText(): String {
    if (this <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    var v = this.toDouble()
    var i = 0
    while (v >= 1024 && i < units.size - 1) {
        v /= 1024.0
        i++
    }
    return if (i == 0) "${this} B" else String.format(Locale.US, "%.2f %s", v, units[i])
}

private val hm = SimpleDateFormat("HH:mm", Locale.getDefault())
private val full = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
private val md = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())
private val dmy = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
private val ymd = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

fun Long.toFullTime(): String = full.format(Date(this))

/** 人性化时间：刚刚 / x分钟前 / 今天 HH:mm / 昨天 HH:mm / MM-dd HH:mm / 年-月-日 */
fun Long.toRelativeTime(): String {
    if (this <= 0) return "未知时间"
    val now = System.currentTimeMillis()
    val diff = now - this
    if (diff in 0..59_999) return "刚刚"
    if (diff in 60_000..3_599_999) return "${diff / 60_000}分钟前"
    val cal = Calendar.getInstance()
    val nowCal = Calendar.getInstance()
    cal.timeInMillis = this
    val sameDay = cal.get(Calendar.YEAR) == nowCal.get(Calendar.YEAR) &&
        cal.get(Calendar.DAY_OF_YEAR) == nowCal.get(Calendar.DAY_OF_YEAR)
    if (sameDay) return "今天 ${hm.format(Date(this))}"
    val yCal = Calendar.getInstance()
    yCal.add(Calendar.DAY_OF_YEAR, -1)
    val isYesterday = cal.get(Calendar.YEAR) == yCal.get(Calendar.YEAR) &&
        cal.get(Calendar.DAY_OF_YEAR) == yCal.get(Calendar.DAY_OF_YEAR)
    if (isYesterday) return "昨天 ${hm.format(Date(this))}"
    if (nowCal.get(Calendar.YEAR) == cal.get(Calendar.YEAR)) return md.format(Date(this))
    return dmy.format(Date(this))
}

/** 分组标题：今天 / 昨天 / 2026-11-20 */
fun Long.toDayTitle(): String {
    val cal = Calendar.getInstance().apply { timeInMillis = this@toDayTitle }
    val now = Calendar.getInstance()
    if (cal.get(Calendar.YEAR) == now.get(Calendar.YEAR) &&
        cal.get(Calendar.DAY_OF_YEAR) == now.get(Calendar.DAY_OF_YEAR)
    ) return "今天"
    val y = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }
    if (cal.get(Calendar.YEAR) == y.get(Calendar.YEAR) &&
        cal.get(Calendar.DAY_OF_YEAR) == y.get(Calendar.DAY_OF_YEAR)
    ) return "昨天"
    return ymd.format(Date(this))
}

/** 明确支持“两段/三段扩展名”的组合后缀；不在表里的仍按最后一个点判断，避免误伤 a.b.txt */
private val COMPOUND_EXT = setOf(
    "tar.gz", "tar.bz2", "tar.xz", "tar.zst", "tar.lz4", "tar.lzma", "tar.lz", "tar.z", "tar.br",
    "7z.001", "zip.001", "rar.001", "tar.001", "tar.gz.001",
    "tar.zst.001", "tar.lz4.001", "tar.br.001",
    "iso.001", "esd.001", "wim.001", "img.001", "bin.001", "dmg.001",
    "qcow2.001", "vmdk.001", "vhd.001", "vhdx.001", "exe.001",
    "nii.gz", "osm.pbf",
    "d.ts", "min.js", "min.css", "user.js", "user.css", "schema.json"
)

fun nameNoExt(name: String): String {
    val n = name.trim()
    val e = extOf(n)
    if (e.isEmpty()) return n
    return n.substring(0, n.length - e.length - 1)
}

fun extOf(name: String): String {
    // 先匹配已知组合后缀（tar.gz、7z.001、d.ts 等），否则仍按最后一个点判断；
    // 即使删到只剩 ".apk" 也算 apk，不把文件名里的点误当扩展名。
    val n = name.trim()
    val lower = n.lowercase(Locale.ROOT)
    val parts = lower.split('.')
    if (parts.size > 1) {
        val max = minOf(3, parts.size - 1)
        for (size in max downTo 2) {
            val candidate = parts.takeLast(size).joinToString(".")
            if (candidate in COMPOUND_EXT) return candidate
        }
    }
    val dot = n.lastIndexOf('.')
    return if (dot >= 0 && dot < n.length - 1) n.substring(dot + 1).lowercase(Locale.ROOT) else ""
}

fun parentPathOf(path: String): String {
    val f = java.io.File(path)
    return f.parent ?: path
}
