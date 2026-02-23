package helium314.keyboard.latin

import org.junit.Assert.assertEquals
import org.junit.Test

class KeyboardResizeUtilTest {

    @Test
    fun `drag up increases scale`() {
        // startY=1000, currentY=900 → dragged up 100px, screenHeight=2000
        // deltaY = (1000-900)/2000 = 0.05, newScale = 1.0 + 0.05*2 = 1.1
        val result = KeyboardResizeUtil.calculateNewScale(1.0f, 1000f, 900f, 2000f)
        assertEquals(1.1f, result, 0.001f)
    }

    @Test
    fun `drag down decreases scale`() {
        // startY=1000, currentY=1100 → dragged down 100px
        // deltaY = (1000-1100)/2000 = -0.05, newScale = 1.0 + (-0.05)*2 = 0.9
        val result = KeyboardResizeUtil.calculateNewScale(1.0f, 1000f, 1100f, 2000f)
        assertEquals(0.9f, result, 0.001f)
    }

    @Test
    fun `scale clamped to minimum 0_3`() {
        // Large downward drag
        val result = KeyboardResizeUtil.calculateNewScale(0.5f, 1000f, 2000f, 2000f)
        assertEquals(0.3f, result, 0.001f)
    }

    @Test
    fun `scale clamped to maximum 1_5`() {
        // Large upward drag
        val result = KeyboardResizeUtil.calculateNewScale(1.0f, 2000f, 0f, 2000f)
        assertEquals(1.5f, result, 0.001f)
    }

    @Test
    fun `no drag returns original scale`() {
        val result = KeyboardResizeUtil.calculateNewScale(1.2f, 500f, 500f, 2000f)
        assertEquals(1.2f, result, 0.001f)
    }

    @Test
    fun `sensitivity factor doubles drag effect`() {
        // 25% of screen dragged up → scale change = 0.25 * 2 = 0.5
        val result = KeyboardResizeUtil.calculateNewScale(1.0f, 1000f, 500f, 2000f)
        assertEquals(1.5f, result, 0.001f)
    }

    @Test
    fun `isVerticalDrag detects vertical gesture`() {
        // dy=30, dx=5 → vertical drag
        assert(KeyboardResizeUtil.isVerticalDrag(5f, 30f, 10f))
    }

    @Test
    fun `isVerticalDrag rejects horizontal gesture`() {
        // dy=5, dx=30 → horizontal
        assert(!KeyboardResizeUtil.isVerticalDrag(30f, 5f, 10f))
    }

    @Test
    fun `isVerticalDrag rejects below threshold`() {
        // dy=5, dx=2 → below threshold
        assert(!KeyboardResizeUtil.isVerticalDrag(2f, 5f, 10f))
    }
}
