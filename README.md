# Travel Mission

> 여행지에서 미션을 수행하고 GPS와 사진으로 인증하며 포인트와 순위를 쌓는 게이미피케이션 Android 애플리케이션

숙명여자대학교 인공지능공학부 졸업 프로젝트로 개발한 Android 앱입니다.  
사용자는 여행지 미션을 탐색하고 단계별 인증을 완료해 포인트를 획득할 수 있으며, 관리자는 앱 안에서 미션과 사용자 권한을 관리할 수 있습니다.

## 주요 기능

### 사용자

- Firebase Authentication 기반 회원가입, 로그인 및 로그아웃
- 로그인 없이 둘러볼 수 있는 게스트 진입
- 회원가입 직후 여행 취향(투어·맛집·체험·쇼핑) 복수 선택 및 저장
- 기존 사용자는 `preferences` 유무를 판정(gate)해 취향 선택 화면을 건너뛰거나 거침
- 전체 미션 목록 조회 및 상세 정보 확인
- 미션 목록 ↔ **네이버 지도** 전환: 좌표가 있는 미션을 지도 마커로 모아 보고, 마커를 눌러 상세로 이동
- 미션별 GPS 위치 인증 (목표 지점 반경 200m 이내)
- 위치 인증 후 카메라 촬영 → 미리보기 → Supabase Storage 업로드로 사진 인증
- 인증 단계별 포인트 지급 및 진행 상태 저장 (중복 지급 방지)
- 진행 중인 미션 확인 및 이어서 수행
- 홈에서 취향·완료 이력 기반 미션 추천 (규칙 점수 + 완료 로그 학습 re-ranker 하이브리드, 완료한 미션 제외)
- 누적 포인트 기반 사용자 랭킹
- 프로필에서 포인트, 레벨 및 미션 현황 확인

### 관리자

- Firestore의 관리자 계정을 기반으로 사용자와 관리자 화면 분리
- 미션 등록, 수정 및 삭제 (제목·설명·카테고리·포인트·이미지·위치 좌표)
- 전체 사용자와 미션 진행 현황 조회
- 사용자별 포인트, 레벨, 완료·진행 미션 확인
- 사진 검수 큐: 자동 판정이 애매한(`photoNeedsReview`) 완료 건을 승인하거나 반려(2단계 보상 회수 후 재인증 요청)
- 관리자 권한 부여 및 해제

## 기술 스택

| 구분 | 기술 |
| --- | --- |
| Language | Kotlin 2.2.10 |
| UI | Jetpack Compose (BOM 2026.02.01), Material 3, Compose Compiler 플러그인 |
| Navigation | Navigation Compose (루트 그래프 + 하단 탭 그래프 2단 구성) |
| Backend | Firebase Authentication(Email/Password), Cloud Firestore |
| 지도 | 네이버 지도 SDK `com.naver.maps:map-sdk` (미션 위치 마커·정보창) |
| 이미지 저장 | Supabase Storage (public 버킷 + anon 업로드 정책) |
| AI | ① 사진 인증: 온디바이스 이미지 분류 (`apple/mobilevit-small` 파인튜닝 → ONNX, `onnxruntime-android`) — `PhotoVerification` / `OnnxPhotoVerifier`  ② 추천: 완료 로그 학습 로지스틱 re-ranker (`LearnedReranker`, `ml/reco/`) |
| Image Loading | Coil |
| Build | Gradle 9.4.1 (Kotlin DSL), Version Catalog, AGP 9.2.0, `compileSdk 36` / `minSdk 26` / `targetSdk 36` |
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
   - 업로드 전에 온디바이스 모델(`PhotoVerifier`)로 사진을 분류하고 `PhotoVerification` 규칙으로 판정합니다. `REJECT` 면 업로드하지 않고 재촬영을 안내하며, `NEEDS_REVIEW` 면 완료는 진행하되 `photoNeedsReview` 플래그를 남깁니다. (자세한 내용은 "사진 인증 모델" 절)
   - 사진을 Supabase Storage 버킷 `mission-photos` 의 `{missionId}/{uid}_{timestamp}.jpg` 로 업로드하고 공개 URL 을 받습니다. (`SupabaseStorage`, 백그라운드 스레드)
   - 업로드가 성공한 뒤에만 트랜잭션으로 미션을 `Completed` 처리하고 2단계 보상을 지급하며, `photoUrl`(Supabase 공개 URL)·`photoStoragePath`·`photoVerified`·`photoNeedsReview`·`photoVerifyScore`·`photoVerifyLabel`·`photoVerifyModelVersion`·`photoUploadedAt` 을 저장합니다.
   - 2단계 보상 = `미션 포인트 - 1단계 보상`
   - 업로드나 저장이 실패하면 미션은 완료되지 않으며, 재시도해도 포인트는 한 번만 지급됩니다.
   - 사용자가 처음 완료할 때 같은 트랜잭션에서 `missions/{id}.completionCount` 를 1 올립니다. (추천 인기도 신호)

