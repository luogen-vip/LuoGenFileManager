package com.luogen.filehub.ui

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.luogen.filehub.data.ArchiveEntry
import com.luogen.filehub.data.ArchiveUtil
import com.luogen.filehub.data.FileOps
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

    Scaffold(
        containerColor = BgDeep,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(file.name, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                        Text(
                            "${ArchiveUtil.formatName(file)} · ${entries.size} 个条目 · ${file.length().toSizeText()}",
                            fontSize = 11.sp, color = TextDim
                        )
                    }
                },
                navigationIcon = { IconButton(onClick = { nav.pop() }) { Icon(Icons.Filled.ArrowBack, null) } },
                actions = {
                    IconButton(onClick = { showDest = true }) { Icon(Icons.Filled.Unarchive, null) }
                    IconButton(onClick = { FileOps.share(ctx, listOf(com.luogen.filehub.model.FileItem(path = path, name = file.name, mime = "application/zip"))) }) {
                        Icon(Icons.Filled.Share, null)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = BgDeep)
            )
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = BrandBlue) }
                error != null && entries.isEmpty() -> Column(Modifier.fillMaxSize().padding(20.dp)) {
                    Text(error!!, color = DangerRed, fontSize = 13.sp)
                    Spacer(Modifier.height(10.dp))
                    Button(onClick = { FileOps.openWith(ctx, path) }) { Text("用其他应用打开") }
                }
                else -> LazyColumn(Modifier.fillMaxSize()) {
                    item {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = skipBroken, onCheckedChange = { skipBroken = it })
                            Text("跳过损坏的条目", fontSize = 12.sp, color = TextDim)
                            Spacer(Modifier.weight(1f))
                            TextButton(onClick = { showDest = true }) { Text("解压全部") }
                        }
                    }
                    items(count = entries.size) { idx ->
                        val e = entries[idx]
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                if (e.isDir) Icons.Filled.Folder else itemIconFor(e.name),
                                null,
                                tint = if (e.isDir) catColor(com.luogen.filehub.model.Cat.FILE) else catColor(com.luogen.filehub.model.Cat.ARCHIVE),
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(e.name, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                if (!e.isDir) Text(e.size.toSizeText(), fontSize = 11.sp, color = TextDim)
                            }
                        }
                    }
                    item { Spacer(Modifier.height(40.dp)) }
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
                scope.launch {
                    prog = Prog("正在解压", null, "准备中…")
                    val r = withContext(Dispatchers.IO) {
                        ArchiveUtil.extract(file, File(dest), skipBroken) { done, total, cur ->
                            prog = Prog("正在解压", if (total > 0) done.toFloat() / total else null, cur)
                        }
                    }
                    prog = null
                    toast = if (r.ok) "${r.message}\n保存到：$dest" else r.message
                }
            }
        )
    }

    prog?.let { ProgressDialog(it.title, it.progress, it.msg) }
    toast?.let {
        AlertDialog(
            onDismissRequest = { toast = null },
            title = { Text("提示") },
            text = { Text(it) },
            confirmButton = { TextButton(onClick = { toast = null }) { Text("知道了") } }
        )
    }
}

private fun itemIconFor(name: String) = when (com.luogen.filehub.model.extOf(name)) {
    in com.luogen.filehub.data.TypeDetect.IMAGE_EXT -> Icons.Filled.Image
    in com.luogen.filehub.data.TypeDetect.VIDEO_EXT -> Icons.Filled.Movie
    in com.luogen.filehub.data.TypeDetect.AUDIO_EXT -> Icons.Filled.MusicNote
    in com.luogen.filehub.data.TypeDetect.ARCHIVE_EXT -> Icons.Filled.Archive
    "apk" -> Icons.Filled.Android
    else -> Icons.Filled.InsertDriveFile
}
