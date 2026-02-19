package helium314.keyboard.latin.dogakdogak

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import helium314.keyboard.latin.R

// -- Pretendard FontFamily --
val PretendardFamily = FontFamily(
    Font(R.font.pretendard_regular, FontWeight.Normal),
    Font(R.font.pretendard_medium, FontWeight.Medium),
    Font(R.font.pretendard_semibold, FontWeight.SemiBold),
    Font(R.font.pretendard_bold, FontWeight.Bold),
)

// -- 테마 타입 --
enum class AppThemeType {
    FORGE,  // 인더스트리얼 다크
    MAISON, // 럭셔리 라이트
    BLACK   // 삼성 키보드 스타일 다크
}

// -- 시맨틱 색상 정의 --
@Immutable
data class DogakdogakColors(
    val background: Color,
    val surface: Color,
    val cardBorder: Color,
    val primary: Color,
    val secondary: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val success: Color,
    val error: Color,
    val gold: Color = Color(0xFFFFD700),
    val silver: Color = Color(0xFFC0C0C0),
    val bronze: Color = Color(0xFFCD7F32),
    val glassBg: Color,
    val glassBorder: Color,
    val isDark: Boolean,
)

// -- FORGE 팔레트 (인더스트리얼 다크) --
val ForgeColors = DogakdogakColors(
    background = Color(0xFF1A1A1A),
    surface = Color(0xFF2A2A2A),
    cardBorder = Color(0xFF444444),
    primary = Color(0xFFFF6B00),
    secondary = Color(0xFFFFB800),
    textPrimary = Color(0xFFFFFFFF),
    textSecondary = Color(0xFFAAAAAA),
    textTertiary = Color(0xFF777777),
    success = Color(0xFF4CAF50),
    error = Color(0xFFFF3B30),
    glassBg = Color(0xFF2A2A2A),
    glassBorder = Color(0xFF444444),
    isDark = true,
)

// -- MAISON 팔레트 (럭셔리 라이트) --
val MaisonColors = DogakdogakColors(
    background = Color(0xFFFFF8F0),
    surface = Color(0xFFFFFFFF),
    cardBorder = Color(0xFFE8D5C4),
    primary = Color(0xFFB76E79),
    secondary = Color(0xFFC9A96E),
    textPrimary = Color(0xFF3D2C2E),
    textSecondary = Color(0xFF8B7D7B),
    textTertiary = Color(0xFFB0A3A0),
    success = Color(0xFF7CB69D),
    error = Color(0xFFC45B5B),
    glassBg = Color(0xFFFFFFFF),
    glassBorder = Color(0xFFE8D5C4),
    isDark = false,
)

// -- BLACK 팔레트 (삼성 키보드 스타일 다크) --
val BlackColors = DogakdogakColors(
    background = Color(0xFF121212),
    surface = Color(0xFF1E1E1E),
    cardBorder = Color(0xFF333333),
    primary = Color(0xFF4A8CFF),
    secondary = Color(0xFF6AB4FF),
    textPrimary = Color(0xFFFFFFFF),
    textSecondary = Color(0xFF999999),
    textTertiary = Color(0xFF666666),
    success = Color(0xFF4CAF50),
    error = Color(0xFFFF3B30),
    glassBg = Color(0xFF1E1E1E),
    glassBorder = Color(0xFF333333),
    isDark = true,
)

// -- CompositionLocal --
val LocalDogakdogakColors = staticCompositionLocalOf { ForgeColors }

// -- Pretendard Typography --
// Pretendard는 자체 자간이 잘 잡힌 폰트이므로 letterSpacing = 0.sp 필수
// Material3 기본값(Roboto 기준 0.5sp 등)을 그대로 쓰면 자간이 넓어짐
// lineHeight를 fontSize × 1.2~1.3 으로 설정하여 Material3 기본값(1.5x)보다 타이트하게
private fun pretendardTypography() = Typography(
    displayLarge = TextStyle(fontFamily = PretendardFamily, fontWeight = FontWeight.Bold, fontSize = 57.sp, lineHeight = 64.sp, letterSpacing = 0.sp),
    displayMedium = TextStyle(fontFamily = PretendardFamily, fontWeight = FontWeight.Bold, fontSize = 45.sp, lineHeight = 52.sp, letterSpacing = 0.sp),
    displaySmall = TextStyle(fontFamily = PretendardFamily, fontWeight = FontWeight.Bold, fontSize = 36.sp, lineHeight = 42.sp, letterSpacing = 0.sp),
    headlineLarge = TextStyle(fontFamily = PretendardFamily, fontWeight = FontWeight.Bold, fontSize = 32.sp, lineHeight = 38.sp, letterSpacing = 0.sp),
    headlineMedium = TextStyle(fontFamily = PretendardFamily, fontWeight = FontWeight.SemiBold, fontSize = 28.sp, lineHeight = 34.sp, letterSpacing = 0.sp),
    headlineSmall = TextStyle(fontFamily = PretendardFamily, fontWeight = FontWeight.SemiBold, fontSize = 24.sp, lineHeight = 30.sp, letterSpacing = 0.sp),
    titleLarge = TextStyle(fontFamily = PretendardFamily, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 28.sp, letterSpacing = 0.sp),
    titleMedium = TextStyle(fontFamily = PretendardFamily, fontWeight = FontWeight.Medium, fontSize = 16.sp, lineHeight = 22.sp, letterSpacing = 0.sp),
    titleSmall = TextStyle(fontFamily = PretendardFamily, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 19.sp, letterSpacing = 0.sp),
    bodyLarge = TextStyle(fontFamily = PretendardFamily, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 22.sp, letterSpacing = 0.sp),
    bodyMedium = TextStyle(fontFamily = PretendardFamily, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 19.sp, letterSpacing = 0.sp),
    bodySmall = TextStyle(fontFamily = PretendardFamily, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.sp),
    labelLarge = TextStyle(fontFamily = PretendardFamily, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 19.sp, letterSpacing = 0.sp),
    labelMedium = TextStyle(fontFamily = PretendardFamily, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.sp),
    labelSmall = TextStyle(fontFamily = PretendardFamily, fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 15.sp, letterSpacing = 0.sp),
)

// -- dogakdogakTheme Composable --
@Composable
fun DogakdogakTheme(
    themeType: AppThemeType = AppThemeType.FORGE,
    content: @Composable () -> Unit,
) {
    val colors = when (themeType) {
        AppThemeType.FORGE -> ForgeColors
        AppThemeType.MAISON -> MaisonColors
        AppThemeType.BLACK -> BlackColors
    }

    val colorScheme = if (colors.isDark) {
        darkColorScheme(
            background = colors.background,
            surface = colors.surface,
            primary = colors.primary,
            secondary = colors.success,
            error = colors.error,
            onBackground = colors.textPrimary,
            onSurface = colors.textPrimary,
        )
    } else {
        lightColorScheme(
            background = colors.background,
            surface = colors.surface,
            primary = colors.primary,
            secondary = colors.success,
            error = colors.error,
            onBackground = colors.textPrimary,
            onSurface = colors.textPrimary,
        )
    }

    CompositionLocalProvider(LocalDogakdogakColors provides colors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = pretendardTypography(),
            content = content,
        )
    }
}
