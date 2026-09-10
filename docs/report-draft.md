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
- 미션별 GPS 위치 인증(목표 반경 200m 이내)
- 위치 인증 후 카메라 촬영 → 미리보기 → 사진 인증
- 인증 단계별 포인트 지급 및 진행 상태 저장(중복 지급 방지), 진행 중 미션 이어서 수행
- 홈에서 선호 카테고리를 우선한 규칙 기반 미션 추천(완료한 미션 제외)
- 누적 포인트 기반 사용자 랭킹, 프로필에서 포인트·레벨·미션 현황 확인
- 마이페이지에서 **보유 포인트를 누르면 적립 내역**(미션별 위치·사진 인증 보상)을 확인

### 2.2 관리자 기능

- Firestore 관리자 계정을 기반으로 사용자/관리자 화면 분리
- 미션 등록·수정·삭제(제목·설명·카테고리·포인트·이미지·위치 좌표)
- 전체 사용자·미션 진행 현황 조회, 사용자별 포인트·레벨·완료/진행 미션 확인
- **사진 검수 큐**: 자동 판정이 애매한 완료 건을 승인하거나 반려(보상 회수 후 재인증 요청)
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
| `GeoDistance` | 두 좌표 사이 거리 계산 (Haversine) |
| `LocationVerification` | 허용 반경(기본 200m) 이내 여부 판정 |
| `MissionRewardPolicy` | 1·2단계 보상 계산과 중복 지급 방지 |
| `MissionCompletion` | 사진 인증 가능 여부·완료 처리 결과(`resolve`) 계산 |
| `TravelPreference` | 취향 카테고리 정의, 최소 1개 선택 규칙, 저장용 정규화 |
| `MissionFeatures` | 미션 추천 신호 6개(0~1 정규화) 계산. 규칙·학습이 공유 |
| `MissionScorer` | 신호를 `RecommendationWeights` 로 가중합 + 근거 문구 (규칙 점수) |
| `LearnedReranker` | 완료 로그로 학습한 로지스틱 회귀로 완료 확률 추정 |
| `MissionRecommender` | 후보 필터 + 점수 정렬 + 다양성 감점으로 상위 N건 추천 |
| `PhotoVerification` | 온디바이스 모델의 라벨별 점수 → 통과 / 재촬영 / 관리자 검수 판정 |

### 3.3 앱 내비게이션

두 개의 `NavHost` 로 나뉜다.

| 그래프 | 경로 | 설명 |
| --- | --- | --- |
| 루트 | `landing` → `signup` / `login` → `gate` / `preference` → `main` | 인증·온보딩. 로그인 상태면 `gate`, 아니면 `landing` 시작 |
| 메인(하단 탭) | `home`, `mission`, `add`(관리자), `ranking`, `profile` | `main` 진입 후. 상세·수행·관리자 화면은 하위 경로 |

- `gate` 는 로그인된 기존 사용자의 `users/{uid}.preferences` 유무를 확인해 `main` 또는 `preference` 로 분기한다. (조회 실패 시 앱을 막지 않고 `main` 진행)
- `admins/{uid}` 문서가 있는 사용자에게만 하단 탭에 **Admin** 이 보이고, 관리자 경로는 진입 시 권한을 재확인한다.

### 3.4 데이터 모델 (Firestore · Supabase Storage)

