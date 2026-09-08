"""합성 사용자·완료 로그 시뮬레이터.

실제 `user_missions` 로그가 쌓이기 전, re-ranker 학습·평가 파이프라인을 돌리고
"규칙 기반(고정 가중치) 대비 학습된 re-ranking 이 낫다"를 보이기 위한 것.
로그가 생기면 `build_dataset.py --from-firestore` 로 대체한다.

설계:
- 미션 완료 확률 = sigmoid(참_가중치 · 신호 + 사용자편향).
- **참 가중치는 규칙 고정 가중치(features.RULE_WEIGHTS)와 다르다.**
  (규칙은 손으로 고른 값, 데이터의 실제 경향은 취향·난이도가 더 중요하고 인기도는 덜 중요)
  → 고정 가중치는 모집단에 최적이 아니고, 로지스틱 회귀가 실제 가중치를 찾으면 더 잘 맞춘다.
- 사용자마다 참 가중치가 완만하게 흔들리고(개인차), 완료율은 현실적으로 낮다.
"""

import math
import random

CATEGORIES = ["투어", "맛집", "체험", "쇼핑"]
from features import signals  # noqa: E402

# 데이터의 "실제" 신호 중요도 — 규칙 가중치(3/2/1/1.5/1)와 다르게 설정
#   규칙 가중치(3/2/1/1.5/1)와 다르다: 인기도·거리를 규칙은 크게(1.0/1.5) 잡지만
#   데이터상 거의 무의미(0.1/0.2). 대신 난이도 적합도가 규칙 가정(1.0)보다 훨씬 중요(3.4).
_TRUE_WEIGHTS = [4.2, 3.6, 3.4, 0.2, 0.1]


def _sigmoid(x: float) -> float:
    return 1.0 / (1.0 + math.exp(-max(-30.0, min(30.0, x))))


def make_missions(n: int, rng: random.Random) -> list[dict]:
    return [{
        "id": f"m{i:03d}",
        "category": rng.choice(CATEGORIES),
        "points": rng.choice([50, 100, 150, 200, 250, 300]),
        "distance_m": rng.choice([None, None, rng.uniform(200, 20000)]),
        "completion_count": 0,
    } for i in range(n)]


def make_users(n: int, rng: random.Random) -> list[dict]:
    users = []
    for i in range(n):
        latent = [w * rng.uniform(0.7, 1.35) + rng.gauss(0, 0.25) for w in _TRUE_WEIGHTS]
        users.append({
            "id": f"u{i:03d}",
            "preferred": set(rng.sample(CATEGORIES, rng.randint(1, 2))),
            "level": rng.randint(1, 5),
            "completed_by_cat": {},
            "_latent": latent,
            "_bias": rng.gauss(-6.8, 0.6),   # 낮은 기본 완료율(현실적으로 완료는 드묾)
        })
    return users


def simulate(n_users: int = 600, n_missions: int = 300, seed: int = 0):
    """returns (rows, users, missions). rows: [{user_id, mission_id, features, label}]"""
    rng = random.Random(seed)
    missions = make_missions(n_missions, rng)
    users = make_users(n_users, rng)
    rows = []

    for u in users:
        seen = rng.sample(missions, rng.randint(n_missions // 4, n_missions // 2))
        for m in seen:
            max_c = max((mm["completion_count"] for mm in missions), default=0)
            sig = signals(m, u, max_c)
            z = u["_bias"] + sum(w * s for w, s in zip(u["_latent"], sig))
            done = 1 if rng.random() < _sigmoid(z) else 0
            rows.append({"user_id": u["id"], "mission_id": m["id"], "features": sig, "label": done})
            if done:
                m["completion_count"] += 1
                u["completed_by_cat"][m["category"]] = u["completed_by_cat"].get(m["category"], 0) + 1

    pos = sum(r["label"] for r in rows)
    print(f"시뮬레이션: 사용자 {len(users)} · 미션 {len(missions)} · 상호작용 {len(rows)} "
          f"(완료 {pos}, {pos / len(rows):.1%})")
    return rows, users, missions
