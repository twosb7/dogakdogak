// SPDX-License-Identifier: GPL-3.0-only

package helium314.keyboard.event

class CheonjiinAutomata : HangulAutomata {

    // 마지막으로 입력된 자음의 호환 자모 코드포인트 (멀티탭 순환용)
    private var lastConsonantCode: Int = 0

    override fun reset() {
        lastConsonantCode = 0
    }

    override fun combine(
        currentSyllable: HangulSyllable,
        jamo: HangulJamo,
        history: MutableList<HangulSyllable>,
        composingWord: StringBuilder
    ) {
        when (jamo) {
            is HangulJamo.Consonant -> handleConsonant(currentSyllable, jamo, history, composingWord)
            is HangulJamo.Vowel -> handleVowelStroke(currentSyllable, jamo.codePoint, history, composingWord)
            is HangulJamo.Araea -> handleVowelStroke(currentSyllable, jamo.codePoint, history, composingWord)
            else -> Unit
        }
    }

    private fun handleConsonant(
        currentSyllable: HangulSyllable,
        jamo: HangulJamo.Consonant,
        history: MutableList<HangulSyllable>,
        composingWord: StringBuilder
    ) {
        val consonantCode = jamo.codePoint

        // 멀티탭 판정: lastConsonantCode의 기본키 == 새로 입력된 자음의 기본키
        val baseOfLast = CHEONJIIN_CONSONANT_TO_BASE[lastConsonantCode]
        if (lastConsonantCode != 0 && baseOfLast != null && baseOfLast == consonantCode) {
            // 같은 물리 키 반복 → 사이클의 다음 자음으로 교체
            val cycle = CHEONJIIN_CONSONANT_CYCLES[consonantCode]!!
            val currentIndex = cycle.indexOf(lastConsonantCode)
            val nextConsonantCode = cycle[(currentIndex + 1) % cycle.size]
            val newConsonant = HangulJamo.Consonant(nextConsonantCode)

            if (currentSyllable.final != null) {
                // 종성 교체
                val newFinal = newConsonant.toFinal()
                if (newFinal != null) {
                    history.removeAt(history.lastIndex)
                    history += currentSyllable.copy(final = newFinal)
                } else {
                    // 종성 불가 (ㄸ 등) → 현재 음절 확정 + 새 음절
                    history.removeAt(history.lastIndex)
                    val withoutFinal = currentSyllable.copy(final = null)
                    history += withoutFinal
                    composingWord.append(withoutFinal.string)
                    history.clear()
                    history += HangulSyllable(initial = newConsonant.toInitial())
                }
            } else if (currentSyllable.initial != null && currentSyllable.medial == null) {
                // 초성 교체
                history.removeAt(history.lastIndex)
                history += currentSyllable.copy(initial = newConsonant.toInitial())
            }
            lastConsonantCode = nextConsonantCode  // 현재 표시된 자음으로 갱신
            return
        }

        // 새 자음 입력 (멀티탭이 아닌 경우)
        val initial = jamo.toInitial()
        val final = jamo.toFinal()

        if (currentSyllable.initial != null && currentSyllable.medial != null) {
            if (currentSyllable.final == null) {
                // 초성+중성만 있는 상태 → 종성 추가
                if (final != null) {
                    history += currentSyllable.copy(final = final)
                } else {
                    composingWord.append(currentSyllable.string)
                    history.clear()
                    history += HangulSyllable(initial = initial)
                }
            } else {
                // 초성+중성+종성 있는 상태 → 종성 결합 시도
                val pair = currentSyllable.final.codePoint to (final?.codePoint ?: -1)
                val combination = COMBINATION_TABLE_CHEONJIIN_FINAL[pair]
                if (combination != null) {
                    history += currentSyllable.copy(final = HangulJamo.Final(combination, combinationPair = pair))
                } else {
                    composingWord.append(currentSyllable.string)
                    history.clear()
                    history += HangulSyllable(initial = initial)
                }
            }
        } else {
            // 빈 상태이거나 초성만 있는 상태
            composingWord.append(currentSyllable.string)
            history.clear()
            history += HangulSyllable(initial = initial)
        }
        lastConsonantCode = consonantCode
    }

    private fun handleVowelStroke(
        currentSyllable: HangulSyllable,
        strokeCode: Int,
        history: MutableList<HangulSyllable>,
        composingWord: StringBuilder
    ) {
        lastConsonantCode = 0  // 모음 입력이면 멀티탭 리셋

        // 스트로크 코드를 medial 코드로 변환 (ㅡ, ㅣ는 호환 모음이므로 medial로 변환)
        val strokeMedialCode = when (strokeCode) {
            0x3161 -> 0x1173  // ㅡ (compat) → medial ᅳ
            0x3163 -> 0x1175  // ㅣ (compat) → medial ᅵ
            0x1173 -> 0x1173  // ㅡ (already medial)
            0x1175 -> 0x1175  // ㅣ (already medial)
            0x318D -> 0x318D  // ㆍ (araea)
            0x318E -> 0x318E  // ㆎ (double araea)
            else -> strokeCode
        }

        if (currentSyllable.final != null) {
            // 종성이 있는 상태에서 모음 입력 → 종성 분리하여 새 음절
            if (currentSyllable.final.combinationPair != null) {
                // 겹받침인 경우: 첫 번째 자음만 남기고 두 번째 자음을 초성으로
                val pair = currentSyllable.final.combinationPair
                history.removeAt(history.lastIndex)
                val final = HangulJamo.Final(pair.first)
                history += currentSyllable.copy(final = final)
                composingWord.append(history.lastOrNull()?.string ?: "")
                history.clear()
                val initial = HangulJamo.Final(pair.second).toConsonant()?.toInitial()
                val newSyllable = HangulSyllable(initial = initial)
                history += newSyllable
                history += newSyllable.copy(medial = HangulJamo.Medial(strokeMedialCode))
            } else {
                // 홑받침인 경우: 종성을 초성으로 분리
                history.removeAt(history.lastIndex)
                composingWord.append(history.lastOrNull()?.string ?: "")
                history.clear()
                val initial = currentSyllable.final.toConsonant()?.toInitial()
                val newSyllable = HangulSyllable(initial = initial)
                history += newSyllable
                history += newSyllable.copy(medial = HangulJamo.Medial(strokeMedialCode))
            }
        } else if (currentSyllable.medial != null) {
            // 이미 중성이 있는 상태 → 모음 조합 시도
            val combination = CHEONJIIN_VOWEL_TABLE[currentSyllable.medial.codePoint to strokeMedialCode]
            if (combination != null) {
                history += currentSyllable.copy(medial = HangulJamo.Medial(combination))
            } else {
                // 조합 불가 → 현재 음절 확정 후 새 음절
                composingWord.append(currentSyllable.string)
                history.clear()
                history += HangulSyllable(medial = HangulJamo.Medial(strokeMedialCode))
            }
        } else if (currentSyllable.initial != null) {
            // 초성만 있는 상태 → 중성 추가
            history += currentSyllable.copy(medial = HangulJamo.Medial(strokeMedialCode))
        } else {
            // 빈 상태 → 중성으로 시작
            history += HangulSyllable(medial = HangulJamo.Medial(strokeMedialCode))
        }
    }
}
