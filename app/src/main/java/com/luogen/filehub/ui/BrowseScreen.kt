package com.luogen.filehub.ui

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.luogen.filehub.data.FileOps
import com.luogen.filehub.data.FileRepo
import com.luogen.filehub.data.Prefs
import com.luogen.filehub.data.Sorter
import com.luogen.filehub.model.Cat
import com.luogen.filehub.model.FileItem
import com.luogen.filehub.model.SortMode
import com.luogen.filehub.model.toSizeText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun BrowseScreen(ctx: Context, nav: NavStack, repo: FileRepo, dirPath: String) {
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current

    var current by remember { mutableStateOf(File(dirPath)) }
    var items by remember { mutableStateOf<List<FileItem>>(emptyList()) }
    var query by remember { mutableStateOf("") }
    var sortMode by remember { mutableStateOf(Prefs.sortMode()) }
    var asc by remember { mutableStateOf(Prefs.sortAsc()) }
    var selected by remember { mutableStateOf<Set<String>>(emptySet()) }
    var refresh by remember { mutableStateOf(0) }
    var showNewFolder by remember { mutableStateOf(false) }
    var showNewFile by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }
    var showRename by remember { mutableStateOf<FileItem?>(null) }
    var showDelete by remember { mutableStateOf(false) }
    var showCopy by remember { mutableStateOf(false) }
    var showMove by remember { mutableStateOf(false) }
    var showInfo by remember { mutableStateOf<FileItem?>(null) }
    var toast by remember { mutableStateOf<String?>(null) }
    var prog by remember { mutableStateOf<Prog?>(null) }
    val startPath = dirPath

    // 返回键：有勾选先取消勾选
    BackHandler(enabled = selected.isNotEmpty()) { selected = emptySet() }

    LaunchedEffect(current, refresh) {
        items = withContext(Dispatchers.IO) { repo.listDir(current) }
    }

    val display = remember(items, query, sortMode, asc) {
        val f = if (query.isBlank()) items else items.filter { it.name.contains(query, true) }
        Sorter.sort(f, sortMode, asc, dirsFirst = true)
    }

    val selItems = remember(selected, items) { items.filter { selected.contains(it.key) } }

    fun runOp(title: String, block: suspend (p: (Float, String) -> Unit) -> String) {
        scope.launch {
            prog = Prog(title, null, "准备中…")
            val msg = try { block { p, m -> prog = Prog(title, p, m) } } catch (t: Throwable) { "失败：${t.message}" }
            prog = null
            toast = msg
            refresh++
            selected = emptySet()
        }
    }

    Scaffold(
        containerColor = BgDeep,
        topBar = {
            Column(Modifier.background(SurfaceDark)) {
                TopAppBar(
                    title = {
                        Column {
                            Text(current.name.ifEmpty { "内部存储" }, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                            Text(current.absolutePath, fontSize = 11.sp, color = TextDim, maxLines = 1)
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = {
                            if (selected.isNotEmpty()) {
                                selected = emptySet()
                            } else if (current.absolutePath != startPath && current.parentFile != null) {
                                current = current.parentFile!!
                            } else {
                                nav.pop()
                            }
                        }) { Icon(Icons.Filled.ArrowBack, null, tint = TextMain) }
                    },
                    actions = {
                        IconButton(onClick = {
                            selected = if (selected.size == display.size) emptySet() else display.map { it.key }.toSet()
                        }) { Icon(Icons.Filled.Checklist, null, tint = TextMain) }
                        IconButton(onClick = {
                            sortMode = if (sortMode == SortMode.TIME) SortMode.NAME else SortMode.TIME
                            Prefs.setSort(sortMode, asc)
                        }) { Icon(Icons.Filled.Sort, null, tint = TextMain) }
                        IconButton(onClick = { refresh++ }) { Icon(Icons.Filled.Refresh, null, tint = TextMain) }
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
                            value = query,
                            onValueChange = { query = it },
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyMedium.copy(color = TextMain, fontSize = 13.sp)
                        )
                        if (query.isEmpty()) Text("在当前文件夹搜索…", color = TextDim, fontSize = 13.sp)
                    }
                    if (query.isNotEmpty()) {
                        Icon(Icons.Filled.Close, null, tint = TextDim, modifier = Modifier.size(17.dp).clickable { query = "" })
                    }
                }
                Spacer(Modifier.height(6.dp))
            }
        },
        floatingActionButton = {
            Column {
                SmallFloatingActionButton(onClick = { showNewFile = true; newName = "" }) {
                    Icon(Icons.Filled.NoteAdd, null)
                }
                Spacer(Modifier.height(8.dp))
                FloatingActionButton(onClick = { showNewFolder = true; newName = "" }) {
                    Icon(Icons.Filled.CreateNewFolder, null)
                }
            }
        },
        bottomBar = {
            if (selected.isNotEmpty()) {
                ActionPanel(
                    listOf(
                        ActionSpec("分享", Icons.Filled.Share) { FileOps.share(ctx, selItems) },
                        ActionSpec("复制", Icons.Filled.ContentCopy) { showCopy = true },
                        ActionSpec("移动", Icons.Filled.DriveFileMove) { showMove = true },
                        ActionSpec("删除", Icons.Filled.Delete) { showDelete = true },
                        ActionSpec("重命名", Icons.Filled.DriveFileRenameOutline) {
                            if (selItems.size == 1) showRename = selItems[0]
                            else toast = "多选时请到对应分类里做批量重命名"
                        },
                        ActionSpec("收藏", Icons.Filled.Bookmark) {
                            selItems.forEach { Prefs.toggleFav(it.path) }
                            toast = "已更新收藏"
                            selected = emptySet()
                        },
                        ActionSpec("详情", Icons.Filled.Info) { if (selItems.size == 1) showInfo = selItems[0] }
                    )
                )
            }
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            if (display.isEmpty()) {
                EmptyView(if (query.isNotEmpty()) "没有匹配项" else "这个文件夹是空的")
            } else {
                val listKey = "browse:" + startPath
                val listState = rememberLazyListState(initialFirstVisibleItemIndex = AppState.scrollOf(listKey))
                LaunchedEffect(listState) {
                    snapshotFlow { listState.firstVisibleItemIndex }.collect { AppState.keepScroll(listKey, it) }
                }
                LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                    items(count = display.size, key = { display[it].key }) { idx ->
                        val item = display[idx]
                        var menu by remember { mutableStateOf(false) }
                        FileRow(
                            item = item,
                            selected = selected.contains(item.key),
                            selectionMode = selected.isNotEmpty(),
                            onClick = {
                                when {
                                    selected.isNotEmpty() -> toggleSel(selected, item.key) { selected = it }
                                    item.isDir -> current = File(item.path)
                                    item.path.endsWith(".apk", true) -> {
                                        if (!FileOps.installApk(ctx, item.path)) toast = "请在系统里允许安装未知应用"
                                    }
                                    else -> openItem(ctx, nav, item, display)
                                }
                            },
                            onLongClick = { if (selected.isEmpty()) selected = setOf(item.key) },
                            trailing = {
                                Box {
                                    IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, null, tint = TextDim) }
                                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                        DropdownMenuItem(text = { Text("详细信息") }, onClick = { menu = false; showInfo = item })
                                        DropdownMenuItem(text = { Text("重命名") }, onClick = { menu = false; showRename = item })
                                        DropdownMenuItem(text = { Text("恢复默认名") }, onClick = {
                                            menu = false
                                            toast = FileOps.restoreDefault(item) ?: "已恢复"
                                            refresh++
                                        })
                                        DropdownMenuItem(text = { Text(if (Prefs.isFav(item.path)) "取消收藏" else "收藏") }, onClick = {
                                            menu = false
                                            toast = if (Prefs.toggleFav(item.path)) "已收藏" else "已取消收藏"
                                        })
                                        DropdownMenuItem(text = { Text("复制路径") }, onClick = {
                                            menu = false
                                            clipboard.setText(AnnotatedString(item.path))
                                            toast = "路径已复制"
                                        })
                                        DropdownMenuItem(text = { Text("移入回收站") }, onClick = {
                                            menu = false
                                            toast = if (FileOps.deleteToTrash(ctx, listOf(item)) > 0) "已移入回收站" else "失败"
                                            refresh++
                                        })
                                        if (current.parentFile == null || current.absolutePath != "/") {
                                            DropdownMenuItem(text = { Text("上一级") }, onClick = {
                                                menu = false
                                                current.parentFile?.let { current = it }
                                            })
                                        }
                                    }
                                }
                            }
                        )
                    }
                    item { Spacer(Modifier.height(80.dp)) }
                }
            }
        }
    }

    if (showNewFolder) {
        AlertDialog(
            onDismissRequest = { showNewFolder = false },
            shape = RoundedCornerShape(22.dp),
            title = { Text("新建文件夹") },
            text = { OutlinedTextField(value = newName, onValueChange = { newName = it }, singleLine = true, label = { Text("文件夹名") }) },
            confirmButton = {
                TextButton(onClick = {
                    val f = FileOps.createFolder(current, newName)
                    showNewFolder = false
                    if (f != null) { toast = "已创建 ${f.name}"; refresh++ } else toast = "创建失败（可能已存在）"
                }) { Text("创建") }
            },
            dismissButton = { TextButton(onClick = { showNewFolder = false }) { Text("取消") } }
        )
    }

    if (showNewFile) {
        AlertDialog(
            onDismissRequest = { showNewFile = false },
            shape = RoundedCornerShape(22.dp),
            title = { Text("新建文件") },
            text = {
                Column {
                    OutlinedTextField(value = newName, onValueChange = { newName = it }, singleLine = true, label = { Text("文件名（含扩展名，如 note.txt）") })
                    Text("文本类文件创建后可直接编辑；其他类型会创建空文件。", fontSize = 11.sp, color = TextDim)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val f = FileOps.createFile(current, newName)
                    showNewFile = false
                    if (f != null) {
                        toast = "已创建 ${f.name}"
                        refresh++
                        if (com.luogen.filehub.data.TypeDetect.catOf(f.name) == Cat.DOC) nav.push(Screen.TextView(f.absolutePath))
                    } else toast = "创建失败"
                }) { Text("创建") }
            },
            dismissButton = { TextButton(onClick = { showNewFile = false }) { Text("取消") } }
        )
    }

    showRename?.let { item ->
        RenameDialog(initial = item.name, onDismiss = { showRename = null }, onConfirm = { nn ->
            showRename = null
            toast = FileOps.rename(item, nn) ?: "已重命名"
            refresh++
        })
    }

    if (showDelete) {
        ConfirmDialog(
            title = "移入回收站",
            text = "选中的 ${selItems.size} 项会被移到回收站。",
            confirmText = "移入回收站",
            danger = true,
            onDismiss = { showDelete = false },
            onConfirm = {
                val list = selItems
                runOp("正在移入回收站") {
                    withContext(Dispatchers.IO) {
                        "已移入回收站 ${FileOps.deleteToTrash(ctx, list)} 项"
                    }
                }
            }
        )
    }

    if (showCopy) {
        DestPickerDialog("复制到", current.absolutePath, { showCopy = false }, { dest ->
            showCopy = false
            val list = selItems
            runOp("正在复制") { p ->
                withContext(Dispatchers.IO) {
                    val ok = FileOps.copy(list, File(dest)) { d, t, n -> p(if (t > 0) d.toFloat() / t else 0f, n) }
                    "已复制 $ok 项"
                }
            }
        })
    }

    if (showMove) {
        DestPickerDialog("移动到", current.absolutePath, { showMove = false }, { dest ->
            showMove = false
            val list = selItems
            runOp("正在移动") { p ->
                withContext(Dispatchers.IO) {
                    val ok = FileOps.move(list, File(dest)) { d, t, n -> p(if (t > 0) d.toFloat() / t else 0f, n) }
                    "已移动 $ok 项"
                }
            }
        })
    }

    showInfo?.let { item ->
        InfoDialog(title = item.name, rows = listOf(
            "名称" to item.name,
            "大小" to item.size.toSizeText(),
            "路径" to item.path,
            "类型" to if (item.isDir) "文件夹" else com.luogen.filehub.data.TypeDetect.catOf(item.name, item.mime).title
        ), onCopy = { clipboard.setText(AnnotatedString(it)); toast = "已复制" }, onDismiss = { showInfo = null })
    }

    prog?.let { ProgressDialog(it.title, it.progress, it.msg) }
    toast?.let { t ->
        AlertDialog(onDismissRequest = { toast = null }, title = { Text("提示") }, text = { Text(t) },
            confirmButton = { TextButton(onClick = { toast = null }) { Text("知道了") } })
    }
}

@Composable
private fun BrowseAction(label: String, onClick: () -> Unit) {
    Column(
        Modifier.width(64.dp).clip(RoundedCornerShape(10.dp)).clickable(onClick = onClick).padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(label, fontSize = 12.sp, color = BrandBlue)
    }
}
