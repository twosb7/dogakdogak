package helium314.keyboard.latin.dogakdogak

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import androidx.annotation.DrawableRes
import java.time.LocalDate

/**
 * 앱별 Score/Touch 카운터 관리 (싱글톤).
 *
 * - ClickCountRepository와 동일한 "dogakdogak_counters" SharedPreferences 파일 공유
 * - 키 형식: app_score_{uid}_{pkg}, app_daily_score_{uid}_{pkg} 등
 * - DEFAULT_TRACKED_APPS에 정의된 앱만 추적
 * - 키프레스 핫 패스에서 StateFlow 갱신 안 함 — UI 진입 시에만 조회
 */
class AppClickCountRepository private constructor(private val prefs: SharedPreferences) {

    private var currentUid: String = prefs.getString(KEY_CURRENT_UID, "guest") ?: "guest"

    /** 키프레스 핫패스에서 LocalDate.now() 반복 호출 방지용 캐시 (60초 TTL) */
    @Volatile private var cachedToday: String = try { LocalDate.now().toString() } catch (_: Exception) { "" }
    @Volatile private var cachedTodayTimestamp: Long = System.currentTimeMillis()

    private fun today(): String? {
        val now = System.currentTimeMillis()
        if (now - cachedTodayTimestamp > 60_000L) {
            cachedToday = try { LocalDate.now().toString() } catch (_: Exception) { return null }
            cachedTodayTimestamp = now
        }
        return cachedToday.ifEmpty { null }
    }

    @Synchronized
    fun setCurrentUserId(uid: String) {
        currentUid = uid
        prefs.edit().putString(KEY_CURRENT_UID, uid).commit()
    }

