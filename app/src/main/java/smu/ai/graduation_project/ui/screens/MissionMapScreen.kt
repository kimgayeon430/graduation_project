package smu.ai.graduation_project.ui.screens

import android.content.ComponentCallbacks
import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.doOnLayout
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import coil.imageLoader
import coil.request.ImageRequest
import com.naver.maps.geometry.LatLng
import com.naver.maps.geometry.LatLngBounds
import com.naver.maps.map.CameraUpdate
import com.naver.maps.map.MapView
import com.naver.maps.map.NaverMap
import com.naver.maps.map.overlay.InfoWindow
import com.naver.maps.map.overlay.Marker
import com.naver.maps.map.overlay.OverlayImage
import smu.ai.graduation_project.R
import smu.ai.graduation_project.data.getLocalizedString
import smu.ai.graduation_project.model.Mission
import smu.ai.graduation_project.ui.theme.CardGray
import smu.ai.graduation_project.ui.theme.MainPurple
import smu.ai.graduation_project.ui.theme.Orange

private val CAPTION_TEXT_COLOR = android.graphics.Color.parseColor("#333333")
private val CAPTION_HALO_COLOR = android.graphics.Color.WHITE

// Missions within roughly this many degrees (~80m) are treated as the same spot and clustered
// into one pin, instead of requiring bit-for-bit identical coordinates.
private const val CLUSTER_GRID_DEGREES = 0.0008

@Composable
fun MissionMapScreen(
    missions: List<Mission>,
    onMissionClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val holder = rememberSaveable(saver = Saver<MissionMapHolder, Bundle>(
        save = { it.saveState() },
        restore = { MissionMapHolder(context, it) }
    )) { MissionMapHolder(context, null) }
    val currentOnClick by rememberUpdatedState(onMissionClick)
    val groups = remember(missions) {
        missions.filter {
            val lat = it.latitude
            val lng = it.longitude
            lat != null && lng != null && lat.isFinite() && lng.isFinite() &&
                lat in -90.0..90.0 && lng in -180.0..180.0
        }.groupBy { mission ->
            val lat = requireNotNull(mission.latitude)
            val lng = requireNotNull(mission.longitude)
            Math.round(lat / CLUSTER_GRID_DEGREES) to Math.round(lng / CLUSTER_GRID_DEGREES)
        }.values.map { group ->
            val lat = group.map { requireNotNull(it.latitude) }.average()
            val lng = group.map { requireNotNull(it.longitude) }.average()
            LatLng(lat, lng) to group
        }
    }
    var selectedIds by remember { mutableStateOf<List<String>>(emptyList()) }
    val selectedMissions = missions.filter { it.id in selectedIds }

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
    DisposableEffect(map, groups) {
        var active = true
        // Only one bubble is shown at a time so labels never pile up on screen.
        var openInfoWindow: InfoWindow? = null
        val markers = mutableListOf<Marker>()
        if (map != null && !holder.destroyed) {
            map.setOnMapClickListener { _, _ ->
                openInfoWindow?.close()
                openInfoWindow = null
            }
            groups.forEach { (coordinate, group) ->
                val marker = Marker(coordinate).apply {
                    icon = OverlayImage.fromResource(
                        if (group.size == 1) missionPinDrawableRes(group.first().status) else R.drawable.ic_map_pin_cluster
                    )
                    captionText = if (group.size == 1) group.first().title
                        else context.getLocalizedString(R.string.map_marker_cluster, group.size)
                    captionTextSize = 12f
                    captionColor = CAPTION_TEXT_COLOR
                    captionHaloColor = CAPTION_HALO_COLOR
                    captionRequestedWidth = (120 * context.resources.displayMetrics.density).toInt()
                    // Hide overlapping pins/labels automatically when missions are close together.
                    isHideCollidedMarkers = true
                    isHideCollidedCaptions = true
                    isHideCollidedSymbols = true
                    setOnClickListener {
                        openInfoWindow?.close()
                        if (group.size == 1) {
                            val mission = group.first()
                            val photoUrl = mission.verifiedPhotoUrl
                            if (mission.status == "완료" && !photoUrl.isNullOrBlank()) {
                                // 사진은 비동기로 불러와야 하므로, 로드가 끝난 뒤에 정보창을 연다.
                                context.imageLoader.enqueue(
                                    ImageRequest.Builder(context)
                                        .data(photoUrl)
                                        .target(onSuccess = { drawable ->
                                            if (active && !holder.destroyed) {
                                                val window = InfoWindow().apply {
                                                    position = coordinate
                                                    adapter = object : InfoWindow.Adapter() {
                                                        override fun getImage(infoWindow: InfoWindow): OverlayImage =
                                                            OverlayImage.fromBitmap(
                                                                renderViewToBitmap(buildPhotoInfoWindowView(context, mission, drawable))
                                                            )
                                                    }
                                                    setOnClickListener {
                                                        close()
                                                        currentOnClick(mission.id)
                                                        true
                                                    }
                                                }
                                                window.open(map)
                                                openInfoWindow = window
                                            }
                                        })
                                        .build()
                                )
                            } else {
                                val window = InfoWindow().apply {
                                    position = coordinate
                                    adapter = object : InfoWindow.DefaultTextAdapter(context) {
                                        override fun getText(infoWindow: InfoWindow): CharSequence =
                                            context.getLocalizedString(R.string.map_bubble_single, mission.title, mission.points)
                                    }
                                    setOnClickListener {
                                        close()
                                        currentOnClick(mission.id)
                                        true
                                    }
                                }
                                window.open(map)
                                openInfoWindow = window
                            }
                        } else {
                            openInfoWindow = null
                            selectedIds = group.map { it.id }
                        }
                        true
                    }
                    this.map = map
                }
                markers.add(marker)
            }
            // Fit after layout so every mission is initially within the viewport.
            val coordinates = groups.map { it.first }
            if (holder.fittedCoordinates != coordinates) {
                holder.view.doOnLayout {
                    if (active && !holder.destroyed) {
                        when (coordinates.size) {
                            0 -> map.moveCamera(CameraUpdate.scrollAndZoomTo(
                                LatLng(37.5665, 126.9780), 11.0))
                            1 -> map.moveCamera(CameraUpdate.scrollAndZoomTo(coordinates.first(), 14.0))
                            else -> {
                                val bounds = LatLngBounds.Builder()
                                coordinates.forEach { bounds.include(it) }
                                map.moveCamera(CameraUpdate.fitBounds(bounds.build(),
                                    (72 * context.resources.displayMetrics.density).toInt()))
                            }
                        }
                        holder.fittedCoordinates = coordinates
                    }
                }
            }
        }
        onDispose {
            active = false
            openInfoWindow?.close()
            markers.forEach { it.map = null }
        }
    }

    Box(modifier) {
        AndroidView(factory = { holder.view }, modifier = Modifier.fillMaxSize())
        Surface(
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 12.dp),
            shape = RoundedCornerShape(20.dp),
            color = Color.White,
            shadowElevation = 4.dp
        ) {
            val count = groups.sumOf { it.second.size }
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(Icons.Default.Place, contentDescription = null, tint = MainPurple, modifier = Modifier.size(16.dp))
                Text(
                    text = if (count == 0) stringResource(R.string.map_empty) else stringResource(R.string.map_summary, count),
                    fontSize = 13.sp,
                    color = Color(0xFF333333)
                )
            }
        }
    }

    if (selectedMissions.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { selectedIds = emptyList() },
            title = { Text(stringResource(R.string.map_dialog_title), fontWeight = FontWeight.Bold) },
            text = {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(selectedMissions, key = { it.id }) { mission ->
                        Surface(
                            onClick = {
                                selectedIds = emptyList()
                                currentOnClick(mission.id)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            color = CardGray
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    mission.title,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 14.sp,
                                    modifier = Modifier.weight(1f)
                                )
                                Text("${mission.points}P", color = Orange, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { selectedIds = emptyList() }) {
                    Text(stringResource(R.string.map_dialog_close), color = MainPurple)
                }
            }
        )
    }
}

