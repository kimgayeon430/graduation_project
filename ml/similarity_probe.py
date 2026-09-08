"""촬영본들의 라벨 점수와 임베딩 코사인 유사도를 뽑아 보는 실험 도구.

    python similarity_probe.py --model pv_embed.onnx --images a.jpg b.jpg c.jpg

- 전처리를 `OnnxPhotoVerifier.preprocessToNchw` 와 **바이트 단위로 같게** 재현한다.
  (짧은 변 리사이즈 → center crop → ×1/255 → RGB→BGR, 정규화 없음)
- `--no-exif` 를 주면 EXIF 회전을 적용하지 않는다. 앱의 현재 동작(`BitmapFactory` 는
  EXIF 를 무시)을 재현해 회전 유무의 영향을 비교할 수 있다.
- 모델은 `add_embedding_output.py` 로 `embedding` 출력을 추가한 것이어야 한다.
  없으면 라벨 점수만 출력한다.
"""

import argparse
import json
from pathlib import Path

import numpy as np
import onnxruntime as ort
from PIL import Image, ImageOps

DEFAULT_SHORTEST = 288
DEFAULT_CROP = 256


def load_preprocess(cfg_path: Path | None) -> dict:
    cfg = {}
    if cfg_path and cfg_path.is_file():
        cfg = json.loads(cfg_path.read_text(encoding="utf-8"))
    size = cfg.get("size") or {}
    crop = cfg.get("crop_size") or {}
    return {
        "shortest": size.get("shortest_edge") or DEFAULT_SHORTEST,
        "crop_h": crop.get("height") or DEFAULT_CROP,
        "crop_w": crop.get("width") or DEFAULT_CROP,
        "rescale": cfg.get("rescale_factor", 1.0 / 255.0) if cfg.get("do_rescale", True) else 1.0,
        # MobileViT 는 do_normalize 키가 없다 → 정규화하지 않는다 (앱과 동일한 기본값).
        "normalize": cfg.get("do_normalize", False),
        "mean": cfg.get("image_mean"),
        "std": cfg.get("image_std"),
        "flip": cfg.get("do_flip_channel_order", True),
    }


def preprocess(path: Path, p: dict, apply_exif: bool) -> np.ndarray:
    img = Image.open(path)
    if apply_exif:
        img = ImageOps.exif_transpose(img)  # EXIF Orientation 반영
    img = img.convert("RGB")

    # 1) 짧은 변을 shortest 로 (앱: Bitmap.createScaledBitmap, filter=true)
    scale = p["shortest"] / min(img.width, img.height)
    rw, rh = round(img.width * scale), round(img.height * scale)
    img = img.resize((rw, rh), Image.BILINEAR)

    # 2) 가운데 crop (앱과 같은 정수 나눗셈)
    left, top = max((rw - p["crop_w"]) // 2, 0), max((rh - p["crop_h"]) // 2, 0)
    img = img.crop((left, top, left + p["crop_w"], top + p["crop_h"]))

    a = np.asarray(img, dtype=np.float32) * p["rescale"]      # HWC, RGB
    if p["normalize"] and p["mean"] and p["std"]:
        a = (a - np.array(p["mean"], np.float32)) / np.array(p["std"], np.float32)
    if p["flip"]:
        a = a[:, :, ::-1]                                      # RGB → BGR
    return np.ascontiguousarray(a.transpose(2, 0, 1)[None])    # NCHW


def softmax(x: np.ndarray) -> np.ndarray:
    e = np.exp(x - x.max())
    return e / e.sum()


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--model", required=True)
    parser.add_argument("--images", nargs="+", required=True)
    parser.add_argument("--labels", default=None, help="photo_verifier_labels.json")
    parser.add_argument("--preprocessor", default=None, help="photo_verifier_preprocessor.json")
    parser.add_argument("--no-exif", action="store_true", help="EXIF 회전을 적용하지 않는다(앱 현재 동작)")
    args = parser.parse_args()

    model_path = Path(args.model)
    assets = model_path.parent
    labels_path = Path(args.labels) if args.labels else assets / "photo_verifier_labels.json"
    pp_path = Path(args.preprocessor) if args.preprocessor else assets / "photo_verifier_preprocessor.json"

    labels = None
    if labels_path.is_file():
        labels = json.loads(labels_path.read_text(encoding="utf-8"))["labels"]

    p = load_preprocess(pp_path if pp_path.is_file() else None)
    sess = ort.InferenceSession(model_path.read_bytes(), providers=["CPUExecutionProvider"])
    out_names = [o.name for o in sess.get_outputs()]
    has_embedding = "embedding" in out_names

    print(f"모델 출력: {out_names}")
    print(f"EXIF 회전: {'적용 안 함 (앱 현재 동작)' if args.no_exif else '적용'}")
    print(f"전처리   : shortest={p['shortest']} crop={p['crop_h']}x{p['crop_w']} "
          f"rescale={p['rescale']:.6f} normalize={p['normalize']} flipBGR={p['flip']}\n")

    names, embeddings = [], []
    for img_path in args.images:
        path = Path(img_path)
        x = preprocess(path, p, apply_exif=not args.no_exif)
        outputs = sess.run(None, {sess.get_inputs()[0].name: x})
        probs = softmax(outputs[out_names.index("logits")][0])

        print(f"[{path.name}]")
        if labels:
            ranked = sorted(zip(labels, probs), key=lambda kv: -kv[1])
            print("  " + "  ".join(f"{k} {v:.3f}" for k, v in ranked))
        else:
            print("  probs:", np.round(probs, 3).tolist())

        if has_embedding:
            e = outputs[out_names.index("embedding")][0]
            embeddings.append(e / (np.linalg.norm(e) + 1e-12))
            names.append(path.name)
        print()

    if has_embedding and len(embeddings) > 1:
        print("== 코사인 유사도 ==")
        width = max(len(n) for n in names)
        print(" " * (width + 2) + "  ".join(f"{n[:8]:>8}" for n in names))
        sim = np.array(embeddings) @ np.array(embeddings).T
        for i, n in enumerate(names):
            print(f"  {n:<{width}}" + "  ".join(f"{sim[i][j]:8.3f}" for j in range(len(names))))


if __name__ == "__main__":
    main()
