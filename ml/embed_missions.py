"""미션 대표 이미지(`missions/{id}.imageUrl` (+ `imageUrls`))를 CLIP 임베딩으로 사전계산해 Firestore 에 저장한다.

    # 서비스 계정 키로 Firestore 직접 갱신
    python embed_missions.py --model ../app/src/main/assets/photo_embedder.onnx \
        --firebase-key serviceAccount.json

    # 키가 없으면: 미션 목록 CSV(id,imageUrl) 를 받아 임베딩 JSON 만 뽑고 수동 반영
    # 같은 id 를 여러 행에 반복하면 그 미션에 참조 이미지 여러 장을 등록하는 것과 같다.
    python embed_missions.py --model photo_embedder.onnx --csv missions.csv --out embeddings.json

앱은 `missions/{id}.photoEmbeddings`(배열, 원소는 Firestore 제약상 `{"v": [...]}` 맵) + `photoEmbeddingModelVersion` 을 읽어
촬영본과의 코사인 유사도(최대값, 보고서 6.7.8)를 사진 인증 보조 신호로 쓴다(보고서 6.7).
이 필드가 없는 미션은 종전대로 카테고리 규칙만 적용되므로, 점진적으로 채워도 된다.

**여러 장 등록**: Firestore 미션 문서에 `imageUrls`(문자열 배열, 선택)를 추가하면 `imageUrl`(기존,
단일)과 합쳐 전부 임베딩한다. 참조 이미지가 여러 장이면 각도·조명이 달라도 한 장만 닮으면
구제된다(오프라인 프로토타입에서 분리력 Youden J 0.33 → 0.47). 하위호환을 위해 첫 번째
임베딩을 `photoEmbedding`(단일, 레거시) 필드에도 그대로 쓴다.

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

    # ---- 미션별 참조 이미지 URL 목록 확보 (id -> [url, ...]) ----
    db = None
    missions: dict[str, list[str]] = {}
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
            if not args.overwrite and d.get("photoEmbeddings"):
                continue
            urls = [(d.get("imageUrl") or "").strip()]
            urls += [str(u).strip() for u in (d.get("imageUrls") or [])]
            urls = [u for u in urls if u]
            if urls:
                missions[doc.id] = urls
    elif args.csv:
        with open(args.csv, newline="", encoding="utf-8") as f:
            for row in csv.DictReader(f):
                url = row.get("imageUrl", "").strip()
                if url:
                    missions.setdefault(row["id"].strip(), []).append(url)
    else:
        raise SystemExit("--firebase-key, --project(ADC), --csv 중 하나가 필요합니다.")

    print(f"대상 미션 {len(missions)}건 (참조 이미지 {sum(len(v) for v in missions.values())}장)\n")
    results: dict[str, list[list[float]]] = {}
    for mid, urls in missions.items():
        embs: list[list[float]] = []
        for url in urls:
            try:
                data = urlopen(url, timeout=20).read()
                embs.append(embed_image_bytes(sess, in_name, out_name, p, data, apply_exif=not args.no_exif))
            except Exception as e:  # noqa: BLE001
                print(f"  [skip] {mid} ({url[:40]}...): {e}")
        if not embs:
            continue
        results[mid] = embs
        print(f"  [ok]   {mid}  {len(embs)}/{len(urls)}장  dim={len(embs[0])}")
        if db is not None:
            # Firestore 는 배열의 배열을 지원하지 않는다("Nested arrays are not allowed").
            # 원소를 {"v": [...]} 맵으로 감싼 "배열의 맵" 구조로 저장한다(배열→맵→배열은 허용).
            db.collection("missions").document(mid).set(
                {
                    "photoEmbeddings": [{"v": e} for e in embs],
                    "photoEmbedding": embs[0],  # 하위호환(레거시 단일 필드)
                    "photoEmbeddingModelVersion": version,
                },
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
