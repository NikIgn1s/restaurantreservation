import numpy as np
import pandas as pd

from app.data import schema

MIN_USERS = 50
USERS_PER_ROW = 8
PRIOR_NO_SHOWS = 1.0
PRIOR_BOOKINGS = 8.0
MAX_LEAD_TIME_HOURS = 720.0
MAX_PARTY_SIZE = 12

DAY_WEIGHTS = np.array([0.10, 0.10, 0.11, 0.13, 0.19, 0.21, 0.16])
HOURS = np.arange(10, 24)
LUNCH_HOURS = (12, 13)
HOUR_WEIGHTS = np.array([2, 3, 6, 7, 4, 2, 2, 4, 8, 9, 7, 4, 2, 1], dtype=float)
VENUE_WEIGHTS = np.array([0.6, 0.2, 0.2])
WEATHER_WEIGHTS = np.array([0.45, 0.30, 0.18, 0.07])
TEMPERATURE_BY_WEATHER = {
    "CLEAR": (18.0, 8.0),
    "CLOUDY": (13.0, 6.0),
    "RAIN": (10.0, 5.0),
    "SNOW": (-3.0, 4.0),
}
WEATHER_EFFECT = {"CLEAR": 0.0, "CLOUDY": 0.05, "RAIN": 0.35, "SNOW": 0.65}
VENUE_EFFECT = {"RESTAURANT": 0.0, "COWORKING": -0.20, "SPORT": 0.25}


def sigmoid(values: np.ndarray) -> np.ndarray:
    return 1.0 / (1.0 + np.exp(-values))


def generate_dataset(rows: int, seed: int = 42, include_probability: bool = False) -> pd.DataFrame:
    if rows <= 0:
        raise ValueError("rows must be positive")

    rng = np.random.default_rng(seed)
    users = _draw_users(rng, rows)

    day_of_week = rng.choice(np.arange(1, 8), size=rows, p=DAY_WEIGHTS / DAY_WEIGHTS.sum())
    hour = rng.choice(HOURS, size=rows, p=HOUR_WEIGHTS / HOUR_WEIGHTS.sum())
    lead_time_hours = np.clip(1.0 + rng.exponential(48.0, size=rows), 1.0, MAX_LEAD_TIME_HOURS).round(1)
    party_size = np.clip(1 + rng.poisson(2.0, size=rows), 1, MAX_PARTY_SIZE)
    venue_type = rng.choice(schema.VENUE_TYPES, size=rows, p=VENUE_WEIGHTS)
    weather = rng.choice(schema.WEATHER_CONDITIONS, size=rows, p=WEATHER_WEIGHTS)
    temperature_c = _draw_temperature(rng, weather)

    history_count = users["history_count"]
    history_no_shows = users["history_no_shows"]
    observed_rate = (history_no_shows + PRIOR_NO_SHOWS) / (history_count + PRIOR_BOOKINGS)

    frame = pd.DataFrame(
        {
            "day_of_week": day_of_week,
            "hour": hour,
            "lead_time_hours": lead_time_hours,
            "party_size": party_size,
            "user_bookings_count": history_count,
            "user_no_show_rate": observed_rate.round(4),
            "is_weekend": (day_of_week >= 6).astype(int),
            "temperature_c": temperature_c.round(1),
            "venue_type": venue_type,
            "weather": weather,
        }
    )

    probability = no_show_probability(frame, users["latent_rate"], rng)
    frame[schema.TARGET_COLUMN] = (rng.random(rows) < probability).astype(int)
    if include_probability:
        frame[schema.PROBABILITY_COLUMN] = probability.round(4)
    return frame


def no_show_probability(frame: pd.DataFrame, latent_rate: np.ndarray, rng: np.random.Generator) -> np.ndarray:
    logit = (
        -2.55
        + 5.0 * (latent_rate - 0.12)
        + 0.35 * np.log1p(frame["lead_time_hours"].to_numpy() / 24.0)
        + np.where(frame["hour"].to_numpy() >= 21, 0.35, 0.0)
        + np.where(np.isin(frame["hour"].to_numpy(), LUNCH_HOURS), -0.15, 0.0)
        + 0.20 * frame["is_weekend"].to_numpy()
        + 0.06 * (np.clip(frame["party_size"].to_numpy(), 1, 8) - 2)
        + frame["weather"].map(WEATHER_EFFECT).to_numpy()
        + frame["venue_type"].map(VENUE_EFFECT).to_numpy()
        + rng.normal(0.0, 0.3, size=len(frame))
    )
    return sigmoid(logit)


def _draw_users(rng: np.random.Generator, rows: int) -> dict[str, np.ndarray]:
    user_count = max(MIN_USERS, rows // USERS_PER_ROW)
    latent_rates = rng.beta(1.2, 9.0, size=user_count)
    activity = rng.gamma(shape=2.0, scale=3.0, size=user_count)

    user_index = rng.integers(0, user_count, size=rows)
    history_count = rng.poisson(activity[user_index])
    latent_rate = latent_rates[user_index]
    history_no_shows = rng.binomial(history_count, latent_rate)
    return {
        "history_count": history_count,
        "history_no_shows": history_no_shows,
        "latent_rate": latent_rate,
    }


def _draw_temperature(rng: np.random.Generator, weather: np.ndarray) -> np.ndarray:
    temperature = np.empty(len(weather))
    for condition, (mean, spread) in TEMPERATURE_BY_WEATHER.items():
        mask = weather == condition
        temperature[mask] = rng.normal(mean, spread, size=int(mask.sum()))
    return temperature
