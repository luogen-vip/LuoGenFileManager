package com.luogen.filehub.ui

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.luogen.filehub.data.ArchiveUtil
import com.luogen.filehub.data.FileOps
import com.luogen.filehub.data.FileRepo
import com.luogen.filehub.data.Prefs
import com.luogen.filehub.data.Sorter
import com.luogen.filehub.data.TrashStore
import com.luogen.filehub.data.AudioHub
import com.luogen.filehub.data.TypeDetect
import com.luogen.filehub.model.Cat
import com.luogen.filehub.model.FileItem
import com.luogen.filehub.model.SortMode
import com.luogen.filehub.model.nameNoExt
import com.luogen.filehub.model.toDayTitle
import com.luogen.filehub.model.toFullTime
import com.luogen.filehub.model.toSizeText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class Prog(val title: String, val progress: Float?, val msg: String)

internal data class ActionSpec(val label: String, val icon: ImageVector, val run: () -> Unit)

private data class DayGroup(val day: String, val list: List<FileItem>)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun FileListScreen(ctx: Context, nav: NavStack, repo: FileRepo, cat: Cat) {
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current

    var appTab by remember(cat) { mutableStateOf(AppState.catTab(cat)) }
    val cacheKey = "list:${cat.name}:$appTab"

    var items by remember(cacheKey) { mutableStateOf(AppState.cached(cacheKey) ?: emptyList()) }
    var loading by remember(cacheKey) { mutableStateOf(AppState.cached(cacheKey) == null) }
    var query by remember(cat) { mutableStateOf(AppState.catQuery(cat)) }
    var sortMode by remember { mutableStateOf(Prefs.sortMode()) }
    var asc by remember { mutableStateOf(Prefs.sortAsc()) }
    var selected by remember { mutableStateOf<Set<String>>(emptySet()) }
    var force by remember { mutableStateOf(0) }
    var gridMode by remember(cat) { mutableStateOf(AppState.catGrid(cat)) }
    var sortExpanded by remember { mutableStateOf(false) }
    var prog by remember { mutableStateOf<Prog?>(null) }
    var toast by remember { mutableStateOf<String?>(null) }
    var favFilter by remember(cat) { mutableStateOf(AppState.catFav(cat)) }

    var showRename by remember { mutableStateOf<FileItem?>(null) }
    var showBatch by remember { mutableStateOf<List<FileItem>?>(null) }
    var showCopy by remember { mutableStateOf(false) }
    var showMove by remember { mutableStateOf(false) }
    var showDelete by remember { mutableStateOf(false) }
    var showInfo by remember { mutableStateOf<FileItem?>(null) }
    var showCompress by remember { mutableStateOf(false) }
    var compressFormat by remember { mutableStateOf<String?>(null) }
    var showRestoreConfirm by remember { mutableStateOf(false) }
    var showRestoreDefaultConfirm by remember { mutableStateOf(false) }

    val root = android.os.Environment.getExternalStorageDirectory().absolutePath

    // 返回键：勾选 → 清搜索词回到分类内容 → 再按才退出
    BackHandler(enabled = selected.isNotEmpty() || query.isNotBlank()) {
        if (selected.isNotEmpty()) selected = emptySet()
        else {
            query = ""
            AppState.setCatQuery(cat, "")
        }
    }

    LaunchedEffect(cacheKey, force, AppState.dataVersion) {
        // 收藏 / 回收站永远读真实数据，不吃缓存：收藏完、彻底删除完进来必须立刻是真实内容
        val alwaysFresh = cat == Cat.FAV || cat == Cat.TRASH || cat == Cat.DOWNLOAD || cat == Cat.APP
        if (alwaysFresh) AppState.drop(cacheKey)
        if (AppState.cached(cacheKey) == null) loading = true
        val pair = AppState.load(cacheKey, force = force > 0 || alwaysFresh, ttlMs = 3 * 60 * 1000L) {
            when (cat) {
                Cat.TRASH -> TrashStore.list(ctx).map { it.toItem(ctx) }
                Cat.APP -> if (appTab == 0) repo.installedApps()
                else repo.apkFiles().map { repo.apkInfo(it.path) }
                else -> repo.load(cat)
            }
        }
        items = pair.first
        loading = false
    }

    val filtered = remember(items, query, favFilter) {
        var list = items
        if (query.isNotBlank()) {
            list = list.filter {
                it.name.contains(query, true) || it.label.contains(query, true) || it.packageName.contains(query, true)
            }
        }
        if (favFilter != null) list = list.filter { itemCat(it) == favFilter }
        list
    }

    val display = remember(filtered, sortMode, asc) { Sorter.sort(filtered, sortMode, asc) }

