import hashlib
import json
from dataclasses import dataclass
from datetime import datetime, timezone
from pathlib import Path

import numpy as np
import pandas as pd
from catboost import CatBoostClassifier, Pool

from app.data import schema
from app.data.synthetic import generate_dataset
from app.model import metrics as scoring

MODEL_FILE = "no_show_model.cbm"
INFO_FILE = "model_info.json"
TRAIN_SHARE = 0.70
VALIDATION_SHARE = 0.15
CALIBRATION_EDGES = (0.0, 0.1, 0.2, 0.3, 0.4, 0.6, 1.0)


@dataclass(frozen=True)
class TrainingResult:
    model: CatBoostClassifier
    info: dict


def train_model(
    rows: int = 50000,
    seed: int = 42,
    iterations: int = 800,
    depth: int = 5,
    learning_rate: float = 0.05,
) -> TrainingResult:
    if rows < 200:
        raise ValueError("rows must be at least 200 to train and evaluate a model")

    data = generate_dataset(rows, seed=seed, include_probability=True)
    true_probability = data.pop(schema.PROBABILITY_COLUMN).to_numpy()
    labels = data.pop(schema.TARGET_COLUMN).to_numpy()
    features = data[list(schema.FEATURE_COLUMNS)]

    train_idx, validation_idx, test_idx = _split_indices(rows, seed)
    train_pool = _pool(features.iloc[train_idx], labels[train_idx])
    validation_pool = _pool(features.iloc[validation_idx], labels[validation_idx])
    test_pool = _pool(features.iloc[test_idx], labels[test_idx])

    model = CatBoostClassifier(
        iterations=iterations,
        depth=depth,
        learning_rate=learning_rate,
        loss_function="Logloss",
        eval_metric="Logloss",
        boost_from_average=True,
        random_seed=seed,
        early_stopping_rounds=40,
        allow_writing_files=False,
        verbose=False,
    )
    model.fit(train_pool, eval_set=validation_pool, use_best_model=True)

    test_labels = labels[test_idx]
    predictions = model.predict_proba(test_pool)[:, 1]
    baseline = np.full(len(test_idx), labels[train_idx].mean())

    info = {
        "trainedAt": datetime.now(timezone.utc).isoformat(),
        "seed": seed,
        "rows": {"total": rows, "train": len(train_idx), "validation": len(validation_idx), "test": len(test_idx)},
        "hyperParameters": {"iterations": iterations, "depth": depth, "learningRate": learning_rate},
        "bestIteration": int(model.get_best_iteration()),
        "features": list(schema.FEATURE_COLUMNS),
        "featureImportance": _importance(model),
        "metrics": {
            "auc": round(scoring.roc_auc(test_labels, predictions), 4),
            "logLoss": round(scoring.log_loss(test_labels, predictions), 4),
            "brier": round(scoring.brier_score(test_labels, predictions), 4),
            "baselineLogLoss": round(scoring.log_loss(test_labels, baseline), 4),
            "baselineBrier": round(scoring.brier_score(test_labels, baseline), 4),
            "noShowRate": round(float(test_labels.mean()), 4),
            "meanPrediction": round(float(predictions.mean()), 4),
            "meanAbsoluteErrorVsTrueProbability": round(float(np.abs(predictions - true_probability[test_idx]).mean()), 4),
            "calibration": scoring.calibration_table(test_labels, predictions, CALIBRATION_EDGES),
        },
    }
    return TrainingResult(model=model, info=info)


def save_artifacts(result: TrainingResult, directory: Path) -> Path:
    directory.mkdir(parents=True, exist_ok=True)
    model_path = directory / MODEL_FILE
    result.model.save_model(str(model_path))
    info = dict(result.info)
    info["version"] = hashlib.sha256(model_path.read_bytes()).hexdigest()[:12]
    (directory / INFO_FILE).write_text(json.dumps(info, indent=2), encoding="utf-8")
    return model_path


def _split_indices(rows: int, seed: int) -> tuple[np.ndarray, np.ndarray, np.ndarray]:
    order = np.random.default_rng(seed + 1).permutation(rows)
    train_end = int(rows * TRAIN_SHARE)
    validation_end = train_end + int(rows * VALIDATION_SHARE)
    return order[:train_end], order[train_end:validation_end], order[validation_end:]


def _pool(features: pd.DataFrame, labels: np.ndarray) -> Pool:
    return Pool(features, labels, cat_features=list(schema.CATEGORICAL_FEATURES))


def _importance(model: CatBoostClassifier) -> dict[str, float]:
    ranked = sorted(zip(schema.FEATURE_COLUMNS, model.get_feature_importance()), key=lambda pair: -pair[1])
    return {name: round(float(value), 2) for name, value in ranked}
