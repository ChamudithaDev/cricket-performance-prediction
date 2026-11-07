from flask import Blueprint, request, jsonify
import sys
from pathlib import Path

# Reuse the exact feature-building logic from predict.py so the API and the
# CLI script never drift out of sync with each other.
sys.path.append(str(Path(__file__).resolve().parent.parent / "models"))
from predict import build_feature_row, align_to_training_columns, normalize_match_type  # noqa: E402
import joblib
import json
import pandas as pd

predict_bp = Blueprint("predict_bp", __name__)

MODELS_DIR = Path(__file__).resolve().parent.parent / "models" / "saved_models"
PROCESSED_DIR = Path(__file__).resolve().parent.parent / "data" / "processed"

_scaler = joblib.load(PROCESSED_DIR / "scaler.pkl")
_reg_model = joblib.load(MODELS_DIR / "best_regression_model.pkl")
_clf_model = joblib.load(MODELS_DIR / "best_classification_model.pkl")
_label_encoder = joblib.load(MODELS_DIR / "tier_label_encoder.pkl")
with open(PROCESSED_DIR / "feature_columns.json") as f:
    _feature_cols = json.load(f)["features"]


@predict_bp.route("/predict", methods=["POST"])
def predict():
    payload = request.get_json(force=True)
    required = ["player", "opponent", "match_type"]
    missing = [k for k in required if k not in payload or payload[k] is None or str(payload[k]).strip() == ""]
    if missing:
        return jsonify({"error": f"Missing required fields: {missing}"}), 400

    try:
        # Normalise match_type first so any alias (T20, t20, ODI…) maps to
        # the training label (t20i, odi, test) before feature building.
        match_type_norm = normalize_match_type(payload["match_type"])

        venue_input = payload.get("venue")
        venue_str = str(venue_input).strip() if venue_input is not None else ""

        raw_row = build_feature_row(
            payload["player"], venue_str, payload["opponent"], match_type_norm
        )
        aligned = align_to_training_columns(raw_row, _feature_cols)
        scaled = _scaler.transform(aligned)
        scaled_df = pd.DataFrame(scaled, columns=_feature_cols)

        # 1. Regression output: predicted runs (runs ≥ 0)
        reg_prediction = max(0.0, float(_reg_model.predict(scaled_df)[0]))

        # Derived tier from verified training set quartile cutoffs:
        # Poor: [0, 1], Average: (1, 9], Good: (9, 27], Excellent: (27, inf)
        if reg_prediction <= 1.0:
            reg_tier = "Poor"
        elif reg_prediction <= 9.0:
            reg_tier = "Average"
        elif reg_prediction <= 27.0:
            reg_tier = "Good"
        else:
            reg_tier = "Excellent"

        # 2. Classification output: predicted performance tier (Poor / Average / Good / Excellent)
        raw_tier = int(_clf_model.predict(scaled_df)[0])
        tier_label = str(_label_encoder.inverse_transform([raw_tier])[0])

        # 3. Class probabilities if supported by classifier
        tier_probabilities = {}
        if hasattr(_clf_model, "predict_proba"):
            probs = _clf_model.predict_proba(scaled_df)[0]
            tier_probabilities = {
                str(_label_encoder.inverse_transform([c])[0]): round(float(probs[i]), 4)
                for i, c in enumerate(_clf_model.classes_)
            }
    except ValueError as e:
        # Covers: unknown player name, unknown match_type alias
        return jsonify({"error": str(e)}), 400

    return jsonify({
        "player": payload["player"],
        "opponent": payload["opponent"],
        "venue": venue_str if venue_str else None,
        "match_type": match_type_norm,          # canonical training label
        "predicted_runs": round(reg_prediction, 1),
        "regression_derived_tier": reg_tier,
        "predicted_tier": tier_label,
        "tier_probabilities": tier_probabilities,
        "model_name": type(_reg_model).__name__,
        "classifier_name": type(_clf_model).__name__
    })


@predict_bp.route("/health", methods=["GET"])
def health():
    return jsonify({"status": "UP"})
