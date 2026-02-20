package helium314.keyboard.latin.dogakdogak

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import java.time.LocalDate

/**
 * 앱별 Score/Touch 카운터 관리 (싱글톤).
 *
 * - ClickCountRepository와 동일한 "dogakdogak_counters" SharedPreferences 파일 공유
 * - 키 형식: app_score_{uid}_{pkg}, app_daily_score_{uid}_{pkg} 등
 * - TRACKED_APPS에 정의된 20개 앱만 추적
 * - 키프레스 핫 패스에서 StateFlow 갱신 안 함 — UI 진입 시에만 조회
 */
class AppClickCountRepository private constructor(private val prefs: SharedPreferences) {

    private var currentUid: String = prefs.getString(KEY_CURRENT_UID, "guest") ?: "guest"

    @Synchronized
    fun setCurrentUserId(uid: String) {
        currentUid = uid
    }

    @Synchronized
    fun incrementAppScore(packageName: String, amount: Long) {
        if (amount <= 0) return
        val uid = currentUid
        val today = try { LocalDate.now().toString() } catch (_: Exception) { return }
        checkDateReset(uid, packageName, today)

        val totalKey = keyAppScore(uid, packageName)
        val dailyKey = keyAppDailyScore(uid, packageName)
        val editor = prefs.edit()
        editor.putLong(totalKey, prefs.getLong(totalKey, 0L) + amount)
        editor.putLong(dailyKey, prefs.getLong(dailyKey, 0L) + amount)
        editor.putString(keyAppDate(uid, packageName), today)
        editor.apply()
    }

    @Synchronized
    fun incrementAppTouch(packageName: String, amount: Long) {
        if (amount <= 0) return
        val uid = currentUid
        val today = try { LocalDate.now().toString() } catch (_: Exception) { return }
        checkDateReset(uid, packageName, today)

        val totalKey = keyAppTouch(uid, packageName)
        val dailyKey = keyAppDailyTouch(uid, packageName)
        val editor = prefs.edit()
        editor.putLong(totalKey, prefs.getLong(totalKey, 0L) + amount)
        editor.putLong(dailyKey, prefs.getLong(dailyKey, 0L) + amount)
        editor.putString(keyAppDate(uid, packageName), today)
        editor.apply()
    }

    /** Supabase 동기화용: 현재 유저의 모든 앱별 daily score 반환 (0인 앱 제외) */
    fun getAllDailyScores(): Map<String, Long> {
        val uid = currentUid
        val today = try { LocalDate.now().toString() } catch (_: Exception) { return emptyMap() }
        val result = mutableMapOf<String, Long>()
        for (pkg in TRACKED_APPS.keys) {
            val savedDate = prefs.getString(keyAppDate(uid, pkg), "") ?: ""
            if (savedDate == today) {
                val value = prefs.getLong(keyAppDailyScore(uid, pkg), 0L)
                if (value > 0L) result[pkg] = value
            }
        }
        return result
    }

    /** Supabase 동기화용: 현재 유저의 모든 앱별 daily touch 반환 (0인 앱 제외) */
    fun getAllDailyTouches(): Map<String, Long> {
        val uid = currentUid
        val today = try { LocalDate.now().toString() } catch (_: Exception) { return emptyMap() }
        val result = mutableMapOf<String, Long>()
        for (pkg in TRACKED_APPS.keys) {
            val savedDate = prefs.getString(keyAppDate(uid, pkg), "") ?: ""
            if (savedDate == today) {
                val value = prefs.getLong(keyAppDailyTouch(uid, pkg), 0L)
                if (value > 0L) result[pkg] = value
            }
        }
        return result
    }

