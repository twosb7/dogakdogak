package helium314.keyboard.latin.dogakdogak

import android.content.Context
import android.view.inputmethod.InputMethodManager
import android.provider.Settings as AndroidSettings
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding

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
fun PulsingDot(color: Color) {
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
fun ColorSliderRow(
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
//  ChillGradientText — 가로로 천천히 흐르는 그래디언트 텍스트
// ═══════════════════════════════════════════════════════════════════

@Composable
fun ChillGradientText(
    text: String,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 48.sp,
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
    val brush = Brush.linearGradient(
        colors = gradientColors,
        start = Offset(shift, 0f),
        end = Offset(shift + gradientWidth, 0f),
    )

    Text(
        text = text,
        fontSize = fontSize,
        fontWeight = fontWeight,
        fontFamily = fontFamily,
        style = TextStyle(brush = brush),
        modifier = modifier,
    )
}

// ═══════════════════════════════════════════════════════════════════
//  Helper Functions
// ═══════════════════════════════════════════════════════════════════

/** IME가 활성화(enabled)되어 있는지 확인 */
fun isImeEnabled(context: Context): Boolean {
    val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
    val packageName = context.packageName
    return imm.enabledInputMethodList.any { it.packageName == packageName }
}

/** IME가 현재 기본 키보드로 선택되어 있는지 확인 */
fun isImeSelected(context: Context): Boolean {
    val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
    val packageName = context.packageName
    val imi = imm.inputMethodList.firstOrNull { it.packageName == packageName } ?: return false
    val currentImeId = AndroidSettings.Secure.getString(
        context.contentResolver, AndroidSettings.Secure.DEFAULT_INPUT_METHOD
    ) ?: return false
    return imi.id == currentImeId
}
