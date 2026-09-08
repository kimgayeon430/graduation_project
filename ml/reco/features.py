"""추천 신호(feature) 계산 — 앱의 `domain/MissionFeatures.kt` 와 정확히 같아야 한다.

FEATURE_NAMES 순서 = MissionFeatures.NAMES = reranker.json 의 weights 순서.
"""

FEATURE_NAMES = ["explicit_pref", "implicit_affinity", "difficulty_fit", "proximity", "popularity"]

NEAR_M = 1_000.0
FAR_M = 15_000.0


def expected_points(level: int) -> int:
    return 50 + max(1, level) * 50


def _clip01(x: float) -> float:
    return 0.0 if x < 0.0 else 1.0 if x > 1.0 else x


def signals(mission: dict, user: dict, max_completion: int) -> list[float]:
    """
    mission: {category, points, distance_m(옵션·None 가능), completion_count}
    user:    {preferred(set), completed_by_cat(dict), level}
    """
    total = sum(user["completed_by_cat"].values())
    explicit = 1.0 if mission["category"] in user["preferred"] else 0.0
    affinity = _clip01(user["completed_by_cat"].get(mission["category"], 0) / total) if total else 0.0

    exp = expected_points(user["level"])
    fit = _clip01(1.0 - abs(mission["points"] - exp) / exp)

    d = mission.get("distance_m")
    proximity = 0.0 if d is None else _clip01((FAR_M - d) / (FAR_M - NEAR_M))

    pop = 0.0 if max_completion <= 0 else _clip01(mission.get("completion_count", 0) / max_completion)

    return [explicit, affinity, fit, proximity, pop]


# 규칙 기반 점수 (domain/MissionScorer.kt 의 RecommendationWeights.DEFAULT). 비교 기준선용.
RULE_WEIGHTS = dict(explicit=3.0, implicit=2.0, difficulty=1.0, proximity=1.5, popularity=1.0)


def rule_score(sig: list[float]) -> float:
    e, a, f, p, pop = sig
    return (RULE_WEIGHTS["explicit"] * e + RULE_WEIGHTS["implicit"] * a
            + RULE_WEIGHTS["difficulty"] * f + RULE_WEIGHTS["proximity"] * p
            + RULE_WEIGHTS["popularity"] * pop)
