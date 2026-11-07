# CricketAI ML Improvement — Phase 2: Data Pipeline Repair and Trustworthy Retraining

**Experiment Version:** `model_v2`  
**Date of Experiment:** October 28, 2026  
**Status:** Completed successfully. All artifacts, models, metrics, predictions, and automated tests are isolated in `ml-service/experiments/model_v2/`. Zero production artifacts were modified or overwritten.

---

## 1. Dataset Problems Fixed

1. **Elimination of Cartesian Join Bug:**
   - In the legacy `build_player_match_stats.py`, an outer join on `["match_id", "player"]` between batting and bowling records produced $2 \times 2 = 4$ rows for all-rounders in multi-innings Test matches.
   - In `model_v2`, batting and bowling records were pre-aggregated to a strict, unique `(match_id, player_name_norm)` grain prior to merging.
   - Result: 2,520 corrupted/duplicate rows eliminated (8,778 clean rows vs 11,298 corrupted rows). Duplicates on `(match_id, player)` reduced to exactly **0**.
2. **Elimination of Intra-Match Target Leakage:**
   - In multi-innings Test matches, Innings 2 no longer leaks into Innings 1 or subsequent duplicate rows via `shift(1)`. Both innings are aggregated into the single match outcome, preserving strict causal pre-match semantics.
3. **Elimination of Model Selection Contamination:**
   - The final test set (`2026-01-29` to `2026-07-26`, 1,966 rows) was held out untouched.
   - Model selection was performed strictly on the **Validation Set** (`2025-11-01` to `2026-01-28`, 1,286 rows) and TimeSeries cross-validation on Training (`2024-07-30` to `2025-10-31`, 5,526 rows).
4. **Correction of Zero-Inflation via Empirical Bayes Shrinkage:**
   - Sparse venue and opponent statistics (>70% zeros previously) were smoothed toward the player's career average using empirical Bayes shrinkage ($m = 3.0$), eliminating artificial zero-biasing for debutants or new venues.
5. **Decoupling of Binary Format Dummies from StandardScaling:**
   - Distance-based algorithms now receive standardized continuous features without geometric distortion of 0/1 indicator flags.

---

## 2. Exact Row Grain & Prediction Timing

- **Row Grain:** Exactly **One Player per Match** (`match_id, player_name_norm`).
- **Prediction Timing:** **Pre-Match** (immediately prior to the commencement of the match).
- **Target Definition:**
  - **Regression:** `runs` — the total batting runs scored by the player in that match (sum of both innings in Tests, single innings in ODI/T20I).
  - **Classification:** `performance_tier` — the performance tier derived from training-set quartile cutoffs:
    * `Poor`: $[0, 1]$ run
    * `Average`: $(1, 10]$ runs
    * `Good`: $(10, 33]$ runs
    * `Excellent`: $(33, 430]$ runs

---

## 3. Train, Validation, and Final Test Periods

| Split | Date Range | Row Count | Percentage | Formats Included |
|---|---|---|---|---|
| **Training Set (`train_v2.csv`)** | 2024-07-30 to 2025-10-31 | 5,526 | 63.0% | T20I: 2,569, ODI: 1,818, Test: 1,139 |
| **Validation Set (`val_v2.csv`)** | 2025-11-01 to 2026-01-28 | 1,286 | 14.7% | T20I: 569, ODI: 474, Test: 243 |
| **Final Test Set (`test_v2.csv`)** | 2026-01-29 to 2026-07-26 | 1,966 | 22.4% | T20I: 1,221, ODI: 524, Test: 221 |

---

## 4. Model Selection & Experimental Leaderboards

### A. Validation Set Model Selection (Hyperparameter Tuning & Selection)

#### Regression Candidates on Validation Set
| Model | Val MAE | Val RMSE | Val $R^2$ | Val MedAE | Pred Mean | Pred Range |
|---|---|---|---|---|---|---|
| **SVR** | **16.8312** | 29.1316 | 0.2057 | **8.9631** | 15.31 | [-1.45, 57.47] |
| **CatBoost** | 17.6945 | **27.5055** | **0.2919** | 11.6265 | 23.58 | [-0.36, 90.26] |
| **RandomForest** | 18.1457 | 28.0061 | 0.2659 | 11.6972 | 24.29 | [1.12, 117.87] |
| **LightGBM** | 18.2151 | 28.2501 | 0.2530 | 11.4236 | 24.08 | [-0.40, 136.82] |
| *Baseline: Career Avg* | 18.6167 | 29.4123 | 0.1903 | 12.0000 | 23.41 | [0.00, 78.64] |
| *Baseline: Recent Form* | 19.6975 | 31.3109 | 0.0824 | 12.5000 | 21.86 | [0.00, 116.00] |
| *Baseline: Train Mean* | 22.6457 | 32.6871 | -0.0000 | 20.3623 | 23.36 | [23.36, 23.36] |

