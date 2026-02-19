// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.settings

import android.content.Context
import androidx.core.content.edit
import androidx.test.core.app.ApplicationProvider
import helium314.keyboard.ShadowLocaleManagerCompat
import helium314.keyboard.keyboard.KeyboardTheme
import helium314.keyboard.latin.utils.SubtypeSettings
import helium314.keyboard.latin.utils.ToolbarMode
import helium314.keyboard.latin.utils.locale
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(shadows = [ShadowLocaleManagerCompat::class])
class SettingsHardeningTest {
    @Test
    fun readToolbarMode_recoversFromInvalidValue() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = context.getSharedPreferences("settings_hardening_test", Context.MODE_PRIVATE)
        prefs.edit { putString(Settings.PREF_TOOLBAR_MODE, "BROKEN_TOOLBAR_MODE") }

        val mode = Settings.readToolbarMode(prefs)

        assertEquals(ToolbarMode.valueOf(Defaults.PREF_TOOLBAR_MODE), mode)
        assertEquals(Defaults.PREF_TOOLBAR_MODE, prefs.getString(Settings.PREF_TOOLBAR_MODE, null))
    }

    @Test
    fun readUserColors_recoversFromCorruptedJson() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = context.getSharedPreferences("settings_hardening_test", Context.MODE_PRIVATE)
        val themeName = "BrokenTheme"
        val key = Settings.PREF_USER_COLORS_PREFIX + themeName
        prefs.edit { putString(key, "{this is not json") }

        KeyboardTheme.readUserColors(prefs, themeName)

        assertEquals(Defaults.PREF_USER_COLORS, prefs.getString(key, null))
    }

    @Test
    fun getSelectedSubtype_recoversFromMalformedPreference() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = context.getSharedPreferences("settings_hardening_test", Context.MODE_PRIVATE)
        SubtypeSettings.init(context)
        prefs.edit { putString(Settings.PREF_SELECTED_SUBTYPE, "") }

        val subtype = SubtypeSettings.getSelectedSubtype(prefs)

        assertTrue(subtype.locale().toLanguageTag().isNotBlank())
        assertTrue(prefs.getString(Settings.PREF_SELECTED_SUBTYPE, "")!!.isNotBlank())
    }
}
