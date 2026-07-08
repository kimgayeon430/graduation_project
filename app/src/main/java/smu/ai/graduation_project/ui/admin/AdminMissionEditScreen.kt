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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.google.firebase.Firebase
import com.google.firebase.firestore.firestore
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
    var isSaving by remember { mutableStateOf(false) }

    LaunchedEffect(missionId) {
        if (isEdit) {
            db.collection("missions").document(missionId!!).get().addOnSuccessListener { doc ->
                title = doc.getString("title") ?: ""
                desc = doc.getString("desc") ?: ""
                category = doc.getString("category") ?: "투어"
                points = doc.getLong("points")?.toInt() ?: 100
                imageUrl = doc.getString("imageUrl").orEmpty()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isEdit) "미션 수정" else "미션 추가", fontWeight = FontWeight.Bold) },
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
            OutlinedTextField(title, { title = it }, modifier = Modifier.fillMaxWidth(), label = { Text("미션 제목") })
            OutlinedTextField(desc, { desc = it }, modifier = Modifier.fillMaxWidth(), minLines = 4, label = { Text("미션 설명") })
            OutlinedTextField(category, { category = it }, modifier = Modifier.fillMaxWidth(), label = { Text("카테고리") })
            OutlinedTextField(points.toString(), { points = it.toIntOrNull() ?: 0 }, modifier = Modifier.fillMaxWidth(), label = { Text("포인트") })
            OutlinedTextField(imageUrl, { imageUrl = it }, modifier = Modifier.fillMaxWidth(), label = { Text("이미지 URL") })

            Button(
                onClick = {
                    if (title.isBlank() || desc.isBlank() || category.isBlank()) {
                        Toast.makeText(context, "필수 항목을 입력하세요.", Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    isSaving = true
                    val payload = mapOf(
                        "title" to title.trim(),
                        "desc" to desc.trim(),
                        "category" to category.trim(),
                        "points" to points,
                        "imageUrl" to imageUrl.trim()
                    )
                    val task = if (isEdit) {
                        db.collection("missions").document(missionId!!).set(payload)
                    } else {
                        db.collection("missions").add(payload)
                    }
                    task.addOnSuccessListener {
                        isSaving = false
                        Toast.makeText(context, if (isEdit) "미션이 수정되었습니다." else "미션이 추가되었습니다.", Toast.LENGTH_SHORT).show()
                        onSaveSuccess()
                    }.addOnFailureListener {
                        isSaving = false
                        Toast.makeText(context, "저장에 실패했습니다.", Toast.LENGTH_SHORT).show()
                    }
                },
                enabled = !isSaving,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = MainPurple)
            ) {
                Text(if (isSaving) "저장 중..." else if (isEdit) "수정 완료" else "미션 추가")
            }
        }
    }
}
