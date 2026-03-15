// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.dogakdogak

import android.content.Context
import android.content.SharedPreferences
import androidx.test.core.app.ApplicationProvider
import helium314.keyboard.latin.App
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
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
@RunWith(RobolectricTestRunner::class)
class RankingRepositoryTest {

    private fun createSyncPrefs(): SharedPreferences {
        val context = ApplicationProvider.getApplicationContext<App>() as Context
        return context.getSharedPreferences("test_ranking_sync_snapshots", Context.MODE_PRIVATE).also {
            it.edit().clear().commit()
        }
    }

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

    @Test
    fun syncDailyClicks_sameValue_skipsSecondRpc() = runTest {
        val repo = RankingRepository(
            snapshotPrefs = createSyncPrefs(),
            currentUserIdProvider = { "user1" }
        )
        var rpcCalls = 0

        repo.syncDailyClicks(clickCount = 120, executeRpc = { rpcCalls++ })
        repo.syncDailyClicks(clickCount = 120, executeRpc = { rpcCalls++ })

        assertEquals(1, rpcCalls)
    }

    @Test
    fun syncDailyClicks_failedRpc_doesNotPersistSnapshot() = runTest {
        val repo = RankingRepository(
            snapshotPrefs = createSyncPrefs(),
            currentUserIdProvider = { "user1" }
        )
        var rpcCalls = 0

        assertFalse(
            repo.syncDailyClicks(clickCount = 120, executeRpc = {
                rpcCalls++
                error("boom")
            })
        )

        assertTrue(
            repo.syncDailyClicks(clickCount = 120, executeRpc = { rpcCalls++ })
        )
        assertEquals(2, rpcCalls)
    }

    @Test
    fun syncAppDailyClicks_sameEntriesDifferentOrder_skipsDuplicateRpc() = runTest {
        val repo = RankingRepository(
            snapshotPrefs = createSyncPrefs(),
            currentUserIdProvider = { "user1" }
        )
        var rpcCalls = 0

        repo.syncAppDailyClicks(
            dailyScores = linkedMapOf("b.pkg" to 20L, "a.pkg" to 10L),
            executeRpc = { rpcCalls++ }
        )
        repo.syncAppDailyClicks(
            dailyScores = linkedMapOf("a.pkg" to 10L, "b.pkg" to 20L),
            executeRpc = { rpcCalls++ }
        )

        assertEquals(1, rpcCalls)
    }
}
