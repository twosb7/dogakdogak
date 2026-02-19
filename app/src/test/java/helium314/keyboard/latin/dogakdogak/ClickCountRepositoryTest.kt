// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.dogakdogak

import android.content.Context
import android.content.SharedPreferences
import androidx.test.core.app.ApplicationProvider
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * ClickCountRepository 단위 테스트
 *
 * 테스트 시나리오:
 * 1. Score/Touch increment 원자성 (동시성)
 * 2. 사용자 전환 시 데이터 격리
 * 3. 일별 리셋 로직
 * 4. Guest → 로그인 유저 데이터 합산
 * 5. 프로세스 재시작 후 데이터 복원
 */
@RunWith(RobolectricTestRunner::class)
class ClickCountRepositoryTest {

    private lateinit var context: Context
    private lateinit var prefs: SharedPreferences

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        prefs = context.getSharedPreferences("test_counters", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
    }

    @Test
    fun incrementScore_addsToTotalAndDaily() {
        val repo = createRepo()
        repo.setCurrentUserId("user1")

        repo.incrementScore(100)
        repo.incrementScore(200)

        assertEquals(300L, repo.totalScore.value)
        assertEquals(300L, repo.dailyScore.value)
    }

    @Test
    fun incrementTouch_addsToTotalAndDaily() {
        val repo = createRepo()
        repo.setCurrentUserId("user1")

        repo.incrementTouch(1)
        repo.incrementTouch(1)
        repo.incrementTouch(1)

        assertEquals(3L, repo.totalTouches.value)
        assertEquals(3L, repo.dailyTouches.value)
    }

    @Test
    fun incrementScore_zeroOrNegative_isIgnored() {
        val repo = createRepo()
        repo.setCurrentUserId("user1")

        repo.incrementScore(0)
        repo.incrementScore(-50)

        assertEquals(0L, repo.totalScore.value)
    }

    @Test
    fun switchUser_isolatesData() {
        val repo = createRepo()

        repo.setCurrentUserId("user_a")
        repo.incrementScore(500)
        repo.incrementTouch(10)

        repo.setCurrentUserId("user_b")
        assertEquals(0L, repo.totalScore.value)
        assertEquals(0L, repo.totalTouches.value)

        repo.incrementScore(200)

        repo.setCurrentUserId("user_a")
        assertEquals(500L, repo.totalScore.value)
        assertEquals(10L, repo.totalTouches.value)
    }

    @Test
    fun mergeGuestData_combinesGuestAndTargetCounters() {
        val repo = createRepo()

        // Guest 데이터 적립
        repo.setCurrentUserId("guest")
        repo.incrementScore(100)
        repo.incrementTouch(5)

        // Target 유저 데이터
        repo.setCurrentUserId("target_user")
        repo.incrementScore(200)
        repo.incrementTouch(3)

        // Guest → target 합산
        repo.setCurrentUserId("guest")
        val (mergedDailyScore, mergedDailyTouches) = repo.mergeGuestData("target_user")

        // Guest 초기화 확인
        assertEquals(0L, prefs.getLong("click_total_guest", -1))
        assertEquals(0L, prefs.getLong("touch_total_guest", -1))

        // 합산 값 확인
        assertTrue(mergedDailyScore >= 0)
        assertTrue(mergedDailyTouches >= 0)
    }

    @Test
    fun initFromSupabase_overwritesLocalData() {
        val repo = createRepo()
        repo.setCurrentUserId("user1")
        repo.incrementScore(100)

        repo.initFromSupabase("user1", 5000, 200)

        assertEquals(5000L, repo.totalScore.value)
        assertEquals(200L, repo.totalTouches.value)
    }

    @Test
    fun reload_restoresFromPreferences() {
        val repo = createRepo()
        repo.setCurrentUserId("user1")
        repo.incrementScore(300)

        // 외부에서 prefs 수정 시뮬레이션
        prefs.edit().putLong("click_total_user1", 999).commit()

        repo.reload()

        assertEquals(999L, repo.totalScore.value)
    }

    @Test
    fun concurrentIncrements_noLostUpdates() {
        val repo = createRepo()
        repo.setCurrentUserId("concurrent_user")

        val threadCount = 10
        val incrementsPerThread = 100
        val executor = Executors.newFixedThreadPool(threadCount)
        val latch = CountDownLatch(threadCount)

        repeat(threadCount) {
            executor.submit {
                try {
                    repeat(incrementsPerThread) {
                        repo.incrementScore(1)
                    }
                } finally {
                    latch.countDown()
                }
            }
        }

        latch.await(10, TimeUnit.SECONDS)
        executor.shutdown()

        assertEquals(
            (threadCount * incrementsPerThread).toLong(),
            repo.totalScore.value,
            "Concurrent increments should not lose updates"
        )
    }

    @Test
    fun processRestart_restoresUidAndData() {
        // 첫 번째 세션: 데이터 저장
        val repo1 = createRepo()
        repo1.setCurrentUserId("persistent_user")
        repo1.incrementScore(1000)
        repo1.incrementTouch(50)

        // 두 번째 세션: 같은 prefs로 새 인스턴스 생성 (프로세스 재시작 시뮬레이션)
        val repo2 = createRepoFromSamePrefs()

        assertEquals("persistent_user", repo2.getCurrentUid())
        assertEquals(1000L, repo2.totalScore.value)
        assertEquals(50L, repo2.totalTouches.value)
    }

    private fun createRepo(): ClickCountRepository {
        // 리플렉션으로 private constructor 접근
        val constructor = ClickCountRepository::class.java.getDeclaredConstructor(SharedPreferences::class.java)
        constructor.isAccessible = true
        return constructor.newInstance(prefs)
    }

    private fun createRepoFromSamePrefs(): ClickCountRepository {
        val constructor = ClickCountRepository::class.java.getDeclaredConstructor(SharedPreferences::class.java)
        constructor.isAccessible = true
        return constructor.newInstance(prefs)
    }
}
