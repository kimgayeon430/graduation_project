package smu.ai.graduation_project.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Screen(val route: String, val label: String, val icon: ImageVector) {
    object Home : Screen("home", "홈", Icons.Default.Home)
    object Mission : Screen("mission", "미션", Icons.AutoMirrored.Filled.Assignment)
    object Add : Screen("add", "", Icons.Default.Add)
    object Ranking : Screen("ranking", "랭킹", Icons.Default.EmojiEvents)
    object Profile : Screen("profile", "마이페이지", Icons.Default.Person)
}