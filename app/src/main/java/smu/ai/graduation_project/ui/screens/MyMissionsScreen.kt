package smu.ai.graduation_project.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.firestore
import smu.ai.graduation_project.R
import smu.ai.graduation_project.domain.MissionReviewStatus
import smu.ai.graduation_project.ui.components.categoryLabel
import smu.ai.graduation_project.ui.theme.CardGray
import smu.ai.graduation_project.ui.theme.MainPurple
import smu.ai.graduation_project.ui.theme.Orange

private data class MyMissionItem(
    val id: String,
    val title: String,
    val category: String,
    val reviewStatus: String,
    val reviewNote: String?,
    val points: Int,
    val likeCount: Int,
    val bookmarkCount: Int,
    val completionCount: Int
)

/**
 * "내가 만든 미션" 전용 화면. 마이페이지에서는 진입점만 두고 여기서 제안 상태 확인·재제출·
 * 제안하기를 모두 처리한다.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyMissionsScreen(
    onNavigateBack: () -> Unit,
    onProposeNew: () -> Unit,
    onEditProposal: (String) -> Unit,
    onViewApprovedMission: (String) -> Unit
) {
    val db = Firebase.firestore
    val uid = Firebase.auth.currentUser?.uid
    var missions by remember { mutableStateOf<List<MyMissionItem>>(emptyList()) }

    LaunchedEffect(uid) {
        val currentUid = uid ?: return@LaunchedEffect
        db.collection("missions")
            .whereEqualTo("creatorId", currentUid)
            .addSnapshotListener { snapshot, _ ->
                missions = snapshot?.documents?.map { doc ->
                    MyMissionItem(
                        id = doc.id,
                        title = doc.getString("title").orEmpty(),
                        category = doc.getString("category") ?: "투어",
                        reviewStatus = doc.getString("reviewStatus") ?: MissionReviewStatus.APPROVED,
                        reviewNote = doc.getString("reviewNote"),
                        points = doc.getLong("points")?.toInt() ?: 0,
                        likeCount = doc.getLong("likeCount")?.toInt() ?: 0,
                        bookmarkCount = doc.getLong("bookmarkCount")?.toInt() ?: 0,
                        completionCount = doc.getLong("completionCount")?.toInt() ?: 0
                    )
                }.orEmpty()
            }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.my_missions_title), fontWeight = FontWeight.Bold) },
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
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Button(
                    onClick = onProposeNew,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = MainPurple)
                ) {
                    Icon(Icons.Default.Add, null)
                    Text("  " + stringResource(R.string.my_missions_propose_new))
                }
            }

            if (missions.isEmpty()) {
                item {
                    Box(Modifier.fillMaxWidth().padding(vertical = 40.dp), contentAlignment = Alignment.Center) {
                        Text(stringResource(R.string.my_missions_empty), color = Color.Gray)
                    }
                }
            }

            items(missions, key = { it.id }) { mission ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = CardGray),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(mission.title, fontWeight = FontWeight.Bold, fontSize = 17.sp, modifier = Modifier.weight(1f))
                            ReviewStatusBadge(mission.reviewStatus)
                        }
                        Text(categoryLabel(mission.category), color = Color.Gray, fontSize = 13.sp)

                        when (mission.reviewStatus) {
                            MissionReviewStatus.PENDING -> {
                                Text(stringResource(R.string.my_missions_pending_hint), color = Color.Gray, fontSize = 12.sp)
                            }
                            MissionReviewStatus.CHANGES_REQUESTED -> {
                                Surface(color = Color.White, shape = RoundedCornerShape(10.dp)) {
                                    Text(
                                        stringResource(R.string.my_missions_review_note, mission.reviewNote.orEmpty()),
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                        color = Color(0xFF444444),
                                        fontSize = 12.sp
                                    )
                                }
                                OutlinedButton(onClick = { onEditProposal(mission.id) }, modifier = Modifier.fillMaxWidth()) {
                                    Text(stringResource(R.string.my_missions_edit_resubmit), color = MainPurple)
                                }
                            }
                            MissionReviewStatus.REJECTED -> {
                                Surface(color = Color.White, shape = RoundedCornerShape(10.dp)) {
                                    Text(
                                        stringResource(R.string.my_missions_review_note, mission.reviewNote.orEmpty()),
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                        color = Color(0xFF444444),
                                        fontSize = 12.sp
                                    )
                                }
                            }
                            else -> {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                                        CreatorStat(Icons.Default.Favorite, mission.likeCount, Orange)
                                        CreatorStat(Icons.Default.Bookmark, mission.bookmarkCount, MainPurple)
                                        CreatorStat(Icons.Default.Flag, mission.completionCount, Color(0xFF4E9A4B))
                                    }
                                    Text("${mission.points}P", fontWeight = FontWeight.Bold, color = Color(0xFF444444))
                                }
                                OutlinedButton(onClick = { onViewApprovedMission(mission.id) }, modifier = Modifier.fillMaxWidth()) {
                                    Text(stringResource(R.string.my_missions_view_detail), color = MainPurple)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CreatorStat(icon: androidx.compose.ui.graphics.vector.ImageVector, count: Int, tint: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(14.dp))
        Text(" $count", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF444444))
    }
}

@Composable
private fun ReviewStatusBadge(status: String) {
    val (labelRes, color) = when (status) {
        MissionReviewStatus.PENDING -> R.string.review_status_pending to Color(0xFFD6A248)
        MissionReviewStatus.CHANGES_REQUESTED -> R.string.review_status_changes_requested to Color(0xFF5E7BD9)
        MissionReviewStatus.REJECTED -> R.string.review_status_rejected to Color(0xFFD9534F)
        else -> R.string.review_status_approved to Color(0xFF4E9A4B)
    }
    Surface(color = color, shape = RoundedCornerShape(10.dp)) {
        Text(
            stringResource(labelRes),
            color = Color.White,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}
