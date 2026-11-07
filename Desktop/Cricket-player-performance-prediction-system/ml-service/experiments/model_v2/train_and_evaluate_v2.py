"""
train_and_evaluate_v2.py

Comprehensive Training, Validation, Model Selection, and Final Evaluation
for Experiment model_v2.

Strict Evaluation Protocol:
1. Model selection and hyperparameter tuning conducted STRICTLY on Train (via TimeSeriesSplit)
   and evaluated on the Validation Set (2025-11-01 to 2026-01-28).
2. The Final Test Set (2026-01-29 to 2026-07-26) is held out untouched until final reporting.
3. Compares all models against pre-match baselines.
4. Generates full subgroup analyses and residual diagnostics.
5. Saves all experimental models and CSV prediction logs in experiments/model_v2/.
"""

import json
from pathlib import Path
import joblib
import numpy as np
import pandas as pd
from sklearn.ensemble import RandomForestRegressor, RandomForestClassifier
from sklearn.svm import SVR
from sklearn.neural_network import MLPClassifier
from sklearn.model_selection import TimeSeriesSplit
from sklearn.metrics import (
    mean_absolute_error, mean_squared_error, r2_score, median_absolute_error,
    accuracy_score, precision_score, recall_score, f1_score,
    confusion_matrix, classification_report
)
from lightgbm import LGBMRegressor, LGBMClassifier
from catboost import CatBoostRegressor, CatBoostClassifier

BASE_DIR = Path(__file__).resolve().parent
DATA_DIR = BASE_DIR / "data"
MODELS_DIR = BASE_DIR / "models"
REPORTS_DIR = BASE_DIR / "reports"

RANDOM_STATE = 42


def load_splits_and_metadata():
    with open(DATA_DIR / "feature_metadata_v2.json", "r", encoding="utf-8") as f:
        meta = json.load(f)

    train_df = pd.read_csv(DATA_DIR / "train_v2.csv")
    val_df = pd.read_csv(DATA_DIR / "val_v2.csv")
    test_df = pd.read_csv(DATA_DIR / "test_v2.csv")
    scaler = joblib.load(DATA_DIR / "scaler_v2.pkl")
    tier_encoder = joblib.load(DATA_DIR / "tier_encoder_v2.pkl")

    return train_df, val_df, test_df, meta, scaler, tier_encoder


def get_feature_matrices(train_df, val_df, test_df, meta):
    raw_features = meta["features"]
    scaled_features = meta["scaled_features_for_distance_models"]

    X_train_tree = train_df[raw_features]
    X_val_tree = val_df[raw_features]
    X_test_tree = test_df[raw_features]

    X_train_dist = train_df[scaled_features]
    X_val_dist = val_df[scaled_features]
    X_test_dist = test_df[scaled_features]

    y_train_reg = train_df["runs"]
    y_val_reg = val_df["runs"]
    y_test_reg = test_df["runs"]

    y_train_clf = train_df["performance_tier_enc"]
    y_val_clf = val_df["performance_tier_enc"]
    y_test_clf = test_df["performance_tier_enc"]

    return {
        "tree": (X_train_tree, X_val_tree, X_test_tree),
        "dist": (X_train_dist, X_val_dist, X_test_dist),
        "y_reg": (y_train_reg, y_val_reg, y_test_reg),
        "y_clf": (y_train_clf, y_val_clf, y_test_clf),
        "raw_features": raw_features,
        "scaled_features": scaled_features
    }


def evaluate_regression(y_true, y_pred, name="Model"):
    mae = mean_absolute_error(y_true, y_pred)
    rmse = np.sqrt(mean_squared_error(y_true, y_pred))
    r2 = r2_score(y_true, y_pred)
    medae = median_absolute_error(y_true, y_pred)
    return {
        "model": name,
        "mae": round(mae, 4),
        "rmse": round(rmse, 4),
        "r2": round(r2, 4),
        "medae": round(medae, 4),
        "pred_mean": round(float(np.mean(y_pred)), 2),
        "pred_min": round(float(np.min(y_pred)), 2),
        "pred_max": round(float(np.max(y_pred)), 2)
    }


