package helium314.keyboard.latin.dogakdogak

import android.content.Intent
import android.net.Uri
import helium314.keyboard.latin.BuildConfig

const val DEVELOPER_SUGGESTION_EMAIL = "dogak.sw@gmail.com"

data class DeveloperSuggestionSender(
    val email: String,
    val provider: String,
    val userId: String,
)

data class DeveloperSuggestionDraft(
    val title: String,
    val content: String,
)

fun normalizeDeveloperSuggestionProvider(provider: String?): String {
    return when (provider?.trim()?.lowercase()) {
        "google" -> "Google"
        "kakao" -> "Kakao"
        else -> "알 수 없음"
    }
}

fun buildDeveloperSuggestionEmailIntent(
    sender: DeveloperSuggestionSender,
    draft: DeveloperSuggestionDraft,
): Intent {
    val normalizedTitle = draft.title.trim()
    val normalizedContent = draft.content.trim()
    val subject = "[도각도각 건의] $normalizedTitle"
    val body = buildString {
        appendLine("로그인 이메일: ${sender.email}")
        appendLine("로그인 제공자: ${sender.provider}")
        appendLine("사용자 UID: ${sender.userId}")
        appendLine("앱 버전: ${BuildConfig.VERSION_NAME}")
        appendLine()
        appendLine("건의 제목: $normalizedTitle")
        appendLine()
        appendLine("건의 내용:")
        append(normalizedContent)
    }
    return Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:$DEVELOPER_SUGGESTION_EMAIL")).apply {
        putExtra(Intent.EXTRA_SUBJECT, subject)
        putExtra(Intent.EXTRA_TEXT, body)
    }
}
