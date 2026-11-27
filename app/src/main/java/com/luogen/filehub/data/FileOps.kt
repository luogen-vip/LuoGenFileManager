package com.luogen.filehub.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.luogen.filehub.model.FileItem
import java.io.File

object FileOps {

    fun uniqueTarget(dir: File, name: String): File {
        var f = File(dir, name)
        if (!f.exists()) return f
        val base = name.substringBeforeLast('.', name)
        val ext = if (name.contains('.')) "." + name.substringAfterLast('.') else ""
        var i = 1
        while (f.exists()) {
            f = File(dir, "$base($i)$ext")
            i++
        }
        return f
    }

    fun copyRecursive(src: File, dst: File, onBytes: (Long) -> Unit = {}): Boolean = try {
        if (src.isDirectory) {
            dst.mkdirs()
            (src.listFiles() ?: emptyArray()).forEach { copyRecursive(it, File(dst, it.name), onBytes) }
            true
        } else {
            dst.parentFile?.mkdirs()
            src.inputStream().use { ins ->
                dst.outputStream().use { fos ->
                    val buf = ByteArray(128 * 1024)
                    var r = ins.read(buf)
                    while (r > 0) {
                        fos.write(buf, 0, r)
                        onBytes(r.toLong())
                        r = ins.read(buf)
                    }
                }
            }
            true
        }
    } catch (_: Throwable) {
        false
    }

    fun deleteRecursive(f: File): Boolean {
        if (f.isDirectory) f.listFiles()?.forEach { deleteRecursive(it) }
        return f.delete()
    }

    fun sizeOf(f: File): Long = if (f.isDirectory) {
        var s = 0L
        f.listFiles()?.forEach { s += sizeOf(it) }
        s
    } else f.length()

    /** 文件夹真实总大小；读不到（权限/路径消失）返回 null，避免把 0 B 当成真实大小 */
    fun sizeOfOrNull(f: File, depth: Int = 0): Long? {
        if (depth > 64 || !f.exists()) return null
        if (!f.isDirectory) return f.length()
        val kids = try { f.listFiles() } catch (_: Throwable) { null } ?: return null
        var total = 0L
        for (k in kids) {
            val part = sizeOfOrNull(k, depth + 1) ?: return null
            total += part
        }
        return total
    }

    fun copy(items: List<FileItem>, destDir: File, onProgress: (Long, Long, String) -> Unit = { _, _, _ -> }): Int {
        val total = items.sumOf { sizeOf(File(it.path)) }
        var done = 0L
        var ok = 0
        items.forEach { item ->
            val src = File(item.path)
            if (!src.exists()) return@forEach
            val target = uniqueTarget(destDir, src.name)
            val before = done
            val success = copyRecursive(src, target) { b ->
                done += b
                onProgress(done, total, src.name)
            }
            if (success) ok++ else done = before
        }
        return ok
    }

    fun move(items: List<FileItem>, destDir: File, onProgress: (Long, Long, String) -> Unit = { _, _, _ -> }): Int {
        var ok = 0
        items.forEach { item ->
            val src = File(item.path)
            if (!src.exists()) return@forEach
            if (src.absolutePath == destDir.absolutePath) return@forEach
            val target = uniqueTarget(destDir, src.name)
            if (src.renameTo(target)) {
                ok++
                Prefs.moveOriginal(item.path, target.absolutePath)
                if (Prefs.isFav(item.path)) {
                    Prefs.removeFav(item.path)
                    Prefs.addFav(target.absolutePath)
                }
            } else {
                val success = copyRecursive(src, target) { }
                if (success) {
                    deleteRecursive(src)
                    Prefs.moveOriginal(item.path, target.absolutePath)
                    if (Prefs.isFav(item.path)) {
                        Prefs.removeFav(item.path)
                        Prefs.addFav(target.absolutePath)
                    }
                    ok++
                }
            }
        }
        return ok
    }

    fun deleteToTrash(ctx: Context, items: List<FileItem>): Int {
        var ok = 0
        items.forEach { item ->
            val f = File(item.path)
            if (!f.exists()) return@forEach
            if (TrashStore.moveToTrash(ctx, f)) {
                ok++
                Prefs.removeFav(item.path)
            }
        }
        return ok
    }

    fun deletePermanent(items: List<FileItem>): Int {
        var ok = 0
        items.forEach { item ->
            val f = File(item.path)
            if (!f.exists()) return@forEach
            if (deleteRecursive(f)) {
                ok++
                Prefs.removeFav(item.path)
            }
        }
        return ok
    }

    /** 重命名，返回错误信息（成功返回 null） */
    fun rename(item: FileItem, newName: String): String? {
        val src = File(item.path)
        if (!src.exists()) return "文件不存在"
        val clean = newName.trim()
        if (clean.isEmpty()) return "名称不能为空"
        if (clean.contains('/') || clean.contains('\\')) return "名称不能包含斜杠"
        if (clean == src.name) return null
        val target = File(src.parentFile, clean)
        if (target.exists()) return "同目录下已存在同名文件"
        // 第一次改名时把真实原名记下来，后面“恢复默认名”才有依据
        Prefs.rememberOriginal(src.absolutePath, src.name)
        if (!src.renameTo(target)) return "重命名失败（可能被占用或没有权限）"
        Prefs.moveOriginal(item.path, target.absolutePath)
        if (Prefs.isFav(item.path)) {
            Prefs.removeFav(item.path)
            Prefs.addFav(target.absolutePath)
        }
        return null
    }

