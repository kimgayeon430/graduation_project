package smu.ai.graduation_project.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Stars
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.auth.userProfileChangeRequest
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.firestore
import smu.ai.graduation_project.R
import smu.ai.graduation_project.data.AppLanguage
import smu.ai.graduation_project.data.LanguagePreference
import smu.ai.graduation_project.ui.components.ProfileMenuItem
import smu.ai.graduation_project.ui.components.StatCard
import smu.ai.graduation_project.ui.theme.CardGray
import smu.ai.graduation_project.ui.theme.LightPurple
import smu.ai.graduation_project.ui.theme.MainPurple
import smu.ai.graduation_project.ui.theme.Orange

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    onLogout: () -> Unit,
    onNavigateToInProgressMissions: () -> Unit,
    onNavigateToCompletedMissions: () -> Unit,
    onNavigateToPointHistory: () -> Unit,
    onNavigateToRanking: () -> Unit
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
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.profile_title), fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = onLogout) {
                        Icon(Icons.AutoMirrored.Filled.ExitToApp, null)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
            )
        },
        containerColor = Color.White
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = LightPurple,
                shape = RoundedCornerShape(24.dp)
            ) {
                Column(
                    modifier = Modifier.padding(22.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(92.dp)
                            .background(Color.White, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Person, null, tint = MainPurple, modifier = Modifier.size(50.dp))
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = nickname,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 24.sp,
                            color = Color(0xFF2B2B2B)
                        )
                        IconButton(onClick = {
                            nicknameDraft = nickname
                            showNicknameDialog = true
                        }) {
                            Icon(Icons.Default.Edit, null, tint = MainPurple, modifier = Modifier.size(18.dp))
                        }
                    }
                    Text(email, color = Color.Gray, fontSize = 14.sp)
                    Surface(color = Color.White, shape = RoundedCornerShape(50)) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Stars, null, tint = Orange, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "$level · ${String.format("%,d", points)}P",
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF333333)
                            )
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
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
                horizontalArrangement = Arrangement.spacedBy(12.dp)
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

            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = CardGray,
                shape = RoundedCornerShape(20.dp)
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(stringResource(R.string.profile_activity_summary_title), fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Text(
                        text = when {
                            completedCount > 0 -> stringResource(R.string.profile_activity_summary_both, completedCount, progressCount)
                            progressCount > 0 -> stringResource(R.string.profile_activity_summary_progress_only, progressCount)
                            else -> stringResource(R.string.profile_activity_summary_none)
                        },
                        color = Color.Gray,
                        lineHeight = 20.sp,
                        fontSize = 14.sp
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.LocalFireDepartment, null, tint = Orange, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.profile_next_goal),
                            color = Color(0xFF444444),
                            fontSize = 13.sp
                        )
                    }
                }
            }

            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = Color.White,
                shadowElevation = 2.dp,
                shape = RoundedCornerShape(18.dp)
            ) {
                Column(modifier = Modifier.padding(vertical = 8.dp)) {
                    ProfileMenuItem(stringResource(R.string.profile_menu_nickname), Icons.Default.Edit) {
                        nicknameDraft = nickname
                        showNicknameDialog = true
                    }
                    ProfileMenuItem(stringResource(R.string.profile_menu_language), Icons.Default.Language) {
                        showLanguageDialog = true
                    }
                    ProfileMenuItem(stringResource(R.string.profile_menu_ranking), Icons.Default.EmojiEvents, onClick = onNavigateToRanking)
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
