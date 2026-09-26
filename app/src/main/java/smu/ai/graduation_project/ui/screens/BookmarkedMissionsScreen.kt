package smu.ai.graduation_project.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Image
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.firestore
import smu.ai.graduation_project.R
import smu.ai.graduation_project.data.LanguagePreference
import smu.ai.graduation_project.data.MissionEngagement
import smu.ai.graduation_project.data.localizedString
import smu.ai.graduation_project.model.Mission
import smu.ai.graduation_project.ui.components.categoryLabel
import smu.ai.graduation_project.ui.theme.CardGray
import smu.ai.graduation_project.ui.theme.MainPurple
import smu.ai.graduation_project.ui.theme.Orange

/** "찜한 미션" 전용 화면. `mission_bookmarks` 에서 본인 문서를 모아 해당 미션들을 보여준다. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookmarkedMissionsScreen(
    onNavigateBack: () -> Unit,
    onMissionClick: (String) -> Unit
) {
    val db = Firebase.firestore
    val uid = Firebase.auth.currentUser?.uid
    val context = LocalContext.current
    var missions by remember { mutableStateOf<List<Mission>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    val missionTitlePlaceholder = stringResource(R.string.mission_no_title)
    val engagementFailedMessage = stringResource(R.string.engagement_toast_failed)

    LaunchedEffect(uid) {
        val currentUid = uid
        if (currentUid == null) {
            loading = false
            return@LaunchedEffect
        }
        db.collection("mission_bookmarks")
            .whereEqualTo("userId", currentUid)
            .get()
            .addOnSuccessListener { bookmarkSnapshot ->
                val missionIds = bookmarkSnapshot.documents.mapNotNull { it.getString("missionId") }
                if (missionIds.isEmpty()) {
                    missions = emptyList()
                    loading = false
                    return@addOnSuccessListener
                }
                val chunks = missionIds.chunked(10)
                val results = mutableListOf<Mission>()
                var remaining = chunks.size
                chunks.forEach { chunk ->
                    db.collection("missions")
                        .whereIn(FieldPath.documentId(), chunk)
                        .get()
                        .addOnSuccessListener { missionSnapshot ->
                            missionSnapshot.documents.forEach { doc ->
                                val location = doc.getGeoPoint("location")
                                results.add(
                                    Mission(
                                        id = doc.id,
                                        title = doc.localizedString("title", LanguagePreference.current, missionTitlePlaceholder),
                                        desc = doc.localizedString("desc", LanguagePreference.current),
                                        points = doc.getLong("points")?.toInt() ?: 0,
                                        category = doc.getString("category") ?: "투어",
                                        imageUrl = doc.getString("imageUrl").orEmpty(),
                                        latitude = location?.latitude,
                                        longitude = location?.longitude,
                                        bookmarkCount = doc.getLong("bookmarkCount")?.toInt() ?: 0,
                                        isBookmarkedByMe = true
                                    )
                                )
                            }
                            remaining--
                            if (remaining == 0) {
                                missions = results
                                loading = false
                            }
                        }
                        .addOnFailureListener {
                            remaining--
                            if (remaining == 0) {
                                missions = results
                                loading = false
                            }
                        }
                }
            }
            .addOnFailureListener { loading = false }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.bookmarked_missions_title), fontWeight = FontWeight.Bold) },
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
        if (loading) {
            Box(Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = MainPurple)
            }
        } else if (missions.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.bookmarked_missions_empty), color = Color.Gray)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(missions, key = { it.id }) { mission ->
                    BookmarkedMissionCard(
                        mission = mission,
                        onClick = { onMissionClick(mission.id) },
                        onUnbookmark = {
                            val currentUid = uid ?: return@BookmarkedMissionCard
                            MissionEngagement.setBookmarked(db, currentUid, mission.id, bookmarked = false,
                                onComplete = {
                                    missions = missions.filterNot { it.id == mission.id }
                                },
                                onError = {
                                    Toast.makeText(context, engagementFailedMessage, Toast.LENGTH_SHORT).show()
                                }
                            )
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun BookmarkedMissionCard(mission: Mission, onClick: () -> Unit, onUnbookmark: () -> Unit) {
    Surface(onClick = onClick, shape = RoundedCornerShape(18.dp), color = Color.White, shadowElevation = 4.dp) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(width = 74.dp, height = 74.dp)
                    .background(CardGray, RoundedCornerShape(12.dp))
                    .clip(RoundedCornerShape(12.dp))
            ) {
                if (mission.imageUrl.isNotBlank()) {
                    AsyncImage(model = mission.imageUrl, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                } else {
                    Icon(Icons.Default.Image, null, tint = Color.LightGray, modifier = Modifier.size(30.dp).align(Alignment.Center))
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(mission.title, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color(0xFF303030))
                Text(categoryLabel(mission.category), color = Color.Gray, fontSize = 12.sp)
                Text("${mission.points}P", color = Orange, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
            IconButton(onClick = onUnbookmark) {
                Icon(Icons.Default.Bookmark, contentDescription = null, tint = MainPurple)
            }
        }
    }
}
