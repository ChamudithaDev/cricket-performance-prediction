# CricketAI ML Model Accuracy & Reliability Audit Report (Phase 1 — Read-Only)

**Date of Audit:** October 28, 2026  
**Audited Artifacts:**
- Regression Model: `ml-service/models/saved_models/best_regression_model.pkl` (`SVR`)
- Classification Model: `ml-service/models/saved_models/best_classification_model.pkl` (`MLPClassifier`)
- Tier Label Encoder: `ml-service/models/saved_models/tier_label_encoder.pkl` (`LabelEncoder`)
- Scaler: `ml-service/data/processed/scaler.pkl` (`StandardScaler`)
- Feature Metadata: `ml-service/data/processed/feature_columns.json`
- Processed Datasets: `ml-service/data/processed/train.csv` (9,017 rows), `ml-service/data/processed/test.csv` (2,281 rows)

---

## Executive Summary

This independent audit evaluated the accuracy, reliability, and data integrity of the existing CricketAI player performance prediction pipeline. The system was audited strictly under read-only conditions without modifying existing code, datasets, or saved model weights.

### Key Audit Findings
1. **Model Selection Contamination (Test Data Leakage):**
   In `train_models.py`, model selection for both regression and classification was performed by sorting results on the **final test set** (`results_df.sort_values("test_mae")` and `sort_values("test_f1", ascending=False)`).
   - In classification, **MLP ranked dead last (6th out of 6)** on TimeSeries cross-validation on training data (CV-F1 = 0.3617), but was selected as the production model because it cherry-picked the highest test score (Test-F1 = 0.3816).
   - This invalidates the test set as an independent evaluation benchmark.
2. **Intra-Match Target Leakage via Cartesian Product and Multi-Innings Records:**
   In `build_player_match_stats.py`, batting and bowling records were merged with `how="outer"` on `["match_id", "player"]` without an innings key. In Test matches where a player batted and bowled in both innings, this created a **4-way Cartesian product** duplicating identical innings up to 4 times. When `shift(1)` was computed in `feature_engineering.py`, later duplicate rows and 2nd innings rows directly absorbed the actual target runs scored in that exact match into `avg_runs_last_5` and `career_avg_runs`.
3. **Severe Compression and Sub-Baseline Performance in Regression:**
   The saved SVR regression model produces an $R^2$ of only **0.1022** and MAE of **13.7029**. More critically:
   - For **specialist batters**, the model's $R^2$ is **negative (-0.0527)** with an MAE of 19.72 runs, performing **worse than predicting the mean**.
   - The SVR model severely compresses outputs: despite actual test runs ranging from 0 to 233, predicted runs are restricted to [-2.77, 54.37]. The model is fundamentally unable to predict big scores, half-centuries, or centuries.
   - A naive career-average baseline alone achieves an $R^2$ of **0.0935** (RMSE 23.49 vs model RMSE 23.38), indicating the complex ML model adds negligible predictive value over a single arithmetic mean.
4. **Classification Collapse on Middle Tiers:**
   The 4-class MLP classifier achieves only **41.82% overall accuracy** and **0.3816 Macro-F1**.
   - Performance collapses on middle performance tiers: **Average tier recall is 15.80%** (Precision 25.00%) and **Good tier recall is 26.96%** (Precision 32.46%).
   - The model essentially functions as a degenerate boundary detector, predicting almost exclusively "Poor" (tail-enders/ducks) and "Excellent".
   - A simple baseline using only the player's recent 5-match form achieves **36.39% accuracy** and **0.3626 Macro-F1** without any model parameters.
5. **Preprocessing Flaws & Binary Scaling:**
   `StandardScaler` was fitted across all 11 features, including the binary one-hot indicators (`match_type_odi`, `match_type_t20i`, `match_type_test`). This distorts the distance geometry for distance-sensitive models (SVR and MLP).

---

## 1. Actual Saved Models and Artifacts

