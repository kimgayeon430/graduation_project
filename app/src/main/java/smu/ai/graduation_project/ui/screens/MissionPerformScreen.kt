package smu.ai.graduation_project.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.net.Uri
import android.os.SystemClock
import android.widget.Toast
import java.io.File
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.LocationSearching
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.location.LocationManagerCompat
import androidx.core.os.CancellationSignal
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.delay
import smu.ai.graduation_project.R
import smu.ai.graduation_project.data.LanguagePreference
import smu.ai.graduation_project.data.localizedString
import smu.ai.graduation_project.domain.MissionCompletion
import smu.ai.graduation_project.domain.MissionRecommender
import smu.ai.graduation_project.domain.MissionReviewStatus
import smu.ai.graduation_project.domain.RecommendationContext
import smu.ai.graduation_project.domain.TravelLevelPolicy
import smu.ai.graduation_project.model.Mission
import smu.ai.graduation_project.ui.components.RecommendedMissionUi
import smu.ai.graduation_project.ui.theme.CardGray
import smu.ai.graduation_project.ui.theme.LightPurple
import smu.ai.graduation_project.ui.theme.MainPurple

/**
 * 위치 인증에 쓸 수 있는 좌표의 최대 나이. 이보다 오래된 값은 "현재 위치" 로 보지 않는다.
 *
 * `getCurrentLocation` 도 아주 최근이면 캐시를 그대로 돌려줄 수 있어, 낡은 좌표로 조용히
 * 오판하지 않도록 한 번 더 거른다.
 */
private const val MAX_LOCATION_AGE_MILLIS = 2 * 60 * 1000L

