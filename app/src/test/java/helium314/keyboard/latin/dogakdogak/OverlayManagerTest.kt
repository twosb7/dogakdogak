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
}
