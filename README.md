# ZETA
Android용 다중 캐릭터 AI 채팅 앱.

## 빌드
1. Settings → Secrets and variables → Actions → New repository secret에서 `GEMINI_API_KEY`를 등록합니다.
2. Actions → Android APK 실행 후 성공한 실행의 Artifacts에서 `ZETA-debug-apk`를 받습니다.
3. API 키는 개인 테스트용입니다. APK에 포함된 키는 추출할 수 있으므로 공개 배포에는 서버 프록시가 필요합니다.

## 현재 구현
- Kotlin / Jetpack Compose Android 앱
- 카카오톡 형태의 말풍선 UI
- `@캐릭터이름: 내용` 역할 지정 입력 및 AI 대화 맥락 반영
- 최대 100개 대화 슬롯, 자동 저장
- 메시지 책갈피
- Gemini 2.5 Flash-Lite 연결
