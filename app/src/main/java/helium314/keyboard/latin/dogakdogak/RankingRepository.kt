package helium314.keyboard.latin.dogakdogak

import android.util.Log
import io.github.jan.supabase.gotrue.auth
import io.github.jan.supabase.gotrue.SessionStatus
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.rpc
import io.github.jan.supabase.storage.storage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.jsonPrimitive

@Serializable
data class RankingEntry(
    val rank: Long,
    @SerialName("user_id") val userId: String,
    @SerialName("display_name") val displayName: String,
    @SerialName("avatar_url") val avatarUrl: String?,
    @SerialName("click_count") val clickCount: Long
)

@Serializable
data class UpsertClicksParams(
    @SerialName("p_click_count") val clickCount: Long
)

@Serializable
data class UpsertTouchesParams(
    @SerialName("p_touch_count") val touchCount: Long
)

@Serializable
data class GetRankingParams(
    @SerialName("p_period") val period: String,
    @SerialName("p_limit") val limit: Int = 50
)

@Serializable
data class ProfileRow(
    @SerialName("display_name") val displayName: String?,
    @SerialName("avatar_url") val avatarUrl: String?,
    @SerialName("click_count") val clickCount: Long? = null,
    @SerialName("touch_count") val touchCount: Long? = null
)

enum class RankingPeriod(val value: String, val displayName: String) {
    DAILY("daily", "일간"),
    WEEKLY("weekly", "주간"),
    MONTHLY("monthly", "월간"),
    ALL_TIME("alltime", "전체")
}

/**
 * Supabase 랭킹 조회 및 클릭 동기화.
 * period별 30초 TTL 캐시로 불필요한 API 호출 방지.
 */
class RankingRepository {

    private val client = SupabaseModule.client

    // Score 캐시: period → (timestamp, data)
    private val scoreCache = mutableMapOf<RankingPeriod, Pair<Long, List<RankingEntry>>>()
    // Touch 캐시: period → (timestamp, data)
    private val touchCache = mutableMapOf<RankingPeriod, Pair<Long, List<RankingEntry>>>()
    private var lastUpdateTime: Long = 0L

    val isLoggedIn: Flow<Boolean> = client.auth.sessionStatus.map { status ->
        status is SessionStatus.Authenticated
    }

    fun currentUserId(): String? {
        return client.auth.currentUserOrNull()?.id
    }

    /** 마지막 업데이트 시간 (ms) */
    fun getLastUpdateTime(): Long = lastUpdateTime

    /**
     * 랭킹 조회 (30초 TTL 캐시).
     * @param forceRefresh true면 캐시 무시하고 강제 새로고침
     */
    suspend fun getRanking(
        period: RankingPeriod,
        limit: Int = 50,
        forceRefresh: Boolean = false
    ): List<RankingEntry> {
        val now = System.currentTimeMillis()
        val cached = scoreCache[period]

        if (!forceRefresh && cached != null && (now - cached.first) < CACHE_TTL_MS) {
            return cached.second
        }

        return try {
            val result = client.postgrest.rpc(
                function = "get_ranking",
                parameters = GetRankingParams(period = period.value, limit = limit)
            ).decodeList<RankingEntry>()
            scoreCache[period] = now to result
            lastUpdateTime = now
            result
        } catch (e: Exception) {
            cached?.second ?: emptyList()
        }
    }

    /**
     * Touch 랭킹 조회 (30초 TTL 캐시).
     */
    suspend fun getTouchRanking(
        period: RankingPeriod,
        limit: Int = 50,
        forceRefresh: Boolean = false
    ): List<RankingEntry> {
        val now = System.currentTimeMillis()
        val cached = touchCache[period]

        if (!forceRefresh && cached != null && (now - cached.first) < CACHE_TTL_MS) {
            return cached.second
        }

        return try {
            val result = client.postgrest.rpc(
                function = "get_touch_ranking",
                parameters = GetRankingParams(period = period.value, limit = limit)
            ).decodeList<RankingEntry>()
            touchCache[period] = now to result
            lastUpdateTime = now
            result
        } catch (e: Exception) {
            cached?.second ?: emptyList()
        }
    }

    /**
     * 일별 점수 동기화 (upsert RPC)
     */
    suspend fun syncDailyClicks(clickCount: Long): Boolean {
        if (currentUserId() == null) return false
        return try {
            client.postgrest.rpc(
                function = "upsert_daily_clicks",
                parameters = UpsertClicksParams(clickCount = clickCount)
            )
            true
        } catch (e: Exception) {
            Log.e("dogakdogak", "syncDailyClicks failed", e)
            false
        }
    }

