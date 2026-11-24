package com.luogen.filehub.ui

import android.graphics.Bitmap
import android.graphics.Color as AColor
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.luogen.filehub.data.FileOps
import com.luogen.filehub.data.TypeDetect
import com.luogen.filehub.model.FileItem
import com.luogen.filehub.model.toFullTime
import com.luogen.filehub.model.toSizeText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TextViewerScreen(nav: NavStack, path: String) {
    val ctx = LocalContext.current
    val file = remember(path) { File(path) }
    var text by remember(path) { mutableStateOf("") }
    var original by remember(path) { mutableStateOf("") }
    var editing by remember(path) { mutableStateOf(false) }
    var loading by remember(path) { mutableStateOf(true) }
    var editable by remember(path) { mutableStateOf(true) }
    var askExit by remember { mutableStateOf(false) }
    var toast by remember { mutableStateOf<String?>(null) }
    var openInfo by remember { mutableStateOf(false) }
    val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current

    LaunchedEffect(path) {
        withContext(Dispatchers.IO) {
            val ok = try { TypeDetect.looksText(file) || file.length() == 0L } catch (_: Throwable) { false }
            val content = try { file.readText() } catch (_: Throwable) { "" }
            withContext(Dispatchers.Main) {
                editable = ok
                text = content
                original = content
                loading = false
            }
        }
    }

    BackHandler {
        if (editing && text != original) askExit = true else nav.pop()
    }

    Scaffold(
        containerColor = BgDeep,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(file.name, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                        Text(
                            "${file.length().toSizeText()} · " + if (editing) "编辑中（未保存）" else if (editable) "可编辑文本" else "二进制/无法编辑",
                            fontSize = 11.sp, color = TextDim
                        )
                    }
                },
                navigationIcon = { IconButton(onClick = { if (editing && text != original) askExit = true else nav.pop() }) { Icon(Icons.Filled.ArrowBack, null) } },
                actions = {
                    IconButton(onClick = { clipboard.setText(androidx.compose.ui.text.AnnotatedString(text)); toast = "全文已复制" }) {
                        Icon(Icons.Filled.ContentCopy, null)
                    }
                    if (editable) {
                        if (editing) {
                            IconButton(onClick = {
                                try {
                                    file.writeText(text)
                                    original = text
                                    editing = false
                                    toast = "已保存"
                                } catch (t: Throwable) {
                                    toast = "保存失败：${t.message}"
                                }
                            }) { Icon(Icons.Filled.Save, null, tint = GoodGreen) }
                        } else {
                            IconButton(onClick = { editing = true }) { Icon(Icons.Filled.Edit, null) }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = BgDeep)
            )
        }
        bottomBar = {
            Surface(color = SurfaceDark) {
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    DocAction("分享", Icons.Filled.Share) { FileOps.share(ctx, listOf(FileItem(path = path, name = File(path).name, size = File(path).length(), mime = com.luogen.filehub.data.TypeDetect.mimeOf(File(path).name))) }
                    DocAction("详情", Icons.Filled.Info) {
                        openInfo = true
                    }
                    DocAction("删除", Icons.Filled.DeleteOutline) {
                        if (FileOps.deleteToTrash(ctx, listOf(FileItem(path = path, name = File(path).name, size = File(path).length(), mime = com.luogen.filehub.data.TypeDetect.mimeOf(File(path).name))) > 0) nav.pop()
                    }
                }
            }
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = BrandBlue) }
                !editable -> Column(Modifier.fillMaxSize().padding(20.dp)) {
                    Text("这个文件不是纯文本（或编码无法解析），无法在应用内编辑。", color = TextDim, fontSize = 13.sp)
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = { FileOps.openWith(ctx, path) }) { Text("用其他应用打开") }
                }
                editing -> OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    modifier = Modifier.fillMaxSize().padding(8.dp),
                    textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace, fontSize = 13.sp)
                )
                else -> SelectionContainer {
                    Text(
                        text,
                        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(14.dp),
                        fontSize = 14.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
    }

    if (askExit) {
        AlertDialog(
            onDismissRequest = { askExit = false },
            title = { Text("还没保存") },
            text = { Text("你修改了内容但没有保存。要保存吗？选择「不保存」会恢复成编辑前的样子。") },
            confirmButton = {
                TextButton(onClick = {
                    try {
                        file.writeText(text)
                        original = text
                        toast = "已保存并退出"
                    } catch (_: Throwable) {
                        toast = "保存失败"
                    }
                    askExit = false
                    nav.pop()
                }) { Text("保存") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = {
                        text = original
                        editing = false
                        askExit = false
                        nav.pop()
                    }) { Text("不保存") }
                    TextButton(onClick = { askExit = false }) { Text("继续编辑") }
                }
            }
        )
    }

    toast?.let {
        AlertDialog(onDismissRequest = { toast = null }, title = { Text("提示") }, text = { Text(it) },
            confirmButton = { TextButton(onClick = { toast = null }) { Text("知道了") } })
    }
}

