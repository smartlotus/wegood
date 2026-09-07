package com.wegood.app

import android.content.Context
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.wegood.app.data.HEART_EMOJI
import com.wegood.app.data.Repo
import com.wegood.app.data.UiEvent
import com.wegood.app.ui.HeartBurstOverlay
import com.wegood.app.ui.screens.AnniversaryScreen
import com.wegood.app.ui.screens.DailyQuestionScreen
import com.wegood.app.ui.screens.GamesScreen
import com.wegood.app.ui.screens.HomeScreen
import com.wegood.app.ui.screens.MeScreen
import com.wegood.app.ui.screens.PairScreen
import com.wegood.app.ui.screens.TicTacToeScreen
import com.wegood.app.ui.screens.TruthDareScreen
import com.wegood.app.ui.theme.Pink
import com.wegood.app.ui.theme.WeGoodTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        Repo.init(applicationContext)
        setContent { WeGoodTheme { AppRoot() } }
    }

    override fun onResume() {
        super.onResume()
        Repo.isForeground = true
        lifecycleScope.launch {
            delay(300) // 等待 App 初始化注册完成
            runCatching { Repo.refresh() }
        }
    }

    override fun onPause() {
        super.onPause()
        Repo.isForeground = false
    }
}

@Composable
private fun AppRoot() {
    val state by Repo.state.collectAsState()
    val context = LocalContext.current

    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) !=
            android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            notifLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    var burst by remember { mutableStateOf<UiEvent.Heart?>(null) }
    LaunchedEffect(Unit) {
        Repo.uiEvents.collect { e ->
            when (e) {
                is UiEvent.Heart -> if (Repo.isForeground) {
                    vibrate(context)
                    burst = e
                }
                is UiEvent.Toast -> Toast.makeText(context, e.msg, Toast.LENGTH_SHORT).show()
                else -> {}
            }
        }
    }

    when {
        state == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = Pink)
        }
        state!!.partner == null -> PairScreen()
        else -> MainTabs(state = state!!)
    }

    burst?.let {
        HeartBurstOverlay(
            fromName = it.fromName,
            kindEmoji = HEART_EMOJI[it.kind] ?: "❤️",
            onDone = { burst = null },
        )
    }
}

private data class TabSpec(val route: String, val label: String, val icon: ImageVector)

@Composable
private fun MainTabs(state: com.wegood.app.data.MeResponse) {
    val nav = rememberNavController()
    val tabs = listOf(
        TabSpec("home", "首页", Icons.Filled.Home),
        TabSpec("ann", "纪念日", Icons.Filled.DateRange),
        TabSpec("games", "游戏", Icons.Filled.Star),
        TabSpec("me", "我的", Icons.Filled.Person),
    )
    val backStack by nav.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route

    Scaffold(
        containerColor = Color.Transparent,
        bottomBar = {
            NavigationBar(containerColor = Color.White) {
                tabs.forEach { tab ->
                    NavigationBarItem(
                        selected = currentRoute == tab.route,
                        onClick = {
                            nav.navigate(tab.route) {
                                popUpTo(nav.graph.startDestinationId) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(tab.icon, contentDescription = tab.label, tint = if (currentRoute == tab.route) Pink else Color(0xFFB0B0B6)) },
                        label = { Text(tab.label, color = if (currentRoute == tab.route) Pink else Color(0xFF8E8E93)) },
                    )
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = nav,
            startDestination = "home",
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            composable("home") { HomeScreen(state) }
            composable("ann") { AnniversaryScreen(state) }
            composable("games") { GamesScreen(onOpen = { nav.navigate(it) }) }
            composable("me") { MeScreen(state) }
            composable("ttt") { DetailPage(title = "井字棋", onBack = { nav.popBackStack() }) { TicTacToeScreen(state) } }
            composable("daily") { DetailPage(title = "每日一问", onBack = { nav.popBackStack() }) { DailyQuestionScreen(state) } }
            composable("td") { DetailPage(title = "真心话大冒险", onBack = { nav.popBackStack() }) { TruthDareScreen() } }
        }
    }
}

@Composable
private fun DetailPage(title: String, onBack: () -> Unit, content: @Composable () -> Unit) {
    androidx.compose.foundation.layout.Column(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), contentAlignment = Alignment.CenterStart) {
            androidx.compose.material3.TextButton(onClick = onBack) { Text("‹ 返回", color = Color(0xFF8E8E93)) }
        }
        Box(Modifier.weight(1f)) { content() }
    }
}

private fun vibrate(context: Context) {
    val vibrator: Vibrator = if (Build.VERSION.SDK_INT >= 31) {
        (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    }
    vibrator.vibrate(VibrationEffect.createOneShot(120, VibrationEffect.DEFAULT_AMPLITUDE))
}
