package smu.ai.graduation_project.ui.screens

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
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
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Stars
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.auth.userProfileChangeRequest
import com.google.firebase.Timestamp
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.firestore
import smu.ai.graduation_project.R
import smu.ai.graduation_project.data.AppLanguage
import smu.ai.graduation_project.data.LanguagePreference
import smu.ai.graduation_project.domain.BadgeId
import smu.ai.graduation_project.domain.TravelLevelPolicy
import smu.ai.graduation_project.ui.components.ProfileMenuItem
import smu.ai.graduation_project.ui.components.StatCard
import smu.ai.graduation_project.ui.components.badgeIcon
import smu.ai.graduation_project.ui.components.badgeLockedHint
import smu.ai.graduation_project.ui.components.badgeTitle
import smu.ai.graduation_project.ui.components.travelLevelLabel
import smu.ai.graduation_project.ui.theme.AppBackground
import smu.ai.graduation_project.ui.theme.LightPurple
import smu.ai.graduation_project.ui.theme.MainPurple
import smu.ai.graduation_project.ui.theme.Orange
import smu.ai.graduation_project.ui.theme.OutlineSoft
import smu.ai.graduation_project.ui.theme.Radius
import smu.ai.graduation_project.ui.theme.Spacing
import smu.ai.graduation_project.ui.theme.SurfaceCard
import smu.ai.graduation_project.ui.theme.TextPrimary
import smu.ai.graduation_project.ui.theme.TextSecondary
import java.text.SimpleDateFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    onLogout: () -> Unit,
    onNavigateToInProgressMissions: () -> Unit,
    onNavigateToCompletedMissions: () -> Unit,
    onNavigateToPointHistory: () -> Unit,
    onNavigateToRanking: () -> Unit,
    onNavigateToMyMissions: () -> Unit,
    onNavigateToBookmarkedMissions: () -> Unit
) {
    val currentUser = Firebase.auth.currentUser
    val db = Firebase.firestore
    val context = LocalContext.current

    val guestNickname = stringResource(R.string.profile_guest_nickname)
    val guestEmail = stringResource(R.string.profile_guest_email)
    var nickname by remember { mutableStateOf(currentUser?.displayName ?: guestNickname) }
    var email by remember { mutableStateOf(currentUser?.email ?: guestEmail) }
    var level by remember { mutableStateOf("Lv.1") }
    var points by remember { mutableIntStateOf(0) }
    var completedCount by remember { mutableIntStateOf(0) }
    var progressCount by remember { mutableIntStateOf(0) }
    var rank by remember { mutableIntStateOf(0) }
    /** badgeId → 획득 시각(millis). 아직 못 얻은 배지는 맵에 없다. */
    var unlockedBadges by remember { mutableStateOf<Map<BadgeId, Long?>>(emptyMap()) }
    var showNicknameDialog by remember { mutableStateOf(false) }
    var showLanguageDialog by remember { mutableStateOf(false) }
    var nicknameDraft by remember { mutableStateOf(nickname) }

    LaunchedEffect(currentUser?.uid) {
        currentUser?.uid?.let { uid ->
            db.collection("users").document(uid).addSnapshotListener { snapshot, _ ->
                if (snapshot != null && snapshot.exists()) {
                    nickname = snapshot.getString("nickname") ?: currentUser.displayName ?: guestNickname
                    email = snapshot.getString("mail") ?: currentUser.email ?: guestEmail
                    level = snapshot.getString("level") ?: "Lv.1"
                    points = snapshot.getLong("points")?.toInt() ?: 0
                    @Suppress("UNCHECKED_CAST")
                    unlockedBadges = (snapshot.get("badges") as? List<Map<String, Any?>>)
                        .orEmpty()
                        .mapNotNull { entry ->
                            val badge = (entry["badgeId"] as? String)?.let { BadgeId.fromId(it) } ?: return@mapNotNull null
                            badge to (entry["unlockedAt"] as? Timestamp)?.toDate()?.time
                        }
                        .toMap()
                    if (!showNicknameDialog) {
                        nicknameDraft = nickname
                    }
                }
            }

            db.collection("user_missions")
                .whereEqualTo("userId", uid)
                .addSnapshotListener { snapshot, _ ->
                    val docs = snapshot?.documents.orEmpty()
                    completedCount = docs.count {
                        val status = it.getString("status").orEmpty()
                        status.contains("완료") || status.equals("Completed", true)
                    }
                    progressCount = docs.count {
                        val status = it.getString("status").orEmpty()
                        status.contains("진행") || status.equals("In Progress", true)
                    }
                }

            db.collection("users")
                .orderBy("points", Query.Direction.DESCENDING)
                .addSnapshotListener { snapshot, _ ->
                    val docs = snapshot?.documents.orEmpty()
                    rank = docs.indexOfFirst { it.id == uid }.let { if (it >= 0) it + 1 else 0 }
                }
        }
    }

    Scaffold(
        // 하단 탭 Scaffold(MainActivity) 안에 중첩되는 화면 — 바깥 Scaffold가 이미 하단 내비게이션
        // 바 높이만큼 콘텐츠 영역을 잡아주므로, 기본 WindowInsets를 여기서 또 적용하면 하단 바와
        // 콘텐츠 사이에 실제로는 없는 여백이 이중으로 생긴다.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.profile_title), style = MaterialTheme.typography.titleLarge, color = TextPrimary) },
                actions = {
                    IconButton(onClick = onLogout) {
                        Icon(Icons.AutoMirrored.Filled.ExitToApp, null, tint = TextSecondary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = AppBackground)
            )
        },
        containerColor = AppBackground
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.lg, vertical = Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.lg - 2.dp)
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = LightPurple,
                shape = RoundedCornerShape(Radius.xl)
            ) {
                Column(
                    modifier = Modifier.padding(Spacing.lg),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm + 2.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(92.dp)
                            .border(2.dp, Color.White, CircleShape)
                            .padding(4.dp)
                            .background(Color.White, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Person, null, tint = MainPurple, modifier = Modifier.size(48.dp))
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = nickname,
                            style = MaterialTheme.typography.headlineMedium,
                            color = TextPrimary
                        )
                        IconButton(onClick = {
                            nicknameDraft = nickname
                            showNicknameDialog = true
                        }) {
                            Icon(Icons.Default.Edit, null, tint = MainPurple, modifier = Modifier.size(18.dp))
                        }
                    }
                    Text(email, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                    Surface(color = Color.White, shape = RoundedCornerShape(Radius.pill)) {
                        Row(
                            modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Stars, null, tint = Orange, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(Spacing.xs))
                            Text(
                                text = "$level · ${String.format("%,d", points)}P",
                                style = MaterialTheme.typography.labelLarge,
                                color = TextPrimary
                            )
                        }
                    }
                }
            }

            val levelProgress = remember(points) { TravelLevelPolicy.progressFor(points) }
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = SurfaceCard,
                border = BorderStroke(1.dp, OutlineSoft),
                shape = RoundedCornerShape(Radius.lg)
            ) {
                Column(modifier = Modifier.padding(Spacing.lg)) {
                    Text(stringResource(R.string.profile_level_section_title), style = MaterialTheme.typography.labelLarge, color = TextSecondary)
                    Spacer(modifier = Modifier.height(Spacing.sm))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            stringResource(R.string.level_display_format, travelLevelLabel(levelProgress.level), levelProgress.level.number),
                            style = MaterialTheme.typography.titleLarge,
                            color = TextPrimary
                        )
                        Text(
                            if (levelProgress.isMaxLevel) {
                                stringResource(R.string.level_max_reached)
                            } else {
                                stringResource(R.string.level_points_to_next, levelProgress.pointsToNextLevel ?: 0)
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = MainPurple
                        )
                    }
                    Spacer(modifier = Modifier.height(Spacing.sm + 2.dp))
                    LinearProgressIndicator(
                        progress = { levelProgress.progressRatio },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(CircleShape),
                        color = MainPurple,
                        trackColor = LightPurple
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                ) {
                    StatCard(
                        title = stringResource(R.string.profile_stat_points),
                        value = String.format("%,dP", points),
                        icon = Icons.Default.Stars,
                        modifier = Modifier.weight(1f),
                        onClick = onNavigateToPointHistory
                    )
                    StatCard(
                        title = stringResource(R.string.profile_stat_ranking),
                        value = if (rank > 0) "#$rank" else "-",
                        icon = Icons.Default.EmojiEvents,
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                ) {
                    StatCard(
                        title = stringResource(R.string.profile_stat_in_progress),
                        value = progressCount.toString(),
                        icon = Icons.Default.HourglassTop,
                        modifier = Modifier.weight(1f),
                        onClick = onNavigateToInProgressMissions
                    )
                    StatCard(
                        title = stringResource(R.string.profile_stat_completed),
                        value = completedCount.toString(),
                        icon = Icons.Default.Flag,
                        modifier = Modifier.weight(1f),
                        onClick = onNavigateToCompletedMissions
                    )
                }
            }

            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = SurfaceCard,
                border = BorderStroke(1.dp, OutlineSoft),
                shape = RoundedCornerShape(Radius.lg)
            ) {
                Column(
                    modifier = Modifier.padding(Spacing.lg),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm)
                ) {
                    Text(stringResource(R.string.profile_activity_summary_title), style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                    Text(
                        text = when {
                            completedCount > 0 -> stringResource(R.string.profile_activity_summary_both, completedCount, progressCount)
                            progressCount > 0 -> stringResource(R.string.profile_activity_summary_progress_only, progressCount)
                            else -> stringResource(R.string.profile_activity_summary_none)
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.LocalFireDepartment, null, tint = Orange, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(Spacing.xs))
                        Text(
                            text = stringResource(R.string.profile_next_goal),
                            style = MaterialTheme.typography.bodySmall,
                            color = TextPrimary
                        )
                    }
                }
            }

            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = SurfaceCard,
                border = BorderStroke(1.dp, OutlineSoft),
                shape = RoundedCornerShape(Radius.lg)
            ) {
                Column(modifier = Modifier.padding(Spacing.lg)) {
                    Text(stringResource(R.string.profile_badges_title), style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                    Spacer(modifier = Modifier.height(Spacing.sm + 6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        BadgeId.entries.forEach { badgeId ->
                            ProfileBadgeItem(
                                badgeId = badgeId,
                                unlockedAtMillis = unlockedBadges[badgeId],
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = SurfaceCard,
                border = BorderStroke(1.dp, OutlineSoft),
                shape = RoundedCornerShape(Radius.lg)
            ) {
                Column(modifier = Modifier.padding(vertical = Spacing.xs)) {
                    ProfileMenuItem(stringResource(R.string.profile_menu_nickname), Icons.Default.Edit) {
                        nicknameDraft = nickname
                        showNicknameDialog = true
                    }
                    HorizontalDivider(color = OutlineSoft, modifier = Modifier.padding(horizontal = Spacing.lg))
                    ProfileMenuItem(stringResource(R.string.profile_menu_language), Icons.Default.Language) {
                        showLanguageDialog = true
                    }
                    HorizontalDivider(color = OutlineSoft, modifier = Modifier.padding(horizontal = Spacing.lg))
                    ProfileMenuItem(stringResource(R.string.profile_menu_my_missions), Icons.AutoMirrored.Filled.Assignment, onClick = onNavigateToMyMissions)
                    HorizontalDivider(color = OutlineSoft, modifier = Modifier.padding(horizontal = Spacing.lg))
                    ProfileMenuItem(stringResource(R.string.profile_menu_bookmarks), Icons.Default.Bookmark, onClick = onNavigateToBookmarkedMissions)
                    HorizontalDivider(color = OutlineSoft, modifier = Modifier.padding(horizontal = Spacing.lg))
                    ProfileMenuItem(stringResource(R.string.profile_menu_ranking), Icons.Default.EmojiEvents, onClick = onNavigateToRanking)
                    HorizontalDivider(color = OutlineSoft, modifier = Modifier.padding(horizontal = Spacing.lg))
                    ProfileMenuItem(stringResource(R.string.profile_menu_logout), Icons.AutoMirrored.Filled.ExitToApp, onClick = onLogout)
                }
            }
        }
    }

    if (showNicknameDialog) {
        val emptyMessage = stringResource(R.string.profile_toast_nickname_empty)
        val updatedMessage = stringResource(R.string.profile_toast_nickname_updated)
        val failedMessage = stringResource(R.string.profile_toast_nickname_update_failed)
        AlertDialog(
            onDismissRequest = { showNicknameDialog = false },
            title = { Text(stringResource(R.string.profile_dialog_nickname_title)) },
            text = {
                OutlinedTextField(
                    value = nicknameDraft,
                    onValueChange = { nicknameDraft = it },
                    label = { Text(stringResource(R.string.profile_dialog_nickname_label)) },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val trimmed = nicknameDraft.trim()
                    if (trimmed.isEmpty()) {
                        Toast.makeText(context, emptyMessage, Toast.LENGTH_SHORT).show()
                        return@TextButton
                    }
                    val user = Firebase.auth.currentUser
                    val uid = user?.uid ?: return@TextButton
                    user.updateProfile(userProfileChangeRequest { displayName = trimmed })
                    db.collection("users").document(uid)
                        .update("nickname", trimmed)
                        .addOnSuccessListener {
                            nickname = trimmed
                            showNicknameDialog = false
                            Toast.makeText(context, updatedMessage, Toast.LENGTH_SHORT).show()
                        }
                        .addOnFailureListener {
                            Toast.makeText(context, failedMessage, Toast.LENGTH_SHORT).show()
                        }
                }) {
                    Text(stringResource(R.string.profile_dialog_save))
                }
            },
            dismissButton = {
                TextButton(onClick = { showNicknameDialog = false }) {
                    Text(stringResource(R.string.profile_dialog_cancel))
                }
            }
        )
    }

    if (showLanguageDialog) {
        AlertDialog(
            onDismissRequest = { showLanguageDialog = false },
            title = { Text("언어 / Language") },
            text = {
                Column {
                    AppLanguage.entries.forEach { language ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    LanguagePreference.set(context, language)
                                    showLanguageDialog = false
                                }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = LanguagePreference.current == language,
                                onClick = {
                                    LanguagePreference.set(context, language)
                                    showLanguageDialog = false
                                }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(language.label)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showLanguageDialog = false }) {
                    Text(stringResource(R.string.profile_dialog_close))
                }
            }
        )
    }
}

/** 배지 1개. 획득했으면 보라색 아이콘 + 획득일, 아직이면 회색 실루엣 + 자물쇠 + 획득 조건. */
@Composable
private fun ProfileBadgeItem(badgeId: BadgeId, unlockedAtMillis: Long?, modifier: Modifier = Modifier) {
    val unlocked = unlockedAtMillis != null
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .background(if (unlocked) LightPurple else Color(0xFFEDEDED), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                if (unlocked) badgeIcon(badgeId) else Icons.Default.Lock,
                null,
                tint = if (unlocked) MainPurple else Color(0xFFAFAFAF),
                modifier = Modifier.size(if (unlocked) 26.dp else 20.dp)
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            badgeTitle(badgeId),
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            color = if (unlocked) Color(0xFF2C2C2C) else Color.Gray
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            if (unlocked) {
                stringResource(R.string.profile_badge_unlocked_on, formatBadgeDate(unlockedAtMillis))
            } else {
                badgeLockedHint(badgeId)
            },
            fontSize = 10.sp,
            color = Color.Gray,
            textAlign = TextAlign.Center,
            lineHeight = 13.sp
        )
    }
}

private fun formatBadgeDate(millis: Long): String =
    SimpleDateFormat("yyyy.MM.dd", Locale.KOREA).format(java.util.Date(millis))
