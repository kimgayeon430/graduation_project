package smu.ai.graduation_project.ui.screens

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
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.Landscape
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Stars
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import coil.compose.AsyncImage
import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.GeoPoint
import com.google.firebase.firestore.firestore
import smu.ai.graduation_project.R
import smu.ai.graduation_project.data.LanguagePreference
import smu.ai.graduation_project.data.RerankerSource
import smu.ai.graduation_project.data.localizedString
import smu.ai.graduation_project.domain.GeoDistance
import smu.ai.graduation_project.domain.MissionCompletion
import smu.ai.graduation_project.domain.MissionRecommender
import smu.ai.graduation_project.domain.MissionReviewStatus
import smu.ai.graduation_project.domain.MissionScorer
import smu.ai.graduation_project.domain.RecommendationContext
import smu.ai.graduation_project.domain.WeekBoundary
import smu.ai.graduation_project.model.Mission
import smu.ai.graduation_project.ui.components.recommendationReasonLabel
import smu.ai.graduation_project.ui.theme.AppBackground
import smu.ai.graduation_project.ui.theme.CardGray
import smu.ai.graduation_project.ui.theme.GradientEnd
import smu.ai.graduation_project.ui.theme.GradientStart
import smu.ai.graduation_project.ui.theme.LightPurple
import smu.ai.graduation_project.ui.theme.MainPurple
import smu.ai.graduation_project.ui.theme.Orange
import smu.ai.graduation_project.ui.theme.Radius
import smu.ai.graduation_project.ui.theme.Spacing
import smu.ai.graduation_project.ui.theme.SuccessGreen
import smu.ai.graduation_project.ui.theme.SurfaceCard
import smu.ai.graduation_project.ui.theme.TextPrimary
import smu.ai.graduation_project.ui.theme.TextSecondary

