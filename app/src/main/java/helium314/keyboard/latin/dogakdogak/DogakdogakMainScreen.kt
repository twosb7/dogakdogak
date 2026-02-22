package helium314.keyboard.latin.dogakdogak

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import io.github.jan.supabase.gotrue.SessionStatus
import io.github.jan.supabase.gotrue.auth
import android.provider.Settings as AndroidSettings
import android.text.InputType
import android.view.Gravity
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
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
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Leaderboard
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import coil.compose.AsyncImage
import helium314.keyboard.keyboard.KeyboardSwitcher
import helium314.keyboard.latin.AudioAndHapticFeedbackManager
import helium314.keyboard.latin.R
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.NumberFormat
import kotlin.math.roundToInt

// ═══════════════════════════════════════════════════════════════════
//  DogakdogakMainScreen — 4탭 구조 (타건음 / 이펙트 / 랭킹 / 설정)
//  원본 도각도각 앱의 MainApp 디자인을 그대로 포팅
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
//  SoundScreen — 스위치 선택 + 미리듣기 바텀시트
// ═══════════════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SoundScreen(prefs: SharedPreferences, purchaseRepository: PurchaseRepository? = null) {
    val colors = LocalDogakdogakColors.current
    val audioEngine = AudioAndHapticFeedbackManager.getInstance().audioEngine
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val savedSwitchName = prefs.getString("dogakdogak_switch_type", SwitchType.getDefaultSwitch().name)
        ?: SwitchType.getDefaultSwitch().name
    var selectedSwitch by remember {
        mutableStateOf(
            try { SwitchType.valueOf(savedSwitchName) } catch (_: Exception) { SwitchType.getDefaultSwitch() }
        )
    }

    // 구매 상태
    val purchasedSwitches by (purchaseRepository?.purchasedSwitchesFlow ?: kotlinx.coroutines.flow.flowOf(emptySet<String>())).collectAsState(initial = emptySet())

    // 미리듣기 바텀시트 상태
    var previewSwitchType by remember { mutableStateOf<SwitchType?>(null) }
    // 구매 유도 토스트 상태 (미리듣기 완료 시 표시)
    var toastSwitchType by remember { mutableStateOf<SwitchType?>(null) }
    // 스위치 선택 시 미리듣기 Job
    var previewJob by remember { mutableStateOf<Job?>(null) }

    fun selectSwitch(sw: SwitchType) {
        selectedSwitch = sw
        audioEngine?.setCurrentSwitch(sw)
        prefs.edit().putString("dogakdogak_switch_type", sw.name).apply()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // -- 로고 헤더 --
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Image(
                    painter = painterResource(R.drawable.dogakdogak_icon),
                    contentDescription = null,
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                )
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("도각도각", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = colors.textPrimary)
                    Text("ASMR 키보드 사운드", fontSize = 12.sp, color = colors.textSecondary)
                }
            }
            Spacer(Modifier.height(20.dp))

            GlassCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "스위치 타입",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.textPrimary
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "(클릭 시 미리듣기)",
                        fontSize = 12.sp,
                        color = colors.textTertiary
                    )
                }
                Spacer(Modifier.height(12.dp))

                SwitchType.entries.forEach { switchType ->
                    val isSelected = switchType == selectedSwitch
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .border(
                                width = if (isSelected) 1.5.dp else 0.5.dp,
                                color = if (isSelected) colors.primary else colors.glassBorder,
                                shape = RoundedCornerShape(14.dp)
                            )
                            .background(
                                if (isSelected) colors.primary.copy(alpha = 0.12f)
                                else Color.Transparent
                            )
                            .clickable {
                                val isUnlocked = !switchType.isPremium || switchType.name in purchasedSwitches
                                if (isUnlocked) {
                                    selectSwitch(switchType)
                                    previewJob?.cancel()
                                    previewJob = scope.launch {
                                        repeat(7) { i ->
                                            audioEngine?.playSwitchSound(switchType)
                                            if (i < 6) delay(kotlin.random.Random.nextLong(150, 250))
                                        }
                                    }
                                } else {
                                    // 프리미엄: 미리듣기 바텀시트
                                    previewSwitchType = switchType
                                }
                            }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "${switchType.displayNameKo} (${switchType.displayName})",
                                    fontSize = 15.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) colors.primary else colors.textPrimary
                                )
                                if (switchType.isPremium && switchType.name !in purchasedSwitches) {
                                    Spacer(Modifier.width(6.dp))
                                    Icon(
                                        Icons.Default.Lock,
                                        contentDescription = "잠금",
                                        modifier = Modifier.size(14.dp),
                                        tint = colors.textTertiary
                                    )
                                }
                            }
                            Text(
                                text = switchType.description,
                                fontSize = 12.sp,
                                color = colors.textSecondary
                            )
                        }
                        if (isSelected) {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = "선택됨",
                                modifier = Modifier.size(20.dp),
                                tint = colors.primary
                            )
                        }
                    }
                }

                // 번들 구매 버튼
                val allPremiumPurchased = SwitchType.getPremiumSwitches().all {
                    purchasedSwitches.contains(it.name)
                }
                if (!allPremiumPurchased) {
                    Spacer(Modifier.height(12.dp))
                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                val activity = context as? androidx.activity.ComponentActivity ?: return@launch
                                purchaseRepository?.launchPurchase(
                                    activity,
                                    SwitchType.BUNDLE_PRODUCT_ID
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.primary),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("조약돌 전체 번들 (1,990원)", fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            Spacer(Modifier.height(32.dp))
        }

        // 구매 유도 토스트 ("소리가 마음에 들면 구매할래요?" + "좋아요" 버튼)
        AnimatedVisibility(
            visible = toastSwitchType != null,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 16.dp),
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut()
        ) {
            toastSwitchType?.let { switchType ->
                LaunchedEffect(switchType) {
                    delay(3000)
                    toastSwitchType = null
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0xE6222222))
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "소리가 마음에 들면 구매할래요?",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = "좋아요",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.onPrimary,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(colors.primary)
                            .clickable {
                                toastSwitchType = null
                                scope.launch {
                                    val activity = context as? androidx.activity.ComponentActivity ?: return@launch
                                    purchaseRepository?.launchPurchase(
                                        activity,
                                        switchType.productId ?: return@launch
                                    )
                                }
                            }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }
        }
    }

    // 미리듣기 바텀시트
    previewSwitchType?.let { switchType ->
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        val focusManager = LocalFocusManager.current

        BackHandler {
            focusManager.clearFocus()
            previewSwitchType = null
            toastSwitchType = switchType
        }

        ModalBottomSheet(
            onDismissRequest = {
                focusManager.clearFocus()
                previewSwitchType = null
                toastSwitchType = switchType
            },
            sheetState = sheetState,
            containerColor = colors.surface,
            contentColor = colors.textPrimary
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 32.dp)
            ) {
                Text(
                    text = "${switchType.displayNameKo} 미리듣기",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.textPrimary
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "아래에 타이핑해서 소리를 들어보세요",
                    fontSize = 13.sp,
                    color = colors.textSecondary
                )
                Spacer(Modifier.height(16.dp))

                val textPrimaryColor = colors.textPrimary.toArgb()
                val hintColor = colors.textTertiary.toArgb()
                AndroidView(
                    factory = { ctx ->
                        EditText(ctx).apply {
                            hint = "여기에 타이핑하세요..."
                            setHintTextColor(hintColor)
                            setTextColor(textPrimaryColor)
                            background = null
                            gravity = Gravity.TOP or Gravity.START
                            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
                            setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 16f)
                            val pad = (16 * resources.displayMetrics.density).toInt()
                            setPadding(pad, pad, pad, pad)
                            addTextChangedListener(object : android.text.TextWatcher {
                                private var prevLen = 0
                                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) { prevLen = s?.length ?: 0 }
                                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                                override fun afterTextChanged(s: android.text.Editable?) {
                                    val added = (s?.length ?: 0) - prevLen
                                    if (added > 0) repeat(added.coerceAtMost(3)) { audioEngine?.playSwitchSound(switchType) }
                                }
                            })
                            post { requestFocus() }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(150.dp)
                        .border(1.dp, colors.glassBorder, RoundedCornerShape(14.dp))
                        .clip(RoundedCornerShape(14.dp))
                )

                Spacer(Modifier.height(16.dp))

                // 구매 버튼
                Button(
                    onClick = {
                        focusManager.clearFocus()
                        previewSwitchType = null
                        scope.launch {
                            val activity = context as? androidx.activity.ComponentActivity ?: return@launch
                            purchaseRepository?.launchPurchase(
                                activity,
                                switchType.productId ?: return@launch
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.primary,
                        contentColor = colors.onPrimary
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("구매하기", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════
//  EffectsScreen — 콤보 이펙트 + 오버레이 설정
// ═══════════════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EffectsScreen(prefs: SharedPreferences, purchaseRepository: PurchaseRepository? = null) {
    val colors = LocalDogakdogakColors.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val hasPremiumEffects by (purchaseRepository?.hasPremiumEffectsFlow
        ?: kotlinx.coroutines.flow.flowOf(false)).collectAsState(initial = false)
    val hasCutiePinkEffects by (purchaseRepository?.hasCutiePinkEffectsFlow
        ?: kotlinx.coroutines.flow.flowOf(false)).collectAsState(initial = false)
    val hasChillEffects by (purchaseRepository?.hasChillEffectsFlow
        ?: kotlinx.coroutines.flow.flowOf(false)).collectAsState(initial = false)

    val audioEngine = AudioAndHapticFeedbackManager.getInstance().audioEngine

    // 콤보 이펙트 미리보기 바텀시트
    var showEffectPreview by remember { mutableStateOf(false) }

    // 이펙트 ON/OFF 상태 (구매자만 사용 가능, 기본 OFF)
    var premiumEffectsOn by remember { mutableStateOf(prefs.getBoolean("premium_effects_on", false)) }
    var cutiePinkEffectsOn by remember { mutableStateOf(prefs.getBoolean("bubble_effects_on", false)) }
    var chillEffectsOn by remember { mutableStateOf(prefs.getBoolean("chill_effects_on", false)) }

    // 최초 1회: 구매 이력 기반으로 이펙트 초기화
    LaunchedEffect(hasPremiumEffects, hasCutiePinkEffects, hasChillEffects) {
        if (!prefs.getBoolean("effects_initialized", false) && (hasPremiumEffects || hasCutiePinkEffects || hasChillEffects)) {
            val lastPurchased = prefs.getString("last_purchased_effect", null)
            when {
                lastPurchased == "chill" && hasChillEffects -> {
                    chillEffectsOn = true
                    prefs.edit().putBoolean("chill_effects_on", true).apply()
                }
                lastPurchased == "bubble" && hasCutiePinkEffects -> {
                    cutiePinkEffectsOn = true
                    prefs.edit().putBoolean("bubble_effects_on", true).apply()
                }
                hasPremiumEffects -> {
                    premiumEffectsOn = true
                    prefs.edit().putBoolean("premium_effects_on", true).apply()
                }
                hasCutiePinkEffects -> {
                    cutiePinkEffectsOn = true
                    prefs.edit().putBoolean("bubble_effects_on", true).apply()
                }
                hasChillEffects -> {
                    chillEffectsOn = true
                    prefs.edit().putBoolean("chill_effects_on", true).apply()
                }
            }
            prefs.edit().putBoolean("effects_initialized", true).apply()
        }
    }

    // 오버레이 설정 상태
    var overlayVisible by remember { mutableStateOf(prefs.getBoolean("dogakdogak_overlay_visible", false)) }
    var overlayTouch by remember { mutableStateOf(prefs.getBoolean("dogakdogak_overlay_touch", true)) }
    var overlayScale by remember { mutableFloatStateOf(prefs.getFloat("dogakdogak_overlay_scale", 1.0f)) }
    var overlayColor by remember { mutableIntStateOf(prefs.getInt("dogakdogak_overlay_color", 0xFFFF6B00.toInt())) }
    var overlayGranted by remember { mutableStateOf(AndroidSettings.canDrawOverlays(context)) }
    var overlayNudgeDismissed by remember { mutableStateOf(prefs.getBoolean("overlay_nudge_dismissed", false)) }

    // 타이핑 점수 (넛지 배너 조건용)
    val clickCountRepo = remember { ClickCountRepository.getInstance(context) }
    val totalScore by clickCountRepo.totalScore.collectAsState()

    // 오버레이 권한 상태 갱신 (설정 화면에서 돌아올 때)
    val effectsLifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(effectsLifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                overlayGranted = AndroidSettings.canDrawOverlays(context)
            }
        }
        effectsLifecycleOwner.lifecycle.addObserver(observer)
        onDispose { effectsLifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // 오버레이 권한 허용 시 자동 ON
    LaunchedEffect(overlayGranted) {
        if (overlayGranted && !overlayVisible) {
            overlayVisible = true
            prefs.edit().putBoolean("dogakdogak_overlay_visible", true).apply()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // -- 로고 헤더 --
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Image(
                painter = painterResource(R.drawable.dogakdogak_icon),
                contentDescription = null,
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
            )
            Spacer(Modifier.width(12.dp))
            Column {
                Text("도각도각", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = colors.textPrimary)
                Text("ASMR 키보드 사운드", fontSize = 12.sp, color = colors.textSecondary)
            }
        }
        Spacer(Modifier.height(20.dp))

        // -- 콤보 이펙트 통합 카드 --
        GlassCard {
            // 헤더: 제목 + 미리보기 버튼
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "콤보 이펙트",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.textPrimary
                )
                TextButton(
                    onClick = { showEffectPreview = true },
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                ) {
                    Text("미리보기 ▶", fontSize = 13.sp, color = colors.primary, fontWeight = FontWeight.SemiBold)
                }
            }

            Spacer(Modifier.height(12.dp))

            // ── 프리미엄 이펙트 행 ──
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("✦ ", fontSize = 13.sp, color = colors.primary)
                        Text(
                            text = "프리미엄",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.textPrimary
                        )
                        if (hasPremiumEffects) {
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = "보유",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.primary,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(colors.primary.copy(alpha = 0.15f))
                                    .padding(horizontal = 5.dp, vertical = 1.dp)
                            )
                        }
                    }
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "파티클 · 진동 · 랜덤 컬러",
                        fontSize = 12.sp,
                        color = colors.textTertiary
                    )
                }
                Spacer(Modifier.width(12.dp))
                if (hasPremiumEffects) {
                    Switch(
                        checked = premiumEffectsOn,
                        onCheckedChange = { on ->
                            premiumEffectsOn = on
                            val editor = prefs.edit().putBoolean("premium_effects_on", on)
                            if (on) { cutiePinkEffectsOn = false; chillEffectsOn = false; editor.putBoolean("bubble_effects_on", false).putBoolean("chill_effects_on", false) }
                            editor.apply()
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = colors.onPrimary,
                            checkedTrackColor = colors.primary,
                            uncheckedThumbColor = colors.textTertiary,
                            uncheckedTrackColor = colors.surface,
                            uncheckedBorderColor = colors.cardBorder
                        )
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .border(1.dp, colors.primary.copy(alpha = 0.5f), RoundedCornerShape(20.dp))
                            .clickable {
                                val activity = context as? androidx.activity.ComponentActivity ?: return@clickable
                                scope.launch {
                                    purchaseRepository?.launchPurchase(activity, SwitchType.PREMIUM_EFFECTS_PRODUCT_ID)
                                }
                            }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text("1,990원", fontSize = 12.sp, color = colors.primary, fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            Spacer(Modifier.height(10.dp))
            // 구분선
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(colors.cardBorder.copy(alpha = 0.5f))
            )
            Spacer(Modifier.height(10.dp))

            // ── 큐티핑크 이펙트 행 ──
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("🩷 ", fontSize = 13.sp)
                        Text(
                            text = "큐티핑크",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.textPrimary
                        )
                        if (hasCutiePinkEffects) {
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = "보유",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.primary,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(colors.primary.copy(alpha = 0.15f))
                                    .padding(horizontal = 5.dp, vertical = 1.dp)
                            )
                        }
                    }
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "통통 튀는 핑크 겅듀 'ㅁ'",
                        fontSize = 12.sp,
                        color = colors.textTertiary
                    )
                }
                Spacer(Modifier.width(12.dp))
                if (hasCutiePinkEffects) {
                    Switch(
                        checked = cutiePinkEffectsOn,
                        onCheckedChange = { on ->
                            cutiePinkEffectsOn = on
                            val editor = prefs.edit().putBoolean("bubble_effects_on", on)
                            if (on) { premiumEffectsOn = false; chillEffectsOn = false; editor.putBoolean("premium_effects_on", false).putBoolean("chill_effects_on", false) }
                            editor.apply()
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = colors.onPrimary,
                            checkedTrackColor = colors.primary,
                            uncheckedThumbColor = colors.textTertiary,
                            uncheckedTrackColor = colors.surface,
                            uncheckedBorderColor = colors.cardBorder
                        )
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .border(1.dp, colors.primary.copy(alpha = 0.5f), RoundedCornerShape(20.dp))
                            .clickable {
                                val activity = context as? androidx.activity.ComponentActivity ?: return@clickable
                                scope.launch {
                                    purchaseRepository?.launchPurchase(activity, SwitchType.CUTIE_PINK_EFFECTS_PRODUCT_ID)
                                }
                            }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text("1,990원", fontSize = 12.sp, color = colors.primary, fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            Spacer(Modifier.height(10.dp))
            // 구분선
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(colors.cardBorder.copy(alpha = 0.5f))
            )
            Spacer(Modifier.height(10.dp))

            // ── Chill 이펙트 행 ──
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("~ ", fontSize = 13.sp, color = Color(0xFF64D2FF))
                        Text(
                            text = "CHILL",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.textPrimary
                        )
                        if (hasChillEffects) {
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = "보유",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.primary,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(colors.primary.copy(alpha = 0.15f))
                                    .padding(horizontal = 5.dp, vertical = 1.dp)
                            )
                        }
                    }
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "차분한 그래디언트 플로우",
                        fontSize = 12.sp,
                        color = colors.textTertiary
                    )
                }
                Spacer(Modifier.width(12.dp))
                if (hasChillEffects) {
                    Switch(
                        checked = chillEffectsOn,
                        onCheckedChange = { on ->
                            chillEffectsOn = on
                            val editor = prefs.edit().putBoolean("chill_effects_on", on)
                            if (on) { premiumEffectsOn = false; cutiePinkEffectsOn = false; editor.putBoolean("premium_effects_on", false).putBoolean("bubble_effects_on", false) }
                            editor.apply()
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = colors.onPrimary,
                            checkedTrackColor = colors.primary,
                            uncheckedThumbColor = colors.textTertiary,
                            uncheckedTrackColor = colors.surface,
                            uncheckedBorderColor = colors.cardBorder
                        )
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .border(1.dp, colors.primary.copy(alpha = 0.5f), RoundedCornerShape(20.dp))
                            .clickable {
                                val activity = context as? androidx.activity.ComponentActivity ?: return@clickable
                                scope.launch {
                                    purchaseRepository?.launchPurchase(activity, SwitchType.CHILL_EFFECTS_PRODUCT_ID)
                                }
                            }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text("1,990원", fontSize = 12.sp, color = colors.primary, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // -- 오버레이 넛지 배너 (100번 이상 타이핑 + 오버레이 OFF + 미해제 상태) --
        if (!overlayVisible && totalScore >= 100 && !overlayNudgeDismissed) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(colors.primary.copy(alpha = 0.08f))
                    .border(1.dp, colors.primary.copy(alpha = 0.25f), RoundedCornerShape(16.dp))
                    .padding(14.dp)
            ) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = "🎮", fontSize = 18.sp)
                        Spacer(Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "콤보 카운터를 켜보세요!",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = colors.textPrimary
                            )
                            Text(
                                text = "타이핑할수록 콤보가 쌓여요",
                                fontSize = 12.sp,
                                color = colors.textSecondary
                            )
                        }
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .clickable {
                                    overlayNudgeDismissed = true
                                    prefs.edit().putBoolean("overlay_nudge_dismissed", true).apply()
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(text = "✕", fontSize = 14.sp, color = colors.textTertiary)
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Button(
                        onClick = {
                            if (overlayGranted) {
                                overlayVisible = true
                                prefs.edit().putBoolean("dogakdogak_overlay_visible", true).apply()
                            } else {
                                context.startActivity(
                                    Intent(
                                        AndroidSettings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                        android.net.Uri.parse("package:${context.packageName}")
                                    )
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colors.primary,
                            contentColor = colors.onPrimary
                        ),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("지금 켜기", fontWeight = FontWeight.SemiBold)
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }

        // -- 오버레이 카운터 토글 --
        GlassCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "오버레이 카운터",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.textPrimary
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "타이핑 시 화면에 클릭 수를 표시합니다",
                        fontSize = 13.sp,
                        color = colors.textSecondary
                    )
                }
                Spacer(Modifier.width(12.dp))
                Switch(
                    checked = overlayVisible,
                    onCheckedChange = { visible ->
                        overlayVisible = visible
                        prefs.edit().putBoolean("dogakdogak_overlay_visible", visible).apply()
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

        // -- 오버레이 터치 토글 --
        if (overlayVisible) {
            Spacer(Modifier.height(12.dp))
            GlassCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "오버레이 터치",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.textPrimary
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = if (overlayTouch) "터치/드래그 가능" else "터치 투과 (뒤 화면 터치됨)",
                            fontSize = 13.sp,
                            color = colors.textSecondary
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Switch(
                        checked = overlayTouch,
                        onCheckedChange = { enabled ->
                            overlayTouch = enabled
                            prefs.edit().putBoolean("dogakdogak_overlay_touch", enabled).apply()
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
        }

        // -- 오버레이 색상 설정 --
        if (overlayVisible) {
            Spacer(Modifier.height(16.dp))

            val initR = remember(overlayColor) { ((overlayColor shr 16) and 0xFF).toFloat() }
            val initG = remember(overlayColor) { ((overlayColor shr 8) and 0xFF).toFloat() }
            val initB = remember(overlayColor) { (overlayColor and 0xFF).toFloat() }
            var red by remember(overlayColor) { mutableFloatStateOf(initR) }
            var green by remember(overlayColor) { mutableFloatStateOf(initG) }
            var blue by remember(overlayColor) { mutableFloatStateOf(initB) }
            val previewColor = Color(red.toInt(), green.toInt(), blue.toInt())

            GlassCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "오버레이 색상",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.textPrimary
                    )
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(previewColor)
                            .border(1.dp, colors.cardBorder, CircleShape)
                    )
                }
                Spacer(Modifier.height(12.dp))

                val applyColor = {
                    @Suppress("USELESS_CAST")
                    val newColor = (0xFF shl 24) or (red.toInt() shl 16) or (green.toInt() shl 8) or blue.toInt()
                    overlayColor = newColor
                    prefs.edit().putInt("dogakdogak_overlay_color", newColor).apply()
                }

                ColorSliderRow(
                    label = "R", value = red, color = Color.Red,
                    onValueChange = { red = it; applyColor() },
                    onValueChangeFinished = {}
                )
                Spacer(Modifier.height(6.dp))
                ColorSliderRow(
                    label = "G", value = green, color = Color(0xFF00C853),
                    onValueChange = { green = it; applyColor() },
                    onValueChangeFinished = {}
                )
                Spacer(Modifier.height(6.dp))
                ColorSliderRow(
                    label = "B", value = blue, color = Color(0xFF2979FF),
                    onValueChange = { blue = it; applyColor() },
                    onValueChangeFinished = {}
                )

                Spacer(Modifier.height(8.dp))
                Text(
                    text = "#${"%02X".format(red.toInt())}${"%02X".format(green.toInt())}${"%02X".format(blue.toInt())}",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = colors.textTertiary
                )
            }
        }

        // -- 오버레이 크기 설정 --
        if (overlayVisible) {
            Spacer(Modifier.height(12.dp))
            var localScale by remember(overlayScale) { mutableFloatStateOf(overlayScale) }
            GlassCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "오버레이 크기",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.textPrimary
                    )
                    Text(
                        text = "${(localScale * 100).toInt()}%",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        color = colors.primary
                    )
                }
                Spacer(Modifier.height(8.dp))
                Slider(
                    value = localScale,
                    onValueChange = {
                        localScale = it
                        overlayScale = it
                        prefs.edit().putFloat("dogakdogak_overlay_scale", it).apply()
                    },
                    onValueChangeFinished = {},
                    valueRange = 0.5f..2.0f,
                    colors = SliderDefaults.colors(
                        thumbColor = colors.primary,
                        activeTrackColor = colors.primary,
                        inactiveTrackColor = colors.surface
                    )
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("50%", fontSize = 11.sp, color = colors.textTertiary)
                    Text("200%", fontSize = 11.sp, color = colors.textTertiary)
                }
            }
        }

        Spacer(Modifier.height(32.dp))
    }

    // 콤보 이펙트 미리보기 바텀시트
    if (showEffectPreview) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        val focusManager = LocalFocusManager.current

        BackHandler {
            focusManager.clearFocus()
            showEffectPreview = false
        }

        ModalBottomSheet(
            onDismissRequest = {
                focusManager.clearFocus()
                showEffectPreview = false
            },
            sheetState = sheetState,
            containerColor = colors.surface,
            contentColor = colors.textPrimary
        ) {
            // 0 = 프리미엄, 1 = 큐티핑크, 2 = CHILL
            var selectedPreview by remember { mutableIntStateOf(0) }

            // 시트 열릴 때의 원본 상태 저장 (닫을 때 복원용)
            val origPremiumPurchased = remember { prefs.getBoolean("premium_effects", false) }
            val origCutiePinkPurchased = remember { prefs.getBoolean("bubble_effects", false) }
            val origChillPurchased = remember { prefs.getBoolean("chill_effects", false) }
            val origPremiumOn = remember { prefs.getBoolean("premium_effects_on", false) }
            val origCutiePinkOn = remember { prefs.getBoolean("bubble_effects_on", false) }
            val origChillOn = remember { prefs.getBoolean("chill_effects_on", false) }

            // 선택한 이펙트를 임시로 활성화 (미리보기)
            LaunchedEffect(selectedPreview) {
                val editor = prefs.edit()
                    .putBoolean("premium_effects", selectedPreview == 0)
                    .putBoolean("premium_effects_on", selectedPreview == 0)
                    .putBoolean("bubble_effects", selectedPreview == 1)
                    .putBoolean("bubble_effects_on", selectedPreview == 1)
                    .putBoolean("chill_effects", selectedPreview == 2)
                    .putBoolean("chill_effects_on", selectedPreview == 2)
                editor.apply()
            }

            // 시트 닫힐 때 원본 상태 복원
            DisposableEffect(Unit) {
                onDispose {
                    prefs.edit()
                        .putBoolean("premium_effects", origPremiumPurchased)
                        .putBoolean("premium_effects_on", origPremiumOn)
                        .putBoolean("bubble_effects", origCutiePinkPurchased)
                        .putBoolean("bubble_effects_on", origCutiePinkOn)
                        .putBoolean("chill_effects", origChillPurchased)
                        .putBoolean("chill_effects_on", origChillOn)
                        .apply()
                }
            }

            val currentSwitch = remember {
                val name = prefs.getString("dogakdogak_switch_type", SwitchType.getDefaultSwitch().name)
                    ?: SwitchType.getDefaultSwitch().name
                try { SwitchType.valueOf(name) } catch (_: Exception) { SwitchType.getDefaultSwitch() }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 32.dp)
            ) {
                Text(
                    text = "콤보 이펙트 미리보기",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.textPrimary
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "이펙트를 선택하고 빠르게 타이핑하세요",
                    fontSize = 13.sp,
                    color = colors.textSecondary
                )
                Spacer(Modifier.height(14.dp))

                // 이펙트 선택 세그먼트
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(colors.cardBorder.copy(alpha = 0.25f))
                        .padding(3.dp),
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    listOf("✦  프리미엄", "🩷  핑크큐티", "~  CHILL").forEachIndexed { i, label ->
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (selectedPreview == i) colors.primary
                                    else Color.Transparent
                                )
                                .clickable { selectedPreview = i }
                                .padding(vertical = 9.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (selectedPreview == i) colors.onPrimary
                                        else colors.textSecondary
                            )
                        }
                    }
                }

                Spacer(Modifier.height(14.dp))

                val textPrimaryColor = colors.textPrimary.toArgb()
                val hintColor = colors.textTertiary.toArgb()
                var editTextRef by remember { mutableStateOf<EditText?>(null) }

                LaunchedEffect(editTextRef) {
                    val et = editTextRef ?: return@LaunchedEffect
                    delay(300) // 바텀시트 애니메이션 완료 대기
                    et.requestFocus()
                    val imm = et.context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                    imm.showSoftInput(et, InputMethodManager.SHOW_IMPLICIT)
                }

                AndroidView(
                    factory = { ctx ->
                        EditText(ctx).apply {
                            hint = "여기에 타이핑하세요..."
                            setHintTextColor(hintColor)
                            setTextColor(textPrimaryColor)
                            background = null
                            gravity = Gravity.TOP or Gravity.START
                            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
                            setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 16f)
                            val pad = (16 * resources.displayMetrics.density).toInt()
                            setPadding(pad, pad, pad, pad)
                            addTextChangedListener(object : android.text.TextWatcher {
                                private var prevLen = 0
                                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) { prevLen = s?.length ?: 0 }
                                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                                override fun afterTextChanged(s: android.text.Editable?) {
                                    val added = (s?.length ?: 0) - prevLen
                                    if (added > 0) repeat(added.coerceAtMost(3)) { audioEngine?.playSwitchSound(currentSwitch) }
                                }
                            })
                        }
                    },
                    update = { et -> editTextRef = et },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(130.dp)
                        .border(1.dp, colors.glassBorder, RoundedCornerShape(14.dp))
                        .clip(RoundedCornerShape(14.dp))
                )

                // 미보유 시 구매 버튼 (선택된 이펙트 기준)
                val needsPurchase = when (selectedPreview) {
                    0 -> !hasPremiumEffects
                    1 -> !hasCutiePinkEffects
                    else -> !hasChillEffects
                }
                val productId = when (selectedPreview) {
                    0 -> SwitchType.PREMIUM_EFFECTS_PRODUCT_ID
                    1 -> SwitchType.CUTIE_PINK_EFFECTS_PRODUCT_ID
                    else -> SwitchType.CHILL_EFFECTS_PRODUCT_ID
                }

                if (needsPurchase) {
                    Spacer(Modifier.height(14.dp))
                    Button(
                        onClick = {
                            focusManager.clearFocus()
                            showEffectPreview = false
                            val activity = context as? androidx.activity.ComponentActivity ?: return@Button
                            scope.launch {
                                purchaseRepository?.launchPurchase(activity, productId)
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colors.primary,
                            contentColor = colors.onPrimary
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("구매하기 (1,990원)", fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════
//  RankingScreen — 랭킹 (Score/Touch 탭 + 기간 탭)
// ═══════════════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
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

        // 헤더
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

        // Score/Touch 모드 선택 세그먼트
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
                            text = period,
                            fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Normal,
                            color = if (selectedTab == index) colors.primary else colors.textSecondary
                        )
                    }
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        // 빈 상태
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

// ═══════════════════════════════════════════════════════════════════
//  SettingsScreen — 설정 (원본 디자인 완전 매칭)
// ═══════════════════════════════════════════════════════════════════

@Composable
private fun DogakdogakSettingsScreen(
    prefs: SharedPreferences,
    onNavigateToKeyboardSettings: () -> Unit,
    rankingRepository: RankingRepository? = null,
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
    var showOverlayToast by remember { mutableStateOf<AppThemeType?>(null) }
    var profileDisplayName by remember { mutableStateOf("익명") }
    var profileAvatarUrl by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(isLoggedIn, rankingRepository) {
        if (isLoggedIn) {
            val repo = rankingRepository ?: return@LaunchedEffect
            repo.refreshProfile()
            profileDisplayName = repo.getCurrentUserDisplayName().ifBlank { "익명" }
            profileAvatarUrl = repo.getCurrentUserAvatarUrl()
        } else {
            profileDisplayName = "익명"
            profileAvatarUrl = null
            showEditProfileDialog = false
        }
    }

    if (showDeleteConfirm) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("계정 삭제") },
            text = { Text("계정을 삭제하면 모든 점수와 프로필 데이터가 영구적으로 삭제됩니다.\n\n정말 삭제하시겠습니까?") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    onDeleteAccount?.invoke()
                }) {
                    Text("삭제", color = LocalDogakdogakColors.current.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("취소")
                }
            }
        )
    }

    if (showEditProfileDialog && rankingRepository != null) {
        EditProfileDialog(
            rankingRepository = rankingRepository,
            currentDisplayName = profileDisplayName,
            currentAvatarUrl = profileAvatarUrl,
            onDismiss = { showEditProfileDialog = false },
            onSaved = { _, _ ->
                showEditProfileDialog = false
                scope.launch {
                    rankingRepository.refreshProfile()
                    profileDisplayName = rankingRepository.getCurrentUserDisplayName().ifBlank { "익명" }
                    profileAvatarUrl = rankingRepository.getCurrentUserAvatarUrl()
                }
            }
        )
    }

    // 볼륨 상태
    var soundVolume by remember {
        mutableFloatStateOf(prefs.getFloat("dogakdogak_volume", 0.5f).coerceIn(0.1f, 0.9f))
    }
    var soundInVibrate by remember { mutableStateOf(prefs.getBoolean("dogakdogak_sound_in_vibrate", false)) }

    // 테마 상태
    val savedTheme = prefs.getString("dogakdogak_theme", AppThemeType.MAISON.name)
        ?: AppThemeType.MAISON.name
    var currentTheme by remember {
        mutableStateOf(
            try { AppThemeType.valueOf(savedTheme) } catch (_: Exception) { AppThemeType.MAISON }
        )
    }

    // 스위치 (볼륨 미리듣기용)
    val savedSwitchName = prefs.getString("dogakdogak_switch_type", SwitchType.getDefaultSwitch().name)
        ?: SwitchType.getDefaultSwitch().name
    val currentSwitch = try { SwitchType.valueOf(savedSwitchName) } catch (_: Exception) { SwitchType.getDefaultSwitch() }

    // 카운터 상태 (ClickCountRepository StateFlow 연동)
    val clickCountRepo = remember { ClickCountRepository.getInstance(context) }
    val totalScore by clickCountRepo.totalScore.collectAsState()
    val totalTouches by clickCountRepo.totalTouches.collectAsState()
    val dailyScore by clickCountRepo.dailyScore.collectAsState()
    val dailyTouches by clickCountRepo.dailyTouches.collectAsState()

    // Score/Touch 카운터 모드
    var counterMode by remember {
        mutableStateOf(prefs.getString("dogakdogak_counter_mode", "score") ?: "score")
    }

    // IME 상태 (onResume마다 갱신)
    var imeEnabled by remember { mutableStateOf(isImeEnabled(context)) }
    var imeCurrent by remember { mutableStateOf(isImeSelected(context)) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                imeEnabled = isImeEnabled(context)
                imeCurrent = isImeSelected(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Box(modifier = Modifier.fillMaxSize()) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // -- 헤더 --
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Image(
                painter = painterResource(R.drawable.dogakdogak_icon),
                contentDescription = "도각도각",
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
            )
            Spacer(Modifier.width(10.dp))
            Column {
                Text(
                    text = "도각도각",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.textPrimary
                )
                Text(
                    text = "ASMR 키보드 사운드",
                    fontSize = 12.sp,
                    color = colors.textSecondary
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        // -- 로그인/로그아웃 카드 --
        if (isLoggedIn) {
            val displayName = profileDisplayName.ifBlank { "익명" }
            val avatarUrl = profileAvatarUrl?.takeIf { it.isNotBlank() }
            GlassCard {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (avatarUrl != null) {
                        AsyncImage(
                            model = avatarUrl,
                            contentDescription = null,
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(colors.primary.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                displayName.take(1).uppercase(),
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.primary
                            )
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(displayName, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = colors.textPrimary)
                        Text("랭킹에 참여 중", fontSize = 13.sp, color = colors.textSecondary)
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { showEditProfileDialog = true },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.primary),
                        border = BorderStroke(1.dp, colors.primary.copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text("프로필 수정", fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false)
                    }
                    OutlinedButton(
                        onClick = { onLogout?.invoke() },
                        modifier = Modifier.width(96.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.error),
                        border = BorderStroke(1.dp, colors.error.copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 8.dp)
                    ) {
                        Text("로그아웃", fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false)
                    }
                }
            }
        } else {
            GlassCard {
                Text(
                    text = "랭킹 참여",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.textPrimary
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "로그인하면 전세계 타이핑 랭킹에 참여할 수 있어요",
                    fontSize = 13.sp,
                    color = colors.textSecondary
                )
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = { onLogin?.invoke("kakao") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFFEE500),
                        contentColor = Color(0xFF191919)
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("카카오로 로그인", fontWeight = FontWeight.SemiBold)
                }
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = { onLogin?.invoke("google") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (colors.isDark) Color.White else colors.primary,
                        contentColor = if (colors.isDark) Color(0xFF1A1A1A) else Color.White
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Google로 로그인", fontWeight = FontWeight.SemiBold)
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // -- 카운터 모드 + 누적 카운터 --
        GlassCard {
            val isScoreMode = counterMode == "score"

            // 모드 선택 버튼
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf("score" to "Score" , "touch" to "Touch").forEach { (mode, label) ->
                    val selected = counterMode == mode
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .then(
                                if (selected) Modifier.border(1.5.dp, colors.primary, RoundedCornerShape(10.dp))
                                else Modifier.border(1.dp, colors.cardBorder, RoundedCornerShape(10.dp))
                            )
                            .background(if (selected) colors.primary.copy(alpha = 0.08f) else Color.Transparent)
                            .clickable {
                                counterMode = mode
                                prefs.edit().putString("dogakdogak_counter_mode", mode).apply()
                            }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = label,
                            fontSize = 13.sp,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                            color = if (selected) colors.primary else colors.textSecondary
                        )
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            // 누적 카운터
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Text(
                    text = "${NumberFormat.getNumberInstance().format(if (isScoreMode) totalScore else totalTouches)}${if (isScoreMode) "점" else "회"}",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.textPrimary
                )
                Text(
                    text = "오늘 ${NumberFormat.getNumberInstance().format(if (isScoreMode) dailyScore else dailyTouches)}${if (isScoreMode) "점" else "회"}",
                    fontSize = 13.sp,
                    color = colors.textTertiary
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        // -- 타건음 볼륨 설정 (원본 디자인: +/- 버튼 + 9칸 바) --
        GlassCard {
            val displayLevel = (soundVolume * 10f).roundToInt().coerceIn(1, 9)
            val canDecrease = displayLevel > 1
            val canIncrease = displayLevel < 9

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "타건음 볼륨",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.textPrimary
                )
                // 진동 모드 타건음 토글
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(
                            if (soundInVibrate) colors.primary.copy(alpha = 0.15f)
                            else colors.surface
                        )
                        .clickable {
                            soundInVibrate = !soundInVibrate
                            prefs.edit().putBoolean("dogakdogak_sound_in_vibrate", soundInVibrate).apply()
                        }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "진동모드시 소리",
                        fontSize = 12.sp,
                        color = if (soundInVibrate) colors.primary else colors.textTertiary
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = if (soundInVibrate) "ON" else "OFF",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (soundInVibrate) colors.primary else colors.textTertiary
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // − 버튼
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(
                            if (canDecrease) colors.primary.copy(alpha = 0.15f)
                            else colors.surface
                        )
                        .clickable(enabled = canDecrease) {
                            val newVol = (soundVolume - 0.1f).coerceIn(0.1f, 0.9f)
                            soundVolume = newVol
                            prefs.edit().putFloat("dogakdogak_volume", newVol).apply()
                            audioEngine?.volume = newVol
                            audioEngine?.playSwitchSound(currentSwitch)
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "−",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (canDecrease) colors.primary else colors.textTertiary
                    )
                }

                Spacer(Modifier.width(24.dp))

                // 볼륨 숫자
                Text(
                    text = "$displayLevel",
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.textPrimary
                )

                Spacer(Modifier.width(24.dp))

                // + 버튼
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(
                            if (canIncrease) colors.primary.copy(alpha = 0.15f)
                            else colors.surface
                        )
                        .clickable(enabled = canIncrease) {
                            val newVol = (soundVolume + 0.1f).coerceIn(0.1f, 0.9f)
                            soundVolume = newVol
                            prefs.edit().putFloat("dogakdogak_volume", newVol).apply()
                            audioEngine?.volume = newVol
                            audioEngine?.playSwitchSound(currentSwitch)
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "+",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (canIncrease) colors.primary else colors.textTertiary
                    )
                }
            }

            Spacer(Modifier.height(10.dp))

            // 볼륨 바 (9칸)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                repeat(9) { index ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(
                                if (index < displayLevel) colors.primary
                                else colors.primary.copy(alpha = 0.15f)
                            )
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        var currentKbTheme by remember {
            mutableStateOf(prefs.getString("theme_colors", "dogakdogak_light") ?: "dogakdogak_light")
        }

        // -- 앱 테마 선택 카드 --
        GlassCard {
            Text(
                text = "앱 테마",
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                color = colors.textPrimary
            )
            Spacer(Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                data class AppThemeCard(
                    val type: AppThemeType,
                    val label: String,
                    val desc: String,
                    val palette: DogakdogakColors,
                )
                val themeCards = listOf(
                    AppThemeCard(AppThemeType.MAISON, "MAISON", "럭셔리", MaisonColors),
                    AppThemeCard(AppThemeType.FORGE, "FORGE", "인더스트리얼", ForgeColors),
                    AppThemeCard(AppThemeType.BLACK, "BLACK", "다크", BlackColors),
                )
                themeCards.forEach { card ->
                    val selected = currentTheme == card.type
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(14.dp))
                            .border(
                                width = if (selected) 2.dp else 0.5.dp,
                                color = if (selected) card.palette.primary else colors.cardBorder,
                                shape = RoundedCornerShape(14.dp)
                            )
                            .background(
                                if (selected) card.palette.primary.copy(alpha = 0.12f)
                                else Color.Transparent
                            )
                            .clickable {
                                currentTheme = card.type
                                val kbColors = when (card.type) {
                                    AppThemeType.FORGE -> "dogakdogak_dark"
                                    AppThemeType.BLACK -> "dogakdogak_black"
                                    else -> "dogakdogak_light"
                                }
                                currentKbTheme = kbColors
                                prefs.edit()
                                    .putString("dogakdogak_theme", card.type.name)
                                    .putString("theme_colors", kbColors)
                                    .putString("theme_colors_night", kbColors)
                                    .apply()
                                KeyboardSwitcher.getInstance().setThemeNeedsReload()
                                showOverlayToast = card.type
                            }
                            .padding(horizontal = 8.dp, vertical = 14.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                                listOf(
                                    card.palette.background,
                                    card.palette.primary,
                                    card.palette.secondary
                                ).forEach { c ->
                                    Box(
                                        modifier = Modifier
                                            .size(14.dp)
                                            .clip(CircleShape)
                                            .background(c)
                                            .border(0.5.dp, colors.cardBorder, CircleShape)
                                    )
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = card.label,
                                fontSize = 13.sp,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                color = if (selected) card.palette.primary else colors.textPrimary
                            )
                            Text(
                                text = card.desc,
                                fontSize = 10.sp,
                                color = colors.textSecondary
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        // -- 키보드 테마 선택 카드 --
        GlassCard {
            Text(
                text = "키보드 테마",
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                color = colors.textPrimary
            )
            Spacer(Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                data class KbThemeCard(
                    val id: String,
                    val label: String,
                    val bg: Color,
                    val key: Color,
                    val accent: Color,
                )
                val kbCards = listOf(
                    KbThemeCard("dogakdogak_light", "라이트", Color(0xFFE8E8E8), Color.White, Color(0xFFB76E79)),
                    KbThemeCard("dogakdogak_dark", "다크", Color(0xFF111111), Color(0xFF2C2C2C), Color(0xFFFF6B00)),
                )
                kbCards.forEach { card ->
                    val selected = currentKbTheme == card.id
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(14.dp))
                            .border(
                                width = if (selected) 2.dp else 0.5.dp,
                                color = if (selected) card.accent else colors.cardBorder,
                                shape = RoundedCornerShape(14.dp)
                            )
                            .background(
                                if (selected) card.accent.copy(alpha = 0.12f)
                                else Color.Transparent
                            )
                            .clickable {
                                currentKbTheme = card.id
                                prefs.edit()
                                    .putString("theme_colors", card.id)
                                    .putString("theme_colors_night", card.id)
                                    .apply()
                                KeyboardSwitcher.getInstance().setThemeNeedsReload()
                            }
                            .padding(horizontal = 8.dp, vertical = 14.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            // 미니 키보드 프리뷰
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(28.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(card.bg)
                                    .padding(4.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                    repeat(3) {
                                        Box(
                                            modifier = Modifier
                                                .size(width = 14.dp, height = 18.dp)
                                                .clip(RoundedCornerShape(3.dp))
                                                .background(card.key)
                                        )
                                    }
                                    Box(
                                        modifier = Modifier
                                            .size(width = 14.dp, height = 18.dp)
                                            .clip(RoundedCornerShape(3.dp))
                                            .background(card.accent)
                                    )
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = card.label,
                                fontSize = 13.sp,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                color = if (selected) card.accent else colors.textPrimary
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // -- IME 상태 카드 --
        val serviceRunning = imeEnabled && imeCurrent
        GlassCard {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                PulsingDot(
                    color = if (serviceRunning) colors.success else colors.error
                )
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = when {
                            serviceRunning -> "키보드 활성"
                            imeEnabled -> "키보드 미선택"
                            else -> "키보드 비활성"
                        },
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.textPrimary
                    )
                    Text(
                        text = when {
                            serviceRunning -> "도각도각 키보드가 동작 중이에요"
                            imeEnabled -> "기본 키보드로 선택해주세요"
                            else -> "입력 방법 설정에서 활성화해주세요"
                        },
                        fontSize = 13.sp,
                        color = colors.textSecondary
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            // 안심 메시지
            Text(
                text = "도각도각은 오직 타건 효과를 위해서만 작동하며,\n입력 내용을 저장하거나 전송하지 않아요.",
                fontSize = 12.sp,
                color = colors.textTertiary,
                lineHeight = 18.sp
            )

            Spacer(Modifier.height(12.dp))

            when {
                !imeEnabled -> {
                    Button(
                        onClick = {
                            context.startActivity(Intent(AndroidSettings.ACTION_INPUT_METHOD_SETTINGS))
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colors.primary,
                            contentColor = colors.onPrimary
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("키보드 활성화하기", fontWeight = FontWeight.SemiBold)
                    }
                }
                !imeCurrent -> {
                    Button(
                        onClick = {
                            val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                            @Suppress("DEPRECATION")
                            imm.showInputMethodPicker()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colors.primary,
                            contentColor = colors.onPrimary
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("기본 키보드로 선택하기", fontWeight = FontWeight.SemiBold)
                    }
                }
                else -> {
                    OutlinedButton(
                        onClick = {
                            context.startActivity(Intent(AndroidSettings.ACTION_INPUT_METHOD_SETTINGS))
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.primary),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("입력 방법 설정 열기", fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // -- 키보드 설정 (HeliBoard) --
        GlassCard {
            Text(
                text = "키보드 설정",
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                color = colors.textPrimary
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "자동완성, 레이아웃, 사전 등",
                fontSize = 13.sp,
                color = colors.textSecondary
            )
            Spacer(Modifier.height(12.dp))
            OutlinedButton(
                onClick = onNavigateToKeyboardSettings,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.primary),
                border = BorderStroke(1.dp, colors.primary.copy(alpha = 0.5f)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("키보드 상세 설정 열기", fontWeight = FontWeight.SemiBold)
            }
        }

        Spacer(Modifier.height(16.dp))

        // -- 배터리 최적화 안내 --
        GlassCard {
            Text(
                text = "배터리 최적화",
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                color = colors.textPrimary
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "서비스가 백그라운드에서 종료되지 않도록\n설정 > 배터리 > 도각도각 키보드 > 제한 없음\n으로 설정해 주세요.",
                fontSize = 13.sp,
                color = colors.textSecondary,
                lineHeight = 20.sp
            )
        }

        Spacer(Modifier.height(16.dp))

        // -- 앱 정보 --
        GlassCard {
            Text(
                text = "앱 정보",
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                color = colors.textPrimary
            )
            Spacer(Modifier.height(8.dp))
            Text("ASMR 도각도각 키보드 v1.0", fontSize = 13.sp, color = colors.textSecondary)
            Text("HeliBoard 기반 오픈소스 키보드", fontSize = 12.sp, color = colors.textTertiary)
            Spacer(Modifier.height(4.dp))
            Text("GPL-3.0 License", fontSize = 11.sp, color = colors.textTertiary)
        }

        // -- 계정 삭제 (맨 하단) --
        if (isLoggedIn) {
            Spacer(Modifier.height(16.dp))
            TextButton(
                onClick = { showDeleteConfirm = true },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    "계정 삭제",
                    fontSize = 12.sp,
                    color = colors.error.copy(alpha = 0.5f)
                )
            }
        }

        Spacer(Modifier.height(32.dp))
    }

    // 테마 오버레이 연동 토스트
    AnimatedVisibility(
        visible = showOverlayToast != null,
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .padding(bottom = 24.dp),
        enter = slideInVertically { it } + fadeIn(),
        exit = slideOutVertically { it } + fadeOut()
    ) {
        val toastTheme = showOverlayToast
        val themeName = when (toastTheme) {
            AppThemeType.FORGE -> "FORGE"
            AppThemeType.BLACK -> "BLACK"
            else -> "MAISON"
        }
        val themeColor = when (toastTheme) {
            AppThemeType.FORGE -> ForgeColors.primary
            AppThemeType.BLACK -> BlackColors.primary
            else -> MaisonColors.primary
        }
        val overlayColor = when (toastTheme) {
            AppThemeType.FORGE -> 0xFFFF6B00.toInt()
            AppThemeType.BLACK -> 0xFFFFFFFF.toInt()
            else -> 0xFFB76E79.toInt()
        }
        Row(
            modifier = Modifier
                .padding(horizontal = 20.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Color(0xE6222222))
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "오버레이도 ${themeName}하게",
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "예",
                color = when (toastTheme) {
                    AppThemeType.BLACK -> Color.Black
                    else -> themeColor
                },
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(themeColor.copy(alpha = if (toastTheme == AppThemeType.BLACK) 0.9f else 0.15f))
                    .clickable {
                        prefs.edit()
                            .putInt("dogakdogak_overlay_color", overlayColor)
                            .apply()
                        showOverlayToast = null
                    }
                    .padding(horizontal = 14.dp, vertical = 6.dp)
            )
        }
        LaunchedEffect(showOverlayToast) {
            if (showOverlayToast != null) {
                delay(5000)
                showOverlayToast = null
            }
        }
    }
    } // Box
}

// ═══════════════════════════════════════════════════════════════════
//  OnboardingScreen — 원본 앱 동일 5단계 온보딩
//  Step 0: 테마 선택 → Step 1: 스위치 선택
//  → Step 2: 오버레이 설정(색상+활성화) → Step 3: IME 활성화 → Step 4: 로그인(선택)
// ═══════════════════════════════════════════════════════════════════

private const val ONBOARDING_STEP_COUNT = 5

@Composable
fun OnboardingScreen(
    prefs: SharedPreferences,
    onComplete: () -> Unit,
    onLogin: (String) -> Unit = {},
) {
    val colors = LocalDogakdogakColors.current
    val context = LocalContext.current

    // Standalone AudioEngine for onboarding (LatinIME's engine is null here)
    val onboardingAudioEngine = remember { AudioEngine(context) }
    DisposableEffect(Unit) {
        onDispose { onboardingAudioEngine.release() }
    }
    // Also set volume from prefs
    val savedVolume = prefs.getFloat("dogakdogak_volume", 0.5f)
    onboardingAudioEngine.volume = savedVolume

    var currentStep by remember { mutableIntStateOf(0) }

    // 스위치 상태
    val savedSwitchName = prefs.getString("dogakdogak_switch_type", SwitchType.getDefaultSwitch().name)
        ?: SwitchType.getDefaultSwitch().name
    var currentSwitch by remember {
        mutableStateOf(
            try { SwitchType.valueOf(savedSwitchName) } catch (_: Exception) { SwitchType.getDefaultSwitch() }
        )
    }

    // 오버레이 색상 — default matches current theme
    val currentThemeName = prefs.getString("dogakdogak_theme", AppThemeType.MAISON.name) ?: AppThemeType.MAISON.name
    val defaultOverlayColor = when (currentThemeName) {
        AppThemeType.FORGE.name -> 0xFFFF6B00.toInt()
        AppThemeType.BLACK.name -> 0xFFFFFFFF.toInt()
        else -> 0xFFB76E79.toInt()
    }
    // Write default overlay color if not set yet (first install)
    if (!prefs.contains("dogakdogak_overlay_color")) {
        prefs.edit().putInt("dogakdogak_overlay_color", defaultOverlayColor).apply()
    }
    var overlayColor by remember { mutableIntStateOf(prefs.getInt("dogakdogak_overlay_color", defaultOverlayColor)) }

    // IME 상태 갱신
    var imeEnabled by remember { mutableStateOf(isImeEnabled(context)) }
    var imeSelected by remember { mutableStateOf(isImeSelected(context)) }
    var overlayGranted by remember { mutableStateOf(AndroidSettings.canDrawOverlays(context)) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                imeEnabled = isImeEnabled(context)
                imeSelected = isImeSelected(context)
                overlayGranted = AndroidSettings.canDrawOverlays(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Periodic polling on step 3 (input method picker is a dialog, ON_RESUME won't fire)
    LaunchedEffect(currentStep) {
        if (currentStep == 3) {
            while (true) {
                delay(500)
                imeEnabled = isImeEnabled(context)
                imeSelected = isImeSelected(context)
                overlayGranted = AndroidSettings.canDrawOverlays(context)
            }
        }
    }

    // 오버레이 권한 획득 시 자동으로 오버레이 카운터 ON
    LaunchedEffect(overlayGranted) {
        if (overlayGranted && !prefs.getBoolean("dogakdogak_overlay_visible", false)) {
            prefs.edit().putBoolean("dogakdogak_overlay_visible", true).apply()
        }
    }

    fun selectSwitch(sw: SwitchType) {
        currentSwitch = sw
        onboardingAudioEngine.setCurrentSwitch(sw)
        prefs.edit().putString("dogakdogak_switch_type", sw.name).apply()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(48.dp))

        // 앱 로고
        Image(
            painter = painterResource(R.drawable.dogakdogak_icon),
            contentDescription = "도각도각",
            modifier = Modifier
                .size(100.dp)
                .clip(CircleShape)
        )
        Text(
            text = "도각도각",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = colors.textPrimary
        )

        Spacer(Modifier.height(16.dp))

        // 스텝 인디케이터 (5개 도트)
        Row(horizontalArrangement = Arrangement.Center) {
            repeat(ONBOARDING_STEP_COUNT) { index ->
                Box(
                    modifier = Modifier
                        .size(if (index == currentStep) 10.dp else 8.dp)
                        .clip(CircleShape)
                        .background(
                            if (index == currentStep) colors.primary
                            else colors.textTertiary.copy(alpha = 0.3f)
                        )
                )
                if (index < ONBOARDING_STEP_COUNT - 1) Spacer(Modifier.width(8.dp))
            }
        }

        Spacer(Modifier.height(32.dp))

        // 스텝 콘텐츠 (400ms 페이지 전환 애니메이션)
        AnimatedContent(
            targetState = currentStep,
            modifier = Modifier.weight(1f),
            transitionSpec = {
                (fadeIn(tween(400)) + slideInVertically(tween(400)) { it / 8 })
                    .togetherWith(fadeOut(tween(300)))
            },
            label = "onboarding_step"
        ) { step ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                when (step) {
                    0 -> OnboardingStepTheme(prefs) { overlayColor = it }
                    1 -> OnboardingStepSwitch(
                        audioEngine = onboardingAudioEngine,
                        currentSwitch = currentSwitch,
                        onSelectSwitch = { sw -> selectSwitch(sw) },
                    )
                    2 -> OnboardingStepOverlaySetup(
                        overlayColor = overlayColor,
                        overlayGranted = overlayGranted,
                        onColorChanged = { newColor ->
                            overlayColor = newColor
                            prefs.edit().putInt("dogakdogak_overlay_color", newColor).apply()
                        }
                    )
                    3 -> OnboardingStepIme(
                        imeEnabled = imeEnabled,
                        imeSelected = imeSelected,
                    )
                    4 -> OnboardingStepLogin(onLogin = onLogin)
                }
            }
        }

        // Step 2: 오버레이 권한 허용 시 자동 다음
        LaunchedEffect(overlayGranted, currentStep) {
            if (currentStep == 2 && overlayGranted) {
                delay(800)
                if (currentStep == 2) currentStep = 3
            }
        }

        // Step 3: IME 설정 완료 시 자동 다음
        LaunchedEffect(imeEnabled, imeSelected, currentStep) {
            if (currentStep == 3 && imeEnabled && imeSelected) {
                delay(500)
                if (currentStep == 3) currentStep = 4
            }
        }

        // 로그인 성공 시 자동 온보딩 완료 (Step 4)
        val sessionStatus by SupabaseModule.client.auth.sessionStatus.collectAsState()
        LaunchedEffect(sessionStatus, currentStep) {
            if (currentStep == 4 && sessionStatus is SessionStatus.Authenticated) {
                delay(800)
                onComplete()
            }
        }

        // 하단 버튼 (뒤로가기 + 다음)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (currentStep > 0) {
                OutlinedButton(
                    onClick = { currentStep-- },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.textSecondary),
                    border = BorderStroke(1.dp, colors.cardBorder),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text("이전", fontSize = 14.sp)
                }
            }
            Button(
                onClick = { if (currentStep < ONBOARDING_STEP_COUNT - 1) currentStep++ else onComplete() },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.primary,
                    contentColor = colors.onPrimary
                ),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text(
                    text = if (currentStep < ONBOARDING_STEP_COUNT - 1) "다음" else "시작하기",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }
        }

        // "나중에 하기" — 로그인 스텝에서만
        if (currentStep == 4) {
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = onComplete) {
                Text("나중에 하기", fontSize = 14.sp, color = colors.textSecondary)
            }
        }

        Spacer(Modifier.height(48.dp))
    }
}

// --- Step 0: 테마 선택 ---
@Composable
private fun OnboardingStepTheme(prefs: SharedPreferences, onOverlayColorChanged: (Int) -> Unit = {}) {
    val colors = LocalDogakdogakColors.current
    val currentThemeStr = prefs.getString("dogakdogak_theme", AppThemeType.MAISON.name) ?: AppThemeType.MAISON.name
    var currentTheme by remember {
        mutableStateOf(try { AppThemeType.valueOf(currentThemeStr) } catch (_: Exception) { AppThemeType.MAISON })
    }

    Text(
        text = "테마를 선택하세요",
        fontSize = 24.sp,
        fontWeight = FontWeight.Bold,
        color = colors.textPrimary
    )
    Spacer(Modifier.height(24.dp))

    GlassCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            data class ThemeCard(
                val type: AppThemeType,
                val label: String,
                val desc: String,
                val palette: DogakdogakColors,
                val kbColors: String,
                val overlayColor: Int,
            )
            val themeCards = listOf(
                ThemeCard(AppThemeType.MAISON, "MAISON", "럭셔리", MaisonColors, "dogakdogak_light", 0xFFB76E79.toInt()),
                ThemeCard(AppThemeType.FORGE, "FORGE", "인더스트리얼", ForgeColors, "dogakdogak_dark", 0xFFFF6B00.toInt()),
                ThemeCard(AppThemeType.BLACK, "BLACK", "다크", BlackColors, "dogakdogak_black", 0xFFFFFFFF.toInt()),
            )
            themeCards.forEach { card ->
                val selected = currentTheme == card.type
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(14.dp))
                        .border(
                            width = if (selected) 2.dp else 0.5.dp,
                            color = if (selected) card.palette.primary else colors.cardBorder,
                            shape = RoundedCornerShape(14.dp)
                        )
                        .background(
                            if (selected) card.palette.primary.copy(alpha = 0.12f)
                            else Color.Transparent
                        )
                        .clickable {
                            currentTheme = card.type
                            prefs.edit()
                                .putString("dogakdogak_theme", card.type.name)
                                .putInt("dogakdogak_overlay_color", card.overlayColor)
                                .putString("theme_colors", card.kbColors)
                                .putString("theme_colors_night", card.kbColors)
                                .apply()
                            onOverlayColorChanged(card.overlayColor)
                        }
                        .padding(horizontal = 8.dp, vertical = 14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                            listOf(card.palette.background, card.palette.primary, card.palette.secondary).forEach { c ->
                                Box(
                                    modifier = Modifier
                                        .size(14.dp)
                                        .clip(CircleShape)
                                        .background(c)
                                        .border(0.5.dp, colors.cardBorder, CircleShape)
                                )
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = card.label,
                            fontSize = 13.sp,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                            color = if (selected) card.palette.primary else colors.textPrimary
                        )
                        Text(card.desc, fontSize = 10.sp, color = colors.textSecondary)
                    }
                }
            }
        }
    }
}

// --- Step 1: 스위치 선택 ---
@Composable
private fun OnboardingStepSwitch(
    audioEngine: AudioEngine?,
    currentSwitch: SwitchType,
    onSelectSwitch: (SwitchType) -> Unit,
) {
    val colors = LocalDogakdogakColors.current
    val scope = rememberCoroutineScope()

    Text(
        text = "키보드 소리를 선택하세요",
        fontSize = 24.sp,
        fontWeight = FontWeight.Bold,
        color = colors.textPrimary
    )
    Text(
        text = "(클릭 시 미리듣기)",
        fontSize = 13.sp,
        color = colors.textSecondary
    )
    Spacer(Modifier.height(24.dp))

    GlassCard {
        SwitchType.getOnboardingSwitches().forEach { switchType ->
            val isSelected = switchType == currentSwitch
            val isPro = switchType.isPremium
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 3.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .border(
                        width = if (isSelected) 1.5.dp else 0.5.dp,
                        color = if (isSelected) colors.primary else colors.glassBorder,
                        shape = RoundedCornerShape(14.dp)
                    )
                    .background(
                        if (isSelected) colors.primary.copy(alpha = 0.12f)
                        else Color.Transparent
                    )
                    .clickable {
                        // Play ~1.5 sec preview (7 clicks with random intervals)
                        scope.launch {
                            repeat(7) { i ->
                                audioEngine?.playSwitchSound(switchType)
                                if (i < 6) delay(kotlin.random.Random.nextLong(150, 250))
                            }
                        }
                        if (!isPro) {
                            onSelectSwitch(switchType)
                        }
                    }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "${switchType.displayNameKo} (${switchType.displayName})",
                            fontSize = 15.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) colors.primary else colors.textPrimary
                        )
                        if (isPro) {
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = "PRO",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                modifier = Modifier
                                    .background(colors.primary, RoundedCornerShape(4.dp))
                                    .padding(horizontal = 5.dp, vertical = 1.dp)
                            )
                        }
                    }
                    Text(
                        text = switchType.description,
                        fontSize = 12.sp,
                        color = colors.textSecondary
                    )
                }
                if (isSelected) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = "선택됨",
                        modifier = Modifier.size(20.dp),
                        tint = colors.primary
                    )
                }
            }
        }
    }
}

