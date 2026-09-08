"""추천 신호(feature) 계산 — 앱의 `domain/MissionFeatures.kt` 와 정확히 같아야 한다.

FEATURE_NAMES 순서 = MissionFeatures.NAMES = reranker.json 의 weights 순서.
"""

FEATURE_NAMES = [
    "explicit_pref", "implicit_affinity", "difficulty_fit", "proximity", "popularity",
    "time_of_day_fit",
]

NEAR_M = 1_000.0
FAR_M = 15_000.0

# 카테고리별 "하기 좋은" 시간대(0~23시, 양끝 포함). MissionFeatures.TIME_WINDOWS 와 동일.
TIME_WINDOWS = {
    "맛집": [(11, 14), (17, 21)],
    "투어": [(9, 17)],
    "체험": [(10, 18)],
    "쇼핑": [(13, 21)],
}
TIME_DECAY_HOURS = 4.0


def expected_points(level: int) -> int:
    return 50 + max(1, level) * 50


def _clip01(x: float) -> float:
    return 0.0 if x < 0.0 else 1.0 if x > 1.0 else x


def time_of_day_fit(category: str, hour) -> float:
    """현재 시각 hour(0~23)가 category 활동 시간대에 맞는 정도 (0~1). hour 가 None 이면 0.
    시간대 안이면 1, 밖이면 가장 가까운 경계까지의 시간 차로 선형 감소(24시 순환)."""
    if hour is None:
        return 0.0
    windows = TIME_WINDOWS.get(category)
    if not windows:
        return 0.0
    if any(lo <= hour <= hi for lo, hi in windows):
        return 1.0
    edges = [e for w in windows for e in w]
    nearest = min(min(abs(hour - e), 24 - abs(hour - e)) for e in edges)
    return _clip01(1.0 - nearest / TIME_DECAY_HOURS)


def signals(mission: dict, user: dict, max_completion: int) -> list[float]:
    """
    mission: {category, points, distance_m(옵션·None 가능), completion_count}
    user:    {preferred(set), completed_by_cat(dict), level, hour(옵션·None 가능)}
    """
    total = sum(user["completed_by_cat"].values())
    explicit = 1.0 if mission["category"] in user["preferred"] else 0.0
    affinity = _clip01(user["completed_by_cat"].get(mission["category"], 0) / total) if total else 0.0

    exp = expected_points(user["level"])
    fit = _clip01(1.0 - abs(mission["points"] - exp) / exp)

    d = mission.get("distance_m")
    proximity = 0.0 if d is None else _clip01((FAR_M - d) / (FAR_M - NEAR_M))

    pop = 0.0 if max_completion <= 0 else _clip01(mission.get("completion_count", 0) / max_completion)

    time_fit = time_of_day_fit(mission["category"], user.get("hour"))

    return [explicit, affinity, fit, proximity, pop, time_fit]


# 규칙 기반 점수 (domain/MissionScorer.kt 의 RecommendationWeights.DEFAULT). 비교 기준선용.
RULE_WEIGHTS = dict(explicit=3.0, implicit=2.0, difficulty=1.0, proximity=1.5, popularity=1.0,
                    time_of_day=1.0)


def rule_score(sig: list[float]) -> float:
    e, a, f, p, pop, tod = sig
    return (RULE_WEIGHTS["explicit"] * e + RULE_WEIGHTS["implicit"] * a
            + RULE_WEIGHTS["difficulty"] * f + RULE_WEIGHTS["proximity"] * p
            + RULE_WEIGHTS["popularity"] * pop + RULE_WEIGHTS["time_of_day"] * tod)
