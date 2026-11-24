package com.luogen.filehub.data

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.io.RandomAccessFile
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.concurrent.thread

data class FtpSettings(
    val port: Int = 2121,
    val user: String = "luogen",
    val pass: String = "123456",
    val anonymous: Boolean = false,
    val readOnly: Boolean = false,
    val root: String = android.os.Environment.getExternalStorageDirectory().absolutePath
)

data class FtpClientRecord(
    val id: String,
    val ip: String,
    val connectedAt: Long,
    var lastSeen: Long,
    var online: Boolean,
    var sessions: Int = 0
)

/**
 * 轻量但完整的 FTP 服务：PASV/EPSV/PORT、上传下载、列目录、重命名、删除、建目录。
 * 由前台服务托管，切后台/小窗都不中断；通知栏显示访问地址。
 */
object FtpManager {
    var running by mutableStateOf(false)
        private set
    var address by mutableStateOf("")
        private set
    var clients by mutableStateOf<List<FtpClientRecord>>(emptyList())
        private set
    var lastError by mutableStateOf<String?>(null)
        private set

    private var server: FtpServer? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null
    private val main = Handler(Looper.getMainLooper())
    private val records = CopyOnWriteArrayList<FtpClientRecord>()
    private var appCtx: Context? = null

    fun settings(ctx: Context): FtpSettings = Prefs.ftpSettings(ctx)

    /** 开启：保存设置 → 启动前台服务（服务里真正开端口） */
    fun start(ctx: Context, cfg: FtpSettings): String? {
        appCtx = ctx.applicationContext
        Prefs.setFtpSettings(ctx, cfg)
        return try {
            val i = Intent(ctx, FtpService::class.java).setAction(FtpService.ACTION_START)
            if (Build.VERSION.SDK_INT >= 26) ctx.startForegroundService(i) else ctx.startService(i)
            null
        } catch (t: Throwable) {
            lastError = "启动失败：${t.message ?: t.javaClass.simpleName}"
            lastError
        }
    }

    fun stop(ctx: Context) {
        try { ctx.startService(Intent(ctx, FtpService::class.java).setAction(FtpService.ACTION_STOP)) } catch (_: Throwable) {}
        stopServer()
    }

    /** 服务调用：真正监听端口 */
    fun startServer(ctx: Context): String? {
        appCtx = ctx.applicationContext
        if (running) return null
        val cfg = Prefs.ftpSettings(ctx)
        val root = File(cfg.root).let { if (it.exists()) it else android.os.Environment.getExternalStorageDirectory() }
        val srv = FtpServer(cfg.copy(root = root.absolutePath), onClient = { ip, id, online ->
            updateClient(ip, id, online)
        })
        return try {
            srv.start()
            server = srv
            running = true
            address = "ftp://${srv.localIp}:${cfg.port}"
            lastError = null
            acquireLocks(ctx)
            refreshNotification()
            null
        } catch (t: Throwable) {
            server = null
            running = false
            address = ""
            lastError = "端口 ${cfg.port} 开启失败：${t.message ?: t.javaClass.simpleName}"
            lastError
        }
    }

    fun stopServer() {
        try { server?.stop() } catch (_: Throwable) {}
        server = null
        running = false
        address = ""
        records.forEach { it.online = false }
        records.clear()
        main.post { clients = emptyList() }
        releaseLocks()
        appCtx?.let { c ->
            try { c.getSystemService(NotificationManager::class.java).cancel(1003) } catch (_: Throwable) {}
        }
    }

    fun deleteClient(id: String) {
        records.removeAll { it.id == id && !it.online }
        main.post { clients = records.toList() }
        refreshNotification()
    }

    fun clearOffline() {
        records.removeAll { !it.online }
        main.post { clients = records.toList() }
        refreshNotification()
    }

