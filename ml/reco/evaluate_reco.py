"""규칙 기반 vs 학습된 re-ranker 비교. (보고서용)

시뮬레이션(또는 --rows) → 사용자 분리 학습 → test 사용자마다 상호작용한 미션을
(rule / learned / blend) 점수로 정렬해 완료를 relevant 로 두고 랭킹 지표를 잰다.
  - AUC: "이 미션을 완료할지" 이진 예측 성능
  - hit@k / precision@k / NDCG@k / MRR / MAP: 사용자별 미션 정렬 품질
    (완료가 드물어 hit@k 는 쉽게 포화하므로 precision@k·NDCG·MRR 을 함께 본다)

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
    """사용자마다 상호작용한 미션을 점수로 정렬해 완료(label=1)를 relevant 로 두고 지표를 잰다.
    hit@k: 상위 k 안에 완료 미션이 하나라도 있으면 1
    precision@k: 상위 k 중 완료 미션 비율 (완료가 드물어 saturate 하지 않음)
    MRR: 첫 완료 미션 순위의 역수 평균
    NDCG@k / MAP: 정렬 품질
    """
    by_user = defaultdict(list)
    for r in rows:
        by_user[r["user"]].append(r)
    agg = defaultdict(float)
    n = 0
    for items in by_user.values():
        rel = sum(i["label"] for i in items)
        if rel == 0 or len(items) < 5:
            continue
        n += 1
        ranked = sorted(items, key=lambda i: -score_fn(i["features"]))
        # MAP + MRR
        hits, ap, rr = 0, 0.0, 0.0
        for idx, it in enumerate(ranked):
            if it["label"]:
                hits += 1
                ap += hits / (idx + 1)
                if hits == 1:
                    rr = 1.0 / (idx + 1)
        agg["map"] += ap / rel
        agg["mrr"] += rr
        for k in ks:
            top = ranked[:k]
            tp = sum(1 for i in top if i["label"])
            agg[f"hit@{k}"] += 1.0 if tp else 0.0
            agg[f"prec@{k}"] += tp / k
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
    ap.add_argument("--blend", type=float, default=0.6, help="reranker.json 과 동일하게 0.6")
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
    rule_w = [3.0, 2.0, 1.0, 1.5, 1.0, 1.0]
    for i, nm in enumerate(FEATURE_NAMES):
        print(f"  {nm:18} 규칙 {rule_w[i]:>5.2f}   학습 {w[i]:>6.2f}")
    print(f"  {'(bias)':18}               학습 {b:>6.2f}")

    print("\n사용자별 미션 정렬 (test):")
    cols = ("hit@3", "prec@3", "ndcg@3", "ndcg@5", "ndcg@10", "mrr", "map")
    print(f"{'':11}" + "".join(f"{c:>9}" for c in cols))
    metrics = {}
    for name, fn in (("규칙", rule_score), ("학습", learned), (f"blend λ={args.blend}", blended)):
        m = metrics[name] = ranking_metrics(test, fn)
        print(f"{name:11}" + "".join(f"{m[k]:>9.3f}" for k in cols))
    print(f"\n(정렬 평가 사용자 {metrics['규칙']['users']}명)")
    r, ln, bl = metrics["규칙"], metrics["학습"], metrics[f"blend λ={args.blend}"]
    print(f"학습 vs 규칙   NDCG@5 {ln['ndcg@5'] - r['ndcg@5']:+.3f}  ·  "
          f"MAP {ln['map'] - r['map']:+.3f}  ·  MRR {ln['mrr'] - r['mrr']:+.3f}")
    print(f"blend vs 규칙  NDCG@5 {bl['ndcg@5'] - r['ndcg@5']:+.3f}  ·  "
          f"MAP {bl['map'] - r['map']:+.3f}  ·  MRR {bl['mrr'] - r['mrr']:+.3f}")


if __name__ == "__main__":
    main()
