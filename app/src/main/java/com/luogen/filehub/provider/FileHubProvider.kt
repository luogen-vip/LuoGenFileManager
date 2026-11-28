package com.luogen.filehub.provider

import android.content.ContentResolver
import android.database.Cursor
import android.database.MatrixCursor
import android.os.CancellationSignal
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.os.storage.StorageManager
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import android.provider.DocumentsContract.Root
import android.provider.DocumentsProvider
import android.util.Base64
import android.webkit.MimeTypeMap
import com.luogen.filehub.data.FileRepo
import java.io.File
import java.io.FileNotFoundException

/**
 * 让「罗根文件管家」出现在系统的文件选择器里（安全框架 SAF）。
 *
 * 效果：别的应用点“上传 / 打开文档 / 选择文件”时，左侧来源列表里会多出「罗根文件管家」，
 * 点进去能看到手机里的文件夹和文件，选中后直接把文件交给对方应用（content:// 授权，不复制）。
 *
 * 权限：只用 MANAGE_DOCUMENTS 作为提供方权限，只有系统选择器能调用，别的 App 拿不到。
 * 可访问范围：只开放 /storage 下的真实文件（含 SD 卡），拒绝任何越界路径。
 */
class FileHubProvider : DocumentsProvider() {

    companion object {
        const val AUTHORITY = "com.luogen.filehub.documents"
        private const val ROOT_ID = "luogen"
        private const val DOC_ROOT = "root"
        private val DEFAULT_PROJECTION = arrayOf(
            Document.COLUMN_DOCUMENT_ID,
            Document.COLUMN_DISPLAY_NAME,
            Document.COLUMN_MIME_TYPE,
            Document.COLUMN_SIZE,
            Document.COLUMN_LAST_MODIFIED,
            Document.COLUMN_FLAGS
        )

        /** 给别的 App 用的文件地址（SD 卡等 FileProvider 覆盖不到的路径走这里） */
        fun uriFor(ctx: android.content.Context, f: File): android.net.Uri {
            val id = Base64.encodeToString(f.absolutePath.toByteArray(Charsets.UTF_8), Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
            return DocumentsContract.buildDocumentUri(ctx.packageName + ".documents", id)
        }
    }

    // ---------------- 编码 ----------------

    private fun encode(f: File): String =
        Base64.encodeToString(f.absolutePath.toByteArray(Charsets.UTF_8), Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)

    private fun decode(id: String): File {
        val raw = String(Base64.decode(id, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING), Charsets.UTF_8)
        return File(raw)
    }

    /** 只允许访问外部存储里的真实文件，防止路径穿越到应用私有目录 */
    private fun allowed(f: File): Boolean {
        val p = try { f.canonicalPath } catch (_: Throwable) { f.absolutePath }
        return p.startsWith("/storage/") || p == Environment.getExternalStorageDirectory().canonicalPath
    }

    private fun fileOf(documentId: String): File {
        if (documentId == DOC_ROOT) return Environment.getExternalStorageDirectory()
        val f = decode(documentId)
        if (!allowed(f)) throw FileNotFoundException("不允许访问：${f.absolutePath}")
        if (!f.exists()) throw FileNotFoundException("文件不存在：${f.absolutePath}")
        return f
    }

    private fun mimeOf(f: File): String {
        if (f.isDirectory) return Document.MIME_TYPE_DIR
        val name = f.name
        val ext = name.substringAfterLast('.', "").lowercase()
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "application/octet-stream"
    }

    // ---------------- 根目录 ----------------

    override fun queryRoots(projection: Array<out String>?): Cursor {
        val cols = projection ?: arrayOf(
            Root.COLUMN_ROOT_ID, Root.COLUMN_DOCUMENT_ID, Root.COLUMN_TITLE,
            Root.COLUMN_FLAGS, Root.COLUMN_ICON, Root.COLUMN_SUMMARY, Root.COLUMN_AVAILABLE_BYTES
        )
        val c = MatrixCursor(cols)
        val primary = Environment.getExternalStorageDirectory()
        c.newRow()
            .add(Root.COLUMN_ROOT_ID, ROOT_ID)
            .add(Root.COLUMN_DOCUMENT_ID, DOC_ROOT)
            .add(Root.COLUMN_TITLE, "罗根文件管家")
            .add(Root.COLUMN_SUMMARY, "手机存储")
            .add(Root.COLUMN_ICON, com.luogen.filehub.R.mipmap.ic_launcher)
            .add(
                Root.COLUMN_FLAGS,
                Root.FLAG_SUPPORTS_CREATE or Root.FLAG_SUPPORTS_IS_CHILD or
                    Root.FLAG_SUPPORTS_SEARCH or Root.FLAG_LOCAL_ONLY
            )
            .add(Root.COLUMN_AVAILABLE_BYTES, primary.usableSpace)

        // SD 卡等第二存储卷也单独列一个来源，方便直接选
        try {
            val sm = context?.getSystemService(StorageManager::class.java)
            sm?.storageVolumes?.forEach { v ->
                val dir: File? = try {
                    if (android.os.Build.VERSION.SDK_INT >= 30) v.directory else {
                        val m = v.javaClass.getMethod("getPathFile")
                        m.invoke(v) as? File
                    }
                } catch (_: Throwable) { null }
                if (dir != null && dir.exists() && dir.absolutePath != primary.absolutePath &&
                    v.state == Environment.MEDIA_MOUNTED
                ) {
                    val id = encode(dir)
                    c.newRow()
                        .add(Root.COLUMN_ROOT_ID, ROOT_ID + ":" + id)
                        .add(Root.COLUMN_DOCUMENT_ID, id)
                        .add(Root.COLUMN_TITLE, "罗根文件管家")
                        .add(Root.COLUMN_SUMMARY, v.getDescription(context))
                        .add(Root.COLUMN_ICON, com.luogen.filehub.R.mipmap.ic_launcher)
                        .add(Root.COLUMN_FLAGS, Root.FLAG_SUPPORTS_CREATE or Root.FLAG_SUPPORTS_IS_CHILD or Root.FLAG_LOCAL_ONLY)
                        .add(Root.COLUMN_AVAILABLE_BYTES, dir.usableSpace)
                }
            }
        } catch (_: Throwable) {
        }
        return c
    }

    // ---------------- 单个文档 ----------------

    override fun queryDocument(documentId: String, projection: Array<out String>?): Cursor {
        val c = MatrixCursor(projection ?: DEFAULT_PROJECTION)
        includeFile(c, fileOf(documentId))
        return c
    }

    private fun includeFile(c: MatrixCursor, f: File) {
        val id = encode(f)
        var flags = 0
        if (f.isDirectory) {
            flags = flags or Document.FLAG_DIR_PREFERS_GRID or Document.FLAG_DIR_SUPPORTS_CREATE
        } else {
            flags = flags or Document.FLAG_SUPPORTS_WRITE
            if (f.canWrite()) flags = flags or Document.FLAG_SUPPORTS_DELETE or Document.FLAG_SUPPORTS_RENAME
        }
        c.newRow()
            .add(Document.COLUMN_DOCUMENT_ID, id)
            .add(Document.COLUMN_DISPLAY_NAME, f.name)
            .add(Document.COLUMN_MIME_TYPE, mimeOf(f))
            .add(Document.COLUMN_SIZE, if (f.isFile) f.length() else null)
            .add(Document.COLUMN_LAST_MODIFIED, f.lastModified())
            .add(Document.COLUMN_FLAGS, flags)
    }

    override fun getDocumentType(documentId: String): String = mimeOf(fileOf(documentId))

    // ---------------- 子目录 ----------------

    override fun queryChildDocuments(
        parentDocumentId: String,
        projection: Array<out String>?,
        sortOrder: String?
    ): Cursor {
        val parent = fileOf(parentDocumentId)
        val c = MatrixCursor(projection ?: DEFAULT_PROJECTION)
        val list = try {
            parent.listFiles()?.toList() ?: emptyList()
        } catch (_: Throwable) {
            emptyList()
        }
        list.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() })).forEach {
            try { includeFile(c, it) } catch (_: Throwable) {}
        }
        return c
    }

    override fun querySearchDocuments(
        rootId: String?,
        query: String?,
        projection: Array<out String>?
    ): Cursor {
        val c = MatrixCursor(projection ?: DEFAULT_PROJECTION)
        val q = (query ?: "").trim()
        if (q.isEmpty()) return c
        val base = Environment.getExternalStorageDirectory()
        val hits = ArrayList<File>()
        fun walk(dir: File, depth: Int) {
            if (depth > 6 || hits.size >= 200) return
            val kids = try { dir.listFiles() } catch (_: Throwable) { null } ?: return
            for (k in kids) {
                if (k.name.startsWith(".")) continue
                if (k.name.contains(q, true)) hits.add(k)
                if (k.isDirectory && hits.size < 200) walk(k, depth + 1)
                if (hits.size >= 200) return
            }
        }
        try { walk(base, 0) } catch (_: Throwable) {}
        hits.forEach { try { includeFile(c, it) } catch (_: Throwable) {} }
        return c
    }

    override fun isChildDocument(parentDocumentId: String, documentId: String): Boolean {
        return try {
            val p = fileOf(parentDocumentId).canonicalPath
            val child = fileOf(documentId).canonicalPath
            child.startsWith(if (p.endsWith(File.separator)) p else p + File.separator)
        } catch (_: Throwable) {
            false
        }
    }

    // ---------------- 打开文件 ----------------

    override fun openDocument(
        documentId: String,
        mode: String,
        signal: CancellationSignal?
    ): ParcelFileDescriptor {
        val f = fileOf(documentId)
        if (f.isDirectory) throw FileNotFoundException("不能直接打开文件夹")
        val access = when (mode) {
            "r" -> ParcelFileDescriptor.MODE_READ_ONLY
            "w", "wt" -> ParcelFileDescriptor.MODE_WRITE_ONLY or ParcelFileDescriptor.MODE_TRUNCATE or ParcelFileDescriptor.MODE_CREATE
            "wa" -> ParcelFileDescriptor.MODE_WRITE_ONLY or ParcelFileDescriptor.MODE_APPEND or ParcelFileDescriptor.MODE_CREATE
            "rw", "rwt" -> ParcelFileDescriptor.MODE_READ_WRITE or ParcelFileDescriptor.MODE_CREATE
            else -> ParcelFileDescriptor.MODE_READ_ONLY
        }
        return ParcelFileDescriptor.open(f, access)
    }

    // ---------------- 新建 / 删除 / 改名（完整的提供方能力） ----------------

    override fun createDocument(parentDocumentId: String, mimeType: String, displayName: String): String {
        val parent = fileOf(parentDocumentId)
        if (!parent.isDirectory) throw FileNotFoundException("不是文件夹")
        val target = File(parent, displayName)
        if (!allowed(target)) throw FileNotFoundException("不允许写入这个位置")
        val ok = if (mimeType == Document.MIME_TYPE_DIR) target.mkdirs() else {
            try {
                target.parentFile?.mkdirs()
                target.createNewFile()
            } catch (_: Throwable) { false }
        }
        if (!ok && !target.exists()) throw FileNotFoundException("创建失败：$displayName")
        return encode(target)
    }

    override fun deleteDocument(documentId: String) {
        val f = fileOf(documentId)
        if (!allowed(f)) throw FileNotFoundException("不允许删除")
        if (!f.delete()) throw FileNotFoundException("删除失败：${f.name}")
    }

    override fun renameDocument(documentId: String, displayName: String): String {
        val f = fileOf(documentId)
        val target = File(f.parentFile, displayName)
        if (!allowed(target)) throw FileNotFoundException("不允许改名到该位置")
        if (!f.renameTo(target)) throw FileNotFoundException("改名失败")
        return encode(target)
    }

    // ---------------- 对外工具 ----------------


    // ---------------- 生命周期 ----------------

    override fun onCreate(): Boolean = true
}
