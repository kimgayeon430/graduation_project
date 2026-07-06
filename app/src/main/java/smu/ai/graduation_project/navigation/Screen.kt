package smu.ai.graduation_project.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Screen(val route: String, val label: String, val icon: ImageVector) {
    data object Home : Screen("home", "Home", Icons.Default.Home)
    data object Mission : Screen("mission", "Mission", Icons.AutoMirrored.Filled.Assignment)
    data object Add : Screen("add", "Admin", Icons.Default.Add)
    data object Ranking : Screen("ranking", "Ranking", Icons.Default.EmojiEvents)
    data object Profile : Screen("profile", "Profile", Icons.Default.Person)
}
