"""
predict.py

Loads the saved best model + scaler + feature column list, and predicts a
player's next-match batting performance given upcoming match context
(venue, opponent, match type).

This does NOT require the match to have happened — it computes the
player's rolling/career/venue/opponent features from their EXISTING match
history (data/processed/player_match_stats.csv), the same way
feature_engineering.py did during training, then feeds that single row
through the model.

Run:
    python predict.py --player "V Kohli" --venue "Eden Gardens" \
                       --opponent "Australia" --match_type T20 \
                       --task regression
"""

import argparse
import json
from pathlib import Path

import joblib
import pandas as pd

BASE_DIR = Path(__file__).resolve().parent          # ml-service/models/
PROCESSED_DIR = BASE_DIR.parent / "data" / "processed"
SAVED_MODELS_DIR = BASE_DIR / "saved_models"

REG_MODEL_PATH = SAVED_MODELS_DIR / "best_regression_model.pkl"
CLF_MODEL_PATH = SAVED_MODELS_DIR / "best_classification_model.pkl"
SCALER_PATH = PROCESSED_DIR / "scaler.pkl"
FEATURE_META_PATH = PROCESSED_DIR / "feature_columns.json"
HISTORY_CSV = PROCESSED_DIR / "player_match_stats.csv"

ROLLING_WINDOW = 5

# ---------------------------------------------------------------------------
# Match-type normalisation
# ---------------------------------------------------------------------------
# The training data stores format as lowercase cricsheet labels:
#   odi, t20i, test
# The UI (and CLI) may send mixed-case aliases like "T20", "ODI", "Test".
# This map normalises all accepted aliases to the training label so that
# pd.get_dummies() produces the correct dummy column (e.g. match_type_t20i).
MATCH_TYPE_ALIASES: dict[str, str] = {
    "t20":   "t20i",
    "t20i":  "t20i",
    "odi":   "odi",
    "one day international": "odi",
    "test":  "test",
    "tests": "test",
}


def normalize_name(name: str) -> str:
    return name.lower().strip()


def normalize_match_type(match_type: str) -> str:
    """Map any accepted alias to the lowercase training label.

    Raises ValueError for unknown values so the caller gets a meaningful
    message rather than a silently-zeroed feature vector.
    """
    key = match_type.lower().strip()
    if key not in MATCH_TYPE_ALIASES:
        valid = sorted(set(MATCH_TYPE_ALIASES.values()))
        raise ValueError(
            f"Unknown match_type '{match_type}'. "
            f"Accepted values (case-insensitive): T20 / t20i, ODI, Test. "
            f"Training labels: {valid}."
        )
    return MATCH_TYPE_ALIASES[key]


def build_feature_row(player: str, venue: str, opponent: str, match_type: str) -> pd.DataFrame:
    """Compute rolling/career/venue/opponent features from the player's
    existing history, matching the logic in feature_engineering.py exactly
    (shift(1) semantics don't apply here since we're appending a brand-new
    future row with no result yet — we simply take the trailing window of
    real past matches directly).

    match_type is normalised here so callers can pass 'T20', 'ODI', 'Test'
    (or any accepted alias) without producing a silently-zeroed feature vector.
    """
    # Normalise match_type to the label used during training (t20i / odi / test)
    match_type = normalize_match_type(match_type)

    history = pd.read_csv(HISTORY_CSV, parse_dates=["match_date"])
    player_norm = normalize_name(player)
    player_history = history[history["player_name_norm"] == player_norm].sort_values("match_date")

    if player_history.empty:
        raise ValueError(
            f"No historical data found for '{player}'. Check the exact name spelling "
            f"used in your dataset (e.g. 'V Kohli' vs 'Virat Kohli')."
        )

    last_n = player_history.tail(ROLLING_WINDOW)
    venue_matches = player_history[player_history["venue"] == venue] if venue else pd.DataFrame()
    opponent_matches = player_history[player_history["team_b"] == opponent]

    row = {
        "avg_runs_last_5": last_n["runs"].mean(),
        "avg_sr_last_5": last_n["strike_rate"].mean(),
        "avg_wickets_last_5": last_n["wickets"].mean(),
        "avg_economy_last_5": last_n["economy_rate"].mean(),
        "career_matches_played": len(player_history),
        "career_avg_runs": player_history["runs"].mean(),
        "venue_avg_runs": venue_matches["runs"].mean() if not venue_matches.empty else 0.0,
        "opponent_avg_runs": opponent_matches["runs"].mean() if not opponent_matches.empty else 0.0,
    }

    # Most recent known metadata for this player (role/batting_style/bowling_style)
    latest_meta = player_history.iloc[-1]
    for col in ["role", "batting_style", "bowling_style"]:
        if col in player_history.columns:
            row[col] = latest_meta.get(col, "Unknown")
    row["match_type"] = match_type  # already normalised (e.g. "t20i", "odi", "test")

    return pd.DataFrame([row])


def align_to_training_columns(raw_row: pd.DataFrame, feature_cols: list[str]) -> pd.DataFrame:
    """One-hot encode the same way training did, then reindex to the exact
    training column set — any dummy column the training set had that this
    single row doesn't produce (e.g. a batting_style the player doesn't have)
    is added back as 0; any column this row produces that training never
    saw is dropped, so the model always receives the shape it expects."""
    categorical_cols = [c for c in ["role", "batting_style", "bowling_style", "match_type"]
                         if c in raw_row.columns]
    encoded = pd.get_dummies(raw_row, columns=categorical_cols, dummy_na=False)
    encoded = encoded.reindex(columns=feature_cols, fill_value=0)
    return encoded


def predict(player: str, venue: str, opponent: str, match_type: str, task: str):
    with open(FEATURE_META_PATH) as f:
        meta = json.load(f)
    feature_cols = meta["features"]

    scaler = joblib.load(SCALER_PATH)
    model_path = REG_MODEL_PATH if task == "regression" else CLF_MODEL_PATH
    model = joblib.load(model_path)

    raw_row = build_feature_row(player, venue, opponent, match_type)
    aligned_row = align_to_training_columns(raw_row, feature_cols)
    scaled_row = scaler.transform(aligned_row)
    scaled_row = pd.DataFrame(scaled_row, columns=feature_cols)

    prediction = model.predict(scaled_row)[0]
    if task == "regression":
        prediction = max(0.0, float(prediction))  # runs can never be negative
        print(f"Predicted runs for {player} vs {opponent} at {venue}: {prediction:.1f}")
    else:
        label_enc_path = SAVED_MODELS_DIR / "tier_label_encoder.pkl"
        if label_enc_path.exists():
            enc = joblib.load(label_enc_path)
            prediction = str(enc.inverse_transform([int(prediction)])[0])
        print(f"Predicted performance tier for {player} vs {opponent} at {venue}: {prediction}")

    return prediction


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--player", required=True)
    parser.add_argument("--venue", default="", help="Match venue (optional)")
    parser.add_argument("--opponent", required=True)
    parser.add_argument("--match_type", default="t20i",
                        help="Match format: T20 / t20i, ODI, Test (case-insensitive)")
    parser.add_argument("--task", choices=["regression", "classification"], default="regression")
    args = parser.parse_args()

    predict(args.player, args.venue, args.opponent, args.match_type, args.task)