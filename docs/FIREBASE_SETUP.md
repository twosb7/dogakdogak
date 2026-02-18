# Firebase Crashlytics + Analytics 설정 가이드

## 전제 조건

1. [Firebase Console](https://console.firebase.google.com)에서 프로젝트 생성
2. Android 앱 등록: 패키지명 `com.dogakdogak.keyboard`
3. `google-services.json` 다운로드

## 설정 단계

### 1단계: google-services.json 배치

```
dogakdogak-new/
  app/
    google-services.json   ← 여기에 배치
```

### 2단계: build.gradle.kts 플러그인 추가

루트 `build.gradle.kts`:
```kotlin
plugins {
    id("com.google.gms.google-services") version "4.4.2" apply false
    id("com.google.firebase.crashlytics") version "3.0.2" apply false
}
```

`app/build.gradle.kts` 상단 plugins 블록:
```kotlin
plugins {
    // 기존 플러그인들...
    id("com.google.gms.google-services")
    id("com.google.firebase.crashlytics")
}
```

### 3단계: 의존성 주석 해제

`app/build.gradle.kts` 의존성 블록에서 주석 해제:
```kotlin
implementation(platform("com.google.firebase:firebase-bom:33.7.0"))
implementation("com.google.firebase:firebase-crashlytics-ktx")
implementation("com.google.firebase:firebase-analytics-ktx")
```

### 4단계: App.kt 초기화 주석 해제

`App.kt`에서 Firebase 초기화 코드 주석 해제:
```kotlin
runNonCritical("Firebase.init") {
    com.google.firebase.FirebaseApp.initializeApp(this)
}
```

## 추적 이벤트 목록

이벤트 트래킹을 위해 아래 코드를 해당 위치에 추가하세요:

```kotlin
// Analytics 초기화
val analytics = FirebaseAnalytics.getInstance(context)

// 스위치 선택
analytics.logEvent("switch_selected") {
    param("switch_type", switchName)
}

// 구매 시작
analytics.logEvent("purchase_initiated") {
    param("product_id", productId)
}

// 구매 성공
analytics.logEvent("purchase_success") {
    param("product_id", productId)
}

// 로그인 성공
analytics.logEvent("login_success") {
    param("provider", "google") // 또는 "kakao"
}

// 콤보 마일스톤
analytics.logEvent("combo_milestone") {
    param("tier", "EPIC") // 또는 "LEGENDARY"
}
```

## 확인 방법

1. Firebase Console → Crashlytics → 테스트 크래시 확인
2. Firebase Console → Analytics → 이벤트 실시간 확인
