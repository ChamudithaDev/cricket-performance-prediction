"""
feature_engineering.py

Takes the merged per-player-per-match dataset and builds the final
ML-ready feature table using only information that would be available
BEFORE the match being predicted.

Input:
    data/processed/player_match_stats.csv

Output:
    data/processed/train.csv
    data/processed/test.csv
    data/processed/scaler.pkl
    data/processed/feature_columns.json

Important:
    The target is the CURRENT match's runs.
    Therefore, all performance-history features must use only
    PREVIOUS matches through shift(1).

    Same-match/post-match statistics such as:
        balls_faced
        fours
        sixes
        strike_rate
        wickets
        overs_bowled
        runs_conceded
        economy_rate
        catches
        stumpings

    are explicitly excluded from the model features.
"""

import json
from pathlib import Path

import joblib
import pandas as pd
from sklearn.preprocessing import StandardScaler


# ---------------------------------------------------------------------
# Paths and configuration
# ---------------------------------------------------------------------

PROCESSED_DIR = Path("data/processed")
INPUT_CSV = PROCESSED_DIR / "player_match_stats.csv"

TARGET_COLUMN = "runs"

ROLLING_WINDOW = 5
TEST_SIZE_FRACTION = 0.2


# ---------------------------------------------------------------------
# Explicit PRE-MATCH feature whitelist
# ---------------------------------------------------------------------
#
# Only these features are allowed to enter the model.
#
# This is intentionally an allow-list rather than an exclusion-list.
# It prevents accidental numeric columns such as match_id, season,
# internal IDs, or future-derived fields from entering the model.
#
# The categorical features role, batting_style, bowling_style and
# match_type are handled separately after one-hot encoding.
# ---------------------------------------------------------------------

NUMERIC_FEATURES = [
    "avg_runs_last_5",
    "avg_sr_last_5",
    "avg_wickets_last_5",
    "avg_economy_last_5",
    "career_matches_played",
    "career_avg_runs",
    "venue_avg_runs",
    "opponent_avg_runs",
]

CATEGORICAL_FEATURES = [
    "role",
    "batting_style",
    "bowling_style",
    "match_type",
]


# ---------------------------------------------------------------------
# Load data
# ---------------------------------------------------------------------

def load_data() -> pd.DataFrame:
    """
    Load player-match data and sort it chronologically per player.

    Sorting is essential because all historical features depend on
    the order of previous matches.
    """

    df = pd.read_csv(
        INPUT_CSV,
        parse_dates=["match_date"]
    )

    required_columns = [
        "player_name_norm",
        "match_date",
        "runs",
        "strike_rate",
        "wickets",
        "economy_rate",
        "venue",
        "team_b",
    ]

    missing_columns = [
        column for column in required_columns
        if column not in df.columns
    ]

    if missing_columns:
        raise ValueError(
            f"Missing required columns in player_match_stats.csv: "
            f"{missing_columns}"
        )

    df = (
        df.sort_values(
            ["player_name_norm", "match_date"]
        )
        .reset_index(drop=True)
    )

    return df


# ---------------------------------------------------------------------
# Match-type normalization
# ---------------------------------------------------------------------

def normalize_match_type(value):
    """
    Normalize match-type values to the canonical labels used by
    the trained model and prediction API.

    Supported values:
        ODI / odi / one day international -> odi
        T20 / t20 / t20i                  -> t20i
        Test / test                       -> test
    """

    if pd.isna(value):
        return value

    value = str(value).strip().lower()

    aliases = {
        "odi": "odi",
        "one day": "odi",
        "one-day": "odi",
        "one day international": "odi",

        "t20": "t20i",
        "t20i": "t20i",
        "t20 international": "t20i",

        "test": "test",
        "tests": "test",
    }

    return aliases.get(value, value)


# ---------------------------------------------------------------------
# Rolling / historical features
# ---------------------------------------------------------------------