    val grouped = remember(display, sortMode, asc, cat) {
        if (sortMode == SortMode.TIME && !asc && (cat == Cat.IMAGE || cat == Cat.VIDEO || cat == Cat.DOWNLOAD)) {
            display.groupBy { it.modified.toDayTitle() }.entries
                .sortedByDescending { e -> e.value.maxOfOrNull { it.modified } ?: 0L }
                .map { DayGroup(it.key, it.value) }
        } else emptyList()
    }

    val selItems = remember(selected, items) { items.filter { selected.contains(it.key) } }
    val isAppInstalled = cat == Cat.APP && appTab == 0

    fun runOp(title: String, block: suspend (prog: (Float, String) -> Unit) -> String) {
        scope.launch {
            prog = Prog(title, null, "准备中…")
            val msg = try {
                block { p, m -> prog = Prog(title, p, m) }
            } catch (t: Throwable) {
                "操作失败：${t.message ?: t.javaClass.simpleName}"
            }
            prog = null
            toast = msg
            AppState.markDataChanged()
            AppState.countsDirty = true
            force++
            selected = emptySet()
        }
    }

    fun extractApks(list: List<FileItem>) {
        scope.launch {
            prog = Prog("正在提取安装包", null, "准备中…")
            val msg = try {
                withContext(Dispatchers.IO) {
                    val dir = File(
                        android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS),
                        "提取的安装包"
                    )
                    dir.mkdirs()
                    var ok = 0
                    list.forEach { it ->
                        val src = File(it.path)
                        if (src.exists()) {
                            val out = FileOps.uniqueTarget(dir, "${it.label.ifEmpty { it.name }}.apk")
                            src.copyTo(out, overwrite = true)
                            ok++
                        }
                    }
                    "已提取 $ok 个安装包到 Download/提取的安装包"
                }
            } catch (t: Throwable) {
                "提取失败：${t.message ?: t.javaClass.simpleName}"
            }
            prog = null
            toast = msg
            AppState.markDataChanged()
            selected = emptySet()
        }
    }

    val listKey = "$cacheKey:list"
    val listState: LazyListState = rememberLazyListState(
        initialFirstVisibleItemIndex = AppState.scrollOf(listKey),
        initialFirstVisibleItemScrollOffset = AppState.scrollOffsetOf(listKey)
    )
    val gridState: LazyGridState = rememberLazyGridState(
        initialFirstVisibleItemIndex = AppState.scrollOf(listKey),
        initialFirstVisibleItemScrollOffset = AppState.scrollOffsetOf(listKey)
    )
    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }.collect {
            AppState.keepScroll(listKey, it.first, it.second)
        }
    }
    LaunchedEffect(gridState) {
        snapshotFlow { gridState.firstVisibleItemIndex to gridState.firstVisibleItemScrollOffset }.collect {
            AppState.keepScroll(listKey, it.first, it.second)
        }
    }
    // 实时性：媒体库变化（改名/下载/删除）与安装/卸载应用都触发静默刷新
    DisposableEffect(cacheKey) {
        val handler = android.os.Handler(android.os.Looper.getMainLooper())
        val co = object : android.database.ContentObserver(handler) {
            override fun onChange(selfChange: Boolean) {
                if (cat != Cat.APP) {
                    AppState.markDataChanged()
                    AppState.countsDirty = true
                    force++
                }
            }
        }
        try {
            ctx.contentResolver.registerContentObserver(
                android.provider.MediaStore.Files.getContentUri("external"), true, co
            )
        } catch (_: Throwable) {}
        val pkgReceiver = object : android.content.BroadcastReceiver() {
            override fun onReceive(c: android.content.Context?, i: android.content.Intent?) {
                if (cat == Cat.APP) {
                    AppState.markDataChanged()
                    AppState.countsDirty = true
                    force++
                }
            }
        }
        try {
            val f = android.content.IntentFilter().apply {
                addAction(android.content.Intent.ACTION_PACKAGE_REMOVED)
                addAction(android.content.Intent.ACTION_PACKAGE_ADDED)
                addAction(android.content.Intent.ACTION_PACKAGE_REPLACED)
                addDataScheme("package")
            }
            ctx.registerReceiver(pkgReceiver, f)
        } catch (_: Throwable) {}
        onDispose {
            try { ctx.contentResolver.unregisterContentObserver(co) } catch (_: Throwable) {}
            try { ctx.unregisterReceiver(pkgReceiver) } catch (_: Throwable) {}
        }
    }

    // 换排序 / 搜索 / 切换已安装·安装包才回顶；第一次进入要恢复上次的位置
    var firstSortEffect by remember { mutableStateOf(true) }
    var restoredScroll by remember { mutableStateOf(false) }
    LaunchedEffect(sortMode, asc, query, appTab, gridMode) {
        if (firstSortEffect) {
            firstSortEffect = false
            return@LaunchedEffect
        }
        try { listState.scrollToItem(0) } catch (_: Throwable) {}
        try { gridState.scrollToItem(0) } catch (_: Throwable) {}
    }
    LaunchedEffect(items.size, cat, appTab) {
        if (!restoredScroll && items.isNotEmpty()) {
            restoredScroll = true
            val saved = AppState.scrollOf(listKey)
            val off = AppState.scrollOffsetOf(listKey)
            if (saved > 0) {
                val max = (items.size - 1).coerceAtLeast(0)
                try { listState.scrollToItem(saved.coerceAtMost(max), off) } catch (_: Throwable) {}
                try { gridState.scrollToItem(saved.coerceAtMost(max), off) } catch (_: Throwable) {}
            }
        }
    }

    Scaffold(
        containerColor = BgDeep,
        floatingActionButton = {
            val atTop = listState.firstVisibleItemIndex <= 4 && gridState.firstVisibleItemIndex <= 4
            if (!atTop) {
                SmallFloatingActionButton(
                    onClick = {
                        scope.launch {
                            try { if (gridMode) gridState.animateScrollToItem(0) else listState.animateScrollToItem(0) } catch (_: Throwable) {}
                        }
                    },
                    containerColor = SurfaceDark, contentColor = BrandBlue
                ) { Icon(Icons.Filled.VerticalAlignTop, null) }
            }
        },
        topBar = {
            Column(Modifier.background(SurfaceDark)) {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                if (selected.isEmpty()) catTitle(cat, appTab) else "已选 ${selected.size} 项",
                                fontSize = 17.sp, fontWeight = FontWeight.Bold, color = TextMain
                            )
                            Text(
                                if (loading) "正在加载…" else "共 ${display.size} 项",
                                fontSize = 11.sp, color = TextDim
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = {
                            if (selected.isNotEmpty()) selected = emptySet()
                            else if (query.isNotBlank()) {
                                query = ""
                                AppState.setCatQuery(cat, "")
                            } else nav.pop()
                        }) { Icon(Icons.Filled.ArrowBack, null, tint = TextMain) }
                    },
                    actions = {
                        IconButton(onClick = {
                            selected = if (selected.size == display.size) emptySet() else display.map { it.key }.toSet()
                        }) { Icon(Icons.Filled.Checklist, null, tint = TextMain) }
                        IconButton(onClick = {
                            gridMode = !gridMode
                            AppState.setCatGrid(cat, gridMode)
                        }) {
                            Icon(if (gridMode) Icons.Filled.ViewList else Icons.Filled.GridView, null, tint = TextMain)
                        }
                        Box {
                            IconButton(onClick = { sortExpanded = true }) { Icon(Icons.Filled.Sort, null, tint = TextMain) }
                            DropdownMenu(expanded = sortExpanded, onDismissRequest = { sortExpanded = false }) {
                                SortMode.values().forEach { m ->
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                m.title + if (sortMode == m) (if (asc) " ↑" else " ↓") else "",
                                                fontWeight = if (sortMode == m) FontWeight.Bold else FontWeight.Normal
                                            )
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
                            onValueChange = {
                                query = it
                                AppState.setCatQuery(cat, it)
                            },
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyMedium.copy(color = TextMain, fontSize = 13.sp)
                        )
                        if (query.isEmpty()) Text("在「${cat.title}」里搜索…", color = TextDim, fontSize = 13.sp)
                    }
                    if (query.isNotEmpty()) {
                        Icon(
                            Icons.Filled.Close, null, tint = TextDim,
                            modifier = Modifier.size(17.dp).clickable {
                                query = ""
                                AppState.setCatQuery(cat, "")
                            }
                        )
                    }
                }
                if (query.isNotBlank()) PathJumpBar(nav, query, cat)
                Spacer(Modifier.height(4.dp))

                if (cat == Cat.APP) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                        FilterChip(
                            selected = appTab == 0,
                            onClick = { appTab = 0; AppState.setCatTab(cat, 0); selected = emptySet() },
                            label = { Text("已安装") },
                            shape = RoundedCornerShape(20)
                        )
                        Spacer(Modifier.width(8.dp))
                        FilterChip(
                            selected = appTab == 1,
                            onClick = { appTab = 1; AppState.setCatTab(cat, 1); selected = emptySet() },
                            label = { Text("安装包") },
                            shape = RoundedCornerShape(20)
                        )
                    }
                }
                if (cat == Cat.FAV) {
                    Row(
                        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(null, Cat.IMAGE, Cat.VIDEO, Cat.AUDIO, Cat.DOC, Cat.APP, Cat.ARCHIVE).forEach { f ->
                            FilterChip(
                                selected = favFilter == f,
                                onClick = {
                                    favFilter = if (favFilter == f) null else f
                                    AppState.setCatFav(cat, favFilter)
                                },
                                label = { Text(if (f == null) "全部" else f.title) },
                                shape = RoundedCornerShape(20)
                            )
                        }
                    }
                }
                Spacer(Modifier.height(6.dp))
            }
        },
        bottomBar = {
            if (selected.isNotEmpty()) {
                val actions: List<ActionSpec> = when {
                    cat == Cat.TRASH -> listOf(
                        ActionSpec("还原", Icons.Filled.RestoreFromTrash) { showRestoreConfirm = true },
                        ActionSpec("彻底删除", Icons.Filled.DeleteForever) { showDelete = true },
                        ActionSpec("详情", Icons.Filled.Info) {
                            if (selItems.size == 1) showInfo = selItems[0] else toast = "只能看单个文件详情"
                        }
                    )
                    isAppInstalled -> listOf(
                        ActionSpec("卸载", Icons.Filled.Delete) {
                            selItems.forEach { FileOps.uninstall(ctx, it.packageName) }
                        },
                        ActionSpec("提取安装包", Icons.Filled.SaveAlt) { extractApks(selItems) },
                        ActionSpec("应用信息", Icons.Filled.Info) {
                            selItems.firstOrNull()?.let { FileOps.openAppInfo(ctx, it.packageName) }
                        },
                        ActionSpec("详情", Icons.Filled.Description) {
                            if (selItems.size == 1) showInfo = selItems[0] else toast = "只能看单个文件详情"
                        }
                    )
                    else -> listOf(
                        ActionSpec("加入列表", Icons.Filled.PlaylistAdd) {
                            val audio = selItems.filter { TypeDetect.catOf(it.name, it.mime) == Cat.AUDIO }
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
                            if (selItems.size == 1) showRename = selItems[0] else showBatch = selItems
                        },
                        ActionSpec(if (cat == Cat.FAV) "取消收藏" else "收藏", if (cat == Cat.FAV) Icons.Filled.BookmarkRemove else Icons.Filled.Bookmark) {
                            selItems.forEach { Prefs.toggleFav(it.path) }
                            AppState.markDataChanged()
                            AppState.countsDirty = true
                            toast = if (cat == Cat.FAV) "已取消收藏" else "收藏状态已更新"
                            force++
                            selected = emptySet()
                        },
                        ActionSpec(if (cat == Cat.ARCHIVE) "解压" else "压缩", Icons.Filled.FolderZip) {
                            if (cat == Cat.ARCHIVE && selItems.size == 1) nav.push(Screen.ArchiveView(selItems[0].path))
                            else showCompress = true
                        },
                        ActionSpec("恢复默认名", Icons.Filled.SettingsBackupRestore) { showRestoreDefaultConfirm = true },
                        ActionSpec("详情", Icons.Filled.Info) {
                            if (selItems.size == 1) showInfo = selItems[0] else toast = "只能看单个文件详情"
                        }
                    )
                }
                ActionPanel(actions)
            } else if (cat == Cat.AUDIO && AudioHub.playlist.isNotEmpty()) {
                Box(Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
                    MiniPlayerBar(nav)
                }
            }
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                loading && items.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = BrandBlue, strokeWidth = 2.5.dp)
                }
                display.isEmpty() -> EmptyView(if (query.isNotEmpty()) "没有找到匹配的文件" else emptyText(cat))
                gridMode -> {
                    LazyVerticalGrid(
                        state = gridState,
                        columns = GridCells.Fixed(3),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (grouped.isNotEmpty()) {
                            grouped.forEach { g ->
                                item(span = { GridItemSpan(3) }) { DayHeader(g.day, g.list.size) }
                                items(count = g.list.size, key = { idx -> g.list[idx].key }) { idx ->
                                    val it0 = g.list[idx]
                                    GridCell(
                                        item = it0,
                                        selected = selected.contains(it0.key),
                                        selectionMode = selected.isNotEmpty(),
                                        onOpen = { openByCat(ctx, nav, it0, display, cat, appTab, toast = { toast = it }) },
                                        onToggle = { toggleSel(selected, it0.key) { selected = it } }
                                    )
                                }
                            }
                        } else {
                            items(count = display.size, key = { idx -> display[idx].key }) { idx ->
                                val it0 = display[idx]
                                GridCell(
                                    item = it0,
                                    selected = selected.contains(it0.key),
                                    selectionMode = selected.isNotEmpty(),
                                    onOpen = { openByCat(ctx, nav, it0, display, cat, appTab, toast = { toast = it }) },
                                    onToggle = { toggleSel(selected, it0.key) { selected = it } }
                                )
                            }
                        }
                    }
                }
                else -> {
                    LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                        if (grouped.isNotEmpty()) {
                            grouped.forEach { g ->
                                item { DayHeader(g.day, g.list.size) }
                                items(count = g.list.size, key = { idx -> g.list[idx].key }) { idx ->
                                    val it0 = g.list[idx]
                                    ListCell(
                                        ctx = ctx, nav = nav, item = it0, siblings = display, cat = cat, appTab = appTab,
                                        selected = selected.contains(it0.key),
                                        selectionMode = selected.isNotEmpty(),
                                        onToggle = { toggleSel(selected, it0.key) { selected = it } },
                                        onInfo = { showInfo = it },
                                        onRename = { showRename = it },
                                        onMsg = { toast = it },
                                        onExtract = { extractApks(listOf(it0)) },
                                        onRefresh = { AppState.markDataChanged(); AppState.countsDirty = true; force++ }
                                    )
                                }
                            }
                        } else {
                            items(count = display.size, key = { idx -> display[idx].key }) { idx ->
                                val it0 = display[idx]
                                ListCell(
                                    ctx = ctx, nav = nav, item = it0, siblings = display, cat = cat, appTab = appTab,
                                    selected = selected.contains(it0.key),
                                    selectionMode = selected.isNotEmpty(),
                                    onToggle = { toggleSel(selected, it0.key) { selected = it } },
                                    onInfo = { showInfo = it },
                                    onRename = { showRename = it },
                                    onMsg = { toast = it },
                                    onExtract = { extractApks(listOf(it0)) },
                                    onRefresh = { AppState.markDataChanged(); force++ }
                                )
                            }
                        }
                        item { Spacer(Modifier.height(16.dp)) }
                    }
                }
            }
        }
    }

    showRename?.let { item ->
        RenameDialog(
            initial = item.name,
            onDismiss = { showRename = null },
            onConfirm = { newName ->
                showRename = null
                val err = FileOps.rename(item, newName)
                toast = err ?: "已重命名"
                AppState.markDataChanged()
                AppState.countsDirty = true
                force++
            }
        )
    }

    showBatch?.let { list ->
        BatchRenameDialog(
            items = list,
            onDismiss = { showBatch = null },
            onConfirm = { pairs ->
                showBatch = null
                var ok = 0
                pairs.forEach { (item, newName) -> if (FileOps.rename(item, newName) == null) ok++ }
                toast = "批量重命名完成：$ok/${pairs.size}"
                AppState.markDataChanged()
                selected = emptySet()
                force++
            }
        )
    }

    if (showCopy) {
        DestPickerDialog(
            title = "复制 ${selItems.size} 项到",
            startDir = root,
            volumes = remember { try { repo.volumes() } catch (_: Throwable) { emptyList() } },
            onDismiss = { showCopy = false },
            onPick = { dest ->
                showCopy = false
                val list = selItems
                runOp("正在复制") { p ->
                    withContext(Dispatchers.IO) {
                        val ok = FileOps.copy(list, File(dest)) { d, t, n -> p(if (t > 0) d.toFloat() / t else 0f, n) }
                        "已复制 $ok 个到 $dest"
                    }
                }
            }
        )
    }

    if (showMove) {
        DestPickerDialog(
            title = "移动 ${selItems.size} 项到",
            startDir = root,
            volumes = remember { try { repo.volumes() } catch (_: Throwable) { emptyList() } },
            onDismiss = { showMove = false },
            onPick = { dest ->
                showMove = false
                val list = selItems
                runOp("正在移动") { p ->
                    withContext(Dispatchers.IO) {
                        val ok = FileOps.move(list, File(dest)) { d, t, n -> p(if (t > 0) d.toFloat() / t else 0f, n) }
                        "已移动 $ok 个到 $dest"
                    }
                }
            }
        )
    }

    if (showDelete) {
        val isTrash = cat == Cat.TRASH
        ConfirmDialog(
            title = if (isTrash) "彻底删除" else "移入回收站",
            text = if (isTrash) "选中的 ${selItems.size} 项将被彻底删除，无法恢复。"
            else "选中的 ${selItems.size} 项会移到回收站，可在回收站还原。",
            confirmText = if (isTrash) "彻底删除" else "移入回收站",
            danger = true,
            onDismiss = { showDelete = false },
            onConfirm = {
                showDelete = false
                val list = selItems
                runOp(if (isTrash) "正在彻底删除" else "正在移入回收站") {
                    withContext(Dispatchers.IO) {
                        if (isTrash) {
                            val entries = TrashStore.list(ctx)
                            var ok = 0
                            list.forEach { s ->
                                entries.firstOrNull { it.id == s.id }?.let { if (TrashStore.deletePermanent(ctx, it)) ok++ }
                            }
                            "已彻底删除 $ok 项"
                        } else {
                            val ok = FileOps.deleteToTrash(ctx, list)
                            "已移入回收站：$ok 项"
                        }
                    }
                }
            }
        )
    }

    if (showRestoreConfirm) {
        ConfirmDialog(
            title = "还原文件",
            text = "把选中的 ${selItems.size} 项还原到原来的位置。",
            onDismiss = { showRestoreConfirm = false },
            onConfirm = {
                showRestoreConfirm = false
                val list = selItems
                runOp("正在还原") {
                    withContext(Dispatchers.IO) {
                        val entries = TrashStore.list(ctx)
                        var ok = 0
                        var failMsg = ""
                        list.forEach { s ->
                            entries.firstOrNull { it.id == s.id }?.let { e ->
                                val err = TrashStore.restore(ctx, e)
                                if (err == null) ok++ else failMsg = err
                            }
                        }
                        if (ok > 0) "已还原 $ok 项" else failMsg.ifEmpty { "还原失败" }
                    }
                }
            }
        )
    }

    if (showRestoreDefaultConfirm) {
        ConfirmDialog(
            title = "恢复默认文件名",
            text = "按记录的原始文件名恢复，并用文件头识别真实类型、自动修正扩展名。",
            onDismiss = { showRestoreDefaultConfirm = false },
            onConfirm = {
                showRestoreDefaultConfirm = false
                val list = selItems
                runOp("正在恢复默认名") {
                    withContext(Dispatchers.IO) {
                        var ok = 0
                        list.forEach { s -> if (FileOps.restoreDefault(s) == null) ok++ }
                        "处理完成：$ok/${list.size}"
                    }
                }
            }
        )
    }

    if (showCompress) {
        SelectDialog(
            title = "压缩 ${selItems.size} 项",
            message = "选择压缩格式",
            options = listOf("ZIP（兼容性最好）" to "zip", "7Z（压缩率更高）" to "7z"),
            onDismiss = { showCompress = false },
            onPick = { fmt ->
                showCompress = false
                compressFormat = fmt
            }
        )
    }

    compressFormat?.let { fmt ->
        DestPickerDialog(
            title = "压缩文件保存到",
            startDir = root,
            volumes = remember { try { repo.volumes() } catch (_: Throwable) { emptyList() } },
            onDismiss = { compressFormat = null },
            onPick = { dest ->
                val list = selItems
                compressFormat = null
                runOp("正在压缩") { p ->
                    withContext(Dispatchers.IO) {
                        val dir = File(dest)
                        val base = list.firstOrNull()?.name?.let { nameNoExt(it) } ?: "archive"
                        var out = File(dir, "$base.$fmt")
                        var i = 1
                        while (out.exists()) {
                            out = File(dir, "${base}_$i.$fmt"); i++
                        }
                        val r = ArchiveUtil.compress(list.map { File(it.path) }, out, fmt) { d, t, n ->
                            p(if (t > 0) d.toFloat() / t else 0f, n)
                        }
                        if (r.ok) "已压缩：${out.name}" else r.message
                    }
                }
            }
        )
    }

    showInfo?.let { item ->
        InfoDialog(
            title = item.label.ifEmpty { item.name },
            rows = infoRows(item),
            onCopy = {
                clipboard.setText(AnnotatedString(it))
                toast = "路径已复制"
            },
            onDismiss = { showInfo = null }
        )
    }

    prog?.let { ProgressDialog(it.title, it.progress, it.msg) }

    toast?.let { t ->
        AlertDialog(
            onDismissRequest = { toast = null },
            shape = RoundedCornerShape(22),
            title = { Text("提示") },
            text = { Text(t) },
            confirmButton = { TextButton(onClick = { toast = null }) { Text("知道了") } }
        )
    }
}

