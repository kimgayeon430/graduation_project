package smu.ai.graduation_project.ui.admin

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Mail
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.Firebase
import com.google.firebase.firestore.firestore
import smu.ai.graduation_project.ui.theme.CardGray
import smu.ai.graduation_project.ui.theme.LightPurple
import smu.ai.graduation_project.ui.theme.MainPurple

private data class AdminUserItem(
    val uid: String,
    val name: String,
    val email: String,
    val level: String,
    val points: Int,
    val completedMissions: Int,
    val inProgressMissions: Int,
    val isAdmin: Boolean
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminUserManagementScreen(onNavigateBack: () -> Unit) {
    val db = Firebase.firestore
    val context = LocalContext.current
    var users by remember { mutableStateOf<List<AdminUserItem>>(emptyList()) }
    var adminCount by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        db.collection("users").addSnapshotListener { userSnapshot, _ ->
            val userDocs = userSnapshot?.documents.orEmpty()
            db.collection("user_missions").get().addOnSuccessListener { missionSnapshot ->
                val missionDocs = missionSnapshot.documents
                db.collection("admins").get().addOnSuccessListener { adminSnapshot ->
                    val adminIds = adminSnapshot.documents.map { it.id }.toSet()
                    adminCount = adminIds.size
                    users = userDocs.map { userDoc ->
                        val uid = userDoc.id
                        val personalMissions = missionDocs.filter { it.getString("userId") == uid }
                        AdminUserItem(
                            uid = uid,
                            name = userDoc.getString("nickname") ?: userDoc.getString("name") ?: "User",
                            email = userDoc.getString("mail") ?: userDoc.getString("email") ?: "-",
                            level = userDoc.getString("level") ?: "Lv.1",
                            points = userDoc.getLong("points")?.toInt() ?: 0,
                            completedMissions = personalMissions.count {
                                val status = it.getString("status").orEmpty()
                                status.contains("완료") || status.equals("Completed", true)
                            },
                            inProgressMissions = personalMissions.count {
                                val status = it.getString("status").orEmpty()
                                status.contains("진행") || status.equals("In Progress", true)
                            },
                            isAdmin = adminIds.contains(uid)
                        )
                    }.sortedByDescending { it.points }
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("사용자 관리", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
            )
        },
        containerColor = Color.White
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = LightPurple,
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(18.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(52.dp)
                                .background(Color.White, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.People, null, tint = MainPurple)
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                        Column {
                            Text("전체 사용자 ${users.size}명", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                            Text("관리자 ${adminCount}명 · 포인트 기반 정렬", color = Color.Gray, fontSize = 13.sp)
                        }
                    }
                }
            }

            items(users) { user ->
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = CardGray,
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(46.dp)
                                    .background(Color.White, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Person, null, tint = MainPurple)
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(user.name, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Mail, null, tint = Color.Gray, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(user.email, color = Color.Gray, fontSize = 12.sp)
                                }
                            }
                            if (user.isAdmin) {
                                Surface(color = MainPurple, shape = RoundedCornerShape(50)) {
                                    Text("ADMIN", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp))
                                }
                            }
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            AdminInfoChip(Icons.Default.EmojiEvents, "${user.points}P")
                            AdminInfoChip(Icons.Default.Person, user.level)
                            AdminInfoChip(Icons.Default.AdminPanelSettings, "완료 ${user.completedMissions}")
                            AdminInfoChip(Icons.Default.People, "진행 ${user.inProgressMissions}")
                        }

                        Button(
                            onClick = {
                                if (user.isAdmin) {
                                    db.collection("admins").document(user.uid).delete().addOnSuccessListener {
                                        Toast.makeText(context, "관리자 권한을 해제했습니다.", Toast.LENGTH_SHORT).show()
                                    }
                                } else {
                                    db.collection("admins").document(user.uid).set(
                                        mapOf("email" to user.email, "name" to user.name)
                                    ).addOnSuccessListener {
                                        Toast.makeText(context, "관리자로 등록했습니다.", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = if (user.isAdmin) Color(0xFFD9534F) else MainPurple),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(if (user.isAdmin) "관리자 권한 해제" else "관리자로 지정")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AdminInfoChip(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    Surface(color = Color.White, shape = RoundedCornerShape(50)) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, null, tint = MainPurple, modifier = Modifier.size(14.dp))
            Spacer(modifier = Modifier.width(5.dp))
            Text(text, fontSize = 12.sp, color = Color(0xFF3B3B3B), fontWeight = FontWeight.Medium)
        }
    }
}