## 개인화 추천 (규칙 + 학습 하이브리드)

홈의 추천 미션 상위 3건은 **규칙 점수를 완료 로그로 학습한 re-ranker 로 다시 매겨** 만듭니다. 학습 모델(`assets/reranker.json`)이 없으면 규칙 점수만으로 정렬합니다(콜드스타트).

**신호 5개** (`MissionFeatures`, 0~1 정규화 — 규칙·학습이 공유)

- **명시적 취향**: 미션 카테고리가 `users/{uid}.preferences` 에 포함되면 1
- **암묵적 취향**: 그 카테고리 미션을 완료한 비율
- **난이도 적합도**: 미션 포인트대가 사용자 레벨 기대치에 가까운 정도
- **거리 근접도**: 현재 위치로부터의 근접도 (위치 권한이 허용된 경우에만)
- **인기도**: 다른 사용자의 완료 횟수(`missions/{id}.completionCount`) 기반

1. id가 없거나 완료한 미션은 후보에서 제외합니다.
2. 후보마다 **규칙 점수**(`MissionScorer`, `RecommendationWeights` 가중합 + 근거 칩)와 **학습된 완료 확률**(`LearnedReranker`, 로지스틱 회귀)을 구합니다.
3. `최종 = (1−λ)·규칙점수/최댓값 + λ·학습확률` (λ = `blend`, 기본 0.6).
4. "최종 − 다양성 감점 × 이미 뽑힌 같은 카테고리 수" 가 가장 높은 미션을 하나씩 3건 선택합니다.
5. 근거 칩(`맛집 취향`, `자주 하는 유형`, `가까운 미션` 등)은 규칙 점수 것을 그대로 표시합니다.
6. 진행 중인 미션이 있으면 추천 대신 노출합니다. 결과가 없으면 안내 카드.

학습·평가 파이프라인은 `ml/reco/` (`build_dataset.py` → `train_reranker.py` → `evaluate_reco.py`). 현재 모델은 실제 로그가 없어 시뮬레이터로 학습(`reranker-lr-sim-1`); `user_missions` 로그가 쌓이면 `--from-firestore` 로 재학습합니다.

신규 가입자는 회원가입 직후 취향 선택 화면으로 이동하고, 기존 사용자는 `preferences` 가 없을 때만 이 화면을 거칩니다.

## 사진 인증 모델 (온디바이스)

2단계 사진 인증에서 "아무 사진이나 통과"되는 문제를 없애기 위해, 촬영본을 **온디바이스 이미지 분류 모델**로 1차 판정합니다. 판정 규칙은 Firebase·모델에 의존하지 않는 순수 Kotlin `PhotoVerification` 으로 분리해 단위 테스트합니다.

### 접근

- **모델**: HuggingFace `apple/mobilevit-small` (~5M 파라미터) 를 미션 사진으로 **전체 파인튜닝**. 학습 없는 `CLIP` 제로샷을 비교 기준선으로 둡니다. test 정확도 0.83 / macro-F1 0.82 (CLIP 제로샷 0.63).
- **데이터**: `투어 / 맛집 / 체험 / 쇼핑` + `무효` 5클래스, 총 5,300장 (HF Hub `kimgayeon430/travel-mission-photos`). Places365 / Food-101 validation 셋에서 scene 별로 표본. 정의는 `ml/labels.json` 이 단일 소스.
- **배포**: `torch.onnx` 로 ONNX(fp32 20MB) 변환 후 `app/src/main/assets/photo_verifier.onnx` 로 번들, `onnxruntime-android` 로 추론. `OnnxPhotoVerifier` 가 `photo_verifier_preprocessor.json` 에서 전처리 상수를 읽어 학습·추론을 자동 정합.

