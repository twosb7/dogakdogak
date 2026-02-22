package helium314.keyboard.latin.dogakdogak

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.PixelFormat
import android.os.Build
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import helium314.keyboard.settings.SettingsActivity
import nl.dionsegijn.konfetti.core.Party
import nl.dionsegijn.konfetti.core.Position
import nl.dionsegijn.konfetti.core.Spread
import nl.dionsegijn.konfetti.core.emitter.Emitter
import nl.dionsegijn.konfetti.core.models.Shape
import nl.dionsegijn.konfetti.core.models.Size
import nl.dionsegijn.konfetti.xml.KonfettiView
import java.util.concurrent.TimeUnit

/**
 * TYPE_APPLICATION_OVERLAY를 사용한 플로팅 콤보 오버레이 (IME용).
 *
 * 레이어 구조 (두 윈도우, 같은 크기/위치):
 *   Window 1 (FLAG_NOT_TOUCHABLE): KonfettiView ← 파티클 (오버레이 영역 내 클립)
 *   Window 2 (드래그 가능):       ComboOverlayView ← 텍스트/팝업/링
 */
class OverlayManager(
    private val context: Context,
    private val prefs: SharedPreferences
) {

    private val appContext: Context = context.applicationContext

    private var windowManager: WindowManager? = null
    private var overlayView: ComboOverlayView? = null
    private var konfettiView: KonfettiView? = null
    private var layoutParams: WindowManager.LayoutParams? = null
    private var konfettiLayoutParams: WindowManager.LayoutParams? = null
    private var isShowing = false

    private var lastCount = 0L

    var premiumEffects = false
        set(value) {
            field = value
            overlayView?.setPremiumEffects(value)
        }

    var cutiePinkComboEffects = false
        set(value) {
            field = value
            overlayView?.setCutiePinkComboEffects(value)
        }

    var chillEffects = false
        set(value) {
            field = value
            overlayView?.setChillEffects(value)
        }

    /** 오버레이 카운트 텍스트 색상 */
    var countColor: Int = 0xFFFF6B00.toInt()
        set(value) {
            field = value
            overlayView?.setCountColor(value)
        }

    /** 오버레이 터치 활성화 여부 (OFF면 터치가 뒤 레이어로 통과) */
    var touchEnabled = true
        set(value) {
            field = value
            applyTouchFlag()
        }

    /** 오버레이 크기 배율 (0.5~2.0) */
    var overlayScale = 1.0f
        set(value) {
            val clamped = value.coerceIn(0.5f, 2.0f)
            if (field == clamped) return
            field = clamped
            if (isShowing) {
                overlayView?.setScaleFactor(value)
                overlayView?.invalidate()
                val params = layoutParams ?: return
                val rView = overlayView ?: return
                val w = dpToPx(120f * value)
                val h = dpToPx(140f * value)
                params.width = w
                params.height = h
                try { windowManager?.updateViewLayout(rView, params) } catch (_: Exception) {}
                // KonfettiView도 동일 크기로 동기화
                val kParams = konfettiLayoutParams
                val kView = konfettiView
                if (kParams != null && kView != null) {
                    kParams.width = w
                    kParams.height = h
                    try { windowManager?.updateViewLayout(kView, kParams) } catch (_: Exception) {}
                }
            }
        }

    @SuppressLint("ClickableAccessibility")
    fun show() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        if (isShowing && overlayView != null) {
            overlayView?.visibility = View.VISIBLE
            konfettiView?.visibility = View.VISIBLE
            overlayView?.invalidate()
            return
        }

        if (overlayView != null) {
            try {
                isShowing = true
                overlayView?.visibility = View.VISIBLE
                konfettiView?.visibility = View.VISIBLE
                overlayView?.invalidate()
                return
            } catch (_: Exception) {
                try { windowManager?.removeView(overlayView) } catch (_: Exception) {}
                try { windowManager?.removeView(konfettiView) } catch (_: Exception) {}
                overlayView = null
                konfettiView = null
                layoutParams = null
                konfettiLayoutParams = null
            }
        }

        isShowing = true
        windowManager = appContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager

        val s = overlayScale
        val savedX = prefs.getInt("dogakdogak_overlay_x", 0)
        val savedY = prefs.getInt("dogakdogak_overlay_y", 200)
        val w = dpToPx(120f * s)
        val h = dpToPx(140f * s)

        // --- KonfettiView: 오버레이와 동일 크기/위치, 터치 패스스루 ---
        val kv = KonfettiView(appContext)
        val kParams = WindowManager.LayoutParams(
            w, h,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = savedX
            y = savedY
        }
        try {
            windowManager?.addView(kv, kParams)
            konfettiView = kv
            konfettiLayoutParams = kParams
        } catch (e: Exception) {
            android.util.Log.e("OverlayManager", "konfettiView addView failed", e)
        }

        // --- ComboOverlayView: 소형 윈도우, 드래그 가능 ---
        val view = ComboOverlayView(appContext).apply {
            setPremiumEffects(premiumEffects)
            setCutiePinkComboEffects(cutiePinkComboEffects)
            setChillEffects(chillEffects)
            setCountColor(countColor)
            setCount(lastCount)
            setScaleFactor(s)
        }

        // 콜백 연결 — Position.Relative(0.5, 0.15) = KonfettiView 상단부
        view.onMilestoneTriggered = { milestone, mode ->
            val konfetti = konfettiView
            if (konfetti != null) {
                when (mode) {
                    EffectMode.CHILL -> burstChillKonfetti(konfetti, milestone)
                    EffectMode.CUTIE_PINK -> burstCutiePinkKonfetti(konfetti, milestone)
                    else -> burstPremiumKonfetti(konfetti, milestone)
                }
            }
        }
        view.onComboParticleSpawn = { count, mode ->
            val konfetti = konfettiView
            if (konfetti != null) burstMiniKonfetti(konfetti, count, mode)
        }

        val params = WindowManager.LayoutParams(
            w, h,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = savedX
            y = savedY
        }

        try {
            windowManager?.addView(view, params)
        } catch (e: Exception) {
            android.util.Log.e("OverlayManager", "overlayView addView failed", e)
            isShowing = false
            return
        }

        overlayView = view
        layoutParams = params
        applyTouchFlag()
        setupDrag(view, params)
    }

    fun updateCount(count: Long) {
        lastCount = count
        overlayView?.setCount(count)
    }

    fun onKeyPress(score: Int, combo: Int) {
        overlayView?.updateCombo(combo, score)
    }

    fun hide() {
        if (!isShowing) return
        isShowing = false
        overlayView?.visibility = View.INVISIBLE
        konfettiView?.visibility = View.INVISIBLE
    }

    fun hideImmediately() {
        isShowing = false
        val oView = overlayView
        val kView = konfettiView
        if (oView != null) {
            try { windowManager?.removeView(oView) } catch (e: Exception) {
                android.util.Log.e("OverlayManager", "removeView(overlayView) failed", e)
            }
        }
        if (kView != null) {
            try { windowManager?.removeView(kView) } catch (e: Exception) {
                android.util.Log.e("OverlayManager", "removeView(konfettiView) failed", e)
            }
        }
        overlayView = null
        konfettiView = null
        layoutParams = null
        konfettiLayoutParams = null
    }

    @SuppressLint("ClickableAccessibility")
    private fun setupDrag(view: ComboOverlayView, params: WindowManager.LayoutParams) {
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f
        var isDragging = false

        view.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    if (!view.isTouchOnVisibleContent(event.x, event.y)) {
                        return@setOnTouchListener false
                    }
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    isDragging = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - initialTouchX
                    val dy = event.rawY - initialTouchY
                    if (!isDragging && (dx * dx + dy * dy) > 100) {
                        isDragging = true
                    }
                    if (isDragging) {
                        params.x = initialX + dx.toInt()
                        params.y = initialY + dy.toInt()
                        try { windowManager?.updateViewLayout(view, params) } catch (_: Exception) {}
                        // KonfettiView 위치 동기화
                        val kParams = konfettiLayoutParams
                        val kView = konfettiView
                        if (kParams != null && kView != null) {
                            kParams.x = params.x
                            kParams.y = params.y
                            try { windowManager?.updateViewLayout(kView, kParams) } catch (_: Exception) {}
                        }
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (isDragging) {
                        prefs.edit()
                            .putInt("dogakdogak_overlay_x", params.x)
                            .putInt("dogakdogak_overlay_y", params.y)
                            .apply()
                    } else {
                        val intent = Intent(context, SettingsActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                        }
                        context.startActivity(intent)
                    }
                    true
                }
                else -> false
            }
        }
    }

    private fun applyTouchFlag() {
        val params = layoutParams ?: return
        if (touchEnabled) {
            params.flags = params.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
        } else {
            params.flags = params.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        }
        val view = overlayView ?: return
        if (isShowing) {
            try { windowManager?.updateViewLayout(view, params) } catch (_: Exception) {}
        }
    }

    // ===================== Konfetti 버스트 함수들 =====================
    // Position.Relative(0.5, 0.15) = KonfettiView 상단 (오버레이 상단부 발사)
    // → 파티클이 오버레이 영역 안에서만 표시됨

    /** CHILL 모드: 파스텔 색상, 양 옆으로 천천히 퍼지는 원 파티클 */
    private fun burstChillKonfetti(kv: KonfettiView, milestone: ComboMilestone) {
        val half = (1 + milestone.ordinal / 2 + 1).coerceAtMost(5)
        val colors = listOf(
            0xFFE8B878.toInt(), 0xFFD4B8E8.toInt(),
            0xFFE8A8A8.toInt(), 0xFFA8D8B0.toInt(),
            0xFFF5E0C0.toInt(), 0xFFB0C8E0.toInt()
        )
        // angle=180: 좌측 sweep (하좌~좌~상좌), angle=0: 우측 sweep (상우~우~하우)
        kv.start(
            Party(
                speed = 0.5f, maxSpeed = 2.5f, damping = 0.95f,
                angle = 180, spread = 90,
                colors = colors,
                emitter = Emitter(200L, TimeUnit.MILLISECONDS).max(half),
                shapes = listOf(Shape.Circle), size = listOf(Size.SMALL),
                timeToLive = 1800L, position = Position.Relative(0.5, 0.15)
            ),
            Party(
                speed = 0.5f, maxSpeed = 2.5f, damping = 0.95f,
                angle = 0, spread = 90,
                colors = colors,
                emitter = Emitter(200L, TimeUnit.MILLISECONDS).max(half),
                shapes = listOf(Shape.Circle), size = listOf(Size.SMALL),
                timeToLive = 1800L, position = Position.Relative(0.5, 0.15)
            )
        )
    }

    /** CUTIE_PINK 모드: 핑크 계열, 양 옆으로 화려하게 */
    private fun burstCutiePinkKonfetti(kv: KonfettiView, milestone: ComboMilestone) {
        val half = (3 + milestone.ordinal * 2).coerceAtMost(10)
        val colors = listOf(
            0xFFFF69B4.toInt(), 0xFFFF1493.toInt(), 0xFFFFB6C1.toInt(),
            0xFFF06292.toInt(), 0xFFEC407A.toInt(), 0xFFFF80AB.toInt()
        )
        kv.start(
            Party(
                speed = 2f, maxSpeed = 7f, damping = 0.88f,
                angle = 180, spread = 100,
                colors = colors,
                emitter = Emitter(70L, TimeUnit.MILLISECONDS).max(half),
                shapes = listOf(Shape.Circle), size = listOf(Size.SMALL),
                timeToLive = 1400L, position = Position.Relative(0.5, 0.15)
            ),
            Party(
                speed = 2f, maxSpeed = 7f, damping = 0.88f,
                angle = 0, spread = 100,
                colors = colors,
                emitter = Emitter(70L, TimeUnit.MILLISECONDS).max(half),
                shapes = listOf(Shape.Circle), size = listOf(Size.SMALL),
                timeToLive = 1400L, position = Position.Relative(0.5, 0.15)
            )
        )
    }

    /** PREMIUM 모드: 화려한 색상, 양 옆으로 폭발적으로, Square+Circle */
    private fun burstPremiumKonfetti(kv: KonfettiView, milestone: ComboMilestone) {
        val half = (4 + milestone.ordinal * 3).coerceAtMost(12)
        val colors = listOf(
            0xFFFF453A.toInt(), 0xFFFF9F0A.toInt(), 0xFFFFD60A.toInt(),
            0xFF30D158.toInt(), 0xFF0A84FF.toInt(), 0xFFBF5AF2.toInt(),
            0xFFFF375F.toInt()
        )
        kv.start(
            Party(
                speed = 3f, maxSpeed = 9f, damping = 0.87f,
                angle = 180, spread = 110,
                colors = colors,
                emitter = Emitter(70L, TimeUnit.MILLISECONDS).max(half),
                shapes = listOf(Shape.Square, Shape.Circle), size = listOf(Size.SMALL, Size.MEDIUM),
                timeToLive = 1200L, position = Position.Relative(0.5, 0.15)
            ),
            Party(
                speed = 3f, maxSpeed = 9f, damping = 0.87f,
                angle = 0, spread = 110,
                colors = colors,
                emitter = Emitter(70L, TimeUnit.MILLISECONDS).max(half),
                shapes = listOf(Shape.Square, Shape.Circle), size = listOf(Size.SMALL, Size.MEDIUM),
                timeToLive = 1200L, position = Position.Relative(0.5, 0.15)
            )
        )
    }

    /** 콤보 증가 시 소량 미니 파티클 - 양 옆으로 */
    private fun burstMiniKonfetti(kv: KonfettiView, count: Int, mode: EffectMode) {
        val colors = when (mode) {
            EffectMode.CHILL -> listOf(
                0xFFE8B878.toInt(), 0xFFD4B8E8.toInt(), 0xFFE8A8A8.toInt()
            )
            EffectMode.CUTIE_PINK -> listOf(
                0xFFFF69B4.toInt(), 0xFFFFB6C1.toInt(), 0xFFF06292.toInt()
            )
            else -> listOf(
                0xFFFF9F0A.toInt(), 0xFFFFD60A.toInt(), 0xFF0A84FF.toInt(), 0xFFBF5AF2.toInt()
            )
        }
        val baseSpeed = if (mode == EffectMode.CHILL) 0.5f else 1.5f
        val maxSpd = if (mode == EffectMode.CHILL) 2.5f else 5f
        val half = (count + 1) / 2
        kv.start(
            Party(
                speed = baseSpeed, maxSpeed = maxSpd, damping = 0.91f,
                angle = 180, spread = 80,
                colors = colors,
                emitter = Emitter(60L, TimeUnit.MILLISECONDS).max(half),
                shapes = listOf(Shape.Circle), size = listOf(Size.SMALL),
                timeToLive = 850L, position = Position.Relative(0.5, 0.15)
            ),
            Party(
                speed = baseSpeed, maxSpeed = maxSpd, damping = 0.91f,
                angle = 0, spread = 80,
                colors = colors,
                emitter = Emitter(60L, TimeUnit.MILLISECONDS).max(half),
                shapes = listOf(Shape.Circle), size = listOf(Size.SMALL),
                timeToLive = 850L, position = Position.Relative(0.5, 0.15)
            )
        )
    }

    private fun dpToPx(dp: Float): Int {
        return (dp * appContext.resources.displayMetrics.density).toInt()
    }

}
