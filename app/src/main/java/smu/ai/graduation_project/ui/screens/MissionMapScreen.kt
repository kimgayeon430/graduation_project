package smu.ai.graduation_project.ui.screens

import android.content.ComponentCallbacks
import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
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
import smu.ai.graduation_project.model.Mission

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
        }.groupBy { LatLng(requireNotNull(it.latitude), requireNotNull(it.longitude)) }
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
        val windows = mutableListOf<InfoWindow>()
        if (map != null && !holder.destroyed) {
            groups.forEach { (coordinate, group) ->
                val bubble = InfoWindow().apply {
                    position = coordinate
                    adapter = object : InfoWindow.DefaultTextAdapter(context) {
                        override fun getText(infoWindow: InfoWindow): CharSequence =
                            if (group.size == 1) {
                                "${group.first().title}\n${group.first().points}P · 상세 보기"
                            } else {
                                "미션 ${group.size}개 · 눌러서 선택"
                            }
                    }
                    setOnClickListener {
                        if (group.size == 1) currentOnClick(group.first().id)
                        else selectedIds = group.map { it.id }
                        true
                    }
                    open(map)
                }
                windows.add(bubble)
            }
            // Fit after layout so every mission is initially within the viewport.
            val coordinates = groups.keys.toList()
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
            windows.forEach { it.close() }
        }
    }

    Box(modifier) {
        AndroidView(factory = { holder.view }, modifier = Modifier.fillMaxSize())
        Surface(modifier = Modifier.align(Alignment.TopCenter).padding(12.dp),
            shadowElevation = 3.dp) {
            val count = groups.values.sumOf { it.size }
            Text(
                text = if (count == 0) "위치가 등록된 미션이 없습니다. 목록에서 확인해 주세요."
                else "미션 ${count}개 · 말풍선을 눌러 상세 보기",
                modifier = Modifier.padding(12.dp)
            )
        }
    }

    if (selectedMissions.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { selectedIds = emptyList() },
            title = { Text("이 장소의 미션") },
            text = {
                LazyColumn {
                    items(selectedMissions, key = { it.id }) { mission ->
                        TextButton(onClick = {
                            selectedIds = emptyList()
                            currentOnClick(mission.id)
                        }) { Text("${mission.title} · ${mission.points}P") }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { selectedIds = emptyList() }) { Text("닫기") }
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
