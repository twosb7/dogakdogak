package helium314.keyboard.latin.dogakdogak

import android.content.SharedPreferences
import java.time.LocalDate

class InAppUpdatePolicy(
    private val prefs: SharedPreferences,
    private val todayProvider: () -> LocalDate = { LocalDate.now() }
) {
    fun shouldPrompt(availableVersionCode: Int): Boolean {
        if (availableVersionCode <= 0) return false

        val permanentlySuppressedVersion = getPermanentlySuppressedVersion()
        if (availableVersionCode <= permanentlySuppressedVersion) return false

        val lastDeferredVersion = prefs.getInt(PrefsKeys.IN_APP_UPDATE_DEFERRED_VERSION, NO_VERSION)
        val lastDeferredDate = prefs.getString(PrefsKeys.IN_APP_UPDATE_DEFERRED_DATE, null)
        val today = todayProvider().toString()

        return !(lastDeferredVersion == availableVersionCode && lastDeferredDate == today)
    }

    fun recordDefer(availableVersionCode: Int) {
        if (availableVersionCode <= 0) return

        val today = todayProvider().toString()
        val lastDeferredVersion = prefs.getInt(PrefsKeys.IN_APP_UPDATE_DEFERRED_VERSION, NO_VERSION)
        val lastDeferredDate = prefs.getString(PrefsKeys.IN_APP_UPDATE_DEFERRED_DATE, null)

        prefs.edit().apply {
            if (lastDeferredVersion == availableVersionCode && lastDeferredDate != null && lastDeferredDate != today) {
                putInt(PrefsKeys.IN_APP_UPDATE_SUPPRESSED_VERSION, availableVersionCode)
            }
            putInt(PrefsKeys.IN_APP_UPDATE_DEFERRED_VERSION, availableVersionCode)
            putString(PrefsKeys.IN_APP_UPDATE_DEFERRED_DATE, today)
        }.apply()
    }

    fun getPermanentlySuppressedVersion(): Int {
        return prefs.getInt(PrefsKeys.IN_APP_UPDATE_SUPPRESSED_VERSION, NO_VERSION)
    }

    companion object {
        private const val NO_VERSION = -1
    }
}

enum class InAppUpdateAction {
    None,
    ShowPrompt,
    ResumeImmediateUpdate
}

fun resolveInAppUpdateAction(
    availableVersionCode: Int,
    isImmediateUpdateAllowed: Boolean,
    isUpdateAvailable: Boolean,
    isImmediateUpdateInProgress: Boolean,
    shouldPrompt: Boolean
): InAppUpdateAction {
    if (isImmediateUpdateInProgress) return InAppUpdateAction.ResumeImmediateUpdate
    if (!isUpdateAvailable || availableVersionCode <= 0) return InAppUpdateAction.None
    if (!isImmediateUpdateAllowed) return InAppUpdateAction.None
    if (!shouldPrompt) return InAppUpdateAction.None
    return InAppUpdateAction.ShowPrompt
}