    // 同一台设备（同一 IP）只保留一条记录：新连接把离线翻成在线，断开才变离线
    private fun updateClient(ip: String, id: String, online: Boolean) {
        val now = System.currentTimeMillis()
        val old = records.firstOrNull { it.ip == ip }
        if (old == null) {
            if (online) records.add(FtpClientRecord(ip, ip, now, now, true, 1))
        } else {
            if (online) old.sessions += 1 else old.sessions = (old.sessions - 1).coerceAtLeast(0)
            old.lastSeen = now
            old.online = old.sessions > 0
        }
        main.post { clients = records.sortedByDescending { it.lastSeen } }
        refreshNotification()
    }

    fun buildNotification(ctx: Context): Notification {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        val ch = "luogen_ftp"
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(NotificationChannel(ch, "FTP 文件传输", NotificationManager.IMPORTANCE_LOW))
        }
        val open = PendingIntent.getActivity(
            ctx, 11, Intent(ctx, com.luogen.filehub.ui.MainActivity::class.java)
                .putExtra("open_ftp", true)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stop = PendingIntent.getBroadcast(
            ctx, 12, Intent(ctx, FtpStopReceiver::class.java).setAction("stop_ftp"),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val b = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(ctx, ch) else Notification.Builder(ctx)
        val online = clients.count { it.online }
        b.setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("FTP 正在运行 · ${address.ifEmpty { "未启动" }}")
            .setContentText(if (online > 0) "$online 台设备在线" else "等待设备连接，关掉这个通知不会停止服务")
            .setContentIntent(open)
            .setOngoing(true)
            .addAction(Notification.Action.Builder(android.R.drawable.ic_menu_close_clear_cancel, "停止 FTP", stop).build())
        return b.build()
    }

    fun refreshNotification() {
        val c = appCtx ?: return
        if (!running) return
        try {
            c.getSystemService(NotificationManager::class.java).notify(1003, buildNotification(c))
        } catch (_: Throwable) {}
    }

    private fun acquireLocks(ctx: Context) {
        try {
            val pm = ctx.getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "luogen:ftp").apply { setReferenceCounted(false); acquire() }
        } catch (_: Throwable) {}
        try {
            val wm = ctx.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            wifiLock = wm.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "luogen:ftp").apply { setReferenceCounted(false); acquire() }
        } catch (_: Throwable) {}
    }

    private fun releaseLocks() {
        try { wakeLock?.release() } catch (_: Throwable) {}
        try { wifiLock?.release() } catch (_: Throwable) {}
        wakeLock = null
        wifiLock = null
    }
}

