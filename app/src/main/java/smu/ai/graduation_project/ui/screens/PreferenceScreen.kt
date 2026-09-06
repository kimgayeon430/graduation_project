package smu.ai.graduation_project.ui.screens

// 처음 가입한 유저 취향 선택

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.LocationCity
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.firestore
import smu.ai.graduation_project.domain.TravelPreference
import smu.ai.graduation_project.ui.theme.CardGray
import smu.ai.graduation_project.ui.theme.LightPurple
import smu.ai.graduation_project.ui.theme.MainPurple

/**
 * 신규 가입자(및 preferences 가 없는 기존 사용자)의 여행 취향 선택 화면.
 * 최소 1개 선택 시 `users/{uid}.preferences` 배열에 저장하고 [onComplete] 를 호출한다.
 */
@Composable
fun PreferenceScreen(
    onComplete: () -> Unit
) {
    val context = LocalContext.current
    val db = Firebase.firestore
    val uid = Firebase.auth.currentUser?.uid

    val selected = remember { mutableStateListOf<String>() }
    var isSaving by remember { mutableStateOf(false) }

    val options: List<Pair<String, ImageVector>> = listOf(
        TravelPreference.TOUR to Icons.Default.LocationCity,
        TravelPreference.FOOD to Icons.Default.Restaurant,
        TravelPreference.EXPERIENCE to Icons.Default.CameraAlt,
        TravelPreference.SHOPPING to Icons.Default.ShoppingBag
    )

    Scaffold(containerColor = Color.White) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("여행 취향을 알려주세요", fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Text(
                "관심 있는 스타일을 모두 선택하면 홈에서 맞춤 미션을 추천해드려요.",
                color = Color.Gray,
                fontSize = 14.sp
            )

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                options.forEach { (label, icon) ->
                    val isOn = label in selected
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(if (isOn) LightPurple else CardGray)
                            .border(
                                width = if (isOn) 1.5.dp else 1.dp,
                                color = if (isOn) MainPurple else Color(0xFFE0E0E0),
                                shape = RoundedCornerShape(16.dp)
                            )
                            .clickable(enabled = !isSaving) {
                                if (isOn) selected.remove(label) else selected.add(label)
                            }
                            .padding(18.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Icon(icon, contentDescription = null, tint = if (isOn) MainPurple else Color.Gray)
                        Text(
                            label,
                            modifier = Modifier.weight(1f),
                            fontWeight = FontWeight.SemiBold,
                            color = if (isOn) MainPurple else Color(0xFF3A3A3A)
                        )
                        Icon(
                            if (isOn) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                            contentDescription = null,
                            tint = if (isOn) MainPurple else Color.Gray
                        )
                    }
                }
            }

            Text(
                "선택 ${selected.size}개",
                color = if (selected.isEmpty()) Color.Gray else MainPurple,
                fontSize = 13.sp
            )

            Button(
                onClick = {
                    val chosen = TravelPreference.normalize(selected)
                    if (!TravelPreference.canComplete(chosen)) {
                        Toast.makeText(context, "취향을 최소 1개 선택해주세요.", Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    if (uid == null) {
                        onComplete()
                        return@Button
                    }
                    isSaving = true
                    db.collection("users").document(uid)
                        .set(mapOf("preferences" to chosen), SetOptions.merge())
                        .addOnSuccessListener {
                            isSaving = false
                            onComplete()
                        }
                        .addOnFailureListener {
                            isSaving = false
                            Toast.makeText(context, "저장에 실패했어요. 다시 시도해주세요.", Toast.LENGTH_SHORT).show()
                        }
                },
                enabled = selected.isNotEmpty() && !isSaving,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MainPurple)
            ) {
                if (isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text("완료", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
