package smu.ai.graduation_project.ui.admin

import android.widget.Toast
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.google.firebase.Firebase
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.firestore
import smu.ai.graduation_project.R
import smu.ai.graduation_project.domain.MissionReviewStatus
import smu.ai.graduation_project.ui.theme.CardGray
import smu.ai.graduation_project.ui.theme.MainPurple

/** 검수 대기(`reviewStatus == pending`) 중인 사용자 제안 미션 1건. */
private data class MissionProposal(
    val id: String,
    val title: String,
    val desc: String,
    val category: String,
    val imageUrl: String,
    val estimatedMinutes: Int?,
    val creatorName: String
)

/**
 * 관리자 미션 제안 검수 큐. `AdminPhotoReviewScreen` 과 같은 구조(LazyColumn + 카드 + 승인/반려
 * 다이얼로그)를 그대로 따른다.
 *
 * - 승인: 관리자가 이 시점에 최종 포인트를 정하고 `points`/`reviewStatus=approved` 로 갱신한다
 *   (사용자는 제안 시 포인트를 정할 수 없다 — `firestore.rules` 로도 강제됨).
 * - 수정 요청/반려: 사유를 남기고 `reviewStatus`/`reviewNote` 를 갱신한다. 제안자는 "내가 만든 미션"
 *   화면에서 사유를 보고, 수정 요청 건만 수정 후 재제출할 수 있다.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminMissionReviewScreen(onNavigateBack: () -> Unit) {
    val db = Firebase.firestore
    val context = LocalContext.current
    var queue by remember { mutableStateOf<List<MissionProposal>>(emptyList()) }
    var approveTarget by remember { mutableStateOf<MissionProposal?>(null) }
    var approvePoints by remember { mutableStateOf("100") }
    var changesTarget by remember { mutableStateOf<MissionProposal?>(null) }
    var rejectTarget by remember { mutableStateOf<MissionProposal?>(null) }
    var reason by remember { mutableStateOf("") }
    val defaultUserName = stringResource(R.string.admin_user_default_name)

    LaunchedEffect(Unit) {
        db.collection("missions")
            .whereEqualTo("reviewStatus", MissionReviewStatus.PENDING)
            .addSnapshotListener { snapshot, _ ->
                queue = snapshot?.documents?.map { doc ->
                    MissionProposal(
                        id = doc.id,
                        title = doc.getString("title").orEmpty(),
                        desc = doc.getString("desc").orEmpty(),
                        category = doc.getString("category") ?: "투어",
                        imageUrl = doc.getString("imageUrl").orEmpty(),
                        estimatedMinutes = doc.getLong("estimatedMinutes")?.toInt(),
                        creatorName = doc.getString("creatorName")?.takeIf { it.isNotBlank() } ?: defaultUserName
                    )
                }.orEmpty()
            }
    }

    val approvedMessage = stringResource(R.string.admin_toast_approved)
    val savedMessage = stringResource(R.string.admin_mission_review_toast_saved)
    val invalidPointsMessage = stringResource(R.string.admin_mission_review_toast_invalid_points)
    val reasonRequiredMessage = stringResource(R.string.admin_mission_review_toast_reason_required)
    val failedMessage = stringResource(R.string.admin_toast_save_failed)

    fun approve(item: MissionProposal, points: Int) {
        db.collection("missions").document(item.id).set(
            mapOf(
                "points" to points,
                "reviewStatus" to MissionReviewStatus.APPROVED,
                "reviewNote" to FieldValue.delete()
            ),
            SetOptions.merge()
        ).addOnSuccessListener {
            Toast.makeText(context, approvedMessage, Toast.LENGTH_SHORT).show()
        }.addOnFailureListener {
            Toast.makeText(context, failedMessage, Toast.LENGTH_SHORT).show()
        }
    }

    fun decide(item: MissionProposal, status: String, note: String) {
        db.collection("missions").document(item.id).set(
            mapOf("reviewStatus" to status, "reviewNote" to note),
            SetOptions.merge()
        ).addOnSuccessListener {
            Toast.makeText(context, savedMessage, Toast.LENGTH_SHORT).show()
        }.addOnFailureListener {
            Toast.makeText(context, failedMessage, Toast.LENGTH_SHORT).show()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.admin_mission_review_title), fontWeight = FontWeight.Bold) },
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
        if (queue.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                Text(stringResource(R.string.admin_mission_review_empty), color = Color.Gray)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(queue, key = { it.id }) { item ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = CardGray),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(item.title, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                            Text(
                                stringResource(R.string.admin_mission_proposed_by, item.creatorName) + " · " + item.category +
                                    (item.estimatedMinutes?.let { " · ${it}${stringResource(R.string.admin_mission_review_minutes_suffix)}" } ?: ""),
                                color = Color.Gray,
                                fontSize = 13.sp
                            )
                            Text(item.desc, color = Color(0xFF444444), fontSize = 13.sp)

                            if (item.imageUrl.isNotBlank()) {
                                AsyncImage(
                                    model = item.imageUrl,
                                    contentDescription = null,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(180.dp)
                                        .background(Color.White, RoundedCornerShape(12.dp))
                                )
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(
                                    onClick = { rejectTarget = item; reason = "" },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(stringResource(R.string.admin_mission_review_reject), color = Color(0xFFD9534F))
                                }
                                OutlinedButton(
                                    onClick = { changesTarget = item; reason = "" },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(stringResource(R.string.admin_mission_review_request_changes), color = MainPurple)
                                }
                                Button(
                                    onClick = { approveTarget = item; approvePoints = "100" },
                                    colors = ButtonDefaults.buttonColors(containerColor = MainPurple),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(stringResource(R.string.admin_photo_review_approve))
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    approveTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { approveTarget = null },
            title = { Text(stringResource(R.string.admin_mission_review_approve_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.admin_mission_review_approve_body, target.title))
                    OutlinedTextField(
                        value = approvePoints,
                        onValueChange = { approvePoints = it.filter { ch -> ch.isDigit() } },
                        label = { Text(stringResource(R.string.admin_mission_field_points)) },
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val points = approvePoints.toIntOrNull()
                    if (points == null || points <= 0) {
                        Toast.makeText(context, invalidPointsMessage, Toast.LENGTH_SHORT).show()
                        return@TextButton
                    }
                    approve(target, points)
                    approveTarget = null
                }) {
                    Text(stringResource(R.string.admin_photo_review_approve))
                }
            },
            dismissButton = {
                TextButton(onClick = { approveTarget = null }) { Text(stringResource(R.string.admin_cancel)) }
            }
        )
    }

    changesTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { changesTarget = null },
            title = { Text(stringResource(R.string.admin_mission_review_request_changes_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.admin_mission_review_reason_body, target.title))
                    OutlinedTextField(
                        value = reason,
                        onValueChange = { reason = it },
                        label = { Text(stringResource(R.string.admin_mission_review_reason_label)) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (reason.isBlank()) {
                        Toast.makeText(context, reasonRequiredMessage, Toast.LENGTH_SHORT).show()
                        return@TextButton
                    }
                    decide(target, MissionReviewStatus.CHANGES_REQUESTED, reason.trim())
                    changesTarget = null
                }) {
                    Text(stringResource(R.string.admin_mission_review_request_changes))
                }
            },
            dismissButton = {
                TextButton(onClick = { changesTarget = null }) { Text(stringResource(R.string.admin_cancel)) }
            }
        )
    }

    rejectTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { rejectTarget = null },
            title = { Text(stringResource(R.string.admin_mission_review_reject_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.admin_mission_review_reason_body, target.title))
                    OutlinedTextField(
                        value = reason,
                        onValueChange = { reason = it },
                        label = { Text(stringResource(R.string.admin_mission_review_reason_label)) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (reason.isBlank()) {
                        Toast.makeText(context, reasonRequiredMessage, Toast.LENGTH_SHORT).show()
                        return@TextButton
                    }
                    decide(target, MissionReviewStatus.REJECTED, reason.trim())
                    rejectTarget = null
                }) {
                    Text(stringResource(R.string.admin_mission_review_reject), color = Color(0xFFD9534F))
                }
            },
            dismissButton = {
                TextButton(onClick = { rejectTarget = null }) { Text(stringResource(R.string.admin_cancel)) }
            }
        )
    }
}