// --- Step 2: 오버레이 설정 (색상 + 활성화) ---
@Composable
private fun OnboardingStepOverlaySetup(
    overlayColor: Int,
    overlayGranted: Boolean,
    onColorChanged: (Int) -> Unit,
) {
    val colors = LocalDogakdogakColors.current
    val context = LocalContext.current

    Text(
        text = "콤보 오버레이 설정",
        fontSize = 24.sp,
        fontWeight = FontWeight.Bold,
        color = colors.textPrimary
    )
    Text(
        text = "타이핑할수록 쌓이는 콤보를 실시간으로 확인하세요",
        fontSize = 13.sp,
        color = colors.textSecondary,
        textAlign = TextAlign.Center
    )
    Spacer(Modifier.height(24.dp))

    val initR = remember(overlayColor) { ((overlayColor shr 16) and 0xFF).toFloat() }
    val initG = remember(overlayColor) { ((overlayColor shr 8) and 0xFF).toFloat() }
    val initB = remember(overlayColor) { (overlayColor and 0xFF).toFloat() }
    var red by remember(overlayColor) { mutableFloatStateOf(initR) }
    var green by remember(overlayColor) { mutableFloatStateOf(initG) }
    var blue by remember(overlayColor) { mutableFloatStateOf(initB) }
    val previewColor = Color(red.toInt(), green.toInt(), blue.toInt())

    val saveColor = {
        @Suppress("USELESS_CAST")
        val newColor = (0xFF shl 24) or (red.toInt() shl 16) or (green.toInt() shl 8) or blue.toInt()
        onColorChanged(newColor)
    }

    GlassCard {
        // 오버레이 미리보기 (선택한 색상으로 실시간 반영)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(colors.background.copy(alpha = 0.5f))
                .padding(vertical = 20.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(previewColor.copy(alpha = 0.15f))
                    .border(1.dp, previewColor.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                    .padding(horizontal = 18.dp, vertical = 8.dp)
            ) {
                Text(
                    text = "⚡ 47 COMBO",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = previewColor
                )
            }
        }

        Spacer(Modifier.height(14.dp))

        // 색상 슬라이더
        ColorSliderRow(
            label = "R", value = red, color = Color.Red,
            onValueChange = { red = it },
            onValueChangeFinished = saveColor
        )
        Spacer(Modifier.height(6.dp))
        ColorSliderRow(
            label = "G", value = green, color = Color(0xFF00C853),
            onValueChange = { green = it },
            onValueChangeFinished = saveColor
        )
        Spacer(Modifier.height(6.dp))
        ColorSliderRow(
            label = "B", value = blue, color = Color(0xFF2979FF),
            onValueChange = { blue = it },
            onValueChangeFinished = saveColor
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "#${"%02X".format(red.toInt())}${"%02X".format(green.toInt())}${"%02X".format(blue.toInt())}",
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = colors.textTertiary
        )

        Spacer(Modifier.height(14.dp))

        // 활성화 버튼 or 완료 상태
        if (overlayGranted) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(colors.success.copy(alpha = 0.1f))
                    .border(1.dp, colors.success.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = null,
                    tint = colors.success,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "오버레이 권한 허용됨",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.success
                )
            }
        } else {
            Button(
                onClick = {
                    context.startActivity(
                        Intent(
                            AndroidSettings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            android.net.Uri.parse("package:${context.packageName}")
                        )
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.primary,
                    contentColor = colors.onPrimary
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = "오버레이 활성화하기",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(vertical = 2.dp)
                )
            }
        }
    }
}

