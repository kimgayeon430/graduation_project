# 학습된 미션 추천 re-ranker

홈 화면 추천은 규칙 기반 점수(`domain/MissionScorer`, 손으로 고른 가중치)만 썼다.
여기에 **완료 로그로 학습한 로지스틱 회귀 re-ranker** 를 얹어 하이브리드로 만든다.

```
후보 미션 → MissionFeatures 신호 5개 ─┬─→ 규칙 점수 (설명 칩·콜드스타트)
                                     └─→ 학습된 완료 확률 (reranker.json)
                        최종 = (1−λ)·규칙정규화 + λ·학습확률  → 다양성 감점 → 상위 3
```

- 신호: `explicit_pref, implicit_affinity, difficulty_fit, proximity, popularity` (0~1).
  `features.py` = 앱의 `domain/MissionFeatures.kt` 와 **정확히 같아야 한다**.
- 모델: 가중치 5 + 절편 1 (`app/src/main/assets/reranker.json`). 없으면 앱은 규칙 기반으로 폴백.
- `blend` (λ): 규칙 vs 학습 혼합. 기본 0.6.

## 파일

| 파일 | 내용 |
| --- | --- |
| `features.py` | 신호 계산 + 규칙 점수 (앱 `MissionFeatures`/`MissionScorer` 미러) |
| `sim.py` | 합성 사용자·완료 로그 시뮬레이터 (실제 로그가 없을 때) |
| `build_dataset.py` | `(user, mission, 신호, 완료여부)` 행 → `rows.csv`. `--from-firestore` 로 실제 로그도 |
| `train_reranker.py` | 로지스틱 회귀 학습 → `reranker.json` (+ AUC·랭킹 지표) |
| `evaluate_reco.py` | 규칙 vs 학습 vs blend 비교 (AUC, NDCG@k, MAP, 가중치 대조) |

## 실행

```bash
cd ml && python -m venv .venv && .venv/bin/pip install -r requirements.txt
cd reco

# 합성 데이터로 (실제 로그가 없을 때)
../.venv/bin/python build_dataset.py --users 800 --missions 300 --out rows.csv
../.venv/bin/python train_reranker.py --rows rows.csv \
    --out ../../app/src/main/assets/reranker.json --blend 0.6 --version reranker-lr-sim-2
../.venv/bin/python evaluate_reco.py

# 실제 user_missions 로그가 쌓이면
../.venv/bin/python build_dataset.py --from-firestore export.json --out rows.csv
#   export.json = {"users":[...], "missions":[...], "user_missions":[...]}
```

## 현재 모델 (`reranker-lr-sim-2`)

실제 로그가 없어 **시뮬레이터로 학습**했다. 시뮬레이터의 참 선호는 규칙 고정 가중치와 다르게
설정돼 있고(인기도·거리를 규칙은 크게 잡지만 실제로는 거의 무의미, 난이도 적합도는 규칙보다 훨씬 중요),
완료율은 현실적으로 낮게(28%) 둬서 "상위 3건" 정렬이 실제로 변별되도록 했다.

| 지표 (test, 사용자 분리) | 규칙 | 학습 | blend λ=0.6 |
| --- | ---: | ---: | ---: |
| ROC-AUC (완료 예측) | 0.950 | **0.960** | — |
| precision@3 | 0.893 | **0.960** | 0.947 |
| NDCG@5 | 0.894 | **0.958** | 0.948 |
| MRR | 0.960 | **0.988** | 0.968 |
| MAP | 0.862 | **0.896** | 0.890 |

hit@3 는 세 방식 모두 0.99+ 로 포화하므로 precision@3·NDCG·MRR 로 본다.
학습된 가중치가 규칙의 손튜닝 오류(proximity 1.5→0.21, popularity 1.0→−0.07, difficulty 1.0→2.95)를 교정한다.
`user_missions` 로그가 쌓이면 같은 파이프라인으로 재학습해 `--from-firestore` 로 교체한다.