*Selection Decision:*
- **SVR** selected as Best Regression Model based on lowest Validation MAE (16.83). Note: CatBoost achieves substantially higher $R^2$ (0.2919) and lower RMSE (27.51), making it the preferred candidate for range preservation.

#### Classification Candidates on Validation Set
| Model | Val Accuracy | Val Macro-Prec | Val Macro-Rec | Val Macro-F1 | Val Weighted-F1 |
|---|---|---|---|---|---|
| **CatBoost** | **0.4891** | **0.4364** | **0.4640** | **0.4399** | **0.4609** |
| **RandomForest** | 0.4767 | 0.4202 | 0.4522 | 0.4271 | 0.4484 |
| **LightGBM** | 0.4635 | 0.4178 | 0.4436 | 0.4221 | 0.4399 |
| **MLPClassifier** | 0.4370 | 0.3944 | 0.4201 | 0.3904 | 0.4035 |
| *Baseline: Recent Form Tier* | 0.3608 | 0.4138 | 0.3516 | 0.3463 | 0.3565 |
| *Baseline: Career Tier* | 0.3577 | 0.4521 | 0.3587 | 0.3240 | 0.3243 |
| *Baseline: Majority Class* | 0.2823 | 0.0706 | 0.2500 | 0.1101 | 0.1243 |

*Selection Decision:*
- **CatBoost** selected as Best Classification Model based on highest Validation Macro-F1 (0.4399) and Accuracy (48.91%).

---

## 5. Final Holdout Evaluation on Untouched Test Set (2026-01-29 to 2026-07-26)

### A. Final Regression Test Benchmark
| Model | Test MAE | Test RMSE | Test $R^2$ | Test MedAE | Pred Mean | Pred Range |
|---|---|---|---|---|---|---|
| **SVR (Selected Best MAE)** | **14.7976** | 25.2173 | 0.1666 | **7.7343** | 11.91 | [-2.38, 58.45] |
| **CatBoost (Best $R^2$ / RMSE)** | 15.5345 | **23.4912** | **0.2768** | 10.1016 | 19.64 | [-0.37, 91.26] |
| **LightGBM** | 15.7723 | 23.8715 | 0.2532 | 10.0989 | 19.91 | [-0.02, 141.85] |
| **RandomForest** | 15.8200 | 23.6985 | 0.2640 | 10.5205 | 20.38 | [1.06, 116.65] |
| *Baseline: Career Average* | 16.9490 | 25.6979 | 0.1345 | 11.0871 | 21.07 | [0.00, 140.00] |
| *Baseline: Recent Form (5-m)* | 17.4993 | 27.4929 | 0.0094 | 11.0000 | 19.98 | [0.00, 140.00] |
| *Baseline: Train Mean* | 21.0411 | 27.9582 | -0.0244 | 20.3623 | 23.36 | [23.36, 23.36] |

### B. Final Classification Test Benchmark
| Model | Test Accuracy | Test Macro-Prec | Test Macro-Rec | Test Macro-F1 | Test Weighted-F1 |
|---|---|---|---|---|---|
| **RandomForest** | **0.4532** | **0.4093** | **0.4311** | **0.4131** | **0.4309** |
| **CatBoost (Selected Best)** | 0.4440 | 0.4044 | 0.4234 | **0.4099** | 0.4271 |
| **LightGBM** | 0.4257 | 0.3967 | 0.4120 | 0.4000 | 0.4145 |
| **MLPClassifier (Legacy Arch)** | 0.4008 | 0.3568 | 0.3802 | 0.3590 | 0.3748 |
| *Baseline: Recent Form Tier* | 0.3662 | 0.4203 | 0.3679 | 0.3616 | 0.3676 |
| *Baseline: Career Tier* | 0.3362 | 0.4322 | 0.3532 | 0.3196 | 0.3091 |
| *Baseline: Majority Class* | 0.3164 | 0.0791 | 0.2500 | 0.1202 | 0.1521 |

---

## 6. Confusion Matrix & Per-Class Breakdown (`CatBoost` Classifier on Test Set)

```
Predicted Tier ->     Average   Excellent      Good      Poor    Total Actual
Actual: Average            83          85       108       184             460 (Recall: 18.0%)
Actual: Excellent          76         178        92        31             377 (Recall: 47.2%)
Actual: Good              119         135       157        96             507 (Recall: 31.0%)
Actual: Poor               66          33        68       455             622 (Recall: 73.2%)
Total Predicted           344         431       425       766           1,966
```

