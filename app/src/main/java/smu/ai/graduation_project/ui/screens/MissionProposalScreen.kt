package smu.ai.graduation_project.ui.screens

import android.content.ComponentCallbacks
import android.content.Context
import android.content.res.Configuration
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
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
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import coil.compose.AsyncImage
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.GeoPoint
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.firestore
import com.naver.maps.geometry.LatLng
import com.naver.maps.map.CameraUpdate
import com.naver.maps.map.MapView
import com.naver.maps.map.NaverMap
import com.naver.maps.map.overlay.Marker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import smu.ai.graduation_project.R
import smu.ai.graduation_project.data.SupabaseStorage
import smu.ai.graduation_project.domain.MissionReviewStatus
import smu.ai.graduation_project.ui.components.categoryLabel
import smu.ai.graduation_project.ui.theme.CardGray
import smu.ai.graduation_project.ui.theme.MainPurple
import java.io.IOException

private val PROPOSAL_CATEGORIES = listOf("투어", "맛집", "체험", "쇼핑")

/**
 * 사용자 미션 제안 생성/재제출 화면.
 *
 * `missionId == null` 이면 신규 제안(`reviewStatus=pending`, `points=0`, `creatorId=본인`으로 생성).
 * `missionId != null` 이면 관리자가 "수정 요청"한 본인 미션을 프리필해 보여주고, 제출 시
 * `reviewStatus` 를 다시 `pending` 으로 돌린다(재제출). 포인트는 사용자가 정할 수 없고, 관리자가
 * [smu.ai.graduation_project.ui.admin.AdminMissionReviewScreen] 승인 시점에 정한다 — 이는
 * `firestore.rules` 로도 강제된다.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MissionProposalScreen(
    missionId: String?,
    onNavigateBack: () -> Unit,
    onSubmitSuccess: () -> Unit
) {
    val db = Firebase.firestore
    val uid = Firebase.auth.currentUser?.uid
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val isEdit = !missionId.isNullOrBlank()

    var title by remember { mutableStateOf("") }
    var desc by remember { mutableStateOf("") }
    var category by remember { mutableStateOf(PROPOSAL_CATEGORIES.first()) }
    var estimatedMinutes by remember { mutableStateOf("") }
    var imageUrl by remember { mutableStateOf("") }
    var pickedImageUri by remember { mutableStateOf<Uri?>(null) }
    var latitude by remember { mutableStateOf<Double?>(null) }
    var longitude by remember { mutableStateOf<Double?>(null) }
    var creatorName by remember { mutableStateOf("") }
    var isSubmitting by remember { mutableStateOf(false) }
    var loaded by remember { mutableStateOf(!isEdit) }

    LaunchedEffect(missionId) {
        if (isEdit) {
            db.collection("missions").document(missionId!!).get().addOnSuccessListener { doc ->
                title = doc.getString("title").orEmpty()
                desc = doc.getString("desc").orEmpty()
                category = doc.getString("category")?.takeIf { it in PROPOSAL_CATEGORIES } ?: PROPOSAL_CATEGORIES.first()
                imageUrl = doc.getString("imageUrl").orEmpty()
                estimatedMinutes = doc.getLong("estimatedMinutes")?.toString().orEmpty()
                doc.getGeoPoint("location")?.let {
                    latitude = it.latitude
                    longitude = it.longitude
                }
                loaded = true
            }
        }
    }

    LaunchedEffect(uid) {
        uid?.let { u ->
            db.collection("users").document(u).get().addOnSuccessListener { doc ->
                creatorName = doc.getString("nickname").orEmpty()
            }
        }
    }

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) pickedImageUri = uri
    }

    val toastLoginRequired = stringResource(R.string.toast_login_required)
    val toastRequiredFields = stringResource(R.string.admin_toast_required_fields)
    val toastUploadFailed = stringResource(R.string.proposal_toast_upload_failed)
    val toastSaveFailed = stringResource(R.string.admin_toast_save_failed)
    val toastSubmitted = stringResource(R.string.proposal_toast_submitted)

    fun submit() {
        val currentUid = uid
        if (currentUid == null) {
            Toast.makeText(context, toastLoginRequired, Toast.LENGTH_SHORT).show()
            return
        }
        if (title.isBlank() || desc.isBlank()) {
            Toast.makeText(context, toastRequiredFields, Toast.LENGTH_SHORT).show()
            return
        }
        isSubmitting = true
        scope.launch {
            try {
                val finalImageUrl = withContext(Dispatchers.IO) {
                    val uri = pickedImageUri
                    if (uri != null) {
                        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                            ?: throw IOException("cannot read picked image")
                        SupabaseStorage.upload("proposals/${currentUid}_${System.currentTimeMillis()}.jpg", bytes)
                    } else imageUrl
                }
                val payload = buildMap<String, Any> {
                    put("title", title.trim())
                    put("desc", desc.trim())
                    put("category", category)
                    put("imageUrl", finalImageUrl)
                    when (val minutes = estimatedMinutes.toIntOrNull()) {
                        null -> if (isEdit) put("estimatedMinutes", FieldValue.delete())
                        else -> put("estimatedMinutes", minutes)
                    }
                    val lat = latitude
                    val lng = longitude
                    when {
                        lat != null && lng != null -> put("location", GeoPoint(lat, lng))
                        isEdit -> put("location", FieldValue.delete())
                    }
                    put("reviewStatus", MissionReviewStatus.PENDING)
                    if (isEdit) {
                        put("reviewNote", FieldValue.delete())
                    } else {
                        put("creatorId", currentUid)
                        put("creatorName", creatorName)
                        put("points", 0)
                    }
                }
                val task = if (isEdit) {
                    db.collection("missions").document(missionId!!).set(payload, SetOptions.merge())
                } else {
                    db.collection("missions").add(payload)
                }
                task.addOnSuccessListener {
                    isSubmitting = false
                    Toast.makeText(context, toastSubmitted, Toast.LENGTH_SHORT).show()
                    onSubmitSuccess()
                }.addOnFailureListener {
                    isSubmitting = false
                    Toast.makeText(context, toastSaveFailed, Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                isSubmitting = false
                Toast.makeText(context, toastUploadFailed, Toast.LENGTH_SHORT).show()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(if (isEdit) R.string.proposal_edit_title else R.string.proposal_new_title), fontWeight = FontWeight.Bold) },
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
        if (!loaded) {
            Box(Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = MainPurple)
            }
            return@Scaffold
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(stringResource(R.string.proposal_intro), color = Color.Gray, fontSize = 13.sp)

            OutlinedTextField(title, { title = it }, modifier = Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.admin_mission_field_title)) })
            OutlinedTextField(desc, { desc = it }, modifier = Modifier.fillMaxWidth(), minLines = 4, label = { Text(stringResource(R.string.admin_mission_field_desc)) })

            Text(stringResource(R.string.admin_mission_field_category), fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color.Gray)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PROPOSAL_CATEGORIES.forEach { value ->
                    val selected = value == category
                    Surface(
                        modifier = Modifier.clickable { category = value },
                        shape = RoundedCornerShape(12.dp),
                        color = if (selected) MainPurple else CardGray
                    ) {
                        Text(
                            text = categoryLabel(value),
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            color = if (selected) Color.White else Color.Gray,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                }
            }

            OutlinedTextField(
                estimatedMinutes,
                { estimatedMinutes = it.filter { ch -> ch.isDigit() } },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.admin_mission_field_duration_minutes)) }
            )

            Text(stringResource(R.string.proposal_image_label), fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color.Gray)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp)
                    .background(CardGray, RoundedCornerShape(16.dp))
                    .clip(RoundedCornerShape(16.dp))
                    .clickable {
                        imagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    },
                contentAlignment = Alignment.Center
            ) {
                val previewModel = pickedImageUri ?: imageUrl.takeIf { it.isNotBlank() }
                if (previewModel != null) {
                    AsyncImage(model = previewModel, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Image, null, tint = Color.LightGray, modifier = Modifier.size(36.dp))
                        Text(stringResource(R.string.proposal_image_pick), color = Color.Gray, fontSize = 12.sp)
                    }
                }
            }

            Text(stringResource(R.string.proposal_location_label), fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color.Gray)
            Text(stringResource(R.string.proposal_location_hint), color = Color.Gray, fontSize = 12.sp)
            LocationPickerMap(
                initialLatLng = if (latitude != null && longitude != null) LatLng(latitude!!, longitude!!) else null,
                onLocationPicked = { lat, lng -> latitude = lat; longitude = lng },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
                    .clip(RoundedCornerShape(16.dp))
            )
            if (latitude != null && longitude != null) {
                Text(
                    stringResource(R.string.proposal_location_selected, latitude ?: 0.0, longitude ?: 0.0),
                    color = MainPurple,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Button(
                onClick = { submit() },
                enabled = !isSubmitting,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = MainPurple)
            ) {
                if (isSubmitting) {
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(22.dp), strokeWidth = 2.5.dp)
                } else {
                    Text(stringResource(if (isEdit) R.string.proposal_submit_resubmit else R.string.proposal_submit_new))
                }
            }
        }
    }
}

/** 탭 한 번으로 마커를 놓아 위/경도를 고르는 경량 지도. [smu.ai.graduation_project.ui.screens.MissionMapScreen]의 지도 생명주기 처리를 단순화해 그대로 따른다. */
@Composable
private fun LocationPickerMap(
    initialLatLng: LatLng?,
    onLocationPicked: (Double, Double) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val holder = rememberSaveable(saver = Saver<PickerMapHolder, Bundle>(
        save = { it.saveState() },
        restore = { PickerMapHolder(context, it) }
    )) { PickerMapHolder(context, null) }

    DisposableEffect(holder, lifecycle) {
        lifecycle.addObserver(holder)
        context.registerComponentCallbacks(holder)
        onDispose {
            lifecycle.removeObserver(holder)
            context.unregisterComponentCallbacks(holder)
            holder.destroy()
        }
    }

    val map = holder.map
    DisposableEffect(map) {
        var marker: Marker? = null
        if (map != null && !holder.destroyed) {
            val initial = initialLatLng ?: LatLng(37.5665, 126.9780)
            map.moveCamera(CameraUpdate.scrollAndZoomTo(initial, 14.0))
            val newMarker = Marker(initial)
            if (initialLatLng != null) newMarker.map = map
            marker = newMarker
            map.setOnMapClickListener { _, coord ->
                newMarker.position = coord
                newMarker.map = map
                onLocationPicked(coord.latitude, coord.longitude)
            }
        }
        onDispose { marker?.map = null }
    }

    AndroidView(factory = { holder.view }, modifier = modifier)
}

