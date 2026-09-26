# 졸업 프로젝트 보고서 (초안)

> 이 문서는 초안이다. `[TODO]` 표시는 실제 수치·인용·스크린샷으로 채워야 한다.
> 제출용(HWP/Word)으로 옮기기 전 지도교수 피드백을 반영한다.

- **프로젝트명**: Travel Mission — 온디바이스 사진 인증을 적용한 여행지 미션 게이미피케이션 앱
- **소속**: 숙명여자대학교 인공지능공학부
- **작성자**: 김가연 (2210723), 강규린 (2215987)
- **지도교수**: 김철연
- **개발 기간**: 2026.03 ~ 2026.09

---

## 1. 프로젝트 개요

### 1.1 배경 및 목적

여행지에서 정해진 미션을 수행하고 GPS와 사진으로 인증하면 포인트와 순위를 얻는 게이미피케이션
Android 애플리케이션을 개발한다. 사용자는 취향에 맞는 미션을 추천받아 탐색하고 단계별 인증을
완료하며, 관리자는 앱 안에서 미션과 사용자 권한을 관리한다.

본 프로젝트의 인공지능 핵심 기여는 **미션 사진 인증을 온디바이스 비전 모델로 자동 검증**하는
기능이다. 초기 구현에서는 사진을 업로드만 하면 통과되어 인증 신뢰도가 낮았고, 개인화 추천도
규칙 기반에 그쳐 직접 학습·평가한 모델이 없었다. 이를 보완해 게이미피케이션의 무결성을 높인다.

### 1.2 목표

1. 여행 미션 탐색–수행–인증–보상의 전체 사이클을 갖춘 Android 앱 구현
2. 사진 인증에 온디바이스 이미지 분류 모델을 도입해 부적절한 사진을 걸러내는 자동 검증
3. Firebase 에 의존하지 않는 순수 도메인 로직 분리와 단위 테스트로 인증·보상 규칙의 정확성 보장

### 1.3 개발 환경

| 구분 | 내용 |
| --- | --- |
| 언어 | Kotlin 2.2.10 |
| UI | Jetpack Compose (BOM 2026.02.01), Material 3, Compose Compiler 플러그인 |
| Navigation | Navigation Compose (루트 그래프 + 하단 탭 그래프 2단 구성) |
| Backend | Firebase Authentication(Email/Password), Cloud Firestore |
| 지도 | 네이버 지도 SDK (`com.naver.maps:map-sdk`) |
| 이미지 저장 | Supabase Storage (public 버킷 + anon 업로드 정책) |
| 이미지 로딩 | Coil |
| Build | Gradle 9.4.1 (Kotlin DSL), Version Catalog, AGP 9.2.0, `compileSdk 36` / `minSdk 26` / `targetSdk 36` |
| AI 학습 | Python, HuggingFace `transformers`/`datasets`/`peft`, Colab GPU |
| AI 추론 | ONNX Runtime (`com.microsoft.onnxruntime:onnxruntime-android` 1.29.0), `abiFilters` 로 `arm64-v8a`/`x86_64` 만 포함 |
| 협업 | 맥(코드 편집) ↔ 윈도우(빌드·학습), GitHub 동기화 |

---

## 2. 주요 기능

### 2.1 사용자 기능

- Firebase Authentication 기반 회원가입·로그인·로그아웃, 로그인 없이 둘러보는 게스트 진입
- 회원가입 직후 여행 취향(투어·맛집·체험·쇼핑) 복수 선택 및 저장, 기존 사용자는 `preferences` 유무를 판정해 취향 화면을 건너뛰거나 거침
- 전체 미션 목록·상세 조회, 미션 목록 ↔ 네이버 지도 전환(좌표가 있는 미션을 마커로)
  - 지도 마커는 이 사용자의 미션 진행 상태(수행 전·수행 중·완료)에 따라 색이 다르고, 사진 인증을 완료한 미션의 마커를 누르면 실제로 제출한 인증 사진을 정보창에서 볼 수 있다 (4.2절)
  - 미션 상세 화면에서 위치 권한이 있으면 현재 위치로부터의 거리를 보여주고, "길찾기" 버튼으로 기기에 설치된 지도 앱의 경로 안내로 바로 이동한다 (4.2절)
- 미션별 GPS 위치 인증(목표 반경 200m 이내)
- 위치 인증 후 카메라 촬영 → 미리보기 → 사진 인증
- 인증 단계별 포인트 지급 및 진행 상태 저장(중복 지급 방지), 진행 중 미션 이어서 수행
- **PASS 판정 직후 성취 연출** — 포인트 상승·컨페티·햅틱에 이어, 이번 완료로 레벨이 올랐으면 "LEVEL UP!" 배너를, 새 배지를 얻었으면 배지 알림을 함께 보여준다 (6.8.4절)
- **미션 성공 화면 하단 다음 미션 추천 카드** — "다음에는 이런 미션 어때요?" 카드 1개, 기존 추천 로직을 그대로 재사용 (4.5.1절)
- 홈에서 취향·완료 이력 기반 미션 추천(규칙 점수 + 완료 로그 학습 re-ranker 하이브리드, 완료한 미션 제외), 추천 카드에 현재 위치로부터의 실제 거리 표시 (4.5절)
- 누적 포인트 기반 사용자 랭킹
- **누적 포인트 기반 여행 레벨(Lv.1~5, 구간별 진행률)과 여행 배지 3종**(첫 발자국·주간 탐험가·취향 발견)을 프로필에서 확인 — 미획득 배지도 조건과 함께 표시 (4.4.1·4.4.2절)
- 마이페이지에서 **보유 포인트를 누르면 적립 내역**(미션별 위치·사진 인증 보상)을 확인
- 마이페이지에서 **한국어 / English / 日本語 3개 언어 UI 전환** — 하단 메뉴바·관리자 화면을 포함한 앱 전체 화면과 미션 콘텐츠(제목·설명)가 즉시 선택한 언어로 전환 (4.7절)
- 사진 인증 후 **AI 판정 결과를 전체 화면으로 확인** — 제출한 사진, 판정 상태(통과·검토중·반려), 요구/예측 카테고리, AI 신뢰도, 포인트 지급 여부를 한 화면에서 보고 판정별로 다른 다음 행동(완료 확인 / 다시 촬영 / 미션 내용 보기)을 선택 (6.8절)
- **사용자 미션 제안** — 제목·설명·카테고리·예상 소요시간·대표 이미지(갤러리 선택, 선택 사항)·장소(지도 탭 선택, 선택 사항)를 입력해 새 여행 미션을 제안. 포인트는 사용자가 정하지 못하고 관리자 승인 시 확정되며, 관리자 검수(검수중 → 승인/수정요청/반려)를 통과해야 다른 사용자에게 노출된다 (4.8절)
- **내가 만든 미션** 전용 화면(마이페이지 진입) — 제안한 미션의 검수 상태를 확인하고, 수정 요청된 제안은 고친 뒤 재제출. 승인된 제안은 좋아요·찜·수행 횟수 같은 Creator 통계를 보여주고 실제 미션 상세로 연결. "+ 미션 제안하기"로 바로 새 제안 작성 (4.8절)
- **좋아요 · 찜** — 모든 미션(관리자 생성/사용자 제안 모두)에 ❤️ 좋아요(공개 반응)와 🔖 찜(개인 저장)을 남기고 취소할 수 있다. 마이페이지에서 **찜한 미션** 전용 목록 확인 (4.8절)

### 2.2 관리자 기능

- Firestore 관리자 계정을 기반으로 사용자/관리자 화면 분리
- 미션 등록·수정·삭제(제목·설명·카테고리·포인트·이미지·위치 좌표)
- 전체 사용자·미션 진행 현황 조회, 사용자별 포인트·레벨·완료/진행 미션 확인
- **사진 검수 큐**: 자동 판정이 애매한 완료 건을 승인하거나 반려(보상 회수 후 재인증 요청)
- **미션 제안 검수 큐**: 사용자가 제안한 미션을 검토해 승인(최종 포인트 결정)·수정 요청(사유 입력)·반려(사유 입력) — 사유는 제안자에게 "내가 만든 미션" 화면에서 그대로 노출 (4.8절)
- 관리자 권한 부여·해제

---

## 3. 시스템 구조

### 3.1 아키텍처

미션 수행 기능을 세 계층으로 분리한다.

| 계층 | 역할 | 예 |
| --- | --- | --- |
| `ui/` (ViewModel + Compose) | 화면 상태 보유, Android 프레임워크 연동(위치·카메라) | `MissionPerformViewModel` |
| `data/` (Repository) | 데이터 접근 추상화, Firebase/Supabase 구현 | `MissionRepository`, `FirebaseMissionRepository` |
| `domain/` (순수 Kotlin) | Firebase 비의존 판정·보상·추천 규칙, 단위 테스트 대상 | `LocationVerification`, `MissionRewardPolicy` |

```text
app/src/main/java/smu/ai/graduation_project
├── MainActivity.kt   # 루트/메인 NavHost, 하단 탭, 인증·권한 게이트
├── data/             # Repository 인터페이스·Firebase 구현, Supabase Storage, PhotoVerifier·OnnxPhotoVerifier
├── domain/           # 거리·보상·완료·취향·추천·사진 인증 판정 (순수 로직)
├── model/            # Mission, UserRank 등 데이터 모델
├── navigation/       # 화면 경로·내비게이션 정의
└── ui/
    ├── admin/        # 미션·사용자 관리, 사진 검수 화면
    ├── components/   # 공통 Compose 컴포넌트
    ├── screens/      # 랜딩·로그인·홈·미션·수행·취향·랭킹·프로필·포인트 내역 및 ViewModel
    └── theme/        # 색상·타이포그래피·테마

ml/                   # 사진 인증 모델 학습·평가·ONNX export (앱 빌드와 분리)
docs/                 # 보고서 등 문서
firestore.rules       # Firestore 보안 규칙
firebase.json         # Firebase CLI 설정 (규칙 배포)
```

`data/` 계층 주요 요소: `MissionRepository`(인터페이스)·`FirebaseMissionRepository`(구현), `SupabaseStorage`(사진 업로드), `PhotoVerifier`(추론 인터페이스)·`OnnxPhotoVerifier`(ONNX Runtime 구현)·`PhotoGate`(업로드 전 결정).

### 3.2 주요 도메인 모듈

| 모듈 | 책임 |
| --- | --- |
| `GeoDistance` | 두 좌표 사이 거리 계산(Haversine) + 사람이 읽는 거리 표기(`format`, "320m"/"1.2km") |
| `LocationVerification` | 허용 반경(기본 200m) 이내 여부 판정 |
| `MissionRewardPolicy` | 1·2단계 보상 계산과 중복 지급 방지 |
| `MissionCompletion` | 사진 인증 가능 여부·완료 처리 결과(`resolve`) 계산 |
| `TravelPreference` | 취향 카테고리 정의, 최소 1개 선택 규칙, 저장용 정규화 |
| `MissionFeatures` | 미션 추천 신호 6개(0~1 정규화) 계산. 규칙·학습이 공유 |
| `MissionScorer` | 신호를 `RecommendationWeights` 로 가중합 + 근거 문구 (규칙 점수) |
| `LearnedReranker` | 완료 로그로 학습한 로지스틱 회귀로 완료 확률 추정 |
| `MissionRecommender` | 후보 필터 + 점수 정렬 + 다양성 감점으로 상위 N건 추천 |
| `PhotoVerification` | 온디바이스 모델의 라벨별 점수 → 통과 / 재촬영 / 관리자 검수 판정 |
| `TravelLevelPolicy` | 누적 포인트 → 여행 레벨(Lv.1~5) + 현재 구간 진행률 계산(4.4.1) |
| `BadgeUnlockEvaluator` | 완료 전/후 카운터 쌍 → 이번에 처음 조건을 충족한 배지 판정(4.4.2) |
| `WeekBoundary` | "이번 주"(월요일 0시~) 경계 계산 — 배지·주간 진행률·성취 연출이 공유 |
| `MissionReviewStatus` | 사용자 제안 미션의 검수 상태(`pending`/`approved`/`changes_requested`/`rejected`) 판정. 필드가 없는 기존 관리자 미션도 공개 대상으로 취급(하위 호환), 재제출 가능 여부(`changes_requested`만) 판정 (4.8절) |

### 3.3 앱 내비게이션

두 개의 `NavHost` 로 나뉜다.

| 그래프 | 경로 | 설명 |
| --- | --- | --- |
| 루트 | `landing` → `signup` / `login` → `gate` / `preference` → `main` | 인증·온보딩. 로그인 상태면 `gate`, 아니면 `landing` 시작 |
| 메인(하단 탭) | `home`, `mission`, `add`(관리자), `ranking`, `profile` | `main` 진입 후. 상세·수행·관리자 화면은 하위 경로 |

- `gate` 는 로그인된 기존 사용자의 `users/{uid}.preferences` 유무를 확인해 `main` 또는 `preference` 로 분기한다. (조회 실패 시 앱을 막지 않고 `main` 진행)
- `admins/{uid}` 문서가 있는 사용자에게만 하단 탭에 **Admin** 이 보이고, 관리자 경로는 진입 시 권한을 재확인한다.
- 미션 제안/검수/마이페이지 하위 경로: `mission_propose`(신규 제안) · `mission_propose/{missionId}`(수정요청 재제출) · `profile/my-missions`(내가 만든 미션) · `profile/bookmarks`(찜한 미션) · `admin/missions/review`(관리자 제안 검수, 관리자 전용). 기존 `admin/missions`(미션 관리) 목록은 승인된 미션만 보여주도록 분리해, 검수 대기 중인 제안과 뒤섞이지 않게 했다.

### 3.4 데이터 모델 (Firestore · Supabase Storage)

| 경로 | 주요 필드 |
| --- | --- |
| `users/{uid}` | `nickname`, `mail`, `points`, `level`(가입 시 1회 기록, 미사용 레거시 — 4.4.1), `preferences[]`, `completedMissionsTotal`(Long, optional), `completedByCategory`(Map, optional), `completedByWeek`(Map, optional), `badges[]`(optional, 각 원소 `badgeId`/`unlockedAt`/`relatedCategory`/`relatedWeek`/`isNew` — 4.4.2) |
| `missions/{id}` | `title`, `desc`, `category`, `points`, `imageUrl`, `location`(GeoPoint), `completionCount`, `estimatedMinutes`(Int, optional — 4.5.1), `photoEmbedding`/`photoEmbeddings`(6.7절), `creatorId`/`creatorName`(String, optional — 사용자 제안 미션만, 4.8절), `reviewStatus`(String, optional: `pending`/`approved`/`changes_requested`/`rejected` — 없으면 `approved` 취급), `reviewNote`(String, optional), `likeCount`/`bookmarkCount`(Int, optional, 4.8절) |
| `user_missions/{id}` | `userId`, `missionId`, `status`, `progress`, `stage1RewardGranted`, `stage1RewardPoints`, `stage1VerifiedAt`, `stage2RewardGranted`, `stage2RewardPoints`, `photoUrl`, `photoStoragePath`, `photoVerified`, `photoUploadedAt`, `completedAt`, `photoNeedsReview`, `photoVerifyScore`, `photoVerifyLabel`, `photoVerifyModelVersion` |
| `admins/{uid}` | `email`, `name` |
| `mission_likes/{uid}_{missionId}` | `userId`, `missionId`, `createdAt`. 문서 ID를 `uid_missionId` 로 고정해 중복 좋아요를 구조적으로 막는다(4.8절) |
| `mission_bookmarks/{uid}_{missionId}` | 위와 동일한 구조(개인 전용 찜) |
| Supabase `mission-photos/<missionKey>/{uid}_{timestamp}.jpg` | 사진 인증 이미지 (공개 URL). `missionKey` 는 `missionId` 를 Supabase 스토리지 키 규칙에 맞춰 ASCII 로 정규화한 값(4.3절) |
| Supabase `mission-photos/proposals/{uid}_{timestamp}.jpg` | 사용자가 미션을 제안할 때 고른 대표 이미지(같은 버킷, 경로만 구분) |

`users/{uid}` 의 레벨·배지 관련 필드는 전부 optional 로 추가했다 — 필드가 없는 기존 사용자 문서도 0/빈 값으로 취급되어 그대로 동작한다(4.4.2절). `completedByWeek` 의 키는 ISO 주차가 아니라 "이번 주 월요일 0시" 기기 로컬 epoch millis 문자열이다(`domain/WeekBoundary`). `missions/{id}` 의 `creatorId`/`reviewStatus`/`likeCount`/`bookmarkCount` 도 전부 optional 이라, 기존 관리자 생성 미션 문서는 필드 추가 없이 그대로 "승인됨·제안자 없음·좋아요 0"으로 취급된다(하위 호환).

### 3.5 필요 권한

| 권한 | 용도 |
| --- | --- |
| `INTERNET` | Firebase·Supabase·지도 통신 |
| `ACCESS_FINE_LOCATION` / `ACCESS_COARSE_LOCATION` | 1단계 GPS 위치 인증, 추천의 거리 근접도 |
| `CAMERA` | 2단계 사진 인증 촬영 |

### 3.6 Firestore 보안 규칙

클라이언트에서 직접 Firestore 를 읽고 쓰므로 `firestore.rules` 로 접근을 제한한다.

