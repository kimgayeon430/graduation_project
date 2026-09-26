package smu.ai.graduation_project.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.clickable
import androidx.compose.ui.res.stringResource
import smu.ai.graduation_project.R
import smu.ai.graduation_project.domain.BadgeId
import smu.ai.graduation_project.domain.TravelLevel
import smu.ai.graduation_project.model.UserRank
import smu.ai.graduation_project.ui.theme.*
import java.util.Locale

/**
 * 카테고리 코드(Firestore 값, 항상 한국어: "투어"/"맛집"/"체험"/"쇼핑")를 현재 언어에 맞는
 * 표시용 라벨로 바꾼다. 내부 로직(필터링·매칭)은 코드값을 그대로 쓰고, 화면에 보여줄 때만 이걸 쓴다.
 */
@Composable
fun categoryLabel(category: String): String = when (category) {
    "전체" -> stringResource(R.string.category_all)
    "투어" -> stringResource(R.string.category_tour)
    "맛집" -> stringResource(R.string.category_food)
    "체험" -> stringResource(R.string.category_experience)
    "쇼핑" -> stringResource(R.string.category_shopping)
    "무효" -> stringResource(R.string.category_invalid)
    else -> category
}

/**
 * 미션 진행 상태 코드(내부값, 항상 한국어: "진행중"/"완료"/"미 진행")를 표시용 라벨로 바꾼다.
 * 내부 로직(색상 분기 등)은 코드값을 그대로 쓴다.
 */
@Composable
fun missionStatusLabel(status: String): String = when (status) {
    "진행중" -> stringResource(R.string.status_in_progress)
    "완료" -> stringResource(R.string.status_completed)
    "미 진행" -> stringResource(R.string.status_not_started)
    else -> status
}

private const val CATEGORY_PREFERENCE_SUFFIX = " 취향"

/**
 * [smu.ai.graduation_project.domain.MissionScorer] 가 만드는 추천 근거 코드(항상 한국어)를
 * 표시용 라벨로 바꾼다. 유닛 테스트가 이 코드값을 직접 검사하므로 도메인 쪽은 그대로 두고
 * 화면에 보여줄 때만 이걸 쓴다.
 */
@Composable
fun recommendationReasonLabel(reason: String): String = when {
    reason.endsWith(CATEGORY_PREFERENCE_SUFFIX) ->
        stringResource(R.string.reco_reason_category_pref, categoryLabel(reason.removeSuffix(CATEGORY_PREFERENCE_SUFFIX)))
    reason == "자주 하는 유형" -> stringResource(R.string.reco_reason_frequent)
    reason == "지금 레벨에 적당" -> stringResource(R.string.reco_reason_level_fit)
    reason == "가까운 미션" -> stringResource(R.string.reco_reason_nearby)
    reason == "인기 미션" -> stringResource(R.string.reco_reason_popular)
    reason == "지금 하기 좋은 시간" -> stringResource(R.string.reco_reason_good_time)
    else -> reason
}

/** [TravelLevel] 을 현재 언어의 표시용 이름으로 바꾼다("여행 새싹", "동네 탐험가" 등). */
@Composable
fun travelLevelLabel(level: TravelLevel): String = when (level) {
    TravelLevel.SEEDLING -> stringResource(R.string.level_name_1)
    TravelLevel.NEIGHBORHOOD_EXPLORER -> stringResource(R.string.level_name_2)
    TravelLevel.CITY_TRAVELER -> stringResource(R.string.level_name_3)
    TravelLevel.HIDDEN_GEM_COLLECTOR -> stringResource(R.string.level_name_4)
    TravelLevel.MASTER_TRAVELER -> stringResource(R.string.level_name_5)
}

/** 배지 아이콘. 획득/미획득 모두 같은 아이콘을 쓰고 색만 달리한다(획득=보라, 미획득=회색+자물쇠). */
fun badgeIcon(badgeId: BadgeId): ImageVector = when (badgeId) {
    BadgeId.FIRST_STEP -> Icons.AutoMirrored.Filled.DirectionsWalk
    BadgeId.WEEKLY_EXPLORER -> Icons.Default.Explore
    BadgeId.TASTE_DISCOVERY -> Icons.Default.Favorite
}

@Composable
fun badgeTitle(badgeId: BadgeId): String = when (badgeId) {
    BadgeId.FIRST_STEP -> stringResource(R.string.badge_first_step_title)
    BadgeId.WEEKLY_EXPLORER -> stringResource(R.string.badge_weekly_explorer_title)
    BadgeId.TASTE_DISCOVERY -> stringResource(R.string.badge_taste_discovery_title)
}

/** 배지를 이미 획득했을 때 보여줄 축하 문구. */
@Composable
fun badgeUnlockedMessage(badgeId: BadgeId): String = when (badgeId) {
    BadgeId.FIRST_STEP -> stringResource(R.string.badge_first_step_unlocked)
    BadgeId.WEEKLY_EXPLORER -> stringResource(R.string.badge_weekly_explorer_unlocked)
    BadgeId.TASTE_DISCOVERY -> stringResource(R.string.badge_taste_discovery_unlocked)
}

/** 아직 못 얻었을 때 보여줄 획득 조건 안내. */
@Composable
fun badgeLockedHint(badgeId: BadgeId): String = when (badgeId) {
    BadgeId.FIRST_STEP -> stringResource(R.string.badge_first_step_hint)
    BadgeId.WEEKLY_EXPLORER -> stringResource(R.string.badge_weekly_explorer_hint)
    BadgeId.TASTE_DISCOVERY -> stringResource(R.string.badge_taste_discovery_hint)
}

