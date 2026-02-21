// SPDX-License-Identifier: GPL-3.0-only

package helium314.keyboard.event

interface HangulAutomata {
    /**
     * 현재 음절에 새 자모를 결합하는 핵심 로직.
     * @param currentSyllable 현재 조합 중인 음절
     * @param jamo 새로 입력된 자모
     * @param history 히스토리 리스트 (직접 수정 가능)
     * @param composingWord 이미 확정된 글자들의 버퍼 (직접 수정 가능)
     */
    fun combine(
        currentSyllable: HangulSyllable,
        jamo: HangulJamo,
        history: MutableList<HangulSyllable>,
        composingWord: StringBuilder
    )
}
