package com.andyer.n2lu.ui

import android.Manifest
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.andyer.n2lu.data.EventLog
import com.andyer.n2lu.data.Prefs
import com.andyer.n2lu.service.DemoPoster
import com.andyer.n2lu.service.Reposter
import com.andyer.n2lu.ui.theme.N2LuTheme
import com.andyer.n2lu.util.PromotionPage
import com.andyer.n2lu.util.SystemStatus
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            N2LuTheme {
                AppRoot()
            }
        }
    }
}

private enum class Screen {
    Home,
    Apps,
}

@Composable
private fun AppRoot() {
    val context = LocalContext.current
    val prefs = remember { Prefs.get(context) }
    var screen by remember { mutableStateOf(Screen.Home) }
    var status by remember { mutableStateOf(SystemStatus.snapshot(context)) }
    val events by EventLog.events.collectAsState()

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        status = SystemStatus.snapshot(context)
        if (!granted) {
            Toast.makeText(
                context,
                "未授予通知权限。若系统不再弹窗，请在「应用通知设置」里手动开启。",
                Toast.LENGTH_LONG,
            ).show()
        }
    }

    LaunchedEffect(Unit) {
        EventLog.load(context)
        status = SystemStatus.snapshot(context)
        if (Build.VERSION.SDK_INT >= 33 && !status.notificationsEnabled) {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        while (true) {
            status = SystemStatus.snapshot(context)
            delay(2000)
        }
    }

    BackHandler(enabled = screen != Screen.Home) { screen = Screen.Home }

    when (screen) {
        Screen.Home -> HomeScreen(
            status = status,
            prefs = prefs,
            events = events,
            onEnabledChanged = { enabled ->
                prefs.enabled = enabled
                if (!enabled) {
                    Reposter.cancelAll(context)
                    DemoPoster.stop(context)
                }
            },
            onOpenAccess = {
                if (!SystemStatus.openNotificationAccess(context)) {
                    Toast.makeText(
                        context,
                        "打不开通知使用权设置页，请在系统设置里手动查找「通知使用权」。",
                        Toast.LENGTH_LONG,
                    ).show()
                }
            },
            onOpenPromotion = {
                if (!status.promotionPermissionDeclared) {
                    Toast.makeText(
                        context,
                        "当前安装包没有声明 POST_PROMOTED_NOTIFICATIONS，请确认版本是 0.3.0 及以上。",
                        Toast.LENGTH_LONG,
                    ).show()
                } else {
                    when (SystemStatus.openPromotionSettings(context)) {
                        PromotionPage.PROMOTION -> Unit
                        PromotionPage.APP_NOTIFICATIONS -> Toast.makeText(
                            context,
                            "此系统没有「实时更新」设置页（需要 Android 16 QPR1），已打开应用通知设置，提升由系统自动判定。",
                            Toast.LENGTH_LONG,
                        ).show()
                        PromotionPage.NONE -> Toast.makeText(
                            context,
                            "打不开系统设置页，请在系统设置中手动查找本应用。",
                            Toast.LENGTH_LONG,
                        ).show()
                    }
                }
            },
            onRequestNotifications = {
                if (Build.VERSION.SDK_INT >= 33 && !status.notificationsEnabled) {
                    permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else if (!status.notificationsEnabled) {
                    if (!SystemStatus.openAppNotificationSettings(context)) {
                        SystemStatus.openAppDetails(context)
                    }
                }
            },
            onOpenUsageAccess = {
                if (!SystemStatus.openUsageAccessSettings(context)) {
                    Toast.makeText(
                        context,
                        "打不开「使用情况访问」设置页，请在系统设置的「特殊应用权限」里手动查找本应用。",
                        Toast.LENGTH_LONG,
                    ).show()
                }
            },
            onOpenApps = { screen = Screen.Apps },
        )

        Screen.Apps -> AppPickerScreen(
            prefs = prefs,
            onBack = { screen = Screen.Home },
        )
    }
}
