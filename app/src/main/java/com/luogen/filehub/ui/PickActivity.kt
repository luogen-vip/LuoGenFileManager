package com.luogen.filehub.ui

import android.content.ClipData
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.luogen.filehub.data.TypeDetect
import com.luogen.filehub.model.toSizeText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 别的 App 点「选择文件 / 上传」时，选到「罗根文件管家」后进入的就是这里：
 * 用我们自己的文件界面去挑，选完把文件交给对方。
 *
 * 单选用 FileProvider 的 content:// 地址，多选用 ClipData；只给临时读权限，用完即失效。
 */
class PickActivity : androidx.activity.ComponentActivity() {

    private var allowMultiple = false
    private var mimeTypes: List<String> = listOf("*/*")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        allowMultiple = intent?.getBooleanExtra(Intent.EXTRA_ALLOW_MULTIPLE, false) ?: false
        mimeTypes = collectTypes(intent)
        setContent {
            MaterialTheme {
                PickScreen(
                    mimeTypes = mimeTypes,
                    multiple = allowMultiple,
                    onCancel = { cancel() },
                    onConfirm = { files -> done(files) }
                )
            }
        }
    }

    private fun collectTypes(intent: Intent?): List<String> {
        val out = ArrayList<String>()
        intent?.type?.let { if (it.isNotBlank()) out.add(it) }
        intent?.getStringArrayExtra(Intent.EXTRA_MIME_TYPES)?.forEach { if (it.isNotBlank()) out.add(it) }
        if (out.isEmpty()) out.add("*/*")
        return out
    }

    private fun cancel() {
        setResult(RESULT_CANCELED)
        finish()
    }

    private fun done(files: List<File>) {
        if (files.isEmpty()) return
        try {
            val uris = files.mapNotNull { uriOf(it) }
            if (uris.isEmpty()) {
                cancel()
                return
            }
            val res = Intent()
            if (uris.size == 1) {
                res.data = uris[0]
            } else {
                val clip = ClipData.newUri(contentResolver, "罗根文件管家", uris[0])
                for (i in 1 until uris.size) clip.addItem(ClipData.Item(uris[i]))
                res.clipData = clip
            }
            res.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            setResult(RESULT_OK, res)
        } catch (_: Throwable) {
            setResult(RESULT_CANCELED)
        }
        finish()
    }

    /** 主存储用 FileProvider；SD 卡等路径用我们自己的文档提供方地址，两种都是 content:// */
    private fun uriOf(f: File): Uri? {
        val primary = Environment.getExternalStorageDirectory().absolutePath
        return try {
            if (f.absolutePath.startsWith(primary)) {
                FileProvider.getUriForFile(this, packageName + ".fileprovider", f)
            } else {
                com.luogen.filehub.provider.FileHubProvider.uriFor(this, f)
            }
        } catch (_: Throwable) {
            try {
                com.luogen.filehub.provider.FileHubProvider.uriFor(this, f)
            } catch (_: Throwable) {
                null
            }
        }
    }
}

@Composable
private fun PickScreen(
    mimeTypes: List<String>,
    multiple: Boolean,
    onCancel: () -> Unit,
    onConfirm: (List<File>) -> Unit
) {
    val root = remember { Environment.getExternalStorageDirectory() }
    var dir by remember { mutableStateOf(root) }
    var files by remember { mutableStateOf<List<File>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var selected by remember { mutableStateOf<List<File>>(emptyList()) }
    val acceptAll = mimeTypes.any { it == "*/*" }

    LaunchedEffect(dir, mimeTypes) {
        loading = true
        files = withContext(Dispatchers.IO) {
            val all = try { dir.listFiles()?.toList() ?: emptyList() } catch (_: Throwable) { emptyList() }
            all.filter { it.name.startsWith(".").not() }
                .filter { it.isDirectory || acceptAll || matches(it, mimeTypes) }
                .sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
        }
        loading = false
    }

    BackHandler {
        if (dir.absolutePath != root.absolutePath && dir.parentFile != null) {
            dir = dir.parentFile!!
            selected = emptyList()
        } else {
            onCancel()
        }
    }

    Surface(Modifier.fillMaxSize(), color = BgDeep) {
        Column(Modifier.fillMaxSize()) {
            // 顶部：取消 / 当前目录 / 完成
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = { onCancel() }) { Text("取消", color = TextDim) }
                Column(Modifier.weight(1f)) {
                    Text(
                        if (dir.absolutePath == root.absolutePath) "手机存储" else dir.name.ifEmpty { "手机存储" },
                        fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextMain,
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        if (selected.isEmpty()) "选一个文件" else "已选 ${selected.size} 个",
                        fontSize = 11.sp, color = TextDim
                    )
                }
                TextButton(
                    onClick = { onConfirm(selected) },
                    enabled = selected.isNotEmpty()
                ) {
                    Text(
                        if (multiple) "完成" else "选择",
                        color = if (selected.isEmpty()) TextDim else BrandBlue,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // 路径：可点回上层
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (dir.absolutePath != root.absolutePath && dir.parentFile != null) {
                    IconButton(onClick = { dir = dir.parentFile!!; selected = emptyList() }, modifier = Modifier.size(30.dp)) {
                        Icon(Icons.Filled.ArrowBack, "上一级", tint = TextDim, modifier = Modifier.size(18.dp))
                    }
                }
                Text(
                    dir.absolutePath.removePrefix(root.absolutePath).ifEmpty { "/" },
                    fontSize = 11.sp, color = TextDim,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
            }

            Box(Modifier.fillMaxSize()) {
                when {
                    loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = BrandBlue)
                    }
                    files.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("这个文件夹里没有可选择的文件", color = TextDim, fontSize = 13.sp)
                    }
                    else -> LazyColumn(Modifier.fillMaxSize()) {
                        items(count = files.size, key = { files[it].absolutePath }) { i ->
                            val f = files[i]
                            val isSel = selected.any { it.absolutePath == f.absolutePath }
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .background(if (isSel) BrandBlue.copy(alpha = 0.12f) else androidx.compose.ui.graphics.Color.Transparent)
                                    .clickable {
                                        if (f.isDirectory) {
                                            dir = f
                                            selected = emptyList()
                                        } else if (multiple) {
                                            selected = if (isSel) selected.filter { it.absolutePath != f.absolutePath }
                                            else selected + f
                                        } else {
                                            selected = listOf(f)
                                        }
                                    }
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    if (f.isDirectory) Icons.Filled.Folder else Icons.Filled.InsertDriveFile,
                                    null,
                                    tint = if (f.isDirectory) WarnAmber else BrandBlue,
                                    modifier = Modifier.size(22.dp)
                                )
                                Spacer(Modifier.size(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(f.name, fontSize = 14.sp, color = TextMain, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(
                                        if (f.isDirectory) "文件夹" else f.length().toSizeText(),
                                        fontSize = 11.sp, color = TextDim
                                    )
                                }
                                if (isSel) {
                                    Icon(Icons.Filled.Check, "已选", tint = BrandBlue, modifier = Modifier.size(20.dp))
                                }
                            }
                        }
                        item { Spacer(Modifier.height(24.dp)) }
                    }
                }
            }
        }
    }
}

private fun matches(f: File, types: List<String>): Boolean {
    val mime = TypeDetect.mimeOf(f.name)
    return types.any { t ->
        when {
            t == "*/*" -> true
            t.endsWith("/*") -> mime.startsWith(t.removeSuffix("*"))
            else -> mime == t
        }
    }
}
