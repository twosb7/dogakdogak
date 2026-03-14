package helium314.keyboard.latin.dogakdogak

import android.content.SharedPreferences
import android.text.InputType
import android.view.Gravity
import android.widget.EditText
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import helium314.keyboard.latin.AudioAndHapticFeedbackManager
import helium314.keyboard.latin.R
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

internal data class SoundPreviewBehavior(
    val switchForLiveTyping: SwitchType,
    val shouldReplaySoundOnTextChange: Boolean
)

internal fun soundPreviewBehavior(
    previewSwitch: SwitchType,
    isDogakdogakImeSelected: Boolean
): SoundPreviewBehavior = SoundPreviewBehavior(
    switchForLiveTyping = previewSwitch,
    shouldReplaySoundOnTextChange = !isDogakdogakImeSelected
)

@Composable
internal fun SoundScreen(prefs: SharedPreferences, purchaseRepository: PurchaseRepository? = null) {
    val colors = LocalDogakdogakColors.current
    val audioEngine = AudioAndHapticFeedbackManager.getInstance().audioEngine
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val savedSwitchName = prefs.getString(PrefsKeys.SWITCH_TYPE, SwitchType.getDefaultSwitch().name)
        ?: SwitchType.getDefaultSwitch().name
    var selectedSwitch by remember {
        mutableStateOf(
            try { SwitchType.valueOf(savedSwitchName) } catch (_: Exception) { SwitchType.getDefaultSwitch() }
        )
    }

    val purchasedSwitches by (purchaseRepository?.purchasedSwitchesFlow
        ?: kotlinx.coroutines.flow.flowOf(emptySet<String>())).collectAsState(initial = emptySet())

    var previewSwitchType by remember { mutableStateOf<SwitchType?>(null) }
    var toastSwitchType by remember { mutableStateOf<SwitchType?>(null) }
    var previewJob by remember { mutableStateOf<Job?>(null) }

    var bundlePrice by remember { mutableStateOf("2,990원") }
    var singlePrice by remember { mutableStateOf("990원") }
    LaunchedEffect(Unit) {
        val sampleProductId = SwitchType.getPremiumSwitches().firstOrNull()?.productId ?: return@LaunchedEffect
        val prices = purchaseRepository?.fetchProductPrices(
            listOf(SwitchType.BUNDLE_PRODUCT_ID, sampleProductId)
        ) ?: emptyMap()
        prices[SwitchType.BUNDLE_PRODUCT_ID]?.takeIf { it.isNotBlank() }?.let { bundlePrice = it }
        prices[sampleProductId]?.takeIf { it.isNotBlank() }?.let { singlePrice = it }
    }

    fun selectSwitch(sw: SwitchType) {
        selectedSwitch = sw
        audioEngine?.setCurrentSwitch(sw)
        prefs.edit().putString(PrefsKeys.SWITCH_TYPE, sw.name).apply()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Image(
                    painter = painterResource(R.drawable.dogakdogak_icon),
                    contentDescription = null,
                    modifier = Modifier.size(52.dp).clip(CircleShape)
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
                    Text("스위치 타입", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = colors.textPrimary)
                    Spacer(Modifier.width(8.dp))
                    Text("(클릭 시 미리듣기)", fontSize = 12.sp, color = colors.textTertiary)
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
                            .background(if (isSelected) colors.primary.copy(alpha = 0.12f) else Color.Transparent)
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
                                    Icon(Icons.Default.Lock, contentDescription = "잠금", modifier = Modifier.size(14.dp), tint = colors.textTertiary)
                                }
                            }
                            Text(text = switchType.description, fontSize = 12.sp, color = colors.textSecondary)
                        }
                        if (isSelected) {
                            Icon(Icons.Default.Check, contentDescription = "선택됨", modifier = Modifier.size(20.dp), tint = colors.primary)
                        }
                    }
                }

                val allPremiumPurchased = SwitchType.getPremiumSwitches().all { purchasedSwitches.contains(it.name) }
                if (!allPremiumPurchased) {
                    Spacer(Modifier.height(12.dp))
                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                val activity = context as? androidx.activity.ComponentActivity ?: return@launch
                                purchaseRepository?.launchPurchase(activity, SwitchType.BUNDLE_PRODUCT_ID)
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.primary),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("조약돌 전체 구매 ($bundlePrice)", fontWeight = FontWeight.SemiBold)
                    }
                }
            }
            Spacer(Modifier.height(32.dp))
        }

        // 구매 유도 토스트
        AnimatedVisibility(
            visible = toastSwitchType != null,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp),
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut()
        ) {
            toastSwitchType?.let { switchType ->
                LaunchedEffect(switchType) { delay(3000); toastSwitchType = null }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)
                        .clip(RoundedCornerShape(14.dp)).background(Color(0xE6222222))
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("소리가 마음에 들면 구매할래요?", color = Color.White, fontSize = 14.sp,
                        fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f, fill = false))
                    Spacer(Modifier.width(12.dp))
                    Text("좋아요", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = colors.onPrimary,
                        modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(colors.primary)
                            .clickable {
                                toastSwitchType = null
                                scope.launch {
                                    val activity = context as? androidx.activity.ComponentActivity ?: return@launch
                                    purchaseRepository?.launchPurchase(activity, switchType.productId ?: return@launch)
                                }
                            }.padding(horizontal = 12.dp, vertical = 6.dp))
                }
            }
        }
    }

    // 미리듣기 다이얼로그 (바텀시트 스타일)
    previewSwitchType?.let { switchType ->
        val previewBehavior = remember(switchType, context) {
            soundPreviewBehavior(
                previewSwitch = switchType,
                isDogakdogakImeSelected = isImeSelected(context)
            )
        }

        fun dismissPreview() {
            previewSwitchType = null
            toastSwitchType = switchType
        }
        BackHandler { dismissPreview() }
        androidx.compose.ui.window.Dialog(
            onDismissRequest = { dismissPreview() },
            properties = androidx.compose.ui.window.DialogProperties(
                usePlatformDefaultWidth = false,
                decorFitsSystemWindows = false
            )
        ) {
            // Dialog 윈도우의 softInputMode를 ALWAYS_VISIBLE로 설정
            val dialogView = androidx.compose.ui.platform.LocalView.current
            androidx.compose.runtime.DisposableEffect(Unit) {
                val dialogWindow = (dialogView.parent as? androidx.compose.ui.window.DialogWindowProvider)?.window
                dialogWindow?.setSoftInputMode(
                    android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE or
                    android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
                )
                onDispose {}
            }

            Box(
                modifier = Modifier.fillMaxSize()
                    .systemBarsPadding()
                    .imePadding(),
                contentAlignment = Alignment.BottomCenter
            ) {
                // 스크림 (배경 터치 시 닫기)
                Box(modifier = Modifier.fillMaxSize().clickable(
                    indication = null,
                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
                ) { dismissPreview() })
                // 시트 콘텐츠
                Column(
                    modifier = Modifier.fillMaxWidth()
                        .background(colors.surface, RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                        .padding(horizontal = 20.dp).padding(top = 24.dp, bottom = 32.dp)
                ) {
                    androidx.compose.runtime.DisposableEffect(audioEngine, selectedSwitch, previewBehavior) {
                        audioEngine?.setCurrentSwitch(previewBehavior.switchForLiveTyping)
                        onDispose {
                            audioEngine?.setCurrentSwitch(selectedSwitch)
                        }
                    }

                    // 드래그 핸들
                    Box(
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                            .width(32.dp).height(4.dp)
                            .background(colors.textTertiary.copy(alpha = 0.4f), RoundedCornerShape(2.dp))
                    )
                    Spacer(Modifier.height(16.dp))
                    Text("${switchType.displayNameKo} 미리듣기", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = colors.textPrimary)
                    Spacer(Modifier.height(4.dp))
                    Text("아래에 타이핑해서 소리를 들어보세요", fontSize = 13.sp, color = colors.textSecondary)
                    Spacer(Modifier.height(16.dp))

                    val textPrimaryColor = colors.textPrimary.toArgb()
                    val hintColor = colors.textTertiary.toArgb()
                    var editTextRef by remember { mutableStateOf<EditText?>(null) }
                    LaunchedEffect(editTextRef) {
                        val et = editTextRef ?: return@LaunchedEffect
                        delay(300)
                        et.requestFocus()
                        val imm = et.context.getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
                        imm.showSoftInput(et, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
                    }
                    AndroidView(
                        factory = { ctx ->
                            EditText(ctx).apply {
                                hint = "여기에 타이핑하세요!"
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
                                        if (added > 0 && previewBehavior.shouldReplaySoundOnTextChange) {
                                            repeat(added.coerceAtMost(3)) { audioEngine?.playSwitchSound(switchType) }
                                        }
                                    }
                                })
                                isFocusableInTouchMode = true
                            }
                        },
                        update = { et -> editTextRef = et },
                        modifier = Modifier.fillMaxWidth().height(150.dp)
                            .border(1.dp, colors.glassBorder, RoundedCornerShape(14.dp)).clip(RoundedCornerShape(14.dp))
                    )
                    Spacer(Modifier.height(16.dp))
                    Button(
                        onClick = {
                            dismissPreview()
                            scope.launch {
                                val activity = context as? androidx.activity.ComponentActivity ?: return@launch
                                purchaseRepository?.launchPurchase(activity, switchType.productId ?: return@launch)
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = colors.primary, contentColor = colors.onPrimary),
                        shape = RoundedCornerShape(12.dp)
                    ) { Text("구매하기 ($singlePrice)", fontWeight = FontWeight.SemiBold) }
                }
            }
        }
    }
}
