package helium314.keyboard.latin.dogakdogak

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
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
    private val bangersTypeface: Typeface = ResourcesCompat.getFont(context, R.font.bangers)
        ?: Typeface.DEFAULT_BOLD
    private val pacificoTypeface: Typeface = ResourcesCompat.getFont(context, R.font.pacifico)
        ?: Typeface.DEFAULT_BOLD

    // 하트 파티클용 재사용 Path
    private val heartPath = Path()

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

    // 버블 이펙트용 Paint + RectF (draw call마다 할당 방지)
    private val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        isFilterBitmap = true
    }
    private val bitmapDstRect = RectF()

    // 기본 상태
    private var count: Long = 0
    private var isAnimating = false
    private var premiumEffects = false
    private var bubbleComboEffects = false
    private var sf = 1.0f  // 크기 배율

    // 프리미엄: 콤보 리셋 / 100콤보마다 랜덤 변경
    // premiumComboColor (×N) 와 premiumScoreColor (+점수 팝업) 는 항상 서로 다른 색
    // Score/Touch 총 카운트는 테마 색상 고정
    private var premiumComboColor = PREMIUM_COLORS[0]
    private var premiumScoreColor = PREMIUM_COLORS[1]
    private var premiumTiltDeg = 0f

    init {
        randomizePremiumColors()
    }

    // 버블 이펙트 비트맵 (index 0=X, 1~10=digits 0~9)
    private var bubbleBitmaps: Array<Bitmap?>? = null

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
        invalidate()
    }

    fun setBubbleComboEffects(enabled: Boolean) {
        bubbleComboEffects = enabled
        if (enabled && bubbleBitmaps == null) {
            loadBubbleBitmaps()
        }
        invalidate()
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
        val textY = height * 0.72f
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
            randomizePremiumColors()
        }

        // 100콤보마다 색상 랜덤 변경 (프리미엄 색상만, 버블은 고정 이미지)
        if (combo > 0 && combo % 100 == 0) {
            randomizePremiumColors()
        }

        // 프리미엄/버블: 매 키 입력마다 기울기 랜덤 변경 (±10도)
        if (premiumEffects || bubbleComboEffects) {
            premiumTiltDeg = Random.nextFloat() * 20f - 10f
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
            if (premiumEffects || bubbleComboEffects) spawnParticles(10 + milestone.ordinal * 5)
        }

        // 프리미엄/핑크큐티: 고콤보 시 파티클
        if ((premiumEffects || bubbleComboEffects) && combo >= 50 && combo % 3 == 0) {
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

    /** 콤보 색상 / 스코어 팝업 색상 / 기울기 랜덤 선택 — 두 색은 대비되는 색으로 (콤보 리셋 / 100콤보 시 호출) */
    private fun randomizePremiumColors() {
        premiumTiltDeg = Random.nextFloat() * 20f - 10f
        val idx1 = Random.nextInt(PREMIUM_COLORS.size)
        // 최소 8칸 떨어진 색상 선택 → 확실한 색상 대비
        val minOffset = 8
        val offset = minOffset + Random.nextInt(PREMIUM_COLORS.size - 2 * minOffset + 1)
        val idx2 = (idx1 + offset) % PREMIUM_COLORS.size
        premiumComboColor = PREMIUM_COLORS[idx1]
        premiumScoreColor = PREMIUM_COLORS[idx2]
    }

    /** 버블 비트맵 로드 (index 0=X, 1~10=digits 0~9) */
    private fun loadBubbleBitmaps() {
        val ids = intArrayOf(
            R.drawable.combo_char_x,
            R.drawable.combo_char_0, R.drawable.combo_char_1,
            R.drawable.combo_char_2, R.drawable.combo_char_3,
            R.drawable.combo_char_4, R.drawable.combo_char_5,
            R.drawable.combo_char_6, R.drawable.combo_char_7,
            R.drawable.combo_char_8, R.drawable.combo_char_9
        )
        bubbleBitmaps = Array(ids.size) { i ->
            try { BitmapFactory.decodeResource(resources, ids[i]) } catch (_: Exception) { null }
        }
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
        val colors = if (bubbleComboEffects) PINK_PARTICLE_COLORS else PARTICLE_COLORS
        var spawned = 0

        for (p in particles) {
            if (spawned >= count) break
            if (p.alive) continue
            p.reset(
                x = centerX + Random.nextFloat() * 80f - 40f,
                y = startY + Random.nextFloat() * 20f - 10f,
                vx = Random.nextFloat() * 500f - 250f,
                vy = -(Random.nextFloat() * 350f + 100f),
                color = colors[Random.nextInt(colors.size)],
                size = Random.nextFloat() * 7f + 3f
            )
            spawned++
        }
    }

    /** 하트 모양 그리기 (핑크큐티 파티클용) */
    private fun drawHeart(canvas: Canvas, cx: Float, cy: Float, size: Float, paint: Paint) {
        heartPath.reset()
        val s = size
        heartPath.moveTo(cx, cy + s * 0.3f)
        heartPath.cubicTo(cx - s, cy - s * 0.3f, cx - s * 0.5f, cy - s, cx, cy - s * 0.5f)
        heartPath.cubicTo(cx + s * 0.5f, cy - s, cx + s, cy - s * 0.3f, cx, cy + s * 0.3f)
        heartPath.close()
        val savedStyle = paint.style
        paint.style = Paint.Style.FILL
        canvas.drawPath(heartPath, paint)
        paint.style = savedStyle
    }

    // ===================== onDraw =====================

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f
        val now = System.currentTimeMillis()

        // 1. 총 카운트 (하단 고정 — 항상 표시)
        // 프리미엄: Bangers, 핑크큐티: Pacifico, 일반: Pretendard
        val countFont = when {
            bubbleComboEffects -> pacificoTypeface
            premiumEffects -> bangersTypeface
            else -> pretendardBold
        }
        countPaint.typeface = countFont
        countPaint.textSize = 38f * sf
        canvas.drawText(cachedCountText, cx, height * 0.72f, countPaint)
        countPaint.typeface = pretendardBold

        if (!isAnimating) return

        // 콤보 타임아웃
        val idleTime = now - lastComboTime
        val comboAlpha = when {
            idleTime <= COMBO_TIMEOUT_MS -> 1f
            idleTime <= COMBO_TIMEOUT_MS + FADE_DURATION_MS ->
                1f - (idleTime - COMBO_TIMEOUT_MS) / FADE_DURATION_MS.toFloat()
            else -> 0f
        }

        // 2. xN 콤보 카운터 (다이나믹)
        if (comboAlpha > 0f) {
            if (bubbleComboEffects && bubbleBitmaps != null) {
                drawBubbleComboCounter(canvas, cx, comboAlpha, now)
            } else {
                drawComboCounter(canvas, cx, comboAlpha, now)
            }
        }

        // 3. 스코어 팝업 (데미지 넘버)
        var hasActivePopups = false
        for (popup in scorePopups) {
            if (!popup.alive) continue
            val elapsed = now - popup.startTime
            val t = (elapsed / POPUP_DURATION_MS).coerceIn(0f, 1f)
            if (t >= 1f) { popup.alive = false; continue }
            hasActivePopups = true
            drawScorePopup(canvas, popup, t)
        }

        // 4. 마일스톤 라벨
        if (milestoneLabel != null && comboAlpha > 0f) {
            drawMilestoneLabel(canvas, cx, comboAlpha, now)
        }

        // 5. 파티클
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
            if (bubbleComboEffects) {
                drawHeart(canvas, p.x, p.y, p.size * p.life * 1.8f, particlePaint)
            } else {
                canvas.drawCircle(p.x, p.y, p.size * p.life, particlePaint)
            }
        }

        // 종료 체크
        if (comboAlpha <= 0f && !hasActivePopups && !hasActiveParticles) {
            isAnimating = false
            return
        }
        postInvalidateOnAnimation()
    }

    // ===================== xN 콤보 카운터 (텍스트) =====================

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

        val totalScale: Float
        val shakeX: Float
        val shakeY: Float
        val color: Int

        if (premiumEffects) {
            // Premium: 펄스 + 성장 + 흔들림 + 랜덤 색상
            val pulse = 1f + sin(now * 0.008).toFloat() * 0.04f * level
            val growth = 1f + (combo * 0.0006f).coerceAtMost(0.5f)
            totalScale = punchScale * pulse * growth

            val baseShake = level * 3.0f
            val comboShake = (combo * 0.015f).coerceAtMost(20f)
            val shakeAmp = baseShake + comboShake
            val t = now.toFloat()
            shakeX = ((sin(t * 0.15) + cos(t * 0.23) * 0.8 + sin(t * 0.37) * 0.5) * shakeAmp).toFloat()
            shakeY = ((cos(t * 0.17) + sin(t * 0.29) * 0.7 + cos(t * 0.41) * 0.4) * shakeAmp).toFloat()

            color = premiumComboColor
        } else {
            // Normal: 펀치만, 흔들림/펄스/성장/무지개 없음
            totalScale = punchScale
            shakeX = 0f
            shakeY = 0f
            color = comboColor(combo)
        }

        val text = "\u00D7$combo"
        val baseFontSize = (40f + level * 5f) * sf
        val fontSize = baseFontSize * totalScale
        val drawX = cx + shakeX
        val drawY = height * 0.55f + shakeY

        if (premiumEffects) {
            // Premium: Bangers 카툰 폰트 + 두꺼운 흰 테두리 + 컬러 채우기 + 기울기
            outlinePaint.typeface = bangersTypeface
            fillPaint.typeface = bangersTypeface
            shadowPaint.typeface = bangersTypeface

            canvas.save()
            canvas.rotate(premiumTiltDeg, drawX, drawY)

            // 그림자
            shadowPaint.textSize = fontSize
            shadowPaint.color = 0x40000000.toInt()
            shadowPaint.alpha = (alpha * 100).toInt()
            canvas.drawText(text, drawX + 4f, drawY + 4f, shadowPaint)

            // 두꺼운 흰색 테두리 (카툰 스티커 느낌)
            outlinePaint.textSize = fontSize
            outlinePaint.color = Color.WHITE
            outlinePaint.strokeWidth = fontSize * 0.14f
            outlinePaint.alpha = (alpha * 255).toInt()
            canvas.drawText(text, drawX, drawY, outlinePaint)

            // 컬러 채우기
            fillPaint.textSize = fontSize
            fillPaint.color = color
            fillPaint.alpha = (alpha * 255).toInt()
            canvas.drawText(text, drawX, drawY, fillPaint)

            canvas.restore()

            // 폰트 복원
            outlinePaint.typeface = pretendardBold
            fillPaint.typeface = pretendardBold
            shadowPaint.typeface = pretendardBold
            outlinePaint.color = 0xDD000000.toInt()
        } else {
            // Normal: 단순 그림자 + 외곽선 + 채우기
            shadowPaint.textSize = fontSize
            shadowPaint.color = 0x40000000.toInt()
            shadowPaint.alpha = (alpha * 80).toInt()
            canvas.drawText(text, drawX + 3f, drawY + 3f, shadowPaint)

            outlinePaint.textSize = fontSize
            outlinePaint.strokeWidth = 8f + level * 2f
            outlinePaint.alpha = (alpha * 230).toInt()
            outlinePaint.color = 0xDD000000.toInt()
            canvas.drawText(text, drawX, drawY, outlinePaint)

            fillPaint.textSize = fontSize
            fillPaint.color = color
            fillPaint.alpha = (alpha * 255).toInt()
            canvas.drawText(text, drawX, drawY, fillPaint)
        }
    }

    // ===================== 버블 콤보 카운터 (비트맵) =====================

    private fun drawBubbleComboCounter(canvas: Canvas, cx: Float, alpha: Float, now: Long) {
        val bitmaps = bubbleBitmaps ?: return
        val combo = comboCount
        val level = comboLevel(combo)

        // -- 펀치 애니메이션 --
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

        // -- 펄스 + 성장 --
        val pulse = 1f + sin(now * 0.008).toFloat() * 0.04f * level
        val growth = 1f + (combo * 0.0006f).coerceAtMost(0.5f)
        val totalScale = punchScale * pulse * growth

        // -- 흔들림 --
        val baseShake = level * 3.0f
        val comboShake = (combo * 0.015f).coerceAtMost(20f)
        val shakeAmp = baseShake + comboShake
        val t = now.toFloat()
        val shakeX = ((sin(t * 0.15) + cos(t * 0.23) * 0.8 + sin(t * 0.37) * 0.5) * shakeAmp).toFloat()
        val shakeY = ((cos(t * 0.17) + sin(t * 0.29) * 0.7 + cos(t * 0.41) * 0.4) * shakeAmp).toFloat()

        // -- 캐릭터 크기 및 배치 --
        val comboStr = combo.toString()
        val charCount = 1 + comboStr.length  // X + 콤보 자릿수
        val baseCharSize = (34f + level * 3f) * sf
        val charSize = baseCharSize * totalScale

        val totalWidth = charSize * charCount
        val startX = cx - totalWidth / 2f + shakeX
        val centerY = height * 0.52f + shakeY

        bitmapPaint.alpha = (alpha * 255).toInt()

        // 기울기 적용 (프리미엄 이펙트와 함께 사용 시)
        val tiltDeg = if (premiumEffects) premiumTiltDeg else 0f
        if (tiltDeg != 0f) canvas.save()
        if (tiltDeg != 0f) canvas.rotate(tiltDeg, cx + shakeX, centerY)

        // X 비트맵 그리기
        bitmaps[0]?.let { bmp ->
            val top = centerY - charSize / 2f
            bitmapDstRect.set(startX, top, startX + charSize, top + charSize)
            canvas.drawBitmap(bmp, null, bitmapDstRect, bitmapPaint)
        }

        // 숫자 비트맵 그리기
        comboStr.forEachIndexed { i, c ->
            val digit = c - '0'
            bitmaps[digit + 1]?.let { bmp ->
                val left = startX + (1 + i) * charSize
                val top = centerY - charSize / 2f
                bitmapDstRect.set(left, top, left + charSize, top + charSize)
                canvas.drawBitmap(bmp, null, bitmapDstRect, bitmapPaint)
            }
        }

        if (tiltDeg != 0f) canvas.restore()
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

        val color = if (premiumEffects) premiumScoreColor else scorePopupColor(popup.combo)
        val useSpecialFont = premiumEffects || bubbleComboEffects
        val specialFont = if (bubbleComboEffects) pacificoTypeface else bangersTypeface

        if (useSpecialFont) {
            outlinePaint.typeface = specialFont
            fillPaint.typeface = specialFont
            canvas.save()
            canvas.rotate(premiumTiltDeg, drawX, drawY)
        }

        // 외곽선
        outlinePaint.textSize = fontSize
        outlinePaint.strokeWidth = if (useSpecialFont) fontSize * 0.12f else 6f + level * 1f
        outlinePaint.color = if (useSpecialFont) Color.WHITE else 0xDD000000.toInt()
        outlinePaint.alpha = (alpha * (if (useSpecialFont) 255 else 200)).toInt()
        canvas.drawText(text, drawX, drawY, outlinePaint)

        // 채우기
        fillPaint.textSize = fontSize
        fillPaint.color = color
        fillPaint.alpha = (alpha * 255).toInt()
        canvas.drawText(text, drawX, drawY, fillPaint)

        if (useSpecialFont) {
            canvas.restore()
            outlinePaint.typeface = pretendardBold
            fillPaint.typeface = pretendardBold
            outlinePaint.color = 0xDD000000.toInt()
        }
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
        val drawX = cx + shakeX
        val drawY = height * 0.12f
        val useSpecialFont = premiumEffects || bubbleComboEffects
        val specialFont = if (bubbleComboEffects) pacificoTypeface else bangersTypeface

        if (useSpecialFont) {
            outlinePaint.typeface = specialFont
            fillPaint.typeface = specialFont
            canvas.save()
            canvas.rotate(premiumTiltDeg, drawX, drawY)
        }

        // 외곽선
        outlinePaint.textSize = fontSize
        outlinePaint.strokeWidth = if (useSpecialFont) fontSize * 0.14f else 11f
        outlinePaint.color = if (useSpecialFont) Color.WHITE else 0xDD000000.toInt()
        outlinePaint.alpha = (alpha * (if (useSpecialFont) 255 else 230)).toInt()
        canvas.drawText(label, drawX, drawY, outlinePaint)

        // 채우기
        fillPaint.textSize = fontSize
        fillPaint.color = milestoneColor
        fillPaint.alpha = (alpha * 255).toInt()
        canvas.drawText(label, drawX, drawY, fillPaint)

        if (useSpecialFont) {
            canvas.restore()
            outlinePaint.typeface = pretendardBold
            fillPaint.typeface = pretendardBold
            outlinePaint.color = 0xDD000000.toInt()
        }
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

    /** 스코어 팝업 색상 — comboColor와 대비되는 보색 계열 */
    private fun scorePopupColor(combo: Int): Int = when {
        combo >= 1000 -> 0xFF00E5FF.toInt()  // Cyan  (↔ Gold)
        combo >= 900 -> 0xFF64FFDA.toInt()   // Teal  (↔ Red)
        combo >= 800 -> 0xFFFFAB40.toInt()   // Orange (↔ Purple)
        combo >= 700 -> 0xFF69F0AE.toInt()   // Green (↔ Pink)
        combo >= 600 -> 0xFFFF9F0A.toInt()   // Amber (↔ Cyan)
        combo >= 500 -> 0xFF7C4DFF.toInt()   // Purple (↔ Yellow)
        combo >= 400 -> 0xFF00E5FF.toInt()   // Cyan  (↔ Red)
        combo >= 300 -> 0xFF0A84FF.toInt()   // Blue  (↔ Orange)
        combo >= 200 -> 0xFFFFD60A.toInt()   // Yellow (↔ Purple)
        combo >= 100 -> 0xFFFF9F0A.toInt()   // Amber (↔ Blue)
        combo >= 50 -> 0xFFFF6B6B.toInt()    // Coral (↔ Green)
        combo >= 20 -> 0xFF42A5F5.toInt()    // Blue  (↔ Lime)
        combo >= 6 -> 0xFFFF9F0A.toInt()     // Amber (↔ Pale Yellow)
        else -> 0xFFFFCC00.toInt()           // Yellow (↔ White)
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

        private val PARTICLE_COLORS = intArrayOf(
            0xFFFF453A.toInt(), 0xFFFF9F0A.toInt(), 0xFFFFD60A.toInt(),
            0xFF30D158.toInt(), 0xFF0A84FF.toInt(), 0xFFBF5AF2.toInt(),
            0xFFFF375F.toInt()
        )

        /** 핑크큐티 하트 파티클 색상 (다양한 핑크 계열) */
        private val PINK_PARTICLE_COLORS = intArrayOf(
            0xFFFF69B4.toInt(), // Hot Pink
            0xFFFF1493.toInt(), // Deep Pink
            0xFFFFB6C1.toInt(), // Light Pink
            0xFFF06292.toInt(), // Pink 300
            0xFFEC407A.toInt(), // Pink 400
            0xFFE91E63.toInt(), // Pink 500
            0xFFFF80AB.toInt(), // Pink Accent
            0xFFFF4081.toInt(), // Pink Accent 200
            0xFFFFC1E3.toInt(), // Pastel Pink
            0xFFFF8A80.toInt(), // Red Accent Light
        )

        /** 프리미엄 콤보 텍스트 색상 30종 — 색상환 순서 배치 (8칸 이상 떨어지면 대비 보장) */
        private val PREMIUM_COLORS = intArrayOf(
            // Red ~ Orange (0-4)
            0xFFFF3B30.toInt(), // Red
            0xFFFF6B6B.toInt(), // Coral
            0xFFFF6E40.toInt(), // Deep Orange
            0xFFFF9500.toInt(), // Orange
            0xFFFF9F0A.toInt(), // Amber
            // Yellow ~ Lime (5-9)
            0xFFFFCC00.toInt(), // Yellow
            0xFFFFD60A.toInt(), // Bright Yellow
            0xFFCDDC39.toInt(), // Yellow-Green
            0xFFA8D948.toInt(), // Lime
            0xFF00C853.toInt(), // Bright Green
            // Green ~ Teal (10-14)
            0xFF30D158.toInt(), // Green
            0xFF34C759.toInt(), // System Green
            0xFF66BB6A.toInt(), // Medium Green
            0xFF64FFDA.toInt(), // Teal Accent
            0xFF00BCD4.toInt(), // Cyan
            // Blue ~ Indigo (15-19)
            0xFF00E5FF.toInt(), // Light Cyan
            0xFF42A5F5.toInt(), // Light Blue
            0xFF0A84FF.toInt(), // Blue
            0xFF007AFF.toInt(), // System Blue
            0xFF5856D6.toInt(), // Indigo
            // Purple ~ Pink (20-24)
            0xFF7C4DFF.toInt(), // Deep Purple
            0xFFBF5AF2.toInt(), // Purple
            0xFFEA80FC.toInt(), // Light Purple
            0xFFE040FB.toInt(), // Magenta
            0xFFF06292.toInt(), // Pink
            // Pink ~ Orange accent (25-29)
            0xFFEC407A.toInt(), // Deep Pink
            0xFFFF375F.toInt(), // Hot Pink
            0xFFFF2D55.toInt(), // Rose
            0xFFFF8A65.toInt(), // Light Orange
            0xFFFFAB40.toInt(), // Orange Accent
        )
    }
}
