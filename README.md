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
- 전체 미션 목록 조회 및 상세 정보 확인 — 위치 권한이 있으면 현재 위치로부터의 거리 표시, "길찾기" 버튼으로 기기에 설치된 지도 앱의 경로 안내로 바로 이동
- 미션 목록 ↔ **네이버 지도** 전환: 좌표가 있는 미션을 지도 마커로 모아 보고, 마커를 눌러 상세로 이동
- 미션별 GPS 위치 인증 (목표 지점 반경 200m 이내)
- 위치 인증 후 카메라 촬영 → 미리보기 → Supabase Storage 업로드로 사진 인증
- 사진 인증 후 AI 판정 결과(통과/검토중/반려)를 전체 화면으로 확인 — 사진·판정 사유·요구/예측 카테고리·신뢰도·포인트 지급 여부를 한 화면에서 보고 판정별로 다른 다음 행동(완료 확인 / 다시 촬영 / 미션 내용 보기) 선택
- PASS 직후 성취 연출(포인트 상승·컨페티·햅틱, 레벨업 시 "LEVEL UP!" 배너, 새 배지 획득 안내) 후 결과 화면으로 이어짐
- 인증 단계별 포인트 지급 및 진행 상태 저장 (중복 지급 방지)
- 진행 중인 미션 확인 및 이어서 수행
- 홈에서 취향·완료 이력 기반 미션 추천 (규칙 점수 + 완료 로그 학습 re-ranker 하이브리드, 완료한 미션 제외)
- 미션 성공 화면 하단에 다음 추천 미션 카드 1개 표시 ("다음에는 이런 미션 어때요?")
- 누적 포인트 기반 사용자 랭킹
- 누적 포인트 기반 여행 레벨(Lv.1~5) 진행률과 여행 배지 3종을 프로필에서 확인
- 프로필에서 포인트, 레벨 및 미션 현황 확인
- 마이페이지에서 한국어 / English / 日本語 3개 언어 UI 전환 (앱 전체 화면·하단 메뉴바에 즉시 반영)

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
| AI | ① 사진 인증: 온디바이스 이미지 분류 (`apple/mobilevit-small` 파인튜닝 → ONNX, `onnxruntime-android`) — `PhotoVerification` / `OnnxPhotoVerifier`, 참조 이미지 CLIP 유사도 보조 신호 — `OnnxClipPhotoEmbedder`  ② 추천: 완료 로그 학습 로지스틱 re-ranker (`LearnedReranker`, `ml/reco/`) |
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
   - 업로드가 성공한 뒤에만 트랜잭션으로 미션을 `Completed` 처리하고 2단계 보상을 지급하며, `photoUrl`(Supabase 공개 URL)·`photoStoragePath`·`photoVerified`·`photoNeedsReview`·`photoVerifyScore`·`photoVerifyLabel`·`photoVerifyModelVersion`·`photoVerifySimilarity`·`photoUploadedAt` 을 저장합니다.
   - 2단계 보상 = `미션 포인트 - 1단계 보상`
   - 업로드나 저장이 실패하면 미션은 완료되지 않으며, 재시도해도 포인트는 한 번만 지급됩니다.
   - 사용자가 처음 완료할 때 같은 트랜잭션에서 `missions/{id}.completionCount` 를 1 올립니다. (추천 인기도 신호)

## 여행 레벨 & 배지

미션을 완료하면 누적 포인트에 따라 레벨이 오르고, 특정 조건을 처음 충족하면 배지를 얻습니다. 둘 다 완료 트랜잭션 밖에서 따로 계산하지 않고, 기존 포인트 지급 트랜잭션(`FirebaseMissionRepository.uploadPhotoAndComplete`, 관리자 승인 `AdminPhotoReviewScreen.approve()`) 안에서 원자적으로 처리합니다.

### 레벨 (`TravelLevel` / `TravelLevelPolicy`)

Firestore에 레벨을 별도 필드로 중복 저장하지 않고 `users.points` 에서 매번 계산합니다(순수 Kotlin, `domain/TravelLevel.kt`).

| 레벨 | 이름 | 최소 포인트 |
| ---: | --- | ---: |
| Lv.1 | 여행 새싹 | 0P |
| Lv.2 | 동네 탐험가 | 500P |
| Lv.3 | 도시 여행자 | 1,500P |
| Lv.4 | 숨은 명소 수집가 | 3,000P |
| Lv.5 | 마스터 트래블러 | 5,000P |

