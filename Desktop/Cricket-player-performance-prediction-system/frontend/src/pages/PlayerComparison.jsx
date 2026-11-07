/**
 * PlayerComparison.jsx
 *
 * Enhanced Cricket Player Performance Comparison Feature.
 *
 * Organised into 5 explicit sections:
 *  1. Player Selection and Format
 *  2. Overall Statistical Comparison
 *  3. Recent Form and Consistency Analysis
 *  4. Venue and Opposition Context Analysis
 *  5. Key Findings and Data Limitations
 */

import { useState, useEffect, useCallback, useRef } from 'react';
import {
  Chart as ChartJS,
  CategoryScale, LinearScale, BarElement,
  PointElement, LineElement, Title, Tooltip, Legend, Filler,
} from 'chart.js';
import { Bar, Line } from 'react-chartjs-2';
import { listAnalyticsPlayers, comparePlayers, exportPlayerComparisonExcel } from '../services/analyticsService';
import { exportPlayerComparisonToExcel } from '../services/excelExportService';
import { predictPerformance } from '../services/predictionService';
import DashboardLayout from '../components/layout/DashboardLayout';

ChartJS.register(
  CategoryScale, LinearScale, BarElement,
  PointElement, LineElement, Title, Tooltip, Legend, Filler
);

const FORMATS = ['ALL', 'ODI', 'T20I', 'TEST'];
const RECENT_WINDOWS = [5, 10, 15];

const P1_COLOR = {
  bg: 'rgba(99, 102, 241, 0.75)',
  border: '#6366F1',
  lineBg: 'rgba(99, 102, 241, 0.15)',
};

const P2_COLOR = {
  bg: 'rgba(236, 72, 153, 0.75)',
  border: '#EC4899',
  lineBg: 'rgba(236, 72, 153, 0.15)',
};

function roleBadge(role) {
  const map = {
    BATSMAN:     { icon: '', label: 'Batsman', cls: 'role-badge--batsman' },
    BOWLER:      { icon: '', label: 'Bowler', cls: 'role-badge--bowler' },
    ALL_ROUNDER: { icon: '⚡', label: 'All-Rounder', cls: 'role-badge--all-rounder' },
    UNKNOWN:     { icon: '❓', label: 'Unknown', cls: 'role-badge--unknown' },
  };
  const r = map[role] || map.UNKNOWN;
  return <span className={`role-badge ${r.cls}`}>{r.icon} {r.label}</span>;
}

function trendBadge(direction) {
  if (direction === 'UPWARD') {
    return <span className="trend-badge trend-badge--upward">Upward Momentum</span>;
  }
  if (direction === 'DOWNWARD') {
    return <span className="trend-badge trend-badge--downward">Downward Trajectory</span>;
  }
  if (direction === 'STABLE') {
    return <span className="trend-badge trend-badge--stable">➡️ Consistent Form</span>;
  }
  return <span className="trend-badge trend-badge--stable">ℹ️ Sample In Progress</span>;
}

function getPerformanceTier(runs) {
  if (runs >= 50) return { label: 'Tier 1 · Match Winner', cls: 'tier-badge--tier1' };
  if (runs >= 25) return { label: 'Tier 2 · Core Performer', cls: 'tier-badge--tier2' };
  return { label: 'Tier 3 · Supporting Role', cls: 'tier-badge--tier3' };
}

