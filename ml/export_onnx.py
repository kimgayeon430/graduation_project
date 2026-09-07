"""파인튜닝한 사진 인증 모델을 ONNX 로 export 한다.

    python export_onnx.py --model <HF_repo_or_local_dir> \
        --out ../app/src/main/assets/photo_verifier.onnx --quantize

- optimum(+ optimum-onnx) 으로 이미지 분류 모델을 ONNX 로 변환한다.
- --quantize 를 주면 int8 동적 양자화본(`*_int8.onnx`)도 만든다. (온디바이스 크기 절감)
- 앱이 읽는 3개 파일을 `--out` 옆에 함께 만든다. Android `OnnxPhotoVerifier` 는 이 파일들에서
  전처리 상수·라벨·모델 버전을 읽으므로 export 결과와 자동으로 정합한다.
    photo_verifier_preprocessor.json  (HF preprocessor_config.json)
    photo_verifier_labels.json        ({"labels": [...]})
    photo_verifier_version.txt        (모델 버전 문자열)
"""

import argparse
import json
import shutil
from datetime import date
from pathlib import Path

from optimum.onnxruntime import ORTModelForImageClassification
from optimum.onnxruntime.configuration import AutoQuantizationConfig
from optimum.onnxruntime import ORTQuantizer
from transformers import AutoImageProcessor


def _model_version(model_arg: str, override: str | None) -> str:
    if override:
        return override
    # 학습 산출물 옆 thresholds.json 의 model_version 을 우선 사용
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
    parser.add_argument("--out", required=True, help="출력 ONNX 경로 (예: app/src/main/assets/photo_verifier.onnx)")
    parser.add_argument("--quantize", action="store_true", help="int8 동적 양자화본도 생성")
    parser.add_argument("--version", default=None, help="모델 버전 문자열 (기본: thresholds.json 의 model_version)")
    args = parser.parse_args()

    out_path = Path(args.out)
    out_path.parent.mkdir(parents=True, exist_ok=True)
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

    version = _model_version(args.model, args.version)
    print(f"[2/3] {out_path} 로 복사 (version: {version})")
    shutil.copy(work_dir / "model.onnx", out_path)
    shutil.copy(work_dir / "preprocessor_config.json", out_path.with_name("photo_verifier_preprocessor.json"))
    shutil.copy(work_dir / "labels.json", out_path.with_name("photo_verifier_labels.json"))
    out_path.with_name("photo_verifier_version.txt").write_text(version + "\n", encoding="utf-8")

    if args.quantize:
        print("[3/3] int8 동적 양자화")
        quantizer = ORTQuantizer.from_pretrained(work_dir)
        qconfig = AutoQuantizationConfig.arm64(is_static=False, per_channel=False)
        quantizer.quantize(save_dir=work_dir / "int8", quantization_config=qconfig)
        shutil.copy(work_dir / "int8" / "model_quantized.onnx", out_path.with_name("photo_verifier_int8.onnx"))
        print("     ->", out_path.with_name("photo_verifier_int8.onnx"))
    else:
        print("[3/3] 양자화 생략 (--quantize 로 활성화)")

    print("완료. 앱 자산:", out_path.name, "+ _preprocessor.json / _labels.json / _version.txt")
    print("      int8 양자화본을 쓰려면 photo_verifier_int8.onnx 를 photo_verifier.onnx 로 이름을 바꿔 넣는다.")


if __name__ == "__main__":
    main()
