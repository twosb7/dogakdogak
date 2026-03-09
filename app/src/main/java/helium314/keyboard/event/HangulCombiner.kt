// SPDX-License-Identifier: GPL-3.0-only

package helium314.keyboard.event

import helium314.keyboard.keyboard.internal.keyboard_parser.floris.KeyCode
import java.lang.StringBuilder
import java.util.ArrayList

class HangulCombiner(private val automata: HangulAutomata) : Combiner {

    private val composingWord = StringBuilder()

    val history: MutableList<HangulSyllable> = mutableListOf()
    private val syllable: HangulSyllable? get() = history.lastOrNull()
    private var wasReconstructed = false
    fun isInSyllableDeletionMode(): Boolean = wasReconstructed
    fun resetAutomataState() = automata.reset()

    override fun processEvent(previousEvents: ArrayList<Event>?, event: Event): Event {
        if (event.keyCode == KeyCode.SHIFT) return event
        // previously we only used the combiner if codePoint > 0x1100 or codePoint == -1, but looks here it's not necessary
        val event = HangulEventDecoder.decodeSoftwareKeyEvent(event)
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
            restoreTrailingHangulStateIfNeeded()
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

    private fun restoreTrailingHangulStateIfNeeded() {
        if (history.isNotEmpty() || composingWord.isEmpty()) return

        val end = composingWord.length
        val lastCodePoint = Character.codePointBefore(composingWord, end)
        val lastLength = Character.charCount(lastCodePoint)
        val trailingJamo = HangulJamo.of(lastCodePoint)

        if (trailingJamo is HangulJamo.Consonant) {
            composingWord.delete(end - lastLength, end)
            trailingJamo.toInitial()?.let { history += HangulSyllable(initial = it) }
            return
        }

        if (automata is CheonjiinAutomata
            && (trailingJamo is HangulJamo.Araea)
            && end > lastLength
        ) {
            val previousEnd = end - lastLength
            val previousCodePoint = Character.codePointBefore(composingWord, previousEnd)
            val previousLength = Character.charCount(previousCodePoint)
            val previousJamo = HangulJamo.of(previousCodePoint)
            if (previousJamo is HangulJamo.Consonant) {
                val initial = previousJamo.toInitial() ?: return
                composingWord.delete(previousEnd - previousLength, end)
                history += HangulSyllable(initial = initial, medial = HangulJamo.Medial(trailingJamo.codePoint))
            }
        }
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

        if (automata is CheonjiinAutomata && finalIdx == 0) {
            val strokes = CHEONJIIN_MEDIAL_DECOMPOSITION[medial.codePoint] ?: return false
            if (strokes.size == 1) {
                history += HangulSyllable(initial = initial)
                automata.reset()
                return true
            }
            val initialConsonant = initial.toConsonant() ?: return false
            val replaySequence = buildList {
                add(initialConsonant)
                addAll(strokes.dropLast(1))
            }
            replayJamoSequence(replaySequence)
            return true
        }

        if (finalIdx == 0) {
            val decomposedMedial = DECOMPOSED_MEDIALS[medial.codePoint]
            if (decomposedMedial != null) {
                history.add(HangulSyllable(initial = initial))
                history.add(HangulSyllable(
                    initial = initial,
                    medial = HangulJamo.Medial(decomposedMedial.first)
                ))
            } else {
                // 종성 없음: 초성 + 중성에서 중성을 제거하면 초성만 남김
                history.add(HangulSyllable(initial = initial))
            }
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

    private fun replayJamoSequence(sequence: List<HangulJamo>) {
        sequence.forEach { jamo ->
            val currentSyllable = syllable ?: HangulSyllable()
            automata.combine(currentSyllable, jamo, history, composingWord)
        }
    }

    companion object {
        private val DECOMPOSED_MEDIALS = mapOf(
            0x116A to (0x1169 to 0x1161), // ㅘ = ㅗ + ㅏ
            0x116B to (0x1169 to 0x1162), // ㅙ = ㅗ + ㅐ
            0x116C to (0x1169 to 0x1175), // ㅚ = ㅗ + ㅣ
            0x116F to (0x116E to 0x1165), // ㅝ = ㅜ + ㅓ
            0x1170 to (0x116E to 0x1166), // ㅞ = ㅜ + ㅔ
            0x1171 to (0x116E to 0x1175), // ㅟ = ㅜ + ㅣ
            0x1174 to (0x1173 to 0x1175)  // ㅢ = ㅡ + ㅣ
        )

        private val CHEONJIIN_MEDIAL_DECOMPOSITION = mapOf(
            0x1161 to listOf(HangulJamo.Vowel(0x3163), HangulJamo.Araea(0x318D)), // ㅏ
            0x1162 to listOf(HangulJamo.Vowel(0x3163), HangulJamo.Araea(0x318D), HangulJamo.Vowel(0x3163)), // ㅐ
            0x1163 to listOf(HangulJamo.Vowel(0x3163), HangulJamo.Araea(0x318D), HangulJamo.Araea(0x318D)), // ㅑ
            0x1164 to listOf(HangulJamo.Vowel(0x3163), HangulJamo.Araea(0x318D), HangulJamo.Araea(0x318D), HangulJamo.Vowel(0x3163)), // ㅒ
            0x1165 to listOf(HangulJamo.Araea(0x318D), HangulJamo.Vowel(0x3163)), // ㅓ
            0x1166 to listOf(HangulJamo.Araea(0x318D), HangulJamo.Vowel(0x3163), HangulJamo.Vowel(0x3163)), // ㅔ
            0x1167 to listOf(HangulJamo.Araea(0x318D), HangulJamo.Vowel(0x3163), HangulJamo.Araea(0x318D)), // ㅕ
            0x1168 to listOf(HangulJamo.Araea(0x318D), HangulJamo.Vowel(0x3163), HangulJamo.Araea(0x318D), HangulJamo.Vowel(0x3163)), // ㅖ
            0x1169 to listOf(HangulJamo.Araea(0x318D), HangulJamo.Vowel(0x3161)), // ㅗ
            0x116A to listOf(HangulJamo.Araea(0x318D), HangulJamo.Vowel(0x3161), HangulJamo.Vowel(0x3163), HangulJamo.Araea(0x318D)), // ㅘ
            0x116B to listOf(HangulJamo.Araea(0x318D), HangulJamo.Vowel(0x3161), HangulJamo.Vowel(0x3163), HangulJamo.Araea(0x318D), HangulJamo.Vowel(0x3163)), // ㅙ
            0x116C to listOf(HangulJamo.Araea(0x318D), HangulJamo.Vowel(0x3161), HangulJamo.Vowel(0x3163)), // ㅚ
            0x116D to listOf(HangulJamo.Araea(0x318D), HangulJamo.Vowel(0x3161), HangulJamo.Araea(0x318D)), // ㅛ
            0x116E to listOf(HangulJamo.Vowel(0x3161), HangulJamo.Araea(0x318D)), // ㅜ
            0x116F to listOf(HangulJamo.Vowel(0x3161), HangulJamo.Araea(0x318D), HangulJamo.Araea(0x318D), HangulJamo.Vowel(0x3163)), // ㅝ
            0x1170 to listOf(HangulJamo.Vowel(0x3161), HangulJamo.Araea(0x318D), HangulJamo.Araea(0x318D), HangulJamo.Vowel(0x3163), HangulJamo.Vowel(0x3163)), // ㅞ
            0x1171 to listOf(HangulJamo.Vowel(0x3161), HangulJamo.Araea(0x318D), HangulJamo.Vowel(0x3163)), // ㅟ
            0x1172 to listOf(HangulJamo.Vowel(0x3161), HangulJamo.Araea(0x318D), HangulJamo.Araea(0x318D)), // ㅠ
            0x1173 to listOf(HangulJamo.Vowel(0x3161)), // ㅡ
            0x1174 to listOf(HangulJamo.Vowel(0x3161), HangulJamo.Vowel(0x3163)), // ㅢ
            0x1175 to listOf(HangulJamo.Vowel(0x3163)) // ㅣ
        )

        private fun createEventChainFromSequence(text: CharSequence, originalEvent: Event): Event {
            return Event.createSoftwareTextEvent(text, KeyCode.MULTIPLE_CODE_POINTS, originalEvent)
        }
    }

}