@Composable
private fun DocAction(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Column(
        Modifier.clickable(onClick = onClick).padding(horizontal = 18.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, null, tint = BrandBlue, modifier = Modifier.size(20.dp))
        Spacer(Modifier.height(2.dp))
        Text(label, fontSize = 10.sp, color = TextDim)
    }
}

fun PdfViewerScreen(nav: NavStack, path: String) {
    val ctx = LocalContext.current
    val file = remember(path) { File(path) }
    var renderer by remember { mutableStateOf<PdfRenderer?>(null) }
    var pageCount by remember { mutableStateOf(0) }
    var error by remember { mutableStateOf<String?>(null) }
    var toast by remember { mutableStateOf<String?>(null) }
    val lock = remember { Any() }

    LaunchedEffect(path) {
        try {
            val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            val r = PdfRenderer(pfd)
            renderer = r
            pageCount = r.pageCount
        } catch (t: Throwable) {
            error = "PDF 打开失败：${t.message}"
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            try { renderer?.close() } catch (_: Throwable) {}
        }
    }

    Scaffold(
        containerColor = BgDeep,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(file.name, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                        Text(if (error != null) "打开失败" else "$pageCount 页 · ${file.length().toSizeText()}", fontSize = 11.sp, color = TextDim)
                    }
                },
                navigationIcon = { IconButton(onClick = { nav.pop() }) { Icon(Icons.Filled.ArrowBack, null) } },
                actions = {
                    IconButton(onClick = { FileOps.share(ctx, listOf(com.luogen.filehub.model.FileItem(path = path, name = file.name, mime = "application/pdf"))) }) {
                        Icon(Icons.Filled.Share, null)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = BgDeep)
            )
        }
        bottomBar = {
            Surface(color = SurfaceDark) {
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    DocAction("分享", Icons.Filled.Share) {
                        FileOps.share(ctx, listOf(FileItem(path = path, name = file.name, size = file.length(), mime = com.luogen.filehub.data.TypeDetect.mimeOf(file.name))))
                    }
                    DocAction("详情", Icons.Filled.Info) {
                        toast = "${file.name}\n${file.length().toSizeText()} · ${pageCount} 页\n${path}"
                    }
                    DocAction("删除", Icons.Filled.DeleteOutline) {
                        if (FileOps.deleteToTrash(ctx, listOf(FileItem(path = path, name = file.name, size = file.length(), mime = com.luogen.filehub.data.TypeDetect.mimeOf(file.name))) > 0) nav.pop()
                    }
                }
            }
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                error != null -> Column(Modifier.fillMaxSize().padding(20.dp)) {
                    Text(error!!, color = DangerRed, fontSize = 13.sp)
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = { FileOps.openWith(ctx, path, "application/pdf") }) { Text("用其他应用打开") }
                }
                renderer == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = BrandBlue)
                }
                else -> PdfPages(renderer!!, pageCount, lock)
            }
        }
    }
}

@Composable
private fun PdfPages(renderer: PdfRenderer, pageCount: Int, lock: Any) {
    val widthPx = with(LocalDensity.current) { 1000.dp.roundToPx() }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(count = pageCount) { idx ->
            PdfPage(renderer, idx, widthPx, lock)
        }
    }
}

@Composable
private fun PdfPage(renderer: PdfRenderer, index: Int, widthPx: Int, lock: Any) {
    var bmp by remember(index) { mutableStateOf<Bitmap?>(null) }
    var failed by remember(index) { mutableStateOf(false) }
    LaunchedEffect(index, widthPx) {
        val page1: Bitmap? = withContext(Dispatchers.IO) {
            try {
                synchronized(lock) {
                    renderer.openPage(index).use { page ->
                        val scale = widthPx.toFloat() / page.width
                        val h = (page.height * scale).toInt().coerceAtLeast(1)
                        val b = Bitmap.createBitmap(widthPx, h, Bitmap.Config.ARGB_8888)
                        b.eraseColor(AColor.WHITE)
                        page.render(b, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        b
                    }
                }
            } catch (_: Throwable) {
                null
            }
        }
        if (page1 != null) bmp = page1 else failed = true
    }
    Column(Modifier.fillMaxWidth()) {
        Text("第 ${index + 1} 页", color = TextDim, fontSize = 11.sp)
        Spacer(Modifier.height(4.dp))
        Box(
            Modifier.fillMaxWidth().background(SurfaceDark, RoundedCornerShape(6.dp)),
            contentAlignment = Alignment.Center
        ) {
            val b = bmp
            when {
                b != null -> Image(b.asImageBitmap(), "第${index + 1}页", Modifier.fillMaxWidth(), contentScale = androidx.compose.ui.layout.ContentScale.FillWidth)
                failed -> Text("这一页渲染失败", color = DangerRed, fontSize = 12.sp)
                else -> Box(Modifier.height(180.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = BrandBlue) }
            }
        }
    }

    toast?.let {
        AlertDialog(onDismissRequest = { toast = null }, title = { Text("详情") }, text = { Text(it) },
            confirmButton = { TextButton(onClick = { toast = null }) { Text("知道了") } })
    }
}