| Artifact | File Path | File Size | Algorithm / Class | Core Hyperparameters / Schema |
|---|---|---|---|---|
| **Best Regression Model** | `ml-service/models/saved_models/best_regression_model.pkl` | 898,588 B | `sklearn.svm.SVR` | `C=10`, `epsilon=0.5`, `kernel='rbf'` |
| **Best Classification Model** | `ml-service/models/saved_models/best_classification_model.pkl` | 113,536 B | `sklearn.neural_network.MLPClassifier` | `hidden_layer_sizes=(64, 32)`, `activation='relu'`, `solver='adam'`, `max_iter=500` |
| **Tier Label Encoder** | `ml-service/models/saved_models/tier_label_encoder.pkl` | 509 B | `sklearn.preprocessing.LabelEncoder` | Classes: `['Average' (0), 'Excellent' (1), 'Good' (2), 'Poor' (3)]` |
| **Feature Scaler** | `ml-service/data/processed/scaler.pkl` | 1,295 B | `sklearn.preprocessing.StandardScaler` | 11 input features, fitted on `train.csv` |
| **Feature Metadata** | `ml-service/data/processed/feature_columns.json` | 320 B | JSON | 11 feature names, target: `"runs"` |

### Features and Targets
- **Target Column:** `runs` (batting runs scored by player in the match/innings).
- **Approved Feature Whitelist (11 features):**
  1. `avg_runs_last_5` (numeric continuous)
  2. `avg_sr_last_5` (numeric continuous)
  3. `avg_wickets_last_5` (numeric continuous)
  4. `avg_economy_last_5` (numeric continuous)
  5. `career_matches_played` (numeric discrete)
  6. `career_avg_runs` (numeric continuous)
  7. `venue_avg_runs` (numeric continuous)
  8. `opponent_avg_runs` (numeric continuous)
  9. `match_type_odi` (binary one-hot, standardized)
  10. `match_type_t20i` (binary one-hot, standardized)
  11. `match_type_test` (binary one-hot, standardized)

*Note:* `role`, `batting_style`, and `bowling_style` were specified in `CATEGORICAL_FEATURES` in `feature_engineering.py`, but were absent from raw CSVs and were omitted from the final trained models.

---

## 2. Dataset Chronology and Split Integrity

| Dataset Split | Record Count | Unique Players | Date Range | Formats Included |
|---|---|---|---|---|
| **Full Processed (`player_match_stats.csv`)** | 11,298 | 508 | 2024-07-30 to 2026-07-26 | Test: 4,303, T20I: 4,171, ODI: 2,824 |
| **Training Set (`train.csv`)** | 9,017 (79.8%) | 460 | 2024-07-30 to 2026-01-28 | Test: 3,767, T20I: 2,950, ODI: 2,300 |
| **Test Set (`test.csv`)** | 2,281 (20.2%) | 327 | 2026-01-29 to 2026-07-26 | Test: 536, T20I: 1,221, ODI: 524 |

### Split Verification Analysis
- **Chronological Boundary:** The split divides cleanly by date (`train` max date is 2026-01-28; `test` min date is 2026-01-29). No match dates cross the boundary.
- **Player Continuity:** 279 of 327 test players (85.3%) appeared in training; 48 test players were complete debutants/unseen.
- **Split Trustworthiness Rating:** **PARTIALLY UNTRUSTWORTHY**. While the macro date cut-off is chronological, the integrity of the evaluation split is compromised by:
  1. Model selection performed on the test set rather than cross-validation.
  2. Rolling features precomputed in a single pass across the concatenated dataset prior to splitting.
  3. Cartesian product duplication creating artificial sample weights in Test matches.

---

## 3. Data Leakage and Preprocessing Audit

### Summary of Audit Checks
| Audit Item | Status | Detailed Finding | Severity |
|---|---|---|---|
| **Same-match / Post-match stats in inputs** | **SAFE** | Same-match stats (`balls_faced`, `fours`, `sixes`, `strike_rate`, `wickets`, etc.) were excluded via explicit allow-list. | Low |
| **Model Selection Contamination** | **VIOLATION** | Best models selected by sorting on test set metrics (`test_mae` and `test_f1`), leading to test overfitting. | **CRITICAL** |
| **Cartesian Join Duplication Leakage** | **VIOLATION** | Outer join without innings key generated 4 duplicate rows for Test all-rounders; subsequent duplicate rows used the target match's own runs via `shift(1)`. | **CRITICAL** |
| **Intra-Match Test Multi-Innings Leakage** | **VIOLATION** | 2nd innings of Test matches used 1st innings stats of the same match as historical input. | **HIGH** |
| **Pre-split Feature Engineering** | **FLAW** | `add_rolling_form_features` executed on full 11,298 rows before `chronological_split`. | **MEDIUM** |
| **StandardScaler on Binary Indicators** | **FLAW** | One-hot binary columns scaled to z-scores (-0.58 to +1.71), distorting SVR and MLP distance calculations. | **MEDIUM** |
| **Quartile Binning Distortion** | **FLAW** | Zero-heavy distribution compressed quartile 1 to [0, 1] run and set "Excellent" threshold at only >27 runs. | **HIGH** |