진행률은 전체 5,000P가 아니라 **현재 레벨 구간** 기준입니다(예: 1,000P → Lv.2 구간에서 50%, 다음 레벨까지 500P). 최고 레벨은 진행률 100%로 고정되고 "다음 레벨까지" 문구 대신 "최고 레벨을 달성했어요!" 를 보여줍니다. 미션 완료로 레벨이 오르면 성취 연출에 "LEVEL UP!" 배너가 한 번만 추가로 표시됩니다. 프로필 화면과 성취 연출이 같은 `LevelProgress` / `LevelProgressCard` 를 공유합니다.

### 배지 3종 (`BadgeId` / `BadgeUnlockEvaluator`)

| 배지 | 조건 |
| --- | --- |
| 첫 발자국 | 누적 완료 미션 수가 처음 1개 이상 |
| 주간 탐험가 | 동일한 주(월요일 0시~)에 완료한 미션 수가 처음 5개 이상 |
| 취향 발견 | 같은 카테고리 완료 미션 수가 처음 3개 이상 |

- **카운터**: `users/{uid}` 문서에 `completedMissionsTotal`(Long) / `completedByCategory`(Map) / `completedByWeek`(Map, 주 시작 epoch millis 키) / `badges`(배열) 를 optional 필드로 추가했습니다. 기존 필드는 삭제·개명하지 않았으므로 이 필드가 없는 기존 사용자 데이터도 0/빈 값으로 취급되어 그대로 동작합니다.
- **왜 쿼리 대신 카운터인가**: Firestore 트랜잭션은 쿼리를 지원하지 않아(문서 단건 get만 가능) "이번 주/이 카테고리 완료 수"를 트랜잭션 안에서 직접 셀 수 없습니다. 그래서 `MissionRewardCounters.applyCompletion()` 이 `FieldValue.increment` 로 카운터를 원자적으로 올리고, before/after 값을 `BadgeUnlockEvaluator`(순수 Kotlin, 이번에 처음 문턱을 넘었는지만 판정)에 넘겨 새로 딴 배지를 같은 트랜잭션에서 함께 기록합니다.
- **중복 지급 방지**: 카운터/배지 갱신은 `MissionCompletion.Outcome.countTowardPopularity`(이 사용자가 이 미션을 처음 완료할 때만 true) 가 참일 때만 실행되어 재제출·중복 완료 요청을 자동으로 걸러냅니다. 배지 자체도 기존 `badgeId` 목록으로 한 번 더 걸러 중복 생성을 막습니다.
- **한 번에 여러 배지**: 취향 발견과 주간 탐험가는 같은 완료로 동시에 달성할 수 있습니다. 화면은 "새로운 배지 N개를 획득했어요!" 로 묶어서 보여줍니다(좌우 넘기기 UI는 만들지 않음 — 로직 정확성을 UI 화려함보다 우선).

### 프로필 화면

레벨 카드(레벨명·번호·포인트·진행률 바)와 "나의 여행 배지" 카드(획득: 보라 아이콘 + 획득일 / 미획득: 회색 실루엣 + 자물쇠 + 획득 조건)를 기존 화면에 추가했습니다. 미획득 배지도 숨기지 않고 조건을 보여줘 사용자가 다음 목표를 알 수 있게 합니다.

### 한계

- `users` 문서 쓰기 권한은 기존과 동일하게 "본인 또는 관리자" 라(서버 없이는) 포인트와 마찬가지로 카운터·배지도 클라이언트가 직접 조작할 수 있는 여지가 있습니다 — 기존 포인트 시스템과 같은 한계이며 아래 "향후 개선 계획"에 이미 있는 서버 사이드 검증 항목으로 함께 다룹니다.
- `completedByWeek` 의 "주"는 ISO 주차가 아니라 기기 로컬 시각 기준 "이번 주 월요일 0시" epoch millis 를 키로 씁니다 — 연도 경계의 ISO 주차 계산 미묘함을 피하려는 선택입니다.

## 미션 성공 시 다음 미션 추천

PASS 판정을 받은 결과 화면 하단에 "다음에는 이런 미션 어때요?" 카드를 하나 보여줍니다. 새 추천 모델을 만들지 않고 기존 `MissionRecommender.recommendScored()` 를 그대로 재사용합니다(아래 "개인화 추천" 절의 규칙 점수 계열 — 카드 1개만 필요해 re-ranker 모델 로딩 없이 규칙 점수만 씁니다).

