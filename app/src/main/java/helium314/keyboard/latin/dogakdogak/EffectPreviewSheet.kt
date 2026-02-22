package helium314.keyboard.latin.dogakdogak

import android.content.Context
import android.content.SharedPreferences
import android.text.InputType
import android.view.Gravity
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun EffectPreviewSheet(
    prefs: SharedPreferences,
    audioEngine: AudioEngine?,
    hasPremiumEffects: Boolean, hasCutiePinkEffects: Boolean, hasArcadeEffects: Boolean,
    purchaseRepository: PurchaseRepository?,
    initialTab: Int = 0,
    formattedPrice: String = "1,490원",
    onDismiss: () -> Unit,
) {
    val colors = LocalDogakdogakColors.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val focusManager = LocalFocusManager.current

    BackHandler { focusManager.clearFocus(); onDismiss() }

    ModalBottomSheet(
        onDismissRequest = { focusManager.clearFocus(); onDismiss() },
        sheetState = sheetState, containerColor = colors.surface, contentColor = colors.textPrimary
    ) {
        var selectedPreview by remember { mutableIntStateOf(initialTab.coerceIn(0, 2)) }
        val origPremiumOn = remember { prefs.getBoolean(PrefsKeys.PREMIUM_EFFECTS_ON, false) }
        val origCutiePinkOn = remember { prefs.getBoolean(PrefsKeys.BUBBLE_EFFECTS_ON, false) }
        val origArcadeOn = remember { prefs.getBoolean(PrefsKeys.ARCADE_EFFECTS_ON, false) }

        // 미리보기 모드: 구매 플래그(PREMIUM_EFFECTS 등)는 절대 조작하지 않음.
        // 전용 PREVIEW_EFFECT_MODE 키로 LatinIME에 미리보기 상태를 전달.
        LaunchedEffect(selectedPreview) {
            prefs.edit()
                .putInt(PrefsKeys.PREVIEW_EFFECT_MODE, selectedPreview)
                .putLong(PrefsKeys.PREVIEW_EFFECT_TIMESTAMP, System.currentTimeMillis())
                .apply()
        }

        DisposableEffect(Unit) {
            onDispose {
                // commit() (동기 쓰기)으로 프로세스 종료 전 반드시 디스크에 기록
                prefs.edit()
                    .putInt(PrefsKeys.PREVIEW_EFFECT_MODE, -1)
                    .putBoolean(PrefsKeys.PREMIUM_EFFECTS_ON, origPremiumOn)
                    .putBoolean(PrefsKeys.BUBBLE_EFFECTS_ON, origCutiePinkOn)
                    .putBoolean(PrefsKeys.ARCADE_EFFECTS_ON, origArcadeOn)
                    .commit()
            }
        }

        val currentSwitch = remember {
            val name = prefs.getString(PrefsKeys.SWITCH_TYPE, SwitchType.getDefaultSwitch().name) ?: SwitchType.getDefaultSwitch().name
            try { SwitchType.valueOf(name) } catch (_: Exception) { SwitchType.getDefaultSwitch() }
        }

        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 32.dp)) {
            Text("콤보 카운터 이펙트 미리보기", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = colors.textPrimary)
            Spacer(Modifier.height(4.dp))
            Text("이펙트를 선택하고 빠르게 타이핑하세요", fontSize = 13.sp, color = colors.textSecondary)
            Spacer(Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(colors.cardBorder.copy(alpha = 0.25f)).padding(3.dp),
                horizontalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                listOf("✦  프리미엄", "\uD83E\uDE77  핑크큐티", "\uD83C\uDFAE  ARCADE").forEachIndexed { i, label ->
                    Box(modifier = Modifier.weight(1f).clip(RoundedCornerShape(8.dp))
                        .background(if (selectedPreview == i) colors.primary else Color.Transparent)
                        .clickable { selectedPreview = i }.padding(vertical = 9.dp), contentAlignment = Alignment.Center) {
                        Text(label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                            color = if (selectedPreview == i) colors.onPrimary else colors.textSecondary)
                    }
                }
            }
            Spacer(Modifier.height(14.dp))

            val textPrimaryColor = colors.textPrimary.toArgb()
            val hintColor = colors.textTertiary.toArgb()
            var editTextRef by remember { mutableStateOf<EditText?>(null) }
            LaunchedEffect(editTextRef) {
                val et = editTextRef ?: return@LaunchedEffect
                delay(300)
                et.requestFocus()
                val imm = et.context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                imm.showSoftInput(et, InputMethodManager.SHOW_IMPLICIT)
            }
            AndroidView(
                factory = { ctx ->
                    EditText(ctx).apply {
                        hint = "여기에 타이핑하세요!"; setHintTextColor(hintColor); setTextColor(textPrimaryColor)
                        background = null; gravity = Gravity.TOP or Gravity.START
                        inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
                        setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 16f)
                        val pad = (16 * resources.displayMetrics.density).toInt(); setPadding(pad, pad, pad, pad)
                        addTextChangedListener(object : android.text.TextWatcher {
                            private var prevLen = 0
                            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) { prevLen = s?.length ?: 0 }
                            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                            override fun afterTextChanged(s: android.text.Editable?) {
                                val added = (s?.length ?: 0) - prevLen
                                if (added > 0) repeat(added.coerceAtMost(3)) { audioEngine?.playSwitchSound(currentSwitch) }
                            }
                        })
                        isFocusableInTouchMode = true
                    }
                },
                update = { et -> editTextRef = et },
                modifier = Modifier.fillMaxWidth().height(130.dp).border(1.dp, colors.glassBorder, RoundedCornerShape(14.dp)).clip(RoundedCornerShape(14.dp))
            )

            val needsPurchase = when (selectedPreview) { 0 -> !hasPremiumEffects; 1 -> !hasCutiePinkEffects; else -> !hasArcadeEffects }
            val productId = when (selectedPreview) { 0 -> SwitchType.PREMIUM_EFFECTS_PRODUCT_ID; 1 -> SwitchType.CUTIE_PINK_EFFECTS_PRODUCT_ID; else -> SwitchType.ARCADE_EFFECTS_PRODUCT_ID }
            if (needsPurchase) {
                Spacer(Modifier.height(14.dp))
                Button(
                    onClick = { focusManager.clearFocus(); onDismiss()
                        val activity = context as? androidx.activity.ComponentActivity ?: return@Button
                        scope.launch { purchaseRepository?.launchPurchase(activity, productId) }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = colors.primary, contentColor = colors.onPrimary),
                    shape = RoundedCornerShape(12.dp)
                ) { Text("구매하기 ($formattedPrice)", fontWeight = FontWeight.SemiBold) }
            }
        }
    }
}
