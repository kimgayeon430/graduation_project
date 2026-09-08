"""파인튜닝한 사진 인증 모델을 ONNX 로 export 한다.

    python export_onnx.py --model <HF_repo_or_local_dir> \
        --out ../app/src/main/assets/photo_verifier.onnx --quantize

- `torch.onnx` 로 직접 변환한다. (Optimum 은 Colab 등에서 diffusers/huggingface_hub 버전 충돌이 잦아 사용하지 않는다)
- 출력은 로짓 `[batch, 5]`. 앱에서 softmax 후 판정한다.
- 앱이 읽는 파일 3개를 `--out` 옆에 함께 만든다:
    photo_verifier_preprocessor.json  (HF preprocessor_config.json)
    photo_verifier_labels.json        ({"labels": [...]})
    photo_verifier_version.txt        (모델 버전 문자열)
- --quantize 를 주면 int8 동적 양자화본(`*_int8.onnx`)도 시도한다. (실패해도 fp32 는 그대로)
"""

import argparse
import json
import shutil
from datetime import date
from pathlib import Path

import torch
from transformers import AutoImageProcessor, AutoModelForImageClassification

CROP = 256


class _LogitsOnly(torch.nn.Module):
    def __init__(self, model: torch.nn.Module) -> None:
        super().__init__()
        self.model = model

    def forward(self, pixel_values: torch.Tensor) -> torch.Tensor:
        return self.model(pixel_values=pixel_values).logits


def _model_version(model_arg: str, override: str | None) -> str:
    if override:
        return override
    for cand in (Path(model_arg) / "thresholds.json", Path("thresholds.json")):
        if cand.is_file():
            try:
                v = json.loads(cand.read_text(encoding="utf-8")).get("model_version")
                if v:
                    return str(v)
            except Exception:
                pass
    return f"photo-verifier-{date.today().isoformat()}"


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--model", required=True, help="HF repo id 또는 로컬 학습 결과 디렉터리")
    parser.add_argument("--out", required=True, help="출력 ONNX 경로")
    parser.add_argument("--quantize", action="store_true", help="int8 동적 양자화본도 시도")
    parser.add_argument("--version", default=None, help="모델 버전 문자열 (기본: thresholds.json 의 model_version)")
    args = parser.parse_args()

    out_path = Path(args.out)
    out_path.parent.mkdir(parents=True, exist_ok=True)
    keep = {out_path.name, "photo_verifier_labels.json", "photo_verifier_preprocessor.json",
            "photo_verifier_version.txt", "photo_verifier_int8.onnx"}
    before = {p.name for p in out_path.parent.iterdir()}

    print(f"[1/4] {args.model} 로드 → torch.onnx export (opset 18)")
    model = AutoModelForImageClassification.from_pretrained(args.model).eval()
    with torch.no_grad():
        torch.onnx.export(
            _LogitsOnly(model).eval(),
            (torch.randn(1, 3, CROP, CROP),),
            str(out_path),
            input_names=["pixel_values"],
            output_names=["logits"],
            dynamic_axes={"pixel_values": {0: "batch"}, "logits": {0: "batch"}},
            opset_version=18,
            dynamo=False,
        )

    # 가중치가 외부 파일로 분리됐으면 단일 파일로 합친다 (앱은 bytes 로만 로드).
    import onnx

    onnx.save(onnx.load(str(out_path)), str(out_path), save_as_external_data=False)

    print("[2/4] 라벨 · 전처리 · 버전 파일")
    id2label = model.config.id2label
    labels = [id2label[i] for i in range(len(id2label))]
    out_path.with_name("photo_verifier_labels.json").write_text(
        json.dumps({"labels": labels}, ensure_ascii=False, indent=2), encoding="utf-8"
    )
    AutoImageProcessor.from_pretrained(args.model).save_pretrained(out_path.parent / "_pp")
    shutil.copy(out_path.parent / "_pp" / "preprocessor_config.json",
                out_path.with_name("photo_verifier_preprocessor.json"))
    shutil.rmtree(out_path.parent / "_pp", ignore_errors=True)
    version = _model_version(args.model, args.version)
    out_path.with_name("photo_verifier_version.txt").write_text(version + "\n", encoding="utf-8")
    print("     labels:", labels, "| version:", version)

    print("[3/4] fp32 추론 검증 (bytes 로드 — 앱과 동일)")
    import numpy as np
    import onnxruntime as ort

    sess = ort.InferenceSession(out_path.read_bytes())
    o = sess.run(None, {"pixel_values": np.random.rand(1, 3, CROP, CROP).astype("float32")})[0]
    assert o.shape == (1, len(labels)), o.shape
    print("     output:", o.shape)

    if args.quantize:
        print("[4/4] int8 동적 양자화")
        try:
            from onnxruntime.quantization import QuantType, quantize_dynamic
            from onnxruntime.quantization.shape_inference import quant_pre_process

            pp = out_path.parent / "_q_pp.onnx"
            quant_pre_process(str(out_path), str(pp), skip_symbolic_shape=True)
            quantize_dynamic(str(pp), str(out_path.with_name("photo_verifier_int8.onnx")),
                             weight_type=QuantType.QInt8)
            pp.unlink(missing_ok=True)
            print("     ->", out_path.with_name("photo_verifier_int8.onnx"))
        except Exception as e:
            print("     int8 실패 → fp32 사용:", repr(e))
    else:
        print("[4/4] 양자화 생략 (--quantize 로 활성화)")

    # export 과정에서 새로 생긴 임시 파일(외부 데이터, shape-infer 임시본 등) 정리
    for p in out_path.parent.iterdir():
        if p.name not in before and p.name not in keep and (
            p.suffix in {".data", ".onnx"} or p.name.startswith("sym_shape_infer")
        ):
            p.unlink()

    print("완료. 앱 자산:", out_path.name, "+ _preprocessor.json / _labels.json / _version.txt")


if __name__ == "__main__":
    main()
