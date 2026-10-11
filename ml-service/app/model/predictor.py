import json
import logging
from dataclasses import dataclass
from pathlib import Path

import pandas as pd
from catboost import CatBoostClassifier, Pool

from app.data import schema
from app.model.inputs import PredictionInput
from app.model.trainer import INFO_FILE

log = logging.getLogger(__name__)


@dataclass(frozen=True)
class ModelInfo:
    version: str
    trained_at: str | None
    auc: float | None
    features: tuple[str, ...]


class Predictor:
    def __init__(self, model: CatBoostClassifier, info: ModelInfo):
        self.model = model
        self.info = info

    @classmethod
    def load(cls, model_path: Path) -> "Predictor":
        model = CatBoostClassifier()
        model.load_model(str(model_path))
        return cls(model, _read_info(model_path.parent / INFO_FILE))

    def predict_proba(self, inputs: list[PredictionInput]) -> list[float]:
        frame = pd.DataFrame([item.to_row() for item in inputs], columns=list(schema.FEATURE_COLUMNS))
        pool = Pool(frame, cat_features=list(schema.CATEGORICAL_FEATURES))
        return [float(value) for value in self.model.predict_proba(pool)[:, 1]]


def load_predictor(model_path: str | Path) -> Predictor | None:
    path = Path(model_path)
    if not path.is_file():
        log.warning("Model file %s was not found, predictions are disabled", path)
        return None
    try:
        predictor = Predictor.load(path)
    except Exception:
        log.exception("Model file %s could not be loaded, predictions are disabled", path)
        return None
    log.info("Loaded model %s from %s", predictor.info.version, path)
    return predictor


def _read_info(path: Path) -> ModelInfo:
    if not path.is_file():
        return ModelInfo(version="unknown", trained_at=None, auc=None, features=schema.FEATURE_COLUMNS)
    raw = json.loads(path.read_text(encoding="utf-8"))
    return ModelInfo(
        version=raw.get("version", "unknown"),
        trained_at=raw.get("trainedAt"),
        auc=raw.get("metrics", {}).get("auc"),
        features=tuple(raw.get("features", schema.FEATURE_COLUMNS)),
    )