### 판정 규칙 (`PhotoVerification`)

모델이 낸 라벨별 점수와 미션이 기대하는 카테고리를 비교해 세 갈래로 판정합니다. 임계값은 `PhotoVerificationConfig` 에 모여 있고 오프라인 평가(PR 커브)로 정합니다.

| 조건 | 판정 | 동작 |
| --- | --- | --- |
| `무효` 점수 ≥ `invalidRejectThreshold` | `REJECT` | 업로드 안 함, 재촬영 안내 |
| 미션 카테고리 점수 < `hardRejectThreshold` | `REJECT` | 업로드 안 함, 재촬영 안내 |
| 미션 카테고리 점수 ≥ `autoPassThreshold` | `PASS` | 기존 업로드·완료 흐름 진행 |
| 그 사이(애매) | `NEEDS_REVIEW` | 미션은 완료하되 `photoNeedsReview` 표시 → 관리자 검수 큐 |

모델을 불러오지 못하면 기본값은 `NEEDS_REVIEW`(관리자 확인) 입니다. 현재 임계값(`ml/thresholds.json` 에서 선정): `autoPass 0.65 / hardReject 0.22 / invalidReject 0.55`.

추론(`PhotoVerifier`) + 판정(`PhotoVerification`)을 묶은 "업로드 전 결정"은 `data/PhotoGate` 로 분리했습니다. Firebase·Android 비의존이라 `FakePhotoVerifier` 로 전 경로를 단위 테스트하며(`PhotoGateTest`), `FirebaseMissionRepository` 는 `PhotoGate.decide()` 결과(`Reject` / `Proceed(needsReview)`)에 따라 업로드/거부만 합니다.

### `ml/` 파이프라인

| 파일 | 내용 |
| --- | --- |
| `ml/labels.json` · `dataset_card.md` | 분류 클래스 정의(앱과 공유) · 수집 출처·규모 |
| `ml/data/` | 데이터셋 구축 (Places365/Food-101 validation → scene별 표본 → 분할 → HF Hub) |
| `ml/notebooks/train_photo_verifier.ipynb` | 데이터 로드 → CLIP 제로샷 → 헤드 학습 → 전체 파인튜닝 → 평가 → 임계값 선정 |
| `ml/export_onnx.py` | 파인튜닝 모델 → ONNX (`torch.onnx`, 전처리·라벨·버전 함께 출력) |
| `ml/thresholds.json` | 학습 결과로 선정한 임계값 + 평가 지표. `PhotoVerificationConfig` 기본값과 동기화 |

### 현재 상태

- [x] 판정 로직 `PhotoVerification` / `PhotoVerificationConfig` / `PhotoGate` + 단위 테스트
- [x] `MissionPerformViewModel` → `FirebaseMissionRepository` 연결 (업로드 전 판정, 결과 기록, UX 분기)
- [x] 관리자 검수 큐 `AdminPhotoReviewScreen`, Firestore 보안 규칙 `firestore.rules`
- [x] 데이터셋 5,300장 구축 (HF Hub `kimgayeon430/travel-mission-photos`)
- [x] Colab T4 파인튜닝 (`mobilevit-small-fullft-1`, test macro-F1 0.82) → `assets/photo_verifier.onnx`
- [x] `OnnxPhotoVerifier` 연결, 임계값 반영, 기본 verifier 전환
- [ ] 실기기 전체 루프 검증, `firestore.rules` 배포, 체험 데이터 보강 후 재학습

## 추천 re-ranker (`ml/reco/`)

규칙 점수(`MissionScorer`)에 완료 로그로 학습한 로지스틱 회귀 re-ranker 를 얹은 하이브리드 추천. "개인화 추천" 절 참고.

| | 규칙 | 학습 |
| --- | ---: | ---: |
| ROC-AUC (완료 예측, test) | 0.918 | 0.937 |
| NDCG@10 / MAP | 0.977 / 0.907 | 0.990 / 0.927 |

