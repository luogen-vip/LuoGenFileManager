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

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.getBooleanExtra("open_audio", false)) AppState.audioOpenRequested = true
        if (intent.getBooleanExtra("open_ftp", false)) AppState.ftpOpenRequested = true
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
    }

    if (!granted) {
        PermissionScreen(
            onRequest = {
                if (Build.VERSION.SDK_INT >= 30) {
                    try {
                        ctx.startActivity(
                            Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:" + ctx.packageName))
                        )
                    } catch (_: Throwable) {
                        ctx.startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
                    }
                } else {
                    permLauncher.launch(
                        arrayOf(
                            Manifest.permission.READ_EXTERNAL_STORAGE,
                            Manifest.permission.WRITE_EXTERNAL_STORAGE
                        )
                    )
                }
            },
            onRecheck = { granted = hasAllFilesAccess(ctx) }
        )
    } else {
        LaunchedEffect(AppState.ftpOpenRequested) {
            if (AppState.ftpOpenRequested) {
                AppState.ftpOpenRequested = false
                try { nav.push(Screen.Ftp) } catch (_: Throwable) {}
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
}

sealed class Screen {
    object Home : Screen()
    data class CatList(val cat: com.luogen.filehub.model.Cat) : Screen()
    data class Browse(val dir: String) : Screen()
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

/** 统一的“打开文件”入口：按类型跳对应查看器 */
fun openItem(ctx: Context, nav: NavStack, item: com.luogen.filehub.model.FileItem, siblings: List<com.luogen.filehub.model.FileItem>) {
    val file = File(item.path)
    if (!file.exists()) return
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
                } else {
                    com.luogen.filehub.data.FileOps.openWith(ctx, item.path, item.mime)
                }
            }
        }
    }
}
