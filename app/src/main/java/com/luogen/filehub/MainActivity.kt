package com.luogen.filehub.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.luogen.filehub.data.Prefs
import com.luogen.filehub.ui.AppNav
import java.io.File

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        if (intent?.getBooleanExtra("open_audio", false) == true) AppState.audioOpenRequested = true
        if (intent?.getBooleanExtra("open_ftp", false) == true) AppState.ftpOpenRequested = true
        super.onCreate(savedInstanceState)
        Prefs.init(this)
        // 文件变化监听：下载/删除/移动/新建后界面立刻刷新，不靠手动刷新或杀后台
        try {
            com.luogen.filehub.data.MediaWatcher.start(this) {
                AppState.markDataChanged()
            }
        } catch (_: Throwable) {}
        // 先把上次的播放队列接回来，再恢复导航，播放页才知道该回到哪首歌
        try { com.luogen.filehub.data.AudioHub.ensure(this) } catch (_: Throwable) {}
        // 进程被清理后重新打开：导航栈回到上次的位置，而不是永远回首页
        try { AppState.restoreSearchFilters() } catch (_: Throwable) {}
        // 但要是这次是被别的 App“用罗根文件管家打开文件”拉起来的，就别再恢复上次的页面了，
        // 否则会先闪一下（甚至停在上次的下载页），看起来像根本没打开文件
        val fromExternal = intent?.action == Intent.ACTION_VIEW && intent?.data != null
        if (!fromExternal) {
            try { AppState.nav.restore(Prefs.navRoutes()) } catch (_: Throwable) {}
        }
        handleExternalIntent(intent)
        try {
            androidx.core.view.WindowCompat.getInsetsController(window, window.decorView)
                .isAppearanceLightStatusBars = true
        } catch (_: Throwable) {
        }
        setContent {
            AppTheme {
                AppRoot()
            }
        }
    }

    override fun onPause() {
        try {
            Prefs.saveNavRoutes(AppState.nav.routes())
            Prefs.flush()
        } catch (_: Throwable) {}
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        try {
            com.luogen.filehub.data.MediaWatcher.start(this) {
                AppState.markDataChanged()
            }
        } catch (_: Throwable) {}
        // 在别的 App 下载/删除/移动后回到本 App：立刻清缓存重读
        try { AppState.markDataChanged() } catch (_: Throwable) {}
    }

    override fun onStop() {
        try { com.luogen.filehub.data.MediaWatcher.stop() } catch (_: Throwable) {}
        super.onStop()
    }

    override fun onDestroy() {
        try { com.luogen.filehub.data.MediaWatcher.stop() } catch (_: Throwable) {}
        super.onDestroy()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.getBooleanExtra("open_audio", false)) AppState.audioOpenRequested = true
        if (intent.getBooleanExtra("open_ftp", false)) AppState.ftpOpenRequested = true
        handleExternalIntent(intent)
    }

    /** 别的 App 选择「用罗根文件管家打开」时，把文件接进来交给对应查看器 */
    private fun handleExternalIntent(i: Intent?) {
        if (i == null || i.action != Intent.ACTION_VIEW) return
        val uri = i.data ?: return
        if (uri.scheme.isNullOrBlank()) return
        try {
            val name = com.luogen.filehub.data.ExternalOpen.nameOf(this, uri, uri.lastPathSegment ?: "外部文件")
            AppState.requestExternalOpen(uri.toString(), name, i.type ?: "")
        } catch (_: Throwable) {
        }
    }
}

fun hasAllFilesAccess(ctx: Context): Boolean {
    return if (Build.VERSION.SDK_INT >= 30) {
        Environment.isExternalStorageManager()
    } else {
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
    }
}

