# ML (Travel Mission)

앱의 두 AI 기능을 위한 학습·평가 파이프라인. Android 빌드와 분리돼 있다.

- **사진 인증** (이 문서): 미션 2단계 사진을 온디바이스 비전 모델(`apple/mobilevit-small` 파인튜닝)로 1차 판정.
- **추천 re-ranker** (`reco/README.md`): 규칙 기반 추천 점수를 완료 로그로 학습한 로지스틱 회귀로 재정렬.

---

## 사진 인증 모델

미션 2단계 **사진 인증**을 사람이 아닌 온디바이스 비전 모델이 1차 판정하도록,
HuggingFace 베이스 모델을 우리 미션 사진 데이터로 파인튜닝하는 파이프라인입니다.

- 학습은 **Colab / 로컬 GPU** 에서 수행합니다. (Android 프로젝트 빌드와 무관)
- 산출물은 ONNX 모델 1개(`photo_verifier.onnx`)와 임계값 파일(`thresholds.json`) 이며,
  각각 앱의 `app/src/main/assets/` 와 `PhotoVerificationConfig` 에 반영됩니다.
- 점수를 통과/재촬영/검수로 바꾸는 규칙은 앱의 순수 Kotlin
  [`domain/PhotoVerification`](../app/src/main/java/smu/ai/graduation_project/domain/PhotoVerification.kt) 에 있습니다.

## 구성

| 파일 | 내용 |
| --- | --- |
| `labels.json` | 분류 클래스 정의 (앱과 공유) |
| `dataset_card.md` | 데이터셋 클래스·수집 출처·분할 규칙 |
| `data/` | 사진 인증 데이터셋 구축 (공개 데이터 수집 → 무효 합성 → 분할 → HF Hub 업로드). `data/README.md` 참고 |
| `reco/` | 추천 re-ranker 학습·평가. `reco/README.md` 참고 |
| `data/make_smoke_dataset.py` | 파이프라인 스모크 테스트용 더미 imagefolder 생성 |
| `notebooks/train_photo_verifier.ipynb` | 데이터 로드 → CLIP 제로샷 베이스라인 → 헤드 학습 → 전체 파인튜닝 → 평가 → 임계값 선정 → 모델 저장 |
| `export_onnx.py` | 파인튜닝 모델을 ONNX 로 export (+ int8 양자화, 전처리·라벨 함께 출력) |
| `export_clip_image_encoder.py` | 참조 이미지 유사도용 **CLIP 이미지 인코더**를 ONNX 로 export (`photo_embedder.onnx`, int8 ≈ 89MB). 보고서 6.7.4 |
| `embedding_separability.py` | 임베딩이 "같은 카테고리 안 대상"을 구분하는지 측정 (ROC AUC). 인코더 선정·임계값 감 잡기 |
| `embed_missions.py` | 미션 대표 이미지를 CLIP 임베딩으로 사전계산해 Firestore `missions/{id}.photoEmbedding` 에 저장 |
| `calibrate_similarity.py` | 실사용 데이터(Firestore `photoVerifySimilarity` + `PhotoVerify` logcat)로 `rescue`/`suspect` 임계값 보정. 관리자 검수 결과를 정답 라벨로 씀. 보고서 6.7.6 |
| `add_embedding_output.py` | (기각) 배포된 MobileViT onnx 에 pooled feature 출력을 붙이는 스크립트. 분리도 부족으로 미채택, 실험 기록용 |
| `similarity_probe.py` | 촬영본 몇 장의 라벨 점수·임베딩 유사도를 눈으로 보는 도구 |
| `requirements.txt` | 학습·평가 의존성 |
| `thresholds.json` | 노트북 7단계가 생성하는 임계값 + 유사도 임계값. 앱의 `PhotoVerificationConfig` 기본값으로 옮긴다 |

## 노트북 환경변수

`train_photo_verifier.ipynb` 는 환경변수로 데이터셋·에폭을 바꿀 수 있습니다.

| 변수 | 기본값 | 설명 |
| --- | --- | --- |
| `TMP_DATASET_ID` | `<user>/travel-mission-photos` | HF Hub id **또는 로컬 imagefolder 경로**. 로컬 경로면 자동으로 "스모크 모드"(CPU 강제) |
| `TMP_EPOCHS_HEAD` / `TMP_EPOCHS_FULL` | 8 / 6 | 헤드 학습 · 전체 파인튜닝 에폭 |

Colab GPU 에서 실제 학습 시엔 환경변수 없이 `DATASET_ID` 상수만 바꾸면 됩니다.

## 클래스

`투어 / 맛집 / 체험 / 쇼핑` 4개 미션 카테고리 + `무효` (셀카·스크린샷·무관한 실내 등).
`labels.json` 이 단일 소스이며, 학습 라벨과 앱의 `PhotoVerification.INVALID_LABEL` 이 일치해야 합니다.

