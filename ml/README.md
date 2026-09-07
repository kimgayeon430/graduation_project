# 사진 인증 모델 (Travel Mission)

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
| `data/` | 데이터셋 구축 스크립트 (공개 데이터 수집 → 무효 합성 → 장소 단위 분할 → HF Hub 업로드). `data/README.md` 참고 |
| `data/make_smoke_dataset.py` | 파이프라인 스모크 테스트용 더미 imagefolder 생성 |
| `notebooks/train_photo_verifier.ipynb` | 데이터 로드 → CLIP 제로샷 베이스라인 → 헤드 학습 → 전체 파인튜닝 → 평가 → 임계값 선정 → 모델 저장 |
| `export_onnx.py` | 파인튜닝 모델을 ONNX 로 export (+ int8 양자화, 전처리·라벨 함께 출력) |
| `requirements.txt` | 학습·평가 의존성 |
| `thresholds.json` | 노트북 7단계가 생성하는 임계값. 앱의 `PhotoVerificationConfig` 기본값으로 옮긴다 |

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