/** 단조 시계 기준 좌표 나이(ms). 기기 시각을 바꿔도 영향받지 않는다. */
private fun locationAgeMillis(location: Location): Long =
    (SystemClock.elapsedRealtimeNanos() - location.elapsedRealtimeNanos) / 1_000_000

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MissionPerformScreen(
    missionId: String,
    onNavigateBack: () -> Unit,
    onNavigateToMissionList: () -> Unit = {},
    onNavigateToHome: () -> Unit = {},
    onNavigateToDetail: (String) -> Unit = {},
    viewModel: MissionPerformViewModel = viewModel()
) {
    val context = LocalContext.current
    val uid = Firebase.auth.currentUser?.uid
    val locationManager = remember { context.getSystemService(LocationManager::class.java) }
    val state = viewModel.uiState

    // 카메라 인텐트로 넘긴 임시 파일 Uri (촬영 성공 시 ViewModel 로 전달)
    var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }

    LaunchedEffect(missionId, uid) {
        viewModel.loadData(missionId, uid)
    }

    // 일회성 이벤트: 토스트
    LaunchedEffect(state.toastMessage) {
        state.toastMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.onToastShown()
        }
    }
    // 일회성 이벤트: 뒤로가기
    LaunchedEffect(state.navigateBack) {
        if (state.navigateBack) {
            viewModel.onNavigateHandled()
            onNavigateBack()
        }
    }

    // 일회성 이벤트: 성취 연출은 버튼이 아니라 타이머로 끝난다(연출 애니메이션 시간 + 여유 300ms).
    LaunchedEffect(state.celebration) {
        state.celebration?.let { celebration ->
            delay(celebration.durationMillis + 300)
            viewModel.onCelebrationFinished()
        }
    }

    // PASS 결과 화면 하단의 "다음 추천 미션" 카드용 데이터. HomeScreen 과 같은 방식(화면에서 직접
    // Firestore 조회)으로 후보를 모으고, 실제 순위는 MissionRecommender 를 그대로 재사용한다.
    var recommendedMission by remember { mutableStateOf<RecommendedMissionUi?>(null) }
    var recommendationLoaded by remember { mutableStateOf(false) }
    LaunchedEffect(state.photoResult?.verdict, uid) {
        if (state.photoResult?.verdict != PhotoVerdictUi.PASS || uid == null) return@LaunchedEffect
        recommendationLoaded = false
        recommendedMission = null
        val db = Firebase.firestore
        db.collection("missions").get().addOnSuccessListener { missionSnapshot ->
            val allMissions = missionSnapshot.documents.mapNotNull { doc ->
                if (doc.getString("title").isNullOrBlank()) return@mapNotNull null
                if (!MissionReviewStatus.isPubliclyVisible(doc.getString("reviewStatus"))) return@mapNotNull null
                Mission(
                    id = doc.id,
                    title = doc.localizedString("title", LanguagePreference.current),
                    points = doc.getLong("points")?.toInt() ?: 0,
                    category = doc.getString("category") ?: "투어",
                    imageUrl = doc.getString("imageUrl").orEmpty(),
                    estimatedMinutes = doc.getLong("estimatedMinutes")?.toInt(),
                    likeCount = doc.getLong("likeCount")?.toInt() ?: 0
                )
            }
            val completionCounts = missionSnapshot.documents.associate { doc ->
                doc.id to (doc.getLong("completionCount")?.toInt() ?: 0)
            }
            db.collection("users").document(uid).get().addOnSuccessListener { userDoc ->
                @Suppress("UNCHECKED_CAST")
                val preferences = (userDoc.get("preferences") as? List<String>).orEmpty()
                val userLevel = TravelLevelPolicy.progressFor(userDoc.getLong("points")?.toInt() ?: 0).level.number
                db.collection("user_missions").whereEqualTo("userId", uid).get()
                    .addOnSuccessListener { userMissionsSnapshot ->
                        // 방금 완료한 미션은 이 조회 시점에 이미 "완료" 로 반영돼 있지만, 혹시 모를 지연에
                        // 대비해 missionId 를 한 번 더 명시적으로 제외한다.
                        val completedIds = userMissionsSnapshot.documents
                            .filter { MissionCompletion.isCompleted(it.getString("status").orEmpty()) }
                            .mapNotNull { it.getString("missionId") }
                            .toSet() + missionId
                        val scored = MissionRecommender.recommendScored(
                            missions = allMissions,
                            context = RecommendationContext(
                                preferredCategories = preferences.toSet(),
                                completedCountByCategory = allMissions
                                    .filter { it.id in completedIds }
                                    .groupingBy { it.category }
                                    .eachCount(),
                                userLevel = userLevel,
                                completionCountByMissionId = completionCounts,
                                currentHour = java.time.LocalTime.now().hour
                            ),
                            completedMissionIds = completedIds,
                            limit = 1
                        )
                        recommendedMission = scored.firstOrNull()?.let { s ->
                            RecommendedMissionUi(
                                missionId = s.mission.id,
                                title = s.mission.title,
                                imageUrl = s.mission.imageUrl,
                                category = s.mission.category,
                                points = s.mission.points,
                                estimatedMinutes = s.mission.estimatedMinutes,
                                reasons = s.reasons
                            )
                        }
                        recommendationLoaded = true
                    }
                    .addOnFailureListener { recommendationLoaded = true }
            }.addOnFailureListener { recommendationLoaded = true }
        }.addOnFailureListener { recommendationLoaded = true }
    }

    // AI 분석 중 / 성취 연출(PASS 전용) / 분석 결과: 전체 화면으로 띄운다(단순 Toast 로 끝내지 않는다).
    // 세 상태 모두 ViewModel 의 uiState 에 있으므로 화면 회전에도 그대로 복원된다.
    if (state.isUploading || state.celebration != null || state.photoResult != null) {
        Dialog(
            onDismissRequest = {
                when {
                    state.isUploading || state.celebration != null -> Unit
                    state.photoResult?.verdict == PhotoVerdictUi.REJECT -> viewModel.onPhotoResultRetake()
                    state.photoResult?.verdict == PhotoVerdictUi.PASS -> {
                        viewModel.onPhotoResultDismissed()
                        onNavigateToMissionList()
                    }
                    state.photoResult != null -> viewModel.onPhotoResultAcknowledged()
                }
            },
            properties = DialogProperties(
                usePlatformDefaultWidth = false,
                dismissOnBackPress = !(state.isUploading || state.celebration != null),
                dismissOnClickOutside = false
            )
        ) {
            Surface(modifier = Modifier.fillMaxSize(), color = Color.White) {
                when {
                    state.isUploading -> MissionPhotoAnalyzingOverlay()
                    state.celebration != null -> MissionSuccessCelebrationOverlay(state.celebration)
                    else -> state.photoResult?.let { result ->
                        MissionPhotoResultOverlay(
                            result = result,
                            onPrimary = {
                                when (result.verdict) {
                                    PhotoVerdictUi.REJECT -> viewModel.onPhotoResultRetake()
                                    PhotoVerdictUi.PASS -> {
                                        viewModel.onPhotoResultDismissed()
                                        onNavigateToMissionList()
                                    }
                                    PhotoVerdictUi.REVIEW -> viewModel.onPhotoResultAcknowledged()
                                }
                            },
                            onSecondary = {
                                if (result.verdict == PhotoVerdictUi.PASS) {
                                    viewModel.onPhotoResultDismissed()
                                    onNavigateToHome()
                                } else {
                                    viewModel.onPhotoResultAcknowledged()
                                }
                            },
                            recommendation = recommendedMission,
                            recommendationLoaded = recommendationLoaded,
                            onStartRecommendedMission = { recommendedMissionId ->
                                viewModel.onPhotoResultDismissed()
                                onNavigateToDetail(recommendedMissionId)
                            },
                            onSkipRecommendedMission = {
                                viewModel.onPhotoResultDismissed()
                                onNavigateToHome()
                            }
                        )
                    }
                }
            }
        }
    }

    // ---- 위치(GPS) 획득: Android 프레임워크 영역 ----

    /**
     * [provider] 로 **새 위치를 한 번 요청**한다. 못 받으면 [fallback] 으로 한 번 더 시도한다.
     *
     * `getLastKnownLocation` 은 쓰지 않는다. 그건 GPS 를 켜지 않고 마지막으로 저장된 값만
     * 돌려주므로, 실내이거나 오랜만에 실행하면 몇 시간 전 다른 동네 좌표가 그대로 나온다.
     * (실제로 목표 지점에 서 있는데 24km 떨어졌다고 나오던 원인)
     *
     * [LocationManagerCompat] 는 API 30 의 `getCurrentLocation` 을 구버전까지 backport 한다.
     */
    fun requestFreshLocation(provider: String, fallback: String?) {
        val lm = locationManager ?: return viewModel.onLocationResult(null)
        // null 은 두 CancellationSignal 오버로드 사이에서 모호하므로 타입을 못 박는다.
        LocationManagerCompat.getCurrentLocation(
            lm, provider, null as CancellationSignal?, ContextCompat.getMainExecutor(context)
        ) { location: Location? ->
            val fresh = location != null && locationAgeMillis(location) <= MAX_LOCATION_AGE_MILLIS
            when {
                fresh -> viewModel.onLocationResult(location)
                // GPS 는 실내에서 자주 실패한다. 그때는 network(와이파이·기지국) 로 넘어간다.
                fallback != null -> requestFreshLocation(fallback, null)
                else -> viewModel.onLocationResult(null)
            }
        }
    }

    fun requestLocation() {
        if (Firebase.auth.currentUser == null) {
            Toast.makeText(context, context.getString(R.string.toast_login_required), Toast.LENGTH_SHORT).show()
            return
        }
        if (locationManager == null) {
            Toast.makeText(context, context.getString(R.string.toast_location_unavailable), Toast.LENGTH_SHORT).show()
            return
        }
        val gpsEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
        val networkEnabled = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
        if (!gpsEnabled && !networkEnabled) {
            Toast.makeText(context, context.getString(R.string.toast_enable_gps), Toast.LENGTH_SHORT).show()
            return
        }

        viewModel.onLocationRequestStarted()
        // GPS 가 정확하므로 먼저 쓰고, 실패하면 network 로 폴백한다.
        if (gpsEnabled) {
            requestFreshLocation(
                LocationManager.GPS_PROVIDER,
                fallback = if (networkEnabled) LocationManager.NETWORK_PROVIDER else null,
            )
        } else {
            requestFreshLocation(LocationManager.NETWORK_PROVIDER, fallback = null)
        }
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val granted = result[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            result[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted) {
            requestLocation()
        } else {
            Toast.makeText(context, context.getString(R.string.toast_location_permission_required), Toast.LENGTH_SHORT).show()
        }
    }

    fun onVerifyLocationClick() {
        val hasFine = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val hasCoarse = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        if (hasFine || hasCoarse) {
            requestLocation()
        } else {
            locationPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    // ---- 카메라 촬영: Android 프레임워크 영역 ----
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        val uri = pendingCameraUri
        if (success && uri != null) {
            viewModel.onPhotoCaptured(uri)
        } else {
            pendingCameraUri = null
            viewModel.onPhotoCaptureCancelled()
        }
    }

    fun launchCamera() {
        val dir = File(context.cacheDir, "mission_photos").apply { mkdirs() }
        val file = File(dir, "capture_${System.currentTimeMillis()}.jpg")
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        pendingCameraUri = uri
        cameraLauncher.launch(uri)
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            launchCamera()
        } else {
            Toast.makeText(context, context.getString(R.string.toast_camera_permission_required), Toast.LENGTH_SHORT).show()
        }
    }

    // 촬영본 Uri 를 바이트로 읽어 ViewModel 로 넘긴다 (Supabase 업로드용). 실패 시 null.
    fun readCapturedPhotoBytes(): ByteArray? =
        state.capturedPhotoUri?.let { uri ->
            runCatching {
                context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            }.getOrNull()
        }

    fun startPhotoCapture() {
        if (!state.locationVerified) {
            Toast.makeText(context, context.getString(R.string.toast_verify_location_first), Toast.LENGTH_SHORT).show()
            return
        }
        val hasCamera = ContextCompat.checkSelfPermission(
            context, Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
        if (hasCamera) {
            launchCamera()
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.perform_title), fontWeight = FontWeight.Bold) },
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = LightPurple,
                shape = RoundedCornerShape(24.dp)
            ) {
                Column(
                    modifier = Modifier.padding(22.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(state.missionTitle, fontWeight = FontWeight.ExtraBold, fontSize = 26.sp, color = Color(0xFF2C2C2C))
                    Text(stringResource(R.string.perform_intro_body), color = Color.Gray, lineHeight = 21.sp)
                    Text(
                        stringResource(R.string.perform_reward_line, state.stage1Reward, state.stage2Reward),
                        color = Color(0xFF5A4DB4),
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp
                    )
                    state.missionLocation?.let {
                        Text(
                            stringResource(
                                R.string.perform_target_location,
                                it.latitude,
                                it.longitude,
                                state.allowedRadiusMeters.toInt()
                            ),
                            color = MainPurple,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = CardGray,
                shape = RoundedCornerShape(22.dp)
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .background(if (state.locationVerified) Color(0xFFE7F7EA) else Color.White, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                if (state.locationVerified) Icons.Default.CheckCircle else Icons.Default.LocationSearching,
                                null,
                                tint = if (state.locationVerified) Color(0xFF4CAF50) else MainPurple
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(stringResource(R.string.perform_step1_title), fontWeight = FontWeight.Bold, fontSize = 17.sp)
                            val verificationText = state.verificationText.ifEmpty { stringResource(R.string.perform_status_not_verified_yet) }
                            Text(
                                if (state.stage1RewardGranted && state.locationVerified) {
                                    verificationText + stringResource(R.string.perform_reward_granted_suffix, state.stage1Reward)
                                } else verificationText,
                                color = Color.Gray,
                                fontSize = 13.sp
                            )
                        }
                    }

                    Button(
                        onClick = { onVerifyLocationClick() },
                        enabled = !state.isVerifying,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = MainPurple),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        if (state.isVerifying) {
                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        } else {
                            Text(
                                stringResource(if (state.locationVerified) R.string.perform_btn_reverify else R.string.perform_btn_verify),
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = if (state.locationVerified) Color(0xFFFFF7E8) else CardGray,
                shape = RoundedCornerShape(22.dp)
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .background(if (state.missionCompleted) Color(0xFFE7F7EA) else Color.White, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.CheckCircle,
                                null,
                                tint = if (state.missionCompleted) Color(0xFF4CAF50) else MainPurple
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(stringResource(R.string.perform_step2_title), fontWeight = FontWeight.Bold, fontSize = 17.sp)
                            Text(
                                if (state.missionCompleted) {
                                    if (state.stage2RewardGranted) {
                                        stringResource(R.string.perform_step2_desc_completed_with_reward, state.stage2Reward)
                                    } else {
                                        stringResource(R.string.perform_step2_desc_completed)
                                    }
                                } else if (state.locationVerified) {
                                    stringResource(R.string.perform_step2_desc_ready, state.stage2Reward)
                                } else {
                                    stringResource(R.string.perform_step2_desc_locked)
                                },
                                color = Color.Gray,
                                fontSize = 13.sp
                            )
                        }
                    }

                    // 촬영한 사진 미리보기 (로컬 촬영본 우선, 없으면 업로드된 사진)
                    val previewModel: Any? = state.capturedPhotoUri ?: state.photoUrl
                    if (previewModel != null) {
                        AsyncImage(
                            model = previewModel,
                            contentDescription = stringResource(R.string.perform_photo_preview_desc),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp)
                                .border(1.dp, Color(0xFFE0D8C4), RoundedCornerShape(14.dp))
                                .background(Color.White, RoundedCornerShape(14.dp))
                        )
                    }

                    state.uploadError?.let {
                        Text(it, color = Color(0xFFD32F2F), fontSize = 13.sp)
                    }

                    if (!state.missionCompleted) {
                        if (state.capturedPhotoUri == null) {
                            Button(
                                onClick = { startPhotoCapture() },
                                enabled = state.locationVerified && !state.isUploading,
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE38B2C)),
                                shape = RoundedCornerShape(16.dp)
                            ) {
                                Icon(Icons.Default.CameraAlt, null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(stringResource(R.string.perform_btn_photo_capture), fontWeight = FontWeight.Bold)
                            }
                        } else {
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                OutlinedButton(
                                    onClick = { startPhotoCapture() },
                                    enabled = !state.isUploading,
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(16.dp)
                                ) {
                                    Text(stringResource(R.string.perform_btn_retake))
                                }
                                Button(
                                    onClick = { viewModel.uploadPhotoAndComplete(readCapturedPhotoBytes()) },
                                    enabled = !state.isUploading,
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE38B2C)),
                                    shape = RoundedCornerShape(16.dp)
                                ) {
                                    if (state.isUploading) {
                                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                    } else {
                                        Text(
                                            stringResource(if (state.uploadError != null) R.string.perform_btn_retry else R.string.perform_btn_photo_complete),
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                        if (state.isUploading) {
                            Text(stringResource(R.string.perform_uploading), color = Color.Gray, fontSize = 12.sp)
                        }
                    }
                }
            }

            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = Color.White,
                shape = RoundedCornerShape(22.dp),
                shadowElevation = 2.dp
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(stringResource(R.string.perform_progress_title), fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    StatusRow(
                        stringResource(R.string.perform_status_location_permission),
                        stringResource(R.string.perform_status_location_permission_value),
                        Icons.Default.Place
                    )
                    val rewardText = stringResource(R.string.perform_status_reward_pending)
                    StatusRow(
                        stringResource(R.string.perform_status_gps),
                        if (state.locationVerified) {
                            stringResource(R.string.perform_status_verified) + " · " +
                                if (state.stage1RewardGranted) stringResource(R.string.perform_status_reward_granted, state.stage1Reward) else rewardText
                        } else {
                            stringResource(R.string.perform_status_waiting)
                        },
                        Icons.Default.LocationSearching
                    )
                    StatusRow(
                        stringResource(R.string.perform_status_photo),
                        if (state.missionCompleted) {
                            stringResource(R.string.perform_status_completed) + " · " +
                                if (state.stage2RewardGranted) stringResource(R.string.perform_status_reward_granted, state.stage2Reward) else rewardText
                        } else if (state.locationVerified) {
                            stringResource(R.string.perform_status_button_active)
                        } else {
                            stringResource(R.string.perform_status_after_location)
                        },
                        Icons.Default.CheckCircle
                    )
                }
            }
        }
    }
}

@Composable
private fun StatusRow(title: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = MainPurple, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text(title, modifier = Modifier.weight(1f), color = Color(0xFF3A3A3A))
        Text(value, color = Color.Gray, fontSize = 13.sp)
    }
}
