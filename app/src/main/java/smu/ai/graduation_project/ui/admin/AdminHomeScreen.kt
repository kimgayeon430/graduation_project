package smu.ai.graduation_project.ui.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.People
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import smu.ai.graduation_project.ui.theme.CardGray
import smu.ai.graduation_project.ui.theme.LightPurple
import smu.ai.graduation_project.ui.theme.MainPurple

@Composable
fun AdminHomeScreen(
    onNavigateToMissionManagement: () -> Unit,
    onNavigateToUserManagement: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .verticalScroll(rememberScrollState())
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Text("Admin", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }

        Spacer(modifier = Modifier.height(24.dp))

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            color = LightPurple,
            shape = RoundedCornerShape(16.dp)
        ) {
            Row(
                modifier = Modifier.padding(24.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.AdminPanelSettings, null, tint = MainPurple, modifier = Modifier.size(48.dp))
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text("Admin mode", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Text("Manage missions and users.", fontSize = 14.sp, color = Color.Gray)
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        AdminMenuCard(
            title = "Mission management",
            desc = "Create and update missions.",
            icon = Icons.Default.Assignment,
            onClick = onNavigateToMissionManagement
        )

        AdminMenuCard(
            title = "User management",
            desc = "Review user activity.",
            icon = Icons.Default.People,
            onClick = onNavigateToUserManagement
        )

        AdminMenuCard(
            title = "Statistics",
            desc = "View app usage summary.",
            icon = Icons.Default.BarChart,
            onClick = { }
        )

        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
private fun AdminMenuCard(
    title: String,
    desc: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = CardGray),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                color = Color.White,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.size(40.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, null, tint = MainPurple, modifier = Modifier.size(24.dp))
                }
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(title, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Text(desc, fontSize = 12.sp, color = Color.Gray)
            }
            Icon(Icons.Default.ChevronRight, null, tint = Color.LightGray)
        }
    }
}