- **읽기**: 로그인 사용자에게 허용(미션은 게스트도 읽음). 목록·랭킹은 앱이 클라이언트에서 구성한다.
- **쓰기**: 소유권과 문서 형태를 강제한다.
  - `admins/{uid}`·`missions` 삭제·일반 수정: 관리자(`admins/{uid}` 문서 존재)만
  - `missions` 의 `completionCount`/`likeCount`/`bookmarkCount` 필드만은 로그인 사용자가 갱신 가능(완료·좋아요·찜 시 인기도 신호)
  - `missions` 생성: 관리자는 제한 없음. 일반 사용자는 본인이 `creatorId`이고 `reviewStatus=pending`·`points=0` 인 문서만 생성 가능 — 포인트/승인 상태를 스스로 정하거나 위조할 수 없다(4.8절)
  - `missions` 수정(제안자 재제출): 본인이 제안한 미션이 `changes_requested` 상태일 때만, `reviewStatus` 를 `pending` 으로 되돌리며 내용을 고칠 수 있다. 이때도 `points`/`creatorId`/카운터 필드는 바꿀 수 없다 — `rejected` 상태는 재제출 대상이 아니다
  - `mission_likes`/`mission_bookmarks`: 문서 ID를 `{uid}_{missionId}` 형식으로 강제해 중복 반응을 구조적으로 막는다. 생성/삭제는 본인 문서만, 좋아요는 공개 읽기·찜은 본인만 읽기
  - `users/{uid}`: 본인 또는 관리자만 수정, 삭제 불가
  - `user_missions/{id}`: 생성은 본인 문서만, 수정은 본인 또는 관리자. (예전엔 "사용자는 자기 `photoNeedsReview` 를 true→false 로 되돌릴 수 없음" 조건도 있었으나, 검수 대기 중 재인증해 정상적으로 `PASS` 가 나온 흐름까지 함께 막는 버그가 실기기에서 드러나 제거했다 — 6.8.3)
- **테스트**: `firestore-tests/` 에서 에뮬레이터 + `@firebase/rules-unit-testing` 으로 38건 검증(8.1절) — 사용자 미션 제안·좋아요·찜 관련 18건 포함.
- **한계**: 서버(Cloud Functions)가 없어 포인트 지급/회수, `likeCount`/`bookmarkCount` 값 자체는 완전히 검증하지 못한다(문서 존재로 "반응 여부"는 강제하지만, 카운터 필드 자체를 임의로 증가시키는 것까지는 못 막음 — 기존 `completionCount` 와 같은 한계). 서버측 검증은 향후 과제다. (10장)

---

## 4. 기능 상세 설계 및 구현

### 4.1 인증 및 온보딩

- Firebase Authentication(Email/Password)로 회원가입·로그인. 게스트는 인증 없이 목록을 둘러본다.
- 신규 가입자는 회원가입 직후 취향 선택 화면으로 이동한다. 취향은 `TravelPreference` 규칙(최소 1개, 저장용 정규화)을 거쳐 `users/{uid}.preferences` 에 저장된다.
- 기존 사용자는 `gate` 에서 `preferences` 가 없을 때만 취향 화면을 거친다.

### 4.2 미션 목록 및 지도

- 미션 목록 화면 우상단의 **지도 보기 / 목록 보기** 토글로 리스트와 네이버 지도를 번갈아 본다. (`MissionMapScreen`)
- 위도·경도가 모두 유효한 미션만 지도에 표시하고, 거의 같은 좌표의 미션은 하나의 마커로 묶어 정보창에 개수를 표시한다. 마커/정보창을 누르면 상세로 이동한다.
- `MapView` 를 Compose `AndroidView` 로 감싸고 `Lifecycle` 이벤트와 `rememberSaveable` 로 카메라 위치 등 상태를 화면 회전에도 유지한다.
- 네이버 지도 인증 키는 `local.properties` → `manifestPlaceholders` 로 주입되어 VCS 에 올라가지 않는다.
- 지도 마커 색으로 이 사용자의 미션 진행 상태(수행 전/중/완료)를 구분하고, 사진 인증을 완료한 미션은 정보창에 실제 인증 사진을 보여준다(6.8.4 이후 추가, README "미션 지도" 절).
- 미션 상세 화면(`MissionDetailScreen`)도 위치 권한이 있으면 현재 위치로부터의 거리를 칩으로 보여주고, "길찾기" 버튼으로 `Intent(ACTION_VIEW, geo:...)` 를 통해 기기에 설치된 지도 앱의 경로 안내로 넘어간다 — 앱 안에 실제 도로 기반 경로(폴리라인)를 그리려면 별도 Directions API 계약·키 발급이 필요해 이번 스코프에서는 제외하고, OS 표준 `geo:` URI 로 위임했다.

### 4.3 미션 인증 흐름

미션 수행은 두 단계이며, 각 단계의 판정·보상·완료 규칙은 순수 Kotlin(`domain/`)으로 분리되어 단위 테스트로 검증된다.

**1단계 · GPS 위치 인증**

- 위치 권한 허용 후 현재 좌표를 얻고 목표 지점과의 거리를 Haversine(`GeoDistance`)으로 계산한다.
- 반경 200m(`LocationVerification`) 이내이면 `user_missions` 를 갱신하고 1단계 보상을 지급한다.
- 1단계 보상 = `min(미션 포인트, 100)`

**2단계 · 사진 인증**

- 카메라로 사진을 촬영하고 미리보기로 확인한다. (`FileProvider` + `TakePicture`)
- **업로드 전에 온디바이스 모델(`PhotoVerifier`)로 사진을 분류하고 `PhotoVerification` 규칙으로 판정한다.** (6장 참조)
  - `REJECT` → 업로드하지 않고 재촬영을 안내
  - `PASS` / `NEEDS_REVIEW` → 사진을 Supabase Storage 에 업로드
- 업로드 성공 뒤에만 트랜잭션으로 미션을 `Completed` 처리하고 2단계 보상을 지급하며, `photoUrl`·`photoStoragePath`·`photoVerified`·`photoNeedsReview`·`photoVerifyScore`·`photoVerifyLabel`·`photoVerifyModelVersion`·`photoUploadedAt` 을 저장한다.
- 2단계 보상 = `미션 포인트 − 1단계 보상`
- 업로드나 저장이 실패하면 미션은 완료되지 않으며, 재시도해도 포인트는 한 번만 지급된다.
- 사용자가 처음 완료할 때 같은 트랜잭션에서 `missions/{id}.completionCount` 를 1 올린다. (추천 인기도 신호)

**Supabase Storage 연동 시 해결한 문제** (7.3절)

- 스토리지 객체 키는 ASCII 일부 문자만 허용한다. 미션 문서 ID 가 한글 제목인 경우 `InvalidKey` 로 업로드가 거부되어, 경로의 미션 폴더명을 `해시_ASCII정규화` 형태(`missionKey`)로 변환했다.
- 업로드 요청의 `x-upsert` 헤더를 켜면 Supabase 가 `UPDATE` 정책까지 요구해, anon `INSERT` 정책만 있는 버킷에서 RLS 로 거부된다. 객체 경로에 타임스탬프가 들어가 항상 유일하므로 `x-upsert` 를 끄고 새로 `INSERT` 한다.

### 4.4 포인트 · 레벨 · 배지 · 랭킹

- 보상 계산과 중복 지급 방지는 `MissionRewardPolicy` 에 모여 있으며, 포인트 지급은 Firestore 트랜잭션으로 원자적으로 처리된다.
- 누적 포인트 기준 사용자 랭킹을 제공하고, 프로필에서 포인트·레벨·완료/진행 미션 수를 보여 준다.
- **포인트 적립 내역**(`PointHistoryScreen`): 마이페이지의 보유 포인트를 누르면 미션별 위치·사진 인증 보상 내역을 최신순으로 보여 준다. 별도 원장 컬렉션 없이 `user_missions` 의 `stage1RewardGranted`/`stage1RewardPoints`/`stage1VerifiedAt`, `stage2RewardGranted`/`stage2RewardPoints`/`completedAt` 에서 재구성하며, 미션명은 `missions/{id}` 에서 조회한다. (`firestore.rules` 미배포 상태에서 새 컬렉션 추가 시 규칙 누락으로 리워드 트랜잭션이 깨질 위험을 피하기 위한 선택. 서버측 포인트 검증 도입 시 실제 원장으로 교체 — 10장)

#### 4.4.1 여행 레벨 (`TravelLevel` / `TravelLevelPolicy`)

가입 시 한 번 `"Lv.1"` 문자열로 써 두고 이후 갱신하지 않던 기존 `users.level` 필드는 실질적으로 죽은 데이터였다(레벨업 로직 자체가 없었음). 이를 대체해 **레벨을 Firestore 에 중복 저장하지 않고 누적 포인트에서 매번 계산**하는 순수 Kotlin 정책(`domain/TravelLevel.kt`)을 도입했다.

| 레벨 | 이름 | 최소 포인트 |
| ---: | --- | ---: |
| Lv.1 | 여행 새싹 | 0P |
| Lv.2 | 동네 탐험가 | 500P |
| Lv.3 | 도시 여행자 | 1,500P |
| Lv.4 | 숨은 명소 수집가 | 3,000P |
| Lv.5 | 마스터 트래블러 | 5,000P |

`LevelProgress` 는 레벨·현재 포인트·레벨 시작 포인트·다음 레벨 기준·**현재 레벨 구간 기준 진행률**·다음 레벨까지 필요 포인트를 담는다. 예를 들어 1,000P 는 Lv.2(500P) 구간 안이므로 진행률 `(1000-500)/(1500-500) = 50%`, 다음 레벨까지 500P — 전체 5,000P 기준이 아니라 구간 기준이라는 요구사항을 그대로 구현했다. 최고 레벨은 진행률 100%로 고정하고 "다음 레벨까지" 문구 대신 "최고 레벨을 달성했어요!" 를 보여준다. 프로필 화면과 미션 성공 성취 연출(6.8.4)이 같은 `LevelProgress`/`LevelProgressCard` 를 공유해 두 화면의 표시가 어긋나지 않는다. 단위 테스트 `TravelLevelPolicyTest`(경계값·구간 진행률·최고 레벨 6건)로 검증했다.

#### 4.4.2 여행 배지 3종 (`BadgeId` / `BadgeUnlockEvaluator`)

| 배지 | 조건 |
| --- | --- |
| 첫 발자국 | 누적 완료 미션 수가 처음 1개 이상 |
| 주간 탐험가 | 동일한 주(월요일 0시~)에 완료한 미션 수가 처음 5개 이상 |
| 취향 발견 | 같은 카테고리 완료 미션 수가 처음 3개 이상 |

**스키마**: `users/{uid}` 에 `completedMissionsTotal`(Long) / `completedByCategory`(Map<String,Long>) / `completedByWeek`(Map<String,Long>, 키는 "이번 주 월요일 0시" 기기 로컬 epoch millis) / `badges`(배열, 원소는 `badgeId`/`unlockedAt`/`relatedCategory`/`relatedWeek`/`isNew`) 를 **optional 필드로만** 추가했다. 기존 필드는 이름도 구조도 바꾸지 않아, 이 필드가 없는 기존 사용자 문서도 0/빈 값으로 취급되어 그대로 동작한다.

**원자성과 트랜잭션 제약**: Firestore 트랜잭션은 쿼리를 지원하지 않고 문서 단건 `get()` 만 가능하다. 그래서 "이번 주에 몇 개 완료했는가"를 매번 `user_missions` 를 쿼리해 셀 수 없다 — 대신 `users` 문서 위에 카운터를 얹고 완료 트랜잭션 안에서 `FieldValue.increment` 로 원자적으로 올리는 방식(`data/MissionRewardCounters.applyCompletion()`)을 택했다. before/after 카운터 쌍을 순수 함수 `BadgeUnlockEvaluator.evaluate()`(이번에 처음 문턱을 넘었는지만 판정)에 넘겨, 새로 조건을 충족한 배지가 있으면 같은 트랜잭션에서 함께 기록한다. 이 헬퍼는 사진 제출 즉시-완료 경로(`FirebaseMissionRepository.uploadPhotoAndComplete`)와 관리자 승인 경로(`AdminPhotoReviewScreen.approve()`) 양쪽에서 호출되어, 검수 대기를 거쳐 나중에 완료된 미션도 배지·카운터 계산에서 빠지지 않는다.

**중복 지급 방지**: 카운터·배지 갱신은 `MissionCompletion.Outcome.countTowardPopularity`(이 사용자가 이 미션을 처음 완료할 때만 true — 기존에 인기도 신호 `completionCount` 증가를 게이팅하던 것과 같은 플래그를 재사용)가 참일 때만 실행된다. 재제출·이미 완료된 미션의 중복 요청은 이 플래그가 false 라 자동으로 걸러진다. 배지는 이미 획득한 `badgeId` 목록으로 한 번 더 걸러 중복 생성을 막는다. 한 번의 완료로 여러 배지(예: 취향 발견 + 주간 탐험가)를 동시에 얻을 수 있으며, 화면은 개별 카드 대신 "새로운 배지 N개를 획득했어요!" 로 묶어 보여준다(좌우 넘기기 UI는 만들지 않았다 — 로직의 정확성을 UI 화려함보다 우선한다는 원칙에 따름). 단위 테스트 `BadgeUnlockEvaluatorTest`(문턱 통과 시점, 카테고리 미상 시 스킵, 동시 다중 획득 등 7건)로 검증했다.

**프로필 화면**: 레벨 카드와 "나의 여행 배지" 카드(획득: 보라 아이콘 + 획득일 / 미획득: 회색 실루엣 + 자물쇠 + 획득 조건 문구)를 기존 화면 레이아웃 안에 추가했다. 미획득 배지도 숨기지 않고 조건을 보여줘 사용자가 다음 목표를 알 수 있게 했다.

**한계**: `users` 쓰기 권한이 기존과 동일하게 "본인 또는 관리자" 라(3.6절), 포인트와 마찬가지로 이 카운터·배지도 서버 검증 없이는 클라이언트가 직접 조작할 수 있는 여지가 있다 — 새 한계가 아니라 기존 포인트 시스템의 한계를 그대로 물려받은 것이며, 10장의 서버 사이드 검증 계획에 함께 포함된다.

### 4.5 개인화 추천 (규칙 + 학습 하이브리드)

홈의 추천 미션 상위 3건은 **규칙 점수를 완료 로그로 학습한 re-ranker 로 다시 매겨** 만든다.
학습 모델(`assets/reranker.json`)이 없으면 규칙 점수만으로 정렬한다(콜드스타트).

**신호 6개** (`MissionFeatures`, 0~1 정규화 — 규칙·학습이 공유)

| 신호 | 의미 |
| --- | --- |
| `explicit_pref` | 미션 카테고리가 `preferences` 에 포함되면 1 |
| `implicit_affinity` | 그 카테고리를 완료한 비율 |
| `difficulty_fit` | 미션 포인트대가 사용자 레벨 기대치에 가까운 정도 |
| `proximity` | 현재 위치로부터의 근접도 (위치를 알 때만) — 점수 계산에만 쓰이고 화면엔 안 보이던 걸, 홈 추천 카드에 실제 거리 텍스트("320m"/"1.2km", `GeoDistance.format`)로 노출(2026-09-26) |
| `popularity` | 다른 사용자 완료 횟수 기반 인기도 |
| `time_of_day_fit` | 현재 시각이 미션 카테고리 활동 시간대(맛집=점심·저녁, 투어/체험=낮, 쇼핑=오후~저녁)에 맞는 정도 |

1. id 가 없거나 완료한 미션은 후보에서 제외한다.
2. 후보마다 **규칙 점수**(`MissionScorer`, `RecommendationWeights` 가중합 + 근거 칩)와
   **학습된 완료 확률**(`LearnedReranker`, 로지스틱 회귀)을 구한다.
3. `최종 = (1−λ)·규칙점수/최댓값 + λ·학습확률` (λ = `blend`, 기본 0.6).
4. "최종 − 다양성 감점 × 이미 뽑힌 같은 카테고리 수" 가 가장 높은 미션을 하나씩 3건 선택한다.
5. 근거 칩(`맛집 취향`, `자주 하는 유형`, `가까운 미션` 등)은 규칙 점수 것을 그대로 표시한다.
6. 진행 중인 미션이 있으면 추천 대신 노출한다. 결과가 없으면 안내 카드.

학습·평가는 8.3절, 파이프라인은 `ml/reco/`.

#### 4.5.1 미션 성공 화면의 다음 미션 추천

PASS 판정 결과 화면 하단에 "다음에는 이런 미션 어때요?" 카드 1개를 추가했다. 새 추천 로직을 만들지 않고 위 `MissionRecommender.recommendScored()` 를 그대로 재사용한다 — 카드 1개만 필요해 re-ranker 모델(`assets/reranker.json`) 로딩 없이 규칙 점수(`MissionScorer`)만으로 정렬한다. 완료한 미션 전체를 제외하는 것은 동일하고, 방금 완료한 미션은 Firestore 반영 지연에 대비해 id 로 한 번 더 명시적으로 제외한다. 추천이 없으면 빈 카드 대신 안내 문구("오늘의 미션을 모두 둘러봤어요.")를 보여준다.

두 가지는 스코프를 의도적으로 좁혔다.

- **거리**: 이 화면에서 사용자의 실시간 위치를 다시 구하지 않는다. 명세가 "계산 불가능하면 임의 값 대신 항목 자체를 생략" 을 허용하므로, 거리 행을 아예 표시하지 않는 쪽을 택했다 — 홈 화면처럼 위치를 상시 들고 있지 않은 화면에 위치 획득 로직을 새로 얹는 비용 대비 이득이 적다고 판단했다.
- **예상 소요 시간**: `Mission` 모델에 없던 정보라 바로 하드코딩하지 않고 `estimatedMinutes: Int?` optional 필드로 모델과 Firestore 스키마를 확장했다. 관리자 화면에 입력 필드를 추가해 값을 넣을 수 있게 했지만, 기존 미션 데이터는 전부 null 이라 이 행은 값이 있는 미션에서만 나타난다.