- 제외 대상: 완료한 미션 전체 + 방금 완료한 미션(id로 한 번 더 명시적 제외 — Firestore 반영 지연에 대비). 프로젝트 스키마에 "비활성 미션"/"관리자 전용 미션" 개념 자체가 없어 별도 필터는 두지 않았습니다.
- 추천이 없으면 빈 카드 대신 "오늘의 미션을 모두 둘러봤어요." 안내를 보여줍니다.
- 이 화면에서 사용자의 실시간 위치를 다시 구하지 않으므로 거리는 표시하지 않습니다 — 명세가 허용한 "계산 불가하면 생략"에 해당합니다.
- 예상 소요 시간(`Mission.estimatedMinutes`)은 새 optional 필드로, 관리자가 입력한 미션만 표시되고 나머지는 이 줄을 숨깁니다.
- "이 미션 시작하기" → 해당 미션 상세 화면으로 이동만 하고 자동으로 완료·참여 처리하지 않습니다. "다음에 할게요" → 기존 "홈으로" 흐름과 같은 목적지로 이동합니다.

## 개인화 추천 (규칙 + 학습 하이브리드)

홈의 추천 미션 상위 3건은 **규칙 점수를 완료 로그로 학습한 re-ranker 로 다시 매겨** 만듭니다. 학습 모델(`assets/reranker.json`)이 없으면 규칙 점수만으로 정렬합니다(콜드스타트).

**신호 6개** (`MissionFeatures`, 0~1 정규화 — 규칙·학습이 공유)

- **명시적 취향**: 미션 카테고리가 `users/{uid}.preferences` 에 포함되면 1
- **암묵적 취향**: 그 카테고리 미션을 완료한 비율
- **난이도 적합도**: 미션 포인트대가 사용자 레벨 기대치에 가까운 정도
- **거리 근접도**: 현재 위치로부터의 근접도 (위치 권한이 허용된 경우에만) — 홈 추천 카드에 실제 거리("320m"/"1.2km", `GeoDistance.format`)도 함께 표시
- **인기도**: 다른 사용자의 완료 횟수(`missions/{id}.completionCount`) 기반
- **시간대 적합도**: 현재 시각이 미션 카테고리 활동 시간대(맛집=점심·저녁, 투어·체험=낮, 쇼핑=오후~저녁)에 맞는 정도

1. id가 없거나 완료한 미션은 후보에서 제외합니다.
2. 후보마다 **규칙 점수**(`MissionScorer`, `RecommendationWeights` 가중합 + 근거 칩)와 **학습된 완료 확률**(`LearnedReranker`, 로지스틱 회귀)을 구합니다.
3. `최종 = (1−λ)·규칙점수/최댓값 + λ·학습확률` (λ = `blend`, 기본 0.6).
4. "최종 − 다양성 감점 × 이미 뽑힌 같은 카테고리 수" 가 가장 높은 미션을 하나씩 3건 선택합니다.
5. 근거 칩(`맛집 취향`, `자주 하는 유형`, `가까운 미션` 등)은 규칙 점수 것을 그대로 표시합니다.
6. 진행 중인 미션이 있으면 추천 대신 노출합니다. 결과가 없으면 안내 카드.

학습·평가 파이프라인은 `ml/reco/` (`build_dataset.py` → `train_reranker.py` → `evaluate_reco.py`). 현재 모델은 실제 로그가 없어 시뮬레이터로 학습(`reranker-lr-sim-3`); `user_missions` 로그가 쌓이면 `--from-firestore` 로 재학습합니다.

신규 가입자는 회원가입 직후 취향 선택 화면으로 이동하고, 기존 사용자는 `preferences` 가 없을 때만 이 화면을 거칩니다.

## 사진 인증 모델 (온디바이스)

2단계 사진 인증에서 "아무 사진이나 통과"되는 문제를 없애기 위해, 촬영본을 **온디바이스 이미지 분류 모델**로 1차 판정합니다. 판정 규칙은 Firebase·모델에 의존하지 않는 순수 Kotlin `PhotoVerification` 으로 분리해 단위 테스트합니다.

### 접근