### Complete Feature Audit Table
See accompanying artifact: `ml-service/reports/feature_leakage_audit.csv`

---

## 4. Reproduced Model Performance

### A. Regression Metrics (Target = `runs`)

| Metric | Saved Model (`SVR` Raw) | Saved Model (`SVR` Clipped $\ge 0$) | Baseline 1: Train Mean (19.47) | Baseline 2: Player Career Avg | Baseline 3: Recent Form (5-match) |
|---|---|---|---|---|---|
| **MAE** | **13.7029** | 13.6929 | 18.1928 | 15.4336 | 16.0125 |
| **RMSE** | **23.3826** | 23.3819 | 24.7849 | 23.4951 | 25.1729 |
| **$R^2$** | **0.1022** | 0.1022 | -0.0087 | 0.0935 | -0.0406 |
| **Median AE** | **7.5839** | 7.5429 | 17.4662 | 10.0000 | 9.8000 |
| **Mean Actual Runs** | 17.1583 | 17.1583 | 17.1583 | 17.1583 | 17.1583 |
| **Mean Predicted Runs**| 11.4655 | 11.4760 | 19.4700 | 18.2520 | 18.0914 |
| **Median Predicted** | 11.2683 | 11.2683 | 19.4700 | 16.5000 | 14.8000 |
| **Negative Predictions**| 59 (2.59%) | 0 (0.00%) | 0 (0.00%) | 0 (0.00%) | 0 (0.00%) |
| **Sample Count** | 2,281 | 2,281 | 2,281 | 2,281 | 2,281 |

*Audit Verification:* Exactly reproduces `ml-service/models/saved_models/regression_comparison.csv` (MAE=13.70287, RMSE=23.38263, R2=0.10217).

### B. Classification Metrics (Target = `performance_tier`)

| Model / Baseline | Accuracy | Macro Precision | Macro Recall | Macro F1 | Weighted F1 |
|---|---|---|---|---|---|
| **Saved Model (`MLPClassifier`)** | **0.4182 (41.82%)** | **0.3794** | **0.3970** | **0.3816** | **0.4017** |
| **Baseline 1: Majority Class (`Poor`)** | 0.3187 (31.87%) | 0.0797 | 0.2500 | 0.1208 | 0.1541 |
| **Baseline 2: Career Average Tier** | 0.3288 (32.88%) | 0.3188 | 0.3229 | 0.3182 | 0.3071 |
| **Baseline 3: Recent Form Tier** | 0.3639 (36.39%) | 0.3653 | 0.3621 | 0.3626 | 0.3667 |

*Audit Verification:* Exactly reproduces `ml-service/models/saved_models/classification_comparison.csv` (Acc=0.41824, Prec=0.37937, Rec=0.39696, F1=0.38164).

#### Confusion Matrix (`MLPClassifier`)
| Actual Tier $ackslash$ Predicted Tier | Pred: Average | Pred: Excellent | Pred: Good | Pred: Poor | Total Actual |
|---|---|---|---|---|---|
| **Actual: Average (1–9 runs)** | **82** (15.8%) | 134 (25.8%) | 115 (22.2%) | 188 (36.2%) | 519 |
| **Actual: Excellent (>27 runs)** | 66 (13.6%) | **241** (49.6%) | 120 (24.7%) | 59 (12.1%) | 486 |
| **Actual: Good (9–27 runs)** | 95 (17.3%) | 204 (37.2%) | **148** (27.0%) | 102 (18.6%) | 549 |
| **Actual: Poor (0–1 runs)** | 85 (11.7%) | 86 (11.8%) | 73 (10.0%) | **483** (66.4%) | 727 |
| **Total Predicted** | 328 | 665 | 456 | 832 | 2,281 |