"이 미션 시작하기" 는 해당 미션 상세 화면으로 이동만 하고 자동으로 완료·참여 처리하지 않는다. "다음에 할게요" 는 기존 "홈으로" 버튼과 같은 목적지로 보낸다(새 목적지를 만들지 않고 이미 검증된 흐름을 재사용).

### 4.6 관리자 기능

- `admins/{uid}` 문서 유무로 관리자 화면을 노출한다.
- 미션 CRUD(위치 좌표 입력 포함), 사용자·미션 진행 현황 조회, 사진 검수 큐(6.5절), 미션 제안 검수 큐(4.8절).

### 4.7 다국어 지원 (i18n)

앱 UI와 미션 콘텐츠를 한국어·English·日本語 3개 언어로 전환할 수 있다. 두 가지 문제를 분리해서 다룬다.

**1) 앱 UI 문구** — `values/strings.xml`(한국어, 기본) / `values-en/strings.xml` / `values-ja/strings.xml` 리소스로 키 1:1 대응해 분리한다. 선택한 언어는 `LanguagePreference`(`SharedPreferences` `app_settings`)에 저장하고, `MainActivity.attachBaseContext` 가 `Context.wrapWithStoredLocale()` 로 `Configuration.setLocale()` 을 적용한 Context 를 감싸 리소스 해석에 반영한다. `LanguagePreference.set()` 은 저장 직후 현재 액티비티를 `recreate()` 해 화면을 바로 갱신한다.

- **`AppCompatDelegate.setApplicationLocales()` 를 쓰지 않은 이유**: 이 앱의 `MainActivity` 는 `ComponentActivity`(`AppCompatActivity` 가 아님)라 언어 변경 시 리소스가 자동으로 다시 갱신되지 않는다. `Configuration` 을 직접 감싸고 액티비티를 재생성하는 수동 방식이 더 확실했다.
- Compose 밖(ViewModel 등)에서는 `applicationContext.getString()` 이 `attachBaseContext` 의 Configuration 래핑을 타지 않아 시스템 언어로 고정되는 문제가 있어, 호출마다 `wrapWithStoredLocale()` 을 다시 적용하는 `Context.getLocalizedString()` 확장을 만들어 `MissionPerformViewModel` 등의 토스트·상태 문구에 썼다.
- 내부 로직이 쓰는 코드값(미션 카테고리 "투어"/"맛집"/"체험"/"쇼핑", 진행 상태 "진행중"/"완료"/"미 진행", 사진 인증 무효 라벨 "무효")은 Firestore·도메인 로직에서 항상 한국어 그대로 두고, 화면에 표시할 때만 `CommonComponents.kt` 의 `categoryLabel()`/`missionStatusLabel()` 로 번역한다. 필터링·판정 로직이 문자열 비교에 의존하므로 언어 전환이 내부 상태에 영향을 주지 않는다.

**2) 미션 콘텐츠(제목·설명)** — Firestore `missions/{id}` 문서에 `title`/`desc`(한국어, 필수)와 함께 `titleEn`/`descEn`, `titleJa`/`descJa` 를 선택적으로 둔다. `DocumentSnapshot.localizedString(field, language)` 이 현재 언어에 맞는 접미사 필드를 찾고, 값이 비어 있으면 한국어로 폴백해 번역이 안 된 미션도 빈 텍스트가 되지 않는다.

**검증 습관**: 리소스 파일 3개(ko/en/ja)의 키가 어긋나면 그 언어에서만 조용히 기본 리소스로 새거나(대부분 문제없이 한국어로 보임) 빌드 오류로 드러나지 않는 경우가 있어, 새 문구를 추가할 때마다 세 파일의 `<string name="...">` 키 집합을 diff 로 비교해 맞춘다. 실제로 일본어 지원을 추가하던 중 `perform_*` 토스트 문구 5개가 영어 리소스에서 누락된 것을 이 방법으로 발견했다.

**적용 범위**: 랜딩·로그인·회원가입·취향 선택, 홈·미션 목록·미션 상세·미션 지도·미션 수행, 마이페이지·포인트 내역·진행 중 미션·완료한 미션·랭킹, 하단 메뉴바, 관리자 5개 화면(홈·미션 목록·미션 편집·사용자 관리·사진 검수) 전체.

**남은 한계**: 네이버 지도 `InfoWindow.DefaultTextAdapter` 의 말풍선 텍스트는 Compose 컴포저블이 아니라 콜백이라 `context.getLocalizedString()` 으로 별도 처리한다. 사진 인증 거절 사유(`PhotoVerification.reason`)는 도메인 계층이 Android `Context` 에 의존하지 않는 순수 Kotlin 이라 처음에는 직접 번역할 수 없었는데, 6.8절에서 결과 화면을 새로 만들며 원인을 `RejectReasonCode` enum 으로만 도메인에서 분류하고 실제 다국어 문구는 UI 레이어 `strings.xml` 에서 고르도록 정리해 해소했다.

### 4.8 사용자 미션 제안 · 관리자 검수 · 좋아요/찜

관리자만 만들 수 있던 미션을 일반 사용자도 제안할 수 있게 열되, 아무 검증 없이 바로 노출되면 스팸·부적절한 콘텐츠·임의 포인트 설정 위험이 생긴다. 그래서 **제안(사용자) → 검수(관리자) → 노출** 3단계로 나누고, 승인 전까지는 기존 미션 목록·지도·추천 어디에도 보이지 않게 했다.

**제안 화면(`MissionProposalScreen`)** — 제목·설명·카테고리·예상 소요시간은 기존 관리자 미션 편집 폼(`AdminMissionEditScreen`)과 같은 필드 구성을 재사용한다. 두 가지는 사용자 경험을 고려해 다르게 만들었다.

- **대표 이미지**: 관리자 폼은 URL을 직접 입력받지만, 일반 사용자에게 이미지 호스팅 URL을 받는 건 비현실적이다. 대신 갤러리에서 고르면(`ActivityResultContracts.PickVisualMedia`, 별도 런타임 권한 불필요) 기존 `SupabaseStorage`(사진 인증에 쓰던 것과 같은 버킷, `proposals/` 경로만 구분)에 업로드해 URL을 채운다. 선택 사항으로 두어, 이미지가 없어도 제안 자체는 낼 수 있게 했다.
- **미션 장소**: 관리자 폼은 위도/경도를 텍스트로 직접 입력받지만, 일반 사용자는 좌표를 모른다. 대신 `MissionMapScreen` 의 지도 생명주기 처리를 단순화한 경량 지도를 새로 만들어, 지도를 한 번 탭하면 그 위치에 마커가 놓이고 좌표가 채워지도록 했다. 이 지도는 기존 미션 지도 화면과 완전히 분리된 새 컴포저블이라 기존 화면 동작에는 영향이 없다.
- **포인트는 입력받지 않는다.** 사용자가 제안 시 정할 수 있는 값이 아니라, 관리자가 검수·승인할 때 처음 정해진다 — 이 정책은 UI 뿐 아니라 `firestore.rules` 로도 강제된다(아래).

**검수(`AdminMissionReviewScreen`)** — 기존 `AdminPhotoReviewScreen`(사진 검수 큐)과 같은 구조(대기열 + 카드 + 승인/반려 다이얼로그)를 그대로 따라 관리자 학습 비용을 줄였다. 승인 시에는 포인트를 입력받아 `reviewStatus=approved`·`points=<입력값>`으로 갱신하고, 수정 요청/반려는 사유를 필수 입력받아 `reviewNote` 에 남긴다. 이 사유는 제안자의 "내가 만든 미션" 화면에 그대로 노출된다.

**재제출** — "수정 요청" 상태에서만 제안자가 `MissionProposalScreen`을 다시 열어 내용을 고치고 제출할 수 있다("반려"는 재제출 대상이 아니다). 재제출은 `reviewStatus`를 `pending`으로 되돌릴 뿐 `points`/`creatorId`는 절대 건드리지 않는다.

**보안 규칙 설계** — 클라이언트가 Firestore를 직접 쓰는 구조라(3.6절), 위 정책은 규칙으로도 강제해야 실제로 우회 불가능하다. 생성 규칙은 `creatorId == 본인 uid`·`reviewStatus == 'pending'`·`points == 0` 세 조건을 모두 요구해 사용자가 자기 미션을 바로 승인하거나 포인트를 스스로 매길 수 없게 한다. 여기에 `request.resource.data.keys().hasOnly([...])`로 생성 시 쓸 수 있는 필드 자체를 화이트리스트로 제한했다 — 그렇지 않으면 세 조건만 만족시키고도 생성 시점에 `completionCount`/`likeCount` 같은 인기도 카운터를 원하는 값으로 미리 끼워 넣을 수 있었다(승인 전에는 목록에 안 보이지만, 검수 화면이 그 필드를 보여주지 않으므로 관리자가 못 보고 승인해버릴 위험이 있었다). 재제출 규칙은 현재 상태가 `changes_requested`일 때만 허용하고, 쓰는 값 중 `points`/`creatorId`가 기존과 같아야 한다는 조건을 별도로 걸어 우회를 막는다. `firestore-tests/rules.test.js`에 18건을 추가해(총 38건, 필드 화이트리스트 위반 시도 포함) 에뮬레이터로 실제 검증했다(3.6·8.1절).

**좋아요 / 찜(`MissionEngagement`)** — 두 반응 모두 `mission_likes`/`mission_bookmarks` 컬렉션에 문서 ID를 `{uid}_{missionId}`로 고정해 저장한다. 이 설계만으로 "사용자당 미션당 최대 1건"이 구조적으로 보장되어 중복 방지 로직을 따로 클라이언트에 둘 필요가 없고, 취소는 그 문서를 지우기만 하면 된다. 문서 생성/삭제와 `missions.{likeCount|bookmarkCount}` 증감을 하나의 `WriteBatch`로 묶어 카운터가 반응 문서 존재 여부와 항상 같이 움직이게 했다. 좋아요는 "공개적인 추천"이라 목록에서 지금 몇 명이 좋아하는지 누구나 보게 했고, 찜은 "개인 저장"이라 본인만 읽을 수 있게 규칙을 다르게 뒀다. 좋아요/찜 버튼은 미션 목록 카드와 미션 상세 화면에 추가했고, 지도 화면의 정보창은 네이버 지도가 살아있는 뷰가 아니라 비트맵만 받는 구조라(4.2절 한계) 버튼을 얹지 않았다.

**추천 시스템과의 연결** — `likeCount`를 `MissionScorer`의 인기도 신호에 반영했다. 다만 [MissionFeatures.of] 가 만드는 `popularity`(완료 횟수 기반) 신호 자체는 그대로 둔다 — 이 신호는 학습된 재랭커(`LearnedReranker`)가 학습 당시 그대로 쓰는 입력이라, 값을 바꾸면 재학습 없이 모델이 실제로 보정된 적 없는 분포를 받게 된다(4.5절, 8.3절 AUC/NDCG 수치는 이 신호 기준). 그래서 좋아요 신호는 `MissionScorer.score()` 안에서만 별도로 계산해 "완료 횟수 기반 인기도와 좋아요 기반 인기도 중 더 강한 쪽"으로 규칙 점수에만 반영하고, `recommendReranked`가 재랭커에 넘기는 `Signals`(`MissionFeatures.of`)는 그대로 완료 횟수만 쓴다 — 좋아요는 규칙 점수(하이브리드의 `(1-blend)` 비중) 경로로만 영향을 준다. `찜`(`bookmarkCount`)은 "나중에 하고 싶다"는 신호라 지금 추천에 쓰기엔 방향이 다르다고 판단해 이번엔 반영하지 않았다. 추천 후보를 불러오는 두 지점(`HomeScreen`, `MissionPerformScreen`)과 `MissionListScreen`·지도에서 공통으로 `MissionReviewStatus.isPubliclyVisible()`을 적용해, 검수 전/반려된 제안이 추천·목록·지도 어디에도 노출되지 않도록 했다. `MissionScorerTest`에 좋아요 기반 인기도 테스트 3건을 추가했다.

**하위 호환** — 새 필드(`creatorId`, `reviewStatus`, `reviewNote`, `likeCount`, `bookmarkCount`)는 전부 optional 이고, 기존 관리자 생성 미션 문서는 필드 자체가 없다. 어디서 읽든 `reviewStatus`가 없으면 `approved`로, 카운터가 없으면 0으로 취급하도록 `MissionReviewStatus`/읽기 코드를 통일해, 기존 데이터에 마이그레이션 없이도 그대로 동작한다. 관리자 미션 목록 화면은 승인된 미션만 보여주도록 필터를 추가해 검수 대기 중인 제안과 뒤섞이지 않게 했을 뿐, 미션 생성·수정·삭제 자체의 동작은 바꾸지 않았다.

---

## 5. (기존 프로젝트 대비) 인공지능 관점의 한계와 문제 정의

### 5.1 한계 (개선 착수 전)

1. **사진 인증에 검증이 없다.** 2단계 인증은 촬영본을 그대로 업로드하고 `photoVerified` 를 참으로 고정했다. 미션과 무관한 사진(셀카, 스크린샷, 실내 사진)으로도 포인트를 받을 수 있어 부정 사용에 취약하다. → 6장(온디바이스 사진 인증 모델)
2. **학습된 모델이 없다.** 추천은 하드코딩 가중치의 규칙 기반이며, 오프라인 평가·학습 절차가 없다. → 8.3절(학습된 추천 re-ranker)

### 5.2 문제 정의

> 촬영된 미션 사진이 해당 미션의 카테고리(투어·맛집·체험·쇼핑)에 부합하는지,
> 혹은 인증에 부적절한 사진(무효)인지 **기기에서 즉시 판정**하고,
> 확신도에 따라 자동 통과 / 관리자 검수 / 재촬영으로 분기한다.

제약: 서버 비용 없이 오프라인에 가깝게 동작해야 하므로 **온디바이스 추론**으로 한정한다.

---

## 6. 인공지능 기여: 온디바이스 사진 인증 모델

### 6.1 관련 연구 및 기술

- **전이학습(Transfer Learning)**: 대규모 사전학습 백본에 소규모 도메인 데이터로 분류 헤드/일부 레이어만 재학습. 데이터가 적은 환경에 적합. `[인용 TODO]`
- **경량 비전 백본**: MobileNet, EfficientNet-Lite, **MobileViT** — 합성곱과 트랜스포머를 결합해 모바일에서 정확도·지연을 절충. 본 프로젝트는 `apple/mobilevit-small`(약 500만 파라미터). `[인용 TODO]`
- **CLIP**: 이미지-텍스트 공동 임베딩으로 제로샷 분류. 학습 없이 베이스라인 성능을 측정하는 기준선. `[인용 TODO]`
- **파라미터 효율적 파인튜닝(LoRA)**: 소수 저랭크 행렬만 학습해 과적합·연산 절감. 비교 실험군. `[인용 TODO]`
- **온디바이스 추론 런타임**: ONNX Runtime Mobile / TensorFlow Lite. HuggingFace Optimum 으로 ONNX 변환·양자화.

### 6.2 전체 구조

```
[카메라 촬영]
     │  촬영본 bytes
     ▼
PhotoVerifier (data/)  ── 온디바이스 모델 추론 ──▶  라벨별 점수
     │
     ▼
PhotoVerification (domain/, 순수 Kotlin)  ── 점수 + 미션 카테고리 ──▶  PASS / NEEDS_REVIEW / REJECT
     │
     ├─ REJECT        → 업로드 안 함, 재촬영 안내
     ├─ PASS          → Supabase 업로드 → 미션 완료 + 포인트
     └─ NEEDS_REVIEW  → 업로드 + 완료 + 포인트, photoNeedsReview 플래그 → 관리자 검수 큐
```

- 모델 의존(추론)은 `data/` 계층에, 판정 규칙은 순수 Kotlin(`domain/PhotoVerification`)으로 분리해 단위 테스트한다. 기존 `LocationVerification`·`MissionCompletion` 과 동일한 설계 원칙.
- `PhotoVerifier` 는 인터페이스이며 구현체(`OnnxPhotoVerifier`, 테스트용 `FakePhotoVerifier`)를 교체할 수 있다.
- `OnnxPhotoVerifier` 는 전처리 상수를 하드코딩하지 않고 `photo_verifier_preprocessor.json`(export 산출물)에서 읽어 학습·추론 전처리를 자동 정합시킨다. 모델 입력은 `pixel_values [1,3,256,256]` float32, 출력은 로짓 `[1,5]` 이며 앱에서 softmax 후 판정한다. MobileViT 전처리는 짧은 변 288 리사이즈 → 256 center-crop → ×(1/255) → RGB→BGR 채널 순서 반전(정규화 없음).
- 추론(`PhotoVerifier`) + 판정(`PhotoVerification`)을 묶은 "업로드 전 결정"은 `data/PhotoGate` 로 분리했다. Firebase·Android 에 의존하지 않아 `FakePhotoVerifier` 로 전 경로를 단위 테스트한다. `FirebaseMissionRepository` 는 `PhotoGate.decide()` 결과(`Reject` / `Proceed(needsReview)`)에 따라 업로드/거부만 수행한다.

### 6.3 데이터셋

`ml/labels.json` 이 클래스 정의의 단일 소스이며 앱의 `PhotoVerification.INVALID_LABEL` 과 일치한다.
구축은 `ml/data/` 스크립트로 자동화한다: `fetch_public.py`(공개 데이터 수집) → `make_negatives.py`(무효 합성) → `build_dataset.py`(분할) → `upload_hf.py`(HF Hub 업로드).

**수집 출처**

