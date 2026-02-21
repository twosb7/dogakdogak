// SPDX-License-Identifier: GPL-3.0-only

package helium314.keyboard.event

class DanmoeumAutomata : HangulAutomata {
    override fun combine(
        currentSyllable: HangulSyllable,
        jamo: HangulJamo,
        history: MutableList<HangulSyllable>,
        composingWord: StringBuilder
    ) {
        when (jamo) {
            is HangulJamo.Consonant -> {
                val initial = jamo.toInitial()
                val final = jamo.toFinal()
                if (currentSyllable.initial != null && currentSyllable.medial != null) {
                    if (currentSyllable.final == null) {
                        val combination = COMBINATION_TABLE_DANMOEUM[currentSyllable.initial.codePoint to (initial?.codePoint ?: -1)]
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
                        val combination = COMBINATION_TABLE_DANMOEUM[pair]
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
                            val combination = COMBINATION_TABLE_DANMOEUM[currentSyllable.medial.codePoint to (medial?.codePoint ?: -1)]
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
                    composingWord.append(history.lastOrNull()?.string ?: "")
                    history.clear()
                    val initial = HangulJamo.Final(pair.second).toConsonant()?.toInitial()
                    val newSyllable = HangulSyllable(initial = initial)
                    history += newSyllable
                    history += newSyllable.copy(medial = medial)
                } else {
                    history.removeAt(history.lastIndex)
                    composingWord.append(history.lastOrNull()?.string ?: "")
                    history.clear()
                    val initial = currentSyllable.final.toConsonant()?.toInitial()
                    val newSyllable = HangulSyllable(initial = initial)
                    history += newSyllable
                    history += newSyllable.copy(medial = medial)
                }
            }
            else -> Unit
        }
    }
}
