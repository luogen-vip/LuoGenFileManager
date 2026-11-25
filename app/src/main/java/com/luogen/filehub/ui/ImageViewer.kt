package com.luogen.filehub.ui

import android.content.Context
import android.graphics.BitmapFactory
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.luogen.filehub.data.FileOps
import com.luogen.filehub.data.Prefs
import com.luogen.filehub.data.TypeDetect
import com.luogen.filehub.model.Cat
import com.luogen.filehub.model.FileItem
import com.luogen.filehub.model.toFullTime
import com.luogen.filehub.model.toSizeText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun ImageViewerScreen(nav: NavStack, paths: List<String>, start: Int) {
    val ctx = LocalContext.current
    val clipboard = LocalClipboardManager.current
    if (paths.isEmpty()) {
        Box(Modifier.fillMaxSize().background(Color.Black))
        return
    }
    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState(
        initialPage = start.coerceIn(0, paths.size - 1),
        pageCount = { paths.size }
    )
    var chrome by remember { mutableStateOf(true) }
    var infoFor by remember { mutableStateOf<String?>(null) }
    var toast by remember { mutableStateOf<String?>(null) }
    var renameFor by remember { mutableStateOf<String?>(null) }
    var zoomed by remember { mutableStateOf(false) }
    LaunchedEffect(pagerState.currentPage) { zoomed = false }

    val cur = paths[pagerState.currentPage]
    fun itemOf(p: String) = FileItem(path = p, name = File(p).name, size = File(p).length(), mime = TypeDetect.mimeOf(p))

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            userScrollEnabled = !zoomed
        ) { page ->
            ImagePage(
                path = paths[page],
                onTap = { chrome = !chrome },
                onZoomChange = { zoomed = it },
                onEdgeNext = {
                    if (pagerState.currentPage < paths.size - 1) {
                        zoomed = false
                        scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                    }
                },
                onEdgePrev = {
                    if (pagerState.currentPage > 0) {
                        zoomed = false
                        scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) }
                    }
                }
            )
        }

        if (chrome) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(Color(0x99000000))
                    .padding(horizontal = 6.dp, vertical = 8.dp)
                    .align(Alignment.TopStart),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { nav.pop() }) { Icon(Icons.Filled.ArrowBack, null, tint = Color.White) }
                Column(Modifier.weight(1f)) {
                    Text(File(cur).name, color = Color.White, fontSize = 14.sp, maxLines = 1)
                    Text("${pagerState.currentPage + 1} / ${paths.size}", color = Color(0xCCFFFFFF), fontSize = 11.sp)
                }
                IconButton(onClick = { infoFor = cur }) { Icon(Icons.Filled.Info, null, tint = Color.White) }
                IconButton(onClick = { FileOps.share(ctx, listOf(itemOf(cur))) }) {
                    Icon(Icons.Filled.Share, null, tint = Color.White)
                }
            }

            // 底部功能栏（预览时也能分享/收藏/重命名/删除）
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(Color(0x99000000))
                    .padding(vertical = 6.dp)
                    .align(Alignment.BottomCenter),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                ViewerAction("分享", Icons.Filled.Share) { FileOps.share(ctx, listOf(itemOf(cur))) }
                ViewerAction(
                    if (Prefs.isFav(cur)) "已收藏" else "收藏",
                    if (Prefs.isFav(cur)) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder
                ) {
                    toast = if (Prefs.toggleFav(cur)) "已收藏" else "已取消收藏"
                    AppState.markDataChanged()
                    AppState.countsDirty = true
                }
                ViewerAction("重命名", Icons.Filled.DriveFileRenameOutline) { renameFor = cur }
                ViewerAction("删除", Icons.Filled.DeleteOutline) {
                    val n = FileOps.deleteToTrash(ctx, listOf(itemOf(cur)))
                    toast = if (n > 0) "已移入回收站" else "删除失败"
                    if (n > 0) { AppState.markDataChanged(); AppState.countsDirty = true }
                    nav.pop()
                }
                ViewerAction("详情", Icons.Filled.Info) { infoFor = cur }
            }
        }
    }

    infoFor?.let { path ->
        val f = File(path)
        val bounds = remember(path) {
            val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(path, o)
            "${o.outWidth} × ${o.outHeight}"
        }
        InfoDialog(
            title = f.name,
            rows = listOf(
                "分辨率" to bounds,
                "大小" to f.length().toSizeText(),
                "修改时间" to f.lastModified().toFullTime(),
                "格式" to f.name.substringAfterLast('.', "").uppercase(),
                "文件类型" to TypeDetect.labelOf(f.name),
                "完整路径" to path
            ),
            onCopy = { clipboard.setText(AnnotatedString(it)); toast = "路径已复制" },
            onDismiss = { infoFor = null }
        )
    }

    renameFor?.let { p ->
        RenameDialog(
            initial = File(p).name,
            onDismiss = { renameFor = null },
            onConfirm = { newName ->
                renameFor = null
                toast = FileOps.rename(itemOf(p), newName) ?: "已重命名（返回列表后刷新可见）"
            }
        )
    }

    toast?.let { t ->
        AlertDialog(
            onDismissRequest = { toast = null },
            shape = RoundedCornerShape(22.dp),
            title = { Text("提示") },
            text = { Text(t) },
            confirmButton = { TextButton(onClick = { toast = null }) { Text("知道了") } }
        )
    }
}

