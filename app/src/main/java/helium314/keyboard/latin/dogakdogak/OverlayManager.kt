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
import nl.dionsegijn.konfetti.xml.KonfettiView

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

    var arcadeEffects = false
        set(value) {
            field = value
            overlayView?.setArcadeEffects(value)
        }

    /** 오버레이 카운트 텍스트 색상 */
    var countColor: Int = 0xFFFF6B00.toInt()
        set(value) {
            field = value
            overlayView?.setCountColor(value)
        }

    /** 오버레이 터치 활성화 여부 (OFF면 드래그/클릭 비활성) */
    var touchEnabled = false

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
                params.alpha = 1.0f
                try { windowManager?.updateViewLayout(rView, params) } catch (_: Exception) {}
                // KonfettiView도 동일 크기로 동기화
                val kParams = konfettiLayoutParams
                val kView = konfettiView
                if (kParams != null && kView != null) {
                    kParams.width = w
                    kParams.height = h
                    kParams.alpha = 1.0f
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
        val w = dpToPx(120f * s)
        val h = dpToPx(140f * s)
        val screenWidth = appContext.resources.displayMetrics.widthPixels
        val defaultX = screenWidth - w - dpToPx(8f)
        val savedX = prefs.getInt(PrefsKeys.OVERLAY_X, defaultX)
        val savedY = prefs.getInt(PrefsKeys.OVERLAY_Y, dpToPx(8f))
        touchEnabled = prefs.getBoolean(PrefsKeys.OVERLAY_TOUCH, false)

        // --- KonfettiView: 오버레이와 동일 크기/위치, 터치 패스스루 ---
        val kv = KonfettiView(appContext)
        val kParams = WindowManager.LayoutParams(
            w, h,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.RGBA_8888
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = savedX
            y = savedY
            alpha = 1.0f
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
            setArcadeEffects(arcadeEffects)
            setCountColor(countColor)
            setCount(lastCount)
            setScaleFactor(s)
        }

        // 콜백 연결 — Position.Relative(0.5, 0.15) = KonfettiView 상단부
        view.onMilestoneTriggered = { milestone, mode ->
            val konfetti = konfettiView
            if (konfetti != null) {
                when (mode) {
                    EffectMode.ARCADE -> burstArcadeKonfetti(konfetti, milestone)
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
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.RGBA_8888
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = savedX
            y = savedY
            alpha = 1.0f
        }

        try {
            windowManager?.addView(view, params)
        } catch (e: Exception) {
            android.util.Log.e("OverlayManager", "overlayView addView failed", e)
            isShowing = false
            return
        }

        // addView 후 alpha 강제 보정 (TRANSLUCENT가 alpha를 덮어쓰는 기기 대응)
        params.alpha = 1.0f
        try { windowManager?.updateViewLayout(view, params) } catch (_: Exception) {}
        kParams.alpha = 1.0f
        try { windowManager?.updateViewLayout(kv, kParams) } catch (_: Exception) {}

        overlayView = view
        layoutParams = params
        setupDrag(view, params)
    }

    fun updateCount(count: Long) {
        lastCount = count
        overlayView?.setCount(count)
    }

    fun onKeyPress(score: Int, combo: Int, luckyStrike: Boolean = false) {
        overlayView?.updateCombo(combo, score, luckyStrike)
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
            // 터치 비활성화 시 드래그/클릭 무시 (윈도우는 터치 수신하지만 동작 없음)
            if (!touchEnabled) return@setOnTouchListener false
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
                            .putInt(PrefsKeys.OVERLAY_X, params.x)
                            .putInt(PrefsKeys.OVERLAY_Y, params.y)
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

    private fun dpToPx(dp: Float): Int {
        return (dp * appContext.resources.displayMetrics.density).toInt()
    }

}