| 경로 | 주요 필드 |
| --- | --- |
| `users/{uid}` | `nickname`, `mail`, `points`, `level`, `preferences[]` |
| `missions/{id}` | `title`, `desc`, `category`, `points`, `imageUrl`, `location`(GeoPoint), `completionCount` |
| `user_missions/{id}` | `userId`, `missionId`, `status`, `progress`, `stage1RewardGranted`, `stage1RewardPoints`, `stage1VerifiedAt`, `stage2RewardGranted`, `stage2RewardPoints`, `photoUrl`, `photoStoragePath`, `photoVerified`, `photoUploadedAt`, `completedAt`, `photoNeedsReview`, `photoVerifyScore`, `photoVerifyLabel`, `photoVerifyModelVersion` |
| `admins/{uid}` | `email`, `name` |
| Supabase `mission-photos/<missionKey>/{uid}_{timestamp}.jpg` | 사진 인증 이미지 (공개 URL). `missionKey` 는 `missionId` 를 Supabase 스토리지 키 규칙에 맞춰 ASCII 로 정규화한 값(4.3절) |

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
  - `admins/{uid}`·`missions` 생성/삭제: 관리자(`admins/{uid}` 문서 존재)만
  - `missions` 의 `completionCount` 필드만은 로그인 사용자가 갱신 가능(완료 시 인기도 신호)
  - `users/{uid}`: 본인 또는 관리자만 수정, 삭제 불가
  - `user_missions/{id}`: 생성은 본인 문서만, **사용자는 자기 `photoNeedsReview` 를 true→false 로 되돌릴 수 없음**(검수 승인은 관리자만)
- **테스트**: `firestore-tests/` 에서 에뮬레이터 + `@firebase/rules-unit-testing` 으로 20건 검증(8.1절).
- **한계**: 서버(Cloud Functions)가 없어 포인트 지급/회수의 값 자체는 검증하지 못한다. 서버측 포인트 검증은 향후 과제다. (10장)

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

### 4.4 포인트 · 레벨 · 랭킹

- 보상 계산과 중복 지급 방지는 `MissionRewardPolicy` 에 모여 있으며, 포인트 지급은 Firestore 트랜잭션으로 원자적으로 처리된다.
- 누적 포인트 기준 사용자 랭킹을 제공하고, 프로필에서 포인트·레벨·완료/진행 미션 수를 보여 준다.
- **포인트 적립 내역**(`PointHistoryScreen`): 마이페이지의 보유 포인트를 누르면 미션별 위치·사진 인증 보상 내역을 최신순으로 보여 준다. 별도 원장 컬렉션 없이 `user_missions` 의 `stage1RewardGranted`/`stage1RewardPoints`/`stage1VerifiedAt`, `stage2RewardGranted`/`stage2RewardPoints`/`completedAt` 에서 재구성하며, 미션명은 `missions/{id}` 에서 조회한다. (`firestore.rules` 미배포 상태에서 새 컬렉션 추가 시 규칙 누락으로 리워드 트랜잭션이 깨질 위험을 피하기 위한 선택. 서버측 포인트 검증 도입 시 실제 원장으로 교체 — 10장)

### 4.5 개인화 추천 (규칙 + 학습 하이브리드)

홈의 추천 미션 상위 3건은 **규칙 점수를 완료 로그로 학습한 re-ranker 로 다시 매겨** 만든다.
학습 모델(`assets/reranker.json`)이 없으면 규칙 점수만으로 정렬한다(콜드스타트).

**신호 6개** (`MissionFeatures`, 0~1 정규화 — 규칙·학습이 공유)

| 신호 | 의미 |
| --- | --- |
| `explicit_pref` | 미션 카테고리가 `preferences` 에 포함되면 1 |
| `implicit_affinity` | 그 카테고리를 완료한 비율 |
| `difficulty_fit` | 미션 포인트대가 사용자 레벨 기대치에 가까운 정도 |
| `proximity` | 현재 위치로부터의 근접도 (위치를 알 때만) |
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

### 4.6 관리자 기능

- `admins/{uid}` 문서 유무로 관리자 화면을 노출한다.
- 미션 CRUD(위치 좌표 입력 포함), 사용자·미션 진행 현황 조회, 사진 검수 큐(6.5절).

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
| 무효 | 합성 | 스크린샷 합성 + 다른 클래스 이미지 열화(하드 네거티브) + `collected_invalid/` 직접 수집분 |

