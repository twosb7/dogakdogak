package helium314.keyboard.latin.dogakdogak

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
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
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshState
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import helium314.keyboard.latin.R
import helium314.keyboard.latin.utils.DeviceProtectedUtils
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import java.text.NumberFormat

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RankingScreen(
    rankingRepository: RankingRepository,
    currentUserId: String?
) {
    val colors = LocalDogakdogakColors.current
    val context = LocalContext.current
    val prefs = remember(context) { DeviceProtectedUtils.getSharedPreferences(context) }
    val appTrackingAllowed = hasRankingDisclosureConsent(prefs)
    var rankingView by remember { mutableIntStateOf(if (appTrackingAllowed) 1 else 0) } // 0=전체 랭킹, 1=앱별 랭킹
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
    val initialTrackedApps = remember { AppClickCountRepository.getManagedTrackedApps(prefs) }
    var managedAppsList by remember { mutableStateOf(initialTrackedApps) }
    var hiddenSelfPackages by remember { mutableStateOf(AppClickCountRepository.getHiddenSelfPackages(prefs)) }
    var selectedPackageName by remember { mutableStateOf(initialTrackedApps.firstOrNull()?.packageName.orEmpty()) }
    var selectedAppTab by remember { mutableIntStateOf(0) }
    var appUserRankings by remember { mutableStateOf<List<RankingEntry>>(emptyList()) }
    var isAppLoading by remember { mutableStateOf(false) }
    var isAppRefreshing by remember { mutableStateOf(false) }
    var showAppManageSheet by remember { mutableStateOf(false) }

    val visibleTrackedApps = remember(managedAppsList, hiddenSelfPackages) {
        managedAppsList.filterNot { it.packageName in hiddenSelfPackages }
    }
    val selectedApp = visibleTrackedApps.firstOrNull { it.packageName == selectedPackageName } ?: visibleTrackedApps.firstOrNull()
    val visibleAppRankings = remember(appUserRankings, hiddenSelfPackages, selectedPackageName, currentUserId) {
        AppClickCountRepository.filterAppRankingEntries(
            entries = appUserRankings,
            currentUserId = currentUserId,
            hideSelfEnabled = selectedPackageName in hiddenSelfPackages
        )
    }

    val periods = RankingPeriod.entries

    // 랭킹 화면 방문 시 타임스탬프 기록 (백그라운드 동기화 7일 필터용)
    LaunchedEffect(Unit) {
        prefs.edit()
            .putLong(PrefsKeys.LAST_RANKING_VISIT, System.currentTimeMillis())
            .apply()
    }

    LaunchedEffect(appTrackingAllowed) {
        if (!appTrackingAllowed && rankingView == 1) {
            rankingView = 0
        }
    }

    LaunchedEffect(isLoggedIn, appTrackingAllowed) {
        if (isLoggedIn) {
            scope.launch {
                rankingRepository.refreshProfile()
                val counterMode = prefs.getString(PrefsKeys.COUNTER_MODE, "score") ?: "score"
                val repo = ClickCountRepository.getInstance(context)
                val appRepo = AppClickCountRepository.getInstance(context)
                if (counterMode == "score") {
                    rankingRepository.syncDailyClicks(repo.getDailyScoreValue())
                    if (appTrackingAllowed) {
                        rankingRepository.syncAppDailyClicks(appRepo.getAllDailyScores())
                    }
                } else {
                    rankingRepository.syncDailyTouches(repo.getDailyTouchesValue())
                    if (appTrackingAllowed) {
                        rankingRepository.syncAppDailyTouches(appRepo.getAllDailyTouches())
                    }
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

    LaunchedEffect(visibleTrackedApps) {
        if (visibleTrackedApps.isEmpty()) {
            selectedPackageName = ""
            appUserRankings = emptyList()
        } else if (visibleTrackedApps.none { it.packageName == selectedPackageName }) {
            selectedPackageName = visibleTrackedApps.first().packageName
        }
    }

    // 앱별 유저 랭킹 로드
    LaunchedEffect(rankingView, selectedPackageName, appRankingMode, selectedAppTab, appTrackingAllowed, visibleTrackedApps) {
        if (rankingView == 1 && appTrackingAllowed && selectedPackageName.isNotBlank()) {
            isAppLoading = true
            val pkg = selectedPackageName
            appUserRankings = if (appRankingMode == 0) {
                rankingRepository.getAppRanking(pkg, periods[selectedAppTab])
            } else {
                rankingRepository.getAppTouchRanking(pkg, periods[selectedAppTab])
            }
            lastUpdateTime = rankingRepository.getLastUpdateTime()
            isAppLoading = false
        } else if (rankingView != 1 || !appTrackingAllowed) {
            appUserRankings = emptyList()
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
                val rankingViews = if (appTrackingAllowed) {
                    listOf("앱별 랭킹" to 1, "전체 랭킹" to 0)
                } else {
                    listOf("전체 랭킹" to 0)
                }
                rankingViews.forEach { (label, index) ->
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
                    if (appTrackingAllowed && index == 1) Spacer(Modifier.width(6.dp))
                }
            }

            if (!appTrackingAllowed) {
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "앱별 랭킹은 로그인 동의 후 사용할 수 있습니다.",
                    fontSize = 12.sp,
                    color = colors.textSecondary,
                    modifier = Modifier.padding(horizontal = 24.dp)
                )
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
                } else {
                    RankingPullToRefreshBox(
                        isRefreshing = isRefreshing,
                        onRefresh = {
                            scope.launch {
                                isRefreshing = true
                                try {
                                    rankings = if (rankingMode == 0) {
                                        rankingRepository.getRanking(periods[selectedTab], forceRefresh = true)
                                    } else {
                                        rankingRepository.getTouchRanking(periods[selectedTab], forceRefresh = true)
                                    }
                                    lastUpdateTime = rankingRepository.getLastUpdateTime()
                                    toastMessage = "랭킹이 갱신됐어요"
                                } finally {
                                    isRefreshing = false
                                }
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    ) {
                        if (rankings.isEmpty()) {
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
                }
            } else {
                // === 앱별 랭킹 ===
                Spacer(Modifier.height(12.dp))

                // Score/Touch 토글 + 앱 드롭다운 (같은 행)
                var appDropdownExpanded by remember { mutableStateOf(false) }
                val dropdownScrollState = rememberScrollState()
                val appControlHeight = 40.dp
                val appControlSpacing = 8.dp

                Row(
                    modifier = Modifier
                        .padding(horizontal = 24.dp)
                        .fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(appControlSpacing)
                ) {
                    // Score/Touch 세그먼트
                    listOf("Score" to 0, "Touch" to 1).forEach { (label, index) ->
                        val selected = appRankingMode == index
                        Box(
                            modifier = Modifier
                                .height(appControlHeight)
                                .clip(RoundedCornerShape(10.dp))
                                .then(
                                    if (selected) Modifier.border(1.5.dp, colors.primary, RoundedCornerShape(10.dp))
                                    else Modifier.border(1.dp, colors.glassBorder, RoundedCornerShape(10.dp))
                                )
                                .clickable { appRankingMode = index }
                                .padding(horizontal = 16.dp),
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

                    // 앱 드롭다운
                    Box(modifier = Modifier.weight(1f)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .border(1.dp, colors.glassBorder, RoundedCornerShape(10.dp))
                                .clickable(enabled = visibleTrackedApps.isNotEmpty()) { appDropdownExpanded = true }
                                .height(appControlHeight)
                                .padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            selectedApp?.let {
                                TrackedAppIcon(app = it, size = 20.dp, cornerRadius = 4.dp)
                                Spacer(Modifier.width(6.dp))
                            }
                            Text(
                                text = selectedApp?.displayName ?: "앱 선택",
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
                            visibleTrackedApps.forEach { app ->
                                DropdownMenuItem(
                                    text = {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            TrackedAppIcon(app = app, size = 24.dp, cornerRadius = 5.dp)
                                            Spacer(Modifier.width(10.dp))
                                            Text(
                                                app.displayName,
                                                fontWeight = if (app.packageName == selectedPackageName) FontWeight.Bold else FontWeight.Normal,
                                                color = if (app.packageName == selectedPackageName) colors.primary else colors.textPrimary
                                            )
                                        }
                                    },
                                    onClick = {
                                        selectedPackageName = app.packageName
                                        appDropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    Button(
                        onClick = { showAppManageSheet = true },
                        modifier = Modifier.size(appControlHeight),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colors.primary.copy(alpha = 0.12f),
                            contentColor = colors.primary
                        ),
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
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
                } else {
                    RankingPullToRefreshBox(
                        isRefreshing = isAppRefreshing,
                        onRefresh = {
                            scope.launch {
                                isAppRefreshing = true
                                try {
                                    val pkg = selectedPackageName
                                    appUserRankings = if (appRankingMode == 0) {
                                        rankingRepository.getAppRanking(pkg, periods[selectedAppTab], forceRefresh = true)
                                    } else {
                                        rankingRepository.getAppTouchRanking(pkg, periods[selectedAppTab], forceRefresh = true)
                                    }
                                    lastUpdateTime = rankingRepository.getLastUpdateTime()
                                    toastMessage = "${selectedApp?.displayName ?: "앱"} 랭킹이 갱신됐어요"
                                } finally {
                                    isAppRefreshing = false
                                }
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    ) {
                        if (visibleTrackedApps.isEmpty()) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("표시할 앱이 없습니다", fontSize = 17.sp, color = colors.textSecondary)
                                    Spacer(Modifier.height(8.dp))
                                    Text("정리 버튼에서 숨긴 앱을 다시 켜보세요!", fontSize = 13.sp, color = colors.textTertiary)
                                }
                            }
                        } else if (visibleAppRankings.isEmpty()) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("아직 데이터가 없습니다", fontSize = 17.sp, color = colors.textSecondary)
                                    Spacer(Modifier.height(8.dp))
                                    Text("${selectedApp?.displayName ?: "이 앱"}에서 타이핑을 시작해보세요!", fontSize = 13.sp, color = colors.textTertiary)
                                }
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 16.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                itemsIndexed(visibleAppRankings) { _, entry ->
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
        }

        if (showAppManageSheet) {
            AppRankingManageSheet(
                managedApps = managedAppsList,
                hiddenSelfPackages = hiddenSelfPackages,
                onDismiss = { showAppManageSheet = false },
                onSave = { updatedApps, updatedHiddenSelfPackages ->
                    managedAppsList = updatedApps
                    hiddenSelfPackages = updatedHiddenSelfPackages
                    AppClickCountRepository.saveManagedTrackedApps(
                        prefs = prefs,
                        packageOrder = updatedApps.map { it.packageName }
                    )
                    AppClickCountRepository.saveHiddenSelfPackages(
                        prefs = prefs,
                        packageNames = updatedHiddenSelfPackages
                    )
                    toastMessage = "앱별 랭킹 구성이 저장됐어요"
                    showAppManageSheet = false
                }
            )
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
private fun TrackedAppIcon(
    app: TrackedAppMeta,
    size: androidx.compose.ui.unit.Dp,
    cornerRadius: androidx.compose.ui.unit.Dp
) {
    val colors = LocalDogakdogakColors.current
    if (app.iconRes != null) {
        Image(
            painter = painterResource(app.iconRes),
            contentDescription = null,
            modifier = Modifier.size(size).clip(RoundedCornerShape(cornerRadius)),
            contentScale = ContentScale.Crop
        )
    } else {
        Box(
            modifier = Modifier
                .size(size)
                .clip(RoundedCornerShape(cornerRadius))
                .background(colors.textTertiary.copy(alpha = 0.28f)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = app.displayName.take(1),
                fontSize = (size.value * 0.48f).sp,
                fontWeight = FontWeight.Bold,
                color = colors.textPrimary
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppRankingManageSheet(
    managedApps: List<TrackedAppMeta>,
    hiddenSelfPackages: Set<String>,
    onDismiss: () -> Unit,
    onSave: (List<TrackedAppMeta>, Set<String>) -> Unit,
) {
    val colors = LocalDogakdogakColors.current
    val sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var draftApps by remember(managedApps) { mutableStateOf(managedApps) }
    var draftHiddenSelfPackages by remember(hiddenSelfPackages) { mutableStateOf(hiddenSelfPackages) }
    val listState = rememberLazyListState()
    val reorderState = rememberReorderableLazyListState(listState) { from, to ->
        draftApps = draftApps.toMutableList().apply {
            add(to.index, removeAt(from.index))
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        sheetGesturesEnabled = false,
        containerColor = colors.surface,
        contentColor = colors.textPrimary,
        dragHandle = null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp)
        ) {
            Text("앱별 랭킹 정리", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = colors.textPrimary)
            Spacer(Modifier.height(4.dp))
            Text(
                "앱 순서를 드래그로 바꾸고, 원하면 특정 앱에서 내 랭킹을 숨길 수 있어요.",
                fontSize = 13.sp,
                color = colors.textSecondary,
                lineHeight = 19.sp
            )
            Spacer(Modifier.height(16.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("앱", modifier = Modifier.weight(1f), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = colors.textTertiary)
                Text("내 랭킹 숨김", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = colors.textTertiary)
                Spacer(Modifier.width(30.dp))
            }
            Spacer(Modifier.height(8.dp))

            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 440.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(draftApps, key = { it.packageName }) { app ->
                    ReorderableItem(
                        state = reorderState,
                        key = app.packageName
                    ) { dragging ->
                        val elevation by animateDpAsState(
                            targetValue = if (dragging) 6.dp else 0.dp,
                            animationSpec = spring(stiffness = 700f),
                            label = "app_ranking_manage_elevation"
                        )
                        Surface(
                            shadowElevation = elevation,
                            shape = RoundedCornerShape(18.dp),
                            color = colors.background
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(18.dp))
                                    .border(
                                        width = 1.dp,
                                        color = if (dragging) colors.primary.copy(alpha = 0.55f) else colors.glassBorder,
                                        shape = RoundedCornerShape(18.dp)
                                    )
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                TrackedAppIcon(app = app, size = 32.dp, cornerRadius = 8.dp)
                                Spacer(Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(app.displayName, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = colors.textPrimary)
                                }
                                Switch(
                                    checked = app.packageName in draftHiddenSelfPackages,
                                    onCheckedChange = { isChecked ->
                                        draftHiddenSelfPackages = if (isChecked) {
                                            draftHiddenSelfPackages + app.packageName
                                        } else {
                                            draftHiddenSelfPackages - app.packageName
                                        }
                                    }
                                )
                                Spacer(Modifier.width(6.dp))
                                Icon(
                                    painter = painterResource(R.drawable.ic_drag_indicator),
                                    contentDescription = null,
                                    modifier = Modifier
                                        .longPressDraggableHandle()
                                        .padding(start = 4.dp),
                                    tint = colors.textSecondary
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(18.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        draftApps = AppClickCountRepository.getTrackedApps()
                        draftHiddenSelfPackages = emptySet()
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.primary)
                ) {
                    Text("기본값", fontWeight = FontWeight.SemiBold)
                }
                Button(
                    onClick = { onSave(draftApps, draftHiddenSelfPackages) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = colors.primary, contentColor = colors.onPrimary)
                ) {
                    Text("적용", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RankingPullToRefreshBox(
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val pullToRefreshState = rememberPullToRefreshState()
    val haptic = LocalHapticFeedback.current
    var didHapticAtThreshold by remember { mutableStateOf(false) }
    val isReadyToRefresh = pullToRefreshState.distanceFraction >= 1f

    LaunchedEffect(isReadyToRefresh, isRefreshing) {
        if (isRefreshing) return@LaunchedEffect
        if (isReadyToRefresh && !didHapticAtThreshold) {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            didHapticAtThreshold = true
        } else if (!isReadyToRefresh) {
            didHapticAtThreshold = false
        }
    }

    PullToRefreshBox(
        state = pullToRefreshState,
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        modifier = modifier,
        indicator = {
            RankingPullToRefreshIndicator(
                state = pullToRefreshState,
                isRefreshing = isRefreshing,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .offset(y = (-12).dp)
            )
        },
        content = content
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RankingPullToRefreshIndicator(
    state: PullToRefreshState,
    isRefreshing: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = LocalDogakdogakColors.current
    val clampedProgress = state.distanceFraction.coerceIn(0f, 1.2f)
    val readyToRefresh = clampedProgress >= 1f

    val indicatorScale by animateFloatAsState(
        targetValue = if (isRefreshing) 1f else 0.85f + (clampedProgress.coerceAtMost(1f) * 0.25f),
        animationSpec = spring(stiffness = 600f),
        label = "ranking_refresh_scale"
    )
    val indicatorAlpha by animateFloatAsState(
        targetValue = if (isRefreshing) 1f else 0.45f + (clampedProgress.coerceAtMost(1f) * 0.55f),
        animationSpec = spring(stiffness = 700f),
        label = "ranking_refresh_alpha"
    )
    val arrowRotation by animateFloatAsState(
        targetValue = if (readyToRefresh) 180f else 0f,
        animationSpec = spring(stiffness = 700f),
        label = "ranking_refresh_arrow_rotation"
    )

    val statusText = when {
        isRefreshing -> "랭킹 새로고침 중..."
        readyToRefresh -> "놓아서 새로고침"
        else -> "당겨서 새로고침"
    }
    val accentColor = if (isRefreshing || readyToRefresh) colors.primary else colors.textSecondary

    Row(
        modifier = modifier
            .padding(top = 10.dp)
            .graphicsLayer {
                scaleX = indicatorScale
                scaleY = indicatorScale
                alpha = indicatorAlpha
            }
            .clip(RoundedCornerShape(14.dp))
            .background(colors.surface.copy(alpha = 0.92f))
            .border(
                width = 1.dp,
                color = if (isRefreshing || readyToRefresh) colors.primary.copy(alpha = 0.5f) else colors.glassBorder,
                shape = RoundedCornerShape(14.dp)
            )
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (isRefreshing) {
            CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                color = colors.primary,
                strokeWidth = 2.dp
            )
        } else {
            Icon(
                imageVector = Icons.Default.KeyboardArrowDown,
                contentDescription = null,
                modifier = Modifier
                    .size(18.dp)
                    .graphicsLayer { rotationZ = arrowRotation },
                tint = accentColor
            )
        }
        Text(
            text = statusText,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = accentColor
        )
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