- **모델**: HuggingFace `apple/mobilevit-small` (~5M 파라미터) 를 미션 사진으로 **전체 파인튜닝**. 학습 없는 `CLIP` 제로샷을 비교 기준선으로 둡니다. test 정확도 0.84 / macro-F1 0.81 (CLIP 제로샷 0.63).
- **데이터**: `투어 / 맛집 / 체험 / 쇼핑` + `무효` 5클래스, 총 6,063장 (HF Hub `kimgayeon430/travel-mission-photos`). Places365 / Food-101 validation 셋에서 scene 별로 표본, 무효는 스크린샷·열화·화면 재촬영(`recapture()`) 3종 합성. 정의는 `ml/labels.json` 이 단일 소스.
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

### 참조 이미지 유사도 (보조 신호, 보고서 6.7절)

카테고리 분류만으로는 "미션 *유형* 에 맞는 사진인가"만 보고 "*이* 미션의 대상을 찍었는가"는 못 봅니다(예: 투어 미션에 아무 야외 사진이나 내도 통과). 이를 보완하기 위해 미션 대표 이미지의 **CLIP 임베딩**과 촬영본 임베딩의 코사인 유사도를 보조 신호로 결합합니다.

- **임베더**: CLIP ViT-B/32 int8 ONNX(≈89MB). APK 에 번들하지 않고 첫 사진 인증 시 HF Hub `kimgayeon430/travel-mission-photo-embedder` 에서 받아 `filesDir` 에 캐시(`OnnxClipPhotoEmbedder`, 미션 화면 진입 시 `prefetch`).
- **참조 이미지는 여러 장 등록 가능**: `missions/{id}.photoEmbeddings`(배열, 원소는 Firestore 의 "배열의 배열 금지" 제약 때문에 `{"v": [...]}` 맵으로 감쌈)에 각도·조명이 다른 사진 여러 장을 저장하면, 판정 시 **최대 유사도**를 씁니다 — 한 장만 닮아도 같은 대상으로 인정(오프라인 검증에서 분리력 Youden J 0.33 → 0.47). 사전계산은 `ml/embed_missions.py`(`imageUrl`+`imageUrls` 또는 CSV 반복 행). 레거시 단일 필드 `photoEmbedding` 도 계속 읽어 합치므로 기존 미션은 그대로 동작합니다.
- **결합 규칙**: 유사도만으로 통과/거절을 뒤집지 않고 판정을 한 단계씩만 조정합니다(무효 판정이 유사도보다 항상 먼저 — 대표 이미지를 화면에 띄워 재촬영하는 스푸핑은 유사도가 높게 나오므로).
- **한계**: 참조 이미지 1장으로는 분리력이 약하고(6.7.6), 임계값(`similarityRescueThreshold`/`similaritySuspectThreshold`)은 아직 실사용 로그가 적어 잠정치입니다. `ml/calibrate_similarity.py`(실사용 로그)·`ml/calibrate_similarity_web.py`(웹 프록시)로 계속 보정합니다.

### 판정 결과 화면

AI 판정이 끝나면 Toast 대신 **전체 화면**(`Dialog(usePlatformDefaultWidth = false)`)으로 결과를 보여줍니다. 사진, 판정 상태(PASS/REVIEW/REJECT), 미션이 요구한 카테고리 vs AI가 예측한 카테고리, confidence(정수 %), 포인트 지급 여부, 온디바이스 분석 고지를 보여주고 판정별로 다른 버튼을 둡니다(PASS·REVIEW: 완료/확인, REJECT: 다시 촬영/미션 내용 보기). REJECT 사유는 도메인 계층에서 `RejectReasonCode`(무효 대상/카테고리 불일치/저신뢰)로만 분류하고 실제 문구는 `strings.xml`에서 고릅니다. 온디바이스 AI 분석 자체가 실패하는 경우(`Stage.ANALYZE`)와 분석 후 업로드·저장이 실패하는 경우(`Stage.UPLOAD`/`FINALIZE`)를 구분해 서로 다른 안내를 보여줍니다. 분석 중/결과 모두 `MissionPerformViewModel.uiState`에 있어 화면 회전에도 유지되고, 결과가 떠 있는 동안은 전체 화면 `Dialog`가 화면을 가려 중복 제출을 막습니다.

이 작업 과정에서 실사용 중 발견한 버그 두 가지를 함께 고쳤습니다.