현재 모델은 실제 로그가 없어 시뮬레이터(`ml/reco/sim.py`)로 학습(`reranker-lr-sim-1`). `user_missions` 로그가 쌓이면 `build_dataset.py --from-firestore` 로 재학습.

## 미션 지도

미션 목록 화면 우상단의 **지도 보기 / 목록 보기** 토글로 같은 미션을 리스트와 네이버 지도로 번갈아 볼 수 있습니다. (`MissionMapScreen`)

- 위도·경도가 모두 유효한(유한값) 미션만 지도에 표시하고, 나머지는 리스트에서만 노출합니다.
- 거의 같은 좌표의 미션은 하나의 마커로 묶어 정보창(`InfoWindow`)에 개수를 표시합니다.
- 마커/정보창을 누르면 해당 미션 상세로 이동합니다.
- `MapView` 는 Compose `AndroidView` 로 감싸고 `Lifecycle` 이벤트와 `rememberSaveable` 로 상태(카메라 위치 등)를 화면 회전에도 유지합니다.
- 네이버 지도 인증 키(`NCP_KEY_ID`)는 `local.properties` → `manifestPlaceholders` 로 주입되어 VCS 에 올라가지 않습니다.

## 앱 내비게이션

내비게이션은 두 개의 `NavHost` 로 나뉩니다.

| 그래프 | 경로 | 설명 |
| --- | --- | --- |
| 루트 | `landing` → `signup` / `login` → `gate` / `preference` → `main` | 인증·온보딩 흐름. 로그인 상태면 `gate`, 아니면 `landing` 에서 시작 |
| 메인(하단 탭) | `home`, `mission`, `add`(관리자), `ranking`, `profile` | `main` 진입 후 표시. 상세·수행·관리자 화면은 이 그래프의 하위 경로 |

- `gate` 는 로그인된 기존 사용자의 `users/{uid}.preferences` 유무를 확인해 `main` 또는 `preference` 로 분기합니다. (조회 실패 시 앱을 막지 않고 `main` 으로 진행)
- `admins/{uid}` 문서가 있는 사용자에게만 하단 탭에 **Admin** 항목이 보이고, 관리자 경로는 진입 시 권한을 재확인합니다.

## 프로젝트 구조

```text
app/src/main/java/smu/ai/graduation_project
├── MainActivity.kt # 루트/메인 NavHost, 하단 탭, 인증·권한 게이트
├── data/           # Repository·Firebase·Supabase, PhotoVerifier·PhotoGate(사진 판정), RerankerSource(추천 모델 로드)
├── domain/         # Firebase 비의존 순수 로직 (거리·보상·완료·취향·추천 규칙, 사진 인증 판정)
├── model/          # Mission, UserRank 등 데이터 모델
├── navigation/     # 화면 경로 및 내비게이션 정의
└── ui/
    ├── admin/      # 미션·사용자 관리, 사진 검수(AdminPhotoReviewScreen) 화면
    ├── components/ # 공통 Compose 컴포넌트
    ├── screens/    # 랜딩·로그인·홈·미션 목록/상세/지도·수행·취향 선택·랭킹·프로필 및 ViewModel
    └── theme/      # 색상, 타이포그래피, 앱 테마

app/src/test/java/smu/ai/graduation_project
├── domain/         # 도메인 규칙 단위 테스트 (JUnit4)
└── data/           # PhotoGate 등 데이터 계층 순수 로직 테스트

firestore.rules     # Firestore 보안 규칙
firebase.json       # Firebase CLI 설정 (규칙 배포)
docs/               # 보고서 등 문서

ml/                 # 모델 학습·평가 (Colab/로컬, 앱 빌드와 분리)
├── labels.json · dataset_card.md · thresholds.json
├── data/           # 사진 인증 데이터셋 구축 (공개 데이터 수집·무효 합성·분할·HF 업로드)
├── notebooks/train_photo_verifier.ipynb · export_onnx.py   # 사진 인증 모델
└── reco/           # 추천 re-ranker (로그 → 로지스틱 회귀 → reranker.json, 오프라인 평가)
```

### 주요 도메인 모듈

