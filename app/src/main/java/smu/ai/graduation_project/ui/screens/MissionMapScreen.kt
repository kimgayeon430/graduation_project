package smu.ai.graduation_project.ui.screens

import android.content.ComponentCallbacks
import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
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
                        if (group.size == 1) R.drawable.ic_map_pin_single else R.drawable.ic_map_pin_cluster
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
