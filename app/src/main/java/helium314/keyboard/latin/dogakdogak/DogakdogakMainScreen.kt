package helium314.keyboard.latin.dogakdogak

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import io.github.jan.supabase.gotrue.SessionStatus
import io.github.jan.supabase.gotrue.auth
import android.provider.Settings as AndroidSettings
import android.view.inputmethod.InputMethodManager
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import helium314.keyboard.keyboard.KeyboardSwitcher
import helium314.keyboard.latin.AudioAndHapticFeedbackManager
import helium314.keyboard.latin.R
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
) {
    val colors = LocalDogakdogakColors.current
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route ?: "sound"

    Scaffold(
        containerColor = Color.Transparent,
        bottomBar = {
            NavigationBar(
                modifier = Modifier
                    .height(80.dp),
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
                                    popUpTo("sound") { saveState = true }
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
            NavHost(navController = navController, startDestination = "sound") {
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
                                    audioEngine?.playSwitchSound(switchType)
                                    selectSwitch(switchType)
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
            val focusRequester = remember { FocusRequester() }
            var previewText by remember { mutableStateOf("") }

            LaunchedEffect(Unit) {
                focusRequester.requestFocus()
            }

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

                OutlinedTextField(
                    value = previewText,
                    onValueChange = { newText ->
                        val added = newText.length - previewText.length
                        if (added > 0) {
                            repeat(added.coerceAtMost(3)) {
                                audioEngine?.playSwitchSound(switchType)
                            }
                        }
                        previewText = newText
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(150.dp)
                        .focusRequester(focusRequester),
                    placeholder = {
                        Text("여기에 타이핑하세요...", color = colors.textTertiary)
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = colors.primary,
                        unfocusedBorderColor = colors.glassBorder,
                        cursorColor = colors.primary,
                        focusedTextColor = colors.textPrimary,
                        unfocusedTextColor = colors.textPrimary
                    ),
                    shape = RoundedCornerShape(14.dp)
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
    val hasBubbleEffects by (purchaseRepository?.hasBubbleEffectsFlow
        ?: kotlinx.coroutines.flow.flowOf(false)).collectAsState(initial = false)

    val audioEngine = AudioAndHapticFeedbackManager.getInstance().audioEngine

    // 콤보 이펙트 미리보기 바텀시트
    var showEffectPreview by remember { mutableStateOf(false) }

    // 오버레이 설정 상태
    var overlayVisible by remember { mutableStateOf(prefs.getBoolean("dogakdogak_overlay_visible", true)) }
    var overlayTouch by remember { mutableStateOf(prefs.getBoolean("dogakdogak_overlay_touch", true)) }
    var overlayScale by remember { mutableFloatStateOf(prefs.getFloat("dogakdogak_overlay_scale", 1.0f)) }
    var overlayColor by remember { mutableIntStateOf(prefs.getInt("dogakdogak_overlay_color", 0xFFFF6B00.toInt())) }

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

        // -- 콤보 이펙트 카드 --
        GlassCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "프리미엄 콤보 이펙트",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.textPrimary
                )
                if (hasPremiumEffects) {
                    Text(
                        text = "보유 중",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.primary,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(colors.primary.copy(alpha = 0.15f))
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = if (hasPremiumEffects)
                    "파티클 불꽃놀이 + 진동 이펙트 활성화"
                else
                    "빠른 타이핑 시 콤보 텍스트가 표시됩니다",
                fontSize = 13.sp,
                color = colors.textSecondary
            )
            Spacer(Modifier.height(12.dp))
            OutlinedButton(
                onClick = { showEffectPreview = true },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.primary),
                border = BorderStroke(1.dp, colors.primary.copy(alpha = 0.5f)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("미리보기", fontWeight = FontWeight.SemiBold)
            }
        }

        Spacer(Modifier.height(16.dp))

        // -- 버블 콤보 이펙트 카드 --
        GlassCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "버블 콤보 이펙트",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.textPrimary
                )
                if (hasBubbleEffects) {
                    Text(
                        text = "보유 중",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.primary,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(colors.primary.copy(alpha = 0.15f))
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = if (hasBubbleEffects)
                    "귀여운 버블 이미지 콤보 카운터 활성화"
                else
                    "X1 X2 X3 스타일 버블 이미지로 콤보를 표시합니다",
                fontSize = 13.sp,
                color = colors.textSecondary
            )
            if (!hasBubbleEffects) {
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = {
                        val activity = context as? androidx.activity.ComponentActivity ?: return@Button
                        scope.launch {
                            purchaseRepository?.launchPurchase(
                                activity,
                                SwitchType.BUBBLE_EFFECTS_PRODUCT_ID
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
                    Text("구매하기 (1,990원)", fontWeight = FontWeight.SemiBold)
                }
            }
        }

        Spacer(Modifier.height(16.dp))

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
            val focusRequester = remember { FocusRequester() }
            var previewText by remember { mutableStateOf("") }

            val currentSwitch = remember {
                val name = prefs.getString("dogakdogak_switch_type", SwitchType.getDefaultSwitch().name)
                    ?: SwitchType.getDefaultSwitch().name
                try { SwitchType.valueOf(name) } catch (_: Exception) { SwitchType.getDefaultSwitch() }
            }

            LaunchedEffect(Unit) {
                focusRequester.requestFocus()
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 32.dp)
            ) {
                Text(
                    text = "프리미엄 콤보 이펙트 미리보기",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.textPrimary
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "아래에 빠르게 타이핑해서 프리미엄 콤보 이펙트를 확인하세요",
                    fontSize = 13.sp,
                    color = colors.textSecondary
                )
                Spacer(Modifier.height(16.dp))

                OutlinedTextField(
                    value = previewText,
                    onValueChange = { newText ->
                        val added = newText.length - previewText.length
                        if (added > 0) {
                            repeat(added.coerceAtMost(3)) {
                                audioEngine?.playSwitchSound(currentSwitch)
                            }
                        }
                        previewText = newText
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(150.dp)
                        .focusRequester(focusRequester),
                    placeholder = {
                        Text("여기에 타이핑하세요...", color = colors.textTertiary)
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = colors.primary,
                        unfocusedBorderColor = colors.glassBorder,
                        cursorColor = colors.primary,
                        focusedTextColor = colors.textPrimary,
                        unfocusedTextColor = colors.textPrimary
                    ),
                    shape = RoundedCornerShape(14.dp)
                )

                if (!hasPremiumEffects) {
                    Spacer(Modifier.height(16.dp))
                    Button(
                        onClick = {
                            focusManager.clearFocus()
                            showEffectPreview = false
                            val activity = context as? androidx.activity.ComponentActivity ?: return@Button
                            scope.launch {
                                purchaseRepository?.launchPurchase(
                                    activity,
                                    SwitchType.PREMIUM_EFFECTS_PRODUCT_ID
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
    val audioEngine = AudioAndHapticFeedbackManager.getInstance().audioEngine

    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showOverlayToast by remember { mutableStateOf<AppThemeType?>(null) }

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

    // 볼륨 상태
    var soundVolume by remember {
        mutableFloatStateOf(prefs.getFloat("dogakdogak_volume", 0.5f).coerceIn(0.1f, 0.9f))
    }
    var soundMuted by remember { mutableStateOf(prefs.getBoolean("dogakdogak_muted", false)) }

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
        val isLoggedIn = rankingRepository?.isLoggedIn?.collectAsState(initial = false)?.value ?: false
        if (isLoggedIn) {
            val displayName = rankingRepository?.getCurrentUserDisplayName() ?: "익명"
            GlassCard {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(colors.primary.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(displayName.take(1).uppercase(), fontSize = 18.sp, fontWeight = FontWeight.Bold, color = colors.primary)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(displayName, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = colors.textPrimary)
                        Text("랭킹에 참여 중", fontSize = 13.sp, color = colors.textSecondary)
                    }
                }
                Spacer(Modifier.height(12.dp))
                OutlinedButton(
                    onClick = { onLogout?.invoke() },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.error),
                    border = BorderStroke(1.dp, colors.error.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("로그아웃", fontWeight = FontWeight.SemiBold)
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
                    fontSize = 30.sp,
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
                // 음소거 버튼
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(
                            if (soundMuted) colors.error.copy(alpha = 0.15f)
                            else colors.surface
                        )
                        .clickable {
                            soundMuted = !soundMuted
                            prefs.edit().putBoolean("dogakdogak_muted", soundMuted).apply()
                            audioEngine?.volume = if (soundMuted) 0f else soundVolume
                        }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (soundMuted) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                        contentDescription = if (soundMuted) "음소거 해제" else "음소거",
                        modifier = Modifier.size(20.dp),
                        tint = if (soundMuted) colors.error else colors.textSecondary
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = "음소거",
                        fontSize = 12.sp,
                        color = if (soundMuted) colors.error else colors.textTertiary
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
                            if (canDecrease && !soundMuted) colors.primary.copy(alpha = 0.15f)
                            else colors.surface
                        )
                        .clickable(enabled = canDecrease && !soundMuted) {
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
                        color = if (canDecrease && !soundMuted) colors.primary else colors.textTertiary
                    )
                }

                Spacer(Modifier.width(24.dp))

                // 볼륨 숫자
                Text(
                    text = if (soundMuted) "OFF" else "$displayLevel",
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (soundMuted) colors.error else colors.textPrimary
                )

                Spacer(Modifier.width(24.dp))

                // + 버튼
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(
                            if (canIncrease && !soundMuted) colors.primary.copy(alpha = 0.15f)
                            else colors.surface
                        )
                        .clickable(enabled = canIncrease && !soundMuted) {
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
                        color = if (canIncrease && !soundMuted) colors.primary else colors.textTertiary
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
                                if (soundMuted) colors.textTertiary.copy(alpha = 0.2f)
                                else if (index < displayLevel) colors.primary
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
        val isLoggedInForDelete = rankingRepository?.isLoggedIn?.collectAsState(initial = false)?.value ?: false
        if (isLoggedInForDelete) {
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
//  Step 0: 테마 선택 → Step 1: 스위치 선택 → Step 2: 오버레이 색상
//  → Step 3: IME 활성화 → Step 4: 로그인(선택)
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
    // Guard flag to prevent auto-advance re-triggering on back button
    var hasAutoAdvanced by remember { mutableStateOf(false) }

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
                    2 -> OnboardingStepOverlayColor(
                        overlayColor = overlayColor,
                        onColorChanged = { newColor ->
                            overlayColor = newColor
                            prefs.edit().putInt("dogakdogak_overlay_color", newColor).apply()
                        }
                    )
                    3 -> OnboardingStepIme(
                        imeEnabled = imeEnabled,
                        imeSelected = imeSelected,
                        overlayGranted = overlayGranted,
                    )
                    4 -> OnboardingStepLogin(onLogin = onLogin)
                }
            }
        }

        // IME 설정 완료 시 자동 다음 (Step 3) — only once
        LaunchedEffect(imeEnabled, imeSelected, overlayGranted, currentStep) {
            if (currentStep == 3 && imeEnabled && imeSelected && overlayGranted && !hasAutoAdvanced) {
                delay(500)
                hasAutoAdvanced = true
                currentStep = 4
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
                        // Play ~1 sec preview (4 clicks at 250ms intervals)
                        scope.launch {
                            repeat(4) { i ->
                                audioEngine?.playSwitchSound(switchType)
                                if (i < 3) delay(250)
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

// --- Step 2: 오버레이 색상 ---
@Composable
private fun OnboardingStepOverlayColor(
    overlayColor: Int,
    onColorChanged: (Int) -> Unit,
) {
    val colors = LocalDogakdogakColors.current

    Text(
        text = "오버레이 색상을 설정하세요",
        fontSize = 24.sp,
        fontWeight = FontWeight.Bold,
        color = colors.textPrimary
    )
    Text(
        text = "타이핑할 때 화면에 표시되는 카운터 색상이에요",
        fontSize = 13.sp,
        color = colors.textSecondary
    )
    Spacer(Modifier.height(24.dp))

    val initR = remember(overlayColor) { ((overlayColor shr 16) and 0xFF).toFloat() }
    val initG = remember(overlayColor) { ((overlayColor shr 8) and 0xFF).toFloat() }
    val initB = remember(overlayColor) { (overlayColor and 0xFF).toFloat() }
    var red by remember(overlayColor) { mutableFloatStateOf(initR) }
    var green by remember(overlayColor) { mutableFloatStateOf(initG) }
    var blue by remember(overlayColor) { mutableFloatStateOf(initB) }
    val previewColor = Color(red.toInt(), green.toInt(), blue.toInt())

    Box(
        modifier = Modifier
            .size(80.dp)
            .clip(CircleShape)
            .background(previewColor)
            .border(2.dp, colors.cardBorder, CircleShape)
    )
    Spacer(Modifier.height(24.dp))

    val saveColor = {
        @Suppress("USELESS_CAST")
        val newColor = (0xFF shl 24) or (red.toInt() shl 16) or (green.toInt() shl 8) or blue.toInt()
        onColorChanged(newColor)
    }

    GlassCard {
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
        Spacer(Modifier.height(8.dp))
        Text(
            text = "#${"%02X".format(red.toInt())}${"%02X".format(green.toInt())}${"%02X".format(blue.toInt())}",
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = colors.textTertiary
        )
    }
}

// --- Step 3: IME 활성화 ---
@Composable
private fun OnboardingStepIme(
    imeEnabled: Boolean,
    imeSelected: Boolean,
    overlayGranted: Boolean,
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
        text = "3단계를 완료하면 도각도각 타건음이 시작돼요",
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

        Spacer(Modifier.height(10.dp))

        // Step 3
        ImeSetupStep(
            stepNumber = 3,
            title = "다른 앱 위에 표시",
            description = "도각도각 오버레이를 표시하기 위한 권한",
            isDone = overlayGranted,
            buttonText = "권한 설정",
            showButton = !overlayGranted,
            onButtonClick = {
                context.startActivity(
                    Intent(
                        AndroidSettings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        android.net.Uri.parse("package:${context.packageName}")
                    )
                )
            }
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
