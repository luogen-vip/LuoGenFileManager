package com.luogen.filehub.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.luogen.filehub.data.FileOps
import com.luogen.filehub.data.Prefs
import com.luogen.filehub.model.FileItem
import com.luogen.filehub.model.extOf
import com.luogen.filehub.model.nameNoExt

@Composable
fun RenameDialog(
    initial: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    val base = nameNoExt(initial)
    var value by remember(initial) {
        mutableStateOf(TextFieldValue(initial, selection = TextRange(0, base.length)))
    }
    var muteWarn by remember { mutableStateOf(Prefs.extWarnMuted()) }
    // 只比较“最后一个点”后面的真实扩展名；只改主体、删掉前面文件名里的点，不算改扩展名
    val oldExt = extOf(initial)
    val newExt = extOf(value.text.trim())
    val extChanged = newExt != oldExt

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(22.dp),
        title = { Text("重命名") },
        text = {
            Column {
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    label = { Text("文件名（含扩展名）") },
                    modifier = Modifier.fillMaxWidth()
                )
                if (extChanged) {
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = muteWarn, onCheckedChange = { muteWarn = it })
                        Text("以后不再提示", fontSize = 12.sp, color = TextDim)
                    }
                    Text(
                        "⚠️ 你修改了扩展名（${extOf(initial).ifEmpty { "无" }} → ${extOf(value.text).ifEmpty { "无" }}）。" +
                            "扩展名与真实内容不一致时，文件可能打不开或无法播放，可随时用「恢复默认」还原。",
                        color = WarnAmber, fontSize = 12.sp
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (muteWarn != Prefs.extWarnMuted() && muteWarn) Prefs.muteExtWarn()
                onConfirm(value.text)
            }) { Text("确定") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

@Composable
fun BatchRenameDialog(
    items: List<FileItem>,
    onDismiss: () -> Unit,
    onConfirm: (List<Pair<FileItem, String>>) -> Unit
) {
    var prefix by remember { mutableStateOf(nameNoExt(items.firstOrNull()?.name ?: "文件").trimEnd('0', '1', '2', '3', '4', '5', '6', '7', '8', '9', '_').ifEmpty { "文件" }) }
    var start by remember { mutableStateOf("1") }
    var pad by remember { mutableStateOf("2") }

    fun buildName(index: Int, ext: String): String {
        val n = (start.toIntOrNull() ?: 1) + index
        val p = pad.toIntOrNull()?.coerceIn(1, 5) ?: 2
        val num = n.toString().padStart(p, '0')
        return "$prefix$num" + if (ext.isNotEmpty()) ".$ext" else ""
    }

    val previews = items.take(3).mapIndexed { i, it -> buildName(i, extOf(it.name)) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("批量重命名（${items.size} 项）") },
        text = {
            Column {
                OutlinedTextField(value = prefix, onValueChange = { prefix = it }, singleLine = true, label = { Text("前缀") }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                Row {
                    OutlinedTextField(value = start, onValueChange = { start = it.filter { c -> c.isDigit() } }, singleLine = true, label = { Text("起始序号") }, modifier = Modifier.weight(1f))
                    Spacer(Modifier.width(8.dp))
                    OutlinedTextField(value = pad, onValueChange = { pad = it.filter { c -> c.isDigit() }.take(1) }, singleLine = true, label = { Text("位数") }, modifier = Modifier.weight(1f))
                }
                Spacer(Modifier.height(10.dp))
                Text("预览：", color = TextDim, fontSize = 12.sp)
                previews.forEach { p -> Text(p, fontSize = 13.sp, color = MaterialTheme.colorScheme.onBackground) }
                if (items.size > 3) Text("… 共 ${items.size} 个", fontSize = 12.sp, color = TextDim)
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val pairs = items.mapIndexed { i, it -> it to buildName(i, extOf(it.name)) }
                    onConfirm(pairs)
                },
                enabled = prefix.isNotEmpty()
            ) { Text("按顺序重命名") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

@Composable
fun DestPickerDialog(
    title: String,
    startDir: String,
    volumes: List<com.luogen.filehub.model.VolumeInfo> = emptyList(),
    onDismiss: () -> Unit,
    onPick: (String) -> Unit
) {
    var current by remember { mutableStateOf(java.io.File(startDir)) }
    var dirs by remember { mutableStateOf(listOf<java.io.File>()) }
    var newFolder by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }

    LaunchedEffect(current) {
        dirs = (current.listFiles() ?: emptyArray())
            .filter { it.isDirectory && !it.name.startsWith(".") }
            .sortedBy { it.name.lowercase() }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(Modifier.heightIn(max = 420.dp)) {
                if (volumes.isNotEmpty()) {
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        volumes.forEach { v ->
                            val active = current.absolutePath.startsWith(v.path)
                            androidx.compose.material3.AssistChip(
                                onClick = { current = java.io.File(v.path) },
                                label = { Text(if (v.removable) (v.name + " (U盘)") else v.name, fontSize = 12.sp) },
                                shape = RoundedCornerShape(20),
                                colors = androidx.compose.material3.AssistChipDefaults.assistChipColors(
                                    containerColor = if (active) BrandBlue.copy(alpha = 0.12f) else SurfaceDark2
                                )
                            )
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                }
                Text(current.absolutePath, color = TextDim, fontSize = 11.sp, maxLines = 2)
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = {
                        current.parentFile?.let { current = it }
                    }, enabled = current.parentFile != null) { Text("上一层") }
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = { newFolder = !newFolder }) { Text("新建文件夹") }
                }
                if (newFolder) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = newName, onValueChange = { newName = it },
                            singleLine = true, modifier = Modifier.weight(1f), label = { Text("文件夹名") }
                        )
                        TextButton(onClick = {
                            val created = FileOps.createFolder(current, newName)
                            if (created != null) {
                                newName = ""
                                newFolder = false
                                dirs = dirs + created
                                current = created
                            }
                        }) { Text("创建") }
                    }
                }
                Spacer(Modifier.height(6.dp))
                Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
                    dirs.forEach { d ->
                        Row(
                            Modifier.fillMaxWidth()
                                .clickable { current = d }
                                .padding(vertical = 10.dp, horizontal = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Filled.Folder, null, tint = BrandBlue, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(d.name, fontSize = 14.sp, maxLines = 1)
                        }
                    }
                    if (dirs.isEmpty()) Text("（没有子文件夹）", color = TextDim, fontSize = 12.sp)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onPick(current.absolutePath) }) { Text("选到这里") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

@Composable
fun ConfirmDialog(
    title: String,
    text: String,
    confirmText: String = "确定",
    dismissText: String = "取消",
    danger: Boolean = false,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(22.dp),
        title = { Text(title) },
        text = { Text(text, fontSize = 14.sp) },
        confirmButton = {
            TextButton(onClick = { onConfirm(); onDismiss() }) {
                Text(confirmText, color = if (danger) DangerRed else BrandBlue)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(dismissText) } }
    )
}

