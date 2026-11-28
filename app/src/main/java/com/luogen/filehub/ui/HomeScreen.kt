package com.luogen.filehub.ui

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.luogen.filehub.data.FileRepo
import com.luogen.filehub.data.Prefs
import com.luogen.filehub.data.TrashStore
import com.luogen.filehub.data.VaultStore
import com.luogen.filehub.model.Cat
import com.luogen.filehub.model.StorageInfo
import com.luogen.filehub.model.VolumeInfo
import com.luogen.filehub.model.toSizeText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private fun mediaSum(c: Map<Cat, Int>): Int =
    (c[Cat.IMAGE] ?: 0) + (c[Cat.VIDEO] ?: 0) + (c[Cat.AUDIO] ?: 0) +
        (c[Cat.DOC] ?: 0) + (c[Cat.ARCHIVE] ?: 0) + (c[Cat.DOWNLOAD] ?: 0)

private val homeCats = listOf(
    Cat.IMAGE, Cat.VIDEO, Cat.AUDIO, Cat.DOC, Cat.APP, Cat.ARCHIVE,
    Cat.FAV, Cat.DOWNLOAD, Cat.FILE, Cat.TRASH, Cat.VAULT, Cat.CLEAN
)

@Composable
fun HomeScreen(ctx: Context, nav: NavStack, repo: FileRepo) {
    // 先用上次缓存，秒出数字；随后后台刷新
    var counts by remember {
        mutableStateOf(AppState.counts ?: Prefs.countsCache().ifEmpty { null })
    }
    var storage by remember { mutableStateOf(AppState.storage ?: StorageInfo()) }
    var extra by remember { mutableStateOf(Triple(0, 0, 0)) }
    var refreshing by remember { mutableStateOf(AppState.counts == null) }
    var volumes by remember { mutableStateOf<List<VolumeInfo>>(AppState.volumes ?: emptyList()) }
    var volTick by remember { mutableStateOf(0) }

    // 插拔 U 盘 / SD 卡时即时刷新
    DisposableEffect(Unit) {
        val receiver = object : android.content.BroadcastReceiver() {
            override fun onReceive(c: android.content.Context?, i: android.content.Intent?) {
                volTick++
            }
        }
        val filter = android.content.IntentFilter().apply {
            addAction(android.content.Intent.ACTION_MEDIA_MOUNTED)
            addAction(android.content.Intent.ACTION_MEDIA_UNMOUNTED)
            addAction(android.content.Intent.ACTION_MEDIA_EJECT)
            addAction(android.content.Intent.ACTION_MEDIA_REMOVED)
            addDataScheme("file")
        }
        try { ctx.registerReceiver(receiver, filter) } catch (_: Throwable) {}
        onDispose { try { ctx.unregisterReceiver(receiver) } catch (_: Throwable) {} }
    }

    // 回收站/收藏/保险箱数量：首页常驻时每 1.2 秒校正一次，任何操作后回来都是最新值
    LaunchedEffect(Unit) {
        while (true) {
            val t = withContext(Dispatchers.IO) { try { TrashStore.list(ctx).size } catch (_: Throwable) { 0 } }
            val v = withContext(Dispatchers.IO) { try { VaultStore.list(ctx).size } catch (_: Throwable) { 0 } }
            val f = withContext(Dispatchers.IO) { try { repo.favorites().size } catch (_: Throwable) { 0 } }
            extra = Triple(t, v, f)
            kotlinx.coroutines.delay(1200)
        }
    }

    LaunchedEffect(volTick) {
        val v = withContext(Dispatchers.IO) { try { repo.volumes() } catch (_: Throwable) { emptyList() } }
        volumes = v
        AppState.storeVolumes(v)
    }

    // 兜底：每 10 秒静默校正一次分类数量，刚下载/刚改名的文件也能自己出现在首页
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(10000)
            if (hasAllFilesAccess(ctx)) {
                val c = withContext(Dispatchers.IO) { try { repo.counts() } catch (_: Throwable) { emptyMap() } }
                if (c.isNotEmpty() && mediaSum(c) > 0) {
                    counts = c
                    AppState.storeCounts(c)
                    Prefs.setCountsCache(c)
                }
            }
        }
    }

    LaunchedEffect(Unit, AppState.countsDirty) {
        if (AppState.countsDirty) {
            // 有删除/收藏等操作：静默刷新统计与存储已用，不显示“扫描中”
            val s2 = withContext(Dispatchers.IO) { try { repo.storage() } catch (_: Throwable) { null } }
            if (s2 != null && s2.total > 0) {
                storage = s2
                AppState.storeStorage(s2)
            }
            val c = withContext(Dispatchers.IO) { try { repo.counts() } catch (_: Throwable) { emptyMap() } }
            val trash = withContext(Dispatchers.IO) { try { TrashStore.list(ctx).size } catch (_: Throwable) { 0 } }
            val vault = withContext(Dispatchers.IO) { try { VaultStore.list(ctx).size } catch (_: Throwable) { 0 } }
            // 没权限时 MediaStore 会全返回 0：这种“假零”不缓存，等授权后重算
            if (c.isNotEmpty() && mediaSum(c) > 0) {
                counts = c
                AppState.storeCounts(c)
                Prefs.setCountsCache(c)
            }
            extra = Triple(trash, vault, withContext(Dispatchers.IO) {
                try { repo.favorites().size } catch (_: Throwable) { 0 }
            })
            AppState.countsDirty = false
            refreshing = false
            return@LaunchedEffect
        }
        if (!AppState.countsFresh()) {
            refreshing = true
            val s = withContext(Dispatchers.IO) { try { repo.storage() } catch (_: Throwable) { StorageInfo() } }
            if (s.total > 0) {
                storage = s
                AppState.storeStorage(s)
            }

            val c = withContext(Dispatchers.IO) { try { repo.counts() } catch (_: Throwable) { emptyMap() } }
            val trash = withContext(Dispatchers.IO) { try { TrashStore.list(ctx).size } catch (_: Throwable) { 0 } }
            val vault = withContext(Dispatchers.IO) { try { VaultStore.list(ctx).size } catch (_: Throwable) { 0 } }
            // 没权限时 MediaStore 会全返回 0：这种“假零”不缓存，等授权后重算
            if (c.isNotEmpty() && mediaSum(c) > 0) {
                counts = c
                AppState.storeCounts(c)
                Prefs.setCountsCache(c)
            }
            extra = Triple(trash, vault, withContext(Dispatchers.IO) {
                try { repo.favorites().size } catch (_: Throwable) { 0 }
            })
            refreshing = false
        } else {
            val trash = withContext(Dispatchers.IO) { try { TrashStore.list(ctx).size } catch (_: Throwable) { 0 } }
            val vault = withContext(Dispatchers.IO) { try { VaultStore.list(ctx).size } catch (_: Throwable) { 0 } }
            extra = Triple(trash, vault, withContext(Dispatchers.IO) {
                try { repo.favorites().size } catch (_: Throwable) { 0 }
            })
        }
    }

    val scroll = rememberScrollState(initial = AppState.scrollOf("home"))
    LaunchedEffect(scroll.value) { AppState.keepScroll("home", scroll.value) }

    Column(
        Modifier
            .fillMaxSize()
            .background(BgDeep)
            .verticalScroll(scroll)
            .padding(horizontal = 16.dp)
    ) {
        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("罗根文件管家", fontSize = 21.sp, fontWeight = FontWeight.Bold, color = TextMain)
                Text("文件、图片、应用，一个地方管完", color = TextDim, fontSize = 11.sp)
            }
            Box(
                Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(50))
                    .background(SurfaceDark)
                    .border(1.dp, BorderSoft, RoundedCornerShape(50))
                    .clickable { nav.push(Screen.GlobalSearch("")) },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.Search, null, tint = TextMain, modifier = Modifier.size(19.dp))
            }
        }

        Spacer(Modifier.height(12.dp))

        // 搜索条
        Row(
            Modifier
                .fillMaxWidth()
                .height(46.dp)
                .clip(RoundedCornerShape(26))
                .background(SurfaceDark)
                .border(1.dp, BorderSoft, RoundedCornerShape(26))
                .clickable { nav.push(Screen.GlobalSearch("")) }
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Filled.Search, null, tint = BrandBlue, modifier = Modifier.size(19.dp))
            Spacer(Modifier.width(9.dp))
            Text("搜索全部文件", color = TextDim, fontSize = 13.sp)
        }

        if (com.luogen.filehub.data.AudioHub.currentPath() != null) {
            Spacer(Modifier.height(10.dp))
            MiniPlayerBar(nav)
        }

        Spacer(Modifier.height(14.dp))

        StorageCard(
            storage = storage,
            totalItems = counts?.values?.sum(),
            refreshing = refreshing,
            onClick = { nav.push(Screen.CatList(Cat.FILE)) }
        )
        volumes.filter { it.removable }.forEach { v ->
            Spacer(Modifier.height(10.dp))
            VolumeCard(
                volume = v,
                onClick = { nav.push(Screen.Browse(v.path)) },
                onEject = {
                    try {
                        ctx.startActivity(android.content.Intent(android.provider.Settings.ACTION_MEMORY_CARD_SETTINGS))
                    } catch (_: Throwable) {
                        try { ctx.startActivity(android.content.Intent(android.provider.Settings.ACTION_SETTINGS)) } catch (_: Throwable) {}
                    }
                }
            )
        }

        Spacer(Modifier.height(10.dp))
        Row(
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(16))
                .background(SurfaceDark)
                .border(1.dp, BorderSoft, RoundedCornerShape(16))
                .clickable { nav.push(Screen.Ftp) }
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier.size(40.dp).clip(RoundedCornerShape(10)).background(BrandBlue.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) { Icon(Icons.Filled.Wifi, null, tint = BrandBlue, modifier = Modifier.size(22.dp)) }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("FTP 无线传输", fontSize = 15.sp, fontWeight = FontWeight.Medium, color = TextMain)
                Text(
                    if (com.luogen.filehub.data.FtpManager.running) "正在运行 · ${com.luogen.filehub.data.FtpManager.address}"
                    else "电脑连同一 WiFi，直接访问手机文件",
                    fontSize = 11.sp, color = if (com.luogen.filehub.data.FtpManager.running) GoodGreen else TextDim,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
            }
            Icon(Icons.Filled.ChevronRight, null, tint = TextDim, modifier = Modifier.size(16.dp))
        }

        Spacer(Modifier.height(12.dp))

        val tiles = homeCats.map { cat ->
            cat to when (cat) {
                Cat.TRASH -> extra.first
                Cat.VAULT -> extra.second
                Cat.FAV -> extra.third
                Cat.CLEAN -> -1
                else -> counts?.get(cat) ?: -1
            }
        }
        tiles.chunked(3).forEach { rowCats ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                rowCats.forEach { (cat, n) ->
                    Tile(
                        cat = cat,
                        count = n,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            when (cat) {
                                Cat.VAULT -> nav.push(Screen.Vault)
                                else -> nav.push(Screen.CatList(cat))
                            }
                        }
                    )
                }
                repeat(3 - rowCats.size) { Spacer(Modifier.weight(1f)) }
            }
            Spacer(Modifier.height(10.dp))
        }

        Spacer(Modifier.height(12.dp))
        Text("v1.8.1 · 罗根", color = TextDim, fontSize = 11.sp)
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun StorageCard(storage: StorageInfo, totalItems: Int?, refreshing: Boolean, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20))
            .background(SurfaceDark)
            .border(1.dp, BorderSoft, RoundedCornerShape(20))
            .clickable(onClick = onClick)
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("存储空间", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = TextMain)
            Spacer(Modifier.width(6.dp))
            Icon(Icons.Filled.ChevronRight, null, tint = TextDim, modifier = Modifier.size(14.dp))
            Spacer(Modifier.weight(1f))
            Column(horizontalAlignment = Alignment.End) {
                val nominal = nominalGb(storage.total)
                Text(
                    if (storage.total > 0) (nominal?.let { "$it GB" } ?: storage.total.toSizeText()) else "读取中…",
                    color = TextMain, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false
                )
                if (nominal != null && storage.total > 0) {
                    Text("实际可用 ${storage.total.toSizeText()}", color = TextDim, fontSize = 9.sp, maxLines = 1, softWrap = false)
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        if (storage.total > 0) storage.used.toSizeText() else "—",
                        fontSize = 24.sp, fontWeight = FontWeight.Bold, color = TextMain,
                        maxLines = 1, softWrap = false
                    )
                    Spacer(Modifier.width(5.dp))
                    Text("已用", color = TextDim, fontSize = 11.sp, modifier = Modifier.padding(bottom = 3.dp))
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    if (storage.total > 0) "剩余 ${storage.free.toSizeText()}" else "",
                    color = GoodGreen, fontSize = 11.sp, fontWeight = FontWeight.Medium,
                    maxLines = 1, softWrap = false
                )
                if (storage.total > 0) Text(
                    "可用 ${(storage.free.toFloat() / storage.total * 100).toInt()}%",
                    color = TextDim, fontSize = 10.sp, maxLines = 1, softWrap = false
                )
            }
        }
        Spacer(Modifier.height(9.dp))
        val ratio = if (storage.total > 0) (storage.used.toFloat() / storage.total).coerceIn(0f, 1f) else 0f
        LinearProgressIndicator(
            progress = { ratio },
            modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(50)),
            color = BrandBlue,
            trackColor = SurfaceDark2,
            drawStopIndicator = {}
        )
        Spacer(Modifier.height(9.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                when {
                    totalItems != null -> "共 $totalItems 项文件"
                    refreshing -> "正在统计文件…"
                    else -> "共 0 项文件"
                },
                color = TextDim, fontSize = 11.sp
            )
            Spacer(Modifier.weight(1f))
            Text("查看我的文件", color = BrandBlue, fontSize = 11.sp)
        }
    }
}

