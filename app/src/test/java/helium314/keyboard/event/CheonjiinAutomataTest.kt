package helium314.keyboard.event

import helium314.keyboard.keyboard.internal.keyboard_parser.floris.KeyCode
import kotlin.test.Test
import kotlin.test.assertEquals

class CheonjiinAutomataTest {

    @Test
    fun basicVowelStrokes_composeExpectedSyllables() {
        assertEquals("가", compose(0x3131, 0x3163, 0x318D))
        assertEquals("거", compose(0x3131, 0x318D, 0x3163))
        assertEquals("고", compose(0x3131, 0x318D, 0x3161))
        assertEquals("구", compose(0x3131, 0x3161, 0x318D))
        assertEquals("개", compose(0x3131, 0x3163, 0x318D, 0x3163))
    }

    @Test
    fun repeatedConsonantKey_cyclesThroughCheonjiinGroup() {
        assertEquals("ㅋ", compose(0x3131, 0x3131))
        assertEquals("ㄲ", compose(0x3131, 0x3131, 0x3131))
        assertEquals("까", compose(0x3131, 0x3131, 0x3131, 0x3163, 0x318D))
    }

    @Test
    fun finalConsonantSplitsIntoNextSyllableOnVowelInput() {
        assertEquals("가니", compose(0x3131, 0x3163, 0x318D, 0x3134, 0x3163))
    }

    @Test
    fun delete_stepsBackThroughCheonjiinComposition() {
        val driver = CheonjiinDriver()
        driver.input(0x3131, 0x3163, 0x318D)
        assertEquals("가", driver.text())
        assertEquals("기", driver.delete())
        assertEquals("ㄱ", driver.delete())
        assertEquals("", driver.delete())
    }

    private fun compose(vararg codePoints: Int): String {
        val driver = CheonjiinDriver()
        driver.input(*codePoints)
        return driver.text()
    }

    private class CheonjiinDriver {
        private val combiner = HangulCombiner(CheonjiinAutomata())

        fun input(vararg codePoints: Int) {
            codePoints.forEach { codePoint ->
                val event = Event.createEventForCodePointFromUnknownSource(codePoint)
                combiner.processEvent(arrayListOf(), event)
            }
        }

        fun delete(): String {
            val deleteEvent = Event.createSoftwareKeypressEvent(
                Event.NOT_A_CODE_POINT,
                KeyCode.DELETE,
                0,
                0,
                0,
                false
            )
            combiner.processEvent(arrayListOf(), deleteEvent)
            return text()
        }

        fun text(): String = combiner.combiningStateFeedback.toString()
    }
}
