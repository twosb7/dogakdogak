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
    }

    @Test
    fun onFinishInputViewInternal_doesNotCrash() {
        val ime = Robolectric.setupService(LatinIME::class.java)
        ime.setInputView(ime.onCreateInputView())
        ime.onFinishInputViewInternal(false)
        assertTrue(true)
    }
}
