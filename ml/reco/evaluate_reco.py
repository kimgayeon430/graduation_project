"""규칙 기반 vs 학습된 re-ranker 비교. (보고서용)

시뮬레이션(또는 --rows) → 사용자 분리 학습 → test 사용자마다 상호작용한 미션을
(rule / learned / blend) 점수로 정렬해 완료를 relevant 로 두고 랭킹 지표를 잰다.
  - AUC: "이 미션을 완료할지" 이진 예측 성능
  - NDCG@k, hit@k, MAP: 사용자별 미션 정렬 품질

실제 로그가 생기면 `build_dataset.py --from-firestore` 로 rows.csv 를 만들고 `--rows rows.csv`.
"""

import argparse
import csv
import math
import random
from collections import defaultdict

from features import FEATURE_NAMES, rule_score
from sim import simulate


def _sigmoid(z):
    return 1.0 / (1.0 + math.exp(-max(-30.0, min(30.0, z))))


def load_rows(path):
    with open(path, newline="", encoding="utf-8") as f:
        return [{"user": r["user_id"], "mission": r["mission_id"],
                 "features": [float(r[n]) for n in FEATURE_NAMES], "label": int(r["label"])}
                for r in csv.DictReader(f)]


def rows_from_sim(n_users, n_missions, seed):
    rows, _, _ = simulate(n_users, n_missions, seed)
    return [{"user": r["user_id"], "mission": r["mission_id"],
             "features": r["features"], "label": r["label"]} for r in rows]


def split_by_user(rows, test_frac, seed):
    users = sorted({r["user"] for r in rows})
    random.Random(seed).shuffle(users)
    cut = int(len(users) * (1 - test_frac))
    tr = set(users[:cut])
    return [r for r in rows if r["user"] in tr], [r for r in rows if r["user"] not in tr]


def ranking_metrics(rows, score_fn, ks=(1, 3, 5, 10)):
    by_user = defaultdict(list)
    for r in rows:
        by_user[r["user"]].append(r)
    agg = defaultdict(float)
    n = 0
    for items in by_user.values():
        rel = sum(i["label"] for i in items)
        if rel == 0 or len(items) < 3:
            continue
        n += 1
        ranked = sorted(items, key=lambda i: -score_fn(i["features"]))
        # MAP
        hits, ap = 0, 0.0
        for idx, it in enumerate(ranked):
            if it["label"]:
                hits += 1
                ap += hits / (idx + 1)
        agg["map"] += ap / rel
        for k in ks:
            top = ranked[:k]
            agg[f"hit@{k}"] += 1.0 if any(i["label"] for i in top) else 0.0
            dcg = sum(i["label"] / math.log2(i2 + 2) for i2, i in enumerate(top))
            idcg = sum(1 / math.log2(i2 + 2) for i2 in range(min(k, rel)))
            agg[f"ndcg@{k}"] += dcg / idcg if idcg else 0.0
    return {key: v / n for key, v in agg.items()} | {"users": n}


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--rows", default=None, help="build_dataset.py 산출 CSV. 없으면 시뮬레이션")
    ap.add_argument("--users", type=int, default=800)
    ap.add_argument("--missions", type=int, default=300)
    ap.add_argument("--seed", type=int, default=0)
    ap.add_argument("--blend", type=float, default=0.5)
    ap.add_argument("--test-frac", type=float, default=0.3)
    args = ap.parse_args()

    rows = load_rows(args.rows) if args.rows else rows_from_sim(args.users, args.missions, args.seed)
    train, test = split_by_user(rows, args.test_frac, args.seed)
    print(f"train {len(train)} / test {len(test)} 행 (사용자 분리)\n")

    from sklearn.linear_model import LogisticRegression
    from sklearn.metrics import roc_auc_score

    clf = LogisticRegression(C=1.0, max_iter=1000, class_weight="balanced")
    clf.fit([r["features"] for r in train], [r["label"] for r in train])
    w, b = clf.coef_[0].tolist(), float(clf.intercept_[0])

    def learned(x):
        return _sigmoid(b + sum(wi * xi for wi, xi in zip(w, x)))

    max_rule_tr = max((rule_score(r["features"]) for r in train), default=1.0) or 1.0

    def blended(x):
        return (1 - args.blend) * rule_score(x) / max_rule_tr + args.blend * learned(x)

    yt = [r["label"] for r in test]
    print("이진 예측 (test):")
    print(f"  규칙 점수  ROC-AUC = {roc_auc_score(yt, [rule_score(r['features']) for r in test]):.3f}")
    print(f"  학습 모델  ROC-AUC = {roc_auc_score(yt, [learned(r['features']) for r in test]):.3f}")

    print("\n규칙 가중치 vs 학습 가중치:")
    rule_w = [3.0, 2.0, 1.0, 1.5, 1.0]
    for i, nm in enumerate(FEATURE_NAMES):
        print(f"  {nm:18} 규칙 {rule_w[i]:>5.2f}   학습 {w[i]:>6.2f}")
    print(f"  {'(bias)':18}               학습 {b:>6.2f}")

    print("\n사용자별 미션 정렬 (test):")
    hdr = f"{'':9}" + "".join(f"{m:>10}" for m in ("hit@3", "hit@5", "ndcg@3", "ndcg@5", "ndcg@10", "map"))
    print(hdr)
    for name, fn in (("규칙", rule_score), ("학습", learned), (f"blend λ={args.blend}", blended)):
        m = ranking_metrics(test, fn)
        print(f"{name:9}" + "".join(f"{m[k]:>10.3f}" for k in
              ("hit@3", "hit@5", "ndcg@3", "ndcg@5", "ndcg@10", "map")))
    r, bl = ranking_metrics(test, rule_score), ranking_metrics(test, blended)
    print(f"\nblend vs 규칙  NDCG@5 {bl['ndcg@5'] - r['ndcg@5']:+.3f}  ·  MAP {bl['map'] - r['map']:+.3f}")


if __name__ == "__main__":
    main()
