package com.luogen.filehub.ui

import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.luogen.filehub.data.FileOps
import com.luogen.filehub.data.FileRepo
import com.luogen.filehub.data.Prefs
import com.luogen.filehub.data.Sorter
import com.luogen.filehub.data.VaultManager
import com.luogen.filehub.data.TypeDetect
import com.luogen.filehub.data.VaultStore
import com.luogen.filehub.model.Cat
import com.luogen.filehub.model.FileItem
import com.luogen.filehub.model.SortMode
import com.luogen.filehub.model.nameNoExt
import com.luogen.filehub.model.toRelativeTime
import com.luogen.filehub.model.toSizeText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.crypto.SecretKey
import kotlin.math.sqrt

@Composable
fun VaultScreen(ctx: Context, nav: NavStack, repo: FileRepo) {
    var key by remember { mutableStateOf<SecretKey?>(AppState.vaultKey) }
    val k = key
    if (k == null) {
        if (!VaultManager.hasCreds()) {
            VaultSetup(ctx) { newKey ->
                key = newKey
                AppState.vaultKey = newKey
            }
        } else {
            VaultLock(ctx) { unlockedKey ->
                key = unlockedKey
                AppState.vaultKey = unlockedKey
            }
        }
    } else {
        VaultContent(ctx, nav, repo, k) {
            key = null
            AppState.vaultKey = null
        }
    }
}

// ---------------------------------------------------------------- 首次设置

