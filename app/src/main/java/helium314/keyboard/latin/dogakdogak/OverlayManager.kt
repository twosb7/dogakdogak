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
 * 레이어 구조:
 *   Window 1 (전체화면, FLAG_NOT_TOUCHABLE): KonfettiView ← 마일스톤 파티클
 *   Window 2 (120x140dp, 드래그 가능):     ComboOverlayView ← 텍스트/팝업/링
 */
class OverlayManager(
    private val context: Context,
    private val prefs: SharedPreferences
) {

    // applicationContext를 사용하여 IME WindowContext 타입 충돌 방지
    private val appContext: Context = context.applicationContext

    private var windowManager: WindowManager? = null
    private var overlayView: ComboOverlayView? = null
    private var konfettiView: KonfettiView? = null
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
                params.width = dpToPx(120f * value)
                params.height = dpToPx(140f * value)
                try { windowManager?.updateViewLayout(rView, params) } catch (_: Exception) {}
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
            konfettiView?.visibility = View.VISIBLE
            overlayView?.invalidate()
            return
        }

        // 뷰는 있지만 숨김 상태 → VISIBLE 전환 (stale view 방어)
        if (overlayView != null) {
            try {
                isShowing = true
                overlayView?.visibility = View.VISIBLE
                konfettiView?.visibility = View.VISIBLE
                overlayView?.invalidate()
                return
            } catch (_: Exception) {
                // stale view — 제거 후 새로 생성
                try { windowManager?.removeView(overlayView) } catch (_: Exception) {}
                try { windowManager?.removeView(konfettiView) } catch (_: Exception) {}
                overlayView = null
                konfettiView = null
                layoutParams = null
            }
        }

        // 첫 생성
        isShowing = true
        windowManager = appContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager

        val s = overlayScale

        // --- KonfettiView: 전체화면, 터치 패스스루 ---
        val kv = KonfettiView(appContext)
        val konfettiParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
        }
        try {
            windowManager?.addView(kv, konfettiParams)
            konfettiView = kv
        } catch (e: Exception) {
            android.util.Log.e("OverlayManager", "konfettiView addView failed", e)
        }

        // --- ComboOverlayView: 소형 윈도우, 드래그 가능 ---
        val view = ComboOverlayView(appContext).apply {
            setPremiumEffects(premiumEffects)
            setBubbleComboEffects(bubbleComboEffects)
            setChillEffects(chillEffects)
            setCountColor(countColor)
            setCount(lastCount)
            setScaleFactor(s)
        }

        // 콜백 연결
        view.onMilestoneTriggered = { milestone, mode ->
            val konfetti = konfettiView
            if (konfetti != null) {
                val pos = widgetRelativePosition()
                when (mode) {
                    EffectMode.CHILL -> burstChillKonfetti(konfetti, milestone, pos)
                    EffectMode.BUBBLE -> burstCuteKonfetti(konfetti, milestone, pos)
                    else -> burstPremiumKonfetti(konfetti, milestone, pos)
                }
            }
        }
        view.onComboParticleSpawn = { count, mode ->
            val konfetti = konfettiView
            if (konfetti != null) burstMiniKonfetti(konfetti, count, mode, widgetRelativePosition())
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
        konfettiView?.visibility = View.INVISIBLE
    }

    /** 완전 제거 (onDestroy 등) */
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

    // ===================== Konfetti 버스트 함수들 =====================

    /** CHILL 모드: 파스텔 색상, 느린 속도, 위로 올라가는 원 파티클 */
    private fun burstChillKonfetti(kv: KonfettiView, milestone: ComboMilestone) {
        val count = 3 + milestone.ordinal
        kv.start(
            Party(
                speed = 0.3f,
                maxSpeed = 1.5f,
                damping = 0.97f,
                angle = 270,        // 위쪽 (북쪽)
                spread = 60,        // 240°~300° 범위 (위쪽 60° 부채꼴)
                colors = listOf(
                    0xFFE8B878.toInt(), 0xFFD4B8E8.toInt(),
                    0xFFE8A8A8.toInt(), 0xFFA8D8B0.toInt(),
                    0xFFF5E0C0.toInt(), 0xFFB0C8E0.toInt()
                ),
                emitter = Emitter(500L, TimeUnit.MILLISECONDS).max(count),
                shapes = listOf(Shape.Circle),
                size = listOf(Size.SMALL),
                timeToLive = 3000L,
                position = Position.Relative(0.5, 0.35)
            )
        )
    }

    /** BUBBLE(CUTE) 모드: 핑크 계열, 중간 속도, 전방향 */
    private fun burstCuteKonfetti(kv: KonfettiView, milestone: ComboMilestone) {
        val count = 15 + milestone.ordinal * 8
        kv.start(
            Party(
                speed = 1f,
                maxSpeed = 4f,
                damping = 0.9f,
                angle = 270,
                spread = Spread.ROUND,
                colors = listOf(
                    0xFFFF69B4.toInt(), 0xFFFF1493.toInt(), 0xFFFFB6C1.toInt(),
                    0xFFF06292.toInt(), 0xFFEC407A.toInt(), 0xFFFF80AB.toInt()
                ),
                emitter = Emitter(100L, TimeUnit.MILLISECONDS).max(count),
                shapes = listOf(Shape.Circle),
                size = listOf(Size.SMALL),
                timeToLive = 1500L,
                position = Position.Relative(0.5, 0.35)
            )
        )
    }

    /** PREMIUM 모드: 화려한 색상, 빠른 속도, 전방향, Square+Circle */
    private fun burstPremiumKonfetti(kv: KonfettiView, milestone: ComboMilestone) {
        val count = 20 + milestone.ordinal * 10
        kv.start(
            Party(
                speed = 2f,
                maxSpeed = 6f,
                damping = 0.9f,
                angle = 270,
                spread = Spread.ROUND,
                colors = listOf(
                    0xFFFF453A.toInt(), 0xFFFF9F0A.toInt(), 0xFFFFD60A.toInt(),
                    0xFF30D158.toInt(), 0xFF0A84FF.toInt(), 0xFFBF5AF2.toInt(),
                    0xFFFF375F.toInt()
                ),
                emitter = Emitter(100L, TimeUnit.MILLISECONDS).max(count),
                shapes = listOf(Shape.Square, Shape.Circle),
                size = listOf(Size.SMALL, Size.MEDIUM),
                timeToLive = 1200L,
                position = Position.Relative(0.5, 0.35)
            )
        )
    }

    /** 콤보 증가 시 소량 미니 파티클 */
    private fun burstMiniKonfetti(kv: KonfettiView, count: Int, mode: EffectMode) {
        val colors = when (mode) {
            EffectMode.CHILL -> listOf(
                0xFFE8B878.toInt(), 0xFFD4B8E8.toInt(), 0xFFE8A8A8.toInt()
            )
            EffectMode.BUBBLE -> listOf(
                0xFFFF69B4.toInt(), 0xFFFFB6C1.toInt(), 0xFFF06292.toInt()
            )
            else -> listOf(
                0xFFFF9F0A.toInt(), 0xFFFFD60A.toInt(), 0xFF0A84FF.toInt(), 0xFFBF5AF2.toInt()
            )
        }
        val maxSpeed = if (mode == EffectMode.CHILL) 1.0f else 3.0f
        kv.start(
            Party(
                speed = 0.1f,
                maxSpeed = maxSpeed,
                damping = 0.95f,
                angle = 270,
                spread = 45,
                colors = colors,
                emitter = Emitter(100L, TimeUnit.MILLISECONDS).max(count),
                shapes = listOf(Shape.Circle),
                size = listOf(Size.SMALL),
                timeToLive = 1000L,
                position = Position.Relative(0.5, 0.4)
            )
        )
    }

    private fun dpToPx(dp: Float): Int {
        return (dp * appContext.resources.displayMetrics.density).toInt()
    }

}
