package smu.ai.graduation_project.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Landscape
import androidx.compose.material.icons.filled.Stars
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import smu.ai.graduation_project.R
import smu.ai.graduation_project.ui.theme.CardGray
import smu.ai.graduation_project.ui.theme.LightPurple
import smu.ai.graduation_project.ui.theme.MainPurple
import smu.ai.graduation_project.ui.theme.Orange

/** 미션 성공 화면 하단 "다음 추천 미션" 카드에 필요한 값. [smu.ai.graduation_project.domain.MissionRecommender] 를 그대로 재사용해 만든다. */
data class RecommendedMissionUi(
    val missionId: String,
    val title: String,
    val imageUrl: String,
    val category: String,
    val points: Int,
    /** 관리자가 아직 입력하지 않았으면 null — 그 경우 이 행은 표시하지 않는다. */
    val estimatedMinutes: Int?,
    /** [smu.ai.graduation_project.domain.MissionScorer] 가 만든 추천 근거 코드. [recommendationReasonLabel] 로 표시한다. */
    val reasons: List<String>
)

/** 미션 성공 화면 하단의 "다음에는 이런 미션 어때요?" 카드. */
@Composable
fun NextMissionRecommendationCard(
    recommendation: RecommendedMissionUi,
    onStart: () -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(
            stringResource(R.string.perform_next_mission_title),
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp,
            color = Color(0xFF2C2C2C)
        )
        Spacer(modifier = Modifier.height(10.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    RecommendationThumbnail(recommendation.imageUrl, recommendation.title)
                    Column(modifier = Modifier.weight(1f)) {
                        Surface(color = MainPurple, shape = RoundedCornerShape(4.dp)) {
                            Text(
                                categoryLabel(recommendation.category),
                                color = Color.White,
                                fontSize = 10.sp,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                            )
                        }
                        Text(recommendation.title, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 2)
                        if (recommendation.reasons.isNotEmpty()) {
                            Row(
                                modifier = Modifier.padding(top = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                recommendation.reasons.take(2).forEach { reason ->
                                    Surface(color = LightPurple, shape = RoundedCornerShape(4.dp)) {
                                        Text(
                                            recommendationReasonLabel(reason),
                                            color = MainPurple,
                                            fontSize = 10.sp,
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Stars, null, tint = Orange, modifier = Modifier.size(14.dp))
                            Text(" ${recommendation.points}P", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            recommendation.estimatedMinutes?.let { minutes ->
                                Spacer(modifier = Modifier.width(10.dp))
                                Icon(Icons.Default.AccessTime, null, tint = Color.Gray, modifier = Modifier.size(14.dp))
                                Text(
                                    " " + stringResource(R.string.perform_next_mission_duration_format, minutes),
                                    fontSize = 12.sp,
                                    color = Color.Gray
                                )
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = onSkip, modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.perform_next_mission_btn_skip), color = Color.Gray, fontSize = 13.sp)
                    }
                    Button(
                        onClick = onStart,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = MainPurple),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(stringResource(R.string.perform_next_mission_btn_start), fontSize = 13.sp)
                    }
                }
            }
        }
    }
}

/** 추천할 다음 미션이 없을 때(오늘 것 다 봤음) 보여줄 안내 카드. 빈 카드나 에러 대신 이걸 보여준다. */
@Composable
fun NextMissionEmptyCard(modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = CardGray),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Text(stringResource(R.string.perform_next_mission_empty_title), fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Text(
                stringResource(R.string.perform_next_mission_empty_desc),
                fontSize = 12.sp,
                color = Color.Gray
            )
        }
    }
}

@Composable
private fun RecommendationThumbnail(imageUrl: String, title: String) {
    Box(
        modifier = Modifier
            .size(84.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(LightPurple),
        contentAlignment = Alignment.Center
    ) {
        if (imageUrl.isBlank()) {
            Icon(Icons.Default.Landscape, contentDescription = null, tint = MainPurple, modifier = Modifier.size(32.dp))
        } else {
            val fallback = rememberVectorPainter(Icons.Default.Landscape)
            AsyncImage(
                model = imageUrl,
                contentDescription = title,
                modifier = Modifier.fillMaxWidth().height(84.dp),
                contentScale = ContentScale.Crop,
                placeholder = fallback,
                error = fallback
            )
        }
    }
}
