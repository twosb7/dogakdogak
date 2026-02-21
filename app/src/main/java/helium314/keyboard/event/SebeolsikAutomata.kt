// SPDX-License-Identifier: GPL-3.0-only

package helium314.keyboard.event

class SebeolsikAutomata : HangulAutomata {
    override fun combine(
        currentSyllable: HangulSyllable,
        jamo: HangulJamo,
        history: MutableList<HangulSyllable>,
        composingWord: StringBuilder
    ) {
        when (jamo) {
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
            else -> Unit
        }
    }
}
