from datetime import datetime

from app import create_app


def test_health_reports_service_is_up(client):
    response = client.get("/health")

    assert response.status_code == 200
    body = response.get_json()
    assert body["status"] == "UP"
    assert body["service"] == "no-show-ml"
    assert body["version"] == "0.1.0"


def test_health_keeps_the_documented_field_order(client):
    body = client.get("/health").get_json()

    assert list(body) == ["status", "service", "version", "uptimeSeconds", "time"]


def test_health_returns_json(client):
    response = client.get("/health")

    assert response.mimetype == "application/json"


def test_health_contains_utc_timestamp(client):
    body = client.get("/health").get_json()

    parsed = datetime.fromisoformat(body["time"])
    assert parsed.utcoffset().total_seconds() == 0


def test_uptime_is_not_negative_and_grows(client):
    first = client.get("/health").get_json()["uptimeSeconds"]
    second = client.get("/health").get_json()["uptimeSeconds"]

    assert first >= 0
    assert second >= first


def test_service_name_and_version_can_be_overridden():
    app = create_app({"SERVICE_NAME": "custom", "SERVICE_VERSION": "9.9.9"})

    body = app.test_client().get("/health").get_json()

    assert body["service"] == "custom"
    assert body["version"] == "9.9.9"


def test_apps_do_not_share_startup_time():
    first = create_app()
    second = create_app()

    assert first.config["STARTED_AT"] <= second.config["STARTED_AT"]


def test_health_accepts_only_get(client):
    response = client.post("/health")

    assert response.status_code == 405
    assert response.get_json()["status"] == 405


def test_unknown_path_returns_json_not_found(client):
    response = client.get("/missing")

    assert response.status_code == 404
    body = response.get_json()
    assert body["error"] == "Not Found"
    assert body["status"] == 404


def test_unexpected_error_is_hidden_from_the_client():
    app = create_app({"TESTING": False, "PROPAGATE_EXCEPTIONS": False})

    @app.get("/boom")
    def boom():
        raise RuntimeError("secret internals")

    response = app.test_client().get("/boom")

    assert response.status_code == 500
    body = response.get_json()
    assert body["detail"] == "Unexpected error"
    assert "secret" not in response.get_data(as_text=True)
