package com.andyer.n2lu.ui

import android.Manifest
import android.os.Build
import android.os.Bundle
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
    ) {
        status = SystemStatus.snapshot(context)
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
            onOpenAccess = { SystemStatus.openNotificationAccess(context) },
            onOpenPromotion = { SystemStatus.openPromotionSettings(context) },
            onRequestNotifications = {
                if (Build.VERSION.SDK_INT >= 33 && !status.notificationsEnabled) {
                    permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
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
