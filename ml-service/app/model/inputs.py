import math
from dataclasses import dataclass

from app.data import schema
from app.data.synthetic import TEMPERATURE_BY_WEATHER

DEFAULT_USER_NO_SHOW_RATE = 0.125
DEFAULT_WEATHER = "CLEAR"
FALLBACK_TEMPERATURE_C = 15.0
TYPICAL_TEMPERATURE_C = {name: mean for name, (mean, _) in TEMPERATURE_BY_WEATHER.items()}
WEEKEND_FROM_DAY = 6


class InputValidationError(Exception):
    def __init__(self, errors: dict[str, str]):
        super().__init__("Invalid prediction input")
        self.errors = errors


@dataclass(frozen=True)
class PredictionInput:
    day_of_week: int
    hour: int
    lead_time_hours: float
    party_size: int
    user_bookings_count: int
    user_no_show_rate: float
    temperature_c: float
    weather: str
    venue_type: str

    @property
    def is_weekend(self) -> int:
        return int(self.day_of_week >= WEEKEND_FROM_DAY)

    def to_row(self) -> dict:
        return {
            "day_of_week": self.day_of_week,
            "hour": self.hour,
            "lead_time_hours": self.lead_time_hours,
            "party_size": self.party_size,
            "user_bookings_count": self.user_bookings_count,
            "user_no_show_rate": self.user_no_show_rate,
            "is_weekend": self.is_weekend,
            "temperature_c": self.temperature_c,
            "venue_type": self.venue_type,
            "weather": self.weather,
        }


def parse_prediction_input(payload: object) -> PredictionInput:
    if not isinstance(payload, dict):
        raise InputValidationError({"body": "A JSON object is expected"})

    errors: dict[str, str] = {}
    weather = _choice(payload, "weather", schema.WEATHER_CONDITIONS, errors, default=DEFAULT_WEATHER)
    values = {
        "day_of_week": _integer(payload, "dayOfWeek", errors, 1, 7),
        "hour": _integer(payload, "hour", errors, 0, 23),
        "lead_time_hours": _number(payload, "leadTimeHours", errors, 0.0, 8760.0),
        "party_size": _integer(payload, "partySize", errors, 1, 50),
        "user_bookings_count": _integer(payload, "userBookingsCount", errors, 0, 100000, default=0),
        "user_no_show_rate": _number(payload, "userNoShowRate", errors, 0.0, 1.0, default=DEFAULT_USER_NO_SHOW_RATE),
        "temperature_c": _number(
            payload, "temperatureC", errors, -60.0, 60.0,
            default=TYPICAL_TEMPERATURE_C.get(weather, FALLBACK_TEMPERATURE_C),
        ),
        "weather": weather,
        "venue_type": _choice(payload, "venueType", schema.VENUE_TYPES, errors),
    }
    if errors:
        raise InputValidationError(errors)
    return PredictionInput(**values)


def _integer(payload: dict, name: str, errors: dict, low: int, high: int, default: int | None = None) -> int | None:
    if name not in payload or payload[name] is None:
        return _missing(name, errors, default)
    value = payload[name]
    if isinstance(value, bool) or not isinstance(value, int):
        errors[name] = "must be an integer"
        return None
    if not low <= value <= high:
        errors[name] = f"must be between {low} and {high}"
        return None
    return value


def _number(payload: dict, name: str, errors: dict, low: float, high: float, default: float | None = None) -> float | None:
    if name not in payload or payload[name] is None:
        return _missing(name, errors, default)
    value = payload[name]
    if isinstance(value, bool) or not isinstance(value, (int, float)) or not math.isfinite(value):
        errors[name] = "must be a number"
        return None
    if not low <= value <= high:
        errors[name] = f"must be between {low:g} and {high:g}"
        return None
    return float(value)


def _choice(payload: dict, name: str, allowed: tuple[str, ...], errors: dict, default: str | None = None) -> str | None:
    if name not in payload or payload[name] is None:
        return _missing(name, errors, default)
    value = payload[name]
    if not isinstance(value, str) or value.strip().upper() not in allowed:
        errors[name] = "must be one of " + ", ".join(allowed)
        return None
    return value.strip().upper()


def _missing(name: str, errors: dict, default):
    if default is None:
        errors[name] = "is required"
    return default
