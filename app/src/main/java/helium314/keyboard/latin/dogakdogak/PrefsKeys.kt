package helium314.keyboard.latin.dogakdogak

/**
 * SharedPreferences 키 상수 중앙화.
 * 도각도각 코드 전체에서 사용하는 모든 Prefs 키를 한곳에서 관리.
 */
object PrefsKeys {
    // ── 스위치/사운드 ──
    const val SWITCH_TYPE = "dogakdogak_switch_type"
    const val VOLUME = "dogakdogak_volume"
    const val SOUND_IN_VIBRATE = "dogakdogak_sound_in_vibrate"

    // ── 오버레이 ──
    const val OVERLAY_VISIBLE = "dogakdogak_overlay_visible"
    const val OVERLAY_TOUCH = "dogakdogak_overlay_touch"
    const val OVERLAY_SCALE = "dogakdogak_overlay_scale"
    const val OVERLAY_COLOR = "dogakdogak_overlay_color"
    const val OVERLAY_X = "dogakdogak_overlay_x"
    const val OVERLAY_Y = "dogakdogak_overlay_y"
    const val OVERLAY_NUDGE_DISMISSED = "overlay_nudge_dismissed"

    // ── 이펙트 ──
    const val PREMIUM_EFFECTS = "premium_effects"
    const val PREMIUM_EFFECTS_ON = "premium_effects_on"
    const val BUBBLE_EFFECTS = "bubble_effects"
    const val BUBBLE_EFFECTS_ON = "bubble_effects_on"
    const val ARCADE_EFFECTS = "arcade_effects"
    const val ARCADE_EFFECTS_ON = "arcade_effects_on"
    const val EFFECTS_INITIALIZED = "effects_initialized"
    const val LAST_PURCHASED_EFFECT = "last_purchased_effect"

    // ── 테마 ──
    const val THEME = "dogakdogak_theme"
    const val THEME_COLORS = "theme_colors"
    const val THEME_COLORS_NIGHT = "theme_colors_night"

    // ── 카운터 ──
    const val COUNTER_MODE = "dogakdogak_counter_mode"
    const val COUNTER_REFRESH = "dogakdogak_counter_refresh"

    // ── 온보딩/마이그레이션 ──
    const val ONBOARDING_COMPLETED = "dogakdogak_onboarding_completed"
    const val KB_STYLE_V5 = "dogakdogak_kb_style_v5"
}
