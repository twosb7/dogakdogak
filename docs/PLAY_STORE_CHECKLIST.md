# Play Store 제출 전 체크리스트

앱 제출 전 아래 항목을 **모두** 확인하세요.

---

## Phase 1: 심사 필수 항목 (없으면 즉시 거절)

### 보안
- [ ] `local.properties`에 모든 시크릿 설정 (SUPABASE_URL, SUPABASE_ANON_KEY, GOOGLE_WEB_CLIENT_ID)
- [ ] `local.properties`가 `.gitignore`에 포함되어 있음 ✅
- [ ] `build.gradle.kts`에 하드코딩된 시크릿 없음 ✅
- [ ] 키스토어 파일(`dogakdogak-release.jks`) 별도 보관, Git 미포함 ✅

### 개인정보처리방침
- [x] `docs/privacy-policy.html` 작성 완료 ✅
- [x] GitHub Pages 배포: `https://twosb7.github.io/dogakdogak/privacy-policy.html`
- [ ] 앱 내 개인정보처리방침 링크 작동 확인 (설정 탭 → 앱 정보 → "개인정보처리방침")
- [ ] Play Console → 앱 콘텐츠 → 개인정보처리방침 URL 입력

### 계정 삭제
- [ ] 앱 내 계정 삭제 기능 작동 확인 (설정 탭 → 로그인 카드 → "계정 삭제")
- [ ] 삭제 확인 다이얼로그 표시 확인
- [ ] 삭제 후 재로그인 불가 확인
- [ ] Supabase Edge Function `delete-user` 배포 완료
- [ ] 외부 삭제 URL이 최신 안내 페이지를 가리키는지 확인

### 네트워크 오류 처리
- [ ] 비행기 모드에서 랭킹 탭 → 오류 메시지 표시 확인
- [ ] "다시 시도" 버튼 작동 확인

---

## Phase 2: 빌드 및 기술 요건

### 빌드
- [ ] `./gradlew assembleRelease` 성공
- [ ] APK 크기 확인 (100MB 이하 권장)
- [ ] R8/ProGuard 적용 확인 (`isMinifyEnabled = true`)
- [ ] debug, release, debugNoMinify 모두 빌드 성공

### 버전
- [ ] `versionCode = 12`
- [ ] `versionName = "1.0.11"`
- [ ] 향후 업데이트 시 versionCode 반드시 증가

### 서명
- [ ] 릴리즈 키스토어로 서명 확인
- [ ] `jarsigner -verify` 또는 `apksigner verify` 통과

---

## Phase 3: Play Console 제출 설정

### 앱 정보
- [ ] 앱 이름: "도각도각 - ASMR 키보드 타건음" (40자 이내)
- [ ] 짧은 설명 (80자 이내) 작성
- [ ] 전체 설명 (4000자 이내) 작성
- [ ] 스크린샷: 핸드폰 2장 이상, 7인치/10인치 태블릿 선택 사항
- [ ] 앱 아이콘: 512x512px PNG
- [ ] 배너 이미지: 1024x500px (선택)

### 콘텐츠 등급
- [ ] 앱 콘텐츠 > 등급 섹션 작성
- [ ] IME 앱 관련 민감 권한 설명 준비

### Data Safety (데이터 안전)
- [ ] `docs/play-store-data-safety.md` 참고하여 Data Safety 양식 작성
- [ ] "수집하는 데이터 유형": 이름, 이메일 주소, 기기 또는 기타 ID
- [ ] "키 입력 데이터 수집 안 함" 명시
- [ ] 설치 앱 이름 미수집, 연락처/마이크 온디바이스 처리 문구 반영

### 대상 국가
- [ ] 배포 국가 선택 (대한민국 포함)

---

## Phase 4: 최종 QA

### 기능 테스트
- [ ] 신규 기기에서 온보딩 플로우 전체 테스트
- [ ] Google 로그인 정상 작동
- [ ] 카카오 로그인 정상 작동
- [ ] 타건음 재생 정상 작동 (볼륨 0~100%)
- [ ] 콤보 이펙트 오버레이 정상 표시
- [ ] 랭킹 조회 정상 작동
- [ ] 구매 플로우 테스트 (테스트 결제 계정 사용)
- [ ] 구매 복원 정상 작동
- [ ] 연락처 권한 토글 시 사전 안내 후 시스템 권한 요청 표시
- [ ] 음성 입력 시작 시 사전 안내 후 시스템 권한 요청 표시
- [ ] 설치 앱 이름 추천 기능이 설정/제안에 나타나지 않음

### 접근성
- [ ] 최소 타겟 기기 (API 21, Android 5.0) 테스트
- [ ] 한국어/영어 시스템 언어 설정에서 모두 정상 작동

### 보안 최종 확인
- [ ] `git log --all -- local.properties` 결과 없음 (한번도 커밋 안 됨)
- [ ] APK에 시크릿 하드코딩 없음 (`strings` 명령 또는 APK 분석 도구로 확인)

---

## 제출 후

- [ ] Play Console에서 심사 상태 모니터링 (보통 1~3 영업일)
- [ ] 심사 통과 후 "프로덕션 출시" 클릭
- [ ] 릴리즈 노트 작성 (한국어)
