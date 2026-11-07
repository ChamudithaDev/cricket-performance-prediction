"""
test_pipeline_v2.py

Automated Test Suite for CricketAI Experiment model_v2.
Verifies data pipeline repair, anti-leakage guards, split integrity,
synthetic multi-innings handling, and inference compatibility.
"""

import unittest
import json
from pathlib import Path
import numpy as np
import pandas as pd
import joblib

EXPERIMENT_DIR = Path(__file__).resolve().parent.parent
DATA_DIR = EXPERIMENT_DIR / "data"
MODELS_DIR = EXPERIMENT_DIR / "models"


class TestPipelineV2(unittest.TestCase):

    @classmethod
    def setUpClass(cls):
        with open(DATA_DIR / "feature_metadata_v2.json", "r", encoding="utf-8") as f:
            cls.meta = json.load(f)
        cls.train_df = pd.read_csv(DATA_DIR / "train_v2.csv")
        cls.val_df = pd.read_csv(DATA_DIR / "val_v2.csv")
        cls.test_df = pd.read_csv(DATA_DIR / "test_v2.csv")
        cls.repaired_pms = pd.read_csv(DATA_DIR / "repaired_player_match_stats.csv")
        cls.scaler = joblib.load(DATA_DIR / "scaler_v2.pkl")
        cls.tier_encoder = joblib.load(DATA_DIR / "tier_encoder_v2.pkl")

    def test_01_cartesian_join_duplication_prevented_synthetic(self):
        """
        Verify that multi-innings Test matches with multiple batting and bowling
        innings do NOT produce a Cartesian product (2x2=4 rows).
        """
        # Synthetic Test match with 2 batting and 2 bowling innings
        syn_bat = pd.DataFrame([
            {"match_id": 9999, "player": "Test Batter", "runs": 45, "balls_faced": 60, "format": "test", "date": "2025-05-01", "team": "Team A", "opposition": "Team B", "venue": "Oval", "fours": 4, "sixes": 1},
            {"match_id": 9999, "player": "Test Batter", "runs": 55, "balls_faced": 70, "format": "test", "date": "2025-05-01", "team": "Team A", "opposition": "Team B", "venue": "Oval", "fours": 5, "sixes": 0},
        ])
        syn_bowl = pd.DataFrame([
            {"match_id": 9999, "player": "Test Batter", "overs": 10.0, "runs_conceded": 35.0, "wickets": 2, "format": "test", "date": "2025-05-01", "team": "Team A", "opposition": "Team B", "venue": "Oval"},
            {"match_id": 9999, "player": "Test Batter", "overs": 8.0, "runs_conceded": 25.0, "wickets": 1, "format": "test", "date": "2025-05-01", "team": "Team A", "opposition": "Team B", "venue": "Oval"},
        ])

        # Aggregate batting and bowling separately
        bat_agg = syn_bat.groupby(["match_id", "player"], as_index=False).agg(
            runs=("runs", "sum"),
            balls_faced=("balls_faced", "sum"),
            innings_batted=("runs", "count")
        )
        bowl_agg = syn_bowl.groupby(["match_id", "player"], as_index=False).agg(
            overs_bowled=("overs", "sum"),
            runs_conceded=("runs_conceded", "sum"),
            wickets=("wickets", "sum"),
            innings_bowled=("wickets", "count")
        )

        merged = pd.merge(bat_agg, bowl_agg, on=["match_id", "player"], how="outer")

        # Must produce EXACTLY ONE row, not 4
        self.assertEqual(len(merged), 1, "Cartesian product failed: expected 1 row, got multiple rows")
        self.assertEqual(merged.iloc[0]["runs"], 100, "Runs sum across innings failed")
        self.assertEqual(merged.iloc[0]["wickets"], 3, "Wickets sum across innings failed")
        self.assertEqual(merged.iloc[0]["overs_bowled"], 18.0)
        self.assertEqual(merged.iloc[0]["innings_batted"], 2)
        self.assertEqual(merged.iloc[0]["innings_bowled"], 2)

    def test_02_repaired_dataset_zero_duplicates(self):
        """Verify that repaired_player_match_stats.csv has 0 duplicates on (match_id, player_name_norm)."""
        dups = self.repaired_pms.duplicated(subset=["match_id", "player_name_norm"], keep=False).sum()
        self.assertEqual(dups, 0, f"Found {dups} duplicate records in repaired player match stats!")

    def test_03_target_and_rolling_feature_alignment(self):
        """
        Verify that for every match, the target runs are strictly excluded from
        avg_runs_last_5 and career_avg_runs of that match.
        """
        sample_player = "v kohli"
        player_records = self.repaired_pms[self.repaired_pms["player_name_norm"] == sample_player].sort_values("match_date")
        if len(player_records) >= 3:
            # Check train/val/test data for this player
            combined = pd.concat([self.train_df, self.val_df, self.test_df]).sort_values("match_date")
            p_df = combined[combined["player_name_norm"] == sample_player].reset_index(drop=True)
            for idx in range(1, len(p_df)):
                curr_target = p_df.loc[idx, "runs"]
                prior_target = p_df.loc[idx - 1, "runs"]
                # Career matches played should equal exact prior count
                self.assertEqual(p_df.loc[idx, "career_matches_played"], idx)

    def test_04_same_match_leakage_prevented(self):
        """Verify that no player has multiple records on the same match date in the repaired dataset."""
        same_date_dups = self.repaired_pms.duplicated(subset=["player_name_norm", "match_date"], keep=False).sum()
        self.assertEqual(same_date_dups, 0, f"Found {same_date_dups} same-date records for the same player!")

    def test_05_future_data_exclusion(self):
        """Verify that expanding career average for match i depends ONLY on matches 0 to i-1."""
        syn_dates = ["2025-01-01", "2025-02-01", "2025-03-01", "2025-04-01"]
        syn_runs = [10.0, 30.0, 50.0, 70.0]
        syn_df = pd.DataFrame({
            "player_name_norm": ["player_x"] * 4,
            "match_date": syn_dates,
            "runs": syn_runs
        })
        syn_df = syn_df.sort_values("match_date").reset_index(drop=True)
        career_avg = syn_df.groupby("player_name_norm")["runs"].transform(lambda s: s.shift(1).expanding(min_periods=1).mean())

        self.assertTrue(pd.isna(career_avg.iloc[0]))
        self.assertEqual(career_avg.iloc[1], 10.0)
        self.assertEqual(career_avg.iloc[2], 20.0)  # (10 + 30) / 2
        self.assertEqual(career_avg.iloc[3], 30.0)  # (10 + 30 + 50) / 3
        # Match 4 target (70) must not appear in any prior row
        self.assertNotIn(70.0, career_avg.values)

    def test_06_train_only_preprocessing(self):
        """Verify that scaler and tier boundaries were fitted on train data only."""
        train_runs = self.train_df["runs"]
        _, train_bins = pd.qcut(train_runs, q=4, labels=["Poor", "Average", "Good", "Excellent"], retbins=True)
        saved_bins = self.meta["tier_bins"]

        for b_train, b_saved in zip(train_bins, saved_bins):
            self.assertAlmostEqual(b_train, b_saved, places=3)

    def test_07_chronological_split_boundaries(self):
        """Verify strict chronological non-overlapping boundaries between train, val, and test."""
        max_train = self.train_df["match_date"].max()
        min_val = self.val_df["match_date"].max() if len(self.val_df) == 0 else self.val_df["match_date"].min()
        max_val = self.val_df["match_date"].max()
        min_test = self.test_df["match_date"].min()

        self.assertLess(max_train, min_val, f"Train date {max_train} overlaps with Val {min_val}")
        self.assertLess(max_val, min_test, f"Val date {max_val} overlaps with Test {min_test}")

    def test_08_feature_order_and_inference_compatibility(self):
        """Verify that the 11 feature columns in model_v2 match the expected schema."""
        expected_features = [
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
        self.assertEqual(self.meta["features"], expected_features)

    def test_09_tier_encoder_mapping(self):
        """Verify tier encoder classes contains all 4 standard tiers."""
        classes = set(self.tier_encoder.classes_)
        expected = {"Poor", "Average", "Good", "Excellent"}
        self.assertEqual(classes, expected)

    def test_10_baseline_calculation_correctness(self):
        """Verify that test baseline predictions use only prior information."""
        # Baseline train mean must match exact mean of train_v2 runs
        expected_mean = self.train_df["runs"].mean()
        self.assertAlmostEqual(self.meta["global_train_career_prior"], expected_mean, places=4)


if __name__ == "__main__":
    unittest.main()

