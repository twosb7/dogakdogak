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

/**
 * TYPE_APPLICATION_OVERLAY를 사용한 플로팅 콤보 오버레이 (IME용).
 * 원본 앱의 OverlayManager를 IME에 맞게 포팅:
 *   - TYPE_ACCESSIBILITY_OVERLAY → TYPE_APPLICATION_OVERLAY
 *   - DataStore/SettingsRepository → SharedPreferences
 *   - 코루틴 제거 (동기 SharedPreferences 사용)
 *   - applicationContext 사용 (IME WindowContext 타입 불일치 방지)
 *   - hide 디바운스 (빠른 InputView 전환 시 깜빡임 방지)
 */
class OverlayManager(
    private val context: Context,
    private val prefs: SharedPreferences
) {

    // applicationContext를 사용하여 IME WindowContext 타입 충돌 방지
    private val appContext: Context = context.applicationContext

    private var windowManager: WindowManager? = null
    private var overlayView: ComboOverlayView? = null
    private var layoutParams: WindowManager.LayoutParams? = null
    private var isShowing = false

    private var lastCount = 0L

    var premiumEffects = false
        set(value) {
            field = value
            overlayView?.setPremiumEffects(value)
        }

    var bubbleComboEffects = false
        set(value) {
            field = value
            overlayView?.setBubbleComboEffects(value)
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
                val view = overlayView ?: return
                params.width = dpToPx(120f * value)
                params.height = dpToPx(140f * value)
                try { windowManager?.updateViewLayout(view, params) } catch (_: Exception) {}
            }
        }

    /**
     * 오버레이를 WindowManager에 추가 (아직 추가 안 된 경우).
     * 이미 추가되어 있으면 VISIBLE로 전환만 한다.
     */
    @SuppressLint("ClickableAccessibility")
    fun show() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        // 이미 뷰가 붙어있으면 VISIBLE로 전환만
        if (isShowing && overlayView != null) {
            overlayView?.visibility = View.VISIBLE
            overlayView?.invalidate()
            return
        }

        // 뷰는 있지만 숨김 상태 → VISIBLE 전환 (stale view 방어)
        if (overlayView != null) {
            try {
                isShowing = true
                overlayView?.visibility = View.VISIBLE
                overlayView?.invalidate()
                return
            } catch (_: Exception) {
                // stale view — 제거 후 새로 생성
                try { windowManager?.removeView(overlayView) } catch (_: Exception) {}
                overlayView = null
                layoutParams = null
            }
        }

        // 첫 생성
        isShowing = true
        windowManager = appContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager

        val s = overlayScale
        val view = ComboOverlayView(appContext).apply {
            setPremiumEffects(premiumEffects)
            setBubbleComboEffects(bubbleComboEffects)
            setCountColor(countColor)
            setCount(lastCount)
            setScaleFactor(s)
        }

        val params = WindowManager.LayoutParams(
            dpToPx(120f * s),
            dpToPx(140f * s),
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = prefs.getInt("dogakdogak_overlay_x", 0)
            y = prefs.getInt("dogakdogak_overlay_y", 200)
        }

        try {
            windowManager?.addView(view, params)
        } catch (e: Exception) {
            android.util.Log.e("OverlayManager", "addView failed", e)
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

    /** 키 입력 시 호출 -> 콤보 이펙트 표시 (파티클만 프리미엄 체크) */
    fun onKeyPress(score: Int, combo: Int) {
        overlayView?.updateCombo(combo, score)
    }

    /**
     * 오버레이를 숨김 (뷰는 유지, INVISIBLE 전환).
     * 뷰를 제거하지 않아서 다시 show() 할 때 즉시 복원됨.
     */
    fun hide() {
        if (!isShowing) return
        isShowing = false
        overlayView?.visibility = View.INVISIBLE
    }

    /** 완전 제거 (onDestroy 등) */
    fun hideImmediately() {
        isShowing = false
        val view = overlayView
        if (view != null) {
            try { windowManager?.removeView(view) } catch (e: Exception) {
                android.util.Log.e("OverlayManager", "removeView failed", e)
            }
        }
        overlayView = null
        layoutParams = null
    }

    @SuppressLint("ClickableAccessibility")
    private fun setupDrag(view: View, params: WindowManager.LayoutParams) {
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f
        var isDragging = false

        view.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    val cv = v as? ComboOverlayView
                    if (cv != null && !cv.isTouchOnVisibleContent(event.x, event.y)) {
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

    /** 터치 플래그 동적 전환 */
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

    private fun dpToPx(dp: Float): Int {
        return (dp * appContext.resources.displayMetrics.density).toInt()
    }

}