private fun catTitle(cat: Cat, appTab: Int): String = when (cat) {
    Cat.APP -> if (appTab == 0) "应用 · 已安装" else "应用 · 安装包"
    else -> cat.title
}

private fun emptyText(cat: Cat): String = when (cat) {
    Cat.IMAGE -> "还没有扫描到图片"
    Cat.VIDEO -> "还没有扫描到视频"
    Cat.AUDIO -> "还没有扫描到音频"
    Cat.DOC -> "还没有扫描到文档"
    Cat.APP -> "没有找到安装包"
    Cat.ARCHIVE -> "还没有扫描到压缩包"
    Cat.FAV -> "还没有收藏任何文件"
    Cat.DOWNLOAD -> "最近没有下载记录"
    Cat.TRASH -> "回收站是空的"
    else -> "这里还没有内容"
}

internal fun toggleSel(current: Set<String>, key: String, setter: (Set<String>) -> Unit) {
    setter(if (current.contains(key)) current - key else current + key)
}

private fun openByCat(
    ctx: Context,
    nav: NavStack,
    item: FileItem,
    siblings: List<FileItem>,
    cat: Cat,
    appTab: Int,
    toast: (String) -> Unit
) {
    if (cat == Cat.TRASH) {
        toast("点右侧菜单可还原 / 彻底删除")
        return
    }
    if (cat == Cat.APP) {
        if (appTab == 1) {
            if (!FileOps.installApk(ctx, item.path)) toast("已跳转授权，请允许安装未知应用后再点一次")
        } else {
            FileOps.launchApp(ctx, item.packageName)?.let { toast(it) }
        }
        return
    }
    if (item.isDir) {
        nav.push(Screen.Browse(item.path))
        return
    }
    openItem(ctx, nav, item, siblings)
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GridCell(
    item: FileItem,
    selected: Boolean,
    selectionMode: Boolean,
    onOpen: () -> Unit,
    onToggle: () -> Unit
) {
    Box(
        Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(12))
            .background(SurfaceDark)
            .combinedClickable(
                onClick = { if (selectionMode) onToggle() else onOpen() },
                onLongClick = { if (!selectionMode) onToggle() }
            )
    ) {
        ThumbBox(Modifier.fillMaxSize(), item, corner = 12)
        if (selectionMode) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .size(22.dp)
                    .clip(RoundedCornerShape(50))
                    .background(if (selected) BrandBlue else Color(0x66000000)),
                contentAlignment = Alignment.Center
            ) {
                if (selected) Icon(Icons.Filled.Check, null, tint = Color.White, modifier = Modifier.size(14.dp))
            }
        }
        Column(
            Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .background(Color(0x99000000))
                .padding(horizontal = 6.dp, vertical = 4.dp)
        ) {
            Text(item.name, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, color = Color.White)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ListCell(
    ctx: Context,
    nav: NavStack,
    item: FileItem,
    siblings: List<FileItem>,
    cat: Cat,
    appTab: Int,
    selected: Boolean,
    selectionMode: Boolean,
    onToggle: () -> Unit,
    onInfo: (FileItem) -> Unit,
    onRename: (FileItem) -> Unit,
    onMsg: (String) -> Unit,
    onExtract: () -> Unit,
    onRefresh: () -> Unit
) {
    var menu by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    val installed = cat == Cat.APP && appTab == 0

    FileRow(
        item = item,
        selected = selected,
        selectionMode = selectionMode,
        onClick = {
            if (selectionMode) onToggle()
            else openByCat(ctx, nav, item, siblings, cat, appTab, onMsg)
        },
        onLongClick = { if (!selectionMode) onToggle() },
        trailing = {
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, null, tint = TextDim) }
                if (menu) {
                    DropdownMenu(
                    expanded = menu,
                    onDismissRequest = { menu = false },
                    shape = RoundedCornerShape(16)
                ) {
                    DropdownMenuItem(text = { Text("详情") }, onClick = { menu = false; onInfo(item) })
                    if (installed) {
                        DropdownMenuItem(text = { Text("打开应用") }, onClick = {
                            menu = false
                            FileOps.launchApp(ctx, item.packageName)?.let { onMsg(it) }
                        })
                        DropdownMenuItem(text = { Text("提取安装包") }, onClick = { menu = false; onExtract() })
                        DropdownMenuItem(text = { Text("应用信息") }, onClick = {
                            menu = false
                            FileOps.openAppInfo(ctx, item.packageName)
                        })
                        DropdownMenuItem(text = { Text("卸载") }, onClick = {
                            menu = false
                            FileOps.uninstall(ctx, item.packageName)
                        })
                    } else if (cat == Cat.ARCHIVE) {
                        DropdownMenuItem(text = { Text("解压 / 查看内容") }, onClick = {
                            menu = false
                            nav.push(Screen.ArchiveView(item.path))
                        })
                        DropdownMenuItem(text = { Text("解压到 Download") }, onClick = {
                            menu = false
                            scope.launch {
                                onMsg("正在解压…")
                                val dest = File(
                                    android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS),
                                    item.name.substringBeforeLast('.').ifEmpty { item.name }
                                )
                                val r = withContext(Dispatchers.IO) { ArchiveUtil.extract(File(item.path), dest) }
                                onMsg(r.message)
                                onRefresh()
                            }
                        })
                    } else if (cat == Cat.APP && appTab == 1) {
                        DropdownMenuItem(text = { Text("安装") }, onClick = {
                            menu = false
                            if (!FileOps.installApk(ctx, item.path)) onMsg("请允许安装未知应用")
                        })
                        DropdownMenuItem(text = { Text("分享") }, onClick = { menu = false; FileOps.share(ctx, listOf(item)) })
                        DropdownMenuItem(text = { Text("移入回收站") }, onClick = {
                            menu = false
                            onMsg(if (FileOps.deleteToTrash(ctx, listOf(item)) > 0) "已移入回收站" else "操作失败")
                            onRefresh()
                        })
                    }
                    if (cat == Cat.TRASH) {
                        DropdownMenuItem(text = { Text("还原") }, onClick = {
                            menu = false
                            TrashStore.list(ctx).firstOrNull { it.id == item.id }?.let {
                                onMsg(TrashStore.restore(ctx, it) ?: "已还原")
                                onRefresh()
                            }
                        })
                        DropdownMenuItem(text = { Text("彻底删除") }, onClick = {
                            menu = false
                            val e = TrashStore.list(ctx).firstOrNull { it.id == item.id }
                            if (e == null) {
                                onMsg("这条记录已经不在了")
                            } else {
                                val ok = TrashStore.deletePermanent(ctx, e)
                                onMsg(if (ok) "已彻底删除" else "删除失败：文件被占用或没有权限")
                                onRefresh()
                            }
                        })
                    } else if (!installed) {
                        if (TypeDetect.catOf(item.name, item.mime) == Cat.AUDIO) {
                            DropdownMenuItem(text = { Text("加入播放列表") }, onClick = {
                                menu = false
                                val n = AudioHub.enqueue(listOf(item.path), ctx)
                                onMsg(if (n > 0) "已加入播放列表" else "这首歌已经在列表里了")
                            })
                        }
                        DropdownMenuItem(text = { Text("重命名") }, onClick = { menu = false; onRename(item) })
                        DropdownMenuItem(text = { Text("恢复默认名") }, onClick = {
                            menu = false
                            onMsg(FileOps.restoreDefault(item) ?: "已恢复")
                            onRefresh()
                        })
                        DropdownMenuItem(text = { Text(if (Prefs.isFav(item.path)) "取消收藏" else "收藏") }, onClick = {
                            menu = false
                            onMsg(if (Prefs.toggleFav(item.path)) "已收藏" else "已取消收藏")
                            onRefresh()
                        })
                        DropdownMenuItem(text = { Text("分享") }, onClick = { menu = false; FileOps.share(ctx, listOf(item)) })
                        DropdownMenuItem(text = { Text("复制路径") }, onClick = {
                            menu = false
                            clipboard.setText(AnnotatedString(item.path))
                            onMsg("路径已复制")
                        })
                        DropdownMenuItem(text = { Text("移入回收站") }, onClick = {
                            menu = false
                            onMsg(if (FileOps.deleteToTrash(ctx, listOf(item)) > 0) "已移入回收站" else "操作失败")
                            onRefresh()
                        })
                    }
                }
                }
            }
        }
    )
}