// --- Step 4: IME 활성화 ---
@Composable
private fun OnboardingStepIme(
    imeEnabled: Boolean,
    imeSelected: Boolean,
) {
    val colors = LocalDogakdogakColors.current
    val context = LocalContext.current
    val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager

    Text(
        text = "키보드를 활성화하세요",
        fontSize = 24.sp,
        fontWeight = FontWeight.Bold,
        color = colors.textPrimary
    )
    Text(
        text = "2단계를 완료하면 도각도각 타건음이 시작돼요",
        fontSize = 13.sp,
        color = colors.textSecondary
    )
    Spacer(Modifier.height(24.dp))

    GlassCard {
        // 안심 메시지
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(colors.primary.copy(alpha = 0.07f))
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Lock,
                contentDescription = null,
                tint = colors.primary,
                modifier = Modifier.size(16.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "오픈소스 기반 · 입력 내용 수집 없음",
                fontSize = 13.sp,
                color = colors.textPrimary,
                fontWeight = FontWeight.Medium
            )
        }

        Spacer(Modifier.height(16.dp))

        // Step 1
        ImeSetupStep(
            stepNumber = 1,
            title = "키보드 활성화",
            description = "시스템 설정에서 도각도각 키보드 켜기",
            isDone = imeEnabled,
            buttonText = "설정으로 이동",
            showButton = !imeEnabled,
            onButtonClick = {
                context.startActivity(Intent(AndroidSettings.ACTION_INPUT_METHOD_SETTINGS))
            }
        )

        Spacer(Modifier.height(10.dp))

        // Step 2
        ImeSetupStep(
            stepNumber = 2,
            title = "기본 키보드 설정",
            description = "도각도각을 기본 키보드로 선택하기",
            isDone = imeSelected,
            buttonText = "키보드 선택하기",
            showButton = imeEnabled && !imeSelected,
            onButtonClick = { imm.showInputMethodPicker() }
        )
    }
}