## 모델

| 단계 | 모델 | 비고 |
| --- | --- | --- |
| 베이스라인 | `openai/clip-vit-base-patch32` (또는 한국어 CLIP) | 학습 없이 제로샷. 표에 비교 기준으로만 |
| 본 모델 | `apple/mobilevit-small` (~5M 파라미터) | 온디바이스용. 헤드 학습 → 전체 파인튜닝 순으로 실험 |
| (선택) | 위 모델 + LoRA | 파라미터 효율적 파인튜닝 비교군 |

## 실행

```bash
cd ml
python -m venv .venv && .venv/bin/pip install -r requirements.txt

# 0) (선택) 파이프라인 스모크 테스트 — 더미 데이터로 노트북이 끝까지 도는지 확인
.venv/bin/python data/make_smoke_dataset.py --out /tmp/smoke --per-class 20
.venv/bin/python -m ipykernel install --user --name tm     # 최초 1회
TMP_DATASET_ID=/tmp/smoke TMP_EPOCHS_HEAD=1 TMP_EPOCHS_FULL=1 \
  .venv/bin/jupyter nbconvert --to notebook --execute \
  --ExecutePreprocessor.kernel_name=tm --output /tmp/smoke_run.ipynb \
  notebooks/train_photo_verifier.ipynb
.venv/bin/python export_onnx.py --model outputs/final \
  --out /tmp/photo_verifier.onnx --quantize

# 1) 실제 데이터셋 구축 (data/README.md 참고)
cd data
../.venv/bin/python fetch_public.py --out raw --per-class 800
../.venv/bin/python make_negatives.py --out raw/무효 --count 1200
../.venv/bin/python build_dataset.py --raw raw --out travel-mission-photos
../.venv/bin/python upload_hf.py --dir travel-mission-photos --repo <user>/travel-mission-photos
cd ..

# 2) 학습: Colab GPU 에서 notebooks/train_photo_verifier.ipynb, DATASET_ID 를 위 repo 로 교체

# 3) export → app/src/main/assets/ 에 커밋
.venv/bin/python export_onnx.py --model outputs/final \
  --out ../app/src/main/assets/photo_verifier.onnx --quantize

# 4) 참조 이미지 유사도 (보고서 6.7) — 분류 재학습과 독립
#    a. 인코더 선정 근거 재확인 (공개 scene 프록시)
.venv/bin/python embedding_separability.py \
  --model ../app/src/main/assets/photo_verifier.onnx --raw data/raw --no-exif
#    b. CLIP 이미지 인코더 export (전처리 json 은 assets 에 커밋, onnx 는 아래 c 로)
.venv/bin/python export_clip_image_encoder.py --out /tmp/emb/photo_embedder.onnx --quantize
#    c. photo_embedder_int8.onnx (≈89MB) 를 HF Hub 공개 repo
#       `kimgayeon430/travel-mission-photo-embedder` 에 업로드 (Supabase 무료 플랜은 50MB 상한)
#       앱은 최초 사용 시 resolve/main 에서 받아 캐시. assets 에 넣으면 번들로도 동작
#    d. 기존 미션 대표 이미지 임베딩 채우기
.venv/bin/python embed_missions.py --model /tmp/emb/photo_embedder.onnx \
  --firebase-key serviceAccount.json
```

## 산출 모델 규격 (Android `OnnxPhotoVerifier` 참고)

- 입력: `pixel_values`, `float32`, `[batch, 3, 256, 256]` (동적 축)
- 출력: `logits`, `[batch, 5]` — **로짓**. 앱에서 softmax 를 적용해 확률로 변환한 뒤 판정한다.
- 라벨 순서: `photo_verifier_labels.json` (= `labels.json` 의 `labels` 순서)
- 전처리 (MobileViT, `photo_verifier_preprocessor.json`):
  1. 짧은 변을 `size.shortest_edge`(288)로 리사이즈 (BILINEAR)
  2. 가운데 `crop_size`(256×256) 크롭
  3. `rescale_factor`(1/255) 곱 → 0~1
  4. `do_flip_channel_order = true` → **RGB 채널을 BGR 로 뒤집는다**
  5. `do_normalize` 없음 → mean/std 정규화 안 함

## 평가 지표 (보고서용)

- 클래스별 precision / recall / F1, confusion matrix
- **무효 사진 차단율** (invalid recall) 과 **정상 사진 오탐율** (valid → REJECT 비율)
- 카테고리 점수에 대한 PR 커브 → `autoPassThreshold` / `hardRejectThreshold` 선정
- CLIP 제로샷 vs 헤드 학습 vs 전체 파인튜닝 비교
- 온디바이스 모델 크기(MB) 와 추론 지연(ms)
