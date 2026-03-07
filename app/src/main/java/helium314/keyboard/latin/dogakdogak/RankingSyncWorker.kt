package helium314.keyboard.latin.dogakdogak

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import helium314.keyboard.latin.utils.DeviceProtectedUtils
import io.github.jan.supabase.gotrue.auth
import io.github.jan.supabase.gotrue.SessionStatus
import kotlinx.coroutines.flow.first

/**
 * 백그라운드 랭킹 동기화 Worker.
 *
 * 조건:
 * 1. 로그인 상태인 유저만
 * 2. 최근 7일 내 랭킹 화면을 방문한 유저만
 *
 * Score + Touch 데이터를 글로벌 + 앱별로 모두 동기화.
 */
class RankingSyncWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        // 조건 1: 로그인 여부
        val sessionStatus = SupabaseModule.client.auth.sessionStatus.first()
        if (sessionStatus !is SessionStatus.Authenticated) {
            Log.d(TAG, "Skip sync: not logged in")
            return Result.success()
        }

        // 조건 2: 최근 7일 내 랭킹 방문 여부
        val prefs = DeviceProtectedUtils.getSharedPreferences(applicationContext)
        val lastVisit = prefs.getLong(PrefsKeys.LAST_RANKING_VISIT, 0L)
        val daysSinceVisit = (System.currentTimeMillis() - lastVisit) / (1000L * 60 * 60 * 24)
        if (lastVisit == 0L || daysSinceVisit > 7) {
            Log.d(TAG, "Skip sync: last ranking visit was $daysSinceVisit days ago")
            return Result.success()
        }

        // 동기화 실행
        return try {
            val repo = ClickCountRepository.getInstance(applicationContext)
            val appRepo = AppClickCountRepository.getInstance(applicationContext)
            val rankingRepo = RankingRepository()
            val appTrackingAllowed = prefs.getBoolean(PrefsKeys.RANKING_DISCLOSURE_ACCEPTED, false)

            // Score + Touch 모두 동기화 (counter_mode와 무관하게)
            rankingRepo.syncDailyClicks(repo.getDailyScoreValue())
            rankingRepo.syncDailyTouches(repo.getDailyTouchesValue())
            if (appTrackingAllowed) {
                rankingRepo.syncAppDailyClicks(appRepo.getAllDailyScores())
                rankingRepo.syncAppDailyTouches(appRepo.getAllDailyTouches())
            }

            Log.d(TAG, "Sync completed successfully")
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Sync failed, will retry", e)
            Result.retry()
        }
    }

    companion object {
        private const val TAG = "RankingSyncWorker"
        const val WORK_NAME = "ranking_sync"
    }
}
