"""
build_player_match_stats.py

Combines the three files produced by cricsheet_parser.py:
    batting_performance.csv
    bowling_performance.csv
    match_summary.csv

into the single player_match_stats.csv shape that feature_engineering.py
expects (one row per player per match, batting + bowling stats together).

A player who only batted in a match gets 0s for bowling columns, and a
player who only bowled gets 0s for batting columns — merged via an OUTER
join on (match_id, player) so neither side's rows are dropped.

Run from ml-service/data/:
    python build_player_match_stats.py
"""

import re
from pathlib import Path

import pandas as pd

INPUT_DIR = Path(".")            # adjust if your 3 CSVs live elsewhere
OUTPUT_DIR = Path("processed")
OUTPUT_CSV = OUTPUT_DIR / "player_match_stats.csv"


def normalize_name(name: str) -> str:
    name = str(name).lower().strip()
    name = re.sub(r"[^a-z\s]", "", name)
    name = re.sub(r"\s+", " ", name)
    return name


def main():
    batting = pd.read_csv(INPUT_DIR / "batting_performance.csv")
    bowling = pd.read_csv(INPUT_DIR / "bowling_performance.csv")

    # Keep only the columns we need from each side before merging, to avoid
    # duplicate team/opposition/venue/date columns colliding under _x/_y.
    bat = batting[[
        "match_id", "format", "date", "player", "team", "opposition", "venue",
        "runs", "balls_faced", "fours", "sixes", "strike_rate",
    ]].copy()

    bowl = bowling[[
        "match_id", "format", "date", "player", "team", "opposition", "venue",
        "overs", "runs_conceded", "wickets", "economy",
    ]].rename(columns={"overs": "overs_bowled", "economy": "economy_rate"})

    merged = pd.merge(
        bat, bowl,
        on=["match_id", "player"],
        how="outer",
        suffixes=("_bat", "_bowl"),
    )

    # Prefer batting-side date/format/venue/team/opposition; fall back to
    # bowling-side for players who only appear in the bowling file.
    for col in ["date", "format", "venue", "team", "opposition"]:
        merged[col] = merged[f"{col}_bat"].fillna(merged[f"{col}_bowl"])
        merged.drop(columns=[f"{col}_bat", f"{col}_bowl"], inplace=True)

    numeric_cols = ["runs", "balls_faced", "fours", "sixes", "strike_rate",
                     "overs_bowled", "runs_conceded", "wickets", "economy_rate"]
    merged[numeric_cols] = merged[numeric_cols].fillna(0)

    # No fielding data in this pipeline — fill with 0 rather than dropping
    # the columns, so feature_engineering.py's schema expectations still hold.
    merged["catches"] = 0
    merged["stumpings"] = 0

    merged["player_name"] = merged["player"]
    merged["player_name_norm"] = merged["player"].apply(normalize_name)
    merged["match_date"] = merged["date"]
    merged["match_type"] = merged["format"]
    merged["team_a"] = merged["team"]
    merged["team_b"] = merged["opposition"]

    final_cols = [
        "player_name", "player_name_norm", "match_date", "venue", "match_type",
        "team_a", "team_b", "runs", "balls_faced", "fours", "sixes", "wickets",
        "overs_bowled", "runs_conceded", "catches", "stumpings",
        "strike_rate", "economy_rate",
    ]
    final = merged[final_cols].sort_values(["player_name_norm", "match_date"])

    OUTPUT_DIR.mkdir(parents=True, exist_ok=True)
    final.to_csv(OUTPUT_CSV, index=False)
    print(f"Saved {len(final)} player-match rows to {OUTPUT_CSV}")


if __name__ == "__main__":
    main()