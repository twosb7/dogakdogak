package helium314.keyboard.latin.dogakdogak

import java.util.ArrayDeque

/**
 * CPS(Clicks Per Second) 기반 콤보 티어 계산 + 연속 입력 스트릭 카운터.
 * 최근 5초간 클릭 타임스탬프를 ArrayDeque로 관리.
 */
class ComboCalculator {

    private val timestamps = ArrayDeque<Long>(64)
    // 삭제 타임스탬프 (정확도 계산용)
    private val deleteTimestamps = ArrayDeque<Long>(32)

    // 연속 입력 스트릭 (5초 이상 쉬면 리셋)
    private var lastClickTime = 0L

    /** 현재 연속 콤보 수 */
    var comboStreak = 0
        private set

    /** 클릭 등록 -> 현재 콤보 티어 반환 + 스트릭 업데이트 */
    fun onClick(): ComboTier {
        val now = System.currentTimeMillis()

        // 5초 이상 쉬었으면 콤보 리셋
        if (now - lastClickTime > COMBO_TIMEOUT_MS) {
            comboStreak = 0
        }
        comboStreak++
        lastClickTime = now

        timestamps.addLast(now)
        pruneOld(now)
        val cps = calculateCps(now)
        return ComboTier.fromCps(cps)
    }

    /** 삭제(백스페이스) 등록 — 정확도 계산에 사용 */
    fun onDelete() {
        val now = System.currentTimeMillis()
        deleteTimestamps.addLast(now)
        pruneDeleteOld(now)
    }

    /**
     * 정확도 가중치 반환 (0.5 ~ 1.0).
     * 최근 5초 윈도우 내 삭제 비율이 높을수록 낮은 값 반환.
     * - 삭제 0%  → 1.0 (감소 없음)
     * - 삭제 20% → ~0.7
     * - 삭제 40%+ → 0.5 (최저)
     */
    fun getAccuracyMultiplier(): Double {
        val now = System.currentTimeMillis()
        pruneOld(now)
        pruneDeleteOld(now)
        val totalKeys = timestamps.size + deleteTimestamps.size
        if (totalKeys < 5) return 1.0 // 샘플 부족 시 페널티 없음
        val deleteRatio = deleteTimestamps.size.toDouble() / totalKeys
        return (1.0 - deleteRatio * 1.5).coerceAtLeast(0.5)
    }

    /** 현재 CPS 반환 (외부에서 조회용) */
    fun currentCps(): Int {
        val now = System.currentTimeMillis()
        pruneOld(now)
        return calculateCps(now)
    }

    private fun pruneOld(now: Long) {
        val threshold = now - WINDOW_MS
        while (timestamps.isNotEmpty() && (timestamps.peekFirst() ?: Long.MAX_VALUE) < threshold) {
            timestamps.pollFirst()
        }
    }

    private fun pruneDeleteOld(now: Long) {
        val threshold = now - WINDOW_MS
        while (deleteTimestamps.isNotEmpty() && (deleteTimestamps.peekFirst() ?: Long.MAX_VALUE) < threshold) {
            deleteTimestamps.pollFirst()
        }
    }

    private fun calculateCps(now: Long): Int {
        if (timestamps.size < 2) return timestamps.size
        val first = timestamps.peekFirst() ?: return 0
        val elapsed = (now - first).coerceAtLeast(1)
        return ((timestamps.size * 1000L) / elapsed).toInt()
    }

    companion object {
        private const val WINDOW_MS = 5000L
        private const val COMBO_TIMEOUT_MS = 5000L
    }
}

/**
 * CPS 기반 속도 티어.
 * speedMultiplier: 기본 100점에 곱해지는 배율.
 */
enum class ComboTier(
    val label: String?,
    val color: Int,
    val particleCount: Int,
    val textScale: Float,
    val rotationRange: Float,
    val speedMultiplier: Int
) {
    NORMAL(null, 0xFFFFFFFF.toInt(), 0, 1.0f, 0f, 1),
    GOOD("Good!", 0xFF30D158.toInt(), 0, 1.1f, 5f, 2),
    GREAT("Great!", 0xFF0A84FF.toInt(), 0, 1.2f, 8f, 4),
    EXCELLENT("Excellent!", 0xFFFF9F0A.toInt(), 5, 1.3f, 12f, 7),
    AWESOME("AWESOME!", 0xFFFF453A.toInt(), 10, 1.5f, 15f, 10);

    companion object {
        fun fromCps(cps: Int): ComboTier = when {
            cps >= 12 -> AWESOME
            cps >= 9 -> EXCELLENT
            cps >= 6 -> GREAT
            cps >= 3 -> GOOD
            else -> NORMAL
        }
    }
}

/**
 * 콤보 마일스톤 - 특정 연속 콤보 달성 시 라벨 표시.
 * 1000+ 콤보는 persistent (사라지지 않음).
 */
enum class ComboMilestone(
    val threshold: Int,
    val label: String,
    val color: Int,
    val bonusScore: Int,
    val persistent: Boolean = false
) {
    NICE(50, "Nice!", 0xFF30D158.toInt(), 50),
    COOL(100, "Cool!", 0xFF0A84FF.toInt(), 100),
    SAVAGE(200, "Savage!", 0xFFBF5AF2.toInt(), 200),
    INSANE(300, "Insane!", 0xFFFF9F0A.toInt(), 300),
    ON_FIRE(400, "On Fire!", 0xFFFF453A.toInt(), 400),
    LEGENDARY(500, "Legendary!", 0xFFFFD60A.toInt(), 600),
    UNSTOPPABLE(600, "Unstoppable!", 0xFF00E5FF.toInt(), 800),
    GODLIKE(700, "Godlike!", 0xFFE040FB.toInt(), 1000),
    MYTHICAL(800, "Mythical!", 0xFF7C4DFF.toInt(), 1200),
    TRANSCENDENT(900, "Transcendent!", 0xFFFF1744.toInt(), 1500),
    GOAT(1000, "GOAT!", 0xFFFFD700.toInt(), 2000, persistent = true);

    companion object {
        fun current(combo: Int): ComboMilestone? =
            entries.lastOrNull { combo >= it.threshold }

        fun justReached(combo: Int): ComboMilestone? =
            entries.firstOrNull { combo == it.threshold }

        fun getBonusScore(combo: Int): Int =
            justReached(combo)?.bonusScore ?: 0

        val ARCADE_MILESTONE_LABELS = mapOf(
            50 to "NICE!",
            100 to "COMBO!",
            200 to "DOUBLE!",
            300 to "TRIPLE!",
            400 to "FEVER!",
            500 to "ULTRA!",
            600 to "MEGA!",
            700 to "MONSTER!",
            800 to "INSANE!",
            900 to "GODLIKE!",
            1000 to "MAX COMBO!",
        )

        val CUTE_MILESTONE_LABELS = mapOf(
            50 to "좋아좋아♡",
            100 to "미쳤어!",
            200 to "헐 대박♡",
            300 to "개잘쳐✦",
            400 to "손가락 뭐야🔥",
            500 to "ㄹㅈㄷ♡",
            600 to "쉬지를 않네!",
            700 to "넌 뭐니✦",
            800 to "인간이 아냐♡",
            900 to "이게 가능?✦",
            1000 to "찐이다♡",
        )
    }
}
