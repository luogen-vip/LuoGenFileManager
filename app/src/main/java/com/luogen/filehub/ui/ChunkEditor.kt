package com.luogen.filehub.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
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
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
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
import java.io.File
import java.io.FileInputStream
import java.io.RandomAccessFile
import java.util.Random

/**
 * 超大文本的“分块编辑器”。
 *
 * 以前是把几百万字一次性塞进一个输入框，Compose 排版直接把主线程卡死（就是那个“无响应”）。
 * 现在把文件按行切成若干段（每段约 64 KB），一段一个输入框、只渲染屏幕上看得见的那几段，
 * 随便点哪段都能直接改；保存时逐段写回磁盘，没改过的段原样按字节复制，不会破坏原文件。
 * 所以文件再大也能编辑，不再是“只能看不能改”。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChunkEditorScreen(nav: NavStack, path: String) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val file = remember(path) { File(path) }
    val scope = rememberCoroutineScope()

    var bounds by remember(path) { mutableStateOf<LongArray?>(null) }
    var loadError by remember(path) { mutableStateOf<String?>(null) }
    var saving by remember(path) { mutableStateOf(false) }
    var dirty by remember(path) { mutableStateOf(false) }
    var toast by remember { mutableStateOf<String?>(null) }
    var askExit by remember { mutableStateOf(false) }
    var openInfo by remember { mutableStateOf(false) }
    var binary by remember(path) { mutableStateOf(false) }
    // 和普通文本页一致：先看，点铅笔才进入编辑
    var editMode by remember(path) { mutableStateOf(false) }
    // 保存后文件结构变了：让所有分段按新内容重新读一遍，避免残留旧文字
    var generation by remember(path) { mutableStateOf(0) }

    val edits = remember(path) { mutableStateMapOf<Int, String>() }
    // 只记住最近读过的几段文本，滚回去不用再读盘；64 KB × 24 ≈ 1.5 MB，不占内存
    val cache = remember(path) { object : android.util.LruCache<Int, String>(24) {} }

    suspend fun build() = withContext(Dispatchers.IO) {
        try {
            binary = !sampleLooksText(file)
            val chunk = if (file.length() > 64L * 1024 * 1024) file.length() / 8000 else 64L * 1024
            bounds = buildBounds(file, chunk)
            if (bounds == null || bounds!!.size < 2) loadError = "读不出文件内容"
        } catch (t: Throwable) {
            loadError = t.message ?: t.javaClass.simpleName
        }
    }

    LaunchedEffect(path) { build() }

    fun doSave(thenExit: Boolean) {
        val b = bounds ?: return
        if (saving) return
        saving = true
        val snapshot = HashMap(edits)
        scope.launch {
            val r = saveChunked(file, b, snapshot)
            saving = false
            if (r.isSuccess) {
                edits.clear()
                dirty = false
                cache.evictAll()
                toast = "已保存（${file.length().toSizeText()}）"
                if (thenExit) nav.pop() else {
                    editMode = false
                    generation++
                    build()
                }
            } else {
                toast = "保存失败：${r.exceptionOrNull()?.message ?: "可能没有写入权限"}"
            }
        }
    }

    BackHandler {
        when {
            dirty -> askExit = true
            editMode -> editMode = false
            else -> nav.pop()
        }
    }

    Scaffold(
        containerColor = BgDeep,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(file.name, fontSize = 16.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, maxLines = 1)
                        Text(
                            buildString {
                                append(file.length().toSizeText())
                                append(" · ")
                                append(if (binary) "按文本显示（可能乱码）· 可编辑" else "可编辑")
                                val n = bounds?.let { it.size - 1 } ?: 0
                                if (n > 1) append(" · 分 $n 段")
                            },
                            fontSize = 11.sp, color = TextDim
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = {
                        when {
                            dirty -> askExit = true
                            editMode -> editMode = false
                            else -> nav.pop()
                        }
                    }) { Icon(Icons.Filled.ArrowBack, null) }
                },
                actions = {
                    if (saving) {
                        Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(Modifier.size(18.dp), color = BrandBlue, strokeWidth = 2.dp)
                        }
                    } else {
                        if (editMode) {
                            IconButton(onClick = { doSave(false) }, enabled = dirty) {
                                Icon(Icons.Filled.Save, null, tint = if (dirty) GoodGreen else TextDim)
                            }
                        }
                        IconButton(onClick = { editMode = !editMode }) {
                            Icon(
                                if (editMode) Icons.Filled.Check else Icons.Filled.Edit, null,
                                tint = if (editMode) GoodGreen else BrandBlue
                            )
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
            val b = bounds
            when {
                loadError != null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("打不开这个文件：$loadError", color = DangerRed, fontSize = 13.sp)
                }
                b == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = BrandBlue)
                }
                else -> {
                    val count = b.size - 1
                    LazyColumn(Modifier.fillMaxSize()) {
                        if (binary) {
                            item {
                                Surface(color = WarnAmber.copy(alpha = 0.12f)) {
                                    Text(
                                        "这个文件可能不是纯文本，下面是按文本显示的内容，出现乱码是正常的；改完保存会以纯文本覆盖原文件。",
                                        color = WarnAmber, fontSize = 11.sp,
                                        modifier = Modifier.fillMaxWidth().padding(10.dp)
                                    )
                                }
                            }
                        }
                        if (editMode) {
                            item {
                                Surface(color = BrandBlue.copy(alpha = 0.10f)) {
                                    Text(
                                        "编辑中：可以直接点任意一段修改，改完点右上角绿色勾保存（有未保存内容时它也变绿）。",
                                        color = BrandBlue, fontSize = 11.sp,
                                        modifier = Modifier.fillMaxWidth().padding(10.dp)
                                    )
                                }
                            }
                        }
                        items(count = count, key = { it }) { idx ->
                            ChunkBlock(
                                index = idx, count = count,
                                start = b[idx], end = b[idx + 1],
                                file = file, edits = edits, cache = cache,
                                generation = generation, editable = editMode,
                                onChanged = { dirty = true }
                            )
                        }
                        item { Spacer(Modifier.height(60.dp)) }
                    }
                }
            }
        }
    }

    if (askExit) {
        AlertDialog(
            onDismissRequest = { askExit = false },
            title = { Text("还有没保存的修改") },
            text = { Text("你修改了内容但没有保存。选择「不保存」会恢复成原来的样子，两个选择都会回到预览。") },
            confirmButton = {
                TextButton(onClick = { askExit = false; doSave(false) }) { Text("保存") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = {
                        edits.clear()
                        dirty = false
                        editMode = false
                        askExit = false
                        generation++
                        scope.launch { build() }
                    }) { Text("不保存") }
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

@Composable
private fun ChunkBlock(
    index: Int,
    count: Int,
    start: Long,
    end: Long,
    file: File,
    edits: MutableMap<Int, String>,
    cache: android.util.LruCache<Int, String>,
    generation: Int,
    editable: Boolean,
    onChanged: () -> Unit
) {
    var value by remember(index, generation) { mutableStateOf<TextFieldValue?>(null) }

    LaunchedEffect(index, generation) {
        if (value == null) {
            val text = edits[index] ?: cache.get(index) ?: withContext(Dispatchers.IO) {
                readRange(file, start, end).also { cache.put(index, it) }
            }
            value = TextFieldValue(text)
        }
    }

    Column(Modifier.fillMaxWidth()) {
        if (count > 1) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("第 ${index + 1}/$count 段", fontSize = 9.sp, color = TextDim.copy(alpha = 0.7f))
            }
        }
        val v = value
        if (v == null) {
            Box(Modifier.fillMaxWidth().height(48.dp), contentAlignment = Alignment.Center) {
                Text("载入中…", fontSize = 11.sp, color = TextDim)
            }
        } else if (!editable) {
            androidx.compose.foundation.text.selection.SelectionContainer {
                Text(
                    v.text,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 2.dp),
                    color = TextMain, fontSize = 13.sp,
                    fontFamily = FontFamily.Monospace, lineHeight = 19.sp
                )
            }
        } else {
            BasicTextField(
                value = v,
                onValueChange = { nv ->
                    value = nv
                    edits[index] = nv.text
                    onChanged()
                },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 2.dp),
                textStyle = TextStyle(
                    color = TextMain, fontSize = 13.sp,
                    fontFamily = FontFamily.Monospace, lineHeight = 19.sp
                ),
                cursorBrush = androidx.compose.ui.graphics.SolidColor(BrandBlue)
            )
        }
    }
}

/* ---------------- 工具 ---------------- */

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
                if (b == 0) { bad += 8 }
                else if (b < 9 || b in 14..31) bad++
                i++
            }
            bad.toDouble() / n < 0.02
        }
    } catch (_: Throwable) { true }
}

