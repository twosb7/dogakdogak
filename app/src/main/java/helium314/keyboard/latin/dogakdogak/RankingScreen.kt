package helium314.keyboard.latin.dogakdogak

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.NumberFormat

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RankingScreen(
    rankingRepository: RankingRepository,
    currentUserId: String?
) {
    val colors = LocalDogakdogakColors.current
    var rankingView by remember { mutableIntStateOf(1) } // 0=전체 랭킹, 1=앱별 랭킹
    var selectedTab by remember { mutableIntStateOf(0) }
    var rankingMode by remember { mutableIntStateOf(0) }
    var rankings by remember { mutableStateOf<List<RankingEntry>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var isRefreshing by remember { mutableStateOf(false) }
    var lastUpdateTime by remember { mutableLongStateOf(0L) }
    val scope = rememberCoroutineScope()

    val isLoggedIn by rankingRepository.isLoggedIn.collectAsState(initial = false)

    var toastMessage by remember { mutableStateOf<String?>(null) }

    // 앱별 랭킹
    var appRankingMode by remember { mutableIntStateOf(0) } // 0=Score, 1=Touch
    var selectedAppIndex by remember { mutableIntStateOf(0) }
    var selectedAppTab by remember { mutableIntStateOf(0) }
    var appUserRankings by remember { mutableStateOf<List<RankingEntry>>(emptyList()) }
    var isAppLoading by remember { mutableStateOf(false) }
    var isAppRefreshing by remember { mutableStateOf(false) }
    val trackedAppsList = remember { AppClickCountRepository.TRACKED_APPS.entries.toList() }

    val periods = RankingPeriod.entries

    val context = LocalContext.current

    // 랭킹 화면 방문 시 타임스탬프 기록 (백그라운드 동기화 7일 필터용)
    LaunchedEffect(Unit) {
        helium314.keyboard.latin.utils.DeviceProtectedUtils
            .getSharedPreferences(context)
            .edit()
            .putLong(PrefsKeys.LAST_RANKING_VISIT, System.currentTimeMillis())
            .apply()
    }

    LaunchedEffect(isLoggedIn) {
        if (isLoggedIn) {
            scope.launch {
                rankingRepository.refreshProfile()
                val counterMode = helium314.keyboard.latin.utils.DeviceProtectedUtils
                    .getSharedPreferences(context)
                    .getString(PrefsKeys.COUNTER_MODE, "score") ?: "score"
                val repo = ClickCountRepository.getInstance(context)
                val appRepo = AppClickCountRepository.getInstance(context)
                if (counterMode == "score") {
                    rankingRepository.syncDailyClicks(repo.getDailyScoreValue())
                    rankingRepository.syncAppDailyClicks(appRepo.getAllDailyScores())
                } else {
                    rankingRepository.syncDailyTouches(repo.getDailyTouchesValue())
                    rankingRepository.syncAppDailyTouches(appRepo.getAllDailyTouches())
                }
            }
        }
    }

    LaunchedEffect(selectedTab, rankingMode) {
        isLoading = true
        rankings = if (rankingMode == 0) {
            rankingRepository.getRanking(periods[selectedTab])
        } else {
            rankingRepository.getTouchRanking(periods[selectedTab])
        }
        lastUpdateTime = rankingRepository.getLastUpdateTime()
        isLoading = false
    }

    // 앱별 유저 랭킹 로드
    LaunchedEffect(rankingView, selectedAppIndex, appRankingMode, selectedAppTab) {
        if (rankingView == 1) {
            isAppLoading = true
            val pkg = trackedAppsList[selectedAppIndex].key
            appUserRankings = if (appRankingMode == 0) {
                rankingRepository.getAppRanking(pkg, periods[selectedAppTab])
            } else {
                rankingRepository.getAppTouchRanking(pkg, periods[selectedAppTab])
            }
            lastUpdateTime = rankingRepository.getLastUpdateTime()
            isAppLoading = false
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(colors.background)
        ) {
            Spacer(Modifier.height(48.dp))

            // 랭킹 제목 + 앱별/전체 토글 (오른쪽 정렬)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "랭킹",
                    fontSize = 34.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.textPrimary
                )
                Spacer(Modifier.weight(1f))
                listOf("앱별 랭킹" to 1, "전체 랭킹" to 0).forEach { (label, index) ->
                    val selected = rankingView == index
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (selected) colors.primary else Color.Transparent)
                            .then(
                                if (!selected) Modifier.border(1.dp, colors.glassBorder, RoundedCornerShape(8.dp))
                                else Modifier
                            )
                            .clickable { rankingView = index }
                            .padding(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = label,
                            fontSize = 13.sp,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                            color = if (selected) colors.onPrimary else colors.textSecondary
                        )
                    }
                    if (index == 1) Spacer(Modifier.width(6.dp))
                }
            }

            if (rankingView == 0) {
                // === 전체 랭킹 ===
                // Score/Touch 모드 선택 세그먼트
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier
                        .padding(horizontal = 24.dp)
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("Score" to 0, "Touch" to 1).forEach { (label, index) ->
                        val selected = rankingMode == index
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .then(
                                    if (selected) Modifier.border(1.5.dp, colors.primary, RoundedCornerShape(10.dp))
                                    else Modifier
                                )
                                .clickable { rankingMode = index }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label,
                                fontSize = 14.sp,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                color = if (selected) colors.primary else colors.textSecondary
                            )
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                // 기간 탭
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
                                    text = period.displayName,
                                    fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Normal,
                                    color = if (selectedTab == index) colors.primary else colors.textSecondary
                                )
                            }
                        )
                    }
                }

                Spacer(Modifier.height(8.dp))

                if (isLoading && rankings.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = colors.primary)
                    }
                } else if (rankings.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("아직 데이터가 없습니다", fontSize = 17.sp, color = colors.textSecondary)
                            Spacer(Modifier.height(8.dp))
                            Text("타이핑을 시작하고 랭킹에 도전하세요!", fontSize = 13.sp, color = colors.textTertiary)
                        }
                    }
                } else {
                    PullToRefreshBox(
                        isRefreshing = isRefreshing,
                        onRefresh = {
                            scope.launch {
                                isRefreshing = true
                                rankings = if (rankingMode == 0) {
                                    rankingRepository.getRanking(periods[selectedTab], forceRefresh = true)
                                } else {
                                    rankingRepository.getTouchRanking(periods[selectedTab], forceRefresh = true)
                                }
                                lastUpdateTime = rankingRepository.getLastUpdateTime()
                                isRefreshing = false
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    ) {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            itemsIndexed(rankings) { _, entry ->
                                RankingItem(
                                    entry = entry,
                                    isCurrentUser = entry.userId == currentUserId,
                                    unit = if (rankingMode == 0) "점" else "회"
                                )
                            }
                            item { Spacer(Modifier.height(80.dp)) }
                        }
                    }
                }
            } else {
                // === 앱별 랭킹 ===
                Spacer(Modifier.height(12.dp))

                // Score/Touch 토글 + 앱 드롭다운 (같은 행)
                var appDropdownExpanded by remember { mutableStateOf(false) }
                val dropdownScrollState = rememberScrollState()

                Row(
                    modifier = Modifier
                        .padding(horizontal = 24.dp)
                        .fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Score/Touch 세그먼트
                    listOf("Score" to 0, "Touch" to 1).forEach { (label, index) ->
                        val selected = appRankingMode == index
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .then(
                                    if (selected) Modifier.border(1.5.dp, colors.primary, RoundedCornerShape(10.dp))
                                    else Modifier.border(1.dp, colors.glassBorder, RoundedCornerShape(10.dp))
                                )
                                .clickable { appRankingMode = index }
                                .padding(horizontal = 16.dp, vertical = 10.dp)
                        ) {
                            Text(
                                text = label,
                                fontSize = 14.sp,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                color = if (selected) colors.primary else colors.textSecondary
                            )
                        }
                    }

                    Spacer(Modifier.weight(1f))

                    // 앱 드롭다운
                    Box {
                        val selectedApp = trackedAppsList[selectedAppIndex]
                        val selectedIconRes = AppClickCountRepository.APP_ICON_RES[selectedApp.key]
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .border(1.dp, colors.glassBorder, RoundedCornerShape(10.dp))
                                .clickable { appDropdownExpanded = true }
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (selectedIconRes != null) {
                                Image(
                                    painter = painterResource(selectedIconRes),
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp).clip(RoundedCornerShape(4.dp)),
                                    contentScale = ContentScale.Crop
                                )
                                Spacer(Modifier.width(6.dp))
                            }
                            Text(
                                text = selectedApp.value,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color = colors.textPrimary,
                                maxLines = 1
                            )
                            Icon(
                                Icons.Default.KeyboardArrowDown,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = colors.textSecondary
                            )
                        }

                        DropdownMenu(
                            expanded = appDropdownExpanded,
                            onDismissRequest = { appDropdownExpanded = false },
                            scrollState = dropdownScrollState,
                            modifier = Modifier
                                .height(300.dp)
                                .simpleScrollbar(dropdownScrollState, colors.primary.copy(alpha = 0.5f))
                        ) {
                            trackedAppsList.forEachIndexed { index, (pkg, displayName) ->
                                val iconRes = AppClickCountRepository.APP_ICON_RES[pkg]
                                DropdownMenuItem(
                                    text = {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            if (iconRes != null) {
                                                Image(
                                                    painter = painterResource(iconRes),
                                                    contentDescription = null,
                                                    modifier = Modifier.size(24.dp).clip(RoundedCornerShape(5.dp)),
                                                    contentScale = ContentScale.Crop
                                                )
                                            } else {
                                                Box(
                                                    modifier = Modifier.size(24.dp).clip(RoundedCornerShape(5.dp))
                                                        .background(colors.textTertiary.copy(alpha = 0.3f)),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Text(displayName.take(1), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = colors.textPrimary)
                                                }
                                            }
                                            Spacer(Modifier.width(10.dp))
                                            Text(
                                                displayName,
                                                fontWeight = if (index == selectedAppIndex) FontWeight.Bold else FontWeight.Normal,
                                                color = if (index == selectedAppIndex) colors.primary else colors.textPrimary
                                            )
                                        }
                                    },
                                    onClick = {
                                        selectedAppIndex = index
                                        appDropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }

                // 기간 탭
                ScrollableTabRow(
                    selectedTabIndex = selectedAppTab,
                    containerColor = Color.Transparent,
                    contentColor = colors.primary,
                    edgePadding = 16.dp,
                    divider = {}
                ) {
                    periods.forEachIndexed { index, period ->
                        Tab(
                            selected = selectedAppTab == index,
                            onClick = { selectedAppTab = index },
                            text = {
                                Text(
                                    text = period.displayName,
                                    fontWeight = if (selectedAppTab == index) FontWeight.Bold else FontWeight.Normal,
                                    color = if (selectedAppTab == index) colors.primary else colors.textSecondary
                                )
                            }
                        )
                    }
                }

                Spacer(Modifier.height(8.dp))

                if (isAppLoading && appUserRankings.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = colors.primary)
                    }
                } else if (appUserRankings.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("아직 데이터가 없습니다", fontSize = 17.sp, color = colors.textSecondary)
                            Spacer(Modifier.height(8.dp))
                            Text("${trackedAppsList[selectedAppIndex].value}에서 타이핑을 시작해보세요!", fontSize = 13.sp, color = colors.textTertiary)
                        }
                    }
                } else {
                    PullToRefreshBox(
                        isRefreshing = isAppRefreshing,
                        onRefresh = {
                            scope.launch {
                                isAppRefreshing = true
                                val pkg = trackedAppsList[selectedAppIndex].key
                                appUserRankings = if (appRankingMode == 0) {
                                    rankingRepository.getAppRanking(pkg, periods[selectedAppTab], forceRefresh = true)
                                } else {
                                    rankingRepository.getAppTouchRanking(pkg, periods[selectedAppTab], forceRefresh = true)
                                }
                                lastUpdateTime = rankingRepository.getLastUpdateTime()
                                isAppRefreshing = false
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    ) {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            itemsIndexed(appUserRankings) { _, entry ->
                                RankingItem(
                                    entry = entry,
                                    isCurrentUser = entry.userId == currentUserId,
                                    unit = if (appRankingMode == 0) "점" else "회"
                                )
                            }
                            item { Spacer(Modifier.height(80.dp)) }
                        }
                    }
                }
            }
        }

        // Toss 스타일 토스트
        AnimatedVisibility(
            visible = toastMessage != null,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 90.dp),
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut()
        ) {
            toastMessage?.let { msg ->
                LaunchedEffect(msg) {
                    delay(2500)
                    toastMessage = null
                }
                Text(
                    text = msg,
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier
                        .padding(horizontal = 20.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0xE6222222))
                        .padding(horizontal = 20.dp, vertical = 14.dp)
                )
            }
        }
    }

}

