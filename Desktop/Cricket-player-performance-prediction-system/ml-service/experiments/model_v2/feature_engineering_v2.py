"""
feature_engineering_v2.py

Feature engineering pipeline for Experiment model_v2.
Computes pre-match historical, rolling, venue, opponent, and format features
with STRICT ZERO DATA LEAKAGE:
- Features for any match use ONLY information available strictly prior to that match date.
- Chronological splitting:
    Train:      2024-07-30 to 2025-10-31 (5,526 rows, 63.0%)
    Validation: 2025-11-01 to 2026-01-28 (1,286 rows, 14.7%)
    Final Test: 2026-01-29 to 2026-07-26 (1,966 rows, 22.4%)
- Preprocessing (StandardScaler, LabelEncoder, global priors) fitted on TRAIN only.
- Final Test set is reserved as an untouched holdout.
"""

import json
from pathlib import Path
import joblib
import numpy as np
import pandas as pd
from sklearn.preprocessing import StandardScaler, LabelEncoder

BASE_DIR = Path(__file__).resolve().parent
DATA_DIR = BASE_DIR / "data"
INPUT_CSV = DATA_DIR / "repaired_player_match_stats.csv"

TRAIN_SPLIT_DATE = "2025-11-01"
TEST_SPLIT_DATE = "2026-01-29"

ROLLING_WINDOW = 5
BAYES_SHRINKAGE_WEIGHT = 3.0


def compute_historical_features(df: pd.DataFrame) -> pd.DataFrame:
    """
    Computes expanding and rolling historical statistics for each player.
    Crucially, df must be sorted by match_date so shift(1) strictly references
    matches played prior to the current match.
    """
    df = df.copy()
    df["match_date"] = pd.to_datetime(df["match_date"])
    df = df.sort_values(["player_name_norm", "match_date", "match_id"]).reset_index(drop=True)

    grp = df.groupby("player_name_norm")

    # 1. Recent form over last 5 matches (shift(1) ensures current match is excluded)
    df["avg_runs_last_5"] = grp["runs"].transform(
        lambda s: s.shift(1).rolling(ROLLING_WINDOW, min_periods=1).mean()
    )
    df["avg_sr_last_5"] = grp["strike_rate"].transform(
        lambda s: s.shift(1).rolling(ROLLING_WINDOW, min_periods=1).mean()
    )
    df["avg_wickets_last_5"] = grp["wickets"].transform(
        lambda s: s.shift(1).rolling(ROLLING_WINDOW, min_periods=1).mean()
    )
    df["avg_economy_last_5"] = grp["economy_rate"].transform(
        lambda s: s.shift(1).rolling(ROLLING_WINDOW, min_periods=1).mean()
    )

    # 2. Career experience and career batting average strictly before match
    df["career_matches_played"] = grp.cumcount()
    df["career_avg_runs"] = grp["runs"].transform(
        lambda s: s.shift(1).expanding(min_periods=1).mean()
    )
    df["career_balls_faced"] = grp["balls_faced"].transform(
        lambda s: s.shift(1).expanding(min_periods=1).sum()
    )
    df["career_overs_bowled"] = grp["overs_bowled"].transform(
        lambda s: s.shift(1).expanding(min_periods=1).sum()
    )

    # 3. Pre-match role profile (historical exposure)
    # Batter: career balls faced >= 30 and career overs bowled <= 10
    # Bowler: career overs bowled > 10 and career balls faced < 30
    # All-Rounder: career overs bowled > 10 and career balls faced >= 30
    # Unestablished / Debutant: default prior
    df["career_balls_faced"] = df["career_balls_faced"].fillna(0)
    df["career_overs_bowled"] = df["career_overs_bowled"].fillna(0)

    # 4. Venue & Opponent historical statistics (strictly prior matches)
    # Venue expanding mean
    venue_grp = df.groupby(["player_name_norm", "venue"])["runs"]
    df["venue_matches_played"] = venue_grp.transform(lambda s: s.shift(1).expanding(min_periods=1).count()).fillna(0)
    df["raw_venue_avg_runs"] = venue_grp.transform(lambda s: s.shift(1).expanding(min_periods=1).mean())

    # Opponent expanding mean
    opp_grp = df.groupby(["player_name_norm", "team_b"])["runs"]
    df["opp_matches_played"] = opp_grp.transform(lambda s: s.shift(1).expanding(min_periods=1).count()).fillna(0)
    df["raw_opponent_avg_runs"] = opp_grp.transform(lambda s: s.shift(1).expanding(min_periods=1).mean())

    return df


