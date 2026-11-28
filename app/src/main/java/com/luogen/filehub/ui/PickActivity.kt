package com.luogen.filehub.ui

import android.content.ClipData
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.luogen.filehub.data.TypeDetect
import com.luogen.filehub.model.Cat
import com.luogen.filehub.model.FileItem
import com.luogen.filehub.model.toSizeText
import com.luogen.filehub.data.FileRepo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 别的 App 点「选择文件 / 上传」时，选到「罗根文件管家」进入的就是这里：
 * 用我们自己的文件界面挑文件，挑完把 content:// 交给对方。
 * 图片、视频直接显示缩略图，能切分类（图片/视频/音频/文档/压缩包），可网格可列表，可搜当前文件夹。
 */
class PickActivity : androidx.activity.ComponentActivity() {

    private var allowMultiple = false
    private var mimeTypes: List<String> = listOf("*/*")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        allowMultiple = intent?.getBooleanExtra(Intent.EXTRA_ALLOW_MULTIPLE, false) ?: false
        mimeTypes = collectTypes(intent)
        setContent {
            AppTheme {
                PickScreen(
                    mimeTypes = mimeTypes,
                    multiple = allowMultiple,
                    onCancel = { cancel() },
                    onConfirm = { files -> done(files) }
                )
            }
        }
    }

    private fun collectTypes(intent: Intent?): List<String> {
        val out = ArrayList<String>()
        intent?.type?.let { if (it.isNotBlank()) out.add(it) }
        intent?.getStringArrayExtra(Intent.EXTRA_MIME_TYPES)?.forEach { if (it.isNotBlank()) out.add(it) }
        if (out.isEmpty()) out.add("*/*")
        // 调用方给了 "*/*" 又给了具体类型时，以具体类型为准
        if (out.size > 1 && out.contains("*/*")) out.removeAll { it == "*/*" }
        return out
    }

    private fun cancel() {
        setResult(RESULT_CANCELED)
        finish()
    }

    private fun done(files: List<File>) {
        if (files.isEmpty()) return
        try {
            val uris = files.mapNotNull { uriOf(it) }
            if (uris.isEmpty()) {
                cancel()
                return
            }
            val res = Intent()
            if (uris.size == 1) {
                res.data = uris[0]
            } else {
                val clip = ClipData.newUri(contentResolver, "罗根文件管家", uris[0])
                for (i in 1 until uris.size) clip.addItem(ClipData.Item(uris[i]))
                res.clipData = clip
            }
            res.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            setResult(RESULT_OK, res)
        } catch (_: Throwable) {
            setResult(RESULT_CANCELED)
        }
        finish()
    }

    /** 主存储用 FileProvider；SD 卡等路径用我们自己的文档提供方地址，两种都是 content:// */
    private fun uriOf(f: File): Uri? {
        val primary = Environment.getExternalStorageDirectory().absolutePath
        return try {
            if (f.absolutePath.startsWith(primary)) {
                FileProvider.getUriForFile(this, packageName + ".fileprovider", f)
            } else {
                com.luogen.filehub.provider.FileHubProvider.uriFor(this, f)
            }
        } catch (_: Throwable) {
            try {
                com.luogen.filehub.provider.FileHubProvider.uriFor(this, f)
            } catch (_: Throwable) {
                null
            }
        }
    }
}

/* ------------------------------ 界面 ------------------------------ */

private class PickTab(val label: String, val test: (String) -> Boolean)

private val TABS = listOf(
    PickTab("全部") { true },
    PickTab("图片") { it.startsWith("image/") },
    PickTab("视频") { it.startsWith("video/") },
    PickTab("音频") { it.startsWith("audio/") },
    PickTab("文档") { it.startsWith("text/") || it.contains("word") || it.contains("excel") || it.contains("sheet") || it.contains("presentation") || it.contains("pdf") || it == "application/json" || it == "application/xml" },
    PickTab("压缩包") { it.contains("zip") || it.contains("rar") || it.contains("7z") || it.contains("tar") || it.contains("compress") || it.contains("x-gzip") }
)

private fun mimeAllowed(mime: String, patterns: List<String>): Boolean = patterns.any { p ->
    when {
        p == "*/*" -> true
        p.endsWith("/*") -> mime.startsWith(p.removeSuffix("*"))
        else -> mime == p
    }
}

private class Entry(val file: File, val item: FileItem, val cat: Cat)

