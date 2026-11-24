package com.luogen.filehub

import android.app.Application
import android.os.Build
import android.os.Environment
import com.luogen.filehub.data.Prefs
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 全局崩溃兜底：任何未捕获异常都落一份日志到
 * - /sdcard/Download/罗根文件管家-崩溃日志.txt
 * - 应用外部私有目录/last_crash.txt
 * - 内部 files/last_crash.txt
 * 方便用户把文件发回来定位问题。
 */
class App : Application() {
    override fun onCreate() {
        super.onCreate()
        Prefs.init(this)

        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, e ->
            try {
                val sw = StringWriter()
                e.printStackTrace(PrintWriter(sw))
                val body = buildString {
                    append("时间: ")
                    append(SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA).format(Date()))
                    append('\n')
                    append("机型: ")
                    append(Build.MANUFACTURER)
                    append(' ')
                    append(Build.MODEL)
                    append('\n')
                    append("系统: Android ")
                    append(Build.VERSION.RELEASE)
                    append(" (SDK ")
                    append(Build.VERSION.SDK_INT)
                    append(")\n")
                    append("应用: 罗根文件管家 1.0.1\n")
                    append("线程: ")
                    append(thread.name)
                    append("\n\n")
                    append(sw.toString())
                }
                runCatching { File(filesDir, "last_crash.txt").writeText(body) }
                runCatching {
                    val ext = getExternalFilesDir(null)
                    if (ext != null) File(ext, "last_crash.txt").writeText(body)
                }
                runCatching {
                    val dl = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                    if (dl != null) {
                        dl.mkdirs()
                        File(dl, "罗根文件管家-崩溃日志.txt").writeText(body)
                    }
                }
            } catch (_: Throwable) {
            }
            previous?.uncaughtException(thread, e)
        }
    }
}