    fun mergeGuestData(targetUid: String) {
        val today = try { LocalDate.now().toString() } catch (_: Exception) { return }
        val editor = prefs.edit()
        for ((pkg, _) in TRACKED_APPS) {
            // Total Score 합산
            val guestScoreKey = keyAppScore(GUEST_UID, pkg)
            val guestScore = prefs.getLong(guestScoreKey, 0L)
            if (guestScore > 0L) {
                val targetScoreKey = keyAppScore(targetUid, pkg)
                editor.putLong(targetScoreKey, prefs.getLong(targetScoreKey, 0L) + guestScore)
                editor.putLong(guestScoreKey, 0L)
            }
            // Total Touch 합산
            val guestTouchKey = keyAppTouch(GUEST_UID, pkg)
            val guestTouch = prefs.getLong(guestTouchKey, 0L)
            if (guestTouch > 0L) {
                val targetTouchKey = keyAppTouch(targetUid, pkg)
                editor.putLong(targetTouchKey, prefs.getLong(targetTouchKey, 0L) + guestTouch)
                editor.putLong(guestTouchKey, 0L)
            }
            // Daily Score 합산
            val guestDate = prefs.getString(keyAppDate(GUEST_UID, pkg), "") ?: ""
            if (guestDate == today) {
                val guestDailyScore = prefs.getLong(keyAppDailyScore(GUEST_UID, pkg), 0L)
                if (guestDailyScore > 0L) {
                    val targetDailyScoreKey = keyAppDailyScore(targetUid, pkg)
                    editor.putLong(targetDailyScoreKey, prefs.getLong(targetDailyScoreKey, 0L) + guestDailyScore)
                    editor.putLong(keyAppDailyScore(GUEST_UID, pkg), 0L)
                }
                val guestDailyTouch = prefs.getLong(keyAppDailyTouch(GUEST_UID, pkg), 0L)
                if (guestDailyTouch > 0L) {
                    val targetDailyTouchKey = keyAppDailyTouch(targetUid, pkg)
                    editor.putLong(targetDailyTouchKey, prefs.getLong(targetDailyTouchKey, 0L) + guestDailyTouch)
                    editor.putLong(keyAppDailyTouch(GUEST_UID, pkg), 0L)
                }
                editor.putString(keyAppDate(targetUid, pkg), today)
            }
        }
        editor.commit()
    }

    private fun checkDateReset(uid: String, pkg: String, today: String) {
        val savedDate = prefs.getString(keyAppDate(uid, pkg), "") ?: ""
        if (today != savedDate) {
            prefs.edit()
                .putLong(keyAppDailyScore(uid, pkg), 0L)
                .putLong(keyAppDailyTouch(uid, pkg), 0L)
                .putString(keyAppDate(uid, pkg), today)
                .apply()
        }
    }

    // SharedPreferences 키
    private fun keyAppScore(uid: String, pkg: String) = "app_score_${uid}_${pkg}"
    private fun keyAppTouch(uid: String, pkg: String) = "app_touch_${uid}_${pkg}"
    private fun keyAppDailyScore(uid: String, pkg: String) = "app_daily_score_${uid}_${pkg}"
    private fun keyAppDailyTouch(uid: String, pkg: String) = "app_daily_touch_${uid}_${pkg}"
    private fun keyAppDate(uid: String, pkg: String) = "app_date_${uid}_${pkg}"

    companion object {
        private const val PREFS_FILE = "dogakdogak_counters"
        private const val KEY_CURRENT_UID = "current_uid"
        private const val GUEST_UID = "guest"

        /** 추적 대상 20개 앱: packageName → 한국어 표시명 (사용자 많은 순) */
        val TRACKED_APPS: Map<String, String> = linkedMapOf(
            "com.kakao.talk" to "카카오톡",
            "com.instagram.android" to "인스타그램",
            "com.nhn.android.search" to "네이버",
            "com.nhn.android.band" to "네이버 밴드",
            "com.towneers.www" to "당근",
            "com.twitter.android" to "X (트위터)",
            "com.discord" to "디스코드",
            "org.telegram.messenger" to "텔레그램",
            "com.instagram.barcelona" to "쓰레드",
            "com.google.android.gm" to "Gmail",
            "com.samsung.android.app.notes" to "삼성 노트",
            "com.openai.chatgpt" to "ChatGPT",
            "com.Slack" to "슬랙",
            "notion.id" to "노션",
            "com.everytime.v2" to "에브리타임",
            "com.nhn.android.navercafe" to "네이버 카페",
            "com.dcinside.app.android" to "디시인사이드",
            "com.teamblind.blind" to "블라인드",
            "com.nhn.android.blog" to "네이버 블로그",
            "com.mobile.app.clien" to "클리앙"
        )

        /** O(1) 패키지명 lookup용 HashSet */
        @JvmField
        val TRACKED_PACKAGES: HashSet<String> = HashSet(TRACKED_APPS.keys)

        @Volatile
        private var instance: AppClickCountRepository? = null

        fun getInstance(context: Context): AppClickCountRepository {
            return instance ?: synchronized(this) {
                instance ?: run {
                    val appCtx = context.applicationContext
                    val deviceCtx = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        appCtx.createDeviceProtectedStorageContext() ?: appCtx
                    } else {
                        appCtx
                    }
                    val prefs = deviceCtx.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE)
                    AppClickCountRepository(prefs).also { instance = it }
                }
            }
        }
    }
}

data class AppRankingEntry(
    val packageName: String,
    val displayName: String,
    val count: Long
)
