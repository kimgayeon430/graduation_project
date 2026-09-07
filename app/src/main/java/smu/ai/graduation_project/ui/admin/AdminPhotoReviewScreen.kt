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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.google.firebase.Firebase
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.firestore
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
                                missionTitle = mission?.getString("title") ?: "삭제된 미션",
                                missionCategory = mission?.getString("category") ?: "-",
                                userName = user?.getString("nickname") ?: user?.getString("name") ?: "사용자",
                                photoUrl = doc.getString("photoUrl").orEmpty(),
                                verifyScore = doc.getDouble("photoVerifyScore") ?: 0.0,
                                verifyLabel = doc.getString("photoVerifyLabel").orEmpty(),
                                modelVersion = doc.getString("photoVerifyModelVersion").orEmpty(),
                                stage2Points = doc.getLong("stage2RewardPoints")?.toInt() ?: 0
                            )
                        }
                    }
                }
            }
    }

    fun approve(item: PhotoReviewItem) {
        db.collection("user_missions").document(item.docId).update(
            mapOf(
                "photoNeedsReview" to false,
                "photoVerified" to true,
                "photoReviewedAt" to FieldValue.serverTimestamp()
            )
        ).addOnSuccessListener {
            android.widget.Toast.makeText(context, "승인했습니다.", android.widget.Toast.LENGTH_SHORT).show()
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
            val message = if (done == true) "반려했습니다. 사용자에게 재인증이 요청됩니다." else "이미 처리된 건입니다."
            android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_SHORT).show()
        }.addOnFailureListener {
            android.widget.Toast.makeText(context, "반려 처리에 실패했습니다.", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("사진 검수", fontWeight = FontWeight.Bold) },
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
                Text("검수할 사진이 없습니다.", color = Color.Gray)
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
                        "자동 판정이 애매한 ${queue.size}건",
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
                                    contentDescription = "인증 사진",
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(220.dp)
                                        .background(Color.White, RoundedCornerShape(12.dp))
                                )
                            }

                            Surface(color = Color.White, shape = RoundedCornerShape(10.dp)) {
                                Text(
                                    "모델 판정: ${item.verifyLabel.ifBlank { "-" }} · " +
                                        "'${item.missionCategory}' 점수 %.2f".format(item.verifyScore) +
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
                                    Text("반려", color = Color(0xFFD9534F))
                                }
                                Button(
                                    onClick = { approve(item) },
                                    colors = ButtonDefaults.buttonColors(containerColor = MainPurple),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("승인")
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
            title = { Text("사진 반려") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("'${target.missionTitle}' 인증을 반려하면 ${target.stage2Points}P가 회수되고 사용자는 다시 사진 인증을 해야 합니다.")
                    OutlinedTextField(
                        value = rejectReason,
                        onValueChange = { rejectReason = it },
                        label = { Text("반려 사유 (선택)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    reject(target, rejectReason.trim())
                    rejectTarget = null
                }) {
                    Text("반려", color = Color(0xFFD9534F))
                }
            },
            dismissButton = {
                TextButton(onClick = { rejectTarget = null }) { Text("취소") }
            }
        )
    }
}
