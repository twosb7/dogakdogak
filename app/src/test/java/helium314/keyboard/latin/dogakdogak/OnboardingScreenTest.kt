// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.dogakdogak

import org.junit.Test
import kotlin.test.assertEquals

class OnboardingScreenTest {
    @Test
    fun imeEnableDescription_isShortSingleLineCopy() {
        assertEquals(
            "시스템 설정 - 도각도각 키보드 켜기",
            onboardingImeEnableDescription()
        )
    }
}