/** 把二进制容量换算成厂商标称容量（8/16/32/64/128/256/512/1024 GB），差 5% 内才返回 */
private fun nominalGb(total: Long): Int? {
    if (total <= 0) return null
    val decimalGb = total / 1_000_000_000.0
    return listOf(8, 16, 32, 64, 128, 256, 512, 1024).firstOrNull {
        kotlin.math.abs(decimalGb - it) / it < 0.05
    }
}

@Composable
private fun VolumeCard(volume: VolumeInfo, onClick: () -> Unit, onEject: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18))
            .background(SurfaceDark)
            .border(1.dp, BorderSoft, RoundedCornerShape(18))
            .clickable(onClick = onClick)
            .padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Usb, null, tint = BrandBlue, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(volume.name, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = TextMain)
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onEject, contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)) {
                Text("安全移除", fontSize = 11.sp, color = BrandBlue)
            }
            Text(if (volume.total > 0) "总 ${volume.total.toSizeText()}" else "", color = TextDim, fontSize = 11.sp)
        }
        Spacer(Modifier.height(6.dp))
        Text(
            if (volume.total > 0) "剩余 ${volume.free.toSizeText()} · 已用 ${volume.used.toSizeText()}" else "读取中…",
            color = TextDim, fontSize = 11.sp
        )
    }
}

