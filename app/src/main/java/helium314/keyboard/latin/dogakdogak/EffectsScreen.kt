package helium314.keyboard.latin.dogakdogak

import android.content.Intent
import android.content.SharedPreferences
import android.provider.Settings as AndroidSettings
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
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import helium314.keyboard.latin.AudioAndHapticFeedbackManager
import helium314.keyboard.latin.R
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun EffectsScreen(prefs: SharedPreferences, purchaseRepository: PurchaseRepository? = null) {
    val colors = LocalDogakdogakColors.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val hasPremiumEffects by (purchaseRepository?.hasPremiumEffectsFlow
        ?: kotlinx.coroutines.flow.flowOf(false)).collectAsState(initial = false)
    val hasCutiePinkEffects by (purchaseRepository?.hasCutiePinkEffectsFlow
        ?: kotlinx.coroutines.flow.flowOf(false)).collectAsState(initial = false)
    val hasArcadeEffects by (purchaseRepository?.hasArcadeEffectsFlow
        ?: kotlinx.coroutines.flow.flowOf(false)).collectAsState(initial = false)

    val audioEngine = AudioAndHapticFeedbackManager.getInstance().audioEngine

    var bundlePrice by remember { mutableStateOf("2,990원") }
    var singlePrice by remember { mutableStateOf("1,490원") }
    LaunchedEffect(Unit) {
        val prices = purchaseRepository?.fetchProductPrices(
            listOf(SwitchType.EFFECTS_BUNDLE_PRODUCT_ID, SwitchType.PREMIUM_EFFECTS_PRODUCT_ID)
        ) ?: emptyMap()
        prices[SwitchType.EFFECTS_BUNDLE_PRODUCT_ID]?.takeIf { it.isNotBlank() }?.let { bundlePrice = it }
        prices[SwitchType.PREMIUM_EFFECTS_PRODUCT_ID]?.takeIf { it.isNotBlank() }?.let { singlePrice = it }
    }

    // 진입 시 이전 세션에서 남은 stale preview_effect_mode 강제 리셋
    LaunchedEffect(Unit) {
        prefs.edit().putInt(PrefsKeys.PREVIEW_EFFECT_MODE, -1).commit()
    }

    var showEffectPreview by remember { mutableIntStateOf(-1) }  // -1=닫힘, 0=프리미엄, 1=큐티핑크, 2=아케이드
    var showBundlePurchaseDialog by remember { mutableStateOf(false) }

    var premiumEffectsOn by remember { mutableStateOf(prefs.getBoolean(PrefsKeys.PREMIUM_EFFECTS_ON, false)) }
    var cutiePinkEffectsOn by remember { mutableStateOf(prefs.getBoolean(PrefsKeys.BUBBLE_EFFECTS_ON, false)) }
    var arcadeEffectsOn by remember { mutableStateOf(prefs.getBoolean(PrefsKeys.ARCADE_EFFECTS_ON, false)) }

    LaunchedEffect(hasPremiumEffects, hasCutiePinkEffects, hasArcadeEffects) {
        if (!prefs.getBoolean(PrefsKeys.EFFECTS_INITIALIZED, false) && (hasPremiumEffects || hasCutiePinkEffects || hasArcadeEffects)) {
            val lastPurchased = prefs.getString(PrefsKeys.LAST_PURCHASED_EFFECT, null)
            when {
                lastPurchased == "arcade" && hasArcadeEffects -> {
                    arcadeEffectsOn = true; prefs.edit().putBoolean(PrefsKeys.ARCADE_EFFECTS_ON, true).apply()
                }
                lastPurchased == "bubble" && hasCutiePinkEffects -> {
                    cutiePinkEffectsOn = true; prefs.edit().putBoolean(PrefsKeys.BUBBLE_EFFECTS_ON, true).apply()
                }
                hasPremiumEffects -> {
                    premiumEffectsOn = true; prefs.edit().putBoolean(PrefsKeys.PREMIUM_EFFECTS_ON, true).apply()
                }
                hasCutiePinkEffects -> {
                    cutiePinkEffectsOn = true; prefs.edit().putBoolean(PrefsKeys.BUBBLE_EFFECTS_ON, true).apply()
                }
                hasArcadeEffects -> {
                    arcadeEffectsOn = true; prefs.edit().putBoolean(PrefsKeys.ARCADE_EFFECTS_ON, true).apply()
                }
            }
            prefs.edit().putBoolean(PrefsKeys.EFFECTS_INITIALIZED, true).apply()
        }
    }

    var overlayVisible by remember { mutableStateOf(prefs.getBoolean(PrefsKeys.OVERLAY_VISIBLE, false)) }
    var overlayTouch by remember { mutableStateOf(prefs.getBoolean(PrefsKeys.OVERLAY_TOUCH, false)) }
    var overlayScale by remember { mutableFloatStateOf(prefs.getFloat(PrefsKeys.OVERLAY_SCALE, 1.0f)) }
    var overlayColor by remember { mutableIntStateOf(prefs.getInt(PrefsKeys.OVERLAY_COLOR, 0xFFFF6B00.toInt())) }
    var overlayGranted by remember { mutableStateOf(AndroidSettings.canDrawOverlays(context)) }
    var overlayNudgeDismissed by remember { mutableStateOf(prefs.getBoolean(PrefsKeys.OVERLAY_NUDGE_DISMISSED, false)) }

    val clickCountRepo = remember { ClickCountRepository.getInstance(context) }
    val totalScore by clickCountRepo.totalScore.collectAsState()

    val effectsLifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(effectsLifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) { overlayGranted = AndroidSettings.canDrawOverlays(context) }
            // Activity가 백그라운드로 갈 때 preview 모드 강제 해제 (앱 kill 대비 안전장치)
            if (event == Lifecycle.Event.ON_STOP) {
                prefs.edit().putInt(PrefsKeys.PREVIEW_EFFECT_MODE, -1).commit()
            }
        }
        effectsLifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            effectsLifecycleOwner.lifecycle.removeObserver(observer)
            // EffectsScreen 자체가 dispose될 때도 리셋 (네비게이션 등)
            prefs.edit().putInt(PrefsKeys.PREVIEW_EFFECT_MODE, -1).commit()
        }
    }

    LaunchedEffect(overlayGranted) {
        if (overlayGranted && !overlayVisible) {
            overlayVisible = true
            prefs.edit().putBoolean(PrefsKeys.OVERLAY_VISIBLE, true).apply()
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Image(painter = painterResource(R.drawable.dogakdogak_icon), contentDescription = null,
                modifier = Modifier.size(52.dp).clip(CircleShape))
            Spacer(Modifier.width(12.dp))
            Column {
                Text("도각도각", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = colors.textPrimary)
                Text("ASMR 키보드 사운드", fontSize = 12.sp, color = colors.textSecondary)
            }
        }
        Spacer(Modifier.height(20.dp))

        // 콤보 카운터 ON/OFF
        if (overlayVisible) {
            Row(
                modifier = Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(colors.glassBg.copy(alpha = if (colors.isDark) 0.6f else 0.9f))
                    .border(0.5.dp, colors.glassBorder, RoundedCornerShape(20.dp))
                    .padding(horizontal = 20.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("콤보 카운터", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = colors.textPrimary)
                Switch(
                    checked = true,
                    onCheckedChange = { overlayVisible = false; prefs.edit().putBoolean(PrefsKeys.OVERLAY_VISIBLE, false).apply() },
                    colors = SwitchDefaults.colors(checkedThumbColor = colors.onPrimary, checkedTrackColor = colors.primary,
                        uncheckedThumbColor = colors.textTertiary, uncheckedTrackColor = colors.surface, uncheckedBorderColor = colors.cardBorder)
                )
            }
        } else {
            OverlayNudgeBanner(
                onEnable = {
                    if (overlayGranted) {
                        overlayVisible = true
                        prefs.edit().putBoolean(PrefsKeys.OVERLAY_VISIBLE, true).apply()
                    } else {
                        context.startActivity(Intent(AndroidSettings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            android.net.Uri.parse("package:${context.packageName}")))
                    }
                },
                onDismiss = {}
            )
        }
        Spacer(Modifier.height(12.dp))

        if (showBundlePurchaseDialog) {
            AlertDialog(
                onDismissRequest = { showBundlePurchaseDialog = false },
                title = { Text("이펙트 전체구매", fontWeight = FontWeight.Bold) },
                text = { Text("프리미엄 · 큐티핑크 · ARCADE 이펙트를\n${bundlePrice}에 모두 구매하시겠습니까?") },
                confirmButton = {
                    Button(
                        onClick = {
                            showBundlePurchaseDialog = false
                            scope.launch {
                                val activity = context as? androidx.activity.ComponentActivity ?: return@launch
                                purchaseRepository?.launchPurchase(activity, SwitchType.EFFECTS_BUNDLE_PRODUCT_ID)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = colors.primary, contentColor = colors.onPrimary)
                    ) { Text("구매하기") }
                },
                dismissButton = {
                    TextButton(onClick = { showBundlePurchaseDialog = false }) {
                        Text("취소", color = colors.textSecondary)
                    }
                },
                containerColor = colors.surface,
                titleContentColor = colors.textPrimary,
                textContentColor = colors.textSecondary
            )
        }

        // 콤보 이펙트 통합 카드 (항상 표시)
        GlassCard {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("콤보 카운터 이펙트", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = colors.textPrimary)
                val allEffectsPurchased = hasPremiumEffects && hasCutiePinkEffects && hasArcadeEffects
                if (!allEffectsPurchased) {
                    Button(
                        onClick = { showBundlePurchaseDialog = true },
                        colors = ButtonDefaults.buttonColors(containerColor = colors.primary, contentColor = colors.onPrimary),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        Text("전체구매", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
            Spacer(Modifier.height(12.dp))

            // 프리미엄 이펙트 행
            EffectRow(
                emoji = "✦ ", label = "프리미엄", desc = "파티클 · 진동 · 랜덤 컬러",
                hasPurchased = hasPremiumEffects, isOn = premiumEffectsOn,
                onToggle = { on ->
                    premiumEffectsOn = on
                    val editor = prefs.edit().putBoolean(PrefsKeys.PREMIUM_EFFECTS_ON, on)
                    if (on) { cutiePinkEffectsOn = false; arcadeEffectsOn = false; editor.putBoolean(PrefsKeys.BUBBLE_EFFECTS_ON, false).putBoolean(PrefsKeys.ARCADE_EFFECTS_ON, false) }
                    editor.apply()
                },
                onPurchase = { showEffectPreview = 0 }
            )

            EffectDivider()

            // 큐티핑크 이펙트 행
            EffectRow(
                emoji = "\uD83E\uDE77 ", label = "큐티핑크", desc = "통통 튀는 핑크 겅듀 'ㅁ'",
                hasPurchased = hasCutiePinkEffects, isOn = cutiePinkEffectsOn,
                onToggle = { on ->
                    cutiePinkEffectsOn = on
                    val editor = prefs.edit().putBoolean(PrefsKeys.BUBBLE_EFFECTS_ON, on)
                    if (on) { premiumEffectsOn = false; arcadeEffectsOn = false; editor.putBoolean(PrefsKeys.PREMIUM_EFFECTS_ON, false).putBoolean(PrefsKeys.ARCADE_EFFECTS_ON, false) }
                    editor.apply()
                },
                onPurchase = { showEffectPreview = 1 }
            )

            EffectDivider()

            // Arcade 이펙트 행
            EffectRow(
                emoji = "\uD83C\uDFAE ", label = "ARCADE", desc = "3D 레트로 코인 콤보",
                hasPurchased = hasArcadeEffects, isOn = arcadeEffectsOn,
                onToggle = { on ->
                    arcadeEffectsOn = on
                    val editor = prefs.edit().putBoolean(PrefsKeys.ARCADE_EFFECTS_ON, on)
                    if (on) { premiumEffectsOn = false; cutiePinkEffectsOn = false; editor.putBoolean(PrefsKeys.PREMIUM_EFFECTS_ON, false).putBoolean(PrefsKeys.BUBBLE_EFFECTS_ON, false) }
                    editor.apply()
                },
                onPurchase = { showEffectPreview = 2 }
            )
        }

        // 콤보 카운터 터치 토글
        if (overlayVisible) {
            Spacer(Modifier.height(12.dp))
            GlassCard {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("콤보 카운터 터치", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = colors.textPrimary)
                        Spacer(Modifier.height(4.dp))
                        Text(if (overlayTouch) "터치/드래그 가능" else "터치 투과 (뒤 화면 터치됨)", fontSize = 13.sp, color = colors.textSecondary)
                    }
                    Spacer(Modifier.width(12.dp))
                    Switch(
                        checked = overlayTouch,
                        onCheckedChange = { enabled -> overlayTouch = enabled; prefs.edit().putBoolean(PrefsKeys.OVERLAY_TOUCH, enabled).apply() },
                        colors = SwitchDefaults.colors(checkedThumbColor = colors.onPrimary, checkedTrackColor = colors.primary,
                            uncheckedThumbColor = colors.textTertiary, uncheckedTrackColor = colors.surface, uncheckedBorderColor = colors.cardBorder)
                    )
                }
            }
        }

        // 콤보 카운터 색상 설정
        if (overlayVisible) {
            Spacer(Modifier.height(12.dp))
            val initR = remember(overlayColor) { ((overlayColor shr 16) and 0xFF).toFloat() }
            val initG = remember(overlayColor) { ((overlayColor shr 8) and 0xFF).toFloat() }
            val initB = remember(overlayColor) { (overlayColor and 0xFF).toFloat() }
            var red by remember(overlayColor) { mutableFloatStateOf(initR) }
            var green by remember(overlayColor) { mutableFloatStateOf(initG) }
            var blue by remember(overlayColor) { mutableFloatStateOf(initB) }
            val previewColor = Color(red.toInt(), green.toInt(), blue.toInt())
            GlassCard {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("콤보 카운터 색상", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = colors.textPrimary)
                    Box(modifier = Modifier.size(32.dp).clip(CircleShape).background(previewColor).border(1.dp, colors.cardBorder, CircleShape))
                }
                Spacer(Modifier.height(12.dp))
                val applyColor = {
                    @Suppress("USELESS_CAST")
                    val newColor = (0xFF shl 24) or (red.toInt() shl 16) or (green.toInt() shl 8) or blue.toInt()
                    overlayColor = newColor
                    prefs.edit().putInt(PrefsKeys.OVERLAY_COLOR, newColor).apply()
                }
                ColorSliderRow(label = "R", value = red, color = Color.Red, onValueChange = { red = it; applyColor() }, onValueChangeFinished = {})
                Spacer(Modifier.height(6.dp))
                ColorSliderRow(label = "G", value = green, color = Color(0xFF00C853), onValueChange = { green = it; applyColor() }, onValueChangeFinished = {})
                Spacer(Modifier.height(6.dp))
                ColorSliderRow(label = "B", value = blue, color = Color(0xFF2979FF), onValueChange = { blue = it; applyColor() }, onValueChangeFinished = {})
                Spacer(Modifier.height(8.dp))
                Text("#${"%02X".format(red.toInt())}${"%02X".format(green.toInt())}${"%02X".format(blue.toInt())}",
                    fontSize = 13.sp, fontWeight = FontWeight.Medium, color = colors.textTertiary)
            }
        }

        // 오버레이 크기 설정
        if (overlayVisible) {
            Spacer(Modifier.height(12.dp))
            var localScale by remember(overlayScale) { mutableFloatStateOf(overlayScale) }
            GlassCard {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("콤보 카운터 크기", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = colors.textPrimary)
                    Text("${(localScale * 100).toInt()}%", fontSize = 15.sp, fontWeight = FontWeight.Medium, color = colors.primary)
                }
                Spacer(Modifier.height(8.dp))
                Slider(
                    value = localScale,
                    onValueChange = { localScale = it; overlayScale = it; prefs.edit().putFloat(PrefsKeys.OVERLAY_SCALE, it).apply() },
                    onValueChangeFinished = {},
                    valueRange = 0.5f..2.0f,
                    colors = SliderDefaults.colors(thumbColor = colors.primary, activeTrackColor = colors.primary, inactiveTrackColor = colors.surface)
                )
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("50%", fontSize = 11.sp, color = colors.textTertiary)
                    Text("200%", fontSize = 11.sp, color = colors.textTertiary)
                }
            }
        }

        Spacer(Modifier.height(32.dp))
    }

    // 콤보 이펙트 미리보기 바텀시트
    if (showEffectPreview >= 0) {
        EffectPreviewSheet(
            prefs = prefs,
            audioEngine = audioEngine,
            hasPremiumEffects = hasPremiumEffects,
            hasCutiePinkEffects = hasCutiePinkEffects,
            hasArcadeEffects = hasArcadeEffects,
            purchaseRepository = purchaseRepository,
            initialTab = showEffectPreview,
            formattedPrice = singlePrice,
            onDismiss = {
                showEffectPreview = -1
                // 동기 쓰기: DisposableEffect보다 먼저 확실히 리셋
                prefs.edit().putInt(PrefsKeys.PREVIEW_EFFECT_MODE, -1).commit()
            }
        )
    }
}

