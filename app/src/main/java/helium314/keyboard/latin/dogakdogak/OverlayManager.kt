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
import android.widget.FrameLayout
import helium314.keyboard.latin.BuildConfig
import helium314.keyboard.settings.SettingsActivity
import nl.dionsegijn.konfetti.xml.KonfettiView

/**
 * TYPE_APPLICATION_OVERLAY를 사용한 플로팅 콤보 오버레이 (IME용).
 *
 * 레이어 구조 (단일 윈도우, FrameLayout 컨테이너):
 *   FrameLayout (WindowManager에 등록)
 *     └─ KonfettiView  ← 파티클 (하위 레이어)
 *     └─ ComboOverlayView ← 텍스트/팝업/링 (상위 레이어)
 *
 * 단일 윈도우 구조로 Android 12+ 'untrusted touch' 이슈를 방지합니다.
 * (동일 패키지의 복수 오버레이 윈도우 opacity 합산으로 인한 터치 차단 문제)
 */
class OverlayManager(
    private val context: Context,
    private val prefs: SharedPreferences
) {

    private val appContext: Context = context.applicationContext

    private var windowManager: WindowManager? = null
    private var containerView: FrameLayout? = null
    private var overlayView: ComboOverlayView? = null
    private var konfettiView: KonfettiView? = null
    private var layoutParams: WindowManager.LayoutParams? = null
    private var isShowing = false
    /** 전체화면 동영상 감지로 인한 임시 숨김 상태 */
    private var isHiddenForFullscreen = false

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

    /** 오버레이 터치 활성화 여부 (OFF면 터치가 뒤 레이어로 통과) */
    var touchEnabled = false
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
                val container = containerView ?: return
                val w = dpToPx(120f * value)
                val h = dpToPx(140f * value)
                params.width = w
                params.height = h
                try { windowManager?.updateViewLayout(container, params) } catch (e: Exception) {
                    if (BuildConfig.DEBUG) android.util.Log.w("OverlayManager", "updateViewLayout failed (scale)", e)
                }
            }
        }

    /** 현재 touchEnabled 상태에 따른 초기 윈도우 flags 계산 (테스트용 공개) */
    fun getInitialOverlayFlags(): Int {
        val base = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        return if (!touchEnabled) {
            base or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        } else {
            base
        }
    }

    @SuppressLint("ClickableAccessibility")
    fun show() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        // Path 1: 이미 표시 중 — 플래그만 재적용 후 리턴
        if (isShowing && containerView != null) {
            containerView?.visibility = View.VISIBLE
            applyTouchFlag()
            overlayView?.invalidate()
            return
        }

        // Path 2: 뷰가 존재하지만 hide() 상태 — 플래그 재적용 후 리턴
        if (containerView != null) {
            try {
                isShowing = true
                containerView?.visibility = View.VISIBLE
                applyTouchFlag()
                overlayView?.invalidate()
                return
            } catch (e: Exception) {
                if (BuildConfig.DEBUG) android.util.Log.w("OverlayManager", "re-show failed, recreating", e)
                isShowing = false
                try { windowManager?.removeView(containerView) } catch (_: Exception) {}
                containerView = null
                overlayView = null
                konfettiView = null
                layoutParams = null
            }
        }

        // Path 3: 새로 생성
        isShowing = true
        windowManager = appContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager

        val s = overlayScale
        val w = dpToPx(120f * s)
        val h = dpToPx(140f * s)
        val screenWidth = appContext.resources.displayMetrics.widthPixels
        val defaultX = screenWidth - w - dpToPx(8f)
        val savedX = prefs.getInt(PrefsKeys.OVERLAY_X, defaultX)
        val savedY = prefs.getInt(PrefsKeys.OVERLAY_Y, dpToPx(8f))
        // setter 트리거 없이 touchEnabled 읽기 (layoutParams 아직 없으므로)
        val touchPref = prefs.getBoolean(PrefsKeys.OVERLAY_TOUCH, false)

        // --- 단일 FrameLayout 컨테이너에 KonfettiView + ComboOverlayView ---
        val container = FrameLayout(appContext)

        val kv = KonfettiView(appContext)
        container.addView(kv, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        ))
        konfettiView = kv

        val view = ComboOverlayView(appContext).apply {
            setPremiumEffects(premiumEffects)
            setCutiePinkComboEffects(cutiePinkComboEffects)
            setArcadeEffects(arcadeEffects)
            setCountColor(countColor)
            setCount(lastCount)
            setScaleFactor(s)
        }
        container.addView(view, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        ))

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

        // touchPref에 따라 처음부터 FLAG_NOT_TOUCHABLE 포함하여 addView
        val overlayFlags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            if (!touchPref) WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE else 0

        val params = WindowManager.LayoutParams(
            w, h,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            overlayFlags,
            PixelFormat.TRANSPARENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = savedX
            y = savedY
            alpha = 1.0f
        }

        try {
            windowManager?.addView(container, params)
        } catch (e: Exception) {
            android.util.Log.e("OverlayManager", "container addView failed", e)
            isShowing = false
            return
        }

        containerView = container
        overlayView = view
        layoutParams = params
        touchEnabled = touchPref
        setupDrag(container, view, params)
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
        containerView?.visibility = View.INVISIBLE
    }

    /** 전체화면 동영상 감지 시 오버레이 임시 숨김 (수동 hide/show와 독립) */
    fun hideForFullscreen() {
        if (!isShowing || isHiddenForFullscreen) return
        isHiddenForFullscreen = true
        containerView?.visibility = View.INVISIBLE
    }

    /** 전체화면 해제 시 오버레이 복원 */
    fun showAfterFullscreen() {
        if (!isHiddenForFullscreen) return
        isHiddenForFullscreen = false
        if (isShowing) {
            containerView?.visibility = View.VISIBLE
        }
    }

    fun isHiddenForFullscreen(): Boolean = isHiddenForFullscreen

    fun hideImmediately() {
        isShowing = false
        isHiddenForFullscreen = false
        val container = containerView
        if (container != null) {
            try { windowManager?.removeView(container) } catch (e: Exception) {
                android.util.Log.e("OverlayManager", "removeView(container) failed", e)
            }
        }
        containerView = null
        overlayView = null
        konfettiView = null
        layoutParams = null
    }

    @SuppressLint("ClickableAccessibility")
    private fun setupDrag(
        container: FrameLayout,
        view: ComboOverlayView,
        params: WindowManager.LayoutParams
    ) {
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f
        var isDragging = false

        container.setOnTouchListener { _, event ->
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
                        try { windowManager?.updateViewLayout(container, params) } catch (e: Exception) {
                            if (BuildConfig.DEBUG) android.util.Log.w("OverlayManager", "updateViewLayout failed (drag)", e)
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

    private fun applyTouchFlag() {
        val params = layoutParams ?: return
        if (touchEnabled) {
            params.flags = params.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
        } else {
            params.flags = params.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        }
        params.alpha = 1.0f
        val container = containerView ?: return
        // INVISIBLE 상태에서도 WindowManager에 flags를 적용해야 show() 복귀 시 올바른 상태 유지
        try { windowManager?.updateViewLayout(container, params) } catch (e: Exception) {
            if (BuildConfig.DEBUG) android.util.Log.w("OverlayManager", "applyTouchFlag failed", e)
        }
    }

    private fun dpToPx(dp: Float): Int {
        return (dp * appContext.resources.displayMetrics.density).toInt()
    }

}
