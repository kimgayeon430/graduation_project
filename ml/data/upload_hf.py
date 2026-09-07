"""imagefolder 디렉터리를 HF Hub 에 비공개 데이터셋으로 push 한다.

먼저 `huggingface-cli login` (또는 HF_TOKEN 환경변수).
"""

import argparse

from datasets import load_dataset


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--dir", default="travel-mission-photos", help="train/validation/test 를 담은 imagefolder 루트")
    parser.add_argument("--repo", required=True, help="예: <user>/travel-mission-photos")
    parser.add_argument("--public", action="store_true", help="공개로 업로드 (기본: 비공개)")
    args = parser.parse_args()

    ds = load_dataset("imagefolder", data_dir=args.dir)
    print({split: len(ds[split]) for split in ds})
    print("labels:", ds["train"].features["label"].names)

    ds.push_to_hub(args.repo, private=not args.public)
    print(f"완료 → https://huggingface.co/datasets/{args.repo}")
    print("notebooks/train_photo_verifier.ipynb 의 DATASET_ID 를 이 값으로 바꾸세요.")


if __name__ == "__main__":
    main()