// ── 이펙트 행 공통 컴포넌트 ──

@Composable
private fun EffectRow(
    emoji: String, label: String, desc: String,
    hasPurchased: Boolean, isOn: Boolean,
    onToggle: (Boolean) -> Unit, onPurchase: () -> Unit,
    emojiColor: Color? = null,
) {
    val colors = LocalDogakdogakColors.current
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (emojiColor != null) Text(emoji, fontSize = 13.sp, color = emojiColor)
                else Text(emoji, fontSize = 13.sp, color = colors.primary)
                Text(label, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = colors.textPrimary)
                if (hasPurchased) {
                    Spacer(Modifier.width(6.dp))
                    Text("보유", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = colors.primary,
                        modifier = Modifier.clip(RoundedCornerShape(4.dp)).background(colors.primary.copy(alpha = 0.15f)).padding(horizontal = 5.dp, vertical = 1.dp))
                }
            }
            Spacer(Modifier.height(2.dp))
            Text(desc, fontSize = 12.sp, color = colors.textTertiary)
        }
        Spacer(Modifier.width(12.dp))
        if (hasPurchased) {
            Switch(checked = isOn, onCheckedChange = onToggle,
                colors = SwitchDefaults.colors(checkedThumbColor = colors.onPrimary, checkedTrackColor = colors.primary,
                    uncheckedThumbColor = colors.textTertiary, uncheckedTrackColor = colors.surface, uncheckedBorderColor = colors.cardBorder))
        } else {
            Box(modifier = Modifier.clip(RoundedCornerShape(20.dp)).border(1.dp, colors.primary.copy(alpha = 0.5f), RoundedCornerShape(20.dp))
                .clickable { onPurchase() }.padding(horizontal = 10.dp, vertical = 6.dp)) {
                Text("사용해보기", fontSize = 12.sp, color = colors.primary, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun EffectDivider() {
    val colors = LocalDogakdogakColors.current
    Spacer(Modifier.height(10.dp))
    Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(colors.cardBorder.copy(alpha = 0.5f)))
    Spacer(Modifier.height(10.dp))
}

@Composable
private fun OverlayNudgeBanner(onEnable: () -> Unit, onDismiss: () -> Unit) {
    val colors = LocalDogakdogakColors.current
    Box(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
            .background(colors.primary.copy(alpha = 0.08f))
            .border(1.dp, colors.primary.copy(alpha = 0.25f), RoundedCornerShape(16.dp)).padding(14.dp)
    ) {
        Column {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(text = "\uD83C\uDFAE", fontSize = 18.sp)
                Spacer(Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("콤보 카운터를 켜보세요!", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = colors.textPrimary)
                    Text("타이핑할수록 콤보가 쌓여요", fontSize = 12.sp, color = colors.textSecondary)
                }
                Box(modifier = Modifier.size(28.dp).clip(CircleShape).clickable { onDismiss() }, contentAlignment = Alignment.Center) {
                    Text("✕", fontSize = 14.sp, color = colors.textTertiary)
                }
            }
            Spacer(Modifier.height(10.dp))
            Button(onClick = onEnable, modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = colors.primary, contentColor = colors.onPrimary),
                shape = RoundedCornerShape(10.dp)) { Text("지금 켜기", fontWeight = FontWeight.SemiBold) }
        }
    }
}

