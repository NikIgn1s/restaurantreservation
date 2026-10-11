import numpy as np
import pandas as pd

EPSILON = 1e-15


def roc_auc(labels: np.ndarray, scores: np.ndarray) -> float:
    positives = int(labels.sum())
    negatives = len(labels) - positives
    if positives == 0 or negatives == 0:
        raise ValueError("AUC needs both classes to be present")
    ranks = pd.Series(scores).rank(method="average").to_numpy()
    rank_sum = ranks[labels == 1].sum()
    return float((rank_sum - positives * (positives + 1) / 2) / (positives * negatives))


def log_loss(labels: np.ndarray, probabilities: np.ndarray) -> float:
    clipped = np.clip(probabilities, EPSILON, 1 - EPSILON)
    return float(-np.mean(labels * np.log(clipped) + (1 - labels) * np.log(1 - clipped)))


def brier_score(labels: np.ndarray, probabilities: np.ndarray) -> float:
    return float(np.mean((probabilities - labels) ** 2))


def calibration_table(labels: np.ndarray, probabilities: np.ndarray, edges: tuple[float, ...]) -> list[dict]:
    bins = np.digitize(probabilities, edges[1:-1], right=False)
    table = []
    for index in range(len(edges) - 1):
        mask = bins == index
        count = int(mask.sum())
        table.append(
            {
                "from": edges[index],
                "to": edges[index + 1],
                "count": count,
                "meanPredicted": round(float(probabilities[mask].mean()), 4) if count else None,
                "observedRate": round(float(labels[mask].mean()), 4) if count else None,
            }
        )
    return table
