package smu.ai.graduation_project.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpCenter
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.firestore
import smu.ai.graduation_project.model.UserRank
import smu.ai.graduation_project.ui.components.RankingListItem
import smu.ai.graduation_project.ui.components.TopThreeSection
import smu.ai.graduation_project.ui.theme.MainPurple

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RankingScreen() {
    val db = Firebase.firestore
    val currentUser = Firebase.auth.currentUser
    var selectedTab by remember { mutableIntStateOf(0) }
    var rankingList by remember { mutableStateOf<List<UserRank>>(emptyList()) }
    val tabs = listOf("전체 랭킹", "친구 랭킹")

    LaunchedEffect(Unit) {
        db.collection("users")
            .orderBy("points", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, _ ->
                if (snapshot != null) {
                    rankingList = snapshot.documents.mapIndexed { index, doc ->
                        UserRank(
                            rank = index + 1,
                            name = doc.getString("nickname") ?: "Traveler",
                            points = doc.getLong("points")?.toInt() ?: 0,
                            uid = doc.id
                        )
                    }
                }
            }
    }

    val visibleRanking = if (selectedTab == 0) {
        rankingList
    } else {
        val current = rankingList.firstOrNull { it.uid == currentUser?.uid }
        val topFriends = rankingList.take(5)
        listOfNotNull(current).plus(topFriends)
            .distinctBy { it.uid }
            .sortedBy { it.rank }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
    ) {
        CenterAlignedTopAppBar(
            title = { Text("랭킹", fontWeight = FontWeight.Bold, fontSize = 20.sp) },
            actions = {
                IconButton(onClick = { }) {
                    Icon(Icons.AutoMirrored.Filled.HelpCenter, null)
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
        )

        SecondaryTabRow(
            selectedTabIndex = selectedTab,
            containerColor = Color.White,
            contentColor = MainPurple,
            divider = {}
        ) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTab == index,
                    onClick = { selectedTab = index },
                    text = {
                        Text(
                            text = title,
                            fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Medium,
                            color = if (selectedTab == index) MainPurple else Color.Gray
                        )
                    }
                )
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 16.dp)
        ) {
            if (visibleRanking.isNotEmpty()) {
                item {
                    TopThreeSection(visibleRanking.take(3))
                }
            }

            item {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    color = Color.White,
                    shadowElevation = 3.dp
                ) {
                    Column {
                        visibleRanking.drop(3).forEachIndexed { index, userRank ->
                            RankingListItem(
                                rank = userRank.rank,
                                name = if (userRank.uid == currentUser?.uid) "You" else userRank.name,
                                points = userRank.points,
                                isMe = userRank.uid == currentUser?.uid
                            )
                            if (index < visibleRanking.drop(3).lastIndex) {
                                HorizontalDivider(
                                    modifier = Modifier.padding(horizontal = 16.dp),
                                    thickness = 0.5.dp,
                                    color = Color(0xFFEAEAEA)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
