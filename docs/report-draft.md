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
| `MissionScorer` | 명시적·암묵적 취향, 난이도 적합도, 거리 근접도, 인기도로 미션 기본 점수 계산(근거 포함) |
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

### 4.5 개인화 추천 (규칙 기반)

추천은 별도 AI 모델 없이 현재 데이터만으로 설명 가능한 점수 규칙으로 홈의 추천 미션 상위 3건을
계산한다. (`MissionScorer` + `MissionRecommender.recommendScored`)

1. id 가 없거나 이미 완료한 미션은 후보에서 제외한다.
2. 후보마다 기본 점수를 매긴다.
   - **명시적 취향**: 미션 카테고리가 `preferences` 에 포함되면 가산
   - **암묵적 취향**: 그 카테고리 미션을 완료한 비율만큼 가산
   - **난이도 적합도**: 미션 포인트대가 사용자 레벨 기대치에 가까울수록 가산
   - **거리 근접도**: 미션 목표 지점이 현재 위치에 가까울수록 가산 (위치 권한이 있을 때만)
   - **인기도**: 다른 사용자의 완료 횟수(`completionCount`)가 많을수록 가산
3. "기본 점수 − 다양성 감점 × 이미 뽑힌 같은 카테고리 수" 가 가장 높은 미션을 하나씩 3건 선택한다.
4. 각 추천에 점수 근거(예: `맛집 취향`, `자주 하는 유형`, `가까운 미션`, `인기 미션`)를 칩으로 표시한다.
5. 진행 중인 미션이 있으면 추천 대신 해당 미션을 노출한다.
6. 추천 결과가 없거나 조회에 실패하면 안내 카드를 표시한다.

가중치는 `RecommendationWeights` 에 모여 있어 오프라인 평가 후 조정할 수 있다.
이 규칙 기반 추천은 향후 완료 로그 기반 학습 모델로 확장할 여지를 둔다. (8장)

### 4.6 관리자 기능

- `admins/{uid}` 문서 유무로 관리자 화면을 노출한다.
- 미션 CRUD(위치 좌표 입력 포함), 사용자·미션 진행 현황 조회, 사진 검수 큐(6.5절).

---

## 5. (기존 프로젝트 대비) 인공지능 관점의 한계와 문제 정의

### 5.1 한계

1. **사진 인증에 검증이 없다.** 2단계 인증은 촬영본을 그대로 업로드하고 `photoVerified` 를 참으로 고정했다. 미션과 무관한 사진(셀카, 스크린샷, 실내 사진)으로도 포인트를 받을 수 있어 부정 사용에 취약하다.
2. **학습된 모델이 없다.** 추천은 하드코딩 가중치의 규칙 기반이며, 오프라인 평가·학습 절차가 없다.

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

- 기본값: `autoPass = 0.70`, `hardReject = 0.30`, `invalidReject = 0.60` (오프라인 PR 커브로 재조정 예정).
- 모델을 불러오지 못하면 기본적으로 `NEEDS_REVIEW`. (현재 모델 미배포 상태에서는 `passWhenModelUnavailable = true` 로 통과)
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

---

## 7. 구현 현황

### 7.1 완료

- [x] 앱 전체 기능: 인증·온보딩, 미션 목록·지도, 2단계 인증 흐름, 포인트·랭킹, 규칙 기반 추천, 관리자 기능
- [x] 순수 도메인 로직 분리 + 단위 테스트 (`GeoDistance`, `LocationVerification`, `MissionRewardPolicy`, `MissionCompletion`, `TravelPreference`, `MissionScorer`, `MissionRecommender`, `PhotoVerification`)
- [x] 사진 인증 판정 로직 `PhotoVerification` + `PhotoVerificationConfig` + 테스트 7건
- [x] 추론 인터페이스 `PhotoVerifier` (+ `FakePhotoVerifier`), 업로드 전 결정 `PhotoGate` + 테스트 7건
- [x] 미션 완료 흐름 연결 (업로드 전 판정, 결과 기록, `REJECT`/`NEEDS_REVIEW` UX 분기)
- [x] 관리자 사진 검수 큐 화면
- [x] 데이터셋 구축 스크립트 `ml/data/`, 학습 노트북, ONNX export 스크립트 — 더미 데이터로 파이프라인 전 구간(데이터 로드 → CLIP 제로샷 → 학습 → 평가 → 임계값 → ONNX export) 스모크 테스트 완료
- [x] 실제 데이터셋 구축 (5,300장, HF Hub `kimgayeon430/travel-mission-photos`) — 6.3절
- [x] `OnnxPhotoVerifier` (ONNX Runtime Mobile) 구현 — `assets/` 의 모델·전처리·라벨 json 을 읽어 추론, 모델 없으면 `null` 반환해 앱 무영향
- [x] Firestore 보안 규칙 `firestore.rules`
- [x] Supabase Storage 사진 업로드 (InvalidKey·RLS 이슈 수정 후 실기기 동작 확인)
- [x] 마이페이지 포인트 적립 내역 화면 (`PointHistoryScreen`)
- [x] Colab T4 에서 파인튜닝 → `photo_verifier.onnx`(20MB) 를 `assets/` 에 번들, 임계값을 `PhotoVerificationConfig.DEFAULT` 로 반영, 기본 verifier 를 `OnnxPhotoVerifier`·`DEFAULT` config 로 전환 (test macro-F1 0.82)