| 클래스 | 출처 | 방식 |
| --- | --- | --- |
| 맛집 | Food-101 (`ethz/food101`) validation | 101개 음식 클래스에서 클래스당 상한을 두고 고르게 표본 |
| 투어·체험·쇼핑 | Places365 validation (`dpdl-benchmark/Places365-Validation`, 365 scene × 100장) | 라벨 인덱스를 `places365_categories.txt` 로 이름화, `place_classes.py` 로 카테고리 매핑(투어 107 · 체험 53 · 쇼핑 28 scene), scene 당 상한을 두고 표본 |
| 무효 | 합성 | 스크린샷 합성 + 다른 클래스 이미지 열화(하드 네거티브) + 화면 재촬영 합성(`recapture()`, 6.7.10) + `collected_invalid/` 직접 수집분 |

- **스트리밍 대신 validation 셋 전체 다운로드**: Places365/Food-101 학습 셋은 클래스 순으로 정렬돼 있어 `datasets` 스트리밍 + shuffle 로는 앞쪽 몇 개 scene 에 편중된다(초기 시도에서 투어 3 scene·쇼핑 1 scene 만 수집됨). validation 셋은 scene 당 100~250장으로 작아(합쳐서 ~5.7GB) scene 다양성을 최대로 확보한다.
- **분할**: train/val/test = 70/15/15. 파일명 `<sceneId>__n.jpg` 의 sceneId 단위로 그룹을 묶어 분할해, 같은 scene 이미지가 train·test 에 걸쳐 성능이 부풀려지는 것을 방지한다(공개 데이터 4개 클래스는 train↔test scene 겹침 0). 그룹이 3개 미만이면 이미지 단위 분할로 대체하는데, 합성 무효가 처음엔 2종(스크린샷·열화)이라 이 대체 경로를 탔었다. `recapture()` 를 3번째 소스로 추가하며 그룹 수가 3개가 돼 대체 경로를 벗어났고, 그 결과 합성 파일명이 소스별로만 겹쳐 소수 그룹에 뭉치는 버그가 드러났다(6.7.10).
- **크라우드소싱**: 동기·지인의 실제 미션 수행 사진. `ml/data/import_collected.py --category <카테고리> --from-dir <폴더>` 로 넣으면 같은 파이프라인으로 합쳐진다(체험 클래스 보강 대상, 아직 미적용).

**구축 결과** (HF Hub 비공개: `kimgayeon430/travel-mission-photos`, `recapture()` 반영 재구축 후)

| split | 투어 | 맛집 | 체험 | 쇼핑 | 무효 | 합계 |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| train | 698 | 700 | 700 | 705 | 1,444 | 4,247 |
| validation | 152 | 150 | 150 | 161 | 310 | 923 |
| test | 150 | 150 | 150 | 134 | 309 | 893 |
| **합계** | **1,000** | **1,000** | **1,000** | **1,000** | **2,063** | **6,063** |

scene 다양성: 투어 107 · 맛집 101 · 체험 53 · 쇼핑 28 · 무효 3(합성 소스 종류).
공개 데이터는 도메인이 실제 촬영본과 다소 다르므로, 크라우드소싱 사진으로 각 클래스(특히 체험)를 보강하는 것이 향후 과제다.

### 6.4 모델 및 학습

| 단계 | 모델 | 학습 |
| --- | --- | --- |
| 베이스라인 | `openai/clip-vit-base-patch32` | 없음 (제로샷) |
| 채택 모델 | `apple/mobilevit-small` | 헤드 학습(8ep) → **전체 파인튜닝(6ep)** — 후자를 채택 |
| 비교군(선택) | `apple/mobilevit-small` + LoRA | 코드만 준비, 미수행 |

- 학습·평가 파이프라인: `ml/notebooks/train_photo_verifier.ipynb` (Colab T4, 약 30분). CLIP 제로샷으로 베이스라인을 먼저 측정한다.
- 최종 모델은 `torch.onnx` 로 ONNX(opset 18) 변환 후 `app/src/main/assets/photo_verifier.onnx` 로 번들(약 20MB). (Optimum 은 Colab 의 `diffusers`/`huggingface_hub` 버전 충돌로 사용 불가 → `torch.onnx` 직접 export)
- 전처리 상수(리사이즈 크기, 정규화 mean/std, 채널 순서)는 export 시 함께 나오는 `preprocessor_config.json` 을 `assets/photo_verifier_preprocessor.json` 으로 번들하고, `OnnxPhotoVerifier` 가 이를 런타임에 읽어 학습·export·추론 전처리를 자동으로 일치시킨다. (키가 없으면 MobileViT 기본값)

### 6.5 판정 규칙 (`PhotoVerification`)

모델이 낸 라벨별 점수 `s` 와 미션 기대 카테고리 `c`, 임계값(`PhotoVerificationConfig`)에 대해:

| 조건 | 판정 |
| --- | --- |
| `s[무효] ≥ invalidRejectThreshold` | `REJECT` |
| `s[c] < hardRejectThreshold` | `REJECT` |
| `s[c] ≥ autoPassThreshold` | `PASS` |
| 그 외 | `NEEDS_REVIEW` |

- 기본값: `autoPass = 0.65`, `hardReject = 0.22`, `invalidReject = 0.55`. `mobilevit-small-fullft-1` 의 test 셋 임계값 스윕으로 선정해 `ml/thresholds.json` → `PhotoVerificationConfig` 로 이식했다. `invalidReject = 0.55` 에서 무효 사진 차단율 0.932(6.7.10 재학습 후, 화면 재촬영 유형 포함).
- 모델을 불러오지 못하면 `NEEDS_REVIEW`(`passWhenModelUnavailable = false`). 모델 배포 완료로 전환했으며, 이제 로딩이 실패하면 통과가 아니라 **전건이 관리자 검수로 넘어간다**. 실패가 조용하므로(`RerankerSource`·`OnnxPhotoVerifier` 모두 `runCatching` 으로 삼킴) 계측 테스트로 방어한다(8.2절).
- **설계 결정**: `NEEDS_REVIEW` 도 미션 완료·포인트 지급은 즉시 진행하고 검수 플래그만 남긴다. "포인트 보류" 상태를 만들지 않아 사용자 경험이 단순하며, 관리자 검수는 사후 부정 적발 용도다.

### 6.6 앱 통합

- **추론기**: `OnnxPhotoVerifier`(`PhotoVerifier` 구현)가 `assets/photo_verifier.onnx` 를 ONNX Runtime 으로 로드해 `Bitmap` 전처리 → 추론 → softmax → 라벨별 점수를 낸다. 라벨 순서는 `photo_verifier_labels.json`, 전처리는 `photo_verifier_preprocessor.json` 에서 읽는다. **모델 파일이 없거나 로드·추론에 실패하면 `classify()` 가 `null` 을 반환**해 앱은 종전대로 동작한다(판정은 `PhotoVerificationConfig` 에 위임).
- **주입**: `MissionPerformViewModel` 을 `AndroidViewModel` 로 두어 `OnnxPhotoVerifier(application)` 를 기본 verifier 로 주입한다. 모델·임계값이 확정되면 `PhotoVerificationConfig.DEFAULT`(모델 부재 시 `NEEDS_REVIEW`)로 전환한다.
- **APK 영향**: `onnxruntime-android` 네이티브 라이브러리가 모든 ABI 를 포함해 APK 가 크게 늘어나므로, `abiFilters` 로 `arm64-v8a`/`x86_64` 만 남겨 증가량을 억제한다(8.2절).
- **업로드 전 판정**: `FirebaseMissionRepository.uploadPhotoAndComplete` 가 백그라운드 스레드에서 `classify → verify` 를 먼저 수행하고, `REJECT` 면 업로드하지 않는다. 트래픽·저장 비용을 아낀다.
- **기록**: `user_missions` 에 `photoVerified`, `photoNeedsReview`, `photoVerifyScore`, `photoVerifyLabel`, `photoVerifyModelVersion` 을 트랜잭션으로 저장.
- **관리자 검수 큐**(`AdminPhotoReviewScreen`): `photoNeedsReview == true` 건을 사진·판정 근거와 함께 목록화.
  - 승인 → `photoVerified = true`
  - 반려 → 트랜잭션으로 2단계 보상 회수(0 미만 클램프) + 미션을 `In Progress` 로 되돌려 재인증 유도

### 6.7 한계 분석: 카테고리 분류의 입도(granularity) 문제

> **요약**: 5-클래스 분류는 "미션 *유형* 에 맞는 사진인가"만 판정할 수 있고, "*이* 미션의 대상을 찍었는가"는 판정하지 못한다. 장소 동일성은 1단계 GPS 가 담당하지만, **같은 카테고리 안에서의 대상 동일성**은 어느 단계도 보지 않는 빈 곳으로 남아 있다.

#### 6.7.1 관찰된 현상

실기기 테스트 중, '투어' 카테고리 미션에 **실내에서 개발 중인 책상 사진**을 제출해 `REJECT` 되었다. 판정 자체는 의도대로 동작한 정상 결과(true negative)지만, 반환된 사유 문구에서 두 가지를 관찰했다.

| 관찰 | 근거 | 함의 |
| --- | --- | --- |
| 카테고리 게이트가 잡았다 | `"'투어' 미션 요소를 찾지 못했어요"` = `s[c] < 0.22` 경로 | 유형 판정은 의도대로 작동 |
| **무효 게이트는 잡지 못했다** | 무효 경로였다면 `"사진이 미션과 무관해 보여요"` 가 표시됨 → `s[무효] < 0.55` | 무효 클래스가 *사무실·책상* 류 실내 장면을 충분히 커버하지 못할 가능성 |

두 번째 관찰은 6.7.5 의 스푸핑 방어 설계와 직결된다. 무효 클래스가 약하면 그 위에 얹는 방어도 함께 약해지기 때문이다.

#### 6.7.2 문제 정의: 분류는 "유형"만 보고 "동일성"은 보지 못한다

`PhotoVerification.verify()` 는 미션의 `category` 문자열로 점수 맵을 조회한다(`scores[missionCategory]`). 따라서 **'투어' 미션은 전부 동일한 기준**으로 판정된다. 경복궁 미션과 남산타워 미션이 요구하는 것은 똑같이 "투어스러운 사진"이다.

현재 2단계 인증의 역할 분담을 정리하면 빈 곳이 드러난다.

| 검증 대상 | 담당 | 상태 |
| --- | --- | --- |
| 장소 동일성 (이 좌표에 있는가) | 1단계 GPS, 반경 200 m | 구현됨 |
| 유형 적합성 (미션 유형에 맞는 사진인가) | 2단계 카테고리 분류 | 구현됨 |
| 부적절 사진 배제 (셀카·스크린샷) | 2단계 무효 클래스 | 구현됨 |
| **대상 동일성 (이 미션의 대상을 찍었는가)** | **없음** | **미해결** |

구체적 취약점: 경복궁 반경 200 m 안에서 하늘이나 옆 건물, 지나가는 관광버스를 찍어도 '투어' 로 분류되면 통과한다. 카테고리 분류만으로는 원리적으로 막을 수 없다.

#### 6.7.3 기각한 대안: 미션별 클래스 확장

가장 직관적인 해법은 클래스를 미션 단위로 늘리는 것(경복궁·남산타워·… + 무효)이다. **확장성이 없어 기각했다.**

- 미션이 하나 추가될 때마다 전체 재학습이 필요하다.
- 신규 미션은 학습 데이터가 존재하지 않아 **콜드스타트가 원천적으로 불가능**하다.
- 클래스 수가 늘수록 클래스당 데이터가 희박해져 정확도가 함께 떨어진다.

미션 추가가 관리자 화면에서 상시 일어나는 운영 구조(6.6절 `AdminMissionEditScreen`)와 근본적으로 상충한다.

#### 6.7.4 개선 방향: 참조 이미지 임베딩 유사도 `[구현]`

분류(classification) 대신 **검색(retrieval)** 관점을 도입한다. 미션마다 참조 이미지를 등록하고, 촬영본과의 임베딩 코사인 유사도를 판정에 보조 신호로 반영한다.

핵심 이점은 **미션을 추가해도 재학습이 필요 없다**는 것이다. 참조 이미지만 등록하면 되므로 6.7.3 이 기각된 이유를 정면으로 해소한다.

- 미션 문서에 이미 **`imageUrl`(대표 이미지)** 필드가 있어 참조 이미지로 활용한다.
- 참조 임베딩은 관리자 스크립트(`ml/embed_missions.py`)로 사전 계산해 Firestore(`missions/{id}.photoEmbedding`)에 저장한다. 인증 시점에 참조 이미지를 내려받을 필요가 없다.
- 온디바이스에서는 촬영본 임베딩과의 코사인 유사도만 계산한다(512차원 내적, 추론 시간 대비 무시 가능).

##### 임베딩 인코더 선정: pooled feature 재사용은 기각

애초 계획은 분류기(`photo_verifier.onnx`, MobileViT)의 분류 헤드 입력 활성값(pooled feature)을 그대로 임베딩으로 써서 **추가 용량 0 MB**로 해결하는 것이었다(`ml/add_embedding_output.py` 로 이미 배포된 모델에 출력만 하나 더 붙일 수 있다). 그러나 이 임베딩이 "같은 카테고리 안에서 대상을 구분"하는지 먼저 검증했더니 실패했다.

`ml/embedding_separability.py` — 공개 scene 데이터(`data/raw/<카테고리>/<scene>__NNNN.jpg`)에서 **같은 scene 쌍(=같은 대상, positive)** 과 **같은 카테고리·다른 scene 쌍(hard negative)** 의 코사인 유사도 분리도(ROC AUC)를 잰다.

| 임베딩 인코더 | 전체 AUC | pos−neg 평균차 | 맛집 | 체험 | 투어 | 쇼핑 |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| MobileViT pooled (분류기 재사용, 0 MB) | 0.60 | 0.107 | 0.63 | 0.66 | 0.63 | 0.60 |
| **CLIP ViT-B/32** (별도 모델) | **0.76** | 0.125 | 0.92 | 0.88 | 0.81 | 0.70 |

MobileViT feature 는 5-클래스 분류로 파인튜닝되며 클래스 판별 방향으로 붕괴해, 같은 '투어' 안의 서로 다른 랜드마크를 거의 구분하지 못한다(AUC 0.60 ≈ 무작위+α). CLIP 이미지 인코더는 범용 임베딩이라 쓸 만하며(int8 양자화해도 AUC 0.762 로 열화 없음), 이를 채택했다.

**비용**: CLIP ViT-B/32 int8 ONNX 는 ≈ 89 MB 로 `photo_verifier.onnx`(20 MB)의 4.4배다. APK 를 그만큼 키우지 않도록, 앱은 이 모델을 **번들하지 않고 최초 사진 인증 시 HuggingFace Hub(데이터셋과 같은 계정)에서 1회 받아 `filesDir` 에 캐시**한다(`ModelSource.cachedDownload`, 버전 불일치 시 재다운로드). Supabase Storage 도 검토했으나 무료 플랜의 파일당 50 MB 상한에 걸린다. 미션 수행 화면 진입 시 백그라운드로 미리 받아, 촬영까지 걸리는 시간 동안 준비된다. `assets/photo_embedder_int8.onnx` 를 넣으면 번들 방식으로도 동작한다. 모델을 아직 못 받았으면 유사도 결합을 건너뛰고 카테고리 규칙만 적용한다.

#### 6.7.5 판정 순서 설계: 무효는 단락하고, 카테고리는 단락하지 않는다

유사도를 도입할 때 **어느 게이트에서 조기 종료(short-circuit)할 것인가**가 설계의 핵심이다. 두 게이트를 서로 다르게 다뤄야 한다.

**(가) 무효는 반드시 먼저 잘라야 한다 — 유사도는 스푸핑에 취약하다.**

참조 이미지를 화면에 띄워놓고 그것을 촬영하면 **유사도가 오히려 높게** 나온다. 유사도만 보면 완벽한 인증으로 보이는 역설이 발생한다. 이를 막는 것은 유사도가 아니라 무효 클래스(스크린샷·셀카 판별)이므로, `s[무효] ≥ 0.55` 는 **유사도를 계산하기 전에 무조건 `REJECT`** 여야 한다. 이 취약점은 실기기에서 재현되었다(6.7.7).

**(나) 카테고리 점수로는 단락하면 안 된다 — 유사도를 도입하는 주된 이득이 사라진다.**

카테고리 점수가 낮다고 즉시 거절하면, "제대로 찍었는데 분류 점수가 낮은 사진"을 구제할 기회가 없어진다. 이는 예외가 아니라 **빈번한 상황**이다. 6.4절 평가에서 체험 클래스 F1 이 0.644 로 가장 낮은데(공개 데이터와 실제 미션 사진의 도메인 차이), 이런 사진들이 정확히 이 경우에 해당한다. 유사도는 약한 클래스를 보완하는 장치이므로 카테고리 뒤에 종속시키면 안 된다.

두 신호를 함께 볼 때의 판정표는 다음과 같다. 유사도는 판정을 **한 단계씩만** 조정한다(`PASS ↔ NEEDS_REVIEW ↔ REJECT`).

| 카테고리 `s[c]` | 참조 유사도 | 판정 | 해석 |
| --- | --- | --- | --- |
| 높음 (≥ autoPass) | 높음 (≥ suspect) | `PASS` | 유형·대상 모두 일치 |
| 애매 | 높음 (≥ suspect) | `PASS` | 애매한 분류를 대표 이미지가 뒷받침 |
| 낮음 (< hardReject) | 높음 (≥ rescue) | `NEEDS_REVIEW` | **약한 카테고리 클래스 구제** (체험 등) — 즉시 거절하지 않고 검수 |
| 높음 | 낮음 (< suspect) | `NEEDS_REVIEW` | 같은 유형의 **다른 대상** 촬영 의심 — 6.7.2 의 빈 곳 |
| 낮음 | 낮음 (< rescue) | `REJECT` | 미션과 무관 |

