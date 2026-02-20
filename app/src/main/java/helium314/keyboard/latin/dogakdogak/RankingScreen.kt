package helium314.keyboard.latin.dogakdogak

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.exifinterface.media.ExifInterface
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.Image
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.core.graphics.drawable.toBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.text.NumberFormat

/** 아바타 이미지 압축: EXIF 회전 자동 보정 + 최대 200x200, JPEG 품질 60 */
private fun compressAvatar(context: Context, uri: Uri): ByteArray? {
    return try {
        var bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val source = ImageDecoder.createSource(context.contentResolver, uri)
            ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                decoder.setTargetSampleSize(2)
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
        } else {
            val rotation = context.contentResolver.openInputStream(uri)?.use { stream ->
                val exif = ExifInterface(stream)
                when (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                    else -> 0f
                }
            } ?: 0f

            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, opts)
            }
            var sampleSize = 1
            while (opts.outWidth / sampleSize > 400 || opts.outHeight / sampleSize > 400) sampleSize *= 2
            val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
            var bmp = context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, decodeOpts)
            } ?: return null

            if (rotation != 0f) {
                val matrix = Matrix().apply { postRotate(rotation) }
                val rotated = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, matrix, true)
                if (rotated !== bmp) bmp.recycle()
                bmp = rotated
            }
            bmp
        }

        val maxSide = 200
        val scale = minOf(maxSide.toFloat() / bitmap.width, maxSide.toFloat() / bitmap.height, 1f)
        if (scale < 1f) {
            val scaled = Bitmap.createScaledBitmap(
                bitmap, (bitmap.width * scale).toInt(), (bitmap.height * scale).toInt(), true
            )
            if (scaled !== bitmap) bitmap.recycle()
            bitmap = scaled
        }

        if (bitmap.config == Bitmap.Config.HARDWARE) {
            val sw = bitmap.copy(Bitmap.Config.ARGB_8888, false)
            bitmap.recycle()
            bitmap = sw
        }

        val out = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 60, out)
        bitmap.recycle()
        out.toByteArray()
    } catch (e: Exception) {
        Log.e("dogakdogak", "compressAvatar failed", e)
        null
    }
}

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
    var showEditDialog by remember { mutableStateOf(false) }

    var currentDisplayName by remember { mutableStateOf(rankingRepository.getCurrentUserDisplayName()) }
    var currentAvatarUrl by remember { mutableStateOf(rankingRepository.getCurrentUserAvatarUrl()) }

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

    LaunchedEffect(isLoggedIn) {
        if (isLoggedIn) {
            scope.launch {
                rankingRepository.refreshProfile()
                // daily 데이터를 Supabase에 동기화
                val repo = ClickCountRepository.getInstance(context)
                rankingRepository.syncDailyClicks(repo.getDailyScoreValue())
                rankingRepository.syncDailyTouches(repo.getDailyTouchesValue())
                // 앱별 daily 데이터 동기화
                val appRepo = AppClickCountRepository.getInstance(context)
                rankingRepository.syncAppDailyClicks(appRepo.getAllDailyScores())
                rankingRepository.syncAppDailyTouches(appRepo.getAllDailyTouches())
                currentDisplayName = rankingRepository.getCurrentUserDisplayName()
                currentAvatarUrl = rankingRepository.getCurrentUserAvatarUrl()
            }
        } else {
            currentDisplayName = rankingRepository.getCurrentUserDisplayName()
            currentAvatarUrl = rankingRepository.getCurrentUserAvatarUrl()
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

                // 프로필 수정 섹션 (로그인 시에만 표시)
                if (isLoggedIn) {
                    Spacer(Modifier.height(12.dp))
                    ProfileSection(
                        displayName = currentDisplayName,
                        avatarUrl = currentAvatarUrl,
                        onEditClick = { showEditDialog = true }
                    )
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
                            modifier = Modifier.height(300.dp)
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

    // 프로필 수정 다이얼로그
    if (showEditDialog) {
        EditProfileDialog(
            rankingRepository = rankingRepository,
            currentDisplayName = currentDisplayName,
            currentAvatarUrl = currentAvatarUrl,
            onDismiss = { showEditDialog = false },
            onSaved = { newName, newAvatarUrl ->
                showEditDialog = false
                currentDisplayName = newName
                if (newAvatarUrl != null) currentAvatarUrl = newAvatarUrl
                toastMessage = "프로필이 업데이트되었어요"
                scope.launch {
                    rankings = if (rankingMode == 0) {
                        rankingRepository.getRanking(periods[selectedTab], forceRefresh = true)
                    } else {
                        rankingRepository.getTouchRanking(periods[selectedTab], forceRefresh = true)
                    }
                }
            }
        )
    }
}

@Composable
private fun ProfileSection(
    displayName: String,
    avatarUrl: String?,
    onEditClick: () -> Unit
) {
    val colors = LocalDogakdogakColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(colors.surface.copy(alpha = 0.8f))
            .border(0.5.dp, colors.glassBorder, RoundedCornerShape(16.dp))
            .clickable { onEditClick() }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (avatarUrl != null) {
            AsyncImage(
                model = avatarUrl,
                contentDescription = null,
                modifier = Modifier.size(44.dp).clip(CircleShape),
                contentScale = ContentScale.Crop
            )
        } else {
            Box(
                modifier = Modifier.size(44.dp).clip(CircleShape)
                    .background(colors.primary.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Text(displayName.take(1).uppercase(), fontSize = 18.sp, fontWeight = FontWeight.Bold, color = colors.primary)
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(displayName, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("프로필 수정", fontSize = 12.sp, color = colors.textTertiary)
        }
        IconButton(onClick = onEditClick) {
            Icon(Icons.Default.Edit, contentDescription = "프로필 수정", modifier = Modifier.size(20.dp), tint = colors.textTertiary)
        }
    }
}

@Composable
private fun EditProfileDialog(
    rankingRepository: RankingRepository,
    currentDisplayName: String,
    currentAvatarUrl: String?,
    onDismiss: () -> Unit,
    onSaved: (newName: String, newAvatarUrl: String?) -> Unit
) {
    val colors = LocalDogakdogakColors.current
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    var nickname by remember { mutableStateOf(currentDisplayName) }
    var avatarUrl by remember { mutableStateOf(currentAvatarUrl) }
    var isSaving by remember { mutableStateOf(false) }
    var isUploading by remember { mutableStateOf(false) }
    var newAvatarUrl by remember { mutableStateOf<String?>(null) }
    var dialogToast by remember { mutableStateOf<String?>(null) }

    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        isUploading = true
        scope.launch {
            try {
                val compressed = compressAvatar(context, uri)
                if (compressed != null) {
                    val uploadedUrl = rankingRepository.uploadAvatar(compressed)
                    if (uploadedUrl != null) {
                        newAvatarUrl = uploadedUrl
                        avatarUrl = uploadedUrl
                        dialogToast = "이미지 업로드 완료"
                    } else {
                        dialogToast = "이미지 업로드에 실패했어요"
                    }
                } else {
                    dialogToast = "이미지를 불러올 수 없어요"
                }
            } catch (e: Exception) {
                dialogToast = "이미지를 불러올 수 없어요"
            }
            isUploading = false
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Box(contentAlignment = Alignment.BottomCenter) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(colors.surface)
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("프로필 수정", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = colors.textPrimary)
                Spacer(Modifier.height(20.dp))

                // 아바타
                Box(
                    modifier = Modifier.size(80.dp).clip(CircleShape).clickable { imagePicker.launch("image/*") },
                    contentAlignment = Alignment.Center
                ) {
                    if (isUploading) {
                        CircularProgressIndicator(color = colors.primary, modifier = Modifier.size(40.dp))
                    } else if (avatarUrl != null) {
                        AsyncImage(model = avatarUrl, contentDescription = null, modifier = Modifier.size(80.dp).clip(CircleShape), contentScale = ContentScale.Crop)
                    } else {
                        Box(modifier = Modifier.size(80.dp).clip(CircleShape).background(colors.primary.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
                            Text(nickname.take(1).uppercase(), fontSize = 28.sp, fontWeight = FontWeight.Bold, color = colors.primary)
                        }
                    }
                    if (!isUploading) {
                        Box(
                            modifier = Modifier.align(Alignment.BottomEnd).size(28.dp).clip(CircleShape).background(colors.primary).border(2.dp, colors.surface, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.CameraAlt, contentDescription = "사진 변경", modifier = Modifier.size(14.dp), tint = Color.White)
                        }
                    }
                }

                Spacer(Modifier.height(4.dp))
                Text("사진을 눌러서 변경할 수 있어요", fontSize = 11.sp, color = colors.textTertiary)
                Spacer(Modifier.height(20.dp))

                Text("닉네임", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = colors.textSecondary, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    value = nickname,
                    onValueChange = { if (it.length <= 20) nickname = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = { Text("닉네임을 입력하세요", color = colors.textTertiary) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = colors.primary,
                        unfocusedBorderColor = colors.glassBorder,
                        cursorColor = colors.primary,
                        focusedTextColor = colors.textPrimary,
                        unfocusedTextColor = colors.textPrimary
                    ),
                    shape = RoundedCornerShape(12.dp)
                )
                Spacer(Modifier.height(6.dp))
                Text("${nickname.length}/20", fontSize = 11.sp, color = colors.textTertiary, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.End)
                Spacer(Modifier.height(16.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    TextButton(onClick = onDismiss, modifier = Modifier.weight(1f)) {
                        Text("취소", color = colors.textSecondary)
                    }
                    Button(
                        onClick = {
                            if (nickname.isBlank()) { dialogToast = "닉네임을 입력해주세요"; return@Button }
                            isSaving = true
                            scope.launch {
                                val success = rankingRepository.updateProfile(displayName = nickname.trim(), avatarUrl = newAvatarUrl)
                                isSaving = false
                                if (success) onSaved(nickname.trim(), newAvatarUrl) else dialogToast = "업데이트에 실패했어요"
                            }
                        },
                        modifier = Modifier.weight(1f),
                        enabled = !isSaving && !isUploading && nickname.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(containerColor = colors.primary, contentColor = Color.White),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(if (isSaving) "저장 중..." else "저장", fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            // 다이얼로그 내부 토스트
            AnimatedVisibility(
                visible = dialogToast != null,
                modifier = Modifier.padding(bottom = 8.dp),
                enter = slideInVertically { it } + fadeIn(),
                exit = slideOutVertically { it } + fadeOut()
            ) {
                dialogToast?.let { msg ->
                    LaunchedEffect(msg) { delay(2500); dialogToast = null }
                    Text(
                        text = msg, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 12.dp).clip(RoundedCornerShape(14.dp)).background(Color(0xE6222222)).padding(horizontal = 16.dp, vertical = 10.dp)
                    )
                }
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

