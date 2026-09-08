"""rows.csv 로 로지스틱 회귀 re-ranker 를 학습하고 `reranker.json` 을 만든다.

산출물은 `app/src/main/assets/reranker.json` 으로 커밋한다.
사용자 단위로 train/test 를 나눠 랭킹 지표(hit@k, NDCG@k)도 함께 보고한다.
"""

import argparse
import csv
import json
import math
from collections import defaultdict
from pathlib import Path

from features import FEATURE_NAMES


def load(path: str):
    rows = []
    with open(path, newline="", encoding="utf-8") as f:
        for r in csv.DictReader(f):
            rows.append({
                "user": r["user_id"], "mission": r["mission_id"],
                "x": [float(r[n]) for n in FEATURE_NAMES], "y": int(r["label"]),
            })
    return rows


def split_by_user(rows, test_frac=0.25, seed=0):
    import random
    users = sorted({r["user"] for r in rows})
    random.Random(seed).shuffle(users)
    cut = int(len(users) * (1 - test_frac))
    train_u = set(users[:cut])
    return ([r for r in rows if r["user"] in train_u],
            [r for r in rows if r["user"] not in train_u])


def _sigmoid(z):
    return 1.0 / (1.0 + math.exp(-max(-30.0, min(30.0, z))))


def ranking_metrics(rows, score_fn, k=3):
    """사용자별로 미션을 score_fn 으로 정렬 → 상위 k 안에 실제 완료가 있는지(hit@k), NDCG@k."""
    by_user = defaultdict(list)
    for r in rows:
        by_user[r["user"]].append(r)
    hits, ndcgs, n = 0.0, 0.0, 0
    for items in by_user.values():
        if not any(i["y"] for i in items):
            continue
        n += 1
        ranked = sorted(items, key=lambda i: -score_fn(i["x"]))
        topk = ranked[:k]
        if any(i["y"] for i in topk):
            hits += 1
        dcg = sum(i["y"] / math.log2(idx + 2) for idx, i in enumerate(topk))
        ideal = sum(1 / math.log2(idx + 2) for idx in range(min(k, sum(i["y"] for i in items))))
        ndcgs += (dcg / ideal) if ideal else 0.0
    return {"users": n, f"hit@{k}": hits / n if n else 0.0, f"ndcg@{k}": ndcgs / n if n else 0.0}


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--rows", default="rows.csv")
    ap.add_argument("--out", default="../../app/src/main/assets/reranker.json")
    ap.add_argument("--blend", type=float, default=0.6, help="λ: 규칙 vs 학습 혼합 비율")
    ap.add_argument("--version", default="reranker-lr-1")
    ap.add_argument("--C", type=float, default=1.0)
    args = ap.parse_args()

    rows = load(args.rows)
    train, test = split_by_user(rows)
    print(f"train {len(train)} 행 / test {len(test)} 행 (사용자 분리)")

    from sklearn.linear_model import LogisticRegression
    from sklearn.metrics import roc_auc_score

    Xtr = [r["x"] for r in train]
    ytr = [r["y"] for r in train]
    clf = LogisticRegression(C=args.C, max_iter=1000, class_weight="balanced").fit(Xtr, ytr)
    w = clf.coef_[0].tolist()
    b = float(clf.intercept_[0])

    def learned(x):
        return _sigmoid(b + sum(wi * xi for wi, xi in zip(w, x)))

    # features.py 의 규칙 점수 (비교 기준선)
    from features import rule_score

    auc = roc_auc_score([r["y"] for r in test], [learned(r["x"]) for r in test])
    print(f"\ntest ROC-AUC (학습 모델): {auc:.3f}")
    print("가중치:", {n: round(wi, 3) for n, wi in zip(FEATURE_NAMES, w)}, "| bias:", round(b, 3))

    print("\n랭킹 지표 (test, 사용자별 미션 정렬):")
    for k in (1, 3, 5):
        rule_m = ranking_metrics(test, rule_score, k)
        learn_m = ranking_metrics(test, learned, k)
        print(f"  k={k}: 규칙  hit={rule_m[f'hit@{k}']:.3f} ndcg={rule_m[f'ndcg@{k}']:.3f}"
              f"   |  학습  hit={learn_m[f'hit@{k}']:.3f} ndcg={learn_m[f'ndcg@{k}']:.3f}")

    out = Path(args.out)
    out.parent.mkdir(parents=True, exist_ok=True)
    payload = {
        "version": args.version,
        "feature_names": FEATURE_NAMES,
        "weights": [round(x, 6) for x in w],
        "bias": round(b, 6),
        "blend": args.blend,
        "diversity_penalty": 0.15,
        "eval": {
            "test_roc_auc": round(auc, 4),
            "rule_hit@3": round(ranking_metrics(test, rule_score, 3)["hit@3"], 4),
            "learned_hit@3": round(ranking_metrics(test, learned, 3)["hit@3"], 4),
            "rule_ndcg@3": round(ranking_metrics(test, rule_score, 3)["ndcg@3"], 4),
            "learned_ndcg@3": round(ranking_metrics(test, learned, 3)["ndcg@3"], 4),
        },
    }
    out.write_text(json.dumps(payload, ensure_ascii=False, indent=2), encoding="utf-8")
    print("\n저장:", out)


if __name__ == "__main__":
    main()
