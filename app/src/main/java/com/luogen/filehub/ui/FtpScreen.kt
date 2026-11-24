package com.luogen.filehub.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Environment
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.luogen.filehub.data.FtpClientRecord
import com.luogen.filehub.data.FtpManager
import com.luogen.filehub.data.FtpSettings
import com.luogen.filehub.data.Prefs
import com.luogen.filehub.model.toFullTime
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FtpScreen(ctx: Context, nav: NavStack) {
    var cfg by remember { mutableStateOf(FtpManager.settings(ctx)) }
    var portText by remember { mutableStateOf(cfg.port.toString()) }
    var user by remember { mutableStateOf(cfg.user) }
    var pass by remember { mutableStateOf(cfg.pass) }
    var anon by remember { mutableStateOf(cfg.anonymous) }
    var readOnly by remember { mutableStateOf(cfg.readOnly) }
    var rootWhole by remember { mutableStateOf(cfg.root != Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).absolutePath) }
    var toast by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val running = FtpManager.running
    val address = FtpManager.address
    val clients = FtpManager.clients

    Scaffold(
        containerColor = BgDeep,
        topBar = {
            Column(Modifier.background(SurfaceDark)) {
                TopAppBar(
                    title = {
                        Column {
                            Text("FTP 无线传输", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = TextMain)
                            Text(if (running) "正在运行 · 同一 WiFi 下电脑可直接访问" else "未开启", fontSize = 11.sp, color = TextDim)
                        }
                    },
                    navigationIcon = { IconButton(onClick = { nav.pop() }) { Icon(Icons.Filled.ArrowBack, null, tint = TextMain) } },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = SurfaceDark)
                )
            }
        }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp)
        ) {
            // 状态卡
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(18)).background(SurfaceDark)
                    .border(1.dp, BorderSoft, RoundedCornerShape(18)).padding(16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Wifi, null, tint = if (running) GoodGreen else TextDim, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(if (running) "FTP 正在运行" else "FTP 未开启", fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = TextMain)
                }
                if (running) {
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(address, color = BrandBlue, fontSize = 15.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f), maxLines = 1)
                        IconButton(onClick = {
                            try {
                                val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                cm.setPrimaryClip(ClipData.newPlainText("ftp", address))
                                toast = "地址已复制"
                            } catch (_: Throwable) {}
                        }) { Icon(Icons.Filled.ContentCopy, null, tint = TextDim) }
                    }
                    Text(
                        "电脑资源管理器地址栏输入：${address}\n用户名：${if (anon) "anonymous（匿名）" else user}    密码：${if (anon) "任意" else pass}\n端口：${cfg.port}    根目录：${cfg.root}",
                        color = TextDim, fontSize = 11.sp
                    )
                } else {
                    Text("开启后通知栏会常驻显示地址；切后台、小窗都不会断开，只要不手动停止就一直能访问。", color = TextDim, fontSize = 11.sp)
                }
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = {
                        if (running) {
                            FtpManager.stop(ctx)
                            toast = "已停止 FTP"
                        } else {
                            val port = portText.toIntOrNull()
                            when {
                                port == null || port !in 1024..65535 -> toast = "端口要填 1024–65535 之间的数字"
                                !anon && user.isBlank() -> toast = "请填写用户名，或开启匿名访问"
                                !anon && pass.isBlank() -> toast = "请填写密码，或开启匿名访问"
                                else -> {
                                    val root = if (rootWhole) Environment.getExternalStorageDirectory().absolutePath
                                    else Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).absolutePath
                                    val s = FtpSettings(port, user.ifBlank { "luogen" }, pass, anon, readOnly, root)
                                    val err = FtpManager.start(ctx, s)
                                    cfg = s
                                    toast = err ?: "FTP 已开启，通知栏会显示地址"
                                }
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = if (running) DangerRed else BrandBlue)
                ) { Text(if (running) "停止 FTP" else "开启 FTP") }
            }

            Spacer(Modifier.height(14.dp))

            // 设置
            Text("设置", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = TextMain)
            Spacer(Modifier.height(8.dp))
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18)).background(SurfaceDark).padding(14.dp)) {
                OutlinedTextField(
                    value = portText, onValueChange = { portText = it.filter { c -> c.isDigit() }.take(5) },
                    label = { Text("端口（默认 2121）") }, singleLine = true,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(), enabled = !running
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = user, onValueChange = { user = it }, label = { Text("用户名") },
                    singleLine = true, modifier = Modifier.fillMaxWidth(), enabled = !running && !anon
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = pass, onValueChange = { pass = it }, label = { Text("密码") },
                    singleLine = true, visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(), enabled = !running && !anon
                )
                Spacer(Modifier.height(6.dp))
                SwitchRow("允许匿名访问（anonymous，不输密码）", anon, enabled = !running) { anon = it }
                SwitchRow("只读模式（电脑只能下载，不能改手机文件）", readOnly, enabled = !running) { readOnly = it }
                Spacer(Modifier.height(6.dp))
                Text("共享范围", fontSize = 12.sp, color = TextDim)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = rootWhole, onClick = { if (!running) rootWhole = true }, enabled = !running)
                    Text("整个内部存储", fontSize = 13.sp)
                    Spacer(Modifier.width(8.dp))
                    RadioButton(selected = !rootWhole, onClick = { if (!running) rootWhole = false }, enabled = !running)
                    Text("仅 Download 文件夹", fontSize = 13.sp)
                }
                Text("提示：手机和电脑要连同一个 WiFi；电脑防火墙如拦截，允许“专用网络”即可。", fontSize = 10.sp, color = TextDim)
            }

            Spacer(Modifier.height(14.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("连接过的设备", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = TextMain)
                Spacer(Modifier.weight(1f))
                if (clients.isNotEmpty()) {
                    Text("清除离线", fontSize = 12.sp, color = BrandBlue, modifier = Modifier.clickable { FtpManager.clearOffline() })
                }
            }
            Spacer(Modifier.height(8.dp))
            if (clients.isEmpty()) {
                Text("还没有设备连接。电脑连上后会显示 IP、连接时间、在线状态。", fontSize = 11.sp, color = TextDim)
            } else {
                clients.forEach { c -> ClientRow(c, onDelete = { FtpManager.deleteClient(c.id) }) }
            }
            Text(
                "说明：FTP 协议本身不携带设备型号，系统名称只能显示为 IP；在线状态和最后一次活动时间实时更新。",
                fontSize = 10.sp, color = TextDim, modifier = Modifier.padding(top = 8.dp)
            )
            Spacer(Modifier.height(30.dp))
        }
    }

    toast?.let {
        AlertDialog(
            onDismissRequest = { toast = null },
            title = { Text("提示") },
            text = { Text(it) },
            confirmButton = { TextButton(onClick = { toast = null }) { Text("知道了") } }
        )
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = 12.sp, color = if (enabled) TextMain else TextDim, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange, enabled = enabled)
    }
}

@Composable
private fun ClientRow(c: FtpClientRecord, onDelete: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(RoundedCornerShape(12)).background(SurfaceDark)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(8.dp).clip(RoundedCornerShape(50))
                .background(if (c.online) GoodGreen else TextDim)
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(c.ip, fontSize = 13.sp, color = TextMain, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                (if (c.online) "在线" else "离线") + " · 连接于 " + c.connectedAt.toFullTime() + " · 最后活动 " + c.lastSeen.toFullTime(),
                fontSize = 10.sp, color = TextDim, maxLines = 1, overflow = TextOverflow.Ellipsis
            )
        }
        if (!c.online) {
            IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, null, tint = TextDim, modifier = Modifier.size(18.dp)) }
        }
    }
}
