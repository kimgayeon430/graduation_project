package smu.ai.graduation_project.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.ui.graphics.vector.ImageVector
import smu.ai.graduation_project.R

sealed class Screen(val route: String, @StringRes val labelRes: Int, val icon: ImageVector) {
    data object Home : Screen("home", R.string.nav_home, Icons.Default.Home)
    data object Mission : Screen("mission", R.string.nav_mission, Icons.AutoMirrored.Filled.Assignment)
    data object Add : Screen("add", R.string.nav_admin, Icons.Default.Add)
    data object Ranking : Screen("ranking", R.string.nav_ranking, Icons.Default.EmojiEvents)
    data object Profile : Screen("profile", R.string.nav_profile, Icons.Default.Person)
}
