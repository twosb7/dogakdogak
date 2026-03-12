package helium314.keyboard.latin.dogakdogak

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object DogakdogakCompat {
    @JvmStatic
    fun canDrawOverlays(context: Context): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(context)
    }
}

object PolicyLinks {
    const val PRIVACY_POLICY_URL = "https://twosb7.github.io/dogakdogak/privacy-policy.html"
    const val ACCOUNT_DELETION_URL = "https://twosb7.github.io/dogakdogak/delete-account.html"
}

fun hasRankingDisclosureConsent(prefs: SharedPreferences): Boolean {
    return prefs.getBoolean(PrefsKeys.RANKING_DISCLOSURE_ACCEPTED, false)
}

fun acceptRankingDisclosure(prefs: SharedPreferences) {
    prefs.edit().putBoolean(PrefsKeys.RANKING_DISCLOSURE_ACCEPTED, true).apply()
}

fun openExternalUrl(context: Context, url: String) {
    runCatching {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}

internal data class RankingDisclosureContent(
    val title: String,
    val detailsLines: List<String>,
    val showDetails: Boolean,
    val showAcceptButton: Boolean,
    val showAcceptedBanner: Boolean,
)

internal fun buildRankingDisclosureContent(
    isAccepted: Boolean,
    compact: Boolean,
    collapseAfterAccept: Boolean,
): RankingDisclosureContent {
    val detailsLines = if (compact) {
        listOf(
            "입력한 텍스트 내용은 저장하거나 전송하지 않습니다.",
            "로그인 후 랭킹에 참여하면 총 점수/터치 수가 서버에 동기화됩니다.",
            "동의 후에는 앱별 타이핑·터치 통계도 랭킹용으로 동기화됩니다.",
        )
    } else {
        listOf(
            "입력한 텍스트 내용은 저장하거나 전송하지 않습니다.",
            "로그인 후 랭킹에 참여하면 총 점수/터치 수가 서버에 동기화됩니다.",
            "동의 후에는 어떤 앱에서 몇 번 타이핑·터치했는지 앱별 통계도 랭킹용으로 동기화됩니다.",
            "닉네임/아바타는 랭킹 표시용이며, 마이크·연락처는 각 기능을 켠 경우에만 사용됩니다.",
        )
    }
    val collapsed = isAccepted && collapseAfterAccept
    return RankingDisclosureContent(
        title = if (isAccepted) "랭킹 데이터 동의 완료" else "랭킹 참여 전 안내",
        detailsLines = detailsLines,
        showDetails = !collapsed,
        showAcceptButton = !isAccepted,
        showAcceptedBanner = isAccepted && !collapseAfterAccept,
    )
}

private fun fullDisclosureLines(): List<String> = buildRankingDisclosureContent(
    isAccepted = false,
    compact = false,
    collapseAfterAccept = false,
).detailsLines

@Composable
internal fun RankingDisclosureCard(
    isAccepted: Boolean,
    onAccept: () -> Unit,
    compact: Boolean = false,
    collapseAfterAccept: Boolean = false,
    detailsMaxHeight: Dp? = null,
    modifier: Modifier = Modifier,
) {
    val colors = LocalDogakdogakColors.current
    val content = buildRankingDisclosureContent(
        isAccepted = isAccepted,
        compact = compact,
        collapseAfterAccept = collapseAfterAccept,
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.surface.copy(alpha = 0.78f), RoundedCornerShape(16.dp))
            .border(1.dp, colors.cardBorder, RoundedCornerShape(16.dp))
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = if (isAccepted) Icons.Default.Check else Icons.Default.Info,
                contentDescription = null,
                tint = if (isAccepted) colors.success else colors.primary
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = content.title,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = colors.textPrimary
            )
        }
        if (content.showDetails) {
            Spacer(Modifier.height(10.dp))
            val detailsModifier = if (detailsMaxHeight != null) {
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = detailsMaxHeight)
                    .verticalScroll(rememberScrollState())
            } else {
                Modifier.fillMaxWidth()
            }
            Box(modifier = detailsModifier) {
                RankingDisclosureDetails(lines = content.detailsLines)
            }
        }
        if (content.showAcceptButton) {
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = onAccept,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.primary,
                    contentColor = colors.onPrimary
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("동의하기", fontWeight = FontWeight.SemiBold)
            }
        } else if (content.showAcceptedBanner) {
            Spacer(Modifier.height(4.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.success.copy(alpha = 0.12f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(Icons.Default.Check, contentDescription = null, tint = colors.success)
                Spacer(Modifier.width(8.dp))
                Text(
                    "앱별 랭킹 데이터 동의가 저장되었습니다.",
                    color = colors.success,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
internal fun RankingDisclosureSummaryCard(
    modifier: Modifier = Modifier,
) {
    val colors = LocalDogakdogakColors.current
    var expanded by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.surface.copy(alpha = 0.72f), RoundedCornerShape(16.dp))
            .border(1.dp, colors.cardBorder, RoundedCornerShape(16.dp))
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                tint = colors.success
            )
            Spacer(Modifier.width(8.dp))
            Column {
                Text(
                    text = "랭킹 데이터 동의 완료",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.textPrimary
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "입력한 텍스트는 저장하지 않고, 랭킹 기능에 필요한 점수/터치 수와 동의한 앱별 통계만 동기화합니다.",
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                    color = colors.textSecondary
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        TextButton(onClick = { expanded = !expanded }) {
            Text(if (expanded) "간단히 보기" else "세부 안내 보기")
        }
        AnimatedVisibility(visible = expanded) {
            Column {
                RankingDisclosureDetails()
            }
        }
    }
}

@Composable
internal fun RankingDisclosureDetails(lines: List<String>) {
    val context = LocalContext.current

    Column(modifier = Modifier.fillMaxWidth()) {
        lines.forEach { DisclosureLine(it) }
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            TextButton(onClick = { openExternalUrl(context, PolicyLinks.PRIVACY_POLICY_URL) }) {
                Text("개인정보 처리방침")
            }
            TextButton(onClick = { openExternalUrl(context, PolicyLinks.ACCOUNT_DELETION_URL) }) {
                Text("삭제 안내")
            }
        }
    }
}

@Composable
internal fun RankingDisclosureDetails() {
    RankingDisclosureDetails(lines = fullDisclosureLines())
}

@Composable
private fun DisclosureLine(text: String) {
    val colors = LocalDogakdogakColors.current
    Row(verticalAlignment = Alignment.Top) {
        Text("•", color = colors.primary, fontWeight = FontWeight.Bold)
        Spacer(Modifier.width(6.dp))
        Text(
            text = text,
            color = colors.textSecondary,
            fontSize = 12.sp,
            lineHeight = 18.sp
        )
    }
}
