package com.luogen.filehub.ui

import android.content.Context
import android.net.Uri
import android.os.Environment
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.luogen.filehub.data.TypeDetect
import com.luogen.filehub.model.Cat
import com.luogen.filehub.model.FileItem
import com.luogen.filehub.model.toSizeText
import java.io.File
import java.net.URLDecoder

/** 把用户粘贴的绝对路径/相对路径解析成真实文件；支持 file:// 和 /sdcard 前缀 */
object PathJump {
    fun looksLikePath(raw: String): Boolean {
        val s = raw.trim()
        if (s.isEmpty() || !s.contains('/')) return false
        return s.startsWith("/") || s.startsWith("file://") || s.startsWith("content://") ||
            s.startsWith("storage/", true) || s.startsWith("sdcard/", true) ||
            s.startsWith("Download/", true) || s.startsWith("下载/")
    }

    fun resolve(ctx: Context, raw: String): File? {
        var s = raw.trim()
        if (!looksLikePath(s)) return null
        try {
            if (s.startsWith("file://", true)) s = Uri.parse(s).path ?: s
            else if (s.startsWith("content://", true)) {
                // 内容 URI 尝试通过显示名落回常用目录；拿不到就交给系统
                return null
            } else s = URLDecoder.decode(s, "UTF-8")
        } catch (_: Throwable) {}
        s = s.trim().trimEnd('/')
        if (s.isEmpty()) return null
        val ext = Environment.getExternalStorageDirectory()
        val candidates = ArrayList<File>()
        if (s.startsWith("/")) {
            candidates.add(File(s))
            if (s.startsWith("/sdcard/", true)) candidates.add(File(ext, s.removePrefix("/sdcard/")))
        } else {
            candidates.add(File(ext, s))
            candidates.add(File("/storage/emulated/0", s))
            candidates.add(File("/sdcard", s))
        }
        return candidates.firstOrNull { it.exists() }
    }

    fun itemOf(f: File): FileItem = FileItem(
        path = f.absolutePath,
        name = f.name,
        size = if (f.isDirectory) 0L else f.length(),
        modified = f.lastModified(),
        isDir = f.isDirectory,
        mime = if (f.isDirectory) "" else TypeDetect.mimeOf(f.name)
    )
}

/**
 * 搜索结果上方的“按路径跳转”条：全局搜索/我的文件直接跳，分类里类型不一致会先询问。
 */
@Composable
fun PathJumpBar(nav: NavStack, query: String, cat: Cat, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val target = remember(query) { PathJump.resolve(ctx, query) } ?: return
    var pending by remember { mutableStateOf<FileItem?>(null) }

    fun open(f: File) {
        if (f.isDirectory) {
            nav.push(Screen.Browse(f.absolutePath))
        } else {
            openItem(ctx, nav, PathJump.itemOf(f), listOf(PathJump.itemOf(f)))
        }
    }

    Surface(
        color = SurfaceDark,
        shape = RoundedCornerShape(14),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
    ) {
        Row(
            Modifier
                .clickable {
                    if (target.isDirectory || cat == Cat.FILE || cat == Cat.FAV || cat == Cat.TRASH ||
                        cat == Cat.DOWNLOAD || cat == Cat.CLEAN
                    ) {
                        open(target)
                    } else {
                        val fi = PathJump.itemOf(target)
                        val realCat = TypeDetect.catOf(target.name, fi.mime)
                        if (realCat == cat) open(target) else pending = fi
                    }
                }
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                if (target.isDirectory) Icons.Filled.FolderOpen else Icons.Filled.InsertDriveFile,
                null, tint = BrandBlue, modifier = Modifier.size(22.dp)
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    if (target.isDirectory) "按路径打开文件夹" else "按路径打开文件",
                    fontSize = 13.sp, color = BrandBlue, maxLines = 1
                )
                Text(
                    target.absolutePath,
                    fontSize = 10.sp, color = TextDim,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
            }
            if (!target.isDirectory) {
                Text(
                    TypeDetect.labelOf(target.name),
                    fontSize = 10.sp, color = TextDim, maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 110.dp)
                )
            }
        }
    }

    pending?.let { fi ->
        val targetCat = TypeDetect.catOf(fi.name, fi.mime)
        AlertDialog(
            onDismissRequest = { pending = null },
            title = { Text("这个路径不是${cat.title}") },
            text = {
                Text("这个路径对应的是「${TypeDetect.labelOf(fi.name)}」，属于${targetCat.title}。仍要打开吗？")
            },
            confirmButton = {
                TextButton(onClick = {
                    pending = null
                    open(File(fi.path))
                }) { Text("仍然打开") }
            },
            dismissButton = { TextButton(onClick = { pending = null }) { Text("取消") } }
        )
    }
}
