package helium314.keyboard.latin.dogakdogak

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

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
}