정리하면 판정 순서는 `무효 단락 → 카테고리 1차 판정 → 유사도로 ±1단계 조정` 이다(`PhotoVerification.verify`). 6.7.1 의 책상 사진은 두 신호가 모두 낮아 `REJECT` 로 동일하지만, 위 표 3·4행이 종전 구조에서 잡지 못하던 사례다.

임계값은 `PhotoVerificationConfig` 에 `similarityRescueThreshold`(구제, 잠정 0.50 = 공개 프록시에서 같은 대상 쌍 p15), `similaritySuspectThreshold`(닮음 기준선, 잠정 0.68 = 다른 대상 쌍 p90)로 둔다. 6.5절 설계상 `NEEDS_REVIEW` 도 포인트는 즉시 지급되므로, 유사도의 실질 효과는 **① 약한 카테고리 구제로 검수 큐 부하 감소, ② 사후 부정 적발 정확도 향상**이다(하드 차단이 아니다).

#### 6.7.6 남은 위험

- **하드 게이트로는 쓸 수 없다.** `embedding_separability.py` 에서 정상 사진이 hard-negative 상위 5% 유사도 문턱을 못 넘는 비율이 투어 56%·쇼핑 80%다. 유사도로 즉시 `REJECT` 하면 정상 사진 오탈락이 과다하므로 보조/구제 신호로만 쓴다. 쇼핑은 인코더와 무관하게 분리도가 낮아(AUC 0.70) 유사도 결합의 이득이 작다.
- **임계값이 공개 scene 프록시 기반의 잠정치.** `raw/<장소>__N.jpg` 그룹을 같은 대상으로 간주해 스윕한 값이다. 실제 미션 사진 vs 대표 이미지 쌍으로 재보정해야 한다. `user_missions.photoVerifySimilarity`(PASS·NEEDS_REVIEW)와 `PhotoVerify` logcat(REJECT 포함)이 데이터를 쌓고, `ml/calibrate_similarity.py` 가 관리자 검수 결과를 정답 라벨로 삼아 `rescue`/`suspect` 를 스윕한다.
- **참조 이미지 1장의 한계.** 각도·조명·계절·주야 차이에 코사인 유사도가 흔들린다. `photoEmbedding` 을 배열의 배열로 두면 여러 장 등록해 최대 유사도를 취하도록 확장할 수 있다.
- **무효 클래스 의존.** (가) 의 스푸핑 방어 전체가 무효 클래스 성능에 걸려 있다. 6.7.1 에서 사무실 장면이, 6.7.7 에서 모니터 재촬영이 모두 무효로 잡히지 않았으므로(`s[무효] ≈ 0.07`), 크라우드소싱 수집 시 *무관한 실내·업무 환경* 과 *화면·모니터 재촬영* 표본을 보강해야 한다. **화면 재촬영은 합성으로도 재현된다**: 베젤·무아레·글레어를 합성한 이미지 12장을 배포 중인 모델에 돌려보면 `s[무효]` 0.011~0.129 로 동일하게 걸러지지 않는다(`make_negatives.py` 합성 3종(스크린샷/열화/재촬영) 중 하나, 7.2). 기존 열화(블러·저조도) augmentation 은 이 패턴을 학습 데이터에 전혀 포함하지 않았다는 뜻이다.
- **전처리 정합.** 촬영본은 EXIF 회전을 반영해 디코드하도록 고쳤다(`ImagePreprocess.decodeUpright`, 분류·임베딩 공통). CLIP 리사이즈는 bicubic 이나 앱은 bilinear 라 미세한 차이가 남는다.

#### 6.7.7 실기기 예비 관측

Galaxy S8(SM-G950N, API 28)에서 `경복궁_투어` 미션(대표 이미지 임베딩 백필 완료)으로 두 가지 사진을 제출해, 유사도 신호가 실제로 어떻게 동작하는지 관측했다. 임베더(CLIP int8 88.6 MB)는 미션 수행 화면 진입 시 HF Hub 에서 받아 `filesDir` 에 캐시되었고(`prefetch`), 첫 인증이 다운로드에 막히지 않았다.

| 제출 사진 | `s[투어]` | `s[무효]` | 참조 유사도 | 판정 |
| --- | ---: | ---: | ---: | --- |
| 실내 책상(미션과 무관) | 0.106 | 0.082 | **0.376** | `REJECT` (유지) |
| 경복궁 사진을 모니터에 띄워 재촬영 | 0.128 | 0.066 | **0.673** | `REJECT` → `NEEDS_REVIEW` (구제) |

관측한 것:

1. **배선 검증.** 참조 임베딩 로드(`ref=y(512)`), 촬영본 임베딩 추론, 코사인 유사도, 구제 경로가 모두 실제로 동작한다. 무관한 사진은 `sim 0.376 < rescueThreshold 0.50` 이라 `REJECT` 가 유지되고, 재촬영본은 `0.673 ≥ 0.50` 이라 즉시 거절 대신 검수로 올라갔다(표 6.7.5 의 3행).
2. **유사도 스케일이 압축돼 있다.** 무관 0.38 ~ 유사 0.67 로, CLIP 임베딩 공간의 이방성(anisotropy) 때문에 `[0, 1]` 전 구간을 쓰지 않는다. 잠정 임계값 `rescue 0.50 / suspect 0.68` 은 이 좁은 구간을 3등분하는 셈이라 여유가 작다.
3. **스푸핑 취약점이 재현되었다(6.7.5-가).** 모니터 재촬영인데 무효 점수가 0.066 에 그쳐 무효 게이트를 통과했고, 유사도만으로 `REJECT` 에서 `NEEDS_REVIEW` 로 승격되었다. `suspect 0.68` 을 근소하게(0.673) 못 넘겨 `PASS` 는 면했으나, 무효 클래스가 화면 재촬영을 커버하지 못하면 이 방어가 유사도 문턱 하나에만 의존하게 된다.
4. **판정을 유사도가 좌우한다.** 두 경우 모두 `s[투어]` 가 0.11~0.13 으로 낮다(6.4·6.7.5-나 의 도메인 격차). `경복궁_투어` 의 실질 판정은 카테고리가 아니라 유사도가 결정하고 있다 — 유사도를 카테고리에 종속시키지 않기로 한 6.7.5-나 설계가 이 미션에서 특히 중요하게 작동한다.
5. **아직 임계값을 확정할 수 없다.** *현장에서 정상 촬영한* 사진의 유사도 값이 없어 `rescue`/`suspect` 를 어디에 둘지 정할 수 없다. `rescue 0.50` 이 무관 사진(0.376)보다 위라 오구제는 막지만, 간격이 0.12 라 "제대로 찍었으나 각도·조명이 나쁜" 사진이 문턱 아래로 떨어질 여지가 있다.

**계측.** REJECT 는 업로드도 Firestore 기록도 하지 않으므로 유사도 값이 어디에도 남지 않는다. `FirebaseMissionRepository` 가 판정 직후 `PhotoVerify` 태그로 `mission·category·ref·s[c]·s[무효]·similarity·verdict` 한 줄을 로깅해(`adb logcat -s PhotoVerify:*`), 판정과 무관하게 실측 보정 데이터를 모을 수 있게 했다. `PASS`/`NEEDS_REVIEW` 는 종전대로 `user_missions.photoVerifySimilarity` 에도 기록된다.

#### 6.7.8 웹 프록시 1차 보정

실기기 촬영은 사용자가 실제로 그 장소에 가야 표본이 늘어 속도가 느리다(6.7.7 은 2건 — `correct` 1, `wrong` 1). 매번 현장에 가지 않고도 표본을 늘리려고, **그 미션이 실제로 가리키는 장소의 공개 사진**(Wikimedia Commons)을 받아 같은 임베딩·유사도 파이프라인에 흘려보는 프록시 보정 도구 `ml/calibrate_similarity_web.py` 를 추가했다. 기존 `embedding_separability.py` 의 "같은 장면 카테고리" 프록시보다 타겟이 좁다(그 장소 자체의 사진).

**1차(4개 랜드마크, 32건)**: 경복궁_투어·명동_맛집·동대문_쇼핑·홍대_체험에 대해 각 미션 사진 4장(`correct`) + 교차 4장(`wrong`)을 스윕한 결과 Youden J 최댓값 0.25(thr=0.66, 현재 `suspect=0.68`과 근접).

**2차(8개 랜드마크로 확장, 68건)**: 광장시장_맛집·용산_투어·여의도_한강·숙대입구를 추가해 `correct`/`wrong` 각 34건으로 늘렸다. 처음에는 "같은 동네의 다른 미션"(예: 용산역 사진을 용산_맛집·용산_쇼핑·용산_체험·용산_투어2 에도 `correct`로 재사용)까지 포함해 104건을 만들었는데, **이게 방법론적 실수였다** — 실제로 이 미션들의 `photoEmbedding` 끼리 코사인 유사도를 재보니 0.28~0.71 로 서로 상당히 다르다(같은 상권이어도 특정 피사체가 다름, 명동 3개 미션도 0.51~0.57). "동네가 맞으면 정답"이 아니라 "그 사진 속 피사체가 맞아야 정답"이라는 뜻이라, 미션당 1장씩만 `correct`로 남기고(34/34) 다시 스윕했다.

| | n | mean | min | max |
| --- | --: | --: | --: | --: |
| `correct` (제 장소, 미션당 1:1) | 34 | 0.524 | 0.278 | 0.875 |
| `wrong` (다른 장소) | 34 | 0.486 | 0.365 | 0.628 |

Youden J 최댓값은 thr=0.52 에서 0.21로, 1차(0.66/J=0.25)보다 **최적점이 왼쪽으로 이동하고 분리력도 약해졌다**. 랜드마크를 늘릴수록 결과가 수렴하기는커녕 더 불안정해진 것 자체가 결론이다 — 유사도 신호의 분리력이 근본적으로 약하다는 6.7.6 의 우려를 재확인한다. 현재 `suspect=0.68` 에서는 `correct` 의 12%만 통과(TPR)하는 대신 `wrong` 통과율(FPR)은 0% — "닮았다고 인정"하는 기준을 매우 보수적으로 잡고 있다는 뜻으로, 6.5절에서 유사도를 하드 게이트가 아닌 보조/구제 신호로만 쓰기로 한 설계와 일치한다(대부분의 `correct` 사진은 애초에 이 기준을 넘길 필요가 없다 — 카테고리 판정이 이미 처리).

**한계**: 두 배치 모두 30건 안팎이고 웹 프록시(사용자가 그 자리에서 찍은 사진이 아님)라 `calibrate_similarity_web.py` 도 실행 시 경고를 출력한다. 임계값을 이 결과로 바꾸지 않았다 — 실사용 로그(`calibrate_similarity.py`)가 우선한다. 다운로드한 이미지는 저작권이 있는 외부 사진이라 리포지토리에는 포함하지 않았고, 스크립트와 절차만 남겼다. 참조 이미지 1장의 한계(6.7.6)를 이번에 정량으로도 확인했으므로, 다중 참조 이미지(최대 유사도) 확장의 우선순위가 올라간다.

**다중 참조 이미지 프로토타입(오프라인)**: 6.7.6 이 제안한 "참조 이미지를 배열로 두고 최대 유사도" 를 실제로 코드·재학습 없이 held-out 실험으로만 먼저 검증했다. 8개 랜드마크마다 웹사진 절반을 "추가 참조 뱅크"(기존 `photoEmbedding` + 웹사진), 나머지 절반을 "새로 들어온 사진"으로 나눠 참조 1장 vs 여러 장(최대 유사도)을 비교했다(n=15/15, 앞의 34건보다 작음 — 그룹당 반으로 쪼갰기 때문).

| | Youden J 최댓값 | thr |
| --- | --: | --: |
| 참조 1장(기존) | 0.33 | 0.56 |
| 참조 여러 장(최대 유사도) | **0.47** | 0.64 |

분리력이 뚜렷이 개선된다(J +0.14). 다만 같은 검색 세션에서 받은 사진끼리라 촬영 조건(조명·각도)이 실제 사용자 촬영본보다 서로 닮아 있을 수 있어 개선폭이 과대추정됐을 가능성이 있다 — 그래도 방향은 명확하다. 이 확장은 **Colab 재학습이 필요 없다**: `missions/{id}.photoEmbedding` 을 배열로 바꾸고 `embed_missions.py` 가 이미지 여러 장을 받아 각각 임베딩하도록, 앱은 `max(cosine(...))` 을 쓰도록 고치면 된다(추론 모델 자체는 그대로).

**실제 구현 완료**: 프로토타입 검증 직후 코드로 옮겼다. `PhotoVerification.verify` 의 `referenceEmbedding: FloatArray?` 를 `referenceEmbeddings: List<FloatArray>` 로 바꾸고 `PhotoEmbedding.maxCosineOrNull` 로 최대 유사도를 취하도록 도메인·데이터 계층(`PhotoGate`, `MissionRepository`, `FirebaseMissionRepository`, `MissionPerformViewModel`)을 전부 고쳤다. Firestore 는 신규 `photoEmbeddings`(배열)와 레거시 `photoEmbedding`(단일)을 둘 다 읽어 합친다(하위호환, 기존 미션 16건은 그대로 동작). `embed_missions.py` 는 미션 문서의 `imageUrl`+`imageUrls`(신규, 배열) 또는 CSV 반복 행으로 여러 장을 받아 `photoEmbeddings` 로 쓰고 첫 장은 `photoEmbedding` 에도 남긴다. `PhotoVerificationTest`/`PhotoGateTest` 에 다중 참조 테스트 추가, 단위 테스트 전건(`testDebugUnitTest`) 통과.

**버그 발견·수정**: 구현 직후 실제 백필을 시도하다 `Nested arrays are not allowed`(400)로 실패 — Firestore 는 **배열의 배열을 지원하지 않는다.** `photoEmbeddings: List<List<Float>>` 을 그대로 쓴 최초 설계가 틀렸다. 원소를 `{"v": [...]}` 맵으로 감싼 "배열의 맵"(배열→맵→배열은 허용)으로 고쳐 `FirebaseMissionRepository`/`embed_missions.py`/`calibrate_similarity_web.py` 세 곳을 함께 수정했다. 실패한 첫 시도는 Firestore `.set()` 이 원자적이라 부분 기록 없이 그대로 롤백됐음을 재조회로 확인. 이후 앞서 웹 프록시 검증에 쓴 사진(6.7.8, 경복궁·명동·동대문·홍대·광장시장·용산·여의도한강·숙대입구 8개 미션, 기존 대표 이미지 1장 + 검증된 웹사진 4~6장)을 `photoEmbeddings` 로 실제 백필 완료.

**백필 후 held-out 검증**: 위 8개 미션의 참조 뱅크에는 없는 **새 사진**(랜드마크당 1~2장, 총 12장)을 추가로 받아, 지금 실제로 Firestore 에 저장된 다중 참조 뱅크(최대 유사도)로 스윕했다.

| | n | mean | Youden J 최댓값 | thr |
| --- | --: | --: | --: | --: |
| 참조 1장(백필 전, 6.7.8 1차) | 34/34 | 0.524 / 0.486 | 0.21 | 0.52 |
| 참조 여러 장(백필 후, 실제 프로덕션 데이터) | 12/12 | 0.711 / 0.659 | **0.33** | 0.72 |

held-out 데이터로도 분리력이 개선된다(J 0.21 → 0.33). 표본이 랜드마크당 1~2장뿐이라 아직 확정적이진 않지만, 오프라인 프로토타입(J 0.33→0.47)과 같은 방향의 결과가 **실제 프로덕션 데이터**에서도 재현됐다.

#### 6.7.9 실사용 라벨 첫 확보 (관리자 검수)

6.7.7 관측 이후, 실기기로 두 건을 추가 제출하고 관리자 검수까지 거쳐 `calibrate_similarity.py` 가 쓸 수 있는 **실제 정답 라벨**(`correct`/`wrong`)을 처음 얻었다.

| 미션 | 상황 | 유사도 | 1차 판정 | 관리자 처리 | 라벨 |
| --- | --- | ---: | --- | --- | --- |
| 숙대입구 | 현장 정상 촬영(투어) | 0.791 | `NEEDS_REVIEW` (`match=0.108` 로 카테고리 점수는 낮았으나 유사도로 구제) | **승인** | `correct` |
| 경복궁_투어 | 모니터 재촬영(6.7.7, 스푸핑) | 0.673 | `NEEDS_REVIEW` (구제) | **반려** | `wrong` |

`ml/calibrate_similarity.py --firebase-key ...` 로 스윕한 결과, `correct`(0.791) 와 `wrong`(0.673) 사이 정확히 현재 `suspect=0.68` 이 낀다 — 표본 1/1 이라 우연일 가능성이 크지만 방향은 현재값을 지지한다. 두 가지를 확인했다:

1. **숙대입구는 카테고리 점수(0.108)가 `hardReject`(0.22) 아래인데도 유사도(0.791)로 구제돼 정상 처리됐다.** 6.4 의 도메인 격차(공개 데이터로 학습한 분류기가 실제 미션 사진에서 카테고리 점수를 낮게 냄)를 유사도가 보완하는 실제 사례.
2. **REJECT 는 여전히 Firestore 에 안 남는다.** 이번 두 건은 둘 다 `NEEDS_REVIEW` 로 검수 큐까지 올라간 경우라 라벨링이 가능했다 — `REJECT` 로 즉시 잘린 케이스의 정답 라벨은 `PhotoVerify` logcat 으로만 얻을 수 있다(6.7.7 계측).

