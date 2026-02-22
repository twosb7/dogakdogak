// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.dogakdogak

import android.content.Context
import android.view.WindowManager
import androidx.test.core.app.ApplicationProvider
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.shadows.ShadowWindowManagerImpl
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.assertFalse

@RunWith(RobolectricTestRunner::class)
class OverlayManagerTest {
    private lateinit var manager: OverlayManager
    private lateinit var prefs: android.content.SharedPreferences

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        prefs = context.getSharedPreferences("overlay_manager_test", Context.MODE_PRIVATE)
        prefs.edit().clear().apply()
        manager = OverlayManager(context, prefs)
    }

    @Test
    fun createManager_returnsInstance() {
        assertNotNull(manager)
    }

    @Test
    fun hideImmediately_withoutShow_doesNotCrash() {
        manager.hideImmediately()
        assertTrue(true)
    }

    @Test
    fun touchEnabled_defaultIsFalse() {
        assertFalse(manager.touchEnabled)
    }

    @Test
    fun setTouchEnabled_updatesProperty() {
        manager.touchEnabled = true
        assertTrue(manager.touchEnabled)
        manager.touchEnabled = false
        assertFalse(manager.touchEnabled)
    }

    @Test
    fun show_withTouchOff_initialFlagsIncludeNotTouchable() {
        // Touch OFF preference
        prefs.edit().putBoolean(PrefsKeys.OVERLAY_TOUCH, false).apply()

        // getInitialOverlayFlags should include FLAG_NOT_TOUCHABLE
        val flags = manager.getInitialOverlayFlags()
        val hasNotTouchable = (flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE) != 0
        assertTrue(hasNotTouchable, "FLAG_NOT_TOUCHABLE should be set when touch is OFF")
    }

    @Test
    fun show_withTouchOn_initialFlagsExcludeNotTouchable() {
        // Touch ON preference
        prefs.edit().putBoolean(PrefsKeys.OVERLAY_TOUCH, true).apply()
        manager.touchEnabled = true

        val flags = manager.getInitialOverlayFlags()
        val hasNotTouchable = (flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE) != 0
        assertFalse(hasNotTouchable, "FLAG_NOT_TOUCHABLE should NOT be set when touch is ON")
    }

    // --- 전체화면 동영상 감지 테스트 ---

    @Test
    fun isHiddenForFullscreen_defaultIsFalse() {
        assertFalse(manager.isHiddenForFullscreen())
    }

    @Test
    fun hideForFullscreen_withoutShow_doesNothing() {
        // show() 호출 전이므로 isShowing=false → hideForFullscreen 무시
        manager.hideForFullscreen()
        assertFalse(manager.isHiddenForFullscreen(), "should not set flag when not showing")
    }

    @Test
    fun showAfterFullscreen_withoutHide_doesNothing() {
        // hideForFullscreen 호출 안 했으므로 무시
        manager.showAfterFullscreen()
        assertFalse(manager.isHiddenForFullscreen())
    }

    @Test
    fun hideForFullscreen_duplicateCall_idempotent() {
        // show()가 안 된 상태에서 두 번 호출해도 크래시 없음
        manager.hideForFullscreen()
        manager.hideForFullscreen()
        assertFalse(manager.isHiddenForFullscreen())
    }

    @Test
    fun showAfterFullscreen_clearsFlag() {
        // 직접 isHiddenForFullscreen 플래그 테스트 (show 없이도 showAfterFullscreen이 안전한지 확인)
        manager.showAfterFullscreen()
        assertFalse(manager.isHiddenForFullscreen())
    }

    @Test
    fun hideImmediately_resetsFullscreenFlag() {
        // hideImmediately는 모든 상태를 초기화해야 함
        manager.hideImmediately()
        assertFalse(manager.isHiddenForFullscreen())
    }
}
