package smu.ai.graduation_project.ui.screens

import android.widget.Toast
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.firestore
import smu.ai.graduation_project.model.Mission
import smu.ai.graduation_project.ui.theme.CardGray
import smu.ai.graduation_project.ui.theme.LightPurple
import smu.ai.graduation_project.ui.theme.MainPurple
import smu.ai.graduation_project.ui.theme.Orange

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MissionDetailScreen(
    missionId: String,
    onNavigateBack: () -> Unit,
    onPerformMission: (String) -> Unit
) {
    val db = Firebase.firestore
    val user = Firebase.auth.currentUser
    val context = LocalContext.current
    var mission by remember { mutableStateOf<Mission?>(null) }
    var imageUrl by remember { mutableStateOf("") }
    var userMissionStatus by remember { mutableStateOf("미 진행") }
    var isStarting by remember { mutableStateOf(false) }

    LaunchedEffect(missionId, user?.uid) {
        db.collection("missions").document(missionId).get().addOnSuccessListener { doc ->
            if (doc.exists()) {
                imageUrl = doc.getString("imageUrl").orEmpty()
                mission = Mission(
                    id = doc.id,
                    title = doc.getString("title") ?: "제목 없는 미션",
                    desc = doc.getString("desc") ?: "",
                    points = doc.getLong("points")?.toInt() ?: 0,
                    category = doc.getString("category") ?: "투어"
                )
            }
        }

        user?.uid?.let { uid ->
            db.collection("user_missions")
                .whereEqualTo("userId", uid)
                .whereEqualTo("missionId", missionId)
                .get()
                .addOnSuccessListener { snapshot ->
                    val raw = snapshot.documents.firstOrNull()?.getString("status").orEmpty()
                    userMissionStatus = when {
                        raw.contains("완료") || raw.equals("Completed", true) -> "완료"
                        raw.contains("진행") || raw.equals("In Progress", true) -> "진행중"
                        else -> "미 진행"
                    }
                }
        }
    }

    Scaffold(
        containerColor = Color.White,
        topBar = {
            TopAppBar(
                title = { Text("미션 상세", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
            )
        }
    ) { innerPadding ->
        mission?.let { currentMission ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp)
                        .background(CardGray, RoundedCornerShape(24.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    if (imageUrl.isNotBlank()) {
                        AsyncImage(
                            model = imageUrl,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.Image, null, tint = Color.LightGray, modifier = Modifier.size(72.dp))
                            Spacer(modifier = Modifier.height(12.dp))
                            Text("미션 대표 이미지", color = Color.Gray)
                        }
                    }

                    Surface(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(14.dp),
                        color = missionStatusColor(userMissionStatus),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text(
                            text = userMissionStatus,
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = currentMission.title,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color(0xFF2C2C2C)
                    )
                    Text(
                        text = currentMission.desc.ifBlank { "미션 설명이 아직 등록되지 않았습니다." },
                        fontSize = 15.sp,
                        lineHeight = 22.sp,
                        color = Color.Gray
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    DetailChip(Icons.Default.Place, currentMission.category, LightPurple, MainPurple)
                    DetailChip(Icons.Default.EmojiEvents, "${currentMission.points}P", Color(0xFFFFF4E4), Orange)
                }

                Surface(
                    color = CardGray,
                    shape = RoundedCornerShape(22.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text("참여 방법", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        DetailStep(Icons.Default.Flag, "1. 미션 시작", "버튼을 눌러 미션을 시작하고 진행 상태를 저장합니다.")
                        DetailStep(Icons.Default.Place, "2. 장소 방문 또는 체험", "미션 설명에 맞는 장소를 방문하거나 행동을 수행합니다.")
                        DetailStep(Icons.Default.CheckCircle, "3. 인증 후 완료", "수행 화면으로 이동해 인증을 마치면 포인트가 지급됩니다.")
                    }
                }

                Button(
                    onClick = {
                        if (user == null) {
                            Toast.makeText(context, "로그인이 필요합니다.", Toast.LENGTH_SHORT).show()
                        } else if (userMissionStatus == "완료") {
                            Toast.makeText(context, "이미 완료한 미션입니다.", Toast.LENGTH_SHORT).show()
                        } else if (userMissionStatus == "진행중") {
                            onPerformMission(missionId)
                        } else {
                            isStarting = true
                            
                            val userMissionId = "\${user.uid}_\${currentMission.id}"
                            val userMissionRef = db.collection("user_missions")
                                .document(userMissionId)

                            val userMissionData = mapOf(
                                "userId" to user.uid,
                                "missionId" to currentMission.id,
                                "title" to currentMission.title,
                                "points" to currentMission.points,
                                "status" to "In Progress",
                                "progress" to 0f
                            )

                            userMissionStatus = "진행중"

                            userMissionRef
                                .set(userMissionData)
                                .addOnFailureListener {
                                    Toast.makeText(
                                        context,
                                        "미션 정보를 서버에 저장하지 못했습니다.",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }

                            isStarting = false
                            onPerformMission(missionId)


                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(18.dp),
                    enabled = !isStarting,
                    colors = ButtonDefaults.buttonColors(containerColor = MainPurple)
                ) {
                    if (isStarting) {
                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(22.dp), strokeWidth = 2.5.dp)
                    } else {
                        Text(
                            text = when (userMissionStatus) {
                                "완료" -> "완료된 미션"
                                "진행중" -> "미션 계속하기"
                                else -> "미션 시작하기"
                            },
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }
        } ?: Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(color = MainPurple)
        }
    }
}

@Composable
private fun DetailChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    containerColor: Color,
    contentColor: Color
) {
    Surface(color = containerColor, shape = RoundedCornerShape(14.dp)) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, null, tint = contentColor, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(text = text, color = contentColor, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        }
    }
}

@Composable
private fun DetailStep(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    body: String
) {
    Row(verticalAlignment = Alignment.Top) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .background(LightPurple, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = MainPurple, modifier = Modifier.size(18.dp))
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Text(body, color = Color.Gray, fontSize = 13.sp, lineHeight = 19.sp)
        }
    }
}

private fun missionStatusColor(status: String): Color {
    return when (status) {
        "진행중" -> Color(0xFF73BF69)
        "완료" -> MainPurple
        else -> Color(0xFFD9A14A)
    }
}