@Composable
private fun VaultSetup(ctx: Context, onDone: (SecretKey) -> Unit) {
    var created by remember { mutableStateOf<List<Triple<String, String, String>>?>(null) }
    var showCreator by remember { mutableStateOf(false) }
    var key by remember { mutableStateOf<SecretKey?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    Column(
        Modifier.fillMaxSize().background(BgDeep).padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(Icons.Filled.Lock, null, tint = BrandBlue, modifier = Modifier.size(56.dp))
        Spacer(Modifier.height(14.dp))
        Text("设置你的保险箱密码", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(
            "支持数字密码、图案密码、问答密码。\n建议至少设置两种作为备用，忘记一个还能用另一个进入。\n加密后的文件存放在应用私有目录，其他文件管理器看不到。",
            color = TextDim, fontSize = 12.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        Spacer(Modifier.height(20.dp))
        Button(onClick = { showCreator = true }, shape = RoundedCornerShape(12.dp)) {
            Text("开始设置")
        }
        if (created != null) {
            Spacer(Modifier.height(14.dp))
            Text("已配置 ${created!!.size} 个解锁方式", color = GoodGreen, fontSize = 12.sp)
            Spacer(Modifier.height(8.dp))
            Button(onClick = { created?.let { list ->
                val vk = key ?: VaultManager.newRandomKey()
                list.forEach { (t, l, p) -> VaultManager.addCred(t, l, p, vk) }
                onDone(vk)
            } }, shape = RoundedCornerShape(12.dp)) { Text("进入保险箱") }
        }
        error?.let {
            Spacer(Modifier.height(10.dp))
            Text(it, color = DangerRed, fontSize = 12.sp)
        }
    }

    if (showCreator) {
        CredentialCreatorDialog(
            title = "设置第一个解锁方式",
            onDismiss = { showCreator = false },
            onCreated = { triples ->
                showCreator = false
                val vk = VaultManager.newRandomKey()
                var okAll = true
                triples.forEach { (t, l, p) ->
                    if (!VaultManager.addCred(t, l, p, vk)) okAll = false
                }
                if (!okAll) {
                    error = "设置失败，请重试"
                } else {
                    created = triples
                    key = vk
                }
            }
        )
    }
}

// ---------------------------------------------------------------- 解锁

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VaultLock(ctx: Context, onUnlock: (SecretKey) -> Unit) {
    val types = remember { VaultManager.types() }
    var type by remember { mutableStateOf(types.firstOrNull() ?: "pin") }
    var pin by remember { mutableStateOf("") }
    var answer by remember { mutableStateOf("") }
    var qIndex by remember { mutableStateOf(0) }
    var message by remember { mutableStateOf<String?>(null) }
    var fails by remember { mutableStateOf(Prefs.failedAttempts()) }
    var lockUntil by remember { mutableStateOf(Prefs.lockUntil()) }
    var now by remember { mutableStateOf(System.currentTimeMillis()) }

    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1000)
        }
    }

    val questions = remember {
        VaultManager.creds().filter { it.type == "qa" }.map { it.label.ifEmpty { "问题" } }
    }

    fun tryUnlock(payload: String) {
        if (now < lockUntil) return
        val k = VaultManager.unlock(type, payload)
        if (k != null) {
            Prefs.setFailedAttempts(0)
            Prefs.setLockUntil(0)
            onUnlock(k)
        } else {
            val f = fails + 1
            fails = f
            Prefs.setFailedAttempts(f)
            if (f % 5 == 0) {
                val until = System.currentTimeMillis() + 60_000
                lockUntil = until
                Prefs.setLockUntil(until)
                message = "连续输错 5 次，已锁定 60 秒"
            } else {
                message = "密码不正确（已错 $f 次）"
            }
            pin = ""
            answer = ""
        }
    }

    Scaffold(
        containerColor = BgDeep,
        topBar = {
            TopAppBar(
                title = { Text("加密保险箱", fontSize = 18.sp, fontWeight = FontWeight.Bold) },
                navigationIcon = { },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = BgDeep)
            )
        }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(24.dp).verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(30.dp))
            Icon(Icons.Filled.Lock, null, tint = BrandBlue, modifier = Modifier.size(52.dp))
            Spacer(Modifier.height(10.dp))
            Text("输入密码解锁", fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(18.dp))

            // 类型切换
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                types.forEach { t ->
                    FilterChip(
                        selected = type == t,
                        onClick = { type = t; message = null; pin = ""; answer = "" },
                        label = { Text(typeLabel(t)) }
                    )
                }
            }
            Spacer(Modifier.height(20.dp))

            when (type) {
                "pattern" -> {
                    PatternLock { seq ->
                        if (seq.size >= 4) tryUnlock("p:" + seq.joinToString("-")) else message = "图案至少 4 个点"
                    }
                    Spacer(Modifier.height(10.dp))
                    Text("按住滑动绘制图案，松手自动验证", color = TextDim, fontSize = 11.sp)
                }
                "qa" -> {
                    if (questions.isEmpty()) {
                        Text("没有可用的问答密码", color = DangerRed, fontSize = 13.sp)
                    } else {
                        Text("问题：${questions[qIndex % questions.size]}", fontSize = 14.sp)
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = answer,
                            onValueChange = { answer = it },
                            singleLine = true,
                            label = { Text("你的答案（不是常规答案就算对）") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Button(onClick = { tryUnlock(answer.trim()) }, enabled = answer.isNotBlank()) { Text("解锁") }
                            if (questions.size > 1) TextButton(onClick = { qIndex++ ; answer = "" }) { Text("换一个问题") }
                        }
                    }
                }
                else -> {
                    OutlinedTextField(
                        value = pin,
                        onValueChange = { pin = it.filter { c -> c.isDigit() }.take(16) },
                        singleLine = true,
                        label = { Text("数字密码") },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(10.dp))
                    Button(onClick = { tryUnlock(pin) }, enabled = pin.length >= 4) { Text("解锁") }
                }
            }

            Spacer(Modifier.height(14.dp))
            if (now < lockUntil) {
                Text(
                    "已锁定，请等待 ${((lockUntil - now) / 1000 + 1)} 秒",
                    color = DangerRed, fontSize = 13.sp
                )
            }
            message?.let { Text(it, color = DangerRed, fontSize = 12.sp) }
            Spacer(Modifier.height(18.dp))
            Text(
                "提示：忘记当前方式时，点上方的其他类型切换（如数字↔图案↔问答）。",
                color = TextDim, fontSize = 11.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}

