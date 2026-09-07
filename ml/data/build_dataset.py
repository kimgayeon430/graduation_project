"""raw/<카테고리>/*.jpg 를 train/val/test imagefolder 로 분할한다.

- 그룹 키 = 파일명 `<placeId>__<n>.jpg` 의 placeId. (없으면 파일명 전체)
- 기본은 **장소 단위 그룹 분할**: 같은 placeId 이미지는 한 split 에만 들어간다(근접 중복 누수 방지).
- 클래스별로 그룹을 목표 비율에 맞춰 배분하되, **모든 split 에 그 클래스 이미지가 최소 1장은 들어가도록** 보정한다.
  - 그룹이 3개 미만이거나 그룹 분할로 어떤 split 이 0장이 되면, 그 클래스만 이미지 단위 랜덤 분할로 대체한다(누수 로그).
- 결정적(파일명·클래스 기반 해시).
"""

import argparse
import hashlib
import shutil
from collections import defaultdict
from pathlib import Path

CATEGORIES = ["투어", "맛집", "체험", "쇼핑", "무효"]
EXTS = {".jpg", ".jpeg", ".png", ".webp"}
SPLITS = ("train", "validation", "test")


def place_id(path: Path) -> str:
    stem = path.stem
    return stem.split("__")[0] if "__" in stem else stem


def _rank(key: str) -> float:
    return int(hashlib.md5(key.encode("utf-8")).hexdigest()[:8], 16) / 0xFFFFFFFF


def _assign_groups(category: str, groups: dict, val: float, test: float) -> dict:
    """그룹을 목표 비율에 맞춰 split 에 배분. {split: [(dst_name, src_path), ...]}"""
    total = sum(len(v) for v in groups.values())
    want = {"validation": total * val, "test": total * test}
    want["train"] = total - want["validation"] - want["test"]
    got = defaultdict(int)
    plan = {s: [] for s in SPLITS}

    # 큰 그룹부터, 가장 부족한 split 에 배정 (val·test 를 우선 채움)
    ordered = sorted(groups.items(), key=lambda kv: (-len(kv[1]), _rank(f"{category}/{kv[0]}")))
    for key, files in ordered:
        deficits = {s: want[s] - got[s] for s in SPLITS}
        # val/test 가 아직 목표 미달이면 그쪽을 먼저
        target = max(("validation", "test", "train"), key=lambda s: deficits[s])
        for i, f in enumerate(sorted(files)):
            plan[target].append((f"{key}__{i:04d}{f.suffix.lower()}", f))
        got[target] += len(files)
    return plan


def _assign_images(category: str, groups: dict, val: float, test: float) -> dict:
    """이미지 단위 랜덤 분할(그룹 누수 허용). 각 split 최소 1장 보장."""
    files = sorted((f for fs in groups.values() for f in fs),
                   key=lambda f: _rank(f"{category}/{f.name}"))
    n = len(files)
    n_val = max(1, round(n * val)) if n >= 3 else (1 if n >= 2 else 0)
    n_test = max(1, round(n * test)) if n >= 3 else 0
    plan = {s: [] for s in SPLITS}
    for i, f in enumerate(files):
        s = "validation" if i < n_val else "test" if i < n_val + n_test else "train"
        plan[s].append((f"{place_id(f)}__img{i:04d}{f.suffix.lower()}", f))
    return plan


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--raw", default="raw")
    parser.add_argument("--out", default="travel-mission-photos")
    parser.add_argument("--val", type=float, default=0.15)
    parser.add_argument("--test", type=float, default=0.15)
    args = parser.parse_args()

    raw, out = Path(args.raw), Path(args.out)
    if out.exists():
        shutil.rmtree(out)

    counts: dict = defaultdict(lambda: defaultdict(int))
    notes: list = []

    for category in CATEGORIES:
        src = raw / category
        if not src.is_dir():
            print(f"  (건너뜀) {src} 없음")
            continue
        groups: dict = defaultdict(list)
        for p in src.rglob("*"):
            if p.suffix.lower() in EXTS:
                groups[place_id(p)].append(p)
        if not groups:
            continue

        plan = _assign_groups(category, groups, args.val, args.test)
        empty = [s for s in SPLITS if not plan[s]]
        if len(groups) < 3 or empty:
            reason = f"그룹 {len(groups)}개" + (f", 그룹분할 시 {empty} 비어 이미지단위 분할" if empty else ", 이미지단위 분할")
            notes.append(f"{category}: {reason}")
            plan = _assign_images(category, groups, args.val, args.test)

        for split in SPLITS:
            dst_dir = out / split / category
            for name, srcpath in plan[split]:
                dst_dir.mkdir(parents=True, exist_ok=True)
                shutil.copy(srcpath, dst_dir / name)
                counts[split][category] += 1

    for split in SPLITS:
        print(split, {c: counts[split][c] for c in CATEGORIES if counts[split][c]})
    zero = [(s, c) for s in SPLITS for c in CATEGORIES
            if counts["train"][c] + counts["validation"][c] + counts["test"][c] > 0 and counts[s][c] == 0]
    if zero:
        print("  ! 0-클래스 split:", zero)
    for n in notes:
        print("  · ", n)
    print(f"완료 → {out}/  (다음: upload_hf.py)")


if __name__ == "__main__":
    main()
