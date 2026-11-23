package app.papertrader

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.padding
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import app.papertrader.ui.AlertsScreen
import app.papertrader.ui.DetailScreen
import app.papertrader.ui.PortfolioScreen
import app.papertrader.ui.WatchlistScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()) {
                Surface { PaperTraderApp() }
            }
        }
    }
}

private data class Tab(val route: String, val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector)

private val tabs = listOf(
    Tab("market", "Mercato", Icons.Filled.ShowChart),
    Tab("portfolio", "Portafoglio", Icons.Filled.AccountBalanceWallet),
    Tab("alerts", "Alert", Icons.Filled.Notifications),
)

@Composable
fun PaperTraderApp(vm: AppViewModel = viewModel()) {
    val nav = rememberNavController()
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    val backStack by nav.currentBackStackEntryAsState()
    val dest = backStack?.destination
    val open = { sym: String -> nav.navigate("detail/$sym") }

    Scaffold(bottomBar = {
        if (dest?.route?.startsWith("detail") != true) {
            NavigationBar {
                tabs.forEach { t ->
                    NavigationBarItem(
                        selected = dest?.hierarchy?.any { it.route == t.route } == true,
                        onClick = {
                            nav.navigate(t.route) {
                                popUpTo("market") { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(t.icon, contentDescription = t.label) },
                        label = { Text(t.label) },
                    )
                }
            }
        }
    }) { pad ->
        NavHost(nav, startDestination = "market", modifier = Modifier.padding(pad)) {
            composable("market") { WatchlistScreen(vm, open) }
            composable("portfolio") { PortfolioScreen(vm, open) }
            composable("alerts") { AlertsScreen(vm) }
            composable("detail/{symbol}") { e ->
                DetailScreen(vm, e.arguments?.getString("symbol").orEmpty()) { nav.popBackStack() }
            }
        }
    }
}
