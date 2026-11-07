"""
build_repaired_dataset.py

Constructs the repaired player-match dataset for Experiment model_v2.
Fixes the Cartesian join bug by aggregating batting and bowling statistics to a
strict, unambiguous (match_id, player) grain prior to merging.

Guarantees:
- Exactly ONE row per player per match.
- Zero Cartesian product duplication for multi-innings Test matches.
- Target runs are the total runs scored by the player in that match.
- Preserves all original source CSVs untouched.
- Outputs repaired dataset to: ml-service/experiments/model_v2/data/repaired_player_match_stats.csv
"""

import re
from pathlib import Path
import pandas as pd
import numpy as np

BASE_DIR = Path(__file__).resolve().parent
EXPERIMENT_DATA_DIR = BASE_DIR / "data"
RAW_DATA_DIR = BASE_DIR.parent.parent / "data"

BATTING_CSV = RAW_DATA_DIR / "batting_performance.csv"
BOWLING_CSV = RAW_DATA_DIR / "bowling_performance.csv"
OUTPUT_CSV = EXPERIMENT_DATA_DIR / "repaired_player_match_stats.csv"


def normalize_name(name: str) -> str:
    name = str(name).lower().strip()
    name = re.sub(r"[^a-z\s]", "", name)
    name = re.sub(r"\s+", " ", name)
    return name


def normalize_format(format_str: str) -> str:
    fmt = str(format_str).strip().lower()
    aliases = {
        "odi": "odi",
        "one day": "odi",
        "one day international": "odi",
        "t20": "t20i",
        "t20i": "t20i",
        "t20 international": "t20i",
        "test": "test",
        "tests": "test"
    }
    return aliases.get(fmt, fmt)


