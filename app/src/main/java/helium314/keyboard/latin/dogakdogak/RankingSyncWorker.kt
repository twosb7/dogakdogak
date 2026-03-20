package helium314.keyboard.latin.dogakdogak

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import helium314.keyboard.latin.utils.DeviceProtectedUtils
import io.github.jan.supabase.gotrue.auth
import io.github.jan.supabase.gotrue.SessionStatus
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

/**
 * 백그라운드 랭킹 동기화 Worker.
 *
 * 조건:
 * 1. 로그인 상태인 유저만
 *
 * Score + Touch 데이터를 글로벌 + 앱별로 모두 동기화.
 */
class RankingSyncWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val sessionStatus = SupabaseModule.client.auth.sessionStatus.first()
        val prefs = DeviceProtectedUtils.getSharedPreferences(applicationContext)
        val repo = ClickCountRepository.getInstance(applicationContext)
        val appRepo = AppClickCountRepository.getInstance(applicationContext)
        val rankingRepo = RankingRepository()

        return executeRankingSync(
            isAuthenticated = sessionStatus is SessionStatus.Authenticated,
            appTrackingAllowed = prefs.getBoolean(PrefsKeys.RANKING_DISCLOSURE_ACCEPTED, false),
            dailyScore = repo.getDailyScoreValue(),
            dailyTouches = repo.getDailyTouchesValue(),
            appDailyScores = appRepo.getAllDailyScores(),
            appDailyTouches = appRepo.getAllDailyTouches(),
            syncDailyClicks = { rankingRepo.syncDailyClicks(it) },
            syncDailyTouches = { rankingRepo.syncDailyTouches(it) },
            syncAppDailyClicks = { rankingRepo.syncAppDailyClicks(it) },
            syncAppDailyTouches = { rankingRepo.syncAppDailyTouches(it) }
        )
    }

    companion object {
        private const val TAG = "RankingSyncWorker"
        const val WORK_NAME = "ranking_sync"
        private const val IMMEDIATE_WORK_NAME = "ranking_sync_immediate"
        private const val IMMEDIATE_SYNC_DELAY_SECONDS = 20L
        private const val IMMEDIATE_SYNC_COOLDOWN_MS = 2 * 60 * 1000L

        internal fun shouldRunPeriodicSync(isAuthenticated: Boolean): Boolean = isAuthenticated

        internal suspend fun executeRankingSync(
            isAuthenticated: Boolean,
            appTrackingAllowed: Boolean,
            dailyScore: Long,
            dailyTouches: Long,
            appDailyScores: Map<String, Long>,
            appDailyTouches: Map<String, Long>,
            syncDailyClicks: suspend (Long) -> Boolean,
            syncDailyTouches: suspend (Long) -> Boolean,
            syncAppDailyClicks: suspend (Map<String, Long>) -> Boolean,
            syncAppDailyTouches: suspend (Map<String, Long>) -> Boolean
        ): Result {
            if (!shouldRunPeriodicSync(isAuthenticated)) {
                Log.d(TAG, "Skip sync: not logged in")
                return Result.success()
            }

            return try {
                if (!syncDailyClicks(dailyScore)) {
                    Log.w(TAG, "Score sync reported failure, will retry")
                    return Result.retry()
                }
                if (!syncDailyTouches(dailyTouches)) {
                    Log.w(TAG, "Touch sync reported failure, will retry")
                    return Result.retry()
                }
                if (appTrackingAllowed) {
                    if (!syncAppDailyClicks(appDailyScores)) {
                        Log.w(TAG, "App score sync reported failure, will retry")
                        return Result.retry()
                    }
                    if (!syncAppDailyTouches(appDailyTouches)) {
                        Log.w(TAG, "App touch sync reported failure, will retry")
                        return Result.retry()
                    }
                }

                Log.d(TAG, "Sync completed successfully")
                Result.success()
            } catch (e: Exception) {
                Log.e(TAG, "Sync failed, will retry", e)
                Result.retry()
            }
        }

        fun enqueuePeriodic(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            val syncRequest = PeriodicWorkRequest.Builder(
                RankingSyncWorker::class.java, 1, TimeUnit.HOURS
            )
                .setConstraints(constraints)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                syncRequest
            )
        }

        fun enqueueSoonIfNeeded(context: Context) {
            val prefs = DeviceProtectedUtils.getSharedPreferences(context)
            val now = System.currentTimeMillis()
            val lastEnqueuedAt = prefs.getLong(PrefsKeys.RANKING_SYNC_LAST_ENQUEUED_AT, 0L)
            if (now - lastEnqueuedAt < IMMEDIATE_SYNC_COOLDOWN_MS) {
                return
            }
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            val request = OneTimeWorkRequestBuilder<RankingSyncWorker>()
                .setInitialDelay(IMMEDIATE_SYNC_DELAY_SECONDS, TimeUnit.SECONDS)
                .setConstraints(constraints)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                IMMEDIATE_WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                request
            )
            prefs.edit().putLong(PrefsKeys.RANKING_SYNC_LAST_ENQUEUED_AT, now).apply()
        }
    }
}
