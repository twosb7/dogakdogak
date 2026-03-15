package helium314.keyboard.latin.dogakdogak

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Image
import helium314.keyboard.latin.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InAppUpdateSheet(
    isStartingUpdate: Boolean,
    onUpdateNow: () -> Unit,
    onLater: () -> Unit
) {
    val colors = LocalDogakdogakColors.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = {},
        sheetState = sheetState,
        containerColor = colors.surface,
        contentColor = colors.textPrimary,
        tonalElevation = 0.dp,
        shape = RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp),
        dragHandle = null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 20.dp, vertical = 10.dp)
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .size(width = 42.dp, height = 4.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(colors.cardBorder)
            )
            Spacer(Modifier.height(18.dp))
            Text(
                text = if (isStartingUpdate) "업데이트 준비 중이에요" else "업데이트가 있습니다",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = colors.textPrimary
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = if (isStartingUpdate) {
                    "곧 다운로드가 시작돼요."
                } else {
                    "3초 만에 설치 해볼까요?"
                },
                fontSize = 14.sp,
                lineHeight = 20.sp,
                color = colors.textSecondary
            )
            if (isStartingUpdate) {
                Spacer(Modifier.height(16.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.5.dp,
                        color = colors.primary
                    )
                    Text(
                        text = "업데이트 다운로드를 준비하는 중...",
                        fontSize = 13.sp,
                        color = colors.textPrimary,
                        fontWeight = FontWeight.Medium
                    )
                }
                Spacer(Modifier.height(18.dp))
            } else {
                Spacer(Modifier.height(18.dp))
                Button(
                    onClick = onUpdateNow,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.primary,
                        contentColor = colors.onPrimary
                    ),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text("업데이트 할게요", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = onLater,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.textSecondary),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text("다음에 할게요", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                }
                Spacer(Modifier.height(2.dp))
            }
        }
    }
}

@Composable
fun InAppUpdateDownloadedBanner(
    onRestart: () -> Unit,
) {
    val colors = LocalDogakdogakColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surface.copy(alpha = 0.98f))
            .border(1.dp, colors.cardBorder)
            .padding(horizontal = 18.dp, vertical = 16.dp)
            .navigationBarsPadding(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "업데이트가 다운로드되었습니다.",
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = colors.textPrimary
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "재시작하면 새 버전으로 바로 적용돼요.",
                fontSize = 12.sp,
                lineHeight = 17.sp,
                color = colors.textSecondary
            )
        }
        Spacer(Modifier.size(12.dp))
        Button(
            onClick = onRestart,
            colors = ButtonDefaults.buttonColors(
                containerColor = colors.primary,
                contentColor = colors.onPrimary
            ),
            shape = RoundedCornerShape(14.dp)
        ) {
            Text("재시작", fontWeight = FontWeight.Bold)
        }
    }
}
