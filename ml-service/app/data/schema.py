VENUE_TYPES = ("RESTAURANT", "COWORKING", "SPORT")
WEATHER_CONDITIONS = ("CLEAR", "CLOUDY", "RAIN", "SNOW")

NUMERIC_FEATURES = (
    "day_of_week",
    "hour",
    "lead_time_hours",
    "party_size",
    "user_bookings_count",
    "user_no_show_rate",
    "is_weekend",
    "temperature_c",
)
CATEGORICAL_FEATURES = ("venue_type", "weather")

FEATURE_COLUMNS = NUMERIC_FEATURES + CATEGORICAL_FEATURES
TARGET_COLUMN = "no_show"
PROBABILITY_COLUMN = "true_probability"
