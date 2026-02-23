package helium314.keyboard.latin.dogakdogak

import android.content.SharedPreferences
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Leaderboard
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController

// ═══════════════════════════════════════════════════════════════════
//  DogakdogakMainScreen — 4탭 구조 (타건음 / 이펙트 / 랭킹 / 설정)
// ═══════════════════════════════════════════════════════════════════

@Composable
fun DogakdogakMainScreen(
    onNavigateToKeyboardSettings: () -> Unit,
    prefs: SharedPreferences,
    rankingRepository: RankingRepository? = null,
    purchaseRepository: PurchaseRepository? = null,
    onLogin: ((String) -> Unit)? = null,
    onLogout: (() -> Unit)? = null,
    onDeleteAccount: (() -> Unit)? = null,
    initialRoute: String = "sound",
    onTabChanged: (String) -> Unit = {},
) {
    val colors = LocalDogakdogakColors.current
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route ?: "sound"

    Scaffold(
        containerColor = Color.Transparent,
        bottomBar = {
            NavigationBar(
                modifier = Modifier,
                containerColor = colors.surface,
                contentColor = colors.primary,
                tonalElevation = 0.dp
            ) {
                data class NavItem(
                    val route: String,
                    val label: String,
                    val icon: @Composable () -> Unit,
                )

                val navItems = listOf(
                    NavItem("sound", "타건음") {
                        Icon(Icons.Default.MusicNote, contentDescription = "타건음", modifier = Modifier.size(24.dp))
                    },
                    NavItem("effects", "이펙트") {
                        Icon(Icons.Default.AutoAwesome, contentDescription = "이펙트", modifier = Modifier.size(24.dp))
                    },
                    NavItem("ranking", "랭킹") {
                        Icon(Icons.Default.Leaderboard, contentDescription = "랭킹", modifier = Modifier.size(24.dp))
                    },
                    NavItem("settings", "설정") {
                        Icon(Icons.Default.Settings, contentDescription = "설정", modifier = Modifier.size(24.dp))
                    },
                )
                navItems.forEach { item ->
                    NavigationBarItem(
                        icon = item.icon,
                        label = { Text(item.label, fontSize = 11.sp) },
                        selected = currentRoute == item.route,
                        onClick = {
                            if (currentRoute != item.route) {
                                navController.navigate(item.route) {
                                    popUpTo(initialRoute) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                                onTabChanged(item.route)
                            }
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = colors.primary,
                            selectedTextColor = colors.primary,
                            unselectedIconColor = colors.textTertiary,
                            unselectedTextColor = colors.textTertiary,
                            indicatorColor = colors.primary.copy(alpha = 0.12f)
                        )
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(colors.background)
                .padding(innerPadding)
        ) {
            NavHost(navController = navController, startDestination = initialRoute) {
                composable("sound") { SoundScreen(prefs = prefs, purchaseRepository = purchaseRepository) }
                composable("effects") { EffectsScreen(prefs = prefs, purchaseRepository = purchaseRepository) }
                composable("ranking") {
                    if (rankingRepository != null) {
                        RankingScreen(
                            rankingRepository = rankingRepository,
                            currentUserId = rankingRepository.currentUserId()
                        )
                    } else {
                        RankingScreenPlaceholder()
                    }
                }
                composable("settings") {
                    DogakdogakSettingsScreen(
                        prefs = prefs,
                        onNavigateToKeyboardSettings = onNavigateToKeyboardSettings,
                        rankingRepository = rankingRepository,
                        onLogin = onLogin,
                        onLogout = onLogout,
                        onDeleteAccount = onDeleteAccount,
                    )
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════
//  RankingScreenPlaceholder — 랭킹 미연결 시 표시
// ═══════════════════════════════════════════════════════════════════

@Composable
private fun RankingScreenPlaceholder() {
    val colors = LocalDogakdogakColors.current
    var rankingMode by remember { mutableIntStateOf(0) }
    val periods = listOf("일간", "주간", "월간", "전체")
    var selectedTab by remember { mutableIntStateOf(0) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
    ) {
        Spacer(Modifier.height(48.dp))

        Text(
            text = "랭킹",
            fontSize = 34.sp,
            fontWeight = FontWeight.Bold,
            color = colors.textPrimary,
            modifier = Modifier.padding(horizontal = 24.dp)
        )
        Text(
            text = "전세계 타이핑 순위",
            fontSize = 13.sp,
            color = colors.textSecondary,
            modifier = Modifier.padding(horizontal = 24.dp)
        )

        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier
                .padding(horizontal = 24.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(colors.surface.copy(alpha = 0.6f))
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            listOf("Score" to 0, "Touch" to 1).forEach { (label, index) ->
                val selected = rankingMode == index
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (selected) colors.primary else Color.Transparent)
                        .clickable { rankingMode = index }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label,
                        fontSize = 14.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                        color = if (selected) Color.White else colors.textSecondary
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        ScrollableTabRow(
            selectedTabIndex = selectedTab,
            containerColor = Color.Transparent,
            contentColor = colors.primary,
            edgePadding = 16.dp,
            divider = {}
        ) {
            periods.forEachIndexed { index, period ->
                Tab(
                    selected = selectedTab == index,
                    onClick = { selectedTab = index },
                    text = {
                        Text(
                            text = period,
                            fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Normal,
                            color = if (selectedTab == index) colors.primary else colors.textSecondary
                        )
                    }
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    Icons.Default.Leaderboard,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = colors.textTertiary.copy(alpha = 0.5f)
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    text = "랭킹 준비 중",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.textSecondary
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "곧 전세계 타이핑 랭킹에\n도전할 수 있어요!",
                    fontSize = 13.sp,
                    color = colors.textTertiary,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}
