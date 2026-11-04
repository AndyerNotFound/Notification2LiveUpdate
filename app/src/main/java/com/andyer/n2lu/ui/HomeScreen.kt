package com.andyer.n2lu.ui

import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.andyer.n2lu.data.ChipStyle
import com.andyer.n2lu.data.EventLog
import com.andyer.n2lu.data.LogEvent
import com.andyer.n2lu.data.Prefs
import com.andyer.n2lu.data.PrefsBus
import com.andyer.n2lu.data.TriggerMode
import com.andyer.n2lu.service.DemoPoster
import com.andyer.n2lu.service.PostResult
import com.andyer.n2lu.service.Reposter
import com.andyer.n2lu.util.StatusSnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val clockFormat = DateTimeFormatter.ofPattern("HH:mm:ss")

@Composable
fun HomeScreen(
    status: StatusSnapshot,
    prefs: Prefs,
    events: List<LogEvent>,
    onEnabledChanged: (Boolean) -> Unit,
    onOpenAccess: () -> Unit,
    onOpenPromotion: () -> Unit,
    onRequestNotifications: () -> Unit,
    onOpenApps: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val revision by PrefsBus.revision.collectAsState()
    var testMessage by remember { mutableStateOf<String?>(null) }
    val selectedCount = remember(revision) { prefs.allowedPackages.size }
    val versionName = remember {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull() ?: "0.1.0"
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(top = 10.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        HeroCard(
            enabled = prefs.enabled,
            hasAccess = status.notificationAccess,
            hasNotifications = status.notificationsEnabled,
            onEnabledChange = onEnabledChanged,
        )
        SystemCard(
            status = status,
            onOpenAccess = onOpenAccess,
            onOpenPromotion = onOpenPromotion,
            onRequestNotifications = onRequestNotifications,
        )
        DiagnosticsCard(status = status, versionName = versionName)
        RulesCard(prefs = prefs, selectedCount = selectedCount, onOpenApps = onOpenApps)
        AdvancedCard(prefs = prefs)
        TestCard(
            message = testMessage,
            onTest = {
                val result = DemoPoster.start(context)
                testMessage = when (result) {
                    is PostResult.Posted -> if (result.promotable) {
                        "已发送，通知具备提升特征，胶囊文字：${result.chip}"
                    } else {
                        "已发送，但系统判定不满足实时更新条件"
                    }
                    is PostResult.Failed -> "发送失败：${result.reason}"
                    else -> "内容未变化，已跳过"
                }
                scope.launch {
                    delay(1800)
                    val promoted = withContext(Dispatchers.IO) {
                        Reposter.isPromoted(context, DemoPoster.DEMO_KEY)
                    }
                    testMessage = when (promoted) {
                        true -> "系统已提升：状态栏应出现胶囊，通知栏里会显示实时更新卡片。"
                        false -> "通知已发布，但系统没有提升。请对照上方诊断信息检查权限与资格。"
                        null -> "通知没有出现在活动列表中，可能被系统拦截。"
                    }
                }
            },
        )
        EventsCard(
            events = events,
            onClear = { EventLog.clear(context) },
        )
        Footer(versionName = versionName)
    }
}

@Composable
private fun HeroCard(
    enabled: Boolean,
    hasAccess: Boolean,
    hasNotifications: Boolean,
    onEnabledChange: (Boolean) -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(26.dp))
            .background(
                Brush.linearGradient(
                    listOf(Color(0xFF0F766E), Color(0xFF0EA5E9), Color(0xFF4F46E5))
                )
            )
            .padding(20.dp)
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "NOTIFICATION 2",
                        color = Color.White.copy(alpha = 0.75f),
                        fontSize = 11.sp,
                        letterSpacing = 2.sp,
                    )
                    Text(
                        text = "Live Update",
                        color = Color.White,
                        fontSize = 30.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Switch(
                    checked = enabled,
                    onCheckedChange = onEnabledChange,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color(0xFF0F766E),
                        checkedTrackColor = Color.White,
                        checkedBorderColor = Color.Transparent,
                        uncheckedThumbColor = Color.White,
                        uncheckedTrackColor = Color.Black.copy(alpha = 0.18f),
                        uncheckedBorderColor = Color.White.copy(alpha = 0.5f),
                    ),
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = when {
                    !hasAccess -> "第一步：授予通知使用权，否则读不到其他应用的通知"
                    !hasNotifications -> "第二步：允许通知权限，否则转换结果不会显示"
                    enabled -> "正在监听横幅通知，并转换为实时更新"
                    else -> "转换已关闭，点击右上角开关启用"
                },
                color = Color.White.copy(alpha = 0.85f),
                fontSize = 13.sp,
            )
            Spacer(Modifier.height(16.dp))
            LivePreview()
        }
    }
}

