"""공개 데이터셋에서 카테고리별 원본 이미지를 raw/ 로 내려받는다.

- 맛집: food101 (train split 에서 무작위 표본)
- 투어/체험/쇼핑: places365 (scene 라벨을 place_classes.py 로 매핑)

주의:
- places365 는 용량이 크다. 스트리밍 모드로 필요한 만큼만 받는다.
- 결과 파일명은 `<placeId>__<n>.jpg` (placeId = 원본 클래스명). build_dataset.py 가 이 prefix 로 그룹 분할한다.
- 저작권/라이선스는 각 데이터셋 카드를 확인하고, 연구·교육 목적 범위에서 사용하라.
"""

import argparse
import io
import random
from collections import defaultdict
from pathlib import Path

from datasets import load_dataset
from PIL import Image

from place_classes import scene_to_category

CATEGORIES = ["투어", "맛집", "체험", "쇼핑"]


def _save(img: Image.Image, path: Path) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    img.convert("RGB").save(path, "JPEG", quality=90)


def fetch_food(out: Path, n: int) -> None:
    print(f"[food101] 맛집 {n}장")
    ds = load_dataset("food101", split="train", streaming=True).shuffle(seed=0, buffer_size=5000)
    per_label = defaultdict(int)
    saved = 0
    label_names = load_dataset("food101", split="train[:1]").features["label"].names
    for row in ds:
        if saved >= n:
            break
        label = label_names[row["label"]]
        per_label[label] += 1
        _save(row["image"], out / "맛집" / f"{label}__{per_label[label]:04d}.jpg")
        saved += 1
    print(f"  저장 {saved}")


def fetch_places(out: Path, per_category: int) -> None:
    print(f"[places365] 투어/체험/쇼핑 각 {per_category}장")
    ds = load_dataset("dpdl-benchmark/places365_train", split="train", streaming=True)
    label_names = ds.features["label"].names if hasattr(ds, "features") and ds.features else None
    counts = defaultdict(int)
    seen_idx = defaultdict(int)
    for row in ds:
        if all(counts[c] >= per_category for c in ("투어", "체험", "쇼핑")):
            break
        name = label_names[row["label"]] if label_names else str(row["label"])
        category = scene_to_category(name)
        if category is None or category == "맛집" or counts[category] >= per_category:
            continue
        seen_idx[name] += 1
        img = row["image"] if isinstance(row["image"], Image.Image) else Image.open(io.BytesIO(row["image"]["bytes"]))
        _save(img, out / category / f"{name.split('/')[-1]}__{seen_idx[name]:04d}.jpg")
        counts[category] += 1
    print("  저장", dict(counts))


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--out", default="raw")
    parser.add_argument("--per-class", type=int, default=800)
    args = parser.parse_args()

    out = Path(args.out)
    random.seed(0)
    fetch_food(out, args.per_class)
    fetch_places(out, args.per_class)
    print("완료. 다음: make_negatives.py → build_dataset.py")


if __name__ == "__main__":
    main()
