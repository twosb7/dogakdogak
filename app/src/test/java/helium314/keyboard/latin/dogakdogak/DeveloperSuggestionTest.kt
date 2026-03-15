package helium314.keyboard.latin.dogakdogak

import android.content.Intent
import android.net.MailTo
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class DeveloperSuggestionTest {
    @Test
    fun rewardDescription_matchesProductCopy() {
        assertEquals(
            "건의 내용이 반영되면 유료 기능 1개를 드려요.",
            developerSuggestionRewardDescription()
        )
    }

    @Test
    fun buildDeveloperSuggestionEmailIntent_setsRecipientSubjectAndBody() {
        val sender = DeveloperSuggestionSender(
            email = "user@example.com",
            provider = "Google",
            userId = "user-123"
        )
        val draft = DeveloperSuggestionDraft(
            title = "이펙트 속도 조절",
            content = "콤보 이펙트 속도를 더 느리게 선택할 수 있으면 좋겠어요."
        )

        val intent = buildDeveloperSuggestionEmailIntent(sender, draft)

        assertEquals(Intent.ACTION_SENDTO, intent.action)
        val data = intent.data ?: error("mailto data missing")
        val mailTo = MailTo.parse(data.toString())
        assertEquals(DEVELOPER_SUGGESTION_EMAIL, mailTo.to)
        assertEquals("[도각도각 건의] 이펙트 속도 조절", mailTo.headers["subject"])
        val body = mailTo.headers["body"].orEmpty()
        assertEquals("콤보 이펙트 속도를 더 느리게 선택할 수 있으면 좋겠어요.", body)
    }
}
