from flask import Blueprint, current_app, jsonify, request

from app.model.inputs import InputValidationError, parse_prediction_input

predict_blueprint = Blueprint("predict", __name__)

PROBABILITY_DIGITS = 4


@predict_blueprint.post("/predict")
def predict():
    predictor = current_app.extensions.get("predictor")
    if predictor is None:
        body = {"error": "Service Unavailable", "status": 503, "detail": "The prediction model is not loaded"}
        return jsonify(body), 503

    payload = request.get_json(silent=True)
    if payload is None:
        body = {"error": "Bad Request", "status": 400, "detail": "A JSON object is expected in the request body"}
        return jsonify(body), 400

    try:
        parsed = parse_prediction_input(payload)
    except InputValidationError as error:
        body = {"error": "Validation failed", "status": 400, "detail": "Request validation failed", "errors": error.errors}
        return jsonify(body), 400

    probability = predictor.predict_proba([parsed])[0]
    return jsonify(probability=round(probability, PROBABILITY_DIGITS), modelVersion=predictor.info.version)
