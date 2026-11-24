package com.luogen.filehub.data

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.os.storage.StorageManager
import android.app.usage.StorageStatsManager
import android.provider.MediaStore
import com.luogen.filehub.model.Cat
import com.luogen.filehub.model.FileItem
import com.luogen.filehub.model.SortMode
import com.luogen.filehub.model.StorageInfo
import com.luogen.filehub.model.VolumeInfo
import com.luogen.filehub.model.extOf
import java.io.File

class FileRepo(private val ctx: Context) {

    private val resolver get() = ctx.contentResolver
    private val pm get() = ctx.packageManager

    // ------------------------- 通用工具 -------------------------

    private val cols = arrayOf(
        MediaStore.MediaColumns._ID, MediaStore.MediaColumns.DISPLAY_NAME,
        MediaStore.MediaColumns.SIZE, MediaStore.MediaColumns.DATE_MODIFIED,
        MediaStore.MediaColumns.DATA, MediaStore.MediaColumns.MIME_TYPE
    )

    /**
     * 只读 MediaStore 索引，不再对每条做 File.exists()（15k 文件时这一步会卡十几秒）。
     * 真实存在性在打开文件时再校验。
     */
    private fun query(
        uri: Uri,
        selection: String? = null,
        selectionArgs: Array<String>? = null,
        sort: String? = null,
        limit: Int = 0
    ): List<FileItem> {
        val out = ArrayList<FileItem>()
        try {
            resolver.query(uri, cols, selection, selectionArgs, sort)?.use { c ->
                val idIdx = c.getColumnIndex(MediaStore.MediaColumns._ID)
                val nameIdx = c.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME)
                val sizeIdx = c.getColumnIndex(MediaStore.MediaColumns.SIZE)
                val dateIdx = c.getColumnIndex(MediaStore.MediaColumns.DATE_MODIFIED)
                val dataIdx = c.getColumnIndex(MediaStore.MediaColumns.DATA)
                val mimeIdx = c.getColumnIndex(MediaStore.MediaColumns.MIME_TYPE)
                while (c.moveToNext()) {
                    val name = if (nameIdx >= 0) c.getString(nameIdx) ?: "" else ""
                    val data = if (dataIdx >= 0) c.getString(dataIdx) ?: "" else ""
                    if (data.isEmpty()) continue
                    out.add(
                        FileItem(
                            path = data,
                            name = name.ifEmpty { File(data).name },
                            size = if (sizeIdx >= 0) c.getLong(sizeIdx) else 0L,
                            modified = (if (dateIdx >= 0) c.getLong(dateIdx) else 0L) * 1000L,
                            mime = if (mimeIdx >= 0) c.getString(mimeIdx) ?: "" else "",
                            id = if (idIdx >= 0) c.getLong(idIdx).toString() else data
                        )
                    )
                    if (limit > 0 && out.size >= limit) break
                }
            }
        } catch (_: Exception) {
        }
        return out
    }

    private fun countUri(uri: Uri, selection: String? = null): Int = try {
        resolver.query(uri, arrayOf(MediaStore.MediaColumns._ID), selection, null, null)?.use { it.count } ?: 0
    } catch (_: Exception) {
        0
    }

    fun images(): List<FileItem> = query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
    fun videos(): List<FileItem> = query(MediaStore.Video.Media.EXTERNAL_CONTENT_URI)
    fun audios(): List<FileItem> = query(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI)

    /** 所有文件（MediaStore 索引到的） */
    fun allMediaFiles(): List<FileItem> = query(MediaStore.Files.getContentUri("external"))

    /** 全局搜索：走 MediaStore 索引的 LIKE 查询，不扫全盘 */
    fun searchAll(q: String, limit: Int = 200): List<FileItem> {
        val key = q.trim()
        if (key.isEmpty()) return recent(limit)
        val uri = MediaStore.Files.getContentUri("external")
        val selection = "${MediaStore.MediaColumns.DISPLAY_NAME} LIKE ?"
        return query(
            uri,
            selection = selection,
            selectionArgs = arrayOf("%$key%"),
            sort = "${MediaStore.MediaColumns.DATE_MODIFIED} DESC",
            limit = limit
        )
    }

    /** 最近文件（按修改时间倒序） */
    fun recent(limit: Int = 200): List<FileItem> = query(
        MediaStore.Files.getContentUri("external"),
        sort = "${MediaStore.MediaColumns.DATE_MODIFIED} DESC",
        limit = limit
    )

    private val docFilter: (FileItem) -> Boolean = { it.ext in TypeDetect.DOC_EXT }
    private val archiveFilter: (FileItem) -> Boolean = { it.ext in TypeDetect.ARCHIVE_EXT && it.ext != "apk" }
    private val apkFilter: (FileItem) -> Boolean = { it.ext == "apk" }

    fun docs(): List<FileItem> = allMediaFiles().filter(docFilter)
    fun archives(): List<FileItem> = allMediaFiles().filter(archiveFilter)
    fun apkFiles(): List<FileItem> = allMediaFiles().filter(apkFilter)

    /** 下载：Download 集合 + 最近 30 天新增文件（全盘，不限目录），按时间倒序 */
    fun downloads(): List<FileItem> {
        val map = LinkedHashMap<String, FileItem>()
        if (Build.VERSION.SDK_INT >= 29) {
            query(MediaStore.Downloads.EXTERNAL_CONTENT_URI).forEach { map[it.path] = it }
        }
        val cutoff = System.currentTimeMillis() - 30L * 24 * 3600 * 1000
        allMediaFiles().forEach {
            if (it.modified >= cutoff) map[it.path] = it
        }
        return map.values.sortedByDescending { it.modified }
    }

    fun favorites(): List<FileItem> {
        val set = Prefs.favorites()
        return set.mapNotNull { p ->
            val f = File(p)
            if (!f.exists() || !f.isFile) return@mapNotNull null
            FileItem(
                path = p, name = f.name, size = f.length(), modified = f.lastModified(),
                mime = TypeDetect.mimeOf(f.name)
            )
        }.sortedByDescending { it.modified }
    }

    fun listDir(dir: File): List<FileItem> {
        val files = dir.listFiles() ?: return emptyList()
        val showHidden = Prefs.showHidden()
        return files.filter { showHidden || !it.name.startsWith(".") }
            .map { f ->
                FileItem(
                    path = f.absolutePath, name = f.name, size = if (f.isDirectory) 0L else f.length(),
                    modified = f.lastModified(), isDir = f.isDirectory,
                    mime = if (f.isDirectory) "" else TypeDetect.mimeOf(f.name)
                )
            }
            // 文件夹永远排在文件前面
            .sortedWith(
                compareByDescending<FileItem> { it.isDir }
                    .thenBy(String.CASE_INSENSITIVE_ORDER) { it.name }
            )
    }

    fun installedApps(): List<FileItem> {
        val list = ArrayList<FileItem>()
        try {
            val apps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
            for (ai in apps) {
                val label = pm.getApplicationLabel(ai).toString()
                val version = try {
                    pm.getPackageInfo(ai.packageName, 0).versionName ?: ""
                } catch (_: Exception) { "" }
                val src = ai.publicSourceDir ?: ai.sourceDir ?: ""
                list.add(
                    FileItem(
                        path = src, name = ai.packageName, size = File(src).length(), modified = 0L,
                        id = ai.packageName, label = label, packageName = ai.packageName,
                        versionName = version,
                        extra = if (ai.flags and ApplicationInfo.FLAG_SYSTEM != 0) "系统应用" else "第三方应用"
                    )
                )
            }
        } catch (_: Exception) {
        }
        return list.sortedBy { it.label.lowercase() }
    }

    fun apkInfo(path: String): FileItem {
        val f = File(path)
        var label = f.name
        var pkg = ""
        var ver = ""
        try {
            val info = pm.getPackageArchiveInfo(path, 0)
            if (info != null) {
                val ai = info.applicationInfo
                if (ai != null) {
                    ai.publicSourceDir = path
                    ai.sourceDir = path
                    label = pm.getApplicationLabel(ai).toString()
                }
                pkg = info.packageName
                ver = info.versionName ?: ""
            }
        } catch (_: Exception) {}
        return FileItem(
            path = path, name = f.name, size = f.length(), modified = f.lastModified(),
            mime = "application/vnd.android.package-archive", label = label,
            packageName = pkg, versionName = ver
        )
    }

    fun load(cat: Cat): List<FileItem> = when (cat) {
        Cat.IMAGE -> images()
        Cat.VIDEO -> videos()
        Cat.AUDIO -> audios()
        Cat.DOC -> docs()
        Cat.ARCHIVE -> archives()
        Cat.APP -> apkFiles()
        Cat.FAV -> favorites()
        Cat.DOWNLOAD -> downloads()
        Cat.TRASH -> emptyList()
        Cat.VAULT -> emptyList()
        Cat.FILE -> emptyList()
        Cat.CLEAN -> emptyList()
    }

    fun storage(): StorageInfo {
        var total = 0L
        var free = 0L
        try {
            val stat = StatFs(Environment.getExternalStorageDirectory().path)
            total = stat.blockCountLong * stat.blockSizeLong
            free = stat.availableBlocksLong * stat.blockSizeLong
        } catch (_: Exception) {
        }
        // 部分机型 StatFs 只给出 data 分区，容量偏小；尝试用 StorageStatsManager 拿到整机总容量
        if (Build.VERSION.SDK_INT >= 26) {
            try {
                val ssm = ctx.getSystemService(StorageStatsManager::class.java)
                val bigger = ssm.getTotalBytes(StorageManager.UUID_DEFAULT)
                if (bigger > total) {
                    total = bigger
                    free = ssm.getFreeBytes(StorageManager.UUID_DEFAULT)
                }
            } catch (_: Throwable) {
            }
        }
        return StorageInfo(total = total, free = free)
    }

    /** 外部存储卷：内部存储 + U 盘 / SD 卡（插拔后调用可即时反映） */
    fun volumes(): List<VolumeInfo> {
        val out = ArrayList<VolumeInfo>()
        try {
            val sm = ctx.getSystemService(StorageManager::class.java)
            val ssm = if (Build.VERSION.SDK_INT >= 26) ctx.getSystemService(StorageStatsManager::class.java) else null
            val primary = Environment.getExternalStorageDirectory().absolutePath
            sm.storageVolumes.forEach { v ->
                var dir: File? = null
                try {
                    if (Build.VERSION.SDK_INT >= 30) dir = v.directory
                    else {
                        val m = v.javaClass.getMethod("getPathFile")
                        dir = m.invoke(v) as? File
                    }
                } catch (_: Throwable) {
                }
                val state = v.state
                if (dir == null || !dir.exists() || state != Environment.MEDIA_MOUNTED) return@forEach
                var total = 0L; var free = 0L
                try {
                    try {
                        val uuid = StorageManager.UUID_DEFAULT.let { def -> if (v.isPrimary) def else null }
                        if (ssm != null && uuid != null) {
                            total = ssm.getTotalBytes(uuid)
                            free = ssm.getFreeBytes(uuid)
                        } else if (ssm != null && !v.isPrimary) {
                            // 可移动存储：用反射拿 UUID，拿不到就退回 StatFs
                            val u = v.uuid?.let { java.util.UUID.fromString(it) } ?: java.util.UUID.nameUUIDFromBytes(v.uuid?.toByteArray() ?: ByteArray(0))
                            total = ssm.getTotalBytes(u)
                            free = ssm.getFreeBytes(u)
                        }
                    } catch (_: Throwable) {}
                } catch (_: Throwable) {}
                if (total <= 0) {
                    try {
                        val st = StatFs(dir.absolutePath)
                        total = st.blockCountLong * st.blockSizeLong
                        free = st.availableBlocksLong * st.blockSizeLong
                    } catch (_: Throwable) {}
                }
                val isPrimary = dir.absolutePath == primary
                val label = try { v.getDescription(ctx) } catch (_: Throwable) { "" }
                out.add(
                    VolumeInfo(
                        id = v.uuid ?: dir.absolutePath,
                        name = if (isPrimary) "内部存储" else label.ifEmpty { "U盘 / SD卡" },
                        path = dir.absolutePath,
                        total = total, free = free, removable = v.isRemovable
                    )
                )
            }
        } catch (_: Throwable) {
        }
        if (out.none { it.path == Environment.getExternalStorageDirectory().absolutePath }) {
            val s = storage()
            out.add(0, VolumeInfo(id = "primary", name = "内部存储",
                path = Environment.getExternalStorageDirectory().absolutePath,
                total = s.total, free = s.free, removable = false))
        }
        return out.sortedBy { it.removable }
    }

    /** 首页统计：只统计数量，不加载列表、不做 File.exists() */
    fun counts(): Map<Cat, Int> {
        val map = HashMap<Cat, Int>()
        map[Cat.IMAGE] = countUri(MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        map[Cat.VIDEO] = countUri(MediaStore.Video.Media.EXTERNAL_CONTENT_URI)
        map[Cat.AUDIO] = countUri(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI)

        var doc = 0
        var archive = 0
        var apk = 0
        try {
            resolver.query(
                MediaStore.Files.getContentUri("external"),
                arrayOf(MediaStore.MediaColumns.DISPLAY_NAME),
                null, null, null
            )?.use { c ->
                while (c.moveToNext()) {
                    val n = c.getString(0) ?: continue
                    val e = n.substringAfterLast('.', "").lowercase()
                    when {
                        e == "apk" -> apk++
                        e in TypeDetect.ARCHIVE_EXT -> archive++
                        e in TypeDetect.DOC_EXT -> doc++
                    }
                }
            }
        } catch (_: Exception) {
        }
        map[Cat.DOC] = doc
        map[Cat.ARCHIVE] = archive
        map[Cat.APP] = apk + try { pm.getInstalledApplications(0).size } catch (_: Throwable) { 0 }
        map[Cat.FAV] = Prefs.favorites().size
        map[Cat.DOWNLOAD] = if (Build.VERSION.SDK_INT >= 29) countUri(MediaStore.Downloads.EXTERNAL_CONTENT_URI) else 0
        return map
    }
}

object Sorter {
    fun sort(items: List<FileItem>, mode: SortMode, asc: Boolean, dirsFirst: Boolean = false): List<FileItem> {
        val base = items.sortedWith(
            when (mode) {
                SortMode.TIME -> compareBy { it.modified }
                SortMode.SIZE -> compareBy { it.size }
                SortMode.NAME -> compareBy(String.CASE_INSENSITIVE_ORDER) { it.name.ifEmpty { it.label } }
            }
        ).let { if (asc) it else it.reversed() }
        return if (dirsFirst) base.sortedByDescending { it.isDir } else base
    }
}