@Composable
private fun RankingItem(entry: RankingEntry, isCurrentUser: Boolean, unit: String = "점") {
    val colors = LocalDogakdogakColors.current
    val rankColor = when (entry.rank) { 1L -> colors.gold; 2L -> colors.silver; 3L -> colors.bronze; else -> colors.textSecondary }
    val medalEmoji = when (entry.rank) { 1L -> "\uD83E\uDD47 "; 2L -> "\uD83E\uDD48 "; 3L -> "\uD83E\uDD49 "; else -> "" }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(if (isCurrentUser) colors.primary.copy(alpha = 0.15f) else colors.surface.copy(alpha = 0.8f))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "$medalEmoji${entry.rank}",
            fontSize = if (entry.rank <= 3) 20.sp else 16.sp,
            fontWeight = FontWeight.Bold,
            color = rankColor,
            modifier = Modifier.width(if (entry.rank <= 3) 56.dp else 40.dp),
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.width(12.dp))

        if (entry.avatarUrl != null) {
            AsyncImage(model = entry.avatarUrl, contentDescription = null, modifier = Modifier.size(40.dp).clip(CircleShape), contentScale = ContentScale.Crop)
        } else {
            Box(modifier = Modifier.size(40.dp).clip(CircleShape).background(colors.textTertiary.copy(alpha = 0.3f)), contentAlignment = Alignment.Center) {
                Text(entry.displayName.take(1).uppercase(), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = colors.textPrimary)
            }
        }

        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                entry.displayName.ifBlank { "익명" }, fontSize = 15.sp,
                fontWeight = if (isCurrentUser) FontWeight.Bold else FontWeight.Normal,
                color = if (isCurrentUser) colors.primary else colors.textPrimary,
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            if (isCurrentUser) {
                Text("나", fontSize = 12.sp, color = colors.primary.copy(alpha = 0.7f))
            }
        }

        Text(
            "${NumberFormat.getNumberInstance().format(entry.clickCount)}${unit}",
            fontSize = 17.sp, fontWeight = FontWeight.Bold,
            color = if (entry.rank <= 3) rankColor else colors.textPrimary
        )
    }
}