private class PickerMapHolder(context: Context, savedState: Bundle?) :
    LifecycleEventObserver, ComponentCallbacks {
    val view = MapView(context)
    var map by mutableStateOf<NaverMap?>(null)
        private set
    var destroyed = false
        private set
    private var started = false
    private var resumed = false

    init {
        view.onCreate(savedState)
        view.getMapAsync { if (!destroyed) map = it }
    }

    override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
        if (destroyed) return
        when (event) {
            Lifecycle.Event.ON_START -> if (!started) { view.onStart(); started = true }
            Lifecycle.Event.ON_RESUME -> if (!resumed) { view.onResume(); resumed = true }
            Lifecycle.Event.ON_PAUSE -> pause()
            Lifecycle.Event.ON_STOP -> stop()
            Lifecycle.Event.ON_DESTROY -> destroy()
            else -> Unit
        }
    }

    fun saveState() = Bundle().also { if (!destroyed) view.onSaveInstanceState(it) }
    private fun pause() { if (resumed) { view.onPause(); resumed = false } }
    private fun stop() { pause(); if (started) { view.onStop(); started = false } }
    fun destroy() {
        if (!destroyed) { stop(); destroyed = true; view.onDestroy() }
    }
    override fun onLowMemory() { if (!destroyed) view.onLowMemory() }
    override fun onConfigurationChanged(newConfig: Configuration) = Unit
}
