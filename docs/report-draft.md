# 졸업 프로젝트 보고서 (초안)

> 이 문서는 초안이다. `[TODO]` 표시는 실제 수치·인용·스크린샷으로 채워야 한다.
> 제출용(HWP/Word)으로 옮기기 전 지도교수 피드백을 반영한다.

- **프로젝트명**: Travel Mission — 온디바이스 사진 인증을 적용한 여행지 미션 게이미피케이션 앱
- **소속**: 숙명여자대학교 인공지능공학부
- **작성자**: 김가연 `[학번]`
- **지도교수**: `[교수명]`
- **개발 기간**: `[YYYY.MM ~ YYYY.MM]`

---

## 1. 프로젝트 개요

### 1.1 목적

여행지에서 미션을 수행하고 GPS와 사진으로 인증해 포인트·순위를 쌓는 Android 앱을 개발한다.
본 프로젝트의 인공지능 핵심 기여는 **미션 사진 인증을 온디바이스 비전 모델로 자동 검증**하는 기능이다.
기존 앱은 사진을 업로드만 하면 통과되어 인증 신뢰도가 낮았고, 개인화 추천도 규칙 기반에 그쳐
"직접 학습·평가한 모델"이 없었다. 이를 보완해 게이미피케이션의 무결성을 높인다.

### 1.2 개발 환경

| 구분 | 내용 |
| --- | --- |
| 언어 / UI | Kotlin, Jetpack Compose (Material 3) |
| 아키텍처 | ViewModel · Repository(인터페이스/Firebase 구현) · 순수 도메인 로직 분리, JUnit4 단위 테스트 |
| 백엔드 | Firebase Authentication, Cloud Firestore, Supabase Storage(사진), 네이버 지도 SDK |
| AI 학습 | Python, HuggingFace `transformers`/`datasets`/`peft`, Colab GPU |
| AI 추론 | ONNX Runtime Mobile (온디바이스) `[예정]` |
| 협업 | 맥(코드 편집) ↔ 윈도우(빌드·학습), GitHub 를 통한 동기화 |

---

## 2. 배경 및 문제 정의

### 2.1 기존 앱 현황

- 사용자: 회원가입·취향 선택 → 미션 목록/지도 → GPS 위치 인증(반경 200m) → 카메라 사진 촬영 → 업로드 → 포인트 지급
- 관리자: 미션 CRUD, 사용자·권한 관리
- 개인화: `MissionScorer` + `MissionRecommender` 의 규칙 기반 점수(명시적/암묵적 취향, 난이도 적합도, 거리, 인기도)로 상위 3건 추천

### 2.2 인공지능 관점의 한계

1. **사진 인증에 검증이 없다.** 2단계 인증은 촬영본을 그대로 업로드하고 `photoVerified = true` 로 하드코딩했다.
   미션과 무관한 사진(셀카, 스크린샷, 실내 사진)으로도 포인트를 받을 수 있어 부정 사용에 취약하다.
2. **학습된 모델이 없다.** 추천은 하드코딩 가중치의 규칙 기반이며, 오프라인 평가·학습 절차가 없다.

### 2.3 문제 정의

> 촬영된 미션 사진이 해당 미션의 카테고리(투어·맛집·체험·쇼핑)에 부합하는지,
> 혹은 인증에 부적절한 사진(무효)인지 **기기에서 즉시 판정**하고,
> 확신도에 따라 자동 통과 / 관리자 검수 / 재촬영으로 분기한다.

제약: 서버 비용 없이 오프라인에 가깝게 동작해야 하므로 **온디바이스 추론**으로 한정한다.

---

## 3. 관련 연구 및 기술

- **전이학습(Transfer Learning)**: 대규모 사전학습 백본에 소규모 도메인 데이터로 분류 헤드/일부 레이어만 재학습.
  데이터가 적은 졸업 프로젝트 환경에 적합. `[인용 TODO]`
- **경량 비전 백본**: MobileNet, EfficientNet-Lite, **MobileViT** — 합성곱과 트랜스포머를 결합해
  모바일에서 정확도·지연을 절충. 본 프로젝트는 `apple/mobilevit-small`(약 500만 파라미터) 사용. `[인용 TODO]`
- **CLIP(Contrastive Language-Image Pre-training)**: 이미지-텍스트 공동 임베딩으로 **제로샷 분류** 가능.
  학습 없이 베이스라인 성능을 측정하는 기준선으로 사용. `[인용 TODO]`
