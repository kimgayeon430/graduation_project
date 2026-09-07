"""공개 데이터셋에서 카테고리별 원본 이미지를 raw/ 로 내려받는다.

- 맛집: Food-101 (`ethz/food101`) validation 스플릿 — 101개 음식 클래스 전부에서 고르게 표본
- 투어/체험/쇼핑: Places365 validation 셋 (`dpdl-benchmark/Places365-Validation`, 365클래스 × 100장, 4.5GB)
  scene 라벨(정수 인덱스)을 `places365_categories.txt` 로 이름화한 뒤 `place_classes.py` 로 카테고리 매핑

두 데이터셋 모두 **비스트리밍**으로 받는다(각 4.5GB / 1.3GB). 클래스가 골고루 들어 있는
validation 셋이라 scene 다양성이 최대로 확보된다. HF 캐시(`~/.cache/huggingface`)에 저장됨(리포 밖).

- 결과 파일명: `<sceneId>__<n>.jpg` (sceneId = 원본 scene/음식 클래스명). build_dataset.py 가 이 prefix 로 그룹 분할.
- 저작권/라이선스는 각 데이터셋 카드를 확인하고 연구·교육 목적 범위에서 사용하라.
"""

import argparse
import io
from collections import defaultdict
from pathlib import Path

from datasets import load_dataset
from PIL import Image

from place_classes import canonical_scene, scene_to_category

HERE = Path(__file__).resolve().parent
FOOD_DATASET = "ethz/food101"
PLACES_DATASET = "dpdl-benchmark/Places365-Validation"


def _places_index_to_scene() -> list[str]:
    """places365_categories.txt: '/a/airfield 0' → 인덱스 순 scene 이름 리스트."""
    lines = (HERE / "places365_categories.txt").read_text(encoding="utf-8").splitlines()
    out = [""] * len(lines)
    for line in lines:
        path, idx = line.rsplit(" ", 1)
        out[int(idx)] = path  # place_classes._norm 가 선행 '/x/' 를 떼어 준다
    return out


def _as_pil(x) -> Image.Image:
    if isinstance(x, Image.Image):
        return x
    if isinstance(x, dict) and "bytes" in x:
        return Image.open(io.BytesIO(x["bytes"]))
    raise TypeError(f"이미지 형식을 모르겠음: {type(x)}")


def _save(img: Image.Image, path: Path) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    img.convert("RGB").save(path, "JPEG", quality=90)


def fetch_food(out: Path, n: int) -> None:
    print(f"[{FOOD_DATASET}] 맛집 목표 {n}장 (validation, 101 클래스 고르게)")
    ds = load_dataset(FOOD_DATASET, split="validation")
    names = ds.features["label"].names
    per_class_cap = max(1, -(-n // len(names)) + 2)  # ceil + 여유
    per_label, saved = defaultdict(int), 0
    for row in ds.shuffle(seed=0):
        if saved >= n:
            break
        label = names[row["label"]]
        if per_label[label] >= per_class_cap:
            continue
        per_label[label] += 1
        _save(_as_pil(row["image"]), out / "맛집" / f"{label}__{per_label[label]:04d}.jpg")
        saved += 1
    print(f"  저장 {saved} (scene {len(per_label)}종)")


def fetch_places(out: Path, per_category: int) -> None:
    targets = ("투어", "체험", "쇼핑")
    print(f"[{PLACES_DATASET}] {'/'.join(targets)} 각 {per_category}장")
    idx2scene = _places_index_to_scene()
    ds = load_dataset(PLACES_DATASET, split="train")

    # scene → category 미리 계산, category 별 scene 수로 per-scene cap 산정
    scenes_by_cat = defaultdict(list)
    for i, sc in enumerate(idx2scene):
        c = scene_to_category(sc)
        if c in targets:
            scenes_by_cat[c].append(i)
    caps = {c: max(4, -(-per_category // max(1, len(scenes_by_cat[c]))) + 3) for c in targets}
    print("  카테고리별 매핑 scene 수:", {c: len(v) for c, v in scenes_by_cat.items()}, "| per-scene cap:", caps)

    label_cat = {}
    for c, idxs in scenes_by_cat.items():
        for i in idxs:
            label_cat[i] = c

    counts, seen = defaultdict(int), defaultdict(int)
    for row in ds.shuffle(seed=0):
        if all(counts[c] >= per_category for c in targets):
            break
        c = label_cat.get(row["label"])
        if c is None or counts[c] >= per_category:
            continue
        scene = canonical_scene(idx2scene[row["label"]])
        if seen[(c, scene)] >= caps[c]:
            continue
        seen[(c, scene)] += 1
        _save(_as_pil(row["image"]), out / c / f"{scene}__{seen[(c, scene)]:04d}.jpg")
        counts[c] += 1

    for c in targets:
        n_scenes = len({s for (cc, s) in seen if cc == c})
        print(f"  {c}: {counts[c]}장 / {n_scenes} scene")
        if counts[c] < per_category:
            print(f"    ! 목표 미달({per_category}). validation 셋은 scene 당 100장이라 매핑 scene 수 × 100 이 상한.")


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--out", default="raw")
    parser.add_argument("--per-class", type=int, default=1000)
    parser.add_argument("--skip-food", action="store_true")
    parser.add_argument("--skip-places", action="store_true")
    args = parser.parse_args()

    out = Path(args.out)
    if not args.skip_food:
        fetch_food(out, args.per_class)
    if not args.skip_places:
        fetch_places(out, args.per_class)
    print("완료. 다음: make_negatives.py → build_dataset.py")


if __name__ == "__main__":
    main()
