"""참조 이미지 임베딩이 "같은 카테고리 안에서 대상을 구분" 할 수 있는지 검증한다.

    python embedding_separability.py --model ../app/src/main/assets/photo_verifier.onnx \
        --raw data/raw --per-scene 8 --scenes 20

보고서 6.7.4 의 전제 — "촬영본과 대표 이미지의 임베딩 코사인 유사도로 대상 동일성을 본다" —
는 **임베딩이 유형(category)보다 세밀한 구분력을 가질 때만** 성립한다.
분류 헤드로 파인튜닝된 pooled feature 는 클래스 판별 방향으로 붕괴했을 수 있어,
같은 '투어' 안에서 경복궁 사진과 남산타워 사진이 임베딩까지 비슷해질 위험이 있다.

이 스크립트는 그 위험을 공개 데이터로 미리 잰다. `data/raw/<카테고리>/<scene>__NNNN.jpg`
에서 **같은 scene = 같은 대상**(positive), **같은 카테고리·다른 scene = 다른 대상**(hard negative)
으로 보고, 코사인 유사도 분포와 분리도(ROC AUC)를 카테고리별로 낸다.

판정 기준(대략):
  - AUC ≥ 0.75 이고 pos-neg 평균차 ≥ 0.10  → MobileViT 임베딩 그대로 진행
  - 그 미만                                → 임베딩 모델을 CLIP 이미지 인코더로 교체 검토

`--model` 은 `logits`/`embedding` 2-출력 모델이면 그 출력을, 아니면
`add_embedding_output.py` 로 임베딩 출력을 임시로 붙여 쓴다.
전처리는 `similarity_probe.py` 와 동일(= 앱 `OnnxPhotoVerifier.preprocessToNchw`).
"""

import argparse
import random
from collections import defaultdict
from pathlib import Path

import numpy as np
import onnx
import onnxruntime as ort
from onnx import helper

from similarity_probe import load_preprocess, preprocess

INVALID = "무효"


def ensure_embedding_output(model_path: Path) -> tuple[bytes, bool]:
    """모델에 `embedding` 출력이 없으면 분류 헤드 입력 활성값을 출력으로 붙인 bytes 를 만든다."""
    model = onnx.load(str(model_path))
    if any(o.name == "embedding" for o in model.graph.output):
        return model_path.read_bytes(), True

    g = model.graph
    inits = {i.name for i in g.initializer}
    weights = {i.name: i for i in g.initializer}
    for node in reversed(g.node):
        if node.op_type not in ("Gemm", "MatMul"):
            continue
        acts = [i for i in node.input if i not in inits]
        params = [i for i in node.input if i in inits]
        if len(acts) != 1 or not params:
            continue
        dim = int(weights[params[0]].dims[-1])
        g.node.append(helper.make_node("Identity", inputs=[acts[0]], outputs=["embedding"]))
        g.output.append(
            helper.make_tensor_value_info("embedding", onnx.TensorProto.FLOAT, ["batch", dim])
        )
        onnx.checker.check_model(model)
        return model.SerializeToString(), False
    raise SystemExit("분류 헤드(Gemm/MatMul)를 찾지 못해 임베딩 출력을 붙일 수 없습니다.")


def scene_of(path: Path) -> str:
    return path.name.rsplit("__", 1)[0]


def embed_dir(
    sess: ort.InferenceSession,
    out_names: list[str],
    p: dict,
    files: list[Path],
    apply_exif: bool,
) -> np.ndarray:
    in_name = sess.get_inputs()[0].name
    emb_idx = out_names.index("embedding")
    vecs = []
    for f in files:
        x = preprocess(f, p, apply_exif=apply_exif)
        e = sess.run(None, {in_name: x})[emb_idx][0].astype(np.float64)
        vecs.append(e / (np.linalg.norm(e) + 1e-12))
    return np.array(vecs)


