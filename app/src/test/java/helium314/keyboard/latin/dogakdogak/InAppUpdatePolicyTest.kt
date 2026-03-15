package helium314.keyboard.latin.dogakdogak

import android.content.Context
import android.content.SharedPreferences
import androidx.test.core.app.ApplicationProvider
import com.google.android.play.core.install.model.InstallStatus
import helium314.keyboard.latin.App
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class InAppUpdatePolicyTest {

    private fun createPrefs(): SharedPreferences {
        val context = ApplicationProvider.getApplicationContext<App>() as Context
        return context.getSharedPreferences("test_in_app_update_policy", Context.MODE_PRIVATE).also {
            it.edit().clear().commit()
        }
    }

    @Test
    fun shouldPrompt_newVersionWithoutHistory_returnsTrue() {
        val policy = InAppUpdatePolicy(
            prefs = createPrefs(),
            todayProvider = { LocalDate.parse("2026-03-15") }
        )

        assertTrue(policy.shouldPrompt(100))
    }

    @Test
    fun shouldPrompt_sameDayAfterDefer_returnsFalse() {
        val policy = InAppUpdatePolicy(
            prefs = createPrefs(),
            todayProvider = { LocalDate.parse("2026-03-15") }
        )

        policy.recordDefer(100)

        assertFalse(policy.shouldPrompt(100))
    }

    @Test
    fun recordDefer_secondDaySameVersion_permanentlySuppressesVersion() {
        val prefs = createPrefs()
        var today = LocalDate.parse("2026-03-15")
        val policy = InAppUpdatePolicy(
            prefs = prefs,
            todayProvider = { today }
        )

        policy.recordDefer(100)
        today = LocalDate.parse("2026-03-16")
        assertTrue(policy.shouldPrompt(100))

        policy.recordDefer(100)

        assertFalse(policy.shouldPrompt(100))
        assertEquals(100, policy.getPermanentlySuppressedVersion())
    }

    @Test
    fun shouldPrompt_newerVersionAfterPermanentSuppression_returnsTrue() {
        val prefs = createPrefs()
        var today = LocalDate.parse("2026-03-15")
        val policy = InAppUpdatePolicy(
            prefs = prefs,
            todayProvider = { today }
        )

        policy.recordDefer(100)
        today = LocalDate.parse("2026-03-16")
        policy.recordDefer(100)

        assertTrue(policy.shouldPrompt(101))
    }

    @Test
    fun resolveAction_whenFlexibleUpdateDownloaded_showsRestartBanner() {
        val decision = resolveInAppUpdateAction(
            availableVersionCode = 100,
            isFlexibleUpdateAllowed = true,
            isUpdateAvailable = true,
            installStatus = InstallStatus.DOWNLOADED,
            shouldPrompt = true
        )

        assertEquals(InAppUpdateAction.ShowDownloadedReady, decision)
    }

    @Test
    fun resolveAction_whenPromptEligible_returnsShowPrompt() {
        val decision = resolveInAppUpdateAction(
            availableVersionCode = 100,
            isFlexibleUpdateAllowed = true,
            isUpdateAvailable = true,
            installStatus = InstallStatus.PENDING,
            shouldPrompt = true
        )

        assertEquals(InAppUpdateAction.ShowPrompt, decision)
    }

    @Test
    fun resolveAction_whenPolicySuppressesVersion_returnsNone() {
        val decision = resolveInAppUpdateAction(
            availableVersionCode = 100,
            isFlexibleUpdateAllowed = true,
            isUpdateAvailable = true,
            installStatus = InstallStatus.UNKNOWN,
            shouldPrompt = false
        )

        assertEquals(InAppUpdateAction.None, decision)
    }
}
