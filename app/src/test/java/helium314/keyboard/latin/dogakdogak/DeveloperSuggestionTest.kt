package helium314.keyboard.latin.dogakdogak

import android.content.Intent
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class DeveloperSuggestionTest {
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
        assertEquals("mailto:${DEVELOPER_SUGGESTION_EMAIL}", intent.dataString)
        assertEquals(
            "[도각도각 건의] 이펙트 속도 조절",
            intent.getStringExtra(Intent.EXTRA_SUBJECT)
        )
        val body = intent.getStringExtra(Intent.EXTRA_TEXT).orEmpty()
        assertTrue(body.contains("로그인 이메일: user@example.com"))
        assertTrue(body.contains("로그인 제공자: Google"))
        assertTrue(body.contains("사용자 UID: user-123"))
        assertTrue(body.contains("앱 버전: 1.1.3"))
        assertTrue(body.contains("건의 제목: 이펙트 속도 조절"))
        assertTrue(body.contains("건의 내용:\n콤보 이펙트 속도를 더 느리게 선택할 수 있으면 좋겠어요."))
    }
}