@Composable
private fun Tile(cat: Cat, count: Int, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier
            .clip(RoundedCornerShape(18))
            .background(SurfaceDark)
            .border(1.dp, BorderSoft, RoundedCornerShape(18))
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(12))
                .background(catColor(cat).copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(catIcon(cat), null, tint = catColor(cat), modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.height(7.dp))
        Text(
            when (cat) {
                Cat.FILE -> "我的文件"
                Cat.CLEAN -> "清理"
                Cat.VAULT -> "保险箱"
                Cat.TRASH -> "回收站"
                Cat.DOWNLOAD -> "下载"
                Cat.APP -> "应用"
                Cat.FAV -> "收藏"
                Cat.ARCHIVE -> "压缩包"
                Cat.DOC -> "文档"
                Cat.AUDIO -> "音频"
                Cat.VIDEO -> "视频"
                Cat.IMAGE -> "图片"
            },
            fontSize = 13.sp, fontWeight = FontWeight.Medium, color = TextMain,
            maxLines = 1, overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(2.dp))
        Text(
            when {
                cat == Cat.CLEAN -> "扫描并清理"
                cat == Cat.FILE -> "浏览文件夹"
                cat == Cat.VAULT && count <= 0 -> "加密存放"
                cat == Cat.VAULT -> "$count 个"
                count < 0 -> "…"
                else -> "$count 项"
            },
            fontSize = 10.sp, color = TextDim, maxLines = 1
        )
    }
}