**의의**: `calibrate_similarity_web.py`(웹 프록시)와 `calibrate_similarity.py`(실사용)가 이제 각각 34/34, 1/1 로 별도 트랙에서 굴러가고 있다. 웹 프록시가 표본 수는 많지만 "그 자리에서 찍은 사진이 아니다"라는 한계가 있고, 실사용은 근거는 확실하지만 아직 표본이 극히 적다 — 두 트랙 다 "30건 미만은 신뢰 낮음" 경고 상태를 벗어나려면 실사용자가 늘어야 한다.

#### 6.7.10 재학습 완료 — place_id 그룹 분할 버그 발견·수정

6.7.6·7.2 에서 준비한 화면 재촬영 합성(`recapture()`)을 실제로 데이터셋에 섞어 Colab 에서 재학습한 첫 결과는 예상과 정반대였다: `classification_report` 에서 무효 클래스의 precision·recall·f1 이 전부 0.000(support 370)이었다 — 무효 사진을 화면 재촬영이든 스크린샷이든 단 한 장도 못 잡는, 기존 배포 모델(invalid_recall 0.918)보다 훨씬 나쁜 결과였다. 나머지 4개 클래스는 recall 0.6~0.99 로 오히려 높아, 무효 사진이 전부 다른 카테고리로 잘못 분류되고 있었다.

원인은 학습 데이터가 아니라 **`build_dataset.py` 의 place_id 그룹 분할 로직**이었다. `place_id()` 는 파일명을 `"__"` 기준으로 잘라 앞부분을 그룹 키로 쓴다(같은 장소 사진이 train·test 에 걸치는 걸 막기 위함). 그런데 `make_negatives.py` 가 만드는 합성 무효 파일명은 전부 `synth__NNNN.jpg` / `degrade__NNNN.jpg` / `recapture__NNNN.jpg` 형태라, 수백~수천 장이 **소스 종류별로 그룹 3개에만 뭉쳤다.** `_assign_groups()` 는 그룹을 통째로 한 split 에만 배정하므로, 특정 합성 소스(예: `recapture` 전체)가 train 에는 전혀 안 들어가고 val 에만 들어가는 식의 극단적 쏠림이 생겼다. 6.3절에서 언급했듯 그룹이 3개 미만이면 이미지 단위 분할로 대체하는 안전장치가 있는데, `recapture()` 추가 전에는 무효 소스가 2종(스크린샷·열화)이라 이 안전장치가 항상 작동해 문제가 가려져 있었다 — 3번째 소스를 추가하자 그룹 수가 정확히 임계값(3개)을 넘겨 안전장치를 벗어났다.

수정은 `make_negatives.py` 의 파일명에서 `"__"` 를 제거해(`synth_NNNN.jpg` 등 단일 언더스코어) place_id() 가 파일마다 고유 그룹으로 인식하게 한 것이다. 같은 조건으로 재학습한 결과:

| | 수정 전(버그) | 수정 후 |
| --- | ---: | ---: |
| invalid recall | 0.000 | **0.932** |
| macro F1 | 0.489 | **0.809** |
| valid false-reject (hardReject 0.22) | — | 0.146 |

기존 배포 모델(recapture 미포함, invalid_recall 0.918)과 비교해도 recall 이 오히려 올랐다 — 이번엔 화면 재촬영 유형까지 포함해서 나온 수치이므로 실질적 개선이다. 체험 클래스는 여전히 가장 약하다(f1 0.632, recall 0.567) — 6.4 절의 도메인 격차가 원인이라 크라우드소싱 실사진 보강(`ml/data/import_collected.py`)이 필요하다.

새 모델(`app/src/main/assets/photo_verifier.onnx` 등)을 실기기(Galaxy S8)에 설치하고 `OnnxPhotoVerifierTest`(6건, 5 통과·1 스킵)로 on-device 로딩·추론을 재확인했다 — 폴백 경로(`"모델 미탑재"` 사유)를 타지 않고 실제 추론이 도는 것을 확인했다.

### 6.8 판정 결과 UX: Toast 에서 전체 화면 결과 화면으로

6.6절까지의 판정 로직·저장 구조는 정확했지만, **사용자에게 결과를 보여주는 방식**에 두 가지 문제가 있었다.

#### 6.8.1 문제 1: `NEEDS_REVIEW` 가 관리자 승인 전인데 "완료"로 보임

`MissionCompletion.resolve()` 가 `needsReview` 여부를 보지 않고 항상 `Completed` 전환 + 포인트 지급을 실행했다. 사진이 관리자 검수 큐로 올라간 상태(승인 전)인데도 사용자 화면에는 미션이 완료된 것으로 표시되어, 6.6절이 설계한 "검수는 사후 부정 적발용" 이라는 전제가 실제로는 관철되지 않고 있었다 — 관리자의 승인이 사실상 요식행위였던 셈이다.

`resolve()` 에 `needsReview: Boolean` 파라미터를 추가해 검수 대기 중에는 `status` 를 `In Progress` 로 유지하고 포인트·완료 처리를 보류하도록 고쳤다. 관리자 승인 시점의 완료·보상 전환은 별도 함수 `resolveApproval()` 로 분리했고(중복 승인 가드 포함), `AdminPhotoReviewScreen.approve()` 가 트랜잭션으로 실제 완료·포인트 지급·`completionCount` 증가를 수행하도록 연결했다. `reject()` 는 제출 시점에 포인트를 준 적이 없으므로(수정 전에는 지급했다가 회수하는 흐름이었다) 회수 로직을 없애고 `In Progress` 로만 되돌리도록 정리했다. `MissionCompletionTest` 에 회귀 테스트 4건을 추가했다.

#### 6.8.2 문제 2: 판정 결과가 Toast 로 잠깐 뜨고 사라짐

사진 제출 직후 "사진 인증 완료" / "관리자 검수 후 포인트 지급" 같은 결과가 `Toast` 로만 표시되고, 화면이 곧바로 이전 화면으로 돌아가(성공 콜백이 결과 메시지와 `navigateBack = true` 를 동시에 세팅) Toast 를 놓치면 무엇이 어떻게 판정됐는지 다시 확인할 방법이 없었다. 특히 `PASS` 와 `NEEDS_REVIEW` 를 구분하지 못해 검수 대기 중인 미션을 "완료된 미션"으로 오인하는 사례로 이어졌다(6.8.1 과 같은 증상의 UI 측 원인).

**해결**: AI 판정이 끝나면 전체 화면으로 결과를 보여주고, 사용자가 명시적으로 버튼을 눌러야 닫히도록 바꿨다. 이 앱에는 BottomSheet 를 쓴 화면이 없고(`AdminPhotoReviewScreen` 의 `AlertDialog` 가 유일한 기존 모달 사례) Navigation 그래프에 결과 전용 경로를 새로 만들면 판정 데이터를 다시 직렬화해 넘겨야 해 더 복잡해지므로, `MissionPerformViewModel` 이 이미 들고 있는 `uiState` 를 그대로 쓰는 `Dialog(usePlatformDefaultWidth = false)` 전체 화면 오버레이를 선택했다.

- **공통 정보**: 제출 사진, 판정 상태(PASS/REVIEW/REJECT), 상태별 제목·설명, 미션이 요구한 카테고리 vs AI 가 예측한 카테고리(`categoryLabel()` 로 번역해 내부 라벨을 그대로 노출하지 않음 — "무효" 라벨도 신규 `category_invalid`("판정 불가")로 번역), confidence(소수점 없는 정수 %, 개발자용 숫자처럼 강조하지 않고 보조 정보로), 포인트 지급 여부, "사진은 기기에서 안전하게 분석되었어요" 온디바이스 고지, 접이식 "분석 정보" 영역에만 모델 버전 노출.
- **REJECT 사유 분류**: 도메인 계층(`PhotoVerification`)이 사유를 `RejectReasonCode`(무효 대상 / 카테고리 불일치 / 저신뢰) 로만 분류하고, 실제 다국어 문구는 UI 레이어 `strings.xml` 에서 고른다 — `PhotoVerification` 이 Android `Context` 에 의존하지 않는 순수 Kotlin 이라 직접 번역할 수 없기 때문이다(4.7절과 같은 제약).
  - 무효 대상: `s[무효] ≥ invalidRejectThreshold` 로 REJECT 된 경우
  - 카테고리 불일치: 다른 카테고리 점수가 미션 카테고리 점수보다 높게 나온 경우
  - 저신뢰: 그 외(전반적으로 애매하거나 어둡거나 흐린 사진)
- **오류 구분**: 온디바이스 AI 추론은 네트워크와 무관하므로, `PhotoGate.decide()` 자체가 예기치 않게 실패하는 경우(신규 `Stage.ANALYZE`, "AI 분석 오류")와 판정 이후 업로드·Firestore 저장이 실패하는 경우(`Stage.UPLOAD`/`Stage.FINALIZE`, "저장 오류")를 서로 다른 안내로 분리했다. 기존에는 `decide()` 호출이 try/catch 로 감싸여 있지 않아, 예상 밖 예외가 나면 성공·실패 콜백이 아예 불리지 않고 화면이 "분석 중" 스피너에서 멈추는 잠재 결함이 있었다 — 이번에 함께 고쳤다.
- **화면 회전·중복 제출**: 분석 중 상태와 결과 모두 `MissionPerformViewModel.uiState`(ViewModel 소유)에 있어 화면 회전에도 그대로 복원된다. 제출 함수는 `isUploading`·`missionCompleted`·`photoResult != null` 세 조건으로 재실행을 막고, 결과가 떠 있는 동안은 `Dialog` 가 화면 전체를 가려 아래 버튼을 다시 누를 수 없다.

| 판정 | 주요 버튼 | 다음 동작 |
| --- | --- | --- |
| `PASS` | 완료 | 결과를 닫고 이전 화면으로 |
| `NEEDS_REVIEW` | 확인 | 결과를 닫고 이전 화면으로 (완료 여부는 관리자 승인 후 확정, 6.8.1) |
| `REJECT` | 다시 촬영 / 미션 내용 보기 | 다시 촬영: 같은 화면에서 재촬영(포인트·완료 처리 없음) / 미션 내용 보기: 이전 화면으로 |

#### 6.8.3 실기기 검증 중 발견: 검수 대기 재인증이 `PERMISSION_DENIED` 로 저장 실패

6.8.2 의 전체 화면을 실기기(Galaxy S8)에 설치해 검증하던 중, 검수 대기(`NEEDS_REVIEW`) 상태였던 미션을 재촬영했더니 분석은 정상 완료(`PhotoVerify` 로그: `Proceed`, 유사도 결합으로 `PASS` 승격)됐는데 Firestore 저장 단계에서 "분석은 완료됐지만 결과를 저장하지 못했어요" 오류로 끝나는 것을 발견했다. 이전에는 `Stage.UPLOAD`/`Stage.FINALIZE` 실패의 원인 예외를 로그에 남기지 않아 왜 실패하는지 알 수 없었는데, 6.8.2 작업 중 이 두 단계에 `Log.e` 를 추가해 두어(원인 진단 목적) 실제 원인을 바로 특정할 수 있었다.

```
FINALIZE 단계 실패: mission=숙대입구 docId=...
com.google.firebase.firestore.FirebaseFirestoreException: PERMISSION_DENIED: Missing or insufficient permissions.
```

**원인**: `firestore.rules` 의 `user_missions` 업데이트 규칙에 "사용자는 자기 `photoNeedsReview` 를 true→false 로 되돌릴 수 없다(관리자 승인 우회 방지)"는 조건이 있었다(4장 3.6절). 그런데 검수 대기 중 재촬영해 AI가 이번엔 자동 `PASS` 를 낸 것도 **똑같은 모양의 쓰기**(`photoNeedsReview: true → false`)라 이 규칙에 함께 걸렸다 — "사용자가 검수를 몰래 우회하는 것"과 "재판정으로 실제 통과한 것"을 Firestore 규칙만으로는 구분할 방법이 없다는 3.6절의 한계(서버 없이는 값 자체를 검증 못함)가 실제로 드러난 사례다.

**조치**: 이 조건을 제거했다. 이 규칙이 막던 위협(클라이언트가 임의로 자기 상태를 조작)은 애초에 서버(Cloud Functions) 없이는 완전히 막을 수 없고(10장), 반대로 제거로 인해 새로 열리는 구멍도 없다 — 어차피 `photoNeedsReview` 를 false 로 쓰면서 포인트까지 지급하려면 `resolve()` 의 계산 로직을 거쳐야 하는데, 그 계산은 클라이언트 코드 안에 있어 이 규칙 유무와 무관하게 이미 신뢰 경계 밖이었기 때문이다. `firestore-tests/rules.test.js` 의 해당 테스트를 `assertFails` → `assertSucceeds` 로 뒤집고 에뮬레이터로 20건 전체 재확인한 뒤 `firebase deploy --only firestore:rules --project grad-proj-5e09c` 로 프로덕션에 반영했다.

**교훈**: `Stage.UPLOAD`/`Stage.FINALIZE` 실패를 사용자에게는 "저장 오류"로 뭉뚱그려 보여주되(6.8.2), 실제 원인은 로그로 남겨두는 방어선이 없었다면 이 버그는 "가끔 저장이 안 된다"는 막연한 증상으로만 남았을 것이다. 보안 규칙이 정상적인 재시도 흐름을 막는 이런 종류의 false positive 는 유닛 테스트(규칙 20건)만으로는 못 잡는다 — 규칙 테스트는 "설계한 대로 동작하는가"만 검증하고, "설계 자체가 실제 사용 흐름과 맞는가"는 실기기 사용으로만 드러난다.

#### 6.8.4 성취 연출 확장: 레벨업 · 배지 획득 반영

PASS 판정 직후 결과 화면(6.8.2)이 뜨기 전에 짧게(2.2~3.2초) 보여주는 성취 연출(`MissionSuccessCelebrationOverlay`)에, 이번 완료로 레벨이 올랐거나(4.4.1) 새 배지를 얻었으면(4.4.2) 그 사실을 함께 보여주도록 확장했다.

- **레벨업/배지 유무로 완료 트랜잭션 결과를 확장**: `MissionRepository.CompleteResult` 에 `totalPointsAfter`/`weeklyCompletedAfter`/`newlyUnlockedBadges` 를 추가해, `FirebaseMissionRepository` 의 완료 트랜잭션이 반환하는 값만으로 뷰모델이 레벨업 여부(`TravelLevelPolicy.progressFor(지급 전 포인트)` vs `progressFor(지급 후 포인트)` 비교)와 새 배지를 판단한다. 기존에 이번 주 진행도 표시를 위해 완료 트랜잭션 뒤 `countMissionsCompletedThisWeek()` 를 한 번 더 쿼리하던 것도, 완료 트랜잭션이 이미 원자적으로 올린 주간 카운터 값을 그대로 돌려주는 것으로 대체해 추가 왕복을 없앴다.
- **연출 길이를 내용에 맞춰 가변으로**: 레벨업이나 배지 획득처럼 더 보여줄 게 있으면 2.2초 대신 3.2초로 더 길게 튼다(사용자가 "너무 짧다"고 피드백해 원래 1.1초 → 2.2초로 늘린 뒤, 내용이 늘어난 지금 다시 한번 늘렸다). 애니메이션 진행도(`Animatable`)의 35% 지점부터 레벨/배지 영역을 페이드인시켜, 정보가 한꺼번에 쏟아지지 않고 순차적으로 드러나게 했다.
- **한 번만 실행**: 레벨업 배너·배지 알림은 별도의 "이미 봤음" 플래그 없이, 이 연출 자체가 `MissionPerformViewModel.uiState.celebration` 이 null 이 아닐 때만 뜨고 한 번 닫히면(`onCelebrationFinished()`) 다시 null 이 되는 일회성 상태이므로 구조적으로 한 번만 보인다.

---

## 7. 구현 현황

### 7.1 완료

