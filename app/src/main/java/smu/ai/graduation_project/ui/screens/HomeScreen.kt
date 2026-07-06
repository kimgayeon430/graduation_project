package smu.ai.graduation_project.ui.screens

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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Landscape
import androidx.compose.material.icons.filled.LocationCity
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.Stars
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.firestore
import smu.ai.graduation_project.model.Mission
import smu.ai.graduation_project.ui.theme.CardGray
import smu.ai.graduation_project.ui.theme.GradientEnd
import smu.ai.graduation_project.ui.theme.GradientStart
import smu.ai.graduation_project.ui.theme.LightPurple
import smu.ai.graduation_project.ui.theme.MainPurple
import smu.ai.graduation_project.ui.theme.Orange

@Composable
fun HomeScreen(onNavigateToDetail: (String) -> Unit) {
    val user = Firebase.auth.currentUser
    var points by remember { mutableLongStateOf(0L) }
    var userName by remember { mutableStateOf(user?.displayName ?: "Traveler") }
    var startedCount by remember { mutableIntStateOf(0) }
    val totalGoal = 5
    var activeMission by remember { mutableStateOf<Mission?>(null) }

    LaunchedEffect(user?.uid) {
        user?.uid?.let { uid ->
            Firebase.firestore.collection("users").document(uid).addSnapshotListener { snapshot, _ ->
                if (snapshot != null && snapshot.exists()) {
                    points = snapshot.getLong("points") ?: 0L
                    userName = snapshot.getString("nickname") ?: user.displayName ?: "Traveler"
                }
            }

            Firebase.firestore.collection("user_missions")
                .whereEqualTo("userId", uid)
                .addSnapshotListener { snapshot, _ ->
                    if (snapshot == null) return@addSnapshotListener

                    val documents = snapshot.documents
                    startedCount = documents.size

                    val currentActive = documents.firstOrNull { doc ->
                        val status = doc.getString("status").orEmpty()
                        status.contains("진행") || status.contains("吏꾪뻾")
                    }

                    if (currentActive != null) {
                        activeMission = Mission(
                            id = currentActive.getString("missionId") ?: "",
                            title = currentActive.getString("title") ?: "",
                            desc = "현재 진행 중인 미션입니다.",
                            points = currentActive.getLong("points")?.toInt() ?: 0,
                            status = "진행중"
                        )
                    } else {
                        Firebase.firestore.collection("missions").limit(1).get()
                            .addOnSuccessListener { missionSnapshot ->
                                val doc = missionSnapshot.documents.firstOrNull() ?: return@addOnSuccessListener
                                activeMission = Mission(
                                    id = doc.id,
                                    title = doc.getString("title") ?: "",
                                    desc = doc.getString("desc") ?: "",
                                    points = doc.getLong("points")?.toInt() ?: 0,
                                    category = doc.getString("category") ?: "투어",
                                    status = "추천"
                                )
                            }
                    }
                }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Seoul Quest", color = MainPurple, fontWeight = FontWeight.Bold, fontSize = 24.sp)
            Icon(Icons.Default.Notifications, contentDescription = null, tint = Color.Gray)
        }

        Spacer(modifier = Modifier.height(24.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Column {
                Text("Hello,", fontSize = 28.sp, fontWeight = FontWeight.Bold)
                Text("$userName!", fontSize = 28.sp, fontWeight = FontWeight.Bold)
                Text("오늘도 새로운 미션에 도전해보세요!", fontSize = 14.sp, color = Color.Gray)
            }

            Surface(
                color = CardGray,
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.padding(top = 8.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Stars, contentDescription = null, tint = Orange, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(String.format("%,d", points), fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp)
                .background(
                    Brush.linearGradient(listOf(GradientStart, GradientEnd)),
                    RoundedCornerShape(16.dp)
                )
                .padding(16.dp)
        ) {
            Column {
                Text("이번 주 미션 진행률", color = Color.White, fontSize = 16.sp)
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        "$startedCount/$totalGoal",
                        color = Color.White,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    LinearProgressIndicator(
                        progress = { (startedCount.toFloat() / totalGoal).coerceIn(0f, 1f) },
                        modifier = Modifier
                            .fillMaxWidth(0.7f)
                            .height(8.dp)
                            .clip(CircleShape),
                        color = Color.White,
                        trackColor = Color.White.copy(alpha = 0.3f)
                    )
                }
            }
            Icon(
                Icons.Default.CardGiftcard,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.8f),
                modifier = Modifier
                    .size(60.dp)
                    .align(Alignment.CenterEnd)
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                if (activeMission?.status == "진행중") "현재 진행 중인 미션" else "추천 미션",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )
            TextButton(onClick = { }) {
                Text("더보기>", color = MainPurple)
            }
        }

        activeMission?.let { mission ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(100.dp)
                            .background(LightPurple, RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Landscape, contentDescription = null, tint = MainPurple, modifier = Modifier.size(40.dp))
                    }
                    Column(modifier = Modifier.width(220.dp)) {
                        Surface(
                            color = if (mission.status == "진행중") Color(0xFF4CAF50) else MainPurple,
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                mission.status.ifEmpty { "추천" },
                                color = Color.White,
                                fontSize = 10.sp,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                            )
                        }
                        Text(mission.title, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text(mission.desc, fontSize = 12.sp, color = Color.Gray, maxLines = 1)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("보상", fontSize = 12.sp)
                            Icon(Icons.Default.Stars, contentDescription = null, tint = Orange, modifier = Modifier.size(14.dp))
                            Text(" ${mission.points}P", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = { onNavigateToDetail(mission.id) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = MainPurple)
                        ) {
                            Text(
                                if (mission.status == "진행중") "미션 계속하기" else "미션 보기",
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        val categories = listOf(
            "전체" to Icons.Default.GridView,
            "투어" to Icons.Default.LocationCity,
            "맛집" to Icons.Default.Restaurant,
            "체험" to Icons.Default.CameraAlt,
            "쇼핑" to Icons.Default.ShoppingBag
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            items(categories) { (label, icon) ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Surface(
                        modifier = Modifier.size(56.dp),
                        shape = RoundedCornerShape(16.dp),
                        color = CardGray
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(icon, contentDescription = null, tint = if (label == "전체") MainPurple else Color.Gray)
                        }
                    }
                    Text(label, fontSize = 12.sp, color = if (label == "전체") MainPurple else Color.Gray)
                }
            }
        }

        Spacer(modifier = Modifier.height(80.dp))
    }
}