@Composable
private fun LivePreview() {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = Color.White.copy(alpha = 0.14f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.22f)),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(RoundedCornerShape(9.dp))
                        .background(Color.White.copy(alpha = 0.92f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Notifications,
                        contentDescription = null,
                        tint = Color(0xFF0F766E),
                        modifier = Modifier.size(17.dp),
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "外卖还有 3 分钟",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        text = "骑手正在路上",
                        color = Color.White.copy(alpha = 0.75f),
                        fontSize = 12.sp,
                    )
                }
                Surface(
                    shape = RoundedCornerShape(50),
                    color = Color.White.copy(alpha = 0.22f),
                ) {
                    Text(
                        text = "3 分钟",
                        color = Color.White,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            LinearProgressIndicator(
                progress = { 0.62f },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(5.dp)
                    .clip(RoundedCornerShape(50)),
                color = Color.White,
                trackColor = Color.White.copy(alpha = 0.25f),
            )
        }
    }
}

@Composable
private fun SystemCard(
    status: StatusSnapshot,
    onOpenAccess: () -> Unit,
    onOpenPromotion: () -> Unit,
    onRequestNotifications: () -> Unit,
) {
    SectionCard {
        SectionTitle("系统状态", "实时更新需要通知使用权，并且系统为 Android 16 及以上")
        HorizontalDivider(
            modifier = Modifier.padding(vertical = 10.dp),
            color = MaterialTheme.colorScheme.outlineVariant,
        )
        StatusRow(
            title = "通知使用权",
            subtitle = if (status.notificationAccess) {
                "已授权，可以读取其他应用的通知"
            } else {
                "未授权，无法读取横幅通知"
            },
            ok = status.notificationAccess,
            actionText = if (status.notificationAccess) null else "去授权",
            onAction = onOpenAccess,
        )
        StatusRow(
            title = "通知权限",
            subtitle = if (status.notificationsEnabled) {
                "已允许发送通知"
            } else {
                "未允许，转换后的通知不会显示"
            },
            ok = status.notificationsEnabled,
            actionText = if (status.notificationsEnabled) null else "允许",
            onAction = onRequestNotifications,
        )
        if (status.promotionSupported) {
            val modeLabel = if (status.promotionOptIn) "QPR1 请求模式" else "旧版彩色模式"
            StatusRow(
                title = "实时更新资格",
                subtitle = if (status.promotionAllowed) {
                    "系统允许本应用使用实时更新（$modeLabel）"
                } else {
                    "系统尚未允许，点击前往系统设置开启（$modeLabel）"
                },
                ok = status.promotionAllowed,
                actionText = if (status.promotionAllowed) null else "去开启",
                onAction = onOpenPromotion,
            )
        } else {
            StatusRow(
                title = "实时更新资格",
                subtitle = "当前 Android ${status.sdkInt}，实时更新需要 Android 16 (API 36)",
                ok = false,
            )
        }
    }
}

