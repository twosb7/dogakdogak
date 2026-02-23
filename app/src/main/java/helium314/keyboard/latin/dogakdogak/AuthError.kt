package helium314.keyboard.latin.dogakdogak

/**
 * 인증 에러 타입. 각 에러는 사용자에게 표시할 한글 메시지를 제공.
 */
sealed class AuthError {
    abstract val userFacingMessage: String

    data class GoogleSignInFailed(val statusCode: Int) : AuthError() {
        /** statusCode 10 = DEVELOPER_ERROR (SHA-1 미등록 등 설정 문제) */
        val isDeveloperError: Boolean get() = statusCode == 10

        override val userFacingMessage: String
            get() = when (statusCode) {
                10 -> "Google 로그인 설정 오류입니다 (코드: 10). 개발자에게 문의해주세요."
                7 -> "네트워크 오류입니다. 인터넷 연결을 확인해주세요."
                12501 -> "로그인이 취소되었습니다."
                12502 -> "로그인 진행 중입니다. 잠시 기다려주세요."
                else -> "Google 로그인에 실패했습니다 (코드: $statusCode)."
            }
    }

    data object GoogleTokenNull : AuthError() {
        override val userFacingMessage: String
            get() = "Google 인증 토큰을 받지 못했습니다. 다시 시도해주세요."
    }

    data class KakaoSignInFailed(val message: String?) : AuthError() {
        override val userFacingMessage: String
            get() = "카카오 로그인에 실패했습니다. 다시 시도해주세요."
    }

    data class SupabaseSignInFailed(val message: String?) : AuthError() {
        override val userFacingMessage: String
            get() = "서버 인증에 실패했습니다. 다시 시도해주세요."
    }

    data class LogoutFailed(val message: String?) : AuthError() {
        override val userFacingMessage: String
            get() = "로그아웃에 실패했습니다. 다시 시도해주세요."
    }

    data class DeleteAccountFailed(val message: String?) : AuthError() {
        override val userFacingMessage: String
            get() = "계정 삭제에 실패했습니다. 다시 시도해주세요."
    }
}

/**
 * 인증 상태
 */
sealed class AuthState {
    data object NotAuthenticated : AuthState()
    data class Authenticated(val userId: String) : AuthState()
    data object Loading : AuthState()
}
