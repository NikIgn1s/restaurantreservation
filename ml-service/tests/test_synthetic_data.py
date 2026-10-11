import numpy as np
import pandas as pd
import pytest

from app.data import schema
from app.data.generate import main
from app.data.synthetic import generate_dataset

ROWS = 30000


@pytest.fixture(scope="module")
def dataset() -> pd.DataFrame:
    return generate_dataset(ROWS, seed=7, include_probability=True)


def test_has_expected_columns_and_size(dataset):
    assert len(dataset) == ROWS
    assert list(dataset.columns) == list(schema.FEATURE_COLUMNS) + [schema.TARGET_COLUMN, schema.PROBABILITY_COLUMN]


def test_probability_column_is_optional():
    frame = generate_dataset(100, seed=1)

    assert schema.PROBABILITY_COLUMN not in frame.columns
    assert list(frame.columns) == list(schema.FEATURE_COLUMNS) + [schema.TARGET_COLUMN]


def test_has_no_missing_values(dataset):
    assert not dataset.isna().any().any()


def test_is_reproducible_for_the_same_seed():
    first = generate_dataset(2000, seed=123)
    second = generate_dataset(2000, seed=123)

    pd.testing.assert_frame_equal(first, second)


def test_differs_between_seeds():
    first = generate_dataset(2000, seed=1)
    second = generate_dataset(2000, seed=2)

    assert not first.equals(second)


def test_rejects_non_positive_row_count():
    with pytest.raises(ValueError):
        generate_dataset(0)
    with pytest.raises(ValueError):
        generate_dataset(-5)


def test_works_for_a_single_row():
    assert len(generate_dataset(1)) == 1


def test_value_ranges_match_the_booking_domain(dataset):
    assert dataset["day_of_week"].between(1, 7).all()
    assert dataset["hour"].between(10, 23).all()
    assert dataset["lead_time_hours"].between(1, 720).all()
    assert dataset["party_size"].between(1, 12).all()
    assert (dataset["user_bookings_count"] >= 0).all()
    assert dataset["user_no_show_rate"].between(0, 1).all()
    assert dataset["is_weekend"].isin([0, 1]).all()
    assert dataset[schema.TARGET_COLUMN].isin([0, 1]).all()
    assert dataset[schema.PROBABILITY_COLUMN].between(0, 1).all()


def test_categories_use_the_names_of_the_java_side(dataset):
    assert set(dataset["venue_type"]) == set(schema.VENUE_TYPES)
    assert set(dataset["weather"]) == set(schema.WEATHER_CONDITIONS)


def test_weekend_flag_matches_day_of_week(dataset):
    assert (dataset["is_weekend"] == (dataset["day_of_week"] >= 6).astype(int)).all()


def test_overall_no_show_rate_is_realistic(dataset):
    rate = dataset[schema.TARGET_COLUMN].mean()

    assert 0.08 <= rate <= 0.30


def test_labels_follow_the_true_probability(dataset):
    assert abs(dataset[schema.TARGET_COLUMN].mean() - dataset[schema.PROBABILITY_COLUMN].mean()) < 0.01


def test_both_classes_are_present_in_every_reasonable_sample():
    for seed in range(5):
        frame = generate_dataset(500, seed=seed)

        assert frame[schema.TARGET_COLUMN].nunique() == 2


def test_users_with_bad_history_skip_more_often(dataset):
    low = dataset[dataset["user_no_show_rate"] < 0.10][schema.TARGET_COLUMN].mean()
    high = dataset[dataset["user_no_show_rate"] > 0.25][schema.TARGET_COLUMN].mean()

    assert high > low + 0.05


def test_longer_lead_time_increases_no_shows(dataset):
    short = dataset[dataset["lead_time_hours"] < 12][schema.TARGET_COLUMN].mean()
    long = dataset[dataset["lead_time_hours"] > 150][schema.TARGET_COLUMN].mean()

    assert long > short


def test_bad_weather_increases_no_shows(dataset):
    clear = dataset[dataset["weather"] == "CLEAR"][schema.TARGET_COLUMN].mean()
    snow = dataset[dataset["weather"] == "SNOW"][schema.TARGET_COLUMN].mean()
    rain = dataset[dataset["weather"] == "RAIN"][schema.TARGET_COLUMN].mean()

    assert snow > rain > clear


def test_late_evening_is_riskier_than_lunch(dataset):
    lunch = dataset[dataset["hour"].isin([12, 13])][schema.TARGET_COLUMN].mean()
    late = dataset[dataset["hour"] >= 21][schema.TARGET_COLUMN].mean()

    assert late > lunch


def test_coworkings_are_more_reliable_than_sport(dataset):
    coworking = dataset[dataset["venue_type"] == "COWORKING"][schema.TARGET_COLUMN].mean()
    sport = dataset[dataset["venue_type"] == "SPORT"][schema.TARGET_COLUMN].mean()

    assert sport > coworking


def test_temperature_depends_on_weather(dataset):
    means = dataset.groupby("weather")["temperature_c"].mean()

    assert means["SNOW"] < 2
    assert means["SNOW"] < means["RAIN"] < means["CLEAR"]


def test_new_users_get_the_prior_rate(dataset):
    newcomers = dataset[dataset["user_bookings_count"] == 0]

    assert len(newcomers) > 0
    assert np.allclose(newcomers["user_no_show_rate"], 1.0 / 8.0, atol=1e-4)


def test_weekend_and_friday_are_the_busiest_days(dataset):
    counts = dataset["day_of_week"].value_counts()

    assert counts[[5, 6]].min() > counts[[1, 2]].max()


def test_cli_writes_a_csv_with_the_requested_rows(tmp_path):
    target = tmp_path / "nested" / "bookings.csv"

    written = main(["--rows", "250", "--seed", "5", "--out", str(target)])

    frame = pd.read_csv(written)
    assert written == target
    assert len(frame) == 250
    assert list(frame.columns) == list(schema.FEATURE_COLUMNS) + [schema.TARGET_COLUMN]


def test_cli_output_is_reproducible(tmp_path):
    first = main(["--rows", "300", "--seed", "9", "--out", str(tmp_path / "a.csv")])
    second = main(["--rows", "300", "--seed", "9", "--out", str(tmp_path / "b.csv")])

    assert first.read_bytes() == second.read_bytes()


def test_cli_can_include_the_true_probability(tmp_path):
    written = main(["--rows", "100", "--out", str(tmp_path / "p.csv"), "--with-probability"])

    assert schema.PROBABILITY_COLUMN in pd.read_csv(written).columns


def test_cli_rejects_invalid_rows(tmp_path):
    with pytest.raises(ValueError):
        main(["--rows", "0", "--out", str(tmp_path / "x.csv")])
