"""참조 이미지 유사도 임계값(`rescue` / `suspect`)을 실측 데이터로 보정한다.

    # (A) Firestore user_missions — 관리자 검수 결과를 정답 라벨로
    python calibrate_similarity.py --firebase-key serviceAccount.json

    # (B) 기기 로그 — REJECT 구간까지 포함 (Firestore 에는 안 남음)
    adb logcat -d -s PhotoVerify:I > pv.log
    python calibrate_similarity.py --logcat pv.log --logcat-labels labels.csv

보고서 6.7.6 / 6.7.7: 현재 `PhotoVerificationConfig` 의 `similarityRescueThreshold = 0.50`,
`similaritySuspectThreshold = 0.68` 은 공개 scene 프록시로 잡은 잠정치다. 실기기 관측(무관 0.38 /
화면재촬영 0.67)만으로는 "현장에서 제대로 찍은" 사진의 유사도 분포를 모른다. 이 스크립트는 그
분포가 쌓였을 때 돌린다.

**정답 라벨**
- Firestore: `photoVerified == true` (자동 PASS 또는 관리자 승인) → 정상(correct).
  `photoReviewedAt` 있고 `photoVerified == false` → 부적합(wrong). `photoNeedsReview` 아직 true → 보류.
  ※ REJECT 는 `user_missions` 에 아무것도 안 남아 이 경로로는 안 보인다.
- logcat: `PhotoVerify` 한 줄에서 `sim` 과 `verdict` 를 파싱. 정상/부적합 판단이 없으면
  `--logcat-labels` CSV(`mission,label` — label ∈ {correct,wrong}) 로 준다. 없으면 verdict 별 분포만.

임계값 후보는 `wrong` 을 문턱 위로 올리는 비율(FPR)과 `correct` 를 문턱 위로 유지하는 비율(TPR)로
평가한다. `rescue` 는 "부적합을 즉시 거절하지 않고 구제" 하는 하한이므로 FPR 을 낮게, `suspect` 는
"닮았다고 인정" 하는 기준이므로 TPR 과 FPR 을 함께 본다.
"""

import argparse
import csv
import re
import sys
from collections import Counter
from pathlib import Path

import numpy as np

RESCUE_NOW = 0.50
SUSPECT_NOW = 0.68

LOG_RE = re.compile(
    r"mission=(?P<mission>\S+)\s+cat=(?P<cat>\S+)\s+ref=(?P<ref>\S+)\s+"
    r"match=(?P<match>[\d.]+)\s+invalid=(?P<invalid>[\d.]+)\s+"
    r"sim=(?P<sim>null|[\d.]+)\s+->\s+(?P<verdict>\w+)(?P<review>\(review\))?"
)


def from_firestore(key_path: str, project: str | None):
    import firebase_admin
    from firebase_admin import credentials, firestore

    if key_path:
        firebase_admin.initialize_app(credentials.Certificate(key_path))
    else:
        firebase_admin.initialize_app(options={"projectId": project})
    db = firestore.client()

    rows = []
    for doc in db.collection("user_missions").stream():
        d = doc.to_dict() or {}
        sim = d.get("photoVerifySimilarity")
        if sim is None:
            continue
        reviewed = d.get("photoReviewedAt") is not None
        verified = d.get("photoVerified") is True
        needs = d.get("photoNeedsReview") is True
        if needs and not reviewed:
            label = "pending"
        elif verified:
            label = "correct"
        elif reviewed and not verified:
            label = "wrong"
        else:
            label = "pending"
        rows.append({
            "mission": d.get("missionId", "?"),
            "sim": float(sim),
            "score": d.get("photoVerifyScore"),
            "label": label,
            "source": "firestore",
        })
    return rows


def from_logcat(log_path: Path, labels_path: Path | None):
    label_map = {}
    if labels_path and labels_path.is_file():
        with open(labels_path, newline="", encoding="utf-8") as f:
            for r in csv.DictReader(f):
                label_map[r["mission"].strip()] = r["label"].strip().lower()

    rows = []
    for line in log_path.read_text(encoding="utf-8", errors="ignore").splitlines():
        m = LOG_RE.search(line)
        if not m:
            continue
        if m["sim"] == "null":
            continue
        rows.append({
            "mission": m["mission"],
            "sim": float(m["sim"]),
            "score": float(m["match"]),
            "verdict": m["verdict"],
            "label": label_map.get(m["mission"], "unlabeled"),
            "source": "logcat",
        })
    return rows


def hist(values: list[float], width: int = 40, lo: float = 0.0, hi: float = 1.0) -> str:
    if not values:
        return "  (표본 없음)"
    bins = np.linspace(lo, hi, 21)
    counts, _ = np.histogram(values, bins=bins)
    peak = max(counts.max(), 1)
    out = []
    for i, c in enumerate(counts):
        bar = "█" * int(width * c / peak)
        out.append(f"  {bins[i]:.2f}-{bins[i+1]:.2f} |{bar} {c}")
    return "\n".join(out)


