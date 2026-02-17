package helium314.keyboard.latin.dogakdogak

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.view.View
import androidx.core.content.res.ResourcesCompat
import helium314.keyboard.latin.R
import java.text.NumberFormat
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * Canvas 기반 콤보 이펙트 오버레이 뷰 - 키보드 위에 표시.
 *
 * 표시 요소 (동시 렌더링):
 *   1) xN 콤보 카운터 - 흔들림, 색상 순환, 펄스, 점점 커짐
 *   2) 스코어 팝업 - "+1234" 데미지 넘버가 튀어올라 사라짐
 *   3) 마일스톤 라벨 - 50/100/200/.../1000 달성 시 드라마틱 등장
 *   4) 원형 파티클 - 마일스톤 축하 + 고콤보 프리미엄 효과
 *
 * 성능 최적화:
 *   - 람다 할당 제거 (수동 루프)
 *   - 스코어 팝업 스로틀 (80ms 간격)
 *   - 파티클 풀링
 */
class ComboOverlayView(context: Context) : View(context) {

    private val pretendardBold: Typeface = ResourcesCompat.getFont(context, R.font.pretendard_bold)
        ?: Typeface.DEFAULT_BOLD

    // 총 카운트
    private val countPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFF6B00.toInt()
        textSize = 40f
        typeface = pretendardBold
        textAlign = Paint.Align.CENTER
    }

    // 외곽선 (콤보/스코어/마일스톤 공용)
    private val outlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = pretendardBold
        textAlign = Paint.Align.CENTER
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
        color = 0xDD000000.toInt()
    }

    // 채우기 (콤보/스코어/마일스톤 공용)
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = pretendardBold
        textAlign = Paint.Align.CENTER
        style = Paint.Style.FILL
    }

    // 그림자
    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = pretendardBold
        textAlign = Paint.Align.CENTER
        style = Paint.Style.FILL
        color = 0x40000000.toInt()
    }

    private val particlePaint = Paint(Paint.ANTI_ALIAS_FLAG)

    // 기본 상태
    private var count: Long = 0
    private var isAnimating = false
    private var premiumEffects = false
    private var sf = 1.0f  // 크기 배율

    // 카운트 포맷 캐시
    private var cachedCount = -1L
    private var cachedCountText = "0"
    private val numberFormat = NumberFormat.getNumberInstance()

    // -- 콤보 카운터 --
    private var comboCount = 0
    private var lastComboTime = 0L

    // -- 스코어 팝업 풀 + 스로틀 --
    private val scorePopups = Array(MAX_POPUPS) { ScorePopup() }
    private var lastPopupTime = 0L

    // -- 마일스톤 --
    private var milestoneLabel: String? = null
    private var milestoneColor = Color.WHITE
    private var milestoneStartTime = 0L
    private var milestonePersistent = false

    // -- 파티클 풀 --
    private val particles = Array(MAX_PARTICLES) { Particle() }

    fun setCount(newCount: Long) {
        count = newCount
        if (newCount != cachedCount) {
            cachedCount = newCount
            cachedCountText = numberFormat.format(newCount)
        }
        invalidate()
    }

    fun setPremiumEffects(enabled: Boolean) {
        premiumEffects = enabled
    }

    fun setCountColor(color: Int) {
        countPaint.color = color
        invalidate()
    }

    fun setScaleFactor(scale: Float) {
        sf = scale
    }

    /**
     * 터치 좌표가 보이는 카운트 텍스트 영역 안에 있는지 확인.
     */
    fun isTouchOnVisibleContent(touchX: Float, touchY: Float): Boolean {
        val cx = width / 2f
        val textY = height * 0.82f
        countPaint.textSize = 38f * sf
        val textWidth = countPaint.measureText(cachedCountText)
        val fm = countPaint.fontMetrics
        val textTop = textY + fm.top
        val textBottom = textY + fm.bottom
        val pad = 24f * sf
        return touchX >= cx - textWidth / 2 - pad &&
                touchX <= cx + textWidth / 2 + pad &&
                touchY >= textTop - pad &&
                touchY <= textBottom + pad
    }

    /**
     * 매 키 입력 시 호출.
     * 콤보 카운터 + 스코어 팝업 + 마일스톤 + 파티클 전부 동시에.
     */
    fun updateCombo(combo: Int, score: Int) {
        val now = System.currentTimeMillis()

        // 콤보 리셋 감지
        if (combo == 1 && comboCount > 1) {
            milestoneLabel = null
            milestonePersistent = false
        }

        comboCount = combo
        lastComboTime = now

        // 스코어 팝업 스폰 (80ms 스로틀)
        if (now - lastPopupTime >= POPUP_THROTTLE_MS) {
            lastPopupTime = now
            spawnScorePopup(score, combo)
        }

        // 마일스톤 체크
        val milestone = checkMilestone(combo)
        if (milestone != null) {
            milestoneLabel = milestone.label
            milestoneColor = milestone.color
            milestoneStartTime = now
            milestonePersistent = milestone.persistent
            spawnParticles(10 + milestone.ordinal * 5)
        }

        // 프리미엄: 고콤보 시 파티클
        if (premiumEffects && combo >= 50 && combo % 3 == 0) {
            val pCount = when {
                combo >= 500 -> 4
                combo >= 200 -> 3
                combo >= 100 -> 2
                else -> 1
            }
            spawnParticles(pCount)
        }

        isAnimating = true
        postInvalidateOnAnimation()
    }

    /** 마일스톤 임계값 직접 비교 (entries 배열 할당 없음) */
    private fun checkMilestone(combo: Int): ComboMilestone? = when (combo) {
        50 -> ComboMilestone.NICE
        100 -> ComboMilestone.COOL
        200 -> ComboMilestone.SAVAGE
        300 -> ComboMilestone.INSANE
        400 -> ComboMilestone.ON_FIRE
        500 -> ComboMilestone.LEGENDARY
        600 -> ComboMilestone.UNSTOPPABLE
        700 -> ComboMilestone.GODLIKE
        800 -> ComboMilestone.MYTHICAL
        900 -> ComboMilestone.TRANSCENDENT
        1000 -> ComboMilestone.GOAT
        else -> null
    }

    /** 팝업 슬롯 찾기 (람다 할당 없이 수동 루프) */
    private fun spawnScorePopup(score: Int, combo: Int) {
        var freeSlot: ScorePopup? = null
        var oldestSlot: ScorePopup? = null
        var oldestTime = Long.MAX_VALUE

        for (popup in scorePopups) {
            if (!popup.alive) { freeSlot = popup; break }
            if (popup.startTime < oldestTime) {
                oldestTime = popup.startTime
                oldestSlot = popup
            }
        }

        val popup = freeSlot ?: oldestSlot ?: return
        val cx = width / 2f
        val baseY = height * 0.22f
        val level = comboLevel(combo)
        val spread = 18f + level * 10f
        val xOffset = Random.nextFloat() * spread * 2f - spread
        popup.reset(score, combo, cx + xOffset, baseY)
    }

    /** 파티클 스폰 (람다 할당 없이 수동 루프) */
    private fun spawnParticles(count: Int) {
        val centerX = width / 2f
        val startY = height * 0.30f
        var spawned = 0

        for (p in particles) {
            if (spawned >= count) break
            if (p.alive) continue
            p.reset(
                x = centerX + Random.nextFloat() * 80f - 40f,
                y = startY + Random.nextFloat() * 20f - 10f,
                vx = Random.nextFloat() * 500f - 250f,
                vy = -(Random.nextFloat() * 350f + 100f),
                color = PARTICLE_COLORS[Random.nextInt(PARTICLE_COLORS.size)],
                size = Random.nextFloat() * 7f + 3f
            )
            spawned++
        }
    }

    // ===================== onDraw =====================

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f
        val now = System.currentTimeMillis()

        // 1. 총 카운트 (하단 고정 — 항상 표시)
        countPaint.textSize = 38f * sf
        canvas.drawText(cachedCountText, cx, height * 0.82f, countPaint)

        if (!isAnimating) return

        // 콤보 타임아웃
        val idleTime = now - lastComboTime
        val comboAlpha = when {
            idleTime <= COMBO_TIMEOUT_MS -> 1f
            idleTime <= COMBO_TIMEOUT_MS + FADE_DURATION_MS ->
                1f - (idleTime - COMBO_TIMEOUT_MS) / FADE_DURATION_MS.toFloat()
            else -> 0f
        }

        // 1. xN 콤보 카운터 (다이나믹)
        if (comboAlpha > 0f) {
            drawComboCounter(canvas, cx, comboAlpha, now)
        }

        // 2. 스코어 팝업 (데미지 넘버)
        var hasActivePopups = false
        for (popup in scorePopups) {
            if (!popup.alive) continue
            val elapsed = now - popup.startTime
            val t = (elapsed / POPUP_DURATION_MS).coerceIn(0f, 1f)
            if (t >= 1f) { popup.alive = false; continue }
            hasActivePopups = true
            drawScorePopup(canvas, popup, t)
        }

        // 3. 마일스톤 라벨
        if (milestoneLabel != null && comboAlpha > 0f) {
            drawMilestoneLabel(canvas, cx, comboAlpha, now)
        }

        // 4. 파티클
        var hasActiveParticles = false
        val dt = 0.016f
        for (p in particles) {
            if (!p.alive) continue
            hasActiveParticles = true
            p.vy += PARTICLE_GRAVITY * dt
            p.vx *= DRAG; p.vy *= DRAG
            p.x += p.vx * dt; p.y += p.vy * dt
            p.life -= dt / PARTICLE_LIFETIME
            if (p.life <= 0f) { p.alive = false; continue }
            particlePaint.color = p.color
            particlePaint.alpha = (p.life * 255).toInt()
            canvas.drawCircle(p.x, p.y, p.size * p.life, particlePaint)
        }

        // 종료 체크
        if (comboAlpha <= 0f && !hasActivePopups && !hasActiveParticles) {
            isAnimating = false
            return
        }
        postInvalidateOnAnimation()
    }

    // ===================== xN 콤보 카운터 =====================

    private fun drawComboCounter(canvas: Canvas, cx: Float, alpha: Float, now: Long) {
        val combo = comboCount
        val level = comboLevel(combo)

        // -- 펀치 (키 입력 순간 스케일 오버슈트) --
        val punchElapsed = now - lastComboTime
        val punchT = (punchElapsed / PUNCH_MS).coerceIn(0f, 1f)
        val overshoot = 1.3f + level * 0.07f
        val punchScale = when {
            punchT < 0.2f -> {
                val p = punchT / 0.2f
                1f + (overshoot - 1f) * (1f - (1f - p) * (1f - p))
            }
            else -> {
                val p = (punchT - 0.2f) / 0.8f
                overshoot - (overshoot - 1f) * p
            }
        }

        // -- 펄스 (연속 호흡) --
        val pulse = 1f + sin(now * 0.008).toFloat() * 0.04f * level

        // -- 성장 (콤보 유지 -> 점점 커짐) --
        val growth = 1f + (combo * 0.0006f).coerceAtMost(0.5f)

        val totalScale = punchScale * pulse * growth

        // -- 흔들림 (다방향 랜덤) --
        val baseShake = level * 3.0f
        val comboShake = (combo * 0.015f).coerceAtMost(20f)
        val shakeAmp = baseShake + comboShake
        val t = now.toFloat()
        val shakeX = ((sin(t * 0.15) + cos(t * 0.23) * 0.8 + sin(t * 0.37) * 0.5) * shakeAmp).toFloat()
        val shakeY = ((cos(t * 0.17) + sin(t * 0.29) * 0.7 + cos(t * 0.41) * 0.4) * shakeAmp).toFloat()

        // -- 색상 (50+ 콤보: HSV 무지개 순환) --
        val color = if (combo >= 50) {
            val speed = 0.12f + level * 0.04f
            val hue = ((now * speed).toFloat() + combo * 3f) % 360f
            hsvBuffer[0] = hue; hsvBuffer[1] = 0.85f; hsvBuffer[2] = 1f
            Color.HSVToColor(hsvBuffer)
        } else {
            comboColor(combo)
        }

        val text = "\u00D7$combo"
        val baseFontSize = (40f + level * 5f) * sf
        val fontSize = baseFontSize * totalScale
        val drawX = cx + shakeX
        val drawY = height * 0.48f + shakeY

        // 그림자
        shadowPaint.textSize = fontSize
        shadowPaint.alpha = (alpha * 80).toInt()
        canvas.drawText(text, drawX + 3f, drawY + 3f, shadowPaint)

        // 외곽선
        outlinePaint.textSize = fontSize
        outlinePaint.strokeWidth = 8f + level * 2f
        outlinePaint.alpha = (alpha * 230).toInt()
        canvas.drawText(text, drawX, drawY, outlinePaint)

        // 채우기
        fillPaint.textSize = fontSize
        fillPaint.color = color
        fillPaint.alpha = (alpha * 255).toInt()
        canvas.drawText(text, drawX, drawY, fillPaint)
    }

    // ===================== 스코어 팝업 =====================

    private fun drawScorePopup(canvas: Canvas, popup: ScorePopup, t: Float) {
        val level = comboLevel(popup.combo)

        // -- 스케일 오버슈트: 0.3->1.6->1.0->0.7 --
        val scale = when {
            t < 0.12f -> {
                val p = t / 0.12f
                0.3f + 1.3f * (1f - (1f - p) * (1f - p))
            }
            t < 0.28f -> {
                val p = (t - 0.12f) / 0.16f
                1.6f - 0.6f * p
            }
            else -> {
                val p = (t - 0.28f) / 0.72f
                1.0f - p * 0.3f
            }
        }

        // -- 알파 --
        val alpha = when {
            t < 0.06f -> t / 0.06f
            t < 0.45f -> 1f
            else -> (1f - (t - 0.45f) / 0.55f).coerceAtLeast(0f)
        }

        // -- Y: 위로 솟구침 --
        val yOffset = -t * t * 100f

        val text = "+${popup.score}"
        val baseFontSize = (28f + level * 3f) * sf
        val fontSize = baseFontSize * scale
        val drawX = popup.x
        val drawY = popup.y + yOffset

        val color = comboColor(popup.combo)

        // 외곽선
        outlinePaint.textSize = fontSize
        outlinePaint.strokeWidth = 6f + level * 1f
        outlinePaint.alpha = (alpha * 200).toInt()
        canvas.drawText(text, drawX, drawY, outlinePaint)

        // 채우기
        fillPaint.textSize = fontSize
        fillPaint.color = color
        fillPaint.alpha = (alpha * 255).toInt()
        canvas.drawText(text, drawX, drawY, fillPaint)
    }

    // ===================== 마일스톤 라벨 =====================

    private fun drawMilestoneLabel(canvas: Canvas, cx: Float, comboAlpha: Float, now: Long) {
        val label = milestoneLabel ?: return
        val elapsed = now - milestoneStartTime

        if (!milestonePersistent && elapsed > MILESTONE_DURATION_MS) {
            milestoneLabel = null
            return
        }

        val t = if (milestonePersistent) {
            (elapsed / 500f).coerceAtMost(1f)
        } else {
            (elapsed / MILESTONE_DURATION_MS.toFloat()).coerceIn(0f, 1f)
        }

        // 스케일: 0.3->2.2->1.3 (드라마틱 입장)
        val scale = when {
            t < 0.08f -> {
                val p = t / 0.08f
                0.3f + 1.9f * (1f - (1f - p) * (1f - p))
            }
            t < 0.22f -> {
                val p = (t - 0.08f) / 0.14f
                2.2f - 0.9f * p
            }
            else -> 1.3f
        }

        val alpha = if (milestonePersistent) {
            comboAlpha
        } else {
            when {
                t < 0.04f -> t / 0.04f
                t < 0.60f -> 1f
                else -> (1f - (t - 0.60f) / 0.40f).coerceAtLeast(0f)
            } * comboAlpha
        }
        if (alpha <= 0f) return

        val shakeX = sin(now * 0.03).toFloat() * 3f
        val fontSize = 32f * sf * scale
        val drawY = height * 0.12f

        // 외곽선
        outlinePaint.textSize = fontSize
        outlinePaint.strokeWidth = 11f
        outlinePaint.alpha = (alpha * 230).toInt()
        canvas.drawText(label, cx + shakeX, drawY, outlinePaint)

        // 채우기
        fillPaint.textSize = fontSize
        fillPaint.color = milestoneColor
        fillPaint.alpha = (alpha * 255).toInt()
        canvas.drawText(label, cx + shakeX, drawY, fillPaint)
    }

    // ===================== 헬퍼 =====================

    private fun comboLevel(combo: Int): Int = when {
        combo >= 1000 -> 10
        combo >= 900 -> 9
        combo >= 800 -> 8
        combo >= 700 -> 7
        combo >= 600 -> 6
        combo >= 500 -> 5
        combo >= 400 -> 4
        combo >= 300 -> 3
        combo >= 200 -> 2
        combo >= 100 -> 1
        else -> 0
    }

    private fun comboColor(combo: Int): Int = when {
        combo >= 1000 -> 0xFFFFD700.toInt()
        combo >= 900 -> 0xFFFF1744.toInt()
        combo >= 800 -> 0xFF7C4DFF.toInt()
        combo >= 700 -> 0xFFE040FB.toInt()
        combo >= 600 -> 0xFF00E5FF.toInt()
        combo >= 500 -> 0xFFFFD60A.toInt()
        combo >= 400 -> 0xFFFF453A.toInt()
        combo >= 300 -> 0xFFFF9F0A.toInt()
        combo >= 200 -> 0xFFBF5AF2.toInt()
        combo >= 100 -> 0xFF0A84FF.toInt()
        combo >= 50 -> 0xFF30D158.toInt()
        combo >= 20 -> 0xFFA8D948.toInt()
        combo >= 6 -> 0xFFE0E8B0.toInt()
        else -> 0xFFFFFFFF.toInt()
    }

    // -- 스코어 팝업 --
    private class ScorePopup {
        var score = 0; var combo = 0
        var x = 0f; var y = 0f
        var startTime = 0L; var alive = false

        fun reset(score: Int, combo: Int, x: Float, y: Float) {
            this.score = score; this.combo = combo
            this.x = x; this.y = y
            this.startTime = System.currentTimeMillis()
            this.alive = true
        }
    }

    // -- 파티클 --
    private class Particle {
        var x = 0f; var y = 0f
        var vx = 0f; var vy = 0f
        var color = Color.WHITE
        var size = 4f; var life = 1f; var alive = false

        fun reset(x: Float, y: Float, vx: Float, vy: Float, color: Int, size: Float) {
            this.x = x; this.y = y; this.vx = vx; this.vy = vy
            this.color = color; this.size = size
            this.life = 1f; this.alive = true
        }
    }

    companion object {
        private const val MAX_POPUPS = 10
        private const val MAX_PARTICLES = 25
        private const val PUNCH_MS = 200f
        private const val POPUP_DURATION_MS = 800f
        private const val POPUP_THROTTLE_MS = 80L
        private const val COMBO_TIMEOUT_MS = 3000L
        private const val FADE_DURATION_MS = 500L
        private const val MILESTONE_DURATION_MS = 2500L

        private const val PARTICLE_GRAVITY = 600f
        private const val DRAG = 0.98f
        private const val PARTICLE_LIFETIME = 1.2f

        // HSV 버퍼 재활용 (GC 방지)
        private val hsvBuffer = FloatArray(3)

        private val PARTICLE_COLORS = intArrayOf(
            0xFFFF453A.toInt(), 0xFFFF9F0A.toInt(), 0xFFFFD60A.toInt(),
            0xFF30D158.toInt(), 0xFF0A84FF.toInt(), 0xFFBF5AF2.toInt(),
            0xFFFF375F.toInt()
        )
    }
}