def add_rolling_form_features(df: pd.DataFrame) -> pd.DataFrame:
    """
    Create historical player-performance features.

    CRITICAL:
        shift(1) is used before calculating every historical statistic.

    Therefore, for the current match, the model can only see information
    from matches that happened BEFORE the current match.

    This prevents target leakage.
    """

    df = df.copy()

    # Group by player so historical statistics are calculated
    # independently for each player.
    grp = df.groupby("player_name_norm")

    # -------------------------------------------------------------
    # Recent batting form
    # -------------------------------------------------------------

    df["avg_runs_last_5"] = grp["runs"].transform(
        lambda s: (
            s.shift(1)
            .rolling(
                ROLLING_WINDOW,
                min_periods=1
            )
            .mean()
        )
    )

    # -------------------------------------------------------------
    # Recent strike-rate form
    # -------------------------------------------------------------

    df["avg_sr_last_5"] = grp["strike_rate"].transform(
        lambda s: (
            s.shift(1)
            .rolling(
                ROLLING_WINDOW,
                min_periods=1
            )
            .mean()
        )
    )

    # -------------------------------------------------------------
    # Recent bowling form
    # -------------------------------------------------------------

    df["avg_wickets_last_5"] = grp["wickets"].transform(
        lambda s: (
            s.shift(1)
            .rolling(
                ROLLING_WINDOW,
                min_periods=1
            )
            .mean()
        )
    )

    df["avg_economy_last_5"] = grp["economy_rate"].transform(
        lambda s: (
            s.shift(1)
            .rolling(
                ROLLING_WINDOW,
                min_periods=1
            )
            .mean()
        )
    )

    # -------------------------------------------------------------
    # Career experience
    # -------------------------------------------------------------

    # Number of previous matches played.
    #
    # cumcount() gives:
    # First match  -> 0
    # Second match -> 1
    # Third match  -> 2
    #
    # Therefore it represents previous matches only.
    df["career_matches_played"] = grp.cumcount()

    # Career batting average using previous matches only.
    df["career_avg_runs"] = grp["runs"].transform(
        lambda s: (
            s.shift(1)
            .expanding(min_periods=1)
            .mean()
        )
    )

    # -------------------------------------------------------------
    # Venue history
    # -------------------------------------------------------------

    # Previous runs by the same player at the same venue.
    df["venue_avg_runs"] = (
        df.groupby(
            ["player_name_norm", "venue"]
        )["runs"]
        .transform(
            lambda s: (
                s.shift(1)
                .expanding(min_periods=1)
                .mean()
            )
        )
    )

    # -------------------------------------------------------------
    # Opposition history
    # -------------------------------------------------------------
    #
    # IMPORTANT:
    # team_b must represent the player's opposition team.
    #

    df["opponent_avg_runs"] = (
        df.groupby(
            ["player_name_norm", "team_b"]
        )["runs"]
        .transform(
            lambda s: (
                s.shift(1)
                .expanding(min_periods=1)
                .mean()
            )
        )
    )

    # -------------------------------------------------------------
    # First-match handling
    # -------------------------------------------------------------
    #
    # A player's first match has no historical information.
    # We retain the row and use 0 for unavailable historical values.
    #

    historical_columns = [
        "avg_runs_last_5",
        "avg_sr_last_5",
        "avg_wickets_last_5",
        "avg_economy_last_5",
        "career_avg_runs",
        "venue_avg_runs",
        "opponent_avg_runs",
    ]

    df[historical_columns] = (
        df[historical_columns]
        .fillna(0)
    )

    return df


# ---------------------------------------------------------------------
# Categorical encoding
# ---------------------------------------------------------------------

def encode_categoricals(df: pd.DataFrame) -> pd.DataFrame:
    """
    Normalize and one-hot encode pre-match categorical variables.

    No post-match performance variables are encoded here.
    """

    df = df.copy()

    # Normalize match type before one-hot encoding.
    if "match_type" in df.columns:
        df["match_type"] = (
            df["match_type"]
            .apply(normalize_match_type)
        )

        supported_match_types = {
            "odi",
            "t20i",
            "test",
        }

        unknown_types = sorted(
            set(df["match_type"].dropna().unique())
            - supported_match_types
        )

        if unknown_types:
            raise ValueError(
                "Unsupported match_type values found: "
                f"{unknown_types}. "
                "Expected only: odi, t20i, test."
            )

    categorical_columns = [
        column
        for column in CATEGORICAL_FEATURES
        if column in df.columns
    ]

    df = pd.get_dummies(
        df,
        columns=categorical_columns,
        dummy_na=False
    )

    return df


# ---------------------------------------------------------------------
# Feature selection
# ---------------------------------------------------------------------

