package smu.ai.graduation_project.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.os.CancellationSignal
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import smu.ai.graduation_project.ui.theme.CardGray
import smu.ai.graduation_project.ui.theme.LightPurple
import smu.ai.graduation_project.ui.theme.MainPurple

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MissionPerformScreen(
    missionId: String,
    onNavigateBack: () -> Unit,
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

    // ---- 위치(GPS) 획득: Android 프레임워크 영역 ----
    fun requestLocation() {
        if (Firebase.auth.currentUser == null) {
            Toast.makeText(context, "로그인이 필요합니다.", Toast.LENGTH_SHORT).show()
            return
        }
        if (locationManager == null) {
            Toast.makeText(context, "위치 서비스를 사용할 수 없습니다.", Toast.LENGTH_SHORT).show()
            return
        }
        val gpsEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
        val networkEnabled = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
        if (!gpsEnabled && !networkEnabled) {
            Toast.makeText(context, "위치를 사용하려면 GPS를 켜주세요.", Toast.LENGTH_SHORT).show()
            return
        }

        viewModel.onLocationRequestStarted()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val provider = if (gpsEnabled) LocationManager.GPS_PROVIDER else LocationManager.NETWORK_PROVIDER
            locationManager.getCurrentLocation(provider, CancellationSignal(), context.mainExecutor) { location ->
                viewModel.onLocationResult(location)
            }
        } else {
            val location = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                ?: locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
            viewModel.onLocationResult(location)
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
            Toast.makeText(context, "위치 권한이 필요합니다.", Toast.LENGTH_SHORT).show()
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
            Toast.makeText(context, "카메라 권한이 필요합니다.", Toast.LENGTH_SHORT).show()
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
            Toast.makeText(context, "먼저 위치 인증을 완료해주세요.", Toast.LENGTH_SHORT).show()
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
                title = { Text("미션 수행", fontWeight = FontWeight.Bold) },
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
                    Text("첫 단계는 현재 위치를 인증하는 것입니다. GPS 권한을 허용하고 현장에서 인증 버튼을 눌러주세요.", color = Color.Gray, lineHeight = 21.sp)
                    Text("포인트 지급: 1단계 ${state.stage1Reward}P · 2단계 ${state.stage2Reward}P", color = Color(0xFF5A4DB4), fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    state.missionLocation?.let {
                        Text(
                            "목표 위치: %.4f, %.4f · 반경 ${state.allowedRadiusMeters.toInt()}m 안에서 인증".format(it.latitude, it.longitude),
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
                            Text("1단계 · GPS 위치 인증", fontWeight = FontWeight.Bold, fontSize = 17.sp)
                            Text(
                                if (state.stage1RewardGranted && state.locationVerified) "${state.verificationText} · ${state.stage1Reward}P 지급 완료" else state.verificationText,
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
                            Text(if (state.locationVerified) "위치 다시 인증하기" else "위치 인증하기", fontWeight = FontWeight.Bold)
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
                            Text("2단계 · 사진 인증", fontWeight = FontWeight.Bold, fontSize = 17.sp)
                            Text(
                                if (state.missionCompleted) {
                                    if (state.stage2RewardGranted) "사진 인증까지 완료됐고 ${state.stage2Reward}P 지급도 반영됐습니다." else "사진 인증까지 완료된 상태입니다."
                                } else if (state.locationVerified) {
                                    "위치 인증이 끝났습니다. 사진 인증 완료 시 ${state.stage2Reward}P가 지급됩니다."
                                } else {
                                    "위치 인증이 끝나야 사진 인증 단계로 진행할 수 있습니다."
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
                            contentDescription = "인증 사진 미리보기",
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
                                Text("사진 촬영하기", fontWeight = FontWeight.Bold)
                            }
                        } else {
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                OutlinedButton(
                                    onClick = { startPhotoCapture() },
                                    enabled = !state.isUploading,
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(16.dp)
                                ) {
                                    Text("다시 촬영")
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
                                        Text(if (state.uploadError != null) "다시 시도" else "사진 인증 완료", fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                        if (state.isUploading) {
                            Text("사진 업로드 중입니다...", color = Color.Gray, fontSize = 12.sp)
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
                    Text("진행 상태", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    StatusRow("위치 권한", "허용 후 인증 버튼 실행", Icons.Default.Place)
                    StatusRow("GPS 인증", if (state.locationVerified) "인증 완료 · ${if (state.stage1RewardGranted) "${state.stage1Reward}P 지급" else "지급 대기"}" else "대기 중", Icons.Default.LocationSearching)
                    StatusRow("사진 인증", if (state.missionCompleted) "완료됨 · ${if (state.stage2RewardGranted) "${state.stage2Reward}P 지급" else "지급 대기"}" else if (state.locationVerified) "버튼 활성화" else "위치 인증 후 진행", Icons.Default.CheckCircle)
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