@Composable
internal fun ViewerAction(label: String, icon: ImageVector, onClick: () -> Unit) {
    Column(
        Modifier
            .clip(RoundedCornerShape(10))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, null, tint = Color.White, modifier = Modifier.size(20.dp))
        Spacer(Modifier.height(2.dp))
        Text(label, fontSize = 10.sp, color = Color(0xCCFFFFFF))
    }
}

@Composable
private fun ImagePage(
    path: String,
    onTap: () -> Unit,
    onZoomChange: (Boolean) -> Unit,
    onEdgeNext: () -> Unit,
    onEdgePrev: () -> Unit
) {
    val ctx = LocalContext.current
    val config = LocalConfiguration.current
    val req = maxOf(config.screenWidthDp, 1080)
    var bmp by remember(path) { mutableStateOf(Thumbs.peek("$req|$path")) }
    LaunchedEffect(path) {
        if (bmp == null) bmp = Thumbs.load(ctx, path, false, req = req)
    }
    // 双指缩放：手指离开后保持当前比例；放大后可自由拖动，拖到左右边缘再拖才翻页
    var scale by remember(path) { mutableStateOf(1f) }
    var offset by remember(path) { mutableStateOf(Offset.Zero) }
    var size by remember { mutableStateOf(IntSize.Zero) }
    var paged by remember { mutableStateOf(false) }
    LaunchedEffect(scale) { onZoomChange(scale > 1.01f) }
    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .onSizeChanged { size = it }
            .pointerInput(path) {
                detectTapGestures(onTap = { onTap() })
            }
            .pointerInput(path, size) {
                // 只有双指、或者已经放大时才接管手势；没放大时单指左右滑必须交给翻页
                awaitEachGesture {
                    var multi = false
                    var active = true
                    while (active) {
                        val event = awaitPointerEvent()
                        val pressed = event.changes.count { it.pressed }
                        if (pressed >= 2) multi = true
                        if (multi || scale > 1.01f) {
                            val zoom = event.calculateZoom()
                            val pan = event.calculatePan()
                            val ns = (scale * zoom).coerceIn(1f, 5f)
                            val mx = (size.width * (ns - 1f) / 2f).coerceAtLeast(0f)
                            val my = (size.height * (ns - 1f) / 2f).coerceAtLeast(0f)
                            val nx = (offset.x + pan.x).coerceIn(-mx, mx)
                            val ny = (offset.y + pan.y).coerceIn(-my, my)
                            if (ns <= 1.01f) {
                                scale = 1f
                                offset = Offset.Zero
                            } else {
                                scale = ns
                                // 拼到边缘继续往里拖，才翻上一张/下一张
                                if (mx > 0f && nx >= mx - 1f && pan.x > 24f && !paged) {
                                    paged = true
                                    onEdgePrev()
                                } else if (mx > 0f && nx <= -mx + 1f && pan.x < -24f && !paged) {
                                    paged = true
                                    onEdgeNext()
                                }
                                offset = Offset(nx, ny)
                            }
                            event.changes.forEach { it.consume() }
                        }
                        if (event.changes.all { !it.pressed }) active = false
                    }
                    paged = false
                }
            },
        contentAlignment = Alignment.Center
    ) {
        val b = bmp
        if (b != null) {
            Image(
                bitmap = b,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        translationX = offset.x
                        translationY = offset.y
                    },
                contentScale = ContentScale.Fit
            )
        } else {
            Icon(Icons.Filled.Photo, null, tint = Color(0x33FFFFFF), modifier = Modifier.size(64.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GlobalSearchScreen(ctx: Context, nav: NavStack, repo: com.luogen.filehub.data.FileRepo, initial: String) {
    // 恢复上次的搜索词与结果：点进文件再返回不会白屏重搜
    val startQuery = if (initial.isNotBlank()) initial else AppState.searchQuery
    var query by remember { mutableStateOf(startQuery) }
    var results by remember {
        mutableStateOf(if (AppState.searchResultsFor == startQuery) AppState.searchResults else emptyList())
    }
    var loading by remember { mutableStateOf(false) }
    var searched by remember {
        mutableStateOf(startQuery.isNotBlank() && AppState.searchResultsFor == startQuery)
    }
    var filt by remember { mutableStateOf(AppState.searchFilt) }
    var excluded by remember { mutableStateOf(AppState.searchExcluded) }
    var showExclude by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) {
        delay(120)
        try { focus.requestFocus() } catch (_: Throwable) {}
        keyboard?.show()
    }

    // 边打边搜：MediaStore 的 LIKE 查询，不再全盘索引
    LaunchedEffect(query) {
        val q = query.trim()
        AppState.searchQuery = q
        if (q.isEmpty()) {
            results = emptyList()
            searched = false
            loading = false
            AppState.searchResults = emptyList()
            AppState.searchResultsFor = ""
            return@LaunchedEffect
        }
        if (AppState.searchResultsFor == q && AppState.searchResults.isNotEmpty()) {
            results = AppState.searchResults
            searched = true
            loading = false
            return@LaunchedEffect
        }
        loading = true
        delay(180)
        val r = withContext(Dispatchers.IO) {
            val media = try { repo.searchAll(q, 200) } catch (_: Throwable) { emptyList() }
            // MediaStore 还没索引到（刚下载/刚改名）时，直接扫常用目录兜底，保证第一次就能搜到
            if (media.isEmpty() || media.size < 3) {
                val extra = try { repo.scanFallback(q, 200) } catch (_: Throwable) { emptyList() }
                (media + extra).distinctBy { it.path }
            } else media
        }
        results = r
        searched = true
        loading = false
        AppState.searchResults = r
        AppState.searchResultsFor = q
    }
    LaunchedEffect(filt, excluded) {
        AppState.searchFilt = filt
        AppState.searchExcluded = excluded
    }

    val shown = remember(results, filt, excluded) {
        results.filter {
            val c = TypeDetect.catOf(it.name, it.mime)
            (filt == null || c == filt) && c !in excluded
        }
    }

    if (showExclude) {
        val cats = listOf(Cat.IMAGE to "图片", Cat.VIDEO to "视频", Cat.AUDIO to "音频", Cat.DOC to "文档", Cat.ARCHIVE to "压缩包", Cat.APP to "应用")
        AlertDialog(
            onDismissRequest = { showExclude = false },
            title = { Text("排除这些类型") },
            text = {
                Column {
                    cats.forEach { (c, name) ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = c in excluded,
                                onCheckedChange = { on ->
                                    excluded = if (on) excluded + c else excluded - c
                                }
                            )
                            Text(name, fontSize = 14.sp)
                        }
                    }
                    Text("勾选的类型不会出现在搜索结果里", fontSize = 11.sp, color = TextDim)
                }
            },
            confirmButton = { TextButton(onClick = { showExclude = false }) { Text("完成") } },
            dismissButton = {
                TextButton(onClick = { excluded = emptySet(); showExclude = false }) { Text("清空排除") }
            }
        )
    }

    Scaffold(
        containerColor = BgDeep,
        topBar = {
            Column(Modifier.background(SurfaceDark)) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { nav.pop() }) { Icon(Icons.Filled.ArrowBack, null, tint = TextMain) }
                    Row(
                        Modifier
                            .weight(1f)
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
                                modifier = Modifier.focusRequester(focus),
                                textStyle = MaterialTheme.typography.bodyMedium.copy(color = TextMain, fontSize = 14.sp)
                            )
                            if (query.isEmpty()) Text("搜索全部文件", color = TextDim, fontSize = 13.sp)
                        }
                        if (query.isNotEmpty()) {
                            Icon(Icons.Filled.Close, null, tint = TextDim, modifier = Modifier.size(17.dp).clickable { query = "" })
                        }
                    }
                }
                if (query.isNotBlank()) PathJumpBar(nav, query, Cat.FILE)
                Text(
                    when {
                        loading -> "正在搜索…"
                        searched -> "找到 ${shown.size} 个结果"
                        else -> "输入文件名或任意关键词"
                    },
                    color = TextDim, fontSize = 11.sp, modifier = Modifier.padding(start = 16.dp, bottom = 8.dp)
                )
            }
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                query.isBlank() -> EmptyView("输入文件名或关键词开始搜索")
                shown.isEmpty() && searched -> EmptyView("没有匹配的文件")
                else -> {
                    val searchList = rememberLazyListState(
                        initialFirstVisibleItemIndex = AppState.scrollOf("gsearch"),
                        initialFirstVisibleItemScrollOffset = AppState.scrollOffsetOf("gsearch")
                    )
                    LaunchedEffect(searchList) {
                        snapshotFlow { searchList.firstVisibleItemIndex to searchList.firstVisibleItemScrollOffset }.collect {
                            AppState.keepScroll("gsearch", it.first, it.second)
                        }
                    }
                    LazyColumn(Modifier.fillMaxSize(), state = searchList) {
                    items(count = shown.size, key = { shown[it].key }) { idx ->
                        val item = shown[idx]
                        FileRow(
                            item = item,
                            selected = false,
                            selectionMode = false,
                            onClick = { openItem(ctx, nav, item, shown) },
                            onLongClick = {},
                            trailing = { Text(itemCat(item).title, color = TextDim, fontSize = 11.sp) }
                        )
                    }
                    }
                }
            }
        }
    }
}
