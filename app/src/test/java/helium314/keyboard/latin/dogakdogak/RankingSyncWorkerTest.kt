package helium314.keyboard.latin.dogakdogak

import androidx.work.ListenableWorker
import kotlinx.coroutines.test.runTest
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class RankingSyncWorkerTest {

    @Test
    fun shouldRunPeriodicSync_allowsLoggedInUserWithoutRankingVisit() {
        assertTrue(
            RankingSyncWorker.shouldRunPeriodicSync(isAuthenticated = true),
            "Logged-in users should remain eligible even before visiting the ranking tab"
        )
    }

    @Test
    fun shouldRunPeriodicSync_blocksLoggedOutUser() {
        assertFalse(
            RankingSyncWorker.shouldRunPeriodicSync(isAuthenticated = false),
            "Logged-out users should still be excluded from periodic sync"
        )
    }

    @Test
    fun executeRankingSync_authenticatedImeUsage_syncsScoreTouchAndAppData() = runTest {
        var syncedScore: Long? = null
        var syncedTouches: Long? = null
        var syncedAppScores: Map<String, Long>? = null
        var syncedAppTouches: Map<String, Long>? = null

        val result = RankingSyncWorker.executeRankingSync(
            isAuthenticated = true,
            appTrackingAllowed = true,
            dailyScore = 320L,
            dailyTouches = 14L,
            appDailyScores = mapOf("com.everytime.v2" to 120L),
            appDailyTouches = mapOf("com.everytime.v2" to 7L),
            syncDailyClicks = { syncedScore = it; true },
            syncDailyTouches = { syncedTouches = it; true },
            syncAppDailyClicks = { syncedAppScores = it; true },
            syncAppDailyTouches = { syncedAppTouches = it; true }
        )

        assertTrue(result is ListenableWorker.Result.Success)
        assertEquals(320L, syncedScore)
        assertEquals(14L, syncedTouches)
        assertEquals(mapOf("com.everytime.v2" to 120L), syncedAppScores)
        assertEquals(mapOf("com.everytime.v2" to 7L), syncedAppTouches)
    }

    @Test
    fun executeRankingSync_failedScoreSync_requestsRetry() = runTest {
        val result = RankingSyncWorker.executeRankingSync(
            isAuthenticated = true,
            appTrackingAllowed = true,
            dailyScore = 320L,
            dailyTouches = 14L,
            appDailyScores = mapOf("com.everytime.v2" to 120L),
            appDailyTouches = mapOf("com.everytime.v2" to 7L),
            syncDailyClicks = { false },
            syncDailyTouches = { true },
            syncAppDailyClicks = { true },
            syncAppDailyTouches = { true }
        )

        assertTrue(result is ListenableWorker.Result.Retry)
    }
}
