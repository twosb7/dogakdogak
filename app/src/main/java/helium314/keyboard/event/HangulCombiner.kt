// SPDX-License-Identifier: GPL-3.0-only

package helium314.keyboard.event

import helium314.keyboard.keyboard.internal.keyboard_parser.floris.KeyCode
import java.lang.StringBuilder
import java.util.ArrayList

class HangulCombiner : Combiner {

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
                        Event.createConsumedEvent(event)
                    }
                    composingWord.isNotEmpty() -> {
                        composingWord.deleteCharAt(composingWord.lastIndex)
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
                composingWord.append(jamo.string)
                history.clear()
            } else {
                when (jamo) {
                    is HangulJamo.Consonant -> {
                        val initial = jamo.toInitial()
                        val final = jamo.toFinal()
                        if (currentSyllable.initial != null && currentSyllable.medial != null) {
                            if (currentSyllable.final == null) {
                                val combination = COMBINATION_TABLE_DUBEOLSIK[currentSyllable.initial.codePoint to (initial?.codePoint ?: -1)]
                                history +=
                                    if (combination != null) {
                                        currentSyllable.copy(initial = HangulJamo.Initial(combination))
                                    } else {
                                        if (final != null) {
                                            currentSyllable.copy(final = final)
                                        } else {
                                            composingWord.append(currentSyllable.string)
                                            history.clear()
                                            HangulSyllable(initial = initial)
                                        }
                                    }
                            } else {
                                val pair = currentSyllable.final.codePoint to (final?.codePoint ?: -1)
                                val combination = COMBINATION_TABLE_DUBEOLSIK[pair]
                                history += if (combination != null) {
                                    currentSyllable.copy(final = HangulJamo.Final(combination, combinationPair = pair))
                                } else {
                                    composingWord.append(currentSyllable.string)
                                    history.clear()
                                    HangulSyllable(initial = initial)
                                }
                            }
                        } else {
                            composingWord.append(currentSyllable.string)
                            history.clear()
                            history += HangulSyllable(initial = initial)
                        }
                    }
                    is HangulJamo.Vowel -> {
                        val medial = jamo.toMedial()
                        if (currentSyllable.final == null) {
                            history +=
                                if (currentSyllable.medial != null) {
                                    val combination = COMBINATION_TABLE_DUBEOLSIK[currentSyllable.medial.codePoint to (medial?.codePoint ?: -1)]
                                    if (combination != null) {
                                        currentSyllable.copy(medial = HangulJamo.Medial(combination))
                                    } else {
                                        composingWord.append(currentSyllable.string)
                                        history.clear()
                                        HangulSyllable(medial = medial)
                                    }
                            } else {
                                currentSyllable.copy(medial = medial)
                            }
                        } else if (currentSyllable.final.combinationPair != null) {
                            val pair = currentSyllable.final.combinationPair

                            history.removeAt(history.lastIndex)
                            val final = HangulJamo.Final(pair.first)
                            history += currentSyllable.copy(final = final)
                            composingWord.append(syllable?.string ?: "")
                            history.clear()
                            val initial = HangulJamo.Final(pair.second).toConsonant()?.toInitial()
                            val newSyllable = HangulSyllable(initial = initial)
                            history += newSyllable
                            history += newSyllable.copy(medial = medial)
                        } else {
                            history.removeAt(history.lastIndex)
                            composingWord.append(syllable?.string ?: "")
                            history.clear()
                            val initial = currentSyllable.final.toConsonant()?.toInitial()
                            val newSyllable = HangulSyllable(initial = initial)
                            history += newSyllable
                            history += newSyllable.copy(medial = medial)
                        }
                    }
                    is HangulJamo.Initial -> {
                        history +=
                            if (currentSyllable.initial != null) {
                                val combination = COMBINATION_TABLE_SEBEOLSIK[currentSyllable.initial.codePoint to jamo.codePoint]
                                if (combination != null && currentSyllable.medial == null && currentSyllable.final == null) {
                                    currentSyllable.copy(initial = HangulJamo.Initial(combination))
                                } else {
                                    composingWord.append(currentSyllable.string)
                                    history.clear()
                                    HangulSyllable(initial = jamo)
                                }
                            } else {
                                currentSyllable.copy(initial = jamo)
                            }
                    }
                    is HangulJamo.Medial -> {
                        history +=
                            if (currentSyllable.medial != null) {
                                val combination = COMBINATION_TABLE_SEBEOLSIK[currentSyllable.medial.codePoint to jamo.codePoint]
                                if (combination != null) {
                                    currentSyllable.copy(medial = HangulJamo.Medial(combination))
                                } else {
                                    composingWord.append(currentSyllable.string)
                                    history.clear()
                                    HangulSyllable(medial = jamo)
                                }
                            } else {
                                currentSyllable.copy(medial = jamo)
                            }
                    }
                    is HangulJamo.Final -> {
                        history +=
                            if (currentSyllable.final != null) {
                                val combination = COMBINATION_TABLE_SEBEOLSIK[currentSyllable.final.codePoint to jamo.codePoint]
                                if (combination != null) {
                                    currentSyllable.copy(final = HangulJamo.Final(combination))
                                } else {
                                    composingWord.append(currentSyllable.string)
                                    history.clear()
                                    HangulSyllable(final = jamo)
                                }
                            } else {
                                currentSyllable.copy(final = jamo)
                            }
                    }
                    // compiler bug? when it's not added, compiler complains that it's missing
                    // but when added, linter (correctly) states it's unreachable anyway
                    is HangulJamo.NonHangul -> Unit
                }
            }
        }

        return Event.createConsumedEvent(event)
    }

    override val combiningStateFeedback: CharSequence
        get() = composingWord.toString() + (syllable?.string ?: "")

    override fun reset() {
        composingWord.setLength(0)
        history.clear()
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

    sealed class HangulJamo {
        abstract val codePoint: Int
        abstract val modern: Boolean
        val string: String get() = String(Character.toChars(codePoint))
        data class NonHangul(override val codePoint: Int) : HangulJamo() {
            override val modern: Boolean get() = false
        }
        data class Initial(override val codePoint: Int) : HangulJamo() {
            override val modern: Boolean get() = codePoint in 0x1100 .. 0x1112
            val ordinal: Int get() = codePoint - 0x1100
            fun toConsonant(): Consonant? {
                val codePoint = COMPAT_CONSONANTS.getOrNull(CONVERT_INITIALS.indexOf(codePoint.toChar())) ?: return null
                if(codePoint.code == 0) return null
                return Consonant(codePoint.code)
            }
        }
        data class Medial(override val codePoint: Int) : HangulJamo() {
            override val modern: Boolean get() = codePoint in 1161 .. 0x1175
            val ordinal: Int get() = codePoint - 0x1161
            fun toVowel(): Vowel? {
                val codePoint = COMPAT_VOWELS.getOrNull(CONVERT_MEDIALS.indexOf(codePoint.toChar())) ?: return null
                return Vowel(codePoint.code)
            }
        }
        data class Final(override val codePoint: Int, val combinationPair: Pair<Int, Int>? = null) : HangulJamo() {
            override val modern: Boolean get() = codePoint in 0x11a8 .. 0x11c2
            val ordinal: Int get() = codePoint - 0x11a7
            fun toConsonant(): Consonant? {
                val codePoint = COMPAT_CONSONANTS.getOrNull(CONVERT_FINALS.indexOf(codePoint.toChar())) ?: return null
                if(codePoint.code == 0) return null
                return Consonant(codePoint.code)
            }
        }
        data class Consonant(override val codePoint: Int) : HangulJamo() {
            override val modern: Boolean get() = codePoint in 0x3131 .. 0x314e
            val ordinal: Int get() = codePoint - 0x3131
            fun toInitial(): Initial? {
                val codePoint = CONVERT_INITIALS.getOrNull(COMPAT_CONSONANTS.indexOf(codePoint.toChar())) ?: return null
                if(codePoint.code == 0) return null
                return Initial(codePoint.code)
            }
            fun toFinal(): Final? {
                val codePoint = CONVERT_FINALS.getOrNull(COMPAT_CONSONANTS.indexOf(codePoint.toChar())) ?: return null
                if(codePoint.code == 0) return null
                return Final(codePoint.code)
            }
        }
        data class Vowel(override val codePoint: Int) : HangulJamo() {
            override val modern: Boolean get() = codePoint in 0x314f .. 0x3163
            val ordinal: Int get() = codePoint - 0x314f1
            fun toMedial(): Medial? {
                val codePoint = CONVERT_MEDIALS.getOrNull(COMPAT_VOWELS.indexOf(codePoint.toChar())) ?: return null
                if(codePoint.code == 0) return null
                return Medial(codePoint.code)
            }
        }
        companion object {
            const val COMPAT_CONSONANTS = "ㄱㄲㄳㄴㄵㄶㄷㄸㄹㄺㄻㄼㄽㄾㄿㅀㅁㅂㅃㅄㅅㅆㅇㅈㅉㅊㅋㅌㅍㅎ"
            const val COMPAT_VOWELS = "ㅏㅐㅑㅒㅓㅔㅕㅖㅗㅘㅙㅚㅛㅜㅝㅞㅟㅠㅡㅢㅣ"
            const val CONVERT_INITIALS = "ᄀᄁ\u0000ᄂ\u0000\u0000ᄃᄄᄅ\u0000\u0000\u0000\u0000\u0000\u0000\u0000ᄆᄇᄈ\u0000ᄉᄊᄋᄌᄍᄎᄏᄐᄑᄒ"
            const val CONVERT_MEDIALS = "ᅡᅢᅣᅤᅥᅦᅧᅨᅩᅪᅫᅬᅭᅮᅯᅰᅱᅲᅳᅴᅵ"
            const val CONVERT_FINALS = "ᆨᆩᆪᆫᆬᆭᆮ\u0000ᆯᆰᆱᆲᆳᆴᆵᆶᆷᆸ\u0000ᆹᆺᆻᆼᆽ\u0000ᆾᆿᇀᇁᇂ"
            fun of(codePoint: Int): HangulJamo {
                return when(codePoint) {
                    in 0x3131 .. 0x314e -> Consonant(codePoint)
                    in 0x314f .. 0x3163 -> Vowel(codePoint)
                    in 0x1100 .. 0x115f -> Initial(codePoint)
                    in 0x1160 .. 0x11a7 -> Medial(codePoint)
                    in 0x11a8 .. 0x11ff -> Final(codePoint)
                    else -> NonHangul(codePoint)
                }
            }
        }
    }

    data class HangulSyllable(
            val initial: HangulJamo.Initial? = null,
            val medial: HangulJamo.Medial? = null,
            val final: HangulJamo.Final? = null
    ) {
        val combinable: Boolean get() = (initial?.modern ?: false) && (medial?.modern ?: false) && (final?.modern ?: true)
        val combined: String get() = (0xac00 + (initial?.ordinal ?: 0) * 21 * 28
                + (medial?.ordinal ?: 0) * 28
                + (final?.ordinal ?: 0)).toChar().toString()
        val uncombined: String get() = (initial?.string ?: "") + (medial?.string ?: "") + (final?.string ?: "")
        val uncombinedCompat: String get() = (initial?.toConsonant()?.string ?: "") +
                (medial?.toVowel()?.string ?: "") + (final?.toConsonant()?.string ?: "")
        val string: String get() = if (this.combinable) this.combined else this.uncombinedCompat
    }

    companion object {
        /** 겹받침 코드포인트 → (첫 번째 자음, 두 번째 자음) 쌍 */
        val COMPOUND_FINALS = mapOf(
            0x11A9 to (0x11A8 to 0x11A8),  // ᆩ = ᆨᆨ
            0x11AA to (0x11A8 to 0x11BA),  // ᆪ = ᆨᆺ
            0x11AC to (0x11AB to 0x11BD),  // ᆬ = ᆫᆽ
            0x11AD to (0x11AB to 0x11C2),  // ᆭ = ᆫᇂ
            0x11B0 to (0x11AF to 0x11A8),  // ᆰ = ᆯᆨ
            0x11B1 to (0x11AF to 0x11B7),  // ᆱ = ᆯᆷ
            0x11B2 to (0x11AF to 0x11B8),  // ᆲ = ᆯᆸ
            0x11B3 to (0x11AF to 0x11BA),  // ᆳ = ᆯᆺ
            0x11B4 to (0x11AF to 0x11C0),  // ᆴ = ᆯᇀ
            0x11B5 to (0x11AF to 0x11C1),  // ᆵ = ᆯᇁ
            0x11B6 to (0x11AF to 0x11C2),  // ᆶ = ᆯᇂ
            0x11B9 to (0x11B8 to 0x11BA),  // ᆹ = ᆸᆺ
            0x11BB to (0x11BA to 0x11BA)   // ᆻ = ᆺᆺ
        )
        val COMBINATION_TABLE_DUBEOLSIK = mapOf<Pair<Int, Int>, Int>(
                0x1169 to 0x1161 to 0x116a,
                0x1169 to 0x1162 to 0x116b,
                0x1169 to 0x1175 to 0x116c,
                0x116e to 0x1165 to 0x116f,
                0x116e to 0x1166 to 0x1170,
                0x116e to 0x1175 to 0x1171,
                0x1173 to 0x1175 to 0x1174,

                0x11a8 to 0x11ba to 0x11aa,
                0x11ab to 0x11bd to 0x11ac,
                0x11ab to 0x11c2 to 0x11ad,
                0x11af to 0x11a8 to 0x11b0,
                0x11af to 0x11b7 to 0x11b1,
                0x11af to 0x11b8 to 0x11b2,
                0x11af to 0x11ba to 0x11b3,
                0x11af to 0x11c0 to 0x11b4,
                0x11af to 0x11c1 to 0x11b5,
                0x11af to 0x11c2 to 0x11b6,
                0x11b8 to 0x11ba to 0x11b9
        )
        val COMBINATION_TABLE_SEBEOLSIK = mapOf<Pair<Int, Int>, Int>(
                0x1100 to 0x1100 to 0x1101,	// ㄲ
                0x1103 to 0x1103 to 0x1104,	// ㄸ
                0x1107 to 0x1107 to 0x1108,	// ㅃ
                0x1109 to 0x1109 to 0x110a,	// ㅆ
                0x110c to 0x110c to 0x110d,	// ㅉ

                0x1169 to 0x1161 to 0x116a,	// ㅘ
                0x1169 to 0x1162 to 0x116b,	// ㅙ
                0x1169 to 0x1175 to 0x116c,	// ㅚ
                0x116e to 0x1165 to 0x116f,	// ㅝ
                0x116e to 0x1166 to 0x1170,	// ㅞ
                0x116e to 0x1175 to 0x1171,	// ㅟ
                0x1173 to 0x1175 to 0x1174,	// ㅢ

                0x11a8 to 0x11a8 to 0x11a9,	// ㄲ
                0x11a8 to 0x11ba to 0x11aa,	// ㄳ
                0x11ab to 0x11bd to 0x11ac,	// ㄵ
                0x11ab to 0x11c2 to 0x11ad,	// ㄶ
                0x11af to 0x11a8 to 0x11b0,	// ㄺ
                0x11af to 0x11b7 to 0x11b1,	// ㄻ
                0x11af to 0x11b8 to 0x11b2,	// ㄼ
                0x11af to 0x11ba to 0x11b3,	// ㄽ
                0x11af to 0x11c0 to 0x11b4,	// ㄾ
                0x11af to 0x11c1 to 0x11b5,	// ㄿ
                0x11af to 0x11c2 to 0x11b6,	// ㅀ
                0x11b8 to 0x11ba to 0x11b9,	// ㅄ
                0x11ba to 0x11ba to 0x11bb	// ㅆ
        )
        private fun createEventChainFromSequence(text: CharSequence, originalEvent: Event): Event {
            return Event.createSoftwareTextEvent(text, KeyCode.MULTIPLE_CODE_POINTS, originalEvent)
        }
    }

}
