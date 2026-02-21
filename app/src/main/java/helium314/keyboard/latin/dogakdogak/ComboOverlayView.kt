package helium314.keyboard.latin.dogakdogak

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.view.View
import androidx.core.content.res.ResourcesCompat
import helium314.keyboard.latin.R
import java.text.NumberFormat
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * Canvas 기반 콤보 이펙트 오버레이 뷰 - 키보드 위에 표시.
 *
 * 성능 최적화:
 *   - onDraw 내 객체 할당 제로 (Shader/Pair/String/Array 모두 캐싱)
 *   - drawText 횟수 최소화 (글로우+채우기 합체, 그림자 패스 제거)
 *   - setShadowLayer 콤보 변경 시만 갱신 (프레임마다 X)
 *   - 파티클 Path 재사용, 다이아몬드는 canvas.rotate+drawRect
 *   - 스코어 팝업 스로틀 (80ms 간격)
 *   - 파티클 풀링
 */
class ComboOverlayView(context: Context) : View(context) {

    // ===================== Fonts =====================

    private val pretendardBold: Typeface = ResourcesCompat.getFont(context, R.font.pretendard_bold)
        ?: Typeface.DEFAULT_BOLD
    private val bangersTypeface: Typeface = ResourcesCompat.getFont(context, R.font.bangers)
        ?: Typeface.DEFAULT_BOLD
    @Suppress("unused")
    private val pacificoTypeface: Typeface = ResourcesCompat.getFont(context, R.font.pacifico)
        ?: Typeface.DEFAULT_BOLD
    private val santokkiTypeface: Typeface = ResourcesCompat.getFont(context, R.font.hs_santokki)
        ?: Typeface.DEFAULT_BOLD
    private val aggroTypeface: Typeface = ResourcesCompat.getFont(context, R.font.sb_aggro_bold)
        ?: Typeface.DEFAULT_BOLD

    // ===================== Reusable Paths =====================

    private val heartPath = Path()
    private val sparklePath = Path()

    // 다이아몬드: Path 대신 canvas.rotate + drawRect 사용 → diamondRect 재사용
    private val diamondRect = RectF()

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

    // fillPaint: 채우기 + 글로우(shadowLayer) 합체
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

