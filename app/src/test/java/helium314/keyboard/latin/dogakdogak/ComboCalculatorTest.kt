// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.dogakdogak

import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * ComboCalculator 단위 테스트
 *
 * 테스트 시나리오:
 * 1. CPS 기반 콤보 티어 계산
 * 2. 5초 비활동 후 콤보 리셋
 * 3. 콤보 스트릭 증가
 * 4. keepAlive (백스페이스 롱프레스 시 타이머 유지)
 */
class ComboCalculatorTest {

    private lateinit var calculator: ComboCalculator

    @Before
    fun setup() {
        calculator = ComboCalculator()
    }

    @Test
    fun onClick_firstClick_returnsNormalTier() {
        val tier = calculator.onClick()
        assertEquals(ComboTier.NORMAL, tier)
        assertEquals(1, calculator.comboStreak)
    }

    @Test
    fun onClick_rapidClicks_increasesComboStreak() {
        repeat(10) { calculator.onClick() }
        assertEquals(10, calculator.comboStreak)
    }

    @Test
    fun comboTier_withHighCPS_returnsHigherTier() {
        // 빠르게 20번 클릭하면 CPS가 올라감
        repeat(20) { calculator.onClick() }
        val tier = calculator.onClick()
        // 21번 빠르게 클릭하면 최소 GOOD 이상이어야 함
        assertTrue(
            tier.ordinal >= ComboTier.GOOD.ordinal,
            "Expected GOOD or higher but got $tier"
        )
    }

    @Test
    fun keepAlive_doesNotIncreaseCombo() {
        calculator.onClick()
        calculator.onClick()
        val streakBefore = calculator.comboStreak

        calculator.keepAlive()

        assertEquals(streakBefore, calculator.comboStreak)
    }

    @Test
    fun comboTier_speedMultipliers_arePositive() {
        ComboTier.entries.forEach { tier ->
            assertTrue(
                tier.speedMultiplier > 0,
                "speedMultiplier for $tier should be positive"
            )
        }
    }

    @Test
    fun comboTier_orderedByMultiplier() {
        val tiers = ComboTier.entries
        for (i in 1 until tiers.size) {
            assertTrue(
                tiers[i].speedMultiplier >= tiers[i - 1].speedMultiplier,
                "${tiers[i]} multiplier should be >= ${tiers[i - 1]}"
            )
        }
    }
}
