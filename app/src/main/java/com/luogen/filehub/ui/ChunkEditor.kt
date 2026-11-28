package com.luogen.filehub.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.luogen.filehub.data.FileOps
import com.luogen.filehub.data.TypeDetect
import com.luogen.filehub.model.FileItem
import com.luogen.filehub.model.toSizeText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.File
import java.io.FileInputStream
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.util.Random

/**
 * 大文本编辑器：整篇读进内存，按字符切成小块渲染。
 *
 * 这样做的原因：
 * 1. 只读一次盘、只存一份字符数据，往后翻不用再读盘 —— 不会再出现“每段都载入中”；
 * 2. 每块约 8 千字，只渲染屏幕上看得到的几块，几十 MB 也不会把主线程卡死；
 * 3. 点铅笔立刻进编辑（数据早在内存里），保存时按块顺序直接写盘，不拼大字符串。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChunkEditorScreen(nav: NavStack, path: String) {
    val ctx = LocalContext.current
    val file = remember(path) { File(path) }
    val scope = rememberCoroutineScope()

    val blocks = remember(path) { ArrayList<String>() }
    val origin = remember(path) { HashMap<Int, String>() }
    var loaded by remember(path) { mutableStateOf(false) }
    var loadError by remember(path) { mutableStateOf<String?>(null) }
    var binary by remember(path) { mutableStateOf(false) }
    var editMode by remember(path) { mutableStateOf(false) }
    var dirty by remember(path) { mutableStateOf(false) }
    var saving by remember(path) { mutableStateOf(false) }
    var toast by remember { mutableStateOf<String?>(null) }
    var askExit by remember { mutableStateOf(false) }
    var openInfo by remember { mutableStateOf(false) }
    var truncated by remember(path) { mutableStateOf(false) }
    var reloadKey by remember(path) { mutableStateOf(0) }
    var menuOpen by remember { mutableStateOf(false) }
    var askClear by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    val maxBytes = 32L * 1024 * 1024

    // 小文件不分块：一个输入框装得下，全选删除就是整篇全选
    val blockChars = if (file.length() <= 128L * 1024) Int.MAX_VALUE else 8000

    LaunchedEffect(path, reloadKey) {
        loaded = false
        loadError = null
        blocks.clear()
        withContext(Dispatchers.IO) {
            try {
                binary = !sampleLooksText(file)
                if (file.length() > maxBytes) {
                    truncated = true
                    // 超过上限：只读前 32 MB，避免把内存吃光
                    var remain = maxBytes
                    BufferedReader(InputStreamReader(FileInputStream(file), Charsets.UTF_8), 64 * 1024).use { r ->
                        val sb = StringBuilder(8192)
                        val buf = CharArray(16384)
                        while (remain > 0) {
                            val n = r.read(buf)
                            if (n <= 0) break
                            var start = 0
                            var i = 0
                            while (i < n) {
                                if (buf[i] == '\n') {
                                    sb.append(buf, start, i - start + 1)
                                    start = i + 1
                                    if (sb.length >= blockChars) { blocks.add(sb.toString()); sb.setLength(0) }
                                }
                                i++
                            }
                            if (start < n) sb.append(buf, start, n - start)
                            if (sb.length >= blockChars) { blocks.add(sb.toString()); sb.setLength(0) }
                            remain -= n
                        }
                        if (sb.isNotEmpty()) blocks.add(sb.toString())
                    }
                    if (blocks.isEmpty()) blocks.add("")
                    loaded = true
                    return@withContext
                }
                BufferedReader(InputStreamReader(FileInputStream(file), Charsets.UTF_8), 64 * 1024).use { r ->
                    val sb = StringBuilder(8192)
                    val buf = CharArray(16384)
                    while (true) {
                        val n = r.read(buf)
                        if (n <= 0) break
                        var start = 0
                        var i = 0
                        while (i < n) {
                            if (buf[i] == '\n') {
                                sb.append(buf, start, i - start + 1)
                                start = i + 1
                                if (sb.length >= blockChars) { blocks.add(sb.toString()); sb.setLength(0) }
                            }
                            i++
                        }
                        if (start < n) sb.append(buf, start, n - start)
                        if (sb.length >= blockChars) { blocks.add(sb.toString()); sb.setLength(0) }
                    }
                    if (sb.isNotEmpty()) blocks.add(sb.toString())
                }
                if (blocks.isEmpty()) blocks.add("")
                loaded = true
            } catch (t: Throwable) {
                loadError = t.message ?: t.javaClass.simpleName
            }
        }
    }

    fun discard() {
        origin.clear()
        dirty = false
        editMode = false
        reloadKey++
    }

    fun doSave() {
        if (saving) return
        saving = true
        scope.launch {
            val r = withContext(Dispatchers.IO) {
                var tmp: File? = null
                try {
                    val dir = file.absoluteFile.parentFile ?: throw IllegalStateException("找不到上级目录")
                    tmp = File(dir, "." + file.name + ".lgsave" + Random().nextInt(9999))
                    BufferedWriter(OutputStreamWriter(tmp.outputStream(), Charsets.UTF_8), 256 * 1024).use { w ->
                        for (i in blocks.indices) w.write(blocks[i])
                    }
                    val t = tmp
                    if (!t.renameTo(file)) {
                        t.copyTo(file, overwrite = true)
                        t.delete()
                    }
                    Result.success(Unit)
                } catch (th: Throwable) {
                    try { tmp?.delete() } catch (_: Throwable) {}
                    Result.failure(th)
                }
            }
            saving = false
            if (r.isSuccess) {
                origin.clear()
                dirty = false
                editMode = false
                toast = "已保存（${file.length().toSizeText()}）"
            } else {
                toast = "保存失败：${r.exceptionOrNull()?.message ?: "可能没有写入权限"}"
            }
        }
    }

    fun back() {
        when {
            dirty -> askExit = true
            editMode -> editMode = false
            else -> nav.pop()
        }
    }

    BackHandler { back() }

    Scaffold(
        containerColor = BgDeep,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(file.name, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                        Text(
                            file.length().toSizeText() + " · " + when {
                                editMode -> "编辑中"
                                binary -> "可能乱码 · 可编辑"
                                truncated -> "只读预览（超过 32 MB）"
                                else -> "可编辑"
                            },
                            fontSize = 11.sp, color = TextDim
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { back() }) { Icon(Icons.Filled.ArrowBack, null) }
                },
                actions = {
                    if (saving) {
                        Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(Modifier.size(18.dp), color = BrandBlue, strokeWidth = 2.dp)
                        }
                    } else if (!truncated) {
                        if (editMode) {
                            IconButton(onClick = { doSave() }, enabled = dirty) {
                                Icon(Icons.Filled.Save, null, tint = if (dirty) GoodGreen else TextDim)
                            }
                        }
                        IconButton(onClick = { editMode = !editMode }) {
                            Icon(
                                if (editMode) Icons.Filled.Check else Icons.Filled.Edit, null,
                                tint = if (editMode) GoodGreen else BrandBlue
                            )
                        }
                        Box {
                            IconButton(onClick = { menuOpen = true }) {
                                Icon(Icons.Filled.MoreVert, null, tint = TextDim)
                            }
                            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                                DropdownMenuItem(
                                    text = { Text("清空全文", fontSize = 13.sp) },
                                    onClick = { menuOpen = false; askClear = true }
                                )
                                DropdownMenuItem(
                                    text = { Text("放弃修改并重新载入", fontSize = 13.sp) },
                                    onClick = {
                                        menuOpen = false
                                        origin.clear(); dirty = false; editMode = false; reloadKey++
                                    }
                                )
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = BgDeep)
            )
        },
        bottomBar = {
            Surface(color = SurfaceDark) {
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    val fi = FileItem(
                        path = path, name = file.name, size = file.length(),
                        mime = TypeDetect.mimeOf(file.name)
                    )
                    DocAction("分享", Icons.Filled.Share) { FileOps.share(ctx, listOf(fi)) }
                    DocAction("详情", Icons.Filled.Info) { openInfo = true }
                }
            }
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).imePadding()) {
            when {
                loadError != null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("打不开这个文件：$loadError", color = DangerRed, fontSize = 13.sp)
                }
                !loaded -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = BrandBlue)
                }
                else -> LazyColumn(Modifier.fillMaxSize(), state = listState) {
                    if (truncated) {
                        item {
                            Surface(color = WarnAmber.copy(alpha = 0.12f)) {
                                Text(
                                    "文件超过 32 MB，这里先只显示前 32 MB；要完整编辑建议用电脑打开。",
                                    color = WarnAmber, fontSize = 11.sp,
                                    modifier = Modifier.fillMaxWidth().padding(10.dp)
                                )
                            }
                        }
                    }
                    items(count = blocks.size, key = { it }) { idx ->
                        TextBlock(
                            index = idx,
                            text = blocks[idx],
                            editable = editMode && !truncated,
                            onEdited = { newText ->
                                if (!origin.containsKey(idx)) origin[idx] = blocks[idx]
                                blocks[idx] = newText
                                if (!dirty) dirty = true
                            }
                        )
                    }
                    item { Spacer(Modifier.height(80.dp)) }
                }
            }
        }
    }

    if (askClear) {
        AlertDialog(
            onDismissRequest = { askClear = false },
            title = { Text("清空全文？") },
            text = { Text("会把整篇内容清空成空白，退出编辑前不会写入文件；想恢复可以点「不保存」或菜单里的「放弃修改并重新载入」。") },
            confirmButton = {
                TextButton(onClick = {
                    askClear = false
                    blocks.clear()
                    blocks.add("")
                    editMode = true
                    dirty = true
                    scope.launch { try { listState.scrollToItem(0) } catch (_: Throwable) {} }
                    toast = "已清空，可以重新写内容；点绿勾保存才会写进文件"
                }) { Text("清空") }
            },
            dismissButton = { TextButton(onClick = { askClear = false }) { Text("取消") } }
        )
    }

    if (askExit) {
        AlertDialog(
            onDismissRequest = { askExit = false },
            title = { Text("还没保存") },
            text = { Text("你修改了内容但没有保存。两个选择都会回到预览页。") },
            confirmButton = { TextButton(onClick = { askExit = false; doSave() }) { Text("保存") } },
            dismissButton = {
                Row {
                    TextButton(onClick = { askExit = false; discard() }) { Text("不保存") }
                    TextButton(onClick = { askExit = false }) { Text("继续编辑") }
                }
            }
        )
    }

    if (openInfo) {
        val rows = listOf(
            "文件名" to file.name,
            "大小" to file.length().toSizeText(),
            "类型" to TypeDetect.labelOf(file.name, TypeDetect.mimeOf(file.name), file),
            "修改时间" to try { file.lastModified().toString() } catch (_: Throwable) { "" },
            "路径" to path
        )
        AlertDialog(
            onDismissRequest = { openInfo = false },
            title = { Text("文件详情") },
            text = {
                Column {
                    rows.forEach { (k, v) ->
                        Text(k, fontSize = 11.sp, color = TextDim)
                        Text(v, fontSize = 13.sp, color = TextMain)
                        Spacer(Modifier.height(8.dp))
                    }
                }
            },
            confirmButton = { TextButton(onClick = { openInfo = false }) { Text("知道了") } }
        )
    }

    LaunchedEffect(toast) {
        toast?.let {
            android.widget.Toast.makeText(ctx, it, android.widget.Toast.LENGTH_SHORT).show()
            toast = null
        }
    }
}

/**
 * 一块文本：预览时是能选中的 Text，编辑时是输入框。
 * 内容直接从内存取，不存在异步加载，也就不会闪“载入中”。
 */
