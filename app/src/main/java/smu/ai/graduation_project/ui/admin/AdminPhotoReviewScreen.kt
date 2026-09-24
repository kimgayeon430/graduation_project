package smu.ai.graduation_project.ui.admin

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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.google.firebase.Firebase
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.firestore
import smu.ai.graduation_project.R
import smu.ai.graduation_project.domain.MissionCompletion
import smu.ai.graduation_project.ui.theme.CardGray
import smu.ai.graduation_project.ui.theme.MainPurple

/** 자동 사진 판정이 애매해 `photoNeedsReview` 로 완료된 미션 1건. */
private data class PhotoReviewItem(
    val docId: String,
    val missionTitle: String,
    val missionCategory: String,
    val userName: String,
    val photoUrl: String,
    val verifyScore: Double,
    val verifyLabel: String,
    val modelVersion: String,
    /** 미션 대표 이미지와의 코사인 유사도. 참조 임베딩이 없던 미션이면 null. */
    val verifySimilarity: Double?,
    val stage2Points: Int
)

/**
 * 관리자 사진 검수 큐. `user_missions` 에서 `photoNeedsReview == true` 인 완료 건을 모아
 * 사진과 모델 판정 근거를 보여 주고 승인/반려한다.
 *
 * - 승인: `photoNeedsReview=false`, `photoVerified=true`. (포인트는 이미 지급됨)
 * - 반려: 2단계 보상을 회수하고 미션을 다시 `In Progress` 로 되돌려 재인증하게 한다.
 *
 * 다른 admin 화면과 같이 Firestore 를 화면에서 직접 다룬다.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminPhotoReviewScreen(onNavigateBack: () -> Unit) {
    val db = Firebase.firestore
    val context = LocalContext.current
    var queue by remember { mutableStateOf<List<PhotoReviewItem>>(emptyList()) }
    var rejectTarget by remember { mutableStateOf<PhotoReviewItem?>(null) }
    var rejectReason by remember { mutableStateOf("") }
    val deletedMissionPlaceholder = stringResource(R.string.admin_photo_review_deleted_mission)
    val defaultUserName = stringResource(R.string.admin_user_default_name)

    LaunchedEffect(Unit) {
        db.collection("user_missions")
            .whereEqualTo("photoNeedsReview", true)
            .addSnapshotListener { snapshot, _ ->
                val docs = snapshot?.documents.orEmpty()
                if (docs.isEmpty()) {
                    queue = emptyList()
                    return@addSnapshotListener
                }
                db.collection("missions").get().addOnSuccessListener { missionSnapshot ->
                    val missionById = missionSnapshot.documents.associateBy { it.id }
                    db.collection("users").get().addOnSuccessListener { userSnapshot ->
                        val userById = userSnapshot.documents.associateBy { it.id }
                        queue = docs.map { doc ->
                            val mission = doc.getString("missionId")?.let { missionById[it] }
                            val user = doc.getString("userId")?.let { userById[it] }
                            PhotoReviewItem(
                                docId = doc.id,
                                missionTitle = mission?.getString("title") ?: deletedMissionPlaceholder,
                                missionCategory = mission?.getString("category") ?: "-",
                                userName = user?.getString("nickname") ?: user?.getString("name") ?: defaultUserName,
                                photoUrl = doc.getString("photoUrl").orEmpty(),
                                verifyScore = doc.getDouble("photoVerifyScore") ?: 0.0,
                                verifyLabel = doc.getString("photoVerifyLabel").orEmpty(),
                                modelVersion = doc.getString("photoVerifyModelVersion").orEmpty(),
                                verifySimilarity = doc.getDouble("photoVerifySimilarity"),
                                stage2Points = doc.getLong("stage2RewardPoints")?.toInt() ?: 0
                            )
                        }
                    }
                }
            }
    }

    val approvedMessage = stringResource(R.string.admin_toast_approved)
    val rejectedMessage = stringResource(R.string.admin_toast_rejected)
    val alreadyProcessedMessage = stringResource(R.string.admin_toast_already_processed)
    val rejectFailedMessage = stringResource(R.string.admin_toast_reject_failed)

    fun approve(item: PhotoReviewItem) {
        db.collection("user_missions").document(item.docId).update(
            mapOf(
                "photoNeedsReview" to false,
                "photoVerified" to true,
                "photoReviewedAt" to FieldValue.serverTimestamp()
            )
        ).addOnSuccessListener {
            android.widget.Toast.makeText(context, approvedMessage, android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    fun reject(item: PhotoReviewItem, reason: String) {
        val userMissionRef = db.collection("user_missions").document(item.docId)
        db.runTransaction { transaction ->
            val snapshot = transaction.get(userMissionRef)
            if (snapshot.getBoolean("photoNeedsReview") != true) return@runTransaction false
            val uid = snapshot.getString("userId") ?: return@runTransaction false
            val granted = snapshot.getLong("stage2RewardPoints")?.toInt() ?: 0
            val userRef = db.collection("users").document(uid)
            val currentPoints = transaction.get(userRef).getLong("points")?.toInt() ?: 0

            transaction.update(
                userMissionRef,
                mapOf(
                    "photoNeedsReview" to false,
                    "photoVerified" to false,
                    "status" to MissionCompletion.STATUS_IN_PROGRESS,
                    "progress" to 0.5f,
                    "stage2RewardGranted" to false,
                    "photoUrl" to "",
                    "photoStoragePath" to "",
                    "photoRejectReason" to reason,
                    "photoReviewedAt" to FieldValue.serverTimestamp()
                )
            )
            // 2단계 보상 회수 (0 미만으로는 내려가지 않게)
            transaction.set(userRef, mapOf("points" to maxOf(0, currentPoints - granted)), SetOptions.merge())
            true
        }.addOnSuccessListener { done ->
            val message = if (done == true) rejectedMessage else alreadyProcessedMessage
            android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_SHORT).show()
        }.addOnFailureListener {
            android.widget.Toast.makeText(context, rejectFailedMessage, android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.admin_photo_review_title), fontWeight = FontWeight.Bold) },
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
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                Text(stringResource(R.string.admin_photo_review_empty), color = Color.Gray)
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Text(
                        stringResource(R.string.admin_photo_review_queue_count, queue.size),
                        color = Color.Gray,
                        fontSize = 13.sp
                    )
                }
                items(queue) { item ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = CardGray),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(item.missionTitle, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                            Text(
                                "${item.userName} · ${item.missionCategory} · ${item.stage2Points}P",
                                color = Color.Gray,
                                fontSize = 13.sp
                            )

                            if (item.photoUrl.isNotBlank()) {
                                AsyncImage(
                                    model = item.photoUrl,
                                    contentDescription = stringResource(R.string.admin_photo_review_photo_desc),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(220.dp)
                                        .background(Color.White, RoundedCornerShape(12.dp))
                                )
                            }

                            Surface(color = Color.White, shape = RoundedCornerShape(10.dp)) {
                                Text(
                                    stringResource(R.string.admin_photo_review_model_label, item.verifyLabel.ifBlank { "-" }) + " · " +
                                        stringResource(R.string.admin_photo_review_score_label, item.missionCategory, item.verifyScore) +
                                        (item.verifySimilarity?.let { stringResource(R.string.admin_photo_review_similarity_label, it) } ?: "") +
                                        (if (item.modelVersion.isNotBlank()) " · ${item.modelVersion}" else ""),
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                    color = Color(0xFF444444),
                                    fontSize = 12.sp
                                )
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(
                                    onClick = {
                                        rejectReason = ""
                                        rejectTarget = item
                                    },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(stringResource(R.string.admin_photo_review_reject), color = Color(0xFFD9534F))
                                }
                                Button(
                                    onClick = { approve(item) },
                                    colors = ButtonDefaults.buttonColors(containerColor = MainPurple),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(stringResource(R.string.admin_photo_review_approve))
                                }
                            }
                        }
                    }
                }
                item { Spacer(modifier = Modifier.height(8.dp)) }
            }
        }
    }

    rejectTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { rejectTarget = null },
            title = { Text(stringResource(R.string.admin_photo_review_reject_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.admin_photo_review_reject_body, target.missionTitle, target.stage2Points))
                    OutlinedTextField(
                        value = rejectReason,
                        onValueChange = { rejectReason = it },
                        label = { Text(stringResource(R.string.admin_photo_review_reject_reason_label)) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    reject(target, rejectReason.trim())
                    rejectTarget = null
                }) {
                    Text(stringResource(R.string.admin_photo_review_reject), color = Color(0xFFD9534F))
                }
            },
            dismissButton = {
                TextButton(onClick = { rejectTarget = null }) { Text(stringResource(R.string.admin_cancel)) }
            }
        )
    }
}
