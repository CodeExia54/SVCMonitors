package moe.fuqiuluo.mamu.ui.screen

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import moe.fuqiuluo.mamu.svc.repo.SvcRuntimeManager
import moe.fuqiuluo.mamu.ui.theme.rememberAdaptiveLayoutInfo

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(windowSizeClass: WindowSizeClass) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val adaptiveLayout = rememberAdaptiveLayoutInfo(windowSizeClass)
    LaunchedEffect(Unit) {
        SvcRuntimeManager.start(pollMs = 1000L)
    }

    when (adaptiveLayout.windowSizeClass.widthSizeClass) {
        WindowWidthSizeClass.Compact -> {
            Scaffold(
                topBar = { TopAppBar(title = { Text("SVC Monitor") }) },
                bottomBar = {
                    NavigationBar {
                        bottomNavItems.forEachIndexed { index, item ->
                            NavigationBarItem(
                                icon = { Icon(imageVector = item.icon, contentDescription = item.label) },
                                label = { Text(item.label) },
                                selected = selectedTab == index,
                                onClick = { selectedTab = index }
                            )
                        }
                    }
                }
            ) { paddingValues ->
                Box(modifier = Modifier.padding(paddingValues)) {
                    TabContent(selectedTab = selectedTab, windowSizeClass = windowSizeClass)
                }
            }
        }

        else -> {
            Scaffold(
                topBar = { TopAppBar(title = { Text("SVC Monitor") }) }
            ) { paddingValues ->
                Row(
                    modifier = Modifier
                        .padding(top = paddingValues.calculateTopPadding())
                        .fillMaxSize()
                ) {
                    NavigationRail {
                        bottomNavItems.forEachIndexed { index, item ->
                            NavigationRailItem(
                                icon = { Icon(imageVector = item.icon, contentDescription = item.label) },
                                label = { Text(item.label) },
                                selected = selectedTab == index,
                                onClick = { selectedTab = index }
                            )
                        }
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    ) {
                        TabContent(selectedTab = selectedTab, windowSizeClass = windowSizeClass)
                    }
                }
            }
        }
    }
}

@Composable
private fun TabContent(
    selectedTab: Int,
    windowSizeClass: WindowSizeClass
) {
    Crossfade(targetState = selectedTab, label = "tab_crossfade") { tab ->
        when (tab) {
            0 -> SvcMonitorScreen()
            1 -> SvcFilterScreen()
            2 -> SvcEventsScreen()
            3 -> LogsScreen(windowSizeClass = windowSizeClass)
        }
    }
}

data class BottomNavItem(
    val label: String,
    val icon: ImageVector
)

private val bottomNavItems = listOf(
    BottomNavItem("Monitor", Icons.Default.Dashboard),
    BottomNavItem("Filter", Icons.Default.FilterList),
    BottomNavItem("Events", Icons.Default.Tune),
    BottomNavItem("Logs", Icons.AutoMirrored.Filled.Article)
)
