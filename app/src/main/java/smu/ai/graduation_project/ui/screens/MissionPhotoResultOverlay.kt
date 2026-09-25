package smu.ai.graduation_project.ui.screens

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.Stars
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlin.math.roundToInt
import smu.ai.graduation_project.R
import smu.ai.graduation_project.domain.PhotoVerification
import smu.ai.graduation_project.ui.components.categoryLabel
import smu.ai.graduation_project.ui.theme.CardGray
import smu.ai.graduation_project.ui.theme.LightPurple
import smu.ai.graduation_project.ui.theme.MainPurple

private val ReviewAmber = Color(0xFFE38B2C)
private val ReviewAmberBg = Color(0xFFFFF7E8)
private val RejectRed = Color(0xFFB85C5C)
private val RejectRedBg = Color(0xFFFBF0EF)

/** 2단계 사진 인증 · AI 분석 중. [MissionPerformUiState.isUploading] 일 때 전체 화면으로 띄운다. */
@Composable
fun MissionPhotoAnalyzingOverlay() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator(color = MainPurple, strokeWidth = 3.dp, modifier = Modifier.size(48.dp))
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            stringResource(R.string.perform_result_analyzing_title),
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp,
            color = Color(0xFF2C2C2C)
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            stringResource(R.string.perform_result_analyzing_desc),
            color = Color.Gray,
            fontSize = 14.sp
        )
    }
}

/**
 * 2단계 사진 인증 · AI 분석 결과. [PhotoResultUi.verdict] 에 따라 PASS/REVIEW/REJECT 세 갈래를
 * 하나의 화면 구조(사진 → 상태 배지·제목·설명 → 판정 정보 → 버튼)로 보여준다.
 */
@Composable
fun MissionPhotoResultOverlay(
    result: PhotoResultUi,
    onPrimary: () -> Unit,
    onSecondary: () -> Unit
) {
    val accentColor = when (result.verdict) {
        PhotoVerdictUi.PASS -> MainPurple
        PhotoVerdictUi.REVIEW -> ReviewAmber
        PhotoVerdictUi.REJECT -> RejectRed
    }
    val badgeBackground = when (result.verdict) {
        PhotoVerdictUi.PASS -> LightPurple
        PhotoVerdictUi.REVIEW -> ReviewAmberBg
        PhotoVerdictUi.REJECT -> RejectRedBg
    }
    val icon = when (result.verdict) {
        PhotoVerdictUi.PASS -> Icons.Default.CheckCircle
        PhotoVerdictUi.REVIEW -> Icons.Default.HourglassEmpty
        PhotoVerdictUi.REJECT -> Icons.Default.ErrorOutline
    }
    val titleRes = when (result.verdict) {
        PhotoVerdictUi.PASS -> R.string.perform_result_pass_title
        PhotoVerdictUi.REVIEW -> R.string.perform_result_review_title
        PhotoVerdictUi.REJECT -> R.string.perform_result_reject_title
    }
    val descRes = when (result.verdict) {
        PhotoVerdictUi.PASS -> R.string.perform_result_pass_desc
        PhotoVerdictUi.REVIEW -> R.string.perform_result_review_desc
        PhotoVerdictUi.REJECT -> when (result.rejectReasonCode) {
            PhotoVerification.RejectReasonCode.INVALID_SUBJECT -> R.string.perform_result_reject_reason_invalid_subject
            PhotoVerification.RejectReasonCode.CATEGORY_MISMATCH -> R.string.perform_result_reject_reason_category_mismatch
            else -> R.string.perform_result_reject_reason_low_confidence
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
    ) {
        AsyncImage(
            model = result.displayPhoto,
            contentDescription = stringResource(R.string.perform_photo_preview_desc),
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth()
                .height(260.dp)
                .background(CardGray, RoundedCornerShape(24.dp))
        )

        Spacer(modifier = Modifier.height(20.dp))

        Surface(color = badgeBackground, shape = RoundedCornerShape(100.dp)) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(icon, null, tint = accentColor, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(stringResource(titleRes), color = accentColor, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            stringResource(titleRes),
            fontWeight = FontWeight.ExtraBold,
            fontSize = 24.sp,
            color = Color(0xFF2C2C2C)
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(stringResource(descRes), color = Color.Gray, fontSize = 14.sp, lineHeight = 20.sp)
        if (result.verdict == PhotoVerdictUi.REVIEW) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                stringResource(R.string.perform_result_review_sub),
                color = Color.Gray,
                fontSize = 13.sp,
                lineHeight = 18.sp
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = CardGray,
            shape = RoundedCornerShape(18.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                ResultInfoRow(
                    stringResource(R.string.perform_result_expected_category_label),
                    categoryLabel(result.missionCategory)
                )
                ResultInfoRow(
                    stringResource(R.string.perform_result_predicted_category_label),
                    if (result.predictedLabel.isEmpty()) {
                        stringResource(R.string.perform_result_predicted_unknown)
                    } else {
                        categoryLabel(result.predictedLabel)
                    }
                )
                result.confidence?.let { confidence ->
                    ResultInfoRow(
                        stringResource(R.string.perform_result_confidence_label),
                        stringResource(R.string.perform_result_confidence_value, (confidence * 100).roundToInt())
                    )
                }
            }
        }

        if (result.verdict == PhotoVerdictUi.PASS && result.pointsGranted > 0) {
            Spacer(modifier = Modifier.height(12.dp))
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = LightPurple,
                shape = RoundedCornerShape(14.dp)
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Stars, null, tint = MainPurple, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        stringResource(R.string.perform_result_points_earned, result.pointsGranted),
                        color = MainPurple,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(28.dp))

        when (result.verdict) {
            PhotoVerdictUi.PASS -> Button(
                onClick = onPrimary,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = MainPurple),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text(stringResource(R.string.perform_result_btn_done), fontWeight = FontWeight.Bold)
            }
            PhotoVerdictUi.REVIEW -> Button(
                onClick = onPrimary,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = MainPurple),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text(stringResource(R.string.perform_result_btn_confirm), fontWeight = FontWeight.Bold)
            }
            PhotoVerdictUi.REJECT -> Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = onPrimary,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = ReviewAmber),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(Icons.Default.CameraAlt, null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.perform_result_btn_retake), fontWeight = FontWeight.Bold)
                }
                OutlinedButton(
                    onClick = onSecondary,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text(stringResource(R.string.perform_result_btn_view_mission))
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            stringResource(R.string.perform_result_ondevice_notice),
            color = Color.LightGray,
            fontSize = 11.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )

        if (result.modelVersion.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            var expanded by remember { mutableStateOf(false) }
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                TextButton(onClick = { expanded = !expanded }) {
                    Text(stringResource(R.string.perform_result_debug_toggle), color = Color.LightGray, fontSize = 11.sp)
                    Icon(
                        if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        null,
                        tint = Color.LightGray,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
            if (expanded) {
                Text(
                    stringResource(R.string.perform_result_debug_model_version, result.modelVersion),
                    color = Color.LightGray,
                    fontSize = 11.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun ResultInfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = Color.Gray, fontSize = 13.sp)
        Text(value, color = Color(0xFF2C2C2C), fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
    }
}
