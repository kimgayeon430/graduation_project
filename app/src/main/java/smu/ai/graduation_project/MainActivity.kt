package smu.ai.graduation_project

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import smu.ai.graduation_project.navigation.Screen
import smu.ai.graduation_project.ui.admin.AdminHomeScreen
import smu.ai.graduation_project.ui.screens.HomeScreen
import smu.ai.graduation_project.ui.screens.LandingScreen
import smu.ai.graduation_project.ui.screens.LoginScreen
import smu.ai.graduation_project.ui.screens.MissionDetailScreen
import smu.ai.graduation_project.ui.screens.MissionListScreen
import smu.ai.graduation_project.ui.screens.ProfileScreen
import smu.ai.graduation_project.ui.screens.RankingScreen
import smu.ai.graduation_project.ui.screens.SignUpScreen
import smu.ai.graduation_project.ui.theme.Graduation_projectTheme
import smu.ai.graduation_project.ui.theme.MainPurple

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            Graduation_projectTheme {
                AppRoot()
            }
        }
    }
}

@Composable
private fun AppRoot() {
    val auth = Firebase.auth
    val rootNavController = rememberNavController()
    val startDestination = if (auth.currentUser != null) "main" else "landing"

    NavHost(navController = rootNavController, startDestination = startDestination) {
        composable("landing") {
            LandingScreen(
                onSignUp = { rootNavController.navigate("signup") },
                onLogin = { rootNavController.navigate("login") },
                onGuest = { rootNavController.navigate("main") }
            )
        }
        composable("signup") {
            SignUpScreen(
                onNavigateBack = { rootNavController.popBackStack() },
                onSignUpSuccess = {
                    rootNavController.navigate("main") {
                        popUpTo("landing") { inclusive = true }
                    }
                }
            )
        }
        composable("login") {
            LoginScreen(
                onNavigateBack = { rootNavController.popBackStack() },
                onLoginSuccess = {
                    rootNavController.navigate("main") {
                        popUpTo("landing") { inclusive = true }
                    }
                }
            )
        }
        composable("main") {
            MainApp(
                onLogout = {
                    Firebase.auth.signOut()
                    rootNavController.navigate("landing") {
                        popUpTo("main") { inclusive = true }
                    }
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainApp(onLogout: () -> Unit) {
    val navController = rememberNavController()
    val items = listOf(Screen.Home, Screen.Mission, Screen.Add, Screen.Ranking, Screen.Profile)

    Scaffold(
        containerColor = Color.White,
        bottomBar = {
            val entry by navController.currentBackStackEntryAsState()
            val current = entry?.destination
            if (items.any { it.route == current?.route }) {
                NavigationBar(containerColor = Color.White) {
                    items.forEach { screen ->
                        NavigationBarItem(
                            icon = { Icon(screen.icon, null) },
                            label = { Text(screen.label, fontSize = 10.sp) },
                            selected = current?.hierarchy?.any { it.route == screen.route } == true,
                            onClick = {
                                navController.navigate(screen.route) {
                                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MainPurple,
                                selectedTextColor = MainPurple,
                                indicatorColor = Color.Transparent
                            )
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Home.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Screen.Home.route) { HomeScreen { navController.navigate("mission_detail/$it") } }
            composable(Screen.Mission.route) { MissionListScreen { navController.navigate("mission_detail/$it") } }
            composable("mission_detail/{missionId}") { backStack ->
                MissionDetailScreen(
                    missionId = backStack.arguments?.getString("missionId").orEmpty(),
                    onNavigateBack = { navController.popBackStack() },
                    onPerformMission = { navController.navigate("mission_perform/$it") }
                )
            }
            composable("mission_perform/{missionId}") { backStack ->
                MissionPerformScreen(
                    missionId = backStack.arguments?.getString("missionId").orEmpty(),
                    onBack = { navController.popBackStack() }
                )
            }
            composable(Screen.Add.route) { AdminHomeScreen({}, {}) }
            composable(Screen.Ranking.route) { RankingScreen() }
            composable(Screen.Profile.route) { ProfileScreen(onLogout) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MissionPerformScreen(missionId: String, onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Mission perform") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
            )
        },
        containerColor = Color.White
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.Center
        ) {
            Text("Performing $missionId")
        }
    }
}
