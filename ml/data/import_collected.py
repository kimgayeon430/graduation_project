"""크라우드소싱 등으로 직접 수집한 사진을 raw/<카테고리>/ 에 합류시킨다.

체험 등 공개 데이터(Places365)와 도메인이 다른 클래스를 실사진으로 보강할 때 쓴다.
합류 후에는 기존 파이프라인 그대로 `build_dataset.py` → `upload_hf.py` → 재학습.

build_dataset.py 의 place_id() 는 파일명을 "__" 기준으로 잘라 그룹을 만들고, 같은
그룹은 한 split 에만 배정한다(근접 중복 누수 방지). make_negatives.py 가 한때 이 규칙을
어겨(합성 무효 파일명이 전부 "synth__"/"degrade__" 접두사로만 겹쳐 그룹이 3~4개로
뭉치고 split 이 쏠렸던 버그, README/report 6.7.10 참고) invalid_recall 이 붕괴한 적이
있다. 여기서는 --batch 를 안 주면 파일마다 고유 placeId 를 매겨 그 문제를 원천적으로
피한다. 같은 방문에서 찍은 여러 장을 한 그룹으로 묶고 싶을 때만 --batch 를 쓰되, 값에
"__" 를 넣지 않는다(그러면 그 배치들끼리 다시 뭉친다).
"""

import argparse
from pathlib import Path

from PIL import Image, ImageOps

EXTS = {".jpg", ".jpeg", ".png", ".webp"}
CATEGORIES = ["투어", "맛집", "체험", "쇼핑"]


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--category", required=True, choices=CATEGORIES)
    parser.add_argument("--from-dir", required=True, help="수집한 원본 사진 폴더(하위 폴더 포함 탐색)")
    parser.add_argument("--raw", default="raw")
    parser.add_argument(
        "--batch", default=None,
        help="같은 방문/제출 배치의 placeId 접두사. 미지정 시 파일마다 고유 id(이미지 단위 분산). '__' 포함 금지.",
    )
    parser.add_argument("--size", type=int, default=512, help="짧은 변 리사이즈 크기")
    args = parser.parse_args()

    if args.batch and "__" in args.batch:
        raise SystemExit("--batch 에 '__' 를 쓰면 place_id 그룹 분할이 다시 뭉칩니다. 단일 '_' 만 쓰세요.")

    src_dir = Path(args.from_dir)
    out = Path(args.raw) / args.category
    out.mkdir(parents=True, exist_ok=True)

    files = sorted(p for p in src_dir.rglob("*") if p.suffix.lower() in EXTS)
    if not files:
        raise SystemExit(f"{src_dir} 에 이미지가 없습니다.")

    n = 0
    for i, p in enumerate(files):
        try:
            img = ImageOps.exif_transpose(Image.open(p)).convert("RGB")
            w, h = img.size
            scale = args.size / min(w, h)
            img = img.resize((round(w * scale), round(h * scale)))
        except Exception as e:
            print(f"  ! 건너뜀 ({p.name}): {e}")
            continue
        place_id = args.batch or f"collected{i:04d}"
        img.save(out / f"{place_id}__{i:04d}.jpg", "JPEG", quality=90)
        n += 1

    print(f"{args.category} {n}장 → {out}/  (다음: build_dataset.py 로 재분할 → upload_hf.py)")


if __name__ == "__main__":
    main()
