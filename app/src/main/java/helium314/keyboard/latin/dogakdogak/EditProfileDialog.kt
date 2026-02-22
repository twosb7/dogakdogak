package helium314.keyboard.latin.dogakdogak

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun EditProfileDialog(
    rankingRepository: RankingRepository,
    currentDisplayName: String,
    currentAvatarUrl: String?,
    onDismiss: () -> Unit,
    onSaved: (newName: String, newAvatarUrl: String?) -> Unit
) {
    val colors = LocalDogakdogakColors.current
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    var nickname by remember { mutableStateOf(currentDisplayName) }
    var avatarUrl by remember { mutableStateOf(currentAvatarUrl) }
    var isSaving by remember { mutableStateOf(false) }
    var isUploading by remember { mutableStateOf(false) }
    var newAvatarUrl by remember { mutableStateOf<String?>(null) }
    var dialogToast by remember { mutableStateOf<String?>(null) }

    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        isUploading = true
        scope.launch {
            try {
                val compressed = compressAvatar(context, uri)
                if (compressed != null) {
                    val uploadedUrl = rankingRepository.uploadAvatar(compressed)
                    if (uploadedUrl != null) {
                        newAvatarUrl = uploadedUrl
                        avatarUrl = uploadedUrl
                        dialogToast = "이미지 업로드 완료"
                    } else {
                        dialogToast = "이미지 업로드에 실패했어요"
                    }
                } else {
                    dialogToast = "이미지를 불러올 수 없어요"
                }
            } catch (_: Exception) {
                dialogToast = "이미지를 불러올 수 없어요"
            }
            isUploading = false
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Box(contentAlignment = Alignment.BottomCenter) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(colors.surface)
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("프로필 수정", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = colors.textPrimary)
                Spacer(Modifier.height(20.dp))

                // 아바타
                Box(
                    modifier = Modifier.size(80.dp).clip(CircleShape).clickable { imagePicker.launch("image/*") },
                    contentAlignment = Alignment.Center
                ) {
                    if (isUploading) {
                        CircularProgressIndicator(color = colors.primary, modifier = Modifier.size(40.dp))
                    } else if (avatarUrl != null) {
                        AsyncImage(model = avatarUrl, contentDescription = null, modifier = Modifier.size(80.dp).clip(CircleShape), contentScale = ContentScale.Crop)
                    } else {
                        Box(modifier = Modifier.size(80.dp).clip(CircleShape).background(colors.primary.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
                            Text(nickname.take(1).uppercase(), fontSize = 28.sp, fontWeight = FontWeight.Bold, color = colors.primary)
                        }
                    }
                    if (!isUploading) {
                        Box(
                            modifier = Modifier.align(Alignment.BottomEnd).size(28.dp).clip(CircleShape).background(colors.primary).border(2.dp, colors.surface, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.CameraAlt, contentDescription = "사진 변경", modifier = Modifier.size(14.dp), tint = Color.White)
                        }
                    }
                }

                Spacer(Modifier.height(4.dp))
                Text("사진을 눌러서 변경할 수 있어요", fontSize = 11.sp, color = colors.textTertiary)
                Spacer(Modifier.height(20.dp))

                Text("닉네임", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = colors.textSecondary, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(6.dp))
                NoUnderlineTextField(
                    value = nickname,
                    onValueChange = { if (it.length <= 20) nickname = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, colors.glassBorder, RoundedCornerShape(12.dp))
                        .clip(RoundedCornerShape(12.dp)),
                    hint = "닉네임을 입력하세요",
                    singleLine = true,
                    textColor = colors.textPrimary,
                    hintColor = colors.textTertiary,
                )
                Spacer(Modifier.height(6.dp))
                Text("${nickname.length}/20", fontSize = 11.sp, color = colors.textTertiary, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.End)
                Spacer(Modifier.height(16.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    TextButton(onClick = onDismiss, modifier = Modifier.weight(1f)) {
                        Text("취소", color = colors.textSecondary)
                    }
                    Button(
                        onClick = {
                            if (nickname.isBlank()) { dialogToast = "닉네임을 입력해주세요"; return@Button }
                            isSaving = true
                            scope.launch {
                                val success = rankingRepository.updateProfile(displayName = nickname.trim(), avatarUrl = newAvatarUrl)
                                isSaving = false
                                if (success) onSaved(nickname.trim(), newAvatarUrl) else dialogToast = "업데이트에 실패했어요"
                            }
                        },
                        modifier = Modifier.weight(1f),
                        enabled = !isSaving && !isUploading && nickname.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(containerColor = colors.primary, contentColor = colors.onPrimary),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(if (isSaving) "저장 중..." else "저장", fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            // 다이얼로그 내부 토스트
            AnimatedVisibility(
                visible = dialogToast != null,
                modifier = Modifier.padding(bottom = 8.dp),
                enter = slideInVertically { it } + fadeIn(),
                exit = slideOutVertically { it } + fadeOut()
            ) {
                dialogToast?.let { msg ->
                    LaunchedEffect(msg) { delay(2500); dialogToast = null }
                    Text(
                        text = msg, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 12.dp).clip(RoundedCornerShape(14.dp)).background(Color(0xE6222222)).padding(horizontal = 16.dp, vertical = 10.dp)
                    )
                }
            }
        }
    }
}
