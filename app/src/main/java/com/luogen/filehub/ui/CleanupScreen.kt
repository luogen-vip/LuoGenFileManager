package com.luogen.filehub.ui

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.luogen.filehub.data.FileOps
import com.luogen.filehub.data.FileRepo
import com.luogen.filehub.data.TrashStore
import com.luogen.filehub.data.VaultStore
import com.luogen.filehub.model.Cat
import com.luogen.filehub.model.FileItem
import com.luogen.filehub.model.StorageInfo
import com.luogen.filehub.model.toSizeText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class JunkItem(val item: FileItem, val level: String, val reason: String)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CleanupScreen(ctx: Context, nav: NavStack, repo: FileRepo) {
    val scope = rememberCoroutineScope()
    var loading by remember { mutableStateOf(true) }
    var storage by remember { mutableStateOf(StorageInfo()) }
    var catSizes by remember { mutableStateOf(mapOf<Cat, Long>()) }
    var junk by remember { mutableStateOf<List<JunkItem>>(emptyList()) }
    var apps by remember { mutableStateOf<List<FileItem>>(emptyList()) }
    var selected by remember { mutableStateOf<Set<String>>(emptySet()) }
    var prog by remember { mutableStateOf<Prog?>(null) }
    var toast by remember { mutableStateOf<String?>(null) }
    var refresh by remember { mutableStateOf(0) }
    var showUninstall by remember { mutableStateOf<FileItem?>(null) }

    LaunchedEffect(refresh) {
        loading = true
        try {
        withContext(Dispatchers.IO) {
            val st = try { repo.storage() } catch (_: Throwable) { StorageInfo() }
            val sizes = HashMap<Cat, Long>()
            sizes[Cat.IMAGE] = repo.images().sumOf { it.size }
            sizes[Cat.VIDEO] = repo.videos().sumOf { it.size }
            sizes[Cat.AUDIO] = repo.audios().sumOf { it.size }
            sizes[Cat.DOC] = repo.docs().sumOf { it.size }
            sizes[Cat.ARCHIVE] = repo.archives().sumOf { it.size }
            sizes[Cat.APP] = repo.apkFiles().sumOf { it.size }
            sizes[Cat.TRASH] = TrashStore.totalSize(ctx)
            sizes[Cat.VAULT] = VaultStore.totalSize(ctx)

            val junkList = ArrayList<JunkItem>()
            val dl = File(android.os.Environment.getExternalStorageDirectory(), "Download")
            val candidates = ArrayList<File>()
            if (dl.exists()) {
                dl.listFiles()?.forEach { f ->
                    if (f.isFile) {
                        when {
                            f.name.endsWith(".apk", true) -> junkList.add(JunkItem(
                                FileItem(path = f.absolutePath, name = f.name, size = f.length(), modified = f.lastModified()),
                                "谨慎", "已下载的安装包，安装完成后可删"
                            ))
                            f.length() == 0L -> junkList.add(JunkItem(
                                FileItem(path = f.absolutePath, name = f.name, size = 0, modified = f.lastModified()),
                                "安全", "0 字节空文件"
                            ))
                            f.name.endsWith(".tmp", true) || f.name.endsWith(".temp", true) || f.name.endsWith(".log", true) ->
                                junkList.add(JunkItem(
                                    FileItem(path = f.absolutePath, name = f.name, size = f.length(), modified = f.lastModified()),
                                    "安全", "临时 / 日志文件"
                                ))
                        }
                    }
                }
            }
            val cacheSize = FileOps.sizeOf(ctx.cacheDir)
            if (cacheSize > 0) {
                junkList.add(JunkItem(
                    FileItem(path = ctx.cacheDir.absolutePath, name = "应用缓存", size = cacheSize, isDir = true, modified = 0),
                    "安全", "本应用产生的临时缓存，可随时清理"
                ))
            }
            val appList = repo.installedApps().take(80)

            withContext(Dispatchers.Main) {
                storage = st
                catSizes = sizes
                junk = junkList.sortedByDescending { it.item.size }
                apps = appList
            }
        }
        } catch (t: Throwable) {
            toast = "统计部分失败：${t.message ?: t.javaClass.simpleName}"
        } finally {
            loading = false
        }
    }

    Scaffold(
        containerColor = BgDeep,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("清理与存储", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Text("已用 ${storage.used.toSizeText()} / 共 ${storage.total.toSizeText()}", fontSize = 11.sp, color = TextDim)
                    }
                },
                navigationIcon = { IconButton(onClick = { nav.pop() }) { Icon(Icons.Filled.ArrowBack, null, tint = TextMain) } },
                actions = { IconButton(onClick = { refresh++ }) { Icon(Icons.Filled.Refresh, null, tint = TextMain) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = BgDeep)
            )
        },
        bottomBar = {
            if (selected.isNotEmpty()) {
                Surface(color = SurfaceDark) {
                    Row(Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                        val selJunk = junk.filter { selected.contains(it.item.key) }
                        Button(
                            onClick = {
                                scope.launch {
                                    prog = Prog("正在清理", null, "准备中…")
                                    val total = selJunk.size
                                    var ok = 0
                                    withContext(Dispatchers.IO) {
                                        selJunk.forEachIndexed { i, j ->
                                            prog = Prog("正在清理", i.toFloat() / total.coerceAtLeast(1), j.item.name)
                                            if (j.item.isDir) {
                                                FileOps.deleteRecursive(File(j.item.path)); ok++
                                            } else if (FileOps.deleteToTrash(ctx, listOf(j.item)) > 0) ok++
                                        }
                                    }
                                    prog = null
                                    toast = "已清理 $ok 项"
                                    selected = emptySet()
                                    refresh++
                                }
                            },
                            modifier = Modifier.weight(1f)
                        ) { Text("清理选中 (${selJunk.size})") }
                        Spacer(Modifier.width(8.dp))
                        TextButton(onClick = { selected = emptySet() }) { Text("取消") }
                    }
                }
            }
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            if (loading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = BrandBlue) }
            } else {
                LazyColumn(Modifier.fillMaxSize()) {
                    item {
                        Column(Modifier.padding(16.dp)) {
                            val ratio = if (storage.total > 0) storage.used.toFloat() / storage.total else 0f
                            LinearProgressIndicator(
                                progress = { ratio.coerceIn(0f, 1f) },
                                modifier = Modifier.fillMaxWidth().height(10.dp),
                                color = BrandBlue, trackColor = SurfaceDark2, drawStopIndicator = {}
                            )
                            Spacer(Modifier.height(8.dp))
                            Row {
                                Text("剩余 ${storage.free.toSizeText()}", color = GoodGreen, fontSize = 12.sp)
                                Spacer(Modifier.weight(1f))
                                Text("已用 ${storage.used.toSizeText()}", color = TextDim, fontSize = 12.sp)
                            }
                        }
                    }

                    item { SectionTitle("各类文件占用") }
                    items(count = catSizes.size) { idx ->
                        val entry = catSizes.entries.elementAt(idx)
                        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(catIcon(entry.key), null, tint = catColor(entry.key), modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(10.dp))
                            Text(entry.key.title, fontSize = 13.sp, modifier = Modifier.weight(1f))
                            Text(entry.value.toSizeText(), color = TextDim, fontSize = 12.sp)
                        }
                    }

                    item { SectionTitle("可清理项（带风险提示）") }
                    if (junk.isEmpty()) {
                        item { Text("没有发现明显的垃圾文件", color = TextDim, fontSize = 12.sp, modifier = Modifier.padding(16.dp)) }
                    }
                    items(count = junk.size) { idx ->
                        val j = junk[idx]
                        val checked = selected.contains(j.item.key)
                        Row(
                            Modifier.fillMaxWidth().clickable {
                                selected = if (checked) selected - j.item.key else selected + j.item.key
                            }.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(checked = checked, onCheckedChange = {
                                selected = if (checked) selected - j.item.key else selected + j.item.key
                            })
                            Column(Modifier.weight(1f)) {
                                Text(j.item.name, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(j.reason, fontSize = 11.sp, color = TextDim)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(j.item.size.toSizeText(), fontSize = 12.sp, color = TextDim)
                                Text(
                                    j.level,
                                    fontSize = 10.sp,
                                    color = when (j.level) { "安全" -> GoodGreen; "谨慎" -> WarnAmber; else -> DangerRed },
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(
                                            when (j.level) { "安全" -> GoodGreen; "谨慎" -> WarnAmber; else -> DangerRed }.copy(alpha = 0.15f)
                                        )
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }

                    item { SectionTitle("已安装应用（按占用排序）") }
                    items(count = apps.size) { idx ->
                        val a = apps[idx]
                        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            ThumbBox(Modifier.size(40.dp), a)
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(a.label, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text("${a.size.toSizeText()} · ${a.extra}", fontSize = 11.sp, color = TextDim)
                            }
                            TextButton(onClick = { showUninstall = a }) { Text("卸载", color = DangerRed) }
                        }
                    }
                    item { Spacer(Modifier.height(60.dp)) }
                }
            }
        }
    }

    showUninstall?.let { a ->
        ConfirmDialog(
            title = "卸载应用",
            text = "将跳转系统卸载界面卸载「${a.label}」。",
            confirmText = "去卸载",
            danger = true,
            onDismiss = { showUninstall = null },
            onConfirm = { FileOps.uninstall(ctx, a.packageName) }
        )
    }

    prog?.let { ProgressDialog(it.title, it.progress, it.msg) }
    toast?.let {
        AlertDialog(onDismissRequest = { toast = null }, title = { Text("提示") }, text = { Text(it) },
            confirmButton = { TextButton(onClick = { toast = null }) { Text("知道了") } })
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = Modifier.padding(start = 16.dp, top = 18.dp, bottom = 6.dp)
    )
}
