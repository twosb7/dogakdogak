package helium314.keyboard.latin.dogakdogak

import android.content.Context
import android.content.SharedPreferences
import android.os.Build

/**
 * 앱별 Score/Touch 카운터 관리 (싱글톤).
 *
 * - ClickCountRepository와 동일한 "dogakdogak_counters" SharedPreferences 파일 공유
 * - 키 형식: app_score_{uid}_{packageName}, app_touch_{uid}_{packageName}
 * - TRACKED_APPS에 정의된 20개 앱만 추적
 * - 키프레스 핫 패스에서 StateFlow 갱신 안 함 — UI 진입 시에만 getAppRankings() 호출
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
        val key = keyAppScore(currentUid, packageName)
        val current = prefs.getLong(key, 0L)
        prefs.edit().putLong(key, current + amount).apply()
    }

    @Synchronized
    fun incrementAppTouch(packageName: String, amount: Long) {
        if (amount <= 0) return
        val key = keyAppTouch(currentUid, packageName)
        val current = prefs.getLong(key, 0L)
        prefs.edit().putLong(key, current + amount).apply()
    }

    fun getAppRankings(mode: String): List<AppRankingEntry> {
        val uid = currentUid
        return TRACKED_APPS.map { (pkg, name) ->
            val value = if (mode == "score") {
                prefs.getLong(keyAppScore(uid, pkg), 0L)
            } else {
                prefs.getLong(keyAppTouch(uid, pkg), 0L)
            }
            AppRankingEntry(pkg, name, value)
        }.sortedByDescending { it.count }
    }

    fun mergeGuestData(targetUid: String) {
        val editor = prefs.edit()
        for ((pkg, _) in TRACKED_APPS) {
            // Score 합산
            val guestScoreKey = keyAppScore(GUEST_UID, pkg)
            val guestScore = prefs.getLong(guestScoreKey, 0L)
            if (guestScore > 0L) {
                val targetScoreKey = keyAppScore(targetUid, pkg)
                val targetScore = prefs.getLong(targetScoreKey, 0L)
                editor.putLong(targetScoreKey, targetScore + guestScore)
                editor.putLong(guestScoreKey, 0L)
            }
            // Touch 합산
            val guestTouchKey = keyAppTouch(GUEST_UID, pkg)
            val guestTouch = prefs.getLong(guestTouchKey, 0L)
            if (guestTouch > 0L) {
                val targetTouchKey = keyAppTouch(targetUid, pkg)
                val targetTouch = prefs.getLong(targetTouchKey, 0L)
                editor.putLong(targetTouchKey, targetTouch + guestTouch)
                editor.putLong(guestTouchKey, 0L)
            }
        }
        editor.commit()
    }

    // SharedPreferences 키
    private fun keyAppScore(uid: String, pkg: String) = "app_score_${uid}_${pkg}"
    private fun keyAppTouch(uid: String, pkg: String) = "app_touch_${uid}_${pkg}"

    companion object {
        private const val PREFS_FILE = "dogakdogak_counters"
        private const val KEY_CURRENT_UID = "current_uid"
        private const val GUEST_UID = "guest"

        /** 추적 대상 20개 앱: packageName → 한국어 표시명 */
        val TRACKED_APPS: Map<String, String> = linkedMapOf(
            "com.kakao.talk" to "카카오톡",
            "com.nhn.android.search" to "네이버",
            "com.everytime.v2" to "에브리타임",
            "com.teamblind.blind" to "블라인드",
            "com.openai.chatgpt" to "ChatGPT",
            "com.dcinside.app.android" to "디시인사이드",
            "com.discord" to "디스코드",
            "com.Slack" to "슬랙",
            "notion.id" to "노션",
            "com.instagram.android" to "인스타그램",
            "com.nhn.android.navercafe" to "네이버 카페",
            "com.twitter.android" to "X (트위터)",
            "com.instagram.barcelona" to "쓰레드",
            "org.telegram.messenger" to "텔레그램",
            "com.google.android.gm" to "Gmail",
            "com.nhn.android.band" to "네이버 밴드",
            "com.towneers.www" to "당근",
            "com.samsung.android.app.notes" to "삼성 노트",
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
