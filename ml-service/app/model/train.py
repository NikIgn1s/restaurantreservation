import argparse
import json
from pathlib import Path

from app.model.trainer import INFO_FILE, save_artifacts, train_model

DEFAULT_OUTPUT_DIR = Path("artifacts")


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description="Train the no-show model on synthetic bookings")
    parser.add_argument("--rows", type=int, default=50000, help="number of synthetic bookings to train on")
    parser.add_argument("--seed", type=int, default=42, help="seed for data, split and model")
    parser.add_argument("--iterations", type=int, default=800, help="maximum number of trees")
    parser.add_argument("--depth", type=int, default=5, help="tree depth")
    parser.add_argument("--learning-rate", type=float, default=0.05, help="boosting learning rate")
    parser.add_argument("--out", type=Path, default=DEFAULT_OUTPUT_DIR, help="directory for the model and its report")
    return parser


def main(argv: list[str] | None = None) -> Path:
    args = build_parser().parse_args(argv)
    result = train_model(
        rows=args.rows,
        seed=args.seed,
        iterations=args.iterations,
        depth=args.depth,
        learning_rate=args.learning_rate,
    )
    model_path = save_artifacts(result, args.out)
    metrics = result.info["metrics"]
    print(
        f"Model saved to {model_path} | best iteration {result.info['bestIteration']} | "
        f"AUC {metrics['auc']} | logLoss {metrics['logLoss']} (baseline {metrics['baselineLogLoss']}) | "
        f"brier {metrics['brier']} (baseline {metrics['baselineBrier']})"
    )
    print(json.dumps(result.info["featureImportance"]))
    print(f"Report: {args.out / INFO_FILE}")
    return model_path


if __name__ == "__main__":
    main()