def get_feature_columns(df: pd.DataFrame) -> list[str]:
    """
    Return the final model feature list.

    Only explicitly approved pre-match features are allowed.

    This prevents accidental inclusion of:
        - match_id
        - player IDs
        - same-match statistics
        - target values
        - future information
        - unrelated numeric columns
    """

    feature_columns = []

    # -------------------------------------------------------------
    # Numeric historical features
    # -------------------------------------------------------------

    for column in NUMERIC_FEATURES:
        if column in df.columns:
            feature_columns.append(column)

    # -------------------------------------------------------------
    # One-hot encoded categorical features
    # -------------------------------------------------------------
    #
    # Include all generated columns belonging to the approved
    # categorical variables.
    #

    categorical_prefixes = [
        "role_",
        "batting_style_",
        "bowling_style_",
        "match_type_",
    ]

    for column in df.columns:
        if any(
            column.startswith(prefix)
            for prefix in categorical_prefixes
        ):
            feature_columns.append(column)

    # Remove duplicates while preserving order.
    feature_columns = list(
        dict.fromkeys(feature_columns)
    )

    if not feature_columns:
        raise ValueError(
            "No valid model features were found."
        )

    return feature_columns


# ---------------------------------------------------------------------
# Chronological train/test split
# ---------------------------------------------------------------------

def chronological_split(df: pd.DataFrame):
    """
    Split data chronologically.

    The final 20% of matches by date are used as the test set.

    This simulates the real-world scenario:
        past matches -> training
        future matches -> testing
    """

    df = (
        df.sort_values("match_date")
        .reset_index(drop=True)
    )

    split_idx = int(
        len(df) * (1 - TEST_SIZE_FRACTION)
    )

    if split_idx <= 0 or split_idx >= len(df):
        raise ValueError(
            "Invalid chronological split. "
            "Check dataset size and TEST_SIZE_FRACTION."
        )

    split_date = df.iloc[split_idx]["match_date"]

    train = df[
        df["match_date"] < split_date
    ].copy()

    test = df[
        df["match_date"] >= split_date
    ].copy()

    print(
        f"Chronological split at {split_date.date()}: "
        f"{len(train)} train rows, "
        f"{len(test)} test rows"
    )

    return train, test


# ---------------------------------------------------------------------
# Align train/test feature columns
# ---------------------------------------------------------------------

def align_feature_columns(
    train: pd.DataFrame,
    test: pd.DataFrame,
    feature_columns: list[str],
):
    """
    Ensure train and test contain exactly the same feature columns.

    This is particularly important for one-hot encoded categorical
    variables because a category may exist in training but not in test,
    or vice versa.
    """

    train = train.copy()
    test = test.copy()

    for column in feature_columns:

        if column not in train.columns:
            train[column] = 0

        if column not in test.columns:
            test[column] = 0

    train = train[
        list(train.columns)
    ]

    test = test[
        list(test.columns)
    ]

    return train, test


# ---------------------------------------------------------------------
# Feature scaling
# ---------------------------------------------------------------------

def scale_features(
    train: pd.DataFrame,
    test: pd.DataFrame,
    feature_columns: list[str],
):
    """
    Scale features using a StandardScaler.

    IMPORTANT:
        The scaler is fitted ONLY on training data.

        The test set is transformed using the training scaler.

    This prevents test-set information from leaking into training.
    """

    scaler = StandardScaler()

    train_scaled = train.copy()
    test_scaled = test.copy()

    train_scaled[feature_columns] = (
        scaler.fit_transform(
            train[feature_columns]
        )
    )

    test_scaled[feature_columns] = (
        scaler.transform(
            test[feature_columns]
        )
    )

    return (
        train_scaled,
        test_scaled,
        scaler,
    )


# ---------------------------------------------------------------------
# Main pipeline
# ---------------------------------------------------------------------

