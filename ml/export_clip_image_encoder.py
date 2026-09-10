"""참조 이미지 임베딩용 CLIP 이미지 인코더를 ONNX 로 export 한다.

    python export_clip_image_encoder.py \
        --out ../app/src/main/assets/photo_embedder.onnx --quantize

**왜 별도 모델인가.** 사진 인증 분류기(`photo_verifier.onnx`, MobileViT)의 pooled feature 를
그대로 임베딩으로 쓰려 했으나(`add_embedding_output.py`), `embedding_separability.py` 측정에서
"같은 카테고리·다른 대상" 분리도(ROC AUC)가 ~0.60 에 그쳤다. 분류 헤드로 파인튜닝되며
클래스 판별 방향으로 붕괴한 탓이다. 같은 지표에서 CLIP 이미지 인코더는 ~0.76 으로,
대표 이미지 유사도 검증에 쓸 만한 유일한 선택지였다. (보고서 6.7.4)

- 출력은 **L2 정규화 전** 의 image embedding `[batch, 512]` (ViT-B/32). 정규화는 온디바이스와
  `embed_missions.py` 양쪽에서 한 곳씩 수행한다.
- 앱이 읽는 전처리 파일을 함께 만든다: `photo_embedder_preprocessor.json`
  (CLIP 은 224 리사이즈·center-crop·mean/std 정규화·채널 반전 없음 — MobileViT 와 다르다).
  `OnnxPhotoVerifier` 의 전처리 코드가 이 상수를 읽어 그대로 재사용한다.
- `--quantize` 로 int8 동적 양자화본(`photo_embedder_int8.onnx`)도 시도한다.
  fp32 ≈ 350MB → 앱에는 int8(≈ 90MB) 또는 추후 MobileCLIP 로 교체 권장.
"""

import argparse
import json
import shutil
from pathlib import Path

import torch
from transformers import AutoImageProcessor, CLIPModel

MODEL_ID = "openai/clip-vit-base-patch32"
CROP = 224


class _ImageEmbedder(torch.nn.Module):
    def __init__(self, clip: CLIPModel) -> None:
        super().__init__()
        self.clip = clip

    def forward(self, pixel_values: torch.Tensor) -> torch.Tensor:
        return self.clip.get_image_features(pixel_values=pixel_values)


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--model", default=MODEL_ID, help="HF CLIP repo id")
    ap.add_argument("--out", required=True)
    ap.add_argument("--quantize", action="store_true")
    args = ap.parse_args()

    out = Path(args.out)
    out.parent.mkdir(parents=True, exist_ok=True)

    print(f"[1/3] {args.model} 로드 → torch.onnx export (opset 18)")
    clip = CLIPModel.from_pretrained(args.model).eval()
    with torch.no_grad():
        torch.onnx.export(
            _ImageEmbedder(clip).eval(),
            (torch.randn(1, 3, CROP, CROP),),
            str(out),
            input_names=["pixel_values"],
            output_names=["embedding"],
            dynamic_axes={"pixel_values": {0: "batch"}, "embedding": {0: "batch"}},
            opset_version=18,
            dynamo=False,
        )
    import onnx

    onnx.save(onnx.load(str(out)), str(out), save_as_external_data=False)

    print("[2/3] 전처리 파일")
    proc = AutoImageProcessor.from_pretrained(args.model)
    proc.save_pretrained(out.parent / "_pp")
    pp = json.loads((out.parent / "_pp" / "preprocessor_config.json").read_text(encoding="utf-8"))
    shutil.rmtree(out.parent / "_pp", ignore_errors=True)
    # OnnxPhotoVerifier.parsePreprocess 가 읽는 키만 남기고 명시적으로 채운다.
    size = pp.get("size") or {}
    crop = pp.get("crop_size") or {}
    embed_pp = {
        "size": {"shortest_edge": size.get("shortest_edge", 224)},
        "crop_size": {"height": crop.get("height", CROP), "width": crop.get("width", CROP)},
        "do_rescale": pp.get("do_rescale", True),
        "rescale_factor": pp.get("rescale_factor", 1.0 / 255.0),
        "do_normalize": pp.get("do_normalize", True),
        "image_mean": pp.get("image_mean", [0.48145466, 0.4578275, 0.40821073]),
        "image_std": pp.get("image_std", [0.26862954, 0.26130258, 0.27577711]),
        "do_flip_channel_order": False,  # CLIP 은 RGB 유지
    }
    out.with_name("photo_embedder_preprocessor.json").write_text(
        json.dumps(embed_pp, ensure_ascii=False, indent=2), encoding="utf-8"
    )
    out.with_name("photo_embedder_version.txt").write_text(
        args.model.split("/")[-1] + "\n", encoding="utf-8"
    )
    print("     preprocessor:", embed_pp)

    print("[3/3] fp32 추론 검증 (bytes 로드 — 앱과 동일)")
    import numpy as np
    import onnxruntime as ort

    sess = ort.InferenceSession(out.read_bytes())
    o = sess.run(None, {"pixel_values": np.random.rand(1, 3, CROP, CROP).astype("float32")})[0]
    print("     output:", o.shape)
    assert o.ndim == 2 and o.shape[0] == 1, o.shape

    if args.quantize:
        try:
            from onnxruntime.quantization import QuantType, quantize_dynamic
            from onnxruntime.quantization.shape_inference import quant_pre_process

            pre = out.with_name("_q_pp.onnx")
            quant_pre_process(str(out), str(pre), skip_symbolic_shape=True)
            q = out.with_name("photo_embedder_int8.onnx")
            quantize_dynamic(str(pre), str(q), weight_type=QuantType.QInt8)
            pre.unlink(missing_ok=True)
            print("     int8 ->", q, f"({q.stat().st_size / 1e6:.1f} MB)")
        except Exception as e:  # noqa: BLE001
            print("     int8 실패 → fp32 사용:", repr(e))

    print(f"완료. fp32 {out.stat().st_size / 1e6:.1f} MB")


if __name__ == "__main__":
    main()