private fun typeLabel(t: String): String = when (t) {
    "pattern" -> "图案"
    "qa" -> "问答"
    else -> "数字"
}

// ---------------------------------------------------------------- 图案锁

@Composable
fun PatternLock(onComplete: (List<Int>) -> Unit) {
    val density = LocalDensity.current
    val sizePx = with(density) { 280.dp.toPx() }
    var seq by remember { mutableStateOf(listOf<Int>()) }
    var cursor by remember { mutableStateOf<Offset?>(null) }
    var active by remember { mutableStateOf(false) }

    fun center(i: Int): Offset {
        val cell = sizePx / 3f
        return Offset((i % 3 + 0.5f) * cell, (i / 3 + 0.5f) * cell)
    }

    fun add(p: Offset) {
        var best = -1
        var bestD = Float.MAX_VALUE
        for (i in 0..8) {
            val d = (center(i) - p).getDistance()
            if (d < bestD) { bestD = d; best = i }
        }
        if (best >= 0 && bestD < sizePx / 7f && !seq.contains(best)) {
            seq = seq + best
        }
    }

    Canvas(
        Modifier
            .size(280.dp)
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { p ->
                        active = true
                        seq = emptyList()
                        cursor = p
                        add(p)
                    },
                    onDrag = { change, _ ->
                        cursor = change.position
                        add(change.position)
                    },
                    onDragEnd = {
                        active = false
                        cursor = null
                        val result = seq
                        seq = emptyList()
                        if (result.size >= 4) onComplete(result) else onComplete(emptyList())
                    },
                    onDragCancel = {
                        active = false
                        cursor = null
                        seq = emptyList()
                    }
                )
            }
    ) {
        val r = sizePx / 12f
        for (i in 0..8) {
            val c = center(i)
            val on = seq.contains(i)
            drawCircle(if (on) BrandBlue else Color(0xFF2E3A4F), radius = r, center = c)
            drawCircle(
                if (on) Color.White else Color(0xFF4B5563),
                radius = r * 0.42f, center = c
            )
        }
        for (i in 0 until seq.size - 1) {
            drawLine(BrandBlue, center(seq[i]), center(seq[i + 1]), strokeWidth = 6f)
        }
        val cur = cursor
        if (active && cur != null && seq.isNotEmpty()) {
            drawLine(BrandBlue.copy(alpha = 0.6f), center(seq.last()), cur, strokeWidth = 5f)
            drawCircle(BrandBlue.copy(alpha = 0.8f), radius = 12f, center = cur)
        }
        drawCircle(Color.Transparent, radius = sizePx / 2f, center = Offset(sizePx / 2f, sizePx / 2f), style = Stroke(1f))
    }
}

// ---------------------------------------------------------------- 凭证创建（可复用）

