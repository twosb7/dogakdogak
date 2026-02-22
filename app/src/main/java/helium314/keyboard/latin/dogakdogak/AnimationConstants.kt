package helium314.keyboard.latin.dogakdogak

/**
 * ComboOverlayView 및 이펙트 관련 애니메이션 상수.
 */
object AnimationConstants {
    // ── 타이밍 ──
    const val COMBO_TIMEOUT_MS = 5000L
    const val FADE_DURATION_MS = 500L
    const val MILESTONE_DURATION_MS = 2500L
    const val IMPACT_RING_DURATION_MS = 500f
    const val ARCADE_COIN_FADE_MS = 500  // GIF 재생 후 페이드아웃 시간
    const val PUNCH_DURATION_MS = 400f
    const val POPUP_DURATION_MS = 800f
    const val POPUP_THROTTLE_MS = 80L

    // ── 팝업/잔상 ──
    const val MAX_POPUPS = 10
    const val GHOST_TRAIL_SIZE = 3
    val GHOST_TRAIL_ALPHAS = floatArrayOf(0.07f, 0.15f, 0.3f)

    // ── Premium 스프링 물리 ──
    const val PREMIUM_SPRING_DECAY = 12f
    const val PREMIUM_SPRING_FREQ = 25f
    const val PREMIUM_SPRING_AMP = 0.5f

    // ── CutiePink 스프링 물리 ──
    const val CUTE_SPRING_DECAY = 8f
    const val CUTE_SPRING_FREQ = 18f
    const val CUTE_SPRING_AMP = 0.6f

    // ── Arcade 스프링 물리 ──
    const val ARCADE_SPRING_DECAY = 30f
    const val ARCADE_SPRING_FREQ = 8f
    const val ARCADE_SPRING_AMP = 0.05f

    // ── 오버레이 레이아웃 ──
    const val OVERLAY_BASE_WIDTH_DP = 120f
    const val OVERLAY_BASE_HEIGHT_DP = 140f
    const val OVERLAY_MARGIN_DP = 8f
}
