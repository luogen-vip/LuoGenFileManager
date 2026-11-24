package com.luogen.filehub.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.Drawable
import android.graphics.ImageDecoder
import android.media.ThumbnailUtils
import android.os.Build
import android.util.LruCache
import android.util.Size
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.luogen.filehub.data.TypeDetect
import com.luogen.filehub.model.Cat
import com.luogen.filehub.model.FileItem
import com.luogen.filehub.model.extOf
import com.luogen.filehub.model.nameNoExt
import com.luogen.filehub.model.toSizeText
import com.luogen.filehub.model.toRelativeTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

fun catIcon(cat: Cat): ImageVector = when (cat) {
    Cat.IMAGE -> Icons.Filled.Image
    Cat.VIDEO -> Icons.Filled.Movie
    Cat.AUDIO -> Icons.Filled.MusicNote
    Cat.DOC -> Icons.Filled.Description
    Cat.APP -> Icons.Filled.Android
    Cat.ARCHIVE -> Icons.Filled.Archive
    Cat.FAV -> Icons.Filled.Star
    Cat.DOWNLOAD -> Icons.Filled.Download
    Cat.TRASH -> Icons.Filled.Delete
    Cat.VAULT -> Icons.Filled.Lock
    Cat.CLEAN -> Icons.Filled.DeleteSweep
    Cat.FILE -> Icons.Filled.Folder
}

fun catColor(cat: Cat): Color = when (cat) {
    Cat.IMAGE -> Color(0xFF60A5FA)
    Cat.VIDEO -> Color(0xFFF472B6)
    Cat.AUDIO -> Color(0xFFA78BFA)
    Cat.DOC -> Color(0xFF34D399)
    Cat.APP -> Color(0xFFFB923C)
    Cat.ARCHIVE -> Color(0xFFFBBF24)
    Cat.FAV -> Color(0xFFFACC15)
    Cat.DOWNLOAD -> Color(0xFF38BDF8)
    Cat.TRASH -> Color(0xFFF87171)
    Cat.VAULT -> Color(0xFF22D3EE)
    Cat.CLEAN -> Color(0xFF94A3B8)
    Cat.FILE -> Color(0xFF93C5FD)
}

fun itemCat(item: FileItem): Cat {
    if (item.isDir) return Cat.FILE
    return TypeDetect.catOf(item.name, item.mime)
}

fun itemIcon(item: FileItem): ImageVector {
    if (item.isDir) return Icons.Filled.Folder
    return when (itemCat(item)) {
        Cat.IMAGE -> Icons.Filled.Image
        Cat.VIDEO -> Icons.Filled.Movie
        Cat.AUDIO -> Icons.Filled.MusicNote
        Cat.DOC -> Icons.Filled.Description
        Cat.ARCHIVE -> Icons.Filled.Archive
        Cat.APP -> Icons.Filled.Android
        else -> Icons.Filled.InsertDriveFile
    }
}

object Thumbs {
    private val cache = object : LruCache<String, ImageBitmap>(30) {}

    fun peek(key: String): ImageBitmap? = cache.get(key)

    suspend fun load(ctx: Context, path: String, isVideo: Boolean, req: Int = 320): ImageBitmap? {
        val key = "$req|$path"
        cache.get(key)?.let { return it }
        return withContext(Dispatchers.IO) {
            val bmp = try {
                if (isVideo) videoThumb(path, req) else imageThumb(ctx, path, req)
            } catch (_: Throwable) {
                null
            }
            bmp?.let {
                val ib = it.asImageBitmap()
                cache.put(key, ib)
                ib
            }
        }
    }

    private fun videoThumb(path: String, req: Int): Bitmap? {
        return try {
            if (Build.VERSION.SDK_INT >= 29) {
                ThumbnailUtils.createVideoThumbnail(File(path), Size(req, req), null)
            } else {
                @Suppress("DEPRECATION")
                ThumbnailUtils.createVideoThumbnail(path, ThumbnailUtils.MINI_KIND)
            }
        } catch (_: Throwable) {
            try {
                val mmr = android.media.MediaMetadataRetriever()
                mmr.setDataSource(path)
                val f = mmr.getFrameAtTime(0)
                mmr.release()
                f
            } catch (_: Throwable) {
                null
            }
        }
    }

    private fun imageThumb(ctx: Context, path: String, req: Int): Bitmap? {
        if (Build.VERSION.SDK_INT >= 28) {
            try {
                val src = ImageDecoder.createSource(File(path))
                val bmp = ImageDecoder.decodeBitmap(src) { decoder, info, _ ->
                    val w = info.size.width
                    val h = info.size.height
                    val scale = maxOf(1, minOf(w, h) / req)
                    decoder.setTargetSampleSize(scale)
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                }
                return bmp
            } catch (_: Throwable) {
            }
        }
        return try {
            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(path, opts)
            var sample = 1
            while (opts.outWidth / sample > req * 2 && opts.outHeight / sample > req * 2) sample *= 2
            BitmapFactory.decodeFile(path, BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = Bitmap.Config.RGB_565
            })
        } catch (_: Throwable) {
            null
        }
    }
}

