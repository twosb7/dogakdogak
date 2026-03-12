// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.dogakdogak

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * RankingRepository 단위 테스트 (네트워크 없이)
 *
 * 테스트 시나리오:
 * 1. Display name 입력 검증 (XSS, 제어 문자, 제로폭 문자)
 * 2. Rate limiting 동작 확인
 * 3. 캐시 TTL 동작 확인
 */
class RankingRepositoryTest {

    @Test
    fun displayName_controlCharacters_areStripped() {
        val input = "Hello\u0000World\u200B" // null char + zero-width space
        val sanitized = RankingRepository.sanitizeDisplayName(input)
        assertEquals("HelloWorld", sanitized)
    }

    @Test
    fun displayName_maxLength_isCapped() {
        val input = "A".repeat(50)
        val sanitized = RankingRepository.sanitizeDisplayName(input)
        assertEquals(20, sanitized.length)
    }

    @Test
    fun displayName_blank_isRejected() {
        val input = "   "
        val sanitized = RankingRepository.sanitizeDisplayName(input)
        assertTrue(sanitized.isBlank())
    }

    @Test
    fun displayName_zeroWidthOnly_isRejected() {
        val input = "\u200B\u200C\u200D\uFEFF" // zero-width chars
        val sanitized = RankingRepository.sanitizeDisplayName(input)
        assertTrue(sanitized.isBlank())
    }

    @Test
    fun displayName_normalInput_isPreserved() {
        val input = "도각도각유저"
        val sanitized = RankingRepository.sanitizeDisplayName(input)
        assertEquals("도각도각유저", sanitized)
    }

    @Test
    fun displayName_withWhitespace_isTrimmed() {
        val input = "  Hello  "
        val sanitized = RankingRepository.sanitizeDisplayName(input)
        assertEquals("Hello", sanitized)
    }
}
