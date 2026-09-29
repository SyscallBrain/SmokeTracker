package pt.tiago.cinzeiro.ui.nav

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Today
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import kotlinx.serialization.Serializable
import pt.tiago.cinzeiro.di.AppContainer
import pt.tiago.cinzeiro.ui.components.DesignShowcase
import pt.tiago.cinzeiro.ui.components.EmptyState
import pt.tiago.cinzeiro.ui.stats.StatsRoute
import pt.tiago.cinzeiro.ui.stats.StatsViewModel
import pt.tiago.cinzeiro.ui.theme.SnackbarAction
import pt.tiago.cinzeiro.ui.today.TodayRoute
import pt.tiago.cinzeiro.ui.today.TodayViewModel
import kotlin.reflect.KClass

@Serializable data object TodayDest
@Serializable data object StatsDest
@Serializable data object HistoryDest
@Serializable data object DesignDest

private data class TopLevel(val route: Any, val cls: KClass<*>, val label: String, val icon: ImageVector)

private val topLevel = listOf(
    TopLevel(TodayDest, TodayDest::class, "Hoje", Icons.Rounded.Today),
    TopLevel(StatsDest, StatsDest::class, "Estatísticas", Icons.Rounded.BarChart),
    TopLevel(HistoryDest, HistoryDest::class, "Histórico", Icons.Rounded.History),
)

@Composable
fun AppNav(container: AppContainer) {
    val nav = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }
    val backStack by nav.currentBackStackEntryAsState()
    val cs = MaterialTheme.colorScheme

    Scaffold(
        containerColor = cs.background,
        snackbarHost = {
            SnackbarHost(snackbarHostState) { data ->
                Snackbar(
                    data,
                    containerColor = cs.inverseSurface,
                    contentColor = cs.inverseOnSurface,
                    actionColor = SnackbarAction,
                )
            }
        },
        bottomBar = {
            NavigationBar(containerColor = cs.surface, tonalElevation = 0.dp) {
                topLevel.forEach { item ->
                    val selected = backStack?.destination?.hasRoute(item.cls) == true
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            nav.navigate(item.route) {
                                popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(item.icon, contentDescription = null) },
                        label = { Text(item.label) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = cs.secondary,
                            selectedTextColor = cs.onSurface,
                            indicatorColor = cs.secondary.copy(alpha = 0.22f),
                            unselectedIconColor = cs.onSurfaceVariant,
                            unselectedTextColor = cs.onSurfaceVariant,
                        ),
                    )
                }
            }
        },
    ) { padding ->
        NavHost(nav, startDestination = TodayDest, modifier = Modifier.padding(padding)) {
            composable<TodayDest> {
                val vm: TodayViewModel = viewModel(
                    factory = viewModelFactory {
                        initializer { TodayViewModel(container.repository, container.debugTools) }
                    },
                )
                TodayRoute(vm, snackbarHostState, onOpenDesignShowcase = { nav.navigate(DesignDest) })
            }
            composable<StatsDest> {
                val vm: StatsViewModel = viewModel(
                    factory = viewModelFactory { initializer { StatsViewModel(container.repository) } },
                )
                StatsRoute(vm)
            }
            composable<HistoryDest> { EmptyState("Em breve: histórico, apagar e acrescentar.", icon = Icons.Rounded.History) }
            composable<DesignDest> { DesignShowcase() }
        }
    }
}
