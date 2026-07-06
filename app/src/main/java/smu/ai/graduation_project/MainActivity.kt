package smu.ai.graduation_project

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.HelpCenter
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import android.widget.Toast
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.auth.userProfileChangeRequest
import com.google.firebase.firestore.firestore
import com.google.firebase.firestore.Query
import com.google.android.gms.tasks.OnCompleteListener
import com.google.android.gms.tasks.Task
import smu.ai.graduation_project.ui.admin.AdminHomeScreen
import smu.ai.graduation_project.ui.components.*
import smu.ai.graduation_project.model.*
import smu.ai.graduation_project.ui.theme.*

class MainActivity : ComponentActivity() {
    private lateinit var firebaseAuth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        firebaseAuth = Firebase.auth
        enableEdgeToEdge()
        setContent {
            Graduation_projectTheme {
                val rootNavController = rememberNavController()
                val startDestination = if (firebaseAuth.currentUser != null) "main" else "landing"
                
                NavHost(navController = rootNavController, startDestination = startDestination) {
                    composable("landing") {
                        LandingScreen(
                            onNavigateToSignUp = {
                                rootNavController.navigate("signup")
                            },
                            onNavigateToLogin = {
                                rootNavController.navigate("login")
                            },
                            onNavigateToGuest = {
                                rootNavController.navigate("main") {
                                    popUpTo("landing") { inclusive = true }
                                }
                            }
                        )
                    }
                    composable("signup") {
                        SignUpScreen(
                            onNavigateBack = { rootNavController.popBackStack() },
                            onSignUpSuccess = {
                                rootNavController.navigate("main") {
                                    popUpTo("landing") { inclusive = true }
                                }
                            }
                        )
                    }
                    composable("login") {
                        LoginScreen(
                            onNavigateBack = { rootNavController.popBackStack() },
                            onLoginSuccess = {
                                rootNavController.navigate("main") {
                                    popUpTo("landing") { inclusive = true }
                                }
                            }
                        )
                    }
                    composable("main") {
                        MainApp(onLogout = {
                            rootNavController.navigate("landing") {
                                popUpTo("main") { inclusive = true }
                            }
                        })
                    }
                }
            }
        }
    }
}

sealed class Screen(val route: String, val label: String, val icon: ImageVector) {
    object Home : Screen("home", "홈", Icons.Default.Home)
    object Mission : Screen("mission", "미션", Icons.AutoMirrored.Filled.Assignment)
    object Add : Screen("add", "", Icons.Default.Add)
    object Ranking : Screen("ranking", "랭킹", Icons.Default.EmojiEvents)
    object Profile : Screen("profile", "마이페이지", Icons.Default.Person)
}

