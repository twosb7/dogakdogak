package helium314.keyboard.latin.dogakdogak

import android.content.Context
import android.util.Log
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import com.google.android.play.core.appupdate.AppUpdateInfo
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.UpdateAvailability

data class InAppUpdateAvailability(
    val appUpdateInfo: AppUpdateInfo? = null,
    val availableVersionCode: Int = 0,
    val isUpdateAvailable: Boolean = false,
    val isImmediateUpdateAllowed: Boolean = false,
    val isImmediateUpdateInProgress: Boolean = false
)

class InAppUpdateCoordinator(
    private val appUpdateManager: AppUpdateManager
) {
    fun checkForUpdate(onResult: (InAppUpdateAvailability) -> Unit) {
        appUpdateManager.appUpdateInfo
            .addOnSuccessListener { info ->
                onResult(info.toAvailability())
            }
            .addOnFailureListener { error ->
                Log.w(TAG, "Failed to fetch app update info", error)
                onResult(InAppUpdateAvailability())
            }
    }

    fun startImmediateUpdate(
        availability: InAppUpdateAvailability,
        launcher: ActivityResultLauncher<IntentSenderRequest>
    ): Boolean {
        val info = availability.appUpdateInfo ?: return false
        return runCatching {
            appUpdateManager.startUpdateFlowForResult(
                info,
                launcher,
                AppUpdateOptions.defaultOptions(AppUpdateType.IMMEDIATE)
            )
        }.onFailure { error ->
            Log.w(TAG, "Failed to start immediate update flow", error)
        }.isSuccess
    }

    private fun AppUpdateInfo.toAvailability(): InAppUpdateAvailability {
        val availability = updateAvailability()
        return InAppUpdateAvailability(
            appUpdateInfo = this,
            availableVersionCode = availableVersionCode(),
            isUpdateAvailable = availability == UpdateAvailability.UPDATE_AVAILABLE,
            isImmediateUpdateAllowed = isUpdateTypeAllowed(AppUpdateType.IMMEDIATE),
            isImmediateUpdateInProgress = availability == UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS
        )
    }

    companion object {
        private const val TAG = "InAppUpdateCoordinator"

        fun create(context: Context): InAppUpdateCoordinator {
            return InAppUpdateCoordinator(AppUpdateManagerFactory.create(context))
        }
    }
}