- [x] 앱 전체 기능: 인증·온보딩, 미션 목록·지도, 2단계 인증 흐름, 포인트·랭킹, 개인화 추천, 관리자 기능
- [x] 순수 도메인 로직 분리 + 단위 테스트 (`GeoDistance`, `LocationVerification`, `MissionRewardPolicy`, `MissionCompletion`, `TravelPreference`, `MissionFeatures`, `MissionScorer`, `MissionRecommender`, `LearnedReranker`, `PhotoVerification`)
- [x] 사진 인증 판정 로직 `PhotoVerification` + `PhotoVerificationConfig` + 테스트 7건
- [x] 추론 인터페이스 `PhotoVerifier` (+ `FakePhotoVerifier`), 업로드 전 결정 `PhotoGate` + 테스트 7건
- [x] 미션 완료 흐름 연결 (업로드 전 판정, 결과 기록, `REJECT`/`NEEDS_REVIEW` UX 분기)
- [x] 관리자 사진 검수 큐 화면
- [x] Firestore 보안 규칙 `firestore.rules` + 에뮬레이터 테스트 20건 (`firestore-tests/`)
- [x] 데이터셋 구축 스크립트 `ml/data/`, 학습 노트북, ONNX export 스크립트 — 더미 데이터로 파이프라인 전 구간(데이터 로드 → CLIP 제로샷 → 학습 → 평가 → 임계값 → ONNX export) 스모크 테스트 완료
- [x] 실제 데이터셋 구축 (6,063장, HF Hub `kimgayeon430/travel-mission-photos`) — 6.3절
- [x] `OnnxPhotoVerifier` (ONNX Runtime Mobile) 구현 — `assets/` 의 모델·전처리·라벨 json 을 읽어 추론, 모델 없으면 `null` 반환해 앱 무영향
- [x] Supabase Storage 사진 업로드 (InvalidKey·RLS 이슈 수정 후 실기기 동작 확인)
- [x] 마이페이지 포인트 적립 내역 화면 (`PointHistoryScreen`)
- [x] Colab T4 에서 파인튜닝 → `photo_verifier.onnx`(20MB) 를 `assets/` 에 번들, 임계값을 `PhotoVerificationConfig.DEFAULT` 로 반영, 기본 verifier 를 `OnnxPhotoVerifier`·`DEFAULT` config 로 전환 (test macro-F1 0.81, 6.7.10)
- [x] 화면 재촬영 합성(`recapture()`) 포함 재학습 + `build_dataset.py` place_id 그룹 분할 버그 발견·수정(6.7.10) — invalid recall 0.0(버그) → 0.932, 실기기 계측 테스트로 on-device 추론 재확인
- [x] 학습된 추천 re-ranker: `MissionFeatures`·`LearnedReranker`·`MissionRecommender.recommendReranked` + `ml/reco/` 파이프라인 + `assets/reranker.json`. 신호 6개(시간대 적합도 포함), 시뮬레이터 학습본으로 규칙 대비 AUC 0.949→0.960, NDCG@5 0.914→0.953 (8.3절)
- [x] 모델 에셋 로딩 계측 테스트 (`OnnxPhotoVerifierTest` 6건, `RerankerSourceTest` 4건) — 실기기(Galaxy S8, API 28)에서 실제 에셋으로 추론·로딩 검증. 두 로더 모두 실패를 `runCatching` 으로 삼켜 **무증상 고장**(사진: 전건 검수 큐행 / 추천: 규칙 기반 폴백)이 나므로, 재학습 모델 교체 시 신호 순서·라벨 불일치를 잡는 방어선
- [x] 참조 이미지 임베딩 유사도(6.7절) — `CLIP ViT-B/32` 임베딩 인코더 채택(pooled feature 재사용은 분리도 AUC 0.60 으로 기각, `ml/embedding_separability.py`). 온디바이스 `OnnxClipPhotoEmbedder`(88.6MB int8, HF Hub 런타임 다운로드+`filesDir` 캐시), 판정 규칙 `PhotoVerification.verify` 에 유사도 ±1단계 결합, 참조 임베딩 사전계산 `ml/embed_missions.py`, EXIF 회전 정합(`ImagePreprocess` + `androidx.exifinterface`). 단위 테스트 신규 13건 포함 `PhotoVerificationTest` 18 + `PhotoGateTest` 8 통과, `testDebugUnitTest`·`compileReleaseKotlin` BUILD SUCCESSFUL. 실기기(Galaxy S8) 계측 테스트 통과 + 유사도 신호 실동작·구제 경로 확인(6.7.7). 임계값(rescue 0.50 / suspect 0.68)은 공개 scene 프록시 기반 잠정치
- [x] 다중 참조 이미지(최대 유사도, 6.7.8) — 오프라인 프로토타입(J 0.33→0.47)으로 효과 확인 후 실제 구현. `referenceEmbedding: FloatArray?` → `referenceEmbeddings: List<FloatArray>`, `PhotoEmbedding.maxCosineOrNull` 로 전 계층(`PhotoVerification`/`PhotoGate`/`MissionRepository`/`FirebaseMissionRepository`/`MissionPerformViewModel`) 반영. Firestore 가 배열의 배열을 지원하지 않아 원소를 `{"v":[...]}` 맵으로 감싼 구조로 수정(발견·수정 과정 포함). 8개 미션 실제 백필 + held-out 검증(J 0.21→0.33) 완료. `PhotoVerificationTest`/`PhotoGateTest` 다중 참조 테스트 추가
- [x] 마이페이지 한국어/English/日本語 3개 언어 UI 전환(4.7절) — `LanguagePreference`(`SharedPreferences`) 저장값을 `MainActivity.attachBaseContext` 가 `Configuration` 으로 감싸 액티비티 재생성 시 반영(수동 Locale 전환; `ComponentActivity` 에서는 `AppCompatDelegate.setApplicationLocales` 가 즉시 갱신되지 않아 미사용). 랜딩·로그인·회원가입·홈·미션 목록/상세/지도/수행·취향 선택·마이페이지·포인트 내역·진행 중/완료 미션·랭킹·관리자 5개 화면 전 UI 문구를 `values/strings.xml`(한국어)·`values-en/strings.xml`(영어)·`values-ja/strings.xml`(일본어) 리소스로 분리(1:1 키 대응). Compose 밖(ViewModel)의 문구는 호출 시점마다 저장된 언어를 다시 읽는 `Context.getLocalizedString()` 확장으로 처리. 미션 제목·설명은 Firestore `title`/`titleEn`/`titleJa`, `desc`/`descEn`/`descJa` 필드를 `localizedString()` 이 선택(미번역 시 한국어로 폴백)하고, 카테고리·진행 상태 같은 내부 코드값은 항상 한국어로 유지한 채 표시할 때만 번역
- [x] 검수 대기(`NEEDS_REVIEW`) 미션이 관리자 승인 전에 완료·포인트 지급되던 버그 수정(6.8.1) — `MissionCompletion.resolve()`/`resolveApproval()` 분리, `AdminPhotoReviewScreen.approve()`/`reject()` 가 실제 완료·보상 처리를 수행하도록 연결, 회귀 테스트 4건 추가
- [x] 사진 인증 결과를 전체 화면으로 표시(6.8.2) — Toast 대신 `Dialog` 전체 화면 오버레이로 PASS/REVIEW/REJECT 판정·근거·포인트를 보여주고, REJECT 사유를 `RejectReasonCode` 로 분류. AI 분석 실패(`Stage.ANALYZE`)와 저장 실패(`Stage.UPLOAD`/`FINALIZE`)를 구분한 안내로 분리
- [x] 검수 대기 중 재인증이 `PERMISSION_DENIED` 로 저장 실패하던 `firestore.rules` 버그 발견·수정·재배포(6.8.3) — 실기기 검증 중 발견, `firestore-tests/rules.test.js` 갱신 후 에뮬레이터 20건 재확인
- [x] 여행 레벨 시스템(4.4.1) — `TravelLevel`/`TravelLevelPolicy` 순수 Kotlin 구현(구간 기준 진행률), `TravelLevelPolicyTest` 6건
- [x] 여행 배지 3종(4.4.2) — `BadgeId`/`BadgeUnlockEvaluator` + `MissionRewardCounters`(완료 트랜잭션 내 원자적 카운터·배지 기록, 사진 제출 즉시완료·관리자 승인 두 경로 모두 반영), `BadgeUnlockEvaluatorTest` 7건, 기존 `users` 스키마는 optional 필드만 추가해 하위 호환
- [x] 프로필 화면에 레벨 진행률 카드·배지 3종 카드(획득/미획득 상태 구분) 추가
- [x] 성취 연출에 레벨업 배너·배지 획득 알림 반영, 연출 길이 가변화(6.8.4)
- [x] 미션 성공 화면 하단 다음 미션 추천 카드(4.5.1) — 기존 `MissionRecommender.recommendScored()` 재사용, `Mission.estimatedMinutes` optional 필드 신규
- [ ] 위 레벨·배지·다음 미션 추천 기능은 컴파일·단위 테스트만 확인했고 실기기/에뮬레이터 화면은 아직 보지 못함 — 다음 세션에서 레벨업 연출, 배지 3종 각각의 획득 순간, 프로필 화면 표시를 실제로 확인할 것
- [x] 미션 지도 핀 상태별 색상 구분 + 완료 미션 인증 사진 정보창(4.2절)
- [x] 홈 추천 카드·미션 상세 화면에 실제 거리 표시(`GeoDistance.format`), 미션 상세 화면 "길찾기" 버튼(`geo:` Intent) — 실기기 미검증
- [x] 사용자 미션 제안(제목·설명·카테고리·예상 소요시간·대표 이미지·지도 탭 위치 선택) → 관리자 검수(승인 시 포인트 확정/수정요청/반려, 사유는 제안자에게 노출) → 승인된 미션만 목록·지도·추천에 노출(4.8절). `MissionReviewStatus` 순수 로직 + 단위 테스트, `MissionEngagement`(좋아요·찜 토글) 신규
- [x] 좋아요/찜 — 모든 미션에 추가, 문서 ID 고정(`{uid}_{missionId}`)으로 중복 방지, 미션 목록/상세 화면에 버튼과 카운트 표시, 마이페이지 "내가 만든 미션"·"찜한 미션" 전용 화면(4.8절)
- [x] `firestore.rules` 에 사용자 제안 생성/재제출·좋아요·찜 권한 추가, 에뮬레이터 테스트 18건 추가(총 38건) — 자기 승인·포인트 자가 설정·타인 명의 반응 생성이 모두 거부됨을 확인(3.6·4.8·8.1절)
- [ ] 위 미션 제안·검수·좋아요/찜 기능은 컴파일·단위 테스트·Firestore 규칙 에뮬레이터 테스트만 확인했고 실기기 화면은 아직 보지 못함 — 지도 탭 위치 선택, 갤러리 이미지 업로드, 검수 승인/반려 플로우를 실제로 확인할 것

### 7.2 남은 작업

- [x] `photo_embedder_int8.onnx`(88.6MB) 를 HF Hub `kimgayeon430/travel-mission-photo-embedder`(Public) 에 업로드, `ml/embed_missions.py` 로 미션 **16/16건** `photoEmbedding`(512d) + `photoEmbeddingModelVersion` 백필 완료 (`숙대입구`는 `imageUrl` 등록 후 추가 백필). export 산출물 sha256 이 HF 업로드본과 동일해 참조 임베딩이 앱 다운로드 모델과 일치
- [x] 실기기에서 사진 인증 전체 루프 확인 — 임베더 HF 다운로드→캐시(`prefetch`), 유사도 결합, 구제 경로(`REJECT`→`NEEDS_REVIEW`), `NEEDS_REVIEW`→관리자 승인/반려 둘 다 실기기+실제 관리자 검수로 확인(6.7.7·6.7.9). 남은 것은 자동 `PASS`(검수 없이 바로 통과) 케이스 관측뿐
- [ ] 유사도 임계값 실측 보정 — 실사용 1/1(6.7.9) + 웹 프록시 34/34(6.7.8) + 백필 후 held-out 12/12(6.7.8) 확보했으나 전부 확정에는 부족(30건 미만이거나 프록시성). `ml/calibrate_similarity.py`(실사용) / `ml/calibrate_similarity_web.py`(웹 프록시) 로 계속 표본 축적
- [x] 참조 이미지 배열화(다중 참조, 최대 유사도) 실제 구현·배포 — `PhotoVerification.verify`/`PhotoGate`/`MissionRepository`/`FirebaseMissionRepository`/`MissionPerformViewModel` 전 계층 반영, Firestore 스키마 버그(배열의 배열 미지원) 발견·수정, 8개 미션 실제 백필 + held-out 검증으로 분리력 개선(J 0.21→0.33) 확인 완료(6.7.8)
- [x] 화면 재촬영 합성 augmentation `ml/data/make_negatives.py`(`recapture()`, 베젤·무아레·글레어) 추가 — 기존 스크린샷/열화 소스에 3번째 소스로 섞여 들어간다. 합성 12장을 배포 중인 `photo_verifier.onnx` 에 직접 돌려보니 `s[무효]` 0.011~0.129(평균 ≈0.04) — 6.7.7 의 실기기 관측(0.066~0.07)과 같은 패턴을 재현·정량화. 기존 `degrade()`(블러·저조도)는 이 패턴을 못 잡는다는 뜻이라 별도 소스로 추가함
- [x] 위 표본을 실제로 섞어 재학습(Colab) — 완료(6.7.10). place_id 그룹 분할 버그 발견·수정 포함, invalid recall 0.932·macro-F1 0.809 로 `thresholds.json`/`PhotoVerificationConfig`/6.7.10 반영 완료
- [ ] 크라우드소싱 사진으로 각 클래스 보강(특히 체험) 후 재학습 — 무효는 합성 augmentation 으로 대체 완료, 체험은 도메인 격차(6.4)가 남아 실사진이 필요. 수집 스크립트 `ml/data/import_collected.py` 준비 완료, 실사진 수집 자체는 별도
- [ ] `user_missions` 로그로 추천 re-ranker 재학습(`--from-firestore`), 시뮬레이터 학습본 대체
- [x] `firestore.rules` 배포 — 규칙 테스트 20건 통과 재확인 후 `firebase deploy --only firestore:rules --project grad-proj-5e09c` 실행, 프로덕션 반영 완료
- [x] 사진 인증 결과 전체 화면(6.8.2) 실기기 확인 — Galaxy S8 에서 결과 화면 표시 및 재인증 흐름 확인, 그 과정에서 6.8.3 의 `firestore.rules` 버그를 실사용으로 발견·수정
- [ ] REVIEW/REJECT 판정과 화면 회전을 포함한 나머지 경우는 실제 기기에서 아직 전부 확인하지 못함
- [ ] Robolectric 기반 ViewModel/Compose UI 테스트 `[선택]`
- [ ] 서버측 포인트 검증(Cloud Functions) `[선택]`

### 7.3 구현 중 해결한 문제

| 문제 | 원인 | 해결 |
| --- | --- | --- |
| 목표 지점에 서 있는데 위치 인증이 실패 (24 km 떨어졌다고 판정) | API 30 미만 경로가 `getLastKnownLocation` 으로 **캐시만** 읽었다. 측위를 하지 않으므로 실내·장기 미실행 시 수 시간 전 다른 지역 좌표가 반환된다 (테스트 기기 API 28 에서 18시간 전 GPS 캐시 잔존). GPS 캐시를 먼저 보므로 더 최신인 network 좌표가 있어도 낡은 값이 우선했다 | `LocationManagerCompat.getCurrentLocation` 으로 통일해 구버전에서도 실제 측위를 수행(API 분기 제거). GPS 실패 시 network 폴백, `elapsedRealtimeNanos` 기준 2분 신선도 검사 추가. `ContextCompat.getMainExecutor` 로 `minSdk 26` 안전성도 함께 확보 |
| 사진 업로드가 항상 `400 InvalidKey` | 미션 문서 ID 가 한글 제목이라 Supabase 스토리지 키 규칙 위반 | 경로의 미션 폴더명을 `해시_ASCII정규화`(`missionKey`)로 변환 |
| 사진 업로드가 `403` RLS 거부 | `x-upsert: true` 가 `UPDATE` 정책을 요구하나 anon 은 `INSERT` 정책만 보유 | 항상 유일한 경로이므로 `x-upsert` 를 끄고 새로 `INSERT` |
| 저사양(RAM 8GB) 환경에서 Gradle sync 중 데몬 강제 종료 | 데몬 힙(2GB) + IDE 메모리 압박 | `gradle.properties` 데몬 힙 1.5GB, `org.gradle.workers.max=2` |
| `onnxruntime-android` 도입 후 APK +120MB | 모든 ABI 네이티브 라이브러리 포함 | `abiFilters` 로 `arm64-v8a`/`x86_64` 만 (+20MB 수준) |
| 학습 노트북이 `transformers` 5.x 에서 학습 실패(`KeyError: 'image'`) | `Trainer` 가 `with_transform` 데이터셋의 컬럼을 제거 | `TrainingArguments(remove_unused_columns=False)`, 전처리를 `AutoImageProcessor` 에 위임(export·앱 전처리와 자동 정합) |
| 공개 데이터 수집 시 투어 3 scene·쇼핑 1 scene 만 확보 | `datasets` 스트리밍이 클래스 정렬 상태라 shuffle 버퍼가 몇 개 클래스만 담음 | 스트리밍 대신 Places365/Food-101 **validation 셋을 통째로 받아** scene 별로 표본 (투어 107·체험 53·쇼핑 28 scene 확보) |
| 일부 scene(`market/indoor` 등)이 한 그룹으로 뭉침 | 파일명 생성 시 `scene.split('/')[-1]` 로 접미어만 사용 | 전체 scene 경로를 정규화(`canonical_scene`)해 그룹 키로 사용 |
| Colab 에서 `optimum` ONNX export 실패 | `optimum.exporters` 가 `diffusers` 를 import 하는데 Colab 의 `diffusers`/`huggingface_hub` 버전 불일치 | `torch.onnx.export`(레거시, `dynamo=False`, opset 18) 로 직접 변환. int8 양자화는 shape inference 오류로 생략하고 fp32(20MB) 채택 |
| `torch.onnx` 가 가중치를 `photo_verifier.onnx.data` 로 분리 저장 | 새 torch 의 external-data 기본 동작 | `onnx.save(..., save_as_external_data=False)` 로 단일 파일화 (앱은 `.onnx` bytes 만 로드) |
| 윈도우 환경에서 `testDebugUnitTest` 가 전량 실행 불가 (`ClassNotFoundException: GradleWorkerMain`) | Gradle 이 테스트 워커 classpath 를 `@argfile` 로 UTF-8 기록하는데 JDK 런처는 네이티브 인코딩(`MS949`)으로 파싱한다. 사용자 홈 경로의 한글이 깨져 `gradle-worker.jar` 를 찾지 못함. 컴파일은 성공해 코드 문제로 오인하기 쉬움 | `GRADLE_USER_HOME` 을 ASCII 경로로 이전. 동일 argfile 을 MS949 로 인코딩하면 정상 로드되는 것으로 원인 확정 |
| '투어' 미션에 개발 책상 사진을 냈더니 `REJECT` — 판정은 맞지만 카테고리만으로는 "같은 유형의 다른 대상"을 못 거른다 (6.7) | 5-클래스 분류는 유형만 본다. 대상 동일성 검증 계층이 없음 | 참조 이미지 임베딩 유사도 결합. 단, 분류기 pooled feature 를 임베딩으로 재사용하려 했으나 `embedding_separability.py` 측정에서 "같은 카테고리·다른 대상" 분리도 AUC 0.60 → 분류 파인튜닝으로 feature 가 클래스 방향으로 붕괴한 것으로 확인, CLIP 인코더(AUC 0.76)로 교체 |

