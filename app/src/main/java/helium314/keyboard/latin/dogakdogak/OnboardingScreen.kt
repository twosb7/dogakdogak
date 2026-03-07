package helium314.keyboard.latin.dogakdogak

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.view.inputmethod.InputMethodManager
import io.github.jan.supabase.gotrue.SessionStatus
import io.github.jan.supabase.gotrue.auth
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import helium314.keyboard.latin.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private const val ONBOARDING_STEP_COUNT = 5

@Composable
fun OnboardingScreen(
    prefs: SharedPreferences,
    onComplete: () -> Unit,
    onLogin: (String) -> Unit = {},
    showLoginLoading: Boolean = false,
    isLoginInProgress: Boolean = showLoginLoading,
) {
    val colors = LocalDogakdogakColors.current
    val context = LocalContext.current

    val onboardingAudioEngine = remember { AudioEngine(context) }
    DisposableEffect(Unit) { onDispose { onboardingAudioEngine.release() } }
    onboardingAudioEngine.volume = prefs.getFloat(PrefsKeys.VOLUME, 0.5f)

    var currentStep by remember { mutableIntStateOf(0) }
    val savedSwitchName = prefs.getString(PrefsKeys.SWITCH_TYPE, SwitchType.getDefaultSwitch().name) ?: SwitchType.getDefaultSwitch().name
    var currentSwitch by remember { mutableStateOf(try { SwitchType.valueOf(savedSwitchName) } catch (_: Exception) { SwitchType.getDefaultSwitch() }) }

    val currentThemeName = prefs.getString(PrefsKeys.THEME, AppThemeType.MAISON.name) ?: AppThemeType.MAISON.name
    val defaultOverlayColor = when (currentThemeName) { AppThemeType.FORGE.name -> 0xFFFF6B00.toInt(); AppThemeType.BLACK.name -> 0xFFFFFFFF.toInt(); else -> 0xFFB76E79.toInt() }
    if (!prefs.contains(PrefsKeys.OVERLAY_COLOR)) { prefs.edit().putInt(PrefsKeys.OVERLAY_COLOR, defaultOverlayColor).apply() }
    var overlayColor by remember { mutableIntStateOf(prefs.getInt(PrefsKeys.OVERLAY_COLOR, defaultOverlayColor)) }

    var imeEnabled by remember { mutableStateOf(isImeEnabled(context)) }
    var imeSelected by remember { mutableStateOf(isImeSelected(context)) }
    var overlayGranted by remember { mutableStateOf(DogakdogakCompat.canDrawOverlays(context)) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) { imeEnabled = isImeEnabled(context); imeSelected = isImeSelected(context); overlayGranted = DogakdogakCompat.canDrawOverlays(context) }
        }
        lifecycleOwner.lifecycle.addObserver(observer); onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(currentStep) {
        if (currentStep == 3) { while (true) { delay(500); imeEnabled = isImeEnabled(context); imeSelected = isImeSelected(context); overlayGranted = DogakdogakCompat.canDrawOverlays(context) } }
    }

    LaunchedEffect(overlayGranted) {
        if (overlayGranted && !prefs.getBoolean(PrefsKeys.OVERLAY_VISIBLE, false)) { prefs.edit().putBoolean(PrefsKeys.OVERLAY_VISIBLE, true).apply() }
    }

    fun selectSwitch(sw: SwitchType) { currentSwitch = sw; onboardingAudioEngine.setCurrentSwitch(sw); prefs.edit().putString(PrefsKeys.SWITCH_TYPE, sw.name).apply() }

    Column(modifier = Modifier.fillMaxSize().background(colors.background).padding(horizontal = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(48.dp))
        Image(painter = painterResource(R.drawable.dogakdogak_icon), contentDescription = "도각도각", modifier = Modifier.size(100.dp).clip(CircleShape))
        Text("도각도각", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = colors.textPrimary)
        Spacer(Modifier.height(16.dp))

        Row(horizontalArrangement = Arrangement.Center) {
            repeat(ONBOARDING_STEP_COUNT) { index ->
                Box(modifier = Modifier.size(if (index == currentStep) 10.dp else 8.dp).clip(CircleShape)
                    .background(if (index == currentStep) colors.primary else colors.textTertiary.copy(alpha = 0.3f)))
                if (index < ONBOARDING_STEP_COUNT - 1) Spacer(Modifier.width(8.dp))
            }
        }
        Spacer(Modifier.height(32.dp))

        AnimatedContent(targetState = currentStep, modifier = Modifier.weight(1f),
            transitionSpec = { (fadeIn(tween(400)) + slideInVertically(tween(400)) { it / 8 }).togetherWith(fadeOut(tween(300))) }, label = "onboarding_step"
        ) { step ->
            Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
                when (step) {
                    0 -> OnboardingStepTheme(prefs) { overlayColor = it }
                    1 -> OnboardingStepSwitch(audioEngine = onboardingAudioEngine, currentSwitch = currentSwitch, onSelectSwitch = { sw -> selectSwitch(sw) })
                    2 -> OnboardingStepOverlaySetup(overlayColor = overlayColor, overlayGranted = overlayGranted, onColorChanged = { newColor -> overlayColor = newColor; prefs.edit().putInt(PrefsKeys.OVERLAY_COLOR, newColor).apply() })
                    3 -> OnboardingStepIme(imeEnabled = imeEnabled, imeSelected = imeSelected)
                    4 -> OnboardingStepLogin(prefs = prefs, onLogin = onLogin, isLoading = isLoginInProgress)
                }
            }
        }

        LaunchedEffect(currentStep) {
            when (currentStep) {
                2 -> { if (overlayGranted) return@LaunchedEffect; snapshotFlow { overlayGranted }.filter { it }.first(); delay(800); if (currentStep == 2) currentStep = 3 }
                3 -> { if (imeEnabled && imeSelected) return@LaunchedEffect; snapshotFlow { imeEnabled && imeSelected }.filter { it }.first(); delay(500); if (currentStep == 3) currentStep = 4 }
            }
        }

        val sessionStatus by SupabaseModule.client.auth.sessionStatus.collectAsState()
        LaunchedEffect(sessionStatus, currentStep) { if (currentStep == 4 && sessionStatus is SessionStatus.Authenticated) { delay(800); onComplete() } }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (currentStep > 0) {
                OutlinedButton(onClick = { currentStep-- }, enabled = !isLoginInProgress, colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.textSecondary),
                    border = BorderStroke(1.dp, colors.cardBorder), shape = RoundedCornerShape(14.dp)) { Text("이전", fontSize = 14.sp) }
            }
            Button(onClick = { if (currentStep < ONBOARDING_STEP_COUNT - 1) currentStep++ else onComplete() }, enabled = !isLoginInProgress, modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = colors.primary, contentColor = colors.onPrimary), shape = RoundedCornerShape(14.dp)) {
                Text(if (currentStep < ONBOARDING_STEP_COUNT - 1) "다음" else "시작하기", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(vertical = 4.dp))
            }
        }
        if (currentStep == 4) { Spacer(Modifier.height(8.dp)); TextButton(onClick = onComplete, enabled = !isLoginInProgress) { Text("나중에 하기", fontSize = 14.sp, color = colors.textSecondary) } }
        Spacer(Modifier.height(48.dp))
    }

    if (showLoginLoading) {
        Dialog(
            onDismissRequest = {},
            properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)
        ) {
            GlassCard {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        strokeWidth = 2.5.dp,
                        color = colors.primary
                    )
                    Text("로그인 중입니다", color = colors.textPrimary, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

// --- Step 0: 테마 선택 ---
@Composable
private fun OnboardingStepTheme(prefs: SharedPreferences, onOverlayColorChanged: (Int) -> Unit = {}) {
    val colors = LocalDogakdogakColors.current
    val currentThemeStr = prefs.getString(PrefsKeys.THEME, AppThemeType.MAISON.name) ?: AppThemeType.MAISON.name
    var currentTheme by remember { mutableStateOf(try { AppThemeType.valueOf(currentThemeStr) } catch (_: Exception) { AppThemeType.MAISON }) }

    Text("테마를 선택하세요", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = colors.textPrimary)
    Spacer(Modifier.height(24.dp))
    GlassCard {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            data class ThemeCard(val type: AppThemeType, val label: String, val desc: String, val palette: DogakdogakColors, val kbColors: String, val overlayColor: Int)
            listOf(
                ThemeCard(AppThemeType.MAISON, "MAISON", "럭셔리", MaisonColors, "dogakdogak_light", 0xFFB76E79.toInt()),
                ThemeCard(AppThemeType.FORGE, "FORGE", "인더스트리얼", ForgeColors, "dogakdogak_dark", 0xFFFF6B00.toInt()),
                ThemeCard(AppThemeType.BLACK, "BLACK", "다크", BlackColors, "dogakdogak_black", 0xFFFFFFFF.toInt()),
            ).forEach { card ->
                val selected = currentTheme == card.type
                Box(modifier = Modifier.weight(1f).clip(RoundedCornerShape(14.dp))
                    .border(if (selected) 2.dp else 0.5.dp, if (selected) card.palette.primary else colors.cardBorder, RoundedCornerShape(14.dp))
                    .background(if (selected) card.palette.primary.copy(alpha = 0.12f) else Color.Transparent)
                    .clickable {
                        currentTheme = card.type
                        prefs.edit().putString(PrefsKeys.THEME, card.type.name).putInt(PrefsKeys.OVERLAY_COLOR, card.overlayColor)
                            .putString(PrefsKeys.THEME_COLORS, card.kbColors).putString(PrefsKeys.THEME_COLORS_NIGHT, card.kbColors).apply()
                        onOverlayColorChanged(card.overlayColor)
                    }.padding(horizontal = 8.dp, vertical = 14.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                            listOf(card.palette.background, card.palette.primary, card.palette.secondary).forEach { c ->
                                Box(modifier = Modifier.size(14.dp).clip(CircleShape).background(c).border(0.5.dp, colors.cardBorder, CircleShape))
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(card.label, fontSize = 13.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium, color = if (selected) card.palette.primary else colors.textPrimary)
                        Text(card.desc, fontSize = 10.sp, color = colors.textSecondary)
                    }
                }
            }
        }
    }
}

// --- Step 1: 스위치 선택 ---
@Composable
private fun OnboardingStepSwitch(audioEngine: AudioEngine?, currentSwitch: SwitchType, onSelectSwitch: (SwitchType) -> Unit) {
    val colors = LocalDogakdogakColors.current
    val scope = rememberCoroutineScope()
    Text("키보드 소리를 선택하세요", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = colors.textPrimary)
    Text("(클릭 시 미리듣기)", fontSize = 13.sp, color = colors.textSecondary)
    Spacer(Modifier.height(24.dp))
    GlassCard {
        SwitchType.getOnboardingSwitches().forEach { switchType ->
            val isSelected = switchType == currentSwitch; val isPro = switchType.isPremium
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp).clip(RoundedCornerShape(14.dp))
                .border(if (isSelected) 1.5.dp else 0.5.dp, if (isSelected) colors.primary else colors.glassBorder, RoundedCornerShape(14.dp))
                .background(if (isSelected) colors.primary.copy(alpha = 0.12f) else Color.Transparent)
                .clickable {
                    scope.launch { repeat(7) { i -> audioEngine?.playSwitchSound(switchType); if (i < 6) delay(kotlin.random.Random.nextLong(150, 250)) } }
                    if (!isPro) onSelectSwitch(switchType)
                }.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("${switchType.displayNameKo} (${switchType.displayName})", fontSize = 15.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal, color = if (isSelected) colors.primary else colors.textPrimary)
                        if (isPro) { Spacer(Modifier.width(6.dp)); Text("PRO", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White,
                            modifier = Modifier.background(colors.primary, RoundedCornerShape(4.dp)).padding(horizontal = 5.dp, vertical = 1.dp)) }
                    }
                    Text(switchType.description, fontSize = 12.sp, color = colors.textSecondary)
                }
                if (isSelected) Icon(Icons.Default.Check, contentDescription = "선택됨", modifier = Modifier.size(20.dp), tint = colors.primary)
            }
        }
    }
}

