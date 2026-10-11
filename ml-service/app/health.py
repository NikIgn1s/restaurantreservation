import time
from datetime import datetime, timezone

from flask import Blueprint, current_app, jsonify

health_blueprint = Blueprint("health", __name__)


@health_blueprint.get("/health")
def health():
    uptime = time.monotonic() - current_app.config["STARTED_AT"]
    return jsonify(
        status="UP",
        service=current_app.config["SERVICE_NAME"],
        version=current_app.config["SERVICE_VERSION"],
        model=_model_status(),
        uptimeSeconds=round(uptime, 1),
        time=datetime.now(timezone.utc).isoformat(),
    )


def _model_status() -> dict:
    predictor = current_app.extensions.get("predictor")
    if predictor is None:
        return {"loaded": False}
    return {
        "loaded": True,
        "version": predictor.info.version,
        "trainedAt": predictor.info.trained_at,
        "auc": predictor.info.auc,
    }