if __name__ == "__main__":

    print("=" * 70)
    print("FEATURE ENGINEERING PIPELINE")
    print("=" * 70)

    # -------------------------------------------------------------
    # 1. Load raw player-match dataset
    # -------------------------------------------------------------

    print("\n[1/7] Loading data...")

    df = load_data()

    print(
        f"Loaded {len(df)} player-match rows."
    )

    print(
        f"Date range: "
        f"{df['match_date'].min().date()} "
        f"to "
        f"{df['match_date'].max().date()}"
    )

    # -------------------------------------------------------------
    # 2. Normalize match type BEFORE encoding
    # -------------------------------------------------------------

    print("\n[2/7] Normalizing match types...")

    if "match_type" in df.columns:

        df["match_type"] = (
            df["match_type"]
            .apply(normalize_match_type)
        )

        print(
            "Match types found:",
            sorted(
                df["match_type"]
                .dropna()
                .unique()
                .tolist()
            )
        )

    # -------------------------------------------------------------
    # 3. Create historical features
    # -------------------------------------------------------------

    print(
        "\n[3/7] Creating pre-match historical features..."
    )

    df = add_rolling_form_features(df)

    # -------------------------------------------------------------
    # 4. Encode categorical features
    # -------------------------------------------------------------

    print(
        "\n[4/7] Encoding categorical features..."
    )

    df = encode_categoricals(df)

    # -------------------------------------------------------------
    # 5. Select ONLY approved features
    # -------------------------------------------------------------

    print(
        "\n[5/7] Selecting approved pre-match features..."
    )

    feature_columns = get_feature_columns(df)

    print(
        f"Selected {len(feature_columns)} model features:"
    )

    for feature in feature_columns:
        print(f"  - {feature}")

    # -------------------------------------------------------------
    # Safety check
    # -------------------------------------------------------------
    #
    # These columns must NEVER appear in the final feature list.
    #

    forbidden_features = {
        "runs",
        "balls_faced",
        "fours",
        "sixes",
        "strike_rate",
        "wickets",
        "overs_bowled",
        "runs_conceded",
        "economy_rate",
        "catches",
        "stumpings",
        "match_id",
    }

    leakage_features = (
        set(feature_columns)
        & forbidden_features
    )

    if leakage_features:
        raise ValueError(
            "LEAKAGE DETECTED! Forbidden columns found "
            f"in feature list: {sorted(leakage_features)}"
        )

    print(
        "\nLeakage safety check: PASSED"
    )

    # -------------------------------------------------------------
    # 6. Chronological split
    # -------------------------------------------------------------

    print(
        "\n[6/7] Creating chronological train/test split..."
    )

    train_df, test_df = chronological_split(df)

    # Ensure all expected feature columns exist in both datasets.
    train_df, test_df = align_feature_columns(
        train_df,
        test_df,
        feature_columns,
    )

    # -------------------------------------------------------------
    # 7. Scale using training data only
    # -------------------------------------------------------------

    print(
        "\n[7/7] Scaling features..."
    )

    (
        train_df,
        test_df,
        scaler,
    ) = scale_features(
        train_df,
        test_df,
        feature_columns,
    )

    # -------------------------------------------------------------
    # Save outputs
    # -------------------------------------------------------------

    PROCESSED_DIR.mkdir(
        parents=True,
        exist_ok=True
    )

    train_path = (
        PROCESSED_DIR / "train.csv"
    )

    test_path = (
        PROCESSED_DIR / "test.csv"
    )

    scaler_path = (
        PROCESSED_DIR / "scaler.pkl"
    )

    feature_columns_path = (
        PROCESSED_DIR / "feature_columns.json"
    )

    train_df.to_csv(
        train_path,
        index=False
    )

    test_df.to_csv(
        test_path,
        index=False
    )

    joblib.dump(
        scaler,
        scaler_path
    )

    with open(
        feature_columns_path,
        "w",
        encoding="utf-8",
    ) as f:

        json.dump(
            {
                "features": feature_columns,
                "target": TARGET_COLUMN,
            },
            f,
            indent=2,
        )

    # -------------------------------------------------------------
    # Final summary
    # -------------------------------------------------------------

    print("\n" + "=" * 70)
    print("FEATURE ENGINEERING COMPLETED")
    print("=" * 70)

    print(
        f"Total rows:       {len(df)}"
    )

    print(
        f"Training rows:    {len(train_df)}"
    )

    print(
        f"Testing rows:     {len(test_df)}"
    )

    print(
        f"Model features:   {len(feature_columns)}"
    )

    print(
        f"Target:           {TARGET_COLUMN}"
    )

    print(
        f"Train date range: "
        f"{train_df['match_date'].min().date()} "
        f"to "
        f"{train_df['match_date'].max().date()}"
    )

    print(
        f"Test date range:  "
        f"{test_df['match_date'].min().date()} "
        f"to "
        f"{test_df['match_date'].max().date()}"
    )

    print("\nSaved files:")

    print(f"  {train_path}")
    print(f"  {test_path}")
    print(f"  {scaler_path}")
    print(f"  {feature_columns_path}")

    print("\nFinal feature list:")

    for index, feature in enumerate(
        feature_columns,
        start=1
    ):
        print(
            f"  {index:2d}. {feature}"
        )

    print(
        "\nLeakage safety check: PASSED"
    )

    print("=" * 70)