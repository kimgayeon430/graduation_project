# Travel Mission

> 여행지에서 미션을 수행하고 GPS와 사진으로 인증하며 포인트와 순위를 쌓는 게이미피케이션 Android 애플리케이션

숙명여자대학교 인공지능공학부 졸업 프로젝트로 개발한 Android 앱입니다.  
사용자는 여행지 미션을 탐색하고 단계별 인증을 완료해 포인트를 획득할 수 있으며, 관리자는 앱 안에서 미션과 사용자 권한을 관리할 수 있습니다.

## 주요 기능

### 사용자

- Firebase Authentication 기반 회원가입, 로그인 및 로그아웃
- 로그인 없이 둘러볼 수 있는 게스트 진입
- 전체 미션 목록 조회 및 상세 정보 확인
- 미션별 GPS 위치 인증
- 위치 인증 후 사진 인증 단계 진행
- 인증 단계별 포인트 지급 및 진행 상태 저장
- 진행 중인 미션 확인 및 이어서 수행
- 누적 포인트 기반 사용자 랭킹
- 프로필에서 포인트, 레벨 및 미션 현황 확인

### 관리자

- Firestore의 관리자 계정을 기반으로 사용자와 관리자 화면 분리
- 미션 등록, 수정 및 삭제
- 전체 사용자와 미션 진행 현황 조회
- 사용자별 포인트, 레벨, 완료·진행 미션 확인
- 관리자 권한 부여 및 해제

## 기술 스택

| 구분 | 기술 |
| --- | --- |
| Language | Kotlin |
| UI | Jetpack Compose, Material 3 |
| Navigation | Navigation Compose |
| Backend | Firebase Authentication, Cloud Firestore |
| Image Loading | Coil |
| Build | Gradle Kotlin DSL, Version Catalog |
| Architecture | 화면·모델·데이터·내비게이션 패키지 분리 |

## 서비스 흐름

1. 회원가입 또는 로그인 후 앱에 진입합니다.
2. 홈이나 미션 목록에서 수행할 미션을 선택합니다.
3. GPS 권한을 허용하고 지정 위치를 인증합니다.
4. 위치 인증을 완료하면 사진 인증 단계를 진행합니다.
5. 단계별 포인트가 사용자 정보에 반영됩니다.
6. 획득한 포인트와 미션 기록은 프로필과 랭킹에서 확인할 수 있습니다.

## 프로젝트 구조

```text
app/src/main/java/smu/ai/graduation_project
├── data/           # Firebase 및 미션·사용자 데이터 접근
├── model/          # Mission, UserRank 등 데이터 모델
├── navigation/     # 화면 경로 및 내비게이션 정의
└── ui/
    ├── admin/      # 미션·사용자 관리 화면
    ├── components/ # 공통 Compose 컴포넌트
    ├── screens/    # 로그인, 홈, 미션, 랭킹, 프로필 화면
    └── theme/      # 색상, 타이포그래피, 앱 테마
```

## 실행 방법

### 요구 환경

- Android Studio
- JDK 11 이상
- Android SDK 26 이상
- Firebase 프로젝트

### 실행

```bash
git clone https://github.com/kimgayeon430/graduation_project.git
cd graduation_project
```

1. Android Studio에서 프로젝트 루트 폴더를 엽니다.
2. Firebase Console에서 Android 앱을 등록합니다.
3. 발급받은 `google-services.json`을 `app/` 폴더에 추가합니다.
4. Gradle Sync를 완료합니다.
5. 에뮬레이터 또는 Android 기기에서 앱을 실행합니다.

## 구현 화면

- 랜딩 및 로그인·회원가입
- 홈과 미션 목록·상세
- GPS·사진 기반 미션 수행
- 진행 중인 미션
- 포인트 랭킹 및 프로필
- 관리자 미션 관리
- 관리자 사용자 관리

## 향후 개선 계획

- 실제 이미지 업로드 및 Firebase Storage 연동 강화
- 사용자 선호와 수행 이력을 활용한 개인화 미션 추천
- 위치 인증 기준 및 부정 인증 방지 로직 고도화
- ViewModel과 Repository 계층을 통한 상태 관리 개선
- 단위 테스트와 UI 테스트 확대