- **파라미터 효율적 파인튜닝(PEFT/LoRA)**: 소수의 저랭크 행렬만 학습해 과적합·연산을 줄임. 비교 실험군. `[인용 TODO]`
- **온디바이스 추론 런타임**: ONNX Runtime Mobile / TensorFlow Lite. HuggingFace Optimum 으로 ONNX 변환·양자화.

---

## 4. 제안 방법: 온디바이스 사진 인증 모델

### 4.1 전체 구조

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

- 모델 의존(추론)은 `data/` 계층에, **판정 규칙은 Firebase·모델·Android 에 의존하지 않는 순수 Kotlin**(`domain/PhotoVerification`)으로 분리해 단위 테스트한다. 기존 `LocationVerification`·`MissionCompletion` 과 동일한 설계.
- `PhotoVerifier` 는 인터페이스이며 구현체(`OnnxPhotoVerifier`, 테스트용 `FakePhotoVerifier`)를 교체할 수 있다.

### 4.2 데이터셋

| 클래스 | 설명 | 목표 규모 |
| --- | --- | --- |
| 투어 | 관광지·랜드마크·전망·거리 | 800~1200 |
| 맛집 | 음식·식당 내부·메뉴판 | 800~1200 |
| 체험 | 공방·액티비티·전통 체험 | 500~800 |
| 쇼핑 | 상점·시장·상품 진열 | 500~800 |
| 무효 | 셀카·스크린샷·무관 실내·흐린 사진 | 1000~1500 |

- **수집 출처**: 공개 데이터셋(Places365, Food-101, Google Landmarks v2 한국 부분집합), 동기·지인 크라우드소싱(실제 미션 수행 사진), 무효 표본 합성.
- **분할**: train/val/test = 70/15/15. **촬영 장소 단위로 그룹을 묶어 분할**해 근접 중복이 학습·평가에 걸쳐 성능이 부풀려지는 것을 방지한다.
- 구축 자동화: `ml/data/` (`fetch_public.py`, `make_negatives.py`, `build_dataset.py`, `upload_hf.py`).
- `ml/labels.json` 이 클래스 정의의 단일 소스이며 앱의 `PhotoVerification.INVALID_LABEL` 과 일치한다.

### 4.3 모델 및 학습

| 단계 | 모델 | 학습 |
| --- | --- | --- |
| 베이스라인 | `openai/clip-vit-base-patch32` (또는 한국어 CLIP) | 없음 (제로샷) |
| 본 모델 A | `apple/mobilevit-small` | 백본 동결, 분류 헤드만 학습 (linear probe) |
| 본 모델 B | `apple/mobilevit-small` | 전체 파인튜닝 |
| 비교군 | `apple/mobilevit-small` + LoRA | 저랭크 어댑터만 학습 |

- 학습·평가 파이프라인: `ml/notebooks/train_photo_verifier.ipynb` (Colab).
- 최종 모델은 HuggingFace Optimum 으로 ONNX 변환(+ int8 양자화) 후 `app/src/main/assets/photo_verifier.onnx` 로 번들.
- 전처리 상수(리사이즈 크기, 정규화 mean/std)는 학습·export·Android 추론에서 동일하게 유지한다.

### 4.4 판정 규칙 (`PhotoVerification`)

모델이 낸 라벨별 점수 `s` 와 미션 기대 카테고리 `c`, 임계값(`PhotoVerificationConfig`)에 대해:

| 조건 | 판정 |
| --- | --- |
| `s[무효] ≥ invalidRejectThreshold` | `REJECT` |
| `s[c] < hardRejectThreshold` | `REJECT` |
| `s[c] ≥ autoPassThreshold` | `PASS` |
| 그 외 | `NEEDS_REVIEW` |

- 기본값: `autoPass = 0.70`, `hardReject = 0.30`, `invalidReject = 0.60` (오프라인 PR 커브로 재조정 예정).
- 모델을 불러오지 못하면 기본적으로 `NEEDS_REVIEW`. (현재 모델 미배포 상태에서는 `passWhenModelUnavailable = true` 로 통과)
- **설계 결정**: `NEEDS_REVIEW` 도 미션 완료·포인트 지급은 즉시 진행하고 검수 플래그만 남긴다.
  "포인트 보류" 상태를 만들지 않아 사용자 경험이 단순하며, 관리자 검수는 사후 부정 적발 용도다.

### 4.5 앱 통합

