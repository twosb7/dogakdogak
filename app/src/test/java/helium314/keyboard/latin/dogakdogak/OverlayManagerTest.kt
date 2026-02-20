// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.dogakdogak

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class OverlayManagerTest {
    private lateinit var manager: OverlayManager

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = context.getSharedPreferences("overlay_manager_test", Context.MODE_PRIVATE)
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
}