@Composable
private fun TextBlock(
    index: Int,
    text: String,
    editable: Boolean,
    onEdited: (String) -> Unit
) {
    if (!editable) {
        SelectionContainer {
            Text(
                text,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 1.dp),
                color = TextMain, fontSize = 13.sp,
                fontFamily = FontFamily.Monospace, lineHeight = 19.sp
            )
        }
    } else {
        var value by remember(index) { mutableStateOf(TextFieldValue(text)) }
        LaunchedEffect(text) {
            if (value.text != text) value = TextFieldValue(text)
        }
        BasicTextField(
            value = value,
            onValueChange = { nv ->
                value = nv
                onEdited(nv.text)
            },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 1.dp),
            textStyle = TextStyle(
                color = TextMain, fontSize = 13.sp,
                fontFamily = FontFamily.Monospace, lineHeight = 19.sp
            ),
            cursorBrush = SolidColor(BrandBlue)
        )
    }
}

private fun sampleLooksText(f: File): Boolean {
    if (f.length() == 0L) return true
    return try {
        FileInputStream(f).use { ins ->
            val buf = ByteArray(8192)
            val n = ins.read(buf)
            if (n <= 0) return true
            var bad = 0
            var i = 0
            while (i < n) {
                val b = buf[i].toInt() and 0xFF
                if (b == 0) bad += 8
                else if (b < 9 || b in 14..31) bad++
                i++
            }
            bad.toDouble() / n < 0.02
        }
    } catch (_: Throwable) { true }
}
