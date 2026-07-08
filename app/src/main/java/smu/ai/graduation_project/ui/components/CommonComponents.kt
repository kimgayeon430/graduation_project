package smu.ai.graduation_project.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import smu.ai.graduation_project.model.UserRank
import smu.ai.graduation_project.ui.theme.*
import java.util.Locale

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