@Composable
private fun ImeSetupStep(
    stepNumber: Int,
    title: String,
    description: String,
    isDone: Boolean,
    buttonText: String,
    showButton: Boolean,
    onButtonClick: () -> Unit,
) {
    val colors = LocalDogakdogakColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (isDone) colors.success.copy(alpha = 0.07f)
                else colors.surface.copy(alpha = 0.6f)
            )
            .border(
                width = 1.dp,
                color = if (isDone) colors.success.copy(alpha = 0.4f) else colors.glassBorder,
                shape = RoundedCornerShape(12.dp)
            )
            .padding(14.dp),
        verticalAlignment = Alignment.Top
    ) {
        // 스텝 번호 / 완료 체크
        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(CircleShape)
                .background(
                    if (isDone) colors.success
                    else colors.primary.copy(alpha = 0.15f)
                ),
            contentAlignment = Alignment.Center
        ) {
            if (isDone) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = "완료",
                    tint = Color.White,
                    modifier = Modifier.size(17.dp)
                )
            } else {
                Text(
                    text = "$stepNumber",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.primary
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (isDone) colors.success else colors.textPrimary
            )
            Text(
                text = description,
                fontSize = 13.sp,
                color = colors.textSecondary
            )
            if (showButton) {
                Spacer(Modifier.height(10.dp))
                Button(
                    onClick = onButtonClick,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.primary,
                        contentColor = colors.onPrimary
                    ),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(buttonText, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

// --- Step 4: 로그인 (선택) ---
@Composable
private fun OnboardingStepLogin(onLogin: (String) -> Unit = {}) {
    val colors = LocalDogakdogakColors.current

    Text(
        text = "랭킹에 참여하세요",
        fontSize = 24.sp,
        fontWeight = FontWeight.Bold,
        color = colors.textPrimary
    )
    Text(
        text = "로그인하면 전세계 타이핑 랭킹에 참여할 수 있어요",
        fontSize = 13.sp,
        color = colors.textSecondary
    )
    Spacer(Modifier.height(24.dp))

    GlassCard {
        // 카카오 로그인
        Button(
            onClick = { onLogin("kakao") },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFFFEE500),
                contentColor = Color(0xFF191919)
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text("카카오로 로그인", fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(8.dp))
        // Google 로그인
        Button(
            onClick = { onLogin("google") },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (colors.isDark) Color.White else colors.primary,
                contentColor = if (colors.isDark) Color(0xFF1A1A1A) else Color.White
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text("Google로 로그인", fontWeight = FontWeight.SemiBold)
        }

    }
}

// ═══════════════════════════════════════════════════════════════════
//  공통 컴포넌트 (원본 디자인 그대로)
// ═══════════════════════════════════════════════════════════════════

/** 글래스모피즘 카드 — 원본 디자인 매칭 */
@Composable
fun GlassCard(content: @Composable () -> Unit) {
    val colors = LocalDogakdogakColors.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(colors.glassBg.copy(alpha = if (colors.isDark) 0.6f else 0.9f))
            .border(0.5.dp, colors.glassBorder, RoundedCornerShape(20.dp))
            .padding(20.dp)
    ) {
        content()
    }
}

/** 펄싱 상태 표시 점 — 원본 디자인 매칭 */
@Composable
private fun PulsingDot(color: Color) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )
    Box(
        modifier = Modifier
            .size(10.dp)
            .alpha(alpha)
            .clip(CircleShape)
            .background(color)
    )
}

