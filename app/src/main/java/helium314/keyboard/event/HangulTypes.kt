// SPDX-License-Identifier: GPL-3.0-only

package helium314.keyboard.event

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
    data class Araea(override val codePoint: Int) : HangulJamo() {
        override val modern: Boolean get() = false
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
                0x318D, 0x318E -> Araea(codePoint) // ㆍ, ㆎ
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
    val uncombinedCompat: String get() = (initial?.toConsonant()?.string ?: initial?.string ?: "") +
            (medial?.toVowel()?.string ?: medial?.string ?: "") + (final?.toConsonant()?.string ?: final?.string ?: "")
    val string: String get() = if (this.combinable) this.combined else this.uncombinedCompat
}

/** 겹받침 코드포인트 -> (첫 번째 자음, 두 번째 자음) 쌍 */
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

val COMBINATION_TABLE_DANMOEUM = mapOf<Pair<Int, Int>, Int>(
        // 모음 더블탭: 단모음→이중모음
        0x1161 to 0x1161 to 0x1163,  // ㅏ+ㅏ=ㅑ
        0x1165 to 0x1165 to 0x1167,  // ㅓ+ㅓ=ㅕ
        0x1169 to 0x1169 to 0x116D,  // ㅗ+ㅗ=ㅛ
        0x116E to 0x116E to 0x1172,  // ㅜ+ㅜ=ㅠ
        // 복합 모음
        0x1169 to 0x1161 to 0x116A,  // ㅗ+ㅏ=ㅘ
        0x1169 to 0x1162 to 0x116B,  // ㅗ+ㅐ=ㅙ
        0x1169 to 0x1175 to 0x116C,  // ㅗ+ㅣ=ㅚ
        0x116E to 0x1165 to 0x116F,  // ㅜ+ㅓ=ㅝ
        0x116E to 0x1166 to 0x1170,  // ㅜ+ㅔ=ㅞ
        0x116E to 0x1175 to 0x1171,  // ㅜ+ㅣ=ㅟ
        0x1173 to 0x1175 to 0x1174,  // ㅡ+ㅣ=ㅢ
        0x1162 to 0x1162 to 0x1164,  // ㅐ+ㅐ=ㅒ
        0x1166 to 0x1166 to 0x1168,  // ㅔ+ㅔ=ㅖ
        // 종성 결합 (두벌식과 동일)
        0x11A8 to 0x11BA to 0x11AA,
        0x11AB to 0x11BD to 0x11AC,
        0x11AB to 0x11C2 to 0x11AD,
        0x11AF to 0x11A8 to 0x11B0,
        0x11AF to 0x11B7 to 0x11B1,
        0x11AF to 0x11B8 to 0x11B2,
        0x11AF to 0x11BA to 0x11B3,
        0x11AF to 0x11C0 to 0x11B4,
        0x11AF to 0x11C1 to 0x11B5,
        0x11AF to 0x11C2 to 0x11B6,
        0x11B8 to 0x11BA to 0x11B9
)