export default function PlayerComparison() {
  const [allPlayers, setAllPlayers] = useState([]);
  const [player1, setPlayer1] = useState('');
  const [player2, setPlayer2] = useState('');
  const [selectedFormat, setSelectedFormat] = useState('ALL');
  const [selectedRecentN, setSelectedRecentN] = useState(15);
  const [selectedOpposition, setSelectedOpposition] = useState('');
  const [selectedVenue, setSelectedVenue] = useState('');

  const [input1, setInput1] = useState('');
  const [input2, setInput2] = useState('');
  const [suggestions1, setSuggestions1] = useState([]);
  const [suggestions2, setSuggestions2] = useState([]);
  const [showDropdown1, setShowDropdown1] = useState(false);
  const [showDropdown2, setShowDropdown2] = useState(false);

  const [comparison, setComparison] = useState(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

  // AI Prediction State
  const [predOpponent, setPredOpponent] = useState('');
  const [predVenue, setPredVenue] = useState('');
  const [predFormat, setPredFormat] = useState('ODI');
  const [predLoading, setPredLoading] = useState(false);
  const [predResult, setPredResult] = useState(null);
  const [predError, setPredError] = useState('');
  const [exporting, setExporting] = useState(false);
  const [exportError, setExportError] = useState(null);
  const [exportSuccessMsg, setExportSuccessMsg] = useState(null);

  const ref1 = useRef(null);
  const ref2 = useRef(null);

  // Load available players list once
  useEffect(() => {
    listAnalyticsPlayers()
      .then(res => setAllPlayers(res.data || []))
      .catch(() => setAllPlayers([]));
  }, []);

  // Filter autocomplete for Player 1
  useEffect(() => {
    const q = input1.trim().toLowerCase();
    if (q.length < 2) { setSuggestions1([]); return; }
    const tokens = q.split(/\s+/);
    const last = tokens[tokens.length - 1];
    setSuggestions1(
      allPlayers.filter(p => {
        if (player2 && p.toLowerCase() === player2.toLowerCase()) return false;
        const pl = p.toLowerCase();
        return pl.includes(q) || (tokens.length > 1 && pl.includes(last));
      }).slice(0, 8)
    );
  }, [input1, allPlayers, player2]);

  // Filter autocomplete for Player 2
  useEffect(() => {
    const q = input2.trim().toLowerCase();
    if (q.length < 2) { setSuggestions2([]); return; }
    const tokens = q.split(/\s+/);
    const last = tokens[tokens.length - 1];
    setSuggestions2(
      allPlayers.filter(p => {
        if (player1 && p.toLowerCase() === player1.toLowerCase()) return false;
        const pl = p.toLowerCase();
        return pl.includes(q) || (tokens.length > 1 && pl.includes(last));
      }).slice(0, 8)
    );
  }, [input2, allPlayers, player1]);

  // Execute comparison
  const handleCompare = useCallback(async (
    p1Name, p2Name, fmt, recentN, opp, ven
  ) => {
    const p1 = (p1Name || player1 || input1).trim();
    const p2 = (p2Name || player2 || input2).trim();
    const formatToUse = fmt !== undefined ? fmt : selectedFormat;
    const nToUse = recentN !== undefined ? recentN : selectedRecentN;
    const oppToUse = opp !== undefined ? opp : selectedOpposition;
    const venToUse = ven !== undefined ? ven : selectedVenue;

    if (!p1 || !p2) {
      setError('Please select both Player 1 and Player 2 before comparing.');
      return;
    }

    if (p1.toLowerCase() === p2.toLowerCase()) {
      setError('Cannot compare a player with themselves. Please choose two distinct players.');
      return;
    }

    setLoading(true);
    setError('');
    setShowDropdown1(false);
    setShowDropdown2(false);

    try {
      const res = await comparePlayers(p1, p2, formatToUse, nToUse, oppToUse || null, venToUse || null);
      setComparison(res.data);
      setPlayer1(res.data.player1Name);
      setPlayer2(res.data.player2Name);
      setInput1(res.data.player1Name);
      setInput2(res.data.player2Name);
      if (!predOpponent && res.data.contextInfo?.commonOppositions?.length > 0) {
        setPredOpponent(res.data.contextInfo.commonOppositions[0]);
      }
      if (!predVenue && res.data.contextInfo?.commonVenues?.length > 0) {
        setPredVenue(res.data.contextInfo.commonVenues[0]);
      }
    } catch (err) {
      const msg = err.response?.data?.error || err.message || 'Comparison failed. Please verify player names.';
      setError(msg);
      setComparison(null);
    } finally {
      setLoading(false);
    }
  }, [player1, player2, input1, input2, selectedFormat, selectedRecentN, selectedOpposition, selectedVenue, predOpponent, predVenue]);

  const handleFormatChange = (fmt) => {
    setSelectedFormat(fmt);
    if (player1 && player2) {
      handleCompare(player1, player2, fmt, selectedRecentN, selectedOpposition, selectedVenue);
    }
  };

  const handleRecentNChange = (n) => {
    setSelectedRecentN(n);
    if (player1 && player2) {
      handleCompare(player1, player2, selectedFormat, n, selectedOpposition, selectedVenue);
    }
  };

  const handleOppositionChange = (opp) => {
    setSelectedOpposition(opp);
    if (player1 && player2) {
      handleCompare(player1, player2, selectedFormat, selectedRecentN, opp, selectedVenue);
    }
  };

  const handleVenueChange = (ven) => {
    setSelectedVenue(ven);
    if (player1 && player2) {
      handleCompare(player1, player2, selectedFormat, selectedRecentN, selectedOpposition, ven);
    }
  };

  const clearFilters = () => {
    setSelectedOpposition('');
    setSelectedVenue('');
    if (player1 && player2) {
      handleCompare(player1, player2, selectedFormat, selectedRecentN, '', '');
    }
  };

  const selectPlayer1 = (name) => {
    if (player2 && name.toLowerCase() === player2.toLowerCase()) {
      setError(`Cannot compare ${name} with themselves. Please choose a different player.`);
      return;
    }
    setError('');
    setPlayer1(name);
    setInput1(name);
    setShowDropdown1(false);
  };

  const selectPlayer2 = (name) => {
    if (player1 && name.toLowerCase() === player1.toLowerCase()) {
      setError(`Cannot compare ${name} with themselves. Please choose a different player.`);
      return;
    }
    setError('');
    setPlayer2(name);
    setInput2(name);
    setShowDropdown2(false);
  };

  const swapPlayers = () => {
    const tempP = player1;
    const tempIn = input1;
    setPlayer1(player2);
    setInput1(input2);
    setPlayer2(tempP);
    setInput2(tempIn);
    if (player1 && player2) {
      handleCompare(player2, player1, selectedFormat, selectedRecentN, selectedOpposition, selectedVenue);
    }
  };

  // Run AI prediction for both players
  const handleRunPrediction = async () => {
    if (!player1 || !player2) return;
    const opp = predOpponent || comparison?.contextInfo?.commonOppositions?.[0] || 'Australia';
    const ven = predVenue || comparison?.contextInfo?.commonVenues?.[0] || 'Melbourne Cricket Ground';
    const matchType = predFormat || (selectedFormat !== 'ALL' ? selectedFormat : 'ODI');

    setPredLoading(true);
    setPredError('');
    setPredResult(null);

    try {
      const [res1, res2] = await Promise.all([
        predictPerformance({ player: player1, opponent: opp, venue: ven, match_type: matchType }),
        predictPerformance({ player: player2, opponent: opp, venue: ven, match_type: matchType })
      ]);

      const runs1 = res1.data?.predicted_runs ?? 0;
      const runs2 = res2.data?.predicted_runs ?? 0;

      let adv = 'tie';
      if (runs1 > runs2) adv = 'player1';
      else if (runs2 > runs1) adv = 'player2';

      setPredResult({
        opponent: opp,
        venue: ven,
        format: matchType,
        runs1,
        runs2,
        adv,
        tier1: getPerformanceTier(runs1),
        tier2: getPerformanceTier(runs2),
      });
    } catch (err) {
      setPredError(err.response?.data?.message || err.message || 'AI prediction service unavailable. Please check backend connection.');
    } finally {
      setPredLoading(false);
    }
  };

  const handleExportExcel = async () => {
    if (!comparison) return;
    setExporting(true);
    setExportError(null);
    setExportSuccessMsg(null);
    try {
      // 1. Primary: Server-generated native Excel with DrawingML charts
      const response = await exportPlayerComparisonExcel(
        player1,
        player2,
        selectedFormat,
        selectedRecentN,
        selectedOpposition || null,
        selectedVenue || null
      );
      const disposition = response.headers?.['content-disposition'];
      let filename = `CricketAI_Player_Comparison_${player1.replace(/[^a-zA-Z0-9_-]/g, '_')}_vs_${player2.replace(/[^a-zA-Z0-9_-]/g, '_')}_${selectedFormat}.xlsx`;
      if (disposition) {
        const match = disposition.match(/filename=["']?([^"';]+)["']?/i);
        if (match && match[1]) filename = match[1].trim();
      }
      const blob = new Blob([response.data], {
        type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
      });
      const url = window.URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = filename;
      document.body.appendChild(a);
      a.click();
      window.URL.revokeObjectURL(url);
      document.body.removeChild(a);

      setExportSuccessMsg(`Exported: ${filename} (Native charts included)`);
      setTimeout(() => setExportSuccessMsg(null), 5000);
    } catch (err) {
      console.warn('Native comparison chart server export failed, falling back to client-side generator:', err);
      try {
        const fallbackFilename = await exportPlayerComparisonToExcel({
          comparison,
          selectedFormat,
          selectedRecentN,
          selectedOpposition,
          selectedVenue,
        });
        setExportSuccessMsg(`Exported: ${fallbackFilename}`);
        setTimeout(() => setExportSuccessMsg(null), 5000);
      } catch (fallbackErr) {
        console.error('Failed to export Player Comparison to Excel:', fallbackErr);
        setExportError(fallbackErr.message || 'Failed to generate comparison Excel report.');
        setTimeout(() => setExportError(null), 6000);
      }
    } finally {
      setExporting(false);
    }
  };

  // Helper to render metric row in comparison table
  const renderMetricRow = (label, v1, v2, detailKey, fallbackUnit = '') => {
    const detail = comparison?.metricAdvantagesDetailed?.[detailKey];
    const isP1Adv = detail?.advantage === 'player1';
    const isP2Adv = detail?.advantage === 'player2';
    const isTie = detail?.advantage === 'tie';

    let badgeText = '';
    if (isP1Adv || isP2Adv) {
      if (detail?.criterion === 'LOWER_IS_BETTER') badgeText = 'Better';
      else if (detail?.criterion === 'LOWER_VARIABILITY') badgeText = 'More Consistent';
      else badgeText = 'Higher';
    }

    const diff = detail?.difference != null ? Math.abs(detail.difference) : null;
    const pct = detail?.percentageDifference != null ? `(${detail.percentageDifference}%)` : '';

    return (
      <tr key={label}>
        <td className={`compare-val ${isP1Adv ? 'compare-val--adv' : ''}`}>
          {v1 != null ? `${v1}${fallbackUnit}` : <span style={{ color: '#94A3B8' }}>N/A</span>}
          {isP1Adv && <span className="compare-adv-badge">{badgeText}</span>}
          {isP1Adv && diff != null && <span className="compare-diff-tag">+{diff} {pct}</span>}
        </td>
        <td className="compare-metric-name">
          {label}
          {isTie && <span className="compare-adv-badge" style={{ background: '#f1f5f9', color: '#475569', borderColor: '#cbd5e1' }}>TIE</span>}
        </td>
        <td className={`compare-val ${isP2Adv ? 'compare-val--adv' : ''}`}>
          {v2 != null ? `${v2}${fallbackUnit}` : <span style={{ color: '#94A3B8' }}>N/A</span>}
          {isP2Adv && <span className="compare-adv-badge">{badgeText}</span>}
          {isP2Adv && diff != null && <span className="compare-diff-tag">+{diff} {pct}</span>}
        </td>
      </tr>
    );
  };

  const p1 = comparison?.player1;
  const p2 = comparison?.player2;
  const batComp = comparison?.battingComparison;
  const bowlComp = comparison?.bowlingComparison;
  const recentSummary = comparison?.recentFormSummary;
  const context = comparison?.contextInfo;
  const scope = comparison?.comparisonScope;

  const showBatting = batComp && (batComp.innings1 > 0 || batComp.innings2 > 0);
  const showBowling = bowlComp && (bowlComp.innings1 > 0 || bowlComp.innings2 > 0);

  // Role detection for Recent Form & Match History Breakdown
  const isBowlerVsBowler = (p1?.role === 'BOWLER' && p2?.role === 'BOWLER') || scope === 'BOWLING_ONLY';
  const isAllRounderVsAllRounder = p1?.role === 'ALL_ROUNDER' && p2?.role === 'ALL_ROUNDER';
  const isBowlingRecentFocus = isBowlerVsBowler || isAllRounderVsAllRounder || recentSummary?.focusDiscipline === 'BOWLING';

  // Batting Chart Data
  const battingChartData = showBatting ? {
    labels: ['Total Runs', 'Strike Rate', 'Runs / Inn', 'Boundary Run %', 'Fours', 'Sixes'],
    datasets: [
      {
        label: comparison.player1Name,
        data: [
          batComp.totalRuns1,
          batComp.strikeRate1 ?? 0,
          batComp.runsPerInnings1 ?? 0,
          batComp.boundaryPercentage1 ?? 0,
          batComp.fours1,
          batComp.sixes1,
        ],
        backgroundColor: P1_COLOR.bg,
        borderColor: P1_COLOR.border,
        borderWidth: 2,
        borderRadius: 4,
      },
      {
        label: comparison.player2Name,
        data: [
          batComp.totalRuns2,
          batComp.strikeRate2 ?? 0,
          batComp.runsPerInnings2 ?? 0,
          batComp.boundaryPercentage2 ?? 0,
          batComp.fours2,
          batComp.sixes2,
        ],
        backgroundColor: P2_COLOR.bg,
        borderColor: P2_COLOR.border,
        borderWidth: 2,
        borderRadius: 4,
      },
    ],
  } : null;

  // Bowling Chart Data
  const bowlingChartData = showBowling ? {
    labels: ['Wickets', 'Overs Bowled', 'Economy Rate', 'Bowling Avg', 'Strike Rate (balls/w)'],
    datasets: [
      {
        label: comparison.player1Name,
        data: [
          bowlComp.totalWickets1,
          bowlComp.totalOvers1,
          bowlComp.economyRate1 ?? 0,
          bowlComp.bowlingAverage1 ?? 0,
          bowlComp.bowlingStrikeRate1 ?? 0,
        ],
        backgroundColor: P1_COLOR.bg,
        borderColor: P1_COLOR.border,
        borderWidth: 2,
        borderRadius: 4,
      },
      {
        label: comparison.player2Name,
        data: [
          bowlComp.totalWickets2,
          bowlComp.totalOvers2,
          bowlComp.economyRate2 ?? 0,
          bowlComp.bowlingAverage2 ?? 0,
          bowlComp.bowlingStrikeRate2 ?? 0,
        ],
        backgroundColor: P2_COLOR.bg,
        borderColor: P2_COLOR.border,
        borderWidth: 2,
        borderRadius: 4,
      },
    ],
  } : null;

  // Recent Form Trend Chart Data
  const recentFormChartData = (() => {
    if (!comparison) return null;
    const isBattingFocus = !isBowlingRecentFocus;
    const form1 = isBattingFocus ? (p1?.recentBattingForm || []) : (p1?.recentBowlingForm || []);
    const form2 = isBattingFocus ? (p2?.recentBattingForm || []) : (p2?.recentBowlingForm || []);

    const slice1 = form1.slice(-selectedRecentN);
    const slice2 = form2.slice(-selectedRecentN);

    const maxLen = Math.max(slice1.length, slice2.length);
    if (maxLen === 0) return null;

    const labels = Array.from({ length: maxLen }, (_, i) => isBattingFocus ? `Inn ${i + 1}` : `Spell ${i + 1}`);

    const data1 = slice1.map(m => isBattingFocus ? m.runs : m.wickets);
    const data2 = slice2.map(m => isBattingFocus ? m.runs : m.wickets);

    return {
      labels,
      datasets: [
        {
          label: `${comparison.player1Name} (${isBattingFocus ? 'Runs' : 'Wickets'})`,
          data: data1,
          borderColor: P1_COLOR.border,
          backgroundColor: P1_COLOR.lineBg,
          tension: 0.3,
          fill: true,
          pointRadius: 4,
        },
        {
          label: `${comparison.player2Name} (${isBattingFocus ? 'Runs' : 'Wickets'})`,
          data: data2,
          borderColor: P2_COLOR.border,
          backgroundColor: P2_COLOR.lineBg,
          tension: 0.3,
          fill: true,
          pointRadius: 4,
        },
      ],
    };
  })();

  return (
    <DashboardLayout pageTitle="Compare Players">
    <div className="analytics-page compare-page">
      {/* Page Title & Intro */}
      <div className="analytics-header">
        <div className="analytics-header-text">
          <h1 className="analytics-title">
            <span className="analytics-title-icon">⚖️</span>
            Cricket Player Comparison
          </h1>
          <p className="analytics-subtitle">
            Side-by-side head-to-head performance comparison with verified cricket metrics, recent-form trajectories, contextual match analysis, and AI predictive estimates.
          </p>
        </div>
      </div>

      {/* ─────────────────────────────────────────────────────────────
          SECTION 1: Player Selection and Format
         ───────────────────────────────────────────────────────────── */}
      <section className="compare-section-block" id="section-1-player-selection">
        <div className="section-header-badge">
          <span className="section-number">1</span>
          <h2 className="section-title">Player Selection & Match Format</h2>
        </div>
        <p className="section-subtitle">
          Select two distinct international cricketers and choose the format to evaluate equivalent match conditions.
        </p>

        {/* Player Autocomplete Selectors */}
        <div className="compare-selectors-grid">
          {/* Player 1 selector */}
          <div className="compare-selector-col" ref={ref1}>
            <label className="compare-selector-label" htmlFor="compare-player-1-input">
              <span>Player 1</span>
            </label>
            <div className="analytics-search-box" style={{ width: '100%' }}>
              
              <input
                id="compare-player-1-input"
                type="text"
                className="analytics-search-input"
                placeholder="Search first player (e.g. V Kohli)…"
                value={input1}
                onChange={e => { setInput1(e.target.value); setShowDropdown1(true); }}
                onFocus={() => setShowDropdown1(true)}
                autoComplete="off"
              />
              {input1 && (
                <button
                  type="button"
                  className="btn btn-secondary"
                  style={{ padding: '4px 8px', fontSize: '0.75rem' }}
                  onClick={() => { setInput1(''); setPlayer1(''); }}
                >
                  ✕
                </button>
              )}
            </div>
            {showDropdown1 && suggestions1.length > 0 && (
              <ul className="analytics-suggestions" role="listbox">
                {suggestions1.map((p, i) => (
                  <li
                    key={i}
                    className="analytics-suggestion-item"
                    role="option"
                    onClick={() => selectPlayer1(p)}
                  >
                    {p}
                  </li>
                ))}
              </ul>
            )}
          </div>

          {/* Swap Button */}
          <div style={{ textAlign: 'center' }}>
            <button
              type="button"
              className="compare-vs-badge"
              title="Swap player order"
              onClick={swapPlayers}
              style={{ cursor: 'pointer', border: 'none' }}
            >
              ⇄
            </button>
          </div>

          {/* Player 2 selector */}
          <div className="compare-selector-col" ref={ref2}>
            <label className="compare-selector-label" htmlFor="compare-player-2-input">
              <span>Player 2</span>
            </label>
            <div className="analytics-search-box" style={{ width: '100%' }}>
              
              <input
                id="compare-player-2-input"
                type="text"
                className="analytics-search-input"
                placeholder="Search second player (e.g. BM Duckett)…"
                value={input2}
                onChange={e => { setInput2(e.target.value); setShowDropdown2(true); }}
                onFocus={() => setShowDropdown2(true)}
                autoComplete="off"
              />
              {input2 && (
                <button
                  type="button"
                  className="btn btn-secondary"
                  style={{ padding: '4px 8px', fontSize: '0.75rem' }}
                  onClick={() => { setInput2(''); setPlayer2(''); }}
                >
                  ✕
                </button>
              )}
            </div>
            {showDropdown2 && suggestions2.length > 0 && (
              <ul className="analytics-suggestions" role="listbox">
                {suggestions2.map((p, i) => (
                  <li
                    key={i}
                    className="analytics-suggestion-item"
                    role="option"
                    onClick={() => selectPlayer2(p)}
                  >
                    {p}
                  </li>
                ))}
              </ul>
            )}
          </div>
        </div>

        {/* Format Selector and Action Button */}
        <div className="compare-actions-bar">
          <div style={{ display: 'flex', alignItems: 'center', gap: 'var(--space-3)', flexWrap: 'wrap' }}>
            <span style={{ fontSize: '0.82rem', color: '#94A3B8', fontWeight: 600 }}>Format:</span>
            <div className="format-tabs" role="tablist">
              {FORMATS.map(f => (
                <button
                  key={f}
                  role="tab"
                  aria-selected={selectedFormat === f}
                  className={`format-tab${selectedFormat === f ? ' format-tab--active' : ''}`}
                  onClick={() => handleFormatChange(f)}
                >
                  {f}
                </button>
              ))}
            </div>
          </div>

          <div style={{ display: 'flex', alignItems: 'center', gap: 'var(--space-3)', flexWrap: 'wrap' }}>
            <button
              id="compare-action-btn"
              className="btn btn-primary compare-action-btn"
              onClick={() => handleCompare()}
              disabled={loading || !input1.trim() || !input2.trim()}
            >
              {loading ? 'Comparing…' : '⚖️ Compare Players'}
            </button>

            {comparison && (
              <button
                id="export-comparison-excel-btn"
                type="button"
                className="btn-excel-export"
                onClick={handleExportExcel}
                disabled={exporting}
                title="Download full player comparison report as genuine Excel (.xlsx)"
              >
                {exporting ? (
                  <>
                    <span className="spinner spinner--sm spinner--light" />
                    <span>Preparing Excel…</span>
                  </>
                ) : (
                  <>
                    <svg className="excel-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                      <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z" />
                      <polyline points="14 2 14 8 20 8" />
                      <line x1="16" y1="13" x2="8" y2="13" />
                      <line x1="16" y1="17" x2="8" y2="17" />
                      <polyline points="10 9 9 9 8 9" />
                    </svg>
                    <span>Export to Excel</span>
                  </>
                )}
              </button>
            )}
          </div>
        </div>

        {exportSuccessMsg && (
          <div style={{ marginTop: 'var(--space-2)', fontSize: '0.8rem', color: '#10B981', fontWeight: 500, textAlign: 'right' }}>
            ✓ {exportSuccessMsg}
          </div>
        )}
        {exportError && (
          <div style={{ marginTop: 'var(--space-2)', fontSize: '0.8rem', color: '#EF4444', fontWeight: 500, textAlign: 'right' }}>
            ⚠️ {exportError}
          </div>
        )}

        {/* Error message */}
        {error && (
          <div className="analytics-error" style={{ marginTop: 'var(--space-3)' }}>
            <span>⚠️</span> {error}
          </div>
        )}
      </section>

      {/* Comparison Sections */}
      {comparison && (
        <>
          {/* ─────────────────────────────────────────────────────────────
              SECTION 2: Overall Statistical Comparison
             ───────────────────────────────────────────────────────────── */}
          <section className="compare-section-block" id="section-2-overall-comparison">
            <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', flexWrap: 'wrap', gap: '12px' }}>
              <div className="section-header-badge">
                <span className="section-number">2</span>
                <h2 className="section-title">Overall Statistical Comparison ({comparison.format})</h2>
              </div>
              <button
                id="export-comparison-excel-btn-sec2"
                type="button"
                className="btn-excel-export"
                onClick={handleExportExcel}
                disabled={exporting}
                title="Download full player comparison report as genuine Excel (.xlsx)"
              >
                {exporting ? (
                  <>
                    <span className="spinner spinner--sm spinner--light" />
                    <span>Preparing Excel…</span>
                  </>
                ) : (
                  <>
                    <svg className="excel-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                      <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z" />
                      <polyline points="14 2 14 8 20 8" />
                      <line x1="16" y1="13" x2="8" y2="13" />
                      <line x1="16" y1="17" x2="8" y2="17" />
                      <polyline points="10 9 9 9 8 9" />
                    </svg>
                    <span>Export to Excel</span>
                  </>
                )}
              </button>
            </div>
            <p className="section-subtitle">
              Format-specific career record comparison with verified cricket metric distinctions, valid denominators, and role-appropriate advantage criteria.
            </p>

            {/* Side-by-side player header cards */}
            <div className="compare-players-header">
              {/* Player 1 Card */}
              <div className="compare-player-card compare-player-card--p1">
                <div className="compare-player-avatar compare-player-avatar--p1">
                  {comparison.player1Name.charAt(0).toUpperCase()}
                </div>
                <div className="compare-player-info">
                  <h2 className="compare-player-name">{comparison.player1Name}</h2>
                  <div style={{ display: 'flex', alignItems: 'center', gap: '8px', flexWrap: 'wrap' }}>
                    {roleBadge(p1?.role)}
                    <span className="compare-player-meta">
                      {p1?.totalBattingInnings ?? 0} bat inn · {p1?.totalBowlingInnings ?? 0} bowl inn
                    </span>
                  </div>
                </div>
              </div>

              {/* Player 2 Card */}
              <div className="compare-player-card compare-player-card--p2">
                <div className="compare-player-avatar compare-player-avatar--p2">
                  {comparison.player2Name.charAt(0).toUpperCase()}
                </div>
                <div className="compare-player-info">
                  <h2 className="compare-player-name">{comparison.player2Name}</h2>
                  <div style={{ display: 'flex', alignItems: 'center', gap: '8px', flexWrap: 'wrap' }}>
                    {roleBadge(p2?.role)}
                    <span className="compare-player-meta">
                      {p2?.totalBattingInnings ?? 0} bat inn · {p2?.totalBowlingInnings ?? 0} bowl inn
                    </span>
                  </div>
                </div>
              </div>
            </div>

            {/* Scope Explanation Banner */}
            <div className={`compare-scope-banner compare-scope-banner--${
              scope === 'BOTH' ? 'both' : (scope === 'BATTING_ONLY' ? 'batting' : (scope === 'BOWLING_ONLY' ? 'bowling' : 'mixed'))
            }`}>
              <span className="compare-scope-icon" style={{ display: 'flex', alignItems: 'center', color: 'var(--color-accent)' }}><svg viewBox="0 0 24 24" width="22" height="22" fill="none" stroke="currentColor" strokeWidth="2"><path d="M16 3h5v5M4 20L20.2 3.8M21 16v5h-5M15 15l5.1 5.1M4 4l5 5" /></svg></span>
              <div>
                <strong>Comparison Scope: {scope.replace('_', ' ')} ({comparison.format})</strong>
                <p style={{ margin: '4px 0 0', opacity: 0.9 }}>{comparison.scopeExplanation}</p>
              </div>
            </div>

            {/* Key Metric Highlight Cards */}
            <div className="compare-metric-cards-grid">
              {/* Card 1: Batting Volume & Power */}
              {showBatting && (
                <div className="compare-kpi-card">
                  <div className="compare-kpi-title">
                    <span>Scoring Power</span>
                    <span style={{ fontSize: '0.7rem', color: '#818CF8' }}>Total Runs</span>
                  </div>
                  <div className="compare-kpi-comparison">
                    <div className="compare-kpi-col compare-kpi-col--p1">
                      <span className="compare-kpi-player">{comparison.player1Name}</span>
                      <span className="compare-kpi-num">{batComp?.totalRuns1 ?? 0}</span>
                      <span className="compare-kpi-sub">{batComp?.runsPerInnings1 ?? 0} RPI</span>
                    </div>
                    <span style={{ color: '#94A3B8', fontWeight: 600 }}>vs</span>
                    <div className="compare-kpi-col compare-kpi-col--p2" style={{ textAlign: 'right' }}>
                      <span className="compare-kpi-player">{comparison.player2Name}</span>
                      <span className="compare-kpi-num">{batComp?.totalRuns2 ?? 0}</span>
                      <span className="compare-kpi-sub">{batComp?.runsPerInnings2 ?? 0} RPI</span>
                    </div>
                  </div>
                </div>
              )}

              {/* Card 2: Strike Rate & Efficiency */}
              {showBatting && (
                <div className="compare-kpi-card">
                  <div className="compare-kpi-title">
                    <span>⚡ Batting Strike Rate</span>
                    <span style={{ fontSize: '0.7rem', color: '#818CF8' }}>Scoring Pace</span>
                  </div>
                  <div className="compare-kpi-comparison">
                    <div className="compare-kpi-col compare-kpi-col--p1">
                      <span className="compare-kpi-player">{comparison.player1Name}</span>
                      <span className="compare-kpi-num">{batComp?.strikeRate1 != null ? batComp.strikeRate1 : '—'}</span>
                      <span className="compare-kpi-sub">{batComp?.boundaryPercentage1 != null ? `${batComp.boundaryPercentage1}% bnd` : ''}</span>
                    </div>
                    <span style={{ color: '#94A3B8', fontWeight: 600 }}>vs</span>
                    <div className="compare-kpi-col compare-kpi-col--p2" style={{ textAlign: 'right' }}>
                      <span className="compare-kpi-player">{comparison.player2Name}</span>
                      <span className="compare-kpi-num">{batComp?.strikeRate2 != null ? batComp.strikeRate2 : '—'}</span>
                      <span className="compare-kpi-sub">{batComp?.boundaryPercentage2 != null ? `${batComp.boundaryPercentage2}% bnd` : ''}</span>
                    </div>
                  </div>
                </div>
              )}

              {/* Card 3: Consistency (CV%) */}
              {showBatting && (
                <div className="compare-kpi-card">
                  <div className="compare-kpi-title">
                    <span>Scoring Consistency</span>
                    <span style={{ fontSize: '0.7rem', color: '#10B981' }}>Lower CV% = Tighter</span>
                  </div>
                  <div className="compare-kpi-comparison">
                    <div className="compare-kpi-col compare-kpi-col--p1">
                      <span className="compare-kpi-player">{comparison.player1Name}</span>
                      <span className="compare-kpi-num">{batComp?.coefficientOfVariation1 != null ? `${batComp.coefficientOfVariation1}%` : 'N/A'}</span>
                      <span className="compare-kpi-sub">σ = {batComp?.stdDevRuns1 ?? '—'}</span>
                    </div>
                    <span style={{ color: '#94A3B8', fontWeight: 600 }}>vs</span>
                    <div className="compare-kpi-col compare-kpi-col--p2" style={{ textAlign: 'right' }}>
                      <span className="compare-kpi-player">{comparison.player2Name}</span>
                      <span className="compare-kpi-num">{batComp?.coefficientOfVariation2 != null ? `${batComp.coefficientOfVariation2}%` : 'N/A'}</span>
                      <span className="compare-kpi-sub">σ = {batComp?.stdDevRuns2 ?? '—'}</span>
                    </div>
                  </div>
                </div>
              )}

              {/* Card 4: Bowling Wickets & Hauls */}
              {showBowling && (
                <div className="compare-kpi-card">
                  <div className="compare-kpi-title">
                    <span>Wickets Output</span>
                    <span style={{ fontSize: '0.7rem', color: '#EC4899' }}>Volume</span>
                  </div>
                  <div className="compare-kpi-comparison">
                    <div className="compare-kpi-col compare-kpi-col--p1">
                      <span className="compare-kpi-player">{comparison.player1Name}</span>
                      <span className="compare-kpi-num">{bowlComp?.totalWickets1 ?? 0}</span>
                      <span className="compare-kpi-sub">{bowlComp?.fourWickets1 + bowlComp?.fiveWickets1} hauls</span>
                    </div>
                    <span style={{ color: '#94A3B8', fontWeight: 600 }}>vs</span>
                    <div className="compare-kpi-col compare-kpi-col--p2" style={{ textAlign: 'right' }}>
                      <span className="compare-kpi-player">{comparison.player2Name}</span>
                      <span className="compare-kpi-num">{bowlComp?.totalWickets2 ?? 0}</span>
                      <span className="compare-kpi-sub">{bowlComp?.fourWickets2 + bowlComp?.fiveWickets2} hauls</span>
                    </div>
                  </div>
                </div>
              )}

              {/* Card 5: Bowling Economy Rate */}
              {showBowling && (
                <div className="compare-kpi-card">
                  <div className="compare-kpi-title">
                    <span>Economy Rate</span>
                    <span style={{ fontSize: '0.7rem', color: '#10B981' }}>Lower is Better</span>
                  </div>
                  <div className="compare-kpi-comparison">
                    <div className="compare-kpi-col compare-kpi-col--p1">
                      <span className="compare-kpi-player">{comparison.player1Name}</span>
                      <span className="compare-kpi-num">{bowlComp?.economyRate1 != null ? bowlComp.economyRate1 : '—'}</span>
                      <span className="compare-kpi-sub">{bowlComp?.totalOvers1 ?? 0} ov</span>
                    </div>
                    <span style={{ color: '#94A3B8', fontWeight: 600 }}>vs</span>
                    <div className="compare-kpi-col compare-kpi-col--p2" style={{ textAlign: 'right' }}>
                      <span className="compare-kpi-player">{comparison.player2Name}</span>
                      <span className="compare-kpi-num">{bowlComp?.economyRate2 != null ? bowlComp.economyRate2 : '—'}</span>
                      <span className="compare-kpi-sub">{bowlComp?.totalOvers2 ?? 0} ov</span>
                    </div>
                  </div>
                </div>
              )}
            </div>

            {/* Batting Comparison Table & Chart */}
            {showBatting && (
              <div style={{ marginTop: 'var(--space-4)' }}>
                <h3 className="analytics-section-title" style={{ fontSize: '0.95rem' }}>
                  
                  Batting Head-to-Head Statistics
                </h3>

                <div className="compare-table-wrapper">
                  <table className="compare-table">
                    <thead>
                      <tr>
                        <th style={{ width: '38%', color: '#818CF8' }}>{comparison.player1Name}</th>
                        <th style={{ width: '24%' }}>Metric</th>
                        <th style={{ width: '38%', color: '#F472B6' }}>{comparison.player2Name}</th>
                      </tr>
                    </thead>
                    <tbody>
                      {renderMetricRow('Innings Batted', batComp.innings1, batComp.innings2, 'innings')}
                      {renderMetricRow('Total Runs Scored', batComp.totalRuns1, batComp.totalRuns2, 'runs')}
                      {renderMetricRow('Runs Per Innings (total runs / innings)', batComp.runsPerInnings1, batComp.runsPerInnings2, 'runsPerInnings')}
                      {renderMetricRow('Official Batting Average (runs / dismissals)', batComp.battingAverage1, batComp.battingAverage2, 'battingAverage')}
                      {renderMetricRow('Not Out Innings', batComp.notOuts1, batComp.notOuts2, 'notOuts')}
                      {renderMetricRow('Highest Score', batComp.highestScore1 != null ? `${batComp.highestScore1}${batComp.highestScoreNotOut1 ? '*' : ''}` : null, batComp.highestScore2 != null ? `${batComp.highestScore2}${batComp.highestScoreNotOut2 ? '*' : ''}` : null, 'highestScore')}
                      {renderMetricRow('Batting Strike Rate', batComp.strikeRate1, batComp.strikeRate2, 'battingStrikeRate')}
                      {renderMetricRow('Boundary Run % (runs in 4s & 6s)', batComp.boundaryPercentage1, batComp.boundaryPercentage2, 'boundaryPercentage', '%')}
                      {renderMetricRow('Boundary Ball % (balls hit to boundary)', batComp.boundaryBallPercentage1, batComp.boundaryBallPercentage2, 'boundaryBallPercentage', '%')}
                      {renderMetricRow('Fours (4s)', batComp.fours1, batComp.fours2, 'fours')}
                      {renderMetricRow('Sixes (6s)', batComp.sixes1, batComp.sixes2, 'sixes')}
                      {renderMetricRow('Half-Centuries (50s)', batComp.fifties1, batComp.fifties2, 'fifties')}
                      {renderMetricRow('Centuries (100s)', batComp.hundreds1, batComp.hundreds2, 'hundreds')}
                      {renderMetricRow('Sample Standard Deviation (σ)', batComp.stdDevRuns1, batComp.stdDevRuns2, 'stdDevRuns')}
                      {renderMetricRow('Consistency (CV% = σ / mean * 100)', batComp.coefficientOfVariation1, batComp.coefficientOfVariation2, 'consistency', '%')}
                      {renderMetricRow('Dot-Ball Percentage (ball-level unrecorded)', null, null, 'dotBallPercentage')}
                    </tbody>
                  </table>
                </div>

                {battingChartData && (
                  <div style={{ marginTop: 'var(--space-4)', background: 'rgba(255, 255, 255, 0.02)', padding: 'var(--space-4)', borderRadius: 'var(--radius-lg)', border: '1px solid var(--color-border)' }}>
                    <div style={{ height: 260 }}>
                      <Bar
                        data={battingChartData}
                        options={{
                          responsive: true,
                          maintainAspectRatio: false,
                          plugins: {
                            legend: { labels: { color: '#334155', font: { size: 11 } } },
                            title: { display: true, text: 'Batting Metrics Overview', color: '#0F172A' },
                          },
                          scales: {
                            x: { ticks: { color: '#94A3B8' }, grid: { color: 'rgba(15, 23, 42, 0.06)' } },
                            y: { ticks: { color: '#94A3B8' }, grid: { color: 'rgba(15, 23, 42, 0.08)' }, beginAtZero: true },
                          },
                        }}
                      />
                    </div>
                  </div>
                )}
              </div>
            )}

            {/* Bowling Comparison Table & Chart */}
            {showBowling && (
              <div style={{ marginTop: 'var(--space-5)' }}>
                <h3 className="analytics-section-title" style={{ fontSize: '0.95rem' }}>
                  
                  Bowling Head-to-Head Statistics
                </h3>

                <div className="compare-table-wrapper">
                  <table className="compare-table">
                    <thead>
                      <tr>
                        <th style={{ width: '38%', color: '#818CF8' }}>{comparison.player1Name}</th>
                        <th style={{ width: '24%' }}>Metric</th>
                        <th style={{ width: '38%', color: '#F472B6' }}>{comparison.player2Name}</th>
                      </tr>
                    </thead>
                    <tbody>
                      {renderMetricRow('Innings Bowled', bowlComp.innings1, bowlComp.innings2, 'bowlingInnings')}
                      {renderMetricRow('Overs Bowled', bowlComp.totalOvers1, bowlComp.totalOvers2, 'overs')}
                      {renderMetricRow('Valid Balls Bowled (4.2 ov = 26b)', bowlComp.validBallsBowled1, bowlComp.validBallsBowled2, 'validBalls')}
                      {renderMetricRow('Runs Conceded', bowlComp.runsConceded1, bowlComp.runsConceded2, 'runsConceded')}
                      {renderMetricRow('Total Wickets', bowlComp.totalWickets1, bowlComp.totalWickets2, 'wickets')}
                      {renderMetricRow('Wickets / Innings', bowlComp.wicketsPerInnings1, bowlComp.wicketsPerInnings2, 'wicketsPerInnings')}
                      {renderMetricRow('Economy Rate (runs / dec. over)', bowlComp.economyRate1, bowlComp.economyRate2, 'economyRate')}
                      {renderMetricRow('Bowling Average (runs / wicket)', bowlComp.bowlingAverage1, bowlComp.bowlingAverage2, 'bowlingAverage')}
                      {renderMetricRow('Bowling Strike Rate (valid balls / wicket)', bowlComp.bowlingStrikeRate1, bowlComp.bowlingStrikeRate2, 'bowlingStrikeRate')}
                      {renderMetricRow('4-Wicket Hauls (4w)', bowlComp.fourWickets1, bowlComp.fourWickets2, 'fourWickets')}
                      {renderMetricRow('5-Wicket Hauls (5w)', bowlComp.fiveWickets1, bowlComp.fiveWickets2, 'fiveWickets')}
                      {renderMetricRow('Maidens Bowled (unrecorded in dataset)', null, null, 'maidens')}
                      {renderMetricRow('Sample Standard Deviation Wickets (σ)', bowlComp.stdDevWickets1, bowlComp.stdDevWickets2, 'stdDevWickets')}
                    </tbody>
                  </table>
                </div>

                {bowlingChartData && (
                  <div style={{ marginTop: 'var(--space-4)', background: 'rgba(255, 255, 255, 0.02)', padding: 'var(--space-4)', borderRadius: 'var(--radius-lg)', border: '1px solid var(--color-border)' }}>
                    <div style={{ height: 260 }}>
                      <Bar
                        data={bowlingChartData}
                        options={{
                          responsive: true,
                          maintainAspectRatio: false,
                          plugins: {
                            legend: { labels: { color: '#334155', font: { size: 11 } } },
                            title: { display: true, text: 'Bowling Metrics Overview (Lower Economy/Average is Better)', color: '#0F172A' },
                          },
                          scales: {
                            x: { ticks: { color: '#94A3B8' }, grid: { color: 'rgba(15, 23, 42, 0.06)' } },
                            y: { ticks: { color: '#94A3B8' }, grid: { color: 'rgba(15, 23, 42, 0.08)' }, beginAtZero: true },
                          },
                        }}
                      />
                    </div>
                  </div>
                )}
              </div>
            )}

            {/* Mixed disciplines notice */}
            {scope === 'MIXED_DISCIPLINES' && (
              <div style={{ background: 'rgba(255,255,255,0.02)', border: '1px dashed var(--color-border)', borderRadius: 'var(--radius-lg)', padding: 'var(--space-4)', marginTop: 'var(--space-4)' }}>
                <h4 style={{ margin: '0 0 6px', color: 'var(--color-text-primary)', fontSize: '0.9rem' }}>⚡ Cross-Discipline Notice</h4>
                <p style={{ margin: 0, color: '#94A3B8', fontSize: '0.85rem', lineHeight: 1.6 }}>
                  Because <strong>{comparison.player1Name}</strong> is primarily a {p1?.role?.toLowerCase()} and <strong>{comparison.player2Name}</strong> is primarily a {p2?.role?.toLowerCase()},
                  direct cross-discipline comparisons (e.g. runs vs wickets) are avoided. Each facet is evaluated on its independent cricket merit.
                </p>
              </div>
            )}
          </section>

          {/* ─────────────────────────────────────────────────────────────
              SECTION 3: Recent Form and Consistency Analysis
             ───────────────────────────────────────────────────────────── */}
          <section className="compare-section-block" id="section-3-recent-form">
            <div className="section-header-badge">
              <span className="section-number">3</span>
              <h2 className="section-title">Recent Form & Consistency Analysis</h2>
            </div>
            <p className="section-subtitle">
              {isBowlingRecentFocus
                ? 'Chronological recent bowling performance across participating eligible matches/spells (missing matches are not zero-filled). Select window to analyze momentum.'
                : 'Chronological recent performance across participating eligible innings (missing matches are not zero-filled). Select window to analyze momentum.'}
            </p>

            {/* Window selector & trend badges */}
            <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', flexWrap: 'wrap', gap: '12px' }}>
              <div className="window-tabs" title="Recent Innings Window">
                <span style={{ fontSize: '0.78rem', padding: '4px 8px', color: '#94A3B8', fontWeight: 600 }}>
                  {isBowlingRecentFocus ? 'Spell Window:' : 'Innings Window:'}
                </span>
                {RECENT_WINDOWS.map(n => (
                  <button
                    key={n}
                    type="button"
                    className={`window-tab${selectedRecentN === n ? ' window-tab--active' : ''}`}
                    onClick={() => handleRecentNChange(n)}
                  >
                    {isBowlingRecentFocus ? `Last ${n} Eligible Spells` : `Last ${n} Eligible Innings`}
                  </button>
                ))}
              </div>

              {isBowlingRecentFocus ? (
                <div style={{ display: 'flex', gap: '12px', alignItems: 'center', flexWrap: 'wrap' }}>
                  <div style={{ display: 'flex', alignItems: 'center', gap: '6px' }}>
                    <span style={{ fontSize: '0.78rem', color: '#818CF8', fontWeight: 600 }}>{comparison.player1Name}:</span>
                    {trendBadge(recentSummary?.wicketTrendDirection1)}
                  </div>
                  <div style={{ display: 'flex', alignItems: 'center', gap: '6px' }}>
                    <span style={{ fontSize: '0.78rem', color: '#F472B6', fontWeight: 600 }}>{comparison.player2Name}:</span>
                    {trendBadge(recentSummary?.wicketTrendDirection2)}
                  </div>
                </div>
              ) : (
                <div style={{ display: 'flex', gap: '12px', alignItems: 'center', flexWrap: 'wrap' }}>
                  <div style={{ display: 'flex', alignItems: 'center', gap: '6px' }}>
                    <span style={{ fontSize: '0.78rem', color: '#818CF8', fontWeight: 600 }}>{comparison.player1Name}:</span>
                    {trendBadge(recentSummary?.runTrendDirection1 || recentSummary?.wicketTrendDirection1)}
                  </div>
                  <div style={{ display: 'flex', alignItems: 'center', gap: '6px' }}>
                    <span style={{ fontSize: '0.78rem', color: '#F472B6', fontWeight: 600 }}>{comparison.player2Name}:</span>
                    {trendBadge(recentSummary?.runTrendDirection2 || recentSummary?.wicketTrendDirection2)}
                  </div>
                </div>
              )}
            </div>

            {/* Recent Form Key Summary Cards */}
            {isBowlingRecentFocus ? (
              <div className="recent-summary-grid">
                {/* Bowling Card 1: Wickets per Innings */}
                <div className="recent-stat-card">
                  <span className="recent-stat-label">Recent Wickets / Inn</span>
                  <div className="recent-stat-row">
                    <span className="recent-stat-val-p1">{recentSummary?.meanWickets1 != null ? recentSummary.meanWickets1.toFixed(2) : '—'}</span>
                    <span style={{ color: '#94A3B8', fontSize: '0.75rem' }}>vs</span>
                    <span className="recent-stat-val-p2">{recentSummary?.meanWickets2 != null ? recentSummary.meanWickets2.toFixed(2) : '—'}</span>
                  </div>
                  <span style={{ fontSize: '0.72rem', color: '#94A3B8' }}>
                    {recentSummary?.totalWickets1 ?? 0} total wkts vs {recentSummary?.totalWickets2 ?? 0} total wkts
                  </span>
                </div>

                {/* Bowling Card 2: Economy Rate */}
                <div className="recent-stat-card">
                  <span className="recent-stat-label">Recent Economy Rate</span>
                  <div className="recent-stat-row">
                    <span className="recent-stat-val-p1">{recentSummary?.recentEconomyRate1 != null ? recentSummary.recentEconomyRate1.toFixed(2) : '—'}</span>
                    <span style={{ color: '#94A3B8', fontSize: '0.75rem' }}>vs</span>
                    <span className="recent-stat-val-p2">{recentSummary?.recentEconomyRate2 != null ? recentSummary.recentEconomyRate2.toFixed(2) : '—'}</span>
                  </div>
                  <span style={{ fontSize: '0.72rem', color: '#94A3B8' }}>
                    {recentSummary?.totalRunsConceded1 ?? 0}r / {recentSummary?.totalOvers1 ?? 0} ov vs {recentSummary?.totalRunsConceded2 ?? 0}r / {recentSummary?.totalOvers2 ?? 0} ov
                  </span>
                </div>

                {/* Bowling Card 3: Bowling Average */}
                <div className="recent-stat-card">
                  <span className="recent-stat-label">Recent Bowling Average</span>
                  <div className="recent-stat-row">
                    <span className="recent-stat-val-p1">{recentSummary?.recentBowlingAverage1 != null ? recentSummary.recentBowlingAverage1.toFixed(2) : 'N/A'}</span>
                    <span style={{ color: '#94A3B8', fontSize: '0.75rem' }}>vs</span>
                    <span className="recent-stat-val-p2">{recentSummary?.recentBowlingAverage2 != null ? recentSummary.recentBowlingAverage2.toFixed(2) : 'N/A'}</span>
                  </div>
                  <span style={{ fontSize: '0.72rem', color: '#94A3B8' }}>Runs conceded / wicket (N/A if 0 wkts)</span>
                </div>

                {/* Bowling Card 4: Bowling Strike Rate */}
                <div className="recent-stat-card">
                  <span className="recent-stat-label">Recent Bowling Strike Rate</span>
                  <div className="recent-stat-row">
                    <span className="recent-stat-val-p1">{recentSummary?.recentBowlingStrikeRate1 != null ? recentSummary.recentBowlingStrikeRate1.toFixed(1) : 'N/A'}</span>
                    <span style={{ color: '#94A3B8', fontSize: '0.75rem' }}>vs</span>
                    <span className="recent-stat-val-p2">{recentSummary?.recentBowlingStrikeRate2 != null ? recentSummary.recentBowlingStrikeRate2.toFixed(1) : 'N/A'}</span>
                  </div>
                  <span style={{ fontSize: '0.72rem', color: '#94A3B8' }}>
                    {recentSummary?.totalValidBalls1 ?? 0}b / {recentSummary?.totalWickets1 ?? 0}w vs {recentSummary?.totalValidBalls2 ?? 0}b / {recentSummary?.totalWickets2 ?? 0}w
                  </span>
                </div>

                {/* Bowling Card 5: Peak Wickets & Hauls */}
                <div className="recent-stat-card">
                  <span className="recent-stat-label">Peak Wickets & Hauls</span>
                  <div className="recent-stat-row">
                    <span className="recent-stat-val-p1">{recentSummary?.highestWickets1 != null ? `Max ${recentSummary.highestWickets1}w` : '—'}</span>
                    <span style={{ color: '#94A3B8', fontSize: '0.75rem' }}>vs</span>
                    <span className="recent-stat-val-p2">{recentSummary?.highestWickets2 != null ? `Max ${recentSummary.highestWickets2}w` : '—'}</span>
                  </div>
                  <span style={{ fontSize: '0.72rem', color: '#94A3B8' }}>
                    4W: {recentSummary?.fourWickets1 ?? 0}, 5W: {recentSummary?.fiveWickets1 ?? 0} vs 4W: {recentSummary?.fourWickets2 ?? 0}, 5W: {recentSummary?.fiveWickets2 ?? 0}
                  </span>
                </div>

                {/* Bowling Card 6: Consistency (std dev) & Sample */}
                <div className="recent-stat-card">
                  <span className="recent-stat-label">Bowling Consistency (σ)</span>
                  <div className="recent-stat-row">
                    <span className="recent-stat-val-p1">σ = {recentSummary?.stdDevWickets1 ?? '—'}</span>
                    <span style={{ color: '#94A3B8', fontSize: '0.75rem' }}>vs</span>
                    <span className="recent-stat-val-p2">σ = {recentSummary?.stdDevWickets2 ?? '—'}</span>
                  </div>
                  <span style={{ fontSize: '0.72rem', color: '#94A3B8' }}>
                    {recentSummary?.bowlingInnings1 ?? 0} spells vs {recentSummary?.bowlingInnings2 ?? 0} spells
                  </span>
                </div>
              </div>
            ) : (
              /* Batting cards - unchanged */
              <div className="recent-summary-grid">
                {/* Card 1: Recent Average Runs per Innings */}
                <div className="recent-stat-card">
                  <span className="recent-stat-label">Recent Average (Runs / Inn)</span>
                  <div className="recent-stat-row">
                    <span className="recent-stat-val-p1">{recentSummary?.meanRuns1 != null ? recentSummary.meanRuns1 : '—'}</span>
                    <span style={{ color: '#94A3B8', fontSize: '0.75rem' }}>vs</span>
                    <span className="recent-stat-val-p2">{recentSummary?.meanRuns2 != null ? recentSummary.meanRuns2 : '—'}</span>
                  </div>
                  {recentSummary?.recentAverageRunsAdvantage && recentSummary.recentAverageRunsAdvantage !== 'insufficient_data' && (
                    <span style={{ fontSize: '0.72rem', color: recentSummary.recentAverageRunsAdvantage === 'player1' ? '#818CF8' : (recentSummary.recentAverageRunsAdvantage === 'player2' ? '#F472B6' : '#94A3B8'), fontWeight: 600 }}>
                      {recentSummary.recentAverageRunsAdvantage === 'player1' ? `+${recentSummary.recentAverageRunsDifference} advantage to ${comparison.player1Name}` : (recentSummary.recentAverageRunsAdvantage === 'player2' ? `+${recentSummary.recentAverageRunsDifference} advantage to ${comparison.player2Name}` : 'Evenly matched averages')}
                    </span>
                  )}
                </div>

                {/* Card 2: Highest Score in Window */}
                <div className="recent-stat-card">
                  <span className="recent-stat-label">Peak Score (Window Max)</span>
                  <div className="recent-stat-row">
                    <span className="recent-stat-val-p1">{recentSummary?.highestRuns1 != null ? `${recentSummary.highestRuns1} runs` : '—'}</span>
                    <span style={{ color: '#94A3B8', fontSize: '0.75rem' }}>vs</span>
                    <span className="recent-stat-val-p2">{recentSummary?.highestRuns2 != null ? `${recentSummary.highestRuns2} runs` : '—'}</span>
                  </div>
                  <span style={{ fontSize: '0.72rem', color: '#94A3B8' }}>Highest individual score in last {selectedRecentN} innings</span>
                </div>

                {/* Card 3: Lowest Score in Window */}
                <div className="recent-stat-card">
                  <span className="recent-stat-label">Floor Score (Window Min)</span>
                  <div className="recent-stat-row">
                    <span className="recent-stat-val-p1">{recentSummary?.lowestRuns1 != null ? `${recentSummary.lowestRuns1} runs` : '—'}</span>
                    <span style={{ color: '#94A3B8', fontSize: '0.75rem' }}>vs</span>
                    <span className="recent-stat-val-p2">{recentSummary?.lowestRuns2 != null ? `${recentSummary.lowestRuns2} runs` : '—'}</span>
                  </div>
                  <span style={{ fontSize: '0.72rem', color: '#94A3B8' }}>Lowest individual score in last {selectedRecentN} innings</span>
                </div>

                {/* Card 4: Scoring Variability (std dev) */}
                <div className="recent-stat-card">
                  <span className="recent-stat-label">Performance Variability (σ)</span>
                  <div className="recent-stat-row">
                    <span className="recent-stat-val-p1">σ = {recentSummary?.stdDevRuns1 ?? '—'}</span>
                    <span style={{ color: '#94A3B8', fontSize: '0.75rem' }}>vs</span>
                    <span className="recent-stat-val-p2">σ = {recentSummary?.stdDevRuns2 ?? '—'}</span>
                  </div>
                  <span style={{ fontSize: '0.72rem', color: '#94A3B8' }}>Sample std dev of runs across window</span>
                </div>

                {/* Card 5: Eligible Innings Count */}
                <div className="recent-stat-card">
                  <span className="recent-stat-label">Sample Basis (Innings Count)</span>
                  <div className="recent-stat-row">
                    <span className="recent-stat-val-p1">{recentSummary?.battingInnings1 ?? 0} inn</span>
                    <span style={{ color: '#94A3B8', fontSize: '0.75rem' }}>vs</span>
                    <span className="recent-stat-val-p2">{recentSummary?.battingInnings2 ?? 0} inn</span>
                  </div>
                  <span style={{ fontSize: '0.72rem', color: '#94A3B8' }}>Matches actually participated</span>
                </div>
              </div>
            )}

            {/* Sample size warning alerts if sample is small */}
            {isBowlingRecentFocus ? (
              (recentSummary?.bowlingSampleSizeWarning1 || recentSummary?.bowlingSampleSizeWarning2) && (
                <div className="context-warning-alert">
                  <span>⚠️</span>
                  <div>
                    <strong>Small Sample Warning:</strong> {recentSummary?.bowlingSampleSizeWarning1 || recentSummary?.bowlingSampleSizeWarning2} Trends based on fewer than 3 bowling spells carry limited statistical confidence.
                  </div>
                </div>
              )
            ) : (
              (recentSummary?.sampleSizeWarning1 || recentSummary?.sampleSizeWarning2) && (
                <div className="context-warning-alert">
                  <span>⚠️</span>
                  <div>
                    <strong>Small Sample Warning:</strong> {recentSummary?.sampleSizeWarning1 || recentSummary?.sampleSizeWarning2} Trends based on fewer than 3 innings carry limited statistical confidence.
                  </div>
                </div>
              )
            )}

            {/* Chronological Recent Form Line Chart */}
            {recentFormChartData && (
              <div style={{ marginTop: 'var(--space-2)' }}>
                <div style={{ height: 280, background: 'rgba(255, 255, 255, 0.02)', padding: 'var(--space-4)', borderRadius: 'var(--radius-lg)', border: '1px solid var(--color-border)' }}>
                  <Line
                    data={recentFormChartData}
                    options={{
                      responsive: true,
                      maintainAspectRatio: false,
                      plugins: {
                        legend: { labels: { color: '#334155', font: { size: 11 } } },
                        tooltip: { mode: 'index', intersect: false },
                      },
                      scales: {
                        x: { ticks: { color: '#94A3B8' }, grid: { color: 'rgba(15, 23, 42, 0.06)' } },
                        y: {
                          ticks: {
                            color: '#94A3B8',
                            precision: isBowlingRecentFocus ? 0 : undefined,
                          },
                          grid: { color: 'rgba(15, 23, 42, 0.08)' },
                          beginAtZero: true
                        },
                      },
                    }}
                  />
                </div>
                <p style={{ margin: '8px 0 0', fontSize: '0.75rem', color: '#94A3B8' }}>
                  ℹ️ {recentSummary?.calculationBasis || (isBowlingRecentFocus ? 'Calculated from eligible bowling spells where player participated in chronological order. Missing matches are not zero-filled.' : 'Calculated from eligible innings where player participated in chronological order. Missing matches are not zero-filled.')}
                </p>
              </div>
            )}

            {/* Match-by-Match History Comparison Table */}
            <div style={{ marginTop: 'var(--space-4)' }}>
              <h4 style={{ margin: '0 0 var(--space-3)', color: 'var(--color-text-primary)', fontSize: '0.9rem' }}>
                Match-by-Match History Breakdown {isBowlingRecentFocus ? '(Bowling Spells)' : '(Batting Innings)'}
              </h4>
              <div className="compare-tables-row">
                {/* Player 1 Recent Matches */}
                <div className="match-table-wrapper">
                  <h5 style={{ padding: '10px 14px', margin: 0, borderBottom: '1px solid var(--color-border)', color: '#818CF8' }}>
                    {comparison.player1Name} {isBowlingRecentFocus ? 'Recent Bowling Spells' : 'Recent Innings'}
                  </h5>
                  {isBowlingRecentFocus ? (
                    <table className="match-table">
                      <thead>
                        <tr>
                          <th>Date</th>
                          <th>Format</th>
                          <th>vs</th>
                          <th>Venue</th>
                          <th>Overs</th>
                          <th>Runs</th>
                          <th>Wkts</th>
                          <th>Figures</th>
                          <th>Econ</th>
                        </tr>
                      </thead>
                      <tbody>
                        {(p1?.recentBowlingForm?.length > 0 ? p1.recentBowlingForm.slice(-selectedRecentN).reverse() : []).map((m, i) => (
                          <tr key={`p1-bowl-${m.matchId || i}`}>
                            <td>{m.date}</td>
                            <td><span className="format-badge-sm">{m.format}</span></td>
                            <td>{m.opposition}</td>
                            <td title={m.venue || ''}>{m.venue ? (m.venue.length > 18 ? m.venue.slice(0, 16) + '…' : m.venue) : '—'}</td>
                            <td>{m.overs != null ? m.overs : '—'}</td>
                            <td>{m.runsConceded != null ? m.runsConceded : '—'}</td>
                            <td className="score-cell" style={{ color: '#F43F5E', fontWeight: 700 }}>{m.wickets != null ? m.wickets : '—'}</td>
                            <td>
                              <strong>{m.figures || (m.wickets != null && m.runsConceded != null ? `${m.wickets}/${m.runsConceded}` : '—')}</strong>
                              {m.haulBadge && (
                                <span
                                  className="compare-adv-badge"
                                  style={{
                                    background: m.haulBadge === '5W' ? 'rgba(239, 68, 68, 0.2)' : 'rgba(245, 158, 11, 0.2)',
                                    color: m.haulBadge === '5W' ? '#F87171' : '#FCD34D',
                                    borderColor: m.haulBadge === '5W' ? '#EF4444' : '#F59E0B',
                                    marginLeft: '4px'
                                  }}
                                >
                                  {m.haulBadge}
                                </span>
                              )}
                            </td>
                            <td>{m.economyRate != null ? m.economyRate.toFixed(2) : '—'}</td>
                          </tr>
                        ))}
                        {!p1?.recentBowlingForm?.length && (
                          <tr><td colSpan="9" style={{ textAlign: 'center', color: '#94A3B8' }}>No bowling spells recorded in this format. (Did not bowl)</td></tr>
                        )}
                      </tbody>
                    </table>
                  ) : (
                    <table className="match-table">
                      <thead>
                        <tr>
                          <th>Date</th><th>Format</th><th>vs</th><th>Score / Wkts</th>
                        </tr>
                      </thead>
                      <tbody>
                        {(p1?.recentBattingForm?.length > 0 ? p1.recentBattingForm.slice(-selectedRecentN).reverse() : []).map((m, i) => (
                          <tr key={`p1-bat-${i}`}>
                            <td>{m.date}</td>
                            <td><span className="format-badge-sm">{m.format}</span></td>
                            <td>{m.opposition}</td>
                            <td className="score-cell">{m.runs} runs ({m.ballsFaced}b)</td>
                          </tr>
                        ))}
                        {(p1?.recentBattingForm?.length === 0 && p1?.recentBowlingForm?.length > 0
                          ? p1.recentBowlingForm.slice(-selectedRecentN).reverse() : []).map((m, i) => (
                          <tr key={`p1-bowl-${i}`}>
                            <td>{m.date}</td>
                            <td><span className="format-badge-sm">{m.format}</span></td>
                            <td>{m.opposition}</td>
                            <td className="score-cell" style={{ color: '#EF4444' }}>{m.wickets} wkts ({m.runsConceded}r)</td>
                          </tr>
                        ))}
                        {!p1?.recentBattingForm?.length && !p1?.recentBowlingForm?.length && (
                          <tr><td colSpan="4" style={{ textAlign: 'center', color: '#94A3B8' }}>No matches recorded in this format.</td></tr>
                        )}
                      </tbody>
                    </table>
                  )}
                </div>

                {/* Player 2 Recent Matches */}
                <div className="match-table-wrapper">
                  <h5 style={{ padding: '10px 14px', margin: 0, borderBottom: '1px solid var(--color-border)', color: '#F472B6' }}>
                    {comparison.player2Name} {isBowlingRecentFocus ? 'Recent Bowling Spells' : 'Recent Innings'}
                  </h5>
                  {isBowlingRecentFocus ? (
                    <table className="match-table">
                      <thead>
                        <tr>
                          <th>Date</th>
                          <th>Format</th>
                          <th>vs</th>
                          <th>Venue</th>
                          <th>Overs</th>
                          <th>Runs</th>
                          <th>Wkts</th>
                          <th>Figures</th>
                          <th>Econ</th>
                        </tr>
                      </thead>
                      <tbody>
                        {(p2?.recentBowlingForm?.length > 0 ? p2.recentBowlingForm.slice(-selectedRecentN).reverse() : []).map((m, i) => (
                          <tr key={`p2-bowl-${m.matchId || i}`}>
                            <td>{m.date}</td>
                            <td><span className="format-badge-sm">{m.format}</span></td>
                            <td>{m.opposition}</td>
                            <td title={m.venue || ''}>{m.venue ? (m.venue.length > 18 ? m.venue.slice(0, 16) + '…' : m.venue) : '—'}</td>
                            <td>{m.overs != null ? m.overs : '—'}</td>
                            <td>{m.runsConceded != null ? m.runsConceded : '—'}</td>
                            <td className="score-cell" style={{ color: '#F43F5E', fontWeight: 700 }}>{m.wickets != null ? m.wickets : '—'}</td>
                            <td>
                              <strong>{m.figures || (m.wickets != null && m.runsConceded != null ? `${m.wickets}/${m.runsConceded}` : '—')}</strong>
                              {m.haulBadge && (
                                <span
                                  className="compare-adv-badge"
                                  style={{
                                    background: m.haulBadge === '5W' ? 'rgba(239, 68, 68, 0.2)' : 'rgba(245, 158, 11, 0.2)',
                                    color: m.haulBadge === '5W' ? '#F87171' : '#FCD34D',
                                    borderColor: m.haulBadge === '5W' ? '#EF4444' : '#F59E0B',
                                    marginLeft: '4px'
                                  }}
                                >
                                  {m.haulBadge}
                                </span>
                              )}
                            </td>
                            <td>{m.economyRate != null ? m.economyRate.toFixed(2) : '—'}</td>
                          </tr>
                        ))}
                        {!p2?.recentBowlingForm?.length && (
                          <tr><td colSpan="9" style={{ textAlign: 'center', color: '#94A3B8' }}>No bowling spells recorded in this format. (Did not bowl)</td></tr>
                        )}
                      </tbody>
                    </table>
                  ) : (
                    <table className="match-table">
                      <thead>
                        <tr>
                          <th>Date</th><th>Format</th><th>vs</th><th>Score / Wkts</th>
                        </tr>
                      </thead>
                      <tbody>
                        {(p2?.recentBattingForm?.length > 0 ? p2.recentBattingForm.slice(-selectedRecentN).reverse() : []).map((m, i) => (
                          <tr key={`p2-bat-${i}`}>
                            <td>{m.date}</td>
                            <td><span className="format-badge-sm">{m.format}</span></td>
                            <td>{m.opposition}</td>
                            <td className="score-cell">{m.runs} runs ({m.ballsFaced}b)</td>
                          </tr>
                        ))}
                        {(p2?.recentBattingForm?.length === 0 && p2?.recentBowlingForm?.length > 0
                          ? p2.recentBowlingForm.slice(-selectedRecentN).reverse() : []).map((m, i) => (
                          <tr key={`p2-bowl-${i}`}>
                            <td>{m.date}</td>
                            <td><span className="format-badge-sm">{m.format}</span></td>
                            <td>{m.opposition}</td>
                            <td className="score-cell" style={{ color: '#EF4444' }}>{m.wickets} wkts ({m.runsConceded}r)</td>
                          </tr>
                        ))}
                        {!p2?.recentBattingForm?.length && !p2?.recentBowlingForm?.length && (
                          <tr><td colSpan="4" style={{ textAlign: 'center', color: '#94A3B8' }}>No matches recorded in this format.</td></tr>
                        )}
                      </tbody>
                    </table>
                  )}
                </div>
              </div>
            </div>
          </section>

          {/* ─────────────────────────────────────────────────────────────
              SECTION 4: Venue and Opposition Context Analysis
             ───────────────────────────────────────────────────────────── */}
          <section className="compare-section-block" id="section-4-context-analysis">
            <div className="section-header-badge">
              <span className="section-number">4</span>
              <h2 className="section-title">Venue & Opposition Context Analysis</h2>
            </div>
            <p className="section-subtitle">
              Filter comparisons under identical environmental conditions (opponent team and ground venue) to isolate performance differences in context.
            </p>

            {/* Context Dropdowns Bar */}
            <div className="compare-context-bar">
              <span className="compare-context-label">
                <span>Match Conditions:</span>
              </span>

              {/* Opposition Dropdown */}
              <select
                className="compare-select"
                value={selectedOpposition}
                onChange={e => handleOppositionChange(e.target.value)}
              >
                <option value="">All Oppositions</option>
                {context?.commonOppositions?.length > 0 && (
                  <optgroup label="Shared Opponents (Both Played)">
                    {context.commonOppositions.map(o => (
                      <option key={`shared-${o}`} value={o}>vs {o}</option>
                    ))}
                  </optgroup>
                )}
                {context?.allOppositions?.length > 0 && (
                  <optgroup label="All Available Opponents">
                    {context.allOppositions.map(o => (
                      <option key={`all-${o}`} value={o}>vs {o}</option>
                    ))}
                  </optgroup>
                )}
              </select>

              {/* Venue Dropdown */}
              <select
                className="compare-select"
                value={selectedVenue}
                onChange={e => handleVenueChange(e.target.value)}
              >
                <option value="">All Venues</option>
                {context?.commonVenues?.length > 0 && (
                  <optgroup label="Shared Venues (Both Played)">
                    {context.commonVenues.map(v => (
                      <option key={`shared-ven-${v}`} value={v}>{v}</option>
                    ))}
                  </optgroup>
                )}
                {context?.allVenues?.length > 0 && (
                  <optgroup label="All Available Venues">
                    {context.allVenues.map(v => (
                      <option key={`all-ven-${v}`} value={v}>{v}</option>
                    ))}
                  </optgroup>
                )}
              </select>

              {(selectedOpposition || selectedVenue) && (
                <button
                  type="button"
                  className="btn btn-secondary"
                  style={{ padding: '4px 10px', fontSize: '0.78rem' }}
                  onClick={clearFilters}
                >
                  ✕ Clear Context Filter
                </button>
              )}

              {(selectedOpposition || selectedVenue) && (
                <span style={{ fontSize: '0.78rem', color: '#818CF8', marginLeft: 'auto' }}>
                  Active Filter: {selectedOpposition ? `vs ${selectedOpposition}` : ''} {selectedVenue ? `@ ${selectedVenue}` : ''}
                </span>
              )}
            </div>

            {/* Context Sample Size Warning */}
            {context?.contextualSampleSizeWarning && (
              <div className="context-warning-alert" style={{ marginTop: 'var(--space-3)' }}>
                <span>⚠️</span>
                <div>
                  <strong>Contextual Data Limitation:</strong> {context.contextualSampleSizeWarning}
                </div>
              </div>
            )}

            {/* Context Comparison Cards (When filtered) */}
            {(selectedOpposition || selectedVenue) && (
              <div className="context-comparison-grid">
                {/* Context Batting Card */}
                {context?.contextualBatting && (
                  <div className="context-card">
                    <h4 className="context-card-title">
                      Context Batting Output ({selectedOpposition ? `vs ${selectedOpposition}` : ''} {selectedVenue ? `@ ${selectedVenue}` : ''})
                    </h4>
                    <div className="context-metrics-list">
                      <div className="context-metric-item">
                        <span className="context-metric-label">Innings Played:</span>
                        <div className="context-metric-values">
                          <span style={{ color: '#818CF8' }}>{context.contextualBatting.innings1} inn</span>
                          <span style={{ color: '#94A3B8' }}>vs</span>
                          <span style={{ color: '#F472B6' }}>{context.contextualBatting.innings2} inn</span>
                        </div>
                      </div>
                      <div className="context-metric-item">
                        <span className="context-metric-label">Total Runs:</span>
                        <div className="context-metric-values">
                          <span style={{ color: '#818CF8' }}>{context.contextualBatting.totalRuns1}</span>
                          <span style={{ color: '#94A3B8' }}>vs</span>
                          <span style={{ color: '#F472B6' }}>{context.contextualBatting.totalRuns2}</span>
                        </div>
                      </div>
                      <div className="context-metric-item">
                        <span className="context-metric-label">Runs Per Innings:</span>
                        <div className="context-metric-values">
                          <span style={{ color: '#818CF8' }}>{context.contextualBatting.runsPerInnings1 ?? '—'}</span>
                          <span style={{ color: '#94A3B8' }}>vs</span>
                          <span style={{ color: '#F472B6' }}>{context.contextualBatting.runsPerInnings2 ?? '—'}</span>
                        </div>
                      </div>
                      <div className="context-metric-item">
                        <span className="context-metric-label">Strike Rate:</span>
                        <div className="context-metric-values">
                          <span style={{ color: '#818CF8' }}>{context.contextualBatting.strikeRate1 ?? '—'}</span>
                          <span style={{ color: '#94A3B8' }}>vs</span>
                          <span style={{ color: '#F472B6' }}>{context.contextualBatting.strikeRate2 ?? '—'}</span>
                        </div>
                      </div>
                      <div className="context-metric-item">
                        <span className="context-metric-label">Highest Score:</span>
                        <div className="context-metric-values">
                          <span style={{ color: '#818CF8' }}>{context.contextualBatting.highestScore1 != null ? `${context.contextualBatting.highestScore1}` : '—'}</span>
                          <span style={{ color: '#94A3B8' }}>vs</span>
                          <span style={{ color: '#F472B6' }}>{context.contextualBatting.highestScore2 != null ? `${context.contextualBatting.highestScore2}` : '—'}</span>
                        </div>
                      </div>
                    </div>
                  </div>
                )}

                {/* Context Bowling Card */}
                {context?.contextualBowling && (
                  <div className="context-card">
                    <h4 className="context-card-title">
                      Context Bowling Output ({selectedOpposition ? `vs ${selectedOpposition}` : ''} {selectedVenue ? `@ ${selectedVenue}` : ''})
                    </h4>
                    <div className="context-metrics-list">
                      <div className="context-metric-item">
                        <span className="context-metric-label">Innings Bowled:</span>
                        <div className="context-metric-values">
                          <span style={{ color: '#818CF8' }}>{context.contextualBowling.innings1} inn</span>
                          <span style={{ color: '#94A3B8' }}>vs</span>
                          <span style={{ color: '#F472B6' }}>{context.contextualBowling.innings2} inn</span>
                        </div>
                      </div>
                      <div className="context-metric-item">
                        <span className="context-metric-label">Total Wickets:</span>
                        <div className="context-metric-values">
                          <span style={{ color: '#818CF8' }}>{context.contextualBowling.totalWickets1}</span>
                          <span style={{ color: '#94A3B8' }}>vs</span>
                          <span style={{ color: '#F472B6' }}>{context.contextualBowling.totalWickets2}</span>
                        </div>
                      </div>
                      <div className="context-metric-item">
                        <span className="context-metric-label">Economy Rate:</span>
                        <div className="context-metric-values">
                          <span style={{ color: '#818CF8' }}>{context.contextualBowling.economyRate1 ?? '—'}</span>
                          <span style={{ color: '#94A3B8' }}>vs</span>
                          <span style={{ color: '#F472B6' }}>{context.contextualBowling.economyRate2 ?? '—'}</span>
                        </div>
                      </div>
                      <div className="context-metric-item">
                        <span className="context-metric-label">Bowling Average:</span>
                        <div className="context-metric-values">
                          <span style={{ color: '#818CF8' }}>{context.contextualBowling.bowlingAverage1 ?? '—'}</span>
                          <span style={{ color: '#94A3B8' }}>vs</span>
                          <span style={{ color: '#F472B6' }}>{context.contextualBowling.bowlingAverage2 ?? '—'}</span>
                        </div>
                      </div>
                      <div className="context-metric-item">
                        <span className="context-metric-label">Bowling Strike Rate:</span>
                        <div className="context-metric-values">
                          <span style={{ color: '#818CF8' }}>{context.contextualBowling.bowlingStrikeRate1 ?? '—'}</span>
                          <span style={{ color: '#94A3B8' }}>vs</span>
                          <span style={{ color: '#F472B6' }}>{context.contextualBowling.bowlingStrikeRate2 ?? '—'}</span>
                        </div>
                      </div>
                    </div>
                  </div>
                )}
              </div>
            )}

            {!selectedOpposition && !selectedVenue && (
              <div style={{ background: 'rgba(255, 255, 255, 0.02)', border: '1px dashed var(--color-border)', borderRadius: 'var(--radius-lg)', padding: 'var(--space-4)', marginTop: 'var(--space-2)' }}>
                <p style={{ margin: 0, color: '#94A3B8', fontSize: '0.84rem' }}>
                  <strong>Note:</strong> Select an opposition team or match venue above to compare both players strictly under equivalent conditions (e.g. testing record against Australian pace or performance on spin-friendly subcontinent pitches).
                </p>
              </div>
            )}
          </section>

          {/* ─────────────────────────────────────────────────────────────
              SECTION 5: Key Findings and Data Limitations
             ───────────────────────────────────────────────────────────── */}
          <section className="compare-section-block" id="section-5-key-findings">
            <div className="section-header-badge">
              <span className="section-number">5</span>
              <h2 className="section-title">Key Findings & Data Limitations</h2>
            </div>
            <p className="section-subtitle">
              Evidence-based analytical synthesis, source dataset integrity notes, and predictive machine-learning projections.
            </p>

            {/* Evidence-Based Written Interpretation */}
            {comparison.writtenInterpretation && (
              <div className="compare-interpretation-card">
                
                <div>
                  <h4 style={{ margin: '0 0 6px', color: '#818CF8', fontSize: '0.92rem', textTransform: 'uppercase', letterSpacing: '0.05em' }}>
                    Analytical Head-to-Head Interpretation
                  </h4>
                  <p className="compare-interpretation-text">
                    {comparison.writtenInterpretation}
                  </p>
                </div>
              </div>
            )}

            {/* Historical Dataset Scope & Data Limitations Card */}
            <div className="coverage-block" style={{ marginTop: 'var(--space-3)' }}>
              <h4 style={{ margin: '0 0 8px', fontSize: '0.92rem', color: 'var(--color-text-primary)' }}>
                Dataset Scope & Source Boundaries ({comparison.format})
              </h4>
              <div className="coverage-grid" style={{ gridTemplateColumns: '1fr 1fr' }}>
                <div className="coverage-col">
                  <strong>{comparison.player1Name}:</strong>
                  <p><span>Available Batting Innings:</span><strong>{p1?.coverage?.battingInningsForFormat ?? 0}</strong></p>
                  <p><span>Available Bowling Innings:</span><strong>{p1?.coverage?.bowlingInningsForFormat ?? 0}</strong></p>
                </div>
                <div className="coverage-col">
                  <strong>{comparison.player2Name}:</strong>
                  <p><span>Available Batting Innings:</span><strong>{p2?.coverage?.battingInningsForFormat ?? 0}</strong></p>
                  <p><span>Available Bowling Innings:</span><strong>{p2?.coverage?.bowlingInningsForFormat ?? 0}</strong></p>
                </div>
              </div>
              <div style={{ marginTop: '12px', fontSize: '0.8rem', color: '#94A3B8', display: 'flex', flexDirection: 'column', gap: '6px', lineHeight: 1.5 }}>
                <p style={{ margin: 0 }}>
                  • <strong>Sample Scope:</strong> Statistics represent the subset of international matches recorded in the curated CSV dataset, not lifetime career totals.
                </p>
                <p style={{ margin: 0 }}>
                  • <strong>Format Isolation:</strong> T20 Internationals (T20I) are strictly segregated from franchise leagues (e.g. IPL, BBL). The ALL aggregate combines formats without duplicating match records.
                </p>
                <p style={{ margin: 0 }}>
                  • <strong>Metric Integrity:</strong> Dot-ball percentage and maiden overs return N/A as ball-by-ball delivery markers and maiden flags are not present in the match-summary source data.
                </p>
                <p style={{ margin: 0 }}>
                  • <strong>Valid Denominators:</strong> Batting averages are only calculated where dismissals &gt; 0. Bowling averages and strike rates strictly require wickets &gt; 0 with overs converted via standard 6-ball units (e.g. 4.2 ov = 26 balls).
                </p>
              </div>
            </div>

            {/* AI Next-Match Performance Prediction Head-to-Head */}
            <div className="compare-prediction-section" style={{ marginTop: 'var(--space-4)' }}>
              <div style={{ display: 'flex', alignItems: 'flex-start', justifyContent: 'space-between', flexWrap: 'wrap', gap: '12px' }}>
                <div>
                  <h3 style={{ margin: '0 0 4px', fontSize: '1.15rem', color: 'var(--color-text-primary)', display: 'flex', alignItems: 'center', gap: '8px' }}>
                    Next-Match Performance Prediction (Head-to-Head)
                  </h3>
                  <p style={{ margin: 0, fontSize: '0.84rem', color: 'var(--color-text-secondary)' }}>
                    Statistical performance projections estimated by the Flask Machine Learning service for upcoming match conditions.
                  </p>
                </div>

                {/* Prediction Parameters */}
                <div style={{ display: 'flex', alignItems: 'center', gap: '8px', flexWrap: 'wrap' }}>
                  <select
                    className="compare-select"
                    value={predFormat}
                    onChange={e => setPredFormat(e.target.value)}
                  >
                    <option value="ODI">ODI</option>
                    <option value="T20I">T20I</option>
                    <option value="TEST">TEST</option>
                  </select>

                  <select
                    className="compare-select"
                    value={predOpponent}
                    onChange={e => setPredOpponent(e.target.value)}
                  >
                    <option value="">Select Opponent</option>
                    {(context?.commonOppositions || ['Australia', 'England', 'India', 'South Africa', 'New Zealand', 'Pakistan', 'Sri Lanka']).map(o => (
                      <option key={`pred-opp-${o}`} value={o}>{o}</option>
                    ))}
                  </select>

                  <button
                    type="button"
                    className="btn btn-primary"
                    style={{ padding: '6px 14px', fontSize: '0.85rem' }}
                    onClick={handleRunPrediction}
                    disabled={predLoading}
                  >
                    {predLoading ? 'Simulating…' : 'Run Prediction'}
                  </button>
                </div>
              </div>

              {predError && (
                <div style={{ background: '#fee2e2', border: '1px solid #fecaca', borderRadius: 'var(--radius-md)', padding: '10px 14px', color: '#b91c1c', fontSize: '0.85rem' }}>
                  ⚠️ {predError}
                </div>
              )}

              {predResult && (
                <div style={{ display: 'flex', flexDirection: 'column', gap: 'var(--space-3)' }}>
                  <div className="compare-prediction-grid">
                    {/* Player 1 Card */}
                    <div className="compare-pred-card" style={{ borderColor: 'rgba(99, 102, 241, 0.4)' }}>
                      <span style={{ fontSize: '0.85rem', color: '#818CF8', fontWeight: 700 }}>{comparison.player1Name}</span>
                      <div className="compare-pred-runs" style={{ color: '#818CF8' }}>
                        {predResult.runs1}
                        <span style={{ fontSize: '0.9rem', color: '#94A3B8', fontWeight: 500, marginLeft: '4px' }}>proj. runs</span>
                      </div>
                      <span className={`tier-badge ${predResult.tier1.cls}`}>{predResult.tier1.label}</span>
                      {predResult.adv === 'player1' && (
                        <span className="compare-adv-badge">Projected Run Advantage (+{predResult.runs1 - predResult.runs2})</span>
                      )}
                    </div>

                    {/* Player 2 Card */}
                    <div className="compare-pred-card" style={{ borderColor: 'rgba(236, 72, 153, 0.4)' }}>
                      <span style={{ fontSize: '0.85rem', color: '#F472B6', fontWeight: 700 }}>{comparison.player2Name}</span>
                      <div className="compare-pred-runs" style={{ color: '#F472B6' }}>
                        {predResult.runs2}
                        <span style={{ fontSize: '0.9rem', color: '#94A3B8', fontWeight: 500, marginLeft: '4px' }}>proj. runs</span>
                      </div>
                      <span className={`tier-badge ${predResult.tier2.cls}`}>{predResult.tier2.label}</span>
                      {predResult.adv === 'player2' && (
                        <span className="compare-adv-badge">Projected Run Advantage (+{predResult.runs2 - predResult.runs1})</span>
                      )}
                    </div>
                  </div>

                  <div style={{ background: '#f8fafc', border: '1px solid var(--color-border)', borderRadius: 'var(--radius-md)', padding: '8px 12px', fontSize: '0.78rem', color: 'var(--color-text-secondary)' }}>
                    ℹ️ <strong>Model Disclaimer:</strong> AI model predictions are statistical ML estimates based on historical player records, opposition, venue, and match format features. Predictions are not guaranteed future outcomes. Historical records and predictive estimates are strictly separated.
                  </div>
                </div>
              )}
            </div>
          </section>
        </>
      )}

      {/* Empty State */}
      {!comparison && !loading && (
        <div className="analytics-empty-state">
          <div className="analytics-empty-icon"><svg viewBox="0 0 24 24" width="40" height="40" fill="none" stroke="currentColor" strokeWidth="1.75"><path d="M16 16l3-8 3 8c-.87.65-1.92 1-3 1s-2.13-.35-3-1zM2 16l3-8 3 8c-.87.65-1.92 1-3 1s-2.13-.35-3-1zM7 21h10M12 3v18M3 7h18" /></svg></div>
          <h3>Select Two Players to Compare</h3>
          <p>Search and select two international cricketers above, choose a match format, and click "Compare Players".</p>
          <p className="analytics-empty-hint">
            The system organizes analysis into 5 clear sections: Player Selection, Overall Statistics, Recent Form & Consistency, Venue & Opposition Context, and Key Analytical Findings.
          </p>
        </div>
      )}
    </div>
    </DashboardLayout>
  );
}
