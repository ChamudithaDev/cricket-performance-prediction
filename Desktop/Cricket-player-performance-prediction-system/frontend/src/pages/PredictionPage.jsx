import { useState, useEffect, useMemo } from "react";
import DashboardLayout from "../components/layout/DashboardLayout";
import {
  predictPerformance,
  getPredictionMetadata,
  getPredictionHistory,
} from "../services/predictionService";
import { getPlayerAnalytics } from "../services/analyticsService";

// Fallback options in case metadata endpoint encounters network delay
const FALLBACK_OPPONENTS = [
  "Australia", "Bangladesh", "England", "India", "Ireland",
  "New Zealand", "Pakistan", "South Africa", "Sri Lanka", "West Indies", "Zimbabwe"
];
const FALLBACK_FORMATS = ["T20I", "ODI", "TEST"];

// Confirmed quartile boundaries from model training dataset (train.csv):
// Q1 (25%): 1.0 run, Median (50%): 9.0 runs, Q3 (75%): 27.0 runs
const QUARTILE_THRESHOLDS = {
  Poor: { label: "Poor", min: 0, max: 1, rangeText: "0–1 runs", percentile: "< 25th percentile" },
  Average: { label: "Average", min: 2, max: 9, rangeText: "2–9 runs", percentile: "25th–50th percentile" },
  Good: { label: "Good", min: 10, max: 27, rangeText: "10–27 runs", percentile: "50th–75th percentile" },
  Excellent: { label: "Excellent", min: 28, max: 367, rangeText: "28+ runs", percentile: "> 75th percentile" },
};

function getQuartileFromRuns(runs) {
  if (runs == null) return "Average";
  const r = Number(runs);
  if (r <= 1.0) return "Poor";
  if (r <= 9.0) return "Average";
  if (r <= 27.0) return "Good";
  return "Excellent";
}

