package helium314.keyboard.latin.dogakdogak

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.LocalDate

/**
 * Per-user 카운터 관리 (싱글톤).
 *
 * 핵심 설계:
 * - 전용 SharedPreferences 파일("dogakdogak_counters") 사용
 * - currentUid를 SharedPreferences에 영속 저장 → 프로세스 재시작 후에도 동일 계정에 기록
 * - 키보드(LatinIME)와 설정(SettingsActivity) 모두 같은 싱글톤 인스턴스 + 같은 파일 공유
 * - StateFlow로 실시간 UI 갱신
 */
class ClickCountRepository private constructor(private val prefs: SharedPreferences) {

    // 영속 저장된 uid를 복원 (없으면 "guest")
    private var currentUid: String = prefs.getString(KEY_CURRENT_UID, "guest") ?: "guest"

    private val _totalScore = MutableStateFlow(0L)
    val totalScore: StateFlow<Long> = _totalScore.asStateFlow()

    private val _totalTouches = MutableStateFlow(0L)
    val totalTouches: StateFlow<Long> = _totalTouches.asStateFlow()

    private val _dailyScore = MutableStateFlow(0L)
    val dailyScore: StateFlow<Long> = _dailyScore.asStateFlow()

    private val _dailyTouches = MutableStateFlow(0L)
    val dailyTouches: StateFlow<Long> = _dailyTouches.asStateFlow()

    init {
        loadForUser(currentUid)
    }

    /** 현재 uid 반환 */
    fun getCurrentUid(): String = currentUid

    /** 유저 전환 시 호출 — uid를 영속 저장하고 해당 유저의 데이터로 StateFlow 갱신 */
    @Synchronized
    fun setCurrentUserId(uid: String) {
        currentUid = uid
        prefs.edit().putString(KEY_CURRENT_UID, uid).commit()
        loadForUser(uid)
    }

    /** Supabase 프로필 값으로 로컬 누적 카운터 초기화 (로그인 시 사용) */
    @Synchronized
    fun initFromSupabase(uid: String, score: Long, touches: Long) {
        currentUid = uid
        prefs.edit()
            .putString(KEY_CURRENT_UID, uid)
            .putLong(keyTotalScore(uid), score)
            .putLong(keyTotalTouches(uid), touches)
            .commit()
        _totalScore.value = score
        _totalTouches.value = touches
    }

    /** Score 증가 (배치 플러시에서 호출) — synchronized로 read-modify-write 원자성 보장 */
    @Synchronized
    fun incrementScore(amount: Long) {
        if (amount <= 0) return
        val today = try { LocalDate.now().toString() } catch (_: Exception) { return }
        checkDateReset(today)

        val newTotal = _totalScore.value + amount
        val newDaily = _dailyScore.value + amount
        _totalScore.value = newTotal
        _dailyScore.value = newDaily

        prefs.edit()
            .putLong(keyTotalScore(currentUid), newTotal)
            .putLong(keyDailyScore(currentUid), newDaily)
            .putString(keyDate(currentUid), today)
            .apply()
    }

    /** Touch 증가 (배치 플러시에서 호출) — synchronized로 read-modify-write 원자성 보장 */
    @Synchronized
    fun incrementTouch(amount: Long) {
        if (amount <= 0) return
        val today = try { LocalDate.now().toString() } catch (_: Exception) { return }
        checkDateReset(today)

        val newTotal = _totalTouches.value + amount
        val newDaily = _dailyTouches.value + amount
        _totalTouches.value = newTotal
        _dailyTouches.value = newDaily

        prefs.edit()
            .putLong(keyTotalTouches(currentUid), newTotal)
            .putLong(keyDailyTouches(currentUid), newDaily)
            .putString(keyDate(currentUid), today)
            .apply()
    }

    /** SharedPreferences에서 StateFlow 다시 로드 (화면 복귀 시 사용) */
    fun reload() {
        loadForUser(currentUid)
    }

    /** 현재 유저의 일별 Score 반환 (Supabase 동기화용) */
    fun getDailyScoreValue(): Long = _dailyScore.value

    /** 현재 유저의 일별 Touch 반환 (Supabase 동기화용) */
    fun getDailyTouchesValue(): Long = _dailyTouches.value

    @Synchronized
    fun clearCurrentUserData() {
        val uid = currentUid
        prefs.edit()
            .remove(keyTotalScore(uid))
            .remove(keyTotalTouches(uid))
            .remove(keyDailyScore(uid))
            .remove(keyDailyTouches(uid))
            .remove(keyDate(uid))
            .apply()
        if (currentUid == uid) {
            _totalScore.value = 0L
            _totalTouches.value = 0L
            _dailyScore.value = 0L
            _dailyTouches.value = 0L
        }
    }