class FtpServer(
    private val cfg: FtpSettings,
    private val onClient: (ip: String, id: String, online: Boolean) -> Unit
) {
    private val root = File(cfg.root).canonicalFile
    private var serverSocket: ServerSocket? = null
    @Volatile private var alive = true
    val localIp: String = findLocalIp()

    fun start() {
        val s = ServerSocket()
        s.reuseAddress = true
        s.bind(InetSocketAddress(cfg.port))
        serverSocket = s
        thread(name = "ftp-accept", isDaemon = true) {
            while (alive) {
                try {
                    val sock = s.accept()
                    thread(name = "ftp-client", isDaemon = true) { handle(sock) }
                } catch (_: Throwable) {
                    if (!alive) break
                }
            }
        }
    }

    fun stop() {
        alive = false
        try { serverSocket?.close() } catch (_: Throwable) {}
        serverSocket = null
    }

    private fun findLocalIp(): String {
        try {
            val ifs = NetworkInterface.getNetworkInterfaces()
            while (ifs.hasMoreElements()) {
                val nif = ifs.nextElement()
                if (!nif.isUp || nif.isLoopback) continue
                val addrs = nif.inetAddresses
                while (addrs.hasMoreElements()) {
                    val a = addrs.nextElement()
                    if (a is Inet4Address && a.isSiteLocalAddress) return a.hostAddress ?: "127.0.0.1"
                }
            }
        } catch (_: Throwable) {}
        return "127.0.0.1"
    }

    private fun handle(socket: Socket) {
        val ip = socket.inetAddress?.hostAddress ?: "未知"
        val id = ip
        onClient(ip, id, true)
        try {
            socket.soTimeout = 0
            val reader = BufferedReader(InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))
            val out = OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8)
            fun reply(line: String) {
                out.write(line + "\r\n"); out.flush()
            }
            reply("220 LuoGen FTP ready")
            var cwd = root
            var logged = false
            var user = ""
            var pasv: ServerSocket? = null
            var active: InetAddress? = null
            var activePort = 0
            var rest = 0L
            var renameFrom: File? = null

            while (alive) {
                val line = reader.readLine() ?: break
                if (line.isBlank()) continue
                val sp = line.indexOf(' ')
                val cmd = (if (sp < 0) line else line.substring(0, sp)).uppercase(Locale.US)
                val arg = if (sp < 0) "" else line.substring(sp + 1)
                when (cmd) {
                    "USER" -> {
                        user = arg.trim()
                        reply("331 请输入密码")
                    }
                    "PASS" -> {
                        val ok = when {
                            cfg.anonymous && (user.equals("anonymous", true) || user.equals("ftp", true)) -> true
                            user == cfg.user && arg == cfg.pass -> true
                            else -> false
                        }
                        if (ok) { logged = true; reply("230 登录成功") } else reply("530 用户名或密码错误")
                    }
                    "AUTH", "PBSZ", "PROT" -> reply("502 不支持加密，请用普通 FTP")
                    "SYST" -> reply("215 UNIX Type: L8")
                    "FEAT" -> {
                        reply("211-Features:")
                        reply(" UTF8")
                        reply(" SIZE")
                        reply(" MDTM")
                        reply(" REST STREAM")
                        reply(" MLST type*;size*;modify*;")
                        reply(" MLSD")
                        reply("211 End")
                    }
                    "OPTS" -> reply("200 OK")
                    "NOOP" -> reply("200 OK")
                    "TYPE" -> reply("200 类型已切换")
                    "MODE" -> reply("200 模式 S")
                    "STRU" -> reply("200 结构 F")
                    "PWD", "XPWD" -> reply("257 \"/${rel(cwd)}\"")
                    "CWD", "XCWD" -> {
                        val f = resolve(cwd, arg)
                        if (f != null && f.isDirectory) { cwd = f; reply("250 目录已切换") } else reply("550 目录不存在")
                    }
                    "CDUP", "XCUP" -> {
                        val p = cwd.parentFile
                        cwd = if (p != null && p.canonicalPath.startsWith(root.canonicalPath)) p else root
                        reply("250 已返回上级")
                    }
                    "PASV" -> {
                        closeQuietly(pasv)
                        val ds = ServerSocket(0, 1, InetAddress.getByName(localIp))
                        pasv = ds
                        val p = ds.localPort
                        reply("227 Entering Passive Mode (${localIp.replace('.', ',')},${p / 256},${p % 256})")
                    }
                    "EPSV" -> {
                        closeQuietly(pasv)
                        val ds = ServerSocket(0, 1, InetAddress.getByName(localIp))
                        pasv = ds
                        reply("229 Entering Extended Passive Mode (|||${ds.localPort}|)")
                    }
                    "PORT" -> {
                        val nums = arg.split(',').mapNotNull { it.trim().toIntOrNull() }
                        if (nums.size >= 6) {
                            active = InetAddress.getByName(nums.take(4).joinToString("."))
                            activePort = nums[4] * 256 + nums[5]
                            reply("200 PORT OK")
                        } else reply("501 参数错误")
                    }
                    "EPRT" -> {
                        val parts = arg.split('|')
                        if (parts.size >= 4) {
                            active = InetAddress.getByName(parts[2])
                            activePort = parts[3].toIntOrNull() ?: 0
                            reply("200 EPRT OK")
                        } else reply("501 参数错误")
                    }
                    "LIST", "NLST", "MLSD" -> {
                        val target = if (arg.isBlank()) cwd else resolve(cwd, arg)
                        if (target == null || !target.isDirectory) { reply("550 目录不存在"); continue }
                        val data = openData(pasv, active, activePort)
                        pasv = null
                        if (data == null) { reply("425 无法建立数据连接"); continue }
                        reply("150 正在列出目录")
                        try {
                            val w = BufferedOutputStream(data.getOutputStream())
                            val fmt = SimpleDateFormat("MMM dd HH:mm", Locale.US)
                            val list = target.listFiles() ?: emptyArray()
                            list.sortedBy { it.name.lowercase() }.forEach { f ->
                                when (cmd) {
                                    "NLST" -> w.write((f.name + "\r\n").toByteArray(StandardCharsets.UTF_8))
                                    "MLSD" -> {
                                        val kind = if (f.isDirectory) "dir" else "file"
                                        val s = "type=$kind;size=${f.length()};modify=${utc(f.lastModified())}; ${f.name}\r\n"
                                        w.write(s.toByteArray(StandardCharsets.UTF_8))
                                    }
                                    else -> {
                                        val perm = if (f.isDirectory) "drwxrwxrwx" else "-rw-rw-rw-"
                                        val s = "$perm 1 ftp ftp ${f.length().toString().padStart(12)} ${fmt.format(Date(f.lastModified()))} ${f.name}\r\n"
                                        w.write(s.toByteArray(StandardCharsets.UTF_8))
                                    }
                                }
                            }
                            w.flush()
                            reply("226 传输完成")
                        } catch (_: Throwable) {
                            reply("426 连接中断")
                        } finally {
                            closeQuietly(data)
                        }
                    }
                    "MLST" -> {
                        val f = if (arg.isBlank()) cwd else resolve(cwd, arg)
                        if (f == null) reply("550 不存在") else {
                            reply("250-Listing")
                            reply(" type=${if (f.isDirectory) "dir" else "file"};size=${f.length()}; ${f.name}")
                            reply("250 End")
                        }
                    }
                    "SIZE" -> {
                        val f = resolve(cwd, arg)
                        if (f != null && f.isFile) reply("213 ${f.length()}") else reply("550 文件不存在")
                    }
                    "MDTM" -> {
                        val f = resolve(cwd, arg)
                        if (f != null && f.exists()) reply("213 ${utc(f.lastModified())}") else reply("550 不存在")
                    }
                    "REST" -> {
                        rest = arg.trim().toLongOrNull() ?: 0L
                        reply("350 从 $rest 继续")
                    }
                    "RETR" -> {
                        val f = resolve(cwd, arg)
                        if (f == null || !f.isFile) { reply("550 文件不存在"); continue }
                        val data = openData(pasv, active, activePort)
                        pasv = null
                        if (data == null) { reply("425 无法建立数据连接"); continue }
                        reply("150 正在发送 ${f.name}")
                        try {
                            RandomAccessFile(f, "r").use { raf ->
                                if (rest > 0) raf.seek(rest.coerceAtMost(f.length()))
                                val outS = BufferedOutputStream(data.getOutputStream())
                                val buf = ByteArray(64 * 1024)
                                while (true) {
                                    val n = raf.read(buf)
                                    if (n <= 0) break
                                    outS.write(buf, 0, n)
                                }
                                outS.flush()
                            }
                            reply("226 传输完成")
                        } catch (_: Throwable) {
                            reply("426 传输中断")
                        } finally {
                            rest = 0
                            closeQuietly(data)
                        }
                    }
                    "STOR", "APPE" -> {
                        if (cfg.readOnly) { reply("550 只读模式，禁止上传"); continue }
                        val f = resolve(cwd, arg, allowMissing = true)
                        if (f == null) { reply("550 路径非法"); continue }
                        val data = openData(pasv, active, activePort)
                        pasv = null
                        if (data == null) { reply("425 无法建立数据连接"); continue }
                        reply("150 正在接收 ${f.name}")
                        try {
                            f.parentFile?.mkdirs()
                            FileOutputStream(f, cmd == "APPE").use { fos ->
                                val ins = BufferedInputStream(data.getInputStream())
                                val buf = ByteArray(64 * 1024)
                                while (true) {
                                    val n = ins.read(buf)
                                    if (n <= 0) break
                                    fos.write(buf, 0, n)
                                }
                                fos.flush()
                            }
                            reply("226 上传完成")
                        } catch (_: Throwable) {
                            reply("426 上传中断")
                        } finally {
                            closeQuietly(data)
                        }
                    }
                    "DELE" -> {
                        if (cfg.readOnly) { reply("550 只读模式"); continue }
                        val f = resolve(cwd, arg)
                        reply(if (f != null && f.isFile && f.delete()) "250 已删除" else "550 删除失败")
                    }
                    "MKD", "XMKD" -> {
                        if (cfg.readOnly) { reply("550 只读模式"); continue }
                        val f = resolve(cwd, arg, allowMissing = true)
                        if (f != null && (f.mkdirs() || f.isDirectory)) reply("257 已创建") else reply("550 创建失败")
                    }
                    "RMD", "XRMD" -> {
                        if (cfg.readOnly) { reply("550 只读模式"); continue }
                        val f = resolve(cwd, arg)
                        val empty = f != null && f.isDirectory && (f.list()?.isEmpty() ?: true)
                        reply(if (empty && f!!.delete()) "250 已删除" else "550 目录非空或删除失败")
                    }
                    "RNFR" -> {
                        val f = resolve(cwd, arg)
                        if (f != null && f.exists()) { renameFrom = f; reply("350 请提供新名字") } else reply("550 不存在")
                    }
                    "RNTO" -> {
                        val from = renameFrom
                        val to = resolve(cwd, arg, allowMissing = true)
                        renameFrom = null
                        if (cfg.readOnly) reply("550 只读模式")
                        else if (from != null && to != null && from.renameTo(to)) reply("250 已重命名")
                        else reply("550 重命名失败")
                    }
                    "QUIT" -> { reply("221 再见"); break }
                    else -> reply("502 命令不支持")
                }
            }
        } catch (_: Throwable) {
        } finally {
            onClient(ip, id, false)
            try { socket.close() } catch (_: Throwable) {}
        }
    }

    private fun openData(pasv: ServerSocket?, active: InetAddress?, activePort: Int): Socket? {
        return try {
            when {
                pasv != null -> {
                    pasv.soTimeout = 30000
                    val s = pasv.accept()
                    closeQuietly(pasv)
                    s
                }
                active != null && activePort > 0 -> Socket(active, activePort, InetAddress.getByName(localIp), 0).apply { soTimeout = 30000 }
                else -> null
            }
        } catch (_: Throwable) { null }
    }

    private fun resolve(base: File, arg: String, allowMissing: Boolean = false): File? {
        val raw = arg.trim().trim('"')
        val f = when {
            raw.isEmpty() -> base
            raw.startsWith("/") -> File(root, raw.trimStart('/'))
            else -> File(base, raw)
        }
        return try {
            val c = f.canonicalFile
            if (c.path == root.path || c.path.startsWith(root.path + File.separator)) {
                if (!allowMissing && !c.exists()) null else c
            } else null
        } catch (_: Throwable) { null }
    }

    private fun rel(f: File): String {
        val p = f.canonicalPath.removePrefix(root.canonicalPath).trimStart(File.separatorChar)
        return p.replace(File.separatorChar, '/')
    }

    private fun utc(ms: Long): String =
        SimpleDateFormat("yyyyMMddHHmmss", Locale.US).apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }.format(Date(ms))

    private fun closeQuietly(c: java.io.Closeable?) {
        try { c?.close() } catch (_: Throwable) {}
    }
}

class FtpService : Service() {
    companion object {
        const val ACTION_START = "ftp_start"
        const val ACTION_STOP = "ftp_stop"
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val nm = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(NotificationChannel("luogen_ftp", "FTP 文件传输", NotificationManager.IMPORTANCE_LOW))
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            FtpManager.stopServer()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        startForeground(1003, FtpManager.buildNotification(this))
        FtpManager.startServer(this)
        // 地址/在线数变化后刷新通知
        FtpManager.refreshNotification()
        return START_STICKY
    }

    override fun onDestroy() {
        FtpManager.stopServer()
        super.onDestroy()
    }
}

class FtpStopReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        if (intent?.action == "stop_ftp" && context != null) {
            FtpManager.stop(context)
            context.stopService(Intent(context, FtpService::class.java))
        }
    }
}
