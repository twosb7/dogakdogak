// SPDX-License-Identifier: GPL-3.0-only

package helium314.keyboard.event

import helium314.keyboard.keyboard.internal.keyboard_parser.floris.KeyCode
import java.lang.StringBuilder
import java.util.ArrayList

class HangulCombiner(private val automata: HangulAutomata) : Combiner {

    private val composingWord = StringBuilder()

    val history: MutableList<HangulSyllable> = mutableListOf()
    private val syllable: HangulSyllable? get() = history.lastOrNull()

    // 첫 번째 음절의 자모 단위 삭제가 시작되었는지 추적.
    // true이면 이후 음절은 자모 분해 없이 음절 단위로 삭제.
    private var wasReconstructed = false
    fun isInSyllableDeletionMode(): Boolean = wasReconstructed

    override fun processEvent(previousEvents: ArrayList<Event>?, event: Event): Event {
        if (event.keyCode == KeyCode.SHIFT) return event
        // previously we only used the combiner if codePoint > 0x1100 or codePoint == -1, but looks here it's not necessary
        val event = HangulEventDecoder.decodeSoftwareKeyEvent(event)
        // DELETE 외의 모든 키 입력은 자모 삭제 모드를 해제
        if (event.keyCode != KeyCode.DELETE) wasReconstructed = false
        if (Character.isWhitespace(event.codePoint)) {
            val text = combiningStateFeedback
            reset()
            return createEventChainFromSequence(text, event)
        } else if (event.isFunctionalKeyEvent) {
            if(event.keyCode == KeyCode.DELETE) {
                return when {
                    history.size == 1 && composingWord.isEmpty() || history.isEmpty() && composingWord.length == 1 -> {
                        reset()
                        // Keep this as a consumed delete event.
                        // Emitting a synthetic "space -> delete" chain can leave stale characters
                        // in editors that commit composing text eagerly.
                        Event.createConsumedEvent(event)
                    }
                    history.isNotEmpty() -> {
                        history.removeAt(history.lastIndex)
                        automata.reset()
                        Event.createConsumedEvent(event)
                    }
                    composingWord.isNotEmpty() -> {
                        composingWord.deleteCharAt(composingWord.lastIndex)
                        automata.reset()
                        Event.createConsumedEvent(event)
                    }
                    else -> event
                }
            }
            val text = combiningStateFeedback
            reset()
            return createEventChainFromSequence(text, event)
        } else {
            val currentSyllable = syllable ?: HangulSyllable()
            val jamo = HangulJamo.of(event.codePoint)
            if (!event.isCombining || jamo is HangulJamo.NonHangul) {
                composingWord.append(currentSyllable.string)
                history.clear()
                // If no pending Hangul composition, let non-Hangul chars (digits, symbols) pass
                // through to normal InputLogic handling. This ensures password/masked fields
                // receive commitText/commitCodePoint instead of setComposingText.
                if (composingWord.isEmpty()) {
                    return event
                }
                composingWord.append(jamo.string)
            } else {
                automata.combine(currentSyllable, jamo, history, composingWord)
            }
        }

        return Event.createConsumedEvent(event)
    }

    override val combiningStateFeedback: CharSequence
        get() = composingWord.toString() + (syllable?.string ?: "")

    override fun reset() {
        composingWord.setLength(0)
        history.clear()
        automata.reset()
    }

    /**
     * 커밋된 한글 음절 하나를 분해해 history를 재구성합니다.
     * 마지막 자모를 제거한 상태로 설정하므로, 호출 후 combiningStateFeedback이
     * 백스페이스 한 번의 결과를 보여줍니다.
     *
     * @param syllableChar 커밋된 한글 음절의 코드포인트 (0xAC00..0xD7A3)
     * @return 재구성 성공 여부
     */
    fun reconstructFromSyllable(syllableChar: Int): Boolean {
        if (syllableChar !in 0xAC00..0xD7A3) return false
        val idx = syllableChar - 0xAC00
        val finalIdx = idx % 28
        val medialIdx = (idx / 28) % 21
        val initialIdx = idx / 28 / 21

        val initial = HangulJamo.Initial(0x1100 + initialIdx)
        val medial = HangulJamo.Medial(0x1161 + medialIdx)

        reset()
        wasReconstructed = true

        if (finalIdx == 0) {
            // 종성 없음: 초성 + 중성 → 중성이 마지막 자모, 초성만 남김
            history.add(HangulSyllable(initial = initial))
        } else {
            val finalCp = 0x11A7 + finalIdx
            val compoundPair = COMPOUND_FINALS[finalCp]
            if (compoundPair != null) {
                // 겹받침: 첫 번째 자음까지의 상태로 재구성
                history.add(HangulSyllable(initial = initial))
                history.add(HangulSyllable(initial = initial, medial = medial))
                history.add(HangulSyllable(initial = initial, medial = medial,
                    final = HangulJamo.Final(compoundPair.first)))
            } else {
                // 홑받침: 종성 없는 상태로 재구성
                history.add(HangulSyllable(initial = initial))
                history.add(HangulSyllable(initial = initial, medial = medial))
            }
        }
        return true
    }

    companion object {
        private fun createEventChainFromSequence(text: CharSequence, originalEvent: Event): Event {
            return Event.createSoftwareTextEvent(text, KeyCode.MULTIPLE_CODE_POINTS, originalEvent)
        }
    }

}