def roc_auc(pos: np.ndarray, neg: np.ndarray) -> float:
    """pos 점수가 neg 보다 큰 쌍의 비율 (Mann–Whitney U). 클수록 분리 잘 됨."""
    if len(pos) == 0 or len(neg) == 0:
        return float("nan")
    wins = sum((pos[:, None] > neg[None, :]).sum() for _ in [0])
    ties = (pos[:, None] == neg[None, :]).sum()
    return (wins + 0.5 * ties) / (len(pos) * len(neg))


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--model", required=True)
    ap.add_argument("--raw", default="data/raw", help="<카테고리>/<scene>__NNNN.jpg 루트")
    ap.add_argument("--preprocessor", default=None)
    ap.add_argument("--scenes", type=int, default=20, help="카테고리당 표본 scene 수")
    ap.add_argument("--per-scene", type=int, default=8, help="scene 당 표본 이미지 수")
    ap.add_argument("--pairs", type=int, default=4000, help="카테고리당 negative 쌍 상한")
    ap.add_argument("--no-exif", action="store_true", help="EXIF 회전 미적용(앱 현재 동작 재현)")
    ap.add_argument("--seed", type=int, default=0)
    args = ap.parse_args()

    random.seed(args.seed)
    np.random.seed(args.seed)

    model_path = Path(args.model)
    assets = model_path.parent
    pp_path = Path(args.preprocessor) if args.preprocessor else assets / "photo_verifier_preprocessor.json"
    p = load_preprocess(pp_path if pp_path.is_file() else None)

    model_bytes, had_output = ensure_embedding_output(model_path)
    sess = ort.InferenceSession(model_bytes, providers=["CPUExecutionProvider"])
    out_names = [o.name for o in sess.get_outputs()]
    if "embedding" not in out_names:
        raise SystemExit(f"모델 출력에 embedding 이 없습니다: {out_names}")

    print(f"모델      : {model_path.name}  (embedding 출력 {'내장' if had_output else '임시 부착'})")
    print(f"전처리    : shortest={p['shortest']} crop={p['crop_h']}x{p['crop_w']} "
          f"normalize={p['normalize']} flipBGR={p['flip']}")
    print(f"EXIF 회전 : {'미적용 (앱 현재 동작)' if args.no_exif else '적용'}")
    print(f"표본      : 카테고리당 scene {args.scenes} × 이미지 {args.per_scene}\n")

    raw = Path(args.raw)
    categories = sorted(d.name for d in raw.iterdir() if d.is_dir() and d.name != INVALID)

    all_pos, all_neg = [], []
    print(f"{'카테고리':<8} {'pos평균':>8} {'neg평균':>8} {'차이':>7} {'AUC':>7} {'겹침@neg95':>10}")
    print("-" * 52)
    for cat in categories:
        by_scene: dict[str, list[Path]] = defaultdict(list)
        for f in sorted((raw / cat).glob("*.jpg")):
            by_scene[scene_of(f)].append(f)
        scenes = [s for s, fs in by_scene.items() if len(fs) >= 2]
        random.shuffle(scenes)
        scenes = scenes[: args.scenes]
        if len(scenes) < 2:
            print(f"{cat:<8}  scene 부족({len(scenes)}) — 건너뜀")
            continue

        emb: dict[str, np.ndarray] = {}
        for s in scenes:
            fs = by_scene[s][:]
            random.shuffle(fs)
            emb[s] = embed_dir(sess, out_names, p, fs[: args.per_scene], apply_exif=not args.no_exif)

        pos, neg = [], []
        for s in scenes:
            v = emb[s]
            for i in range(len(v)):
                for j in range(i + 1, len(v)):
                    pos.append(float(v[i] @ v[j]))
        others = scenes[:]
        for _ in range(args.pairs):
            a, b = random.sample(others, 2)
            va, vb = emb[a], emb[b]
            neg.append(float(va[random.randrange(len(va))] @ vb[random.randrange(len(vb))]))

        pos, neg = np.array(pos), np.array(neg)
        auc = roc_auc(pos, neg)
        thr95 = np.quantile(neg, 0.95)
        # 같은 대상인데 neg 상위 5% 문턱을 못 넘는 비율 = 유사도 문턱으로 걸러질 때의 미검출
        miss = float((pos < thr95).mean())
        print(f"{cat:<8} {pos.mean():>8.3f} {neg.mean():>8.3f} {pos.mean()-neg.mean():>7.3f} "
              f"{auc:>7.3f} {miss:>10.1%}")
        all_pos.append(pos)
        all_neg.append(neg)

    if all_pos:
        pos = np.concatenate(all_pos)
        neg = np.concatenate(all_neg)
        print("-" * 52)
        print(f"{'전체':<8} {pos.mean():>8.3f} {neg.mean():>8.3f} {pos.mean()-neg.mean():>7.3f} "
              f"{roc_auc(pos, neg):>7.3f}")
        gap = pos.mean() - neg.mean()
        auc = roc_auc(pos, neg)
        print()
        if auc >= 0.75 and gap >= 0.10:
            print("→ 판정: 이 임베딩에 카테고리보다 세밀한 구분력이 있음. 보조/구제 신호로 진행 가능.")
        elif auc >= 0.65:
            print("→ 판정: 경계선. 보조 신호로는 쓸 수 있으나 임계값 보정을 신중히. 더 큰 인코더와 비교 권장.")
        else:
            print("→ 판정: 구분력 부족. 다른 임베딩 인코더(CLIP 계열) 검토.")
        print("  (공개 scene 기준 프록시다. 실제 미션 사진 vs 대표 이미지로 재확인할 것)")


if __name__ == "__main__":
    main()
