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
import com.luogen.filehub.data.Prefs
import com.luogen.filehub.model.nameNoExt
import com.luogen.filehub.model.toSizeText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoViewerScreen(nav: NavStack, paths: List<String>, start: Int) {
    val ctx = LocalContext.current
    if (paths.isEmpty()) return
    var index by remember { mutableStateOf(start.coerceIn(0, paths.size - 1)) }
    var chrome by remember { mutableStateOf(true) }
    var duration by remember { mutableStateOf(0L) }
    var position by remember { mutableStateOf(0L) }
    var playing by remember { mutableStateOf(false) }

    val player = remember { ExoPlayer.Builder(ctx).build() }

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
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioViewerScreen(nav: NavStack, paths: List<String>, start: Int) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    if (paths.isEmpty()) return
    var index by remember { mutableStateOf(start.coerceIn(0, paths.size - 1)) }
    var playing by remember { mutableStateOf(false) }
    var position by remember { mutableStateOf(0L) }
    var duration by remember { mutableStateOf(0L) }
    var shuffle by remember { mutableStateOf(false) }
    var repeatMode by remember { mutableStateOf(0) }
    var cover by remember { mutableStateOf<ImageBitmap?>(null) }
    var swirl by remember { mutableStateOf(Prefs.swirl(paths[start.coerceIn(0, paths.size - 1)]) ) }
    var toast by remember { mutableStateOf<String?>(null) }
    var seekDrag by remember { mutableStateOf<Float?>(null) }
    var endedTick by remember { mutableStateOf(0) }

    val player = remember { ExoPlayer.Builder(ctx).build() }

    fun load(i: Int) {
        if (i !in paths.indices) return
        index = i
        player.setMediaItem(MediaItem.fromUri(Uri.fromFile(File(paths[i]))))
        player.prepare()
        player.playWhenReady = true
        player.shuffleModeEnabled = shuffle
    }

    val coverPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            try {
                val dir = File(ctx.filesDir, "covers")
                dir.mkdirs()
                val target = File(dir, "cover_${paths[index].hashCode()}.img")
                ctx.contentResolver.openInputStream(uri)?.use { ins ->
                    target.outputStream().use { fos -> ins.copyTo(fos) }
                }
                Prefs.setCover(paths[index], target.absolutePath)
                scope.launch { cover = Thumbs.load(ctx, target.absolutePath, false, 400) }
                toast = "封面已更新"
            } catch (_: Throwable) {
                toast = "设置封面失败"
            }
        }
    }

    DisposableEffect(Unit) {
        val l = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) { playing = isPlaying }
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_READY) duration = player.duration.coerceAtLeast(0)
                if (state == Player.STATE_ENDED) endedTick++
            }
        }
        player.addListener(l)
        onDispose { player.removeListener(l); player.release() }
    }

    LaunchedEffect(endedTick) {
        if (endedTick > 0) {
            if (repeatMode == 1) {
                player.seekTo(0)
                player.play()
            } else if (index < paths.size - 1) {
                load(index + 1)
            } else if (repeatMode == 2) {
                load(0)
            }
        }
    }

    LaunchedEffect(Unit) { load(index) }

    LaunchedEffect(playing) {
        while (playing) {
            position = player.currentPosition
            duration = player.duration.coerceAtLeast(0)
            delay(300)
        }
    }

    LaunchedEffect(index) {
        swirl = Prefs.swirl(paths[index])
        cover = withContext(Dispatchers.IO) {
            val custom = Prefs.coverFor(paths[index])
            if (custom != null && File(custom).exists()) {
                Thumbs.load(ctx, custom, false, 400)
            } else {
                loadEmbeddedCover(paths[index])
            }
        }
    }

    val transition = rememberInfiniteTransition(label = "swirl")
    val angleAnim by transition.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 12000, easing = LinearEasing)
        ),
        label = "angle"
    )
    val angle = if (swirl && playing) angleAnim else 0f

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
                Prefs.setSwirl(paths[index], swirl)
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
            nameNoExt(File(paths[index]).name),
            fontSize = 19.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "${File(paths[index]).name.substringAfterLast('.', "").uppercase()} · ${File(paths[index]).length().toSizeText()} · ${index + 1}/${paths.size}",
            color = TextDim, fontSize = 12.sp
        )

        Spacer(Modifier.height(18.dp))
        val sliderValue = seekDrag ?: if (duration > 0) position.toFloat() / duration else 0f
        Slider(
            value = sliderValue.coerceIn(0f, 1f),
            onValueChange = { seekDrag = it },
            onValueChangeFinished = {
                seekDrag?.let { player.seekTo((it * duration).toLong()) }
                seekDrag = null
            },
            colors = SliderDefaults.colors(thumbColor = BrandBlue, activeTrackColor = BrandBlue)
        )
        Row(Modifier.fillMaxWidth()) {
            Text(fmtTime(seekDrag?.let { (it * duration).toLong() } ?: position), color = TextDim, fontSize = 11.sp)
            Spacer(Modifier.weight(1f))
            Text(fmtTime(duration), color = TextDim, fontSize = 11.sp)
        }

        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = {
                shuffle = !shuffle
                player.shuffleModeEnabled = shuffle
            }) { Icon(Icons.Filled.Shuffle, null, tint = if (shuffle) BrandBlue else TextDim) }
            IconButton(onClick = { load(index - 1) }, enabled = index > 0) {
                Icon(Icons.Filled.SkipPrevious, null, tint = if (index > 0) TextDim else Color(0x33FFFFFF))
            }
            FloatingActionButton(onClick = {
                if (player.isPlaying) player.pause() else player.play()
            }, containerColor = BrandBlue) {
                Icon(if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow, null, tint = Color.White, modifier = Modifier.size(30.dp))
            }
            IconButton(onClick = { load(index + 1) }, enabled = index < paths.size - 1) {
                Icon(Icons.Filled.SkipNext, null, tint = if (index < paths.size - 1) TextDim else Color(0x33FFFFFF))
            }
            IconButton(onClick = {
                repeatMode = (repeatMode + 1) % 3
                player.repeatMode = when (repeatMode) {
                    1 -> Player.REPEAT_MODE_ONE
                    2 -> Player.REPEAT_MODE_ALL
                    else -> Player.REPEAT_MODE_OFF
                }
            }) {
                Icon(
                    if (repeatMode == 1) Icons.Filled.RepeatOne else Icons.Filled.Repeat,
                    null,
                    tint = if (repeatMode != 0) BrandBlue else TextDim
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(
            when (repeatMode) { 1 -> "单曲循环"; 2 -> "列表循环"; else -> "顺序播放" } + if (shuffle) " · 随机" else "",
            color = TextDim, fontSize = 11.sp, modifier = Modifier.fillMaxWidth(),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        Spacer(Modifier.weight(1f))
        Text(
            "滑动/左右按钮切换 · 长按可回到列表",
            color = Color(0x55FFFFFF), fontSize = 10.sp,
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
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
