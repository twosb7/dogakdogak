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
     * 계정 삭제: Edge Function이 연관 데이터와 auth 계정을 함께 제거해야만 성공 처리한다.
     */
    suspend fun deleteAccount(): Boolean {
        client.auth.currentUserOrNull()?.id ?: return false
        return try {
            client.functions.invoke("delete-user")
            client.auth.signOut()
            true
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) Log.e("SupabaseModule", "deleteAccount failed", e)
            else Log.e("SupabaseModule", "deleteAccount failed")
            false
        }
    }
}
