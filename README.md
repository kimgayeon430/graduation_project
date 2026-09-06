# Travel Mission

> 여행지에서 미션을 수행하고 GPS와 사진으로 인증하며 포인트와 순위를 쌓는 게이미피케이션 Android 애플리케이션

숙명여자대학교 인공지능공학부 졸업 프로젝트로 개발한 Android 앱입니다.  
사용자는 여행지 미션을 탐색하고 단계별 인증을 완료해 포인트를 획득할 수 있으며, 관리자는 앱 안에서 미션과 사용자 권한을 관리할 수 있습니다.

## 주요 기능

### 사용자

- Firebase Authentication 기반 회원가입, 로그인 및 로그아웃
- 로그인 없이 둘러볼 수 있는 게스트 진입
- 회원가입 직후 여행 취향(투어·맛집·체험·쇼핑) 복수 선택 및 저장
- 전체 미션 목록 조회 및 상세 정보 확인
- 미션별 GPS 위치 인증 (목표 지점 반경 200m 이내)
- 위치 인증 후 카메라 촬영 → 미리보기 → Firebase Storage 업로드로 사진 인증
- 인증 단계별 포인트 지급 및 진행 상태 저장 (중복 지급 방지)
- 진행 중인 미션 확인 및 이어서 수행
- 홈에서 선호 카테고리를 우선한 규칙 기반 미션 추천 (완료한 미션 제외)
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
| Backend | Firebase Authentication, Cloud Firestore, Cloud Storage |
| Image Loading | Coil |
| Build | Gradle Kotlin DSL, Version Catalog |
| Architecture | 미션 수행 기능을 ViewModel · Repository(인터페이스/Firebase 구현) · 순수 도메인 로직으로 분리 |
| Testing | JUnit4 단위 테스트 (도메인 규칙) |

## 미션 인증 흐름

미션 수행은 두 단계로 나뉘며, 각 단계의 판정·보상·완료 규칙은 Firebase에 의존하지 않는 순수 Kotlin(`domain/`)으로 분리되어 단위 테스트로 검증합니다.

1. **1단계 · GPS 위치 인증**
   - 위치 권한 허용 후 현재 좌표를 획득하고 목표 지점과의 거리를 계산합니다. (Haversine)
   - 반경 200m 이내이면 `user_missions` 문서를 갱신하고 1단계 보상을 지급합니다.
   - 1단계 보상 = `min(미션 포인트, 100)`
2. **2단계 · 사진 인증**
   - 카메라로 사진을 촬영하고 미리보기로 확인합니다. (`FileProvider` + `TakePicture`)
   - 사진을 `mission_photos/{missionId}/{uid}_{timestamp}.jpg` 로 업로드합니다.
   - 업로드가 성공한 뒤에만 트랜잭션으로 미션을 `Completed` 처리하고 2단계 보상을 지급하며, `photoUrl`·`photoStoragePath`·`photoVerified`·`photoUploadedAt` 을 저장합니다.
   - 2단계 보상 = `미션 포인트 - 1단계 보상`
   - 업로드나 저장이 실패하면 미션은 완료되지 않으며, 재시도해도 포인트는 한 번만 지급됩니다.

## 개인화 추천 (규칙 기반)

AI 모델 없이 현재 데이터만으로 설명 가능한 규칙으로 홈의 추천 미션을 계산합니다. (`MissionRecommender`)

1. id가 없거나 이미 완료한 미션은 후보에서 제외합니다.
2. 사용자의 선호 카테고리(`users/{uid}.preferences`)에 속한 미션을 먼저 배치합니다.
3. 자리가 남으면 나머지 카테고리 미션으로 채웁니다.
4. 진행 중인 미션이 있으면 추천 대신 해당 미션을 노출합니다.
5. 추천 결과가 없거나 조회에 실패하면 빈 화면 대신 안내 카드를 표시합니다.

신규 가입자는 회원가입 직후 취향 선택 화면으로 이동하고, 기존 사용자는 `preferences` 가 없을 때만 이 화면을 거칩니다.

## 프로젝트 구조

