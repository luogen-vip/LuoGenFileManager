package com.luogen.filehub.ui

import android.content.Context
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
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
import com.luogen.filehub.data.TypeDetect
import com.luogen.filehub.model.Cat
import com.luogen.filehub.model.FileItem
import com.luogen.filehub.model.SortMode
import com.luogen.filehub.model.nameNoExt
import com.luogen.filehub.model.toDayTitle
import com.luogen.filehub.model.toFullTime
import com.luogen.filehub.model.toRelativeTime
import com.luogen.filehub.model.toSizeText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class Prog(val title: String, val progress: Float?, val msg: String)

private data class DayGroup(val day: String, val list: List<FileItem>)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileListScreen(ctx: Context, nav: NavStack, repo: FileRepo, cat: Cat) {
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current

    var items by remember { mutableStateOf<List<FileItem>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var query by remember { mutableStateOf("") }
    var sortMode by remember { mutableStateOf(Prefs.sortMode()) }
    var asc by remember { mutableStateOf(Prefs.sortAsc()) }
    var selected by remember { mutableStateOf<Set<String>>(emptySet()) }
    var refresh by remember { mutableStateOf(0) }
    var appTab by remember { mutableStateOf(0) }
    var gridMode by remember { mutableStateOf(cat == Cat.IMAGE || cat == Cat.VIDEO) }
    var sortExpanded by remember { mutableStateOf(false) }
    var prog by remember { mutableStateOf<Prog?>(null) }
    var toast by remember { mutableStateOf<String?>(null) }
    var favFilter by remember { mutableStateOf<Cat?>(null) }

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

    LaunchedEffect(refresh, cat, appTab) {
        loading = true
        items = withContext(Dispatchers.IO) {
            try {
                when (cat) {
                    Cat.TRASH -> TrashStore.list(ctx).map { it.toItem(ctx) }
                    Cat.APP -> if (appTab == 0) repo.installedApps() else repo.apkFiles().map { repo.apkInfo(it.path) }
                    else -> repo.load(cat)
                }
            } catch (_: Throwable) {
                emptyList()
            }
        }
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
            refresh++
            selected = emptySet()
        }
    }

