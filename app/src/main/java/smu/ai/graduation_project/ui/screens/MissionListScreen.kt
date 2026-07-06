package smu.ai.graduation_project.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.firestore
import smu.ai.graduation_project.model.Mission
import smu.ai.graduation_project.ui.theme.CardGray
import smu.ai.graduation_project.ui.theme.MainPurple
import smu.ai.graduation_project.ui.theme.Orange

private data class MissionCategory(val label: String, val dbValue: String?)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MissionListScreen(onMissionClick: (String) -> Unit) {
    val categories = listOf(
        MissionCategory("전체", null),
        MissionCategory("투어", "투어"),
        MissionCategory("맛집", "맛집"),
        MissionCategory("체험", "체험"),
        MissionCategory("쇼핑", "쇼핑")
    )
    var selectedCategory by remember { mutableStateOf(categories.first()) }
    var missions by remember { mutableStateOf<List<Mission>>(emptyList()) }
    val db = Firebase.firestore
    val user = Firebase.auth.currentUser

    LaunchedEffect(selectedCategory, user?.uid) {
        val query = selectedCategory.dbValue?.let {
            db.collection("missions").whereEqualTo("category", it)
        } ?: db.collection("missions")

        query.get().addOnSuccessListener { missionSnapshot ->
            val loadedMissions = missionSnapshot.documents.map { doc ->
                Mission(
                    id = doc.id,
                    title = doc.getString("title") ?: "제목 없는 미션",
                    desc = doc.getString("desc") ?: "",
                    points = doc.getLong("points")?.toInt() ?: 0,
                    category = doc.getString("category") ?: "투어",
                    imageUrl = doc.getString("imageUrl").orEmpty(),
                    status = "미 진행",
                    progress = 0f,
                    progressText = "0/1"
                )
            }

            if (user == null) {
                missions = loadedMissions
            } else {
                db.collection("user_missions")
                    .whereEqualTo("userId", user.uid)
                    .get()
                    .addOnSuccessListener { userMissionSnapshot ->
                        val byMissionId = userMissionSnapshot.documents.associateBy {
                            it.getString("missionId").orEmpty()
                        }
                        missions = loadedMissions.map { mission ->
                            val userMission = byMissionId[mission.id]
                            val rawStatus = userMission?.getString("status").orEmpty()
                            val progress = userMission?.get("progress")?.toString()?.toFloatOrNull() ?: 0f
                            val normalizedStatus = when {
                                rawStatus.contains("완료") || rawStatus.equals("Completed", true) -> "완료"
                                rawStatus.contains("진행") || rawStatus.equals("In Progress", true) -> "진행중"
                                else -> "미 진행"
                            }
                            mission.copy(
                                status = normalizedStatus,
                                progress = progress.coerceIn(0f, 1f),
                                progressText = if (progress >= 1f) "1/1" else if (progress > 0f) "2/3" else "0/1"
                            )
                        }
                    }
            }
        }
    }

    Scaffold(
        containerColor = Color.White,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("미션 목록", fontWeight = FontWeight.Bold, fontSize = 20.sp) },
                actions = {
                    IconButton(onClick = { }) {
                        Icon(Icons.Default.Search, null)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(Color.White)
        ) {
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(categories) { category ->
                    val selected = category == selectedCategory
                    Surface(
                        modifier = Modifier.clickable { selectedCategory = category },
                        shape = RoundedCornerShape(12.dp),
                        color = if (selected) MainPurple else Color.Transparent
                    ) {
                        Text(
                            text = category.label,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            color = if (selected) Color.White else Color.Gray,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                            fontSize = 13.sp
                        )
                    }
                }
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(missions) { mission ->
                    MissionListCard(mission = mission, onClick = { onMissionClick(mission.id) })
                }
            }
        }
    }
}

@Composable
private fun MissionListCard(
    mission: Mission,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        color = Color.White,
        shadowElevation = 4.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(width = 74.dp, height = 86.dp)
                    .background(CardGray, RoundedCornerShape(12.dp))
                    .clip(RoundedCornerShape(12.dp))
            ) {
                if (mission.imageUrl.isNotBlank()) {
                    AsyncImage(
                        model = mission.imageUrl,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(
                        Icons.Default.Image,
                        null,
                        tint = Color.LightGray,
                        modifier = Modifier
                            .size(34.dp)
                            .align(Alignment.Center)
                    )
                }

                Surface(
                    color = missionStatusColor(mission.status),
                    shape = RoundedCornerShape(topStart = 12.dp, bottomEnd = 10.dp),
                    modifier = Modifier.align(Alignment.TopStart)
                ) {
                    Text(
                        text = mission.status,
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Top
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = mission.title,
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp,
                            color = Color(0xFF303030)
                        )
                        Text(
                            text = mission.desc,
                            color = Color.Gray,
                            fontSize = 12.sp,
                            maxLines = 2
                        )
                    }
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = Color.LightGray)
                }

                Text(
                    text = mission.progressText,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = Color(0xFF353535)
                )

                LinearProgressIndicator(
                    progress = { mission.progress.coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(12.dp)),
                    color = Color(0xFF70BE63),
                    trackColor = Color(0xFFE8E8E8)
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Circle, null, tint = Orange, modifier = Modifier.size(9.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "${mission.points}P",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = Color(0xFF404040)
                    )
                }
            }
        }
    }
}

private fun missionStatusColor(status: String): Color {
    return when (status) {
        "진행중" -> Color(0xFF7BC96F)
        "완료" -> MainPurple
        else -> Color(0xFFD6A248)
    }
}
