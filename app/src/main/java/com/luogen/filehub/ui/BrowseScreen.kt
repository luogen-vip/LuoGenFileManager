package com.luogen.filehub.ui

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.luogen.filehub.data.AudioHub
import com.luogen.filehub.data.FileOps
import com.luogen.filehub.data.FileRepo
import com.luogen.filehub.data.Prefs
import com.luogen.filehub.data.Sorter
import com.luogen.filehub.model.Cat
import com.luogen.filehub.model.FileItem
import com.luogen.filehub.model.SortMode
import com.luogen.filehub.model.toSizeText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
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
    var loading by remember { mutableStateOf(false) }
    var loadedPath by remember { mutableStateOf("") }
    var showSpinner by remember { mutableStateOf(false) }
    val browseCache = remember { HashMap<String, List<FileItem>>() }
    var sortExpanded by remember { mutableStateOf(false) }
    val startPath = dirPath
    val rootPath = android.os.Environment.getExternalStorageDirectory().absolutePath
    var volumes by remember { mutableStateOf<List<com.luogen.filehub.model.VolumeInfo>>(emptyList()) }
    LaunchedEffect(current.absolutePath) {
        volumes = withContext(Dispatchers.IO) { try { repo.volumes() } catch (_: Throwable) { emptyList() } }
    }
    // 每个文件夹一个独立滚动状态，进入新目录不会带着上个目录的位置跳一下
    val listState = remember(current.absolutePath) {
        androidx.compose.foundation.lazy.LazyListState(
            AppState.scrollOf("browse:" + current.absolutePath),
            AppState.scrollOffsetOf("browse:" + current.absolutePath)
        )
    }
    var firstSortEffect by remember { mutableStateOf(true) }
    LaunchedEffect(sortMode, asc, query) {
        if (firstSortEffect) {
            firstSortEffect = false
            return@LaunchedEffect
        }
        try { listState.scrollToItem(0) } catch (_: Throwable) {}
    }

    // 系统返回键：与左上角箭头一致 —— 先取消勾选，再回上一级文件夹，最后才退出
    BackHandler(enabled = true) {
        when {
            selected.isNotEmpty() -> selected = emptySet()
            current.absolutePath != startPath && current.absolutePath != rootPath && current.parentFile != null ->
                current = current.parentFile!!
            else -> nav.pop()
        }
    }

    // 先秒出上次看过的同一文件夹缓存，再后台刷新；绝不先清空 -> 不再闪白/闪空
    LaunchedEffect(current.absolutePath) {
        showSpinner = false
        delay(220)
        if (loadedPath != current.absolutePath) showSpinner = true
    }
    LaunchedEffect(current.absolutePath, refresh) {
        val path = current.absolutePath
        val changedDir = loadedPath != path
        if (changedDir) {
            val hit = browseCache[path]
            if (hit != null) {
                items = hit
                loadedPath = path
                loading = false
            } else {
                loading = true
            }
        }
        val loaded = withContext(Dispatchers.IO) { try { repo.listDir(current) } catch (_: Throwable) { emptyList() } }
        if (loaded.isNotEmpty() || changedDir) browseCache[path] = loaded
        items = loaded
        loadedPath = path
        loading = false
        showSpinner = false
        // 恢复这个文件夹上次的滚动位置
        try {
            val saved = AppState.scrollOf("browse:" + current.absolutePath)
            val off = AppState.scrollOffsetOf("browse:" + current.absolutePath)
            if (saved > 0 && loaded.isNotEmpty()) {
                kotlinx.coroutines.yield()
                listState.scrollToItem(saved.coerceIn(0, (loaded.size - 1).coerceAtLeast(0)), off)
            }
        } catch (_: Throwable) {}
    }
    // 当前文件夹有增删改名时自动刷新，不用手动点刷新
    DisposableEffect(current.absolutePath) {
        val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())
        val obs = try {
            object : android.os.FileObserver(
                current.absolutePath,
                CREATE or DELETE or MOVED_TO or MOVED_FROM or MODIFY or CLOSE_WRITE
            ) {
                override fun onEvent(event: Int, path: String?) {
                    mainHandler.post { refresh++ }
                }
            }.also { it.startWatching() }
        } catch (_: Throwable) { null }
        onDispose { try { obs?.stopWatching() } catch (_: Throwable) {} }
    }

    LaunchedEffect(current.absolutePath) {
        snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }.collect {
            // 加载中/空列表时 index 会瞬间变成 0，不能拿它覆盖已保存的位置
            if (!loading && items.isNotEmpty()) AppState.keepScroll("browse:" + current.absolutePath, it.first, it.second)
        }
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
            AppState.countsDirty = true
            AppState.cleanDirty = true
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
                            } else if (current.absolutePath != startPath && current.absolutePath != rootPath && current.parentFile != null) {
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
                        Box {
                            IconButton(onClick = { sortExpanded = true }) { Icon(Icons.Filled.Sort, null, tint = TextMain) }
                            DropdownMenu(expanded = sortExpanded, onDismissRequest = { sortExpanded = false }, shape = RoundedCornerShape(16)) {
                                SortMode.values().forEach { m ->
                                    DropdownMenuItem(
                                        text = {
                                            Text(m.title + if (sortMode == m) (if (asc) " ↑" else " ↓") else "",
                                                fontWeight = if (sortMode == m) FontWeight.Bold else FontWeight.Normal)
                                        },
                                        onClick = {
                                            if (sortMode == m) asc = !asc else { sortMode = m; asc = false }
                                            Prefs.setSort(sortMode, asc)
                                            sortExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                        IconButton(onClick = { refresh++; toast = "已刷新" }) { Icon(Icons.Filled.Refresh, null, tint = TextMain) }
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
                if (listState.firstVisibleItemIndex > 5) {
                    SmallFloatingActionButton(
                        onClick = { scope.launch { listState.animateScrollToItem(0) } },
                        containerColor = SurfaceDark, contentColor = BrandBlue
                    ) { Icon(Icons.Filled.VerticalAlignTop, null) }
                    Spacer(Modifier.height(8.dp))
                }
                SmallFloatingActionButton(onClick = { newName = ""; showNewFile = true }) {
                    Icon(Icons.Filled.NoteAdd, null)
                }
                Spacer(Modifier.height(8.dp))
                FloatingActionButton(onClick = { newName = ""; showNewFolder = true }) {
                    Icon(Icons.Filled.CreateNewFolder, null)
                }
            }
        },
        bottomBar = {
            if (selected.isNotEmpty()) {
                ActionPanel(
                    listOf(
                        ActionSpec("加入列表", Icons.Filled.PlaylistAdd) {
                            val audio = selItems.filter { com.luogen.filehub.data.TypeDetect.catOf(it.name, it.mime) == Cat.AUDIO }
                            if (audio.isEmpty()) toast = "选中的文件里没有音频"
                            else {
                                val n = AudioHub.enqueue(audio.map { it.path }, ctx)
                                toast = if (n > 0) "已加入 $n 首到播放列表" else "这些歌已经在列表里了"
                                selected = emptySet()
                            }
                        },
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
                            AppState.markDataChanged()
                            AppState.countsDirty = true
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
            val ready = loadedPath == current.absolutePath
            if (!ready) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    if (showSpinner) CircularProgressIndicator(color = BrandBlue)
                }
            } else if (display.isEmpty()) {
                EmptyView(if (query.isNotEmpty()) "没有匹配项" else "这个文件夹是空的")
            } else {
                LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                    if (current.absolutePath == rootPath) {
                        val extraVols = volumes.filter { it.removable }
                        items(count = extraVols.size, key = { "vol_" + extraVols[it].path }) { idx ->
                            val v = extraVols[idx]
                            Row(
                                Modifier.fillMaxWidth()
                                    .clickable { current = File(v.path) }
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    Modifier.size(44.dp).background(BrandBlue.copy(alpha = 0.12f), RoundedCornerShape(10.dp)),
                                    contentAlignment = Alignment.Center
                                ) { Icon(Icons.Filled.Usb, null, tint = BrandBlue, modifier = Modifier.size(24.dp)) }
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(v.name, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                                    Text(
                                        if (v.total > 0) "U盘 · 剩余 ${v.free.toSizeText()} / 共 ${v.total.toSizeText()}" else "U盘",
                                        fontSize = 11.sp, color = TextDim
                                    )
                                }
                                Icon(Icons.Filled.ChevronRight, null, tint = TextDim, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
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
                                    if (menu) {
                                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                        DropdownMenuItem(text = { Text("详细信息") }, onClick = { menu = false; showInfo = item })
                                        if (!item.isDir && com.luogen.filehub.data.TypeDetect.catOf(item.name, item.mime) == Cat.AUDIO) {
                                            DropdownMenuItem(text = { Text("加入播放列表") }, onClick = {
                                                menu = false
                                                val n = AudioHub.enqueue(listOf(item.path), ctx)
                                                toast = if (n > 0) "已加入播放列表" else "这首歌已经在列表里了"
                                            })
                                        }
                                        DropdownMenuItem(text = { Text("重命名") }, onClick = { menu = false; showRename = item })
                                        DropdownMenuItem(text = { Text("恢复默认名") }, onClick = {
                                            menu = false
                                            toast = FileOps.restoreDefault(item) ?: "已恢复"
                                            refresh++
                                        })
                                        DropdownMenuItem(text = { Text(if (Prefs.isFav(item.path)) "取消收藏" else "收藏") }, onClick = {
                                            menu = false
                                            toast = if (Prefs.toggleFav(item.path)) "已收藏" else "已取消收藏"
                                            AppState.markDataChanged()
                                            AppState.countsDirty = true
                                        })
                                        DropdownMenuItem(text = { Text("复制路径") }, onClick = {
                                            menu = false
                                            clipboard.setText(AnnotatedString(item.path))
                                            toast = "路径已复制"
                                        })
                                        DropdownMenuItem(text = { Text("移入回收站") }, onClick = {
                                            menu = false
                                            toast = if (FileOps.deleteToTrash(ctx, listOf(item)) > 0) "已移入回收站" else "失败"
                                            AppState.countsDirty = true
                                            AppState.cleanDirty = true
                                            refresh++
                                        })
                                        if (current.absolutePath != rootPath && current.parentFile != null) {
                                            DropdownMenuItem(text = { Text("上一级") }, onClick = {
                                                menu = false
                                                current.parentFile?.let { current = it }
                                                AppState.countsDirty = true
                                            })
                                        }
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

    if (showNewFolder || showNewFile) {
        val isFolder = showNewFolder
        val focus = remember { FocusRequester() }
        val keyboard = LocalSoftwareKeyboardController.current
        LaunchedEffect(Unit) {
            focus.requestFocus()
            keyboard?.show()
        }
        AlertDialog(
            onDismissRequest = { showNewFolder = false; showNewFile = false },
            shape = RoundedCornerShape(22.dp),
            title = { Text(if (isFolder) "新建文件夹" else "新建文件") },
            text = {
                Column {
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().focusRequester(focus),
                        placeholder = { Text(if (isFolder) "输入文件夹名" else "输入文件名，如 note.txt", fontSize = 14.sp) },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { keyboard?.hide() }),
                        shape = RoundedCornerShape(14.dp)
                    )
                    if (!isFolder) {
                        Spacer(Modifier.height(6.dp))
                        Text("文本类文件创建后可直接编辑；其他类型会创建空文件", fontSize = 11.sp, color = TextDim)
                    }
                    if (newName.isNotBlank() && File(current, newName.trim()).exists()) {
                        Spacer(Modifier.height(6.dp))
                        Text("同名文件已存在，换个名字吧", fontSize = 11.sp, color = DangerRed)
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = newName.isNotBlank(),
                    onClick = {
                        val clean = newName.trim()
                        val f = if (isFolder) FileOps.createFolder(current, clean) else FileOps.createFile(current, clean)
                        if (f != null) {
                            showNewFolder = false; showNewFile = false; newName = ""
                            toast = "已创建 ${f.name}"
                            refresh++
                            if (!isFolder && com.luogen.filehub.data.TypeDetect.catOf(f.name) == Cat.DOC) nav.push(Screen.TextView(f.absolutePath))
                        } else {
                            toast = if (File(current, clean).exists()) "创建失败：同名文件已存在" else "创建失败：没有写入权限（请确认已授予「所有文件访问权限」）"
                        }
                    }
                ) { Text("创建") }
            },
            dismissButton = { TextButton(onClick = { showNewFolder = false; showNewFile = false; newName = "" }) { Text("取消") } }
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
        DestPickerDialog(
            title = "复制到",
            startDir = current.absolutePath,
            volumes = remember { try { repo.volumes() } catch (_: Throwable) { emptyList() } },
            onDismiss = { showCopy = false },
            onPick = { dest ->
                showCopy = false
                val list = selItems
                runOp("正在复制") { p ->
                    withContext(Dispatchers.IO) {
                        val ok = FileOps.copy(list, File(dest)) { d, t, n -> p(if (t > 0) d.toFloat() / t else 0f, n) }
                        "已复制 $ok 项"
                    }
                }
            }
        )
    }

    if (showMove) {
        DestPickerDialog(
            title = "移动到",
            startDir = current.absolutePath,
            volumes = remember { try { repo.volumes() } catch (_: Throwable) { emptyList() } },
            onDismiss = { showMove = false },
            onPick = { dest ->
                showMove = false
                val list = selItems
                runOp("正在移动") { p ->
                    withContext(Dispatchers.IO) {
                        val ok = FileOps.move(list, File(dest)) { d, t, n -> p(if (t > 0) d.toFloat() / t else 0f, n) }
                        "已移动 $ok 项"
                    }
                }
            }
        )
    }

    showInfo?.let { item ->
        InfoDialog(title = item.name, rows = listOf(
            "名称" to item.name,
            "大小" to item.size.toSizeText(),
            "路径" to item.path,
            "类型" to if (item.isDir) "文件夹" else com.luogen.filehub.data.TypeDetect.labelOf(item.name, item.mime),
            "分类" to if (item.isDir) "文件夹" else com.luogen.filehub.data.TypeDetect.catOf(item.name, item.mime).title
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