def apply_empirical_bayes_shrinkage(df: pd.DataFrame, global_career_prior: float) -> pd.DataFrame:
    """
    Applies empirical Bayes shrinkage to venue and opponent averages:
    Shrinks raw venue average toward the player's career average (or global prior if debutant),
    preventing 70%+ zero-inflation when a player hasn't played at a venue.
    """
    df = df.copy()

    # Fill cold-start career average with global training prior
    eff_career_avg = df["career_avg_runs"].fillna(global_career_prior)

    # Venue shrinkage
    v_n = df["venue_matches_played"]
    v_raw = df["raw_venue_avg_runs"].fillna(eff_career_avg)
    df["venue_avg_runs"] = (v_n * v_raw + BAYES_SHRINKAGE_WEIGHT * eff_career_avg) / (v_n + BAYES_SHRINKAGE_WEIGHT)

    # Opponent shrinkage
    o_n = df["opp_matches_played"]
    o_raw = df["raw_opponent_avg_runs"].fillna(eff_career_avg)
    df["opponent_avg_runs"] = (o_n * o_raw + BAYES_SHRINKAGE_WEIGHT * eff_career_avg) / (o_n + BAYES_SHRINKAGE_WEIGHT)

    # Cold start fill for other historical stats
    df["avg_runs_last_5"] = df["avg_runs_last_5"].fillna(eff_career_avg)
    df["career_avg_runs"] = eff_career_avg
    df["avg_sr_last_5"] = df["avg_sr_last_5"].fillna(0.0)
    df["avg_wickets_last_5"] = df["avg_wickets_last_5"].fillna(0.0)
    df["avg_economy_last_5"] = df["avg_economy_last_5"].fillna(0.0)

    return df


