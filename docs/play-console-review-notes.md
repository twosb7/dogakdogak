# Play Console 검토 메모 초안

아래 문구는 Play Console의 "앱 검토 메모"에 그대로 붙여 넣을 수 있게 정리했습니다.

```
Dogakdogak is an Android keyboard app.

- The app does not collect, store, or transmit typed text content, passwords, messages, or clipboard content to Dogakdogak servers.
- Global rankings sync only total score and total touch count after sign-in.
- Per-app rankings sync only count-based statistics for a fixed supported app list, and only after the user explicitly accepts the in-app disclosure.
- The release build does not read installed app names for suggestion features.
- Contacts permission is optional and used only for on-device contact-name suggestions after the user enables the feature and confirms the in-app explanation.
- Microphone permission is optional and used only for voice input after the user confirms the in-app explanation. Raw audio is not uploaded to Dogakdogak servers.
- SYSTEM_ALERT_WINDOW is optional and used only for the combo overlay feature when enabled by the user.
- Account deletion is available both in-app and through the public deletion page:
  https://twosb7.github.io/dogakdogak/delete-account.html
- Account deletion removes the user profile, ranking totals, per-app ranking stats, purchase sync records, and avatar file.
- Privacy policy:
  https://twosb7.github.io/dogakdogak/privacy-policy.html
```

추가 메모:

- reviewer가 민감 권한 조합을 질문하면 `docs/play-store-data-safety.md`와 동일한 표현을 유지합니다.
- `delete-user` Edge Function이 실제 배포되어 있어야 위 문구가 사실과 일치합니다.
