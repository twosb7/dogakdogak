package helium314.keyboard.latin.dogakdogak

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
        finishWithoutAnimation()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        overridePendingTransition(0, 0)
        super.onCreate(savedInstanceState)

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            == PackageManager.PERMISSION_GRANTED
        ) {
            finishWithoutAnimation()
            return
        }

        val prefs = DeviceProtectedUtils.getSharedPreferences(this)
        val themeType = prefs.getString(PrefsKeys.THEME, AppThemeType.MAISON.name)
            ?.let {
                runCatching { AppThemeType.valueOf(it) }.getOrDefault(AppThemeType.MAISON)
            }
            ?: AppThemeType.MAISON

        setContent {
            DogakdogakTheme(themeType = themeType) {
                VoiceInputPermissionScreen(
                    onContinue = { requestPermission.launch(Manifest.permission.RECORD_AUDIO) },
                    onCancel = { finishWithoutAnimation() },
                    onOpenPrivacyPolicy = { openExternalUrl(this, PolicyLinks.PRIVACY_POLICY_URL) }
                )
            }
        }
    }

    private fun finishWithoutAnimation() {
        finish()
        overridePendingTransition(0, 0)
    }

    companion object {
        fun createIntent(context: Context): Intent {
            return Intent(context, VoiceInputPermissionActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
            }
        }
    }
}

@Composable
private fun VoiceInputPermissionScreen(
    onContinue: () -> Unit,
    onCancel: () -> Unit,
    onOpenPrivacyPolicy: () -> Unit,
) {
    val colors = LocalDogakdogakColors.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.42f))
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        GlassCard {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    "마이크 권한 안내",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.textPrimary
                )
                Text(
                    "음성 입력을 시작하려면 마이크 권한이 필요합니다.",
                    color = colors.textSecondary,
                    lineHeight = 20.sp
                )
                VoiceDisclosureLine("권한은 사용자가 음성 입력을 눌렀을 때만 요청됩니다.")
                VoiceDisclosureLine("오디오 원본은 도각도각 서버로 업로드하지 않습니다.")
                VoiceDisclosureLine("음성 인식은 운영체제 또는 사용 중인 음성 서비스에서 처리될 수 있습니다.")
                TextButton(
                    onClick = onOpenPrivacyPolicy,
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text("개인정보 처리방침")
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TextButton(
                        onClick = onCancel,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("나중에", color = colors.textSecondary)
                    }
                    Button(
                        onClick = onContinue,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colors.primary,
                            contentColor = colors.onPrimary
                        )
                    ) {
                        Text("권한 요청", fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

@Composable
private fun VoiceDisclosureLine(text: String) {
    val colors = LocalDogakdogakColors.current
    Row(verticalAlignment = Alignment.Top) {
        Text("•", color = colors.primary, fontWeight = FontWeight.Bold)
        Spacer(Modifier.width(6.dp))
        Text(
            text = text,
            color = colors.textSecondary,
            fontSize = 13.sp,
            lineHeight = 19.sp
        )
    }
}
