package com.luogen.filehub.ui

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import com.luogen.filehub.data.FileRepo
import com.luogen.filehub.data.Prefs
import com.luogen.filehub.model.Cat

@Composable
fun AppNav(ctx: Context, nav: NavStack) {
    val repo = remember { FileRepo(ctx) }

    // 每次导航变化立刻落盘：被后台清理后重新打开仍回到这一页/这一层
    LaunchedEffect(nav.current) {
        // 但「从外部 App 打开」产生的查看页不落盘：它指向缓存副本，过期清理后恢复出来只会是坏页面
        if (!refsExternalCache(ctx, nav.current)) {
            try { Prefs.saveNavRoutes(nav.routes()) } catch (_: Throwable) {}
        }
    }

    BackHandler(enabled = nav.stack.size > 1) { nav.pop() }

    when (val s = nav.current) {
        is Screen.Home -> HomeScreen(ctx, nav, repo)

        is Screen.CatList -> when (s.cat) {
            Cat.CLEAN -> CleanupScreen(ctx, nav, repo)
            Cat.VAULT -> VaultScreen(ctx, nav, repo)
            Cat.FILE -> BrowseScreen(
                ctx, nav, repo,
                android.os.Environment.getExternalStorageDirectory().absolutePath,
                "browse:cat:" + s.id
            )
            else -> FileListScreen(ctx, nav, repo, s.cat)
        }

        is Screen.Browse -> BrowseScreen(ctx, nav, repo, s.dir, "browse:" + s.id)
        is Screen.ImageView -> ImageViewerScreen(nav, s.paths, s.start)
        is Screen.VideoView -> VideoViewerScreen(nav, s.paths, s.start)
        is Screen.AudioView -> AudioViewerScreen(nav, s.paths, s.start)
        is Screen.ExternalVideo -> ExternalVideoScreen(nav, s.uri, s.name)
        is Screen.TextView -> TextViewerScreen(nav, s.path)
        is Screen.PdfView -> PdfViewerScreen(nav, s.path)
        is Screen.ArchiveView -> ArchiveViewerScreen(ctx, nav, s.path)
        is Screen.GlobalSearch -> GlobalSearchScreen(ctx, nav, repo, s.query)
        is Screen.CleanCat -> CleanCatScreen(ctx, nav, repo, s.cat)
        is Screen.Vault -> VaultScreen(ctx, nav, repo)
        is Screen.Ftp -> FtpScreen(ctx, nav)
        is Screen.VaultViewer -> ArchiveViewerScreen(ctx, nav, s.cachedPath)
    }
}

/** 这个页面的内容是不是来自「外部打开」的缓存副本 */
private fun refsExternalCache(ctx: Context, s: Screen): Boolean {
    val dir = com.luogen.filehub.data.ExternalOpen.dir(ctx).absolutePath + java.io.File.separator
    if (s is Screen.ExternalVideo) return true
    val p = when (s) {
        is Screen.ImageView -> s.paths.firstOrNull()
        is Screen.VideoView -> s.paths.firstOrNull()
        is Screen.AudioView -> s.paths.firstOrNull()
        is Screen.TextView -> s.path
        is Screen.PdfView -> s.path
        is Screen.ArchiveView -> s.path
        else -> null
    }
    return p != null && p.startsWith(dir)
}
