package com.luogen.filehub.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.asImageBitmap
import com.luogen.filehub.data.AudioHub
import com.luogen.filehub.model.nameNoExt
import kotlinx.coroutines.delay
import java.io.File

/**
 * 首页迷你播放条：音频在后台/返回后仍能看到正在播放什么，点一下直接回到播放页。
 */
@Composable
fun MiniPlayerBar(nav: NavStack) {
    LaunchedEffect(Unit) {
        while (true) {
            AudioHub.tick()
            delay(700)
        }
    }
    val path = AudioHub.currentPath() ?: return
    if (AudioHub.playlist.isEmpty()) return
    val playing = AudioHub.playing
    val pos = AudioHub.position
    val dur = AudioHub.duration

    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16))
            .background(SurfaceDark)
            .border(1.dp, BorderSoft, RoundedCornerShape(16))
            .clickable {
                nav.push(Screen.AudioView(AudioHub.playlist, AudioHub.index))
            }
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val ctx = LocalContext.current
        val cover = remember(path, AudioHub.coverTick) {
            AudioHub.coverBitmap(ctx, path, 200)
        }
        Box(
            Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(10))
                .background(BrandBlue.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center
        ) {
            if (cover != null) {
                Image(cover.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            } else {
                Icon(Icons.Filled.MusicNote, null, tint = BrandBlue, modifier = Modifier.size(22.dp))
            }
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                nameNoExt(File(path).name),
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = 14.sp, fontWeight = FontWeight.Medium,
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            Text(
                (if (playing) "正在播放" else "已暂停") + " · " + fmtTime(pos) + " / " + fmtTime(dur),
                color = TextDim, fontSize = 11.sp, maxLines = 1
            )
        }
        IconButton(onClick = { AudioHub.prev() }) {
            Icon(Icons.Filled.SkipPrevious, null, tint = TextDim)
        }
        IconButton(onClick = { AudioHub.toggle() }) {
            Icon(if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow, null, tint = BrandBlue)
        }
        IconButton(onClick = { AudioHub.next() }) {
            Icon(Icons.Filled.SkipNext, null, tint = TextDim)
        }
    }
}