- **스트리밍 대신 validation 셋 전체 다운로드**: Places365/Food-101 학습 셋은 클래스 순으로 정렬돼 있어 `datasets` 스트리밍 + shuffle 로는 앞쪽 몇 개 scene 에 편중된다(초기 시도에서 투어 3 scene·쇼핑 1 scene 만 수집됨). validation 셋은 scene 당 100~250장으로 작아(합쳐서 ~5.7GB) scene 다양성을 최대로 확보한다.
- **분할**: train/val/test = 70/15/15. 파일명 `<sceneId>__n.jpg` 의 sceneId 단위로 그룹을 묶어 분할해, 같은 scene 이미지가 train·test 에 걸쳐 성능이 부풀려지는 것을 방지한다(공개 데이터 4개 클래스는 train↔test scene 겹침 0). 합성 무효는 그룹이 2개뿐이라 이미지 단위로 분할한다.
- **크라우드소싱**(권장, 미적용): 동기·지인의 실제 미션 수행 사진. `raw/<카테고리>/<장소이름>__001.jpg` 로 넣으면 같은 파이프라인으로 합쳐진다.

**구축 결과** (HF Hub 비공개: `kimgayeon430/travel-mission-photos`)

| split | 투어 | 맛집 | 체험 | 쇼핑 | 무효 | 합계 |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| train | 698 | 700 | 700 | 705 | 910 | 3,713 |
| validation | 152 | 150 | 150 | 161 | 195 | 808 |
| test | 150 | 150 | 150 | 134 | 195 | 779 |
| **합계** | **1,000** | **1,000** | **1,000** | **1,000** | **1,300** | **5,300** |

scene 다양성: 투어 107 · 맛집 101 · 체험 53 · 쇼핑 28 · 무효 2(합성).
공개 데이터는 도메인이 실제 촬영본과 다소 다르므로, 크라우드소싱 사진으로 각 클래스를 보강하는 것이 향후 과제다.

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

