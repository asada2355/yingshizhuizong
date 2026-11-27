package com.example.yingshizhuizong.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.yingshizhuizong.ui.screens.detail.DetailScreen
import com.example.yingshizhuizong.ui.screens.edit.EditScreen
import com.example.yingshizhuizong.ui.screens.search.SearchScreen
import com.example.yingshizhuizong.ui.screens.settings.SettingsScreen
import com.example.yingshizhuizong.ui.screens.trailer.TrailerScreen
import com.example.yingshizhuizong.ui.screens.upcoming.UpcomingScreen
import com.example.yingshizhuizong.ui.screens.watchlist.WatchlistScreen

object Routes {
    const val WATCHLIST = "watchlist"
    const val UPCOMING = "upcoming"
    const val SETTINGS = "settings"
    const val SEARCH = "search"
    const val DETAIL = "detail/{itemId}"
    const val EDIT = "edit?itemId={itemId}"
    const val TRAILER = "trailer/{itemId}"

    fun detail(id: Long) = "detail/$id"
    fun edit(id: Long? = null) = if (id == null) "edit" else "edit?itemId=$id"
    fun trailer(id: Long) = "trailer/$id"
}

data class BottomItem(val route: String, val label: String, val icon: ImageVector)

private val bottomItems = listOf(
    BottomItem(Routes.WATCHLIST, "看单", Icons.Filled.List),
    BottomItem(Routes.UPCOMING, "上映", Icons.Filled.DateRange),
    BottomItem(Routes.SETTINGS, "设置", Icons.Filled.Settings),
)

@Composable
fun AppRoot() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val showBottomBar = currentRoute in bottomItems.map { it.route }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    bottomItems.forEach { item ->
                        NavigationBarItem(
                            selected = currentRoute == item.route,
                            onClick = {
                                navController.navigate(item.route) {
                                    popUpTo(Routes.WATCHLIST) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(item.icon, contentDescription = item.label) },
                            label = { Text(item.label) }
                        )
                    }
                }
            }
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Routes.WATCHLIST,
            modifier = Modifier.padding(padding)
        ) {
            composable(Routes.WATCHLIST) {
                WatchlistScreen(
                    onItemClick = { navController.navigate(Routes.detail(it)) },
                    onAddClick = { navController.navigate(Routes.edit()) },
                    onSearchClick = { navController.navigate(Routes.SEARCH) }
                )
            }
            composable(Routes.SEARCH) {
                SearchScreen(
                    onBack = { navController.popBackStack() },
                    onItemClick = { navController.navigate(Routes.detail(it)) }
                )
            }
            composable(Routes.UPCOMING) {
                UpcomingScreen(onItemClick = { navController.navigate(Routes.detail(it)) })
            }
            composable(Routes.SETTINGS) {
                SettingsScreen()
            }
            composable(
                Routes.DETAIL,
                arguments = listOf(navArgument("itemId") { type = NavType.LongType })
            ) {
                DetailScreen(
                    onBack = { navController.popBackStack() },
                    onEdit = { navController.navigate(Routes.edit(it)) },
                    onPlayTrailer = { navController.navigate(Routes.trailer(it)) }
                )
            }
            composable(
                Routes.TRAILER,
                arguments = listOf(navArgument("itemId") { type = NavType.LongType })
            ) {
                TrailerScreen(
                    onBack = { navController.popBackStack() },
                    onEdit = { navController.navigate(Routes.edit(it)) }
                )
            }
            composable(
                Routes.EDIT,
                arguments = listOf(
                    navArgument("itemId") { type = NavType.LongType; defaultValue = -1L }
                )
            ) {
                EditScreen(onBack = { navController.popBackStack() })
            }
        }
    }
}