def build_repaired_dataset():
    EXPERIMENT_DATA_DIR.mkdir(parents=True, exist_ok=True)

    print("=" * 70)
    print("STEP 2: DATASET CONSTRUCTION REPAIR (model_v2)")
    print("=" * 70)

    batting = pd.read_csv(BATTING_CSV)
    bowling = pd.read_csv(BOWLING_CSV)

    print(f"Loaded raw batting records: {len(batting)} rows from {BATTING_CSV}")
    print(f"Loaded raw bowling records: {len(bowling)} rows from {BOWLING_CSV}")

    # Normalize player names and formats
    batting["player_name_norm"] = batting["player"].apply(normalize_name)
    batting["format_norm"] = batting["format"].apply(normalize_format)

    bowling["player_name_norm"] = bowling["player"].apply(normalize_name)
    bowling["format_norm"] = bowling["format"].apply(normalize_format)

    # -------------------------------------------------------------------------
    # 1. Aggregate Batting to (match_id, player_name_norm)
    # -------------------------------------------------------------------------
    print("\nAggregating batting records per player per match...")
    bat_agg = batting.groupby(["match_id", "player_name_norm"], as_index=False).agg(
        player_name=("player", "first"),
        match_date=("date", "first"),
        venue=("venue", "first"),
        match_type=("format_norm", "first"),
        team_a=("team", "first"),
        team_b=("opposition", "first"),
        runs=("runs", "sum"),
        balls_faced=("balls_faced", "sum"),
        fours=("fours", "sum"),
        sixes=("sixes", "sum"),
        innings_batted=("runs", "count")
    )
    bat_agg["strike_rate"] = np.where(
        bat_agg["balls_faced"] > 0,
        (bat_agg["runs"] / bat_agg["balls_faced"]) * 100.0,
        0.0
    )

    print(f"  Aggregated batting rows: {len(bat_agg)} unique (match_id, player) records")
    multi_bat = (bat_agg["innings_batted"] > 1).sum()
    print(f"  Multi-innings batting matches aggregated: {multi_bat} (Test matches)")

    # -------------------------------------------------------------------------
    # 2. Aggregate Bowling to (match_id, player_name_norm)
    # -------------------------------------------------------------------------
    print("\nAggregating bowling records per player per match...")
    bowling["overs"] = pd.to_numeric(bowling["overs"], errors="coerce").fillna(0.0)
    bowling["runs_conceded"] = pd.to_numeric(bowling["runs_conceded"], errors="coerce").fillna(0.0)
    bowling["wickets"] = pd.to_numeric(bowling["wickets"], errors="coerce").fillna(0)

    bowl_agg = bowling.groupby(["match_id", "player_name_norm"], as_index=False).agg(
        player_name=("player", "first"),
        match_date=("date", "first"),
        venue=("venue", "first"),
        match_type=("format_norm", "first"),
        team_a=("team", "first"),
        team_b=("opposition", "first"),
        overs_bowled=("overs", "sum"),
        runs_conceded=("runs_conceded", "sum"),
        wickets=("wickets", "sum"),
        innings_bowled=("wickets", "count")
    )
    bowl_agg["economy_rate"] = np.where(
        bowl_agg["overs_bowled"] > 0,
        bowl_agg["runs_conceded"] / bowl_agg["overs_bowled"],
        0.0
    )
    print(f"  Aggregated bowling rows: {len(bowl_agg)} unique (match_id, player) records")
    multi_bowl = (bowl_agg["innings_bowled"] > 1).sum()
    print(f"  Multi-innings bowling matches aggregated: {multi_bowl} (Test matches)")

    # -------------------------------------------------------------------------
    # 3. Outer Join Batting and Bowling at strict 1-to-1 match-player grain
    # -------------------------------------------------------------------------
    print("\nMerging aggregated batting and bowling records (1-to-1 outer join)...")
    merged = pd.merge(
        bat_agg,
        bowl_agg[[
            "match_id", "player_name_norm", "overs_bowled", "runs_conceded",
            "wickets", "economy_rate", "innings_bowled"
        ]],
        on=["match_id", "player_name_norm"],
        how="outer"
    )

    # For players who only bowled, backfill match metadata from bowling table
    if merged["player_name"].isna().any():
        bowl_meta = bowling.drop_duplicates(subset=["match_id", "player_name_norm"]).set_index(["match_id", "player_name_norm"])
        missing_mask = merged["player_name"].isna()
        missing_keys = merged.loc[missing_mask, ["match_id", "player_name_norm"]].set_index(["match_id", "player_name_norm"]).index
        
        for col, source_col in [
            ("player_name", "player"),
            ("match_date", "date"),
            ("venue", "venue"),
            ("match_type", "format_norm"),
            ("team_a", "team"),
            ("team_b", "opposition")
        ]:
            merged.loc[missing_mask, col] = bowl_meta.loc[missing_keys, source_col].values

    # Fill numeric statistics
    fill_zeros = [
        "runs", "balls_faced", "fours", "sixes", "strike_rate", "innings_batted",
        "overs_bowled", "runs_conceded", "wickets", "economy_rate", "innings_bowled"
    ]
    merged[fill_zeros] = merged[fill_zeros].fillna(0)

    merged["catches"] = 0
    merged["stumpings"] = 0

    # Ensure match_date is standard string format YYYY-MM-DD
    merged["match_date"] = pd.to_datetime(merged["match_date"]).dt.strftime("%Y-%m-%d")

    # Sort strictly chronologically by player and match_date
    final_cols = [
        "match_id", "player_name", "player_name_norm", "match_date", "venue", "match_type",
        "team_a", "team_b", "runs", "balls_faced", "fours", "sixes", "wickets",
        "overs_bowled", "runs_conceded", "catches", "stumpings",
        "strike_rate", "economy_rate", "innings_batted", "innings_bowled"
    ]
    repaired_df = merged[final_cols].sort_values(["player_name_norm", "match_date", "match_id"]).reset_index(drop=True)

    # -------------------------------------------------------------------------
    # 4. Integrity Checks
    # -------------------------------------------------------------------------
    dups = repaired_df.duplicated(subset=["match_id", "player_name_norm"], keep=False).sum()
    if dups > 0:
        raise ValueError(f"CRITICAL ERROR: Found {dups} duplicate (match_id, player) records in repaired dataset!")

    print("\n--- Integrity Verification ---")
    print(f"Total Repaired Player-Match Records: {len(repaired_df)}")
    print(f"Unique Matches: {repaired_df['match_id'].nunique()}")
    print(f"Unique Players: {repaired_df['player_name_norm'].nunique()}")
    print(f"Format Breakdown:\n{repaired_df['match_type'].value_counts().to_string()}")
    print(f"Duplicates on (match_id, player): {dups} (PASSED)")

    repaired_df.to_csv(OUTPUT_CSV, index=False)
    print(f"\nSaved repaired dataset to: {OUTPUT_CSV}")
    return repaired_df


if __name__ == "__main__":
    build_repaired_dataset()