| 모듈 | 책임 |
| --- | --- |
| `GeoDistance` | 두 좌표 사이 거리 계산 (Haversine) |
| `LocationVerification` | 허용 반경(기본 200m) 이내 여부 판정 |
| `MissionRewardPolicy` | 1·2단계 보상 계산과 중복 지급 방지 규칙 |
| `MissionCompletion` | 사진 인증 가능 여부·완료 처리 결과(`resolve`) 계산 |
| `PhotoVerification` | 온디바이스 모델의 라벨별 점수 → 통과 / 재촬영 / 관리자 검수 판정 |
| `TravelPreference` | 취향 카테고리 정의, 최소 1개 선택 규칙, 저장용 정규화 |
| `MissionFeatures` | 미션 추천 신호 5개(0~1 정규화) 계산. 규칙·학습이 공유 (`ml/reco/features.py` 와 일치) |
| `MissionScorer` | 신호를 `RecommendationWeights` 로 가중합 + 근거 문구 (규칙 점수) |
| `LearnedReranker` | 완료 로그로 학습한 로지스틱 회귀로 완료 확률 추정 (`assets/reranker.json`) |
| `MissionRecommender` | 후보 필터 → 규칙/학습 점수 블렌드 → 다양성 감점으로 상위 N건 |

## Firestore · Supabase Storage 데이터

| 경로 | 주요 필드 |
| --- | --- |
| `users/{uid}` | `nickname`, `mail`, `points`, `level`, `preferences[]` |
| `missions/{id}` | `title`, `desc`, `category`, `points`, `imageUrl`, `location`(GeoPoint), `completionCount` |
| `user_missions/{id}` | `userId`, `missionId`, `status`, `progress`, `stage1RewardGranted`, `stage2RewardGranted`, `photoUrl`, `photoStoragePath`, `photoVerified`, `photoUploadedAt`, `completedAt`, `photoNeedsReview`, `photoVerifyScore`, `photoVerifyLabel`, `photoVerifyModelVersion` |
| `admins/{uid}` | `email`, `name` |
| Supabase Storage `mission-photos/{missionId}/{uid}_{timestamp}.jpg` | 사진 인증 이미지 (공개 URL 로 접근) |

## 실행 방법

### 요구 환경

- Android Studio (AGP 9.2.0 / Gradle 9.4.1 지원 버전)
- JDK 17 이상 (Gradle 실행용)
- Android SDK 36 (`compileSdk 36`), 실행 기기·에뮬레이터는 Android 8.0(API 26) 이상
- Email/Password 인증과 Firestore가 활성화된 Firebase 프로젝트
- 사진 업로드용 Supabase 프로젝트 (무료 플랜, 결제 수단 불필요)
- 네이버 클라우드 플랫폼 **Maps** 이용 신청 후 발급받은 Client ID (지도 화면용)

### 필요 권한

| 권한 | 용도 |
| --- | --- |
| `INTERNET` | Firebase·Supabase·지도 통신 |
| `ACCESS_FINE_LOCATION` / `ACCESS_COARSE_LOCATION` | 1단계 GPS 위치 인증, 추천의 거리 근접도 |
| `CAMERA` | 2단계 사진 인증 촬영 |

### 실행

```bash
git clone https://github.com/kimgayeon430/graduation_project.git
cd graduation_project
```

1. Android Studio에서 프로젝트 루트 폴더를 엽니다.
2. Firebase Console에서 Android 앱을 등록하고 Authentication·Firestore를 활성화합니다.
3. 발급받은 `google-services.json`을 `app/` 폴더에 추가합니다.
4. Supabase에서 프로젝트를 만들고 Storage에 **public 버킷** `mission-photos` 를 생성합니다.
5. 그 버킷에 anon INSERT 정책을 추가합니다. (SQL Editor에서)
   ```sql
   create policy "anon upload mission-photos"
   on storage.objects for insert to anon
   with check (bucket_id = 'mission-photos');
   ```