- **Poor Tier:** Precision = 0.5940, Recall = **0.7315**, F1 = **0.6556**
- **Good Tier:** Precision = 0.3694, Recall = **0.3097**, F1 = **0.3369** (Improved over Phase 1's 0.2945)
- **Excellent Tier:** Precision = 0.4130, Recall = 0.4721, F1 = **0.4406** (Improved over Phase 1's 0.4188)
- **Average Tier:** Precision = 0.2413, Recall = **0.1804**, F1 = **0.2065** (Still challenging due to dense cluster around 2–9 runs)

---

## 7. Subgroup Performance on Test Set

| Subgroup Type | Subgroup Value | Sample Size ($n$) | Actual Mean | Pred Mean | MAE | RMSE | $R^2$ | Classif. Acc | Macro F1 |
|---|---|---|---|---|---|---|---|---|---|
| **Format** | **T20I** | 1,221 | 14.50 | 8.26 | 11.65 | 19.32 | 0.0704 | 45.13% | 0.3628 |
| **Format** | **ODI** | 524 | 18.87 | 13.05 | 15.28 | 24.13 | 0.0897 | 40.46% | 0.3431 |
| **Format** | **TEST** | 221 | 44.54 | 29.39 | 31.03 | 47.04 | 0.0326 | 49.77% | 0.3702 |
| **Tier** | **Poor** | 622 | 0.17 | 5.32 | 5.24 | 8.39 | -508.36 | 73.15% | 0.2112 |
| **Tier** | **Average** | 460 | 5.42 | 10.85 | 7.88 | 10.72 | -17.22 | 18.04% | 0.0764 |
| **Tier** | **Good** | 507 | 20.51 | 14.61 | 10.47 | 13.07 | -3.23 | 30.97% | 0.1182 |
| **Tier** | **Excellent** | 377 | 64.85 | 20.43 | 44.83 | 53.20 | -1.83 | 47.21% | 0.1604 |

---

## 8. Preserving Production Inference Compatibility

### Inspection of Existing Flask Pipeline
- Production files: `ml-service/models/predict.py`, `ml-service/api/predict_routes.py`.
- **Incompatibilities Identified:**
  1. `predict.py` loads `player_match_stats.csv` directly from `data/processed/`. When deploying `model_v2`, the inference dataset should be switched to `repaired_player_match_stats.csv` to ensure historical tail calculations use clean player-match records without Cartesian duplicates.
  2. `predict_routes.py` contains hardcoded legacy quartile boundaries (`1.0, 9.0, 27.0`). The repaired model boundaries are `1.0, 10.0, 33.0`.
  3. `StandardScaler` in production scales all 11 columns including binary format dummies. `scaler_v2.pkl` only scales the 8 continuous numeric columns, leaving `match_type_*` as discrete 0/1.

---

## 9. Automated Test Suite Results

Ran automated test suite: `ml-service/experiments/model_v2/tests/test_pipeline_v2.py`
Command: `ml-service\venv\Scripts\python.exe -m unittest ml-service/experiments/model_v2/tests/test_pipeline_v2.py`

| Test Name | Verification Focus | Result |
|---|---|:---:|
| `test_01_cartesian_join_duplication_prevented_synthetic` | Synthetic 2-innings Test match produce exactly 1 merged row, not 4 | **PASS** |
| `test_02_repaired_dataset_zero_duplicates` | Assert 0 duplicates on `(match_id, player)` in repaired PMS | **PASS** |
| `test_03_target_and_rolling_feature_alignment` | Target runs strictly excluded from rolling/career metrics of target match | **PASS** |
| `test_04_same_match_leakage_prevented` | Assert 0 same-date records for the same player across entire dataset | **PASS** |
| `test_05_future_data_exclusion` | Expanding career average depends strictly on prior dates only | **PASS** |
| `test_06_train_only_preprocessing` | Quartile boundaries and scalers fitted strictly on training set | **PASS** |
| `test_07_chronological_split_boundaries` | Strict non-overlapping chronological boundaries (Train < Val < Test) | **PASS** |
| `test_08_feature_order_and_inference_compatibility` | 11 feature names and ordering match production interface | **PASS** |
| `test_09_tier_encoder_mapping` | Encoder classes preserve all 4 standard tier labels | **PASS** |
| `test_10_baseline_calculation_correctness` | Baseline priors match training set distributions | **PASS** |

**Summary:** 10 tests ran in 1.615s, **0 failures, 0 errors (ALL PASSED)**.

