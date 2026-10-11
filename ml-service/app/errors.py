from flask import Flask, jsonify
from werkzeug.exceptions import HTTPException


def register_error_handlers(app: Flask) -> None:
    @app.errorhandler(HTTPException)
    def handle_http_error(error: HTTPException):
        body = {"error": error.name, "status": error.code, "detail": error.description}
        return jsonify(body), error.code

    @app.errorhandler(Exception)
    def handle_unexpected_error(error: Exception):
        app.logger.exception("Unhandled error: %s", error)
        body = {"error": "Internal Server Error", "status": 500, "detail": "Unexpected error"}
        return jsonify(body), 500
