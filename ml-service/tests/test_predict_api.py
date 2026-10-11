import pytest

VALID = {"dayOfWeek": 5, "hour": 20, "leadTimeHours": 36.5, "partySize": 4, "venueType": "RESTAURANT"}
RISKY = {"dayOfWeek": 6, "hour": 22, "leadTimeHours": 400, "partySize": 6, "venueType": "SPORT",
         "weather": "SNOW", "userNoShowRate": 0.45, "userBookingsCount": 10, "temperatureC": -3}
SAFE = {"dayOfWeek": 2, "hour": 13, "leadTimeHours": 3, "partySize": 2, "venueType": "COWORKING",
        "weather": "CLEAR", "userNoShowRate": 0.03, "userBookingsCount": 30, "temperatureC": 20}


def probability(client, payload) -> float:
    response = client.post("/predict", json=payload)
    assert response.status_code == 200
    return response.get_json()["probability"]


def test_predict_returns_probability_and_model_version(client, trained):
    response = client.post("/predict", json=VALID)

    assert response.status_code == 200
    body = response.get_json()
    assert list(body) == ["probability", "modelVersion"]
    assert 0.0 < body["probability"] < 1.0
    assert len(body["modelVersion"]) == 12


def test_probability_is_rounded_to_four_digits(client):
    value = probability(client, VALID)

    assert value == round(value, 4)


def test_same_input_gives_the_same_answer(client):
    assert probability(client, VALID) == probability(client, VALID)


def test_risky_booking_is_scored_higher_than_a_safe_one(client):
    assert probability(client, RISKY) > probability(client, SAFE) + 0.3


def test_bad_history_raises_the_risk(client):
    reliable = probability(client, {**VALID, "userNoShowRate": 0.02, "userBookingsCount": 20})
    unreliable = probability(client, {**VALID, "userNoShowRate": 0.5, "userBookingsCount": 20})

    assert unreliable > reliable


def test_snow_raises_the_risk_compared_to_clear_weather(client):
    assert probability(client, {**VALID, "weather": "SNOW"}) > probability(client, {**VALID, "weather": "CLEAR"})


def test_defaults_describe_an_average_newcomer(client):
    value = probability(client, VALID)

    assert 0.04 < value < 0.40


def test_enum_names_are_case_insensitive(client):
    assert probability(client, {**VALID, "venueType": "restaurant"}) == probability(client, VALID)


def test_missing_required_fields_are_listed(client):
    response = client.post("/predict", json={})

    assert response.status_code == 400
    body = response.get_json()
    assert body["error"] == "Validation failed"
    assert set(body["errors"]) == {"dayOfWeek", "hour", "leadTimeHours", "partySize", "venueType"}


def test_invalid_values_are_reported_per_field(client):
    response = client.post("/predict", json={**VALID, "hour": 30, "weather": "FOG", "partySize": "four"})

    assert response.status_code == 400
    assert set(response.get_json()["errors"]) == {"hour", "weather", "partySize"}


def test_body_must_be_a_json_object(client):
    for payload in ([], [VALID], "text", 5):
        response = client.post("/predict", json=payload)

        assert response.status_code == 400
        assert response.get_json()["errors"] == {"body": "A JSON object is expected"}


def test_non_json_body_is_a_bad_request(client):
    response = client.post("/predict", data="dayOfWeek=5", content_type="text/plain")

    assert response.status_code == 400
    assert response.get_json()["status"] == 400


def test_malformed_json_is_a_bad_request(client):
    response = client.post("/predict", data="{not json", content_type="application/json")

    assert response.status_code == 400


def test_empty_body_is_a_bad_request(client):
    assert client.post("/predict").status_code == 400


def test_predict_accepts_only_post(client):
    response = client.get("/predict")

    assert response.status_code == 405


def test_unknown_fields_do_not_break_the_prediction(client):
    assert probability(client, {**VALID, "extra": "ignored"}) == probability(client, VALID)


def test_prediction_is_unavailable_without_a_model(client_without_model):
    response = client_without_model.post("/predict", json=VALID)

    assert response.status_code == 503
    body = response.get_json()
    assert body["status"] == 503
    assert body["error"] == "Service Unavailable"


def test_missing_model_is_reported_before_the_payload_is_checked(client_without_model):
    assert client_without_model.post("/predict", json={}).status_code == 503


def test_corrupted_model_file_disables_predictions_instead_of_crashing(tmp_path):
    from app import create_app

    broken = tmp_path / "broken.cbm"
    broken.write_bytes(b"not a catboost model")
    client = create_app({"TESTING": True, "MODEL_PATH": str(broken)}).test_client()

    assert client.get("/health").get_json()["model"] == {"loaded": False}
    assert client.post("/predict", json=VALID).status_code == 503


@pytest.mark.parametrize("day", range(1, 8))
@pytest.mark.parametrize("hour", [10, 15, 23])
def test_every_day_and_hour_gives_a_valid_probability(client, day, hour):
    value = probability(client, {**VALID, "dayOfWeek": day, "hour": hour})

    assert 0.0 < value < 1.0
