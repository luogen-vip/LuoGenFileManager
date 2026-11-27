package com.luogen.filehub.ui

import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.luogen.filehub.data.AudioHub
import com.luogen.filehub.data.FileOps
import com.luogen.filehub.data.Prefs
import com.luogen.filehub.data.TypeDetect
import com.luogen.filehub.model.FileItem
import com.luogen.filehub.model.extOf
import com.luogen.filehub.model.nameNoExt
import com.luogen.filehub.model.toFullTime
import com.luogen.filehub.model.toSizeText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
fun VideoViewerScreen(nav: NavStack, paths: List<String>, start: Int) {
    val ctx = LocalContext.current
    if (paths.isEmpty()) return
    var index by remember { mutableStateOf(start.coerceIn(0, paths.size - 1)) }
    var chrome by remember { mutableStateOf(true) }
    var duration by remember { mutableStateOf(0L) }
    var position by remember { mutableStateOf(0L) }
    var playing by remember { mutableStateOf(false) }
    var toast by remember { mutableStateOf<String?>(null) }
    var infoFor by remember { mutableStateOf<String?>(null) }
    var renameFor by remember { mutableStateOf<String?>(null) }

    val player = remember { ExoPlayer.Builder(ctx).build() }

    LaunchedEffect(Unit) { AudioHub.pauseForVideo() }

    DisposableEffect(Unit) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_READY) {
                    duration = player.duration.coerceAtLeast(0)
                }
            }
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                playing = isPlaying
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }

    fun load(i: Int) {
        if (i !in paths.indices) return
        index = i
        player.setMediaItem(MediaItem.fromUri(Uri.fromFile(File(paths[i]))))
        player.prepare()
        player.playWhenReady = true
    }

    LaunchedEffect(Unit) { load(index) }

    LaunchedEffect(playing) {
        while (playing) {
            position = player.currentPosition
            duration = player.duration.coerceAtLeast(0)
            delay(400)
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(index, paths.size) {
                var total = 0f
                detectHorizontalDragGestures(
                    onDragEnd = {
                        if (abs(total) > 120f) {
                            if (total < 0) load(index + 1) else load(index - 1)
                        }
                        total = 0f
                    },
                    onHorizontalDrag = { _, dragAmount -> total += dragAmount }
                )
            }
    ) {
        AndroidView(
            factory = { c ->
                PlayerView(c).apply {
                    useController = true
                    this.player = player
                    setShowNextButton(true)
                    setShowPreviousButton(true)
                }
            },
            modifier = Modifier.fillMaxSize(),
            update = { it.player = player }
        )

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
                    Text(File(paths[index]).name, color = Color.White, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${index + 1} / ${paths.size}", color = Color(0xCCFFFFFF), fontSize = 11.sp)
                }
                IconButton(onClick = { load(index - 1) }, enabled = index > 0) {
                    Icon(Icons.Filled.SkipPrevious, null, tint = if (index > 0) Color.White else Color(0x55FFFFFF))
                }
                IconButton(onClick = { load(index + 1) }, enabled = index < paths.size - 1) {
                    Icon(Icons.Filled.SkipNext, null, tint = if (index < paths.size - 1) Color.White else Color(0x55FFFFFF))
                }
            }

            Row(
                Modifier
                    .fillMaxWidth()
                    .background(Color(0x99000000))
                    .padding(vertical = 6.dp)
                    .align(Alignment.BottomCenter),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                val p = paths[index]
                val fi = FileItem(path = p, name = File(p).name, size = File(p).length(), mime = TypeDetect.mimeOf(p))
                ViewerAction("分享", Icons.Filled.Share) { FileOps.share(ctx, listOf(fi)) }
                ViewerAction(
                    if (Prefs.isFav(p)) "已收藏" else "收藏",
                    if (Prefs.isFav(p)) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder
                ) {
                    toast = if (Prefs.toggleFav(p)) "已收藏" else "已取消收藏"
                    AppState.markDataChanged()
                    AppState.countsDirty = true
                }
                ViewerAction("重命名", Icons.Filled.DriveFileRenameOutline) { renameFor = p }
                ViewerAction("删除", Icons.Filled.DeleteOutline) {
                    if (FileOps.deleteToTrash(ctx, listOf(fi)) > 0) {
                        toast = "已移入回收站"
                        AppState.markDataChanged()
                        AppState.countsDirty = true
                        nav.pop()
                    }
                }
                ViewerAction("详情", Icons.Filled.Info) { infoFor = p }
            }
        }
    }

    infoFor?.let { p ->
        val f = File(p)
        InfoDialog(
            title = f.name,
            rows = listOf(
                "大小" to f.length().toSizeText(),
                "时长" to fmtTime(player.duration.coerceAtLeast(0)),
                "文件类型" to TypeDetect.labelOf(f.name),
                "修改时间" to f.lastModified().toFullTime(),
                "完整路径" to p
            ),
            onCopy = { },
            onDismiss = { infoFor = null }
        )
    }

    renameFor?.let { p ->
        RenameDialog(
            initial = File(p).name,
            onDismiss = { renameFor = null },
            onConfirm = { newName ->
                renameFor = null
                toast = FileOps.rename(FileItem(path = p, name = File(p).name, size = File(p).length(), mime = TypeDetect.mimeOf(p)), newName) ?: "已重命名"
            }
        )
    }

    toast?.let {
        AlertDialog(
            onDismissRequest = { toast = null },
            shape = RoundedCornerShape(22.dp),
            title = { Text("提示") },
            text = { Text(it) },
            confirmButton = { TextButton(onClick = { toast = null }) { Text("知道了") } }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioViewerScreen(nav: NavStack, paths: List<String>, start: Int) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    if (paths.isEmpty()) return

    var cover by remember { mutableStateOf<ImageBitmap?>(null) }
    var swirl by remember { mutableStateOf(Prefs.swirl(paths[start.coerceIn(0, paths.size - 1)])) }
    var toast by remember { mutableStateOf<String?>(null) }
    var seekDrag by remember { mutableStateOf<Float?>(null) }
    var pendingSeek by remember { mutableStateOf<Long?>(null) }
    var queueOpen by remember { mutableStateOf(false) }

    val hub = AudioHub
    // 播放队列才是真实音源：从任何地方“加入播放列表”后，这里马上能看到新歌
    val queuePaths = hub.playlist.ifEmpty { paths }
    val index = hub.index.coerceIn(0, queuePaths.lastIndex.coerceAtLeast(0))
    val currentPath = queuePaths.getOrNull(index) ?: paths[start.coerceIn(0, queuePaths.size - 1)]
    // 首次进入：如果已经在播同一首就接着看，否则从当前这首开始播
    LaunchedEffect(Unit) {
        val cur = hub.currentPath()
        if (cur == null || cur != paths[start.coerceIn(0, paths.size - 1)]) {
            hub.play(paths, start.coerceIn(0, paths.size - 1), ctx)
        }
    }
    LaunchedEffect(Unit) {
        while (true) {
            hub.tick()
            pendingSeek?.let { target ->
                if (kotlin.math.abs(hub.position - target) < 1200) pendingSeek = null
            }
            delay(500)
        }
    }

    val playing = hub.playing
    val position = hub.position
    val duration = hub.duration

    val coverPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            try {
                val dir = File(ctx.filesDir, "covers")
                dir.mkdirs()
                val target = File(dir, "cover_${currentPath.hashCode()}.img")
                ctx.contentResolver.openInputStream(uri)?.use { ins ->
                    target.outputStream().use { fos -> ins.copyTo(fos) }
                }
                Prefs.setCover(currentPath, target.absolutePath)
                scope.launch { cover = Thumbs.load(ctx, target.absolutePath, false, 400) }
                hub.notifyCoverChanged()
                toast = "封面已更新（首页和通知栏同步生效）"
            } catch (_: Throwable) {
                toast = "设置封面失败"
            }
        }
    }

    LaunchedEffect(index) {
        swirl = Prefs.swirl(currentPath)
        cover = withContext(Dispatchers.IO) {
            val custom = Prefs.coverFor(currentPath)
            if (custom != null && File(custom).exists()) {
                Thumbs.load(ctx, custom, false, 400)
            } else {
                loadEmbeddedCover(currentPath)
            }
        }
    }

    // 自己累计角度：暂停停在原处，继续播放接着转，拖动进度条不会回零
    var rotation by remember { mutableStateOf(0f) }
    LaunchedEffect(swirl, playing) {
        while (swirl && playing) {
            kotlinx.coroutines.delay(50)
            rotation = (rotation + 1.5f) % 360f
        }
    }
    val angle = rotation

    Column(
        Modifier
            .fillMaxSize()
            .background(BgDeep)
            .padding(horizontal = 20.dp)
    ) {
        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { nav.pop() }) { Icon(Icons.Filled.ArrowBack, null) }
            Spacer(Modifier.weight(1f))
            IconButton(onClick = { coverPicker.launch("image/*") }) {
                Icon(Icons.Filled.AddPhotoAlternate, null, tint = TextDim)
            }
            IconButton(onClick = {
                swirl = !swirl
                Prefs.setSwirl(currentPath, swirl)
            }) { Icon(Icons.Filled.RotateRight, null, tint = if (swirl) BrandBlue else TextDim) }
        }

        Spacer(Modifier.height(20.dp))
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Box(
                Modifier
                    .size(250.dp)
                    .clip(CircleShape)
                    .background(SurfaceDark2)
                    .rotate(angle),
                contentAlignment = Alignment.Center
            ) {
                val c = cover
                if (c != null) {
                    Image(c, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                } else {
                    Icon(Icons.Filled.MusicNote, null, tint = BrandBlue, modifier = Modifier.size(64.dp))
                }
            }
        }

        Spacer(Modifier.height(26.dp))
        Text(
            nameNoExt(File(currentPath).name),
            fontSize = 19.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "${extOf(File(currentPath).name).uppercase()} · ${File(currentPath).length().toSizeText()} · ${index + 1}/${queuePaths.size}",
            color = TextDim, fontSize = 12.sp
        )

        Spacer(Modifier.height(18.dp))
        val shownPos = seekDrag?.let { (it * duration).toLong() } ?: pendingSeek ?: position
        val sliderValue = if (duration > 0) shownPos.toFloat() / duration else 0f
        Slider(
            value = sliderValue.coerceIn(0f, 1f),
            onValueChange = { seekDrag = it },
            onValueChangeFinished = {
                seekDrag?.let {
                    val target = (it * duration).toLong()
                    hub.seekTo(target)
                    pendingSeek = target   // 等播放器真的走到这里再交还进度，避免进度条来回跳
                }
                seekDrag = null
            },
            colors = SliderDefaults.colors(thumbColor = BrandBlue, activeTrackColor = BrandBlue)
        )
        Row(Modifier.fillMaxWidth()) {
            Text(fmtTime(seekDrag?.let { (it * duration).toLong() } ?: pendingSeek ?: position), color = TextDim, fontSize = 11.sp)
            Spacer(Modifier.weight(1f))
            Text(fmtTime(duration), color = TextDim, fontSize = 11.sp)
        }

        Spacer(Modifier.height(10.dp))
        // 五格等宽：循环、上一曲、播放、下一曲、列表，全部严格居中，左右对称
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.width(52.dp), contentAlignment = Alignment.Center) {
                IconButton(onClick = { hub.cycleRepeat() }) {
                    Icon(
                        when (hub.repeatMode) {
                            1 -> Icons.Filled.RepeatOne
                            2 -> Icons.Filled.Shuffle
                            else -> Icons.Filled.Repeat
                        },
                        "播放模式",
                        tint = if (hub.repeatMode != 0) BrandBlue else TextDim
                    )
                }
            }
            Box(Modifier.width(52.dp), contentAlignment = Alignment.Center) {
                IconButton(onClick = { hub.prev() }) {
                    Icon(Icons.Filled.SkipPrevious, "上一曲", tint = TextDim, modifier = Modifier.size(30.dp))
                }
            }
            Surface(
                onClick = { hub.toggle() },
                shape = RoundedCornerShape(18.dp),
                color = BrandBlue,
                modifier = Modifier.size(62.dp)
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow, null,
                        tint = Color.White, modifier = Modifier.size(30.dp))
                }
            }
            Box(Modifier.width(52.dp), contentAlignment = Alignment.Center) {
                IconButton(onClick = { hub.next() }) {
                    Icon(Icons.Filled.SkipNext, "下一曲", tint = TextDim, modifier = Modifier.size(30.dp))
                }
            }
            Box(Modifier.width(52.dp), contentAlignment = Alignment.Center) {
                IconButton(onClick = { queueOpen = true }) {
                    Icon(Icons.Filled.PlaylistPlay, "播放列表", tint = BrandBlue, modifier = Modifier.size(26.dp))
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(
            when (hub.repeatMode) { 1 -> "单曲循环"; 2 -> "随机播放"; else -> "顺序播放" },
            color = TextDim, fontSize = 11.sp, modifier = Modifier.fillMaxWidth(),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        Spacer(Modifier.weight(1f))
        Text(
            "返回、小窗、后台都会继续播放 · 通知栏可控制",
            color = Color(0x33FFFFFF), fontSize = 10.sp,
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }

    if (queueOpen) {
        PlayQueueDialog(
            queue = queuePaths,
            current = hub.index,
            playing = hub.playing,
            onPlay = { i ->
                queueOpen = false
                hub.playQueueAt(i, ctx)
            },
            onRemove = { i ->
                hub.removeAt(i)
                if (hub.playlist.isEmpty()) {
                    queueOpen = false
                    nav.pop()
                }
            },
            onDismiss = { queueOpen = false }
        )
    }

    toast?.let {
        AlertDialog(
            onDismissRequest = { toast = null },
            title = { Text("提示") },
            text = { Text(it) },
            confirmButton = { TextButton(onClick = { toast = null }) { Text("知道了") } }
        )
    }
}

@Composable
private fun PlayQueueDialog(
    queue: List<String>,
    current: Int,
    playing: Boolean,
    onPlay: (Int) -> Unit,
    onRemove: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    var picked by remember { mutableStateOf(-1) }
    LaunchedEffect(picked) {
        if (picked >= 0) {
            delay(140)
            onPlay(picked)
            picked = -1
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("播放列表 · ${queue.size} 首", fontSize = 16.sp) },
        text = {
            if (queue.isEmpty()) {
                Text("播放列表是空的", color = TextDim, fontSize = 13.sp)
            } else {
                androidx.compose.foundation.lazy.LazyColumn(
                    Modifier.fillMaxWidth().heightIn(max = 420.dp)
                ) {
                    items(count = queue.size, key = { i -> "$i|${queue[i]}" }) { i ->
                        val name = File(queue[i]).name
                        Row(
                            Modifier.fillMaxWidth().clickable { if (picked < 0) picked = i }.padding(vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                if (i == current || i == picked) Icons.Filled.VolumeUp else Icons.Filled.MusicNote,
                                null,
                                tint = if (i == current || i == picked) BrandBlue else TextDim,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Column(Modifier.weight(1f)) {
                                Text(name, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                    color = if (i == current || i == picked) BrandBlue else Color.Unspecified)
                                Text(if (i == picked) "正在切换…"
                                    else if (i == current) (if (playing) "正在播放" else "已暂停")
                                    else "第 ${i + 1} 首",
                                    fontSize = 10.sp, color = TextDim)
                            }
                            IconButton(onClick = { onRemove(i) }, modifier = Modifier.size(32.dp)) {
                                Icon(Icons.Filled.Close, "从列表移除", tint = TextDim, modifier = Modifier.size(16.dp))
                            }
                        }
                        HorizontalDivider(color = Color(0x14000000), thickness = 0.5.dp)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("关闭") } }
    )
}

private suspend fun loadEmbeddedCover(path: String): ImageBitmap? = withContext(Dispatchers.IO) {
    try {
        val mmr = MediaMetadataRetriever()
        mmr.setDataSource(path)
        val art = mmr.embeddedPicture
        mmr.release()
        if (art != null && art.isNotEmpty()) {
            BitmapFactory.decodeByteArray(art, 0, art.size)?.asImageBitmap()
        } else null
    } catch (_: Throwable) {
        null
    }
}

fun fmtTime(ms: Long): String {
    if (ms <= 0) return "00:00"
    val total = ms / 1000
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) String.format("%d:%02d:%02d", h, m, s) else String.format("%02d:%02d", m, s)
}