private fun entryOf(f: File): Entry {
    val mime = if (f.isDirectory) "" else TypeDetect.mimeOf(f.name)
    return Entry(
        f,
        FileItem(
            path = f.absolutePath,
            name = f.name,
            size = f.length(),
            modified = f.lastModified(),
            isDir = f.isDirectory,
            mime = mime
        ),
        TypeDetect.catOf(f.name, mime, f.isDirectory)
    )
}

private fun readDir(dir: File, patterns: List<String>, tab: PickTab): List<Entry> {
    val all = try { dir.listFiles()?.toList() ?: emptyList() } catch (_: Throwable) { emptyList() }
    return all.filter { !it.name.startsWith(".") }
        .filter { f -> f.isDirectory || (mimeAllowed(TypeDetect.mimeOf(f.name), patterns) && tab.test(TypeDetect.mimeOf(f.name))) }
        .map { entryOf(it) }
        .sortedWith(compareBy({ !it.file.isDirectory }, { it.file.name.lowercase() }))
}

/** 分类页：先取 MediaStore 索引里的全部同类文件，再补一遍文件系统扫描，保证不漏 */
private suspend fun mediaOfCategory(ctx: android.content.Context, tab: PickTab, patterns: List<String>): List<Entry> {
    val repo = FileRepo(ctx)
    val prefixes = when (tab.label) {
        "图片" -> listOf("image/")
        "视频" -> listOf("video/")
        "音频" -> listOf("audio/")
        "文档" -> listOf("text/", "application/")
        "压缩包" -> listOf("application/")
        else -> listOf("")
    }
    val seen = HashSet<String>()
    val out = ArrayList<Entry>()
    for (p in prefixes) {
        val list = try { repo.byMimePrefix(p, 4000) } catch (_: Throwable) { emptyList() }
        for (it in list) {
            if (it.isDir) continue
            val mime = it.mime.ifEmpty { TypeDetect.mimeOf(it.name) }
            if (!mimeAllowed(mime, patterns) || !tab.test(mime)) continue
            if (!seen.add(it.path)) continue
            out.add(entryOf(File(it.path)))
        }
    }
    return out.sortedByDescending { it.file.lastModified() }
}

/** 索引里没有的新文件（刚下载/刚解压）兜底扫一遍，只收同类文件 */
private suspend fun scanOfCategory(root: File, tab: PickTab, patterns: List<String>, skip: MutableSet<String>): List<Entry> {
    val out = ArrayList<Entry>()
    val start = System.currentTimeMillis()
    fun walk(dir: File, depth: Int) {
        if (depth > 7 || out.size >= 2000) return
        if (System.currentTimeMillis() - start > 6000) return
        val path = dir.absolutePath
        if (path.contains("/Android/data") || path.contains("/Android/obb")) return
        val list = try { dir.listFiles()?.toList() ?: emptyList() } catch (_: Throwable) { emptyList() }
        for (f in list) {
            if (out.size >= 2000) return
            if (f.name.startsWith(".")) continue
            if (f.isDirectory) {
                walk(f, depth + 1)
            } else {
                val mime = TypeDetect.mimeOf(f.name)
                if (!tab.test(mime) || !mimeAllowed(mime, patterns)) continue
                if (!skip.add(f.absolutePath)) continue
                out.add(entryOf(f))
            }
        }
    }
    walk(root, 0)
    return out.sortedByDescending { it.file.lastModified() }
}

/**
 * 搜索走系统的 MediaStore 索引（和 App 内搜索同一套），不再自己递归扫全盘，
 * 几万个文件也是瞬间出结果；没有结果时再兜底扫常用目录。
 */
private suspend fun searchFiles(ctx: android.content.Context, keyword: String, patterns: List<String>, tab: PickTab): List<Entry> {
    val repo = FileRepo(ctx)
    var list = try { repo.searchAll(keyword, 300) } catch (_: Throwable) { emptyList() }
    if (list.isEmpty()) {
        list = try { repo.scanFallback(keyword, 150) } catch (_: Throwable) { emptyList() }
    }
    val out = ArrayList<Entry>()
    for (it in list) {
        coroutineContext.ensureActive()
        val f = File(it.path)
        if (!f.exists() || f.isDirectory || f.name.startsWith(".")) continue
        val mime = TypeDetect.mimeOf(f.name)
        if (!mimeAllowed(mime, patterns) || !tab.test(mime)) continue
        out.add(entryOf(f))
        if (out.size >= 300) break
    }
    return out
}

