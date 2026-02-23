package helium314.keyboard.latin.dogakdogak

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import helium314.keyboard.latin.utils.DeviceProtectedUtils

class VoiceInputPermissionActivity : ComponentActivity() {

    private val requestPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            DeviceProtectedUtils.getSharedPreferences(this)
                .edit().putBoolean("dogakdogak_voice_key_main", true).apply()
            Toast.makeText(this, "마이크 권한이 허용되었습니다", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "마이크 권한이 필요합니다", Toast.LENGTH_SHORT).show()
        }
        finish()
        overridePendingTransition(0, 0)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        overridePendingTransition(0, 0)
        super.onCreate(savedInstanceState)

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            == PackageManager.PERMISSION_GRANTED
        ) {
            finish()
            overridePendingTransition(0, 0)
            return
        }

        requestPermission.launch(Manifest.permission.RECORD_AUDIO)
    }

    companion object {
        fun createIntent(context: Context): Intent {
            return Intent(context, VoiceInputPermissionActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
            }
        }
    }
}
