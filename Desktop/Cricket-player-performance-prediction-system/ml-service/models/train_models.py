"""
train_models.py

Trains Random Forest, SVM, XGBoost, MLP, CatBoost, and LightGBM on the
processed cricket dataset, for BOTH:
  (a) Regression  — predict next-match runs (MAE, RMSE, R2)
  (b) Classification — predict a performance tier (Accuracy, Precision,
      Recall, F1), tier derived from runs via quartile binning

Uses TimeSeriesSplit for cross-validation (not k-fold) since match order
matters — training on future data to predict the past would leak information.

Saves the best model of each task to saved_models/best_regression_model.pkl
and saved_models/best_classification_model.pkl.

Run:
    pip install scikit-learn xgboost catboost lightgbm pandas joblib --break-system-packages
    python train_models.py
"""

import json
from pathlib import Path

import joblib
import numpy as np
import pandas as pd
from sklearn.ensemble import RandomForestRegressor, RandomForestClassifier
from sklearn.svm import SVR, SVC
from sklearn.neural_network import MLPRegressor, MLPClassifier
from sklearn.model_selection import TimeSeriesSplit, cross_val_score
from sklearn.metrics import (
    mean_absolute_error, mean_squared_error, r2_score,
    accuracy_score, precision_score, recall_score, f1_score,
)
from sklearn.preprocessing import LabelEncoder
from xgboost import XGBRegressor, XGBClassifier
from catboost import CatBoostRegressor, CatBoostClassifier
from lightgbm import LGBMRegressor, LGBMClassifier

PROCESSED_DIR = Path("../data/processed")
SAVED_MODELS_DIR = Path("saved_models")
RANDOM_STATE = 42


def load_train_test():
    train = pd.read_csv(PROCESSED_DIR / "train.csv")
    test = pd.read_csv(PROCESSED_DIR / "test.csv")
    with open(PROCESSED_DIR / "feature_columns.json") as f:
        meta = json.load(f)
    return train, test, meta["features"], meta["target"]


def make_performance_tier(runs_series: pd.Series) -> pd.Series:
    """Bin runs into 4 tiers using quartiles computed on the TRAIN set only
    (passed in already fitted via pd.qcut on train, then applied via cut
    on test using the same bin edges — avoids leaking test distribution)."""
    return pd.qcut(runs_series, q=4, labels=["Poor", "Average", "Good", "Excellent"])


# ------------------------------------------------------------------
# Regression models
# ------------------------------------------------------------------
REGRESSORS = {
    "RandomForest": RandomForestRegressor(n_estimators=300, max_depth=10, random_state=RANDOM_STATE),
    "SVM": SVR(kernel="rbf", C=10, epsilon=0.5),
    "XGBoost": XGBRegressor(n_estimators=300, max_depth=6, learning_rate=0.05, random_state=RANDOM_STATE),
    "MLP": MLPRegressor(hidden_layer_sizes=(64, 32), max_iter=500, random_state=RANDOM_STATE),
    "CatBoost": CatBoostRegressor(iterations=300, depth=6, learning_rate=0.05,
                                   random_state=RANDOM_STATE, verbose=False),
    "LightGBM": LGBMRegressor(n_estimators=300, max_depth=6, learning_rate=0.05,
                               random_state=RANDOM_STATE, verbose=-1),
}

# ------------------------------------------------------------------
# Classification models
# ------------------------------------------------------------------
CLASSIFIERS = {
    "RandomForest": RandomForestClassifier(n_estimators=300, max_depth=10, random_state=RANDOM_STATE),
    "SVM": SVC(kernel="rbf", C=10, probability=True),
    "XGBoost": XGBClassifier(n_estimators=300, max_depth=6, learning_rate=0.05,
                              random_state=RANDOM_STATE, eval_metric="mlogloss"),
    "MLP": MLPClassifier(hidden_layer_sizes=(64, 32), max_iter=500, random_state=RANDOM_STATE),
    "CatBoost": CatBoostClassifier(iterations=300, depth=6, learning_rate=0.05,
                                    random_state=RANDOM_STATE, verbose=False),
    "LightGBM": LGBMClassifier(n_estimators=300, max_depth=6, learning_rate=0.05,
                                random_state=RANDOM_STATE, verbose=-1),
}


