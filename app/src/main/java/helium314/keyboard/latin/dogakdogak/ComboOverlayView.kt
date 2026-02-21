package helium314.keyboard.latin.dogakdogak

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.view.View
import androidx.core.content.res.ResourcesCompat
import helium314.keyboard.latin.R
import java.text.NumberFormat
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random

/**
 * Canvas 기반 콤보 이펙트 오버레이 뷰 - 키보드 위에 표시.
 *
 * 표시 요소 (동시 렌더링):
 *   1) xN 콤보 카운터 - 스프링 바운스, 글로우, 색상 순환, 잔상
 *   2) 스코어 팝업 - "+1234" 데미지 넘버가 튀어올라 사라짐
 *   3) 마일스톤 라벨 - 50/100/200/.../1000 달성 시 드라마틱 등장
 *   4) 임팩트 링 - 마일스톤 달성 시 확장 링 버스트
 *   5) 파티클 - 원형/하트/스파클/다이아몬드 혼합
 *
 * 성능 최적화:
 *   - 람다 할당 제거 (수동 루프)
 *   - 스코어 팝업 스로틀 (80ms 간격)
 *   - 파티클 풀링
 *   - Paint/Path 사전 할당, Shader 캐싱 불필요 (프레임마다 파라미터 변동)
 */
class ComboOverlayView(context: Context) : View(context) {

    // ===================== Fonts =====================

    private val pretendardBold: Typeface = ResourcesCompat.getFont(context, R.font.pretendard_bold)
        ?: Typeface.DEFAULT_BOLD
    private val bangersTypeface: Typeface = ResourcesCompat.getFont(context, R.font.bangers)
        ?: Typeface.DEFAULT_BOLD
    private val pacificoTypeface: Typeface = ResourcesCompat.getFont(context, R.font.pacifico)
        ?: Typeface.DEFAULT_BOLD
    private val santokkiTypeface: Typeface = ResourcesCompat.getFont(context, R.font.hs_santokki)
        ?: Typeface.DEFAULT_BOLD

    // ===================== Reusable Paths =====================

    private val heartPath = Path()
    private val sparklePath = Path()
    private val diamondPath = Path()

    // ===================== Paints =====================

