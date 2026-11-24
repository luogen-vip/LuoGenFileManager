package com.luogen.filehub.ui

import android.content.Context
import androidx.activity.compose.BackHandler
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
import com.luogen.filehub.model.toSizeText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** 从「清理与存储」点某一类进来：显示该类所有文件 + 安全等级 + 多选清理 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CleanCatScreen(ctx: Context, nav: NavStack, repo: FileRepo, cat: Cat) {
    val scope = rememberCoroutineScope()
    var items by remember { mutableStateOf<List<FileItem>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var selected by remember { mutableStateOf<Set<String>>(emptySet()) }
    var prog by remember { mutableStateOf<Prog?>(null) }
    var toast by remember { mutableStateOf<String?>(null) }
    var tick by remember { mutableStateOf(0) }
    var confirm by remember { mutableStateOf(false) }
    var uninstall by remember { mutableStateOf<FileItem?>(null) }

    LaunchedEffect(tick) {
        loading = items.isEmpty()
        val loaded = withContext(Dispatchers.IO) {
            try {
                when (cat) {
                    Cat.TRASH -> TrashStore.list(ctx).map { it.toItem(ctx) }
                    Cat.VAULT -> VaultStore.list(ctx).map { it.toItem(ctx) }
                    Cat.APP -> repo.installedApps()
                    else -> repo.load(cat)
                }.sortedByDescending { it.size }
            } catch (_: Throwable) { emptyList() }
        }
        items = loaded
        selected = selected.filter { k -> loaded.any { it.key == k } }.toSet()
        loading = false
    }

    BackHandler(enabled = selected.isNotEmpty()) { selected = emptySet() }

    val selItems = items.filter { selected.contains(it.key) }
    val selSize = selItems.sumOf { it.size }
    val totalSize = items.sumOf { it.size }

    Scaffold(
        containerColor = BgDeep,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("清理 · ${cat.title}", fontSize = 17.sp, fontWeight = FontWeight.Bold)
                        Text(
                            if (loading) "正在读取…" else "共 ${items.size} 项 · ${totalSize.toSizeText()}",
                            fontSize = 11.sp, color = TextDim
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { if (selected.isNotEmpty()) selected = emptySet() else nav.pop() }) {
                        Icon(Icons.Filled.ArrowBack, null, tint = TextMain)
                    }
                },
                actions = {
                    if (cat != Cat.APP) {
                        IconButton(onClick = {
                            selected = if (selected.size == items.size) emptySet() else items.map { it.key }.toSet()
                        }) { Icon(Icons.Filled.Checklist, null, tint = TextMain) }
                    }
                    IconButton(onClick = { tick++ }) { Icon(Icons.Filled.Refresh, null, tint = TextMain) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = SurfaceDark)
            )
        },
        bottomBar = {
            if (selected.isNotEmpty()) {
                Surface(color = SurfaceDark) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("已选 ${selItems.size} 项", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                            Text("可释放 ${selSize.toSizeText()}", fontSize = 11.sp, color = TextDim)
                        }
                        TextButton(onClick = { selected = emptySet() }) { Text("取消") }
                        Spacer(Modifier.width(6.dp))
                        Button(onClick = { confirm = true }, shape = RoundedCornerShape(12.dp)) {
                            Text(if (cat == Cat.TRASH) "彻底删除" else "清理")
                        }
                    }
                }
            }
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            if (loading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = BrandBlue) }
            } else if (items.isEmpty()) {
                EmptyView("这一类暂时没有文件")
            } else {
                LazyColumn(Modifier.fillMaxSize()) {
                    item {
                        Text(
                            safetyHint(cat),
                            fontSize = 11.sp, color = TextDim,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                        )
                    }
                    items(count = items.size, key = { items[it].key }) { idx ->
                        val item = items[idx]
                        val checked = selected.contains(item.key)
                        Row(
                            Modifier.fillMaxWidth()
                                .clickable {
                                    selected = if (checked) selected - item.key else selected + item.key
                                }
                                .padding(horizontal = 14.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (cat != Cat.APP) {
                                Checkbox(checked = checked, onCheckedChange = {
                                    selected = if (checked) selected - item.key else selected + item.key
                                })
                                Spacer(Modifier.width(4.dp))
                            }
                            Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                                ThumbBox(Modifier.size(40.dp), item)
                            }
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(item.label.ifEmpty { item.name }, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(safetyReason(cat, item), fontSize = 10.sp, color = TextDim, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(item.size.toSizeText(), fontSize = 12.sp, color = TextDim)
                                Text(
                                    safetyLevel(cat),
                                    fontSize = 9.sp,
                                    color = if (safetyLevel(cat) == "安全") GoodGreen else WarnAmber,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background((if (safetyLevel(cat) == "安全") GoodGreen else WarnAmber).copy(alpha = 0.13f))
                                        .padding(horizontal = 5.dp, vertical = 1.dp)
                                )
                            }
                            if (cat == Cat.APP) {
                                TextButton(onClick = { uninstall = item }) { Text("卸载", color = DangerRed, fontSize = 12.sp) }
                            }
                        }
                    }
                    item { Spacer(Modifier.height(70.dp)) }
                }
            }
        }
    }

    if (confirm) {
        ConfirmDialog(
            title = when (cat) {
                Cat.TRASH -> "彻底删除"
                Cat.VAULT -> "删除保险箱文件"
                else -> "清理文件"
            },
            text = "将处理选中的 ${selItems.size} 项（${selSize.toSizeText()}）。" + when (cat) {
                Cat.TRASH -> "彻底删除后无法恢复。"
                Cat.VAULT -> "会从加密保险箱里永久移除，无法恢复。"
                else -> "会移到回收站，可在回收站还原。"
            },
            confirmText = if (cat == Cat.TRASH || cat == Cat.VAULT) "彻底删除" else "清理",
            danger = true,
            onDismiss = { confirm = false },
            onConfirm = {
                confirm = false
                val list = selItems
                scope.launch {
                    prog = Prog("正在清理", null, "准备中…")
                    var ok = 0
                    withContext(Dispatchers.IO) {
                        when (cat) {
                            Cat.TRASH -> {
                                val entries = TrashStore.list(ctx)
                                list.forEach { s -> entries.firstOrNull { it.id == s.id }?.let { TrashStore.deletePermanent(ctx, it); ok++ } }
                            }
                            Cat.VAULT -> {
                                val entries = VaultStore.list(ctx)
                                list.forEach { s -> entries.firstOrNull { it.id == s.id }?.let { VaultStore.delete(ctx, it); ok++ } }
                            }
                            else -> list.forEach { s ->
                                if (s.isDir) { FileOps.deleteToTrash(ctx, listOf(s)); ok++ }
                                else if (FileOps.deleteToTrash(ctx, listOf(s)) > 0) ok++
                            }
                        }
                    }
                    prog = null
                    AppState.cleanDirty = true
                    toast = "已清理 $ok 项，释放 ${list.sumOf { it.size }.toSizeText()}"
                    selected = emptySet()
                    tick++
                }
            }
        )
    }

    uninstall?.let { a ->
        ConfirmDialog(
            title = "卸载应用",
            text = "将跳转系统卸载界面卸载「${a.label}」。",
            confirmText = "去卸载",
            danger = true,
            onDismiss = { uninstall = null },
            onConfirm = { FileOps.uninstall(ctx, a.packageName); uninstall = null }
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

private fun safetyLevel(cat: Cat): String = when (cat) {
    Cat.TRASH, Cat.ARCHIVE, Cat.DOWNLOAD -> "安全"
    else -> "谨慎"
}

private fun safetyHint(cat: Cat): String = when (cat) {
    Cat.TRASH -> "回收站里的文件删除后无法恢复；不需要的直接彻底删除即可。"
    Cat.VAULT -> "加密保险箱里的文件，删除后无法恢复，确认不再需要再清理。"
    Cat.ARCHIVE -> "压缩包通常可以安全清理，解压后的内容仍在原目录。"
    Cat.APP -> "已安装应用不能直接删，点右侧「卸载」走系统卸载；想清理安装包请回「应用 → 安装包」。"
    Cat.IMAGE -> "图片可能是你的照片或截图，清理前确认已备份。"
    Cat.VIDEO -> "视频通常体积较大，清理前确认不是要保留的影片。"
    Cat.AUDIO -> "音频可能是录音或音乐，清理前确认。"
    Cat.DOC -> "文档可能是重要资料，清理前请确认。"
    else -> "清理前请确认文件不再需要。"
}

private fun safetyReason(cat: Cat, item: FileItem): String = when (cat) {
    Cat.TRASH -> "已在回收站，可彻底删除"
    Cat.VAULT -> "加密存放，删除不可恢复"
    Cat.ARCHIVE -> "压缩包，按需保留"
    Cat.APP -> if (item.extra.isNotEmpty()) item.extra else "已安装应用"
    Cat.IMAGE -> "图片文件"
    Cat.VIDEO -> "视频文件"
    Cat.AUDIO -> "音频文件"
    Cat.DOC -> "文档文件"
    else -> "文件"
}
