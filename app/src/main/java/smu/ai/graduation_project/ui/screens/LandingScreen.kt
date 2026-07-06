package smu.ai.graduation_project.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Map
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import smu.ai.graduation_project.ui.theme.CardGray
import smu.ai.graduation_project.ui.theme.GradientEnd
import smu.ai.graduation_project.ui.theme.GradientStart
import smu.ai.graduation_project.ui.theme.LightPurple
import smu.ai.graduation_project.ui.theme.MainPurple

@Composable
fun LandingScreen(
    onSignUp: () -> Unit,
    onLogin: () -> Unit,
    onGuest: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF7F7FB))
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(320.dp)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(GradientStart, GradientEnd, Color(0xFF7F6BFF))
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Spacer(Modifier.height(28.dp))
                Surface(
                    color = Color.White.copy(alpha = 0.18f),
                    shape = RoundedCornerShape(50)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.AutoAwesome, null, tint = Color.White, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Seoul mission guide", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    }
                }
                Spacer(Modifier.height(24.dp))
                Text("TripQuest", color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.ExtraBold)
                Spacer(Modifier.height(10.dp))
                Text(
                    "여행 동선을 따라 미션을 수행하고\n포인트와 기록을 함께 쌓아보세요.",
                    color = Color.White.copy(alpha = 0.92f),
                    lineHeight = 24.sp,
                    fontSize = 16.sp
                )
                Spacer(Modifier.height(26.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    LandingInfoPill(Icons.Default.Map, "동선 기반")
                    LandingInfoPill(Icons.Default.Explore, "현장 미션")
                    LandingInfoPill(Icons.Default.EmojiEvents, "포인트 보상")
                }
            }

            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = Color.White,
                shape = RoundedCornerShape(28.dp),
                shadowElevation = 8.dp
            ) {
                Column(
                    modifier = Modifier.padding(22.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text("시작하기", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color(0xFF232323))
                    Text(
                        "회원가입 후 미션 진행 상황과 포인트를 저장할 수 있어요.",
                        color = Color.Gray,
                        fontSize = 14.sp,
                        lineHeight = 20.sp
                    )

                    Button(
                        onClick = onSignUp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MainPurple)
                    ) {
                        Text("회원가입", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }

                    OutlinedButton(
                        onClick = onLogin,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp),
                        shape = RoundedCornerShape(16.dp),
                        border = androidx.compose.foundation.BorderStroke(1.5.dp, LightPurple)
                    ) {
                        Text("로그인", color = Color(0xFF2B2B2B), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }

                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = CardGray,
                        shape = RoundedCornerShape(18.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .background(LightPurple, CircleShape)
                                    .border(1.dp, Color.White, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Explore, null, tint = MainPurple, modifier = Modifier.size(18.dp))
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("둘러보기", fontWeight = FontWeight.Bold, color = Color(0xFF2F2F2F))
                                Text("계정 없이 먼저 화면을 확인할 수 있어요.", color = Color.Gray, fontSize = 12.sp)
                            }
                            Text(
                                text = "입장",
                                color = MainPurple,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.clickable(onClick = onGuest)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LandingInfoPill(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String
) {
    Surface(
        color = Color.White.copy(alpha = 0.16f),
        shape = RoundedCornerShape(50)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, null, tint = Color.White, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(6.dp))
            Text(text, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium)
        }
    }
}
