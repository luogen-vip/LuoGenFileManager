package com.luogen.filehub.ui

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.luogen.filehub.data.ArchiveEntry
import com.luogen.filehub.data.ArchiveUtil
import com.luogen.filehub.data.FileOps
import com.luogen.filehub.data.TypeDetect
import com.luogen.filehub.model.Cat
import com.luogen.filehub.model.FileItem
import com.luogen.filehub.model.extOf
import com.luogen.filehub.model.nameNoExt
import com.luogen.filehub.model.toSizeText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArchiveViewerScreen(ctx: Context, nav: NavStack, path: String) {
    val file = remember(path) { File(path) }
    val scope = rememberCoroutineScope()
    var entries by remember { mutableStateOf<List<ArchiveEntry>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var skipBroken by remember { mutableStateOf(true) }
    var showDest by remember { mutableStateOf(false) }
    var prog by remember { mutableStateOf<Prog?>(null) }
    var toast by remember { mutableStateOf<String?>(null) }
    var filter by remember { mutableStateOf("") }
    var pendingEntry by remember { mutableStateOf<ArchiveEntry?>(null) }
    var doneDir by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(path) {
        withContext(Dispatchers.IO) {
            val r = try { ArchiveUtil.list(file) } catch (t: Throwable) {
                com.luogen.filehub.data.ArchiveResult(false, t.message ?: "读取失败")
            }
            withContext(Dispatchers.Main) {
                entries = r.entries
                if (!r.ok) error = r.message
                loading = false
            }
        }
    }

    val root = android.os.Environment.getExternalStorageDirectory().absolutePath
    val shown = remember(entries, filter) {
        if (filter.isBlank()) entries else entries.filter { it.name.contains(filter, true) }
    }

    fun extractAll(dest: String) {
        scope.launch {
            prog = Prog("正在解压", null, "准备中…")
            val r = withContext(Dispatchers.IO) {
                ArchiveUtil.extract(file, File(dest), skipBroken) { done, total, cur ->
                    prog = Prog("正在解压", if (total > 0) done.toFloat() / total else null, cur)
                }
            }
            prog = null
            if (r.ok) {
                doneDir = dest
            } else {
                toast = r.message
            }
        }
    }

    fun extractThis(entry: ArchiveEntry) {
        scope.launch {
            val base = File(
                android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS),
                nameNoExt(file.name)
            )
            prog = Prog("正在解压", null, entry.name)
            val r = withContext(Dispatchers.IO) { ArchiveUtil.extractOne(file, entry.name, base) }
            prog = null
            if (r.ok) {
                doneDir = base.absolutePath
                toast = "已解压：${entry.name}"
            } else {
                toast = r.message
            }
        }
    }

    Scaffold(
        containerColor = BgDeep,
        topBar = {
            Column(Modifier.background(SurfaceDark)) {
                TopAppBar(
                    title = {
                        Column {
                            Text(file.name, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1, color = TextMain)
                            Text(
                                "${ArchiveUtil.formatName(file)} · ${entries.size} 个条目 · ${file.length().toSizeText()}",
                                fontSize = 11.sp, color = TextDim
                            )
                        }
                    },
                    navigationIcon = { IconButton(onClick = { nav.pop() }) { Icon(Icons.Filled.ArrowBack, null, tint = TextMain) } },
                    actions = {
                        IconButton(onClick = {
                            FileOps.share(ctx, listOf(FileItem(path = path, name = file.name, mime = "application/octet-stream")))
                        }) { Icon(Icons.Filled.Share, null, tint = TextMain) }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = SurfaceDark)
                )
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 2.dp)
                        .height(44.dp)
                        .clip(RoundedCornerShape(22))
                        .background(SurfaceDark2)
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.Search, null, tint = TextDim, modifier = Modifier.size(17.dp))
                    Spacer(Modifier.width(8.dp))
                    Box(Modifier.weight(1f)) {
                        BasicTextField(
                            value = filter,
                            onValueChange = { filter = it },
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyMedium.copy(color = TextMain, fontSize = 13.sp)
                        )
                        if (filter.isEmpty()) Text("在压缩包里搜索条目…", color = TextDim, fontSize = 13.sp)
                    }
                }
                Spacer(Modifier.height(6.dp))
            }
        },
        bottomBar = {
            Surface(color = SurfaceDark, shadowElevation = 8.dp) {
                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                    ActionItem("解压全部", Icons.Filled.Unarchive) { showDest = true }
                    ActionItem("解压到 Download", Icons.Filled.Download) {
                        val d = File(
                            android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS),
                            nameNoExt(file.name)
                        )
                        extractAll(d.absolutePath)
                    }
                    ActionItem("其他应用打开", Icons.Filled.OpenInNew) { FileOps.openWith(ctx, path) }
                }
            }
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = BrandBlue, strokeWidth = 2.5.dp)
                }
                error != null && entries.isEmpty() -> Column(Modifier.fillMaxSize().padding(20.dp)) {
                    Text(error!!, color = DangerRed, fontSize = 13.sp)
                    Spacer(Modifier.height(10.dp))
                    Button(onClick = { FileOps.openWith(ctx, path) }) { Text("用其他应用打开") }
                }
                else -> LazyColumn(Modifier.fillMaxSize()) {
                    item {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(checked = skipBroken, onCheckedChange = { skipBroken = it })
                            Text("跳过损坏的条目", fontSize = 12.sp, color = TextDim)
                            Spacer(Modifier.weight(1f))
                            Text("点条目可单独解压查看", fontSize = 11.sp, color = TextDim)
                        }
                    }
                    items(count = shown.size) { idx ->
                        val e = shown[idx]
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (!e.isDir) pendingEntry = e
                                }
                                .padding(horizontal = 14.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(10))
                                    .background(catColor(if (e.isDir) Cat.FILE else catOfName(e.name)).copy(alpha = 0.12f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    if (e.isDir) Icons.Filled.FolderOpen else itemIconFor(e.name),
                                    null,
                                    tint = catColor(if (e.isDir) Cat.FILE else catOfName(e.name)),
                                    modifier = Modifier.size(19.dp)
                                )
                            }
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(e.name, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, color = TextMain)
                                if (!e.isDir) Text(e.size.toSizeText(), fontSize = 11.sp, color = TextDim)
                            }
                            if (!e.isDir) Icon(Icons.Filled.Unarchive, null, tint = TextDim, modifier = Modifier.size(16.dp))
                        }
                    }
                    item { Spacer(Modifier.height(16.dp)) }
                }
            }
        }
    }

    if (showDest) {
        DestPickerDialog(
            title = "解压到",
            startDir = root,
            onDismiss = { showDest = false },
            onPick = { dest ->
                showDest = false
                extractAll(dest)
            }
        )
    }

    pendingEntry?.let { e ->
        AlertDialog(
            onDismissRequest = { pendingEntry = null },
            shape = RoundedCornerShape(22.dp),
            title = { Text("查看「${e.name.substringAfterLast('/')}」") },
            text = {
                Text(
                    "压缩包里的文件需要先解压才能预览。要现在就解压到 Download/${nameNoExt(file.name)} 并打开吗？",
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val entry = e
                    pendingEntry = null
                    extractThis(entry)
                }) { Text("解压并查看") }
            },
            dismissButton = { TextButton(onClick = { pendingEntry = null }) { Text("先不解压") } }
        )
    }

    doneDir?.let { dir ->
        AlertDialog(
            onDismissRequest = { doneDir = null },
            shape = RoundedCornerShape(22.dp),
            title = { Text("解压完成") },
            text = { Text("已解压到：\n$dir", fontSize = 13.sp) },
            confirmButton = {
                TextButton(onClick = {
                    doneDir = null
                    nav.push(Screen.Browse(dir))
                }) { Text("去查看") }
            },
            dismissButton = { TextButton(onClick = { doneDir = null }) { Text("留在这里") } }
        )
    }

    prog?.let { ProgressDialog(it.title, it.progress, it.msg) }
    toast?.let {
        AlertDialog(
            onDismissRequest = { toast = null },
            shape = RoundedCornerShape(22.dp),
            title = { Text("提示") },
            text = { Text(it) },
            confirmButton = { TextButton(onClick = { toast = null }) { Text("知道了") } }
        )
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.ActionItem(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Column(
        Modifier
            .weight(1f)
            .clip(RoundedCornerShape(12))
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, null, tint = BrandBlue, modifier = Modifier.size(20.dp))
        Spacer(Modifier.height(3.dp))
        Text(label, fontSize = 10.sp, color = TextDim, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

private fun catOfName(name: String): Cat = TypeDetect.catOf(name, "")

private fun itemIconFor(name: String) = when (extOf(name)) {
    in TypeDetect.IMAGE_EXT -> Icons.Filled.Photo
    in TypeDetect.VIDEO_EXT -> Icons.Filled.Videocam
    in TypeDetect.AUDIO_EXT -> Icons.Filled.Audiotrack
    in TypeDetect.DOC_EXT -> Icons.Filled.Article
    in TypeDetect.ARCHIVE_EXT -> Icons.Filled.FolderZip
    "apk" -> Icons.Filled.Apps
    else -> Icons.Filled.Description
}