@Composable
fun InfoCardSmall(title: String, icon: ImageVector, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.height(80.dp),
        colors = CardDefaults.cardColors(containerColor = CardGray),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(icon, null, modifier = Modifier.size(20.dp), tint = MainPurple)
            Spacer(modifier = Modifier.height(4.dp))
            Text(title, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun StatCard(
    title: String,
    value: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    Card(
        modifier = modifier.then(
            if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
        ),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(0.5.dp, Color.LightGray),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, null, tint = MainPurple, modifier = Modifier.size(24.dp))
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(title, fontSize = 11.sp, color = Color.Gray)
                Text(value, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun MissionStatusItem(title: String, value: String, icon: ImageVector, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, null, tint = color, modifier = Modifier.size(24.dp))
        Spacer(modifier = Modifier.height(8.dp))
        Text(title, fontSize = 10.sp, color = Color.Gray)
        Text(value, fontSize = 14.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun ProfileMenuItem(title: String, icon: ImageVector, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        color = Color.Transparent
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 32.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, null, modifier = Modifier.size(24.dp), tint = Color.Gray)
            Spacer(modifier = Modifier.width(16.dp))
            Text(title, fontSize = 16.sp, modifier = Modifier.weight(1f))
            Icon(Icons.Default.ChevronRight, null, tint = Color.LightGray)
        }
    }
}

@Composable
fun MissionCard(
    title: String,
    desc: String,
    progress: Float,
    progressText: String,
    points: Int,
    status: String,
    statusColor: Color,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(100.dp)
                    .background(CardGray, RoundedCornerShape(12.dp))
            ) {
                Surface(
                    color = statusColor,
                    shape = RoundedCornerShape(bottomEnd = 8.dp, topStart = 12.dp),
                    modifier = Modifier.align(Alignment.TopStart)
                ) {
                    Text(
                        text = status,
                        color = Color.White,
                        fontSize = 10.sp,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
                Icon(
                    Icons.Default.Landscape,
                    null,
                    tint = Color.LightGray,
                    modifier = Modifier.align(Alignment.Center).size(40.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text(text = desc, fontSize = 12.sp, color = Color.Gray, maxLines = 1)
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(text = progressText, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(CircleShape),
                    color = if (progress > 0) Color(0xFF4CAF50) else Color(0xFFE0E0E0),
                    trackColor = Color(0xFFF5F5F5),
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Stars, null, tint = Orange, modifier = Modifier.size(16.dp))
                    Text(text = " ${points}P", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }

            Icon(
                Icons.Default.ChevronRight,
                null,
                tint = Color.LightGray,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

@Composable
fun TopThreeSection(top3: List<UserRank>) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 32.dp, horizontal = 16.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.Bottom
    ) {
        if (top3.size >= 2) {
            TopRankItem(rank = 2, name = top3[1].name, points = top3[1].points, color = Color(0xFFC0C0C0))
        }
        if (top3.isNotEmpty()) {
            TopRankItem(rank = 1, name = top3[0].name, points = top3[0].points, color = Color(0xFFFFD700), isFirst = true)
        }
        if (top3.size >= 3) {
            TopRankItem(rank = 3, name = top3[2].name, points = top3[2].points, color = Color(0xFFCD7F32))
        }
    }
}

@Composable
fun TopRankItem(rank: Int, name: String, points: Int, color: Color, isFirst: Boolean = false) {
    val size = if (isFirst) 100.dp else 80.dp
    val avatarSize = if (isFirst) 80.dp else 65.dp
    
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                Icons.Default.KeyboardArrowUp,
                contentDescription = null,
                tint = color,
                modifier = Modifier
                    .size(24.dp)
                    .offset(y = if (isFirst) (-55).dp else (-45).dp)
            )
            Box(
                modifier = Modifier
                    .size(size)
                    .background(color.copy(alpha = 0.2f), CircleShape)
                    .border(2.dp, color, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(avatarSize)
                        .background(Color.LightGray, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Person, null, modifier = Modifier.size(size / 2), tint = Color.White)
                }
                Surface(
                    color = color,
                    shape = CircleShape,
                    modifier = Modifier
                        .size(24.dp)
                        .align(Alignment.BottomCenter)
                        .offset(y = 12.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(rank.toString(), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(20.dp))
        Text(name, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        Text("${String.format("%,d", points)}P", color = MainPurple, fontWeight = FontWeight.Bold, fontSize = 14.sp)
    }
}

@Composable
fun RankingListItem(rank: Int, name: String, points: Int, isMe: Boolean) {
    Surface(
        color = if (isMe) LightPurple.copy(alpha = 0.5f) else Color.Transparent,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                rank.toString(),
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                modifier = Modifier.width(32.dp),
                textAlign = TextAlign.Center
            )
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(Color.LightGray, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Person, null, modifier = Modifier.size(24.dp), tint = Color.White)
            }
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                name,
                fontWeight = if (isMe) FontWeight.Bold else FontWeight.Normal,
                fontSize = 14.sp,
                modifier = Modifier.weight(1f),
                color = if (isMe) MainPurple else Color.Black
            )
            Text(
                "${String.format("%,d", points)}P",
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = if (isMe) MainPurple else Color(0xFF5E43FF)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Icon(Icons.Default.ChevronRight, null, tint = Color.LightGray, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
fun GenericScreen(name: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text = name)
    }
}