@Composable
internal fun ActionPanel(actions: List<ActionSpec>) {
    Surface(color = SurfaceDark, shadowElevation = 8.dp) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 6.dp)) {
            actions.chunked(5).forEach { rowActions ->
                Row(Modifier.fillMaxWidth()) {
                    rowActions.forEach { act ->
                        Column(
                            Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12))
                                .clickable(onClick = act.run)
                                .padding(vertical = 6.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(act.icon, null, tint = BrandBlue, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.height(3.dp))
                            Text(act.label, fontSize = 10.sp, color = TextDim, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    repeat(5 - rowActions.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

private fun infoRows(item: FileItem): List<Pair<String, String>> {
    val rows = ArrayList<Pair<String, String>>()
    rows.add("名称" to item.name)
    if (item.label.isNotEmpty()) rows.add("应用名" to item.label)
    if (item.packageName.isNotEmpty()) rows.add("包名" to item.packageName)
    if (item.versionName.isNotEmpty()) rows.add("版本" to item.versionName)
    rows.add("大小" to item.size.toSizeText())
    if (item.modified > 0) rows.add("修改时间" to item.modified.toFullTime())
    if (item.ext.isNotEmpty()) rows.add("扩展名" to item.ext.uppercase())
    rows.add("文件类型" to TypeDetect.labelOf(item.name, item.mime))
    if (item.path.isNotEmpty()) rows.add("完整路径" to item.path)
    val f = File(item.path)
    if (f.exists() && !f.isDirectory) {
        val magic = TypeDetect.detectByMagic(f)
        rows.add("文件头识别" to (magic?.uppercase() ?: "无法识别"))
        TypeDetect.mismatchHint(f)?.let { rows.add("提示" to it) }
    }
    if (item.extra.isNotEmpty()) rows.add("备注" to item.extra)
    return rows
}
