package com.luogen.filehub.model

enum class Cat(val title: String) {
    FILE("我的文件"),
    IMAGE("图片"),
    VIDEO("视频"),
    AUDIO("音频"),
    DOC("文档"),
    APP("应用"),
    ARCHIVE("压缩包"),
    FAV("收藏"),
    DOWNLOAD("下载"),
    TRASH("回收站"),
    VAULT("加密保险箱"),
    CLEAN("清理")
}

enum class SortMode(val title: String) {
    TIME("按时间"), SIZE("按大小"), NAME("按名称")
}

data class FileItem(
    val path: String = "",
    val name: String = "",
    val size: Long = 0L,
    val modified: Long = 0L,
    val isDir: Boolean = false,
    val mime: String = "",
    val id: String = "",
    val label: String = "",          // 已安装应用的显示名
    val packageName: String = "",
    val versionName: String = "",
    val extra: String = "",          // 附加信息，如原路径
    val encrypted: Boolean = false
) {
    val ext: String get() = name.substringAfterLast('.', "").lowercase()
    val key: String get() = if (id.isNotEmpty()) id else path
}

data class StorageInfo(val total: Long = 0L, val free: Long = 0L) {
    val used: Long get() = (total - free).coerceAtLeast(0L)
}