def run_regression_benchmark(X_train, y_train, X_test, y_test):
    print("\n" + "=" * 60)
    print("REGRESSION — predicting next-match runs")
    print("=" * 60)
    tscv = TimeSeriesSplit(n_splits=5)
    results = []
    fitted_models = {}

    for name, model in REGRESSORS.items():
        cv_scores = cross_val_score(model, X_train, y_train, cv=tscv,
                                     scoring="neg_mean_absolute_error")
        model.fit(X_train, y_train)
        preds = model.predict(X_test)

        mae = mean_absolute_error(y_test, preds)
        rmse = np.sqrt(mean_squared_error(y_test, preds))
        r2 = r2_score(y_test, preds)

        results.append({
            "model": name,
            "cv_mae_mean": -cv_scores.mean(),
            "test_mae": mae,
            "test_rmse": rmse,
            "test_r2": r2,
        })
        fitted_models[name] = model
        print(f"{name:15s}  CV-MAE={-cv_scores.mean():.3f}  "
              f"Test-MAE={mae:.3f}  RMSE={rmse:.3f}  R2={r2:.3f}")

    results_df = pd.DataFrame(results).sort_values("test_mae")
    best_name = results_df.iloc[0]["model"]
    print(f"\nBest regression model: {best_name} (lowest test MAE)")
    return results_df, fitted_models[best_name], best_name


def run_classification_benchmark(X_train, y_train_tier, X_test, y_test_tier):
    print("\n" + "=" * 60)
    print("CLASSIFICATION — predicting performance tier")
    print("=" * 60)
    tscv = TimeSeriesSplit(n_splits=5)
    results = []
    fitted_models = {}

    for name, model in CLASSIFIERS.items():
        cv_scores = cross_val_score(model, X_train, y_train_tier, cv=tscv, scoring="f1_macro")
        model.fit(X_train, y_train_tier)
        preds = model.predict(X_test)

        acc = accuracy_score(y_test_tier, preds)
        prec = precision_score(y_test_tier, preds, average="macro", zero_division=0)
        rec = recall_score(y_test_tier, preds, average="macro", zero_division=0)
        f1 = f1_score(y_test_tier, preds, average="macro", zero_division=0)

        results.append({
            "model": name,
            "cv_f1_mean": cv_scores.mean(),
            "test_accuracy": acc,
            "test_precision": prec,
            "test_recall": rec,
            "test_f1": f1,
        })
        fitted_models[name] = model
        print(f"{name:15s}  CV-F1={cv_scores.mean():.3f}  Acc={acc:.3f}  "
              f"Prec={prec:.3f}  Rec={rec:.3f}  F1={f1:.3f}")

    results_df = pd.DataFrame(results).sort_values("test_f1", ascending=False)
    best_name = results_df.iloc[0]["model"]
    print(f"\nBest classification model: {best_name} (highest macro F1)")
    return results_df, fitted_models[best_name], best_name


if __name__ == "__main__":
    train, test, feature_cols, target_col = load_train_test()

    X_train, y_train = train[feature_cols], train[target_col]
    X_test, y_test = test[feature_cols], test[target_col]

    reg_results, best_reg_model, best_reg_name = run_regression_benchmark(
        X_train, y_train, X_test, y_test
    )

    # Build tiers from train distribution, apply same bin edges to test
    train_tiers, bins = pd.qcut(y_train, q=4, labels=["Poor", "Average", "Good", "Excellent"],
                                 retbins=True)
    test_tiers = pd.cut(y_test, bins=bins, labels=["Poor", "Average", "Good", "Excellent"],
                         include_lowest=True)

    # XGBoost requires numeric class labels (0,1,2,3), not text — encode once
    # here so every model in the comparison receives the same label format.
    label_encoder = LabelEncoder()
    train_tiers_enc = label_encoder.fit_transform(train_tiers.astype(str))
    test_tiers_enc = label_encoder.transform(test_tiers.astype(str))

    clf_results, best_clf_model, best_clf_name = run_classification_benchmark(
        X_train, train_tiers_enc, X_test, test_tiers_enc
    )

    SAVED_MODELS_DIR.mkdir(exist_ok=True)
    reg_results.to_csv(SAVED_MODELS_DIR / "regression_comparison.csv", index=False)
    clf_results.to_csv(SAVED_MODELS_DIR / "classification_comparison.csv", index=False)

    joblib.dump(best_reg_model, SAVED_MODELS_DIR / "best_regression_model.pkl")
    joblib.dump(best_clf_model, SAVED_MODELS_DIR / "best_classification_model.pkl")
    joblib.dump(label_encoder, SAVED_MODELS_DIR / "tier_label_encoder.pkl")

    print(f"\nSaved best_regression_model.pkl ({best_reg_name})")
    print(f"Saved best_classification_model.pkl ({best_clf_name})")
    print("Comparison tables saved to saved_models/*_comparison.csv")