    Scaffold(
        containerColor = BgDeep,
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                if (selected.isEmpty()) catTitle(cat, appTab) else "已选 ${selected.size} 项",
                                 fontSize = 18.sp, fontWeight = FontWeight.Bold
                            )
                            Text(if (loading) "扫描中…" else "共 ${display.size} 项", fontSize = 11.sp, color = TextDim)
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = { nav.pop() }) { Icon(Icons.Filled.ArrowBack, null) }
                    },
                    actions = {
                        IconButton(onClick = {
                            selected = if (selected.size == display.size) {
                                emptySet()
                            } else display.map { it.key }.toSet()
                        }) { Icon(Icons.Filled.Checklist, null) }
                        IconButton(onClick = { gridMode = !gridMode }) {
                            Icon(if (gridMode) Icons.Filled.ViewList else Icons.Filled.GridView, null)
                        }
                        Box {
                            IconButton(onClick = { sortExpanded = true }) { Icon(Icons.Filled.Sort, null) }
                            DropdownMenu(expanded = sortExpanded, onDismissRequest = { sortExpanded = false }) {
                                SortMode.values().forEach { m ->
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                m.title + if (sortMode == m) (if (asc) " ↑ 升序" else " ↓ 降序") else "",
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
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = BgDeep)
                )

                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                    singleLine = true,
                    placeholder = { Text("在「${cat.title}」里实时搜索…", fontSize = 13.sp) },
                    leadingIcon = { Icon(Icons.Filled.Search, null, modifier = Modifier.size(18.dp)) },
                    trailingIcon = {
                        if (query.isNotEmpty()) {
                            IconButton(onClick = { query = "" }) {
                                Icon(Icons.Filled.Close, null, modifier = Modifier.size(16.dp))
                            }
                        }
                    },
                    textStyle = MaterialTheme.typography.bodyMedium,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = BrandBlue,
                        unfocusedBorderColor = SurfaceDark2,
                        focusedContainerColor = SurfaceDark,
                        unfocusedContainerColor = SurfaceDark
                    )
                )

                if (cat == Cat.APP) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
                        FilterChip(selected = appTab == 0, onClick = { appTab = 0 }, label = { Text("已安装") })
                        Spacer(Modifier.width(8.dp))
                        FilterChip(selected = appTab == 1, onClick = { appTab = 1 }, label = { Text("安装包") })
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
                                onClick = { favFilter = f },
                                label = { Text(if (f == null) "全部" else f.title) }
                            )
                        }
                    }
                }
            }
        },
        bottomBar = {
            if (selected.isNotEmpty()) {
                SelectionBar(
                    isTrash = cat == Cat.TRASH,
                    onShare = { FileOps.share(ctx, selItems) },
                    onCopy = { showCopy = true },
                    onMove = { showMove = true },
                    onDelete = { showDelete = true },
                    onRename = {
                        when {
                            cat == Cat.TRASH -> toast = "回收站文件不能重命名，请先还原"
                            selItems.size == 1 -> showRename = selItems[0]
                            else -> showBatch = selItems
                        }
                    },
                    onFav = {
                        selItems.forEach { Prefs.toggleFav(it.path) }
                        toast = "收藏状态已更新"
                        selected = emptySet()
                    },
                    onCompress = { showCompress = true },
                    onRestore = { showRestoreConfirm = true },
                    onInfo = { if (selItems.size == 1) showInfo = selItems[0] else toast = "只能看单个文件详情" },
                    onRestoreDefault = { showRestoreDefaultConfirm = true }
                )
            }
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = BrandBlue)
                }
                display.isEmpty() -> EmptyView(if (query.isNotEmpty()) "没有找到匹配的文件" else emptyText(cat))
                gridMode -> {
                    LazyVerticalGrid(
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
                                        onOpen = { openItem(ctx, nav, it0, g.list) },
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
                                    onOpen = { openItem(ctx, nav, it0, display) },
                                    onToggle = { toggleSel(selected, it0.key) { selected = it } }
                                )
                            }
                        }
                    }
                }
                else -> {
                    LazyColumn(Modifier.fillMaxSize()) {
                        if (grouped.isNotEmpty()) {
                            grouped.forEach { g ->
                                item { DayHeader(g.day, g.list.size) }
                                items(count = g.list.size, key = { idx -> g.list[idx].key }) { idx ->
                                    val it0 = g.list[idx]
                                    ListCell(
                                        ctx = ctx, nav = nav, item = it0, siblings = g.list, cat = cat,
                                        selected = selected.contains(it0.key),
                                        selectionMode = selected.isNotEmpty(),
                                        onToggle = { toggleSel(selected, it0.key) { selected = it } },
                                        onInfo = { showInfo = it },
                                        onRename = { showRename = it },
                                        onMsg = { toast = it }
                                    )
                                }
                            }
                        } else {
                            items(count = display.size, key = { idx -> display[idx].key }) { idx ->
                                val it0 = display[idx]
                                ListCell(
                                    ctx = ctx, nav = nav, item = it0, siblings = display, cat = cat,
                                    selected = selected.contains(it0.key),
                                    selectionMode = selected.isNotEmpty(),
                                    onToggle = { toggleSel(selected, it0.key) { selected = it } },
                                    onInfo = { showInfo = it },
                                    onRename = { showRename = it },
                                    onMsg = { toast = it }
                                )
                            }
                        }
                        item { Spacer(Modifier.height(80.dp)) }
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
                refresh++
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
                refresh++
                selected = emptySet()
            }
        )
    }

    if (showCopy) {
        DestPickerDialog(
            title = "复制 ${selItems.size} 项到",
            startDir = root,
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
                val list = selItems
                runOp(if (isTrash) "正在彻底删除" else "正在移入回收站") {
                    withContext(Dispatchers.IO) {
                        if (isTrash) {
                            val entries = TrashStore.list(ctx)
                            var ok = 0
                            list.forEach { s ->
                                entries.firstOrNull { it.id == s.id }?.let { TrashStore.deletePermanent(ctx, it); ok++ }
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
                val list = selItems
                runOp("正在还原") {
                    withContext(Dispatchers.IO) {
                        val entries = TrashStore.list(ctx)
                        var ok = 0
                        list.forEach { s ->
                            entries.firstOrNull { it.id == s.id }?.let { if (TrashStore.restore(ctx, it)) ok++ }
                        }
                        "已还原 $ok 项"
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
                val list = selItems
                runOp("正在恢复默认名") {
                    withContext(Dispatchers.IO) {
                        var ok = 0
                        val lines = ArrayList<String>()
                        list.forEach { s ->
                            val r = FileOps.restoreDefault(s)
                            lines.add("${s.name} → $r")
                            if (r == null || r.startsWith("已恢复")) ok++
                        }
                        "处理完成：$ok/${list.size}\n" + lines.take(4).joinToString("\n")
                    }
                }
            }
        )
    }

    if (showCompress) {
        AlertDialog(
            onDismissRequest = { showCompress = false },
            title = { Text("压缩 ${selItems.size} 项") },
            text = { Text("选择压缩格式：") },
            confirmButton = { TextButton(onClick = { showCompress = false; compressFormat = "zip" }) { Text("ZIP") } },
            dismissButton = { TextButton(onClick = { showCompress = false; compressFormat = "7z" }) { Text("7Z") } }
        )
    }

    compressFormat?.let { fmt ->
        DestPickerDialog(
            title = "压缩文件保存到",
            startDir = root,
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
            .clip(RoundedCornerShape(12.dp))
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
                .background(Color(0xCC0B1220))
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
    selected: Boolean,
    selectionMode: Boolean,
    onToggle: () -> Unit,
    onInfo: (FileItem) -> Unit,
    onRename: (FileItem) -> Unit,
    onMsg: (String) -> Unit
) {
    var menu by remember { mutableStateOf(false) }
    val clipboard = LocalClipboardManager.current

    FileRow(
        item = item,
        selected = selected,
        selectionMode = selectionMode,
        onClick = {
            when {
                selectionMode -> onToggle()
                cat == Cat.TRASH -> onMsg("回收站：点右侧菜单可还原 / 彻底删除，长按可多选")
                item.isDir -> nav.push(Screen.Browse(item.path))
                cat == Cat.APP && item.path.endsWith(".apk", true) -> {
                    if (!FileOps.installApk(ctx, item.path)) {
                        onMsg("已跳转授权，请允许「安装未知应用」后再点一次")
                    }
                }
                cat == Cat.APP && item.packageName.isNotEmpty() -> menu = true
                else -> openItem(ctx, nav, item, siblings)
            }
        },
        onLongClick = { if (!selectionMode) onToggle() },
        trailing = {
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, null, tint = TextDim) }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("查看详细信息") }, onClick = { menu = false; onInfo(item) })
                    if (cat == Cat.APP && item.packageName.isNotEmpty() && !item.path.endsWith(".apk", true)) {
                        DropdownMenuItem(text = { Text("打开应用") }, onClick = {
                            menu = false
                            if (!FileOps.launchApp(ctx, item.packageName)) onMsg("打开失败")
                        })
                        DropdownMenuItem(text = { Text("应用设置") }, onClick = { menu = false; FileOps.openAppInfo(ctx, item.packageName) })
                        DropdownMenuItem(text = { Text("卸载") }, onClick = { menu = false; FileOps.uninstall(ctx, item.packageName) })
                    }
                    if (cat == Cat.TRASH) {
                        DropdownMenuItem(text = { Text("还原") }, onClick = {
                            menu = false
                            TrashStore.list(ctx).firstOrNull { it.id == item.id }?.let {
                                onMsg(if (TrashStore.restore(ctx, it)) "已还原" else "还原失败")
                            }
                        })
                        DropdownMenuItem(text = { Text("彻底删除") }, onClick = {
                            menu = false
                            TrashStore.list(ctx).firstOrNull { it.id == item.id }?.let {
                                TrashStore.deletePermanent(ctx, it)
                                onMsg("已彻底删除")
                            }
                        })
                    } else {
                        DropdownMenuItem(text = { Text("重命名") }, onClick = { menu = false; onRename(item) })
                        DropdownMenuItem(text = { Text("恢复默认名") }, onClick = {
                            menu = false
                            onMsg(FileOps.restoreDefault(item) ?: "已恢复")
                        })
                        DropdownMenuItem(text = { Text(if (Prefs.isFav(item.path)) "取消收藏" else "收藏") }, onClick = {
                            menu = false
                            onMsg(if (Prefs.toggleFav(item.path)) "已收藏" else "已取消收藏")
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
                        })
                    }
                }
            }
        }
    )
}