```text
app/src/main/java/smu/ai/graduation_project
├── data/           # Repository 인터페이스와 Firebase 구현 (미션 수행 데이터 접근)
├── domain/         # Firebase 비의존 순수 로직 (거리·보상·완료·취향·추천 규칙)
├── model/          # Mission, UserRank 등 데이터 모델
├── navigation/     # 화면 경로 및 내비게이션 정의
└── ui/
    ├── admin/      # 미션·사용자 관리 화면
    ├── components/ # 공통 Compose 컴포넌트
    ├── screens/    # 랜딩·로그인·홈·미션·취향 선택·랭킹·프로필 화면 및 ViewModel
    └── theme/      # 색상, 타이포그래피, 앱 테마

app/src/test/java/smu/ai/graduation_project
└── domain/         # 도메인 규칙 단위 테스트 (JUnit4)
```

### 주요 도메인 모듈

| 모듈 | 책임 |
| --- | --- |
| `GeoDistance` | 두 좌표 사이 거리 계산 (Haversine) |
| `LocationVerification` | 허용 반경(기본 200m) 이내 여부 판정 |
| `MissionRewardPolicy` | 1·2단계 보상 계산과 중복 지급 방지 규칙 |
| `MissionCompletion` | 사진 인증 가능 여부·완료 처리 결과(`resolve`) 계산 |
| `TravelPreference` | 취향 카테고리 정의, 최소 1개 선택 규칙, 저장용 정규화 |
| `MissionRecommender` | 선호 우선·완료 제외·부족분 보충 규칙 기반 추천 |

## Firestore · Storage 데이터

| 경로 | 주요 필드 |
| --- | --- |
| `users/{uid}` | `nickname`, `mail`, `points`, `level`, `preferences[]` |
| `missions/{id}` | `title`, `desc`, `category`, `points`, `imageUrl`, `location`(GeoPoint) |
| `user_missions/{id}` | `userId`, `missionId`, `status`, `progress`, `stage1RewardGranted`, `stage2RewardGranted`, `photoUrl`, `photoStoragePath`, `photoVerified`, `photoUploadedAt`, `completedAt` |
| `admins/{uid}` | `email`, `name` |
| Storage `mission_photos/{missionId}/{uid}_{timestamp}.jpg` | 사진 인증 이미지 |

## 실행 방법

### 요구 환경

- Android Studio
- JDK 11 이상 (Gradle 실행에는 JDK 17 이상 권장)
- Android SDK 26 이상
- Email/Password 인증, Firestore, Storage가 활성화된 Firebase 프로젝트

### 실행

```bash
git clone https://github.com/kimgayeon430/graduation_project.git
cd graduation_project
```

1. Android Studio에서 프로젝트 루트 폴더를 엽니다.
2. Firebase Console에서 Android 앱을 등록하고 Authentication·Firestore·Storage를 활성화합니다.
3. 발급받은 `google-services.json`을 `app/` 폴더에 추가합니다.
4. Storage 보안 규칙에서 인증된 사용자가 `mission_photos/` 경로에 이미지를 업로드할 수 있도록 허용합니다.
5. Gradle Sync를 완료합니다.
6. 에뮬레이터 또는 Android 기기에서 앱을 실행합니다.

### 단위 테스트

```bash
./gradlew :app:testDebugUnitTest
```

> 사용자 홈 경로에 한글 등 비 ASCII 문자가 있으면 Gradle 테스트 워커가 실행되지 않습니다.
> 이 경우 `GRADLE_USER_HOME` 을 ASCII 경로로 지정해 실행하세요. 예: `GRADLE_USER_HOME=D:\gradle-home ./gradlew :app:testDebugUnitTest`

## 구현 화면

- 랜딩 및 로그인·회원가입
- 여행 취향 선택
- 홈(선호 기반 추천)과 미션 목록·상세
- GPS·사진 기반 미션 수행
- 진행 중인 미션
- 포인트 랭킹 및 프로필
- 관리자 미션 관리
- 관리자 사용자 관리

## 향후 개선 계획

- 사진 인증 부정 방지(촬영 시각·위치 메타데이터 검증)와 관리자 검수 흐름
- 수행 이력·시간대·인기도까지 반영한 추천 고도화
- ViewModel·Repository 패턴을 홈·목록·관리자 등 나머지 화면으로 확대
- Firestore·Storage 보안 규칙 정비 및 서버 사이드 포인트 검증
- Compose UI 테스트와 Repository 계약 테스트 추가
