# Notification Filter · 알림 필터

알림 제목이나 내용에 지정한 키워드가 포함되면 해당 알림을 지우는 Android 앱입니다. 광고 알림을 간단히 줄이는 것을 목표로 합니다.

[APK 다운로드](https://github.com/ForUltraplayer/notification-filter/releases/latest) · [MIT 라이선스](LICENSE) · [개인정보 안내](PRIVACY.md)

## 기능

- 키워드 추가 → 입력 → 저장. 키워드를 수정하거나 삭제할 수 있습니다.
- 키워드는 정규식이 아닌 일반 문자열로 비교하며, 영문 대소문자를 구분하지 않습니다. 예: `(광고)`.
- 제외할 앱 선택, 필터 ON/OFF.
- 차단 기록에서 앱, 키워드, 시간, 처리 상태, 저장된 알림 제목과 내용을 확인합니다.
- 삭제 요청과 실제 삭제 확인을 구분하며, 확인된 건만 삭제 횟수에 포함합니다.
- 한국어 UI, 밝은/어두운 테마. 광고·계정·서버 통신이 없습니다.

## 설치 및 사용

Android 8.0 이상에서 사용할 수 있습니다.

1. Releases에서 `notification-filter-0.1.0.apk`를 받습니다.
2. 설치 후 앱의 안내를 따라 Android 설정에서 **알림 접근 권한**을 허용합니다.
3. 키워드를 저장하고 필요한 앱을 제외한 다음 기능을 켭니다.
4. 차단 기록의 **삭제 확인** 상태와 알림 내용을 확인합니다.

필터는 처음에는 꺼져 있습니다. 앱 화면을 닫아도 Android의 알림 리스너가 연결되어 있으면 알림을 처리합니다. 강제 종료, 권한 해제, 제조사 절전 정책 등에 따른 중단 가능성이 있으며 항상 실행을 보장하지 않습니다.

### 알려진 제한

- Android가 알림을 게시한 **뒤에 삭제하는 방식**입니다. 팝업이 잠깐 표시될 수 있으며 소리·진동·팝업의 사전 차단을 보장하지 않습니다.
- 알림 영역에 게시되는 알림을 처리합니다. 다른 앱 화면 안에서 자체적으로 그리는 팝업은 처리하지 않습니다.
- 진행 중 알림과 그룹 요약 알림은 보호합니다. 확장 본문과 메시지 이력도 검사 대상에 포함될 수 있습니다.
- 짧거나 흔한 키워드는 필요한 알림까지 지울 수 있습니다.
- GitHub APK 설치 시 Play 프로텍트가 차단할 수 있습니다. 이번 공개로 해당 문제가 해결된 것은 아닙니다. 차단된다면 보호 기능을 끄지 말고 오류 문구를 개인정보 없이 이슈에 알려 주세요.

### v0.1.0 배포 및 서명

첫 공개 버전이며 이전 비공개 테스트 버전의 차단 기록·내용 확인 기능을 포함합니다. 표시 버전은 `0.1.0`, 내부 버전 코드는 `4`입니다. 같은 서명을 사용한 기존 테스트 설치본에서 업데이트할 수 있도록 내부 번호를 올렸습니다.

**이번 APK는 기존 개발용(debug) 인증서로 서명한 초기 배포본**입니다. 빌드 자체는 최적화된 release 빌드이며 디버깅은 비활성화되어 있습니다. Play 스토어 배포본이 아닙니다. 향후 배포 전용 서명 전환 시 업데이트 경로를 별도로 안내해야 합니다.

서명 인증서 SHA-256:
```text
d586a7026ce58f89a30d5b4215494c26666527be59ca3fdce658e755cb7dcd09
```

다른 PC에서 직접 빌드하면 서명이 달라질 수 있으므로 기존 설치본을 바로 업데이트하지 못할 수 있습니다. 서명키와 비밀번호는 저장소에 포함하지 않습니다.

## 기록과 개인정보

차단 대상 알림의 제목·내용과 처리 상태를 기기 내부에 저장합니다. 최근 7일·최대 5,000건을 보관하고 화면에는 최근 500건을 표시합니다. 오래된 기록 정리는 앱 진입 및 기록 저장 등 작업 시 수행됩니다. 기록 전체 삭제가 가능합니다.

데이터는 앱 전용 저장소에 저장되며 별도의 DB 암호화는 적용하지 않았습니다. 알림에 포함된 개인정보도 저장될 수 있습니다. 자세한 내용은 [개인정보 안내](PRIVACY.md)를 확인하세요.

## 소스 빌드

- JDK 17 이상(로컬 검증: JDK 21)
- Android SDK Platform 36 및 Build Tools 35.0.0
- Gradle Wrapper 8.14.3, Android Gradle Plugin 8.13.0
- Android Studio 또는 명령행 SDK

저장소를 복제한 후 Android Studio에서 열어 SDK 경로를 지정합니다. Windows에서는 Java 도구의 경로 인코딩 문제를 피하도록 영문 경로에 체크아웃하는 것을 권장합니다.

Windows:
```powershell
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug assembleRelease
```

macOS/Linux:
```sh
./gradlew testDebugUnitTest lintDebug assembleDebug assembleRelease
```

결과: `app/build/outputs/apk/release/app-release.apk`.

배포용 키를 직접 사용하는 경우 다음 환경 변수를 설정합니다: `PUSHFILTER_KEYSTORE`, `PUSHFILTER_KEYSTORE_PASSWORD`, `PUSHFILTER_KEY_ALIAS`, `PUSHFILTER_KEY_PASSWORD`. 설정하지 않으면 개발용 키로 서명합니다. 키를 Git에 추가하지 마세요.

CI는 테스트·Lint·빌드를 수행합니다. 자동 릴리스는 비활성화했으며 수동 Release 워크플로는 별도 서명용 GitHub Secrets를 설정한 경우에만 사용할 수 있습니다. v0.1.0 첨부 APK는 로컬에서 검증 후 업로드한 파일입니다.

## 검증 범위

단위 테스트와 Android Lint 및 debug/release 빌드로 검증합니다. 이전 테스트 설치본에서 Galaxy S25 Ultra / One UI 9.0의 키워드 일치 알림 삭제·삭제 확인 기록·제외 앱 동작을 사용자가 확인했습니다. 이번 공개 APK 전체 기능, 재부팅·장시간 대기 및 모든 앱의 소리/팝업 동작에 대한 실기기 검증은 완료되지 않았습니다.

## 원본 및 라이선스

[Rai220/push-filter](https://github.com/Rai220/push-filter)의 MIT 소스를 기반으로 UI와 키워드·기록 기능을 변경했습니다. 원본 저작권과 라이선스는 유지합니다.

- 원본: Copyright (c) 2026 Konstantin Krestnikov
- 변경분: Copyright (c) 2026 rudals252
- 본 프로젝트: [MIT](LICENSE)
- 원본 러시아어 설명과 스크린샷은 이력·참고 자료이며 현재 UI와 다릅니다.