### 7.2 남은 작업

- [ ] 실기기에서 사진 인증 전체 루프 확인 (PASS/REJECT/NEEDS_REVIEW → 관리자 승인/반려), 추론 지연 측정
- [ ] 크라우드소싱 사진으로 각 클래스 보강(특히 체험) 후 재학습
- [ ] `firestore.rules` 배포 및 규칙 시뮬레이터 검증
- [ ] Robolectric 기반 ViewModel/Compose UI 테스트 `[선택]`
- [ ] 서버측 포인트 검증(Cloud Functions) `[선택]`

### 7.3 구현 중 해결한 문제

| 문제 | 원인 | 해결 |
| --- | --- | --- |
| 사진 업로드가 항상 `400 InvalidKey` | 미션 문서 ID 가 한글 제목이라 Supabase 스토리지 키 규칙 위반 | 경로의 미션 폴더명을 `해시_ASCII정규화`(`missionKey`)로 변환 |
| 사진 업로드가 `403` RLS 거부 | `x-upsert: true` 가 `UPDATE` 정책을 요구하나 anon 은 `INSERT` 정책만 보유 | 항상 유일한 경로이므로 `x-upsert` 를 끄고 새로 `INSERT` |
| 저사양(RAM 8GB) 환경에서 Gradle sync 중 데몬 강제 종료 | 데몬 힙(2GB) + IDE 메모리 압박 | `gradle.properties` 데몬 힙 1.5GB, `org.gradle.workers.max=2` |
| `onnxruntime-android` 도입 후 APK +120MB | 모든 ABI 네이티브 라이브러리 포함 | `abiFilters` 로 `arm64-v8a`/`x86_64` 만 (+20MB 수준) |
| 학습 노트북이 `transformers` 5.x 에서 학습 실패(`KeyError: 'image'`) | `Trainer` 가 `with_transform` 데이터셋의 컬럼을 제거 | `TrainingArguments(remove_unused_columns=False)`, 전처리를 `AutoImageProcessor` 에 위임(export·앱 전처리와 자동 정합) |
| 공개 데이터 수집 시 투어 3 scene·쇼핑 1 scene 만 확보 | `datasets` 스트리밍이 클래스 정렬 상태라 shuffle 버퍼가 몇 개 클래스만 담음 | 스트리밍 대신 Places365/Food-101 **validation 셋을 통째로 받아** scene 별로 표본 (투어 107·체험 53·쇼핑 28 scene 확보) |
| 일부 scene(`market/indoor` 등)이 한 그룹으로 뭉침 | 파일명 생성 시 `scene.split('/')[-1]` 로 접미어만 사용 | 전체 scene 경로를 정규화(`canonical_scene`)해 그룹 키로 사용 |
| Colab 에서 `optimum` ONNX export 실패 | `optimum.exporters` 가 `diffusers` 를 import 하는데 Colab 의 `diffusers`/`huggingface_hub` 버전 불일치 | `torch.onnx.export`(레거시, `dynamo=False`, opset 18) 로 직접 변환. int8 양자화는 shape inference 오류로 생략하고 fp32(20MB) 채택 |
| `torch.onnx` 가 가중치를 `photo_verifier.onnx.data` 로 분리 저장 | 새 torch 의 external-data 기본 동작 | `onnx.save(..., save_as_external_data=False)` 로 단일 파일화 (앱은 `.onnx` bytes 만 로드) |

---

## 8. 평가 계획

### 8.1 도메인 규칙

- JUnit4 단위 테스트로 거리·보상·완료·취향·추천·사진 판정 규칙을 검증한다. (경계값 포함)
- 사진 인증 관련: `PhotoVerificationTest`(임계값별 PASS/REJECT/NEEDS_REVIEW, 경계값, 모델 부재), `PhotoGateTest`(정상/무효/애매 분기, 모델 부재·추론 예외 처리).
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

- 모델: fp32 ONNX 약 20MB (int8 양자화는 Colab 라이브러리 충돌로 미적용, fp32 채택).
- `onnxruntime-android` 도입 시 디버그 APK 약 +120MB(전 ABI) → `abiFilters`(arm64-v8a/x86_64) 적용 후 약 +20MB.
- 추론 지연(ms): `[실기기 측정 TODO]`
- 전처리·라벨·모델버전은 export 산출물(`photo_verifier_preprocessor.json` / `_labels.json` / `_version.txt`)에서 `OnnxPhotoVerifier` 가 읽어 자동 정합.

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

---

## 10. 향후 계획

- 미션별 레퍼런스 사진 few-shot 매칭(랜드마크 정합성 강화)
- 촬영 시각·EXIF·위치 메타데이터 교차 검증, GPS 스푸핑/순간이동 탐지
- 추천 고도화: 완료 로그 기반 학습된 re-ranking 또는 컨텍스트 밴딧, 오프라인 평가(hit@k, NDCG)
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