6. 네이버 클라우드 플랫폼 콘솔에서 **Maps** 이용 신청 후 애플리케이션을 등록하고, Android 앱 패키지 이름 `smu.ai.graduation_project` 을 추가합니다.
7. `local.properties` 에 Supabase·네이버 지도 설정을 추가합니다. (anon/publishable 키와 지도 Client ID 는 클라이언트 노출용이라 안전, `local.properties` 는 VCS 에 올라가지 않음)
   ```properties
   SUPABASE_URL=https://<프로젝트>.supabase.co
   SUPABASE_ANON_KEY=<anon 또는 publishable 키>
   NAVER_MAP_CLIENT_ID=<네이버 클라우드 플랫폼 Maps Client ID>
   ```
   이 값들은 `app/build.gradle.kts` 에서 각각 `BuildConfig` 필드와 `manifestPlaceholders` 로 주입됩니다.
8. Gradle Sync 후 에뮬레이터 또는 Android 기기에서 앱을 실행합니다. (터미널에서는 `./gradlew installDebug`)
9. (선택) Firestore 보안 규칙을 배포합니다.
   ```bash
   npm i -g firebase-tools
   firebase login
   firebase use <Firebase 프로젝트 ID>
   firebase deploy --only firestore:rules
   ```

> 사진 인증 모델(`app/src/main/assets/photo_verifier.onnx`)이 없어도 앱은 동작합니다. 이때 판정은 `NEEDS_REVIEW` 로 처리됩니다.

### 사진 인증 모델 (선택)

앱 빌드와 분리된 파이프라인입니다. GPU가 있는 Colab/로컬에서 실행합니다.

```bash
cd ml
pip install -r requirements.txt
# notebooks/train_photo_verifier.ipynb 실행 → outputs/final/, thresholds.json 생성
python export_onnx.py --model outputs/final --out ../app/src/main/assets/photo_verifier.onnx --quantize
```

이후 `thresholds.json` 값을 `PhotoVerificationConfig` 기본값으로, 전처리 상수를 `OnnxPhotoVerifier` 로 옮깁니다. 자세한 내용은 [`ml/README.md`](ml/README.md).

### 단위 테스트

```bash
./gradlew :app:testDebugUnitTest
```

> 사용자 홈 경로에 한글 등 비 ASCII 문자가 있으면 Gradle 테스트 워커가 실행되지 않습니다.
> 이 경우 `GRADLE_USER_HOME` 을 ASCII 경로로 지정해 실행하세요. 예: `GRADLE_USER_HOME=D:\gradle-home ./gradlew :app:testDebugUnitTest`

### 빌드 문제 해결

- **`Gradle build daemon disappeared unexpectedly` / Sync 실패**: 빌드 스크립트 문제가 아니라 메모리 부족으로 데몬이 종료된 경우가 많습니다. Android Studio·브라우저 등을 정리해 RAM 을 확보한 뒤 다시 Sync 하세요. `gradle.properties` 는 저사양(RAM 8GB) 환경을 기준으로 데몬 힙(`-Xmx1536m`)과 동시 워커 수(`org.gradle.workers.max=2`)를 낮춰 두었습니다.
- 데몬이 꼬였을 때는 `./gradlew --stop` 으로 모든 데몬을 정리한 뒤 다시 실행합니다.
- IDE Gradle 설정(JDK·JVM 옵션)이 `gradle.properties` 보다 우선하므로, 값이 반영되지 않으면 Settings → Build Tools → Gradle 을 확인하세요.

## 구현 화면

- 랜딩 및 로그인·회원가입
- 여행 취향 선택
- 홈(선호 기반 추천)과 미션 목록·상세
- 미션 지도(네이버 지도, 마커 → 상세 이동)
- GPS·사진 기반 미션 수행
- 진행 중인 미션
- 포인트 랭킹 및 프로필
- 관리자 미션 관리 (위치 좌표 입력 포함)
- 관리자 사용자 관리

## 향후 개선 계획

- 사진 인증 모델 데이터 수집·학습 실행, `OnnxPhotoVerifier` 연결, 촬영 시각·위치 메타데이터 교차 검증
- 시간대·미션 간 동시출현(협업 필터링)까지 반영한 추천 고도화 및 오프라인 평가(hit@k)
- ViewModel·Repository 패턴을 홈·목록·관리자 등 나머지 화면으로 확대
- 서버 사이드 포인트 검증(Cloud Functions), Supabase Storage 업로드 서버 검증
- Robolectric 기반 ViewModel/Compose UI 테스트와 Repository 계약 테스트 추가
