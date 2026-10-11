import math

import pytest

from app.data import schema
from app.model.inputs import InputValidationError, parse_prediction_input

VALID = {"dayOfWeek": 5, "hour": 20, "leadTimeHours": 36.5, "partySize": 4, "venueType": "RESTAURANT"}


def errors_for(payload) -> dict:
    with pytest.raises(InputValidationError) as raised:
        parse_prediction_input(payload)
    return raised.value.errors


def test_minimal_payload_gets_documented_defaults():
    parsed = parse_prediction_input(VALID)

    assert parsed.user_bookings_count == 0
    assert parsed.user_no_show_rate == 0.125
    assert parsed.temperature_c == 18.0
    assert parsed.weather == "CLEAR"


@pytest.mark.parametrize("weather, expected", [("CLEAR", 18.0), ("CLOUDY", 13.0), ("RAIN", 10.0), ("SNOW", -3.0)])
def test_default_temperature_matches_the_given_weather(weather, expected):
    assert parse_prediction_input({**VALID, "weather": weather}).temperature_c == expected


def test_explicit_temperature_overrides_the_weather_default():
    assert parse_prediction_input({**VALID, "weather": "SNOW", "temperatureC": 2}).temperature_c == 2.0


def test_full_payload_is_read_as_given():
    parsed = parse_prediction_input(
        {**VALID, "userBookingsCount": 12, "userNoShowRate": 0.3, "temperatureC": -4.5, "weather": "snow"}
    )

    assert parsed.day_of_week == 5
    assert parsed.hour == 20
    assert parsed.lead_time_hours == 36.5
    assert parsed.party_size == 4
    assert parsed.user_bookings_count == 12
    assert parsed.user_no_show_rate == 0.3
    assert parsed.temperature_c == -4.5
    assert parsed.weather == "SNOW"
    assert parsed.venue_type == "RESTAURANT"


def test_enum_values_are_case_and_space_insensitive():
    parsed = parse_prediction_input({**VALID, "venueType": " sport ", "weather": "Rain"})

    assert parsed.venue_type == "SPORT"
    assert parsed.weather == "RAIN"


def test_integers_are_accepted_where_numbers_are_expected():
    parsed = parse_prediction_input({**VALID, "leadTimeHours": 48, "userNoShowRate": 1})

    assert parsed.lead_time_hours == 48.0
    assert parsed.user_no_show_rate == 1.0


def test_weekend_flag_is_derived_from_the_day():
    assert [parse_prediction_input({**VALID, "dayOfWeek": day}).is_weekend for day in range(1, 8)] == [0, 0, 0, 0, 0, 1, 1]


def test_row_uses_exactly_the_training_columns():
    row = parse_prediction_input(VALID).to_row()

    assert set(row) == set(schema.FEATURE_COLUMNS)


def test_all_missing_required_fields_are_reported_together():
    errors = errors_for({})

    assert set(errors) == {"dayOfWeek", "hour", "leadTimeHours", "partySize", "venueType"}
    assert all(message == "is required" for message in errors.values())


def test_null_is_treated_like_a_missing_value():
    assert errors_for({**VALID, "hour": None}) == {"hour": "is required"}
    assert parse_prediction_input({**VALID, "weather": None}).weather == "CLEAR"


@pytest.mark.parametrize("field", ["dayOfWeek", "hour", "partySize", "userBookingsCount"])
def test_integer_fields_reject_text_floats_and_booleans(field):
    for bad in ("3", 3.5, True):
        assert errors_for({**VALID, field: bad}) == {field: "must be an integer"}


@pytest.mark.parametrize("field", ["leadTimeHours", "userNoShowRate", "temperatureC"])
def test_number_fields_reject_text_booleans_and_non_finite_values(field):
    for bad in ("12", True, math.nan, math.inf):
        assert errors_for({**VALID, field: bad}) == {field: "must be a number"}


@pytest.mark.parametrize(
    "field, value",
    [
        ("dayOfWeek", 0),
        ("dayOfWeek", 8),
        ("hour", -1),
        ("hour", 24),
        ("leadTimeHours", -0.1),
        ("leadTimeHours", 9000),
        ("partySize", 0),
        ("partySize", 51),
        ("userBookingsCount", -1),
        ("userNoShowRate", 1.01),
        ("userNoShowRate", -0.01),
        ("temperatureC", 70),
    ],
)
def test_values_outside_the_allowed_range_are_rejected(field, value):
    errors = errors_for({**VALID, field: value})

    assert list(errors) == [field]
    assert errors[field].startswith("must be between")


def test_boundary_values_are_accepted():
    parsed = parse_prediction_input(
        {**VALID, "dayOfWeek": 1, "hour": 0, "leadTimeHours": 0, "partySize": 50, "userNoShowRate": 0, "temperatureC": 60}
    )

    assert parsed.hour == 0
    assert parsed.party_size == 50


def test_unknown_enum_values_are_rejected_with_the_allowed_list():
    errors = errors_for({**VALID, "venueType": "CASTLE", "weather": "FOG"})

    assert errors["venueType"] == "must be one of RESTAURANT, COWORKING, SPORT"
    assert errors["weather"] == "must be one of CLEAR, CLOUDY, RAIN, SNOW"


def test_non_string_enum_is_rejected():
    assert "venueType" in errors_for({**VALID, "venueType": 5})


@pytest.mark.parametrize("payload", [[], "text", 5, None, [VALID]])
def test_body_must_be_an_object(payload):
    assert errors_for(payload) == {"body": "A JSON object is expected"}


def test_unknown_fields_are_ignored():
    assert parse_prediction_input({**VALID, "somethingElse": 1}).party_size == 4
