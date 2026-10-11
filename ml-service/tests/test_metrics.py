import numpy as np
import pytest

from app.model import metrics


def test_perfect_ranking_has_auc_one():
    labels = np.array([0, 0, 1, 1])

    assert metrics.roc_auc(labels, np.array([0.1, 0.2, 0.8, 0.9])) == 1.0


def test_reversed_ranking_has_auc_zero():
    labels = np.array([0, 0, 1, 1])

    assert metrics.roc_auc(labels, np.array([0.9, 0.8, 0.2, 0.1])) == 0.0


def test_constant_scores_have_auc_one_half():
    labels = np.array([0, 1, 0, 1, 1, 0])

    assert metrics.roc_auc(labels, np.full(6, 0.3)) == 0.5


def test_auc_matches_the_definition_on_a_small_example():
    labels = np.array([0, 0, 1, 1])
    scores = np.array([0.1, 0.4, 0.35, 0.8])

    assert metrics.roc_auc(labels, scores) == pytest.approx(0.75)


def test_auc_requires_both_classes():
    with pytest.raises(ValueError):
        metrics.roc_auc(np.array([1, 1, 1]), np.array([0.2, 0.5, 0.9]))
    with pytest.raises(ValueError):
        metrics.roc_auc(np.array([0, 0]), np.array([0.2, 0.5]))


def test_log_loss_of_confident_correct_predictions_is_small():
    assert metrics.log_loss(np.array([1, 0]), np.array([0.999, 0.001])) < 0.01


def test_log_loss_of_coin_flip_is_ln_two():
    assert metrics.log_loss(np.array([1, 0, 1, 0]), np.full(4, 0.5)) == pytest.approx(np.log(2))


def test_log_loss_survives_certain_wrong_predictions():
    value = metrics.log_loss(np.array([1, 0]), np.array([0.0, 1.0]))

    assert np.isfinite(value)
    assert value > 10


def test_brier_score_values():
    assert metrics.brier_score(np.array([1, 0]), np.array([1.0, 0.0])) == 0.0
    assert metrics.brier_score(np.array([1, 0]), np.array([0.0, 1.0])) == 1.0
    assert metrics.brier_score(np.array([1, 0]), np.array([0.5, 0.5])) == 0.25


def test_calibration_table_groups_predictions_into_bins():
    labels = np.array([0, 0, 1, 1, 0])
    probabilities = np.array([0.05, 0.08, 0.15, 0.55, 0.95])

    table = metrics.calibration_table(labels, probabilities, (0.0, 0.1, 0.5, 1.0))

    assert [row["count"] for row in table] == [2, 1, 2]
    assert table[0]["observedRate"] == 0.0
    assert table[1]["observedRate"] == 1.0
    assert table[2]["observedRate"] == 0.5
    assert table[2]["meanPredicted"] == 0.75


def test_calibration_table_reports_empty_bins_without_failing():
    table = metrics.calibration_table(np.array([1]), np.array([0.05]), (0.0, 0.5, 1.0))

    assert table[1]["count"] == 0
    assert table[1]["meanPredicted"] is None
    assert table[1]["observedRate"] is None