@Composable
fun HomeScreen(onNavigateToDetail: (String) -> Unit) {
    val context = LocalContext.current
    val user = Firebase.auth.currentUser
    var points by remember { mutableLongStateOf(0L) }
    var userName by remember { mutableStateOf(user?.displayName ?: "Traveler") }
    var weeklyCompletedCount by remember { mutableIntStateOf(0) }
    val totalGoal = 5

    var activeMission by remember { mutableStateOf<Mission?>(null) }
    var allMissions by remember { mutableStateOf<List<Mission>>(emptyList()) }
    var completedMissionIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var preferences by remember { mutableStateOf<List<String>>(emptyList()) }
    var level by remember { mutableIntStateOf(1) }
    var missionGeo by remember { mutableStateOf<Map<String, GeoPoint>>(emptyMap()) }
    var completionCounts by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }
    var userLatLng by remember { mutableStateOf<Pair<Double, Double>?>(null) }
    var missionsLoaded by remember { mutableStateOf(false) }

    // 위치 권한이 이미 허용돼 있으면 마지막 known location 을 읽는다. (홈에서 새로 팝업은 띄우지 않음)
    LaunchedEffect(Unit) {
        val granted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) return@LaunchedEffect
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            ?: return@LaunchedEffect
        val best = try {
            lm.getProviders(true).mapNotNull { lm.getLastKnownLocation(it) }.maxByOrNull { it.time }
        } catch (e: SecurityException) {
            null
        }
        if (best != null) userLatLng = best.latitude to best.longitude
    }

    LaunchedEffect(user?.uid) {
        // 추천 후보로 쓸 전체 미션 (읽기 전용 — 일반 미션 목록/관리자 기능과 무관)
        Firebase.firestore.collection("missions").get()
            .addOnSuccessListener { snapshot ->
                allMissions = snapshot.documents.mapNotNull { doc ->
                    if (doc.getString("title").isNullOrBlank()) return@mapNotNull null
                    if (!MissionReviewStatus.isPubliclyVisible(doc.getString("reviewStatus"))) return@mapNotNull null
                    Mission(
                        id = doc.id,
                        title = doc.localizedString("title", LanguagePreference.current),
                        desc = doc.localizedString("desc", LanguagePreference.current),
                        points = doc.getLong("points")?.toInt() ?: 0,
                        category = doc.getString("category") ?: "투어",
                        imageUrl = doc.getString("imageUrl").orEmpty(),
                        likeCount = doc.getLong("likeCount")?.toInt() ?: 0
                    )
                }
                missionGeo = snapshot.documents.mapNotNull { doc ->
                    doc.getGeoPoint("location")?.let { doc.id to it }
                }.toMap()
                completionCounts = snapshot.documents.associate { doc ->
                    doc.id to (doc.getLong("completionCount")?.toInt() ?: 0)
                }
                missionsLoaded = true
            }
            .addOnFailureListener { missionsLoaded = true }

        user?.uid?.let { uid ->
            Firebase.firestore.collection("users").document(uid).addSnapshotListener { snapshot, _ ->
                if (snapshot != null && snapshot.exists()) {
                    points = snapshot.getLong("points") ?: 0L
                    userName = snapshot.getString("nickname") ?: user.displayName ?: "Traveler"
                    // level 은 "Lv.1" 형태의 문자열로 저장된다 → 숫자 부분만 추출
                    level = snapshot.getString("level")?.filter { it.isDigit() }?.toIntOrNull() ?: 1
                    @Suppress("UNCHECKED_CAST")
                    preferences = (snapshot.get("preferences") as? List<String>).orEmpty()
                }
            }

            Firebase.firestore.collection("user_missions")
                .whereEqualTo("userId", uid)
                .addSnapshotListener { snapshot, _ ->
                    if (snapshot == null) return@addSnapshotListener

                    val documents = snapshot.documents
                    val startOfWeek = WeekBoundary.startOfThisWeekMillis()
                    weeklyCompletedCount = documents.count { doc ->
                        val status = doc.getString("status").orEmpty()
                        val completedAt = doc.getTimestamp("completedAt")
                        MissionCompletion.isCompleted(status) &&
                            completedAt != null &&
                            completedAt.toDate().time >= startOfWeek
                    }

                    completedMissionIds = documents
                        .filter { doc ->
                            val status = doc.getString("status").orEmpty()
                            status.contains("완료") || status.equals("Completed", true)
                        }
                        .mapNotNull { it.getString("missionId") }
                        .toSet()

                    val currentActive = documents.firstOrNull { doc ->
                        val status = doc.getString("status").orEmpty()
                        status.contains("진행") || status.contains("吏꾪뻾")
                    }

                    activeMission = currentActive?.let { doc ->
                        Mission(
                            id = doc.getString("missionId") ?: "",
                            title = doc.getString("title") ?: "",
                            desc = "현재 진행 중인 미션입니다.",
                            points = doc.getLong("points")?.toInt() ?: 0,
                            status = "진행중"
                        )
                    }
                }
        }
    }

    // 사용자 현재 위치 ↔ 각 미션 목표 지점 거리(m). 위치를 모르면 빈 맵.
    val distances = remember(missionGeo, userLatLng) {
        val loc = userLatLng
        if (loc == null) emptyMap<String, Double>()
        else missionGeo.mapValues { (_, geo) ->
            GeoDistance.meters(loc.first, loc.second, geo.latitude, geo.longitude)
        }
    }

    // 학습된 re-ranker 모델(assets/reranker.json). 없으면 null → 규칙 기반으로 폴백.
    val rerankerModel = remember { RerankerSource.load(context) }

    // 추천: 진행 중 미션이 있으면 그것을 우선 표시, 없으면 상위 3건.
    // 규칙 점수(명시적/암묵적 취향·난이도·거리·인기도) 를 완료 로그로 학습한 re-ranker 로 다시 매긴 뒤
    // 다양성 감점을 적용한다. 모델이 없으면 규칙 점수만으로 정렬한다.
    val recommendations = remember(
        allMissions, preferences, completedMissionIds, level, distances, completionCounts, rerankerModel
    ) {
        MissionRecommender.recommendReranked(
            missions = allMissions,
            context = RecommendationContext(
                preferredCategories = preferences.toSet(),
                completedCountByCategory = allMissions
                    .filter { it.id in completedMissionIds }
                    .groupingBy { it.category }
                    .eachCount(),
                userLevel = level,
                distanceMetersByMissionId = distances,
                completionCountByMissionId = completionCounts,
                currentHour = java.time.LocalTime.now().hour
            ),
            completedMissionIds = completedMissionIds,
            model = rerankerModel,
            limit = 3
        )
    }
    val showRecommendations = activeMission == null && recommendations.isNotEmpty()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppBackground)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.lg, vertical = Spacing.md)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Column {
                Text(
                    stringResource(R.string.home_greeting, userName),
                    style = MaterialTheme.typography.headlineMedium,
                    color = TextPrimary
                )
                Spacer(modifier = Modifier.height(Spacing.xs))
                Text(
                    stringResource(R.string.home_tagline),
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
            }

            Surface(
                color = LightPurple,
                shape = RoundedCornerShape(Radius.pill),
                modifier = Modifier.padding(top = 4.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Stars, contentDescription = null, tint = Orange, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(Spacing.xs))
                    Text(String.format("%,d", points), style = MaterialTheme.typography.labelLarge, color = MainPurple)
                }
            }
        }

        Spacer(modifier = Modifier.height(Spacing.xl))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(128.dp)
                .background(
                    Brush.linearGradient(listOf(GradientStart, GradientEnd)),
                    RoundedCornerShape(Radius.xl)
                )
                .padding(Spacing.lg)
        ) {
            Column {
                Text(
                    stringResource(R.string.home_weekly_progress_title),
                    style = MaterialTheme.typography.titleSmall,
                    color = Color.White.copy(alpha = 0.9f)
                )
                Spacer(modifier = Modifier.height(Spacing.sm))
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        "$weeklyCompletedCount/$totalGoal",
                        style = MaterialTheme.typography.headlineMedium,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.width(Spacing.md))
                    LinearProgressIndicator(
                        progress = { (weeklyCompletedCount.toFloat() / totalGoal).coerceIn(0f, 1f) },
                        modifier = Modifier
                            .fillMaxWidth(0.7f)
                            .height(8.dp)
                            .clip(CircleShape)
                            .padding(bottom = 6.dp),
                        color = Color.White,
                        trackColor = Color.White.copy(alpha = 0.25f)
                    )
                }
            }
            Icon(
                Icons.Default.CardGiftcard,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.8f),
                modifier = Modifier
                    .size(60.dp)
                    .align(Alignment.CenterEnd)
            )
        }

        Spacer(modifier = Modifier.height(Spacing.xl))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                if (activeMission != null) stringResource(R.string.home_active_mission_title)
                else stringResource(R.string.home_recommended_title),
                style = MaterialTheme.typography.titleLarge,
                color = TextPrimary
            )
            TextButton(onClick = { }) {
                Text(stringResource(R.string.home_view_more), style = MaterialTheme.typography.labelLarge, color = MainPurple)
            }
        }

        Spacer(modifier = Modifier.height(Spacing.sm))

        activeMission?.let { mission ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = SurfaceCard),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                shape = RoundedCornerShape(Radius.lg)
            ) {
                Row(
                    modifier = Modifier.padding(Spacing.md),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.md)
                ) {
                    HomeMissionImage(
                        imageUrl = allMissions.firstOrNull { it.id == mission.id }?.imageUrl.orEmpty(),
                        title = mission.title
                    )
                    Column(modifier = Modifier.width(220.dp)) {
                        HomeStatusChip(
                            text = if (mission.status == "진행중") stringResource(R.string.home_badge_in_progress)
                            else stringResource(R.string.home_badge_recommended),
                            tint = if (mission.status == "진행중") SuccessGreen else MainPurple
                        )
                        Spacer(modifier = Modifier.height(Spacing.xs))
                        Text(mission.title, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                        Text(mission.desc, style = MaterialTheme.typography.bodySmall, color = TextSecondary, maxLines = 1)
                        Spacer(modifier = Modifier.height(Spacing.xs))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Stars, contentDescription = null, tint = Orange, modifier = Modifier.size(14.dp))
                            Text(" ${mission.points}P", style = MaterialTheme.typography.labelMedium, color = TextPrimary)
                        }
                        Spacer(modifier = Modifier.height(Spacing.sm))
                        Button(
                            onClick = { onNavigateToDetail(mission.id) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(Radius.sm),
                            colors = ButtonDefaults.buttonColors(containerColor = MainPurple)
                        ) {
                            Text(
                                if (mission.status == "진행중") stringResource(R.string.home_btn_continue)
                                else stringResource(R.string.home_btn_view),
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    }
                }
            }
        }

        if (showRecommendations) {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                recommendations.forEach { scored ->
                    RecommendedMissionCard(
                        scored = scored,
                        distanceMeters = distances[scored.mission.id],
                        onClick = { onNavigateToDetail(scored.mission.id) }
                    )
                }
            }
        }

        if (activeMission == null && recommendations.isEmpty() && missionsLoaded) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = CardGray),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                shape = RoundedCornerShape(Radius.lg)
            ) {
                Column(modifier = Modifier.padding(Spacing.lg)) {
                    Text(stringResource(R.string.home_empty_title), style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                    Text(
                        stringResource(R.string.home_empty_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(80.dp))
    }
}

/** 점수 기반 추천 미션 1건. 추천 이유(칩)와, 위치를 알면 현재 위치로부터의 거리를 함께 보여 준다. */
@Composable
private fun RecommendedMissionCard(
    scored: MissionScorer.Scored,
    distanceMeters: Double?,
    onClick: () -> Unit
) {
    val mission = scored.mission
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = SurfaceCard),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        shape = RoundedCornerShape(Radius.lg)
    ) {
        Row(
            modifier = Modifier.padding(Spacing.md),
            horizontalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            HomeMissionImage(imageUrl = mission.imageUrl, title = mission.title)
            Column(modifier = Modifier.width(220.dp)) {
                HomeStatusChip(text = stringResource(R.string.home_badge_recommended), tint = MainPurple)
                Spacer(modifier = Modifier.height(Spacing.xs))
                Text(mission.title, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                if (scored.reasons.isNotEmpty()) {
                    Row(
                        modifier = Modifier.padding(top = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        scored.reasons.take(2).forEach { reason ->
                            Surface(color = LightPurple, shape = RoundedCornerShape(Radius.pill)) {
                                Text(
                                    recommendationReasonLabel(reason),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MainPurple,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(Spacing.xs))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Stars, contentDescription = null, tint = Orange, modifier = Modifier.size(14.dp))
                    Text(" ${mission.points}P", style = MaterialTheme.typography.labelMedium, color = TextPrimary)
                    distanceMeters?.let { meters ->
                        Spacer(modifier = Modifier.width(Spacing.sm))
                        Icon(Icons.Default.Place, contentDescription = null, tint = MainPurple, modifier = Modifier.size(14.dp))
                        Text(" ${GeoDistance.format(meters)}", style = MaterialTheme.typography.labelMedium, color = TextPrimary)
                    }
                }
                Spacer(modifier = Modifier.height(Spacing.sm))
                Button(
                    onClick = onClick,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(Radius.sm),
                    colors = ButtonDefaults.buttonColors(containerColor = MainPurple)
                ) {
                    Text(stringResource(R.string.home_btn_view), style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

/** 상태 배지(진행중/추천 등)를 톤온톤 캡슐 칩으로 통일해서 그린다. */
@Composable
private fun HomeStatusChip(text: String, tint: Color) {
    Surface(color = tint.copy(alpha = 0.12f), shape = RoundedCornerShape(Radius.pill)) {
        Text(
            text,
            style = MaterialTheme.typography.labelSmall,
            color = tint,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}

/** Home cards use the same Firestore imageUrl as the mission list and detail. */
@Composable
private fun HomeMissionImage(imageUrl: String, title: String) {
    Box(
        modifier = Modifier
            .size(100.dp)
            .clip(RoundedCornerShape(Radius.md))
            .background(LightPurple),
        contentAlignment = Alignment.Center
    ) {
        if (imageUrl.isBlank()) {
            Icon(Icons.Default.Landscape, contentDescription = null,
                tint = MainPurple, modifier = Modifier.size(40.dp))
        } else {
            val fallback = rememberVectorPainter(Icons.Default.Landscape)
            AsyncImage(
                model = imageUrl,
                contentDescription = stringResource(R.string.home_mission_image_desc, title),
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                placeholder = fallback,
                error = fallback
            )
        }
    }
}
