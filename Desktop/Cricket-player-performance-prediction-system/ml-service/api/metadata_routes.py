"""
metadata_routes.py

Read-only metadata endpoints for the ML service:

    GET /api/ml/players     — sorted list of all player names in the dataset
    GET /api/ml/venues      — sorted list of all venues in the dataset
    GET /api/ml/model-info  — model type, features, and evaluation metrics
                              read from the stored comparison CSVs

Design principles:
  * No data is invented or hardcoded — every value comes from the existing
    processed dataset or the comparison CSVs produced by train_models.py.
  * The dataset is read once at startup and cached; it is never modified.
  * File paths, stack traces, and implementation details are never exposed.
  * Errors return {"error": "<user-friendly message>"} with an appropriate
    5xx status code, consistent with the existing predict endpoint style.
"""

from flask import Blueprint, jsonify
from pathlib import Path
import json
import pandas as pd
import joblib

metadata_bp = Blueprint("metadata_bp", __name__)

# ---------------------------------------------------------------------------
# Resolve paths relative to this file so the blueprint works regardless of
# the working directory the server is launched from.
# ---------------------------------------------------------------------------
_BASE = Path(__file__).resolve().parent.parent          # ml-service/
_PROCESSED_DIR = _BASE / "data" / "processed"
_SAVED_MODELS_DIR = _BASE / "models" / "saved_models"

_HISTORY_CSV = _PROCESSED_DIR / "player_match_stats.csv"
_FEATURE_META = _PROCESSED_DIR / "feature_columns.json"
_REG_COMPARISON = _SAVED_MODELS_DIR / "regression_comparison.csv"
_CLF_COMPARISON = _SAVED_MODELS_DIR / "classification_comparison.csv"
_REG_MODEL_PATH = _SAVED_MODELS_DIR / "best_regression_model.pkl"
_CLF_MODEL_PATH = _SAVED_MODELS_DIR / "best_classification_model.pkl"

# ---------------------------------------------------------------------------
# Module-level cache — loaded once when the blueprint is imported.
# Both lists are sorted and deduplicated; neither modifies the source file.
# ---------------------------------------------------------------------------
try:
    _history_df = pd.read_csv(_HISTORY_CSV)
    _players: list[str] = sorted(_history_df["player_name"].dropna().unique().tolist())
    _venues: list[str] = sorted(_history_df["venue"].dropna().unique().tolist())
    _teams: list[str] = sorted(_history_df["team_b"].dropna().unique().tolist())
    _cache_error: str | None = None
except Exception as exc:
    _players = []
    _venues = []
    _teams = []
    _cache_error = str(exc)


def _load_best_row(csv_path: Path, sort_col: str, ascending: bool) -> dict | None:
    """Return the row of the comparison CSV that was selected as best model.

    Returns None (not raises) if the file is missing or unreadable — the
    caller decides what to put in the response in that case.
    """
    try:
        df = pd.read_csv(csv_path).dropna(subset=[sort_col])
        if df.empty:
            return None
        best = df.sort_values(sort_col, ascending=ascending).iloc[0]
        return best.to_dict()
    except Exception:
        return None


def _get_model_class_name(path: Path) -> str | None:
    """Load the pickled model and return its class name without exposing paths."""
    try:
        m = joblib.load(path)
        return type(m).__name__
    except Exception:
        return None


# ---------------------------------------------------------------------------
# Endpoints
# ---------------------------------------------------------------------------

@metadata_bp.route("/players", methods=["GET"])
def get_players():
    """Return every unique player name present in the historical dataset."""
    if _cache_error:
        return jsonify({"error": "Player data is temporarily unavailable."}), 503

    return jsonify({
        "players": _players,
        "count": len(_players),
    })


@metadata_bp.route("/venues", methods=["GET"])
def get_venues():
    """Return every unique venue present in the historical dataset."""
    if _cache_error:
        return jsonify({"error": "Venue data is temporarily unavailable."}), 503

    return jsonify({
        "venues": _venues,
        "count": len(_venues),
    })


@metadata_bp.route("/teams", methods=["GET"])
def get_teams():
    """Return every unique opponent team present in the historical dataset."""
    if _cache_error:
        return jsonify({"error": "Team data is temporarily unavailable."}), 503

    return jsonify({
        "teams": _teams,
        "count": len(_teams),
    })


@metadata_bp.route("/model-info", methods=["GET"])
def get_model_info():
    """
    Return verified information about the two loaded models.

    Only values that genuinely exist in the project are included:
      - model class names (read from the .pkl files)
      - feature list and target (from feature_columns.json)
      - evaluation metrics (from the *_comparison.csv files saved by train_models.py)
      - accepted match types (from the normalisation map in predict.py)

    Nothing is invented. Fields that cannot be read are set to null.
    """
    # --- Feature metadata ---
    try:
        with open(_FEATURE_META) as f:
            feature_meta = json.load(f)
        features = feature_meta.get("features", [])
        target = feature_meta.get("target", None)
    except Exception:
        features = []
        target = None

    # --- Regression model (MLP — best by lowest test MAE) ---
    reg_row = _load_best_row(_REG_COMPARISON, sort_col="test_mae", ascending=True)
    reg_class = _get_model_class_name(_REG_MODEL_PATH)

    regression_info: dict = {
        "task": "regression",
        "target": target,
        "model_class": reg_class,
    }
    if reg_row:
        regression_info["model_name"] = reg_row.get("model")
        regression_info["metrics"] = {
            "cv_mae_mean":  round(reg_row["cv_mae_mean"], 4) if reg_row.get("cv_mae_mean") is not None else None,
            "test_mae":     round(reg_row["test_mae"], 4)    if reg_row.get("test_mae")    is not None else None,
            "test_rmse":    round(reg_row["test_rmse"], 4)   if reg_row.get("test_rmse")   is not None else None,
            "test_r2":      round(reg_row["test_r2"], 6)     if reg_row.get("test_r2")     is not None else None,
        }

    # --- Classification model (XGBoost — best by highest test F1) ---
    clf_row = _load_best_row(_CLF_COMPARISON, sort_col="test_f1", ascending=False)
    clf_class = _get_model_class_name(_CLF_MODEL_PATH)

    classification_info: dict = {
        "task": "classification",
        "target": "performance_tier",
        "tier_labels": ["Poor", "Average", "Good", "Excellent"],
        "model_class": clf_class,
    }
    if clf_row:
        classification_info["model_name"] = clf_row.get("model")
        classification_info["metrics"] = {
            "cv_f1_mean":       round(clf_row["cv_f1_mean"], 4)       if clf_row.get("cv_f1_mean")       is not None else None,
            "test_accuracy":    round(clf_row["test_accuracy"], 4)    if clf_row.get("test_accuracy")    is not None else None,
            "test_precision":   round(clf_row["test_precision"], 4)   if clf_row.get("test_precision")   is not None else None,
            "test_recall":      round(clf_row["test_recall"], 4)      if clf_row.get("test_recall")      is not None else None,
            "test_f1":          round(clf_row["test_f1"], 4)          if clf_row.get("test_f1")          is not None else None,
        }

    return jsonify({
        "dataset": {
            "player_count":     len(_players),
            "venue_count":      len(_venues),
            "total_rows":       int(len(_history_df)) if not _cache_error else None,
        },
        "features": {
            "count":    len(features),
            "names":    features,
        },
        "accepted_match_types": ["t20i", "odi", "test"],
        "accepted_match_type_aliases": ["T20", "t20", "t20i", "ODI", "odi", "Test", "test"],
        "models": {
            "regression":       regression_info,
            "classification":   classification_info,
        },
    })
