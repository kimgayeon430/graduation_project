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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.sp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.firestore
import smu.ai.graduation_project.navigation.Screen
import smu.ai.graduation_project.ui.admin.AdminMissionEditScreen
import smu.ai.graduation_project.ui.admin.AdminHomeScreen
import smu.ai.graduation_project.ui.admin.AdminMissionListScreen
import smu.ai.graduation_project.ui.admin.AdminUserManagementScreen
import smu.ai.graduation_project.ui.screens.HomeScreen
import smu.ai.graduation_project.ui.screens.InProgressMissionScreen
import smu.ai.graduation_project.ui.screens.LandingScreen
import smu.ai.graduation_project.ui.screens.LoginScreen
import smu.ai.graduation_project.ui.screens.MissionDetailScreen
import smu.ai.graduation_project.ui.screens.MissionListScreen
import smu.ai.graduation_project.ui.screens.MissionPerformScreen
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
    val allItems = listOf(Screen.Home, Screen.Mission, Screen.Add, Screen.Ranking, Screen.Profile)
    val currentUser = Firebase.auth.currentUser
    val context = LocalContext.current
    var isAdmin by remember { mutableStateOf(false) }

    LaunchedEffect(currentUser?.uid) {
        currentUser?.uid?.let { uid ->
            Firebase.firestore.collection("admins").document(uid).get().addOnSuccessListener { doc ->
                isAdmin = doc.exists()
            }
        }
    }

    Scaffold(
        containerColor = Color.White,
        bottomBar = {
            val entry by navController.currentBackStackEntryAsState()
            val current = entry?.destination
            val items = if (isAdmin) allItems else allItems.filterNot { it == Screen.Add }
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
                    onNavigateBack = { navController.popBackStack() }
                )
            }
            composable("profile/in-progress") {
                InProgressMissionScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onContinueMission = { navController.navigate("mission_perform/$it") }
                )
            }
            composable(Screen.Add.route) {
                if (isAdmin) {
                    AdminHomeScreen(
                        onNavigateToMissionManagement = { navController.navigate("admin/missions") },
                        onNavigateToUserManagement = { navController.navigate("admin/users") }
                    )
                } else {
                    LaunchedEffect(Unit) {
                        android.widget.Toast.makeText(context, "관리자만 접근할 수 있습니다.", android.widget.Toast.LENGTH_SHORT).show()
                        navController.navigate(Screen.Home.route) {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                }
            }
            composable("admin/users") {
                if (isAdmin) {
                    AdminUserManagementScreen(
                        onNavigateBack = { navController.popBackStack() }
                    )
                } else {
                    LaunchedEffect(Unit) {
                        android.widget.Toast.makeText(context, "관리자만 접근할 수 있습니다.", android.widget.Toast.LENGTH_SHORT).show()
                        navController.popBackStack()
                    }
                }
            }
            composable("admin/missions") {
                if (isAdmin) {
                    AdminMissionListScreen(
                        onNavigateBack = { navController.popBackStack() },
                        onAddMission = { navController.navigate("admin/missions/new") },
                        onEditMission = { navController.navigate("admin/missions/edit/$it") }
                    )
                } else {
                    LaunchedEffect(Unit) {
                        android.widget.Toast.makeText(context, "관리자만 접근할 수 있습니다.", android.widget.Toast.LENGTH_SHORT).show()
                        navController.popBackStack()
                    }
                }
            }
            composable("admin/missions/new") {
                if (isAdmin) {
                    AdminMissionEditScreen(
                        missionId = null,
                        onNavigateBack = { navController.popBackStack() },
                        onSaveSuccess = { navController.popBackStack() }
                    )
                } else {
                    LaunchedEffect(Unit) {
                        android.widget.Toast.makeText(context, "관리자만 접근할 수 있습니다.", android.widget.Toast.LENGTH_SHORT).show()
                        navController.popBackStack()
                    }
                }
            }
            composable("admin/missions/edit/{missionId}") { backStack ->
                if (isAdmin) {
                    AdminMissionEditScreen(
                        missionId = backStack.arguments?.getString("missionId"),
                        onNavigateBack = { navController.popBackStack() },
                        onSaveSuccess = { navController.popBackStack() }
                    )
                } else {
                    LaunchedEffect(Unit) {
                        android.widget.Toast.makeText(context, "관리자만 접근할 수 있습니다.", android.widget.Toast.LENGTH_SHORT).show()
                        navController.popBackStack()
                    }
                }
            }
            composable(Screen.Ranking.route) { RankingScreen() }
            composable(Screen.Profile.route) {
                ProfileScreen(
                    onLogout = onLogout,
                    onNavigateToInProgressMissions = { navController.navigate("profile/in-progress") }
                )
            }
        }
    }
}
