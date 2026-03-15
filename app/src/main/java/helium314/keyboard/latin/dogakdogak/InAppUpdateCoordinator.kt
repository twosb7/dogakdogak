package helium314.keyboard.latin.dogakdogak

import android.content.Context
import android.util.Log
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import com.google.android.play.core.appupdate.AppUpdateInfo
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.InstallStateUpdatedListener
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.InstallStatus
import com.google.android.play.core.install.model.UpdateAvailability
import com.google.android.play.core.install.model.UpdatePrecondition

data class InAppUpdateAvailability(
    val appUpdateInfo: AppUpdateInfo? = null,
    val availableVersionCode: Int = 0,
    val isUpdateAvailable: Boolean = false,
    val isFlexibleUpdateAllowed: Boolean = false,
    val installStatus: Int = InstallStatus.UNKNOWN,
    val bytesDownloaded: Long = 0L,
    val totalBytesToDownload: Long = 0L,
)

class InAppUpdateCoordinator(
    private val appUpdateManager: AppUpdateManager
) {
    private var installStateUpdatedListener: InstallStateUpdatedListener? = null

    fun checkForUpdate(onResult: (InAppUpdateAvailability) -> Unit) {
        appUpdateManager.appUpdateInfo
            .addOnSuccessListener { info ->
                val availability = info.toAvailability()
                Log.d(
                    TAG,
                    "in_app_update availability=${availability.isUpdateAvailable} " +
                        "availableVersionCode=${availability.availableVersionCode} " +
                        "updateAvailability=${info.updateAvailability()} " +
                        "installStatus=${info.installStatus()} " +
                        "flexibleAllowed=${availability.isFlexibleUpdateAllowed} " +
                        "failedPreconditions=${formatUpdatePreconditions(info.getFailedUpdatePreconditions(FLEXIBLE_OPTIONS))}"
                )
                onResult(availability)
            }
            .addOnFailureListener { error ->
                Log.w(TAG, "Failed to fetch app update info", error)
                onResult(InAppUpdateAvailability())
            }
    }

    fun startFlexibleUpdate(
        availability: InAppUpdateAvailability,
        launcher: ActivityResultLauncher<IntentSenderRequest>
    ): Boolean {
        val info = availability.appUpdateInfo ?: return false
        return runCatching {
            appUpdateManager.startUpdateFlowForResult(
                info,
                launcher,
                FLEXIBLE_OPTIONS
            )
        }.onFailure { error ->
            Log.w(TAG, "Failed to start flexible update flow", error)
        }.isSuccess
    }

    fun registerInstallStateListener(onUpdate: (InAppUpdateAvailability) -> Unit) {
        if (installStateUpdatedListener != null) return
        installStateUpdatedListener = InstallStateUpdatedListener { state ->
            val availability = InAppUpdateAvailability(
                installStatus = state.installStatus(),
                bytesDownloaded = state.bytesDownloaded(),
                totalBytesToDownload = state.totalBytesToDownload(),
            )
            Log.d(
                TAG,
                "in_app_update installState=${state.installStatus()} " +
                    "bytesDownloaded=${state.bytesDownloaded()} " +
                    "totalBytesToDownload=${state.totalBytesToDownload()}"
            )
            onUpdate(availability)
        }
        appUpdateManager.registerListener(requireNotNull(installStateUpdatedListener))
    }

    fun unregisterInstallStateListener() {
        installStateUpdatedListener?.let(appUpdateManager::unregisterListener)
        installStateUpdatedListener = null
    }

    fun completeUpdate() {
        appUpdateManager.completeUpdate()
            .addOnFailureListener { error ->
                Log.w(TAG, "Failed to complete flexible update", error)
            }
    }

    private fun AppUpdateInfo.toAvailability(): InAppUpdateAvailability {
        val availability = updateAvailability()
        return InAppUpdateAvailability(
            appUpdateInfo = this,
            availableVersionCode = availableVersionCode(),
            isUpdateAvailable = availability == UpdateAvailability.UPDATE_AVAILABLE,
            isFlexibleUpdateAllowed = isUpdateTypeAllowed(AppUpdateType.FLEXIBLE),
            installStatus = installStatus(),
            bytesDownloaded = bytesDownloaded(),
            totalBytesToDownload = totalBytesToDownload(),
        )
    }

    companion object {
        private const val TAG = "InAppUpdateCoordinator"
        private val FLEXIBLE_OPTIONS = AppUpdateOptions.defaultOptions(AppUpdateType.FLEXIBLE)

        fun create(context: Context): InAppUpdateCoordinator {
            return InAppUpdateCoordinator(AppUpdateManagerFactory.create(context))
        }
    }
}

internal fun formatUpdatePreconditions(preconditions: Set<Int>): List<String> {
    return preconditions.toList().sorted().map { code ->
        when (code) {
            UpdatePrecondition.UNKNOWN -> "UNKNOWN"
            UpdatePrecondition.CANNOT_DISPLAY -> "CANNOT_DISPLAY"
            UpdatePrecondition.NEED_STORE_TO_PROCEED -> "NEED_STORE_TO_PROCEED"
            UpdatePrecondition.INSUFFICIENT_STORAGE -> "INSUFFICIENT_STORAGE"
            UpdatePrecondition.DEVICE_STATUS -> "DEVICE_STATUS"
            UpdatePrecondition.APP_VERSION_FRESH -> "APP_VERSION_FRESH"
            else -> "UNKNOWN_$code"
        }
    }
}
