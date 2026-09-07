"""공개 데이터셋에서 카테고리별 원본 이미지를 raw/ 로 내려받는다.

- 맛집: Food-101 (`ethz/food101`)
- 투어/체험/쇼핑: Places365 (scene 라벨을 place_classes.py 로 매핑)

주의:
- 둘 다 스트리밍으로 필요한 만큼만 받는다. (전체 다운로드 아님)
- HF 데이터셋 id 는 버전에 따라 바뀐다. Places365 는 미러가 여럿이라 --places-dataset 로 교체 가능.
  기본값이 안 되면 `huggingface-cli` 로 로그인하거나 다른 미러를 지정하라.
- 결과 파일명은 `<placeId>__<n>.jpg` (placeId = 원본 클래스명). build_dataset.py 가 이 prefix 로 그룹 분할한다.
- 저작권/라이선스는 각 데이터셋 카드를 확인하고 연구·교육 목적 범위에서 사용하라.
"""

import argparse
import io
from collections import defaultdict
from pathlib import Path

from datasets import load_dataset, load_dataset_builder
from PIL import Image

from place_classes import scene_to_category

FOOD_DATASET = "ethz/food101"
PLACES_DATASET_DEFAULT = "ljnlonoljpiljm/places365-256px"  # 안 되면 --places-dataset 로 교체


def _label_names(dataset_id: str, split: str = "train"):
    """이미지 다운로드 없이 ClassLabel 이름만 읽는다."""
    info = load_dataset_builder(dataset_id).info
    feat = info.features
    if feat and "label" in feat and hasattr(feat["label"], "names"):
        return feat["label"].names
    return None


def _save(img: Image.Image, path: Path) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    img.convert("RGB").save(path, "JPEG", quality=90)


def _as_pil(x) -> Image.Image:
    if isinstance(x, Image.Image):
        return x
    if isinstance(x, dict) and "bytes" in x:
        return Image.open(io.BytesIO(x["bytes"]))
    raise TypeError(f"이미지 형식을 모르겠음: {type(x)}")


def fetch_food(out: Path, n: int) -> None:
    print(f"[{FOOD_DATASET}] 맛집 {n}장")
    names = _label_names(FOOD_DATASET)
    buf = max(200, min(5000, n * 10))
    ds = load_dataset(FOOD_DATASET, split="train", streaming=True).shuffle(seed=0, buffer_size=buf)
    per_label, saved = defaultdict(int), 0
    for row in ds:
        if saved >= n:
            break
        label = names[row["label"]] if names else str(row["label"])
        per_label[label] += 1
        _save(_as_pil(row["image"]), out / "맛집" / f"{label}__{per_label[label]:04d}.jpg")
        saved += 1
    print(f"  저장 {saved}")


def fetch_places(out: Path, per_category: int, dataset_id: str) -> None:
    targets = ("투어", "체험", "쇼핑")
    print(f"[{dataset_id}] {'/'.join(targets)} 각 {per_category}장")
    names = _label_names(dataset_id)
    buf = max(200, min(5000, per_category * 10))
    ds = load_dataset(dataset_id, split="train", streaming=True).shuffle(seed=0, buffer_size=buf)
    counts, seen = defaultdict(int), defaultdict(int)
    for row in ds:
        if all(counts[c] >= per_category for c in targets):
            break
        name = names[row["label"]] if names else str(row["label"])
        category = scene_to_category(name)
        if category not in targets or counts[category] >= per_category:
            continue
        seen[name] += 1
        _save(_as_pil(row["image"]), out / category / f"{name.replace(' ', '_')}__{seen[name]:04d}.jpg")
        counts[category] += 1
    print("  저장", dict(counts))
    if any(counts[c] == 0 for c in targets):
        print("  ! 일부 카테고리가 0장. place_classes.py 매핑을 늘리거나 --places-dataset 를 바꿔라.")


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--out", default="raw")
    parser.add_argument("--per-class", type=int, default=800)
    parser.add_argument("--places-dataset", default=PLACES_DATASET_DEFAULT)
    parser.add_argument("--skip-food", action="store_true")
    parser.add_argument("--skip-places", action="store_true")
    args = parser.parse_args()

    out = Path(args.out)
    if not args.skip_food:
        fetch_food(out, args.per_class)
    if not args.skip_places:
        fetch_places(out, args.per_class, args.places_dataset)
    print("완료. 다음: make_negatives.py → build_dataset.py")


if __name__ == "__main__":
    main()
