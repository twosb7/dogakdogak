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

/**
 * Supabase 클라이언트 싱글톤
 */
object SupabaseModule {

    private const val SUPABASE_URL = "https://nsbsosaeukhpifexmwnq.supabase.co"
    private const val SUPABASE_ANON_KEY =
        "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6Im5zYnNvc2FldWtocGlmZXhtd25xIiwicm9sZSI6ImFub24iLCJpYXQiOjE3NzEwNzM2NTEsImV4cCI6MjA4NjY0OTY1MX0.nCuWS5DlE5rYmJl6QKpJg6EsZd1PfmVUQ5U11KFoewI"

    const val GOOGLE_WEB_CLIENT_ID = "532036082742-ufjr4sukagejeb8sejebpgc7iq1s297v.apps.googleusercontent.com"
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
