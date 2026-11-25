package com.luogen.filehub.ui

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.luogen.filehub.data.FileRepo
import com.luogen.filehub.model.Cat

@Composable
fun AppNav(ctx: Context, nav: NavStack) {
    val repo = remember { FileRepo(ctx) }

    BackHandler(enabled = nav.stack.size > 1) { nav.pop() }

    when (val s = nav.current) {
        is Screen.Home -> HomeScreen(ctx, nav, repo)

        is Screen.CatList -> {
            // 每次真正进入“我的文件”都是一次新的根浏览；查看器返回时仍复用同一个 key
            val rootKey = remember(s) { "catfile:" + System.nanoTime() }
            when (s.cat) {
                Cat.CLEAN -> CleanupScreen(ctx, nav, repo)
                Cat.VAULT -> VaultScreen(ctx, nav, repo)
                Cat.FILE -> BrowseScreen(
                    ctx, nav, repo,
                    android.os.Environment.getExternalStorageDirectory().absolutePath,
                    "browse:" + rootKey
                )
                else -> FileListScreen(ctx, nav, repo, s.cat)
            }
        }

        is Screen.Browse -> BrowseScreen(ctx, nav, repo, s.dir, "browse:" + s.id)
        is Screen.ImageView -> ImageViewerScreen(nav, s.paths, s.start)
        is Screen.VideoView -> VideoViewerScreen(nav, s.paths, s.start)
        is Screen.AudioView -> AudioViewerScreen(nav, s.paths, s.start)
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