object AppIcons {
    private val cache = object : LruCache<String, ImageBitmap>(60) {}

    suspend fun load(ctx: Context, item: FileItem, size: Int = 128): ImageBitmap? {
        val key = (item.packageName.ifEmpty { item.path }) + "@" + size
        cache.get(key)?.let { return it }
        return withContext(Dispatchers.IO) {
            val d: Drawable? = try {
                if (item.path.endsWith(".apk", true) && File(item.path).exists()) {
                    val info = ctx.packageManager.getPackageArchiveInfo(item.path, 0)
                    val ai = info?.applicationInfo
                    if (ai != null) {
                        ai.sourceDir = item.path
                        ai.publicSourceDir = item.path
                        ai.loadIcon(ctx.packageManager)
                    } else null
                } else {
                    ctx.packageManager.getApplicationIcon(item.packageName)
                }
            } catch (_: Throwable) {
                null
            }
            d?.toBitmap(size, size)?.let {
                val ib = it.asImageBitmap()
                cache.put(key, ib)
                ib
            }
        }
    }
}

@Composable
fun ThumbBox(
    modifier: Modifier,
    item: FileItem,
    corner: Int = 10,
    contentScale: androidx.compose.ui.layout.ContentScale = androidx.compose.ui.layout.ContentScale.Crop
) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    var bmp by remember(item.path) { mutableStateOf(Thumbs.peek("320|${item.path}")) }
    LaunchedEffect(item.path) {
        if (bmp == null) {
            bmp = if (itemCat(item) == Cat.APP) AppIcons.load(ctx, item) else Thumbs.load(ctx, item.path, itemCat(item) == Cat.VIDEO)
        }
    }
    Box(
        modifier
            .clip(RoundedCornerShape(corner.dp))
            .background(SurfaceDark2),
        contentAlignment = Alignment.Center
    ) {
        val b = bmp
        if (b != null) {
            Image(bitmap = b, contentDescription = item.name, modifier = Modifier.fillMaxSize(), contentScale = contentScale)
        } else {
            Icon(
                itemIcon(item),
                contentDescription = null,
                tint = TextDim,
                modifier = Modifier.size(26.dp)
            )
        }
    }
}

@Composable
fun FileRow(
    item: FileItem,
    selected: Boolean,
    selectionMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    trailing: @Composable (() -> Unit)? = null
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (selectionMode) {
            androidx.compose.material3.Checkbox(checked = selected, onCheckedChange = { onClick() })
            Spacer(Modifier.width(6.dp))
        }
        Box(
            Modifier.size(44.dp),
            contentAlignment = Alignment.Center
        ) {
            if (itemCat(item) == Cat.IMAGE || itemCat(item) == Cat.VIDEO || itemCat(item) == Cat.APP) {
                ThumbBox(Modifier.size(44.dp), item)
            } else {
                Box(
                    Modifier
                        .size(44.dp)
                        .background(catColor(itemCat(item)).copy(alpha = 0.14f), RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(itemIcon(item), null, tint = catColor(itemCat(item)), modifier = Modifier.size(24.dp))
                }
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                item.label.ifEmpty { item.name },
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(3.dp))
            Text(
                buildString {
                    append(item.size.toSizeText())
                    if (!item.isDir && item.ext.isNotEmpty()) append(" · ").append(item.ext.uppercase())
                    append(" · ").append(item.modified.toRelativeTime())
                },
                color = TextDim,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (trailing != null) trailing()
    }
}

@Composable
fun EmptyView(text: String) {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 60.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(Icons.Filled.Inbox, null, tint = TextDim, modifier = Modifier.size(46.dp))
        Spacer(Modifier.height(10.dp))
        Text(text, color = TextDim, fontSize = 14.sp)
    }
}

@Composable
fun DayHeader(title: String, count: Int) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(3.dp, 14.dp).background(BrandBlue, RoundedCornerShape(2.dp)))
        Spacer(Modifier.width(8.dp))
        Text(title, color = MaterialTheme.colorScheme.onBackground, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.width(8.dp))
        Text("$count 项", color = TextDim, fontSize = 12.sp)
    }
}

fun apkLabelOf(ctx: Context, path: String): String {
    return try {
        val info = ctx.packageManager.getPackageArchiveInfo(path, 0)
        info?.applicationInfo?.let { ai ->
            ai.sourceDir = path
            ai.publicSourceDir = path
            ctx.packageManager.getApplicationLabel(ai).toString()
        } ?: File(path).name
    } catch (_: Throwable) {
        File(path).name
    }
}

fun fileBaseNoExt(name: String): String = nameNoExt(name)
