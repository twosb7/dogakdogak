package helium314.keyboard.latin;

public final class KeyboardResizeUtil {

    public static final float MIN_SCALE = 0.3f;
    public static final float MAX_SCALE = 1.5f;

    private KeyboardResizeUtil() {}

    /** Apply a step delta to the current scale and clamp to [MIN_SCALE, MAX_SCALE]. */
    public static float stepScale(final float currentScale, final float delta) {
        return Math.max(MIN_SCALE, Math.min(MAX_SCALE, currentScale + delta));
    }
}