- **검수 대기(`NEEDS_REVIEW`) 미션이 관리자 승인 전에 완료·포인트 지급되던 버그**: `MissionCompletion.resolve()`가 `needsReview` 여부를 보지 않고 항상 완료 처리했습니다. `resolve()`에 `needsReview` 파라미터를 추가하고, 관리자 승인 시점의 완료·보상 전환은 `resolveApproval()`로 분리했습니다.
- **검수 대기 중 재인증했을 때 `PERMISSION_DENIED`로 저장이 실패하던 버그**: `firestore.rules`가 "사용자는 자기 `photoNeedsReview`를 true→false로 되돌릴 수 없다(관리자 승인 우회 방지)"는 규칙을 갖고 있었는데, 이게 검수 대기 중 재촬영해 AI가 이번엔 자동 PASS를 낸 정상적인 흐름까지 막았습니다. 서버 검증 없이는 "정상 재판정"과 "임의 조작"을 규칙만으로 구분할 수 없어(아래 "향후 개선 계획" 참고) 해당 제약을 제거하고 재배포했습니다(`firestore-tests/rules.test.js` 갱신, 에뮬레이터 테스트 20건 통과 확인).

### `ml/` 파이프라인

| 파일 | 내용 |
| --- | --- |
| `ml/labels.json` · `dataset_card.md` | 분류 클래스 정의(앱과 공유) · 수집 출처·규모 |
| `ml/data/` | 데이터셋 구축 (Places365/Food-101 validation → scene별 표본 → 분할 → HF Hub) |
| `ml/data/import_collected.py` | 크라우드소싱 등 직접 수집한 사진을 `raw/<카테고리>/`에 합류(place_id 그룹 분할 안전) |
| `ml/notebooks/train_photo_verifier.ipynb` | 데이터 로드 → CLIP 제로샷 → 헤드 학습 → 전체 파인튜닝 → 평가 → 임계값 선정 |
| `ml/export_onnx.py` | 파인튜닝 모델 → ONNX (`torch.onnx`, 전처리·라벨·버전 함께 출력) |
| `ml/thresholds.json` | 학습 결과로 선정한 임계값 + 평가 지표. `PhotoVerificationConfig` 기본값과 동기화 |
| `ml/export_clip_image_encoder.py` | 참조 이미지 유사도용 CLIP 이미지 인코더 export |
| `ml/embed_missions.py` | 미션 대표 이미지(들)를 CLIP 임베딩으로 사전계산해 Firestore `photoEmbeddings`에 저장 |
| `ml/calibrate_similarity.py` / `ml/calibrate_similarity_web.py` | 유사도 임계값(rescue/suspect) 실사용 로그 / 웹 프록시 보정 |

### 현재 상태

- [x] 판정 로직 `PhotoVerification` / `PhotoVerificationConfig` / `PhotoGate` + 단위 테스트
- [x] `MissionPerformViewModel` → `FirebaseMissionRepository` 연결 (업로드 전 판정, 결과 기록, UX 분기)
- [x] 관리자 검수 큐 `AdminPhotoReviewScreen`, Firestore 보안 규칙 `firestore.rules`
- [x] 데이터셋 6,063장 구축 (HF Hub `kimgayeon430/travel-mission-photos`)
- [x] Colab T4 파인튜닝 (`mobilevit-small-fullft-1`, test macro-F1 0.81) → `assets/photo_verifier.onnx`
- [x] `OnnxPhotoVerifier` 연결, 임계값 반영, 기본 verifier 전환
- [x] `firestore.rules` 배포 (`grad-proj-5e09c`, 에뮬레이터 테스트 20건 통과 확인 후 배포)
- [x] 참조 이미지 유사도 결합 (CLIP 임베딩, 6.7절) + 다중 참조 이미지(최대 유사도, 6.7.8) — 실기기 승인/반려 플로우 확인
- [x] 무효 클래스에 화면 재촬영 합성 augmentation(`recapture()`) 반영 재학습 — invalid recall 0.92→0.93 유지, `build_dataset.py`의 place_id 그룹 분할 버그(합성 무효 파일명이 소수 그룹에 뭉쳐 split이 쏠리던 문제)를 발견·수정한 뒤 재학습해 정상화
- [x] 검수 대기(`NEEDS_REVIEW`) 미션이 관리자 승인 전에 완료·포인트 지급되던 버그 수정 — `MissionCompletion.resolve()`/`resolveApproval()` 분리
- [x] 사진 인증 결과를 전체 화면으로 표시 — Toast 대신 `Dialog`로 PASS/REVIEW/REJECT 판정·근거·포인트 표시, AI 분석 실패와 저장 실패를 구분한 안내
- [x] 검수 대기 중 재인증 시 `PERMISSION_DENIED`로 저장 실패하던 `firestore.rules` 버그 발견·수정·재배포
- [ ] 크라우드소싱 사진으로 체험 클래스 보강 — 수집 스크립트(`ml/data/import_collected.py`)만 준비됨, 실사진 수집은 별도

