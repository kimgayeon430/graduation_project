"""무효(negatives) 클래스 표본을 만든다.

세 가지 소스를 합친다.
  1. 합성 스크린샷: 단색/그라데이션 배경 + 가짜 UI 박스 + 텍스트 (앱 스크린샷·캡처 흉내)
  2. 카테고리 이미지 열화: raw/<카테고리> 이미지를 심한 블러·과도 확대·저조도로 망가뜨린 하드 네거티브
  3. 직접 수집한 폴더(--from-dir): 셀카, 실제 스크린샷, 무관한 실내 사진 등

무효는 "미션과 무관하거나 인증으로 부적절한 사진"을 뜻한다.
"""

import argparse
import random
from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter, ImageOps

W, H = 512, 512


def synth_screenshot(rng: random.Random) -> Image.Image:
    base = Image.new("RGB", (W, H), tuple(rng.randint(230, 255) for _ in range(3)))
    draw = ImageDraw.Draw(base)
    # 상단 상태바 + 카드 몇 개로 앱 화면 흉내
    draw.rectangle([0, 0, W, rng.randint(40, 70)], fill=tuple(rng.randint(90, 160) for _ in range(3)))
    y = rng.randint(90, 130)
    for _ in range(rng.randint(2, 5)):
        h = rng.randint(50, 110)
        draw.rounded_rectangle([24, y, W - 24, y + h], radius=16,
                               fill=tuple(rng.randint(200, 245) for _ in range(3)))
        draw.text((40, y + 14), "x" * rng.randint(6, 20), fill=(80, 80, 80))
        y += h + rng.randint(14, 28)
        if y > H - 60:
            break
    return base


def degrade(img: Image.Image, rng: random.Random) -> Image.Image:
    img = ImageOps.exif_transpose(img).convert("RGB").resize((W, H))
    mode = rng.choice(["blur", "zoom", "dark", "crop"])
    if mode == "blur":
        return img.filter(ImageFilter.GaussianBlur(rng.uniform(6, 14)))
    if mode == "zoom":
        c = W // rng.choice([6, 8, 10])
        return img.crop((W // 2 - c, H // 2 - c, W // 2 + c, H // 2 + c)).resize((W, H))
    if mode == "dark":
        return Image.eval(img, lambda p: int(p * rng.uniform(0.15, 0.35)))
    off = rng.randint(W // 3, W // 2)
    return img.crop((off, 0, W, H)).resize((W, H))


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--out", default="raw/무효")
    parser.add_argument("--count", type=int, default=1200)
    parser.add_argument("--raw", default="raw", help="열화 하드네거티브 원본이 있는 raw 루트")
    parser.add_argument("--from-dir", default=None, help="직접 수집한 무효 이미지 폴더")
    args = parser.parse_args()

    out = Path(args.out)
    out.mkdir(parents=True, exist_ok=True)
    rng = random.Random(0)
    n = 0

    collected = list(Path(args.from_dir).rglob("*")) if args.from_dir else []
    collected = [p for p in collected if p.suffix.lower() in {".jpg", ".jpeg", ".png", ".webp"}]
    for i, p in enumerate(collected):
        try:
            img = ImageOps.exif_transpose(Image.open(p)).convert("RGB").resize((W, H))
            img.save(out / f"collected__{i:04d}.jpg", "JPEG", quality=90)
            n += 1
        except Exception:
            pass

    src_pool = [p for p in Path(args.raw).rglob("*.jpg") if "무효" not in p.parts]
    target = args.count
    idx = 0
    while n < target:
        idx += 1
        if rng.random() < 0.55 or not src_pool:
            synth_screenshot(rng).save(out / f"synth__{idx:04d}.jpg", "JPEG", quality=88)
        else:
            src = rng.choice(src_pool)
            try:
                degrade(Image.open(src), rng).save(out / f"degrade__{idx:04d}.jpg", "JPEG", quality=85)
            except Exception:
                continue
        n += 1

    print(f"무효 {n}장 → {out}")


if __name__ == "__main__":
    main()
