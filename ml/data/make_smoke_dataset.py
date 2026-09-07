"""파이프라인 스모크 테스트용 더미 imagefolder 데이터셋을 만든다.

실제 학습용이 아니다. 클래스별로 색·패턴·텍스트가 다른 합성 이미지를 소량 생성해
`train_photo_verifier.ipynb` 가 끝까지 도는지만 확인한다.

    python make_smoke_dataset.py --out smoke --per-class 20
    TMP_DATASET_ID=$(pwd)/smoke jupyter nbconvert --to notebook --execute ../notebooks/train_photo_verifier.ipynb
"""

import argparse
import json
import random
from pathlib import Path

from PIL import Image, ImageDraw

HERE = Path(__file__).resolve().parent
LABELS = json.loads((HERE.parent / "labels.json").read_text(encoding="utf-8"))["labels"]

# 클래스별로 구분되는 배경색(대략적). 모델이 색만 보고도 학습되게 해 스모크에서 loss 가 준다.
COLORS = {
    "투어": (70, 130, 180),
    "맛집": (200, 90, 60),
    "체험": (90, 170, 100),
    "쇼핑": (180, 140, 60),
    "무효": (120, 120, 120),
}
SIZE = 256


def make_image(label: str, seed: int) -> Image.Image:
    rng = random.Random(seed)
    base = COLORS.get(label, (128, 128, 128))
    jitter = tuple(max(0, min(255, c + rng.randint(-25, 25))) for c in base)
    img = Image.new("RGB", (SIZE, SIZE), jitter)
    draw = ImageDraw.Draw(img)
    for _ in range(rng.randint(3, 7)):
        x0, y0 = rng.randint(0, SIZE), rng.randint(0, SIZE)
        x1, y1 = x0 + rng.randint(20, 90), y0 + rng.randint(20, 90)
        shade = tuple(max(0, min(255, c + rng.randint(-40, 40))) for c in jitter)
        draw.rectangle([x0, y0, x1, y1], fill=shade)
    draw.text((12, 12), f"{label}\n#{seed}", fill=(255, 255, 255))
    return img


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--out", default="smoke")
    parser.add_argument("--per-class", type=int, default=20, help="클래스당 총 장수 (train/val/test 로 분할)")
    args = parser.parse_args()

    out = Path(args.out)
    n = args.per_class
    n_val = max(2, n // 5)
    n_test = max(2, n // 5)
    n_train = n - n_val - n_test
    splits = {"train": n_train, "validation": n_val, "test": n_test}

    seed = 0
    for label in LABELS:
        for split, count in splits.items():
            d = out / split / label
            d.mkdir(parents=True, exist_ok=True)
            for _ in range(count):
                make_image(label, seed).save(d / f"{label}_{seed:04d}.jpg", "JPEG", quality=85)
                seed += 1

    print(f"생성 완료 → {out}/  (클래스 {len(LABELS)} × {n}장, split {splits})")


if __name__ == "__main__":
    main()
