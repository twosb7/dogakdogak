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

    // ── Chill: 따뜻한 lo-fi 파스텔 그래디언트 ──
    val CHILL_GRADIENT_COLORS = intArrayOf(
        0xFFE8B878.toInt(),  // Warm amber
        0xFFF5E0C0.toInt(),  // Warm cream
        0xFFE8A8A8.toInt(),  // Dusty rose
        0xFFA8D8B0.toInt(),  // Sage green
        0xFFB0C8E0.toInt(),  // Muted sky
        0xFFD4B8E8.toInt(),  // Soft lavender
        0xFFE8B878.toInt(),  // Warm amber (repeat)
    )
    val CHILL_GRADIENT_POSITIONS = floatArrayOf(
        0f, 0.17f, 0.33f, 0.50f, 0.67f, 0.83f, 1f
    )

    val CHILL_PARTICLE_COLORS = intArrayOf(
        0xFFE8B878.toInt(),  // Warm amber
        0xFFD4B8E8.toInt(),  // Soft lavender
        0xFFE8A8A8.toInt(),  // Dusty rose
        0xFFA8D8B0.toInt(),  // Sage green
        0xFFF5E0C0.toInt(),  // Warm cream
        0xFFB0C8E0.toInt(),  // Muted sky
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
    val CHILL_KONFETTI_COLORS = listOf(
        0xFFE8B878.toInt(), 0xFFD4B8E8.toInt(),
        0xFFE8A8A8.toInt(), 0xFFA8D8B0.toInt(),
        0xFFF5E0C0.toInt(), 0xFFB0C8E0.toInt()
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

    val MINI_CHILL_KONFETTI_COLORS = listOf(
        0xFFE8B878.toInt(), 0xFFD4B8E8.toInt(), 0xFFE8A8A8.toInt()
    )

    val MINI_CUTIE_PINK_KONFETTI_COLORS = listOf(
        0xFFFF69B4.toInt(), 0xFFFFB6C1.toInt(), 0xFFF06292.toInt()
    )

    val MINI_PREMIUM_KONFETTI_COLORS = listOf(
        0xFFFF9F0A.toInt(), 0xFFFFD60A.toInt(), 0xFF0A84FF.toInt(), 0xFFBF5AF2.toInt()
    )
}
