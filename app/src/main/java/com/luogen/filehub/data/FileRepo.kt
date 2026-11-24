package com.luogen.filehub.data

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.provider.MediaStore
import com.luogen.filehub.model.Cat
import com.luogen.filehub.model.FileItem
import com.luogen.filehub.model.SortMode
import com.luogen.filehub.model.StorageInfo
import com.luogen.filehub.model.extOf
import java.io.File

class FileRepo(private val ctx: Context) {

    private val resolver get() = ctx.contentResolver
    private val pm get() = ctx.packageManager

    // ------------------------- 通用工具 -------------------------

    private fun query(
        uri: Uri,
        projection: Array<String>,
        selection: String? = null
    ): List<FileItem> {
        val out = ArrayList<FileItem>()
        try {
            resolver.query(uri, projection, selection, null, null)?.use { c ->
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
                    val f = File(data)
                    if (!f.exists()) continue
                    out.add(
                        FileItem(
                            path = data,
                            name = name.ifEmpty { f.name },
                            size = if (sizeIdx >= 0) c.getLong(sizeIdx) else f.length(),
                            modified = (if (dateIdx >= 0) c.getLong(dateIdx) else 0L) * 1000L,
                            mime = if (mimeIdx >= 0) c.getString(mimeIdx) ?: "" else "",
                            id = if (idIdx >= 0) c.getLong(idIdx).toString() else data
                        )
                    )
                }
            }
        } catch (_: Exception) {
        }
        return out
    }

    fun images(): List<FileItem> = query(
        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
        arrayOf(
            MediaStore.MediaColumns._ID, MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.SIZE, MediaStore.MediaColumns.DATE_MODIFIED,
            MediaStore.MediaColumns.DATA, MediaStore.MediaColumns.MIME_TYPE
        )
    )

    fun videos(): List<FileItem> = query(
        MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
        arrayOf(
            MediaStore.MediaColumns._ID, MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.SIZE, MediaStore.MediaColumns.DATE_MODIFIED,
            MediaStore.MediaColumns.DATA, MediaStore.MediaColumns.MIME_TYPE
        )
    )

    fun audios(): List<FileItem> = query(
        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
        arrayOf(
            MediaStore.MediaColumns._ID, MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.SIZE, MediaStore.MediaColumns.DATE_MODIFIED,
            MediaStore.MediaColumns.DATA, MediaStore.MediaColumns.MIME_TYPE
        )
    )

    /** 所有文件（MediaStore 索引到的） */
    fun allMediaFiles(): List<FileItem> = query(
        MediaStore.Files.getContentUri("external"),
        arrayOf(
            MediaStore.MediaColumns._ID, MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.SIZE, MediaStore.MediaColumns.DATE_MODIFIED,
            MediaStore.MediaColumns.DATA, MediaStore.MediaColumns.MIME_TYPE
        )
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
            query(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                arrayOf(
                    MediaStore.MediaColumns._ID, MediaStore.MediaColumns.DISPLAY_NAME,
                    MediaStore.MediaColumns.SIZE, MediaStore.MediaColumns.DATE_MODIFIED,
                    MediaStore.MediaColumns.DATA, MediaStore.MediaColumns.MIME_TYPE
                )
            ).forEach { map[it.path] = it }
        }
        // 最近新增的文件，任何目录
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
            .sortedWith(compareByDescending<FileItem> { it.isDir }.thenByDescending { it.modified })
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
                val size = if (src.isNotEmpty()) File(src).length() else 0L
                list.add(
                    FileItem(
                        path = src, name = ai.packageName, size = size, modified = 0L,
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
        return try {
            val dir = Environment.getExternalStorageDirectory()
            val stat = StatFs(dir.path)
            StorageInfo(total = stat.blockCountLong * stat.blockSizeLong, free = stat.availableBlocksLong * stat.blockSizeLong)
        } catch (_: Exception) {
            StorageInfo()
        }
    }

    fun counts(): Map<Cat, Int> {
        val map = HashMap<Cat, Int>()
        map[Cat.IMAGE] = images().size
        map[Cat.VIDEO] = videos().size
        map[Cat.AUDIO] = audios().size
        val all = allMediaFiles()
        map[Cat.DOC] = all.count(docFilter)
        map[Cat.ARCHIVE] = all.count(archiveFilter)
        map[Cat.APP] = all.count(apkFilter) + installedApps().size
        map[Cat.FAV] = Prefs.favorites().size
        map[Cat.DOWNLOAD] = downloads().size
        return map
    }
}

object Sorter {
    fun sort(items: List<FileItem>, mode: SortMode, asc: Boolean): List<FileItem> {
        val base = items.sortedWith(
            when (mode) {
                SortMode.TIME -> compareBy { it.modified }
                SortMode.SIZE -> compareBy { it.size }
                SortMode.NAME -> compareBy(String.CASE_INSENSITIVE_ORDER) { it.name.ifEmpty { it.label } }
            }
        )
        return if (asc) base else base.reversed()
    }
}