@Composable
fun AppRoot() {
    val ctx = LocalContext.current
    val nav = AppState.nav
    var granted by remember { mutableStateOf(hasAllFilesAccess(ctx)) }

    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        granted = hasAllFilesAccess(ctx) || result.values.any { it }
        if (granted) { AppState.invalidateCounts(); AppState.dropAllLists() }
    }

    var showPerm by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(500)
        if (!hasAllFilesAccess(ctx)) showPerm = true
    }
    // 从系统设置回来时自动检测
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(1200)
            val now = hasAllFilesAccess(ctx)
            if (now && !granted) {
                granted = true
                AppState.invalidateCounts()
                AppState.dropAllLists()
            }
            if (now) showPerm = false
        }
    }

    LaunchedEffect(AppState.ftpOpenRequested) {
        if (AppState.ftpOpenRequested) {
            AppState.ftpOpenRequested = false
            try { nav.push(Screen.Ftp) } catch (_: Throwable) {}
        }
    }
    // 外部 App 传来的文件：先复制到缓存，再按类型打开；失败给明确提示
    // 注意：这里用 Channel 收，不要用 Compose 状态当 key —— 清空状态会取消正在处理的协程
    LaunchedEffect(Unit) {
        for (req in AppState.externalOpenQueue) {
            AppState.externalBusy = req.name
            val item = try {
                com.luogen.filehub.data.ExternalOpen.materialize(ctx, req.uri, req.name, req.mime)
            } finally {
                AppState.externalBusy = null
            }
            if (item == null) {
                android.widget.Toast.makeText(ctx, "无法读取这个文件（可能已被移动或没有访问权限）", android.widget.Toast.LENGTH_LONG).show()
            } else {
                try {
                    openItem(ctx, nav, item, listOf(item))
                } catch (t: Throwable) {
                    android.widget.Toast.makeText(
                        ctx, "这个文件暂时打不开：${item.name}", android.widget.Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    LaunchedEffect(AppState.audioOpenRequested) {
        if (AppState.audioOpenRequested) {
            AppState.audioOpenRequested = false
            try {
                if (com.luogen.filehub.data.AudioHub.playlist.isNotEmpty()) {
                    nav.push(Screen.AudioView(com.luogen.filehub.data.AudioHub.playlist, com.luogen.filehub.data.AudioHub.index))
                }
            } catch (_: Throwable) {}
        }
    }
    // 通知栏播放控制需要通知权限（Android 13+）
    if (Build.VERSION.SDK_INT >= 33) {
        val nm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
        LaunchedEffect(Unit) {
            try {
                val ok = androidx.core.content.ContextCompat.checkSelfPermission(
                    ctx, android.Manifest.permission.POST_NOTIFICATIONS
                ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                if (!ok) nm.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            } catch (_: Throwable) {}
        }
    }
    AppNav(ctx, nav)

    // 从别的 App 转进来的大文件要复制一份，复制期间给明确提示，别让人以为点了没反应
    AppState.externalBusy?.let { busyName ->
        Box(
            Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color(0x88000000)),
            contentAlignment = Alignment.Center
        ) {
            Column(
                Modifier
                    .background(SurfaceDark, RoundedCornerShape(16.dp))
                    .padding(horizontal = 26.dp, vertical = 22.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                CircularProgressIndicator(color = BrandBlue, strokeWidth = 2.5.dp, modifier = Modifier.size(30.dp))
                Spacer(Modifier.height(12.dp))
                Text("正在打开…", color = TextMain, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                if (busyName.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(busyName, color = TextDim, fontSize = 12.sp, maxLines = 2)
                }
            }
        }
    }

    if (showPerm) {
        AlertDialog(
            onDismissRequest = { },
            title = { Text("需要所有文件访问权限") },
            text = {
                Text("授权后才能真正扫描、预览、复制、移动、压缩和解压手机里的全部文件。首页已经可以先看，随时点下面去授权。", fontSize = 14.sp)
            },
            confirmButton = {
                TextButton(onClick = {
                    if (Build.VERSION.SDK_INT >= 30) {
                        try {
                            ctx.startActivity(
                                Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:" + ctx.packageName))
                            )
                        } catch (_: Throwable) {
                            ctx.startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
                        }
                    } else {
                        permLauncher.launch(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE))
                    }
                }) { Text("去授权") }
            },
            dismissButton = {
                TextButton(onClick = {
                    granted = hasAllFilesAccess(ctx)
                    if (granted) {
                        showPerm = false
                    } else {
                        android.widget.Toast.makeText(ctx, "还没有授权，部分文件可能看不到", android.widget.Toast.LENGTH_SHORT).show()
                    }
                }) { Text("我已授权，重新检测") }
            }
        )
    }
}

@Composable
private fun PermissionScreen(onRequest: () -> Unit, onRecheck: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().background(BgDeep).padding(28.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("罗根文件管家", fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        Text(
            "需要「所有文件访问权限」才能扫描、预览、复制、移动、重命名、压缩和解压手机里的所有文件。",
            color = TextDim, fontSize = 15.sp
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onRequest, shape = RoundedCornerShape(12.dp)) {
            Text("去授权")
        }
        Spacer(Modifier.height(10.dp))
        TextButton(onClick = onRecheck) { Text("我已经授权了，重新检测") }
    }
}

class NavStack {
    val stack = mutableStateListOf<Screen>(Screen.Home)

    val current: Screen get() = stack.last()

    fun push(s: Screen) {
        if (stack.size > 24) stack.removeAt(1)
        stack.add(s)
    }

    fun pop() {
        if (stack.size > 1) stack.removeAt(stack.size - 1)
    }

    fun popToHome() {
        while (stack.size > 1) stack.removeAt(stack.size - 1)
    }

    fun replace(s: Screen) {
        if (stack.isNotEmpty()) stack.removeAt(stack.size - 1)
        stack.add(s)
    }

    /** 把整个导航栈编码成字符串，进程被清理后还能原样恢复 */
    fun routes(): List<String> = stack.map { it.route() }

    fun restore(routes: List<String>) {
        if (routes.isEmpty()) return
        val out = ArrayList<Screen>()
        routes.forEach { route ->
            val s = routeToScreen(route) ?: return@forEach
            if (s !is Screen.Home && out.none { it === s }) out.add(s)
        }
        if (out.isEmpty()) return
        stack.clear()
        stack.add(Screen.Home)
        out.take(23).forEach { stack.add(it) }
    }
}

sealed class Screen {
    object Home : Screen()
    // id 让分类页在查看器返回后仍是同一个页面实例，目录/搜索状态不会丢
    data class CatList(val cat: com.luogen.filehub.model.Cat, val id: Long = System.nanoTime()) : Screen()
    // id 让每一次“从某处点进文件夹”都是一个独立浏览页，返回不会串目录
    data class Browse(val dir: String, val id: Long = System.nanoTime()) : Screen()
    data class ImageView(val paths: List<String>, val start: Int) : Screen()
    data class VideoView(val paths: List<String>, val start: Int) : Screen()
    data class AudioView(val paths: List<String>, val start: Int) : Screen()
    data class TextView(val path: String) : Screen()
    data class PdfView(val path: String) : Screen()
    data class ArchiveView(val path: String) : Screen()
    data class GlobalSearch(val query: String) : Screen()
    data class CleanCat(val cat: com.luogen.filehub.model.Cat) : Screen()
    object Vault : Screen()
    object Ftp : Screen()
    data class VaultViewer(val cachedPath: String, val name: String) : Screen()
}

private fun enc(s: String): String = try { android.net.Uri.encode(s) } catch (_: Throwable) { s }
private fun dec(s: String): String = try { android.net.Uri.decode(s) } catch (_: Throwable) { s }

private fun Screen.route(): String = when (this) {
    is Screen.Home -> "home"
    is Screen.CatList -> "cat|" + cat.name
    is Screen.Browse -> "browse|" + id + "|" + enc(dir)
    is Screen.ImageView -> "image|" + enc(paths.getOrNull(start) ?: paths.firstOrNull().orEmpty())
    is Screen.VideoView -> "video|" + enc(paths.getOrNull(start) ?: paths.firstOrNull().orEmpty())
    is Screen.AudioView -> "audio|" + start
    is Screen.TextView -> "text|" + enc(path)
    is Screen.PdfView -> "pdf|" + enc(path)
    is Screen.ArchiveView -> "archive|" + enc(path)
    is Screen.GlobalSearch -> "search|" + enc(query)
    is Screen.CleanCat -> "clean|" + cat.name
    is Screen.Vault -> "vault"
    is Screen.Ftp -> "ftp"
    is Screen.VaultViewer -> "vv|" + enc(cachedPath) + "|" + enc(name)
}

private fun catByName(name: String): com.luogen.filehub.model.Cat? =
    com.luogen.filehub.model.Cat.values().firstOrNull { it.name == name }

private fun routeToScreen(route: String): Screen? {
    val p = route.split("|")
    fun path(i: Int): String? {
        if (i >= p.size) return null
        val v = dec(p[i])
        return v.ifEmpty { null }
    }
    return when (p.firstOrNull()) {
        "home" -> Screen.Home
        "cat" -> catByName(p.getOrNull(1) ?: "")?.let { Screen.CatList(it) }
        "browse" -> {
            val d = path(2) ?: return null
            val f = File(d)
            if (f.exists() && f.isDirectory) Screen.Browse(d, p.getOrNull(1)?.toLongOrNull() ?: (d.hashCode().toLong())) else null
        }
        "image" -> path(1)?.let { File(it) }?.takeIf { it.exists() && it.isFile }?.let {
            Screen.ImageView(listOf(it.absolutePath), 0)
        }
        "video" -> path(1)?.let { File(it) }?.takeIf { it.exists() && it.isFile }?.let {
            Screen.VideoView(listOf(it.absolutePath), 0)
        }
        "audio" -> {
            val hub = com.luogen.filehub.data.AudioHub
            if (hub.playlist.isEmpty()) null else Screen.AudioView(hub.playlist, hub.index.coerceIn(0, hub.playlist.size - 1))
        }
        "text" -> path(1)?.let { File(it) }?.takeIf { it.exists() && it.isFile }?.let { Screen.TextView(it.absolutePath) }
        "pdf" -> path(1)?.let { File(it) }?.takeIf { it.exists() && it.isFile }?.let { Screen.PdfView(it.absolutePath) }
        "archive" -> path(1)?.let { File(it) }?.takeIf { it.exists() && it.isFile }?.let { Screen.ArchiveView(it.absolutePath) }
        "search" -> path(1)?.let { Screen.GlobalSearch(it) }
        "clean" -> catByName(p.getOrNull(1) ?: "")?.let { Screen.CleanCat(it) }
        "vault" -> Screen.Vault
        "ftp" -> Screen.Ftp
        "vv" -> {
            val c = path(1) ?: return null
            if (File(c).exists()) Screen.VaultViewer(c, dec(p.getOrNull(2) ?: "")) else null
        }
        else -> null
    }
}

/** 统一的“打开文件”入口：按类型跳对应查看器 */
fun openItem(ctx: Context, nav: NavStack, item: com.luogen.filehub.model.FileItem, siblings: List<com.luogen.filehub.model.FileItem>) {
    val file = File(item.path)
    if (!file.exists()) return
    // 搜索结果里可能混着文件夹：点文件夹必须进目录，不能当文件丢给系统
    if (file.isDirectory) {
        nav.push(Screen.Browse(file.absolutePath))
        return
    }
    val cat = com.luogen.filehub.data.TypeDetect.catOfFile(file, item.mime)
    when (cat) {
        com.luogen.filehub.model.Cat.IMAGE -> {
            val images = siblings.filter { com.luogen.filehub.data.TypeDetect.catOf(it.name, it.mime) == com.luogen.filehub.model.Cat.IMAGE }
            val list = if (images.any { it.path == item.path }) images.map { it.path } else listOf(item.path)
            val idx = list.indexOf(item.path).coerceAtLeast(0)
            nav.push(Screen.ImageView(list, idx))
        }
        com.luogen.filehub.model.Cat.VIDEO -> {
            val videos = siblings.filter { com.luogen.filehub.data.TypeDetect.catOf(it.name, it.mime) == com.luogen.filehub.model.Cat.VIDEO }
            val list = if (videos.any { it.path == item.path }) videos.map { it.path } else listOf(item.path)
            val idx = list.indexOf(item.path).coerceAtLeast(0)
            nav.push(Screen.VideoView(list, idx))
        }
        com.luogen.filehub.model.Cat.AUDIO -> {
            val audios = siblings.filter { com.luogen.filehub.data.TypeDetect.catOf(it.name, it.mime) == com.luogen.filehub.model.Cat.AUDIO }
            val list = if (audios.any { it.path == item.path }) audios.map { it.path } else listOf(item.path)
            val idx = list.indexOf(item.path).coerceAtLeast(0)
            nav.push(Screen.AudioView(list, idx))
        }
        com.luogen.filehub.model.Cat.ARCHIVE -> nav.push(Screen.ArchiveView(item.path))
        else -> {
            val ext = item.ext
            when {
                ext == "pdf" -> nav.push(Screen.PdfView(item.path))
                ext in com.luogen.filehub.data.TypeDetect.TEXT_EXT -> nav.push(Screen.TextView(item.path))
                ext == "apk" || ext == "xapk" -> com.luogen.filehub.data.FileOps.installApk(ctx, item.path)
                // 未知后缀（比如自己改的 tx2）：先嗅探内容，是文本就用内置查看器，不再甩给系统
                else -> if (com.luogen.filehub.data.TypeDetect.looksText(file)) {
                    nav.push(Screen.TextView(item.path))
                } else if (item.path.startsWith(com.luogen.filehub.data.ExternalOpen.dir(ctx).absolutePath + File.separator)) {
                    // 从别的 App 转进来的文件、应用内确实预览不了：转给别的应用打开，
                    // 并且把自己的入口排除掉，避免“选自己→再弹选择器→再选自己”的死循环
                    val handed = com.luogen.filehub.data.FileOps.openWithOther(ctx, item.path, item.mime)
                    if (!handed) {
                        android.widget.Toast.makeText(
                            ctx, "这个文件应用内打不开，也没有其他应用能打开它", android.widget.Toast.LENGTH_LONG
                        ).show()
                    }
                } else {
                    com.luogen.filehub.data.FileOps.openWith(ctx, item.path, item.mime)
                }
            }
        }
    }
}
