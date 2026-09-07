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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.LocationOn
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.auth
import com.google.firebase.firestore.firestore
import smu.ai.graduation_project.ui.theme.LightPurple
import smu.ai.graduation_project.ui.theme.MainPurple
import smu.ai.graduation_project.ui.theme.Orange
import java.text.SimpleDateFormat
import java.util.Locale

private data class PointEntry(
    val missionTitle: String,
    val reason: String,
    val points: Int,
    val at: Timestamp?,
    val isPhotoStage: Boolean,
)

/**
 * 마이페이지 "보유 포인트" 를 누르면 보이는 포인트 적립 내역.
 *
 * 별도 원장(ledger) 컬렉션 없이 `user_missions` 문서에 이미 기록되는
 * `stage1RewardGranted/stage1RewardPoints/stage1VerifiedAt`,
 * `stage2RewardGranted/stage2RewardPoints/completedAt` 에서 재구성한다.
 * 미션명은 `missions/{missionId}` 에서 가져오며, 없으면 missionId 를 그대로 쓴다.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PointHistoryScreen(onNavigateBack: () -> Unit) {
    val uid = Firebase.auth.currentUser?.uid
    val db = Firebase.firestore

    var entries by remember { mutableStateOf<List<PointEntry>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(uid) {
        if (uid == null) {
            entries = emptyList()
            isLoading = false
            return@LaunchedEffect
        }

        db.collection("user_missions")
            .whereEqualTo("userId", uid)
            .get()
            .addOnSuccessListener { snapshot ->
                data class Raw(
                    val missionId: String,
                    val reason: String,
                    val points: Int,
                    val at: Timestamp?,
                    val isPhoto: Boolean,
                )

                val raw = mutableListOf<Raw>()
                snapshot.documents.forEach { doc ->
                    val missionId = doc.getString("missionId").orEmpty()
                    if (doc.getBoolean("stage1RewardGranted") == true) {
                        val p = doc.getLong("stage1RewardPoints")?.toInt() ?: 0
                        if (p > 0) raw += Raw(missionId, "위치 인증", p, doc.getTimestamp("stage1VerifiedAt"), false)
                    }
                    if (doc.getBoolean("stage2RewardGranted") == true) {
                        val p = doc.getLong("stage2RewardPoints")?.toInt() ?: 0
                        if (p > 0) raw += Raw(missionId, "사진 인증", p, doc.getTimestamp("completedAt"), true)
                    }
                }

                if (raw.isEmpty()) {
                    entries = emptyList()
                    isLoading = false
                    return@addOnSuccessListener
                }

                val missionIds = raw.map { it.missionId }.filter { it.isNotBlank() }.toSet()
                val titles = mutableMapOf<String, String>()
                var remaining = missionIds.size

                fun finish() {
                    entries = raw
                        .map {
                            PointEntry(
                                missionTitle = titles[it.missionId]
                                    ?: it.missionId.ifBlank { "미션" },
                                reason = it.reason,
                                points = it.points,
                                at = it.at,
                                isPhotoStage = it.isPhoto,
                            )
                        }
                        .sortedByDescending { it.at?.seconds ?: Long.MIN_VALUE }
                    isLoading = false
                }

                if (missionIds.isEmpty()) {
                    finish()
                    return@addOnSuccessListener
                }
                missionIds.forEach { id ->
                    db.collection("missions").document(id).get()
                        .addOnSuccessListener { m -> m.getString("title")?.let { titles[id] = it } }
                        .addOnCompleteListener {
                            remaining -= 1
                            if (remaining == 0) finish()
                        }
                }
            }
            .addOnFailureListener {
                entries = emptyList()
                isLoading = false
            }
    }

    Scaffold(
        containerColor = Color.White,
        topBar = {
            TopAppBar(
                title = { Text("포인트 내역", fontWeight = FontWeight.Bold) },
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
            isLoading -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator(color = MainPurple) }

            entries.isEmpty() -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("아직 받은 포인트가 없어요.", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("미션을 완료하면 여기에 적립 내역이 쌓입니다.", color = Color.Gray, fontSize = 13.sp)
                }
            }

            else -> LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = LightPurple,
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Column(modifier = Modifier.padding(20.dp)) {
                            Text("적립 포인트 합계", color = Color.Gray, fontSize = 13.sp)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = String.format(Locale.KOREA, "%,dP", entries.sumOf { it.points }),
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 26.sp,
                                color = MainPurple
                            )
                            Text("총 ${entries.size}건", color = Color.Gray, fontSize = 12.sp)
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                }
                items(entries) { entry -> PointHistoryRow(entry) }
            }
        }
    }
}

@Composable
private fun PointHistoryRow(entry: PointEntry) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color.White,
        shadowElevation = 1.dp,
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                if (entry.isPhotoStage) Icons.Default.CameraAlt else Icons.Default.LocationOn,
                contentDescription = null,
                tint = if (entry.isPhotoStage) Orange else MainPurple,
                modifier = Modifier.width(24.dp)
            )
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(entry.missionTitle, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color(0xFF2B2B2B))
                Text(
                    text = buildString {
                        append(entry.reason)
                        formatDate(entry.at)?.let { append(" · "); append(it) }
                    },
                    color = Color.Gray,
                    fontSize = 12.sp
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = String.format(Locale.KOREA, "+%,dP", entry.points),
                fontWeight = FontWeight.ExtraBold,
                fontSize = 15.sp,
                color = MainPurple
            )
        }
    }
}

private fun formatDate(ts: Timestamp?): String? =
    ts?.toDate()?.let { SimpleDateFormat("yyyy.MM.dd", Locale.KOREA).format(it) }