@Composable
fun CredentialCreatorDialog(
    title: String,
    onDismiss: () -> Unit,
    onCreated: (List<Triple<String, String, String>>) -> Unit
) {
    var type by remember { mutableStateOf("pin") }
    var pin by remember { mutableStateOf("") }
    var pin2 by remember { mutableStateOf("") }
    var pat1 by remember { mutableStateOf<List<Int>?>(null) }
    var qaList by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    var q by remember { mutableStateOf("") }
    var a by remember { mutableStateOf("") }
    var step by remember { mutableStateOf(0) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("pin" to "数字", "pattern" to "图案", "qa" to "问答").forEach { (t, l) ->
                        FilterChip(selected = type == t, onClick = { type = t; step = 0; error = null }, label = { Text(l) })
                    }
                }
                Spacer(Modifier.height(10.dp))

                when (type) {
                    "pin" -> {
                        if (step == 0) {
                            OutlinedTextField(
                                value = pin, onValueChange = { pin = it.filter { c -> c.isDigit() }.take(16) },
                                singleLine = true, label = { Text("设置数字密码（至少 4 位）") },
                                visualTransformation = PasswordVisualTransformation(),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                                modifier = Modifier.fillMaxWidth()
                            )
                            Text("不要用 1234、生日这类太好猜的密码。", fontSize = 11.sp, color = TextDim)
                            Spacer(Modifier.height(8.dp))
                            Button(onClick = {
                                if (pin.length < 4) error = "至少 4 位数字" else { step = 1; error = null }
                            }) { Text("下一步") }
                        } else {
                            OutlinedTextField(
                                value = pin2, onValueChange = { pin2 = it.filter { c -> c.isDigit() }.take(16) },
                                singleLine = true, label = { Text("再输入一次确认") },
                                visualTransformation = PasswordVisualTransformation(),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(Modifier.height(8.dp))
                            Row {
                                TextButton(onClick = { step = 0; pin2 = "" }) { Text("重输") }
                                Spacer(Modifier.width(8.dp))
                                Button(onClick = {
                                    if (pin2 != pin) { error = "两次输入不一致"; return@Button }
                                    onCreated(listOf(Triple("pin", "数字密码", pin)))
                                }) { Text("完成") }
                            }
                        }
                        if (looksWeak(pin)) {
                            Text("⚠️ 这个密码比较简单，容易被猜到，建议之后再加一个备用方式。", color = WarnAmber, fontSize = 11.sp)
                        }
                    }
                    "pattern" -> {
                        Text(
                            if (pat1 == null) "绘制第一次图案（至少 4 个点）" else "再绘制一次相同图案确认",
                            fontSize = 12.sp, color = TextDim
                        )
                        Spacer(Modifier.height(8.dp))
                        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            PatternLock { seq ->
                                if (seq.isEmpty()) {
                                    error = "至少连接 4 个点"
                                } else if (pat1 == null) {
                                    pat1 = seq
                                    error = null
                                } else if (seq == pat1) {
                                    onCreated(listOf(Triple("pattern", "图案密码", "p:" + seq.joinToString("-"))))
                                } else {
                                    error = "两次图案不一致，请重新绘制"
                                    pat1 = null
                                }
                            }
                        }
                    }
                    else -> {
                        Text("问答密码：答案与问题不需要有常规关系，例如问「1+1=？」答「hello」。", fontSize = 11.sp, color = TextDim)
                        qaList.forEach { (qq, aa) ->
                            Text("· $qq → $aa", fontSize = 12.sp)
                        }
                        Spacer(Modifier.height(6.dp))
                        OutlinedTextField(value = q, onValueChange = { q = it }, singleLine = true, label = { Text("问题（可自定义）") }, modifier = Modifier.fillMaxWidth())
                        Spacer(Modifier.height(6.dp))
                        OutlinedTextField(value = a, onValueChange = { a = it }, singleLine = true, label = { Text("答案（越不常规越安全）") }, modifier = Modifier.fillMaxWidth())
                        Spacer(Modifier.height(8.dp))
                        Row {
                            TextButton(onClick = {
                                if (q.isNotBlank() && a.isNotBlank()) {
                                    qaList = qaList + (q.trim() to a.trim())
                                    q = ""
                                    a = ""
                                }
                            }) { Text("添加这一组") }
                            Spacer(Modifier.width(8.dp))
                            Button(onClick = {
                                if (qaList.isEmpty()) {
                                    if (q.isBlank() || a.isBlank()) { error = "至少添加一组问答"; return@Button }
                                    onCreated(listOf(Triple("qa", q.trim(), a.trim())))
                                } else {
                                    onCreated(qaList.map { Triple("qa", it.first, it.second) })
                                }
                            }) { Text("完成") }
                        }
                        if (qaList.size == 1) Text("建议至少添加 2 组问答，任意一组答对都能进入。", fontSize = 11.sp, color = WarnAmber)
                    }
                }
                error?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, color = DangerRed, fontSize = 12.sp)
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

