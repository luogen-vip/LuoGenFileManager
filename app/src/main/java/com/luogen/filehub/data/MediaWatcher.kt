package com.luogen.filehub.data

import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.FileObserver
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import java.io.File

/**
 * 文件变化监听：下载、删除、移动、新建、改名都立刻通知界面刷新。
 * MediaStore 的 ContentObserver 负责“索引变化”，FileObserver 负责浏览器刚下载、
 * 其它文件管理器直接写盘的瞬间；再加一个轻量轮询兜底，避免个别机型不通知。
 */
object MediaWatcher {
    private var appCtx: Context? = null
    private var observer: ContentObserver? = null
    private val fileObservers = ArrayList<FileObserver>()
    private val handler = Handler(Looper.getMainLooper())
    private var started = false
    private var lastSig = ""

    private var onChange: (() -> Unit)? = null

    private val notifyRunnable = Runnable {
        try { onChange?.invoke() } catch (_: Throwable) {}
    }

    private val pollRunnable = object : Runnable {
        override fun run() {
            if (!started) return
            val ctx = appCtx
            if (ctx != null) {
                val sig = try {
                    FileRepo(ctx).recent(12).joinToString("|") {
                        it.path + ":" + it.modified + ":" + it.size
                    }
                } catch (_: Throwable) { "" }
                if (lastSig.isNotEmpty() && sig != lastSig) {
                    lastSig = sig
                    scheduleNotify()
                } else if (lastSig.isEmpty()) {
                    lastSig = sig
                }
            }
            handler.postDelayed(this, 1500L)
        }
    }

    fun start(ctx: Context, callback: () -> Unit) {
        appCtx = ctx.applicationContext
        onChange = callback
        if (started) return
        started = true
        val c = ctx.applicationContext
        try {
            val co = object : ContentObserver(handler) {
                override fun onChange(selfChange: Boolean) = scheduleNotify()
                override fun onChange(selfChange: Boolean, uri: Uri?) = scheduleNotify()
            }
            observer = co
            c.contentResolver.registerContentObserver(
                MediaStore.Files.getContentUri("external"), true, co
            )
            if (android.os.Build.VERSION.SDK_INT >= 29) {
                try {
                    c.contentResolver.registerContentObserver(
                        MediaStore.Downloads.EXTERNAL_CONTENT_URI, true, co
                    )
                } catch (_: Throwable) {}
            }
        } catch (_: Throwable) {}

        val mask = FileObserver.CREATE or FileObserver.DELETE or
            FileObserver.MOVED_FROM or FileObserver.MOVED_TO or
            FileObserver.CLOSE_WRITE or FileObserver.ATTRIB
        val root = android.os.Environment.getExternalStorageDirectory()
        val dirs = listOf(
            File(root, "Download"), File(root, "Documents"), File(root, "DCIM"),
            File(root, "Pictures"), File(root, "Movies"), File(root, "Music"),
            File(root, "Android/media"), File(root, "Telegram"), File(root, "tencent"),
            root
        )
        dirs.forEach { dir ->
            if (!dir.exists()) return@forEach
            try {
                val fo = object : FileObserver(dir.absolutePath, mask) {
                    override fun onEvent(event: Int, path: String?) = scheduleNotify()
                }
                fo.startWatching()
                fileObservers.add(fo)
            } catch (_: Throwable) {}
        }

        lastSig = try {
            FileRepo(c).recent(12).joinToString("|") {
                it.path + ":" + it.modified + ":" + it.size
            }
        } catch (_: Throwable) { "" }
        handler.postDelayed(pollRunnable, 1500L)
    }

    fun stop() {
        started = false
        handler.removeCallbacks(pollRunnable)
        handler.removeCallbacks(notifyRunnable)
        observer?.let {
            try { appCtx?.contentResolver?.unregisterContentObserver(it) } catch (_: Throwable) {}
        }
        observer = null
        fileObservers.forEach { try { it.stopWatching() } catch (_: Throwable) {} }
        fileObservers.clear()
    }

    fun scheduleNotify() {
        handler.removeCallbacks(notifyRunnable)
        handler.postDelayed(notifyRunnable, 450L)
    }
}