    /** 恢复默认名（原名 + 用文件头识别真实类型修正后缀） */
    fun restoreDefault(item: FileItem): String? {
        val f = File(item.path)
        if (!f.exists()) return "文件不存在"
        val orig = Prefs.originalOf(item.path) ?: item.name
        val realExt = TypeDetect.detectByMagic(f)
        val origExt = orig.substringAfterLast('.', "")
        // 有记录就严格恢复记录的原名（含原后缀）；只有原名没后缀时才用文件头补后缀
        val targetName = if (origExt.isEmpty() && realExt != null) "$orig.$realExt" else orig
        if (targetName == f.name) return "已经是默认名称"
        val target = File(f.parentFile, targetName)
        if (target.exists() && target.absolutePath != f.absolutePath) {
            val n = uniqueTarget(f.parentFile, targetName)
            if (!f.renameTo(n)) return "恢复失败"
            Prefs.moveOriginal(f.absolutePath, n.absolutePath)
            return "已恢复为 ${n.name}"
        }
        if (!f.renameTo(target)) return "恢复失败"
        Prefs.moveOriginal(item.path, target.absolutePath)
        return "已恢复为 $targetName"
    }

    fun createFolder(dir: File, name: String): File? {
        val clean = name.trim()
        if (clean.isEmpty()) return null
        val f = File(dir, clean)
        if (f.exists()) return null
        return if (dir.mkdirs() || f.mkdirs()) f else null
    }

    fun createFile(dir: File, name: String, content: String = ""): File? {
        val clean = name.trim()
        if (clean.isEmpty()) return null
        val f = File(dir, clean)
        if (f.exists()) return null
        return try {
            dir.mkdirs()
            f.writeText(content)
            f
        } catch (_: Throwable) {
            null
        }
    }

    fun share(ctx: Context, items: List<FileItem>) {
        val uris = ArrayList<Uri>()
        items.forEach { item ->
            val f = File(item.path)
            if (f.exists() && !f.isDirectory) {
                try {
                    uris.add(FileProvider.getUriForFile(ctx, ctx.packageName + ".fileprovider", f))
                } catch (_: Throwable) {}
            }
        }
        if (uris.isEmpty()) return
        val intent = if (uris.size == 1) {
            Intent(Intent.ACTION_SEND).apply {
                putExtra(Intent.EXTRA_STREAM, uris[0])
                type = items.firstOrNull()?.mime?.ifEmpty { "*/*" } ?: "*/*"
            }
        } else {
            Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
                type = "*/*"
            }
        }
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        ctx.startActivity(Intent.createChooser(intent, "分享到").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    fun openWith(ctx: Context, path: String, mime: String = "") {
        val f = File(path)
        if (!f.exists()) return
        val uri = try {
            FileProvider.getUriForFile(ctx, ctx.packageName + ".fileprovider", f)
        } catch (_: Throwable) {
            return
        }
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mime.ifEmpty { TypeDetect.mimeOf(f.name) })
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            ctx.startActivity(intent)
        } catch (_: Throwable) {
            // 没有可用应用
        }
    }

    /** 转给别的应用打开，但把“自己”从候选里排除，避免选来选去又回到本应用 */
    fun openWithOther(ctx: Context, path: String, mime: String = ""): Boolean {
        val f = File(path)
        if (!f.exists()) return false
        val uri = try {
            FileProvider.getUriForFile(ctx, ctx.packageName + ".fileprovider", f)
        } catch (_: Throwable) {
            return false
        }
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mime.ifEmpty { TypeDetect.mimeOf(f.name) })
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            try {
                putExtra(
                    Intent.EXTRA_EXCLUDE_COMPONENTS,
                    arrayOf(android.content.ComponentName(ctx, com.luogen.filehub.ui.MainActivity::class.java))
                )
            } catch (_: Throwable) {}
        }
        return try {
            ctx.startActivity(intent)
            true
        } catch (_: Throwable) {
            false
        }
    }

    /** 安装 APK：未开启“安装未知应用”时先跳设置 */
    fun installApk(ctx: Context, path: String): Boolean {
        val f = File(path)
        if (!f.exists()) return false
        if (Build.VERSION.SDK_INT >= 26 && !ctx.packageManager.canRequestPackageInstalls()) {
            try {
                ctx.startActivity(
                    Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + ctx.packageName))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            } catch (_: Throwable) {}
            return false
        }
        val uri = try {
            FileProvider.getUriForFile(ctx, ctx.packageName + ".fileprovider", f)
        } catch (_: Throwable) {
            return false
        }
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            ctx.startActivity(intent)
            true
        } catch (_: Throwable) {
            false
        }
    }

    fun uninstall(ctx: Context, packageName: String) {
        try {
            val intent = Intent(Intent.ACTION_DELETE, Uri.parse("package:$packageName"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            ctx.startActivity(intent)
        } catch (_: Throwable) {}
    }

    fun openAppInfo(ctx: Context, packageName: String) {
        try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            ctx.startActivity(intent)
        } catch (_: Throwable) {}
    }

    /** 返回 null 表示成功，否则是给用户看的具体失败原因 */
    fun launchApp(ctx: Context, packageName: String): String? {
        return try {
            val intent = ctx.packageManager.getLaunchIntentForPackage(packageName)
                ?: return "这个应用没有可打开的界面：它属于系统底层服务（如音频/蓝牙/网络等后台服务），不是普通应用，无法直接启动"
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            ctx.startActivity(intent)
            null
        } catch (t: Throwable) {
            "打开失败：${t.message ?: "系统拒绝启动该应用"}"
        }
    }
}