/** RGB 슬라이더 행 — 원본 디자인 매칭 */
@Composable
private fun ColorSliderRow(
    label: String,
    value: Float,
    color: Color,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
) {
    val colors = LocalDogakdogakColors.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = label,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = color,
            modifier = Modifier.width(20.dp)
        )
        Slider(
            value = value,
            onValueChange = onValueChange,
            onValueChangeFinished = onValueChangeFinished,
            valueRange = 0f..255f,
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 24.dp),
            colors = SliderDefaults.colors(
                thumbColor = color,
                activeTrackColor = color,
                inactiveTrackColor = color.copy(alpha = 0.2f)
            )
        )
        Text(
            text = value.toInt().toString(),
            fontSize = 12.sp,
            color = colors.textTertiary,
            modifier = Modifier.width(30.dp),
            fontWeight = FontWeight.Medium
        )
    }
}

// ═══════════════════════════════════════════════════════════════════
//  Helper Functions
// ═══════════════════════════════════════════════════════════════════

/** IME가 활성화(enabled)되어 있는지 확인 — InputMethodManager API 사용 (SDK 34+ 호환) */
private fun isImeEnabled(context: Context): Boolean {
    val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
    val packageName = context.packageName
    return imm.enabledInputMethodList.any { it.packageName == packageName }
}