@Composable
fun LandingScreen(
    onNavigateToSignUp: () -> Unit,
    onNavigateToLogin: () -> Unit,
    onNavigateToGuest: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // [1.7/5.3 영역] 제목 및 설명 (즐거움을 더해보세요. 까지)
        Column(
            modifier = Modifier.weight(1.7f),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(text = "TripQuest", color = MainPurple, fontWeight = FontWeight.Bold, fontSize = 20.sp)
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = buildAnnotatedString {
                    append("여행을 ")
                    withStyle(style = SpanStyle(color = MainPurple)) { append("미션") }
                    append("으로,\n")
                    append("더 ")
                    withStyle(style = SpanStyle(color = MainPurple)) { append("특별") }
                    append("하게!")
                },
                fontSize = 26.sp,
                fontWeight = FontWeight.ExtraBold,
                lineHeight = 34.sp,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "다양한 미션을 수행하고\n기록을 남기며 여행의 즐거움을 더해보세요.",
                fontSize = 14.sp,
                color = Color.Gray,
                textAlign = TextAlign.Center
            )
        }

        // [1.6/5.3 영역] 메인 일러스트 및 보상획득 카드까지
        Column(
            modifier = Modifier.weight(1.6f),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(LightPurple, RoundedCornerShape(16.dp)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.CardTravel, null, modifier = Modifier.size(48.dp), tint = MainPurple)
                    Text("메인 일러스트 영역", color = MainPurple, fontSize = 10.sp)
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                InfoCardSmall("미션 수행", Icons.Default.TrackChanges, Modifier.weight(1f))
                InfoCardSmall("랭킹 경쟁", Icons.Default.EmojiEvents, Modifier.weight(1f))
                InfoCardSmall("보상 획득", Icons.Default.CardGiftcard, Modifier.weight(1f))
            }
        }

        // [2/5.3 영역] 회원가입, 로그인, 비회원 버튼 영역
        Column(
            modifier = Modifier.weight(2f),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Button(
                onClick = onNavigateToSignUp,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MainPurple),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("회원가입", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedButton(
                onClick = onNavigateToLogin,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, MainPurple),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("로그인", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MainPurple)
            }
            Spacer(modifier = Modifier.height(8.dp))
            TextButton(onClick = onNavigateToGuest) {
                Text("비회원으로 둘러보기", color = Color.Gray, fontSize = 14.sp)
            }
            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SignUpScreen(onNavigateBack: () -> Unit, onSignUpSuccess: () -> Unit) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val auth = Firebase.auth

    Scaffold(
        containerColor = Color.White,
        topBar = {
            TopAppBar(
                title = { Text("회원가입", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("새로운 계정을 만들어보세요!", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("이름") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                enabled = !isLoading
            )

            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("이메일 주소") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                enabled = !isLoading
            )

            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("비밀번호") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                enabled = !isLoading
            )

            Spacer(modifier = Modifier.weight(1f))

            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
            }

            Button(
                onClick = {
                    if (email.isNotEmpty() && password.isNotEmpty()) {
                        isLoading = true
                        auth.createUserWithEmailAndPassword(email, password)
                            .addOnCompleteListener { task ->
                                if (task.isSuccessful) {
                                    val user = auth.currentUser
                                    val profileUpdates = userProfileChangeRequest {
                                        displayName = name
                                    }
                                    
                                    // 1. 프로필 업데이트 (Auth)
                                    user?.updateProfile(profileUpdates)
                                        ?.addOnCompleteListener { profileTask ->
                                            if (profileTask.isSuccessful) {
                                                // 2. Firestore에 유저 정보 저장
                                                val userMap = hashMapOf(
                                                    "nickname" to name,
                                                    "mail" to email,
                                                    "points" to 0,
                                                    "level" to "Lv.1"
                                                )
                                                
                                                Firebase.firestore.collection("users")
                                                    .document(user.uid)
                                                    .set(userMap)
                                                    .addOnSuccessListener {
                                                        isLoading = false
                                                        Toast.makeText(context, "회원가입 성공!", Toast.LENGTH_SHORT).show()
                                                        onSignUpSuccess()
                                                    }
                                                    .addOnFailureListener { e ->
                                                        isLoading = false
                                                        Toast.makeText(context, "DB 저장 실패: ${e.message}", Toast.LENGTH_SHORT).show()
                                                        onSignUpSuccess() // Auth는 성공했으므로 이동은 시킴
                                                    }
                                            } else {
                                                isLoading = false
                                                Toast.makeText(context, "프로필 설정 실패", Toast.LENGTH_SHORT).show()
                                                onSignUpSuccess()
                                            }
                                        }
                                } else {
                                    isLoading = false
                                    Toast.makeText(context, "실패: ${task.exception?.message}", Toast.LENGTH_LONG).show()
                                }
                            }
                    } else {
                        Toast.makeText(context, "정보를 모두 입력해주세요.", Toast.LENGTH_SHORT).show()
                    }
                },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MainPurple),
                shape = RoundedCornerShape(12.dp),
                enabled = !isLoading
            ) {
                Text("가입하기", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
            
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(onNavigateBack: () -> Unit, onLoginSuccess: () -> Unit) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val auth = Firebase.auth

    Scaffold(
        containerColor = Color.White,
        topBar = {
            TopAppBar(
                title = { Text("로그인", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("다시 오신 것을 환영합니다!", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            
            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("이메일 주소") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                enabled = !isLoading
            )

            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("비밀번호") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                enabled = !isLoading
            )

            Spacer(modifier = Modifier.height(8.dp))
            
            TextButton(
                onClick = { /* TODO */ },
                modifier = Modifier.align(Alignment.End)
            ) {
                Text("비밀번호를 잊으셨나요?", color = MainPurple, fontSize = 14.sp)
            }

            Spacer(modifier = Modifier.weight(1f))

            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
            }

            Button(
                onClick = {
                    if (email.isNotEmpty() && password.isNotEmpty()) {
                        isLoading = true
                        auth.signInWithEmailAndPassword(email, password)
                            .addOnCompleteListener { task ->
                                isLoading = false
                                if (task.isSuccessful) {
                                    Toast.makeText(context, "로그인 성공!", Toast.LENGTH_SHORT).show()
                                    onLoginSuccess()
                                } else {
                                    Toast.makeText(context, "로그인 실패: ${task.exception?.message}", Toast.LENGTH_LONG).show()
                                }
                            }
                    } else {
                        Toast.makeText(context, "이메일과 비밀번호를 입력해주세요.", Toast.LENGTH_SHORT).show()
                    }
                },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MainPurple),
                shape = RoundedCornerShape(12.dp),
                enabled = !isLoading
            ) {
                Text("로그인", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
            
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainApp(onLogout: () -> Unit) {
    val navController = rememberNavController()
    val items = listOf(Screen.Home, Screen.Mission, Screen.Add, Screen.Ranking, Screen.Profile)
    val db = Firebase.firestore
    val currentUser = Firebase.auth.currentUser
    val context = LocalContext.current
    var isAdmin by remember { mutableStateOf(false) }

    LaunchedEffect(currentUser?.uid) {
        currentUser?.uid?.let { uid ->
            db.collection("admins").document(uid).get().addOnSuccessListener { doc ->
                isAdmin = doc.exists()
            }
        }
    }

    Scaffold(
        containerColor = Color.White,
        bottomBar = {
            val navBackStackEntry by navController.currentBackStackEntryAsState()
            val currentDestination = navBackStackEntry?.destination
            val showBottomBar = items.any { it.route == currentDestination?.route }

            if (showBottomBar) {
                NavigationBar(containerColor = Color.White, tonalElevation = 8.dp) {
                    items.forEach { screen ->
                        if (screen is Screen.Add) {
                            NavigationBarItem(
                                icon = {
                                    Box(modifier = Modifier.size(48.dp).background(MainPurple, CircleShape), contentAlignment = Alignment.Center) {
                                        Icon(screen.icon, null, tint = Color.White)
                                    }
                                },
                                label = null,
                                selected = false,
                                onClick = {
                                    if (isAdmin) {
                                        navController.navigate(screen.route) {
                                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    } else {
                                        Toast.makeText(context, "관리자 권한이 없습니다.", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                colors = NavigationBarItemDefaults.colors(indicatorColor = Color.Transparent)
                            )
                        } else {
                            NavigationBarItem(
                                icon = { Icon(screen.icon, null) },
                                label = { Text(screen.label, fontSize = 10.sp) },
                                selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true,
                                onClick = {
                                    navController.navigate(screen.route) {
                                        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = MainPurple,
                                    selectedTextColor = MainPurple,
                                    unselectedIconColor = Color.Gray,
                                    unselectedTextColor = Color.Gray,
                                    indicatorColor = Color.Transparent
                                )
                            )
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(navController, startDestination = Screen.Home.route, Modifier.padding(innerPadding)) {
            composable(Screen.Home.route) { 
                HomeScreen(onNavigateToDetail = { missionId ->
                    navController.navigate("mission_detail/$missionId")
                }) 
            }
            composable(Screen.Mission.route) { 
                MissionScreen(onMissionClick = { missionId ->
                    navController.navigate("mission_detail/$missionId")
                }) 
            }
            composable("mission_detail/{missionId}") { backStackEntry ->
                val missionId = backStackEntry.arguments?.getString("missionId") ?: ""
                MissionDetailScreen(
                    missionId = missionId, 
                    onNavigateBack = { navController.popBackStack() },
                    onPerformMission = { mid -> navController.navigate("mission_perform/$mid") }
                )
            }
            composable("mission_perform/{missionId}") { backStackEntry ->
                val missionId = backStackEntry.arguments?.getString("missionId") ?: ""
                MissionPerformScreen(missionId = missionId, onNavigateBack = { navController.popBackStack() })
            }
            composable(Screen.Add.route) { 
                AdminHomeScreen(
                    onNavigateToMissionManagement = { /* TODO */ },
                    onNavigateToUserManagement = { /* TODO */ }
                ) 
            }
            composable(Screen.Ranking.route) { RankingScreen() }
            composable(Screen.Profile.route) { 
                ProfileScreen(onLogout = {
                    Firebase.auth.signOut()
                    onLogout()
                })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MissionDetailScreen(missionId: String, onNavigateBack: () -> Unit, onPerformMission: (String) -> Unit) {
    val db = Firebase.firestore
    val user = Firebase.auth.currentUser
    val context = LocalContext.current
    var mission by remember { mutableStateOf<Mission?>(null) }
    var isStarting by remember { mutableStateOf(false) }
    var userMissionStatus by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(missionId, user?.uid) {
        // 1. 미션 상세 정보 가져오기
        db.collection("missions").document(missionId).get().addOnSuccessListener { doc ->
            if (doc.exists()) {
                mission = Mission(
                    id = doc.id,
                    title = doc.getString("title") ?: "",
                    desc = doc.getString("desc") ?: "",
                    points = doc.getLong("points")?.toInt() ?: 0,
                    category = doc.getString("category") ?: "투어"
                )
            }
        }

        // 2. 이미 시작한 미션인지 확인
        user?.uid?.let { uid ->
            db.collection("user_missions")
                .whereEqualTo("userId", uid)
                .whereEqualTo("missionId", missionId)
                .get()
                .addOnSuccessListener { snapshot ->
                    if (!snapshot.isEmpty) {
                        userMissionStatus = snapshot.documents.first().getString("status")
                    }
                }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("미션 상세", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
            )
        },
        containerColor = Color.White
    ) { padding ->
        mission?.let { m ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(24.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .background(CardGray, RoundedCornerShape(16.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Landscape, null, modifier = Modifier.size(80.dp), tint = Color.LightGray)
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                Surface(color = LightPurple, shape = RoundedCornerShape(8.dp)) {
                    Text(m.category, color = MainPurple, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp))
                }
                
                Spacer(modifier = Modifier.height(12.dp))
                
                Text(m.title, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(m.desc, fontSize = 16.sp, color = Color.Gray)
                
                Spacer(modifier = Modifier.height(24.dp))
                
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Stars, null, tint = Orange, modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("완료 보상: ", fontSize = 16.sp)
                    Text("${m.points}P", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = MainPurple)
                }
                
                Spacer(modifier = Modifier.weight(1f))
                
                val buttonText = when (userMissionStatus) {
                    "진행중" -> "미션 계속하기"
                    "완료" -> "이미 완료한 미션"
                    else -> "미션 시작하기"
                }

                Button(
                    onClick = {
                        if (userMissionStatus == "진행중") {
                            onPerformMission(missionId)
                        } else if (user != null) {
                            isStarting = true
                            val userMission = hashMapOf(
                                "userId" to user.uid,
                                "missionId" to missionId,
                                "status" to "진행중",
                                "progress" to 0f,
                                "title" to m.title,
                                "points" to m.points
                            )
                            db.collection("user_missions")
                                .add(userMission)
                                .addOnSuccessListener {
                                    isStarting = false
                                    Toast.makeText(context, "미션을 시작했습니다!", Toast.LENGTH_SHORT).show()
                                    onNavigateBack()
                                }
                                .addOnFailureListener {
                                    isStarting = false
                                    Toast.makeText(context, "실패: ${it.message}", Toast.LENGTH_SHORT).show()
                                }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (userMissionStatus == "완료") Color.Gray else MainPurple
                    ),
                    shape = RoundedCornerShape(12.dp),
                    enabled = !isStarting && userMissionStatus != "완료"
                ) {
                    if (isStarting) {
                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
                    } else {
                        Text(buttonText, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun ProfileScreen(onLogout: () -> Unit) {
    val auth = Firebase.auth
    val user = auth.currentUser
    val context = LocalContext.current
    
    var nickname by remember { mutableStateOf(user?.displayName ?: "Traveler") }
    var points by remember { mutableLongStateOf(0L) }
    var level by remember { mutableStateOf("Lv.1") }
    
    var completedCount by remember { mutableIntStateOf(0) }
    var ongoingCount by remember { mutableIntStateOf(0) }
    var totalParticipatedCount by remember { mutableIntStateOf(0) }
    var totalMissionsCount by remember { mutableIntStateOf(0) }
    
    var showNicknameDialog by remember { mutableStateOf(false) }
    var newNickname by remember { mutableStateOf(nickname) }
    
    var showPasswordDialog by remember { mutableStateOf(false) }

    LaunchedEffect(user?.uid) {
        user?.uid?.let { uid ->
            // 1. Listen to user info (points, nickname)
            Firebase.firestore.collection("users").document(uid).addSnapshotListener { snapshot, _ ->
                if (snapshot != null && snapshot.exists()) {
                    nickname = snapshot.getString("nickname") ?: user.displayName ?: "Traveler"
                    points = snapshot.getLong("points") ?: 0L
                    level = snapshot.getString("level") ?: "Lv.1"
                }
            }

            // 2. Listen to user mission stats
            Firebase.firestore.collection("user_missions")
                .whereEqualTo("userId", uid)
                .addSnapshotListener { snapshot, _ ->
                    if (snapshot != null) {
                        val docs = snapshot.documents
                        completedCount = docs.count { it.getString("status") == "완료" }
                        ongoingCount = docs.count { it.getString("status") == "진행중" }
                        totalParticipatedCount = docs.size
                    }
                }

            // 3. Listen to total missions available
            Firebase.firestore.collection("missions").addSnapshotListener { snapshot, _ ->
                if (snapshot != null) {
                    totalMissionsCount = snapshot.size()
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .verticalScroll(rememberScrollState())
    ) {
        // Top Header
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Text("마이페이지", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            IconButton(
                onClick = { /* TODO: Settings */ },
                modifier = Modifier.align(Alignment.CenterEnd)
            ) {
                Icon(Icons.Default.Settings, contentDescription = "Settings", tint = Color.Black)
            }
        }

        // Profile Info Section
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box {
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .background(Color(0xFF81C784), CircleShape), // Green avatar bg from image
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Person, null, modifier = Modifier.size(48.dp), tint = Color.White)
                }
                Surface(
                    modifier = Modifier
                        .size(24.dp)
                        .align(Alignment.BottomEnd),
                    shape = CircleShape,
                    color = Color.White,
                    shadowElevation = 2.dp
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.PhotoCamera, null, modifier = Modifier.size(14.dp), tint = MainPurple)
                    }
                }
            }
            
            Spacer(modifier = Modifier.width(20.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = nickname,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(
                        color = LightPurple,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            level,
                            color = MainPurple,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(12.dp))
                
                LinearProgressIndicator(
                    progress = { 0.625f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(CircleShape),
                    color = MainPurple,
                    trackColor = Color(0xFFEEEEEE),
                )
                
                Text(
                    text = buildAnnotatedString {
                        withStyle(style = SpanStyle(color = MainPurple, fontWeight = FontWeight.Bold)) {
                            append("1,250")
                        }
                        append(" / 2,000")
                    },
                    fontSize = 12.sp,
                    modifier = Modifier.align(Alignment.End).padding(top = 4.dp),
                    color = Color.Gray
                )
            }
        }

        // Stats Row (Points & Badges)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            StatCard(
                title = "보유 포인트",
                value = "${String.format("%,d", points)}P",
                icon = Icons.Default.EmojiEvents,
                modifier = Modifier.weight(1f)
            )
            StatCard(
                title = "획득 배지",
                value = "0개",
                icon = Icons.Default.Stars,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Grade Card
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            color = LightPurple,
            shape = RoundedCornerShape(16.dp)
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.MilitaryTech, null, tint = MainPurple, modifier = Modifier.size(32.dp))
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("나의 등급", fontSize = 12.sp, color = MainPurple)
                    Text("여행자", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("다음 등급까지", fontSize = 11.sp, color = Color.Gray)
                    Text("--- P 남음", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
                Icon(Icons.Default.ChevronRight, null, tint = Color.Gray)
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Mission Status Section
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("미션 현황", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Text("전체 보기 >", fontSize = 13.sp, color = MainPurple)
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            MissionStatusItem("완료한 미션", "${completedCount}개", Icons.Default.CheckCircle, Color(0xFF4CAF50))
            MissionStatusItem("진행 중 미션", "${ongoingCount}개", Icons.Default.PlayCircle, Color(0xFFFF9800))
            MissionStatusItem("참여한 미션", "${totalParticipatedCount}개", Icons.Default.HourglassEmpty, Color.Gray)
            MissionStatusItem("총 미션", "${totalMissionsCount}개", Icons.Default.Flag, Color(0xFFE91E63))
        }

        Spacer(modifier = Modifier.height(24.dp))

        // My Activity Section
        Text(
            "나의 활동",
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        Spacer(modifier = Modifier.height(8.dp))
        ProfileMenuItem("미션 내역", Icons.AutoMirrored.Filled.Assignment) { /* TODO */ }
        ProfileMenuItem("내가 올린 사진", Icons.Default.PhotoLibrary) { /* TODO */ }
        ProfileMenuItem("보상 내역", Icons.Default.CardGiftcard) { /* TODO */ }
        ProfileMenuItem("찜한 미션", Icons.Default.FavoriteBorder) { /* TODO */ }
        ProfileMenuItem("통계", Icons.Default.BarChart) { /* TODO */ }

        Spacer(modifier = Modifier.height(24.dp))

        // Misc Section
        Text(
            "기타",
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        Spacer(modifier = Modifier.height(8.dp))
        ProfileMenuItem("설정", Icons.Default.Settings) { showNicknameDialog = true }
        ProfileMenuItem("고객센터", Icons.AutoMirrored.Filled.HelpCenter) { /* TODO */ }
        ProfileMenuItem("로그아웃", Icons.AutoMirrored.Filled.ExitToApp) { onLogout() }

        Spacer(modifier = Modifier.height(32.dp))
    }

    // Dialogs (Nickname Change)
    if (showNicknameDialog) {
        AlertDialog(
            onDismissRequest = { showNicknameDialog = false },
            title = { Text("닉네임 변경") },
            text = {
                OutlinedTextField(
                    value = newNickname,
                    onValueChange = { newNickname = it },
                    label = { Text("새 닉네임") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val profileUpdates = userProfileChangeRequest {
                        displayName = newNickname
                    }
                    user?.updateProfile(profileUpdates)?.addOnCompleteListener { task ->
                        if (task.isSuccessful) {
                            // Firestore에도 업데이트
                            user.uid.let { uid ->
                                Firebase.firestore.collection("users").document(uid)
                                    .update("nickname", newNickname)
                            }
                            Toast.makeText(context, "닉네임이 변경되었습니다.", Toast.LENGTH_SHORT).show()
                            showNicknameDialog = false
                        }
                    }
                }) { Text("변경") }
            },
            dismissButton = {
                TextButton(onClick = { showNicknameDialog = false }) { Text("취소") }
            }
        )
    }
}

@Composable
fun HomeScreen(onNavigateToDetail: (String) -> Unit) {
    val user = Firebase.auth.currentUser
    var points by remember { mutableLongStateOf(0L) }
    var userName by remember { mutableStateOf(user?.displayName ?: "Traveler") }
    
    // Mission Progress State
    var startedCount by remember { mutableIntStateOf(0) }
    val totalGoal = 5 // Assume goal is 5 per week
    
    // Recommended Mission State
    var activeMission by remember { mutableStateOf<Mission?>(null) }

    LaunchedEffect(user?.uid) {
        user?.uid?.let { uid ->
            // 1. Listen to user info (points, nickname)
            Firebase.firestore.collection("users").document(uid).addSnapshotListener { snapshot, _ ->
                if (snapshot != null && snapshot.exists()) {
                    points = snapshot.getLong("points") ?: 0L
                    userName = snapshot.getString("nickname") ?: user.displayName ?: "Traveler"
                }
            }
            
            // 2. Listen to user missions for progress and recommended
            Firebase.firestore.collection("user_missions")
                .whereEqualTo("userId", uid)
                .addSnapshotListener { snapshot, _ ->
                    if (snapshot != null) {
                        val documents = snapshot.documents
                        startedCount = documents.size
                        
                        // Find first "진행중" mission to show as recommended
                        val currentActive = documents.firstOrNull { it.getString("status") == "진행중" }
                        if (currentActive != null) {
                            activeMission = Mission(
                                id = currentActive.getString("missionId") ?: "",
                                title = currentActive.getString("title") ?: "",
                                desc = "현재 진행 중인 미션입니다.",
                                points = currentActive.getLong("points")?.toInt() ?: 0,
                                status = "진행중"
                            )
                        } else {
                            // If no active mission, fetch one from general missions
                            Firebase.firestore.collection("missions").limit(1).get().addOnSuccessListener { missionSnapshot ->
                                val doc = missionSnapshot.documents.firstOrNull()
                                if (doc != null) {
                                    activeMission = Mission(
                                        id = doc.id,
                                        title = doc.getString("title") ?: "",
                                        desc = doc.getString("desc") ?: "",
                                        points = doc.getLong("points")?.toInt() ?: 0,
                                        category = doc.getString("category") ?: "투어"
                                    )
                                }
                            }
                        }
                    }
                }
        }
    }
    
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)
    ) {
        // ... (Top Header)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Seoul Quest", color = MainPurple, fontWeight = FontWeight.Bold, fontSize = 24.sp)
            Icon(Icons.Default.Notifications, null, tint = Color.Gray)
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // Greeting and Balance
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Column {
                Text("Hello,", fontSize = 28.sp, fontWeight = FontWeight.Bold)
                Text("$userName! 👋", fontSize = 28.sp, fontWeight = FontWeight.Bold)
                Text("오늘도 새로운 미션에 도전해보세요!", fontSize = 14.sp, color = Color.Gray)
            }
            
            Surface(
                color = CardGray,
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.padding(top = 8.dp)
            ) {
                Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Stars, null, tint = Orange, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(String.format("%,d", points), fontWeight = FontWeight.Bold)
                }
            }
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // Progress Card
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp)
                .background(Brush.linearGradient(listOf(GradientStart, GradientEnd)), RoundedCornerShape(16.dp))
                .padding(16.dp)
        ) {
            Column {
                Text("이번 주 미션 진행률", color = Color.White, fontSize = 16.sp)
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.Bottom) {
                    Text("$startedCount/$totalGoal", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.width(8.dp))
                    LinearProgressIndicator(
                        progress = { (startedCount.toFloat() / totalGoal).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth(0.7f).height(8.dp).clip(CircleShape),
                        color = Color.White,
                        trackColor = Color.White.copy(alpha = 0.3f),
                    )
                }
            }
            Icon(
                Icons.Default.CardGiftcard, null,
                tint = Color.White.copy(alpha = 0.8f),
                modifier = Modifier.size(60.dp).align(Alignment.CenterEnd)
            )
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // Recommended Mission
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(if (activeMission?.status == "진행중") "현재 진행 중인 미션" else "추천 미션", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            TextButton(onClick = { /* TODO */ }) {
                Text("더보기 >", color = MainPurple)
            }
        }
        
        activeMission?.let { mission ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Row(modifier = Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(modifier = Modifier.size(100.dp).background(LightPurple, RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Landscape, null, tint = MainPurple, modifier = Modifier.size(40.dp))
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Surface(color = if (mission.status == "진행중") Color(0xFF4CAF50) else MainPurple, shape = RoundedCornerShape(4.dp)) {
                            Text(mission.status.ifEmpty { "추천" }, color = Color.White, fontSize = 10.sp, modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp))
                        }
                        Text(mission.title, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text(mission.desc, fontSize = 12.sp, color = Color.Gray, maxLines = 1)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("보상", fontSize = 12.sp)
                            Icon(Icons.Default.Stars, null, tint = Orange, modifier = Modifier.size(14.dp))
                            Text(" ${mission.points}P", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = { onNavigateToDetail(mission.id) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = MainPurple),
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Text(if (mission.status == "진행중") "미션 계속하기" else "미션 보기", fontSize = 12.sp)
                        }
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // Categories
        val categories = listOf("전체" to Icons.Default.GridView, "투어" to Icons.Default.LocationCity, "맛집" to Icons.Default.Restaurant, "체험" to Icons.Default.CameraAlt, "쇼핑" to Icons.Default.ShoppingBag)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            items(categories) { (label, icon) ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Surface(modifier = Modifier.size(56.dp), shape = RoundedCornerShape(16.dp), color = CardGray) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(icon, null, tint = if (label == "전체") MainPurple else Color.Gray)
                        }
                    }
                    Text(label, fontSize = 12.sp, color = if (label == "전체") MainPurple else Color.Gray)
                }
            }
        }
        
        Spacer(modifier = Modifier.height(80.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MissionScreen(onMissionClick: (String) -> Unit) {
    var selectedCategory by remember { mutableStateOf("전체") }
    val categories = listOf("전체", "투어", "맛집", "체험", "쇼핑")
    
    // DB에서 불러온 미션 목록을 담을 상태
    var missionList by remember { mutableStateOf<List<Mission>>(emptyList()) }
    val db = Firebase.firestore

    // 카테고리가 바뀔 때마다 DB에서 데이터 가져오기
    LaunchedEffect(selectedCategory, Firebase.auth.currentUser?.uid) {
        val user = Firebase.auth.currentUser
        val collectionRef = db.collection("missions")
        val query = if (selectedCategory == "전체") {
            collectionRef
        } else {
            collectionRef.whereEqualTo("category", selectedCategory)
        }

        query.addSnapshotListener { snapshot, _ ->
            if (snapshot != null) {
                val allMissions = snapshot.documents.map { doc ->
                    Mission(
                        id = doc.id,
                        title = doc.getString("title") ?: "",
                        desc = doc.getString("desc") ?: "",
                        points = doc.getLong("points")?.toInt() ?: 0,
                        category = doc.getString("category") ?: "투어",
                        status = "미 진행"
                    )
                }

                // 사용자 진행 상태 연동
                if (user != null) {
                    db.collection("user_missions")
                        .whereEqualTo("userId", user.uid)
                        .addSnapshotListener { userSnapshot, _ ->
                            if (userSnapshot != null) {
                                val userStatusMap = userSnapshot.documents.associate { 
                                    (it.getString("missionId") ?: "") to (it.getString("status") ?: "미 진행") 
                                }
                                val userProgressMap = userSnapshot.documents.associate {
                                    (it.getString("missionId") ?: "") to (it.get("progress")?.toString()?.toFloatOrNull() ?: 0f)
                                }
                                
                                missionList = allMissions.map { m ->
                                    m.copy(
                                        status = userStatusMap[m.id] ?: "미 진행",
                                        progress = userProgressMap[m.id] ?: 0f,
                                        progressText = if (userStatusMap[m.id] == "완료") "1/1" else "0/1"
                                    )
                                }
                            } else {
                                missionList = allMissions
                            }
                        }
                } else {
                    missionList = allMissions
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
    ) {
        // Top App Bar
        CenterAlignedTopAppBar(
            title = { Text("미션 목록", fontWeight = FontWeight.Bold, fontSize = 20.sp) },
            actions = {
                IconButton(onClick = { /* TODO */ }) {
                    Icon(Icons.Default.Search, contentDescription = "Search")
                }
            },
            colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color.White)
        )

        // Category Tabs
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(categories) { category ->
                val isSelected = category == selectedCategory
                Surface(
                    onClick = { selectedCategory = category },
                    shape = RoundedCornerShape(12.dp),
                    color = if (isSelected) MainPurple else Color.Transparent,
                    modifier = Modifier.height(36.dp)
                ) {
                    Box(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = category,
                            color = if (isSelected) Color.White else Color.Gray,
                            fontSize = 14.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }
        }

        // Mission List
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(missionList) { mission ->
                MissionCard(
                    title = mission.title,
                    desc = mission.desc,
                    progress = mission.progress,
                    progressText = mission.progressText,
                    points = mission.points,
                    status = mission.status,
                    statusColor = when(mission.status) {
                        "진행중" -> Color(0xFF4CAF50)
                        "완료" -> MainPurple
                        "선택" -> Color(0xFFFF9800)
                        else -> Color(0xFF757575)
                    },
                    onClick = { onMissionClick(mission.id) }
                )
            }
        }
    }
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RankingScreen() {
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("전체 랭킹", "친구 랭킹")
    val db = Firebase.firestore
    val currentUser = Firebase.auth.currentUser
    
    var rankingList by remember { mutableStateOf<List<UserRank>>(emptyList()) }

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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
    ) {
        // Top App Bar
        CenterAlignedTopAppBar(
            title = { Text("랭킹", fontWeight = FontWeight.Bold, fontSize = 20.sp) },
            actions = {
                IconButton(onClick = { /* TODO */ }) {
                    Icon(Icons.AutoMirrored.Filled.HelpCenter, contentDescription = "Help")
                }
            },
            colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color.White)
        )


        // Tabs
        SecondaryTabRow(
            selectedTabIndex = selectedTab,
            containerColor = Color.White,
            contentColor = MainPurple,
            indicator = { 
                TabRowDefaults.SecondaryIndicator(
                    Modifier.tabIndicatorOffset(selectedTab),
                    color = MainPurple
                )
            },
            divider = {}
        ) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTab == index,
                    onClick = { selectedTab = index },
                    text = {
                        Text(
                            text = title,
                            fontSize = 15.sp,
                            fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Normal,
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
            // Top 3 Section
            if (rankingList.isNotEmpty()) {
                item {
                    val top3 = rankingList.take(3)
                    TopThreeSection(top3)
                }
            }

            // List Section
            item {
                Surface(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    shape = RoundedCornerShape(16.dp),
                    color = Color.White,
                    shadowElevation = 2.dp
                ) {
                    Column {
                        val otherRanks = rankingList.drop(3)
                        otherRanks.forEachIndexed { index, userRank ->
                            RankingListItem(
                                rank = userRank.rank,
                                name = userRank.name,
                                points = userRank.points,
                                isMe = userRank.uid == currentUser?.uid
                            )
                            if (index < otherRanks.size - 1) {
                                HorizontalDivider(
                                    modifier = Modifier.padding(horizontal = 16.dp),
                                    thickness = 0.5.dp,
                                    color = Color.LightGray
                                )
                            }
                        }
                    }
                }
            }

            // Info Box
            item {
                Spacer(modifier = Modifier.height(24.dp))
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    color = CardGray,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.EmojiEvents, null, tint = MainPurple, modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text("랭킹 정보", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text("매일 자정(00:00)에 랭킹이 갱신됩니다.", color = Color.Gray, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MissionPerformScreen(missionId: String, onNavigateBack: () -> Unit) {
    val db = Firebase.firestore
    val user = Firebase.auth.currentUser
    val context = LocalContext.current
    var missionTitle by remember { mutableStateOf("") }
    var points by remember { mutableIntStateOf(0) }
    var isCompleting by remember { mutableStateOf(false) }

    LaunchedEffect(missionId) {
        db.collection("missions").document(missionId).get().addOnSuccessListener { doc ->
            missionTitle = doc.getString("title") ?: ""
            points = doc.getLong("points")?.toInt() ?: 0
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("미션 인증", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
            )
        },
        containerColor = Color.Black // Camera feel
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(Color.DarkGray),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.PhotoCamera, null, modifier = Modifier.size(80.dp), tint = Color.Gray)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("인증 사진을 촬영해주세요", color = Color.White)
                    Text(missionTitle, color = MainPurple, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp)
                    .background(Color.Black),
                contentAlignment = Alignment.Center
            ) {
                if (isCompleting) {
                    CircularProgressIndicator(color = MainPurple)
                } else {
                    // Capture Button
                    Surface(
                        modifier = Modifier
                            .size(80.dp)
                            .clickable {
                                if (user != null) {
                                    isCompleting = true
                                    // 1. Update user_missions
                                    db.collection("user_missions")
                                        .whereEqualTo("userId", user.uid)
                                        .whereEqualTo("missionId", missionId)
                                        .get()
                                        .addOnSuccessListener { snapshot ->
                                            val docId = snapshot.documents.firstOrNull()?.id
                                            if (docId != null) {
                                                db.collection("user_missions").document(docId)
                                                    .update("status", "완료", "progress", 1.0f)
                                                    .addOnSuccessListener {
                                                        // 2. Update user points
                                                        db.collection("users").document(user.uid)
                                                            .get()
                                                            .addOnSuccessListener { userDoc ->
                                                                val currentPoints = userDoc.getLong("points") ?: 0L
                                                                db.collection("users").document(user.uid)
                                                                    .update("points", currentPoints + points)
                                                                    .addOnSuccessListener {
                                                                        Toast.makeText(context, "미션 완료! ${points}P 획득!", Toast.LENGTH_LONG).show()
                                                                        onNavigateBack()
                                                                    }
                                                            }
                                                    }
                                            }
                                        }
                                }
                            },
                        shape = CircleShape,
                        color = Color.White,
                        border = androidx.compose.foundation.BorderStroke(4.dp, Color.Gray)
                    ) {
                        Box(
                            modifier = Modifier
                                .padding(4.dp)
                                .background(Color.White, CircleShape)
                                .border(2.dp, Color.Black, CircleShape)
                        )
                    }
                }
            }
        }
    }
}

// End of MainActivity.kt
