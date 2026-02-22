package helium314.keyboard.latin.dogakdogak

import android.graphics.Color

/**
 * ComboOverlayView 및 OverlayManager에서 사용하는 색상 상수.
 */
object OverlayColors {

    // ── Premium: HSV 기반 연속 색상 ──
    val PREMIUM_COLORS = intArrayOf(
        0xFFFF3B30.toInt(), 0xFFFF6B6B.toInt(), 0xFFFF6E40.toInt(),
        0xFFFF9500.toInt(), 0xFFFF9F0A.toInt(),
        0xFFFFCC00.toInt(), 0xFFFFD60A.toInt(), 0xFFCDDC39.toInt(),
        0xFFA8D948.toInt(), 0xFF00C853.toInt(),
        0xFF30D158.toInt(), 0xFF34C759.toInt(), 0xFF66BB6A.toInt(),
        0xFF64FFDA.toInt(), 0xFF00BCD4.toInt(),
        0xFF00E5FF.toInt(), 0xFF42A5F5.toInt(), 0xFF0A84FF.toInt(),
        0xFF007AFF.toInt(), 0xFF5856D6.toInt(),
        0xFF7C4DFF.toInt(), 0xFFBF5AF2.toInt(), 0xFFEA80FC.toInt(),
        0xFFE040FB.toInt(), 0xFFF06292.toInt(),
        0xFFEC407A.toInt(), 0xFFFF375F.toInt(), 0xFFFF2D55.toInt(),
        0xFFFF8A65.toInt(), 0xFFFFAB40.toInt(),
    )

    // ── Arcade: 3D 레트로 무지개 리버 그래디언트 ──
    val ARCADE_RIVER_COLORS = intArrayOf(
        0xFFB500FF.toInt(),  // Purple
        0xFF00D2FF.toInt(),  // Cyan
        0xFFFFEA00.toInt(),  // Yellow
        0xFFFF007B.toInt(),  // Pink
        0xFFB500FF.toInt(),  // Purple (repeat for seamless loop)
    )

    // ── Arcade: 금/은화 파티클 색상 ──
    val ARCADE_PARTICLE_COLORS = intArrayOf(
        0xFFFFD700.toInt(),  // Gold
        0xFFFFC107.toInt(),  // Amber gold
        0xFFB8860B.toInt(),  // Dark gold
        0xFFC0C0C0.toInt(),  // Silver
        0xFFE0E0E0.toInt(),  // Light silver
        0xFF808080.toInt(),  // Dark silver
    )

    // ── 콤보 레벨별 색상 ──
    fun comboColor(combo: Int): Int = when {
        combo >= 1000 -> 0xFFFFD700.toInt()
        combo >= 900 -> 0xFFFF1744.toInt()
        combo >= 800 -> 0xFF7C4DFF.toInt()
        combo >= 700 -> 0xFFE040FB.toInt()
        combo >= 600 -> 0xFF00E5FF.toInt()
        combo >= 500 -> 0xFFFFD60A.toInt()
        combo >= 400 -> 0xFFFF453A.toInt()
        combo >= 300 -> 0xFFFF9F0A.toInt()
        combo >= 200 -> 0xFFBF5AF2.toInt()
        combo >= 100 -> 0xFF0A84FF.toInt()
        combo >= 50 -> 0xFF30D158.toInt()
        combo >= 20 -> 0xFFA8D948.toInt()
        combo >= 6 -> 0xFFE0E8B0.toInt()
        else -> 0xFFFFFFFF.toInt()
    }

    fun scorePopupColor(combo: Int): Int = when {
        combo >= 1000 -> 0xFF00E5FF.toInt()
        combo >= 900 -> 0xFF64FFDA.toInt()
        combo >= 800 -> 0xFFFFAB40.toInt()
        combo >= 700 -> 0xFF69F0AE.toInt()
        combo >= 600 -> 0xFFFF9F0A.toInt()
        combo >= 500 -> 0xFF7C4DFF.toInt()
        combo >= 400 -> 0xFF00E5FF.toInt()
        combo >= 300 -> 0xFF0A84FF.toInt()
        combo >= 200 -> 0xFFFFD60A.toInt()
        combo >= 100 -> 0xFFFF9F0A.toInt()
        combo >= 50 -> 0xFFFF6B6B.toInt()
        combo >= 20 -> 0xFF42A5F5.toInt()
        combo >= 6 -> 0xFFFF9F0A.toInt()
        else -> 0xFFFFCC00.toInt()
    }

    // ── Konfetti 버스트 색상 ──
    val ARCADE_KONFETTI_COLORS = listOf(
        0xFFFFD700.toInt(), 0xFFFFC107.toInt(),
        0xFFB8860B.toInt(), 0xFFC0C0C0.toInt(),
        0xFFE0E0E0.toInt(), 0xFF808080.toInt()
    )

    val CUTIE_PINK_KONFETTI_COLORS = listOf(
        0xFFFF69B4.toInt(), 0xFFFF1493.toInt(), 0xFFFFB6C1.toInt(),
        0xFFF06292.toInt(), 0xFFEC407A.toInt(), 0xFFFF80AB.toInt()
    )

    val PREMIUM_KONFETTI_COLORS = listOf(
        0xFFFF453A.toInt(), 0xFFFF9F0A.toInt(), 0xFFFFD60A.toInt(),
        0xFF30D158.toInt(), 0xFF0A84FF.toInt(), 0xFFBF5AF2.toInt(),
        0xFFFF375F.toInt()
    )

    val MINI_ARCADE_KONFETTI_COLORS = listOf(
        0xFFFFD700.toInt(), 0xFFC0C0C0.toInt(), 0xFFFFC107.toInt()
    )

    val MINI_CUTIE_PINK_KONFETTI_COLORS = listOf(
        0xFFFF69B4.toInt(), 0xFFFFB6C1.toInt(), 0xFFF06292.toInt()
    )

    val MINI_PREMIUM_KONFETTI_COLORS = listOf(
        0xFFFF9F0A.toInt(), 0xFFFFD60A.toInt(), 0xFF0A84FF.toInt(), 0xFFBF5AF2.toInt()
    )
}