private fun looksWeak(p: String): Boolean {
    if (p.length < 6) return true
    val common = setOf("1234", "12345", "123456", "0000", "1111", "8888", "6666", "12345678", "abc123")
    return p.lowercase() in common || p.toSet().size <= 2
}

// ---------------------------------------------------------------- 保险箱内容

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VaultContent(ctx: Context, nav: NavStack, repo: FileRepo, key: SecretKey, onLock: () -> Unit) {
    val scope = rememberCoroutineScope()
    var entries by remember { mutableStateOf(VaultStore.list(ctx)) }
    var selected by remember { mutableStateOf<Set<String>>(emptySet()) }
    var prog by remember { mutableStateOf<Prog?>(null) }
    var toast by remember { mutableStateOf<String?>(null) }
    var showAdd by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var showExport by remember { mutableStateOf(false) }
    var showDelete by remember { mutableStateOf(false) }
    var addCredFor by remember { mutableStateOf<String?>(null) }
    var changeType by remember { mutableStateOf<String?>(null) }
    var showCloseVault by remember { mutableStateOf(false) }
    var infoFor by remember { mutableStateOf<VaultStore.Entry?>(null) }

    fun reload() { entries = VaultStore.list(ctx) }

    val selEntries = entries.filter { selected.contains(it.id) }
    val root = android.os.Environment.getExternalStorageDirectory().absolutePath

    fun preview(e: VaultStore.Entry) {
        scope.launch {
            prog = Prog("正在解密预览", null, e.name)
            val dir = File(ctx.cacheDir, "vault_preview").apply { mkdirs() }
            val out = File(dir, e.name)
            val ok = withContext(Dispatchers.IO) { VaultStore.exportTo(ctx, e, out, key) }
            prog = null
            if (ok) {
                openItem(ctx, nav, FileItem(path = out.absolutePath, name = e.name, mime = e.mime), emptyList())
            } else {
                toast = "解密失败（文件可能已损坏）"
            }
        }
    }

    Scaffold(
        containerColor = BgDeep,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(if (selected.isEmpty()) "加密保险箱" else "已选 ${selected.size} 项", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Text("${entries.size} 个加密文件 · 已解锁", fontSize = 11.sp, color = GoodGreen)
                    }
                },
                navigationIcon = { IconButton(onClick = { nav.pop() }) { Icon(Icons.Filled.ArrowBack, null) } },
                actions = {
                    IconButton(onClick = { showSettings = true }) { Icon(Icons.Filled.Tune, null) }
                    IconButton(onClick = onLock) { Icon(Icons.Filled.LockOpen, null, tint = WarnAmber) }
                    IconButton(onClick = { showAdd = true }) { Icon(Icons.Filled.Add, null, tint = BrandBlue) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = BgDeep)
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAdd = true }) { Icon(Icons.Filled.Add, null) }
        },
        bottomBar = {
            if (selected.isNotEmpty()) {
                Surface(color = SurfaceDark) {
                    Row(Modifier.fillMaxWidth().padding(6.dp)) {
                        TextButton(onClick = { showExport = true }) { Text("导出到手机") }
                        TextButton(onClick = { showDelete = true }) { Text("删除", color = DangerRed) }
                        TextButton(onClick = { selected = emptySet() }) { Text("取消选择") }
                    }
                }
            }
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            if (entries.isEmpty()) {
                EmptyView("保险箱是空的，点右下角 + 添加文件")
            } else {
                val vaultList = rememberLazyListState(initialFirstVisibleItemIndex = AppState.scrollOf("vault:list"))
                LaunchedEffect(vaultList) {
                    snapshotFlow { vaultList.firstVisibleItemIndex }.collect { AppState.keepScroll("vault:list", it) }
                }
                LazyColumn(Modifier.fillMaxSize(), state = vaultList) {
                    items(count = entries.size, key = { entries[it].id }) { idx ->
                        val e = entries[idx]
                        var menu by remember { mutableStateOf(false) }
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (selected.isNotEmpty()) {
                                        selected = if (selected.contains(e.id)) selected - e.id else selected + e.id
                                    } else preview(e)
                                }
                                .padding(horizontal = 14.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (selected.isNotEmpty()) {
                                Checkbox(checked = selected.contains(e.id), onCheckedChange = {
                                    selected = if (selected.contains(e.id)) selected - e.id else selected + e.id
                                })
                                Spacer(Modifier.width(6.dp))
                            }
                            Box(
                                Modifier
                                    .size(44.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(catColor(Cat.VAULT).copy(alpha = 0.14f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    when (com.luogen.filehub.data.TypeDetect.catOf(e.name, e.mime)) {
                                        Cat.IMAGE -> Icons.Filled.Image
                                        Cat.VIDEO -> Icons.Filled.Movie
                                        Cat.AUDIO -> Icons.Filled.MusicNote
                                        Cat.DOC -> Icons.Filled.Description
                                        else -> Icons.Filled.InsertDriveFile
                                    },
                                    null, tint = catColor(com.luogen.filehub.data.TypeDetect.catOf(e.name, e.mime)),
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(e.name, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text("${e.size.toSizeText()} · ${e.time.toRelativeTime()} · 已加密", color = TextDim, fontSize = 11.sp)
                            }
                            Box {
                                IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, null, tint = TextDim) }
                                if (menu) {
                                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                    DropdownMenuItem(text = { Text("解密预览") }, onClick = { menu = false; preview(e) })
                                    DropdownMenuItem(text = { Text("详情") }, onClick = { menu = false; infoFor = e })
                                    DropdownMenuItem(text = { Text("选中此文件") }, onClick = {
                                        menu = false
                                        selected = selected + e.id
                                    })
                                }
                                }
                            }
                        }
                    }
                    item { Spacer(Modifier.height(90.dp)) }
                }
            }
        }
    }

    if (showAdd) {
        VaultAddDialog(
            ctx = ctx,
            repo = repo,
            key = key,
            onDismiss = { showAdd = false },
            onProgress = { prog = it },
            onDone = { msg ->
                toast = msg
                reload()
                showAdd = false
            }
        )
    }

    if (showExport) {
        DestPickerDialog(
            title = "解密导出到",
            startDir = root,
            volumes = emptyList(),
            onDismiss = { showExport = false },
            onPick = { dest ->
            showExport = false
            val list = selEntries
            scope.launch {
                prog = Prog("正在解密导出", null, "准备中…")
                var ok = 0
                val total = list.size
                withContext(Dispatchers.IO) {
                    list.forEachIndexed { i, e ->
                        prog = Prog("正在解密导出", (i.toFloat() / total), e.name)
                        val out = FileOps.uniqueTarget(File(dest), e.name)
                        if (VaultStore.exportTo(ctx, e, out, key)) ok++
                    }
                }
                prog = null
                toast = "已解密导出 $ok/${list.size} 个到 $dest"
                selected = emptySet()
            }
        })
    }

    if (showDelete) {
        ConfirmDialog(
            title = "删除加密文件",
            text = "将永久删除 ${selEntries.size} 个加密文件（不可恢复）。",
            confirmText = "永久删除",
            danger = true,
            onDismiss = { showDelete = false },
            onConfirm = {
                selEntries.forEach { VaultStore.delete(ctx, it) }
                toast = "已删除 ${selEntries.size} 个"
                selected = emptySet()
                reload()
            }
        )
    }

    infoFor?.let { e ->
        InfoDialog(
            title = e.name,
            rows = listOf(
                "名称" to e.name,
                "大小" to e.size.toSizeText(),
                "文件类型" to TypeDetect.labelOf(e.name),
                "添加时间" to e.time.toRelativeTime(),
                "原始位置" to e.orig.ifEmpty { "未记录" },
                "加密方式" to "AES-256-GCM（密钥仅存于解锁凭证）",
                "存储位置" to "应用私有目录 .vault（其他应用不可见）"
            ),
            onDismiss = { infoFor = null }
        )
    }

    if (showSettings) {
        AlertDialog(
            onDismissRequest = { showSettings = false },
            title = { Text("解锁方式管理") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    VaultManager.creds().forEach { c ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(typeLabel(c.type), fontSize = 13.sp, modifier = Modifier.width(56.dp))
                            Text(c.label, fontSize = 12.sp, color = TextDim, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("修改", color = BrandBlue, fontSize = 12.sp, modifier = Modifier.clickable { changeType = c.type; showSettings = false })
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    TextButton(onClick = { addCredFor = "any"; showSettings = false }) { Text("添加备用解锁方式") }
                    TextButton(onClick = { showCloseVault = true; showSettings = false }) { Text("关闭保险箱（全部解密还原）", color = DangerRed) }
                }
            },
            confirmButton = { TextButton(onClick = { showSettings = false }) { Text("关闭") } }
        )
    }

    if (addCredFor != null) {
        CredentialCreatorDialog(
            title = "添加备用解锁方式",
            onDismiss = { addCredFor = null },
            onCreated = { triples ->
                var ok = 0
                triples.forEach { (t, l, p) -> if (VaultManager.addCred(t, l, p, key)) ok++ }
                addCredFor = null
                toast = if (ok > 0) "备用解锁方式已添加" else "添加失败"
            }
        )
    }

    if (changeType != null) {
        CredentialCreatorDialog(
            title = "修改密码（${typeLabel(changeType!!)}）",
            onDismiss = { changeType = null },
            onCreated = { triples ->
                val t = changeType
                changeType = null
                if (t != null) {
                    val cred = VaultManager.creds().firstOrNull { it.type == t }
                    val payload = triples.firstOrNull()?.third ?: ""
                    if (cred != null && payload.isNotEmpty() && VaultManager.changePayload(cred.id, payload, key)) {
                        toast = "密码已修改，立即生效"
                    } else toast = "修改失败"
                }
            }
        )
    }

    if (showCloseVault) {
        ConfirmDialog(
            title = "关闭保险箱",
            text = "把所有加密文件解密，还原到它们原来的位置（原位置不存在时保存到 Download/保险箱还原），并清空所有解锁密码。此操作不可撤销。",
            confirmText = "全部解密还原",
            danger = true,
            onDismiss = { showCloseVault = false },
            onConfirm = {
                scope.launch {
                    prog = Prog("正在解密还原", null, "准备中…")
                    val list = VaultStore.list(ctx)
                    var ok = 0
                    withContext(Dispatchers.IO) {
                        val fallback = File(android.os.Environment.getExternalStorageDirectory(), "Download/保险箱还原")
                        fallback.mkdirs()
                        list.forEachIndexed { i, e ->
                            prog = Prog("正在解密还原", i.toFloat() / list.size.coerceAtLeast(1), e.name)
                            val target = if (e.orig.isNotEmpty()) File(e.orig) else File(fallback, e.name)
                            target.parentFile?.mkdirs()
                            val out = if (target.exists()) FileOps.uniqueTarget(target.parentFile!!, target.name) else target
                            if (VaultStore.exportTo(ctx, e, out, key)) ok++
                        }
                    }
                    prog = null
                    if (ok == list.size) {
                        VaultStore.clear(ctx)
                        VaultManager.clearAll()
                        toast = "已全部解密还原并关闭保险箱"
                        onLock()
                    } else {
                        toast = "解密 $ok/${list.size}，剩余文件未删除，请重试"
                    }
                    reload()
                }
            }
        )
    }

    prog?.let { ProgressDialog(it.title, it.progress, it.msg) }
    toast?.let {
        AlertDialog(onDismissRequest = { toast = null }, title = { Text("提示") }, text = { Text(it) },
            confirmButton = { TextButton(onClick = { toast = null }) { Text("知道了") } })
    }
}

