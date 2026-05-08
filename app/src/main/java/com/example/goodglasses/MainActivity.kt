package com.example.goodglasses

import android.content.Context
import android.media.AudioManager
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteDefaults
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.htc.viveglass.sdk.ViveGlassKit
import com.htc.viveglass.sdk.simulator.ViveGlassSimulator
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.htc.viveglass.sdk.ViveGlass
import com.example.goodglasses.ui.tab.AppDestination
import com.example.goodglasses.ui.tab.CameraScreen
import com.example.goodglasses.ui.tab.GlassesScreen
import com.example.goodglasses.ui.tab.setSimulator
import com.example.goodglasses.ui.theme.AppColors
import com.example.goodglasses.util.DebugLogger
import com.example.goodglasses.util.Logger
import com.example.goodglasses.util.NoOpLogger

class MainActivity : AppCompatActivity() {

    val enableDebugLog = false

    var viveClientManager: ViveGlassKitManager? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Logger.instance =
            if (enableDebugLog) DebugLogger()
            else NoOpLogger()

        enableEdgeToEdge()
        setContent {

            val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val glass = ViveGlass()
            val appContext = applicationContext
            val kit = ViveGlassKit(appContext)
            ViveGlassSimulator.create(appContext)
            val simulator = ViveGlassSimulator.instance()

            viveClientManager = ViveGlassKitManager(glass, kit, simulator, appContext, audioManager)
            val manager = viveClientManager ?: return@setContent
            SampleApp(manager, simulator, appContext)
        }
    }

    override fun onPause() {
        super.onPause()
    }

    override fun onDestroy() {
        super.onDestroy()
        viveClientManager?.cleanup()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SampleApp(
    viveClientManager: ViveGlassKitManager,
    simulator: ViveGlassSimulator,
    context: Context
) {
    val navController = rememberNavController()
    val items = AppDestination.items

    val isConnected by viveClientManager.connection.collectAsStateWithLifecycle()
    val currentRoute = navController.currentBackStackEntry?.destination?.route

    LaunchedEffect(isConnected, currentRoute) {
        if (!isConnected && currentRoute != AppDestination.Glasses.route) {
            navController.navigate(AppDestination.Glasses.route) {
                popUpTo(navController.graph.startDestinationId) { saveState = true }
                launchSingleTop = true
                restoreState = true
            }
        }
    }

    Surface(
        color = AppColors.BgDarkPrimary,
        modifier = Modifier.fillMaxSize()
    ) {
        val navBackStackEntry by navController.currentBackStackEntryAsState()
        val currentRoute = navBackStackEntry?.destination?.route
        val myNavigationSuiteItemColors = NavigationSuiteDefaults.itemColors(
            navigationBarItemColors = NavigationBarItemDefaults.colors(
                indicatorColor = if (isConnected) AppColors.BgBlue500_30 else Color.Transparent,
                selectedIconColor = AppColors.TextBlue400,
                selectedTextColor = AppColors.TextBlue400,
                unselectedIconColor = AppColors.TextGray400,
                unselectedTextColor = AppColors.TextGray400,
                disabledTextColor = AppColors.BgDarkSecondary,
                disabledIconColor = AppColors.BgDarkSecondary
            ),
        )

        NavigationSuiteScaffold(
            containerColor = AppColors.BgDarkPrimary,
            contentColor = AppColors.TextGray400,
            navigationSuiteColors = NavigationSuiteDefaults.colors(
                navigationBarContainerColor = AppColors.BgDarkPrimary,
                navigationBarContentColor = AppColors.TextGray400,
            ),
            navigationSuiteItems = {
                items.forEach { dest ->
                    item(
                        selected = currentRoute == dest.route,
                        onClick = {
                            if (currentRoute != dest.route) {
                                if (currentRoute != null)
                                    viveClientManager.releasePreviousPage(currentRoute)

                                navController.navigate(dest.route) {
                                    popUpTo(navController.graph.startDestinationId) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        },
                        icon = {
                            Icon(
                                painter = painterResource(dest.iconRes),
                                contentDescription = dest.label,
                                modifier = Modifier.size(26.dp),
                            )
                        },
                        label = {
                            Text(dest.label)
                        },
                        colors = myNavigationSuiteItemColors,
                        enabled = isConnected
                    )
                }
            }
        ) {
            NavHost(
                navController = navController,
                startDestination = AppDestination.Glasses.route,
            ) {
                setSimulator(simulator)
                composable(AppDestination.Glasses.route) { GlassesScreen(viveClientManager) }
                composable(AppDestination.Camera.route) { CameraScreen(viveClientManager) }
            }
        }
    }
}
