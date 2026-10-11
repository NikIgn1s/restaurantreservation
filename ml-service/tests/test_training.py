import json
import re

import numpy as np
import pandas as pd

from app.data import schema
from app.model import train as train_cli
from app.model.inputs import parse_prediction_input
from app.model.predictor import Predictor
from app.model.trainer import INFO_FILE, MODEL_FILE, save_artifacts, train_model

import pytest

RISKY = {"dayOfWeek": 6, "hour": 22, "leadTimeHours": 400, "partySize": 6, "venueType": "SPORT",
         "weather": "SNOW", "userNoShowRate": 0.45, "userBookingsCount": 10, "temperatureC": -3}
SAFE = {"dayOfWeek": 2, "hour": 13, "leadTimeHours": 3, "partySize": 2, "venueType": "COWORKING",
        "weather": "CLEAR", "userNoShowRate": 0.03, "userBookingsCount": 30, "temperatureC": 20}


def test_rows_are_split_into_train_validation_and_test(trained):
    rows = trained.info["rows"]

    assert rows["total"] == 20000
    assert rows["train"] + rows["validation"] + rows["test"] == rows["total"]
    assert rows["train"] == 14000
    assert rows["validation"] == 3000
    assert rows["test"] == 3000


def test_model_beats_the_base_rate_baseline(trained):
    metrics = trained.info["metrics"]

    assert metrics["logLoss"] < metrics["baselineLogLoss"]
    assert metrics["brier"] < metrics["baselineBrier"]


def test_model_ranks_risky_bookings_above_safe_ones(trained):
    assert trained.info["metrics"]["auc"] > 0.60


def test_predictions_are_calibrated_on_average(trained):
    metrics = trained.info["metrics"]

    assert abs(metrics["meanPrediction"] - metrics["noShowRate"]) < 0.03


def test_predictions_are_close_to_the_true_probability(trained):
    assert trained.info["metrics"]["meanAbsoluteErrorVsTrueProbability"] < 0.08


def test_calibration_table_covers_every_test_row(trained):
    table = trained.info["metrics"]["calibration"]

    assert sum(row["count"] for row in table) == trained.info["rows"]["test"]


def test_user_history_is_among_the_most_important_features(trained):
    importance = trained.info["featureImportance"]

    assert set(importance) == set(schema.FEATURE_COLUMNS)
    assert "user_no_show_rate" in list(importance)[:3]
    assert list(importance.values()) == sorted(importance.values(), reverse=True)


def test_training_stops_early_instead_of_using_every_tree(trained):
    assert 0 < trained.info["bestIteration"] < 300


def test_info_records_how_the_model_was_built(trained):
    assert trained.info["seed"] == 11
    assert trained.info["features"] == list(schema.FEATURE_COLUMNS)
    assert trained.info["hyperParameters"]["depth"] == 5
    assert trained.info["trainedAt"].endswith("+00:00")


def test_artifacts_are_written_with_a_content_version(model_dir):
    info = json.loads((model_dir / INFO_FILE).read_text(encoding="utf-8"))

    assert (model_dir / MODEL_FILE).is_file()
    assert re.fullmatch(r"[0-9a-f]{12}", info["version"])


def test_saved_model_gives_the_same_predictions_after_reload(trained, model_dir):
    reloaded = Predictor.load(model_dir / MODEL_FILE)
    inputs = [parse_prediction_input(RISKY), parse_prediction_input(SAFE)]
    frame = pd.DataFrame([item.to_row() for item in inputs], columns=list(schema.FEATURE_COLUMNS))
    from catboost import Pool

    original = trained.model.predict_proba(Pool(frame, cat_features=list(schema.CATEGORICAL_FEATURES)))[:, 1]

    np.testing.assert_allclose(reloaded.predict_proba(inputs), original)


def test_risky_booking_scores_much_higher_than_a_safe_one(model_dir):
    predictor = Predictor.load(model_dir / MODEL_FILE)

    risky, safe = predictor.predict_proba([parse_prediction_input(RISKY), parse_prediction_input(SAFE)])

    assert risky > safe + 0.3
    assert 0.0 < safe < risky < 1.0


def test_training_is_reproducible():
    first = train_model(rows=1500, iterations=40, seed=5)
    second = train_model(rows=1500, iterations=40, seed=5)

    assert first.info["metrics"] == second.info["metrics"]
    assert first.info["featureImportance"] == second.info["featureImportance"]


def test_different_seeds_give_different_models():
    first = train_model(rows=1500, iterations=40, seed=5)
    second = train_model(rows=1500, iterations=40, seed=6)

    assert first.info["metrics"] != second.info["metrics"]


def test_too_few_rows_are_refused():
    with pytest.raises(ValueError):
        train_model(rows=100)


def test_training_leaves_no_catboost_work_directory_behind(tmp_path, monkeypatch):
    monkeypatch.chdir(tmp_path)

    train_model(rows=1000, iterations=20, seed=3)

    assert list(tmp_path.iterdir()) == []


def test_cli_trains_and_writes_the_artifacts(tmp_path, capsys):
    out = tmp_path / "artifacts"

    path = train_cli.main(["--rows", "1200", "--iterations", "30", "--seed", "4", "--out", str(out)])

    assert path == out / MODEL_FILE
    assert path.is_file()
    assert (out / INFO_FILE).is_file()
    printed = capsys.readouterr().out
    assert "AUC" in printed
    assert "user_no_show_rate" in printed


def test_saving_twice_into_the_same_directory_overwrites_cleanly(tmp_path):
    result = train_model(rows=1000, iterations=20, seed=3)

    first = save_artifacts(result, tmp_path)
    second = save_artifacts(result, tmp_path)

    assert first == second
    assert sorted(item.name for item in tmp_path.iterdir()) == sorted([MODEL_FILE, INFO_FILE])
