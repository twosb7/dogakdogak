package helium314.keyboard.latin.dogakdogak

import androidx.core.content.ContextCompat
import nl.dionsegijn.konfetti.core.Party
import nl.dionsegijn.konfetti.core.Position
import nl.dionsegijn.konfetti.core.emitter.Emitter
import nl.dionsegijn.konfetti.core.models.Shape
import nl.dionsegijn.konfetti.core.models.Size
import nl.dionsegijn.konfetti.xml.KonfettiView
import java.util.concurrent.TimeUnit

/** Arcade 코인 Shape 캐시 (Drawable 로딩 1회) */
private var arcadeCoinShapes: List<Shape>? = null
private val arcadeCoinDrawableNames = listOf(
    "coin_gold_pixel",
    "coin_silver_pixel",
    "coin_gold_pixel2",
    "coin_silver_pixel2",
)

private fun getArcadeCoinShapes(kv: KonfettiView): List<Shape> {
    arcadeCoinShapes?.let { return it }
    val ctx = kv.context
    val shapes = arcadeCoinDrawableNames.mapNotNull { name ->
        ctx.resources.getIdentifier(name, "drawable", ctx.packageName)
            .takeIf { it != 0 }
            ?.let { drawableId -> ContextCompat.getDrawable(ctx, drawableId) }
            ?.let { Shape.DrawableShape(it, tint = false) }
    }.ifEmpty { listOf(Shape.Square) }
    arcadeCoinShapes = shapes
    return shapes
}

/**
 * Konfetti 파티클 버스트 팩토리.
 * Position.Relative(0.5, 0.15) = KonfettiView 상단 (오버레이 상단부 발사)
 * → 파티클이 오버레이 영역 안에서만 표시됨
 */

/** ARCADE 모드: 금/은화 픽셀 코인 파티클 */
internal fun burstArcadeKonfetti(kv: KonfettiView, milestone: ComboMilestone) {
    val half = (2 + milestone.ordinal * 2).coerceAtMost(8)
    val colors = OverlayColors.ARCADE_KONFETTI_COLORS
    val coinShapes = getArcadeCoinShapes(kv)
    kv.start(
        Party(
            speed = 1f, maxSpeed = 4f, damping = 0.90f,
            angle = 180, spread = 100,
            colors = colors,
            emitter = Emitter(150L, TimeUnit.MILLISECONDS).max(half),
            shapes = coinShapes, size = listOf(Size.MEDIUM, Size.LARGE),
            timeToLive = 1500L, position = Position.Relative(0.5, 0.15)
        ),
        Party(
            speed = 1f, maxSpeed = 4f, damping = 0.90f,
            angle = 0, spread = 100,
            colors = colors,
            emitter = Emitter(150L, TimeUnit.MILLISECONDS).max(half),
            shapes = coinShapes, size = listOf(Size.MEDIUM, Size.LARGE),
            timeToLive = 1500L, position = Position.Relative(0.5, 0.15)
        )
    )
}

/** CUTIE_PINK 모드: 핑크 계열, 양 옆으로 화려하게 */
internal fun burstCutiePinkKonfetti(kv: KonfettiView, milestone: ComboMilestone) {
    val half = (3 + milestone.ordinal * 2).coerceAtMost(10)
    val colors = OverlayColors.CUTIE_PINK_KONFETTI_COLORS
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
internal fun burstPremiumKonfetti(kv: KonfettiView, milestone: ComboMilestone) {
    val half = (4 + milestone.ordinal * 3).coerceAtMost(12)
    val colors = OverlayColors.PREMIUM_KONFETTI_COLORS
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
internal fun burstMiniKonfetti(kv: KonfettiView, count: Int, mode: EffectMode) {
    val colors = when (mode) {
        EffectMode.ARCADE -> OverlayColors.MINI_ARCADE_KONFETTI_COLORS
        EffectMode.CUTIE_PINK -> OverlayColors.MINI_CUTIE_PINK_KONFETTI_COLORS
        else -> OverlayColors.MINI_PREMIUM_KONFETTI_COLORS
    }
    val baseSpeed = if (mode == EffectMode.ARCADE) 1f else 1.5f
    val maxSpd = if (mode == EffectMode.ARCADE) 3.5f else 5f
    val shapes = if (mode == EffectMode.ARCADE) getArcadeCoinShapes(kv) else listOf(Shape.Circle)
    val half = (count + 1) / 2
    kv.start(
        Party(
            speed = baseSpeed, maxSpeed = maxSpd, damping = 0.91f,
            angle = 180, spread = 80,
            colors = colors,
            emitter = Emitter(60L, TimeUnit.MILLISECONDS).max(half),
            shapes = shapes, size = if (mode == EffectMode.ARCADE) listOf(Size.SMALL, Size.MEDIUM) else listOf(Size.SMALL),
            timeToLive = 850L, position = Position.Relative(0.5, 0.15)
        ),
        Party(
            speed = baseSpeed, maxSpeed = maxSpd, damping = 0.91f,
            angle = 0, spread = 80,
            colors = colors,
            emitter = Emitter(60L, TimeUnit.MILLISECONDS).max(half),
            shapes = shapes, size = if (mode == EffectMode.ARCADE) listOf(Size.SMALL, Size.MEDIUM) else listOf(Size.SMALL),
            timeToLive = 850L, position = Position.Relative(0.5, 0.15)
        )
    )
}
