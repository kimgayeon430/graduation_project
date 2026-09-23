"""웹에서 받은 "실제 장소 사진"으로 유사도 임계값(rescue/suspect)을 프록시 보정한다.

`calibrate_similarity.py`는 앱 실사용 로그(Firestore/logcat)가 있어야 도는데, 초기에는
표본이 거의 없다(6.7.7 관측 참고). 이 스크립트는 사용자가 실기기로 매번 찍지 않고도
Wikimedia Commons 같은 공개 사진으로 "정답/오답" 쌍을 만들어 같은 스윕 분석을 돌린다.

    python calibrate_similarity_web.py --firebase-key serviceAccount.json \
        --model /path/to/photo_embedder_int8.onnx --manifest manifest.json

manifest.json 형식 (probe 이미지 파일 경로 → 비교할 미션 id → 라벨):
    [
      {"image": "img/gyeongbokgung_0.jpg", "mission": "경복궁_투어", "label": "correct"},
      {"image": "img/hongdae_0.jpg",       "mission": "경복궁_투어", "label": "wrong"},
      ...
    ]

**한계 (보고서 6.7.6과 동일한 종류)**: 이건 사용자가 그 자리에서 찍은 사진이 아니라
인터넷에 있는 그 장소의 사진이다. 기존 "공개 scene 프록시"보다는 타겟이 정확하지만
(장면 카테고리가 아니라 그 장소 자체), 실사용 캡처만큼의 근거는 못 된다.
`correct`/`wrong`이 실사용 로그로 쌓이면 `calibrate_similarity.py`가 우선한다.
"""

import argparse
import json
import sys
from pathlib import Path

import numpy as np
import onnxruntime as ort

sys.path.insert(0, str(Path(__file__).parent))
from similarity_probe import load_preprocess
from embed_missions import embed_image_bytes
from calibrate_similarity import describe, sweep


def cosine(a: np.ndarray, b: np.ndarray) -> float:
    return float(np.dot(a, b) / (np.linalg.norm(a) * np.linalg.norm(b) + 1e-9))


def load_mission_embeddings(mission_ids: set[str], firebase_key: str | None, project: str | None):
    import firebase_admin
    from firebase_admin import credentials, firestore

    if firebase_key:
        cred = credentials.Certificate(firebase_key)
        firebase_admin.initialize_app(cred)
    else:
        firebase_admin.initialize_app(options={"projectId": project})
    db = firestore.client()

    out = {}
    for mid in mission_ids:
        doc = db.collection("missions").document(mid).get()
        d = doc.to_dict() or {}
        emb = d.get("photoEmbedding")
        if not emb:
            print(f"  [경고] {mid}: photoEmbedding 없음 — 건너뜀", file=sys.stderr)
            continue
        out[mid] = np.array(emb, dtype=np.float32)
    return out


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--model", required=True, help="photo_embedder(_int8).onnx 경로")
    ap.add_argument("--preprocessor", default=None,
                     help="기본: <model 폴더>/photo_embedder_preprocessor.json")
    ap.add_argument("--manifest", required=True, help="image/mission/label 목록 JSON")
    ap.add_argument("--image-root", default=".", help="manifest 의 image 경로 기준 디렉터리")
    ap.add_argument("--firebase-key", default=None)
    ap.add_argument("--project", default=None, help="ADC 사용 시 Firebase 프로젝트 id")
    ap.add_argument("--no-exif", action="store_true")
    args = ap.parse_args()

    manifest = json.loads(Path(args.manifest).read_text(encoding="utf-8"))
    mission_ids = {row["mission"] for row in manifest}
    print(f"대상 미션 {len(mission_ids)}개, probe {len(manifest)}건")
    mission_emb = load_mission_embeddings(mission_ids, args.firebase_key, args.project)

    model_path = Path(args.model)
    pp_path = Path(args.preprocessor) if args.preprocessor else model_path.parent / "photo_embedder_preprocessor.json"
    p = load_preprocess(pp_path if pp_path.is_file() else None)

    sess = ort.InferenceSession(str(model_path), providers=["CPUExecutionProvider"])
    in_name = sess.get_inputs()[0].name
    out_name = sess.get_outputs()[0].name

    image_root = Path(args.image_root)
    embed_cache: dict[str, np.ndarray] = {}
    correct, wrong = [], []
    rows_out = []
    for row in manifest:
        img_path = image_root / row["image"]
        mission = row["mission"]
        label = row["label"]
        if mission not in mission_emb:
            continue
        if row["image"] not in embed_cache:
            data = img_path.read_bytes()
            embed_cache[row["image"]] = np.array(
                embed_image_bytes(sess, in_name, out_name, p, data, apply_exif=not args.no_exif),
                dtype=np.float32,
            )
        sim = cosine(embed_cache[row["image"]], mission_emb[mission])
        rows_out.append({**row, "sim": sim})
        (correct if label == "correct" else wrong).append(sim)
        print(f"{label:8s} {row['image']:24s} vs {mission:12s} sim={sim:.3f}")

    describe("correct (웹 프록시 — 실제 장소 사진)", correct)
    describe("wrong (웹 프록시 — 다른 장소 사진)", wrong)
    sweep(correct, wrong)
    print("\n⚠ 웹 프록시 데이터다. 실사용 로그(calibrate_similarity.py)가 쌓이면 그쪽을 우선한다.")


if __name__ == "__main__":
    main()
