package helium314.keyboard.latin.dogakdogak

import androidx.annotation.RawRes
import helium314.keyboard.latin.R

/**
 * 기계식 스위치 타입
 */
enum class SwitchType(
    val displayName: String,
    val displayNameKo: String,
    val description: String,
    @RawRes val soundResIds: IntArray,
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
        soundResIds = intArrayOf(
            R.raw.switch_pebble1_1, R.raw.switch_pebble1_2, R.raw.switch_pebble1_3,
            R.raw.switch_pebble1_4, R.raw.switch_pebble1_5, R.raw.switch_pebble1_6,
            R.raw.switch_pebble1_7, R.raw.switch_pebble1_8
        ),
        isPremium = false
    ),

    PEBBLE_2(
        displayName = "Pebble 2",
        displayNameKo = "조약돌 2",
        description = "도각도각 타건음 #2",
        soundResIds = intArrayOf(
            R.raw.switch_pebble2_1, R.raw.switch_pebble2_2, R.raw.switch_pebble2_3,
            R.raw.switch_pebble2_4, R.raw.switch_pebble2_5, R.raw.switch_pebble2_6,
            R.raw.switch_pebble2_7, R.raw.switch_pebble2_8
        ),
        isPremium = true,
        productId = "com.dogakdogak.switch.pebble2"
    ),

    PEBBLE_3(
        displayName = "Pebble 3",
        displayNameKo = "조약돌 3",
        description = "도각도각 타건음 #3",
        soundResIds = intArrayOf(
            R.raw.switch_pebble3_1, R.raw.switch_pebble3_2, R.raw.switch_pebble3_3,
            R.raw.switch_pebble3_4, R.raw.switch_pebble3_5, R.raw.switch_pebble3_6,
            R.raw.switch_pebble3_7, R.raw.switch_pebble3_8
        ),
        isPremium = true,
        productId = "com.dogakdogak.switch.pebble3"
    ),

    PEBBLE_4(
        displayName = "Pebble 4",
        displayNameKo = "조약돌 4",
        description = "도각도각 타건음 #4",
        soundResIds = intArrayOf(
            R.raw.switch_pebble4_1, R.raw.switch_pebble4_2, R.raw.switch_pebble4_3,
            R.raw.switch_pebble4_4, R.raw.switch_pebble4_5, R.raw.switch_pebble4_6,
            R.raw.switch_pebble4_7, R.raw.switch_pebble4_8
        ),
        isPremium = true,
        productId = "com.dogakdogak.switch.pebble4"
    ),

    PEBBLE_5(
        displayName = "Pebble 5",
        displayNameKo = "조약돌 5",
        description = "도각도각 타건음 #5",
        soundResIds = intArrayOf(
            R.raw.switch_pebble5_1, R.raw.switch_pebble5_2, R.raw.switch_pebble5_3,
            R.raw.switch_pebble5_4, R.raw.switch_pebble5_5, R.raw.switch_pebble5_6,
            R.raw.switch_pebble5_7, R.raw.switch_pebble5_8
        ),
        isPremium = true,
        productId = "com.dogakdogak.switch.pebble5"
    ),

    PEBBLE_6(
        displayName = "Pebble 6",
        displayNameKo = "조약돌 6",
        description = "도각도각 타건음 #6",
        soundResIds = intArrayOf(
            R.raw.switch_pebble6_1, R.raw.switch_pebble6_2, R.raw.switch_pebble6_3,
            R.raw.switch_pebble6_4, R.raw.switch_pebble6_5, R.raw.switch_pebble6_6,
            R.raw.switch_pebble6_7, R.raw.switch_pebble6_8
        ),
        isPremium = true,
        productId = "com.dogakdogak.switch.pebble6"
    ),

    PEBBLE_7(
        displayName = "Pebble 7",
        displayNameKo = "조약돌 7",
        description = "도각도각 타건음 #7",
        soundResIds = intArrayOf(
            R.raw.switch_pebble7_1, R.raw.switch_pebble7_2, R.raw.switch_pebble7_3,
            R.raw.switch_pebble7_4, R.raw.switch_pebble7_5, R.raw.switch_pebble7_6,
            R.raw.switch_pebble7_7, R.raw.switch_pebble7_8
        ),
        isPremium = true,
        productId = "com.dogakdogak.switch.pebble7"
    ),

    PEBBLE_8(
        displayName = "Pebble 8",
        displayNameKo = "조약돌 8",
        description = "도각도각 타건음 #8",
        soundResIds = intArrayOf(
            R.raw.switch_pebble8_1, R.raw.switch_pebble8_2, R.raw.switch_pebble8_3,
            R.raw.switch_pebble8_4, R.raw.switch_pebble8_5, R.raw.switch_pebble8_6,
            R.raw.switch_pebble8_7, R.raw.switch_pebble8_8
        ),
        isPremium = true,
        productId = "com.dogakdogak.switch.pebble8"
    ),

    PEBBLE_9(
        displayName = "Pebble 9",
        displayNameKo = "조약돌 9",
        description = "도각도각 타건음 #9",
        soundResIds = intArrayOf(
            R.raw.switch_pebble9_1, R.raw.switch_pebble9_2, R.raw.switch_pebble9_3,
            R.raw.switch_pebble9_4, R.raw.switch_pebble9_5, R.raw.switch_pebble9_6,
            R.raw.switch_pebble9_7, R.raw.switch_pebble9_8
        ),
        isPremium = true,
        productId = "com.dogakdogak.switch.pebble9"
    ),

    PEBBLE_10(
        displayName = "Pebble 10",
        displayNameKo = "조약돌 10",
        description = "도각도각 타건음 #10",
        soundResIds = intArrayOf(
            R.raw.switch_pebble10_1, R.raw.switch_pebble10_2, R.raw.switch_pebble10_3,
            R.raw.switch_pebble10_4, R.raw.switch_pebble10_5, R.raw.switch_pebble10_6,
            R.raw.switch_pebble10_7, R.raw.switch_pebble10_8
        ),
        isPremium = true,
        productId = "com.dogakdogak.switch.pebble10"
    ),

    PEBBLE_11(
        displayName = "Pebble 11",
        displayNameKo = "조약돌 11",
        description = "도각도각 타건음 #11",
        soundResIds = intArrayOf(
            R.raw.switch_pebble11_1, R.raw.switch_pebble11_2, R.raw.switch_pebble11_3,
            R.raw.switch_pebble11_4, R.raw.switch_pebble11_5, R.raw.switch_pebble11_6,
            R.raw.switch_pebble11_7, R.raw.switch_pebble11_8
        ),
        isPremium = true,
        productId = "com.dogakdogak.switch.pebble11"
    ),

    // -- Cherry MX 시리즈 (전부 무료) --

    BLUE(
        displayName = "Cherry MX Blue",
        displayNameKo = "청축",
        description = "뚜렷한 클릭감과 큰 소리",
        soundResIds = intArrayOf(R.raw.switch_blue_1, R.raw.switch_blue_2, R.raw.switch_blue_3),
        isPremium = false
    ),

    BROWN(
        displayName = "Cherry MX Brown",
        displayNameKo = "갈축",
        description = "부드러운 촉감과 적당한 소리",
        soundResIds = intArrayOf(R.raw.switch_brown_1, R.raw.switch_brown_2, R.raw.switch_brown_3),
        isPremium = false
    ),

    RED(
        displayName = "Cherry MX Red",
        displayNameKo = "적축",
        description = "조용하고 가벼운 리니어",
        soundResIds = intArrayOf(R.raw.switch_red_1, R.raw.switch_red_2, R.raw.switch_red_3),
        isPremium = false
    ),

    BLACK(
        displayName = "Cherry MX Black",
        displayNameKo = "흑축",
        description = "무겁고 조용한 리니어",
        soundResIds = intArrayOf(R.raw.switch_black_1, R.raw.switch_black_2, R.raw.switch_black_3),
        isPremium = false
    ),

    SILVER(
        displayName = "Cherry MX Silver",
        displayNameKo = "은축",
        description = "빠른 반응속도의 게이밍 스위치",
        soundResIds = intArrayOf(R.raw.switch_silver_1, R.raw.switch_silver_2, R.raw.switch_silver_3),
        isPremium = false
    );

    companion object {
        /** 조약돌 2~11 전체 번들 */
        const val BUNDLE_PRODUCT_ID = "com.dogakdogak.switch.pebble.bundle"
        const val PREMIUM_EFFECTS_PRODUCT_ID = "com.dogakdogak.effects.premium"
        const val CUTIE_PINK_EFFECTS_PRODUCT_ID = "com.dogakdogak.effects.bubble"
        const val ARCADE_EFFECTS_PRODUCT_ID = "com.dogakdogak.effects.arcade"

        fun getDefaultSwitch() = PEBBLE_1

        fun getFreeSwitch() = PEBBLE_1

        fun getPremiumSwitches() = entries.filter { it.isPremium }

        fun getOnboardingSwitches() = listOf(
            PEBBLE_1, PEBBLE_2, PEBBLE_3, PEBBLE_4,
            BLUE, BROWN, RED, BLACK, SILVER
        )
    }
}