// --- Step 2: 오버레이 설정 ---
@Composable
private fun OnboardingStepOverlaySetup(overlayColor: Int, overlayGranted: Boolean, onColorChanged: (Int) -> Unit) {
    val colors = LocalDogakdogakColors.current
    val context = LocalContext.current
    Text("콤보 오버레이 설정", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = colors.textPrimary)
    Text("타이핑할수록 쌓이는 콤보를 실시간으로 확인하세요", fontSize = 13.sp, color = colors.textSecondary, textAlign = TextAlign.Center)
    Spacer(Modifier.height(24.dp))

    val initR = remember(overlayColor) { ((overlayColor shr 16) and 0xFF).toFloat() }
    val initG = remember(overlayColor) { ((overlayColor shr 8) and 0xFF).toFloat() }
    val initB = remember(overlayColor) { (overlayColor and 0xFF).toFloat() }
    var red by remember(overlayColor) { mutableStateOf(initR) }
    var green by remember(overlayColor) { mutableStateOf(initG) }
    var blue by remember(overlayColor) { mutableStateOf(initB) }
    val previewColor = Color(red.toInt(), green.toInt(), blue.toInt())
    val saveColor = { @Suppress("USELESS_CAST") val nc = (0xFF shl 24) or (red.toInt() shl 16) or (green.toInt() shl 8) or blue.toInt(); onColorChanged(nc) }

    GlassCard {
        Box(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(colors.background.copy(alpha = 0.5f)).padding(vertical = 20.dp), contentAlignment = Alignment.Center) {
            Box(modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(previewColor.copy(alpha = 0.15f)).border(1.dp, previewColor.copy(alpha = 0.5f), RoundedCornerShape(10.dp)).padding(horizontal = 18.dp, vertical = 8.dp)) {
                Text("⚡ 47 COMBO", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = previewColor)
            }
        }
        Spacer(Modifier.height(14.dp))
        ColorSliderRow(label = "R", value = red, color = Color.Red, onValueChange = { red = it }, onValueChangeFinished = saveColor)
        Spacer(Modifier.height(6.dp))
        ColorSliderRow(label = "G", value = green, color = Color(0xFF00C853), onValueChange = { green = it }, onValueChangeFinished = saveColor)
        Spacer(Modifier.height(6.dp))
        ColorSliderRow(label = "B", value = blue, color = Color(0xFF2979FF), onValueChange = { blue = it }, onValueChangeFinished = saveColor)
        Spacer(Modifier.height(4.dp))
        Text("#${"%02X".format(red.toInt())}${"%02X".format(green.toInt())}${"%02X".format(blue.toInt())}", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = colors.textTertiary)
        Spacer(Modifier.height(14.dp))

        if (overlayGranted) {
            Row(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(colors.success.copy(alpha = 0.1f)).border(1.dp, colors.success.copy(alpha = 0.4f), RoundedCornerShape(12.dp)).padding(14.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                Icon(Icons.Default.Check, contentDescription = null, tint = colors.success, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp)); Text("오버레이 권한 허용됨", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = colors.success)
            }
        } else {
            Button(onClick = { context.startActivity(Intent(android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION, android.net.Uri.parse("package:${context.packageName}"))) },
                modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = colors.primary, contentColor = colors.onPrimary), shape = RoundedCornerShape(12.dp)) {
                Text("오버레이 활성화하기", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(vertical = 2.dp))
            }
        }
    }
}