## 추천 re-ranker (`ml/reco/`)

규칙 점수(`MissionScorer`)에 완료 로그로 학습한 로지스틱 회귀 re-ranker 를 얹은 하이브리드 추천. "개인화 추천" 절 참고.

| | 규칙 | 학습 |
| --- | ---: | ---: |
| ROC-AUC (완료 예측, test) | 0.949 | 0.960 |
| precision@3 | 0.914 | 0.951 |
| NDCG@5 / MAP | 0.914 / 0.873 | 0.953 / 0.904 |

현재 모델은 실제 로그가 없어 시뮬레이터(`ml/reco/sim.py`)로 학습(`reranker-lr-sim-3`). `user_missions` 로그가 쌓이면 `build_dataset.py --from-firestore` 로 재학습.

## 미션 지도

미션 목록 화면 우상단의 **지도 보기 / 목록 보기** 토글로 같은 미션을 리스트와 네이버 지도로 번갈아 볼 수 있습니다. (`MissionMapScreen`)

- 위도·경도가 모두 유효한(유한값) 미션만 지도에 표시하고, 나머지는 리스트에서만 노출합니다.
- 마커 색으로 이 사용자의 미션 진행 상태(수행 전/수행 중/완료)를 구분합니다 — 미션 목록 화면과 같은 3색(완료: 보라, 진행중: 초록, 미 진행: 황토)을 씁니다. 거의 같은 좌표의 미션은 하나의 클러스터 마커(주황)로 묶어 정보창(`InfoWindow`)에 개수를 표시합니다.
- 사진 인증까지 완료한 미션의 마커를 누르면, 제목·포인트 대신(위에) 실제로 제출한 인증 사진(`user_missions.photoUrl`)을 정보창에 보여줍니다. 네이버 지도 `InfoWindow.Adapter` 는 구글 지도와 달리 살아있는 View 를 못 붙이고 비트맵(`OverlayImage`)만 받아, 사진을 Coil 로 비동기 로드한 뒤 뷰를 직접 캔버스에 그려 비트맵으로 변환해 붙입니다.
- 마커/정보창을 누르면 해당 미션 상세로 이동합니다.
- `MapView` 는 Compose `AndroidView` 로 감싸고 `Lifecycle` 이벤트와 `rememberSaveable` 로 상태(카메라 위치 등)를 화면 회전에도 유지합니다.
- 네이버 지도 인증 키(`NCP_KEY_ID`)는 `local.properties` → `manifestPlaceholders` 로 주입되어 VCS 에 올라가지 않습니다.

## 다국어 지원 (한국어 / English / 日本語)

마이페이지 → **언어 / Language / 言語** 에서 한국어·영어·일본어를 선택하면 앱 전체(하단 메뉴바, 관리자 화면 포함)가 즉시 해당 언어로 전환됩니다.