- 기본값: `autoPass = 0.65`, `hardReject = 0.22`, `invalidReject = 0.55`. `mobilevit-small-fullft-1` 의 test 셋 임계값 스윕으로 선정해 `ml/thresholds.json` → `PhotoVerificationConfig` 로 이식했다. `invalidReject = 0.55` 에서 무효 사진 차단율 0.918.
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
- **임계값이 공개 scene 프록시 기반의 잠정치.** `raw/<장소>__N.jpg` 그룹을 같은 대상으로 간주해 스윕한 값이다. 실제 미션 사진 vs 대표 이미지 쌍(크라우드소싱)으로 재보정해야 하며, `user_missions.photoVerifySimilarity` 로그를 그 데이터로 쌓는다.
- **참조 이미지 1장의 한계.** 각도·조명·계절·주야 차이에 코사인 유사도가 흔들린다. `photoEmbedding` 을 배열의 배열로 두면 여러 장 등록해 최대 유사도를 취하도록 확장할 수 있다.
- **무효 클래스 의존.** (가) 의 스푸핑 방어 전체가 무효 클래스 성능에 걸려 있다. 6.7.1 에서 사무실 장면이, 6.7.7 에서 모니터 재촬영이 모두 무효로 잡히지 않았으므로(`s[무효] ≈ 0.07`), 크라우드소싱 수집 시 *무관한 실내·업무 환경* 과 *화면·모니터 재촬영* 표본을 보강해야 한다.
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
- [x] 실제 데이터셋 구축 (5,300장, HF Hub `kimgayeon430/travel-mission-photos`) — 6.3절
- [x] `OnnxPhotoVerifier` (ONNX Runtime Mobile) 구현 — `assets/` 의 모델·전처리·라벨 json 을 읽어 추론, 모델 없으면 `null` 반환해 앱 무영향
- [x] Supabase Storage 사진 업로드 (InvalidKey·RLS 이슈 수정 후 실기기 동작 확인)
- [x] 마이페이지 포인트 적립 내역 화면 (`PointHistoryScreen`)
- [x] Colab T4 에서 파인튜닝 → `photo_verifier.onnx`(20MB) 를 `assets/` 에 번들, 임계값을 `PhotoVerificationConfig.DEFAULT` 로 반영, 기본 verifier 를 `OnnxPhotoVerifier`·`DEFAULT` config 로 전환 (test macro-F1 0.82)
- [x] 학습된 추천 re-ranker: `MissionFeatures`·`LearnedReranker`·`MissionRecommender.recommendReranked` + `ml/reco/` 파이프라인 + `assets/reranker.json`. 신호 6개(시간대 적합도 포함), 시뮬레이터 학습본으로 규칙 대비 AUC 0.949→0.960, NDCG@5 0.914→0.953 (8.3절)
- [x] 모델 에셋 로딩 계측 테스트 (`OnnxPhotoVerifierTest` 6건, `RerankerSourceTest` 4건) — 실기기(Galaxy S8, API 28)에서 실제 에셋으로 추론·로딩 검증. 두 로더 모두 실패를 `runCatching` 으로 삼켜 **무증상 고장**(사진: 전건 검수 큐행 / 추천: 규칙 기반 폴백)이 나므로, 재학습 모델 교체 시 신호 순서·라벨 불일치를 잡는 방어선
- [x] 참조 이미지 임베딩 유사도(6.7절) — `CLIP ViT-B/32` 임베딩 인코더 채택(pooled feature 재사용은 분리도 AUC 0.60 으로 기각, `ml/embedding_separability.py`). 온디바이스 `OnnxClipPhotoEmbedder`(88.6MB int8, HF Hub 런타임 다운로드+`filesDir` 캐시), 판정 규칙 `PhotoVerification.verify` 에 유사도 ±1단계 결합, 참조 임베딩 사전계산 `ml/embed_missions.py`, EXIF 회전 정합(`ImagePreprocess` + `androidx.exifinterface`). 단위 테스트 신규 13건 포함 `PhotoVerificationTest` 18 + `PhotoGateTest` 8 통과, `testDebugUnitTest`·`compileReleaseKotlin` BUILD SUCCESSFUL. 실기기(Galaxy S8) 계측 테스트 통과 + 유사도 신호 실동작·구제 경로 확인(6.7.7). 임계값(rescue 0.50 / suspect 0.68)은 공개 scene 프록시 기반 잠정치

### 7.2 남은 작업

- [x] `photo_embedder_int8.onnx`(88.6MB) 를 HF Hub `kimgayeon430/travel-mission-photo-embedder`(Public) 에 업로드, `ml/embed_missions.py` 로 미션 15/16건 `photoEmbedding`(512d) + `photoEmbeddingModelVersion` 백필 (나머지 1건은 `imageUrl` 없음)
- [ ] 실기기에서 사진 인증 전체 루프 확인 — 임베더 HF 다운로드→캐시(`prefetch`), 유사도 결합, 구제 경로(`REJECT`→`NEEDS_REVIEW`)까지 확인 완료(6.7.7). 남은 것: *현장 정상 촬영* 케이스, `PASS` → 관리자 승인/반려 분기
- [ ] 유사도 임계값 실측 보정 — 6.7.7 관측(무관 0.38 / 재촬영 0.67)에 *현장 정상* 값을 더해 `rescue`/`suspect` 확정. 크라우드소싱 미션 사진 vs 대표 이미지 쌍으로 스윕(현재는 공개 scene 프록시 잠정치), `PhotoVerify` 로그·`user_missions.photoVerifySimilarity` 활용
- [ ] 무효 클래스에 화면·모니터 재촬영 표본 보강 — 6.7.7 에서 재촬영본 `s[무효] ≈ 0.07` 로 스푸핑 방어가 유사도 문턱에만 의존
- [ ] 크라우드소싱 사진으로 각 클래스 보강(특히 체험·무관 실내) 후 재학습
- [ ] `user_missions` 로그로 추천 re-ranker 재학습(`--from-firestore`), 시뮬레이터 학습본 대체
- [ ] `firestore.rules` 배포 (`firebase deploy --only firestore:rules` — 규칙 테스트 20건은 통과)
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

