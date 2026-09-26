package smu.ai.graduation_project.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.Uri
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
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.firestore
import smu.ai.graduation_project.R
import smu.ai.graduation_project.data.LanguagePreference
import smu.ai.graduation_project.data.MissionEngagement
import smu.ai.graduation_project.data.localizedString
import smu.ai.graduation_project.domain.GeoDistance
import smu.ai.graduation_project.model.Mission
import smu.ai.graduation_project.ui.components.categoryLabel
import smu.ai.graduation_project.ui.components.missionStatusLabel
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
    var userLatLng by remember { mutableStateOf<Pair<Double, Double>?>(null) }
    var isLiked by remember { mutableStateOf(false) }
    var isBookmarked by remember { mutableStateOf(false) }
    val missionTitlePlaceholder = stringResource(R.string.mission_no_title)
    val toastLoginRequired = stringResource(R.string.toast_login_required)
    val engagementFailedMessage = stringResource(R.string.engagement_toast_failed)
    val toastAlreadyCompleted = stringResource(R.string.toast_already_completed)
    val toastSaveFailed = stringResource(R.string.toast_save_failed)
    val toastNoMapApp = stringResource(R.string.toast_no_map_app)

    // 위치 권한이 이미 허용돼 있으면 마지막 known location 을 읽어 목표 지점까지의 거리를 보여준다.
    // (HomeScreen 의 추천 카드 거리 계산과 같은 방식 — 여기서 새로 권한을 요청하지는 않는다.)
    LaunchedEffect(Unit) {
        val granted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) return@LaunchedEffect
        val lm = context.getSystemService(LocationManager::class.java) ?: return@LaunchedEffect
        val best = try {
            lm.getProviders(true).mapNotNull { lm.getLastKnownLocation(it) }.maxByOrNull { it.time }
        } catch (e: SecurityException) {
            null
        }
        if (best != null) userLatLng = best.latitude to best.longitude
    }

    LaunchedEffect(missionId, user?.uid) {
        db.collection("missions").document(missionId).get().addOnSuccessListener { doc ->
            if (doc.exists()) {
                imageUrl = doc.getString("imageUrl").orEmpty()
                val location = doc.getGeoPoint("location")
                mission = Mission(
                    id = doc.id,
                    title = doc.localizedString("title", LanguagePreference.current, missionTitlePlaceholder),
                    desc = doc.localizedString("desc", LanguagePreference.current),
                    points = doc.getLong("points")?.toInt() ?: 0,
                    category = doc.getString("category") ?: "투어",
                    latitude = location?.latitude,
                    longitude = location?.longitude,
                    likeCount = doc.getLong("likeCount")?.toInt() ?: 0,
                    bookmarkCount = doc.getLong("bookmarkCount")?.toInt() ?: 0
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
            db.collection("mission_likes").document("${uid}_$missionId").get()
                .addOnSuccessListener { isLiked = it.exists() }
            db.collection("mission_bookmarks").document("${uid}_$missionId").get()
                .addOnSuccessListener { isBookmarked = it.exists() }
        }
    }

    fun toggleLike() {
        val uid = user?.uid ?: run {
            Toast.makeText(context, toastLoginRequired, Toast.LENGTH_SHORT).show()
            return
        }
        val nowLiked = !isLiked
        isLiked = nowLiked
        mission = mission?.let { it.copy(likeCount = (it.likeCount + if (nowLiked) 1 else -1).coerceAtLeast(0)) }
        MissionEngagement.setLiked(db, uid, missionId, nowLiked,
            onComplete = {},
            onError = {
                isLiked = !nowLiked
                mission = mission?.let { it.copy(likeCount = (it.likeCount + if (nowLiked) -1 else 1).coerceAtLeast(0)) }
                Toast.makeText(context, engagementFailedMessage, Toast.LENGTH_SHORT).show()
            }
        )
    }

    fun toggleBookmark() {
        val uid = user?.uid ?: run {
            Toast.makeText(context, toastLoginRequired, Toast.LENGTH_SHORT).show()
            return
        }
        val nowBookmarked = !isBookmarked
        isBookmarked = nowBookmarked
        mission = mission?.let { it.copy(bookmarkCount = (it.bookmarkCount + if (nowBookmarked) 1 else -1).coerceAtLeast(0)) }
        MissionEngagement.setBookmarked(db, uid, missionId, nowBookmarked,
            onComplete = {},
            onError = {
                isBookmarked = !nowBookmarked
                mission = mission?.let { it.copy(bookmarkCount = (it.bookmarkCount + if (nowBookmarked) -1 else 1).coerceAtLeast(0)) }
                Toast.makeText(context, engagementFailedMessage, Toast.LENGTH_SHORT).show()
            }
        )
    }

    Scaffold(
        containerColor = Color.White,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.detail_title), fontWeight = FontWeight.Bold) },
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
                            Text(stringResource(R.string.detail_image_placeholder), color = Color.Gray)
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
                            text = missionStatusLabel(userMissionStatus),
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
                        text = currentMission.desc.ifBlank { stringResource(R.string.detail_desc_fallback) },
                        fontSize = 15.sp,
                        lineHeight = 22.sp,
                        color = Color.Gray
                    )
                }

                val distanceMeters = remember(currentMission.latitude, currentMission.longitude, userLatLng) {
                    val lat = currentMission.latitude
                    val lng = currentMission.longitude
                    val user = userLatLng
                    if (lat != null && lng != null && user != null) {
                        GeoDistance.meters(user.first, user.second, lat, lng)
                    } else null
                }

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    DetailChip(Icons.Default.Place, categoryLabel(currentMission.category), LightPurple, MainPurple)
                    DetailChip(Icons.Default.EmojiEvents, "${currentMission.points}P", Color(0xFFFFF4E4), Orange)
                    distanceMeters?.let { meters ->
                        DetailChip(Icons.AutoMirrored.Filled.DirectionsWalk, GeoDistance.format(meters), Color(0xFFE9F6E7), Color(0xFF4E9A4B))
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(18.dp), verticalAlignment = Alignment.CenterVertically) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { toggleLike() }) {
                            Icon(
                                if (isLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                contentDescription = null,
                                tint = if (isLiked) Color(0xFFE0537A) else Color.Gray
                            )
                        }
                        Text("${currentMission.likeCount}", color = Color.Gray, fontSize = 13.sp)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { toggleBookmark() }) {
                            Icon(
                                if (isBookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                                contentDescription = null,
                                tint = if (isBookmarked) MainPurple else Color.Gray
                            )
                        }
                        Text("${currentMission.bookmarkCount}", color = Color.Gray, fontSize = 13.sp)
                    }
                }

                if (currentMission.latitude != null && currentMission.longitude != null) {
                    OutlinedButton(
                        onClick = {
                            val lat = currentMission.latitude
                            val lng = currentMission.longitude
                            val label = Uri.encode(currentMission.title)
                            val intent = Intent(
                                Intent.ACTION_VIEW,
                                Uri.parse("geo:$lat,$lng?q=$lat,$lng($label)")
                            )
                            try {
                                context.startActivity(intent)
                            } catch (e: android.content.ActivityNotFoundException) {
                                Toast.makeText(context, toastNoMapApp, Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(Icons.Default.Place, null, tint = MainPurple, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(R.string.detail_btn_navigate), color = MainPurple, fontWeight = FontWeight.Bold)
                    }
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
                        Text(stringResource(R.string.detail_how_to_title), fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        DetailStep(Icons.Default.Flag, stringResource(R.string.detail_step1_title), stringResource(R.string.detail_step1_body))
                        DetailStep(Icons.Default.Place, stringResource(R.string.detail_step2_title), stringResource(R.string.detail_step2_body))
                        DetailStep(Icons.Default.CheckCircle, stringResource(R.string.detail_step3_title), stringResource(R.string.detail_step3_body))
                    }
                }

                Button(
                    onClick = {
                        if (user == null) {
                            Toast.makeText(context, toastLoginRequired, Toast.LENGTH_SHORT).show()
                        } else if (userMissionStatus == "완료") {
                            Toast.makeText(context, toastAlreadyCompleted, Toast.LENGTH_SHORT).show()
                        } else if (userMissionStatus == "진행중") {
                            onPerformMission(missionId)
                        } else {
                            isStarting = true
                            
                            val userMissionId = "${user.uid}_${currentMission.id}"
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
                                    Toast.makeText(context, toastSaveFailed, Toast.LENGTH_SHORT).show()
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
                                "완료" -> stringResource(R.string.detail_btn_completed)
                                "진행중" -> stringResource(R.string.home_btn_continue)
                                else -> stringResource(R.string.detail_btn_start)
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