    /**
     * 일별 터치 횟수 동기화 (upsert RPC)
     */
    suspend fun syncDailyTouches(touchCount: Long): Boolean {
        if (currentUserId() == null) return false
        return try {
            client.postgrest.rpc(
                function = "upsert_daily_touches",
                parameters = UpsertTouchesParams(touchCount = touchCount)
            )
            true
        } catch (e: Exception) {
            Log.e("dogakdogak", "syncDailyTouches failed", e)
            false
        }
    }

    // profiles 테이블에서 조회한 프로필 캐시
    private var cachedDisplayName: String? = null
    private var cachedAvatarUrl: String? = null
    private val _totalScore = MutableStateFlow<Long?>(null)
    private val _totalTouches = MutableStateFlow<Long?>(null)

    /** 로그인 유저의 Supabase 누적 점수 (profiles.click_count) */
    val totalScoreFlow: StateFlow<Long?> = _totalScore.asStateFlow()
    /** 로그인 유저의 Supabase 누적 터치 (profiles.touch_count) */
    val totalTouchesFlow: StateFlow<Long?> = _totalTouches.asStateFlow()

    /** profiles 테이블에서 최신 프로필 조회 → 캐시 갱신 */
    suspend fun refreshProfile() {
        val userId = currentUserId() ?: return
        try {
            val row = client.postgrest.from("profiles")
                .select { filter { eq("id", userId) } }
                .decodeSingleOrNull<ProfileRow>()
            if (row != null) {
                cachedDisplayName = row.displayName
                cachedAvatarUrl = row.avatarUrl
                _totalScore.value = row.clickCount ?: 0L
                _totalTouches.value = row.touchCount ?: 0L
            }
        } catch (_: Exception) {}
    }

    /** 계정 삭제 시 DB 데이터 삭제 (클릭/터치 점수) */
    suspend fun deleteUserData(): Boolean {
        val userId = currentUserId() ?: return false
        return try {
            client.postgrest.from("user_clicks").delete {
                filter { eq("user_id", userId) }
            }
            clearProfileCache()
            scoreCache.clear()
            touchCache.clear()
            true
        } catch (e: Exception) {
            Log.e("dogakdogak", "deleteUserData failed", e)
            false
        }
    }

    /** 로그아웃 시 프로필 캐시 초기화 */
    fun clearProfileCache() {
        cachedDisplayName = null
        cachedAvatarUrl = null
        _totalScore.value = null
        _totalTouches.value = null
    }

    /** 현재 로그인된 유저의 표시 이름 (profiles 테이블 우선) */
    fun getCurrentUserDisplayName(): String {
        cachedDisplayName?.let { if (it.isNotBlank()) return it }
        val user = client.auth.currentUserOrNull() ?: return "익명"
        val metadata = user.userMetadata ?: return "익명"
        return try {
            metadata["display_name"]?.jsonPrimitive?.content
                ?: metadata["full_name"]?.jsonPrimitive?.content
                ?: user.email?.substringBefore('@')
                ?: "익명"
        } catch (_: Exception) { "익명" }
    }

    /** 현재 로그인된 유저의 아바타 URL (profiles 테이블 우선) */
    fun getCurrentUserAvatarUrl(): String? {
        cachedAvatarUrl?.let { return it }
        val user = client.auth.currentUserOrNull() ?: return null
        return try {
            user.userMetadata?.get("avatar_url")?.jsonPrimitive?.content
        } catch (_: Exception) { null }
    }

    /** 프로필(닉네임) 업데이트 — profiles 테이블 직접 갱신 */
    suspend fun updateProfile(displayName: String, avatarUrl: String? = null): Boolean {
        val userId = currentUserId() ?: return false
        return try {
            client.postgrest.from("profiles").update({
                set("display_name", displayName)
                if (avatarUrl != null) {
                    set("avatar_url", avatarUrl)
                }
            }) {
                filter { eq("id", userId) }
            }
            cachedDisplayName = displayName
            if (avatarUrl != null) cachedAvatarUrl = avatarUrl
            scoreCache.clear()
            touchCache.clear()
            true
        } catch (e: Exception) {
            Log.e("dogakdogak", "Profile update failed", e)
            false
        }
    }

    /** 아바타 이미지 업로드 → public URL 반환 */
    suspend fun uploadAvatar(imageBytes: ByteArray): String? {
        val userId = currentUserId() ?: return null
        return try {
            val path = "$userId.jpg"
            val bucket = client.storage.from("avatars")
            bucket.upload(path, imageBytes, upsert = true)
            "${bucket.publicUrl(path)}?t=${System.currentTimeMillis()}"
        } catch (e: Exception) {
            Log.e("dogakdogak", "Avatar upload failed", e)
            null
        }
    }

    companion object {
        private const val CACHE_TTL_MS = 30_000L // 30초
    }
}