val CHEONJIIN_VOWEL_TABLE = mapOf<Pair<Int, Int>, Int>(
        // ㆍ 시작 조합
        0x318D to 0x1175 to 0x1161,  // ㆍ+ㅣ = ㅏ
        0x318D to 0x1173 to 0x1169,  // ㆍ+ㅡ = ㅗ
        0x318D to 0x318D to 0x318E,  // ㆍ+ㆍ = ㆎ (double dot)
        // ㆎ(더블닷) 조합
        0x318E to 0x1175 to 0x1163,  // ㆎ+ㅣ = ㅑ
        0x318E to 0x1173 to 0x116D,  // ㆎ+ㅡ = ㅛ
        // ㅣ 시작 조합
        0x1175 to 0x318D to 0x1165,  // ㅣ+ㆍ = ㅓ
        0x1175 to 0x318E to 0x1167,  // ㅣ+ㆎ = ㅕ (shortcut: ㅣ+ㆍ+ㆍ)
        // ㅡ 시작 조합
        0x1173 to 0x318D to 0x116E,  // ㅡ+ㆍ = ㅜ
        0x1173 to 0x318E to 0x1172,  // ㅡ+ㆎ = ㅠ (shortcut: ㅡ+ㆍ+ㆍ)
        // 복합 모음 (이미 완성된 모음에서 추가 획)
        0x1161 to 0x1175 to 0x1162,  // ㅏ+ㅣ = ㅐ
        0x1163 to 0x1175 to 0x1164,  // ㅑ+ㅣ = ㅒ
        0x1165 to 0x1175 to 0x1166,  // ㅓ+ㅣ = ㅔ
        0x1167 to 0x1175 to 0x1168,  // ㅕ+ㅣ = ㅖ
        0x1169 to 0x1161 to 0x116A,  // ㅗ+ㅏ = ㅘ
        0x1169 to 0x1175 to 0x116C,  // ㅗ+ㅣ = ㅚ
        0x116A to 0x1175 to 0x116B,  // ㅘ+ㅣ = ㅙ
        0x116C to 0x318D to 0x116A,  // ㅚ+ㆍ = ㅘ (reinterpret)
        0x116E to 0x1165 to 0x116F,  // ㅜ+ㅓ = ㅝ
        0x116E to 0x1175 to 0x1171,  // ㅜ+ㅣ = ㅟ
        0x116F to 0x1175 to 0x1170,  // ㅝ+ㅣ = ㅞ
        0x1171 to 0x318D to 0x116F,  // ㅟ+ㆍ = ㅝ (reinterpret)
        0x1173 to 0x1175 to 0x1174   // ㅡ+ㅣ = ㅢ
)

val CHEONJIIN_CONSONANT_GROUPS = mapOf<Int, Int>(
        // 기본자음 → 격자음
        0x3131 to 0x314B,  // ㄱ → ㅋ
        0x3134 to 0x314C,  // ㄴ → ㅌ
        0x3137 to 0x314C,  // ㄷ → ㅌ
        0x3139 to 0x314B,  // ㄹ → ㅋ
        0x3141 to 0x314D,  // ㅁ → ㅍ
        0x3142 to 0x314D,  // ㅂ → ㅍ
        0x3145 to 0x314E,  // ㅅ → ㅎ
        0x3147 to 0x314A,  // ㅇ → ㅊ
        0x3148 to 0x314A,  // ㅈ → ㅊ
        // 격자음 → 쌍자음 (있는 경우)
        0x314B to 0x3132,  // ㅋ → ㄲ
        0x314C to 0x3138,  // ㅌ → ㄸ
        0x314D to 0x3143,  // ㅍ → ㅃ
        0x314E to 0x3146,  // ㅎ → ㅆ
        0x314A to 0x3149,  // ㅊ → ㅉ
        // 쌍자음 → 기본자음 (순환)
        0x3132 to 0x3131,  // ㄲ → ㄱ
        0x3138 to 0x3137,  // ㄸ → ㄷ
        0x3143 to 0x3142,  // ㅃ → ㅂ
        0x3146 to 0x3145,  // ㅆ → ㅅ
        0x3149 to 0x3148   // ㅉ → ㅈ
)

/** 천지인 종성 결합 테이블 */
val COMBINATION_TABLE_CHEONJIIN_FINAL = mapOf<Pair<Int, Int>, Int>(
        0x11A8 to 0x11BA to 0x11AA,
        0x11AB to 0x11BD to 0x11AC,
        0x11AB to 0x11C2 to 0x11AD,
        0x11AF to 0x11A8 to 0x11B0,
        0x11AF to 0x11B7 to 0x11B1,
        0x11AF to 0x11B8 to 0x11B2,
        0x11AF to 0x11BA to 0x11B3,
        0x11AF to 0x11C0 to 0x11B4,
        0x11AF to 0x11C1 to 0x11B5,
        0x11AF to 0x11C2 to 0x11B6,
        0x11B8 to 0x11BA to 0x11B9
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
