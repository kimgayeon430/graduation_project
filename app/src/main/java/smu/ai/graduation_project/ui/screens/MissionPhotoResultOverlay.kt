package smu.ai.graduation_project.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random
import smu.ai.graduation_project.R
import smu.ai.graduation_project.domain.PhotoVerification
import smu.ai.graduation_project.ui.components.categoryLabel
import smu.ai.graduation_project.ui.theme.CardGray
import smu.ai.graduation_project.ui.theme.LightPurple
import smu.ai.graduation_project.ui.theme.MainPurple
import smu.ai.graduation_project.ui.theme.Orange

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
 * PASS 판정 직후 약 2.2초 보여주는 성취 연출. [PhotoResultUi] 화면은 이미 준비돼 있고,
 * 애니메이션이 끝나면 화면(타이머)이 [MissionPerformViewModel.onCelebrationFinished] 를 불러
 * 이 연출을 닫으면 그 결과 화면이 자연스럽게 이어서 보인다.
 */
@Composable
fun MissionSuccessCelebrationOverlay(celebration: CelebrationUi) {
    val haptic = LocalHapticFeedback.current
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        progress.animateTo(1f, animationSpec = tween(durationMillis = 2200, easing = LinearEasing))
    }

    val badgeScale by animateFloatAsState(
        targetValue = if (progress.value > 0.03f) 1f else 0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "celebrationBadgeScale"
    )
    val pointsVisible = progress.value > 0.15f
    val pointsOffsetY by animateFloatAsState(
        targetValue = if (pointsVisible) -18f else 8f,
        animationSpec = tween(durationMillis = 450),
        label = "celebrationPointsOffset"
    )
    val pointsAlpha by animateFloatAsState(
        targetValue = if (pointsVisible) 1f else 0f,
        animationSpec = tween(durationMillis = 400),
        label = "celebrationPointsAlpha"
    )
    val particles = remember { List(18) { ConfettiParticle.random() } }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(LightPurple)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val burstOrigin = Offset(size.width / 2f, size.height * 0.38f)
            particles.forEach { particle -> drawConfettiParticle(particle, progress.value, burstOrigin) }
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                Icons.Default.CheckCircle,
                null,
                tint = MainPurple,
                modifier = Modifier
                    .size(88.dp)
                    .scale(badgeScale)
            )
            Spacer(modifier = Modifier.height(20.dp))
            Text(
                stringResource(R.string.perform_celebration_title),
                fontWeight = FontWeight.ExtraBold,
                fontSize = 26.sp,
                color = Color(0xFF2C2C2C)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                stringResource(R.string.perform_celebration_subtitle, celebration.missionTitle),
                color = Color.Gray,
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                lineHeight = 20.sp
            )
            if (celebration.pointsGranted > 0) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    stringResource(R.string.perform_celebration_points_format, celebration.pointsGranted),
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 22.sp,
                    color = MainPurple,
                    modifier = Modifier
                        .offset(y = pointsOffsetY.dp)
                        .alpha(pointsAlpha)
                )
            }
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                stringResource(
                    R.string.perform_celebration_weekly_progress,
                    celebration.weeklyCompleted,
                    celebration.weeklyGoal
                ),
                color = Color.Gray,
                fontSize = 13.sp
            )
        }
    }
}

/** 작은 색색의 파티클 하나의 궤적. 중심에서 바깥으로 터지며 서서히 떨어지고 옅어진다. */
private data class ConfettiParticle(
    val angleRad: Float,
    val distancePx: Float,
    val radiusPx: Float,
    val color: Color,
    val delay: Float
) {
    companion object {
        private val palette = listOf(MainPurple, Color(0xFFB8A9FF), Color(0xFF8F7BFF), Orange)
        fun random() = ConfettiParticle(
            angleRad = Random.nextFloat() * (2f * PI.toFloat()),
            distancePx = Random.nextFloat() * 260f + 120f,
            radiusPx = Random.nextFloat() * 5f + 3f,
            color = palette.random(),
            delay = Random.nextFloat() * 0.15f
        )
    }
}

private fun DrawScope.drawConfettiParticle(particle: ConfettiParticle, progress: Float, origin: Offset) {
    val local = ((progress - particle.delay) / (1f - particle.delay)).coerceIn(0f, 1f)
    if (local <= 0f) return
    val distance = particle.distancePx * local
    val gravity = local * local * 140f
    val x = origin.x + cos(particle.angleRad) * distance
    val y = origin.y + sin(particle.angleRad) * distance + gravity
    val alpha = if (local < 0.6f) 1f else (1f - (local - 0.6f) / 0.4f).coerceIn(0f, 1f)
    drawCircle(color = particle.color.copy(alpha = alpha), radius = particle.radiusPx, center = Offset(x, y))
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
            PhotoVerdictUi.PASS -> Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = onPrimary,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = MainPurple),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text(stringResource(R.string.perform_result_btn_next_mission), fontWeight = FontWeight.Bold)
                }
                OutlinedButton(
                    onClick = onSecondary,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text(stringResource(R.string.perform_result_btn_go_home))
                }
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
