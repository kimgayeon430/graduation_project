"""파인튜닝한 사진 인증 모델을 ONNX 로 export 한다.

    python export_onnx.py --model <HF_repo_or_local_dir> \
        --out ../app/src/main/assets/photo_verifier.onnx

- Optimum 으로 이미지 분류 모델을 ONNX 로 변환한다.
- --quantize 를 주면 int8 동적 양자화본(`*_int8.onnx`)도 만든다. (온디바이스 크기 절감)
- 함께 나오는 `preprocessor_config.json` 의 정규화 상수(mean/std)·리사이즈 크기는
  Android 쪽 `OnnxPhotoVerifier` 전처리와 반드시 일치시켜야 한다.
"""

import argparse
import json
import shutil
from pathlib import Path

from optimum.onnxruntime import ORTModelForImageClassification
from optimum.onnxruntime.configuration import AutoQuantizationConfig
from optimum.onnxruntime import ORTQuantizer
from transformers import AutoImageProcessor


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--model", required=True, help="HF repo id 또는 로컬 학습 결과 디렉터리")
    parser.add_argument("--out", required=True, help="출력 ONNX 경로 (예: app/src/main/assets/photo_verifier.onnx)")
    parser.add_argument("--quantize", action="store_true", help="int8 동적 양자화본도 생성")
    args = parser.parse_args()

    out_path = Path(args.out)
    work_dir = out_path.parent / "_onnx_export"
    work_dir.mkdir(parents=True, exist_ok=True)

    print(f"[1/3] {args.model} → ONNX 변환")
    model = ORTModelForImageClassification.from_pretrained(args.model, export=True)
    model.save_pretrained(work_dir)
    AutoImageProcessor.from_pretrained(args.model).save_pretrained(work_dir)

    # 라벨 순서를 앱이 읽을 수 있도록 함께 저장한다.
    id2label = model.config.id2label
    labels = [id2label[i] for i in range(len(id2label))]
    (work_dir / "labels.json").write_text(
        json.dumps({"labels": labels}, ensure_ascii=False, indent=2), encoding="utf-8"
    )
    print("     labels:", labels)

    print(f"[2/3] {out_path} 로 복사")
    shutil.copy(work_dir / "model.onnx", out_path)
    shutil.copy(work_dir / "preprocessor_config.json", out_path.with_name("photo_verifier_preprocessor.json"))
    shutil.copy(work_dir / "labels.json", out_path.with_name("photo_verifier_labels.json"))

    if args.quantize:
        print("[3/3] int8 동적 양자화")
        quantizer = ORTQuantizer.from_pretrained(work_dir)
        qconfig = AutoQuantizationConfig.arm64(is_static=False, per_channel=False)
        quantizer.quantize(save_dir=work_dir / "int8", quantization_config=qconfig)
        shutil.copy(work_dir / "int8" / "model_quantized.onnx", out_path.with_name("photo_verifier_int8.onnx"))
        print("     ->", out_path.with_name("photo_verifier_int8.onnx"))
    else:
        print("[3/3] 양자화 생략 (--quantize 로 활성화)")

    print("완료. 전처리 상수(mean/std/size)는 photo_verifier_preprocessor.json 참고.")


if __name__ == "__main__":
    main()