@Composable
private fun PickScreen(
    mimeTypes: List<String>,
    multiple: Boolean,
    onCancel: () -> Unit,
    onConfirm: (List<File>) -> Unit
) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    // 每个「目录 + 分类 + 搜索词」各自记住滚动位置：进下一级再返回，停在刚才那一行
    val listStates = remember { HashMap<String, LazyListState>() }
    val gridStates = remember { HashMap<String, LazyGridState>() }
    val root = remember { Environment.getExternalStorageDirectory() }
    var dir by remember { mutableStateOf(root) }
    var tabIndex by remember { mutableStateOf(0) }
    var grid by remember { mutableStateOf(false) }
    var keyword by remember { mutableStateOf("") }
    var entries by remember { mutableStateOf<List<Entry>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var selected by remember { mutableStateOf<List<File>>(emptyList()) }

    val tabs = remember(mimeTypes) {
        val open = mimeTypes.any { it == "*/*" }
        if (open) TABS else TABS.filter { t -> t.label == "全部" || mimeTypes.any { m -> t.test(m) } }
    }
    val tab = tabs.getOrNull(tabIndex) ?: tabs.first()
    val globalCat = tab.label != "全部" && keyword.isBlank()

    // 切到图片/视频分类自动用网格（用户手动切过视图就不再干预）
    var manualView by remember { mutableStateOf(false) }
    LaunchedEffect(tabIndex) {
        if (!manualView) grid = tab.label == "图片" || tab.label == "视频"
    }

    LaunchedEffect(dir, keyword, tabIndex, mimeTypes) {
        loading = true
        if (keyword.isNotBlank()) delay(220)   // 打字防抖，避免每敲一个字都查一次
        val globalCat = keyword.isBlank() && tab.label != "全部"
        if (globalCat) {
            // 分类页：不按当前文件夹，直接列全机这一类的所有文件
            entries = withContext(Dispatchers.IO) {
                try { mediaOfCategory(ctx, tab, mimeTypes) } catch (_: Throwable) { emptyList() }
            }
            loading = false
            val have = entries.map { it.file.absolutePath }.toHashSet()
            val extra = withContext(Dispatchers.IO) {
                try { scanOfCategory(root, tab, mimeTypes, have) } catch (_: Throwable) { emptyList() }
            }
            if (extra.isNotEmpty()) {
                entries = (entries + extra).sortedByDescending { it.file.lastModified() }
            }
        } else {
            entries = withContext(Dispatchers.IO) {
                try {
                    if (keyword.isBlank()) readDir(dir, mimeTypes, tab) else searchFiles(ctx, keyword, mimeTypes, tab)
                } catch (_: Throwable) {
                    emptyList()
                }
            }
            loading = false
        }
    }

    val stateKey = "${dir.absolutePath}|$tabIndex|$keyword"
    val listState = remember(stateKey) {
        if (listStates.size > 80) listStates.clear()
        listStates.getOrPut(stateKey) { LazyListState() }
    }
    val gridState = remember(stateKey) {
        if (gridStates.size > 80) gridStates.clear()
        gridStates.getOrPut(stateKey) { LazyGridState() }
    }

    // 位置单独记一份（不走 Compose 自带恢复，避免列表重建时被拉回顶部）
    val listPos = remember { HashMap<String, IntArray>() }
    val gridPos = remember { HashMap<String, IntArray>() }
    var restoredKey by remember { mutableStateOf("") }

    LaunchedEffect(stateKey, listState) {
        snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
            .collect { (i, o) -> listPos[stateKey] = intArrayOf(i, o) }
    }
    LaunchedEffect(stateKey, gridState) {
        snapshotFlow { gridState.firstVisibleItemIndex to gridState.firstVisibleItemScrollOffset }
            .collect { (i, o) -> gridPos[stateKey] = intArrayOf(i, o) }
    }
    // 列表数据回来后，把上次离开时的位置还原（每次进入某个 key 只还原一次）
    LaunchedEffect(loading, stateKey, entries.size) {
        if (!loading && entries.isNotEmpty() && restoredKey != stateKey) {
            val p = if (grid) gridPos[stateKey] else listPos[stateKey]
            if (p != null) {
                val idx = p[0].coerceIn(0, entries.lastIndex)
                try {
                    if (grid) gridState.scrollToItem(idx, p[1]) else listState.scrollToItem(idx, p[1])
                } catch (_: Throwable) {
                }
            }
            restoredKey = stateKey
        }
    }

    BackHandler {
        when {
            keyword.isNotBlank() -> keyword = ""
            dir.absolutePath != root.absolutePath && dir.parentFile != null -> dir = dir.parentFile!!
            else -> onCancel()
        }
    }

    Surface(Modifier.fillMaxSize(), color = BgDeep) {
        Column(Modifier.fillMaxSize()) {
            // 顶部栏
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = { onCancel() }) { Text("取消", color = TextDim, fontSize = 14.sp) }
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        if (globalCat) "全部${tab.label}" else if (dir.absolutePath == root.absolutePath) "手机存储" else dir.name.ifEmpty { "手机存储" },
                        fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextMain, maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        if (selected.isEmpty()) (if (globalCat) "共 ${entries.size} 个" else if (multiple) "可多选" else "选一个文件") else "已选 ${selected.size} 个",
                        fontSize = 11.sp, color = if (selected.isEmpty()) TextDim else BrandBlue
                    )
                }
                TextButton(onClick = { if (selected.isNotEmpty()) onConfirm(selected) }, enabled = selected.isNotEmpty()) {
                    Text(
                        if (selected.isEmpty()) "选择" else "完成",
                        color = if (selected.isEmpty()) TextDim else BrandBlue,
                        fontWeight = FontWeight.Bold, fontSize = 14.sp
                    )
                }
            }

            // 分类 + 视图切换
            Row(
                Modifier.fillMaxWidth().padding(start = 12.dp, end = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    Modifier.weight(1f).horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    tabs.forEachIndexed { i, t ->
                        val on = i == tabIndex
                        Text(
                            t.label, fontSize = 12.sp,
                            color = if (on) Color.White else TextDim,
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(if (on) BrandBlue else SurfaceDark)
                                .clickable { tabIndex = i }
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }
                IconButton(onClick = { grid = !grid; manualView = true }, modifier = Modifier.size(34.dp)) {
                    Icon(if (grid) Icons.Filled.ViewList else Icons.Filled.GridView, "切换视图", tint = TextDim, modifier = Modifier.size(20.dp))
                }
            }

            // 路径 + 返回上一级（分类页是全机列表，不需要目录导航）
            if (tab.label == "全部" || keyword.isNotBlank()) Row(
                Modifier.fillMaxWidth().padding(start = 8.dp, end = 12.dp, top = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { if (dir.absolutePath != root.absolutePath && dir.parentFile != null) dir = dir.parentFile!! },
                    enabled = dir.absolutePath != root.absolutePath && dir.parentFile != null,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(Icons.Filled.ArrowBack, "上一级", tint = if (dir.absolutePath == root.absolutePath) BorderSoft else TextDim, modifier = Modifier.size(16.dp))
                }
                Text(
                    dir.absolutePath.removePrefix(root.absolutePath).ifEmpty { "/" },
                    fontSize = 11.sp, color = TextDim, maxLines = 1, overflow = TextOverflow.Ellipsis
                )
            }

            // 搜索框
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)
                    .clip(RoundedCornerShape(14.dp)).background(SurfaceDark)
                    .padding(horizontal = 10.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Filled.Search, null, tint = TextDim, modifier = Modifier.size(17.dp))
                Spacer(Modifier.width(6.dp))
                androidx.compose.foundation.text.BasicTextField(
                    value = keyword,
                    onValueChange = { keyword = it },
                    singleLine = true,
                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp, color = TextMain),
                    modifier = Modifier.weight(1f).padding(vertical = 9.dp),
                    decorationBox = { inner ->
                        if (keyword.isEmpty()) Text("搜索手机里的文件…", fontSize = 13.sp, color = TextDim)
                        inner()
                    }
                )
                if (keyword.isNotEmpty()) {
                    Icon(Icons.Filled.Close, "清空", tint = TextDim, modifier = Modifier.size(16.dp).clickable { keyword = "" })
                }
            }

            Box(Modifier.fillMaxSize()) {
                when {
                    loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = BrandBlue, strokeWidth = 2.dp, modifier = Modifier.size(26.dp))
                    }
                    entries.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            when {
                                keyword.isNotBlank() -> "没找到匹配的文件"
                                globalCat -> "手机里没有${tab.label}文件"
                                else -> "这个文件夹里没有可选的文件"
                            },
                            color = TextDim, fontSize = 13.sp
                        )
                    }
                    grid -> LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        state = gridState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(10.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(count = entries.size) { i ->
                            val e = entries[i]
                            GridCell(
                                e = e,
                                selected = selected.any { it.absolutePath == e.file.absolutePath },
                                onClick = {
                                    if (e.file.isDirectory) {
                                        dir = e.file; keyword = ""
                                    } else if (multiple) {
                                        selected = if (selected.any { it.absolutePath == e.file.absolutePath })
                                            selected.filter { it.absolutePath != e.file.absolutePath } else selected + e.file
                                    } else {
                                        selected = listOf(e.file)
                                    }
                                }
                            )
                        }
                        item { Spacer(Modifier.height(80.dp)) }
                    }
                    else -> LazyColumn(Modifier.fillMaxSize(), state = listState) {
                        items(count = entries.size, key = { entries[it].file.absolutePath }) { i ->
                            val e = entries[i]
                            val isSel = selected.any { it.absolutePath == e.file.absolutePath }
                            FileRow(
                                item = e.item,
                                selected = isSel,
                                selectionMode = multiple,
                                onClick = {
                                    if (e.file.isDirectory) {
                                        dir = e.file; keyword = ""
                                    } else if (multiple) {
                                        selected = if (isSel) selected.filter { it.absolutePath != e.file.absolutePath } else selected + e.file
                                    } else {
                                        selected = listOf(e.file)
                                    }
                                },
                                badge = if (globalCat) e.file.parentFile?.name else null,
                                onLongClick = {
                                    if (!multiple) return@FileRow
                                    selected = if (isSel) selected.filter { it.absolutePath != e.file.absolutePath } else selected + e.file
                                },
                                trailing = if (multiple) null else if (isSel) {
                                    { Icon(Icons.Filled.Check, "已选", tint = BrandBlue, modifier = Modifier.size(20.dp)) }
                                } else null
                            )
                        }
                        item { Spacer(Modifier.height(80.dp)) }
                    }
                }
            }
        }

        // 多选时底部确认条
        if (multiple && selected.isNotEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
                Row(
                    Modifier.fillMaxWidth().padding(12.dp)
                        .clip(RoundedCornerShape(16.dp)).background(BrandBlue)
                        .clickable { onConfirm(selected) }
                        .padding(horizontal = 16.dp, vertical = 13.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("已选 ${selected.size} 个", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    Text("清空", color = Color.White.copy(alpha = 0.85f), fontSize = 13.sp, modifier = Modifier.clickable { selected = emptyList() }.padding(horizontal = 10.dp))
                    Text("完成", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun GridCell(e: Entry, selected: Boolean, onClick: () -> Unit) {
    Column(
        Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) BrandBlue.copy(alpha = 0.12f) else SurfaceDark)
            .border(if (selected) 2.dp else 0.dp, if (selected) BrandBlue else Color.Transparent, RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(Modifier.fillMaxWidth().height(96.dp), contentAlignment = Alignment.Center) {
            when {
                e.file.isDirectory -> Icon(Icons.Filled.Folder, null, tint = WarnAmber, modifier = Modifier.size(42.dp))
                e.cat == Cat.IMAGE || e.cat == Cat.VIDEO -> ThumbBox(Modifier.fillMaxSize(), e.item, corner = 8)
                else -> Icon(itemIcon(e.item), null, tint = catColor(e.cat), modifier = Modifier.size(36.dp))
            }
            if (e.cat == Cat.VIDEO && !e.file.isDirectory) {
                Icon(Icons.Filled.PlayArrow, null, tint = Color.White, modifier = Modifier.size(26.dp).align(Alignment.Center))
            }
            if (selected) {
                Box(
                    Modifier.align(Alignment.TopEnd).padding(4.dp).size(20.dp)
                        .clip(RoundedCornerShape(10.dp)).background(BrandBlue),
                    contentAlignment = Alignment.Center
                ) { Icon(Icons.Filled.Check, null, tint = Color.White, modifier = Modifier.size(14.dp)) }
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            e.file.name, fontSize = 11.sp, color = TextMain, maxLines = 1, overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()
        )
        Text(
            if (e.file.isDirectory) "文件夹" else e.file.length().toSizeText(),
            fontSize = 10.sp, color = TextDim, maxLines = 1
        )
    }
}
