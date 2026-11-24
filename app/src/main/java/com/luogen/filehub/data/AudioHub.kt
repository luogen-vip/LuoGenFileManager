package com.luogen.filehub.data

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadata
import android.media.MediaMetadataRetriever
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.net.Uri
import android.os.Build
import android.os.IBinder
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
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
    private var session: MediaSession? = null
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
    /** 封面变化计数，界面靠它立刻刷新 */
    var coverTick by mutableStateOf(0)
        private set
    var shuffleOn by mutableStateOf(false)
        private set
    var repeatMode by mutableStateOf(0)
        private set
    private var startedService = false

    fun currentPath(): String? = playlist.getOrNull(index)

    fun notifyCoverChanged() {
        coverTick++
        pushState()
        refreshNotification()
    }

    /** 先取用户自定义封面，再退回歌曲内嵌封面 */
    fun coverBitmap(ctx: Context, path: String, max: Int = 512): Bitmap? {
        val custom = Prefs.coverFor(path)
        if (custom != null && File(custom).exists()) {
            try {
                val opts = BitmapFactory.Options().apply { inSampleSize = 2 }
                return BitmapFactory.decodeFile(custom, opts)
            } catch (_: Throwable) {}
        }
        return try {
            val mmr = MediaMetadataRetriever()
            mmr.setDataSource(path)
            val art = mmr.embeddedPicture
            mmr.release()
            if (art != null && art.isNotEmpty()) BitmapFactory.decodeByteArray(art, 0, art.size) else null
        } catch (_: Throwable) { null }
    }

    fun ensure(ctx: Context): ExoPlayer {
        appCtx = ctx.applicationContext
        player?.let { return it }
        val p = ExoPlayer.Builder(ctx.applicationContext).build()
        // 正确处理音频焦点：别的应用抢声音时暂停，回来点播放能重新拿到焦点继续
        try {
            p.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                true
            )
        } catch (_: Throwable) {}
        p.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                playing = isPlaying
                pushState()
                refreshNotification()
            }

            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_READY) {
                    duration = p.duration.coerceAtLeast(0)
                }
                pushState()
                refreshNotification()
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                index = p.currentMediaItemIndex.coerceAtLeast(0)
                duration = p.duration.coerceAtLeast(0)
                position = 0
                pushState()
                refreshNotification()
            }
        })
        p.repeatMode = Player.REPEAT_MODE_OFF
        player = p
        if (session == null) {
            val s = MediaSession(ctx.applicationContext, "LuoGenFileManager")
            s.setCallback(object : MediaSession.Callback() {
                override fun onPlay() { player?.play() }
                override fun onPause() { player?.pause() }
                override fun onSkipToNext() { next() }
                override fun onSkipToPrevious() { prev() }
                override fun onSeekTo(pos: Long) { seekTo(pos) }
                override fun onStop() {
                    // 系统/其他应用抢走媒体控制时只暂停，绝不清空队列，回来还能接着播
                    player?.pause()
                    pushState()
                    refreshNotification()
                }
            })
            s.isActive = true
            session = s
        }
        pushState()
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
        pushState()
        startForegroundService()
        refreshNotification()
    }

    fun toggle() {
        val p = ensure(appCtx ?: return)
        if (p.isPlaying) {
            p.pause()
        } else {
            // 队列被系统清掉/播放器进过 ENDED 时，按当前列表原地重建再播，不能点了没反应
            if (p.mediaItemCount == 0 && playlist.isNotEmpty()) {
                playlist.forEach { path -> p.addMediaItem(MediaItem.fromUri(Uri.fromFile(File(path)))) }
                p.seekTo(index.coerceIn(0, playlist.size - 1), 0)
                p.prepare()
            } else if (p.playbackState == Player.STATE_ENDED) {
                p.seekTo(p.currentMediaItemIndex.coerceAtLeast(0), 0)
                p.prepare()
            }
            p.play()
            startForegroundService()
        }
        pushState()
        refreshNotification()
    }

    fun pauseForVideo() {
        player?.pause()
        pushState()
        refreshNotification()
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
        pushState()
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
        // 保留播放列表与当前曲目：下次点播放还能原地续播，不会变成点了没反应
        player?.clearMediaItems()
        playing = false
        appCtx?.let { NotificationManagerCompatSafe.cancel(it) }
        appCtx?.let { c ->
            try { c.stopService(Intent(c, AudioFgService::class.java)) } catch (_: Throwable) {}
        }
        startedService = false
    }

    private fun pushState() {
        val s = session ?: return
        val p = player ?: return
        try {
            val actions = PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE or
                PlaybackState.ACTION_PLAY_PAUSE or PlaybackState.ACTION_SKIP_TO_NEXT or
                PlaybackState.ACTION_SKIP_TO_PREVIOUS or PlaybackState.ACTION_SEEK_TO or
                PlaybackState.ACTION_STOP
            val state = when {
                p.isPlaying -> PlaybackState.STATE_PLAYING
                p.playbackState == Player.STATE_BUFFERING -> PlaybackState.STATE_BUFFERING
                p.playbackState == Player.STATE_ENDED -> PlaybackState.STATE_STOPPED
                else -> PlaybackState.STATE_PAUSED
            }
            s.setPlaybackState(
                PlaybackState.Builder()
                    .setActions(actions)
                    .setState(state, p.currentPosition, if (p.isPlaying) 1f else 0f)
                    .build()
            )
            val path = currentPath()
            val b = MediaMetadata.Builder()
                .putString(MediaMetadata.METADATA_KEY_TITLE, path?.let { File(it).name } ?: "罗根文件管家")
                .putString(MediaMetadata.METADATA_KEY_ARTIST, "罗根文件管家")
                .putLong(MediaMetadata.METADATA_KEY_DURATION, duration)
            val art = path?.let { appCtx?.let { c -> coverBitmap(c, it) } }
            if (art != null) {
                b.putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, art)
                b.putBitmap(MediaMetadata.METADATA_KEY_ART, art)
            }
            s.setMetadata(b.build())
        } catch (_: Throwable) {}
    }

    private fun startForegroundService() {
        val c = appCtx ?: return
        // 不靠 startedService 一次性判断：服务被系统回收后，再次播放也要能重新拉起来
        startedService = true
        try {
            val i = Intent(c, AudioFgService::class.java)
            if (Build.VERSION.SDK_INT >= 26) c.startForegroundService(i) else c.startService(i)
        } catch (_: Throwable) {}
    }

    fun buildNotification(): Notification? {
        val ctx = appCtx ?: return null
        player ?: return null
        val path = currentPath() ?: return null
        return try {
            val nm = ctx.getSystemService(NotificationManager::class.java)
            val channel = "luogen_audio"
            if (Build.VERSION.SDK_INT >= 26) {
                nm.createNotificationChannel(NotificationChannel(channel, "音频播放", NotificationManager.IMPORTANCE_LOW))
            }
            val openIntent = Intent(ctx, MainActivity::class.java)
                .putExtra("open_audio", true)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            val open = PendingIntent.getActivity(
                ctx, 10, openIntent,
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
            val art = coverBitmap(ctx, path)
            if (art != null) builder.setLargeIcon(art)
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
                        .setMediaSession(session?.sessionToken)
                        .setShowActionsInCompactView(0, 1, 2)
                )
            builder.build()
        } catch (_: Throwable) {
            null
        }
    }

    fun refreshNotification() {
        val ctx = appCtx ?: return
        val n = buildNotification() ?: return
        try {
            ctx.getSystemService(NotificationManager::class.java).notify(1001, n)
        } catch (_: Throwable) {}
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
        try { ctx.getSystemService(NotificationManager::class.java).cancel(1001) } catch (_: Throwable) {}
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
            // 直接复用同一个媒体通知（带上一曲/播放暂停/下一曲），不再额外弹一条“音频播放中”
            val n = AudioHub.buildNotification()
            if (n != null) {
                startForeground(1001, n)
            } else {
                val channel = "luogen_audio"
                val nm = ctx.getSystemService(NotificationManager::class.java)
                if (Build.VERSION.SDK_INT >= 26) {
                    nm.createNotificationChannel(NotificationChannel(channel, "音频播放", NotificationManager.IMPORTANCE_LOW))
                }
                val fb = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(ctx, channel) else Notification.Builder(ctx)
                fb.setSmallIcon(android.R.drawable.ic_media_play)
                    .setContentTitle("罗根文件管家")
                    .setContentText("音频播放中")
                    .setOngoing(true)
                startForeground(1001, fb.build())
            }
        } catch (_: Throwable) {
        }
        return START_STICKY
    }
}
