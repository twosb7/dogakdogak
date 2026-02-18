# Google Play Developer Service Account 설정 가이드

서버사이드 영수증 검증(verify-purchase Edge Function)을 위한 설정 방법입니다.

## 전제 조건

- Google Play Developer 계정 ($25 일회성 등록비)
- Google Cloud Console 접근 권한

## 설정 단계

### 1단계: Google Cloud Console에서 Service Account 생성

1. [Google Cloud Console](https://console.cloud.google.com) → IAM 및 관리자 → 서비스 계정
2. "서비스 계정 만들기" 클릭
3. 이름: `dogakdogak-play-verifier` (또는 원하는 이름)
4. 역할: **없음** (Play Console에서 권한 부여)
5. 생성 완료 후 → 키 탭 → "키 추가" → JSON 형식으로 다운로드

### 2단계: Google Play Console에서 권한 연결

1. [Google Play Console](https://play.google.com/console) → 설정 → API 액세스
2. "기존 Google Cloud 프로젝트 연결" → 1단계에서 생성한 프로젝트 선택
3. "서비스 계정 관리" → 1단계 서비스 계정 찾기 → "액세스 권한 부여"
4. 권한: **재무 데이터 보기**, **주문 관리** 체크
5. 저장

### 3단계: Supabase Edge Function 환경변수 등록

Supabase Dashboard → 프로젝트 → Settings → Edge Functions → Secrets:

```
GOOGLE_PLAY_SERVICE_ACCOUNT_JSON = <1단계에서 다운로드한 JSON 전체 내용>
```

### 4단계: Edge Function 배포

```bash
# Supabase CLI 설치 (없는 경우)
npm install -g supabase

# 로그인
supabase login

# 프로젝트 연결
supabase link --project-ref <your-project-ref>

# 배포
supabase functions deploy verify-purchase
```

### 5단계: BillingManager.kt 활성화

`BillingManager.kt`의 주석 처리된 `verifyPurchase()` 코드를 해제하고,
`acknowledgePurchase()` 호출 전에 `verifyPurchase(purchase)` 호출을 추가합니다.

## 주의사항

- Service Account JSON에는 민감한 개인키가 포함됩니다. **절대 Git에 커밋하지 마세요.**
- Supabase Edge Function Secrets를 통해 안전하게 주입하세요.
- Play API 할당량: 프로젝트당 일일 200,000 요청 (무료)
