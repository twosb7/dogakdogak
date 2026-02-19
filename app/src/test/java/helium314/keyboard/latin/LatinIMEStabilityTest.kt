// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin

import helium314.keyboard.ShadowInputMethodManager2
import helium314.keyboard.ShadowLocaleManagerCompat
import helium314.keyboard.keyboard.KeyboardSwitcher
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(shadows = [
    ShadowLocaleManagerCompat::class,
    ShadowInputMethodManager2::class,
])
class LatinIMEStabilityTest {
    @Test
    fun onStartInputViewInternal_withNullEditorInfo_usesFallbackAndLoadsKeyboard() {
        val ime = Robolectric.setupService(LatinIME::class.java)
        ime.setInputView(ime.onCreateInputView())

        ime.onStartInputViewInternal(null, false)

        assertNotNull(KeyboardSwitcher.getInstance().getMainKeyboardView())
        assertNotNull(KeyboardSwitcher.getInstance().keyboard)
    }

    @Test
    fun showWindowSafely_retriesAfterIllegalStateException() {
        val ime = Robolectric.setupService(RetryShowWindowLatinIME::class.java)

        val shown = ime.showWindowSafely(true)

        assertTrue(shown)
        assertEquals(2, ime.showWindowCallCount)
    }
}

class RetryShowWindowLatinIME : LatinIME() {
    var showWindowCallCount: Int = 0

    override fun showWindowCompat(showInput: Boolean) {
        showWindowCallCount++
        if (showWindowCallCount == 1) {
            throw IllegalStateException("Window token is not set yet.")
        }
    }
}
