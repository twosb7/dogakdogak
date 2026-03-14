// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.dogakdogak

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SoundPreviewBehaviorTest {
    @Test
    fun previewTyping_usesPreviewSwitchWithoutManualReplay_whenDogakdogakImeIsSelected() {
        val behavior = soundPreviewBehavior(
            previewSwitch = SwitchType.PEBBLE_7,
            isDogakdogakImeSelected = true
        )

        assertEquals(SwitchType.PEBBLE_7, behavior.switchForLiveTyping)
        assertFalse(behavior.shouldReplaySoundOnTextChange)
    }

    @Test
    fun previewTyping_replaysSoundOnTextChange_whenAnotherImeIsSelected() {
        val behavior = soundPreviewBehavior(
            previewSwitch = SwitchType.PEBBLE_7,
            isDogakdogakImeSelected = false
        )

        assertEquals(SwitchType.PEBBLE_7, behavior.switchForLiveTyping)
        assertTrue(behavior.shouldReplaySoundOnTextChange)
    }
}
