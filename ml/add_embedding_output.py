"""이미 export 된 `photo_verifier.onnx` 에 임베딩 출력을 추가한다.

    python add_embedding_output.py --in  ../app/src/main/assets/photo_verifier.onnx \
                                   --out ../app/src/main/assets/photo_verifier.onnx

분류 헤드(마지막 `Gemm`/`MatMul`)의 **입력 활성값**이 곧 pooled feature(임베딩)이므로,
그 텐서를 그래프 출력으로 하나 더 선언하기만 하면 된다.

- **가중치를 건드리지 않는다.** 출력 선언만 추가하므로 파일 크기·추론 결과가 그대로다.
- 재학습·체크포인트가 필요 없다. (학습 결과는 Colab 에 있고 세션이 만료될 수 있음)
- `export_onnx.py` 를 고쳐 처음부터 두 출력을 내보내는 방법도 있으나, 이미 배포된
  모델에 소급 적용할 수 있다는 점에서 이 스크립트를 별도로 둔다.

mobilevit-small 기준 임베딩 차원은 640 이다. (`classifier.weight` = [5, 640])
"""

import argparse
from pathlib import Path

import onnx
from onnx import helper


def find_embedding_tensor(graph: onnx.GraphProto) -> tuple[str, int]:
    """분류 헤드의 입력 활성 텐서 이름과 차원을 찾는다."""
    initializers = {i.name for i in graph.initializer}
    weights = {i.name: i for i in graph.initializer}

    for node in reversed(graph.node):
        if node.op_type not in ("Gemm", "MatMul"):
            continue
        activations = [i for i in node.input if i not in initializers]
        params = [i for i in node.input if i in initializers]
        if len(activations) != 1 or not params:
            continue
        # classifier.weight 는 [num_labels, embed_dim] 이므로 마지막 축이 임베딩 차원.
        dim = int(weights[params[0]].dims[-1])
        return activations[0], dim

    raise SystemExit("분류 헤드(Gemm/MatMul)를 찾지 못했습니다.")


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--in", dest="src", required=True, help="입력 ONNX")
    parser.add_argument("--out", dest="dst", required=True, help="출력 ONNX (같은 경로면 덮어씀)")
    parser.add_argument("--name", default="embedding", help="추가할 출력 이름")
    args = parser.parse_args()

    model = onnx.load(args.src)
    graph = model.graph

    existing = {o.name for o in graph.output}
    if args.name in existing:
        print(f"이미 '{args.name}' 출력이 있습니다. 그대로 둡니다.")
        return

    tensor, dim = find_embedding_tensor(graph)
    print(f"임베딩 텐서: {tensor}  (dim={dim})")

    # 원래 이름을 유지한 채 출력으로 선언하면 되지만, 앱에서 쓰기 쉽도록 Identity 로 별칭을 만든다.
    graph.node.append(helper.make_node("Identity", inputs=[tensor], outputs=[args.name]))
    graph.output.append(
        helper.make_tensor_value_info(args.name, onnx.TensorProto.FLOAT, ["batch", dim])
    )

    onnx.checker.check_model(model)
    Path(args.dst).parent.mkdir(parents=True, exist_ok=True)
    onnx.save(model, args.dst, save_as_external_data=False)

    src_mb = Path(args.src).stat().st_size / 1024 / 1024
    dst_mb = Path(args.dst).stat().st_size / 1024 / 1024
    print(f"저장: {args.dst}")
    print(f"크기: {src_mb:.2f} MB -> {dst_mb:.2f} MB")
    print(f"출력: {[o.name for o in onnx.load(args.dst).graph.output]}")


if __name__ == "__main__":
    main()
