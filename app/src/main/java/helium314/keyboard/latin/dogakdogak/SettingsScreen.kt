package helium314.keyboard.latin.dogakdogak

import android.content.Context
import android.content.BroadcastReceiver
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.media.AudioManager
import android.view.inputmethod.InputMethodManager
import android.provider.Settings as AndroidSettings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColor
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import coil.compose.AsyncImage
import helium314.keyboard.keyboard.KeyboardSwitcher
import helium314.keyboard.latin.AudioAndHapticFeedbackManager
import helium314.keyboard.latin.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.NumberFormat
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DogakdogakSettingsScreen(
    prefs: SharedPreferences,
    onNavigateToKeyboardSettings: () -> Unit,
    rankingRepository: RankingRepository? = null,
    developerSuggestionSender: DeveloperSuggestionSender? = null,
    onSubmitDeveloperSuggestion: ((DeveloperSuggestionDraft, DeveloperSuggestionSender) -> Unit)? = null,
    onLogin: ((String) -> Unit)? = null,
    onLogout: (() -> Unit)? = null,
    onDeleteAccount: (() -> Unit)? = null,
) {
    val colors = LocalDogakdogakColors.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val audioEngine = AudioAndHapticFeedbackManager.getInstance().audioEngine

    val isLoggedIn = rankingRepository?.isLoggedIn?.collectAsState(initial = false)?.value ?: false
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showEditProfileDialog by remember { mutableStateOf(false) }
    var showRankingDisclosureInfo by remember { mutableStateOf(false) }
    var showSuggestionLoginRequired by remember { mutableStateOf(false) }
    var showSuggestionUnavailable by remember { mutableStateOf(false) }
    var showSuggestionComposer by remember { mutableStateOf(false) }
    var showOverlayToast by remember { mutableStateOf<AppThemeType?>(null) }
    var profileDisplayName by remember { mutableStateOf("익명") }
    var profileAvatarUrl by remember { mutableStateOf<String?>(null) }
    var disclosureAccepted by remember { mutableStateOf(hasRankingDisclosureConsent(prefs)) }
    var suggestionTitle by remember { mutableStateOf("") }
    var suggestionBody by remember { mutableStateOf("") }

    LaunchedEffect(isLoggedIn, rankingRepository) {
        if (isLoggedIn) {
            val repo = rankingRepository ?: return@LaunchedEffect
            repo.refreshProfile()
            profileDisplayName = repo.getCurrentUserDisplayName().ifBlank { "익명" }
            profileAvatarUrl = repo.getCurrentUserAvatarUrl()
        } else {
            profileDisplayName = "익명"; profileAvatarUrl = null; showEditProfileDialog = false
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("계정 삭제") },
            text = {
                Column {
                    Text("계정을 삭제하면 프로필, 랭킹 점수, 앱별 랭킹 통계, 구매 동기화 기록, 아바타가 영구적으로 삭제됩니다.\n\n정말 삭제하시겠습니까?")
                    TextButton(onClick = { openExternalUrl(context, PolicyLinks.ACCOUNT_DELETION_URL) }) {
                        Text("앱 밖에서 삭제 요청하기")
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showDeleteConfirm = false; onDeleteAccount?.invoke() }) { Text("삭제", color = colors.error) } },
            dismissButton = { TextButton(onClick = { showDeleteConfirm = false }) { Text("취소") } }
        )
    }

    if (showRankingDisclosureInfo) {
        RankingDisclosureInfoDialog(
            onDismissRequest = { showRankingDisclosureInfo = false }
        )
    }

    if (showSuggestionLoginRequired) {
        AlertDialog(
            onDismissRequest = { showSuggestionLoginRequired = false },
            title = { Text("로그인이 필요해요") },
            text = { Text("개발자에게 건의하기는 Google 또는 카카오 로그인 후 이용할 수 있어요.") },
            confirmButton = {
                TextButton(onClick = { showSuggestionLoginRequired = false }) {
                    Text("확인")
                }
            }
        )
    }

    if (showSuggestionUnavailable) {
        AlertDialog(
            onDismissRequest = { showSuggestionUnavailable = false },
            title = { Text("이메일 정보를 확인할 수 없어요") },
            text = { Text("로그인 계정 이메일을 읽지 못해 건의를 보낼 수 없어요. 다시 로그인한 뒤 시도해 주세요.") },
            confirmButton = {
                TextButton(onClick = { showSuggestionUnavailable = false }) {
                    Text("확인")
                }
            }
        )
    }

    if (showSuggestionComposer) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showSuggestionComposer = false },
            sheetState = sheetState,
            containerColor = colors.surface,
            contentColor = colors.textPrimary,
            tonalElevation = 0.dp,
            shape = RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp),
            dragHandle = null,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .imePadding()
                    .padding(horizontal = 24.dp, vertical = 12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .width(42.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(colors.cardBorder)
                )
                Spacer(Modifier.height(20.dp))
                Text(
                    text = "개발자에게 건의하기",
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.textPrimary
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "좋은 아이디어는 실제 기능으로 반영될 수 있어요.",
                    fontSize = 14.sp,
                    color = colors.textSecondary,
                    lineHeight = 20.sp
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = developerSuggestionSender?.email ?: "",
                    fontSize = 12.sp,
                    color = colors.textTertiary
                )
                Spacer(Modifier.height(20.dp))
                OutlinedTextField(
                    value = suggestionTitle,
                    onValueChange = { suggestionTitle = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("제목") },
                    singleLine = true,
                    shape = RoundedCornerShape(18.dp),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = colors.background,
                        unfocusedContainerColor = colors.background,
                        focusedIndicatorColor = colors.primary.copy(alpha = 0.45f),
                        unfocusedIndicatorColor = colors.cardBorder
                    )
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = suggestionBody,
                    onValueChange = { suggestionBody = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 180.dp),
                    label = { Text("내용") },
                    placeholder = { Text("개선되면 좋겠는 점을 편하게 적어주세요") },
                    shape = RoundedCornerShape(22.dp),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = colors.background,
                        unfocusedContainerColor = colors.background,
                        focusedIndicatorColor = colors.primary.copy(alpha = 0.45f),
                        unfocusedIndicatorColor = colors.cardBorder
                    )
                )
                Spacer(Modifier.height(20.dp))
                Button(
                    onClick = {
                        val sender = developerSuggestionSender ?: return@Button
                        onSubmitDeveloperSuggestion?.invoke(
                            DeveloperSuggestionDraft(
                                title = suggestionTitle.trim(),
                                content = suggestionBody.trim()
                            ),
                            sender
                        )
                        suggestionTitle = ""
                        suggestionBody = ""
                        showSuggestionComposer = false
                    },
                    enabled = suggestionTitle.isNotBlank() && suggestionBody.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.primary,
                        contentColor = colors.onPrimary
                    )
                ) {
                    Text("메일 앱에서 이어서 보내기", fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(10.dp))
                TextButton(
                    onClick = { showSuggestionComposer = false },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("닫기", color = colors.textSecondary)
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }

    if (showEditProfileDialog && rankingRepository != null) {
        EditProfileDialog(
            rankingRepository = rankingRepository, currentDisplayName = profileDisplayName, currentAvatarUrl = profileAvatarUrl,
            onDismiss = { showEditProfileDialog = false },
            onSaved = { _, _ ->
                showEditProfileDialog = false
                scope.launch { rankingRepository.refreshProfile(); profileDisplayName = rankingRepository.getCurrentUserDisplayName().ifBlank { "익명" }; profileAvatarUrl = rankingRepository.getCurrentUserAvatarUrl() }
            }
        )
    }

    var soundVolume by remember { mutableFloatStateOf(prefs.getFloat(PrefsKeys.VOLUME, 0.5f).coerceIn(0.1f, 0.9f)) }
    var soundInVibrate by remember { mutableStateOf(prefs.getBoolean(PrefsKeys.SOUND_IN_VIBRATE, true)) }
    var silentModeBehavior by remember {
        mutableStateOf(
            prefs.getString(PrefsKeys.SILENT_MODE_BEHAVIOR, null)
                ?: if (prefs.getBoolean(PrefsKeys.SOUND_IN_SILENT, true)) "sound_on" else "sound_off"
        )
    }

    // 현재 기기 벨소리 모드 감지
    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    var ringerMode by remember { mutableStateOf(audioManager.ringerMode) }
    DisposableEffect(Unit) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                if (intent?.action == AudioManager.RINGER_MODE_CHANGED_ACTION) {
                    ringerMode = audioManager.ringerMode
                }
            }
        }
        context.registerReceiver(receiver, IntentFilter(AudioManager.RINGER_MODE_CHANGED_ACTION))
        onDispose { context.unregisterReceiver(receiver) }
    }
    val savedTheme = prefs.getString(PrefsKeys.THEME, AppThemeType.MAISON.name) ?: AppThemeType.MAISON.name
    var currentTheme by remember { mutableStateOf(try { AppThemeType.valueOf(savedTheme) } catch (_: Exception) { AppThemeType.MAISON }) }
    val savedSwitchName = prefs.getString(PrefsKeys.SWITCH_TYPE, SwitchType.getDefaultSwitch().name) ?: SwitchType.getDefaultSwitch().name
    val currentSwitch = try { SwitchType.valueOf(savedSwitchName) } catch (_: Exception) { SwitchType.getDefaultSwitch() }
    val clickCountRepo = remember { ClickCountRepository.getInstance(context) }
    val totalScore by clickCountRepo.totalScore.collectAsState()
    val totalTouches by clickCountRepo.totalTouches.collectAsState()
    val dailyScore by clickCountRepo.dailyScore.collectAsState()
    val dailyTouches by clickCountRepo.dailyTouches.collectAsState()
    var counterMode by remember { mutableStateOf(prefs.getString(PrefsKeys.COUNTER_MODE, "score") ?: "score") }
    var spacebarTriviaEnabled by remember { mutableStateOf(prefs.getBoolean(PrefsKeys.SPACEBAR_TRIVIA_ENABLED, true)) }
    var imeEnabled by remember { mutableStateOf(isImeEnabled(context)) }
    var imeCurrent by remember { mutableStateOf(isImeSelected(context)) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) { imeEnabled = isImeEnabled(context); imeCurrent = isImeSelected(context) }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    // showInputMethodPicker()는 시스템 다이얼로그라 ON_RESUME이 안 불림 → 폴링으로 보완
    val serviceRunning = imeEnabled && imeCurrent
    LaunchedEffect(serviceRunning) {
        if (!serviceRunning) {
            while (true) {
                delay(500)
                imeEnabled = isImeEnabled(context)
                imeCurrent = isImeSelected(context)
            }
        }
    }

    // IME 상태 카드 — 비활성/미선택 시 상단 배치 + 글로우 이펙트
    val imeStatusCard: @Composable () -> Unit = {
        val glowColor = if (!imeEnabled) colors.error else colors.primary
        val infiniteTransition = rememberInfiniteTransition(label = "imeGlow")
        val animatedGlow by infiniteTransition.animateColor(
            initialValue = glowColor.copy(alpha = 0.2f),
            targetValue = glowColor.copy(alpha = 0.8f),
            animationSpec = infiniteRepeatable(
                animation = tween(1000, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ), label = "glowColor"
        )
        val cardModifier = if (!serviceRunning) {
            Modifier.border(2.dp, animatedGlow, RoundedCornerShape(20.dp))
        } else Modifier

        Box(modifier = cardModifier) {
            GlassCard {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    PulsingDot(color = if (serviceRunning) colors.success else colors.error)
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.Top) {
                            Text(when { serviceRunning -> "키보드 활성"; imeEnabled -> "키보드 미선택"; else -> "키보드 비활성" },
                                fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = colors.textPrimary,
                                modifier = Modifier.weight(1f))
                            if (!serviceRunning) {
                                Text(if (!imeEnabled) "STEP 1" else "STEP 2",
                                    fontSize = 11.sp, fontWeight = FontWeight.Bold, color = colors.primary,
                                    letterSpacing = (-0.5).sp)
                            }
                        }
                        Text(when { serviceRunning -> "도각도각 키보드가 동작 중이에요"; imeEnabled -> "기본 키보드로 선택해주세요"; else -> "입력 방법 설정에서 활성화해주세요" },
                            fontSize = 13.sp, color = colors.textSecondary)
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text("입력한 텍스트 내용은 저장하거나 전송하지 않아요.\n랭킹 참여 중에는 키보드 사용에 따라 점수/터치 수와 동의한 앱별 통계만 동기화됩니다.", fontSize = 12.sp, color = colors.textTertiary, lineHeight = 18.sp)
                Spacer(Modifier.height(12.dp))
                when {
                    !imeEnabled -> Button(onClick = { context.startActivity(Intent(AndroidSettings.ACTION_INPUT_METHOD_SETTINGS)) }, modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = colors.primary, contentColor = colors.onPrimary), shape = RoundedCornerShape(12.dp)) {
                        Text("키보드 활성화하기", fontWeight = FontWeight.SemiBold) }
                    !imeCurrent -> Button(onClick = { val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager; @Suppress("DEPRECATION") imm.showInputMethodPicker() },
                        modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = colors.primary, contentColor = colors.onPrimary), shape = RoundedCornerShape(12.dp)) {
                        Text("기본 키보드로 선택하기", fontWeight = FontWeight.SemiBold) }
                    else -> OutlinedButton(onClick = { context.startActivity(Intent(AndroidSettings.ACTION_INPUT_METHOD_SETTINGS)) }, modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.primary), shape = RoundedCornerShape(12.dp)) {
                        Text("입력 방법 설정 열기", fontWeight = FontWeight.SemiBold) }
                }
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 헤더
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Image(painter = painterResource(R.drawable.dogakdogak_icon), contentDescription = "도각도각",
                modifier = Modifier.size(40.dp).clip(CircleShape))
            Spacer(Modifier.width(10.dp))
            Column {
                Text("도각도각", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = colors.textPrimary)
                Text("ASMR 키보드 사운드", fontSize = 12.sp, color = colors.textSecondary)
            }
        }
        Spacer(Modifier.height(16.dp))

        // 키보드 비활성/미선택 시 → 최상단에 IME 상태 카드
        if (!serviceRunning) {
            imeStatusCard()
            Spacer(Modifier.height(16.dp))
        }

        // 로그인/프로필
        if (isLoggedIn) {
            if (!disclosureAccepted) {
                RankingDisclosureCard(
                    isAccepted = false,
                    onAccept = {
                        acceptRankingDisclosure(prefs)
                        AppClickCountRepository.getInstance(context).resetCurrentUserDailyData()
                        disclosureAccepted = true
                    }
                )
                Spacer(Modifier.height(12.dp))
            }
            GlassCard {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    val avatarUrl = profileAvatarUrl?.takeIf { it.isNotBlank() }
                    if (avatarUrl != null) {
                        AsyncImage(model = avatarUrl, contentDescription = null, modifier = Modifier.size(44.dp).clip(CircleShape), contentScale = ContentScale.Crop)
                    } else {
                        Box(modifier = Modifier.size(44.dp).clip(CircleShape).background(colors.primary.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
                            Text(profileDisplayName.take(1).uppercase(), fontSize = 18.sp, fontWeight = FontWeight.Bold, color = colors.primary)
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(profileDisplayName, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = colors.textPrimary)
                        Text("랭킹에 참여 중", fontSize = 13.sp, color = colors.textSecondary)
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { showEditProfileDialog = true }, modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.primary),
                        border = BorderStroke(1.dp, colors.primary.copy(alpha = 0.5f)), shape = RoundedCornerShape(12.dp)) {
                        Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp)); Text("프로필 수정", fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false)
                    }
                    OutlinedButton(onClick = { onLogout?.invoke() }, modifier = Modifier.width(96.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.error),
                        border = BorderStroke(1.dp, colors.error.copy(alpha = 0.5f)), shape = RoundedCornerShape(12.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 8.dp)) {
                        Text("로그아웃", fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false)
                    }
                }
            }
        } else {
            GlassCard {
                Text("랭킹 참여", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = colors.textPrimary)
                Spacer(Modifier.height(4.dp))
                Text("로그인하면 전세계 타이핑 랭킹에 참여할 수 있어요", fontSize = 13.sp, color = colors.textSecondary)
                Spacer(Modifier.height(12.dp))
                Button(onClick = { onLogin?.invoke("kakao") }, enabled = disclosureAccepted, modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFEE500), contentColor = Color(0xFF191919)), shape = RoundedCornerShape(12.dp)) {
                    Text("카카오로 로그인", fontWeight = FontWeight.SemiBold)
                }
                Spacer(Modifier.height(8.dp))
                Button(onClick = { onLogin?.invoke("google") }, enabled = disclosureAccepted, modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = if (colors.isDark) Color.White else colors.primary,
                        contentColor = if (colors.isDark) Color(0xFF1A1A1A) else Color.White), shape = RoundedCornerShape(12.dp)) {
                    Text("Google로 로그인", fontWeight = FontWeight.SemiBold)
                }
                Spacer(Modifier.height(12.dp))
                if (!disclosureAccepted) {
                    Text(
                        "아래 안내에 동의하면 로그인 버튼이 활성화됩니다.",
                        fontSize = 12.sp,
                        color = colors.textTertiary,
                        lineHeight = 18.sp
                    )
                    Spacer(Modifier.height(10.dp))
                    RankingDisclosureCard(
                        isAccepted = false,
                        onAccept = {
                            acceptRankingDisclosure(prefs)
                            AppClickCountRepository.getInstance(context).resetCurrentUserDailyData()
                            disclosureAccepted = true
                        }
                    )
                }
            }
        }
        Spacer(Modifier.height(16.dp))

        // 카운터 모드
        GlassCard {
            val isScoreMode = counterMode == "score"
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("score" to "Score", "touch" to "Touch").forEach { (mode, label) ->
                    val selected = counterMode == mode
                    Box(modifier = Modifier.weight(1f).clip(RoundedCornerShape(10.dp))
                        .then(if (selected) Modifier.border(1.5.dp, colors.primary, RoundedCornerShape(10.dp)) else Modifier.border(1.dp, colors.cardBorder, RoundedCornerShape(10.dp)))
                        .background(if (selected) colors.primary.copy(alpha = 0.08f) else Color.Transparent)
                        .clickable { counterMode = mode; prefs.edit().putString(PrefsKeys.COUNTER_MODE, mode).apply() }
                        .padding(vertical = 10.dp), contentAlignment = Alignment.Center) {
                        Text(label, fontSize = 13.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                            color = if (selected) colors.primary else colors.textSecondary)
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
                Text("${NumberFormat.getNumberInstance().format(if (isScoreMode) totalScore else totalTouches)}${if (isScoreMode) "점" else "회"}",
                    fontSize = 22.sp, fontWeight = FontWeight.Bold, color = colors.textPrimary)
                Text("오늘 ${NumberFormat.getNumberInstance().format(if (isScoreMode) dailyScore else dailyTouches)}${if (isScoreMode) "점" else "회"}",
                    fontSize = 13.sp, color = colors.textTertiary)
            }
        }
        Spacer(Modifier.height(16.dp))

        // 볼륨
        GlassCard {
            val displayLevel = (soundVolume * 10f).roundToInt().coerceIn(1, 9)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("타건음 볼륨", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = colors.textPrimary)
                when (ringerMode) {
                    AudioManager.RINGER_MODE_SILENT -> {
                        Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                            listOf("sound_on" to "소리ON", "sound_off" to "소리OFF", "vibrate_only" to "진동만").forEach { (value, label) ->
                                val selected = silentModeBehavior == value
                                Text(label, fontSize = 10.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (selected) colors.primary else colors.textTertiary,
                                    maxLines = 1,
                                    modifier = Modifier.clip(RoundedCornerShape(20.dp))
                                        .background(if (selected) colors.primary.copy(alpha = 0.15f) else colors.surface)
                                        .clickable {
                                            silentModeBehavior = value
                                            prefs.edit().putString(PrefsKeys.SILENT_MODE_BEHAVIOR, value).apply()
                                        }
                                        .padding(horizontal = 8.dp, vertical = 5.dp))
                            }
                        }
                    }
                    AudioManager.RINGER_MODE_VIBRATE -> {
                        val isOn = soundInVibrate
                        Row(modifier = Modifier.clip(RoundedCornerShape(20.dp)).background(if (isOn) colors.primary.copy(alpha = 0.15f) else colors.surface)
                            .clickable { soundInVibrate = !soundInVibrate; prefs.edit().putBoolean(PrefsKeys.SOUND_IN_VIBRATE, soundInVibrate).apply() }
                            .padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("진동모드시 소리", fontSize = 12.sp, color = if (isOn) colors.primary else colors.textTertiary)
                            Spacer(Modifier.width(6.dp))
                            Text(if (isOn) "ON" else "OFF", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if (isOn) colors.primary else colors.textTertiary)
                        }
                    }
                    // RINGER_MODE_NORMAL: 일반 모드에서는 토글 불필요
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                VolumeButton("-", displayLevel > 1, colors) {
                    val newVol = (soundVolume - 0.1f).coerceIn(0.1f, 0.9f); soundVolume = newVol
                    prefs.edit().putFloat(PrefsKeys.VOLUME, newVol).apply(); audioEngine?.volume = newVol; audioEngine?.playSwitchSound(currentSwitch)
                }
                Spacer(Modifier.width(24.dp))
                Text("$displayLevel", fontSize = 32.sp, fontWeight = FontWeight.Bold, color = colors.textPrimary)
                Spacer(Modifier.width(24.dp))
                VolumeButton("+", displayLevel < 9, colors) {
                    val newVol = (soundVolume + 0.1f).coerceIn(0.1f, 0.9f); soundVolume = newVol
                    prefs.edit().putFloat(PrefsKeys.VOLUME, newVol).apply(); audioEngine?.volume = newVol; audioEngine?.playSwitchSound(currentSwitch)
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                repeat(9) { index ->
                    Box(modifier = Modifier.weight(1f).height(6.dp).clip(RoundedCornerShape(3.dp))
                        .background(if (index < displayLevel) colors.primary else colors.primary.copy(alpha = 0.15f)))
                }
            }
        }
        Spacer(Modifier.height(16.dp))

        var currentKbTheme by remember { mutableStateOf(prefs.getString(PrefsKeys.THEME_COLORS, "dogakdogak_light") ?: "dogakdogak_light") }

        // 키보드 테마
        GlassCard {
            Text("키보드 테마", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = colors.textPrimary)
            Spacer(Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                data class KbThemeCard(val id: String, val label: String, val bg: Color, val key: Color, val accent: Color)
                listOf(
                    KbThemeCard("dogakdogak_light", "라이트", Color(0xFFE8E8E8), Color.White, Color(0xFFB76E79)),
                    KbThemeCard("dogakdogak_dark", "다크", Color(0xFF111111), Color(0xFF2C2C2C), Color(0xFFFF6B00)),
                ).forEach { card ->
                    val selected = currentKbTheme == card.id
                    Box(modifier = Modifier.weight(1f).clip(RoundedCornerShape(14.dp))
                        .border(if (selected) 2.dp else 0.5.dp, if (selected) card.accent else colors.cardBorder, RoundedCornerShape(14.dp))
                        .background(if (selected) card.accent.copy(alpha = 0.12f) else Color.Transparent)
                        .clickable { currentKbTheme = card.id; prefs.edit().putString(PrefsKeys.THEME_COLORS, card.id).putString(PrefsKeys.THEME_COLORS_NIGHT, card.id).apply(); KeyboardSwitcher.getInstance().setThemeNeedsReload() }
                        .padding(horizontal = 8.dp, vertical = 14.dp), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(modifier = Modifier.fillMaxWidth().height(28.dp).clip(RoundedCornerShape(6.dp)).background(card.bg).padding(4.dp), contentAlignment = Alignment.Center) {
                                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                    repeat(3) { Box(modifier = Modifier.size(width = 14.dp, height = 18.dp).clip(RoundedCornerShape(3.dp)).background(card.key)) }
                                    Box(modifier = Modifier.size(width = 14.dp, height = 18.dp).clip(RoundedCornerShape(3.dp)).background(card.accent))
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                            Text(card.label, fontSize = 13.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium, color = if (selected) card.accent else colors.textPrimary)
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(10.dp))

        // 앱 테마
        GlassCard {
            Text("앱 테마", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = colors.textPrimary)
            Spacer(Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    Triple(AppThemeType.MAISON, "MAISON" to "럭셔리", MaisonColors),
                    Triple(AppThemeType.FORGE, "FORGE" to "인더스트리얼", ForgeColors),
                    Triple(AppThemeType.BLACK, "BLACK" to "다크", BlackColors),
                ).forEach { (type, labelDesc, palette) ->
                    val selected = currentTheme == type
                    Box(modifier = Modifier.weight(1f).clip(RoundedCornerShape(14.dp))
                        .border(if (selected) 2.dp else 0.5.dp, if (selected) palette.primary else colors.cardBorder, RoundedCornerShape(14.dp))
                        .background(if (selected) palette.primary.copy(alpha = 0.12f) else Color.Transparent)
                        .clickable {
                            currentTheme = type
                            val kbColors = when (type) { AppThemeType.FORGE -> "dogakdogak_dark"; AppThemeType.BLACK -> "dogakdogak_black"; else -> "dogakdogak_light" }
                            currentKbTheme = kbColors
                            prefs.edit().putString(PrefsKeys.THEME, type.name).putString(PrefsKeys.THEME_COLORS, kbColors).putString(PrefsKeys.THEME_COLORS_NIGHT, kbColors).apply()
                            KeyboardSwitcher.getInstance().setThemeNeedsReload(); showOverlayToast = type
                        }.padding(horizontal = 8.dp, vertical = 14.dp), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                                listOf(palette.background, palette.primary, palette.secondary).forEach { c ->
                                    Box(modifier = Modifier.size(14.dp).clip(CircleShape).background(c).border(0.5.dp, colors.cardBorder, CircleShape))
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                            Text(labelDesc.first, fontSize = 13.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium, color = if (selected) palette.primary else colors.textPrimary)
                            Text(labelDesc.second, fontSize = 10.sp, color = colors.textSecondary)
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))

        // IME 상태 — 활성 상태일 때만 여기에 표시 (비활성/미선택은 상단에 배치됨)
        if (serviceRunning) {
            imeStatusCard()
            Spacer(Modifier.height(16.dp))
        }

        // 키보드 설정
        GlassCard {
            Text("키보드 설정", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = colors.textPrimary)
            Spacer(Modifier.height(4.dp)); Text("자동완성, 레이아웃, 사전 등", fontSize = 13.sp, color = colors.textSecondary)
            Spacer(Modifier.height(12.dp))
            OutlinedButton(onClick = onNavigateToKeyboardSettings, modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.primary),
                border = BorderStroke(1.dp, colors.primary.copy(alpha = 0.5f)), shape = RoundedCornerShape(12.dp)) {
                Text("키보드 상세 설정 열기", fontWeight = FontWeight.SemiBold) }
        }
        Spacer(Modifier.height(16.dp))

        GlassCard {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(colors.surface)
                    .padding(horizontal = 14.dp, vertical = spacebarTriviaRowVerticalPadding()),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    spacebarTriviaSubject(),
                    modifier = Modifier.weight(1f),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.textPrimary
                )
                Spacer(Modifier.width(12.dp))
                Switch(
                    checked = spacebarTriviaEnabled,
                    onCheckedChange = { enabled ->
                        spacebarTriviaEnabled = enabled
                        prefs.edit().putBoolean(PrefsKeys.SPACEBAR_TRIVIA_ENABLED, enabled).apply()
                        KeyboardSwitcher.getInstance().getMainKeyboardView()?.refreshTrivia()
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = colors.onPrimary,
                        checkedTrackColor = colors.primary,
                        uncheckedThumbColor = colors.textTertiary,
                        uncheckedTrackColor = colors.surface,
                        uncheckedBorderColor = colors.cardBorder
                    )
                )
            }
        }
        Spacer(Modifier.height(16.dp))

        // 배터리 최적화
        GlassCard {
            Text("배터리 최적화", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = colors.textPrimary)
            Spacer(Modifier.height(8.dp))
            Text("서비스가 백그라운드에서 종료되지 않도록\n설정 > 배터리 > 도각도각 키보드 > 제한 없음\n으로 설정해 주세요.", fontSize = 13.sp, color = colors.textSecondary, lineHeight = 20.sp)
        }
        Spacer(Modifier.height(16.dp))

        // 앱 정보
        GlassCard {
            Text("앱 정보", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = colors.textPrimary)
            Spacer(Modifier.height(8.dp)); Text("ASMR 도각도각 키보드 v1.0", fontSize = 13.sp, color = colors.textSecondary)
            Text("HeliBoard 기반 오픈소스 키보드", fontSize = 12.sp, color = colors.textTertiary)
            Spacer(Modifier.height(4.dp)); Text("GPL-3.0 License", fontSize = 11.sp, color = colors.textTertiary)
            Spacer(Modifier.height(4.dp))
            TextButton(
                onClick = { showRankingDisclosureInfo = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("랭킹 데이터 안내 보기")
            }
            TextButton(
                onClick = {
                    when {
                        !isLoggedIn -> showSuggestionLoginRequired = true
                        developerSuggestionSender == null || onSubmitDeveloperSuggestion == null -> showSuggestionUnavailable = true
                        else -> showSuggestionComposer = true
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("개발자에게 건의하기")
            }
            Text(
                text = developerSuggestionRewardDescription(),
                fontSize = 12.sp,
                color = colors.textTertiary,
                lineHeight = 18.sp,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
            )
        }

        if (isLoggedIn) {
            Spacer(Modifier.height(16.dp))
            TextButton(onClick = { showDeleteConfirm = true }, modifier = Modifier.fillMaxWidth()) {
                Text("계정 삭제", fontSize = 12.sp, color = colors.error.copy(alpha = 0.5f))
            }
        }
        Spacer(Modifier.height(32.dp))
    }

    // 테마 오버레이 연동 토스트
    AnimatedVisibility(visible = showOverlayToast != null, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 24.dp),
        enter = slideInVertically { it } + fadeIn(), exit = slideOutVertically { it } + fadeOut()) {
        val toastTheme = showOverlayToast
        val themeName = when (toastTheme) { AppThemeType.FORGE -> "FORGE"; AppThemeType.BLACK -> "BLACK"; else -> "MAISON" }
        val themeColor = when (toastTheme) { AppThemeType.FORGE -> ForgeColors.primary; AppThemeType.BLACK -> BlackColors.primary; else -> MaisonColors.primary }
        val overlayColor = when (toastTheme) { AppThemeType.FORGE -> 0xFFFF6B00.toInt(); AppThemeType.BLACK -> 0xFFFFFFFF.toInt(); else -> 0xFFB76E79.toInt() }
        Row(modifier = Modifier.padding(horizontal = 20.dp).clip(RoundedCornerShape(14.dp)).background(Color(0xE6222222)).padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Text("오버레이도 ${themeName}하게", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(8.dp))
            Text("예", color = when (toastTheme) { AppThemeType.BLACK -> Color.Black; else -> themeColor }, fontSize = 14.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(themeColor.copy(alpha = if (toastTheme == AppThemeType.BLACK) 0.9f else 0.15f))
                    .clickable { prefs.edit().putInt(PrefsKeys.OVERLAY_COLOR, overlayColor).apply(); showOverlayToast = null }.padding(horizontal = 14.dp, vertical = 6.dp))
        }
        LaunchedEffect(showOverlayToast) { if (showOverlayToast != null) { delay(5000); showOverlayToast = null } }
    }
    } // Box
}

@Composable
private fun VolumeButton(text: String, enabled: Boolean, colors: DogakdogakColors, onClick: () -> Unit) {
    Box(modifier = Modifier.size(44.dp).clip(CircleShape)
        .background(if (enabled) colors.primary.copy(alpha = 0.15f) else colors.surface)
        .clickable(enabled = enabled) { onClick() }, contentAlignment = Alignment.Center) {
        Text(text, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = if (enabled) colors.primary else colors.textTertiary)
    }
}

internal fun spacebarTriviaSubject(): String = "스페이스 상식 표시"

internal fun spacebarTriviaRowVerticalPadding(): Dp = 2.dp