    @Synchronized
    fun incrementAppScore(packageName: String, amount: Long) {
        if (amount <= 0) return
        val uid = currentUid
        val today = today() ?: return
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
        val today = today() ?: return
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
        val today = today() ?: return emptyMap()
        val result = mutableMapOf<String, Long>()
        for (pkg in TRACKED_PACKAGES) {
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
        val today = today() ?: return emptyMap()
        val result = mutableMapOf<String, Long>()
        for (pkg in TRACKED_PACKAGES) {
            val savedDate = prefs.getString(keyAppDate(uid, pkg), "") ?: ""
            if (savedDate == today) {
                val value = prefs.getLong(keyAppDailyTouch(uid, pkg), 0L)
                if (value > 0L) result[pkg] = value
            }
        }
        return result
    }

    fun mergeGuestData(targetUid: String) {
        val today = today() ?: return
        val editor = prefs.edit()
        for (pkg in TRACKED_PACKAGES) {
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

    @Synchronized
    fun resetCurrentUserDailyData() {
        val uid = currentUid
        val today = today() ?: return
        val editor = prefs.edit()
        for (pkg in TRACKED_PACKAGES) {
            editor.putLong(keyAppDailyScore(uid, pkg), 0L)
            editor.putLong(keyAppDailyTouch(uid, pkg), 0L)
            editor.putString(keyAppDate(uid, pkg), today)
        }
        editor.apply()
    }

    @Synchronized
    fun clearCurrentUserData() {
        val uid = currentUid
        val editor = prefs.edit()
        for (pkg in TRACKED_PACKAGES) {
            editor.remove(keyAppScore(uid, pkg))
            editor.remove(keyAppTouch(uid, pkg))
            editor.remove(keyAppDailyScore(uid, pkg))
            editor.remove(keyAppDailyTouch(uid, pkg))
            editor.remove(keyAppDate(uid, pkg))
        }
        editor.apply()
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
        private const val ORDER_SEPARATOR = "|"

        /** 기본 추적 앱 목록 */
        val DEFAULT_TRACKED_APPS: List<TrackedAppMeta> = listOf(
            TrackedAppMeta("com.kakao.talk", "카카오톡", helium314.keyboard.latin.R.drawable.ic_app_kakaotalk),
            TrackedAppMeta("com.instagram.android", "인스타그램", helium314.keyboard.latin.R.drawable.ic_app_instagram),
            TrackedAppMeta("com.facebook.katana", "페이스북", helium314.keyboard.latin.R.drawable.ic_app_facebook),
            TrackedAppMeta("com.twitter.android", "X (트위터)", helium314.keyboard.latin.R.drawable.ic_app_x),
            TrackedAppMeta("com.instagram.barcelona", "쓰레드", helium314.keyboard.latin.R.drawable.ic_app_threads),
            TrackedAppMeta("com.everytime.v2", "에브리타임", helium314.keyboard.latin.R.drawable.ic_app_everytime),
            TrackedAppMeta("com.dcinside.app.android", "디시인사이드", helium314.keyboard.latin.R.drawable.ic_app_dcinside),
            TrackedAppMeta("com.teamblind.blind", "블라인드", helium314.keyboard.latin.R.drawable.ic_app_blind),
            TrackedAppMeta("com.nhn.android.search", "네이버", helium314.keyboard.latin.R.drawable.ic_app_naver),
            TrackedAppMeta("com.nhn.android.navercafe", "네이버 카페", helium314.keyboard.latin.R.drawable.ic_app_navercafe),
            TrackedAppMeta("com.nhn.android.band", "네이버 밴드", helium314.keyboard.latin.R.drawable.ic_app_band),
            TrackedAppMeta("com.google.android.youtube", "유튜브", helium314.keyboard.latin.R.drawable.ic_app_youtube),
            TrackedAppMeta("com.discord", "디스코드", helium314.keyboard.latin.R.drawable.ic_app_discord),
            TrackedAppMeta("com.openai.chatgpt", "ChatGPT", helium314.keyboard.latin.R.drawable.ic_app_chatgpt),
            TrackedAppMeta("com.google.android.apps.bard", "Gemini", helium314.keyboard.latin.R.drawable.ic_app_gemini),
            TrackedAppMeta("com.Slack", "슬랙", helium314.keyboard.latin.R.drawable.ic_app_slack),
            TrackedAppMeta("notion.id", "노션", helium314.keyboard.latin.R.drawable.ic_app_notion),
            TrackedAppMeta("com.google.android.gm", "Gmail", helium314.keyboard.latin.R.drawable.ic_app_gmail),
            TrackedAppMeta("org.telegram.messenger", "텔레그램", helium314.keyboard.latin.R.drawable.ic_app_telegram),
            TrackedAppMeta("kr.co.nowcom.mobile.afreeca", "SOOP", helium314.keyboard.latin.R.drawable.ic_app_soop),
            TrackedAppMeta("com.navercorp.game.android.community", "치지직", helium314.keyboard.latin.R.drawable.ic_app_chzzk),
            TrackedAppMeta("com.android.chrome", "구글 크롬", helium314.keyboard.latin.R.drawable.ic_app_chrome),
            TrackedAppMeta("com.sec.android.app.sbrowser", "삼성 브라우저", helium314.keyboard.latin.R.drawable.ic_app_sbrowser)
        )

        private val TRACKED_APPS_BY_PACKAGE: Map<String, TrackedAppMeta> =
            DEFAULT_TRACKED_APPS.associateBy { it.packageName }

        /** O(1) 패키지명 lookup용 HashSet */
        @JvmField
        val TRACKED_PACKAGES: HashSet<String> = HashSet(TRACKED_APPS_BY_PACKAGE.keys)

        fun getTrackedApps(): List<TrackedAppMeta> = DEFAULT_TRACKED_APPS

        fun getTrackedApp(packageName: String): TrackedAppMeta? = TRACKED_APPS_BY_PACKAGE[packageName]

        fun getManagedTrackedApps(prefs: SharedPreferences): List<TrackedAppMeta> {
            val rawOrder = prefs.getString(PrefsKeys.APP_RANKING_ORDER, null)
                ?.split(ORDER_SEPARATOR)
                ?.map { it.trim() }
                ?.filter { it.isNotEmpty() }
                .orEmpty()
            return resolveTrackedAppsOrder(rawOrder)
        }

        fun saveManagedTrackedApps(prefs: SharedPreferences, packageOrder: List<String>) {
            val normalized = resolveTrackedAppsOrder(packageOrder).map { it.packageName }
            prefs.edit()
                .putString(PrefsKeys.APP_RANKING_ORDER, normalized.joinToString(ORDER_SEPARATOR))
                .apply()
        }

        fun getHiddenSelfPackages(prefs: SharedPreferences): Set<String> {
            val stored = prefs.getStringSet(PrefsKeys.APP_RANKING_HIDE_SELF_PACKAGES, emptySet()).orEmpty()
            return stored.filterTo(linkedSetOf()) { it in TRACKED_PACKAGES }
        }

        fun saveHiddenSelfPackages(prefs: SharedPreferences, packageNames: Set<String>) {
            val normalized = packageNames.filterTo(linkedSetOf()) { it in TRACKED_PACKAGES }
            prefs.edit()
                .putStringSet(PrefsKeys.APP_RANKING_HIDE_SELF_PACKAGES, normalized)
                .apply()
        }

        fun resolveTrackedAppsOrder(packageOrder: List<String>): List<TrackedAppMeta> {
            val seen = LinkedHashSet<String>()
            val ordered = mutableListOf<TrackedAppMeta>()
            for (pkg in packageOrder) {
                val meta = TRACKED_APPS_BY_PACKAGE[pkg] ?: continue
                if (seen.add(pkg)) ordered += meta
            }
            for (meta in DEFAULT_TRACKED_APPS) {
                if (seen.add(meta.packageName)) ordered += meta
            }
            return ordered
        }

        fun filterAppRankingEntries(
            entries: List<RankingEntry>,
            currentUserId: String?,
            hideSelfEnabled: Boolean
        ): List<RankingEntry> {
            if (!hideSelfEnabled || currentUserId.isNullOrBlank()) return entries
            return entries
                .filterNot { it.userId == currentUserId }
                .mapIndexed { index, entry -> entry.copy(rank = (index + 1).toLong()) }
        }

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

data class TrackedAppMeta(
    val packageName: String,
    val displayName: String,
    @DrawableRes val iconRes: Int? = null
)

data class AppRankingEntry(
    val packageName: String,
    val displayName: String,
    val count: Long
)