    private val particlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    // 앰비언트 글로우용 Paint (RadialGradient 대신 단순 반투명 원)
    private val ambientPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 4f
    }

    // 버블 이펙트용 Paint + RectF
    private val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        isFilterBitmap = true
    }
    private val bitmapDstRect = RectF()

    // ===================== State =====================

    private var count: Long = 0
    private var isAnimating = false
    private var premiumEffects = false
    private var bubbleComboEffects = false
    private var chillEffects = false
    private var sf = 1.0f

    // Premium: HSV 기반 연속 색상 순환
    private var premiumHue = 0f
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

    // Squash & Stretch 결과 재사용 배열 (Pair 할당 방지)
    private val squashResult = FloatArray(2)

    // 캐시: 콤보 텍스트 (매 프레임 String 할당 방지)
    private var cachedComboCount = -1
    private var cachedComboText = "\u00D7"

    // 캐시: 핑크큐티 그래디언트 셰이더
    private var cachedGradientFontSize = -1
    private var cachedGradient: LinearGradient? = null

    // Chill: 가로 흐름 그래디언트
    private var chillGradient: LinearGradient? = null
    private var chillGradientWidth = 0f
    private val chillGradientMatrix = Matrix()
    private var chillGradientOffset = 0f

    // 캐시: 글로우 반경 (setShadowLayer 호출 최소화)
    private var cachedGlowRadius = 0f
    private var cachedGlowColor = 0

    init {
        premiumHue = Random.nextFloat() * 360f
        updatePremiumColorsFromHue()
    }

    // 버블 이펙트 비트맵
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

    fun setChillEffects(enabled: Boolean) {
        chillEffects = enabled
        invalidate()
    }

    fun setCountColor(color: Int) {
        countPaint.color = color
        invalidate()
    }

    fun setScaleFactor(scale: Float) {
        sf = scale
    }

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

    fun updateCombo(combo: Int, score: Int) {
        val now = System.currentTimeMillis()

        // 콤보 리셋 감지
        if (combo == 1 && comboCount > 1) {
            milestoneLabel = null
            milestonePersistent = false
            impactRingActive = false
            ghostTrailCount = 0
            premiumHue = (premiumHue + 60f + Random.nextFloat() * 60f) % 360f
            updatePremiumColorsFromHue()
            premiumTiltDeg = Random.nextFloat() * 20f - 10f
        }

        // HSV 색상 순환: 매 키입력 +12도
        if (premiumEffects) {
            premiumHue = (premiumHue + 12f) % 360f
            updatePremiumColorsFromHue()
        }

        // Chill: 그래디언트 오프셋 증가 (키입력마다 부드럽게 흐름)
        if (chillEffects) {
            chillGradientOffset += 8f
        }

        if (premiumEffects || bubbleComboEffects) {
            premiumTiltDeg = Random.nextFloat() * 20f - 10f
        }

        comboCount = combo
        lastComboTime = now

        // 콤보 텍스트 캐시 갱신
        if (combo != cachedComboCount) {
            cachedComboCount = combo
            cachedComboText = "\u00D7$combo"
        }

        // 글로우 셰도우 레이어 갱신 (프레임마다가 아닌 콤보 변경 시만)
        val level = comboLevel(combo)
        val glowLevel = (level - 1).coerceAtLeast(0)
        val newGlowRadius = if (glowLevel > 0) (8f + glowLevel * 5f) * sf else 0f
        val newGlowColor = when {
            chillEffects -> 0xFF64D2FF.toInt()  // Soft cyan glow
            bubbleComboEffects -> 0xFFFF69B4.toInt()
            premiumEffects -> premiumComboColor
            else -> 0
        }
        if (newGlowRadius != cachedGlowRadius || newGlowColor != cachedGlowColor) {
            cachedGlowRadius = newGlowRadius
            cachedGlowColor = newGlowColor
            if (newGlowRadius > 0f) {
                fillPaint.setShadowLayer(newGlowRadius, 0f, 0f, newGlowColor)
            } else {
                fillPaint.setShadowLayer(0f, 0f, 0f, 0)
            }
        }

        // 스코어 팝업 스폰 (80ms 스로틀)
        if (now - lastPopupTime >= POPUP_THROTTLE_MS) {
            lastPopupTime = now
            spawnScorePopup(score, combo)
        }

        // 마일스톤 체크
        val milestone = checkMilestone(combo)
        if (milestone != null) {
            milestoneLabel = when {
                chillEffects -> CHILL_MILESTONE_LABELS[combo] ?: milestone.label
                bubbleComboEffects -> CUTE_MILESTONE_LABELS[combo] ?: milestone.label
                else -> milestone.label
            }
            milestoneColor = milestone.color
            milestoneStartTime = now
            milestonePersistent = milestone.persistent

            impactRingStartTime = now
            impactRingColor = milestone.color
            impactRingActive = true
            impactRingCx = width / 2f
            impactRingCy = height * 0.55f

            if (premiumEffects || bubbleComboEffects || chillEffects) spawnParticles(10 + milestone.ordinal * 5)
        }

        if ((premiumEffects || bubbleComboEffects || chillEffects) && combo >= 50 && combo % 3 == 0) {
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

    private fun updatePremiumColorsFromHue() {
        val level = comboLevel(comboCount)
        val saturation = (0.7f + level * 0.03f).coerceAtMost(1f)
        val brightness = (0.85f + level * 0.015f).coerceAtMost(1f)

        hsvArray[0] = premiumHue
        hsvArray[1] = saturation
        hsvArray[2] = brightness
        premiumComboColor = Color.HSVToColor(hsvArray)

        hsvArray[0] = (premiumHue + 180f) % 360f
        premiumScoreColor = Color.HSVToColor(hsvArray)
    }

    // ===================== Spring Physics =====================

    /** 감쇠 스프링 바운스 — Float 연산만 사용 (Double boxing 방지) */
    private fun springPunch(elapsedMs: Long, decay: Float, freq: Float, amp: Float): Float {
        val t = elapsedMs / 1000f
        if (t > 0.5f) return 1f
        // Fast exp approximation: (1 - decay*t/n)^n, n=4 for reasonable accuracy
        val x = decay * t * 0.25f
        val expApprox = (1f - x) * (1f - x) * (1f - x) * (1f - x)
        return 1f + amp * expApprox * cos(freq * t)
    }

    /** Squash & stretch 결과를 squashResult[0]=scaleX, [1]=scaleY에 저장 (Pair 할당 방지) */
    private fun squashStretch(elapsedMs: Long) {
        val t = (elapsedMs / PUNCH_DURATION_MS).coerceIn(0f, 1f)
        when {
            t < 0.3f -> {
                val p = t / 0.3f
                squashResult[0] = 1f + 0.15f * p
                squashResult[1] = 1f - 0.15f * p
            }
            t < 0.6f -> {
                val p = (t - 0.3f) / 0.3f
                squashResult[0] = 1.15f - 0.25f * p
                squashResult[1] = 0.85f + 0.35f * p
            }
            else -> {
                val p = (t - 0.6f) / 0.4f
                val tSec = p * 0.2f
                val x = 10f * tSec * 0.25f
                val settle = (1f - x) * (1f - x) * (1f - x) * (1f - x) * cos(20f * tSec)
                squashResult[0] = 1f - 0.1f * settle
                squashResult[1] = 1f + 0.1f * settle
            }
        }
    }

    // ===================== Internal Helpers =====================

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

            if (chillEffects) {
                if (Random.nextFloat() < 0.4f) {
                    type = PARTICLE_SPARKLE
                    color = CHILL_PARTICLE_COLORS[Random.nextInt(CHILL_PARTICLE_COLORS.size)]
                    size = Random.nextFloat() * 8f + 4f
                    rotSpeed = (Random.nextFloat() - 0.5f) * 3f
                } else {
                    type = PARTICLE_CIRCLE
                    color = CHILL_PARTICLE_COLORS[Random.nextInt(CHILL_PARTICLE_COLORS.size)]
                    size = Random.nextFloat() * 6f + 3f
                    rotSpeed = 0f
                }
            } else if (bubbleComboEffects) {
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

        // 1. 총 카운트 (하단 고정)
        val countFont = when {
            chillEffects -> aggroTypeface
            bubbleComboEffects -> santokkiTypeface
            premiumEffects -> bangersTypeface
            else -> pretendardBold
        }
        countPaint.typeface = countFont
        countPaint.textSize = 38f * sf
        canvas.drawText(cachedCountText, cx, height * 0.72f, countPaint)
        countPaint.typeface = pretendardBold

        if (!isAnimating) return

        val idleTime = now - lastComboTime
        val comboAlpha = when {
            idleTime <= COMBO_TIMEOUT_MS -> 1f
            idleTime <= COMBO_TIMEOUT_MS + FADE_DURATION_MS ->
                1f - (idleTime - COMBO_TIMEOUT_MS) / FADE_DURATION_MS.toFloat()
            else -> 0f
        }

        // 2. 배경 앰비언트 글로우 (RadialGradient → 단순 반투명 원 2개)
        if (comboAlpha > 0f && (premiumEffects || bubbleComboEffects || chillEffects)) {
            drawAmbientGlow(canvas, cx, comboAlpha, now)
        }

        // 3. xN 콤보 카운터
        if (comboAlpha > 0f) {
            when {
                chillEffects -> drawChillComboCounter(canvas, cx, comboAlpha, now)
                bubbleComboEffects -> drawBubbleComboCounter(canvas, cx, comboAlpha, now)
                else -> drawComboCounter(canvas, cx, comboAlpha, now)
            }
        }

        // 4. 스코어 팝업
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

        // 6. 임팩트 링
        if (impactRingActive) {
            drawImpactRing(canvas, now)
        }

        // 7. 파티클
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

        if (comboAlpha <= 0f && !hasActivePopups && !hasActiveParticles && !impactRingActive) {
            isAnimating = false
            return
        }
        postInvalidateOnAnimation()
    }

    // ===================== 배경 앰비언트 글로우 =====================
    // RadialGradient 제거 → 2개 반투명 원으로 대체 (Shader 할당 제로)

    private fun drawAmbientGlow(canvas: Canvas, cx: Float, alpha: Float, now: Long) {
        val level = comboLevel(comboCount)
        if (level < 3) return

        val glowAlpha = when {
            level >= 6 -> 0.15f
            else -> 0.08f
        } * alpha

        val pulse = 1f + sin(now * 0.004).toFloat() * 0.1f * (level - 2)
        val radius = (80f + level * 20f) * pulse * sf
        val centerY = height * 0.55f

        val baseColor = when {
            chillEffects -> 0xFF64D2FF.toInt()
            bubbleComboEffects -> 0xFFFF69B4.toInt()
            else -> premiumComboColor
        }
        val r = Color.red(baseColor)
        val g = Color.green(baseColor)
        val b = Color.blue(baseColor)

        // 외부 원 (넓고 희미)
        ambientPaint.color = Color.argb((glowAlpha * 100).toInt(), r, g, b)
        canvas.drawCircle(cx, centerY, radius, ambientPaint)
        // 내부 원 (좁고 밝음)
        ambientPaint.color = Color.argb((glowAlpha * 180).toInt(), r, g, b)
        canvas.drawCircle(cx, centerY, radius * 0.5f, ambientPaint)
    }

    // ===================== Premium 콤보 카운터 =====================
    // 최적화: 글로우 패스 제거 (fillPaint.shadowLayer로 통합), 그림자 패스 제거

    private fun drawComboCounter(canvas: Canvas, cx: Float, alpha: Float, now: Long) {
        val combo = comboCount
        val level = comboLevel(combo)
        val punchElapsed = now - lastComboTime

        val totalScale: Float
        val shakeX: Float
        val shakeY: Float
        val color: Int

        if (premiumEffects) {
            val punchScale = springPunch(
                punchElapsed, PREMIUM_SPRING_DECAY, PREMIUM_SPRING_FREQ,
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
            shakeX = 0f; shakeY = 0f
            color = comboColor(combo)
        }

        val text = cachedComboText
        val baseFontSize = (40f + level * 5f) * sf
        val fontSize = baseFontSize * totalScale
        val drawX = cx + shakeX
        val drawY = height * 0.55f + shakeY

        if (premiumEffects) {
            outlinePaint.typeface = bangersTypeface
            fillPaint.typeface = bangersTypeface

            canvas.save()
            canvas.rotate(premiumTiltDeg, drawX, drawY)

            // 잔상 (Ghost trail) — fillPaint의 shadowLayer 일시 해제하고 그림
            if (level >= 1 && ghostTrailCount > 0) {
                fillPaint.setShadowLayer(0f, 0f, 0f, 0)
                val startIdx = maxOf(0, GHOST_TRAIL_SIZE - ghostTrailCount)
                for (i in startIdx until GHOST_TRAIL_SIZE) {
                    val bufIdx = (ghostTrailIndex - GHOST_TRAIL_SIZE + i + GHOST_TRAIL_SIZE * 2) % GHOST_TRAIL_SIZE
                    val trailAlpha = GHOST_TRAIL_ALPHAS[i] * alpha * (level / 10f).coerceIn(0.3f, 1f)
                    if (trailAlpha < 0.01f) continue
                    fillPaint.textSize = fontSize
                    fillPaint.color = color
                    fillPaint.alpha = (trailAlpha * 255).toInt()
                    canvas.drawText(text, ghostTrailX[bufIdx], ghostTrailY[bufIdx], fillPaint)
                }
                // shadowLayer 복원
                if (cachedGlowRadius > 0f) {
                    fillPaint.setShadowLayer(cachedGlowRadius, 0f, 0f, cachedGlowColor)
                }
            }

            // 잔상 링버퍼 업데이트
            ghostTrailX[ghostTrailIndex] = drawX
            ghostTrailY[ghostTrailIndex] = drawY
            ghostTrailIndex = (ghostTrailIndex + 1) % GHOST_TRAIL_SIZE
            if (ghostTrailCount < GHOST_TRAIL_SIZE) ghostTrailCount++

            // 흰 테두리 (drawText 1회)
            outlinePaint.textSize = fontSize
            outlinePaint.color = Color.WHITE
            outlinePaint.strokeWidth = fontSize * 0.14f
            outlinePaint.alpha = (alpha * 255).toInt()
            canvas.drawText(text, drawX, drawY, outlinePaint)

            // 컬러 채우기 + 글로우 (shadowLayer 이미 설정됨, drawText 1회)
            fillPaint.textSize = fontSize
            fillPaint.color = color
            fillPaint.alpha = (alpha * 255).toInt()
            canvas.drawText(text, drawX, drawY, fillPaint)

            canvas.restore()

            outlinePaint.typeface = pretendardBold
            fillPaint.typeface = pretendardBold
            outlinePaint.color = 0xDD000000.toInt()
        } else {
            // Normal 모드
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

    // ===================== Pink Cutie 콤보 카운터 =====================
    // 최적화: 글로우/그림자 패스 제거, LinearGradient 캐싱

    private fun drawBubbleComboCounter(canvas: Canvas, cx: Float, alpha: Float, now: Long) {
        val combo = comboCount
        val level = comboLevel(combo)
        val punchElapsed = now - lastComboTime

        val punchScale = springPunch(
            punchElapsed, CUTE_SPRING_DECAY, CUTE_SPRING_FREQ,
            CUTE_SPRING_AMP + level * 0.02f
        )

        squashStretch(punchElapsed)
        val squashX = squashResult[0]
        val squashY = squashResult[1]

        val pulse = 1f + sin(now * 0.008).toFloat() * 0.04f * level
        val growth = 1f + (combo * 0.0006f).coerceAtMost(0.5f)
        val baseScale = punchScale * pulse * growth

        val baseShake = level * 3.0f
        val comboShake = (combo * 0.015f).coerceAtMost(20f)
        val shakeAmp = baseShake + comboShake
        val t = now.toFloat()
        val shakeX = ((sin(t * 0.15) + cos(t * 0.23) * 0.8 + sin(t * 0.37) * 0.5) * shakeAmp).toFloat()
        val shakeY = ((cos(t * 0.17) + sin(t * 0.29) * 0.7 + cos(t * 0.41) * 0.4) * shakeAmp).toFloat()

        val text = cachedComboText
        val baseFontSize = (40f + level * 5f) * sf
        val fontSize = baseFontSize * baseScale
        val drawX = cx + shakeX
        val drawY = height * 0.55f + shakeY

        outlinePaint.typeface = santokkiTypeface
        fillPaint.typeface = santokkiTypeface

        canvas.save()
        canvas.rotate(premiumTiltDeg, drawX, drawY)
        canvas.scale(squashX, squashY, drawX, drawY)

        // 흰 테두리 (drawText 1회)
        outlinePaint.textSize = fontSize
        outlinePaint.color = Color.WHITE
        outlinePaint.strokeWidth = fontSize * 0.16f
        outlinePaint.alpha = (alpha * 255).toInt()
        canvas.drawText(text, drawX, drawY, outlinePaint)

        // 핑크 그래디언트 + 글로우 (drawText 1회)
        // LinearGradient 캐싱: fontSize가 1px 이상 변하면 재생성
        val fontSizeInt = fontSize.toInt()
        if (fontSizeInt != cachedGradientFontSize) {
            cachedGradientFontSize = fontSizeInt
            cachedGradient = LinearGradient(
                0f, -fontSize * 0.8f,
                0f, fontSize * 0.2f,
                0xFFDA1884.toInt(), 0xFFFF69B4.toInt(),  // Barbie Pink → Hot Pink
                Shader.TileMode.CLAMP
            )
        }
        fillPaint.textSize = fontSize
        fillPaint.shader = cachedGradient
        fillPaint.alpha = (alpha * 255).toInt()
        canvas.drawText(text, drawX, drawY, fillPaint)
        fillPaint.shader = null

        canvas.restore()

        outlinePaint.typeface = pretendardBold
        fillPaint.typeface = pretendardBold
        outlinePaint.color = 0xDD000000.toInt()
    }

    // ===================== Chill 콤보 카운터 =====================
    // 정적이고 차분한 느낌, 가로 흐르는 그래디언트

    private fun drawChillComboCounter(canvas: Canvas, cx: Float, alpha: Float, now: Long) {
        val combo = comboCount
        val level = comboLevel(combo)
        val punchElapsed = now - lastComboTime

        // 차분한 스프링 (약한 바운스)
        val punchScale = springPunch(
            punchElapsed, CHILL_SPRING_DECAY, CHILL_SPRING_FREQ,
            CHILL_SPRING_AMP + level * 0.01f
        )

        // 부드러운 숨쉬기 펄스
        val pulse = 1f + sin(now * 0.003).toFloat() * 0.02f * level
        val growth = 1f + (combo * 0.0004f).coerceAtMost(0.35f)
        val totalScale = punchScale * pulse * growth

        val text = cachedComboText
        val baseFontSize = (40f + level * 5f) * sf
        val fontSize = baseFontSize * totalScale
        val drawX = cx
        val drawY = height * 0.55f

        outlinePaint.typeface = aggroTypeface
        fillPaint.typeface = aggroTypeface

        canvas.save()

        // 둥근 흰 테두리
        outlinePaint.textSize = fontSize
        outlinePaint.color = Color.WHITE
        outlinePaint.strokeWidth = fontSize * 0.12f
        outlinePaint.alpha = (alpha * 255).toInt()
        canvas.drawText(text, drawX, drawY, outlinePaint)

        // 가로 흐르는 그래디언트
        val textWidth = fillPaint.apply { textSize = fontSize }.measureText(text)
        val gradientWidth = textWidth * 2f
        if (gradientWidth != chillGradientWidth || chillGradient == null) {
            chillGradientWidth = gradientWidth
            chillGradient = LinearGradient(
                0f, 0f, gradientWidth, 0f,
                CHILL_GRADIENT_COLORS, CHILL_GRADIENT_POSITIONS,
                Shader.TileMode.REPEAT
            )
        }

        // 시간 기반 부드러운 흐름 + 키입력 오프셋
        val flowOffset = chillGradientOffset + (now % 100000) * 0.05f
        chillGradientMatrix.reset()
        chillGradientMatrix.setTranslate(flowOffset % gradientWidth, 0f)
        chillGradient?.setLocalMatrix(chillGradientMatrix)

        fillPaint.textSize = fontSize
        fillPaint.shader = chillGradient
        fillPaint.alpha = (alpha * 255).toInt()
        canvas.drawText(text, drawX, drawY, fillPaint)
        fillPaint.shader = null

        canvas.restore()

        outlinePaint.typeface = pretendardBold
        fillPaint.typeface = pretendardBold
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
        val text = popup.cachedText
        val baseFontSize = (28f + level * 3f) * sf
        val fontSize = baseFontSize * scale
        val drawX = popup.x
        val drawY = popup.y + yOffset

        val color = when {
            chillEffects -> CHILL_GRADIENT_COLORS[((System.currentTimeMillis() / 200) % CHILL_GRADIENT_COLORS.size).toInt()]
            premiumEffects -> premiumScoreColor
            else -> scorePopupColor(popup.combo)
        }
        val useSpecialFont = premiumEffects || bubbleComboEffects || chillEffects
        val specialFont = when {
            chillEffects -> aggroTypeface
            bubbleComboEffects -> santokkiTypeface
            else -> bangersTypeface
        }

        if (useSpecialFont) {
            outlinePaint.typeface = specialFont
            fillPaint.typeface = specialFont
            canvas.save()
            canvas.rotate(premiumTiltDeg, drawX, drawY)
        }

        outlinePaint.textSize = fontSize
        outlinePaint.strokeWidth = if (useSpecialFont) fontSize * 0.12f else 6f + level * 1f
        outlinePaint.color = if (useSpecialFont) Color.WHITE else 0xDD000000.toInt()
        outlinePaint.alpha = (alpha * (if (useSpecialFont) 255 else 200)).toInt()
        canvas.drawText(text, drawX, drawY, outlinePaint)

        // fillPaint에 shadowLayer가 걸려있으므로 팝업에선 해제
        fillPaint.setShadowLayer(0f, 0f, 0f, 0)
        fillPaint.textSize = fontSize
        fillPaint.color = color
        fillPaint.alpha = (alpha * 255).toInt()
        canvas.drawText(text, drawX, drawY, fillPaint)
        // shadowLayer 복원
        if (cachedGlowRadius > 0f) {
            fillPaint.setShadowLayer(cachedGlowRadius, 0f, 0f, cachedGlowColor)
        }

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

        val useSpecialFont = premiumEffects || bubbleComboEffects || chillEffects
        val specialFont = when {
            chillEffects -> aggroTypeface
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

        outlinePaint.textSize = fontSize
        outlinePaint.strokeWidth = if (useSpecialFont) fontSize * 0.14f else 11f
        outlinePaint.color = if (useSpecialFont) Color.WHITE else 0xDD000000.toInt()
        outlinePaint.alpha = (alpha * (if (useSpecialFont) 255 else 230)).toInt()
        canvas.drawText(label, drawX, drawY, outlinePaint)

        // 마일스톤도 shadowLayer 해제 후 그리기
        fillPaint.setShadowLayer(0f, 0f, 0f, 0)
        fillPaint.textSize = fontSize
        fillPaint.color = milestoneColor
        fillPaint.alpha = (alpha * 255).toInt()
        canvas.drawText(label, drawX, drawY, fillPaint)
        if (cachedGlowRadius > 0f) {
            fillPaint.setShadowLayer(cachedGlowRadius, 0f, 0f, cachedGlowColor)
        }

        if (useSpecialFont) {
            canvas.restore()
            outlinePaint.typeface = pretendardBold
            fillPaint.typeface = pretendardBold
            outlinePaint.color = 0xDD000000.toInt()
        }
    }

    // ===================== 임팩트 링 =====================

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

        if (t < 0.7f) {
            val radius2 = (10f + t * 60f) * sf
            ringPaint.alpha = (alpha * 0.4f).toInt()
            ringPaint.strokeWidth = strokeWidth * 0.6f
            canvas.drawCircle(impactRingCx, impactRingCy, radius2, ringPaint)
        }
    }

    // ===================== 파티클 도형 =====================

    private fun drawHeart(canvas: Canvas, cx: Float, cy: Float, size: Float, paint: Paint) {
        heartPath.reset()
        heartPath.moveTo(cx, cy + size * 0.3f)
        heartPath.cubicTo(cx - size, cy - size * 0.3f, cx - size * 0.5f, cy - size, cx, cy - size * 0.5f)
        heartPath.cubicTo(cx + size * 0.5f, cy - size, cx + size, cy - size * 0.3f, cx, cy + size * 0.3f)
        heartPath.close()
        canvas.drawPath(heartPath, paint)
    }

    private fun drawSparkle(canvas: Canvas, cx: Float, cy: Float, size: Float, rotation: Float, paint: Paint) {
        sparklePath.reset()
        val inner = size * 0.3f
        for (i in 0 until 8) {
            val angle = i * PI_OVER_4 + rotation
            val r = if (i % 2 == 0) size else inner
            val px = cx + r * cos(angle)
            val py = cy + r * sin(angle)
            if (i == 0) sparklePath.moveTo(px, py) else sparklePath.lineTo(px, py)
        }
        sparklePath.close()
        canvas.drawPath(sparklePath, paint)
    }

    /** 다이아몬드: Path 대신 canvas.rotate + drawRect 사용 (Path 빌드 비용 제거) */
    private fun drawDiamond(canvas: Canvas, cx: Float, cy: Float, size: Float, rotation: Float, paint: Paint) {
        canvas.save()
        canvas.rotate(rotation * 57.2958f, cx, cy) // radians to degrees
        diamondRect.set(cx - size * 0.7f, cy - size, cx + size * 0.7f, cy + size)
        canvas.rotate(45f, cx, cy)
        canvas.drawRect(diamondRect, paint)
        canvas.restore()
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
        var cachedText = ""  // String 할당 캐시

        fun reset(score: Int, combo: Int, x: Float, y: Float) {
            this.score = score; this.combo = combo
            this.x = x; this.y = y
            this.startTime = System.currentTimeMillis()
            this.alive = true
            this.cachedText = "+$score"
        }
    }

    private class Particle {
        var x = 0f; var y = 0f
        var vx = 0f; var vy = 0f
        var color = Color.WHITE
        var size = 4f; var life = 1f; var alive = false
        var rotation = 0f
        var rotSpeed = 0f
        var type = 0

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
        private val GHOST_TRAIL_ALPHAS = floatArrayOf(0.07f, 0.15f, 0.3f)

        private const val PI_OVER_4 = PI.toFloat() / 4f

        private const val PREMIUM_SPRING_DECAY = 12f
        private const val PREMIUM_SPRING_FREQ = 25f
        private const val PREMIUM_SPRING_AMP = 0.5f
        private const val CUTE_SPRING_DECAY = 8f
        private const val CUTE_SPRING_FREQ = 18f
        private const val CUTE_SPRING_AMP = 0.6f
        private const val CHILL_SPRING_DECAY = 18f   // 빠르게 안정
        private const val CHILL_SPRING_FREQ = 14f     // 느린 진동
        private const val CHILL_SPRING_AMP = 0.2f     // 약한 바운스

        private const val PARTICLE_CIRCLE = 0
        private const val PARTICLE_HEART = 1
        private const val PARTICLE_SPARKLE = 2
        private const val PARTICLE_DIAMOND = 3

        private val PARTICLE_COLORS = intArrayOf(
            0xFFFF453A.toInt(), 0xFFFF9F0A.toInt(), 0xFFFFD60A.toInt(),
            0xFF30D158.toInt(), 0xFF0A84FF.toInt(), 0xFFBF5AF2.toInt(),
            0xFFFF375F.toInt()
        )

        private val PINK_PARTICLE_COLORS = intArrayOf(
            0xFFFF69B4.toInt(), 0xFFFF1493.toInt(), 0xFFFFB6C1.toInt(),
            0xFFF06292.toInt(), 0xFFEC407A.toInt(), 0xFFE91E63.toInt(),
            0xFFFF80AB.toInt(), 0xFFFF4081.toInt(), 0xFFFFC1E3.toInt(),
            0xFFFF8A80.toInt(),
        )

        private val SPARKLE_COLORS = intArrayOf(
            0xFFFFFFFF.toInt(), 0xFFFFB6C1.toInt(), 0xFFE6E6FA.toInt(),
        )

        private val PREMIUM_COLORS = intArrayOf(
            0xFFFF3B30.toInt(), 0xFFFF6B6B.toInt(), 0xFFFF6E40.toInt(),
            0xFFFF9500.toInt(), 0xFFFF9F0A.toInt(),
            0xFFFFCC00.toInt(), 0xFFFFD60A.toInt(), 0xFFCDDC39.toInt(),
            0xFFA8D948.toInt(), 0xFF00C853.toInt(),
            0xFF30D158.toInt(), 0xFF34C759.toInt(), 0xFF66BB6A.toInt(),
            0xFF64FFDA.toInt(), 0xFF00BCD4.toInt(),
            0xFF00E5FF.toInt(), 0xFF42A5F5.toInt(), 0xFF0A84FF.toInt(),
            0xFF007AFF.toInt(), 0xFF5856D6.toInt(),
            0xFF7C4DFF.toInt(), 0xFFBF5AF2.toInt(), 0xFFEA80FC.toInt(),
            0xFFE040FB.toInt(), 0xFFF06292.toInt(),
            0xFFEC407A.toInt(), 0xFFFF375F.toInt(), 0xFFFF2D55.toInt(),
            0xFFFF8A65.toInt(), 0xFFFFAB40.toInt(),
        )

        // Chill: 이미지 기반 그래디언트 (핑크 → 옐로우 → 시안 → 블루 → 퍼플 → 핑크)
        private val CHILL_GRADIENT_COLORS = intArrayOf(
            0xFFFF6B9D.toInt(),  // Pink
            0xFFFFB347.toInt(),  // Orange-Yellow
            0xFFFFF176.toInt(),  // Yellow
            0xFF69F0AE.toInt(),  // Mint Green
            0xFF64D2FF.toInt(),  // Cyan
            0xFF7C4DFF.toInt(),  // Purple
            0xFFFF6B9D.toInt(),  // Pink (repeat for seamless loop)
        )
        private val CHILL_GRADIENT_POSITIONS = floatArrayOf(
            0f, 0.17f, 0.33f, 0.50f, 0.67f, 0.83f, 1f
        )

        private val CHILL_PARTICLE_COLORS = intArrayOf(
            0xFF64D2FF.toInt(),  // Cyan
            0xFF7C4DFF.toInt(),  // Purple
            0xFFFF6B9D.toInt(),  // Pink
            0xFF69F0AE.toInt(),  // Mint
            0xFFFFB347.toInt(),  // Orange
            0xFFFFF176.toInt(),  // Yellow
        )

        private val CHILL_MILESTONE_LABELS = mapOf(
            50 to "vibe~",
            100 to "so chill",
            200 to "flow~",
            300 to "groovy",
            400 to "smooth~",
            500 to "zen mode",
            600 to "floating~",
            700 to "dreamy",
            800 to "euphoria~",
            900 to "nirvana",
            1000 to "transcend~",
        )

        private val CUTE_MILESTONE_LABELS = mapOf(
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