// --- Step 3: IME 활성화 ---
@Composable
private fun OnboardingStepIme(imeEnabled: Boolean, imeSelected: Boolean) {
    val colors = LocalDogakdogakColors.current; val context = LocalContext.current
    val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
    Text("키보드를 활성화하세요", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = colors.textPrimary)
    Text("2단계를 완료하면 도각도각 타건음이 시작돼요", fontSize = 13.sp, color = colors.textSecondary)
    Spacer(Modifier.height(24.dp))
    GlassCard {
        Row(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(colors.primary.copy(alpha = 0.07f)).padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Lock, contentDescription = null, tint = colors.primary, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp)); Text("오픈소스 기반 · 입력 내용 수집 없음", fontSize = 13.sp, color = colors.textPrimary, fontWeight = FontWeight.Medium)
        }
        Spacer(Modifier.height(16.dp))
        ImeSetupStep(1, "키보드 활성화", "시스템 설정에서 도각도각 키보드 켜기", imeEnabled, "설정으로 이동", !imeEnabled) { context.startActivity(Intent(android.provider.Settings.ACTION_INPUT_METHOD_SETTINGS)) }
        Spacer(Modifier.height(10.dp))
        ImeSetupStep(2, "기본 키보드 설정", "도각도각을 기본 키보드로 선택하기", imeSelected, "키보드 선택하기", imeEnabled && !imeSelected) { @Suppress("DEPRECATION") imm.showInputMethodPicker() }
    }
}

