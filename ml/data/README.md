# 데이터셋 구축

`ml/dataset_card.md` 의 규칙대로 `투어 / 맛집 / 체험 / 쇼핑 / 무효` 5클래스 이미지 데이터셋을
`imagefolder` 구조로 만들고 HF Hub 에 올리는 스크립트 모음.

윈도우/Colab 에서 실행한다. (맥에서는 편집만)

## 순서

```bash
cd ml
python -m venv .venv && .venv/bin/pip install -r requirements.txt
cd data

# 1) 공개 데이터셋에서 카테고리별 원본을 raw/ 로 내려받는다
#    (Food-101 스트리밍은 첫 shard 다운로드 때문에 처음 수 분 느릴 수 있다)
../.venv/bin/python fetch_public.py --out raw --per-class 800
#    Places365 미러가 안 되면:  --places-dataset <다른 미러 id>

# 2) 무효(negatives) 표본을 만든다 (스크린샷 합성 + 카테고리 이미지 열화 + 직접 모은 폴더)
../.venv/bin/python make_negatives.py --out raw/무효 --count 1200 --raw raw --from-dir ./collected_invalid

# 3) raw/<카테고리>/*.jpg 를 장소 단위로 묶어 train/val/test 로 분할
../.venv/bin/python build_dataset.py --raw raw --out travel-mission-photos --val 0.15 --test 0.15

# 4) HF Hub 에 비공개 데이터셋으로 업로드
huggingface-cli login
../.venv/bin/python upload_hf.py --dir travel-mission-photos --repo <user>/travel-mission-photos
```

이후 `ml/notebooks/train_photo_verifier.ipynb` 의 `DATASET_ID` 를 `<user>/travel-mission-photos` 로 바꾼다.

## 파일

| 파일 | 역할 |
| --- | --- |
| `place_classes.py` | Places365 장면 클래스 → 우리 카테고리 매핑 (표기 정규화로 미러별 차이 흡수, 현재 365중 100 매핑) |
| `fetch_public.py` | Food-101(`ethz/food101`, 맛집), Places365(`ljnlonoljpiljm/places365-256px`, 투어·체험·쇼핑) 스트리밍 부분집합 → `raw/` |
| `make_negatives.py` | 무효 표본: 스크린샷 합성 + 카테고리 이미지 열화 + 수집 폴더 병합 |
| `build_dataset.py` | 장소(파일명 prefix) 단위 그룹 분할로 `imagefolder` 생성 |
| `upload_hf.py` | `imagefolder` 디렉터리를 HF Hub 로 push |
| `make_smoke_dataset.py` | (학습용 아님) 파이프라인 스모크 테스트용 더미 데이터셋 |

## 주의

- **분할은 장소 단위로 묶어서** 한다. `build_dataset.py` 는 파일명의 `<placeId>__<n>.jpg` 패턴에서
  `placeId` 를 그룹 키로 쓴다. 공개 데이터셋은 원본 클래스명을 placeId 로 넣어 근사한다.
  (그룹이 적으면 특정 split 이 비는 게 정상 — 실제 데이터는 클래스·장소가 많아 고르게 분포한다.)
- 크라우드소싱 사진은 `raw/<카테고리>/<장소이름>__001.jpg` 형태로 직접 넣으면 같은 규칙으로 처리된다.
- 얼굴이 크게 나온 사진은 무효 클래스에만 최소한으로. 공개 저장소 업로드 시 제외/블러.
- HF 데이터셋 id 는 버전에 따라 사라질 수 있다. Places365 미러가 안 되면 `fetch_public.py --places-dataset` 로 교체하고 `place_classes.py` 매핑을 그 미러의 라벨 표기에 맞춰 조정한다.