    private val countPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFF6B00.toInt()
        textSize = 40f
        typeface = pretendardBold
        textAlign = Paint.Align.CENTER
    }

    private val outlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = pretendardBold
        textAlign = Paint.Align.CENTER
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
        color = 0xDD000000.toInt()
    }

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = pretendardBold
        textAlign = Paint.Align.CENTER
        style = Paint.Style.FILL
    }

    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = pretendardBold
        textAlign = Paint.Align.CENTER
        style = Paint.Style.FILL
        color = 0x40000000.toInt()
    }

    private val particlePaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        style = Paint.Style.FILL
    }

    private val ambientPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 4f
    }

    // 버블 이펙트용 Paint + RectF (draw call마다 할당 방지)
    private val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        isFilterBitmap = true
    }
    private val bitmapDstRect = RectF()

    // ===================== State =====================

    private var count: Long = 0
    private var isAnimating = false
    private var premiumEffects = false
    private var bubbleComboEffects = false
    private var sf = 1.0f  // 크기 배율

    // Premium: HSV 기반 연속 색상 순환
    private var premiumHue = 0f  // 0..360
    private var premiumComboColor = PREMIUM_COLORS[0]
    private var premiumScoreColor = PREMIUM_COLORS[1]
    private var premiumTiltDeg = 0f

    // 잔상(Ghost trail) 링버퍼
    private val ghostTrailX = FloatArray(GHOST_TRAIL_SIZE)
    private val ghostTrailY = FloatArray(GHOST_TRAIL_SIZE)
    private var ghostTrailIndex = 0
    private var ghostTrailCount = 0

    // 임팩트 링
    private var impactRingStartTime = 0L
    private var impactRingColor = Color.WHITE
    private var impactRingActive = false
    private var impactRingCx = 0f
    private var impactRingCy = 0f

    // HSV 재사용 배열
    private val hsvArray = floatArrayOf(0f, 1f, 1f)

    init {
        premiumHue = Random.nextFloat() * 360f
        updatePremiumColorsFromHue()
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

    // ===================== Public API =====================

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

    // ===================== updateCombo =====================

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
            impactRingActive = false
            ghostTrailCount = 0
            // 리셋 시 hue를 60~120도 점프 (신선한 느낌)
            premiumHue = (premiumHue + 60f + Random.nextFloat() * 60f) % 360f
            updatePremiumColorsFromHue()
            premiumTiltDeg = Random.nextFloat() * 20f - 10f
        }

        // HSV 색상 순환: 매 키입력 +12도
        if (premiumEffects) {
            premiumHue = (premiumHue + 12f) % 360f
            updatePremiumColorsFromHue()
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
            // 핑크큐티: 귀여운 한글 라벨 사용
            milestoneLabel = if (bubbleComboEffects) {
                CUTE_MILESTONE_LABELS[combo] ?: milestone.label
            } else {
                milestone.label
            }
            milestoneColor = milestone.color
            milestoneStartTime = now
            milestonePersistent = milestone.persistent

            // 임팩트 링 버스트
            impactRingStartTime = now
            impactRingColor = milestone.color
            impactRingActive = true
            impactRingCx = width / 2f
            impactRingCy = height * 0.55f

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

    // ===================== HSV Color Cycling =====================

    /** HSV hue 기반으로 프리미엄 색상 업데이트 (콤보 레벨에 따라 채도/명도 증가) */
    private fun updatePremiumColorsFromHue() {
        val level = comboLevel(comboCount)
        val saturation = (0.7f + level * 0.03f).coerceAtMost(1f)
        val brightness = (0.85f + level * 0.015f).coerceAtMost(1f)

        hsvArray[0] = premiumHue
        hsvArray[1] = saturation
        hsvArray[2] = brightness
        premiumComboColor = Color.HSVToColor(hsvArray)

        // 스코어 색상: 보색 (180도 오프셋)
        hsvArray[0] = (premiumHue + 180f) % 360f
        premiumScoreColor = Color.HSVToColor(hsvArray)
    }

    // ===================== Spring Physics =====================

    /** 감쇠 스프링 바운스: 1.0 + amplitude * e^(-decay*t) * cos(frequency*t) */
    private fun springPunch(elapsedMs: Long, decay: Float, freq: Float, amp: Float): Float {
        val t = elapsedMs / 1000f
        if (t > 0.5f) return 1f
        val envelope = exp(-decay.toDouble() * t).toFloat()
        val oscillation = cos(freq * t)
        return 1f + amp * envelope * oscillation
    }

    /** Pink Cutie squash & stretch: Pair(scaleX, scaleY) 반환 */
    private fun squashStretch(elapsedMs: Long): Pair<Float, Float> {
        val t = (elapsedMs / PUNCH_DURATION_MS).coerceIn(0f, 1f)
        return when {
            t < 0.3f -> {
                // squash: 납작하게
                val p = t / 0.3f
                val sx = 1f + 0.15f * p
                val sy = 1f - 0.15f * p
                sx to sy
            }
            t < 0.6f -> {
                // stretch: 길쭉하게
                val p = (t - 0.3f) / 0.3f
                val sx = 1.15f - 0.25f * p
                val sy = 0.85f + 0.35f * p
                sx to sy
            }
            else -> {
                // settle: 스프링으로 1.0에 수렴
                val p = (t - 0.6f) / 0.4f
                val tSec = p * 0.2f
                val settle = exp(-10.0 * tSec).toFloat() * cos(20f * tSec)
                val sx = 1f - 0.1f * settle
                val sy = 1f + 0.1f * settle
                sx to sy
            }
        }
    }

    // ===================== Internal Helpers =====================

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

    /** 파티클 스폰 - 타입별 분기 (하트/스파클/다이아몬드/원형) */
    private fun spawnParticles(count: Int) {
        val centerX = width / 2f
        val startY = height * 0.30f
        var spawned = 0

        for (p in particles) {
            if (spawned >= count) break
            if (p.alive) continue

            val type: Int
            val color: Int
            val size: Float
            val rotSpeed: Float

            if (bubbleComboEffects) {
                // Pink Cutie: 하트 60% + 스파클 40%
                if (Random.nextFloat() < 0.6f) {
                    type = PARTICLE_HEART
                    color = PINK_PARTICLE_COLORS[Random.nextInt(PINK_PARTICLE_COLORS.size)]
                    size = Random.nextFloat() * 14f + 8f
                    rotSpeed = 0f
                } else {
                    type = PARTICLE_SPARKLE
                    color = SPARKLE_COLORS[Random.nextInt(SPARKLE_COLORS.size)]
                    size = Random.nextFloat() * 10f + 6f
                    rotSpeed = (Random.nextFloat() - 0.5f) * 8f
                }
            } else if (premiumEffects) {
                // Premium: 원형 + 고콤보(200+)에서 다이아몬드 35% 혼합
                if (comboCount >= 200 && Random.nextFloat() < 0.35f) {
                    type = PARTICLE_DIAMOND
                    color = PARTICLE_COLORS[Random.nextInt(PARTICLE_COLORS.size)]
                    size = Random.nextFloat() * 12f + 6f
                    rotSpeed = (Random.nextFloat() - 0.5f) * 6f
                } else {
                    type = PARTICLE_CIRCLE
                    color = PARTICLE_COLORS[Random.nextInt(PARTICLE_COLORS.size)]
                    size = Random.nextFloat() * 10f + 5f
                    rotSpeed = 0f
                }
            } else {
                type = PARTICLE_CIRCLE
                color = PARTICLE_COLORS[Random.nextInt(PARTICLE_COLORS.size)]
                size = Random.nextFloat() * 7f + 3f
                rotSpeed = 0f
            }

            p.reset(
                x = centerX + Random.nextFloat() * 80f - 40f,
                y = startY + Random.nextFloat() * 20f - 10f,
                vx = Random.nextFloat() * 500f - 250f,
                vy = -(Random.nextFloat() * 350f + 100f),
                color = color,
                size = size,
                type = type,
                rotSpeed = rotSpeed
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
        val countFont = when {
            bubbleComboEffects -> santokkiTypeface
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

        // 2. 배경 앰비언트 글로우
        if (comboAlpha > 0f && (premiumEffects || bubbleComboEffects)) {
            drawAmbientGlow(canvas, cx, comboAlpha, now)
        }

        // 3. xN 콤보 카운터 (다이나믹)
        if (comboAlpha > 0f) {
            if (bubbleComboEffects) {
                drawBubbleComboCounter(canvas, cx, comboAlpha, now)
            } else {
                drawComboCounter(canvas, cx, comboAlpha, now)
            }
        }

        // 4. 스코어 팝업 (데미지 넘버)
        var hasActivePopups = false
        for (popup in scorePopups) {
            if (!popup.alive) continue
            val elapsed = now - popup.startTime
            val t = (elapsed / POPUP_DURATION_MS).coerceIn(0f, 1f)
            if (t >= 1f) { popup.alive = false; continue }
            hasActivePopups = true
            drawScorePopup(canvas, popup, t)
        }

        // 5. 마일스톤 라벨
        if (milestoneLabel != null && comboAlpha > 0f) {
            drawMilestoneLabel(canvas, cx, comboAlpha, now)
        }

        // 6. 임팩트 링 버스트
        if (impactRingActive) {
            drawImpactRing(canvas, now)
        }

        // 7. 파티클 (타입별 렌더링)
        var hasActiveParticles = false
        val dt = 0.016f
        for (p in particles) {
            if (!p.alive) continue
            hasActiveParticles = true
            p.vy += PARTICLE_GRAVITY * dt
            p.vx *= DRAG; p.vy *= DRAG
            p.x += p.vx * dt; p.y += p.vy * dt
            p.rotation += p.rotSpeed * dt
            p.life -= dt / PARTICLE_LIFETIME
            if (p.life <= 0f) { p.alive = false; continue }
            particlePaint.color = p.color
            particlePaint.alpha = (p.life * 255).toInt()
            val drawSize = p.size * p.life
            when (p.type) {
                PARTICLE_HEART -> drawHeart(canvas, p.x, p.y, drawSize * 2.8f, particlePaint)
                PARTICLE_SPARKLE -> drawSparkle(canvas, p.x, p.y, drawSize * 2f, p.rotation, particlePaint)
                PARTICLE_DIAMOND -> drawDiamond(canvas, p.x, p.y, drawSize * 1.5f, p.rotation, particlePaint)
                else -> canvas.drawCircle(p.x, p.y, drawSize, particlePaint)
            }
        }

        // 종료 체크
        if (comboAlpha <= 0f && !hasActivePopups && !hasActiveParticles && !impactRingActive) {
            isAnimating = false
            return
        }
        postInvalidateOnAnimation()
    }

    // ===================== 배경 앰비언트 글로우 =====================

    private fun drawAmbientGlow(canvas: Canvas, cx: Float, alpha: Float, now: Long) {
        val level = comboLevel(comboCount)
        if (level < 3) return  // 레벨 3 미만: 글로우 없음

        val glowAlpha = when {
            level >= 6 -> 0.15f
            else -> 0.08f
        } * alpha

        val pulse = 1f + sin(now * 0.004).toFloat() * 0.1f * (level - 2)
        val radius = (80f + level * 20f) * pulse * sf
        val centerY = height * 0.55f

        val baseColor = if (bubbleComboEffects) 0xFFFF69B4.toInt() else premiumComboColor
        val r = Color.red(baseColor)
        val g = Color.green(baseColor)
        val b = Color.blue(baseColor)

        val shader = RadialGradient(
            cx, centerY, radius,
            Color.argb((glowAlpha * 255).toInt(), r, g, b),
            Color.argb(0, r, g, b),
            Shader.TileMode.CLAMP
        )
        ambientPaint.shader = shader
        canvas.drawCircle(cx, centerY, radius, ambientPaint)
        ambientPaint.shader = null
    }

    // ===================== Premium 콤보 카운터 =====================

    private fun drawComboCounter(canvas: Canvas, cx: Float, alpha: Float, now: Long) {
        val combo = comboCount
        val level = comboLevel(combo)
        val punchElapsed = now - lastComboTime

        val totalScale: Float
        val shakeX: Float
        val shakeY: Float
        val color: Int

        if (premiumEffects) {
            // Premium: 스프링 바운스 + 펄스 + 성장 + 흔들림 + HSV 색상
            val punchScale = springPunch(
                punchElapsed,
                PREMIUM_SPRING_DECAY, PREMIUM_SPRING_FREQ,
                PREMIUM_SPRING_AMP + level * 0.02f
            )
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
            // Normal: 단순 오버슈트 펀치만
            val punchT = (punchElapsed / 200f).coerceIn(0f, 1f)
            val overshoot = 1.3f
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
            // Premium: Bangers + 글로우 + 잔상 + 두꺼운 테두리
            outlinePaint.typeface = bangersTypeface
            fillPaint.typeface = bangersTypeface
            shadowPaint.typeface = bangersTypeface
            glowPaint.typeface = bangersTypeface

            canvas.save()
            canvas.rotate(premiumTiltDeg, drawX, drawY)

            // -- 잔상 (Ghost trail / Afterimage) --
            if (level >= 1 && ghostTrailCount > 0) {
                val trailAlphas = floatArrayOf(0.07f, 0.15f, 0.3f)
                val startIdx = maxOf(0, GHOST_TRAIL_SIZE - ghostTrailCount)
                for (i in startIdx until GHOST_TRAIL_SIZE) {
                    val bufIdx = (ghostTrailIndex - GHOST_TRAIL_SIZE + i + GHOST_TRAIL_SIZE * 2) % GHOST_TRAIL_SIZE
                    val trailAlpha = trailAlphas[i] * alpha * (level / 10f).coerceIn(0.3f, 1f)
                    if (trailAlpha < 0.01f) continue

                    fillPaint.textSize = fontSize
                    fillPaint.color = color
                    fillPaint.alpha = (trailAlpha * 255).toInt()
                    canvas.drawText(text, ghostTrailX[bufIdx], ghostTrailY[bufIdx], fillPaint)
                }
            }

            // 잔상 링버퍼 업데이트
            ghostTrailX[ghostTrailIndex] = drawX
            ghostTrailY[ghostTrailIndex] = drawY
            ghostTrailIndex = (ghostTrailIndex + 1) % GHOST_TRAIL_SIZE
            if (ghostTrailCount < GHOST_TRAIL_SIZE) ghostTrailCount++

            // -- 네온 글로우 (setShadowLayer) --
            val glowLevel = (level - 1).coerceAtLeast(0)
            if (glowLevel > 0) {
                val glowRadius = (8f + glowLevel * 5f) * sf
                val glowPulse = 1f + sin(now * 0.006).toFloat() * 0.3f * glowLevel
                glowPaint.textSize = fontSize
                glowPaint.setShadowLayer(glowRadius * glowPulse, 0f, 0f, color)
                glowPaint.color = color
                glowPaint.alpha = (alpha * 180).toInt()
                canvas.drawText(text, drawX, drawY, glowPaint)
                glowPaint.setShadowLayer(0f, 0f, 0f, 0)
            }

            // 그림자
            shadowPaint.textSize = fontSize
            shadowPaint.color = 0x40000000.toInt()
            shadowPaint.alpha = (alpha * 100).toInt()
            canvas.drawText(text, drawX + 4f, drawY + 4f, shadowPaint)

            // 두꺼운 흰색 테두리
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
            glowPaint.typeface = pretendardBold
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

    // ===================== Pink Cutie 콤보 카운터 (텍스트 기반) =====================

    private fun drawBubbleComboCounter(canvas: Canvas, cx: Float, alpha: Float, now: Long) {
        val combo = comboCount
        val level = comboLevel(combo)
        val punchElapsed = now - lastComboTime

        // 스프링 바운스
        val punchScale = springPunch(
            punchElapsed,
            CUTE_SPRING_DECAY, CUTE_SPRING_FREQ,
            CUTE_SPRING_AMP + level * 0.02f
        )

        // Squash & Stretch
        val (squashX, squashY) = squashStretch(punchElapsed)

        val pulse = 1f + sin(now * 0.008).toFloat() * 0.04f * level
        val growth = 1f + (combo * 0.0006f).coerceAtMost(0.5f)
        val baseScale = punchScale * pulse * growth

        // 흔들림
        val baseShake = level * 3.0f
        val comboShake = (combo * 0.015f).coerceAtMost(20f)
        val shakeAmp = baseShake + comboShake
        val t = now.toFloat()
        val shakeX = ((sin(t * 0.15) + cos(t * 0.23) * 0.8 + sin(t * 0.37) * 0.5) * shakeAmp).toFloat()
        val shakeY = ((cos(t * 0.17) + sin(t * 0.29) * 0.7 + cos(t * 0.41) * 0.4) * shakeAmp).toFloat()

        val text = "\u00D7$combo"
        val baseFontSize = (40f + level * 5f) * sf
        val fontSize = baseFontSize * baseScale
        val drawX = cx + shakeX
        val drawY = height * 0.55f + shakeY

        outlinePaint.typeface = santokkiTypeface
        fillPaint.typeface = santokkiTypeface
        shadowPaint.typeface = santokkiTypeface
        glowPaint.typeface = santokkiTypeface

        canvas.save()
        canvas.rotate(premiumTiltDeg, drawX, drawY)
        // Squash & Stretch 적용
        canvas.scale(squashX, squashY, drawX, drawY)

        // -- 핑크 글로우 --
        val glowLevel = (level - 1).coerceAtLeast(0)
        if (glowLevel > 0) {
            val glowRadius = (8f + glowLevel * 5f) * sf
            val glowPulse = 1f + sin(now * 0.006).toFloat() * 0.3f * glowLevel
            glowPaint.textSize = fontSize
            glowPaint.setShadowLayer(glowRadius * glowPulse, 0f, 0f, 0xFFFF69B4.toInt())
            glowPaint.color = 0xFFFF69B4.toInt()
            glowPaint.alpha = (alpha * 150).toInt()
            canvas.drawText(text, drawX, drawY, glowPaint)
            glowPaint.setShadowLayer(0f, 0f, 0f, 0)
        }

        // 그림자
        shadowPaint.textSize = fontSize
        shadowPaint.color = 0x30000000.toInt()
        shadowPaint.alpha = (alpha * 80).toInt()
        canvas.drawText(text, drawX + 3f, drawY + 3f, shadowPaint)

        // 두꺼운 하얀 테두리 (카툰 스티커 느낌)
        outlinePaint.textSize = fontSize
        outlinePaint.color = Color.WHITE
        outlinePaint.strokeWidth = fontSize * 0.16f
        outlinePaint.alpha = (alpha * 255).toInt()
        canvas.drawText(text, drawX, drawY, outlinePaint)

        // 핑크 그래디언트 채우기 (Hot Pink → Pastel Pink)
        val gradientShader = LinearGradient(
            drawX, drawY - fontSize * 0.8f,
            drawX, drawY + fontSize * 0.2f,
            0xFFFF1493.toInt(),  // Hot Pink (상단)
            0xFFFFB6C1.toInt(),  // Pastel Pink (하단)
            Shader.TileMode.CLAMP
        )
        fillPaint.textSize = fontSize
        fillPaint.shader = gradientShader
        fillPaint.alpha = (alpha * 255).toInt()
        canvas.drawText(text, drawX, drawY, fillPaint)
        fillPaint.shader = null

        canvas.restore()

        // 폰트 복원
        outlinePaint.typeface = pretendardBold
        fillPaint.typeface = pretendardBold
        shadowPaint.typeface = pretendardBold
        glowPaint.typeface = pretendardBold
        outlinePaint.color = 0xDD000000.toInt()
    }

    // ===================== 스코어 팝업 =====================

    private fun drawScorePopup(canvas: Canvas, popup: ScorePopup, t: Float) {
        val level = comboLevel(popup.combo)

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

        val alpha = when {
            t < 0.06f -> t / 0.06f
            t < 0.45f -> 1f
            else -> (1f - (t - 0.45f) / 0.55f).coerceAtLeast(0f)
        }

        val yOffset = -t * t * 100f
        val text = "+${popup.score}"
        val baseFontSize = (28f + level * 3f) * sf
        val fontSize = baseFontSize * scale
        val drawX = popup.x
        val drawY = popup.y + yOffset

        val color = if (premiumEffects) premiumScoreColor else scorePopupColor(popup.combo)
        val useSpecialFont = premiumEffects || bubbleComboEffects
        val specialFont = if (bubbleComboEffects) santokkiTypeface else bangersTypeface

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

        // 핑크큐티 한글 라벨: Pretendard (한글 지원), 그 외: 특수 폰트
        val useSpecialFont = premiumEffects || bubbleComboEffects
        val specialFont = when {
            bubbleComboEffects -> santokkiTypeface
            premiumEffects -> bangersTypeface
            else -> pretendardBold
        }

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

    // ===================== 임팩트 링 버스트 =====================

    private fun drawImpactRing(canvas: Canvas, now: Long) {
        val elapsed = now - impactRingStartTime
        val t = elapsed / IMPACT_RING_DURATION_MS
        if (t >= 1f) {
            impactRingActive = false
            return
        }

        val radius = (20f + t * 100f) * sf
        val alpha = ((1f - t) * 0.8f * 255).toInt()
        val strokeWidth = (4f + (1f - t) * 4f) * sf

        ringPaint.color = impactRingColor
        ringPaint.alpha = alpha
        ringPaint.strokeWidth = strokeWidth
        canvas.drawCircle(impactRingCx, impactRingCy, radius, ringPaint)

        // 두 번째 링 (더 작고 희미)
        if (t < 0.7f) {
            val radius2 = (10f + t * 60f) * sf
            ringPaint.alpha = (alpha * 0.4f).toInt()
            ringPaint.strokeWidth = strokeWidth * 0.6f
            canvas.drawCircle(impactRingCx, impactRingCy, radius2, ringPaint)
        }
    }

    // ===================== 파티클 도형 그리기 =====================

    /** 하트 모양 (핑크큐티 파티클) */
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

    /** 4각 별 스파클 (핑크큐티 파티클) - 회전 지원 */
    private fun drawSparkle(canvas: Canvas, cx: Float, cy: Float, size: Float, rotation: Float, paint: Paint) {
        sparklePath.reset()
        val outer = size
        val inner = size * 0.3f
        // 8 꼭지점 (4개 뾰족 + 4개 안쪽)
        for (i in 0 until 8) {
            val baseAngle = i * PI.toFloat() / 4f
            val angle = baseAngle + rotation
            val r = if (i % 2 == 0) outer else inner
            val px = cx + r * cos(angle)
            val py = cy + r * sin(angle)
            if (i == 0) sparklePath.moveTo(px, py) else sparklePath.lineTo(px, py)
        }
        sparklePath.close()
        val savedStyle = paint.style
        paint.style = Paint.Style.FILL
        canvas.drawPath(sparklePath, paint)
        paint.style = savedStyle
    }

    /** 다이아몬드 (프리미엄 파티클) - 회전 지원 */
    private fun drawDiamond(canvas: Canvas, cx: Float, cy: Float, size: Float, rotation: Float, paint: Paint) {
        diamondPath.reset()
        val cosR = cos(rotation)
        val sinR = sin(rotation)
        // top
        diamondPath.moveTo(cx - size * sinR, cy - size * cosR)
        // right
        diamondPath.lineTo(cx + size * cosR, cy + size * sinR)
        // bottom
        diamondPath.lineTo(cx + size * sinR, cy + size * cosR)
        // left
        diamondPath.lineTo(cx - size * cosR, cy - size * sinR)
        diamondPath.close()
        val savedStyle = paint.style
        paint.style = Paint.Style.FILL
        canvas.drawPath(diamondPath, paint)
        paint.style = savedStyle
    }

    // ===================== 색상 헬퍼 =====================

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
        combo >= 1000 -> 0xFF00E5FF.toInt()
        combo >= 900 -> 0xFF64FFDA.toInt()
        combo >= 800 -> 0xFFFFAB40.toInt()
        combo >= 700 -> 0xFF69F0AE.toInt()
        combo >= 600 -> 0xFFFF9F0A.toInt()
        combo >= 500 -> 0xFF7C4DFF.toInt()
        combo >= 400 -> 0xFF00E5FF.toInt()
        combo >= 300 -> 0xFF0A84FF.toInt()
        combo >= 200 -> 0xFFFFD60A.toInt()
        combo >= 100 -> 0xFFFF9F0A.toInt()
        combo >= 50 -> 0xFFFF6B6B.toInt()
        combo >= 20 -> 0xFF42A5F5.toInt()
        combo >= 6 -> 0xFFFF9F0A.toInt()
        else -> 0xFFFFCC00.toInt()
    }

    // ===================== 내부 클래스 =====================

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

    private class Particle {
        var x = 0f; var y = 0f
        var vx = 0f; var vy = 0f
        var color = Color.WHITE
        var size = 4f; var life = 1f; var alive = false
        var rotation = 0f      // 현재 회전각 (rad)
        var rotSpeed = 0f      // 회전 속도 (rad/s)
        var type = 0           // 0=circle, 1=heart, 2=sparkle, 3=diamond

        fun reset(x: Float, y: Float, vx: Float, vy: Float, color: Int, size: Float,
                  type: Int = 0, rotSpeed: Float = 0f) {
            this.x = x; this.y = y; this.vx = vx; this.vy = vy
            this.color = color; this.size = size
            this.type = type; this.rotSpeed = rotSpeed
            this.rotation = 0f
            this.life = 1f; this.alive = true
        }
    }

    companion object {
        private const val MAX_POPUPS = 10
        private const val MAX_PARTICLES = 40
        private const val PUNCH_DURATION_MS = 400f
        private const val POPUP_DURATION_MS = 800f
        private const val POPUP_THROTTLE_MS = 80L
        private const val COMBO_TIMEOUT_MS = 5000L
        private const val FADE_DURATION_MS = 500L
        private const val MILESTONE_DURATION_MS = 2500L
        private const val IMPACT_RING_DURATION_MS = 500f

        private const val PARTICLE_GRAVITY = 600f
        private const val DRAG = 0.98f
        private const val PARTICLE_LIFETIME = 1.2f

        private const val GHOST_TRAIL_SIZE = 3

        // 스프링 물리 상수
        private const val PREMIUM_SPRING_DECAY = 12f
        private const val PREMIUM_SPRING_FREQ = 25f
        private const val PREMIUM_SPRING_AMP = 0.5f
        private const val CUTE_SPRING_DECAY = 8f
        private const val CUTE_SPRING_FREQ = 18f
        private const val CUTE_SPRING_AMP = 0.6f

        // 파티클 타입
        private const val PARTICLE_CIRCLE = 0
        private const val PARTICLE_HEART = 1
        private const val PARTICLE_SPARKLE = 2
        private const val PARTICLE_DIAMOND = 3

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

        /** 스파클 파티클 색상 (반짝이 느낌) */
        private val SPARKLE_COLORS = intArrayOf(
            0xFFFFFFFF.toInt(), // White
            0xFFFFB6C1.toInt(), // Light Pink
            0xFFE6E6FA.toInt(), // Lavender
        )

        /** 프리미엄 콤보 텍스트 색상 30종 — 색상환 순서 배치 */
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

        /** 핑크큐티 전용 마일스톤 라벨 (귀여운 한글/이모지 혼합) */
        private val CUTE_MILESTONE_LABELS = mapOf(
            50 to "좋아요♡",
            100 to "대박~!",
            200 to "미쳤다♡♡",
            300 to "레전드✦",
            400 to "불타올라🔥",
            500 to "전설이다♡",
            600 to "못 막아!",
            700 to "신이시다✦",
            800 to "차원이 다름",
            900 to "초월✦✦",
            1000 to "갓오브갓♡",
        )
    }
}