@Composable
private fun ImeSetupStep(stepNumber: Int, title: String, description: String, isDone: Boolean, buttonText: String, showButton: Boolean, onButtonClick: () -> Unit) {
    val colors = LocalDogakdogakColors.current
    Row(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
        .background(if (isDone) colors.success.copy(alpha = 0.07f) else colors.surface.copy(alpha = 0.6f))
        .border(1.dp, if (isDone) colors.success.copy(alpha = 0.4f) else colors.glassBorder, RoundedCornerShape(12.dp)).padding(14.dp), verticalAlignment = Alignment.Top) {
        Box(modifier = Modifier.size(30.dp).clip(CircleShape).background(if (isDone) colors.success else colors.primary.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
            if (isDone) Icon(Icons.Default.Check, contentDescription = "완료", tint = Color.White, modifier = Modifier.size(17.dp))
            else Text("$stepNumber", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = colors.primary)
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = if (isDone) colors.success else colors.textPrimary)
            Text(description, fontSize = 13.sp, color = colors.textSecondary)
            if (showButton) {
                Spacer(Modifier.height(10.dp))
                Button(onClick = onButtonClick, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = colors.primary, contentColor = colors.onPrimary), shape = RoundedCornerShape(10.dp)) { Text(buttonText, fontWeight = FontWeight.SemiBold) }
            }
        }
    }
}

