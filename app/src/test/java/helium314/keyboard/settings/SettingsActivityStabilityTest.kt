// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.settings

import helium314.keyboard.ShadowLocaleManagerCompat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(shadows = [ShadowLocaleManagerCompat::class])
class SettingsActivityStabilityTest {
    @Test
    fun launch_doesNotCrashWithCurrentBuildConfig() {
        val launchResult = runCatching {
            Robolectric.buildActivity(SettingsActivity::class.java).setup()
        }

        assertTrue(
            launchResult.isSuccess,
            launchResult.exceptionOrNull()?.stackTraceToString() ?: "SettingsActivity launch failed"
        )
    }
}