- **업로드 전 판정**: `FirebaseMissionRepository.uploadPhotoAndComplete` 가 백그라운드 스레드에서 `classify → verify` 를 먼저 수행하고, `REJECT` 면 업로드하지 않는다. 트래픽·저장 비용을 아낀다.
- **기록 필드**: `user_missions` 에 `photoVerified`, `photoNeedsReview`, `photoVerifyScore`, `photoVerifyLabel`, `photoVerifyModelVersion` 을 트랜잭션으로 저장.
- **관리자 검수 큐**(`AdminPhotoReviewScreen`): `photoNeedsReview == true` 건을 사진·판정 근거와 함께 목록화.
  - 승인 → `photoVerified = true`
  - 반려 → 트랜잭션으로 2단계 보상 회수(0 미만 클램프) + 미션을 `In Progress` 로 되돌려 재인증 유도

---

## 5. 구현 현황

### 5.1 완료

- [x] 판정 도메인 로직 `PhotoVerification` + `PhotoVerificationConfig` + 단위 테스트 7건
- [x] 추론 인터페이스 `PhotoVerifier` (+ `FakePhotoVerifier`)
- [x] 미션 완료 흐름 연결 (업로드 전 판정, 결과 기록, `REJECT`/`NEEDS_REVIEW` UX 분기)
- [x] 관리자 사진 검수 큐 화면
- [x] 데이터셋 구축 스크립트 `ml/data/`, 학습 노트북, ONNX export 스크립트

### 5.2 남은 작업

- [ ] 데이터셋 수집 실행 (목표 규모 확보)
- [ ] 모델 학습 및 임계값 확정 (`thresholds.json` → `PhotoVerificationConfig` 반영)
- [ ] `OnnxPhotoVerifier` 구현 (ONNX Runtime Mobile) 및 기본 verifier 교체
- [ ] Compose UI 테스트, Firestore 보안 규칙 정비 `[선택]`

---

## 6. 평가 계획

| 항목 | 지표 |
| --- | --- |
| 분류 성능 | 클래스별 precision / recall / F1, macro-F1, confusion matrix |
| 인증 신뢰도 | **무효 사진 차단율**(무효 recall), **정상 사진 오탐율**(정상이 `REJECT` 되는 비율) |
| 임계값 선정 | 카테고리 점수 PR 커브, 무효 점수 ROC |
| 비교 실험 | CLIP 제로샷 vs 헤드 학습 vs 전체 파인튜닝 vs LoRA |
| 온디바이스 비용 | 모델 크기(MB), 평균 추론 지연(ms), 앱 APK 증가량 |

- 테스트셋은 학습에 쓰지 않은 장소로만 구성한다.
- `[결과 표 TODO]`

---

## 7. 개발 환경 및 협업 방식

- 회사 맥(Android SDK 미설치)에서는 코드 편집·리팩터링만 하고, GitHub 에 push.
- 집 윈도우 노트북에서 pull 받아 빌드·단위 테스트·모델 학습 수행.
- ML 산출물(체크포인트·데이터셋)은 `.gitignore` 로 제외, 최종 배포 모델(`photo_verifier.onnx`)만 커밋.
- 주요 커밋:
  - `7c4545d` 사진 인증 판정 로직 + `ml/` 학습 파이프라인
  - `599c31e` 사진 인증 판정을 미션 완료 흐름에 연결
  - `fc53e3a` 관리자 사진 검수 큐 + 데이터셋 구축 스크립트

---

## 8. 향후 계획

- 미션별 레퍼런스 사진 few-shot 매칭(랜드마크 정합성 강화)
- 촬영 시각·EXIF·위치 메타데이터 교차 검증, GPS 스푸핑/순간이동 탐지
- 추천 고도화: 완료 로그 기반 학습된 re-ranking 또는 컨텍스트 밴딧, 오프라인 평가(hit@k, NDCG)
- 서버 사이드 포인트 검증

---

## 9. 참고 문헌

1. `[Transfer learning survey — TODO]`
2. Mehta & Rastegari, *MobileViT*, ICLR 2022. `[정확한 서지 TODO]`
3. Radford et al., *Learning Transferable Visual Models From Natural Language Supervision (CLIP)*, ICML 2021. `[TODO]`
4. Hu et al., *LoRA: Low-Rank Adaptation of Large Language Models*, ICLR 2022. `[TODO]`
5. Zhou et al., *Places: A 10 million Image Database for Scene Recognition*, TPAMI 2017. `[TODO]`
6. Bossard et al., *Food-101 – Mining Discriminative Components with Random Forests*, ECCV 2014. `[TODO]`
