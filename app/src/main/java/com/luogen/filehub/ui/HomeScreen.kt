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
import androidx.compose.material.icons.filled.Search
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
import com.luogen.filehub.model.toSizeText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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
    var extra by remember { mutableStateOf(0 to 0) }
    var refreshing by remember { mutableStateOf(AppState.counts == null) }

    LaunchedEffect(Unit) {
        if (!AppState.countsFresh()) {
            refreshing = true
            val s = withContext(Dispatchers.IO) { try { repo.storage() } catch (_: Throwable) { StorageInfo() } }
            storage = s
            AppState.storeStorage(s)

            val c = withContext(Dispatchers.IO) { try { repo.counts() } catch (_: Throwable) { emptyMap() } }
            val trash = withContext(Dispatchers.IO) { try { TrashStore.list(ctx).size } catch (_: Throwable) { 0 } }
            val vault = withContext(Dispatchers.IO) { try { VaultStore.list(ctx).size } catch (_: Throwable) { 0 } }
            if (c.isNotEmpty()) {
                counts = c
                AppState.storeCounts(c)
                Prefs.setCountsCache(c)
            }
            extra = trash to vault
            refreshing = false
        } else {
            val trash = withContext(Dispatchers.IO) { try { TrashStore.list(ctx).size } catch (_: Throwable) { 0 } }
            val vault = withContext(Dispatchers.IO) { try { VaultStore.list(ctx).size } catch (_: Throwable) { 0 } }
            extra = trash to vault
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

        Spacer(Modifier.height(14.dp))

        StorageCard(storage = storage, totalItems = counts?.values?.sum(), refreshing = refreshing)

        Spacer(Modifier.height(16.dp))

        val tiles = homeCats.map { cat ->
            cat to when (cat) {
                Cat.TRASH -> extra.first
                Cat.VAULT -> extra.second
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
        Text("v1.1.0 · 罗根", color = TextDim, fontSize = 11.sp)
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun StorageCard(storage: StorageInfo, totalItems: Int?, refreshing: Boolean) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20))
            .background(SurfaceDark)
            .border(1.dp, BorderSoft, RoundedCornerShape(20))
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("存储空间", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = TextMain)
            Spacer(Modifier.weight(1f))
            Text(
                if (storage.total > 0) "总 ${storage.total.toSizeText()}" else "读取中…",
                color = TextDim, fontSize = 11.sp
            )
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                if (storage.total > 0) storage.used.toSizeText() else "—",
                fontSize = 25.sp, fontWeight = FontWeight.Bold, color = TextMain
            )
            Spacer(Modifier.width(6.dp))
            Text("已用", color = TextDim, fontSize = 11.sp)
            Spacer(Modifier.weight(1f))
            Text(
                if (storage.total > 0) "剩余 ${storage.free.toSizeText()}" else "",
                color = GoodGreen, fontSize = 12.sp, fontWeight = FontWeight.Medium
            )
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
        Row {
            Text(
                when {
                    totalItems != null -> "共 $totalItems 项文件"
                    refreshing -> "正在统计文件…"
                    else -> "共 0 项文件"
                },
                color = TextDim, fontSize = 11.sp
            )
            Spacer(Modifier.weight(1f))
            Text(
                if (storage.total > 0) "可用 ${(storage.free.toFloat() / storage.total * 100).toInt()}%" else "",
                color = TextDim, fontSize = 11.sp
            )
        }
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
                cat == Cat.VAULT && count <= 0 -> "加密存放"
                cat == Cat.VAULT -> "$count 个"
                count < 0 -> "…"
                else -> "$count 项"
            },
            fontSize = 10.sp, color = TextDim, maxLines = 1
        )
    }
}
