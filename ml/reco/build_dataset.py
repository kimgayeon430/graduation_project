"""re-ranker 학습용 (user, mission) 행을 만든다 → CSV.

기본: `sim.py` 합성 로그.
`--from-firestore export.json`: 실제 데이터.
  export 형식: {"users": [...], "missions": [...], "user_missions": [...]}  (컬렉션 문서 배열)
  (Firebase 콘솔 또는 `firebase firestore:export` 후 변환. users.mail 등 개인정보는 제외해도 됨)
"""

import argparse
import csv
import json
import random
from pathlib import Path

from features import FEATURE_NAMES, signals
from sim import simulate


def _completed_status(s: str) -> bool:
    return "완료" in (s or "") or (s or "").lower() == "completed"


def from_firestore(path: str, neg_per_pos: int = 4, seed: int = 0) -> list[dict]:
    """실제 로그. 상호작용한 미션 = user_missions 행, 완료면 positive.
    본 적 없는 미션에서 무작위로 implicit negative 를 뽑는다(비율 neg_per_pos).
    거리 신호는 과거 위치를 알 수 없어 0 으로 둔다(한계).
    """
    rng = random.Random(seed)
    data = json.loads(Path(path).read_text(encoding="utf-8"))
    missions = {m["id"]: m for m in data["missions"] if m.get("id")}
    users = {u["id"]: u for u in data["users"] if u.get("id")}

    # 미션 인기도
    comp_count: dict = {}
    per_user: dict = {}
    for um in data["user_missions"]:
        uid, mid = um.get("userId"), um.get("missionId")
        if uid not in users or mid not in missions:
            continue
        per_user.setdefault(uid, {})[mid] = _completed_status(um.get("status"))
        if _completed_status(um.get("status")):
            comp_count[mid] = comp_count.get(mid, 0) + 1
    max_c = max(comp_count.values(), default=0)

    rows = []
    for uid, seen in per_user.items():
        u = users[uid]
        done_cats: dict = {}
        for mid, done in seen.items():
            if done:
                c = missions[mid].get("category", "투어")
                done_cats[c] = done_cats.get(c, 0) + 1
        uctx = {
            "preferred": set(u.get("preferences", []) or []),
            "level": _level_int(u.get("level")),
            "completed_by_cat": done_cats,
        }

        def row(mid: str, label: int):
            m = missions[mid]
            mm = {"category": m.get("category", "투어"), "points": int(m.get("points", 0)),
                  "distance_m": None, "completion_count": comp_count.get(mid, 0)}
            return {"user_id": uid, "mission_id": mid,
                    "features": signals(mm, uctx, max_c), "label": label}

        for mid, done in seen.items():
            rows.append(row(mid, 1 if done else 0))
        unseen = [mid for mid in missions if mid not in seen]
        rng.shuffle(unseen)
        for mid in unseen[: max(1, len(seen) * neg_per_pos)]:
            rows.append(row(mid, 0))

    pos = sum(r["label"] for r in rows)
    print(f"firestore: 사용자 {len(per_user)} · 상호작용행 {len(rows)} (완료 {pos})")
    return rows


def _level_int(level) -> int:
    if isinstance(level, int):
        return level
    if isinstance(level, str):
        d = "".join(ch for ch in level if ch.isdigit())
        return int(d) if d else 1
    return 1


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--out", default="rows.csv")
    ap.add_argument("--from-firestore", default=None)
    ap.add_argument("--users", type=int, default=600)
    ap.add_argument("--missions", type=int, default=300)
    ap.add_argument("--seed", type=int, default=0)
    args = ap.parse_args()

    if args.from_firestore:
        rows = from_firestore(args.from_firestore, seed=args.seed)
    else:
        rows, _, _ = simulate(args.users, args.missions, args.seed)

    with open(args.out, "w", newline="", encoding="utf-8") as f:
        w = csv.writer(f)
        w.writerow(["user_id", "mission_id", *FEATURE_NAMES, "label"])
        for r in rows:
            w.writerow([r["user_id"], r["mission_id"], *(f"{v:.6f}" for v in r["features"]), r["label"]])
    print("저장:", args.out, f"({len(rows)} 행)")


if __name__ == "__main__":
    main()
