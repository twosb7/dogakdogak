// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.dogakdogak

import org.junit.Test
import androidx.compose.ui.unit.dp
import kotlin.test.assertEquals

class SettingsScreenTextTest {
    @Test
    fun spacebarTriviaSubject_usesShortSingleLineCopy() {
        assertEquals(
            "스페이스 상식 표시",
            spacebarTriviaSubject()
        )
    }

    @Test
    fun spacebarTriviaRowVerticalPadding_isCompact() {
        assertEquals(2.dp, spacebarTriviaRowVerticalPadding())
    }
}