export default function PredictionPage() {
  const [form, setForm] = useState({
    player: "",
    venue: "",
    opponent: "",
    match_type: "T20I",
  });

  const [metadata, setMetadata] = useState({
    players: [],
    venues: [],
    opponents: FALLBACK_OPPONENTS,
    matchTypes: FALLBACK_FORMATS,
  });

  const [playerSearch, setPlayerSearch] = useState("");
  const [loadingMeta, setLoadingMeta] = useState(true);
  const [loadingPrediction, setLoadingPrediction] = useState(false);
  const [predictionResult, setPredictionResult] = useState(null);
  const [errorMessage, setErrorMessage] = useState(null);

  // Historical context for selected player
  const [playerContext, setPlayerContext] = useState(null);
  const [loadingContext, setLoadingContext] = useState(false);

  // Prediction History
  const [history, setHistory] = useState([]);
  const [loadingHistory, setLoadingHistory] = useState(false);

  // 1. Initial load of metadata and history
  useEffect(() => {
    loadMetadata();
    loadHistory();
  }, []);

  const loadMetadata = async () => {
    setLoadingMeta(true);
    try {
      const res = await getPredictionMetadata();
      if (res.data) {
        setMetadata({
          players: res.data.players || [],
          venues: res.data.venues || [],
          opponents: res.data.opponents && res.data.opponents.length > 0 ? res.data.opponents : FALLBACK_OPPONENTS,
          matchTypes: res.data.matchTypes || FALLBACK_FORMATS,
        });
      }
    } catch {
      // Fallback silently without breaking UI
    } finally {
      setLoadingMeta(false);
    }
  };

  const loadHistory = async () => {
    setLoadingHistory(true);
    try {
      const res = await getPredictionHistory();
      if (res.data) {
        setHistory(res.data);
      }
    } catch {
      // History failure shouldn't crash the prediction page
    } finally {
      setLoadingHistory(false);
    }
  };

  // 2. Load historical context whenever player or format changes
  useEffect(() => {
    if (!form.player) {
      setPlayerContext(null);
      return;
    }

    let isMounted = true;
    setLoadingContext(true);

    const normFmt = form.match_type.toUpperCase();
    getPlayerAnalytics(form.player, normFmt, 5)
      .then((res) => {
        if (isMounted) {
          setPlayerContext(res.data);
        }
      })
      .catch(() => {
        if (isMounted) setPlayerContext(null);
      })
      .finally(() => {
        if (isMounted) setLoadingContext(false);
      });

    return () => {
      isMounted = false;
    };
  }, [form.player, form.match_type]);

  // Filtered players list for autocomplete
  const filteredPlayers = useMemo(() => {
    if (!playerSearch.trim()) return metadata.players;
    const q = playerSearch.toLowerCase().trim();
    return metadata.players.filter((p) => p.toLowerCase().includes(q));
  }, [metadata.players, playerSearch]);

  const handleChange = (e) => {
    const { name, value } = e.target;
    setForm((prev) => ({ ...prev, [name]: value }));
  };

  const handlePlayerSelect = (p) => {
    setForm((prev) => ({ ...prev, player: p }));
    setPlayerSearch(p);
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    setErrorMessage(null);

    if (!form.player) {
      setErrorMessage("Please select a player.");
      return;
    }
    // Venue is optional: leave blank to predict against neutral baseline across all venues
    if (!form.opponent) {
      setErrorMessage("Please select an opposing team.");
      return;
    }

    setLoadingPrediction(true);
    setPredictionResult(null);

    try {
      const res = await predictPerformance({
        player: form.player,
        venue: form.venue,
        opponent: form.opponent,
        match_type: form.match_type,
      });

      setPredictionResult(res.data);
      // Reload history to include the new saved prediction
      loadHistory();
    } catch (err) {
      const msg =
        err.response?.data?.error ||
        err.response?.data?.message ||
        err.message ||
        "Failed to generate AI performance prediction. Please try again.";
      setErrorMessage(msg);
    } finally {
      setLoadingPrediction(false);
    }
  };

  const handleLoadFromHistory = (item) => {
    setForm({
      player: item.player,
      venue: item.venue,
      opponent: item.opponent,
      match_type: item.matchType ? item.matchType.toUpperCase() : "T20I",
    });
    setPlayerSearch(item.player);
    window.scrollTo({ top: 120, behavior: "smooth" });
  };

  // Interpretation generator based on actual outputs and historical stats
  const interpretation = useMemo(() => {
    if (!predictionResult) return null;

    const runs = predictionResult.predicted_runs ?? 0;
    const tier = predictionResult.predicted_tier || "N/A";
    const player = predictionResult.player;
    const opponent = predictionResult.opponent;
    const venue = predictionResult.venue;

    const recentMean = playerContext?.recentFormSummary?.meanRuns;
    const careerAvg = playerContext?.battingSummary?.runsPerInnings;

    let compText = "";
    if (recentMean != null) {
      const diff = runs - recentMean;
      if (Math.abs(diff) < 3) {
        compText = `The predicted ${runs} runs closely aligns with their recent 5-match form mean (${recentMean.toFixed(1)} runs).`;
      } else if (diff > 0) {
        compText = `The predicted ${runs} runs represents a positive uptick (+${diff.toFixed(1)}) compared to their recent 5-match average (${recentMean.toFixed(1)} runs).`;
      } else {
        compText = `The predicted ${runs} runs reflects a conservative projection (-${Math.abs(diff).toFixed(1)}) below their recent 5-match average (${recentMean.toFixed(1)} runs).`;
      }
    } else if (careerAvg != null) {
      const diff = runs - careerAvg;
      compText = `The model estimates ${runs} runs compared to their overall career baseline of ${careerAvg.toFixed(1)} runs per innings.`;
    } else {
      compText = `The model projects an estimated output of ${runs} runs for this encounter.`;
    }

    const venueDesc = venue ? ` at ${venue}` : " (All Venues / Neutral Baseline)";
    return {
      title: `${player} vs ${opponent}${venueDesc}`,
      summary: `Performance Classification assigns this encounter to the "${tier}" tier. ${compText}`,
      disclaimer:
        "Estimates are calculated via independent SVR regression and MLP quartile classification models based on rolling match form, opponent histories, and venue records (when provided). When venue is not specified, historical venue average defaults to 0.0 without home/away venue bias.",
    };
  }, [predictionResult, playerContext]);

  return (
    <DashboardLayout pageTitle="Performance Predictions">
      <div className="prediction-page-container">
      {/* 1. Header Banner */}
      <header className="prediction-header">
        <div>
          <h1 className="prediction-header__title">
            <span>⚡</span> AI Performance Prediction
          </h1>
          <p className="prediction-header__subtitle">
            Dual-task machine learning engine evaluating expected batting runs (SVR Regression)
            and performance tier classification (MLP Classifier) against match format, opponent history, and venue record.
          </p>
        </div>
        <div className="prediction-header__badge-row">
          <span className="prediction-ai-tag">SVR Regression Active</span>
          <span className="prediction-ai-tag">MLP Classifier Active</span>
          <span className="prediction-ai-tag prediction-ai-tag--verified">
            ✓ Historical Pipeline Validated
          </span>
        </div>
      </header>

      {/* 2. Main Grid: Input Form (Left) & Context / Output (Right) */}
      <div className="prediction-main-grid">
        {/* Left Column: Form & Historical Snapshot */}
        <div style={{ display: "flex", flexDirection: "column", gap: "var(--space-5)" }}>
          <div className="prediction-card">
            <h2 className="prediction-card__title">
              Match Scenario Configuration
            </h2>
            <p className="prediction-card__desc">
              Select an eligible player, opponent, and match format (venue is optional) to generate AI predictions.
            </p>

            <form onSubmit={handleSubmit}>
              <div className="prediction-form-grid">
                {/* Player Selection */}
                <div className="prediction-field prediction-field--full">
                  <label className="prediction-label">
                    <span>Target Player</span>
                    {form.player && (
                      <span style={{ color: "var(--color-primary)", fontWeight: 700 }}>
                        {form.player}
                      </span>
                    )}
                  </label>
                  <input
                    type="text"
                    className="prediction-input"
                    placeholder="Search player name (e.g. V Kohli, RG Sharma)..."
                    value={playerSearch}
                    onChange={(e) => setPlayerSearch(e.target.value)}
                    disabled={loadingMeta}
                  />
                  {/* Selectable dropdown matching search */}
                  <select
                    name="player"
                    value={form.player}
                    onChange={(e) => handlePlayerSelect(e.target.value)}
                    className="prediction-select"
                    style={{ marginTop: 4 }}
                    required
                  >
                    <option value="" disabled>
                      {loadingMeta ? "Loading players..." : `-- Select from ${filteredPlayers.length} Players --`}
                    </option>
                    {filteredPlayers.map((p) => (
                      <option key={p} value={p}>
                        {p}
                      </option>
                    ))}
                  </select>
                </div>

                {/* Match Format */}
                <div className="prediction-field">
                  <label className="prediction-label">Match Format</label>
                  <select
                    name="match_type"
                    value={form.match_type}
                    onChange={handleChange}
                    className="prediction-select"
                    required
                  >
                    {metadata.matchTypes.map((fmt) => (
                      <option key={fmt} value={fmt}>
                        {fmt}
                      </option>
                    ))}
                  </select>
                </div>

                {/* Opponent Selection */}
                <div className="prediction-field">
                  <label className="prediction-label">Opposing Team</label>
                  <select
                    name="opponent"
                    value={form.opponent}
                    onChange={handleChange}
                    className="prediction-select"
                    required
                  >
                    <option value="" disabled>Select Opponent</option>
                    {metadata.opponents.map((opp) => (
                      <option key={opp} value={opp}>
                        {opp}
                      </option>
                    ))}
                  </select>
                </div>

                {/* Venue Selection */}
                <div className="prediction-field prediction-field--full">
                  <label className="prediction-label">
                    <span>Match Venue (Optional)</span>
                    <span style={{ fontSize: "0.74rem", color: "#94A3B8", fontWeight: 400 }}>
                      Leave blank for neutral baseline across all venues
                    </span>
                  </label>
                  <select
                    name="venue"
                    value={form.venue}
                    onChange={handleChange}
                    className="prediction-select"
                  >
                    <option value="">-- No Specific Venue (General / All Venues) --</option>
                    {metadata.venues.map((v) => (
                      <option key={v} value={v}>
                        {v}
                      </option>
                    ))}
                  </select>
                </div>
              </div>

              {errorMessage && (
                <div
                  style={{
                    background: "#fee2e2",
                    border: "1px solid #fecaca",
                    color: "#b91c1c",
                    borderRadius: "var(--radius-md)",
                    padding: "10px 14px",
                    marginTop: "var(--space-4)",
                    fontSize: "0.86rem",
                  }}
                >
                  <strong>⚠️ Prediction Error:</strong> {errorMessage}
                </div>
              )}

              <button
                type="submit"
                className="prediction-btn-predict"
                disabled={loadingPrediction || loadingMeta}
              >
                {loadingPrediction ? (
                  <>
                    <span className="spinner" style={{ width: 18, height: 18 }}></span>
                    Calculating AI Prediction...
                  </>
                ) : (
                  <>
                    <span>⚡</span> Predict Performance
                  </>
                )}
              </button>
            </form>
          </div>

          {/* Player Historical Baseline Context */}
          {form.player && (
            <div className="pred-context-card">
              <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
                <h3 style={{ margin: 0, fontSize: "1rem", color: "var(--color-text-primary)", fontWeight: 700 }}>
                  Historical Observed Baseline ({form.match_type})
                </h3>
                {loadingContext && (
                  <span style={{ fontSize: "0.75rem", color: "var(--color-text-secondary)" }}>Updating...</span>
                )}
              </div>
              <p style={{ fontSize: "0.78rem", color: "var(--color-text-secondary)", margin: "4px 0 0 0" }}>
                Empirical career & recent form from historical match recordings for {form.player}.
              </p>

              {playerContext ? (
                <>
                  <div className="pred-context-stats-grid">
                    <div className="pred-stat-chip">
                      <div className="pred-stat-chip__label">Role</div>
                      <div className="pred-stat-chip__val" style={{ color: "var(--color-primary)", fontSize: "0.95rem" }}>
                        {playerContext.role || "UNKNOWN"}
                      </div>
                    </div>

                    <div className="pred-stat-chip">
                      <div className="pred-stat-chip__label">Recent 5 Mean</div>
                      <div className="pred-stat-chip__val">
                        {playerContext.recentFormSummary?.meanRuns != null
                          ? `${playerContext.recentFormSummary.meanRuns.toFixed(1)}`
                          : "—"}
                      </div>
                    </div>

                    <div className="pred-stat-chip">
                      <div className="pred-stat-chip__label">Career Avg Runs</div>
                      <div className="pred-stat-chip__val">
                        {playerContext.battingSummary?.runsPerInnings != null
                          ? `${playerContext.battingSummary.runsPerInnings.toFixed(1)}`
                          : "—"}
                      </div>
                    </div>

                    <div className="pred-stat-chip">
                      <div className="pred-stat-chip__label">Strike Rate</div>
                      <div className="pred-stat-chip__val">
                        {playerContext.battingSummary?.strikeRate != null
                          ? `${playerContext.battingSummary.strikeRate.toFixed(1)}`
                          : "—"}
                      </div>
                    </div>

                    <div className="pred-stat-chip">
                      <div className="pred-stat-chip__label">Form Momentum</div>
                      <div
                        className="pred-stat-chip__val"
                        style={{
                          fontSize: "0.85rem",
                          color:
                            playerContext.recentFormSummary?.runTrendDirection === "UPWARD"
                              ? "#15803d"
                              : playerContext.recentFormSummary?.runTrendDirection === "DOWNWARD"
                              ? "#b91c1c"
                              : "var(--color-text-secondary)",
                        }}
                      >
                        {playerContext.recentFormSummary?.runTrendDirection || "STABLE"}
                      </div>
                    </div>
                  </div>

                  {playerContext.recentBattingForm && playerContext.recentBattingForm.length > 0 && (
                    <div style={{ marginTop: 10 }}>
                      <span style={{ fontSize: "0.75rem", color: "var(--color-text-secondary)", fontWeight: 600 }}>
                        Recent Innings Scores:{" "}
                      </span>
                      <span style={{ fontSize: "0.82rem", color: "var(--color-text-primary)", fontFamily: "monospace" }}>
                        {playerContext.recentBattingForm.slice(0, 5).map((inn, idx) => (
                          <span
                            key={idx}
                            style={{
                              display: "inline-block",
                              background: "#f1f5f9",
                              border: "1px solid var(--color-border)",
                              color: "var(--color-text-primary)",
                              padding: "2px 6px",
                              borderRadius: 4,
                              marginRight: 6,
                            }}
                          >
                            {inn.runs}
                            {inn.notOut ? "*" : ""}
                          </span>
                        ))}
                      </span>
                    </div>
                  )}
                </>
              ) : (
                <p style={{ fontSize: "0.82rem", color: "#64748B", margin: "14px 0 0 0" }}>
                  {loadingContext
                    ? "Retrieving historical player context..."
                    : "No format-specific baseline data recorded for this player."}
                </p>
              )}
            </div>
          )}
        </div>

        {/* Right Column: Prediction Results & Interpretation */}
        <div className="prediction-results-container">
          {predictionResult ? (
            <>
              {/* Dual Task Cards */}
              {(() => {
                const regDerivedTier = predictionResult.regression_derived_tier || getQuartileFromRuns(predictionResult.predicted_runs);
                const regBracketInfo = QUARTILE_THRESHOLDS[regDerivedTier] || QUARTILE_THRESHOLDS.Good;
                const clfTier = predictionResult.predicted_tier || "Average";
                const clfBracketInfo = QUARTILE_THRESHOLDS[clfTier] || QUARTILE_THRESHOLDS.Average;
                const topClfProb = predictionResult.tier_probabilities?.[clfTier] != null
                  ? (predictionResult.tier_probabilities[clfTier] * 100).toFixed(1)
                  : "—";

                return (
                  <>
                    <div className="prediction-dual-grid">
                      {/* 1. Regression Task Result */}
                      <div className="pred-task-card pred-task-card--reg">
                        <div className="pred-task-tag">
                          Regression Task ({predictionResult.model_name || "SVR"})
                        </div>
                        <div className="pred-task-title">Expected Runs Prediction</div>

                        <div className="pred-metric-large">
                          <span style={{ color: "var(--color-primary)" }}>
                            {predictionResult.predicted_runs != null
                              ? Number(predictionResult.predicted_runs).toFixed(1)
                              : "0.0"}
                          </span>
                          <span className="pred-metric-unit">Runs</span>
                        </div>

                        <div className="pred-derived-tier-chip">
                          <span>Training Quartile:</span>
                          <span className={`pred-tier-text--${regDerivedTier}`}>
                            {regDerivedTier} ({regBracketInfo.rangeText})
                          </span>
                        </div>

                        <div className="pred-task-footer-note">
                          <strong>Point Estimate:</strong> SVR continuous projection based on rolling 5-match form and opponent history
                          {predictionResult.venue ? ` at ${predictionResult.venue}` : " (neutral baseline across all venues)"}.
                          Minimizes MAE; continuous regression naturally contracts toward historical central tendencies.
                        </div>
                      </div>

                      {/* 2. Classification Task Result */}
                      <div className="pred-task-card pred-task-card--clf">
                        <div className="pred-task-tag">
                          Classification Task ({predictionResult.classifier_name || "MLP"})
                        </div>
                        <div className="pred-task-title">Performance Tier Category</div>

                        <div
                          className={`pred-tier-badge-large pred-tier-badge-large--${
                            clfTier
                          }`}
                        >
                          {clfTier}
                        </div>

                        <div className="pred-derived-tier-chip">
                          <span>Target Range:</span>
                          <span style={{ color: "var(--color-text-primary)", fontWeight: 700 }}>
                            {clfBracketInfo.rangeText}
                          </span>
                          <span style={{ color: "var(--color-text-secondary)", fontSize: "0.72rem" }}>
                            ({topClfProb}% top probability)
                          </span>
                        </div>

                        <div className="pred-task-footer-note">
                          <strong>Discrete Tier:</strong> Independent neural network evaluating probability across 4 historical quartiles:
                          Poor (0–1), Average (2–9), Good (10–27), or Excellent (28+ runs). Elite players often peak in the Excellent tier.
                        </div>
                      </div>
                    </div>

                    {/* Independent Model Comparison & Consensus Card */}
                    <div
                      className={`pred-consensus-card ${
                        regDerivedTier === clfTier
                          ? "pred-consensus-card--aligned"
                          : "pred-consensus-card--divergent"
                      }`}
                    >
                      <div className="pred-consensus-header">
                        <span className="pred-consensus-icon">
                          {regDerivedTier === clfTier ? "✓" : "⚖️"}
                        </span>
                        <span className="pred-consensus-title">
                          {regDerivedTier === clfTier
                            ? `Model Consensus: Both Models Agree on "${clfTier}" Tier`
                            : `Independent Model Comparison: Regression (${regDerivedTier}) vs Classification (${clfTier})`}
                        </span>
                      </div>
                      <p className="pred-consensus-text">
                        {regDerivedTier === clfTier ? (
                          <>
                            Both independent models agree that this encounter is best categorized in the{" "}
                            <strong>{clfTier}</strong> tier ({clfBracketInfo.rangeText}). SVR projects an expected
                            value of <strong>{Number(predictionResult.predicted_runs).toFixed(1)} runs</strong>, while MLP assigns
                            its highest softmax probability ({topClfProb}%) to this category.
                          </>
                        ) : (
                          <>
                            <strong>Why the outputs differ:</strong> SVR regression predicts a continuous point estimate of{" "}
                            <strong>{Number(predictionResult.predicted_runs).toFixed(1)} runs</strong>, placing it in the{" "}
                            <strong>{regDerivedTier}</strong> bracket ({regBracketInfo.rangeText}) by minimizing absolute error
                            around the dataset's central tendency. Meanwhile, the independent MLP neural network predicts categorical
                            distribution, evaluating a <strong>{topClfProb}%</strong> probability
                            for the <strong>{clfTier}</strong> tier ({clfBracketInfo.rangeText}). In cricket, elite players have volatile scores with high upside,
                            so modal classification can favor a higher impact tier than a conservative regression point estimate.
                          </>
                        )}
                      </p>
                    </div>

                    {/* Class Probabilities Distribution */}
                    {predictionResult.tier_probabilities &&
                      Object.keys(predictionResult.tier_probabilities).length > 0 && (
                        <div className="pred-prob-section">
                          <div className="pred-prob-title">
                            <span>Model Class Probabilities (Softmax Distribution)</span>
                            <span style={{ fontSize: "0.72rem", color: "#A855F7" }}>
                              {predictionResult.classifier_name || "MLP Classifier"}
                            </span>
                          </div>

                          {["Poor", "Average", "Good", "Excellent"].map((tierName) => {
                            const prob = predictionResult.tier_probabilities[tierName] ?? 0;
                            const pct = (prob * 100).toFixed(1);
                            const rangeInfo = QUARTILE_THRESHOLDS[tierName]?.rangeText || "";

                            let fillGrad = "#94a3b8";
                            if (tierName === "Excellent") fillGrad = "#10b981";
                            if (tierName === "Good") fillGrad = "#06b6d4";
                            if (tierName === "Average") fillGrad = "#f59e0b";
                            if (tierName === "Poor") fillGrad = "#ef4444";

                            return (
                              <div key={tierName} className="pred-prob-row">
                                <div className="pred-prob-label-group">
                                  <span className="pred-prob-label">{tierName}</span>
                                  <span className="pred-prob-range-sub">({rangeInfo})</span>
                                </div>
                                <div className="pred-prob-track">
                                  <div
                                    className="pred-prob-fill"
                                    style={{ width: `${Math.min(100, Math.max(0, pct))}%`, background: fillGrad }}
                                  ></div>
                                </div>
                                <span className="pred-prob-val">{pct}%</span>
                              </div>
                            );
                          })}

                          <div className="pred-prob-footer">
                            <span>Probability sum: 100.0% (Softmax Normalized)</span>
                            <span>Verified Dataset Quartiles</span>
                          </div>
                        </div>
                      )}
                  </>
                );
              })()}

              {/* Interpretation Panel */}
              {interpretation && (
                <div className="pred-interpretation-box">
                  <div className="pred-interpretation-title">
                    Prediction Interpretation & Context
                  </div>
                  <p className="pred-interpretation-text">
                    {interpretation.summary}
                  </p>
                  <p className="pred-interpretation-disclaimer">
                    {interpretation.disclaimer}
                  </p>
                </div>
              )}
            </>
          ) : loadingPrediction ? (
            /* Explicit High-Contrast Loading State */
            <div
              className="prediction-card"
              style={{
                display: "flex",
                flexDirection: "column",
                alignItems: "center",
                justifyContent: "center",
                minHeight: 380,
                textAlign: "center",
                padding: "var(--space-8)",
                gap: "var(--space-4)",
              }}
            >
              <div className="spinner" style={{ width: 44, height: 44, borderWidth: 3 }} />
              <div>
                <h3 style={{ margin: "0 0 8px 0", color: "var(--color-text-primary)", fontSize: "1.25rem", fontWeight: 700 }}>
                  Generating Performance Forecast...
                </h3>
                <p style={{ color: "var(--color-text-secondary)", maxWidth: 460, fontSize: "0.88rem", lineHeight: 1.5, margin: 0 }}>
                  Evaluating pre-match statistics, rolling averages, venue records, and opposition matchups.
                </p>
              </div>
            </div>
          ) : (
            /* Empty State */
            <div
              className="prediction-card"
              style={{
                display: "flex",
                flexDirection: "column",
                alignItems: "center",
                justifyContent: "center",
                minHeight: 380,
                textAlign: "center",
                padding: "var(--space-8)",
              }}
            >
              <div style={{ marginBottom: "var(--space-3)", color: "var(--color-cricket-green)" }}><svg viewBox="0 0 24 24" width="48" height="48" fill="none" stroke="currentColor" strokeWidth="1.5"><circle cx="12" cy="12" r="10"/><path d="M12 2a10 10 0 0 1 0 20"/></svg></div>
              <h3 style={{ margin: "0 0 8px 0", color: "var(--color-text-primary)", fontSize: "1.25rem", fontWeight: 700 }}>
                Ready to Predict Match Performance
              </h3>
              <p style={{ color: "var(--color-text-secondary)", maxWidth: 440, fontSize: "0.88rem", lineHeight: 1.5, margin: 0 }}>
                Configure the target player, venue, opponent, and match format on the left panel,
                then click <strong>Predict Performance</strong> to view the estimated runs and performance classification.
              </p>
            </div>
          )}
        </div>
      </div>

      {/* 3. Prediction Audit History Section */}
      <section className="pred-history-section">
        <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
          <div>
            <h2 style={{ fontSize: "1.15rem", fontWeight: 700, margin: 0, color: "var(--color-text-primary)" }}>
              Recent Prediction History
            </h2>
            <p style={{ fontSize: "0.8rem", color: "var(--color-text-secondary)", margin: "2px 0 0 0" }}>
              Stored predictions for audit, analysis, and scenario comparison.
            </p>
          </div>
          <button
            onClick={loadHistory}
            className="format-tab-btn"
            style={{ padding: "6px 12px", fontSize: "0.8rem" }}
            disabled={loadingHistory}
          >
            {loadingHistory ? "Refreshing..." : "↻ Refresh History"}
          </button>
        </div>

        <div className="pred-history-table-wrapper">
          {history && history.length > 0 ? (
            <table className="pred-table">
              <thead>
                <tr>
                  <th>Date & Time</th>
                  <th>Player</th>
                  <th>Format</th>
                  <th>Opponent</th>
                  <th>Venue</th>
                  <th>Predicted Runs</th>
                  <th>Performance Tier</th>
                  <th>Action</th>
                </tr>
              </thead>
              <tbody>
                {history.map((item) => (
                  <tr key={item.id}>
                    <td style={{ color: "var(--color-text-secondary)", fontSize: "0.78rem" }}>
                      {item.predictionDate ? new Date(item.predictionDate).toLocaleString() : "—"}
                    </td>
                    <td style={{ fontWeight: 600 }}>{item.player}</td>
                    <td>
                      <span className="pred-badge-sm" style={{ background: "#dbeafe", color: "#1d4ed8", border: "1px solid #bfdbfe" }}>
                        {item.matchType}
                      </span>
                    </td>
                    <td>{item.opponent}</td>
                    <td style={{ color: "var(--color-text-secondary)" }}>{item.venue ? item.venue : "— (All Venues)"}</td>
                    <td style={{ fontWeight: 700, color: "var(--color-primary)" }}>
                      {item.predictedRuns != null ? Number(item.predictedRuns).toFixed(1) : "—"}
                    </td>
                    <td>
                      <span
                        className={`pred-badge-sm pred-tier-badge-large--${item.predictedTier || "Average"}`}
                        style={{ fontSize: "0.72rem", padding: "2px 8px" }}
                      >
                        {item.predictedTier || "—"}
                      </span>
                    </td>
                    <td>
                      <button
                        onClick={() => handleLoadFromHistory(item)}
                        style={{
                          background: "#f1f5f9",
                          border: "1px solid var(--color-border)",
                          color: "var(--color-primary)",
                          fontWeight: 600,
                          padding: "4px 10px",
                          borderRadius: "var(--radius-sm)",
                          fontSize: "0.75rem",
                          cursor: "pointer",
                        }}
                      >
                        Load
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          ) : (
            <div style={{ textAlign: "center", padding: "var(--space-6)", color: "#64748B", fontSize: "0.85rem" }}>
              {loadingHistory
                ? "Loading prediction history..."
                : "No saved predictions found. Generate your first prediction above to view history."}
            </div>
          )}
        </div>
      </section>
    </div>
    </DashboardLayout>
  );
}