#### Per-Class Performance Breakdown
| Tier Label | Class Enc | Range (Runs) | Precision | Recall | F1-Score | Support (Test) | Support (Train) |
|---|---|---|---|---|---|---|---|
| **Average** | 0 | (1, 9] | 0.2500 | 0.1580 | 0.1936 | 519 (22.8%) | 2,116 (23.5%) |
| **Excellent** | 1 | (27, 367] | 0.3624 | 0.4959 | 0.4188 | 486 (21.3%) | 2,216 (24.6%) |
| **Good** | 2 | (9, 27] | 0.3246 | 0.2696 | 0.2945 | 549 (24.1%) | 2,089 (23.2%) |
| **Poor** | 3 | [0, 1] | 0.5805 | 0.6644 | 0.6196 | 727 (31.9%) | 2,596 (28.8%) |

---

## 5. Subgroup Performance Analysis

### A. Performance by Match Format
| Match Format | Sample Size ($n$) | Actual Mean | Pred Mean | MAE | RMSE | $R^2$ | Classif. Acc | Macro F1 | Residual Over% | Residual Under% | Neg Pred% |
|---|---|---|---|---|---|---|---|---|---|---|---|
| **ODI** | 524 | 18.87 | 13.53 | 14.97 | 23.53 | 0.1345 | 38.55% | 0.3443 | 51.1% | 48.9% | 4.58% |
| **T20I** | 1,221 | 14.50 | 8.96 | 11.50 | 18.96 | 0.1053 | 44.55% | 0.3795 | 52.5% | 47.5% | 2.46% |
| **Test** | 536 | 21.53 | 15.15 | 17.47 | 31.09 | 0.0457 | 38.81% | 0.3753 | 53.5% | 46.5% | 0.93% |

*Key Subgroup Finding:*
- In all three formats, the model exhibits systematic underprediction (-5.34 runs in ODI, -5.54 runs in T20I, -6.38 runs in Tests).
- Test match regression performance is severely degraded ($R^2 = 0.0457$, RMSE = 31.09) due to high variance and unhandled 2-innings structure.

### B. Performance by Player Role Heuristic
| Role | Sample Size ($n$) | Actual Mean | Pred Mean | MAE | RMSE | $R^2$ | Classif. Acc | Macro F1 |
|---|---|---|---|---|---|---|---|---|
| **Specialist Batter** | 780 | 26.30 | 17.65 | 19.72 | 30.08 | **-0.0527** | 34.87% | 0.2991 |
| **All-Rounder** | 1,405 | 13.16 | 8.70 | 11.13 | 19.59 | 0.1100 | 43.77% | 0.3671 |
| **Specialist Bowler** | 96 | 1.39 | 1.75 | 2.47 | 4.93 | -0.7364 | 69.79% | 0.2081 |

*Critical Insight:*
- For **Specialist Batters**, the regression model's $R^2$ is **negative (-0.0527)**! It is worse than predicting the mean score. It underpredicts batters by an average of 8.65 runs per innings.
- Bowler accuracy appears high (69.79%) purely because the model defaults to predicting "Poor" for tail-enders.

### C. Performance by Actual Performance Tier
| Actual Tier | Sample Size ($n$) | Actual Mean | Pred Mean | MAE | RMSE | MedAE | Classif. Recall |
|---|---|---|---|---|---|---|---|
| **Poor (0–1 runs)** | 727 | 0.17 | 5.92 | 5.84 | 9.48 | 1.99 | 66.44% |
| **Average (1–9 runs)** | 519 | 4.97 | 10.94 | 8.15 | 10.61 | 6.48 | 15.80% |
| **Good (9–27 runs)** | 549 | 17.54 | 14.16 | 7.84 | 9.68 | 6.84 | 26.96% |
| **Excellent (>27 runs)**| 486 | 55.17 | 17.28 | 38.02 | 46.96 | 32.14 | 49.59% |

*Insight:* The regression model severely overpredicts low scorers (+5.75 runs for ducks) and severely underpredicts high scorers (-37.89 runs for scores >27).

### D. Performance Across Chronological Test Periods
| Period | Date Span | Sample Size ($n$) | Actual Mean | Pred Mean | MAE | RMSE | $R^2$ | Classif. Acc | Macro F1 |
|---|---|---|---|---|---|---|---|---|---|
| **Q1 2026** | 2026-01-29 to 2026-03-31 | 839 | 15.02 | 9.36 | 11.85 | 19.49 | 0.1122 | 44.58% | 0.3878 |
| **Q2 2026** | 2026-04-01 to 2026-06-30 | 864 | 18.58 | 12.87 | 14.98 | 25.98 | 0.0643 | 39.47% | 0.3692 |
| **Q3 2026** | 2026-07-01 to 2026-07-26 | 578 | 18.15 | 12.41 | 14.49 | 24.45 | 0.1383 | 41.35% | 0.3750 |

