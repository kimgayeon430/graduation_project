"""raw/<카테고리>/*.jpg 를 장소 단위로 묶어 imagefolder(train/val/test) 로 분할한다.

- 그룹 키 = 파일명의 `<placeId>__<n>.jpg` 에서 placeId. (없으면 파일명 전체)
- 같은 placeId 의 이미지는 한 split 에만 들어간다. (근접 중복 누수 방지)
- 분할은 placeId 해시로 결정적. 클래스별로 대략 목표 비율을 맞춘다.
"""

import argparse
import hashlib
import shutil
from collections import defaultdict
from pathlib import Path

CATEGORIES = ["투어", "맛집", "체험", "쇼핑", "무효"]
EXTS = {".jpg", ".jpeg", ".png", ".webp"}


def place_id(path: Path) -> str:
    stem = path.stem
    return stem.split("__")[0] if "__" in stem else stem


def bucket(key: str) -> float:
    h = hashlib.md5(key.encode("utf-8")).hexdigest()
    return int(h[:8], 16) / 0xFFFFFFFF


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--raw", default="raw")
    parser.add_argument("--out", default="travel-mission-photos")
    parser.add_argument("--val", type=float, default=0.15)
    parser.add_argument("--test", type=float, default=0.15)
    args = parser.parse_args()

    raw = Path(args.raw)
    out = Path(args.out)
    if out.exists():
        shutil.rmtree(out)

    val_hi = args.val
    test_hi = args.val + args.test
    counts: dict = defaultdict(lambda: defaultdict(int))

    for category in CATEGORIES:
        src = raw / category
        if not src.is_dir():
            print(f"  (건너뜀) {src} 없음")
            continue
        groups: dict = defaultdict(list)
        for p in src.rglob("*"):
            if p.suffix.lower() in EXTS:
                groups[place_id(p)].append(p)

        for key, files in groups.items():
            b = bucket(f"{category}/{key}")
            split = "validation" if b < val_hi else "test" if b < test_hi else "train"
            dst_dir = out / split / category
            dst_dir.mkdir(parents=True, exist_ok=True)
            for i, f in enumerate(sorted(files)):
                shutil.copy(f, dst_dir / f"{key}__{i:04d}{f.suffix.lower()}")
                counts[split][category] += 1

    for split in ("train", "validation", "test"):
        print(split, dict(counts[split]))
    print(f"완료 → {out}/  (다음: upload_hf.py)")


if __name__ == "__main__":
    main()
