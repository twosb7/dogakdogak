package helium314.keyboard.latin.dogakdogak

import android.content.Intent
import android.net.Uri

const val DEVELOPER_SUGGESTION_EMAIL = "dogak.sw@gmail.com"

fun developerSuggestionRewardDescription(): String =
    "건의 내용이 반영되면 유료 기능 1개를 드려요."

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
    val mailtoUri = Uri.parse(
        "mailto:$DEVELOPER_SUGGESTION_EMAIL" +
            "?subject=${Uri.encode(subject)}" +
            "&body=${Uri.encode(normalizedContent)}"
    )
    return Intent(Intent.ACTION_SENDTO, mailtoUri)
}