/** 수행 전/중/완료 상태별로 다른 색 핀을 쓴다. 여러 미션이 묶인 클러스터 핀은 상태와 무관하게 그대로 둔다. */
private fun missionPinDrawableRes(status: String): Int = when (status) {
    "완료" -> R.drawable.ic_map_pin_completed
    "진행중" -> R.drawable.ic_map_pin_in_progress
    else -> R.drawable.ic_map_pin_not_started
}

/**
 * 네이버 지도 `InfoWindow.Adapter` 는 (구글 지도와 달리) 살아있는 View 를 못 붙이고
 * [OverlayImage]（비트맵）만 받는다 — 그래서 View 를 측정·배치한 뒤 캔버스에 직접 그려 비트맵으로 만든다.
 */
private fun renderViewToBitmap(view: View): Bitmap {
    val unspecified = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
    view.measure(unspecified, unspecified)
    view.layout(0, 0, view.measuredWidth, view.measuredHeight)
    val bitmap = Bitmap.createBitmap(
        view.measuredWidth.coerceAtLeast(1),
        view.measuredHeight.coerceAtLeast(1),
        Bitmap.Config.ARGB_8888
    )
    view.draw(Canvas(bitmap))
    return bitmap
}

/**
 * 사진 인증을 완료한 미션의 정보창. 촬영한 인증 사진을 미리보기처럼 위에 보여주고
 * 그 아래에 기존과 같은 제목·포인트·"상세 보기" 문구를 둔다.
 */
private fun buildPhotoInfoWindowView(context: Context, mission: Mission, photoDrawable: Drawable?): View {
    val density = context.resources.displayMetrics.density
    fun dp(value: Int) = (value * density).toInt()
    val imageSize = dp(140)

    return LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setBackgroundColor(android.graphics.Color.WHITE)
        setPadding(dp(10), dp(10), dp(10), dp(10))
        addView(
            ImageView(context).apply {
                layoutParams = LinearLayout.LayoutParams(imageSize, imageSize)
                scaleType = ImageView.ScaleType.CENTER_CROP
                setImageDrawable(photoDrawable)
            }
        )
        addView(
            TextView(context).apply {
                layoutParams = LinearLayout.LayoutParams(imageSize, LinearLayout.LayoutParams.WRAP_CONTENT)
                text = context.getLocalizedString(R.string.map_bubble_single, mission.title, mission.points)
                setTextColor(CAPTION_TEXT_COLOR)
                textSize = 12f
                setPadding(0, dp(6), 0, 0)
            }
        )
    }
}

/** Bridges the Compose destination lifecycle to the native map view. */
private class MissionMapHolder(context: Context, savedState: Bundle?) :
    LifecycleEventObserver, ComponentCallbacks {
    val view = MapView(context)
    var map by mutableStateOf<NaverMap?>(null)
        private set
    var destroyed = false
        private set
    var fittedCoordinates: List<LatLng>? = null
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
