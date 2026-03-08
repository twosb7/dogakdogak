package helium314.keyboard.latin.dogakdogak

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class AppClickCountRepositoryTest {

    @Test
    fun defaultTrackedApps_matchRequestedOrder() {
        val packageNames = AppClickCountRepository.getTrackedApps().map { it.packageName }

        assertContentEquals(
            listOf(
                "com.kakao.talk",
                "com.instagram.android",
                "com.facebook.katana",
                "com.twitter.android",
                "com.instagram.barcelona",
                "com.everytime.v2",
                "com.dcinside.app.android",
                "com.teamblind.blind",
                "com.nhn.android.search",
                "com.nhn.android.navercafe",
                "com.nhn.android.band",
                "com.google.android.youtube",
                "com.discord",
                "com.openai.chatgpt",
                "com.google.android.apps.bard",
                "com.Slack",
                "notion.id",
                "com.google.android.gm",
                "org.telegram.messenger",
                "kr.co.nowcom.mobile.afreeca",
                "com.navercorp.game.android.community",
                "com.android.chrome",
                "com.sec.android.app.sbrowser"
            ),
            packageNames
        )
    }

    @Test
    fun resolveTrackedAppsOrder_dropsRemovedAndAppendsMissingDefaults() {
        val ordered = AppClickCountRepository.resolveTrackedAppsOrder(
            listOf(
                "com.android.chrome",
                "com.kakao.talk",
                "com.mobile.app.clien",
                "com.android.chrome",
                "missing.package"
            )
        )

        assertEquals("com.android.chrome", ordered[0].packageName)
        assertEquals("com.kakao.talk", ordered[1].packageName)
        assertEquals(
            AppClickCountRepository.DEFAULT_TRACKED_APPS.size,
            ordered.map { it.packageName }.distinct().size
        )
        assertEquals(
            AppClickCountRepository.DEFAULT_TRACKED_APPS.map { it.packageName }.toSet(),
            ordered.map { it.packageName }.toSet()
        )
    }

    @Test
    fun filterAppRankingEntries_hidesCurrentUserAndReindexes() {
        val entries = listOf(
            RankingEntry(rank = 1, userId = "u1", displayName = "하나", avatarUrl = null, clickCount = 300),
            RankingEntry(rank = 2, userId = "me", displayName = "나", avatarUrl = null, clickCount = 250),
            RankingEntry(rank = 3, userId = "u3", displayName = "셋", avatarUrl = null, clickCount = 200)
        )

        val filtered = AppClickCountRepository.filterAppRankingEntries(
            entries = entries,
            currentUserId = "me",
            hideSelfEnabled = true
        )

        assertContentEquals(listOf("u1", "u3"), filtered.map { it.userId })
        assertContentEquals(listOf(1L, 2L), filtered.map { it.rank })
    }

    @Test
    fun filterAppRankingEntries_keepsOriginalWhenNoCurrentUser() {
        val entries = listOf(
            RankingEntry(rank = 1, userId = "u1", displayName = "하나", avatarUrl = null, clickCount = 300),
            RankingEntry(rank = 2, userId = "u2", displayName = "둘", avatarUrl = null, clickCount = 250)
        )

        val filtered = AppClickCountRepository.filterAppRankingEntries(
            entries = entries,
            currentUserId = null,
            hideSelfEnabled = true
        )

        assertEquals(entries, filtered)
    }
}