// ---------------------------------------------------------------- 添加文件到保险箱

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VaultAddDialog(
    ctx: Context,
    repo: FileRepo,
    key: SecretKey,
    onDismiss: () -> Unit,
    onProgress: (Prog?) -> Unit,
    onDone: (String) -> Unit
) {
    val scope = rememberCoroutineScope()
    var cat by remember { mutableStateOf(Cat.IMAGE) }
    var items by remember { mutableStateOf<List<FileItem>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var selected by remember { mutableStateOf<Set<String>>(emptySet()) }
    var query by remember { mutableStateOf("") }
    var confirmDelete by remember { mutableStateOf(false) }

    LaunchedEffect(cat) {
        loading = true
        selected = emptySet()
        items = withContext(Dispatchers.IO) {
            try { if (cat == Cat.FILE) repo.allMediaFiles() else repo.load(cat) } catch (_: Throwable) { emptyList() }
        }
        loading = false
    }

    val shown = remember(items, query) {
        if (query.isBlank()) items else items.filter { it.name.contains(query, true) }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("添加文件到保险箱") },
        text = {
            Column(Modifier.heightIn(max = 480.dp)) {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(Cat.IMAGE, Cat.VIDEO, Cat.AUDIO, Cat.DOC, Cat.APP, Cat.ARCHIVE, Cat.FILE).forEach { c ->
                        FilterChip(selected = cat == c, onClick = { cat = c }, label = { Text(c.title) })
                    }
                }
                OutlinedTextField(
                    value = query, onValueChange = { query = it }, singleLine = true,
                    placeholder = { Text("搜索文件名") }, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                )
                Text(if (loading) "读取中…" else "共 ${shown.size} 项，已选 ${selected.size}", fontSize = 11.sp, color = TextDim)
                Box(Modifier.weight(1f, fill = false)) {
                    LazyColumn(Modifier.heightIn(max = 320.dp)) {
                        items(count = shown.size) { idx ->
                            val it0 = shown[idx]
                            Row(
                                Modifier.fillMaxWidth().clickable {
                                    selected = if (selected.contains(it0.path)) selected - it0.path else selected + it0.path
                                }.padding(vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(checked = selected.contains(it0.path), onCheckedChange = {
                                    selected = if (selected.contains(it0.path)) selected - it0.path else selected + it0.path
                                })
                                Column(Modifier.weight(1f)) {
                                    Text(it0.name, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(it0.size.toSizeText(), fontSize = 11.sp, color = TextDim)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { confirmDelete = true },
                enabled = selected.isNotEmpty()
            ) { Text("加密并移入(${selected.size})") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )

    if (confirmDelete) {
        ConfirmDialog(
            title = "加密后原文件会被删除",
            text = "加密完成后，原文件会从相册 / 文件夹里移除（只保留保险箱里的加密副本），这样才算真正隐藏。继续吗？",
            confirmText = "加密并删除原件",
            danger = true,
            onDismiss = { confirmDelete = false },
            onConfirm = {
                val list = shown.filter { selected.contains(it.path) }
                scope.launch {
                    onProgress(Prog("正在加密", null, "准备中…"))
                    var ok = 0
                    withContext(Dispatchers.IO) {
                        list.forEachIndexed { i, it0 ->
                            onProgress(Prog("正在加密", i.toFloat() / list.size.coerceAtLeast(1), it0.name))
                            val f = File(it0.path)
                            if (f.exists() && !f.isDirectory) {
                                if (VaultStore.add(ctx, f, key)) {
                                    FileOps.deletePermanent(listOf(it0))
                                    ok++
                                }
                            }
                        }
                    }
                    onProgress(null)
                    onDone("已加密 $ok/${list.size} 个文件")
                }
            }
        )
    }
}