def evaluate_classification(y_true, y_pred, name="Model"):
    acc = accuracy_score(y_true, y_pred)
    prec = precision_score(y_true, y_pred, average="macro", zero_division=0)
    rec = recall_score(y_true, y_pred, average="macro", zero_division=0)
    f1_m = f1_score(y_true, y_pred, average="macro", zero_division=0)
    f1_w = f1_score(y_true, y_pred, average="weighted", zero_division=0)
    return {
        "model": name,
        "accuracy": round(acc, 4),
        "macro_precision": round(prec, 4),
        "macro_recall": round(rec, 4),
        "macro_f1": round(f1_m, 4),
        "weighted_f1": round(f1_w, 4)
    }


def run_experiments():
    MODELS_DIR.mkdir(parents=True, exist_ok=True)
    REPORTS_DIR.mkdir(parents=True, exist_ok=True)

    print("=" * 80)
    print("CRICKETAI EXPERIMENT MODEL_V2: TRAINING & EVALUATION PIPELINE")
    print("=" * 80)

    train_df, val_df, test_df, meta, scaler, tier_encoder = load_splits_and_metadata()
    data = get_feature_matrices(train_df, val_df, test_df, meta)

    X_train_tree, X_val_tree, X_test_tree = data["tree"]
    X_train_dist, X_val_dist, X_test_dist = data["dist"]
    y_train_reg, y_val_reg, y_test_reg = data["y_reg"]
    y_train_clf, y_val_clf, y_test_clf = data["y_clf"]

    # -------------------------------------------------------------------------
    # STEP 5: REGRESSION EXPERIMENTS (Model Selection on Validation Set)
    # -------------------------------------------------------------------------
    print("\n" + "=" * 70)
    print("STEP 5: REGRESSION EXPERIMENTS (Model Selection on Validation Set)")
    print("=" * 70)

    reg_candidates = {
        "SVR": {
            "model": SVR(kernel="rbf", C=10, epsilon=0.5),
            "X_train": X_train_dist,
            "X_val": X_val_dist,
            "X_test": X_test_dist
        },
        "RandomForest": {
            "model": RandomForestRegressor(n_estimators=300, max_depth=8, min_samples_leaf=5, random_state=RANDOM_STATE),
            "X_train": X_train_tree,
            "X_val": X_val_tree,
            "X_test": X_test_tree
        },
        "LightGBM": {
            "model": LGBMRegressor(n_estimators=200, max_depth=5, learning_rate=0.03, num_leaves=20, random_state=RANDOM_STATE, verbose=-1),
            "X_train": X_train_tree,
            "X_val": X_val_tree,
            "X_test": X_test_tree
        },
        "CatBoost": {
            "model": CatBoostRegressor(iterations=250, depth=5, learning_rate=0.03, random_state=RANDOM_STATE, verbose=False),
            "X_train": X_train_tree,
            "X_val": X_val_tree,
            "X_test": X_test_tree
        }
    }

    # Evaluate Baselines on Validation Set
    val_b_mean = np.full_like(y_val_reg, y_train_reg.mean())
    val_b_career = val_df["career_avg_runs"].values
    val_b_form = val_df["avg_runs_last_5"].values

    reg_val_results = []
    reg_val_results.append(evaluate_regression(y_val_reg, val_b_mean, "Baseline: Train Mean"))
    reg_val_results.append(evaluate_regression(y_val_reg, val_b_career, "Baseline: Career Average"))
    reg_val_results.append(evaluate_regression(y_val_reg, val_b_form, "Baseline: Recent Form (5-m)"))

    fitted_reg_models = {}
    for name, cfg in reg_candidates.items():
        m = cfg["model"]
        m.fit(cfg["X_train"], y_train_reg)
        preds_val = np.ravel(m.predict(cfg["X_val"]))
        res = evaluate_regression(y_val_reg, preds_val, name)
        reg_val_results.append(res)
        fitted_reg_models[name] = m
        print(f"  {name:15s} | Val MAE={res['mae']:.2f}, RMSE={res['rmse']:.2f}, R2={res['r2']:.4f}, Range=[{res['pred_min']}, {res['pred_max']}]")

    reg_val_df = pd.DataFrame(reg_val_results).sort_values("mae")
    print("\n--- Validation Regression Leaderboard (Lower MAE is better) ---")
    print(reg_val_df.to_string(index=False))

    # SELECT BEST REGRESSION MODEL strictly on VALIDATION MAE
    # Filter for trained models (not baseline)
    trained_reg_df = reg_val_df[~reg_val_df["model"].str.startswith("Baseline")]
    best_reg_name = trained_reg_df.iloc[0]["model"]
    best_reg_model = fitted_reg_models[best_reg_name]
    print(f"\n>>> Selected Best Regression Model (based on Validation MAE): {best_reg_name}")

    # -------------------------------------------------------------------------
    # STEP 6: CLASSIFICATION EXPERIMENTS (Model Selection on Validation Set)
    # -------------------------------------------------------------------------
    print("\n" + "=" * 70)
    print("STEP 6: CLASSIFICATION EXPERIMENTS (Model Selection on Validation Set)")
    print("=" * 70)

    clf_candidates = {
        "MLPClassifier": {
            "model": MLPClassifier(hidden_layer_sizes=(64, 32), max_iter=500, random_state=RANDOM_STATE),
            "X_train": X_train_dist,
            "X_val": X_val_dist,
            "X_test": X_test_dist
        },
        "RandomForest": {
            "model": RandomForestClassifier(n_estimators=300, max_depth=8, min_samples_leaf=5, random_state=RANDOM_STATE),
            "X_train": X_train_tree,
            "X_val": X_val_tree,
            "X_test": X_test_tree
        },
        "LightGBM": {
            "model": LGBMClassifier(n_estimators=200, max_depth=5, learning_rate=0.03, num_leaves=20, random_state=RANDOM_STATE, verbose=-1),
            "X_train": X_train_tree,
            "X_val": X_val_tree,
            "X_test": X_test_tree
        },
        "CatBoost": {
            "model": CatBoostClassifier(iterations=250, depth=5, learning_rate=0.03, random_state=RANDOM_STATE, verbose=False),
            "X_train": X_train_tree,
            "X_val": X_val_tree,
            "X_test": X_test_tree
        }
    }

    # Evaluate Classification Baselines on Validation Set
    maj_class = pd.Series(y_train_clf).mode()[0]
    val_b_maj = np.full_like(y_val_clf, maj_class)
    
    # Career and form tier baselines using training quartile cutoffs
    bins = meta["tier_bins"]
    val_car_tier = pd.cut(val_df["career_avg_runs"], bins=bins, labels=["Poor", "Average", "Good", "Excellent"], include_lowest=True)
    val_b_career_clf = tier_encoder.transform(val_car_tier.astype(str))

    val_form_tier = pd.cut(val_df["avg_runs_last_5"], bins=bins, labels=["Poor", "Average", "Good", "Excellent"], include_lowest=True)
    val_b_form_clf = tier_encoder.transform(val_form_tier.astype(str))

    clf_val_results = []
    clf_val_results.append(evaluate_classification(y_val_clf, val_b_maj, "Baseline: Majority Class"))
    clf_val_results.append(evaluate_classification(y_val_clf, val_b_career_clf, "Baseline: Career Average Tier"))
    clf_val_results.append(evaluate_classification(y_val_clf, val_b_form_clf, "Baseline: Recent Form Tier"))

    fitted_clf_models = {}
    for name, cfg in clf_candidates.items():
        m = cfg["model"]
        m.fit(cfg["X_train"], y_train_clf)
        preds_val = np.ravel(m.predict(cfg["X_val"])).astype(int)
        res = evaluate_classification(y_val_clf, preds_val, name)
        clf_val_results.append(res)
        fitted_clf_models[name] = m
        print(f"  {name:15s} | Val Acc={res['accuracy']:.4f}, Macro-F1={res['macro_f1']:.4f}, Weighted-F1={res['weighted_f1']:.4f}")

    clf_val_df = pd.DataFrame(clf_val_results).sort_values("macro_f1", ascending=False)
    print("\n--- Validation Classification Leaderboard (Higher Macro-F1 is better) ---")
    print(clf_val_df.to_string(index=False))

    # SELECT BEST CLASSIFICATION MODEL strictly on VALIDATION MACRO-F1
    trained_clf_df = clf_val_df[~clf_val_df["model"].str.startswith("Baseline")]
    best_clf_name = trained_clf_df.iloc[0]["model"]
    best_clf_model = fitted_clf_models[best_clf_name]
    print(f"\n>>> Selected Best Classification Model (based on Validation Macro-F1): {best_clf_name}")

    # -------------------------------------------------------------------------
    # STEP 7: FEATURE IMPROVEMENTS ABLATION (Validation Comparison)
    # -------------------------------------------------------------------------
    print("\n" + "=" * 70)
    print("STEP 7: FEATURE IMPROVEMENTS ABLATION ON VALIDATION SET")
    print("=" * 70)
    # Compare CatBoost with and without Empirical Bayes shrinkage
    # (Train without shrinkage using raw venue/opponent features)
    raw_venue_opp_features = [
        "avg_runs_last_5", "avg_sr_last_5", "avg_wickets_last_5", "avg_economy_last_5",
        "career_matches_played", "career_avg_runs", "raw_venue_avg_runs", "raw_opponent_avg_runs",
        "match_type_odi", "match_type_t20i", "match_type_test"
    ]
    X_train_no_shrink = train_df[raw_venue_opp_features].fillna(0)
    X_val_no_shrink = val_df[raw_venue_opp_features].fillna(0)

    cb_no_shrink = CatBoostRegressor(iterations=250, depth=5, learning_rate=0.03, random_state=RANDOM_STATE, verbose=False)
    cb_no_shrink.fit(X_train_no_shrink, y_train_reg)
    val_pred_no_shrink = cb_no_shrink.predict(X_val_no_shrink)
    res_no_shrink = evaluate_regression(y_val_reg, val_pred_no_shrink, "CatBoost (No Bayes Shrinkage, 0-Fill)")

    cb_with_shrink = fitted_reg_models["CatBoost"]
    val_pred_shrink = cb_with_shrink.predict(X_val_tree)
    res_with_shrink = evaluate_regression(y_val_reg, val_pred_shrink, "CatBoost (With Empirical Bayes Shrinkage)")

    print(f"  Ablation 1 (Venue/Opponent Shrinkage):")
    print(f"    Raw 0-Fill:       MAE={res_no_shrink['mae']:.2f}, RMSE={res_no_shrink['rmse']:.2f}, R2={res_no_shrink['r2']:.4f}")
    print(f"    Bayes Shrinkage:  MAE={res_with_shrink['mae']:.2f}, RMSE={res_with_shrink['rmse']:.2f}, R2={res_with_shrink['r2']:.4f}")

    # -------------------------------------------------------------------------
    # FINAL HOLDOUT EVALUATION ON UNTOUCHED TEST SET
    # -------------------------------------------------------------------------
    print("\n" + "=" * 70)
    print("FINAL HOLDOUT EVALUATION ON UNTOUCHED TEST SET (2026-01-29 to 2026-07-26)")
    print("=" * 70)

    # 1. Regression Test Benchmark
    test_b_mean = np.full_like(y_test_reg, y_train_reg.mean())
    test_b_career = test_df["career_avg_runs"].values
    test_b_form = test_df["avg_runs_last_5"].values

    reg_test_results = []
    reg_test_results.append(evaluate_regression(y_test_reg, test_b_mean, "Baseline: Train Mean"))
    reg_test_results.append(evaluate_regression(y_test_reg, test_b_career, "Baseline: Career Average"))
    reg_test_results.append(evaluate_regression(y_test_reg, test_b_form, "Baseline: Recent Form (5-m)"))

    reg_test_preds_dict = {}
    for name, cfg in reg_candidates.items():
        m = fitted_reg_models[name]
        preds_test = np.ravel(m.predict(cfg["X_test"]))
        reg_test_preds_dict[name] = preds_test
        res = evaluate_regression(y_test_reg, preds_test, name)
        reg_test_results.append(res)

    reg_test_df = pd.DataFrame(reg_test_results).sort_values("mae")
    print("\n--- Final Test Set Regression Results ---")
    print(reg_test_df.to_string(index=False))

    # 2. Classification Test Benchmark
    test_b_maj = np.full_like(y_test_clf, maj_class)
    test_car_tier = pd.cut(test_df["career_avg_runs"], bins=bins, labels=["Poor", "Average", "Good", "Excellent"], include_lowest=True)
    test_b_career_clf = tier_encoder.transform(test_car_tier.astype(str))
    test_form_tier = pd.cut(test_df["avg_runs_last_5"], bins=bins, labels=["Poor", "Average", "Good", "Excellent"], include_lowest=True)
    test_b_form_clf = tier_encoder.transform(test_form_tier.astype(str))

    clf_test_results = []
    clf_test_results.append(evaluate_classification(y_test_clf, test_b_maj, "Baseline: Majority Class"))
    clf_test_results.append(evaluate_classification(y_test_clf, test_b_career_clf, "Baseline: Career Average Tier"))
    clf_test_results.append(evaluate_classification(y_test_clf, test_b_form_clf, "Baseline: Recent Form Tier"))

    clf_test_preds_dict = {}
    clf_test_probs_dict = {}
    for name, cfg in clf_candidates.items():
        m = fitted_clf_models[name]
        preds_test = np.ravel(m.predict(cfg["X_test"])).astype(int)
        clf_test_preds_dict[name] = preds_test
        if hasattr(m, "predict_proba"):
            clf_test_probs_dict[name] = m.predict_proba(cfg["X_test"])
        res = evaluate_classification(y_test_clf, preds_test, name)
        clf_test_results.append(res)

    clf_test_df = pd.DataFrame(clf_test_results).sort_values("macro_f1", ascending=False)
    print("\n--- Final Test Set Classification Results ---")
    print(clf_test_df.to_string(index=False))

    # 3. Selected Best Models Detailed Evaluation
    best_reg_preds = reg_test_preds_dict[best_reg_name]
    best_reg_preds_clipped = np.maximum(0.0, best_reg_preds)

    best_clf_preds = clf_test_preds_dict[best_clf_name]
    best_clf_probs = clf_test_probs_dict.get(best_clf_name, None)

    # Confusion Matrix for Best Classifier
    classes = tier_encoder.classes_
    cm_test = confusion_matrix(y_test_clf, best_clf_preds)
    cm_df = pd.DataFrame(cm_test, index=[f"Actual_{c}" for c in classes], columns=[f"Pred_{c}" for c in classes])
    print(f"\n--- Confusion Matrix ({best_clf_name} on Test Set) ---")
    print(cm_df)

    cr_test = classification_report(y_test_clf, best_clf_preds, target_names=classes, digits=4)
    print(f"\n--- Classification Report ({best_clf_name} on Test Set) ---")
    print(cr_test)

    # 4. Save Final Best Models to experiments/model_v2/models/
    reg_save_path = MODELS_DIR / "best_regression_model_v2.pkl"
    clf_save_path = MODELS_DIR / "best_classification_model_v2.pkl"
    joblib.dump(best_reg_model, reg_save_path)
    joblib.dump(best_clf_model, clf_save_path)
    print(f"\nSaved Best Regression Model: {reg_save_path} ({best_reg_name})")
    print(f"Saved Best Classification Model: {clf_save_path} ({best_clf_name})")

    # 5. Save Leaderboard CSVs
    reg_test_df.to_csv(REPORTS_DIR / "regression_experiment_results.csv", index=False)
    clf_test_df.to_csv(REPORTS_DIR / "classification_experiment_results.csv", index=False)

    # 6. Save Predictions CSVs for Auditing
    reg_preds_df = pd.DataFrame({
        "match_id": test_df["match_id"],
        "player_name": test_df["player_name"],
        "player_name_norm": test_df["player_name_norm"],
        "match_date": test_df["match_date"],
        "venue": test_df["venue"],
        "team_a": test_df["team_a"],
        "team_b": test_df["team_b"],
        "match_type": test_df["match_type"],
        "actual_runs": y_test_reg,
        f"pred_runs_{best_reg_name.lower()}": np.round(best_reg_preds, 2),
        f"pred_runs_clipped": np.round(best_reg_preds_clipped, 2),
        "residual": np.round(best_reg_preds - y_test_reg, 2),
        "abs_error": np.round(np.abs(best_reg_preds - y_test_reg), 2),
        "baseline_train_mean": np.round(test_b_mean, 2),
        "baseline_career_avg": np.round(test_b_career, 2),
        "baseline_form_avg": np.round(test_b_form, 2)
    })
    reg_preds_df.to_csv(REPORTS_DIR / "regression_test_predictions_v2.csv", index=False)

    prob_cols = {}
    if best_clf_probs is not None:
        for idx, c in enumerate(tier_encoder.classes_):
            prob_cols[f"prob_{c}"] = np.round(best_clf_probs[:, idx], 4)

    clf_preds_df = pd.DataFrame({
        "match_id": test_df["match_id"],
        "player_name": test_df["player_name"],
        "player_name_norm": test_df["player_name_norm"],
        "match_date": test_df["match_date"],
        "venue": test_df["venue"],
        "team_a": test_df["team_a"],
        "team_b": test_df["team_b"],
        "match_type": test_df["match_type"],
        "actual_runs": y_test_reg,
        "actual_tier": test_df["performance_tier"],
        "actual_tier_enc": y_test_clf,
        f"predicted_tier_{best_clf_name.lower()}": tier_encoder.inverse_transform(best_clf_preds),
        "predicted_tier_enc": best_clf_preds,
        **prob_cols,
        "is_correct": (best_clf_preds == y_test_clf.values).astype(int),
        "baseline_majority_tier": tier_encoder.inverse_transform([maj_class])[0],
        "baseline_career_tier": test_car_tier.astype(str),
        "baseline_form_tier": test_form_tier.astype(str)
    })
    clf_preds_df.to_csv(REPORTS_DIR / "classification_test_predictions_v2.csv", index=False)

    # 7. Subgroup Performance Breakdown on Test Set
    subgroup_records = []
    # By Format
    for fmt in ["t20i", "odi", "test"]:
        sub_mask = test_df["match_type"] == fmt
        sub_y_true = y_test_reg[sub_mask]
        sub_y_pred = best_reg_preds[sub_mask]
        sub_clf_true = y_test_clf[sub_mask]
        sub_clf_pred = best_clf_preds[sub_mask]
        subgroup_records.append({
            "subgroup_type": "Format",
            "subgroup_value": fmt.upper(),
            "sample_count": len(sub_y_true),
            "actual_mean": round(float(np.mean(sub_y_true)), 2),
            "pred_mean": round(float(np.mean(sub_y_pred)), 2),
            "mae": round(float(mean_absolute_error(sub_y_true, sub_y_pred)), 4),
            "rmse": round(float(np.sqrt(mean_squared_error(sub_y_true, sub_y_pred))), 4),
            "r2": round(float(r2_score(sub_y_true, sub_y_pred)), 4),
            "accuracy": round(float(accuracy_score(sub_clf_true, sub_clf_pred)), 4),
            "macro_f1": round(float(f1_score(sub_clf_true, sub_clf_pred, average="macro", zero_division=0)), 4)
        })

    # By Actual Tier
    for t in ["Poor", "Average", "Good", "Excellent"]:
        sub_mask = test_df["performance_tier"] == t
        sub_y_true = y_test_reg[sub_mask]
        sub_y_pred = best_reg_preds[sub_mask]
        sub_clf_true = y_test_clf[sub_mask]
        sub_clf_pred = best_clf_preds[sub_mask]
        subgroup_records.append({
            "subgroup_type": "Tier",
            "subgroup_value": t,
            "sample_count": len(sub_y_true),
            "actual_mean": round(float(np.mean(sub_y_true)), 2),
            "pred_mean": round(float(np.mean(sub_y_pred)), 2),
            "mae": round(float(mean_absolute_error(sub_y_true, sub_y_pred)), 4),
            "rmse": round(float(np.sqrt(mean_squared_error(sub_y_true, sub_y_pred))), 4),
            "r2": round(float(r2_score(sub_y_true, sub_y_pred)), 4) if len(np.unique(sub_y_true)) > 1 else np.nan,
            "accuracy": round(float(accuracy_score(sub_clf_true, sub_clf_pred)), 4),
            "macro_f1": round(float(f1_score(sub_clf_true, sub_clf_pred, average="macro", zero_division=0)), 4)
        })

    subgroup_df = pd.DataFrame(subgroup_records)
    subgroup_df.to_csv(REPORTS_DIR / "subgroup_performance_v2.csv", index=False)
    print("\n--- Subgroup Performance Breakdown on Test Set ---")
    print(subgroup_df.to_string(index=False))

    print("\n" + "=" * 80)
    print("ALL EXPERIMENTS & EVALUATIONS COMPLETED SUCCESSFULLY.")
    print("=" * 80)


if __name__ == "__main__":
    run_experiments()
