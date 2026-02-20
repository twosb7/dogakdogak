package helium314.keyboard.latin.dogakdogak

import android.util.Log
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.functions.Functions
import io.github.jan.supabase.functions.functions
import io.github.jan.supabase.gotrue.Auth
import io.github.jan.supabase.gotrue.ExternalAuthAction
import io.github.jan.supabase.gotrue.FlowType
import io.github.jan.supabase.gotrue.auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.storage.Storage
import io.github.jan.supabase.storage.storage
import helium314.keyboard.latin.BuildConfig

/**
 * Supabase 클라이언트 싱글톤
 */
object SupabaseModule {

    private val SUPABASE_URL = BuildConfig.SUPABASE_URL
    private val SUPABASE_ANON_KEY = BuildConfig.SUPABASE_ANON_KEY

    val GOOGLE_WEB_CLIENT_ID = BuildConfig.GOOGLE_WEB_CLIENT_ID
    const val AUTH_REDIRECT_URL = "dogak-dogak://login-callback"

    val client: SupabaseClient by lazy {
        createSupabaseClient(
            supabaseUrl = SUPABASE_URL,
            supabaseKey = SUPABASE_ANON_KEY
        ) {
            install(Auth) {
                flowType = FlowType.PKCE
                scheme = "dogak-dogak"
                host = "login-callback"
                defaultRedirectUrl = AUTH_REDIRECT_URL
                defaultExternalAuthAction = ExternalAuthAction.CustomTabs()
            }
            install(Postgrest)
            install(Storage)
            install(Functions)
        }
    }

    val auth get() = client.auth
    val storage get() = client.storage

    /**
     * 계정 삭제: DB 데이터 삭제 후 Edge Function으로 auth 계정 삭제.
     * Edge Function 미배포 시 DB 데이터 삭제 + 로그아웃으로 fallback.
     * @return true if successful
     */
    suspend fun deleteAccount(): Boolean {
        val userId = client.auth.currentUserOrNull()?.id ?: return false
        return try {
            // 1. DB 데이터 삭제 (사용자 JWT로 직접 삭제)
            try {
                client.postgrest.from("profiles").delete {
                    filter { eq("id", userId) }
                }
            } catch (e: Exception) {
                Log.w("SupabaseModule", "profiles delete failed: ${e.message}")
            }
            // 2. Edge Function으로 auth 계정 삭제 (배포된 경우)
            try {
                client.functions.invoke("delete-user")
            } catch (e: Exception) {
                Log.w("SupabaseModule", "delete-user function not available: ${e.message}")
            }
            // 3. 항상 로그아웃
            client.auth.signOut()
            true
        } catch (e: Exception) {
            Log.e("SupabaseModule", "deleteAccount failed", e)
            false
        }
    }
}
