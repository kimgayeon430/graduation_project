"""미션 대표 이미지(`missions/{id}.imageUrl`)를 CLIP 임베딩으로 사전계산해 Firestore 에 저장한다.

    # 서비스 계정 키로 Firestore 직접 갱신
    python embed_missions.py --model ../app/src/main/assets/photo_embedder.onnx \
        --firebase-key serviceAccount.json

    # 키가 없으면: 미션 목록 CSV(id,imageUrl) 를 받아 임베딩 JSON 만 뽑고 수동 반영
    python embed_missions.py --model photo_embedder.onnx --csv missions.csv --out embeddings.json

앱은 `missions/{id}.photoEmbedding`(float 배열) + `photoEmbeddingModelVersion` 을 읽어
촬영본과의 코사인 유사도를 사진 인증 보조 신호로 쓴다(보고서 6.7). 이 필드가 없는 미션은
종전대로 카테고리 규칙만 적용되므로, 점진적으로 채워도 된다.

전처리는 `photo_embedder_preprocessor.json`(CLIP)을 `similarity_probe.py` 로 재사용해
앱 `OnnxPhotoVerifier`/`OnnxClipPhotoEmbedder` 와 바이트 단위로 맞춘다.
"""

import argparse
import csv
import io
import json
from pathlib import Path
from urllib.request import urlopen

import numpy as np
import onnxruntime as ort

from similarity_probe import load_preprocess

MODEL_VERSION_DEFAULT = "clip-vit-base-patch32"


def embed_image_bytes(sess, in_name, out_name, p, data: bytes, apply_exif: bool) -> list[float]:
    # similarity_probe.preprocess 는 경로를 받으므로 임시로 메모리 파일처럼 다룬다.
    from PIL import Image, ImageOps

    img = Image.open(io.BytesIO(data))
    if apply_exif:
        img = ImageOps.exif_transpose(img)
    img = img.convert("RGB")
    # preprocess() 와 동일 로직을 직접 태운다 (경로 대신 PIL 이미지).
    scale = p["shortest"] / min(img.width, img.height)
    img = img.resize((round(img.width * scale), round(img.height * scale)), Image.BILINEAR)
    left = max((img.width - p["crop_w"]) // 2, 0)
    top = max((img.height - p["crop_h"]) // 2, 0)
    img = img.crop((left, top, left + p["crop_w"], top + p["crop_h"]))
    a = np.asarray(img, dtype=np.float32) * p["rescale"]
    if p["normalize"] and p["mean"] and p["std"]:
        a = (a - np.array(p["mean"], np.float32)) / np.array(p["std"], np.float32)
    if p["flip"]:
        a = a[:, :, ::-1]
    x = np.ascontiguousarray(a.transpose(2, 0, 1)[None])

    e = sess.run([out_name], {in_name: x})[0][0].astype(np.float64)
    e = e / (np.linalg.norm(e) + 1e-12)
    return [round(float(v), 6) for v in e]


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--model", required=True, help="photo_embedder(.onnx) — CLIP 이미지 인코더")
    ap.add_argument("--preprocessor", default=None)
    ap.add_argument("--firebase-key", default=None, help="서비스 계정 JSON. 주면 Firestore 를 직접 갱신")
    ap.add_argument("--project", default=None,
                    help="Firebase 프로젝트 id. --firebase-key 없이 ADC(gcloud auth application-default login)로 쓸 때")
    ap.add_argument("--csv", default=None, help="id,imageUrl 헤더의 CSV (키·ADC 없이 쓸 때)")
    ap.add_argument("--out", default=None, help="임베딩 결과 JSON 출력 경로")
    ap.add_argument("--version", default=None, help="photoEmbeddingModelVersion (기본: photo_embedder_version.txt)")
    ap.add_argument("--no-exif", action="store_true", help="EXIF 회전 미적용")
    ap.add_argument("--overwrite", action="store_true", help="이미 photoEmbedding 이 있어도 다시 계산")
    args = ap.parse_args()

    model_path = Path(args.model)
    assets = model_path.parent
    pp_path = Path(args.preprocessor) if args.preprocessor else assets / "photo_embedder_preprocessor.json"
    p = load_preprocess(pp_path if pp_path.is_file() else None)

    version = args.version
    if not version:
        vf = assets / "photo_embedder_version.txt"
        version = vf.read_text(encoding="utf-8").strip() if vf.is_file() else MODEL_VERSION_DEFAULT

    sess = ort.InferenceSession(model_path.read_bytes(), providers=["CPUExecutionProvider"])
    in_name = sess.get_inputs()[0].name
    out_name = sess.get_outputs()[0].name
    print(f"모델 {model_path.name}  출력 {out_name}  버전 {version}")
    print(f"전처리 shortest={p['shortest']} crop={p['crop_h']}x{p['crop_w']} "
          f"normalize={p['normalize']} flipBGR={p['flip']}  EXIF={'off' if args.no_exif else 'on'}\n")

    # ---- 미션 목록 확보 ----
    db = None
    missions: list[tuple[str, str]] = []
    if args.firebase_key or args.project:
        import firebase_admin
        from firebase_admin import credentials, firestore

        if args.firebase_key:
            firebase_admin.initialize_app(credentials.Certificate(args.firebase_key))
        else:
            # ADC: gcloud auth application-default login 으로 얻은 사용자 자격증명
            firebase_admin.initialize_app(options={"projectId": args.project})
        db = firestore.client()
        for doc in db.collection("missions").stream():
            d = doc.to_dict() or {}
            if not args.overwrite and d.get("photoEmbedding"):
                continue
            url = (d.get("imageUrl") or "").strip()
            if url:
                missions.append((doc.id, url))
    elif args.csv:
        with open(args.csv, newline="", encoding="utf-8") as f:
            for row in csv.DictReader(f):
                if row.get("imageUrl", "").strip():
                    missions.append((row["id"].strip(), row["imageUrl"].strip()))
    else:
        raise SystemExit("--firebase-key, --project(ADC), --csv 중 하나가 필요합니다.")

    print(f"대상 미션 {len(missions)}건\n")
    results: dict[str, list[float]] = {}
    for mid, url in missions:
        try:
            data = urlopen(url, timeout=20).read()
            emb = embed_image_bytes(sess, in_name, out_name, p, data, apply_exif=not args.no_exif)
        except Exception as e:  # noqa: BLE001
            print(f"  [skip] {mid}: {e}")
            continue
        results[mid] = emb
        print(f"  [ok]   {mid}  dim={len(emb)}")
        if db is not None:
            db.collection("missions").document(mid).set(
                {"photoEmbedding": emb, "photoEmbeddingModelVersion": version},
                merge=True,
            )

    if args.out:
        Path(args.out).write_text(
            json.dumps(
                {"modelVersion": version, "embeddings": results}, ensure_ascii=False, indent=2
            ),
            encoding="utf-8",
        )
        print(f"\n저장: {args.out}  ({len(results)}건)")
    if db is not None:
        print(f"\nFirestore 갱신 완료: {len(results)}건")


if __name__ == "__main__":
    main()