// --- Step 4: 로그인 ---
@Composable
private fun OnboardingStepLogin(
    prefs: SharedPreferences,
    onLogin: (String) -> Unit = {},
    isLoading: Boolean = false,
) {
    val colors = LocalDogakdogakColors.current
    val context = LocalContext.current
    var disclosureAccepted by remember { mutableStateOf(hasRankingDisclosureConsent(prefs)) }
    Text("랭킹에 참여하세요", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = colors.textPrimary)
    Text("로그인하면 전세계 타이핑 랭킹에 참여할 수 있어요", fontSize = 13.sp, color = colors.textSecondary)
    Spacer(Modifier.height(24.dp))
    GlassCard {
        RankingDisclosureCard(
            isAccepted = disclosureAccepted,
            onAccept = {
                acceptRankingDisclosure(prefs)
                AppClickCountRepository.getInstance(context).resetCurrentUserDailyData()
                disclosureAccepted = true
            }
        )
        Spacer(Modifier.height(12.dp))
        Button(onClick = { onLogin("kakao") }, enabled = disclosureAccepted && !isLoading, modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFEE500), contentColor = Color(0xFF191919)), shape = RoundedCornerShape(12.dp)) { Text("카카오로 로그인", fontWeight = FontWeight.SemiBold) }
        Spacer(Modifier.height(8.dp))
        Button(onClick = { onLogin("google") }, enabled = disclosureAccepted && !isLoading, modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = if (colors.isDark) Color.White else colors.primary, contentColor = if (colors.isDark) Color(0xFF1A1A1A) else Color.White), shape = RoundedCornerShape(12.dp)) { Text("Google로 로그인", fontWeight = FontWeight.SemiBold) }
    }
}
