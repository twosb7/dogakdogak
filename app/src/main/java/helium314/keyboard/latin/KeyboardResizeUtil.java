package helium314.keyboard.latin;

public final class KeyboardResizeUtil {

    public static final float MIN_SCALE = 0.3f;
    public static final float MAX_SCALE = 1.5f;
    public static final float SENSITIVITY = 2.0f;

    private KeyboardResizeUtil() {}

    public static float calculateNewScale(
            final float startScale,
            final float startRawY,
            final float currentRawY,
            final float screenHeight) {
        final float deltaY = (startRawY - currentRawY) / screenHeight;
        final float newScale = startScale + deltaY * SENSITIVITY;
        return Math.max(MIN_SCALE, Math.min(MAX_SCALE, newScale));
    }

    public static boolean isVerticalDrag(
            final float dx,
            final float dy,
            final float threshold) {
        return dy > threshold && dy > dx;
    }
}
