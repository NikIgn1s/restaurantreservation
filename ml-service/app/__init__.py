import logging
import time

from flask import Flask

from app.config import Config
from app.errors import register_error_handlers
from app.health import health_blueprint
from app.model.predictor import load_predictor
from app.predict import predict_blueprint


def create_app(overrides: dict | None = None) -> Flask:
    app = Flask(__name__)
    app.config.from_object(Config)
    app.json.sort_keys = False
    app.config["STARTED_AT"] = time.monotonic()
    if overrides:
        app.config.update(overrides)

    logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(name)s %(message)s")

    predictor = load_predictor(app.config["MODEL_PATH"])
    if predictor is not None:
        app.extensions["predictor"] = predictor

    app.register_blueprint(health_blueprint)
    app.register_blueprint(predict_blueprint)
    register_error_handlers(app)
    return app