/** 通用单选对话框（例如压缩格式） */
@Composable
fun SelectDialog(
    title: String,
    message: String,
    options: List<Pair<String, String>>,
    onDismiss: () -> Unit,
    onPick: (String) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(22.dp),
        title = { Text(title) },
        text = {
            Column {
                Text(message, color = TextDim, fontSize = 13.sp)
                Spacer(Modifier.height(10.dp))
                options.forEach { (label, value) ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onPick(value) }
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(label, fontSize = 14.sp)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

@Composable
fun ProgressDialog(title: String, progress: Float?, message: String) {
    AlertDialog(
        onDismissRequest = {},
        title = { Text(title) },
        text = {
            Column {
                if (progress != null && progress >= 0f) {
                    LinearProgressIndicator(
                        progress = { progress.coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth(),
                        color = BrandBlue,
                        trackColor = SurfaceDark2,
                        drawStopIndicator = {}
                    )
                    Spacer(Modifier.height(6.dp))
                    Text("${(progress * 100).toInt()}%", fontSize = 12.sp, color = TextDim)
                } else {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth(),
                        color = BrandBlue,
                        trackColor = SurfaceDark2
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(message, fontSize = 12.sp, color = TextDim, maxLines = 2)
            }
        },
        confirmButton = {}
    )
}

@Composable
fun InfoDialog(
    title: String,
    rows: List<Pair<String, String>>,
    onCopy: ((String) -> Unit)? = null,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontSize = 16.sp) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                rows.forEach { (k, v) ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.Top) {
                        Text(k, color = TextDim, fontSize = 12.sp, modifier = Modifier.width(84.dp))
                        Text(v, fontSize = 13.sp, modifier = Modifier.weight(1f))
                        if (onCopy != null && k.contains("路径")) {
                            Text("复制", color = BrandBlue, fontSize = 12.sp,
                                modifier = Modifier.clickable { onCopy(v) }.padding(start = 8.dp))
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("关闭") } }
    )
}
