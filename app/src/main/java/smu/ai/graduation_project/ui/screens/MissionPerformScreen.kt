package smu.ai.graduation_project.ui.screens

import android.Manifest
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.CancellationSignal
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.LocationSearching
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.GeoPoint
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.firestore
import smu.ai.graduation_project.ui.theme.CardGray
import smu.ai.graduation_project.ui.theme.LightPurple
import smu.ai.graduation_project.ui.theme.MainPurple

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MissionPerformScreen(
    missionId: String,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val db = Firebase.firestore
    val user = Firebase.auth.currentUser
    val locationManager = remember {
        context.getSystemService(LocationManager::class.java)
    }

    var missionTitle by remember { mutableStateOf("미션") }
    var missionPoints by remember { mutableStateOf(0) }
    var isVerifying by remember { mutableStateOf(false) }
    var isCompleting by remember { mutableStateOf(false) }
    var locationVerified by remember { mutableStateOf(false) }
    var missionCompleted by remember { mutableStateOf(false) }
    var stage1RewardGranted by remember { mutableStateOf(false) }
    var stage2RewardGranted by remember { mutableStateOf(false) }
    var verificationText by remember { mutableStateOf("아직 위치 인증을 하지 않았습니다.") }
    var missionDocId by remember { mutableStateOf<String?>(null) }
    var missionLocation by remember { mutableStateOf<GeoPoint?>(null) }

    val allowedRadiusMeters = 200f
    val stage1Reward = minOf(100, missionPoints)
    val stage2Reward = (missionPoints - stage1Reward).coerceAtLeast(0)

    fun verifyLocation() {
        if (user == null) {
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

        isVerifying = true
        val onLocationResult: (Location?) -> Unit = locationResult@{ location ->
            if (location == null) {
                isVerifying = false
                Toast.makeText(context, "현재 위치를 가져오지 못했습니다.", Toast.LENGTH_SHORT).show()
            } else {
                val lat = location.latitude
                val lng = location.longitude
                val targetLocation = missionLocation
                if (targetLocation == null) {
                    isVerifying = false
                    Toast.makeText(context, "미션 위치 정보가 없습니다.", Toast.LENGTH_SHORT).show()
                    return@locationResult
                }

                val distanceResult = FloatArray(1)
                Location.distanceBetween(
                    lat,
                    lng,
                    targetLocation.latitude,
                    targetLocation.longitude,
                    distanceResult
                )
                val distanceMeters = distanceResult[0]
                val isNearEnough = distanceMeters <= allowedRadiusMeters

                verificationText = if (isNearEnough) {
                    "위치 인증 완료 · 목표 지점까지 %.0fm".format(distanceMeters)
                } else {
                    "현재 위치가 인증 범위를 벗어났습니다 · %.0fm 떨어져 있어요".format(distanceMeters)
                }
                locationVerified = isNearEnough
                val targetDocId = missionDocId
                if (targetDocId != null) {
                    val userMissionRef = db.collection("user_missions").document(targetDocId)
                    val userRef = db.collection("users").document(user.uid)
                    db.runTransaction { transaction ->
                        val userMissionSnapshot = transaction.get(userMissionRef)
                        val alreadyRewarded = userMissionSnapshot.getBoolean("stage1RewardGranted") == true
                        val rewardToGrant = if (isNearEnough && !alreadyRewarded) stage1Reward else 0
                        transaction.update(
                            userMissionRef,
                            mapOf(
                                "locationVerified" to isNearEnough,
                                "verifiedLatitude" to lat,
                                "verifiedLongitude" to lng,
                                "distanceToTargetMeters" to distanceMeters,
                                "progress" to if (isNearEnough) 0.5f else 0f,
                                "status" to "In Progress",
                                "stage1RewardGranted" to (alreadyRewarded || isNearEnough),
                                "stage1RewardPoints" to stage1Reward
                            )
                        )
                        if (rewardToGrant > 0) {
                            transaction.set(
                                userRef,
                                mapOf("points" to FieldValue.increment(rewardToGrant.toLong())),
                                SetOptions.merge()
                            )
                        }
                        rewardToGrant
                    }.addOnSuccessListener { rewardToGrant ->
                            isVerifying = false
                            if (isNearEnough) {
                                stage1RewardGranted = true
                            }
                            Toast.makeText(
                                context,
                                when {
                                    !isNearEnough -> "미션 위치 근처에서 다시 시도해주세요."
                                    rewardToGrant > 0 -> "위치 인증 완료. ${rewardToGrant}P가 지급되었습니다."
                                    else -> "위치 인증이 완료되었습니다."
                                },
                                Toast.LENGTH_SHORT
                            ).show()
                        }.addOnFailureListener {
                            isVerifying = false
                            Toast.makeText(context, "위치 인증 처리에 실패했습니다.", Toast.LENGTH_SHORT).show()
                        }
                } else {
                    isVerifying = false
                    Toast.makeText(context, "미션 진행 정보가 없습니다.", Toast.LENGTH_SHORT).show()
                }
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val provider = if (gpsEnabled) LocationManager.GPS_PROVIDER else LocationManager.NETWORK_PROVIDER
            locationManager.getCurrentLocation(provider, CancellationSignal(), context.mainExecutor) { location ->
                onLocationResult(location)
            }
        } else {
            val location = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                ?: locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
            onLocationResult(location)
        }
    }

    fun completeMission() {
        if (user == null) {
            Toast.makeText(context, "로그인이 필요합니다.", Toast.LENGTH_SHORT).show()
            return
        }
        val targetDocId = missionDocId
        if (targetDocId == null) {
            Toast.makeText(context, "미션 진행 정보가 없습니다.", Toast.LENGTH_SHORT).show()
            return
        }
        if (!locationVerified) {
            Toast.makeText(context, "먼저 위치 인증을 완료해주세요.", Toast.LENGTH_SHORT).show()
            return
        }

        isCompleting = true
        val userMissionRef = db.collection("user_missions").document(targetDocId)
        val userRef = db.collection("users").document(user.uid)

        db.runTransaction { transaction ->
            val missionSnapshot = transaction.get(userMissionRef)
            val status = missionSnapshot.getString("status").orEmpty()
            val alreadyCompleted = status.contains("완료") || status.equals("Completed", true)
            val alreadyRewarded = missionSnapshot.getBoolean("stage2RewardGranted") == true
            val rewardToGrant = if (!alreadyRewarded) stage2Reward else 0
            transaction.update(
                userMissionRef,
                mapOf(
                    "status" to "Completed",
                    "progress" to 1f,
                    "locationVerified" to true,
                    "stage2RewardGranted" to true,
                    "stage2RewardPoints" to stage2Reward,
                    "completedAt" to FieldValue.serverTimestamp()
                )
            )
            if (!alreadyCompleted && rewardToGrant > 0) {
                transaction.set(
                    userRef,
                    mapOf("points" to FieldValue.increment(rewardToGrant.toLong())),
                    SetOptions.merge()
                )
            }
            rewardToGrant to alreadyCompleted
        }.addOnSuccessListener { result ->
            isCompleting = false
            missionCompleted = true
            stage2RewardGranted = true
            verificationText = "위치 인증 완료 · 미션이 완료되었습니다."
            val rewardToGrant = result.first
            val alreadyCompleted = result.second
            Toast.makeText(
                context,
                when {
                    alreadyCompleted -> "이미 완료 처리된 미션입니다."
                    rewardToGrant > 0 -> "사진 인증 완료. ${rewardToGrant}P가 지급되었습니다."
                    else -> "사진 인증 완료."
                },
                Toast.LENGTH_SHORT
            ).show()
            onNavigateBack()
        }.addOnFailureListener {
            isCompleting = false
            Toast.makeText(context, "미션 완료 처리에 실패했습니다.", Toast.LENGTH_SHORT).show()
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val granted = result[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            result[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted) {
            verifyLocation()
        } else {
            Toast.makeText(context, "위치 권한이 필요합니다.", Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(missionId, user?.uid) {
        db.collection("missions").document(missionId).get().addOnSuccessListener { doc ->
            missionTitle = doc.getString("title") ?: "미션"
            missionPoints = doc.getLong("points")?.toInt() ?: 0
            missionLocation = doc.getGeoPoint("location")
        }

        user?.uid?.let { uid ->
            db.collection("user_missions")
                .whereEqualTo("userId", uid)
                .whereEqualTo("missionId", missionId)
                .get()
                .addOnSuccessListener { snapshot ->
                    val doc = snapshot.documents.firstOrNull()
                    missionDocId = doc?.id
                    val rawStatus = doc?.getString("status").orEmpty()
                    val verified = doc?.getBoolean("locationVerified") == true
                    locationVerified = verified
                    missionCompleted = rawStatus.contains("완료") || rawStatus.equals("Completed", true)
                    stage1RewardGranted = doc?.getBoolean("stage1RewardGranted") == true
                    stage2RewardGranted = doc?.getBoolean("stage2RewardGranted") == true
                    if (missionCompleted) {
                        verificationText = "위치 인증 완료 · 미션 완료 상태입니다."
                    } else if (verified) {
                        val lat = doc.getDouble("verifiedLatitude") ?: 0.0
                        val lng = doc.getDouble("verifiedLongitude") ?: 0.0
                        verificationText = "위치 인증 완료: %.5f, %.5f".format(lat, lng)
                    }
                }
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
                    Text(missionTitle, fontWeight = FontWeight.ExtraBold, fontSize = 26.sp, color = Color(0xFF2C2C2C))
                    Text("첫 단계는 현재 위치를 인증하는 것입니다. GPS 권한을 허용하고 현장에서 인증 버튼을 눌러주세요.", color = Color.Gray, lineHeight = 21.sp)
                    Text("포인트 지급: 1단계 ${stage1Reward}P · 2단계 ${stage2Reward}P", color = Color(0xFF5A4DB4), fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    missionLocation?.let {
                        Text(
                            "목표 위치: %.4f, %.4f · 반경 ${allowedRadiusMeters.toInt()}m 안에서 인증".format(it.latitude, it.longitude),
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
                                .background(if (locationVerified) Color(0xFFE7F7EA) else Color.White, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                if (locationVerified) Icons.Default.CheckCircle else Icons.Default.LocationSearching,
                                null,
                                tint = if (locationVerified) Color(0xFF4CAF50) else MainPurple
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text("1단계 · GPS 위치 인증", fontWeight = FontWeight.Bold, fontSize = 17.sp)
                            Text(
                                if (stage1RewardGranted && locationVerified) "$verificationText · ${stage1Reward}P 지급 완료" else verificationText,
                                color = Color.Gray,
                                fontSize = 13.sp
                            )
                        }
                    }

                    Button(
                        onClick = {
                            val hasFine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED
                            val hasCoarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED
                            if (hasFine || hasCoarse) {
                                verifyLocation()
                            } else {
                                permissionLauncher.launch(
                                    arrayOf(
                                        Manifest.permission.ACCESS_FINE_LOCATION,
                                        Manifest.permission.ACCESS_COARSE_LOCATION
                                    )
                                )
                            }
                        },
                        enabled = !isVerifying,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = MainPurple),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        if (isVerifying) {
                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        } else {
                            Text(if (locationVerified) "위치 다시 인증하기" else "위치 인증하기", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = if (locationVerified) Color(0xFFFFF7E8) else CardGray,
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
                                .background(if (missionCompleted) Color(0xFFE7F7EA) else Color.White, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.CheckCircle,
                                null,
                                tint = if (missionCompleted) Color(0xFF4CAF50) else MainPurple
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text("2단계 · 사진 인증", fontWeight = FontWeight.Bold, fontSize = 17.sp)
                            Text(
                                if (missionCompleted) {
                                    if (stage2RewardGranted) "사진 인증까지 완료됐고 ${stage2Reward}P 지급도 반영됐습니다." else "사진 인증까지 완료된 상태입니다."
                                } else if (locationVerified) {
                                    "위치 인증이 끝났습니다. 사진 인증 완료 시 ${stage2Reward}P가 지급됩니다."
                                } else {
                                    "위치 인증이 끝나야 사진 인증 단계로 진행할 수 있습니다."
                                },
                                color = Color.Gray,
                                fontSize = 13.sp
                            )
                        }
                    }

                    Button(
                        onClick = ::completeMission,
                        enabled = locationVerified && !missionCompleted && !isCompleting,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE38B2C)),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        if (isCompleting) {
                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        } else {
                            Text(
                                if (missionCompleted) "사진 인증 완료" else "사진 인증하기",
                                fontWeight = FontWeight.Bold
                            )
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
                    StatusRow("GPS 인증", if (locationVerified) "인증 완료 · ${if (stage1RewardGranted) "${stage1Reward}P 지급" else "지급 대기"}" else "대기 중", Icons.Default.LocationSearching)
                    StatusRow("사진 인증", if (missionCompleted) "완료됨 · ${if (stage2RewardGranted) "${stage2Reward}P 지급" else "지급 대기"}" else if (locationVerified) "버튼 활성화" else "위치 인증 후 진행", Icons.Default.CheckCircle)
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
