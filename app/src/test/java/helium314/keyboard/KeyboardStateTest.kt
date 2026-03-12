// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard

import helium314.keyboard.event.Event
import helium314.keyboard.keyboard.internal.KeyboardState
import helium314.keyboard.keyboard.internal.keyboard_parser.floris.KeyCode
import helium314.keyboard.latin.utils.RecapitalizeMode
import kotlin.test.Test
import kotlin.test.assertEquals

class KeyboardStateTest {
    @Test fun samsungStyleCheonjiinSymbolPagesCycleAcrossThreeScreens() {
        val actions = FakeSwitchActions(cheonjiinEnabled = true)
        val state = KeyboardState(actions)

        state.onLoadKeyboard(0, null, false)
        assertEquals("alphabet", actions.lastKeyboard)

        state.onReleaseSymbol(KeyCode.SYMBOL, false)
        assertEquals("symbols", actions.lastKeyboard)

        state.onPressKey(KeyCode.SHIFT, true, 0, null)
        assertEquals("symbols_shifted", actions.lastKeyboard)

        state.onPressKey(KeyCode.SHIFT, true, 0, null)
        assertEquals("symbols_shifted_2", actions.lastKeyboard)

        state.onPressKey(KeyCode.SHIFT, true, 0, null)
        assertEquals("symbols", actions.lastKeyboard)
    }

    private class FakeSwitchActions(
        private val cheonjiinEnabled: Boolean
    ) : KeyboardState.SwitchActions {
        var lastKeyboard = "alphabet"

        override fun setAlphabetKeyboard() { lastKeyboard = "alphabet" }
        override fun setAlphabetManualShiftedKeyboard() { lastKeyboard = "alphabet_manual" }
        override fun setAlphabetAutomaticShiftedKeyboard() { lastKeyboard = "alphabet_auto" }
        override fun setAlphabetShiftLockedKeyboard() { lastKeyboard = "alphabet_locked" }
        override fun setAlphabetShiftLockShiftedKeyboard() { lastKeyboard = "alphabet_lock_shifted" }
        override fun setEmojiKeyboard() { lastKeyboard = "emoji" }
        override fun setClipboardKeyboard() { lastKeyboard = "clipboard" }
        override fun setNumpadKeyboard() { lastKeyboard = "numpad" }
        override fun toggleNumpad(withSliding: Boolean, autoCapsFlags: Int, recapitalizeMode: RecapitalizeMode?, forceReturnToAlpha: Boolean) {
            lastKeyboard = if (lastKeyboard == "numpad") "alphabet" else "numpad"
        }
        override fun setSymbolsKeyboard() { lastKeyboard = "symbols" }
        override fun setSymbolsShiftedKeyboard() { lastKeyboard = "symbols_shifted" }
        override fun setSymbolsShifted2Keyboard() { lastKeyboard = "symbols_shifted_2" }
        override fun isSamsungStyleCheonjiinEnabled(): Boolean = cheonjiinEnabled
        override fun requestUpdatingShiftState(autoCapsFlags: Int, recapitalizeMode: RecapitalizeMode?) = Unit
        override fun startDoubleTapShiftKeyTimer() = Unit
        override val isInDoubleTapShiftKeyTimeout: Boolean = false
        override fun cancelDoubleTapShiftKeyTimer() = Unit
        override fun setOneHandedModeEnabled(enabled: Boolean) = Unit
        override fun switchOneHandedMode() = Unit
    }

    private fun KeyboardState.onReleaseSymbol(code: Int, withSliding: Boolean) {
        onReleaseKey(code, withSliding, 0, null)
    }
}
