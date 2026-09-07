package smu.ai.graduation_project.ui.screens

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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

/**
 * 마이페이지 "완료한 미션" 을 누르면 보이는 완료 미션 목록.
 * 진행 중 미션 화면(`InProgressMissionScreen`)과 같은 형식이며, 상태 필터만 "완료" 로 바꾼다.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompletedMissionScreen(
    onNavigateBack: () -> Unit,
    onViewMission: (String) -> Unit
) {
    val user = Firebase.auth.currentUser
    val db = Firebase.firestore
    var missions by remember { mutableStateOf<List<Mission>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(user?.uid) {
        val uid = user?.uid
        if (uid == null) {
            missions = emptyList()
            isLoading = false
            return@LaunchedEffect
        }

        db.collection("user_missions")
            .whereEqualTo("userId", uid)
            .get()
            .addOnSuccessListener { userMissionSnapshot ->
                val completedDocs = userMissionSnapshot.documents.filter { doc ->
                    val status = doc.getString("status").orEmpty()
                    status.contains("완료") || status.equals("Completed", true)
                }.sortedByDescending { it.getTimestamp("completedAt")?.seconds ?: Long.MIN_VALUE }

                if (completedDocs.isEmpty()) {
                    missions = emptyList()
                    isLoading = false
                    return@addOnSuccessListener
                }

                val loaded = arrayOfNulls<Mission>(completedDocs.size)
                var remaining = completedDocs.size

                completedDocs.forEachIndexed { index, userMissionDoc ->
                    val missionId = userMissionDoc.getString("missionId").orEmpty()

                    db.collection("missions").document(missionId).get()
                        .addOnSuccessListener { missionDoc ->
                            loaded[index] = Mission(
                                id = missionId,
                                title = missionDoc.getString("title")
                                    ?: userMissionDoc.getString("title")
                                    ?: "완료한 미션",
                                desc = missionDoc.getString("desc") ?: "",
                                points = missionDoc.getLong("points")?.toInt() ?: 0,
                                category = missionDoc.getString("category") ?: "투어",
                                imageUrl = missionDoc.getString("imageUrl").orEmpty(),
                                status = "완료",
                                progress = 1f,
                                progressText = "3/3"
                            )
                        }
                        .addOnCompleteListener {
                            remaining -= 1
                            if (remaining == 0) {
                                missions = loaded.filterNotNull()
                                isLoading = false
                            }
                        }
                }
            }
            .addOnFailureListener {
                missions = emptyList()
                isLoading = false
            }
    }

    Scaffold(
        containerColor = Color.White,
        topBar = {
            TopAppBar(
                title = { Text("완료한 미션", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
            )
        }
    ) { innerPadding ->
        when {
            isLoading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = MainPurple)
                }
            }

            missions.isEmpty() -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("완료한 미션이 없습니다.", fontWeight = FontWeight.Bold, fontSize = 20.sp)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("미션을 끝까지 인증하면 여기에 쌓입니다.", color = Color.Gray)
                    }
                }
            }

            else -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .background(Color.White),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    items(missions) { mission ->
                        CompletedMissionCard(
                            mission = mission,
                            onView = { onViewMission(mission.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CompletedMissionCard(
    mission: Mission,
    onView: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = Color.White,
        shadowElevation = 4.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(width = 88.dp, height = 96.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(CardGray)
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
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(mission.title, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Color(0xFF2F2F2F))
                    Text(mission.desc, color = Color.Gray, fontSize = 13.sp, maxLines = 2)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Circle, null, tint = Orange, modifier = Modifier.size(9.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("${mission.points}P", fontWeight = FontWeight.Bold, color = Color(0xFF444444))
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CheckCircle, null, tint = Color(0xFF70BE63), modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("인증 완료 ${mission.progressText}", fontWeight = FontWeight.Bold, color = Color(0xFF353535))
                }
                LinearProgressIndicator(
                    progress = { mission.progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(12.dp)),
                    color = Color(0xFF70BE63),
                    trackColor = Color(0xFFE8E8E8)
                )
            }

            Button(
                onClick = onView,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = MainPurple),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text("미션 다시 보기", fontWeight = FontWeight.Bold)
            }
        }
    }
}
