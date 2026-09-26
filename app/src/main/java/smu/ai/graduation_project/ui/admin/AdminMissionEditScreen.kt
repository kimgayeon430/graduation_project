package smu.ai.graduation_project.ui.admin

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.google.firebase.Firebase
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.GeoPoint
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.firestore
import smu.ai.graduation_project.R
import smu.ai.graduation_project.ui.theme.MainPurple

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminMissionEditScreen(
    missionId: String?,
    onNavigateBack: () -> Unit,
    onSaveSuccess: () -> Unit
) {
    val db = Firebase.firestore
    val context = LocalContext.current
    val isEdit = !missionId.isNullOrBlank()

    var title by remember { mutableStateOf("") }
    var desc by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("투어") }
    var points by remember { mutableIntStateOf(100) }
    var imageUrl by remember { mutableStateOf("") }
    var estimatedMinutes by remember { mutableStateOf("") }
    var latitude by remember { mutableStateOf("") }
    var longitude by remember { mutableStateOf("") }
    var isSaving by remember { mutableStateOf(false) }

    LaunchedEffect(missionId) {
        if (isEdit) {
            db.collection("missions").document(missionId!!).get().addOnSuccessListener { doc ->
                title = doc.getString("title") ?: ""
                desc = doc.getString("desc") ?: ""
                category = doc.getString("category") ?: "투어"
                points = doc.getLong("points")?.toInt() ?: 100
                imageUrl = doc.getString("imageUrl").orEmpty()
                estimatedMinutes = doc.getLong("estimatedMinutes")?.toString().orEmpty()
                doc.getGeoPoint("location")?.let {
                    latitude = it.latitude.toString()
                    longitude = it.longitude.toString()
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(if (isEdit) R.string.admin_mission_edit_title else R.string.admin_mission_add_title), fontWeight = FontWeight.Bold) },
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
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            OutlinedTextField(title, { title = it }, modifier = Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.admin_mission_field_title)) })
            OutlinedTextField(desc, { desc = it }, modifier = Modifier.fillMaxWidth(), minLines = 4, label = { Text(stringResource(R.string.admin_mission_field_desc)) })
            OutlinedTextField(category, { category = it }, modifier = Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.admin_mission_field_category)) })
            OutlinedTextField(points.toString(), { points = it.toIntOrNull() ?: 0 }, modifier = Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.admin_mission_field_points)) })
            OutlinedTextField(imageUrl, { imageUrl = it }, modifier = Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.admin_mission_field_image_url)) })
            OutlinedTextField(
                estimatedMinutes,
                { estimatedMinutes = it.filter { ch -> ch.isDigit() } },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.admin_mission_field_duration_minutes)) }
            )

            Text(stringResource(R.string.admin_mission_field_location_section), fontWeight = FontWeight.Bold)
            OutlinedTextField(latitude, { latitude = it }, modifier = Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.admin_mission_field_latitude)) })
            OutlinedTextField(longitude, { longitude = it }, modifier = Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.admin_mission_field_longitude)) })

            val requiredFieldsMessage = stringResource(R.string.admin_toast_required_fields)
            val invalidLatLngMessage = stringResource(R.string.admin_toast_invalid_latlng)
            val updatedMessage = stringResource(R.string.admin_toast_mission_updated)
            val addedMessage = stringResource(R.string.admin_toast_mission_added)
            val saveFailedMessage = stringResource(R.string.admin_toast_save_failed)
            Button(
                onClick = {
                    if (title.isBlank() || desc.isBlank() || category.isBlank()) {
                        Toast.makeText(context, requiredFieldsMessage, Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    val latText = latitude.trim()
                    val lngText = longitude.trim()
                    val lat = latText.toDoubleOrNull()
                    val lng = lngText.toDoubleOrNull()
                    val hasLocationInput = latText.isNotEmpty() || lngText.isNotEmpty()
                    if (hasLocationInput &&
                        (lat == null || lng == null || lat !in -90.0..90.0 || lng !in -180.0..180.0)
                    ) {
                        Toast.makeText(context, invalidLatLngMessage, Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    isSaving = true
                    val payload = buildMap<String, Any> {
                        put("title", title.trim())
                        put("desc", desc.trim())
                        put("category", category.trim())
                        put("points", points)
                        put("imageUrl", imageUrl.trim())
                        when (val minutes = estimatedMinutes.toIntOrNull()) {
                            null -> if (isEdit) put("estimatedMinutes", FieldValue.delete())
                            else -> put("estimatedMinutes", minutes)
                        }
                        when {
                            lat != null && lng != null -> put("location", GeoPoint(lat, lng))
                            isEdit -> put("location", FieldValue.delete())
                        }
                    }
                    val task = if (isEdit) {
                        // completionCount 등 다른 필드를 보존하도록 merge
                        db.collection("missions").document(missionId!!).set(payload, SetOptions.merge())
                    } else {
                        db.collection("missions").add(payload)
                    }
                    task.addOnSuccessListener {
                        isSaving = false
                        Toast.makeText(context, if (isEdit) updatedMessage else addedMessage, Toast.LENGTH_SHORT).show()
                        onSaveSuccess()
                    }.addOnFailureListener {
                        isSaving = false
                        Toast.makeText(context, saveFailedMessage, Toast.LENGTH_SHORT).show()
                    }
                },
                enabled = !isSaving,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = MainPurple)
            ) {
                Text(
                    stringResource(
                        if (isSaving) R.string.admin_mission_saving
                        else if (isEdit) R.string.admin_mission_save_edit
                        else R.string.admin_mission_save_add
                    )
                )
            }
        }
    }
}
