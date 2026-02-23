package helium314.keyboard.latin.dogakdogak

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * AuthManager 단위 테스트 (TDD — RED phase)
 *
 * Fake 구현체로 Android/Supabase 의존성 없이 순수 로직 검증.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AuthManagerTest {

    // ── Fake 구현체 ──────────────────────────────────────────────

    private class FakeSupabaseAuth : SupabaseAuthPort {
        var signInResult: Result<Unit> = Result.success(Unit)
        var signOutResult: Result<Unit> = Result.success(Unit)
        var deleteAccountResult: Result<Boolean> = Result.success(true)
        var userId: String? = null
        override val sessionStatus = MutableStateFlow<SupabaseSessionState>(SupabaseSessionState.NotAuthenticated)

        override suspend fun signInWithGoogle(idToken: String) {
            signInResult.getOrThrow()
            userId = "google-user-123"
            sessionStatus.value = SupabaseSessionState.Authenticated
        }

        var kakaoOAuthUrl: String = "https://supabase.co/auth/v1/authorize?provider=kakao&redirect_to=test"
        var kakaoOAuthUrlError: Exception? = null

        override fun getKakaoOAuthUrl(redirectUrl: String): String {
            kakaoOAuthUrlError?.let { throw it }
            return kakaoOAuthUrl
        }

        override suspend fun signOut() {
            signOutResult.getOrThrow()
            userId = null
            sessionStatus.value = SupabaseSessionState.NotAuthenticated
        }

        override suspend fun deleteAccount(): Boolean {
            return deleteAccountResult.getOrThrow()
        }

        override fun currentUserId(): String? = userId
    }

    private class FakeGoogleSignIn : GoogleSignInPort {
        var result: GoogleSignInResult = GoogleSignInResult.Success("valid-id-token")

        override fun extractResult(intentData: Any?): GoogleSignInResult = result
    }

    private lateinit var fakeAuth: FakeSupabaseAuth
    private lateinit var fakeGoogle: FakeGoogleSignIn
    private lateinit var authManager: AuthManager

    @Before
    fun setup() {
        fakeAuth = FakeSupabaseAuth()
        fakeGoogle = FakeGoogleSignIn()
        authManager = AuthManager(fakeAuth, fakeGoogle)
    }

    // ── Google Sign-In 결과 처리 ─────────────────────────────────

    @Test
    fun handleGoogleSignIn_validToken_authenticates() = runTest {
        fakeGoogle.result = GoogleSignInResult.Success("valid-token")

        authManager.handleGoogleSignInResult(null)

        val state = authManager.authState.value
        assertIs<AuthState.Authenticated>(state)
        assertEquals("google-user-123", state.userId)
    }

    @Test
    fun handleGoogleSignIn_nullToken_emitsError() = runTest {
        fakeGoogle.result = GoogleSignInResult.NullToken

        val errors = mutableListOf<AuthError>()
        val job = launch(UnconfinedTestDispatcher()) {
            authManager.authErrors.toList(errors)
        }

        authManager.handleGoogleSignInResult(null)

        assertTrue(errors.any { it is AuthError.GoogleTokenNull })
        assertEquals(AuthState.NotAuthenticated, authManager.authState.value)
        job.cancel()
    }

    @Test
    fun handleGoogleSignIn_apiException10_emitsDeveloperError() = runTest {
        fakeGoogle.result = GoogleSignInResult.Failed(statusCode = 10)

        val errors = mutableListOf<AuthError>()
        val job = launch(UnconfinedTestDispatcher()) {
            authManager.authErrors.toList(errors)
        }

        authManager.handleGoogleSignInResult(null)

        val error = errors.filterIsInstance<AuthError.GoogleSignInFailed>().first()
        assertEquals(10, error.statusCode)
        assertTrue(error.isDeveloperError)
        job.cancel()
    }

    @Test
    fun handleGoogleSignIn_apiException12501_emitsCancelError() = runTest {
        fakeGoogle.result = GoogleSignInResult.Failed(statusCode = 12501)

        val errors = mutableListOf<AuthError>()
        val job = launch(UnconfinedTestDispatcher()) {
            authManager.authErrors.toList(errors)
        }

        authManager.handleGoogleSignInResult(null)

        val error = errors.filterIsInstance<AuthError.GoogleSignInFailed>().first()
        assertEquals(12501, error.statusCode)
        assertTrue(error.userFacingMessage.contains("취소"))
        job.cancel()
    }

    @Test
    fun handleGoogleSignIn_apiException7_emitsNetworkError() = runTest {
        fakeGoogle.result = GoogleSignInResult.Failed(statusCode = 7)

        val errors = mutableListOf<AuthError>()
        val job = launch(UnconfinedTestDispatcher()) {
            authManager.authErrors.toList(errors)
        }

        authManager.handleGoogleSignInResult(null)

        val error = errors.filterIsInstance<AuthError.GoogleSignInFailed>().first()
        assertEquals(7, error.statusCode)
        assertTrue(error.userFacingMessage.contains("네트워크"))
        job.cancel()
    }

    // ── Supabase 토큰 교환 ───────────────────────────────────────

    @Test
    fun handleGoogleSignIn_supabaseFails_emitsSupabaseError() = runTest {
        fakeGoogle.result = GoogleSignInResult.Success("valid-token")
        fakeAuth.signInResult = Result.failure(RuntimeException("Supabase down"))

        val errors = mutableListOf<AuthError>()
        val job = launch(UnconfinedTestDispatcher()) {
            authManager.authErrors.toList(errors)
        }

        authManager.handleGoogleSignInResult(null)

        assertTrue(errors.any { it is AuthError.SupabaseSignInFailed })
        job.cancel()
    }

    // ── 카카오 로그인 ────────────────────────────────────────────

    @Test
    fun getKakaoOAuthUrl_success_returnsUrl() {
        fakeAuth.kakaoOAuthUrl = "https://supabase.co/auth/v1/authorize?provider=kakao"

        val url = authManager.getKakaoOAuthUrl("dogak-dogak://login-callback")

        assertEquals("https://supabase.co/auth/v1/authorize?provider=kakao", url)
    }

    @Test
    fun getKakaoOAuthUrl_failure_emitsError() {
        fakeAuth.kakaoOAuthUrlError = RuntimeException("Kakao config error")

        val url = authManager.getKakaoOAuthUrl("dogak-dogak://login-callback")

        assertNull(url)
    }

    // ── 인증 상태 전환 ───────────────────────────────────────────

    @Test
    fun authState_initiallyNotAuthenticated() {
        assertEquals(AuthState.NotAuthenticated, authManager.authState.value)
    }

    @Test
    fun authState_loadingDuringSignIn() = runTest {
        // Loading 상태가 거치는지 확인하기 위해 states를 수집
        val states = mutableListOf<AuthState>()
        val job = launch(UnconfinedTestDispatcher()) {
            authManager.authState.toList(states)
        }

        fakeGoogle.result = GoogleSignInResult.Success("valid-token")
        authManager.handleGoogleSignInResult(null)

        assertTrue(states.any { it is AuthState.Loading })
        assertTrue(states.any { it is AuthState.Authenticated })
        job.cancel()
    }

    // ── 에러 메시지 검증 (한글) ──────────────────────────────────

    @Test
    fun authError_developerError_containsCode10() {
        val error = AuthError.GoogleSignInFailed(10)
        assertTrue(error.userFacingMessage.contains("코드: 10"))
        assertTrue(error.userFacingMessage.contains("개발자"))
    }

    @Test
    fun authError_networkError_mentionsNetwork() {
        val error = AuthError.GoogleSignInFailed(7)
        assertTrue(error.userFacingMessage.contains("네트워크"))
    }

    @Test
    fun authError_cancelled_mentionsCancellation() {
        val error = AuthError.GoogleSignInFailed(12501)
        assertTrue(error.userFacingMessage.contains("취소"))
    }

    @Test
    fun authError_googleTokenNull_hasMessage() {
        assertTrue(AuthError.GoogleTokenNull.userFacingMessage.isNotBlank())
    }

    @Test
    fun authError_kakaoFailed_hasKoreanMessage() {
        val error = AuthError.KakaoSignInFailed("some error")
        assertTrue(error.userFacingMessage.contains("카카오"))
    }

    @Test
    fun authError_supabaseFailed_hasKoreanMessage() {
        val error = AuthError.SupabaseSignInFailed("timeout")
        assertTrue(error.userFacingMessage.contains("서버"))
    }

    // ── 로그아웃 ─────────────────────────────────────────────────

    @Test
    fun logout_success_becomesNotAuthenticated() = runTest {
        // 먼저 로그인
        fakeGoogle.result = GoogleSignInResult.Success("valid-token")
        authManager.handleGoogleSignInResult(null)
        assertIs<AuthState.Authenticated>(authManager.authState.value)

        // 로그아웃
        authManager.logout()
        assertEquals(AuthState.NotAuthenticated, authManager.authState.value)
    }

    @Test
    fun logout_failure_emitsError() = runTest {
        fakeAuth.signOutResult = Result.failure(RuntimeException("Network error"))

        val errors = mutableListOf<AuthError>()
        val job = launch(UnconfinedTestDispatcher()) {
            authManager.authErrors.toList(errors)
        }

        authManager.logout()

        assertTrue(errors.any { it is AuthError.LogoutFailed })
        job.cancel()
    }

    // ── 계정 삭제 ────────────────────────────────────────────────

    @Test
    fun deleteAccount_success_becomesNotAuthenticated() = runTest {
        // 먼저 로그인
        fakeGoogle.result = GoogleSignInResult.Success("valid-token")
        authManager.handleGoogleSignInResult(null)

        authManager.deleteAccount()
        // deleteAccount 내부에서 signOut까지 호출하므로 NotAuthenticated
        assertEquals(AuthState.NotAuthenticated, authManager.authState.value)
    }

    @Test
    fun deleteAccount_failure_emitsError() = runTest {
        fakeAuth.deleteAccountResult = Result.failure(RuntimeException("Server error"))

        val errors = mutableListOf<AuthError>()
        val job = launch(UnconfinedTestDispatcher()) {
            authManager.authErrors.toList(errors)
        }

        authManager.deleteAccount()

        assertTrue(errors.any { it is AuthError.DeleteAccountFailed })
        job.cancel()
    }

    // ── 세션 상태 관찰 ───────────────────────────────────────────

    @Test
    fun observeSessionStatus_authenticated_updatesState() = runTest {
        val job = launch(UnconfinedTestDispatcher()) {
            authManager.observeSessionStatus()
        }

        fakeAuth.userId = "ext-user-789"
        fakeAuth.sessionStatus.value = SupabaseSessionState.Authenticated

        val state = authManager.authState.value
        assertIs<AuthState.Authenticated>(state)
        assertEquals("ext-user-789", state.userId)
        job.cancel()
    }

    @Test
    fun observeSessionStatus_notAuthenticated_updatesState() = runTest {
        // Start as authenticated
        fakeAuth.userId = "user-1"
        fakeAuth.sessionStatus.value = SupabaseSessionState.Authenticated

        val job = launch(UnconfinedTestDispatcher()) {
            authManager.observeSessionStatus()
        }

        // Then lose session
        fakeAuth.userId = null
        fakeAuth.sessionStatus.value = SupabaseSessionState.NotAuthenticated

        assertEquals(AuthState.NotAuthenticated, authManager.authState.value)
        job.cancel()
    }
}