def describe(name: str, values: list[float]) -> None:
    if not values:
        print(f"\n[{name}] 표본 없음")
        return
    a = np.array(values)
    print(f"\n[{name}] n={len(a)}  mean={a.mean():.3f}  "
          f"min={a.min():.3f}  p10={np.quantile(a,.1):.3f}  "
          f"p50={np.quantile(a,.5):.3f}  p90={np.quantile(a,.9):.3f}  max={a.max():.3f}")
    print(hist(values))


def sweep(correct: list[float], wrong: list[float]) -> None:
    if not correct or not wrong:
        print("\n임계값 스윕: correct/wrong 표본이 둘 다 있어야 한다. (현재 "
              f"correct={len(correct)}, wrong={len(wrong)})")
        return
    c, w = np.array(correct), np.array(wrong)
    print(f"\n{'thr':>6} {'correct통과(TPR)':>16} {'wrong통과(FPR)':>16} {'youden':>8}")
    best = (None, -1)
    for thr in np.round(np.arange(0.30, 0.85, 0.02), 2):
        tpr = (c >= thr).mean()
        fpr = (w >= thr).mean()
        j = tpr - fpr
        mark = "  <- 현재 rescue" if abs(thr - RESCUE_NOW) < 1e-9 else (
            "  <- 현재 suspect" if abs(thr - SUSPECT_NOW) < 1e-9 else "")
        print(f"{thr:>6.2f} {tpr:>16.2f} {fpr:>16.2f} {j:>8.2f}{mark}")
        if j > best[1]:
            best = (thr, j)
    print(f"\n제안:")
    print(f"  rescue  ≈ wrong 통과율(FPR) ≤ 0.10 이 되는 가장 낮은 thr "
          f"(부적합 오구제 억제). 후보: {_first_below(w, 0.10):.2f}")
    print(f"  suspect ≈ Youden J 최대 지점. 후보: {best[0]:.2f} (J={best[1]:.2f})")
    print("  ※ correct 표본이 30건 미만이면 신뢰 낮음. 더 모을 것.")


def _first_below(wrong: np.ndarray, target_fpr: float) -> float:
    for thr in np.round(np.arange(0.30, 0.85, 0.01), 2):
        if (wrong >= thr).mean() <= target_fpr:
            return thr
    return 0.85


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--firebase-key", default=None)
    ap.add_argument("--project", default=None, help="ADC 사용 시 Firebase 프로젝트 id")
    ap.add_argument("--logcat", default=None, help="adb logcat -d -s PhotoVerify:I 출력 파일")
    ap.add_argument("--logcat-labels", default=None, help="mission,label(correct|wrong) CSV")
    ap.add_argument("--out", default=None, help="요약 Markdown 출력 경로")
    args = ap.parse_args()

    rows = []
    if args.firebase_key or args.project:
        rows += from_firestore(args.firebase_key, args.project)
    if args.logcat:
        rows += from_logcat(Path(args.logcat),
                            Path(args.logcat_labels) if args.logcat_labels else None)
    if not rows:
        raise SystemExit("--firebase-key/--project 또는 --logcat 중 하나가 필요합니다.")

    print(f"관측 {len(rows)}건  (firestore {sum(r['source']=='firestore' for r in rows)}, "
          f"logcat {sum(r['source']=='logcat' for r in rows)})")
    print("라벨 분포:", dict(Counter(r["label"] for r in rows)))
    if any("verdict" in r for r in rows):
        print("verdict 분포:", dict(Counter(r.get("verdict", "-") for r in rows)))

    correct = [r["sim"] for r in rows if r["label"] == "correct"]
    wrong = [r["sim"] for r in rows if r["label"] == "wrong"]
    unlabeled = [r["sim"] for r in rows if r["label"] in ("unlabeled", "pending")]

    describe("correct (정상 — 자동 PASS · 관리자 승인)", correct)
    describe("wrong (부적합 — 관리자 반려)", wrong)
    describe("unlabeled / pending", unlabeled)

    # verdict 별 (logcat)
    for v in ("Reject", "Proceed"):
        vs = [r["sim"] for r in rows if r.get("verdict") == v]
        if vs:
            describe(f"verdict={v}", vs)

    print(f"\n현재 임계값: rescue={RESCUE_NOW}  suspect={SUSPECT_NOW}  "
          f"(PhotoVerificationConfig / ml/thresholds.json)")
    sweep(correct, wrong)

    if args.out:
        # 재현 가능한 원자료만 덤프 (판단은 사람이)
        with open(args.out, "w", encoding="utf-8") as f:
            f.write("# 유사도 임계값 보정 원자료\n\n")
            f.write("| source | mission | label | verdict | sim | s[c] |\n|---|---|---|---|---:|---:|\n")
            for r in sorted(rows, key=lambda r: r["sim"]):
                f.write(f"| {r['source']} | {r['mission']} | {r['label']} | "
                        f"{r.get('verdict','-')} | {r['sim']:.3f} | "
                        f"{r['score'] if r.get('score') is not None else '-'} |\n")
        print(f"\n원자료 저장: {args.out}")


if __name__ == "__main__":
    main()
