package helium314.keyboard.latin.dogakdogak

import android.content.Context

private fun soundResourceRange(prefix: String, range: IntRange): List<String> =
    range.map { index -> "${prefix}_$index" }

/**
 * 기계식 스위치 타입
 */
enum class SwitchType(
    val displayName: String,
    val displayNameKo: String,
    val description: String,
    private val soundResourceNames: List<String>,
    val isPremium: Boolean,
    val productId: String? = null,
    /** 소리가 작은 스위치를 보정하기 위한 볼륨 배율 (기본 1.0) */
    val volumeBoost: Float = 1.0f
) {
    // -- 조약돌 시리즈 (1번 무료, 2~11 유료) --

    PEBBLE_1(
        displayName = "Pebble 1",
        displayNameKo = "조약돌 1",
        description = "도각도각 타건음 #1",
        soundResourceNames = soundResourceRange("switch_pebble1", 1..8),
        isPremium = false
    ),

    PEBBLE_2(
        displayName = "Pebble 2",
        displayNameKo = "조약돌 2",
        description = "도각도각 타건음 #2",
        soundResourceNames = soundResourceRange("switch_pebble2", 1..8),
        isPremium = true,
        productId = "com.dogakdogak.switch.pebble2"
    ),

    PEBBLE_3(
        displayName = "Pebble 3",
        displayNameKo = "조약돌 3",
        description = "도각도각 타건음 #3",
        soundResourceNames = soundResourceRange("switch_pebble3", 1..8),
        isPremium = true,
        productId = "com.dogakdogak.switch.pebble_3"
    ),

    PEBBLE_4(
        displayName = "Pebble 4",
        displayNameKo = "조약돌 4",
        description = "도각도각 타건음 #4",
        soundResourceNames = soundResourceRange("switch_pebble4", 1..8),
        isPremium = true,
        productId = "com.dogakdogak.switch.pebble_4"
    ),

    PEBBLE_5(
        displayName = "Pebble 5",
        displayNameKo = "조약돌 5",
        description = "도각도각 타건음 #5",
        soundResourceNames = soundResourceRange("switch_pebble5", 1..8),
        isPremium = true,
        productId = "com.dogakdogak.switch.pebble5"
    ),

    PEBBLE_6(
        displayName = "Pebble 6",
        displayNameKo = "조약돌 6",
        description = "도각도각 타건음 #6",
        soundResourceNames = soundResourceRange("switch_pebble6", 1..8),
        isPremium = true,
        productId = "com.dogakdogak.switch.pebble6"
    ),

    PEBBLE_7(
        displayName = "Pebble 7",
        displayNameKo = "조약돌 7",
        description = "도각도각 타건음 #7",
        soundResourceNames = soundResourceRange("switch_pebble7", 1..8),
        isPremium = true,
        productId = "com.dogakdogak.switch.pebble7"
    ),

    PEBBLE_8(
        displayName = "Pebble 8",
        displayNameKo = "조약돌 8",
        description = "도각도각 타건음 #8",
        soundResourceNames = soundResourceRange("switch_pebble8", 1..8),
        isPremium = true,
        productId = "com.dogakdogak.switch.pebble8"
    ),

    PEBBLE_9(
        displayName = "Pebble 9",
        displayNameKo = "조약돌 9",
        description = "도각도각 타건음 #9",
        soundResourceNames = soundResourceRange("switch_pebble9", 1..8),
        isPremium = true,
        productId = "com.dogakdogak.switch.pebble9"
    ),

    PEBBLE_10(
        displayName = "Pebble 10",
        displayNameKo = "조약돌 10",
        description = "도각도각 타건음 #10",
        soundResourceNames = soundResourceRange("switch_pebble10", 1..8),
        isPremium = true,
        productId = "com.dogakdogak.switch.pebble10"
    ),

    PEBBLE_11(
        displayName = "Pebble 11",
        displayNameKo = "조약돌 11",
        description = "도각도각 타건음 #11",
        soundResourceNames = soundResourceRange("switch_pebble11", 1..8),
        isPremium = true,
        productId = "com.dogakdogak.switch.pebble11"
    ),

    // -- Cherry MX 시리즈 (전부 무료) --

    BLUE(
        displayName = "Cherry MX Blue",
        displayNameKo = "청축",
        description = "뚜렷한 클릭감과 큰 소리",
        soundResourceNames = soundResourceRange("switch_blue", 1..3),
        isPremium = false
    ),

    BROWN(
        displayName = "Cherry MX Brown",
        displayNameKo = "갈축",
        description = "부드러운 촉감과 적당한 소리",
        soundResourceNames = soundResourceRange("switch_brown", 1..3),
        isPremium = false
    ),

    RED(
        displayName = "Cherry MX Red",
        displayNameKo = "적축",
        description = "조용하고 가벼운 리니어",
        soundResourceNames = soundResourceRange("switch_red", 1..3),
        isPremium = false
    ),

    BLACK(
        displayName = "Cherry MX Black",
        displayNameKo = "흑축",
        description = "무겁고 조용한 리니어",
        soundResourceNames = soundResourceRange("switch_black", 1..3),
        isPremium = false
    ),

    SILVER(
        displayName = "Cherry MX Silver",
        displayNameKo = "은축",
        description = "빠른 반응속도의 게이밍 스위치",
        soundResourceNames = soundResourceRange("switch_silver", 1..3),
        isPremium = false
    );

    fun resolveSoundResIds(context: Context): IntArray =
        soundResourceNames
            .mapNotNull { name ->
                context.resources.getIdentifier(name, "raw", context.packageName).takeIf { it != 0 }
            }
            .toIntArray()

    companion object {
        /** 조약돌 2~11 전체 번들 */
        const val BUNDLE_PRODUCT_ID = "com.dogakdogak.switch.pebble.bundle"
        const val PREMIUM_EFFECTS_PRODUCT_ID = "com.dogakdogak.effects.premium"
        const val CUTIE_PINK_EFFECTS_PRODUCT_ID = "com.dogakdogak.effects.cuttypink"
        const val ARCADE_EFFECTS_PRODUCT_ID = "com.dogakdogak.effects.arcade"
        /** 이펙트 전체 번들 (프리미엄 + 큐티핑크 + 아케이드) */
        const val EFFECTS_BUNDLE_PRODUCT_ID = "com.dogakdogak.effects.bundle"

        fun getDefaultSwitch() = PEBBLE_1

        fun getFreeSwitch() = PEBBLE_1

        fun getPremiumSwitches() = entries.filter { it.isPremium }

        fun getOnboardingSwitches() = listOf(
            PEBBLE_1, PEBBLE_2, PEBBLE_3, PEBBLE_4,
            BLUE, BROWN, RED, BLACK, SILVER
        )
    }
}