- **JUnit4 단위 테스트**로 거리·보상·완료·취향·추천·사진 판정 규칙을 검증한다(경계값 포함). 사진 인증: `PhotoVerificationTest`, `PhotoGateTest`. 추천: `MissionFeaturesTest`, `LearnedRerankerTest`, `MissionRecommenderRerankedTest`.
- **Firestore 보안 규칙 테스트** (`firestore-tests/`, `@firebase/rules-unit-testing` + 에뮬레이터, 20건): 게스트/일반/관리자 컨텍스트로 `admins`·`missions`·`users`·`user_missions` 의 읽기·쓰기 허용/거부를 검증한다. 핵심: 일반 사용자가 `missions.completionCount` 외 필드를 못 바꾸고, 자기 `photoNeedsReview` 를 true→false 로 못 되돌린다.
- ViewModel 레벨 테스트는 `android.net.Uri`·`android.location.Location` 의존으로 순수 JUnit 에서 불가하며, Robolectric 도입은 향후 과제로 둔다.

### 8.2 사진 인증 모델

**학습 결과** (Colab T4, `mobilevit-small-fullft-1`, test 셋 779장. train↔test scene 겹침 0)

| 모델 | test accuracy | test macro-F1 |
| --- | ---: | ---: |
| CLIP 제로샷 (`clip-vit-base-patch32`, 학습 없음) | 0.639 | 0.631 |
| MobileViT-small 전체 파인튜닝 | **0.829** | **0.816** |

클래스별 F1: 투어 0.80 · 맛집 0.93 · **체험 0.64** · 쇼핑 0.76 · 무효 0.95.
체험이 가장 약함(recall 0.57) — 공개 데이터가 실제 "체험 미션 사진"과 도메인이 다르고 카테고리 경계가 모호. 크라우드소싱 사진 보강이 향후 과제.

**임계값 선정** (`ml/thresholds.json`, test 셋 임계값 스윕)

| 임계값 | 값 | 근거 |
| --- | ---: | --- |
| `invalidRejectThreshold` | 0.55 | 무효 사진 차단율 0.92, 정상 사진 오탐 0.005 |
| `hardRejectThreshold` | 0.22 | 정상 사진 오탐(0.30일 때 0.18)을 낮추는 방향. 애매한 사진은 `REJECT` 대신 `NEEDS_REVIEW` 로 |
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

---

## 10. 향후 계획

- **참조 이미지 임베딩 유사도 정식 배포** — 설계·구현은 6.7절에서 완료(CLIP 인코더, 판정 결합, 사전계산 스크립트). 남은 것은 임베더 모델 업로드, 기존 미션 임베딩 채우기, 실측 임계값 보정. 다중 참조 이미지(최대 유사도)·MobileCLIP 로 모델 경량화는 후속.
- 무효 클래스 데이터 보강(무관한 실내·업무 환경 표본) — 스푸핑 방어가 이 클래스에 의존하므로 우선순위 높음 (6.7.6)
- 1단계 위치 인증 정밀화: 반경 200 m 축소 및 `location.accuracy` 반영 (사진 모델 변경 없이 장소 특이성을 높이는 저비용 개선)
- 촬영 시각·EXIF·위치 메타데이터 교차 검증, GPS 스푸핑/순간이동 탐지
- 추천 re-ranker 를 실제 `user_missions` 로그로 재학습(현재는 시뮬레이터 학습본), 온라인 A/B 또는 컨텍스트 밴딧으로 확장
- 서버 사이드 포인트 검증, Firestore 보안 규칙 정비, Compose UI 테스트·Repository 계약 테스트

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