def run_feature_pipeline():
    print("=" * 70)
    print("STEP 3 & 4: FEATURE REBUILD & CHRONOLOGICAL SPLIT (model_v2)")
    print("=" * 70)

    df_raw = pd.read_csv(INPUT_CSV)
    print(f"Loaded repaired player-match records: {len(df_raw)} from {INPUT_CSV}")

    # 1. Compute historical features across chronological match order
    df_features = compute_historical_features(df_raw)

    # 2. Chronological Split: Train, Validation, Final Test
    df_features["match_date_str"] = df_features["match_date"].dt.strftime("%Y-%m-%d")
    
    train_mask = df_features["match_date_str"] < TRAIN_SPLIT_DATE
    val_mask = (df_features["match_date_str"] >= TRAIN_SPLIT_DATE) & (df_features["match_date_str"] < TEST_SPLIT_DATE)
    test_mask = df_features["match_date_str"] >= TEST_SPLIT_DATE

    train_df = df_features[train_mask].copy()
    val_df = df_features[val_mask].copy()
    test_df = df_features[test_mask].copy()

    print("\n--- Chronological Split Boundaries ---")
    print(f"Training:   {train_df['match_date_str'].min()} to {train_df['match_date_str'].max()} | {len(train_df)} rows ({len(train_df)/len(df_features)*100:.1f}%)")
    print(f"Validation: {val_df['match_date_str'].min()} to {val_df['match_date_str'].max()} | {len(val_df)} rows ({len(val_df)/len(df_features)*100:.1f}%)")
    print(f"Final Test: {test_df['match_date_str'].min()} to {test_df['match_date_str'].max()} | {len(test_df)} rows ({len(test_df)/len(df_features)*100:.1f}%)")

    # 3. Fit Global Prior on TRAIN ONLY
    global_train_career_prior = train_df["runs"].mean()
    print(f"\nGlobal Training Mean Target Runs (Fitted Prior): {global_train_career_prior:.2f}")

    # 4. Apply Empirical Bayes Shrinkage using Train Prior
    train_df = apply_empirical_bayes_shrinkage(train_df, global_train_career_prior)
    val_df = apply_empirical_bayes_shrinkage(val_df, global_train_career_prior)
    test_df = apply_empirical_bayes_shrinkage(test_df, global_train_career_prior)

    # 5. One-Hot Encode Match Format
    for df in [train_df, val_df, test_df]:
        df["match_type_odi"] = (df["match_type"] == "odi").astype(int)
        df["match_type_t20i"] = (df["match_type"] == "t20i").astype(int)
        df["match_type_test"] = (df["match_type"] == "test").astype(int)

    # 6. Feature Selection (Keep 11 features matching inference interface + unscaled version)
    feature_cols = [
        "avg_runs_last_5",
        "avg_sr_last_5",
        "avg_wickets_last_5",
        "avg_economy_last_5",
        "career_matches_played",
        "career_avg_runs",
        "venue_avg_runs",
        "opponent_avg_runs",
        "match_type_odi",
        "match_type_t20i",
        "match_type_test"
    ]
    numeric_to_scale = [
        "avg_runs_last_5",
        "avg_sr_last_5",
        "avg_wickets_last_5",
        "avg_economy_last_5",
        "career_matches_played",
        "career_avg_runs",
        "venue_avg_runs",
        "opponent_avg_runs"
    ]

    # 7. Scaler: Fitted on TRAIN ONLY. Note: Only numeric features scaled, leaving binary dummies unscaled!
    scaler = StandardScaler()
    scaler.fit(train_df[numeric_to_scale])

    # Store scaled versions for distance-based models (SVR/MLP)
    for name, df in [("train", train_df), ("val", val_df), ("test", test_df)]:
        scaled_vals = scaler.transform(df[numeric_to_scale])
        for idx, col in enumerate(numeric_to_scale):
            df[f"{col}_scaled"] = scaled_vals[:, idx]

    # 8. Classification Tiers: Computed strictly on TRAIN runs quartiles
    train_tiers, tier_bins = pd.qcut(train_df["runs"], q=4, labels=["Poor", "Average", "Good", "Excellent"], retbins=True)
    val_tiers = pd.cut(val_df["runs"], bins=tier_bins, labels=["Poor", "Average", "Good", "Excellent"], include_lowest=True)
    test_tiers = pd.cut(test_df["runs"], bins=tier_bins, labels=["Poor", "Average", "Good", "Excellent"], include_lowest=True)

    print(f"\n--- Classification Tier Boundaries (Fitted on Train Runs) ---")
    print(f"Quartile cutoffs: {tier_bins}")
    print(f"Train Tier Distribution:\n{train_tiers.value_counts().to_string()}")
    print(f"Val Tier Distribution:\n{val_tiers.value_counts().to_string()}")
    print(f"Test Tier Distribution:\n{test_tiers.value_counts().to_string()}")

    # Fit LabelEncoder on Train tiers
    tier_encoder = LabelEncoder()
    tier_encoder.fit(train_tiers.astype(str))

    train_df["performance_tier"] = train_tiers.astype(str)
    train_df["performance_tier_enc"] = tier_encoder.transform(train_tiers.astype(str))

    val_df["performance_tier"] = val_tiers.astype(str)
    val_df["performance_tier_enc"] = tier_encoder.transform(val_tiers.astype(str))

    test_df["performance_tier"] = test_tiers.astype(str)
    test_df["performance_tier_enc"] = tier_encoder.transform(test_tiers.astype(str))

    # 9. Save Processed Splits and Artifacts to experiments/model_v2/data/
    train_out = DATA_DIR / "train_v2.csv"
    val_out = DATA_DIR / "val_v2.csv"
    test_out = DATA_DIR / "test_v2.csv"
    scaler_out = DATA_DIR / "scaler_v2.pkl"
    encoder_out = DATA_DIR / "tier_encoder_v2.pkl"
    meta_out = DATA_DIR / "feature_metadata_v2.json"

    train_df.to_csv(train_out, index=False)
    val_df.to_csv(val_out, index=False)
    test_df.to_csv(test_out, index=False)

    joblib.dump(scaler, scaler_out)
    joblib.dump(tier_encoder, encoder_out)

    metadata = {
        "experiment_version": "model_v2",
        "target": "runs",
        "features": feature_cols,
        "scaled_features_for_distance_models": [f"{c}_scaled" if c in numeric_to_scale else c for c in feature_cols],
        "numeric_features": numeric_to_scale,
        "categorical_binary_features": ["match_type_odi", "match_type_t20i", "match_type_test"],
        "train_period": [str(train_df['match_date_str'].min()), str(train_df['match_date_str'].max())],
        "val_period": [str(val_df['match_date_str'].min()), str(val_df['match_date_str'].max())],
        "test_period": [str(test_df['match_date_str'].min()), str(test_df['match_date_str'].max())],
        "train_rows": len(train_df),
        "val_rows": len(val_df),
        "test_rows": len(test_df),
        "tier_bins": [float(b) for b in tier_bins],
        "tier_classes": list(tier_encoder.classes_),
        "global_train_career_prior": float(global_train_career_prior),
        "bayes_shrinkage_weight": BAYES_SHRINKAGE_WEIGHT
    }

    with open(meta_out, "w", encoding="utf-8") as f:
        json.dump(metadata, f, indent=2)

    print("\nSaved Pipeline Artifacts:")
    print(f"  {train_out}")
    print(f"  {val_out}")
    print(f"  {test_out}")
    print(f"  {scaler_out}")
    print(f"  {encoder_out}")
    print(f"  {meta_out}")
    print("\nFeature Engineering Pipeline completed successfully.")


if __name__ == "__main__":
    run_feature_pipeline()

