import logging
import time

from flask import Flask

from app.config import Config
from app.errors import register_error_handlers
from app.health import health_blueprint


def create_app(overrides: dict | None = None) -> Flask:
    app = Flask(__name__)
    app.config.from_object(Config)
    app.json.sort_keys = False
    app.config["STARTED_AT"] = time.monotonic()
    if overrides:
        app.config.update(overrides)

    logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(name)s %(message)s")

    app.register_blueprint(health_blueprint)
    register_error_handlers(app)
    return app
