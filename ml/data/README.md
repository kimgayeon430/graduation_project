# 데이터셋 구축

`ml/dataset_card.md` 의 규칙대로 `투어 / 맛집 / 체험 / 쇼핑 / 무효` 5클래스 이미지 데이터셋을
`imagefolder` 구조로 만들고 HF Hub 에 올리는 스크립트 모음.

윈도우/Colab/맥 어디서든 Python 만 있으면 된다. (Android 빌드와 무관)

## 순서

```bash
cd ml
python -m venv .venv && .venv/bin/pip install -r requirements.txt
cd data

# 1) 공개 데이터셋에서 카테고리별 원본을 raw/ 로 내려받는다
#    Food-101 val(1.3GB) + Places365 val(4.5GB) 를 비스트리밍으로 받는다. HF 캐시에 저장됨(리포 밖).
../.venv/bin/python fetch_public.py --out raw --per-class 1000

# 2) 무효(negatives) 표본  (collected_invalid/ 폴더가 있으면 --from-dir 로 함께)
../.venv/bin/python make_negatives.py --out raw/무효 --count 1300 --raw raw

# 3) train/val/test 분할 (파일명 <sceneId>__n.jpg 의 sceneId 단위 그룹 분할)
../.venv/bin/python build_dataset.py --raw raw --out travel-mission-photos --val 0.15 --test 0.15

# 4) HF Hub 에 비공개 데이터셋으로 업로드
huggingface-cli login          # 또는  hf auth login
../.venv/bin/python upload_hf.py --dir travel-mission-photos --repo <user>/travel-mission-photos
```

이후 `ml/notebooks/train_photo_verifier.ipynb` 의 `DATASET_ID` 를 `<user>/travel-mission-photos` 로 바꾼다.

## 파일

| 파일 | 역할 |
| --- | --- |
| `place_classes.py` | Places365 scene → 카테고리 매핑 (표기 정규화, 365중 209 매핑: 투어 107·체험 53·쇼핑 28·맛집 21) |
| `places365_categories.txt` | Places365 라벨 인덱스 → scene 이름 (표준 순서, 365줄) |
| `fetch_public.py` | Food-101 val(맛집) + Places365 val(투어·체험·쇼핑) 을 scene 별로 고르게 표본 → `raw/` |
| `make_negatives.py` | 무효 표본: 스크린샷 합성 + 카테고리 이미지 열화 + 수집 폴더 병합 |
| `build_dataset.py` | sceneId 단위 그룹 분할로 `imagefolder` 생성. 그룹이 적으면 이미지 단위로 폴백, 모든 split 에 모든 클래스 보장 |
| `upload_hf.py` | `imagefolder` 디렉터리를 HF Hub 로 push |
| `make_smoke_dataset.py` | (학습용 아님) 파이프라인 스모크 테스트용 더미 데이터셋 |

## 주의

- **스트리밍 대신 validation 셋을 통째로 받는다.** Places365/Food-101 학습 셋은 클래스 순으로
  정렬돼 있어 `datasets` 스트리밍 + shuffle 로는 앞쪽 몇 개 scene 에 편중된다. validation 셋은
  scene 당 100~250장으로 작아(합쳐서 ~5.7GB) 다양성을 최대로 확보한다.
- **분할은 sceneId 단위 그룹**으로 한다. 공개 데이터는 scene/음식 클래스명을 sceneId 로 쓴다.
  크라우드소싱 사진은 `raw/<카테고리>/<장소이름>__001.jpg` 형태로 넣으면 같은 규칙으로 처리된다.
  무효는 합성이라 sceneId 그룹이 2개뿐 → 이미지 단위 분할된다(장소 누수 개념 없음).
- 얼굴이 크게 나온 사진은 무효 클래스에만 최소한으로. 공개 저장소 업로드 시 제외/블러.
- HF 데이터셋 id 는 버전에 따라 사라질 수 있다. 안 되면 다른 미러로 바꾸고
  `place_classes.py` / `places365_categories.txt` 를 그 표기에 맞춰 조정한다.
