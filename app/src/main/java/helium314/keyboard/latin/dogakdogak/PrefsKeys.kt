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
    const val SOUND_IN_SILENT = "dogakdogak_sound_in_silent"
    /** 무음모드 동작: "sound_on" | "sound_off" | "vibrate_only" */
    const val SILENT_MODE_BEHAVIOR = "dogakdogak_silent_mode_behavior"

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
    /** 미리보기 모드: -1=비활성, 0=프리미엄, 1=큐티핑크, 2=아케이드 */
    const val PREVIEW_EFFECT_MODE = "preview_effect_mode"
    /** 미리보기 모드 활성화 시각 (System.currentTimeMillis) — staleness 감지용 */
    const val PREVIEW_EFFECT_TIMESTAMP = "preview_effect_ts"
    const val LAST_PURCHASED_EFFECT = "last_purchased_effect"

    // ── 테마 ──
    const val THEME = "dogakdogak_theme"
    const val THEME_COLORS = "theme_colors"
    const val THEME_COLORS_NIGHT = "theme_colors_night"

    // ── 카운터 ──
    const val COUNTER_MODE = "dogakdogak_counter_mode"
    const val COUNTER_REFRESH = "dogakdogak_counter_refresh"

    // ── 음성입력 ──
    const val VOICE_KEY_MAIN = "dogakdogak_voice_key_main"  // default: false

    // ── 백그라운드 동기화 ──
    const val LAST_RANKING_VISIT = "last_ranking_visit"
    const val RANKING_DISCLOSURE_ACCEPTED = "dogakdogak_ranking_disclosure_accepted"

    // ── 온보딩/마이그레이션 ──
    const val ONBOARDING_COMPLETED = "dogakdogak_onboarding_completed"
    const val KB_STYLE_V5 = "dogakdogak_kb_style_v5"
}
