package helium314.keyboard.latin.dogakdogak

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

// ── Port interfaces (테스트에서 Fake로 교체 가능) ────────────────

/**
 * Supabase 인증 포트. 실제 구현체(RealSupabaseAuth)와 Fake를 모두 지원.
 */
interface SupabaseAuthPort {
    val sessionStatus: StateFlow<SupabaseSessionState>
    suspend fun signInWithGoogle(idToken: String)
    /** OAuth URL만 생성하여 반환 (PKCE code verifier는 내부 저장). 브라우저 열기는 호출자 책임. */
    fun getKakaoOAuthUrl(redirectUrl: String): String
    suspend fun signOut()
    suspend fun deleteAccount(): Boolean
    fun currentUserId(): String?
}

/**
 * Supabase 세션 상태 (Supabase SDK SessionStatus의 추상화)
 */
sealed class SupabaseSessionState {
    data object Authenticated : SupabaseSessionState()
    data object NotAuthenticated : SupabaseSessionState()
    data object Loading : SupabaseSessionState()
}

/**
 * Google Sign-In 결과 포트
 */
interface GoogleSignInPort {
    fun extractResult(intentData: Any?): GoogleSignInResult
}

/**
 * Google Sign-In 결과
 */
sealed class GoogleSignInResult {
    data class Success(val idToken: String) : GoogleSignInResult()
    data object NullToken : GoogleSignInResult()
    data class Failed(val statusCode: Int) : GoogleSignInResult()
}

// ── AuthManager ──────────────────────────────────────────────────

/**
 * 인증 로직을 캡슐화. SettingsActivity Composable에서 분리하여 테스트 가능하게 함.
 *
 * - authState: 현재 인증 상태 (UI 바인딩용)
 * - authErrors: 에러 이벤트 (Snackbar/Toast 표시용)
 */
class AuthManager(
    private val supabaseAuth: SupabaseAuthPort,
    private val googleSignIn: GoogleSignInPort,
) {
    private val _authState = MutableStateFlow<AuthState>(AuthState.NotAuthenticated)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    private val _authErrors = MutableSharedFlow<AuthError>(extraBufferCapacity = 10)
    val authErrors: SharedFlow<AuthError> = _authErrors.asSharedFlow()

    /**
     * Google Sign-In ActivityResult에서 호출.
     * intentData는 실제로는 Intent이지만 Port에서 추상화.
     */
    suspend fun handleGoogleSignInResult(intentData: Any?) {
        val result = googleSignIn.extractResult(intentData)
        when (result) {
            is GoogleSignInResult.Failed -> {
                _authErrors.emit(AuthError.GoogleSignInFailed(result.statusCode))
                _authState.value = AuthState.NotAuthenticated
            }
            is GoogleSignInResult.NullToken -> {
                _authErrors.emit(AuthError.GoogleTokenNull)
                _authState.value = AuthState.NotAuthenticated
            }
            is GoogleSignInResult.Success -> {
                _authState.value = AuthState.Loading
                try {
                    supabaseAuth.signInWithGoogle(result.idToken)
                    val userId = supabaseAuth.currentUserId()
                    _authState.value = if (userId != null) {
                        AuthState.Authenticated(userId)
                    } else {
                        AuthState.NotAuthenticated
                    }
                } catch (e: Exception) {
                    _authErrors.emit(AuthError.SupabaseSignInFailed(e.message))
                    _authState.value = AuthState.NotAuthenticated
                }
            }
        }
    }

    /**
     * 카카오 OAuth URL을 생성. 호출자가 직접 브라우저를 열어야 함.
     * 인증 완료 후 딥링크 콜백 → observeSessionStatus에서 상태 갱신.
     * @return OAuth URL 또는 null (에러 시)
     */
    fun getKakaoOAuthUrl(redirectUrl: String): String? {
        return try {
            supabaseAuth.getKakaoOAuthUrl(redirectUrl)
        } catch (e: Exception) {
            _authErrors.tryEmit(AuthError.KakaoSignInFailed(e.message))
            null
        }
    }

    /**
     * 로그아웃
     */
    suspend fun logout() {
        try {
            supabaseAuth.signOut()
            _authState.value = AuthState.NotAuthenticated
        } catch (e: Exception) {
            _authErrors.emit(AuthError.LogoutFailed(e.message))
        }
    }

    /**
     * 계정 삭제
     */
    suspend fun deleteAccount() {
        try {
            supabaseAuth.deleteAccount()
            _authState.value = AuthState.NotAuthenticated
        } catch (e: Exception) {
            _authErrors.emit(AuthError.DeleteAccountFailed(e.message))
        }
    }

    /**
     * Supabase 세션 상태 변화를 관찰하여 authState를 갱신.
     * LaunchedEffect에서 호출. 외부에서 세션이 변경될 때(딥링크 등) 반영.
     */
    suspend fun observeSessionStatus() {
        supabaseAuth.sessionStatus.collect { status ->
            when (status) {
                is SupabaseSessionState.Authenticated -> {
                    val uid = supabaseAuth.currentUserId()
                    if (uid != null) {
                        _authState.value = AuthState.Authenticated(uid)
                    }
                }
                is SupabaseSessionState.NotAuthenticated -> {
                    _authState.value = AuthState.NotAuthenticated
                }
                is SupabaseSessionState.Loading -> {
                    _authState.value = AuthState.Loading
                }
            }
        }
    }
}