    /**
     * guest 계정의 점수를 targetUid에 합산 후 guest 데이터 초기화.
     * 로그인 시 setCurrentUserId() 호출 전에 사용.
     *
     * @return Pair(guestDailyScore, guestDailyTouches) — 합산된 guest daily 값
     */
    fun mergeGuestData(targetUid: String): Pair<Long, Long> {
        val today = LocalDate.now().toString()
        val guestDate = prefs.getString(keyDate(GUEST_UID), "") ?: ""

        // guest의 total 값 읽기
        val guestTotalScore = prefs.getLong(keyTotalScore(GUEST_UID), 0L)
        val guestTotalTouches = prefs.getLong(keyTotalTouches(GUEST_UID), 0L)

        // guest의 daily 값 읽기 (날짜가 오늘이면 유지, 아니면 0)
        val guestDailyScore = if (today == guestDate) prefs.getLong(keyDailyScore(GUEST_UID), 0L) else 0L
        val guestDailyTouches = if (today == guestDate) prefs.getLong(keyDailyTouches(GUEST_UID), 0L) else 0L

        if (guestTotalScore == 0L && guestTotalTouches == 0L) {
            return Pair(0L, 0L)
        }

        // target uid의 기존 값 읽기
        val targetTotalScore = prefs.getLong(keyTotalScore(targetUid), 0L)
        val targetTotalTouches = prefs.getLong(keyTotalTouches(targetUid), 0L)
        val targetDate = prefs.getString(keyDate(targetUid), "") ?: ""
        val targetDailyScore = if (today == targetDate) prefs.getLong(keyDailyScore(targetUid), 0L) else 0L
        val targetDailyTouches = if (today == targetDate) prefs.getLong(keyDailyTouches(targetUid), 0L) else 0L

        // 합산
        val mergedTotalScore = targetTotalScore + guestTotalScore
        val mergedTotalTouches = targetTotalTouches + guestTotalTouches
        val mergedDailyScore = targetDailyScore + guestDailyScore
        val mergedDailyTouches = targetDailyTouches + guestDailyTouches

        // target uid에 합산 값 저장 + guest 초기화
        prefs.edit()
            // target uid 업데이트
            .putLong(keyTotalScore(targetUid), mergedTotalScore)
            .putLong(keyTotalTouches(targetUid), mergedTotalTouches)
            .putLong(keyDailyScore(targetUid), mergedDailyScore)
            .putLong(keyDailyTouches(targetUid), mergedDailyTouches)
            .putString(keyDate(targetUid), today)
            // guest 초기화
            .putLong(keyTotalScore(GUEST_UID), 0L)
            .putLong(keyTotalTouches(GUEST_UID), 0L)
            .putLong(keyDailyScore(GUEST_UID), 0L)
            .putLong(keyDailyTouches(GUEST_UID), 0L)
            .commit()

        return Pair(mergedDailyScore, mergedDailyTouches)
    }

    /**
     * Supabase 프로필 값으로 로컬 카운터 초기화 (total + daily 모두).
     * 로그인 시 guest 합산 후 사용.
     */
    @Synchronized
    fun initFromSupabaseWithDaily(uid: String, score: Long, touches: Long, dailyScore: Long, dailyTouches: Long) {
        currentUid = uid
        val today = LocalDate.now().toString()
        prefs.edit()
            .putString(KEY_CURRENT_UID, uid)
            .putLong(keyTotalScore(uid), score)
            .putLong(keyTotalTouches(uid), touches)
            .putLong(keyDailyScore(uid), dailyScore)
            .putLong(keyDailyTouches(uid), dailyTouches)
            .putString(keyDate(uid), today)
            .commit()
        _totalScore.value = score
        _totalTouches.value = touches
        _dailyScore.value = dailyScore
        _dailyTouches.value = dailyTouches
    }

    // --- private helpers ---

    private fun loadForUser(uid: String) {
        val today = LocalDate.now().toString()
        val savedDate = prefs.getString(keyDate(uid), "") ?: ""

        _totalScore.value = prefs.getLong(keyTotalScore(uid), 0L)
        _totalTouches.value = prefs.getLong(keyTotalTouches(uid), 0L)

        if (today == savedDate) {
            _dailyScore.value = prefs.getLong(keyDailyScore(uid), 0L)
            _dailyTouches.value = prefs.getLong(keyDailyTouches(uid), 0L)
        } else {
            _dailyScore.value = 0L
            _dailyTouches.value = 0L
            prefs.edit()
                .putString(keyDate(uid), today)
                .putLong(keyDailyScore(uid), 0L)
                .putLong(keyDailyTouches(uid), 0L)
                .commit()
        }
    }

    private fun checkDateReset(today: String) {
        val savedDate = prefs.getString(keyDate(currentUid), "") ?: ""
        if (today != savedDate) {
            _dailyScore.value = 0L
            _dailyTouches.value = 0L
            prefs.edit()
                .putString(keyDate(currentUid), today)
                .putLong(keyDailyScore(currentUid), 0L)
                .putLong(keyDailyTouches(currentUid), 0L)
                .apply()
        }
    }

    // Per-user SharedPreferences 키
    private fun keyTotalScore(uid: String) = "click_total_$uid"
    private fun keyTotalTouches(uid: String) = "touch_total_$uid"
    private fun keyDailyScore(uid: String) = "daily_click_$uid"
    private fun keyDailyTouches(uid: String) = "daily_touch_$uid"
    private fun keyDate(uid: String) = "counter_date_$uid"

    companion object {
        private const val PREFS_FILE = "dogakdogak_counters"
        private const val KEY_CURRENT_UID = "current_uid"
        private const val GUEST_UID = "guest"

        @Volatile
        private var instance: ClickCountRepository? = null

        fun getInstance(context: Context): ClickCountRepository {
            return instance ?: synchronized(this) {
                instance ?: run {
                    val appCtx = context.applicationContext
                    val deviceCtx = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        appCtx.createDeviceProtectedStorageContext() ?: appCtx
                    } else {
                        appCtx
                    }
                    // One-time migration from credential-encrypted to device-protected storage
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        val dpPrefs = deviceCtx.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE)
                        if (dpPrefs.all.isEmpty()) {
                            deviceCtx.moveSharedPreferencesFrom(appCtx, PREFS_FILE)
                        }
                    }
                    val prefs = deviceCtx.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE)
                    ClickCountRepository(prefs).also { instance = it }
                }
            }
        }
    }
}
