// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.dogakdogak

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PolicyDisclosureTest {

    @Test
    fun onboardingDisclosure_compactState_showsCondensedDetailsAndAcceptButton() {
        val content = buildRankingDisclosureContent(
            isAccepted = false,
            compact = true,
            collapseAfterAccept = true,
        )

        assertEquals("랭킹 참여 전 안내", content.title)
        assertEquals(3, content.detailsLines.size)
        assertTrue(content.showDetails)
        assertTrue(content.showAcceptButton)
        assertFalse(content.showAcceptedBanner)
    }

    @Test
    fun onboardingDisclosure_afterAccept_collapsesToOneLineSummary() {
        val content = buildRankingDisclosureContent(
            isAccepted = true,
            compact = true,
            collapseAfterAccept = true,
        )

        assertEquals("랭킹 데이터 동의 완료", content.title)
        assertFalse(content.showDetails)
        assertFalse(content.showAcceptButton)
        assertFalse(content.showAcceptedBanner)
    }

    @Test
    fun rankingDisclosureModalContent_buildsThreeStructuredSections() {
        val content = buildRankingDisclosureModalContent()

        assertEquals("랭킹 데이터 안내", content.title)
        assertEquals("입력한 텍스트는 저장하지 않아요", content.highlight)
        assertEquals(3, content.sections.size)
        assertEquals("저장하지 않아요", content.sections[0].title)
        assertEquals("동기화돼요", content.sections[1].title)
        assertEquals("프로필에 보여요", content.sections[2].title)
    }
}
