package com.luogen.filehub.ui

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.luogen.filehub.data.FileOps
import com.luogen.filehub.model.toFullTime
import com.luogen.filehub.model.toSizeText
import kotlinx.coroutines.Dispatchers
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
    val pagerState = rememberPagerState(
        initialPage = start.coerceIn(0, paths.size - 1),
        pageCount = { paths.size }
    )
    var chrome by remember { mutableStateOf(true) }
    var infoFor by remember { mutableStateOf<String?>(null) }
    var toast by remember { mutableStateOf<String?>(null) }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
            ImagePage(path = paths[page], onTap = { chrome = !chrome })
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
                IconButton(onClick = { nav.pop() }) {
                    Icon(Icons.Filled.ArrowBack, null, tint = Color.White)
                }
                Column(Modifier.weight(1f)) {
                    Text(File(paths[pagerState.currentPage]).name, color = Color.White, fontSize = 14.sp, maxLines = 1)
                    Text("${pagerState.currentPage + 1} / ${paths.size}", color = Color(0xCCFFFFFF), fontSize = 11.sp)
                }
                IconButton(onClick = { infoFor = paths[pagerState.currentPage] }) {
                    Icon(Icons.Filled.Info, null, tint = Color.White)
                }
                IconButton(onClick = {
                    val p = paths[pagerState.currentPage]
                    val fi = com.luogen.filehub.model.FileItem(
                        path = p,
                        name = File(p).name,
                        mime = com.luogen.filehub.data.TypeDetect.mimeOf(p)
                    )
                    FileOps.share(ctx, listOf(fi))
                }) { Icon(Icons.Filled.Share, null, tint = Color.White) }
            }
        }
    }

    infoFor?.let { path ->
        val f = File(path)
        LaunchedEffect(path) {}
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
                "完整路径" to path
            ),
            onCopy = { clipboard.setText(AnnotatedString(it)); toast = "路径已复制" },
            onDismiss = { infoFor = null }
        )
        Spacer(Modifier.height(0.dp))
    }

    toast?.let { t ->
        AlertDialog(
            onDismissRequest = { toast = null },
            title = { Text("提示") },
            text = { Text(t) },
            confirmButton = { TextButton(onClick = { toast = null }) { Text("知道了") } }
        )
    }
}

@Composable
private fun ImagePage(path: String, onTap: () -> Unit) {
    val ctx = LocalContext.current
    val config = LocalConfiguration.current
    var bmp by remember(path) { mutableStateOf(Thumbs.peek("1200|$path")) }
    LaunchedEffect(path) {
        bmp = Thumbs.load(ctx, path, false, req = maxOf(config.screenWidthDp, 1080))
    }
    Box(
        Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures(onTap = { onTap() }) },
        contentAlignment = Alignment.Center
    ) {
        val b = bmp
        if (b != null) {
            Image(
                bitmap = b,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit
            )
        } else {
            CircularProgressIndicator(color = Color.White)
        }
    }
}

@Composable
fun GlobalSearchScreen(ctx: Context, nav: NavStack, repo: com.luogen.filehub.data.FileRepo, initial: String) {
    var query by remember { mutableStateOf(initial) }
    var all by remember { mutableStateOf<List<com.luogen.filehub.model.FileItem>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        all = withContext(Dispatchers.IO) {
            try {
                repo.allMediaFiles().sortedByDescending { it.modified }
            } catch (_: Throwable) { emptyList() }
        }
        loading = false
    }

    val results = remember(query, all) {
        if (query.isBlank()) emptyList()
        else all.filter { it.name.contains(query, true) }.take(200)
    }

    Scaffold(
        containerColor = BgDeep,
        topBar = {
            Column {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { nav.pop() }) { Icon(Icons.Filled.ArrowBack, null) }
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        placeholder = { Text("搜索全部文件·实时预览") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = BrandBlue, unfocusedBorderColor = SurfaceDark2,
                            focusedContainerColor = SurfaceDark, unfocusedContainerColor = SurfaceDark
                        )
                    )
                }
                Text(
                    if (loading) "正在索引全部文件…" else "找到 ${results.size} 个（最多显示 200）",
                    color = TextDim, fontSize = 11.sp, modifier = Modifier.padding(start = 16.dp, bottom = 6.dp)
                )
            }
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            if (query.isBlank()) {
                EmptyView("输入关键词，边打边出结果")
            } else if (results.isEmpty() && !loading) {
                EmptyView("没有匹配的文件")
            } else {
                androidx.compose.foundation.lazy.LazyColumn(Modifier.fillMaxSize()) {
                    items(count = results.size, key = { results[it].key }) { idx ->
                        val item = results[idx]
                        FileRow(
                            item = item,
                            selected = false,
                            selectionMode = false,
                            onClick = { openItem(ctx, nav, item, results) },
                            onLongClick = {},
                            trailing = {
                                Text(itemCat(item).title, color = TextDim, fontSize = 11.sp)
                            }
                        )
                    }
                }
            }
        }
    }
}
