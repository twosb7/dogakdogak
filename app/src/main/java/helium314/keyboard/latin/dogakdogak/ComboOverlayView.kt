package helium314.keyboard.latin.dogakdogak

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.view.View
import androidx.core.content.res.ResourcesCompat
import helium314.keyboard.latin.R
import java.text.NumberFormat
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

enum class EffectMode { NORMAL, PREMIUM, CUTIE_PINK, ARCADE }

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

    // 앰비언트 글로우용 Paint (RadialGradient 대신 단순 반투명 원)
    private val ambientPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 4f
    }

    // 큐티핑크 이펙트용 Paint + RectF
    private val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        isFilterBitmap = true
    }
    private val bitmapDstRect = RectF()

    // ===================== State =====================

    private var count: Long = 0
    private var isAnimating = false
    private var premiumEffects = false
    private var cutiePinkComboEffects = false
    private var arcadeEffects = false
    private var sf = 1.0f

    // Premium: HSV 기반 연속 색상 순환
    private var premiumHue = 0f
    private var premiumComboColor = OverlayColors.PREMIUM_COLORS[0]
    private var premiumScoreColor = OverlayColors.PREMIUM_COLORS[1]
    private var premiumTiltDeg = 0f

    // 잔상(Ghost trail) 링버퍼
    private val ghostTrailX = FloatArray(AnimationConstants.GHOST_TRAIL_SIZE)
    private val ghostTrailY = FloatArray(AnimationConstants.GHOST_TRAIL_SIZE)
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

    // Arcade 3D 레트로: 무지개 리버 그래디언트 캐시
    private var cachedArcadeTextGradientSize = -1
    private var cachedArcadeTextGradient: LinearGradient? = null
    private val arcadeTextGradientMatrix = Matrix()

    // Arcade 3D 레트로: 보라 압출 그래디언트 캐시
    private var cachedArcadeShineGradientSize = -1
    private var cachedArcadeShineGradient: LinearGradient? = null
    private val arcadeShineMatrix = Matrix()

    // 캐시: 글로우 반경 (setShadowLayer 호출 최소화)
    private var cachedGlowRadius = 0f
    private var cachedGlowColor = 0

    init {
        premiumHue = Random.nextFloat() * 360f
        updatePremiumColorsFromHue()
    }

    // 큐티핑크 이펙트 비트맵
    private var cutiePinkBitmaps: Array<Bitmap?>? = null

    // 카운트 포맷 캐시
    private var cachedCount = -1L
    private var cachedCountText = "0"
    private val numberFormat = NumberFormat.getNumberInstance()

    // -- 콤보 카운터 --
    private var comboCount = 0
    private var lastComboTime = 0L

    // -- 스코어 팝업 풀 + 스로틀 --
    private val scorePopups = Array(AnimationConstants.MAX_POPUPS) { ScorePopup() }
    private var lastPopupTime = 0L

    // -- Arcade wander (콤보 카운터 좌우 이동) --
    private var arcadeWanderStep = 0      // -3..+3
    private var arcadeLastWanderDir = 0   // -1 or +1

    // -- Arcade 마일스톤 코인 애니메이션 --
    private var arcadeCoinActive = false
    private var arcadeCoinStartTime = 0L
    @Suppress("DEPRECATION")
    private val arcadeCoinMovie: android.graphics.Movie? = try {
        android.graphics.Movie.decodeStream(context.resources.openRawResource(R.raw.spinning_coin))
    } catch (_: Exception) { null }
    private val arcadeCoinMovieDuration: Int = arcadeCoinMovie?.duration() ?: 1500
    private var coinOffscreenBitmap: Bitmap? = null
    private var coinOffscreenCanvas: Canvas? = null

    // -- 마일스톤 --
    private var milestoneLabel: String? = null
    private var milestoneColor = Color.WHITE
    private var milestoneStartTime = 0L
    private var milestonePersistent = false

    // ===================== Public API =====================

    /** 마일스톤 달성 시 KonfettiView에서 파티클 폭발을 트리거하기 위한 콜백 */
    var onMilestoneTriggered: ((ComboMilestone, EffectMode) -> Unit)? = null

    /** 콤보 증가 시 소량 파티클 스폰을 위한 콜백 */
    var onComboParticleSpawn: ((count: Int, EffectMode) -> Unit)? = null

    private val currentEffectMode: EffectMode
        get() = when {
            arcadeEffects -> EffectMode.ARCADE
            cutiePinkComboEffects -> EffectMode.CUTIE_PINK
            premiumEffects -> EffectMode.PREMIUM
            else -> EffectMode.NORMAL
        }

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

    fun setCutiePinkComboEffects(enabled: Boolean) {
        cutiePinkComboEffects = enabled
        if (enabled) {
            if (cutiePinkBitmaps == null) loadCutiePinkBitmaps()
        } else {
            cutiePinkBitmaps?.forEach { it?.recycle() }
            cutiePinkBitmaps = null
        }
        invalidate()
    }

    fun setArcadeEffects(enabled: Boolean) {
        arcadeEffects = enabled
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

    fun updateCombo(combo: Int, score: Int, luckyStrike: Boolean = false) {
        val now = System.currentTimeMillis()

        // 콤보 리셋 감지
        if (combo == 1 && comboCount > 1) {
            milestoneLabel = null
            milestonePersistent = false
            impactRingActive = false
            arcadeCoinActive = false
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

        // (Arcade 3D 그래디언트는 fontSize 변경 시 자동 갱신)

        // Arcade wander: 매 콤보마다 좌우 1칸 이동 (-3..+3)
        if (arcadeEffects) {
            if (combo == 1 && comboCount > 1) {
                arcadeWanderStep = 0
                arcadeLastWanderDir = 0
            } else {
                val dir = if (arcadeWanderStep <= -3) 1
                    else if (arcadeWanderStep >= 3) -1
                    else if (Random.nextBoolean()) 1 else -1
                arcadeWanderStep += dir
                arcadeLastWanderDir = dir
            }
        }

        if (premiumEffects || cutiePinkComboEffects) {
            premiumTiltDeg = Random.nextFloat() * 20f - 10f
        } else if (arcadeEffects) {
            premiumTiltDeg = Random.nextFloat() * 6f - 3f  // 아주 미세한 기울기
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
        val newGlowRadius = if (glowLevel > 0) (4f + glowLevel * 2.5f) * sf else 0f
        val newGlowColor = when {
            arcadeEffects -> 0x50B500FF.toInt()  // 아케이드 퍼플 글로우
            cutiePinkComboEffects -> 0x60FF69B4.toInt()
            premiumEffects -> (premiumComboColor and 0x00FFFFFF) or 0x60000000
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
        if (now - lastPopupTime >= AnimationConstants.POPUP_THROTTLE_MS) {
            lastPopupTime = now
            spawnScorePopup(score, combo, luckyStrike)
        }

        // 마일스톤 체크
        val milestone = checkMilestone(combo)
        if (milestone != null) {
            milestoneLabel = when {
                arcadeEffects -> ComboMilestone.ARCADE_MILESTONE_LABELS[combo] ?: milestone.label
                cutiePinkComboEffects -> ComboMilestone.CUTE_MILESTONE_LABELS[combo] ?: milestone.label
                else -> milestone.label
            }
            milestoneColor = if (arcadeEffects) OverlayColors.ARCADE_PARTICLE_COLORS[Random.nextInt(OverlayColors.ARCADE_PARTICLE_COLORS.size)] else milestone.color
            milestoneStartTime = now
            milestonePersistent = milestone.persistent

            if (arcadeEffects) {
                // Arcade: 임팩트링 대신 회전 금화 애니메이션
                arcadeCoinActive = true
                arcadeCoinStartTime = now
            } else {
                impactRingStartTime = now
                impactRingColor = milestone.color
                impactRingActive = true
                impactRingCx = width / 2f
                impactRingCy = height * 0.55f
            }

            if (premiumEffects || cutiePinkComboEffects || arcadeEffects) {
                onMilestoneTriggered?.invoke(milestone, currentEffectMode)
            }
        }

        if ((premiumEffects || cutiePinkComboEffects) && combo >= 9 && combo % 3 == 0) {
            val pCount = when {
                combo >= 300 -> 4
                combo >= 100 -> 3
                else -> 2
            }
            onComboParticleSpawn?.invoke(pCount, currentEffectMode)
        }

        // Arcade: 3콤보마다 코인 파티클
        if (arcadeEffects && combo >= 6 && combo % 3 == 0) {
            onComboParticleSpawn?.invoke(2, EffectMode.ARCADE)
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
        val t = (elapsedMs / AnimationConstants.PUNCH_DURATION_MS).coerceIn(0f, 1f)
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

    private fun loadCutiePinkBitmaps() {
        val ids = intArrayOf(
            R.drawable.combo_char_x,
            R.drawable.combo_char_0, R.drawable.combo_char_1,
            R.drawable.combo_char_2, R.drawable.combo_char_3,
            R.drawable.combo_char_4, R.drawable.combo_char_5,
            R.drawable.combo_char_6, R.drawable.combo_char_7,
            R.drawable.combo_char_8, R.drawable.combo_char_9
        )
        cutiePinkBitmaps = Array(ids.size) { i ->
            try { BitmapFactory.decodeResource(resources, ids[i]) } catch (_: Exception) { null }
        }
    }

    private fun checkMilestone(combo: Int): ComboMilestone? = ComboMilestone.justReached(combo)

    private fun spawnScorePopup(score: Int, combo: Int, luckyStrike: Boolean = false) {
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
        // Arcade: 스폰 시점에 랜덤 금/은 색상 1개 확정
        val popupColor = if (arcadeEffects) {
            OverlayColors.ARCADE_PARTICLE_COLORS[Random.nextInt(OverlayColors.ARCADE_PARTICLE_COLORS.size)]
        } else 0
        popup.reset(score, combo, cx + xOffset, baseY, popupColor, luckyStrike)
    }

    // ===================== onDraw =====================

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f
        val now = System.currentTimeMillis()

        // 1. 총 카운트 (하단 고정)
        val countFont = when {
            arcadeEffects -> aggroTypeface
            cutiePinkComboEffects -> santokkiTypeface
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
            idleTime <= AnimationConstants.COMBO_TIMEOUT_MS -> 1f
            idleTime <= AnimationConstants.COMBO_TIMEOUT_MS + AnimationConstants.FADE_DURATION_MS ->
                1f - (idleTime - AnimationConstants.COMBO_TIMEOUT_MS) / AnimationConstants.FADE_DURATION_MS.toFloat()
            else -> 0f
        }

        // 2. 배경 앰비언트 글로우 (RadialGradient → 단순 반투명 원 2개)
        if (comboAlpha > 0f && (premiumEffects || cutiePinkComboEffects || arcadeEffects)) {
            drawAmbientGlow(canvas, cx, comboAlpha, now)
        }

        // 3. xN 콤보 카운터
        if (comboAlpha > 0f) {
            when {
                arcadeEffects -> drawArcadeComboCounter(canvas, cx, comboAlpha, now)
                cutiePinkComboEffects -> drawCutiePinkComboCounter(canvas, cx, comboAlpha, now)
                else -> drawComboCounter(canvas, cx, comboAlpha, now)
            }
        }

        // 4. 스코어 팝업
        var hasActivePopups = false
        for (popup in scorePopups) {
            if (!popup.alive) continue
            val elapsed = now - popup.startTime
            val t = (elapsed / AnimationConstants.POPUP_DURATION_MS).coerceIn(0f, 1f)
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

        // 7. Arcade 마일스톤 코인
        if (arcadeCoinActive) {
            drawArcadeMilestoneCoin(canvas, cx, now)
        }

        if (comboAlpha <= 0f && !hasActivePopups && !impactRingActive && !arcadeCoinActive) {
            isAnimating = false
            return
        }
        postInvalidateOnAnimation()
    }

    // ===================== 배경 앰비언트 글로우 =====================
    // RadialGradient 제거 → 2개 반투명 원으로 대체 (Shader 할당 제로)

    private fun drawAmbientGlow(canvas: Canvas, cx: Float, alpha: Float, now: Long) {
        val level = comboLevel(comboCount)
        val centerY = height * 0.55f

        if (arcadeEffects) {
            // Arcade: 퍼플 글로우 + 아주 느린 breathing
            if (level < 1) return
            val glowAlpha = (0.04f + level * 0.01f).coerceAtMost(0.12f) * alpha
            val breathe = 1f + 0.06f * sin(now.toFloat() / 1500f)
            val radius = 60f * sf * breathe
            ambientPaint.color = Color.argb(
                (glowAlpha * 120 * breathe).toInt().coerceAtMost(255), 232, 184, 120
            )
            canvas.drawCircle(cx, centerY, radius, ambientPaint)
            return
        }

        if (level < 3) return

        val glowAlpha = when {
            level >= 6 -> 0.15f
            else -> 0.08f
        } * alpha

        val pulse = 1f + sin(now * 0.004).toFloat() * 0.1f
        val radius = 80f * pulse * sf

        val baseColor = if (cutiePinkComboEffects) 0xFFFF69B4.toInt() else premiumComboColor
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
                punchElapsed, AnimationConstants.PREMIUM_SPRING_DECAY, AnimationConstants.PREMIUM_SPRING_FREQ,
                AnimationConstants.PREMIUM_SPRING_AMP + level * 0.02f
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
            color = OverlayColors.comboColor(combo)
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
                val startIdx = maxOf(0, AnimationConstants.GHOST_TRAIL_SIZE - ghostTrailCount)
                for (i in startIdx until AnimationConstants.GHOST_TRAIL_SIZE) {
                    val bufIdx = (ghostTrailIndex - AnimationConstants.GHOST_TRAIL_SIZE + i + AnimationConstants.GHOST_TRAIL_SIZE * 2) % AnimationConstants.GHOST_TRAIL_SIZE
                    val trailAlpha = AnimationConstants.GHOST_TRAIL_ALPHAS[i] * alpha * (level / 10f).coerceIn(0.3f, 1f)
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
            ghostTrailIndex = (ghostTrailIndex + 1) % AnimationConstants.GHOST_TRAIL_SIZE
            if (ghostTrailCount < AnimationConstants.GHOST_TRAIL_SIZE) ghostTrailCount++

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

    // ===================== 큐티핑크 콤보 카운터 =====================
    // 최적화: 글로우/그림자 패스 제거, LinearGradient 캐싱

    private fun drawCutiePinkComboCounter(canvas: Canvas, cx: Float, alpha: Float, now: Long) {
        val combo = comboCount
        val level = comboLevel(combo)
        val punchElapsed = now - lastComboTime

        val punchScale = springPunch(
            punchElapsed, AnimationConstants.CUTE_SPRING_DECAY, AnimationConstants.CUTE_SPRING_FREQ,
            AnimationConstants.CUTE_SPRING_AMP + level * 0.02f
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

    // ===================== Arcade 콤보 카운터 =====================
    // 3D 레트로 스타일: 무지개 리버 그래디언트 + 흰 외곽선 + 보라 3D 압출 + 드롭 섀도우

    private fun drawArcadeComboCounter(canvas: Canvas, cx: Float, alpha: Float, now: Long) {
        val combo = comboCount
        val level = comboLevel(combo)
        val punchElapsed = now - lastComboTime

        val punchScale = springPunch(
            punchElapsed, AnimationConstants.ARCADE_SPRING_DECAY, AnimationConstants.ARCADE_SPRING_FREQ,
            AnimationConstants.ARCADE_SPRING_AMP
        )
        val growth = 1f + (combo * 0.0006f).coerceAtMost(0.5f)
        val totalScale = punchScale * growth

        val text = cachedComboText
        val baseFontSize = (30f + level * 5f) * sf
        val fontSize = baseFontSize * totalScale
        val stepSize = fontSize * 0.18f
        val drawX = cx + arcadeWanderStep * stepSize
        val drawY = height * 0.55f

        outlinePaint.typeface = aggroTypeface
        fillPaint.typeface = aggroTypeface

        canvas.save()

        val fontSizeInt = fontSize.toInt()
        val extrusionDepth = 8
        val layerStep = fontSize * 0.02f

        // -- 1. 드롭 섀도우 (45° 오프셋, 반투명 블러) --
        fillPaint.shader = null
        fillPaint.textSize = fontSize
        fillPaint.style = Paint.Style.FILL_AND_STROKE
        fillPaint.strokeWidth = fontSize * 0.06f
        fillPaint.strokeJoin = Paint.Join.ROUND
        fillPaint.color = 0xFF000000.toInt()
        fillPaint.alpha = (alpha * 80).toInt()
        val shadowOff = (extrusionDepth + 2) * layerStep
        fillPaint.setShadowLayer(fontSize * 0.05f, 0f, 0f, (0x60000000).toInt())
        canvas.drawText(text, drawX + shadowOff, drawY + shadowOff, fillPaint)
        fillPaint.setShadowLayer(0f, 0f, 0f, 0)

        // -- 2. 3D 압출 블록 (딥 인디고 → 미디엄 퍼플 그래디언트) --
        if (fontSizeInt != cachedArcadeShineGradientSize) {
            cachedArcadeShineGradientSize = fontSizeInt
            cachedArcadeShineGradient = LinearGradient(
                0f, 0f, fontSize * 3f, fontSize * 3f,
                intArrayOf(0xFF150B59.toInt(), 0xFF5534B8.toInt()),
                null,
                Shader.TileMode.CLAMP
            )
        }
        arcadeShineMatrix.setTranslate(drawX - fontSize, drawY - fontSize)
        cachedArcadeShineGradient?.setLocalMatrix(arcadeShineMatrix)
        fillPaint.shader = cachedArcadeShineGradient
        fillPaint.alpha = (alpha * 255).toInt()
        for (i in extrusionDepth downTo 1) {
            val offset = i * layerStep
            canvas.drawText(text, drawX + offset, drawY + offset, fillPaint)
        }
        fillPaint.style = Paint.Style.FILL
        fillPaint.shader = null

        // -- 3. 흰색 두꺼운 외곽선 --
        outlinePaint.textSize = fontSize
        outlinePaint.color = Color.WHITE
        outlinePaint.strokeWidth = fontSize * 0.07f
        outlinePaint.alpha = (alpha * 255).toInt()
        canvas.drawText(text, drawX, drawY, outlinePaint)

        // -- 4. 무지개 리버 그래디언트 (대각선 흐름 애니메이션) --
        val gradCycleSize = fontSize * 4f
        if (fontSizeInt != cachedArcadeTextGradientSize) {
            cachedArcadeTextGradientSize = fontSizeInt
            cachedArcadeTextGradient = LinearGradient(
                0f, 0f, gradCycleSize, gradCycleSize,
                OverlayColors.ARCADE_RIVER_COLORS,
                null,
                Shader.TileMode.REPEAT
            )
        }
        val riverPeriod = 4000L
        val riverPhase = (now % riverPeriod) / riverPeriod.toFloat()
        val riverOffset = -(riverPhase * gradCycleSize)
        arcadeTextGradientMatrix.setTranslate(riverOffset, riverOffset)
        cachedArcadeTextGradient?.setLocalMatrix(arcadeTextGradientMatrix)
        fillPaint.shader = cachedArcadeTextGradient
        fillPaint.color = Color.WHITE
        fillPaint.alpha = (alpha * 255).toInt()
        fillPaint.setShadowLayer(0f, 0f, 0f, 0)
        canvas.drawText(text, drawX, drawY, fillPaint)
        fillPaint.shader = null

        // 글로우 섀도우 복원
        if (cachedGlowRadius > 0f) {
            fillPaint.setShadowLayer(cachedGlowRadius, 0f, 0f, cachedGlowColor)
        } else {
            fillPaint.setShadowLayer(0f, 0f, 0f, 0)
        }

        canvas.restore()

        outlinePaint.typeface = pretendardBold
        fillPaint.typeface = pretendardBold
        outlinePaint.color = 0xDD000000.toInt()
    }

    // ===================== 스코어 팝업 =====================

    private fun drawScorePopup(canvas: Canvas, popup: ScorePopup, t: Float) {
        val level = comboLevel(popup.combo)
        val luckyScale = if (popup.isLuckyStrike) 1.3f else 1f

        val scale = (when {
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
        }) * luckyScale

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

        val color = if (popup.isLuckyStrike) 0xFFFFD700.toInt() else when {
            arcadeEffects -> popup.cachedColor
            premiumEffects -> premiumScoreColor
            else -> OverlayColors.scorePopupColor(popup.combo)
        }
        val useSpecialFont = premiumEffects || cutiePinkComboEffects || arcadeEffects
        val specialFont = when {
            arcadeEffects -> aggroTypeface
            cutiePinkComboEffects -> santokkiTypeface
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

        if (!milestonePersistent && elapsed > AnimationConstants.MILESTONE_DURATION_MS) {
            milestoneLabel = null
            return
        }

        val t = if (milestonePersistent) {
            (elapsed / 500f).coerceAtMost(1f)
        } else {
            (elapsed / AnimationConstants.MILESTONE_DURATION_MS.toFloat()).coerceIn(0f, 1f)
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

        val useSpecialFont = premiumEffects || cutiePinkComboEffects || arcadeEffects
        val specialFont = when {
            arcadeEffects -> aggroTypeface
            cutiePinkComboEffects -> santokkiTypeface
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

    // ===================== Arcade 마일스톤 코인 GIF =====================
    // GIF 한 사이클 재생 → 페이드아웃

    @Suppress("DEPRECATION")
    private fun drawArcadeMilestoneCoin(canvas: Canvas, cx: Float, now: Long) {
        val movie = arcadeCoinMovie ?: run { arcadeCoinActive = false; return }
        val elapsed = (now - arcadeCoinStartTime).toInt()
        val fadeDurationMs = AnimationConstants.ARCADE_COIN_FADE_MS
        val totalDuration = arcadeCoinMovieDuration + fadeDurationMs

        if (elapsed >= totalDuration) {
            arcadeCoinActive = false
            return
        }

        // GIF 프레임 설정: 한 사이클만 재생
        val movieTime = elapsed.coerceAtMost(arcadeCoinMovieDuration)
        movie.setTime(movieTime)

        // 오프스크린 비트맵 준비 (Movie는 SW 캔버스 필요)
        val mw = movie.width()
        val mh = movie.height()
        if (coinOffscreenBitmap == null || coinOffscreenBitmap!!.width != mw) {
            coinOffscreenBitmap?.recycle()
            coinOffscreenBitmap = Bitmap.createBitmap(mw, mh, Bitmap.Config.ARGB_8888)
            coinOffscreenCanvas = Canvas(coinOffscreenBitmap!!)
        }
        val offBmp = coinOffscreenBitmap!!
        val offCanvas = coinOffscreenCanvas!!

        offBmp.eraseColor(Color.TRANSPARENT)
        movie.draw(offCanvas, 0f, 0f)

        // 투명도: GIF 재생 중 100% → 끝나면 페이드아웃
        val alpha = if (elapsed < arcadeCoinMovieDuration) {
            1f
        } else {
            1f - (elapsed - arcadeCoinMovieDuration) / fadeDurationMs.toFloat()
        }

        // 화면에 그리기: 콤보 카운터 위쪽, 60dp 크기
        val coinSize = 60f * sf
        val halfSize = coinSize / 2f
        val drawY = height * 0.35f

        bitmapPaint.alpha = (alpha * 255).toInt()
        bitmapDstRect.set(cx - halfSize, drawY - halfSize, cx + halfSize, drawY + halfSize)
        canvas.drawBitmap(offBmp, null, bitmapDstRect, bitmapPaint)
        bitmapPaint.alpha = 255
    }

    // ===================== 임팩트 링 =====================

    private fun drawImpactRing(canvas: Canvas, now: Long) {
        val elapsed = now - impactRingStartTime
        val t = elapsed / AnimationConstants.IMPACT_RING_DURATION_MS
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

    // ===================== 내부 클래스 =====================

    private class ScorePopup {
        var score = 0; var combo = 0
        var x = 0f; var y = 0f
        var startTime = 0L; var alive = false
        var cachedText = ""  // String 할당 캐시
        var cachedColor = 0  // 스폰 시점에 결정된 색상
        var isLuckyStrike = false

        fun reset(score: Int, combo: Int, x: Float, y: Float, color: Int = 0, luckyStrike: Boolean = false) {
            this.score = score; this.combo = combo
            this.x = x; this.y = y
            this.startTime = System.currentTimeMillis()
            this.alive = true
            this.isLuckyStrike = luckyStrike
            this.cachedText = if (luckyStrike) "\u2605+$score" else "+$score"
            this.cachedColor = color
        }
    }

}