- **UI 문구**: `values/strings.xml`(기본, 한국어) / `values-en/strings.xml`(영어) / `values-ja/strings.xml`(일본어) 리소스로 관리하며, 세 파일은 키가 1:1로 대응합니다.
- **적용 방식**: 선택한 언어는 `SharedPreferences`(`LanguagePreference`)에 저장되고, `MainActivity.attachBaseContext` 가 이를 읽어 `Configuration` 을 감싼 Context 로 액티비티를 재생성합니다. `AppCompatDelegate.setApplicationLocales` 는 `ComponentActivity`(AppCompatActivity 아님)에서 리소스가 즉시 갱신되지 않아 쓰지 않고, 수동 Locale/Configuration 전환 방식을 사용합니다.
- **Compose 밖(ViewModel 등)**: `Context.getLocalizedString()` 확장 함수가 호출 시점마다 저장된 언어로 다시 감싼 Context 에서 문자열을 읽어, 화면 재구성 없이도 최신 언어를 반영합니다. (`MissionPerformViewModel` 의 위치·사진 인증 안내 메시지 등)
- **미션 콘텐츠(제목/설명)**: Firestore에 `title`/`desc`(한국어)와 `titleEn`/`descEn`(영어), `titleJa`/`descJa`(일본어)로 함께 저장하고, `DocumentSnapshot.localizedString()` 이 현재 언어에 맞는 필드를 고릅니다. 번역이 비어 있으면 한국어로 안전하게 폴백합니다.
- **내부 상태값**: 미션 카테고리(`투어`/`맛집`/`체험`/`쇼핑`)와 진행 상태(`진행중`/`완료`/`미 진행`) 코드는 Firestore·내부 로직에서 항상 한국어 값을 그대로 쓰고, 화면에 표시할 때만 `categoryLabel()` / `missionStatusLabel()` 로 번역합니다.
- **검증 습관**: 리소스 파일 3개(ko/en/ja)의 키가 어긋나면 그 언어만 조용히 깨지므로, 새 문구를 추가할 때마다 세 파일의 `<string name="...">` 키 집합을 diff 로 비교합니다.

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
| `MissionFeatures` | 미션 추천 신호 6개(0~1 정규화) 계산. 규칙·학습이 공유 (`ml/reco/features.py` 와 일치) |
| `MissionScorer` | 신호를 `RecommendationWeights` 로 가중합 + 근거 문구 (규칙 점수) |
| `LearnedReranker` | 완료 로그로 학습한 로지스틱 회귀로 완료 확률 추정 (`assets/reranker.json`) |
| `MissionRecommender` | 후보 필터 → 규칙/학습 점수 블렌드 → 다양성 감점으로 상위 N건 |

## Firestore · Supabase Storage 데이터

| 경로 | 주요 필드 |
| --- | --- |
| `users/{uid}` | `nickname`, `mail`, `points`, `level`, `preferences[]` |
| `missions/{id}` | `title`, `desc`, `category`, `points`, `imageUrl`, `imageUrls`(배열, 선택 — 참조 이미지 추가), `location`(GeoPoint), `completionCount`, `photoEmbeddings`(배열, 원소는 `{v:[...]}` 맵), `photoEmbedding`(단일, 레거시), `photoEmbeddingModelVersion` |
| `user_missions/{id}` | `userId`, `missionId`, `status`, `progress`, `stage1RewardGranted`, `stage2RewardGranted`, `photoUrl`, `photoStoragePath`, `photoVerified`, `photoUploadedAt`, `completedAt`, `photoNeedsReview`, `photoVerifyScore`, `photoVerifyLabel`, `photoVerifyModelVersion`, `photoVerifySimilarity` |
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
- 미션 성공 성취 연출(레벨업·배지 획득 포함) 및 다음 추천 미션 카드
- 진행 중인 미션
- 포인트 랭킹 및 프로필(여행 레벨 진행률, 여행 배지 3종)
- 관리자 미션 관리 (위치 좌표 입력 포함)
- 관리자 사용자 관리

## 향후 개선 계획

- 유사도 임계값(`rescue`/`suspect`) 실사용 로그 기반 확정 — 현재는 실사용 1건 + 웹 프록시 표본뿐이라 통계적으로 부족 (`ml/calibrate_similarity.py`)
- 크라우드소싱 사진으로 체험(및 무관 실내) 클래스 보강 후 재학습 — 무효 클래스는 화면 재촬영 합성으로 이미 보강·재학습 완료(위), 체험은 실사진 수집이 남음. 수집 시 `ml/data/import_collected.py --category 체험 --from-dir <폴더>` 로 기존 파이프라인에 합류
- 촬영 시각·EXIF·위치 메타데이터 교차 검증, GPS 스푸핑/순간이동 탐지
- 미션 간 동시출현(협업 필터링) 신호까지 반영한 추천 고도화 (시간대 적합도·오프라인 평가는 반영 완료), `user_missions` 실로그로 re-ranker 재학습
- ViewModel·Repository 패턴을 홈·목록·관리자 등 나머지 화면으로 확대
- 서버 사이드 포인트 검증(Cloud Functions), Supabase Storage 업로드 서버 검증 — 여행 레벨·배지 카운터도 포인트와 같은 구조라 같은 서버 검증 작업으로 함께 다룰 예정
- 다음 미션 추천 카드에 사용자 실시간 위치 기반 거리 표시 추가
- Robolectric 기반 ViewModel/Compose UI 테스트와 Repository 계약 테스트 추가