@Composable
private fun DiagnosticsCard(status: StatusSnapshot, versionName: String) {
    SectionCard {
        SectionTitle("诊断信息", "没有出现在实时更新名单时，先看这里")
        Spacer(Modifier.height(8.dp))
        DiagText("应用版本", versionName)
        DiagText("系统", "${Build.VERSION.RELEASE} · ${status.sdkLabel} · ${Build.DISPLAY}")
        DiagFlag("提升权限声明", status.promotionPermissionDeclared)
        DiagFlag("提升权限授予", status.promotionPermissionGranted)
        DiagFlag("系统允许提升", status.promotionAllowed)
        DiagFlag("提升设置页", status.promotionSettingsAvailable)
        DiagText(
            "判定模式",
            when {
                !status.promotionSupported -> "不支持（需要 Android 16）"
                status.promotionOptIn -> "QPR1 请求模式"
                else -> "旧版彩色模式"
            },
        )
        if (!status.promotionPermissionDeclared) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = "安装包没有请求 POST_PROMOTED_NOTIFICATIONS，系统设置里不会出现本应用的实时更新开关。0.3.0 起才包含该权限。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Composable
private fun DiagText(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(96.dp),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun DiagFlag(label: String, value: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(96.dp),
        )
        Text(
            text = if (value) "✓ 是" else "✗ 否",
            style = MaterialTheme.typography.bodySmall,
            color = if (value) Color(0xFF10B981) else MaterialTheme.colorScheme.error,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun RulesCard(prefs: Prefs, selectedCount: Int, onOpenApps: () -> Unit) {
    SectionCard {
        SectionTitle("转换规则", "选择哪些通知需要被转换")
        Spacer(Modifier.height(12.dp))
        Text("触发时机", style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ChoiceChip("仅横幅", prefs.triggerMode == TriggerMode.BANNER) {
                prefs.triggerMode = TriggerMode.BANNER
            }
            ChoiceChip("横幅 + 进度", prefs.triggerMode == TriggerMode.BANNER_OR_PROGRESS) {
                prefs.triggerMode = TriggerMode.BANNER_OR_PROGRESS
            }
            ChoiceChip("全部通知", prefs.triggerMode == TriggerMode.ALL) {
                prefs.triggerMode = TriggerMode.ALL
            }
        }
        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text("对所有应用生效", style = MaterialTheme.typography.bodyLarge)
                Text(
                    text = if (prefs.allApps) {
                        "已开启，转换所有应用的通知"
                    } else {
                        "仅转换选中的应用（$selectedCount 个）"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = prefs.allApps, onCheckedChange = { prefs.allApps = it })
        }
        if (!prefs.allApps) {
            OutlinedButton(
                onClick = onOpenApps,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
            ) {
                Text("选择应用")
            }
        }
        Spacer(Modifier.height(14.dp))
        Text("胶囊文字", style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ChoiceChip("自动", prefs.chipStyle == ChipStyle.AUTO) { prefs.chipStyle = ChipStyle.AUTO }
            ChoiceChip("百分比", prefs.chipStyle == ChipStyle.PERCENT) { prefs.chipStyle = ChipStyle.PERCENT }
            ChoiceChip("应用名", prefs.chipStyle == ChipStyle.APP_NAME) { prefs.chipStyle = ChipStyle.APP_NAME }
            ChoiceChip("标题", prefs.chipStyle == ChipStyle.TITLE) { prefs.chipStyle = ChipStyle.TITLE }
        }
    }
}

@Composable
private fun AdvancedCard(prefs: Prefs) {
    SectionCard {
        SectionTitle("高级选项")
        Spacer(Modifier.height(8.dp))
        SettingSwitch(
            title = "显示来源应用",
            subtitle = "在通知副标题中标注原始应用名称",
            checked = prefs.showSourceApp,
            onCheckedChange = { prefs.showSourceApp = it },
        )
        SettingSwitch(
            title = "源通知消失后保留",
            subtitle = "关闭时，原始通知被移除会同时移除转换结果",
            checked = prefs.keepAfterSourceGone,
            onCheckedChange = { prefs.keepAfterSourceGone = it },
        )
        SettingSwitch(
            title = "替换原始通知",
            subtitle = "转换成功后移除原应用的通知，可能丢失其操作按钮",
            checked = prefs.replaceSource,
            onCheckedChange = { prefs.replaceSource = it },
        )
        SettingSwitch(
            title = "跳过已有的实时更新",
            subtitle = "已经是 Live Update 的通知不再重复转换",
            checked = prefs.skipAlreadyPromoted,
            onCheckedChange = { prefs.skipAlreadyPromoted = it },
        )
    }
}

@Composable
private fun TestCard(message: String?, onTest: () -> Unit) {
    SectionCard {
        SectionTitle("试一试", "发送一条约 20 秒的演示进度通知")
        Spacer(Modifier.height(12.dp))
        Button(onClick = onTest, modifier = Modifier.fillMaxWidth()) {
            Icon(
                imageVector = Icons.Filled.PlayArrow,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text("发送测试 Live Update")
        }
        if (message != null) {
            Spacer(Modifier.height(10.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = "若系统未授予实时更新资格，通知仍会以常驻进度条的形式显示。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun EventsCard(events: List<LogEvent>, onClear: () -> Unit) {
    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.weight(1f)) {
                SectionTitle("最近转换")
            }
            if (events.isNotEmpty()) {
                TextButton(onClick = onClear) { Text("清空") }
            }
        }
        if (events.isEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = "还没有记录。开启转换并收到横幅通知后会显示在这里。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@SectionCard
        }
        Spacer(Modifier.height(4.dp))
        events.take(8).forEach { event ->
            EventRow(event)
        }
    }
}

@Composable
private fun EventRow(event: LogEvent) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 7.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = Instant.ofEpochMilli(event.time)
                .atZone(ZoneId.systemDefault())
                .format(clockFormat),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = event.title,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "${event.appLabel} · ${event.state}" + if (event.chip.isNotBlank()) " · ${event.chip}" else "",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun Footer(versionName: String) {
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        TextButton(
            onClick = {
                runCatching {
                    context.startActivity(
                        Intent(
                            Intent.ACTION_VIEW,
                            Uri.parse("https://github.com/AndyerNotFound/Notification2LiveUpdate"),
                        )
                    )
                }
            }
        ) {
            Text("GitHub · Notification2LiveUpdate")
        }
        Text(
            text = "v$versionName · AGPL-3.0 · 本地运行，不联网",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
