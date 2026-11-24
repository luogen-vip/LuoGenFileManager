package com.luogen.filehub.data

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.IBinder
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.luogen.filehub.ui.MainActivity
import com.luogen.filehub.R
import java.io.File

/**
 * 全局唯一的音频播放器：Activity 重建 / 返回 / 切小窗 / 后台都不会重建播放器，
 * 并由前台服务 + 通知栏控制播放。
 */
object AudioHub {
    private var player: ExoPlayer? = null
    private var appCtx: Context? = null

    var playlist: List<String> = emptyList()
        private set
    var index: Int = 0
        private set
    var playing by mutableStateOf(false)
        private set
    var position by mutableStateOf(0L)
        private set
    var duration by mutableStateOf(0L)
        private set
    var shuffleOn by mutableStateOf(false)
        private set
    var repeatMode by mutableStateOf(0)
        private set
    private var startedService = false

    fun currentPath(): String? = playlist.getOrNull(index)

    fun ensure(ctx: Context): ExoPlayer {
        appCtx = ctx.applicationContext
        player?.let { return it }
        val p = ExoPlayer.Builder(ctx.applicationContext).build()
        p.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                playing = isPlaying
                refreshNotification()
            }

            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_READY) {
                    duration = p.duration.coerceAtLeast(0)
                    refreshNotification()
                }
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                index = p.currentMediaItemIndex.coerceAtLeast(0)
                duration = p.duration.coerceAtLeast(0)
                position = 0
                refreshNotification()
            }
        })
        p.repeatMode = Player.REPEAT_MODE_OFF
        player = p
        return p
    }

    fun play(list: List<String>, i: Int, ctx: Context) {
        if (list.isEmpty()) return
        val p = ensure(ctx)
        val idx = i.coerceIn(0, list.size - 1)
        val sameList = playlist == list
        if (sameList && p.mediaItemCount == list.size) {
            if (p.currentMediaItemIndex == idx && p.isPlaying) return
            if (p.currentMediaItemIndex == idx) {
                p.play()
                return
            }
        }
        playlist = list
        index = idx
        p.clearMediaItems()
        list.forEach { path -> p.addMediaItem(MediaItem.fromUri(Uri.fromFile(File(path)))) }
        p.shuffleModeEnabled = shuffleOn
        p.seekTo(idx, 0)
        p.prepare()
        p.playWhenReady = true
        startForegroundService()
        refreshNotification()
    }

    fun toggle() {
        val p = player ?: return
        if (p.isPlaying) p.pause() else p.play()
    }

    fun pauseForVideo() {
        player?.pause()
    }

    fun next() {
        val p = player ?: return
        if (p.mediaItemCount == 0) return
        if (p.hasNextMediaItem()) p.seekToNextMediaItem() else p.seekTo(0, 0)
        p.play()
    }

    fun prev() {
        val p = player ?: return
        if (p.mediaItemCount == 0) return
        if (p.hasPreviousMediaItem()) p.seekToPreviousMediaItem() else p.seekTo(p.mediaItemCount - 1, 0)
        p.play()
    }

    fun seekTo(ms: Long) {
        player?.seekTo(ms.coerceAtLeast(0))
        position = ms.coerceAtLeast(0)
    }

    fun setShuffle(on: Boolean) {
        shuffleOn = on
        player?.shuffleModeEnabled = on
    }

    fun cycleRepeat(): Int {
        repeatMode = (repeatMode + 1) % 3
        player?.repeatMode = when (repeatMode) {
            1 -> Player.REPEAT_MODE_ONE
            2 -> Player.REPEAT_MODE_ALL
            else -> Player.REPEAT_MODE_OFF
        }
        return repeatMode
    }

    fun tick() {
        val p = player ?: return
        position = p.currentPosition
        duration = p.duration.coerceAtLeast(0)
    }

    fun stopAndClear() {
        player?.pause()
        player?.clearMediaItems()
        playlist = emptyList()
        playing = false
        appCtx?.let { NotificationManagerCompatSafe.cancel(it) }
        appCtx?.let { c ->
            try { c.stopService(Intent(c, AudioFgService::class.java)) } catch (_: Throwable) {}
        }
        startedService = false
    }

    private fun startForegroundService() {
        val c = appCtx ?: return
        if (startedService) return
        startedService = true
        try {
            val i = Intent(c, AudioFgService::class.java)
            if (Build.VERSION.SDK_INT >= 26) c.startForegroundService(i) else c.startService(i)
        } catch (_: Throwable) {}
    }

    fun refreshNotification() {
        val ctx = appCtx ?: return
        val p = player ?: return
        val path = currentPath() ?: return
        try {
            val nm = ctx.getSystemService(NotificationManager::class.java)
            val channel = "luogen_audio"
            if (Build.VERSION.SDK_INT >= 26) {
                nm.createNotificationChannel(NotificationChannel(channel, "音频播放", NotificationManager.IMPORTANCE_LOW))
            }
            val open = PendingIntent.getActivity(
                ctx, 0, Intent(ctx, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            fun act(code: String, icon: Int, title: String): Notification.Action {
                val i = Intent(ctx, AudioActionReceiver::class.java).setAction(code)
                val pi = PendingIntent.getBroadcast(
                    ctx, code.hashCode(), i,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                return Notification.Action.Builder(icon, title, pi).build()
            }
            val builder = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(ctx, channel) else Notification.Builder(ctx)
            builder
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setContentTitle(File(path).name)
                .setContentText(if (playing) "正在播放" else "已暂停")
                .setContentIntent(open)
                .setOngoing(playing)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .addAction(act("prev", android.R.drawable.ic_media_previous, "上一曲"))
                .addAction(
                    act(
                        "toggle",
                        if (playing) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
                        if (playing) "暂停" else "播放"
                    )
                )
                .addAction(act("next", android.R.drawable.ic_media_next, "下一曲"))
                .setStyle(
                    Notification.MediaStyle()
                        .setShowActionsInCompactView(0, 1, 2)
                )
            nm.notify(1002, builder.build())
        } catch (_: Throwable) {
        }
    }

    fun handleAction(action: String?) {
        when (action) {
            "prev" -> prev()
            "next" -> next()
            "toggle" -> toggle()
            "stop" -> stopAndClear()
        }
    }
}

private object NotificationManagerCompatSafe {
    fun cancel(ctx: Context) {
        try { ctx.getSystemService(NotificationManager::class.java).cancel(1002) } catch (_: Throwable) {}
    }
}

class AudioActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        AudioHub.handleAction(intent?.action)
    }
}

/** 前台服务：只为让播放不被系统回收，真正的播放器在 AudioHub 里 */
class AudioFgService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        try {
            val ctx = applicationContext
            AudioHub.ensure(ctx)
            AudioHub.refreshNotification()
            val nm = ctx.getSystemService(NotificationManager::class.java)
            val channel = "luogen_audio"
            if (Build.VERSION.SDK_INT >= 26) {
                nm.createNotificationChannel(NotificationChannel(channel, "音频播放", NotificationManager.IMPORTANCE_LOW))
            }
            val n = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(ctx, channel) else Notification.Builder(ctx)
            n.setSmallIcon(android.R.drawable.ic_media_play)
                .setContentTitle("罗根文件管家")
                .setContentText("音频播放中")
                .setOngoing(true)
            startForeground(1001, n.build())
        } catch (_: Throwable) {
        }
        return START_STICKY
    }
}