@Composable
private fun SelectionBar(
    isTrash: Boolean,
    onShare: () -> Unit,
    onCopy: () -> Unit,
    onMove: () -> Unit,
    onDelete: () -> Unit,
    onRename: () -> Unit,
    onFav: () -> Unit,
    onCompress: () -> Unit,
    onRestore: () -> Unit,
    onInfo: () -> Unit,
    onRestoreDefault: () -> Unit
) {
    Surface(color = SurfaceDark) {
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 6.dp, vertical = 4.dp)
        ) {
            if (isTrash) {
                BarAction("还原", Icons.Filled.RestoreFromTrash, onRestore)
                BarAction("彻底删除", Icons.Filled.DeleteForever, onDelete)
                BarAction("详情", Icons.Filled.Info, onInfo)
            } else {
                BarAction("分享", Icons.Filled.Share, onShare)
                BarAction("复制", Icons.Filled.ContentCopy, onCopy)
                BarAction("移动", Icons.Filled.DriveFileMove, onMove)
                BarAction("删除", Icons.Filled.Delete, onDelete)
                BarAction("重命名", Icons.Filled.DriveFileRenameOutline, onRename)
                BarAction("收藏", Icons.Filled.Star, onFav)
                BarAction("压缩", Icons.Filled.Archive, onCompress)
                BarAction("恢复默认", Icons.Filled.SettingsBackupRestore, onRestoreDefault)
                BarAction("详情", Icons.Filled.Info, onInfo)
            }
        }
    }
}

@Composable
private fun BarAction(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Column(
        Modifier.width(64.dp).clip(RoundedCornerShape(10.dp)).clickable(onClick = onClick).padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, null, tint = BrandBlue, modifier = Modifier.size(20.dp))
        Spacer(Modifier.height(3.dp))
        Text(label, fontSize = 10.sp, color = TextDim, maxLines = 1)
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
    rows.add("类型判定" to TypeDetect.catOf(item.name, item.mime).title)
    if (item.path.isNotEmpty()) rows.add("完整路径" to item.path)
    val f = File(item.path)
    if (f.exists() && !f.isDirectory) {
        val magic = TypeDetect.detectByMagic(f)
        rows.add("文件头识别" to (magic?.uppercase() ?: "无法识别"))
    }
    if (item.extra.isNotEmpty()) rows.add("备注" to item.extra)
    return rows
}
