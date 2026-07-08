package smu.ai.graduation_project.ui.admin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.Firebase
import com.google.firebase.firestore.firestore
import smu.ai.graduation_project.model.Mission
import smu.ai.graduation_project.ui.theme.CardGray
import smu.ai.graduation_project.ui.theme.MainPurple

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminMissionListScreen(
    onNavigateBack: () -> Unit,
    onAddMission: () -> Unit,
    onEditMission: (String) -> Unit
) {
    val db = Firebase.firestore
    var missions by remember { mutableStateOf<List<Mission>>(emptyList()) }
    var missionToDelete by remember { mutableStateOf<Mission?>(null) }

    LaunchedEffect(Unit) {
        db.collection("missions").addSnapshotListener { snapshot, _ ->
            missions = snapshot?.documents?.map { doc ->
                Mission(
                    id = doc.id,
                    title = doc.getString("title") ?: "제목 없는 미션",
                    desc = doc.getString("desc") ?: "",
                    points = doc.getLong("points")?.toInt() ?: 0,
                    category = doc.getString("category") ?: "투어",
                    imageUrl = doc.getString("imageUrl").orEmpty()
                )
            }.orEmpty()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("미션 관리", fontWeight = FontWeight.Bold) },
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
        androidx.compose.foundation.lazy.LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Button(
                    onClick = onAddMission,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = MainPurple)
                ) {
                    Icon(Icons.Default.Add, null)
                    Spacer(modifier = Modifier.padding(4.dp))
                    Text("새 미션 추가")
                }
            }

            items(missions.size) { index ->
                val mission = missions[index]
                Card(
                    colors = CardDefaults.cardColors(containerColor = CardGray),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(mission.title, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Text(mission.desc, color = Color.Gray, fontSize = 13.sp)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Surface(color = Color.White) {
                                Text(mission.category, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp), color = MainPurple, fontWeight = FontWeight.Bold)
                            }
                            Text("${mission.points}P", color = Color(0xFF444444), fontWeight = FontWeight.Bold)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = { onEditMission(mission.id) },
                                colors = ButtonDefaults.buttonColors(containerColor = MainPurple),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Edit, null)
                                Spacer(modifier = Modifier.padding(2.dp))
                                Text("수정")
                            }
                            Button(
                                onClick = { missionToDelete = mission },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD9534F)),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Delete, null)
                                Spacer(modifier = Modifier.padding(2.dp))
                                Text("삭제")
                            }
                        }
                    }
                }
            }
        }
    }

    missionToDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { missionToDelete = null },
            title = { Text("미션 삭제") },
            text = { Text("'${target.title}' 미션을 삭제할까요?") },
            confirmButton = {
                TextButton(onClick = {
                    db.collection("missions").document(target.id).delete()
                    missionToDelete = null
                }) {
                    Text("삭제")
                }
            },
            dismissButton = {
                TextButton(onClick = { missionToDelete = null }) {
                    Text("취소")
                }
            }
        )
    }
}
