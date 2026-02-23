package helium314.keyboard.latin

import org.junit.Assert.assertEquals
import org.junit.Test

class KeyboardResizeUtilTest {

    @Test
    fun `step up increases scale`() {
        val result = KeyboardResizeUtil.stepScale(1.0f, 0.1f)
        assertEquals(1.1f, result, 0.001f)
    }

    @Test
    fun `step down decreases scale`() {
        val result = KeyboardResizeUtil.stepScale(1.0f, -0.1f)
        assertEquals(0.9f, result, 0.001f)
    }

    @Test
    fun `scale clamped to minimum 0_3`() {
        val result = KeyboardResizeUtil.stepScale(0.3f, -0.1f)
        assertEquals(0.3f, result, 0.001f)
    }

    @Test
    fun `scale clamped to maximum 1_5`() {
        val result = KeyboardResizeUtil.stepScale(1.5f, 0.1f)
        assertEquals(1.5f, result, 0.001f)
    }

    @Test
    fun `zero delta returns original scale`() {
        val result = KeyboardResizeUtil.stepScale(1.2f, 0.0f)
        assertEquals(1.2f, result, 0.001f)
    }

    @Test
    fun `clamp works near boundary`() {
        val result = KeyboardResizeUtil.stepScale(0.35f, -0.1f)
        assertEquals(0.3f, result, 0.001f)
    }
}
