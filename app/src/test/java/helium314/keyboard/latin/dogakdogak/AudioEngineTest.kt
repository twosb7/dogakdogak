// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.dogakdogak

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * AudioEngine / SwitchType 관련 단위 테스트
 *
 * 테스트 시나리오:
 * 1. 모든 SwitchType에 사운드 리소스가 있는지 확인
 * 2. SwitchType의 volumeBoost가 유효 범위인지 확인
 * 3. 기본 스위치 타입이 유효한지 확인
 * 4. 프리미엄 스위치 목록이 비어있지 않은지 확인
 */
@RunWith(RobolectricTestRunner::class)
class AudioEngineTest {
    private lateinit var context: Context
    private val hasBundledSwitchSounds: Boolean
        get() = SwitchType.entries.any { it.resolveSoundResIds(context).isNotEmpty() }

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
    }

    @Test
    fun allSwitchTypes_haveSoundResources() {
        SwitchType.entries.forEach { switchType ->
            val resolvedSoundResIds = switchType.resolveSoundResIds(context)
            assertTrue(
                resolvedSoundResIds.distinct().size == resolvedSoundResIds.size,
                "${switchType.name} should not resolve duplicate sound resources"
            )
            assertTrue(
                resolvedSoundResIds.all { it != 0 },
                "${switchType.name} should not resolve invalid resource ids"
            )
            if (hasBundledSwitchSounds) {
                assertTrue(
                    resolvedSoundResIds.isNotEmpty(),
                    "${switchType.name} should have at least one sound resource when switch sounds are bundled"
                )
            }
        }
    }

    @Test
    fun allSwitchTypes_haveValidVolumeBoost() {
        SwitchType.entries.forEach { switchType ->
            assertTrue(
                switchType.volumeBoost > 0f,
                "${switchType.name} volumeBoost should be positive, was ${switchType.volumeBoost}"
            )
            assertTrue(
                switchType.volumeBoost <= 5f,
                "${switchType.name} volumeBoost should be <= 5.0, was ${switchType.volumeBoost}"
            )
        }
    }

    @Test
    fun defaultSwitch_isValid() {
        val defaultSwitch = SwitchType.getDefaultSwitch()
        assertNotNull(defaultSwitch)
        if (hasBundledSwitchSounds) {
            assertTrue(defaultSwitch.resolveSoundResIds(context).isNotEmpty())
        }
    }

    @Test
    fun audioEngine_handlesMissingBundledSoundsGracefully() {
        val audioEngine = AudioEngine(context)
        try {
            SwitchType.entries.forEach { switchType ->
                audioEngine.setCurrentSwitch(switchType)
                audioEngine.playClick()
                audioEngine.playDelete()
                audioEngine.playSpace()
                audioEngine.playEnter()
                audioEngine.playSwitchSound(switchType)
            }
            assertTrue(true)
        } finally {
            audioEngine.release()
        }
    }

    @Test
    fun premiumSwitches_areNonEmpty() {
        val premiumSwitches = SwitchType.getPremiumSwitches()
        assertTrue(
            premiumSwitches.isNotEmpty(),
            "There should be at least one premium switch"
        )
    }

    @Test
    fun premiumSwitches_allHaveProductIds() {
        SwitchType.getPremiumSwitches().forEach { switchType ->
            assertNotNull(
                switchType.productId,
                "Premium switch ${switchType.name} should have a productId"
            )
        }
    }

    @Test
    fun allSwitchTypes_haveDisplayNames() {
        SwitchType.entries.forEach { switchType ->
            assertTrue(
                switchType.displayName.isNotBlank(),
                "${switchType.name} should have a non-blank displayName"
            )
            assertTrue(
                switchType.displayNameKo.isNotBlank(),
                "${switchType.name} should have a non-blank Korean displayName"
            )
        }
    }

    @Test
    fun switchType_valueOf_returnsCorrectType() {
        SwitchType.entries.forEach { switchType ->
            assertEquals(switchType, SwitchType.valueOf(switchType.name))
        }
    }

    @Test
    fun switchType_valueOf_invalidName_throwsException() {
        try {
            SwitchType.valueOf("NONEXISTENT_SWITCH")
            assertTrue(false, "Should have thrown IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            // Expected
        }
    }
}