/** 按行边界切段，保证不会把中文/emoji 的字节劈成两半 */
private fun buildBounds(f: File, chunk: Long): LongArray? {
    val list = ArrayList<Long>()
    list.add(0L)
    var since = 0L
    try {
        FileInputStream(f).use { ins ->
            val buf = ByteArray(256 * 1024)
            var pos = 0L
            while (true) {
                val r = ins.read(buf)
                if (r <= 0) break
                var i = 0
                while (i < r) {
                    if (buf[i] == '\n'.code.toByte()) {
                        val abs = pos + i + 1
                        if (abs - since >= chunk) {
                            list.add(abs)
                            since = abs
                        }
                    }
                    i++
                }
                pos += r
            }
        }
    } catch (_: Throwable) { return null }
    val len = f.length()
    if (list[list.size - 1] != len) list.add(len)
    return list.toLongArray()
}

private fun readRange(f: File, start: Long, end: Long): String {
    val len = (end - start).coerceAtLeast(0).toInt()
    if (len == 0) return ""
    return try {
        RandomAccessFile(f, "r").use { raf ->
            raf.seek(start)
            val buf = ByteArray(len)
            var off = 0
            while (off < len) {
                val r = raf.read(buf, off, len - off)
                if (r <= 0) break
                off += r
            }
            String(buf, 0, off, Charsets.UTF_8)
        }
    } catch (_: Throwable) {
        ""
    }
}

private suspend fun saveChunked(
    file: File,
    bounds: LongArray,
    edits: Map<Int, String>
): Result<Unit> = withContext(Dispatchers.IO) {
    var tmp: File? = null
    try {
        val dir = file.absoluteFile.parentFile ?: throw IllegalStateException("找不到上级目录")
        tmp = File(dir, "." + file.name + ".lgsave" + Random().nextInt(9999))
        RandomAccessFile(file, "r").use { src ->
            java.io.BufferedOutputStream(java.io.FileOutputStream(tmp), 512 * 1024).use { out ->
                val buf = ByteArray(256 * 1024)
                for (i in 0 until bounds.size - 1) {
                    val e = edits[i]
                    if (e != null) {
                        out.write(e.toByteArray(Charsets.UTF_8))
                    } else {
                        src.seek(bounds[i])
                        var rem = bounds[i + 1] - bounds[i]
                        while (rem > 0) {
                            val want = minOf(rem, buf.size.toLong()).toInt()
                            val r = src.read(buf, 0, want)
                            if (r <= 0) break
                            out.write(buf, 0, r)
                            rem -= r
                        }
                    }
                }
            }
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