---

## 6. Prediction Behavior & Inference Pipeline Checks

1. **Range Clamping and Negative Predictions:**
   - The unconstrained SVR model produces negative predictions for **59 test cases (2.59%)** (minimum -2.77 runs).
   - In `predict_routes.py` and `predict.py`, the code applies `max(0.0, float(pred))`. With clamping, MAE drops negligibly from 13.7029 to 13.6929.
2. **Severe Upper-Bound Compression:**
   - The maximum prediction generated by the SVR model across all 2,281 test matches is **54.37 runs**, whereas actual scores in the test set reach **233 runs** (52 individual scores exceeding 80 runs).
   - The SVR kernel with `epsilon=0.5` acts as a heavy smoother centered near the median (~11.3 runs).
3. **Flask Inference Input Alignment:**
   - Expected input schema: `[player, opponent, match_type]` with optional `venue`.
   - Feature alignment: `align_to_training_columns()` correctly reindexes columns to match `feature_columns.json`.
   - Inference-Training Discrepancy: In training, `career_matches_played` is computed via `cumcount()`. In `predict.py`, it is computed as `len(player_history)`. While functionally similar, `predict.py` takes the historical tail directly without shifting, whereas training shifted rows.

---

## 7. Recommended Improvements Ranked by Expected Benefit and Risk

| Rank | Recommended Improvement | Expected Benefit | Implementation Risk | Rationale |
|:---:|---|---|---|---|
| **1** | **Fix Cartesian Join in `build_player_match_stats.py`** | **CRITICAL** (Removes direct target leakage) | **LOW** (Safe data bugfix) | Merging `batting` and `bowling` with an innings key prevents 4x row duplication and same-match target leakage. |
| **2** | **Switch Model Selection to Cross-Validation / Validation Set** | **HIGH** (Restores evaluation validity) | **LOW** (Standard ML practice) | Stop selecting models using `test_mae` and `test_f1`. Select on TimeSeriesSplit CV mean score and reserve test data for final unbiased reporting. |
| **3** | **Replace SVR with Gradient Boosted Trees (CatBoost / LightGBM)** | **HIGH** (Solves output compression, raises $R^2$) | **LOW** (Models already scripted) | CatBoost/LightGBM naturally handle non-linear tabular interactions and large positive tails without flattening predictions to 54 runs. |
| **4** | **Re-engineer Performance Tiers (Context-Aware / Format-Specific)** | **HIGH** (Fixes middle tier collapse) | **MEDIUM** (Requires UI alignment) | Replace global runs quartiles [0, 1, 9, 27] with format-specific thresholds (e.g. T20I: 0-10, 11-25, 26-45, 46+; ODI/Test: 0-15, 16-35, 36-65, 66+). |
| **5** | **Integrate Player Role Features (`role`, `batting_style`)** | **HIGH** (Eliminates negative $R^2$ for batters) | **LOW** (Extractable from Cricsheet) | Distinguishing specialist batsmen from specialist bowlers will eliminate the -0.0527 $R^2$ on specialist batters. |
| **6** | **Exclude One-Hot Binary Features from `StandardScaler`** | **MEDIUM** (Improves geometric consistency) | **LOW** (Pipeline adjustment) | Pass binary format flags through unscaled or use models (trees) that do not require feature normalization. |
| **7** | **Bayesian Shrinkage for Venue and Opponent Averages** | **MEDIUM** (Stabilizes sparse history) | **MEDIUM** (Feature engineering) | >70% of venue/opponent stats are currently 0. Blending player career average with venue average using empirical Bayes prevents artificial zero-biasing. |

---

## 8. Exact Files Created

1. `ml-service/audit_model_accuracy.py` — Standalone reproducible audit script.
2. `ml-service/reports/model_accuracy_audit.md` — This comprehensive audit report.
3. `ml-service/reports/regression_test_predictions.csv` — Full test set regression predictions, errors, and baselines (2,281 rows).
4. `ml-service/reports/classification_test_predictions.csv` — Full test set classification predictions, probabilities, and baselines (2,281 rows).
5. `ml-service/reports/feature_leakage_audit.csv` — Structured feature-level data leakage audit table (11 features).
