package com.personalkanban.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.personalkanban.app.data.local.SettingsDataStore
import com.personalkanban.app.domain.model.ThemeMode
import com.personalkanban.app.ui.detail.TaskDetailScreen
import com.personalkanban.app.ui.detail.TaskDetailViewModel
import com.personalkanban.app.ui.extra.ArchiveTrashScreen
import com.personalkanban.app.ui.extra.ArchiveTrashViewModel
import com.personalkanban.app.ui.extra.CustomersScreen
import com.personalkanban.app.ui.extra.CustomersViewModel
import com.personalkanban.app.ui.extra.StatsScreen
import com.personalkanban.app.ui.extra.StatsViewModel
import com.personalkanban.app.ui.main.MainScreen
import com.personalkanban.app.ui.main.MainViewModel
import com.personalkanban.app.ui.settings.SettingsScreen
import com.personalkanban.app.ui.settings.SettingsViewModel
import com.personalkanban.app.ui.theme.PersonalKanbanTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    companion object {
        const val EXTRA_OPEN_TASK_ID = "extra_open_task_id"
        const val EXTRA_QUICK_ADD = "extra_quick_add"
    }

    @Inject
    lateinit var settingsDataStore: SettingsDataStore

    private var pendingTaskId by mutableStateOf<String?>(null)
    private var pendingQuickAdd by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleIntent(intent)

        setContent {
            val settings by settingsDataStore.settingsFlow.collectAsState(
                initial = com.personalkanban.app.domain.model.AppSettings(themeMode = ThemeMode.SYSTEM)
            )
            val navController = rememberNavController()

            PersonalKanbanTheme(themeMode = settings.themeMode) {
                val targetTask = pendingTaskId
                if (targetTask != null) {
                    pendingTaskId = null
                    navController.navigate("task/$targetTask")
                }

                NavHost(navController = navController, startDestination = "main") {
                    composable("main") {
                        val vm: MainViewModel = hiltViewModel()
                        MainScreen(
                            viewModel = vm,
                            initialQuickAdd = pendingQuickAdd,
                            onConsumeInitialQuickAdd = { pendingQuickAdd = false },
                            onOpenTask = { id -> navController.navigate("task/$id") },
                            onOpenSettings = { navController.navigate("settings") }
                        )
                    }

                    composable(
                        route = "task/{taskId}",
                        arguments = listOf(navArgument("taskId") { type = NavType.StringType })
                    ) {
                        val vm: TaskDetailViewModel = hiltViewModel()
                        TaskDetailScreen(
                            viewModel = vm,
                            onBack = { navController.popBackStack() },
                            onOpenTask = { newId ->
                                navController.navigate("task/$newId") {
                                    popUpTo("main")
                                }
                            }
                        )
                    }

                    composable("settings") {
                        val vm: SettingsViewModel = hiltViewModel()
                        SettingsScreen(
                            viewModel = vm,
                            onBack = { navController.popBackStack() },
                            onOpenArchiveTrash = { navController.navigate("archive_trash") },
                            onOpenCustomers = { navController.navigate("customers") },
                            onOpenStats = { navController.navigate("stats") }
                        )
                    }

                    composable("archive_trash") {
                        val vm: ArchiveTrashViewModel = hiltViewModel()
                        ArchiveTrashScreen(
                            viewModel = vm,
                            onBack = { navController.popBackStack() },
                            onOpenTask = { id -> navController.navigate("task/$id") }
                        )
                    }

                    composable("customers") {
                        val vm: CustomersViewModel = hiltViewModel()
                        CustomersScreen(
                            viewModel = vm,
                            onBack = { navController.popBackStack() }
                        )
                    }

                    composable("stats") {
                        val vm: StatsViewModel = hiltViewModel()
                        StatsScreen(
                            viewModel = vm,
                            onBack = { navController.popBackStack() }
                        )
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        intent ?: return
        intent.getStringExtra(EXTRA_OPEN_TASK_ID)?.let {
            pendingTaskId = it
            intent.removeExtra(EXTRA_OPEN_TASK_ID)
        }
        if (intent.getBooleanExtra(EXTRA_QUICK_ADD, false)) {
            pendingQuickAdd = true
            intent.removeExtra(EXTRA_QUICK_ADD)
        }
    }
}