/** IME가 현재 기본 키보드로 선택되어 있는지 확인 — InputMethodManager API 사용 (SDK 34+ 호환) */
private fun isImeSelected(context: Context): Boolean {
    val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
    val packageName = context.packageName
    val imi = imm.inputMethodList.firstOrNull { it.packageName == packageName } ?: return false
    val currentImeId = AndroidSettings.Secure.getString(
        context.contentResolver, AndroidSettings.Secure.DEFAULT_INPUT_METHOD
    ) ?: return false
    return imi.id == currentImeId
}

// ═══════════════════════════════════════════════════════════════════
//  ChillGradientText — 가로로 천천히 흐르는 그래디언트 텍스트
//
//  텍스트가 "×1" → "×2" → "×3"으로 바뀌어도 그래디언트 흐름은
//  끊기지 않고 연속됨 (infiniteTransition이 text와 독립적으로 유지)
//
//  사용법:
//    var combo by remember { mutableIntStateOf(0) }
//    ChillGradientText(text = "×$combo")
// ═══════════════════════════════════════════════════════════════════

@Composable
fun ChillGradientText(
    text: String,
    modifier: Modifier = Modifier,
    fontSize: androidx.compose.ui.unit.TextUnit = 48.sp,
    fontWeight: FontWeight = FontWeight.ExtraBold,
    fontFamily: FontFamily = AggroFamily,
    gradientWidth: Float = 800f,
    durationMs: Int = 6000,
) {
    val gradientColors = listOf(
        Color(0xFFFF6B9D),  // Pink
        Color(0xFFFFB347),  // Orange-Yellow
        Color(0xFFFFF176),  // Yellow
        Color(0xFF69F0AE),  // Mint Green
        Color(0xFF64D2FF),  // Cyan
        Color(0xFF7C4DFF),  // Purple
        Color(0xFFFF6B9D),  // Pink (seamless loop)
    )

    val infiniteTransition = rememberInfiniteTransition(label = "chillGradient")
    val offset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = durationMs, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "gradientOffset"
    )

    val shift = offset * gradientWidth
    val brush = androidx.compose.ui.graphics.Brush.linearGradient(
        colors = gradientColors,
        start = androidx.compose.ui.geometry.Offset(shift, 0f),
        end = androidx.compose.ui.geometry.Offset(shift + gradientWidth, 0f),
    )

    Text(
        text = text,
        fontSize = fontSize,
        fontWeight = fontWeight,
        fontFamily = fontFamily,
        style = androidx.compose.ui.text.TextStyle(brush = brush),
        modifier = modifier,
    )
}
