package smu.ai.graduation_project.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import smu.ai.graduation_project.ui.components.ProfileMenuItem
import smu.ai.graduation_project.ui.theme.CardGray
import smu.ai.graduation_project.ui.theme.LightPurple
import smu.ai.graduation_project.ui.theme.MainPurple

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(onLogout: () -> Unit) {
    val currentUser = Firebase.auth.currentUser

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Profile") },
                actions = {
                    IconButton(onClick = onLogout) {
                        Icon(Icons.AutoMirrored.Filled.ExitToApp, null)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
            )
        },
        containerColor = Color.White
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(88.dp)
                        .background(LightPurple, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Person, null, tint = MainPurple, modifier = Modifier.size(48.dp))
                }
                Text(
                    text = currentUser?.displayName ?: "Guest",
                    fontWeight = FontWeight.Bold,
                    fontSize = 22.sp
                )
                Text(
                    text = currentUser?.email ?: "guest",
                    color = Color.Gray,
                    fontSize = 14.sp
                )
                Surface(
                    color = CardGray,
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text(
                        text = "Logout from the top-right button.",
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                        color = Color.Gray
                    )
                }
            }

            ProfileMenuItem("Mission history", Icons.Default.EmojiEvents) { }
            ProfileMenuItem("Logout", Icons.AutoMirrored.Filled.ExitToApp, onClick = onLogout)
        }
    }
}
