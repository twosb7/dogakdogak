package helium314.keyboard.latin.dogakdogak

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.gotrue.Auth
import io.github.jan.supabase.gotrue.ExternalAuthAction
import io.github.jan.supabase.gotrue.FlowType
import io.github.jan.supabase.gotrue.auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
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
        }
    }

    val auth get() = client.auth
    val storage get() = client.storage
}