---

## 8. 평가 계획

### 8.1 도메인 규칙 · 보안 규칙

- **JUnit4 단위 테스트**로 거리·보상·완료·취향·추천·사진 판정·미션 검수 상태 규칙을 검증한다(경계값 포함). 사진 인증: `PhotoVerificationTest`, `PhotoGateTest`. 추천: `MissionFeaturesTest`, `LearnedRerankerTest`, `MissionRecommenderRerankedTest`. 미션 제안 검수: `MissionReviewStatusTest`(4.8절).
- **Firestore 보안 규칙 테스트** (`firestore-tests/`, `@firebase/rules-unit-testing` + 에뮬레이터, 38건): 게스트/일반/관리자 컨텍스트로 `admins`·`missions`·`users`·`user_missions`·`mission_likes`·`mission_bookmarks` 의 읽기·쓰기 허용/거부를 검증한다. 핵심: 일반 사용자가 `missions.completionCount`/`likeCount`/`bookmarkCount` 외 필드를 못 바꾸고, 남의 진행 문서는 못 건드리며, 미션 제안 시 `points`/`reviewStatus` 를 스스로 정하거나 재제출 중 바꿀 수 없고, 좋아요/찜은 문서 ID 형식(`{uid}_{missionId}`) 위반이나 타인 명의 생성이 모두 거부된다(4.8절). (`photoNeedsReview` true→false 차단 테스트는 실사용 버그로 6.8.3 에서 반대로 뒤집었다 — 이제 본인 진행 문서 재인증으로 통과할 수 있다)
- ViewModel 레벨 테스트는 `android.net.Uri`·`android.location.Location` 의존으로 순수 JUnit 에서 불가하며, Robolectric 도입은 향후 과제로 둔다.

### 8.2 사진 인증 모델

**학습 결과** (Colab T4, `mobilevit-small-fullft-1`, test 셋 893장 — `recapture()` 반영 재구축 후, 6.7.10. train↔test scene 겹침 0)

| 모델 | test accuracy | test macro-F1 |
| --- | ---: | ---: |
| CLIP 제로샷 (`clip-vit-base-patch32`, 학습 없음) | 0.648 | 0.627 |
| MobileViT-small 전체 파인튜닝 | **0.840** | **0.809** |

클래스별 F1: 투어 0.79 · 맛집 0.94 · **체험 0.63** · 쇼핑 0.73 · 무효 0.96.
체험이 가장 약함(recall 0.57) — 공개 데이터가 실제 "체험 미션 사진"과 도메인이 다르고 카테고리 경계가 모호. 크라우드소싱 사진 보강이 향후 과제(`ml/data/import_collected.py` 준비 완료).

**임계값 선정** (`ml/thresholds.json`, test 셋 임계값 스윕)

| 임계값 | 값 | 근거 |
| --- | ---: | --- |
| `invalidRejectThreshold` | 0.55 | 무효 사진 차단율 0.93, 화면 재촬영 유형 포함(6.7.10) |
| `hardRejectThreshold` | 0.22 | 정상 사진 오탐 0.146 을 낮추는 방향. 애매한 사진은 `REJECT` 대신 `NEEDS_REVIEW` 로 |
| `autoPassThreshold` | 0.65 | 정상 사진의 약 71%가 자동 통과 |

**온디바이스 비용**

- 분류 모델: fp32 ONNX 약 20MB (int8 양자화는 Colab 라이브러리 충돌로 미적용, fp32 채택).
- 임베딩 모델(6.7.4): CLIP ViT-B/32 int8 ONNX ≈ 88.6MB. APK 에 번들하지 않고 최초 사용 시 HF Hub 에서 받아 `filesDir` 캐시.
- `onnxruntime-android` 도입 시 디버그 APK 약 +120MB(전 ABI) → `abiFilters`(arm64-v8a/x86_64) 적용 후 약 +20MB.
- 추론 지연: 분류 ≈ 0.9초(실기기 Galaxy S8). 임베더 모델은 미션 수행 화면 진입 시 `prefetch` 로 미리 받아, 6.7.7 관측에서 첫 인증이 다운로드에 막히지 않았다. 임베딩 추론 지연 측정 `[TODO]`.
- 전처리·라벨·모델버전은 export 산출물(`photo_verifier_preprocessor.json` / `_labels.json` / `_version.txt`, 임베더는 `photo_embedder_preprocessor.json`)에서 읽어 자동 정합. 전처리 코드는 `ImagePreprocess` 로 분류·임베딩이 공유하며 EXIF 회전을 반영한다.

### 8.3 학습된 추천 re-ranker

규칙 점수(`MissionScorer`, 손튜닝 가중치)에 완료 로그로 학습한 **로지스틱 회귀 re-ranker** 를 얹은
하이브리드(4.5절). 신호 6개는 규칙·학습이 공유한다(`MissionFeatures`).

- **파이프라인**(`ml/reco/`): `build_dataset.py`(로그 → 학습행) → `train_reranker.py`(로지스틱 회귀 → `reranker.json`) → `evaluate_reco.py`(규칙 vs 학습 비교). 앱은 `assets/reranker.json` 이 없으면 규칙 기반으로 폴백한다.
- **데이터**: 실제 `user_missions` 로그가 없어 시뮬레이터(`sim.py`)로 학습했다(`reranker-lr-sim-3`). 시뮬레이터의 참 선호는 규칙 고정 가중치와 다르게 설정했다(인기도·거리를 규칙은 크게 잡지만 실제로는 거의 무의미, 난이도 적합도와 시간대 적합도는 규칙 가정보다 훨씬 중요). 사용자마다 앱을 여는 시각을 아침·점심·저녁에 몰리게 두고, 완료율은 현실적으로 낮게(31%) 두어 "상위 3건" 정렬이 실제로 변별되도록 했다. 로그가 쌓이면 `--from-firestore` 로 교체한다(완료 시각에서 KST 기준 시간대 신호를 복원).
- **평가**: 사용자 800·미션 300, 사용자 단위 7:3 분리. `evaluate_reco.py` 를 인자 없이 실행하면 재현된다(blend λ=0.6).

| 지표 (test, 사용자 분리) | 규칙 | 학습 | blend λ=0.6 |
| --- | ---: | ---: | ---: |
| ROC-AUC (완료 예측) | 0.949 | **0.960** | — |
| precision@3 | 0.914 | **0.951** | 0.947 |
| NDCG@5 | 0.914 | **0.953** | 0.947 |
| NDCG@10 | 0.908 | **0.947** | 0.940 |
| MRR | 0.967 | **0.987** | 0.983 |
| MAP | 0.873 | **0.904** | 0.899 |

hit@3 는 세 방식 모두 0.99+ 로 포화하므로 precision@3·NDCG·MRR 로 본다. 학습 모델은 규칙 대비
NDCG@5 +0.039, MAP +0.030; 앱이 실제로 쓰는 blend(콜드스타트 안전을 위해 규칙을 40% 섞음)도
NDCG@5 +0.033 으로 이득의 대부분을 가져온다.

학습 가중치가 규칙의 손튜닝 오류를 교정한다: `proximity` 1.5→0.21, `popularity` 1.0→−0.06,
`difficulty_fit` 1.0→3.16, `implicit_affinity` 2.0→3.95, `time_of_day_fit` 1.0→2.46
(규칙이 시간대·난이도를 과소평가한다).

---

## 9. 개발 환경 및 협업 방식

- 맥(Android SDK 미설치)에서는 Kotlin 코드 편집·리팩터링과 Python ML 파이프라인(데이터 수집·전처리)을, 윈도우 노트북에서 Android 빌드·계측 테스트를, Colab GPU 에서 모델 학습을 수행한다. GitHub 로 동기화.
- ML 산출물(가상환경·체크포인트·raw 데이터·분할 데이터)은 `.gitignore` 로 제외하고, 학습 데이터셋은 HF Hub 비공개 저장소에, 최종 배포 모델(`photo_verifier.onnx` 등 assets)만 리포에 커밋한다.
- 주요 커밋:
  - `7c4545d` 사진 인증 판정 로직 + `ml/` 학습 파이프라인
  - `599c31e` 사진 인증 판정을 미션 완료 흐름에 연결
  - `fc53e3a` 관리자 사진 검수 큐 + 데이터셋 구축 스크립트
  - `3cb2647` 보고서 초안(전체 프로젝트)
  - `823eec3` Firestore 보안 규칙 + 업로드 전 판정 게이트(`PhotoGate`) 분리
  - `f3dcaa6` Supabase 업로드 실패 수정(스토리지 키 정규화, `x-upsert`/RLS)
  - `e46e7cf` 온디바이스 추론기 `OnnxPhotoVerifier` 연결(`onnxruntime-android`)
  - `afaeb33` 마이페이지 포인트 적립 내역 화면
  - `f0e001e` `ml/` 파이프라인 스모크 테스트 + 최신 라이브러리 대응(transformers 5.x / optimum 2.x)
  - `1e209b2` 마이페이지 완료한 미션 목록 화면
  - `c0d1029` 데이터셋 구축 스크립트 실전화 + 실제 데이터셋 생성(5,300장, HF Hub 업로드)
  - `2db4900` 사진 인증 모델 학습 결과 반영(fullft-1, macro-F1 0.82)
  - 학습된 추천 re-ranker (`MissionFeatures`/`LearnedReranker` + `ml/reco/`)
  - `625185c` 마이페이지 한/영 언어 전환 전 화면 적용
  - `4e83053` 일본어 UI 언어 지원 추가
  - `6d3f8d1` 언어 설정 메뉴 라벨에 言語 추가
  - `f343c19` 추천 사유 메뉴 라벨 언어(영어, 일본어) 변경
  - `dbe0d06` 관리자 검수 대상 사진이 승인 전에 완료·포인트 지급되던 문제 수정
  - `85e0680` 검수 대기 시 잘못된 "사진 인증 완료" 토스트 문구 표시 수정
  - `be56bd1` 사진 인증 결과를 전체 화면으로 표시
  - `c6f77da` 검수 대기 재인증 시 `PERMISSION_DENIED` 로 저장 실패하던 `firestore.rules` 버그 수정
  - `7c84b9a` 미션 성공 성취 연출(포인트·컨페티·햅틱) 추가
  - `d524c43` / `6b1f121` 성취 연출 문자열 리소스 누락 수정, 연출 길이 1.1초→2.2초로 조정
  - `4efb90a` 여행 레벨·배지 3종·성취 연출 확장(레벨업·배지 알림)·다음 미션 추천 카드 추가(4.4·4.5.1·6.8.4절) — `TravelLevelPolicyTest`/`BadgeUnlockEvaluatorTest` 포함
  - `e7be908` 배지/레벨 카운터 추가로 깨졌던 미션 완료 저장 트랜잭션 순서 버그 수정(Firestore 는 모든 읽기가 모든 쓰기보다 먼저여야 함)
  - `4406c4d` 홈 화면 "이번 주 진행률"이 위치 인증만 해도 올라가던 버그 수정, `domain/WeekBoundary` 로 "이번 주" 계산 통합
  - `e7731a1` 미션 지도 핀에 진행 상태별 색상 + 사진 인증 완료 미션은 인증 사진을 정보창에 표시(4.2절)
  - `5b30e1a` 홈 추천 카드·미션 상세 화면에 실제 거리 표시, 미션 상세 화면 "길찾기" 버튼 추가(4.2·4.5절)
  - `f005275` 사용자 미션 제안을 위한 데이터 모델 기반(`MissionReviewStatus`/`MissionEngagement`) 추가(4.8절)
  - `b08d60e` 미션 제안·검수 화면(`MissionProposalScreen`/`AdminMissionReviewScreen`)과 찜한 미션 화면 추가, 보안 규칙 반영
  - `ddf8e27` 미션 제안·검수·좋아요·찜 화면을 내비게이션에 연결, 목록/추천에서 미승인 미션 제외, 좋아요·찜 버튼 추가
  - `cf05d08` 위 화면들 한국어/영어/일본어 문구 추가
  - `05c8469` 미션 제안·좋아요·찜 Firestore 보안 규칙 테스트 18건 추가(총 38건, 4.8절)

---

## 10. 향후 계획

- **참조 이미지 임베딩 유사도** — 설계·구현·배포 완료(6.7절): CLIP 인코더 HF Hub 배포, 미션 16/16 임베딩 백필, 판정 결합, 실기기 예비 관측(6.7.7), 다중 참조 이미지(최대 유사도) 구현·8개 미션 백필·held-out 검증(6.7.8), 실사용 라벨 첫 확보(6.7.9). 남은 것은 실사용자가 늘어 `rescue`/`suspect` 를 30건 이상 실측으로 확정하는 것(`ml/calibrate_similarity.py`). MobileCLIP 경량화는 후속.
- 무효 클래스 화면 재촬영 augmentation 반영 재학습 완료(6.7.10, invalid recall 0.932). 무관한 실내·업무 환경 표본은 `collected_invalid/` 소량뿐이라 계속 보강 여지 있음
- 크라우드소싱 사진으로 체험 클래스 보강 후 재학습 — 도메인 격차로 가장 약한 클래스(f1 0.63). 수집 스크립트(`ml/data/import_collected.py`) 준비 완료, 실사진 수집이 남음
- 1단계 위치 인증 정밀화: 반경 200 m 축소 및 `location.accuracy` 반영 (사진 모델 변경 없이 장소 특이성을 높이는 저비용 개선)
- 촬영 시각·EXIF·위치 메타데이터 교차 검증, GPS 스푸핑/순간이동 탐지
- 추천 re-ranker 를 실제 `user_missions` 로그로 재학습(현재는 시뮬레이터 학습본), 온라인 A/B 또는 컨텍스트 밴딧으로 확장
- 서버 사이드 포인트 검증, Firestore 보안 규칙 정비, Compose UI 테스트·Repository 계약 테스트 — 여행 레벨·배지 카운터(4.4.1·4.4.2)도 포인트와 같은 신뢰 경계(클라이언트가 직접 쓰는 구조)에 있어 같은 서버 검증 작업 범위에 포함
- 다음 미션 추천 카드(4.5.1)에 사용자 실시간 위치 기반 거리 표시 추가 — 이번 구현은 위치 재획득 없이 "계산 불가 시 생략" 으로 스코프를 좁혔음
- 위 레벨/배지/추천 카드 기능의 실기기 검증(레벨업 연출, 배지 3종 각각의 최초 획득, 프로필 표시, 화면 회전 시 상태 유지)
- 사용자 미션 제안·검수·좋아요/찜(4.8절)의 실기기 검증 — 지도 탭 위치 선택, 갤러리 이미지 업로드, 검수 승인/수정요청/반려 전체 흐름, 재제출까지 실제 기기로 아직 못 봄
- [x] 좋아요 수(`likeCount`)를 `MissionScorer`의 인기도 신호에 반영(완료 횟수 기반 신호와 더 강한 쪽을 채택) — 학습된 재랭커(`LearnedReranker`)가 쓰는 `MissionFeatures` 신호는 그대로 두어 기존 모델 보정치를 건드리지 않았다(4.5·4.8절, `MissionScorerTest` 3건 추가)
- [ ] 찜 수(`bookmarkCount`)를 추천에 반영 — "나중에 하고 싶다"는 방향이 다른 신호라 이번엔 보류. 실사용 로그가 쌓이면 재랭커 재학습 시 별도 신호로 검토
- 미션 제안 검수 대기열에 알림(푸시/뱃지)을 붙여 관리자가 신규 제안을 놓치지 않게 하는 것, 대량 제안 시 스팸 방지(예: 사용자당 동시 pending 제안 수 제한)

---

## 11. 참고 문헌

> 서지 형식은 학과 양식에 맞춰 최종 정리한다.

1. S. J. Pan and Q. Yang, "A Survey on Transfer Learning," *IEEE Transactions on Knowledge and Data Engineering*, vol. 22, no. 10, 2010.
2. S. Mehta and M. Rastegari, "MobileViT: Light-weight, General-purpose, and Mobile-friendly Vision Transformer," *ICLR*, 2022.
3. A. Radford et al., "Learning Transferable Visual Models From Natural Language Supervision," *ICML*, 2021.
4. E. J. Hu et al., "LoRA: Low-Rank Adaptation of Large Language Models," *ICLR*, 2022.
5. B. Zhou et al., "Places: A 10 Million Image Database for Scene Recognition," *IEEE Transactions on Pattern Analysis and Machine Intelligence*, vol. 40, no. 6, 2018. (본 프로젝트는 validation 셋 미러 `dpdl-benchmark/Places365-Validation` 사용, 투어·체험·쇼핑 학습 데이터)
6. L. Bossard, M. Guillaumin, and L. Van Gool, "Food-101 – Mining Discriminative Components with Random Forests," *ECCV*, 2014. (`ethz/food101` validation 스플릿, 맛집 학습 데이터)
7. ONNX Runtime, *https://onnxruntime.ai* (온디바이스 추론 런타임).
8. Hugging Face Optimum, *https://huggingface.co/docs/optimum* (ONNX 변환·양자화).
9. L. Li, W. Chu, J. Langford, and R. E. Schapire, "A Contextual-Bandit Approach to Personalized News Article Recommendation," *WWW*, 2010. (개인화 추천의 문맥 기반 온라인 학습)
10. F. Ricci, L. Rokach, and B. Shapira, *Recommender Systems Handbook*, Springer, 2015. (하이브리드 추천, 오프라인 평가 지표)
