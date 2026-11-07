import DashboardLayout from "../components/layout/DashboardLayout";
/**
 * PlayerAnalytics.jsx
 *
 * Enhanced Player Analytics & Historical Performance Dashboard
 *
 * Provides comprehensive, role-aware cricket performance analytics:
 *  - BATSMAN: Runs per innings trend (with average line), batting strike-rate trend,
 *             scoring distribution (milestones), boundary contribution (4s & 6s),
 *             scoring consistency (CV%), and chronological match history.
 *  - BOWLER:  Wickets trend (with average line), economy-rate trend (actual overs),
 *             bowling effectiveness (average, strike rate), wickets vs runs comparison,
 *             bowling distribution, consistency (std dev), and match history.
 *  - ALL_ROUNDER: Two distinct, partitioned groups of charts for Batting and Bowling.
 *  - UNKNOWN: Displays verified records without forcing unconfirmed roles.
 *  - Format filtering (ALL, ODI, T20I, TEST) and Recent-Form window selector (Last 5, 10, 15).
 *
 * Data source: Spring Boot API (/api/analytics/player).
 * All calculations and rate formulas are performed in Spring Boot.
 */

import { useState, useEffect, useCallback, useRef, useMemo } from 'react';
import {
  Chart as ChartJS,
  CategoryScale, LinearScale,
  BarElement, PointElement, LineElement,
  Title, Tooltip, Legend, Filler,
} from 'chart.js';
import { Bar, Line } from 'react-chartjs-2';
import { getPlayerAnalytics, getPlayerStrengths, listAnalyticsPlayers, exportPlayerAnalysisExcel } from '../services/analyticsService';
import { exportPlayerAnalysisToExcel } from '../services/excelExportService';

ChartJS.register(
  CategoryScale, LinearScale, BarElement,
  PointElement, LineElement, Title, Tooltip, Legend, Filler
);

// ─── Format colors & constants ───────────────────────────────────────────────

const FORMAT_COLORS = {
  TEST: { bg: 'rgba(251,191,36,0.85)',  border: '#FBB924' },
  ODI:  { bg: 'rgba(99,200,132,0.85)',  border: '#63C884' },
  T20I: { bg: 'rgba(139,92,246,0.85)',  border: '#8B5CF6' },
  T20:  { bg: 'rgba(251,107,30,0.85)',  border: '#FB6B1E' },
};
const DEFAULT_COLOR = { bg: 'rgba(148,163,184,0.7)', border: '#94A3B8' };
const BOWL_COLOR    = { bg: 'rgba(239,68,68,0.7)',   border: '#EF4444' };
const BAT_COLOR     = { bg: 'rgba(99,200,132,0.7)',  border: '#63C884' };

function fmtColor(fmt) { return FORMAT_COLORS[fmt?.toUpperCase()] || DEFAULT_COLOR; }

const FORMATS = ['ALL', 'ODI', 'T20I', 'TEST'];
const RECENT_WINDOWS = [5, 10, 15];

// ─── Reusable UI building blocks ─────────────────────────────────────────────

function StatCard({ label, value, sub, accent }) {
  return (
    <div className="stat-card" style={{ borderTop: `3px solid ${accent || '#6C63FF'}` }}>
      <span className="stat-card-value">{value ?? '—'}</span>
      <span className="stat-card-label">{label}</span>
      {sub && <span className="stat-card-sub">{sub}</span>}
    </div>
  );
}

function Section({ id, title, icon, subtitle, children }) {
  return (
    <div className="analytics-section" id={id}>
      <div style={{ marginBottom: 'var(--space-4)' }}>
        <h3 className="analytics-section-title" style={{ margin: 0 }}>
          <span className="analytics-section-icon">{icon}</span>
          {title}
        </h3>
        {subtitle && (
          <p style={{ margin: '4px 0 0 0', fontSize: '0.82rem', color: '#94A3B8' }}>
            {subtitle}
          </p>
        )}
      </div>
      {children}
    </div>
  );
}

function Empty({ msg }) {
  return <p className="analytics-empty">{msg || 'No data available for this selection.'}</p>;
}

function trendBadge(dir) {
  if (dir === 'UPWARD')
    return <span className="trend-badge trend-badge--upward">Upward Form</span>;
  if (dir === 'DOWNWARD')
    return <span className="trend-badge trend-badge--downward">Declining</span>;
  if (dir === 'STABLE')
    return <span className="trend-badge trend-badge--stable">➡️ Stable Form</span>;
  return <span className="trend-badge trend-badge--stable">ℹ️ Sample In Progress</span>;
}

function FormatTabs({ active, onChange }) {
  return (
    <div className="format-tabs" role="tablist">
      {FORMATS.map(f => (
        <button
          key={f}
          role="tab"
          aria-selected={active === f}
          className={`format-tab${active === f ? ' format-tab--active' : ''}`}
          style={active === f ? { borderBottomColor: fmtColor(f).border, color: fmtColor(f).border } : {}}
          onClick={() => onChange(f)}
        >
          {f}
        </button>
      ))}
    </div>
  );
}

function WindowTabs({ active, onChange, isBowling }) {
  return (
    <div className="window-tabs" title="Recent Appearances Window">
      <span style={{ fontSize: '0.78rem', padding: '4px 8px', color: '#94A3B8', fontWeight: 600 }}>
        {isBowling ? 'Spell Window:' : 'Innings Window:'}
      </span>
      {RECENT_WINDOWS.map(n => (
        <button
          key={n}
          type="button"
          className={`window-tab${active === n ? ' window-tab--active' : ''}`}
          onClick={() => onChange(n)}
        >
          Last {n} {isBowling ? 'Spells' : 'Innings'}
        </button>
      ))}
    </div>
  );
}

// ─── Data Coverage block ─────────────────────────────────────────────────────

function CoverageBlock({ coverage, role }) {
  if (!coverage) return null;

  const roleLabels = {
    BATSMAN:    { icon: '', label: 'Batsman' },
    BOWLER:     { icon: '', label: 'Bowler' },
    ALL_ROUNDER:{ icon: '', label: 'All-Rounder' },
    UNKNOWN:    { icon: '❓', label: 'Unknown' },
  };
  const rl = roleLabels[role] || roleLabels.UNKNOWN;

  return (
    <div className="coverage-block">
      <div className="coverage-header">
        <span className="coverage-role-badge">
          {rl.icon} {rl.label}
        </span>
        <span className="coverage-format-badge">
          Format: {coverage.selectedFormat}
        </span>
      </div>

      <div className="coverage-grid">
        {(role === 'BATSMAN' || role === 'ALL_ROUNDER' || role === 'UNKNOWN') && (
          <div className="coverage-col">
            <h4> Batting Coverage</h4>
            <p><span>All-format innings:</span><strong>{coverage.totalBattingInningsAllFormats ?? '—'}</strong></p>
            <p><span>Innings for selected format:</span><strong>{coverage.battingInningsForFormat ?? '—'}</strong></p>
            <p><span>Used in recent form:</span><strong>{coverage.recentBattingFormCount ?? '—'}</strong></p>
            {coverage.battingEarliestDate && <p><span>Dataset start:</span><strong>{coverage.battingEarliestDate}</strong></p>}
            {coverage.battingLatestDate   && <p><span>Dataset end:</span><strong>{coverage.battingLatestDate}</strong></p>}
            {coverage.recentBattingFormStartDate && (
              <p><span>Recent-form from:</span><strong>{coverage.recentBattingFormStartDate}</strong></p>
            )}
          </div>
        )}

        {(role === 'BOWLER' || role === 'ALL_ROUNDER' || role === 'UNKNOWN') && (
          <div className="coverage-col">
            <h4> Bowling Coverage</h4>
            <p><span>All-format innings:</span><strong>{coverage.totalBowlingInningsAllFormats ?? '—'}</strong></p>
            <p><span>Innings for selected format:</span><strong>{coverage.bowlingInningsForFormat ?? '—'}</strong></p>
            <p><span>Used in recent form:</span><strong>{coverage.recentBowlingFormCount ?? '—'}</strong></p>
            {coverage.bowlingEarliestDate && <p><span>Dataset start:</span><strong>{coverage.bowlingEarliestDate}</strong></p>}
            {coverage.bowlingLatestDate   && <p><span>Dataset end:</span><strong>{coverage.bowlingLatestDate}</strong></p>}
            {coverage.recentBowlingFormStartDate && (
              <p><span>Recent-form from:</span><strong>{coverage.recentBowlingFormStartDate}</strong></p>
            )}
          </div>
        )}
      </div>

      {coverage.coverageNote && (
        <p className="coverage-note">ℹ️ {coverage.coverageNote}</p>
      )}
    </div>
  );
}

// ─── Phase 1: BATSMAN Charts & Components ─────────────────────────────────────

function RunsPerInningsTrendChart({ data, meanRuns, windowN }) {
  if (!data?.length) return <Empty msg="No recent batting innings found in this format." />;

  const labels = data.map((d, i) => `${d.date ? d.date.slice(5) : `Inn ${i + 1}`}\nvs ${d.opposition || 'Opp'}`);
  const runs = data.map(d => d.runs);
  const avgLine = meanRuns != null ? data.map(() => meanRuns) : [];

  const chartData = {
    labels,
    datasets: [
      {
        label: 'Innings Runs',
        data: runs,
        borderColor: '#63C884',
        backgroundColor: 'rgba(99, 200, 132, 0.15)',
        fill: true,
        tension: 0.3,
        pointRadius: 4,
        pointBackgroundColor: '#63C884',
        borderWidth: 2,
      },
      ...(meanRuns != null ? [{
        label: `Window Average (${meanRuns.toFixed(1)} runs)`,
        data: avgLine,
        borderColor: 'rgba(251, 191, 36, 0.9)',
        backgroundColor: 'transparent',
        borderDash: [5, 4],
        borderWidth: 2,
        pointRadius: 0,
        fill: false,
      }] : []),
    ],
  };

  const options = {
    responsive: true,
    maintainAspectRatio: false,
    plugins: {
      legend: { labels: { color: '#334155', font: { size: 11 } } },
      title: { display: true, text: `Chronological Runs per Innings (Last ${data.length} eligible innings)`, color: '#0F172A' },
      tooltip: {
        callbacks: {
          afterLabel: ctx => {
            if (ctx.datasetIndex !== 0) return null;
            const d = data[ctx.dataIndex];
            return [
              `Opponent: vs ${d.opposition} (${d.format})`,
              `Venue: ${d.venue || '—'}`,
              `Balls: ${d.ballsFaced} | SR: ${d.strikeRate?.toFixed(1) ?? '—'}`,
              `4s: ${d.fours} | 6s: ${d.sixes}${d.notOut ? ' (Not Out ✓)' : ''}`,
            ];
          },
        },
      },
    },
    scales: {
      x: { ticks: { color: '#475569', maxRotation: 40, font: { size: 10 } }, grid: { color: 'rgba(15, 23, 42, 0.06)' } },
      y: { ticks: { color: '#475569' }, grid: { color: 'rgba(15, 23, 42, 0.08)' }, beginAtZero: true,
           title: { display: true, text: 'Runs Scored', color: '#334155', font: { size: 11, weight: '600' } } },
    },
  };

  return (
    <div style={{ height: 280 }}>
      <Line data={chartData} options={options} />
    </div>
  );
}

function BattingStrikeRateTrendChart({ data, aggStrikeRate }) {
  if (!data?.length) return null;

  const validEntries = data.filter(d => d.ballsFaced > 0);
  if (!validEntries.length) return <Empty msg="No balls faced recorded for strike rate trend." />;

  const labels = validEntries.map((d, i) => `${d.date ? d.date.slice(5) : `Inn ${i + 1}`}\nvs ${d.opposition || 'Opp'}`);
  const srValues = validEntries.map(d => +(d.strikeRate ?? ((d.runs / d.ballsFaced) * 100)).toFixed(1));
  const aggLine = aggStrikeRate != null ? validEntries.map(() => aggStrikeRate) : [];

  const chartData = {
    labels,
    datasets: [
      {
        label: 'Innings Strike Rate',
        data: srValues,
        borderColor: '#818CF8',
        backgroundColor: 'rgba(129, 140, 248, 0.15)',
        fill: true,
        tension: 0.3,
        pointRadius: 4,
        pointBackgroundColor: '#818CF8',
        borderWidth: 2,
      },
      ...(aggStrikeRate != null ? [{
        label: `Aggregate Format SR (${aggStrikeRate.toFixed(1)})`,
        data: aggLine,
        borderColor: '#F472B6',
        backgroundColor: 'transparent',
        borderDash: [5, 4],
        borderWidth: 2,
        pointRadius: 0,
        fill: false,
      }] : []),
    ],
  };

  const options = {
    responsive: true,
    maintainAspectRatio: false,
    plugins: {
      legend: { labels: { color: '#334155', font: { size: 11 } } },
      title: { display: true, text: 'Batting Strike Rate Trend (Innings vs Aggregate)', color: '#0F172A' },
      tooltip: {
        callbacks: {
          afterLabel: ctx => {
            if (ctx.datasetIndex !== 0) return null;
            const d = validEntries[ctx.dataIndex];
            return [`Runs: ${d.runs} off ${d.ballsFaced} balls`, `Match SR: ${d.strikeRate?.toFixed(1)}`];
          },
        },
      },
    },
    scales: {
      x: { ticks: { color: '#475569', maxRotation: 40, font: { size: 10 } }, grid: { color: 'rgba(15, 23, 42, 0.06)' } },
      y: { ticks: { color: '#475569' }, grid: { color: 'rgba(15, 23, 42, 0.08)' }, beginAtZero: true,
           title: { display: true, text: 'Strike Rate (runs / 100 balls)', color: '#334155', font: { size: 11, weight: '600' } } },
    },
  };

  return (
    <div style={{ height: 280 }}>
      <Line data={chartData} options={options} />
    </div>
  );
}

function ScoringDistributionChart({ distribution, totalInnings }) {
  if (!distribution) return null;

  const labels = ['0 - 9 (Single)', '10 - 29 (Start)', '30 - 49 (Solid)', '50 - 99 (Fifty)', '100+ (Century)'];
  const counts = [
    distribution.singleDigits,
    distribution.starts,
    distribution.solidStarts,
    distribution.fifties,
    distribution.centuries,
  ];
  const pcts = [
    distribution.singleDigitsPct,
    distribution.startsPct,
    distribution.solidStartsPct,
    distribution.fiftiesPct,
    distribution.centuriesPct,
  ];

  const chartData = {
    labels,
    datasets: [{
      label: 'Innings Count',
      data: counts,
      backgroundColor: [
        'rgba(239, 68, 68, 0.75)',
        'rgba(251, 146, 60, 0.75)',
        'rgba(251, 191, 36, 0.75)',
        'rgba(52, 211, 153, 0.75)',
        'rgba(129, 140, 248, 0.85)',
      ],
      borderColor: [
        '#EF4444', '#FB923C', '#FBB924', '#34D399', '#818CF8'
      ],
      borderWidth: 2,
      borderRadius: 6,
    }],
  };

  const options = {
    responsive: true,
    maintainAspectRatio: false,
    plugins: {
      legend: { display: false },
      title: { display: true, text: `Innings Scoring Distribution (${totalInnings} total innings in format)`, color: '#0F172A' },
      tooltip: {
        callbacks: {
          afterLabel: ctx => `Share: ${pcts[ctx.dataIndex]?.toFixed(1)}% of all innings`,
        },
      },
    },
    scales: {
      x: { ticks: { color: '#334155', font: { size: 11, weight: '600' } }, grid: { color: 'rgba(15, 23, 42, 0.06)' } },
      y: { ticks: { color: '#475569', stepSize: 1 }, grid: { color: 'rgba(15, 23, 42, 0.08)' }, beginAtZero: true,
           title: { display: true, text: 'Frequency (Innings)', color: '#334155', font: { size: 11, weight: '600' } } },
    },
  };

  return (
    <div>
      <div style={{ height: 260 }}>
        <Bar data={chartData} options={options} />
      </div>
      {distribution.patternInterpretation && (
        <div className="analytics-interpretation-box">
          
          <div>
            <strong>Scoring Pattern Interpretation:</strong> {distribution.patternInterpretation}
            <p className="metric-formula-note">
              Distribution groups scores into standard cricket innings milestones: Single Digits (0-9), Starts (10-29), Solid Starts (30-49), Fifties (50-99), and Centuries (100+). Percentages denote empirical frequency across participated matches.
            </p>
          </div>
        </div>
      )}
    </div>
  );
}

function BoundaryContributionChart({ summary }) {
  if (!summary || summary.totalRuns === 0) return null;

  const fourRuns = summary.fours * 4;
  const sixRuns = summary.sixes * 6;
  const runningRuns = Math.max(0, summary.totalRuns - (fourRuns + sixRuns));

  const chartData = {
    labels: ['Runs via 4s', 'Runs via 6s', 'Running Between Wickets (1s, 2s, 3s)'],
    datasets: [{
      label: 'Runs Accumulated',
      data: [fourRuns, sixRuns, runningRuns],
      backgroundColor: [
        'rgba(99, 200, 132, 0.8)',
        'rgba(139, 92, 246, 0.8)',
        'rgba(148, 163, 184, 0.5)',
      ],
      borderColor: ['#63C884', '#8B5CF6', '#94A3B8'],
      borderWidth: 2,
      borderRadius: 6,
    }],
  };

  const options = {
    responsive: true,
    maintainAspectRatio: false,
    plugins: {
      legend: { display: false },
      title: { display: true, text: `Boundary Run Contribution (${summary.totalRuns} total format runs)`, color: '#0F172A' },
      tooltip: {
        callbacks: {
          afterLabel: ctx => {
            const val = [fourRuns, sixRuns, runningRuns][ctx.dataIndex];
            const pct = summary.totalRuns > 0 ? ((val / summary.totalRuns) * 100).toFixed(1) : 0;
            return `Runs: ${val} (${pct}% of total runs)`;
          },
        },
      },
    },
    scales: {
      x: { ticks: { color: '#475569', font: { size: 10 } }, grid: { color: 'rgba(15, 23, 42, 0.06)' } },
      y: { ticks: { color: '#475569' }, grid: { color: 'rgba(15, 23, 42, 0.08)' }, beginAtZero: true,
           title: { display: true, text: 'Runs', color: '#334155', font: { size: 11, weight: '600' } } },
    },
  };

  return (
    <div>
      <div style={{ height: 260 }}>
        <Bar data={chartData} options={options} />
      </div>

      <div className="recent-summary-grid" style={{ marginTop: 'var(--space-3)' }}>
        <div className="recent-stat-card">
          <span className="recent-stat-label">Boundary Run %</span>
          <div className="recent-stat-row">
            <span className="recent-stat-val-p1">{summary.boundaryPercentage != null ? `${summary.boundaryPercentage}%` : '—'}</span>
          </div>
          <span style={{ fontSize: '0.72rem', color: '#94A3B8' }}>
            {fourRuns + sixRuns} runs scored from boundaries
          </span>
        </div>
        <div className="recent-stat-card">
          <span className="recent-stat-label">Boundary Ball %</span>
          <div className="recent-stat-row">
            <span className="recent-stat-val-p1">{summary.boundaryBallPercentage != null ? `${summary.boundaryBallPercentage}%` : '—'}</span>
          </div>
          <span style={{ fontSize: '0.72rem', color: '#94A3B8' }}>
            {summary.fours + summary.sixes} boundaries in {summary.ballsFaced} balls faced
          </span>
        </div>
        <div className="recent-stat-card">
          <span className="recent-stat-label">Boundary Count</span>
          <div className="recent-stat-row">
            <span className="recent-stat-val-p1">{summary.fours} fours · {summary.sixes} sixes</span>
          </div>
          <span style={{ fontSize: '0.72rem', color: '#94A3B8' }}>
            Ratio: {summary.sixes > 0 ? (summary.fours / summary.sixes).toFixed(1) : summary.fours} fours per six
          </span>
        </div>
      </div>
      <p className="metric-formula-note">
        Formula: Boundary Run % = (Fours × 4 + Sixes × 6) / Total Runs × 100. Denominator represents total runs scored in participated innings.
      </p>
    </div>
  );
}

function BattingConsistencyCard({ summary, recentSummary, windowN }) {
  return (
    <div style={{ marginTop: 'var(--space-3)' }}>
      <div className="recent-summary-grid">
        <div className="recent-stat-card">
          <span className="recent-stat-label">Recent Window Mean</span>
          <div className="recent-stat-row">
            <span className="recent-stat-val-p1">{recentSummary?.meanRuns != null ? `${recentSummary.meanRuns} runs` : '—'}</span>
          </div>
          <span style={{ fontSize: '0.72rem', color: '#94A3B8' }}>
            Average across last {windowN} innings (total: {recentSummary?.totalRuns ?? 0})
          </span>
        </div>

        <div className="recent-stat-card">
          <span className="recent-stat-label">Scoring Dispersion (σ)</span>
          <div className="recent-stat-row">
            <span className="recent-stat-val-p1">σ = {recentSummary?.stdDevRuns ?? summary?.stdDevRuns ?? '—'}</span>
          </div>
          <span style={{ fontSize: '0.72rem', color: '#94A3B8' }}>
            Sample standard deviation of runs per innings
          </span>
        </div>

        <div className="recent-stat-card">
          <span className="recent-stat-label">Consistency (CV%)</span>
          <div className="recent-stat-row">
            <span className="recent-stat-val-p1">
              {recentSummary?.coefficientOfVariation != null
                ? `${recentSummary.coefficientOfVariation}%`
                : (summary?.coefficientOfVariation != null ? `${summary.coefficientOfVariation}%` : 'N/A')}
            </span>
          </div>
          <span style={{ fontSize: '0.72rem', color: '#10B981', fontWeight: 600 }}>
            Lower CV% = Tighter interval consistency
          </span>
        </div>

        <div className="recent-stat-card">
          <span className="recent-stat-label">Window Peak / Floor</span>
          <div className="recent-stat-row">
            <span className="recent-stat-val-p1">
              {recentSummary?.highestRuns ?? '—'} / {recentSummary?.lowestRuns ?? '—'}
            </span>
          </div>
          <span style={{ fontSize: '0.72rem', color: '#94A3B8' }}>
            High score vs low score in window
          </span>
        </div>

        <div className="recent-stat-card">
          <span className="recent-stat-label">Scoring Momentum Trend</span>
          <div className="recent-stat-row" style={{ marginTop: '4px' }}>
            {trendBadge(recentSummary?.runTrendDirection)}
          </div>
          <span style={{ fontSize: '0.72rem', color: '#94A3B8' }}>
            Linear trajectory across window
          </span>
        </div>
      </div>

      {recentSummary?.sampleSizeWarning && (
        <div className="context-warning-alert" style={{ marginTop: 'var(--space-3)' }}>
          <span>⚠️</span>
          <div>
            <strong>Sample Size Notice:</strong> {recentSummary.sampleSizeWarning} Trends based on fewer than 3 innings carry limited statistical confidence.
          </div>
        </div>
      )}

      <div className="analytics-interpretation-box">
        <span className="analytics-interpretation-icon"></span>
        <div>
          <strong>Consistency vs Volume Distinction:</strong> Scoring volume (total runs and batting average) reflects accumulated output, whereas consistency (Coefficient of Variation: CV% = σ / mean × 100) measures variance around expected scores. A batter can be high-volume yet volatile, or moderate-volume with reliable consistency.
        </div>
      </div>
    </div>
  );
}

// ─── Phase 2: BOWLER Charts & Components ──────────────────────────────────────

function WicketsTrendChart({ data, meanWickets, windowN }) {
  if (!data?.length) return <Empty msg="No recent bowling spells found in this format." />;

  const labels = data.map((d, i) => `${d.date ? d.date.slice(5) : `Spell ${i + 1}`}\nvs ${d.opposition || 'Opp'}`);
  const wkts = data.map(d => d.wickets);
  const avgLine = meanWickets != null ? data.map(() => meanWickets) : [];

  const chartData = {
    labels,
    datasets: [
      {
        label: 'Wickets Taken',
        data: wkts,
        borderColor: '#EF4444',
        backgroundColor: 'rgba(239, 68, 68, 0.15)',
        fill: true,
        tension: 0.3,
        pointRadius: 4,
        pointBackgroundColor: '#EF4444',
        borderWidth: 2,
      },
      ...(meanWickets != null ? [{
        label: `Window Average (${meanWickets.toFixed(2)} wkts/spell)`,
        data: avgLine,
        borderColor: '#FBB924',
        backgroundColor: 'transparent',
        borderDash: [5, 4],
        borderWidth: 2,
        pointRadius: 0,
        fill: false,
      }] : []),
    ],
  };

  const options = {
    responsive: true,
    maintainAspectRatio: false,
    plugins: {
      legend: { labels: { color: '#334155', font: { size: 11 } } },
      title: { display: true, text: `Chronological Wickets per Bowling Spell (Last ${data.length} eligible spells)`, color: '#0F172A' },
      tooltip: {
        callbacks: {
          afterLabel: ctx => {
            if (ctx.datasetIndex !== 0) return null;
            const d = data[ctx.dataIndex];
            return [
              `Opponent: vs ${d.opposition} (${d.format})`,
              `Venue: ${d.venue || '—'}`,
              `Figures: ${d.figures || `${d.wickets}/${d.runsConceded}`}${d.haulBadge ? ` (${d.haulBadge} Haul!)` : ''}`,
              `Overs: ${d.overs} | Economy: ${d.economyRate != null ? d.economyRate.toFixed(2) : '—'}`,
            ];
          },
        },
      },
    },
    scales: {
      x: { ticks: { color: '#475569', maxRotation: 40, font: { size: 10 } }, grid: { color: 'rgba(15, 23, 42, 0.06)' } },
      y: { ticks: { color: '#475569', stepSize: 1 }, grid: { color: 'rgba(15, 23, 42, 0.08)' }, beginAtZero: true,
           title: { display: true, text: 'Wickets Taken', color: '#334155', font: { size: 11, weight: '600' } } },
    },
  };

  return (
    <div style={{ height: 280 }}>
      <Line data={chartData} options={options} />
    </div>
  );
}

function EconomyRateTrendChart({ data, aggEconomy }) {
  if (!data?.length) return null;

  const validEntries = data.filter(d => d.overs > 0 && d.economyRate != null);
  if (!validEntries.length) return <Empty msg="No valid bowling overs recorded for economy trend." />;

  const labels = validEntries.map((d, i) => `${d.date ? d.date.slice(5) : `Spell ${i + 1}`}\nvs ${d.opposition || 'Opp'}`);
  const econValues = validEntries.map(d => +d.economyRate.toFixed(2));
  const aggLine = aggEconomy != null ? validEntries.map(() => aggEconomy) : [];

  const chartData = {
    labels,
    datasets: [
      {
        label: 'Spell Economy Rate',
        data: econValues,
        borderColor: '#10B981',
        backgroundColor: 'rgba(16, 185, 129, 0.15)',
        fill: true,
        tension: 0.3,
        pointRadius: 4,
        pointBackgroundColor: '#10B981',
        borderWidth: 2,
      },
      ...(aggEconomy != null ? [{
        label: `Aggregate Format Economy (${aggEconomy.toFixed(2)})`,
        data: aggLine,
        borderColor: '#F59E0B',
        backgroundColor: 'transparent',
        borderDash: [5, 4],
        borderWidth: 2,
        pointRadius: 0,
        fill: false,
      }] : []),
    ],
  };

  const options = {
    responsive: true,
    maintainAspectRatio: false,
    plugins: {
      legend: { labels: { color: '#334155', font: { size: 11 } } },
      title: { display: true, text: 'Economy Rate Trend (Runs per Decimal Over)', color: '#0F172A' },
      tooltip: {
        callbacks: {
          afterLabel: ctx => {
            if (ctx.datasetIndex !== 0) return null;
            const d = validEntries[ctx.dataIndex];
            return [`Figures: ${d.wickets}/${d.runsConceded} in ${d.overs} overs`, `Economy: ${d.economyRate.toFixed(2)} rpo`];
          },
        },
      },
    },
    scales: {
      x: { ticks: { color: '#475569', maxRotation: 40, font: { size: 10 } }, grid: { color: 'rgba(15, 23, 42, 0.06)' } },
      y: { ticks: { color: '#475569' }, grid: { color: 'rgba(15, 23, 42, 0.08)' }, beginAtZero: true,
           title: { display: true, text: 'Economy Rate (lower is better)', color: '#334155', font: { size: 11, weight: '600' } } },
    },
  };

  return (
    <div style={{ height: 280 }}>
      <Line data={chartData} options={options} />
    </div>
  );
}

function WicketsRunsComparisonChart({ data, windowN }) {
  if (!data?.length) return null;

  const labels = data.map((d, i) => `${d.date ? d.date.slice(5) : `Spell ${i + 1}`}\nvs ${d.opposition || 'Opp'}`);
  const wkts = data.map(d => d.wickets);
  const runs = data.map(d => d.runsConceded);

  const chartData = {
    labels,
    datasets: [
      {
        type: 'bar',
        label: 'Wickets Taken',
        data: wkts,
        backgroundColor: 'rgba(239, 68, 68, 0.8)',
        borderColor: '#EF4444',
        borderWidth: 2,
        borderRadius: 4,
        yAxisID: 'yWickets',
      },
      {
        type: 'line',
        label: 'Runs Conceded',
        data: runs,
        borderColor: '#818CF8',
        backgroundColor: 'rgba(129, 140, 248, 0.1)',
        tension: 0.3,
        pointRadius: 4,
        pointBackgroundColor: '#818CF8',
        borderWidth: 2,
        yAxisID: 'yRuns',
      },
    ],
  };

  const options = {
    responsive: true,
    maintainAspectRatio: false,
    plugins: {
      legend: { labels: { color: '#334155', font: { size: 11 } } },
      title: { display: true, text: `Wickets vs Runs Conceded (Dual-Axis Scale Across Last ${data.length} Spells)`, color: '#0F172A' },
      tooltip: {
        callbacks: {
          afterLabel: ctx => {
            const d = data[ctx.dataIndex];
            return [`Overs: ${d.overs} ov (${d.validBalls || 0} balls)`, `Economy: ${d.economyRate != null ? d.economyRate.toFixed(2) : '—'}`];
          },
        },
      },
    },
    scales: {
      x: { ticks: { color: '#475569', maxRotation: 40, font: { size: 10 } }, grid: { color: 'rgba(15, 23, 42, 0.06)' } },
      yWickets: {
        type: 'linear',
        position: 'left',
        beginAtZero: true,
        ticks: { color: '#EF4444', stepSize: 1 },
        grid: { color: 'rgba(15, 23, 42, 0.08)' },
        title: { display: true, text: 'Wickets Taken (Left)', color: '#EF4444', font: { size: 11 } },
      },
      yRuns: {
        type: 'linear',
        position: 'right',
        beginAtZero: true,
        ticks: { color: '#818CF8' },
        grid: { drawOnChartArea: false },
        title: { display: true, text: 'Runs Conceded (Right)', color: '#818CF8', font: { size: 11 } },
      },
    },
  };

  return (
    <div style={{ height: 280 }}>
      <Bar data={chartData} options={options} />
    </div>
  );
}

function BowlingDistributionChart({ distribution, totalSpells }) {
  if (!distribution) return null;

  const labels = ['0 Wickets', '1 Wicket', '2 Wickets', '3 Wickets', '4+ Wickets (Hauls)'];
  const counts = [
    distribution.zeroWickets,
    distribution.oneWicket,
    distribution.twoWickets,
    distribution.threeWickets,
    distribution.fourPlusWickets,
  ];
  const pcts = [
    distribution.zeroWicketsPct,
    distribution.oneWicketPct,
    distribution.twoWicketsPct,
    distribution.threeWicketsPct,
    distribution.fourPlusWicketsPct,
  ];

  const chartData = {
    labels,
    datasets: [{
      label: 'Spells Count',
      data: counts,
      backgroundColor: [
        'rgba(148, 163, 184, 0.6)',
        'rgba(96, 165, 250, 0.75)',
        'rgba(52, 211, 153, 0.75)',
        'rgba(251, 191, 36, 0.8)',
        'rgba(239, 68, 68, 0.85)',
      ],
      borderColor: [
        '#94A3B8', '#60A5FA', '#34D399', '#FBB924', '#EF4444'
      ],
      borderWidth: 2,
      borderRadius: 6,
    }],
  };

  const options = {
    responsive: true,
    maintainAspectRatio: false,
    plugins: {
      legend: { display: false },
      title: { display: true, text: `Wickets per Spell Frequency (${totalSpells} total bowling spells)`, color: '#0F172A' },
      tooltip: {
        callbacks: {
          afterLabel: ctx => `Share: ${pcts[ctx.dataIndex]?.toFixed(1)}% of all spells`,
        },
      },
    },
    scales: {
      x: { ticks: { color: '#334155', font: { size: 11, weight: '600' } }, grid: { color: 'rgba(15, 23, 42, 0.06)' } },
      y: { ticks: { color: '#475569', stepSize: 1 }, grid: { color: 'rgba(15, 23, 42, 0.08)' }, beginAtZero: true,
           title: { display: true, text: 'Number of Spells', color: '#334155', font: { size: 11, weight: '600' } } },
    },
  };

  return (
    <div>
      <div style={{ height: 260 }}>
        <Bar data={chartData} options={options} />
      </div>
      {distribution.patternInterpretation && (
        <div className="analytics-interpretation-box">
          <span className="analytics-interpretation-icon"></span>
          <div>
            <strong>Bowling Pattern Interpretation:</strong> {distribution.patternInterpretation}
            <p className="metric-formula-note">
              Shows breakdown of wicket volume per bowling appearance. Multi-wicket spells (2W+) measure strike penetration, while 4+ wicket hauls represent decisive match-winning performances.
            </p>
          </div>
        </div>
      )}
    </div>
  );
}

function BowlingConsistencyCard({ summary, recentSummary, windowN }) {
  return (
    <div style={{ marginTop: 'var(--space-3)' }}>
      <div className="recent-summary-grid">
        <div className="recent-stat-card">
          <span className="recent-stat-label">Window Wickets / Spell</span>
          <div className="recent-stat-row">
            <span className="recent-stat-val-p1">{recentSummary?.meanWickets != null ? `${recentSummary.meanWickets} wkts` : '—'}</span>
          </div>
          <span style={{ fontSize: '0.72rem', color: '#94A3B8' }}>
            {recentSummary?.totalWickets ?? 0} wickets across last {windowN} spells
          </span>
        </div>

        <div className="recent-stat-card">
          <span className="recent-stat-label">Recent Economy Rate</span>
          <div className="recent-stat-row">
            <span className="recent-stat-val-p1">{recentSummary?.recentEconomyRate != null ? `${recentSummary.recentEconomyRate} rpo` : '—'}</span>
          </div>
          <span style={{ fontSize: '0.72rem', color: '#94A3B8' }}>
            {recentSummary?.totalRunsConceded ?? 0} runs in {recentSummary?.totalOvers ?? 0} overs
          </span>
        </div>

        <div className="recent-stat-card">
          <span className="recent-stat-label">Bowling Effectiveness</span>
          <div className="recent-stat-row">
            <span className="recent-stat-val-p1">
              Avg: {recentSummary?.recentBowlingAverage != null ? recentSummary.recentBowlingAverage.toFixed(2) : (summary?.bowlingAverage != null ? summary.bowlingAverage.toFixed(2) : 'N/A')}
            </span>
          </div>
          <span style={{ fontSize: '0.72rem', color: '#94A3B8' }}>
            SR: {recentSummary?.recentBowlingStrikeRate != null ? `${recentSummary.recentBowlingStrikeRate.toFixed(1)} b/w` : (summary?.bowlingStrikeRate != null ? `${summary.bowlingStrikeRate.toFixed(1)} b/w` : 'N/A')} (runs & balls per wkt)
          </span>
        </div>

        <div className="recent-stat-card">
          <span className="recent-stat-label">Wickets Consistency (σ)</span>
          <div className="recent-stat-row">
            <span className="recent-stat-val-p1">σ = {recentSummary?.stdDevWickets ?? summary?.stdDevWickets ?? '—'}</span>
          </div>
          <span style={{ fontSize: '0.72rem', color: '#94A3B8' }}>
            Sample std dev across window spells
          </span>
        </div>

        <div className="recent-stat-card">
          <span className="recent-stat-label">Wicket Momentum Trend</span>
          <div className="recent-stat-row" style={{ marginTop: '4px' }}>
            {trendBadge(recentSummary?.wicketTrendDirection)}
          </div>
          <span style={{ fontSize: '0.72rem', color: '#94A3B8' }}>
            Linear trend across window spells
          </span>
        </div>
      </div>

      {recentSummary?.bowlingSampleSizeWarning && (
        <div className="context-warning-alert" style={{ marginTop: 'var(--space-3)' }}>
          <span>⚠️</span>
          <div>
            <strong>Sample Size Notice:</strong> {recentSummary.bowlingSampleSizeWarning} Trends based on fewer than 3 bowling spells carry limited statistical confidence.
          </div>
        </div>
      )}

      <div className="analytics-interpretation-box">
        
        <div>
          <strong>Bowling Metrics Context:</strong> Wicket count alone does not fully represent bowling quality. Economy rate (runs per actual decimal over), bowling average (runs conceded per wicket taken), and strike rate (legal balls bowled per wicket taken) must be interpreted together. Cricket overs are converted accurately (e.g. 4.2 overs represents 26 legal balls, or 4.333 decimal overs). If wickets are zero, average and strike rate are returned as N/A rather than zero.
        </div>
      </div>
    </div>
  );
}

// ─── Format breakdown table ───────────────────────────────────────────────────

function FormatBreakdown({ data, role }) {
  if (!data?.length) return <Empty />;
  return (
    <div className="format-grid">
      {data.map(fmt => (
        <div key={fmt.format} className="format-card"
          style={{ borderLeft: `4px solid ${fmtColor(fmt.format).border}` }}>
          <div className="format-card-header">
            <span className="format-badge" style={{ background: fmtColor(fmt.format).bg, color: '#fff' }}>
              {fmt.format}
            </span>
          </div>
          <div className="format-stats-grid">
            {fmt.battingInnings > 0 && (role !== 'BOWLER') && (
              <>
                <div className="fmt-stat"><span>{fmt.battingInnings}</span><small>Bat Inn</small></div>
                <div className="fmt-stat"><span>{fmt.totalRuns}</span><small>Runs</small></div>
                <div className="fmt-stat"><span>{fmt.runsPerInnings?.toFixed(1)}</span><small>Runs/Inn</small></div>
                <div className="fmt-stat"><span>{fmt.battingStrikeRate?.toFixed(1)}</span><small>Strike Rate</small></div>
                <div className="fmt-stat"><span>{fmt.fifties}</span><small>50s</small></div>
                <div className="fmt-stat"><span>{fmt.hundreds}</span><small>100s</small></div>
              </>
            )}
            {fmt.bowlingInnings > 0 && (role !== 'BATSMAN') && (
              <>
                <div className="fmt-stat bowl"><span>{fmt.bowlingInnings}</span><small>Bowl Inn</small></div>
                <div className="fmt-stat bowl"><span>{fmt.totalWickets}</span><small>Wickets</small></div>
                <div className="fmt-stat bowl">
                  <span>{fmt.bowlingAverage != null ? fmt.bowlingAverage.toFixed(2) : '—'}</span>
                  <small>Bowl Avg</small>
                </div>
                <div className="fmt-stat bowl">
                  <span>{fmt.economyRate != null ? fmt.economyRate.toFixed(2) : '—'}</span>
                  <small>Economy</small>
                </div>
                <div className="fmt-stat bowl"><span>{fmt.fourWickets ?? 0}</span><small>4W Hauls</small></div>
                <div className="fmt-stat bowl"><span>{fmt.fiveWickets ?? 0}</span><small>5W Hauls</small></div>
              </>
            )}
          </div>
        </div>
      ))}
    </div>
  );
}

// ─── Match-by-match tables ───────────────────────────────────────────────────

function BattingMatchTable({ data, windowN }) {
  if (!data?.length) return <Empty msg="No match records for this format." />;
  const displayed = data.slice(-windowN).reverse();

  return (
    <div className="match-table-wrapper">
      <table className="match-table">
        <thead>
          <tr>
            <th>Date</th><th>Format</th><th>vs</th><th>Venue</th>
            <th>Runs</th><th>Balls</th><th>SR</th><th>4s</th><th>6s</th><th>NO</th>
          </tr>
        </thead>
        <tbody>
          {displayed.map((r, i) => (
            <tr key={`bat-row-${i}`}>
              <td>{r.date}</td>
              <td><span className="format-badge-sm" style={{ background: fmtColor(r.format).bg }}>{r.format}</span></td>
              <td>{r.opposition}</td>
              <td title={r.venue}>{r.venue?.length > 22 ? r.venue.slice(0, 20) + '…' : r.venue}</td>
              <td className="score-cell">{r.runs}{r.notOut ? '*' : ''}</td>
              <td>{r.ballsFaced}</td>
              <td>{r.strikeRate?.toFixed(1) ?? '—'}</td>
              <td>{r.fours}</td>
              <td>{r.sixes}</td>
              <td>{r.notOut ? '✓' : ''}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

function BowlingMatchTable({ data, windowN }) {
  if (!data?.length) return <Empty msg="No bowling match records for this format." />;
  const displayed = data.slice(-windowN).reverse();

  return (
    <div className="match-table-wrapper">
      <table className="match-table">
        <thead>
          <tr>
            <th>Date</th><th>Format</th><th>vs</th><th>Venue</th>
            <th>Overs</th><th>Runs</th><th>Wickets</th><th>Figures</th><th>Economy</th>
          </tr>
        </thead>
        <tbody>
          {displayed.map((r, i) => (
            <tr key={`bowl-row-${i}`}>
              <td>{r.date}</td>
              <td><span className="format-badge-sm" style={{ background: fmtColor(r.format).bg }}>{r.format}</span></td>
              <td>{r.opposition}</td>
              <td title={r.venue}>{r.venue?.length > 22 ? r.venue.slice(0, 20) + '…' : r.venue}</td>
              <td>{r.overs}</td>
              <td>{r.runsConceded}</td>
              <td className="score-cell" style={{ color: '#EF4444', fontWeight: 700 }}>{r.wickets}</td>
              <td>
                <strong>{r.figures || `${r.wickets}/${r.runsConceded}`}</strong>
                {r.haulBadge && (
                  <span
                    className="compare-adv-badge"
                    style={{
                      background: r.haulBadge === '5W' ? 'rgba(239, 68, 68, 0.2)' : 'rgba(245, 158, 11, 0.2)',
                      color: r.haulBadge === '5W' ? '#F87171' : '#FCD34D',
                      borderColor: r.haulBadge === '5W' ? '#EF4444' : '#F59E0B',
                      marginLeft: '6px',
                    }}
                  >
                    {r.haulBadge}
                  </span>
                )}
              </td>
              <td>{r.economyRate != null ? r.economyRate.toFixed(2) : (r.economy != null ? r.economy.toFixed(2) : '—')}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

// ─── Venue & Opposition Charts ────────────────────────────────────────────────

function BattingVenueChart({ data }) {
  if (!data?.length) return <Empty msg="No venue data for this format." />;
  const labels = data.map(v => v.venue.length > 28 ? v.venue.slice(0, 26) + '…' : v.venue);
  const chartData = {
    labels,
    datasets: [{ label: 'Runs / Innings', data: data.map(v => v.runsPerInnings),
      backgroundColor: BAT_COLOR.bg, borderColor: BAT_COLOR.border, borderWidth: 2, borderRadius: 4 }],
  };
  const options = {
    responsive: true, maintainAspectRatio: false, indexAxis: 'y',
    plugins: {
      legend: { display: false },
      title: { display: true, text: 'Top Batting Venues (Runs per Innings)', color: '#0F172A' },
      tooltip: { callbacks: { afterLabel: ctx => {
        const v = data[ctx.dataIndex];
        return [`Total runs: ${v.totalRuns}`, `Innings: ${v.innings}`, `50s: ${v.fifties}  100s: ${v.hundreds}`];
      }}},
    },
    scales: {
      x: { ticks: { color: '#475569' }, grid: { color: 'rgba(15, 23, 42, 0.08)' },
           title: { display: true, text: 'Runs per Innings', color: '#334155', font: { size: 11, weight: '600' } } },
      y: { ticks: { color: '#334155', font: { size: 11, weight: '600' } }, grid: { color: 'rgba(15, 23, 42, 0.05)' } },
    },
  };
  return <div style={{ height: Math.max(250, data.length * 38) }}><Bar data={chartData} options={options} /></div>;
}

function BattingOppositionChart({ data }) {
  if (!data?.length) return <Empty msg="No opposition data for this format." />;
  const top = data.slice(0, 10);
  const chartData = {
    labels: top.map(o => o.opposition),
    datasets: [{ label: 'Runs / Innings', data: top.map(o => o.runsPerInnings),
      backgroundColor: 'rgba(139,92,246,0.7)', borderColor: '#8B5CF6', borderWidth: 2, borderRadius: 4 }],
  };
  const options = {
    responsive: true, maintainAspectRatio: false,
    plugins: {
      legend: { display: false },
      title: { display: true, text: 'vs Opposition (Runs per Innings, top 10)', color: '#0F172A' },
      tooltip: { callbacks: { afterLabel: ctx => {
        const o = top[ctx.dataIndex];
        return [`Total: ${o.totalRuns}`, `Inn: ${o.innings}`, `50s: ${o.fifties}  100s: ${o.hundreds}`];
      }}},
    },
    scales: {
      x: { ticks: { color: '#475569', maxRotation: 30, font: { size: 10 } }, grid: { color: 'rgba(15, 23, 42, 0.06)' } },
      y: { ticks: { color: '#475569' }, grid: { color: 'rgba(15, 23, 42, 0.08)' }, beginAtZero: true,
           title: { display: true, text: 'Runs per Innings', color: '#334155', font: { size: 11, weight: '600' } } },
    },
  };
  return <div style={{ height: 280 }}><Bar data={chartData} options={options} /></div>;
}

function BowlingVenueChart({ data }) {
  if (!data?.length) return <Empty msg="No bowling venue data for this format." />;
  const labels = data.map(v => v.venue.length > 28 ? v.venue.slice(0, 26) + '…' : v.venue);
  const chartData = {
    labels,
    datasets: [{ label: 'Economy Rate', data: data.map(v => v.economyRate ?? 0),
      backgroundColor: BOWL_COLOR.bg, borderColor: BOWL_COLOR.border, borderWidth: 2, borderRadius: 4 }],
  };
  const options = {
    responsive: true, maintainAspectRatio: false, indexAxis: 'y',
    plugins: {
      legend: { display: false },
      title: { display: true, text: 'Bowling by Venue (Economy Rate)', color: '#0F172A' },
      tooltip: { callbacks: { afterLabel: ctx => {
        const v = data[ctx.dataIndex];
        return [
          `Wickets: ${v.totalWickets}`, `Runs: ${v.totalRunsConceded}`,
          `Appearances: ${v.appearances}`,
          `Avg: ${v.bowlingAverage != null ? v.bowlingAverage.toFixed(2) : '—'}`,
        ];
      }}},
    },
    scales: {
      x: { ticks: { color: '#475569' }, grid: { color: 'rgba(15, 23, 42, 0.08)' },
           title: { display: true, text: 'Economy Rate (runs/over)', color: '#334155', font: { size: 11, weight: '600' } } },
      y: { ticks: { color: '#334155', font: { size: 11, weight: '600' } }, grid: { color: 'rgba(15, 23, 42, 0.05)' } },
    },
  };
  return <div style={{ height: Math.max(250, data.length * 38) }}><Bar data={chartData} options={options} /></div>;
}

function BowlingOppositionChart({ data }) {
  if (!data?.length) return <Empty msg="No bowling opposition data for this format." />;
  const top = data.slice(0, 10);
  const chartData = {
    labels: top.map(o => o.opposition),
    datasets: [{ label: 'Wickets', data: top.map(o => o.totalWickets),
      backgroundColor: 'rgba(251,191,36,0.7)', borderColor: '#FBB924', borderWidth: 2, borderRadius: 4 }],
  };
  const options = {
    responsive: true, maintainAspectRatio: false,
    plugins: {
      legend: { display: false },
      title: { display: true, text: 'vs Opposition (Wickets, top 10)', color: '#0F172A' },
      tooltip: { callbacks: { afterLabel: ctx => {
        const o = top[ctx.dataIndex];
        return [
          `Appearances: ${o.appearances}`, `Runs conceded: ${o.totalRunsConceded}`,
          `Economy: ${o.economyRate != null ? o.economyRate.toFixed(2) : '—'}`,
          `Avg: ${o.bowlingAverage != null ? o.bowlingAverage.toFixed(2) : '—'}`,
        ];
      }}},
    },
    scales: {
      x: { ticks: { color: '#475569', maxRotation: 30, font: { size: 10 } }, grid: { color: 'rgba(15, 23, 42, 0.06)' } },
      y: { ticks: { color: '#475569', stepSize: 1 }, grid: { color: 'rgba(15, 23, 42, 0.08)' }, beginAtZero: true,
           title: { display: true, text: 'Wickets', color: '#334155', font: { size: 11, weight: '600' } } },
    },
  };
  return <div style={{ height: 280 }}><Bar data={chartData} options={options} /></div>;
}

// ─── Phase: Strengths & Weaknesses Components ─────────────────────────────────

function StrengthsAssessmentBadge({ assessment }) {
  const configs = {
    DOMINANT:         { label: 'Dominant', cls: 'assessment-badge--dominant', icon: '' },
    DOMINANT_CONTROL: { label: 'Dominant Control', cls: 'assessment-badge--dominant', icon: '' },
    FAVOURABLE:       { label: 'Favourable', cls: 'assessment-badge--favourable', icon: '' },
    HIGH_CONTROL:     { label: 'High Control', cls: 'assessment-badge--favourable', icon: '' },
    EFFECTIVE:        { label: 'Effective', cls: 'assessment-badge--effective', icon: '' },
    VULNERABLE:       { label: 'Vulnerable', cls: 'assessment-badge--vulnerable', icon: '⚠️' },
    CHALLENGING:      { label: 'Challenging', cls: 'assessment-badge--challenging', icon: '⚔️' },
    EXPENSIVE:        { label: 'Expensive', cls: 'assessment-badge--expensive', icon: '' },
    NEUTRAL:          { label: 'No Clear Advantage', cls: 'assessment-badge--neutral', icon: '⚖️' },
    LIMITED_DATA:     { label: 'Limited Data (<20b)', cls: 'assessment-badge--limited', icon: 'ℹ️' },
  };
  const cfg = configs[assessment] || configs.NEUTRAL;
  return (
    <span className={`assessment-badge ${cfg.cls}`} title={`Assessment: ${cfg.label}`}>
      <span>{cfg.icon}</span>
      <span>{cfg.label}</span>
    </span>
  );
}

function SampleSizeBadge({ badge, threshold = 20 }) {
  if (badge === 'SUFFICIENT') {
    return (
      <span className="sample-badge sample-badge--sufficient" title={`Statistically valid sample size of ${threshold}+ deliveries`}>
        ✓ Sufficient ({threshold}+b)
      </span>
    );
  }
  return (
    <span className="sample-badge sample-badge--limited" title={`Sample below ${threshold} deliveries. Historical trends may carry high variance.`}>
      ⚠️ Limited (&lt;{threshold}b)
    </span>
  );
}

function PhaseBreakdownChart({ phases, discipline }) {
  if (!phases?.length) return null;

  const phaseOrder = ['POWERPLAY', 'MIDDLE', 'DEATH'];
  const sortedPhases = [...phases].sort((a, b) => phaseOrder.indexOf(a.phase) - phaseOrder.indexOf(b.phase));
  const labels = sortedPhases.map(p => {
    if (p.phase === 'POWERPLAY') return 'Powerplay';
    if (p.phase === 'MIDDLE') return 'Middle Overs';
    if (p.phase === 'DEATH') return 'Death Overs';
    return p.phase;
  });

  const isBat = discipline === 'BATTING';

  const chartData = isBat ? {
    labels,
    datasets: [
      {
        label: 'Strike Rate',
        data: sortedPhases.map(p => p.rateStat ?? 0),
        backgroundColor: 'rgba(129, 140, 248, 0.75)',
        borderColor: '#818CF8',
        borderWidth: 2,
        borderRadius: 4,
        yAxisID: 'y',
      },
      {
        label: 'Boundary % of Runs',
        data: sortedPhases.map(p => p.boundaryPct ?? 0),
        backgroundColor: 'rgba(99, 200, 132, 0.75)',
        borderColor: '#63C884',
        borderWidth: 2,
        borderRadius: 4,
        yAxisID: 'y1',
      },
    ],
  } : {
    labels,
    datasets: [
      {
        label: 'Economy Rate',
        data: sortedPhases.map(p => p.rateStat ?? 0),
        backgroundColor: 'rgba(244, 63, 94, 0.75)',
        borderColor: '#F43F5E',
        borderWidth: 2,
        borderRadius: 4,
        yAxisID: 'y',
      },
      {
        label: 'Dot Ball %',
        data: sortedPhases.map(p => p.dotPct ?? 0),
        backgroundColor: 'rgba(56, 189, 248, 0.75)',
        borderColor: 'var(--color-primary)',
        borderWidth: 2,
        borderRadius: 4,
        yAxisID: 'y1',
      },
    ],
  };

  const options = {
    responsive: true,
    maintainAspectRatio: false,
    plugins: {
      legend: { labels: { color: '#334155', font: { size: 11 } } },
      title: {
        display: true,
        text: isBat ? 'Phase Comparison: Strike Rate & Boundary %' : 'Phase Comparison: Economy Rate & Dot %',
        color: '#0F172A',
      },
      tooltip: {
        callbacks: {
          afterLabel: ctx => {
            const p = sortedPhases[ctx.dataIndex];
            if (isBat) {
              return [
                `Runs: ${p.runs} off ${p.balls} balls`,
                `Boundaries: ${p.fours}×4, ${p.sixes}×6`,
                `Dismissals: ${p.wicketsOrDismissals} (Avg: ${p.avgStat ?? '—'})`,
                `Sample: ${p.sampleSizeBadge === 'SUFFICIENT' ? 'Sufficient (30+b)' : 'Limited (<30b)'}`,
              ];
            } else {
              return [
                `Legal balls: ${p.balls} (${(p.balls / 6).toFixed(1)} ov)`,
                `Runs conceded: ${p.runs} | Wickets: ${p.wicketsOrDismissals}`,
                `Bowling Avg: ${p.avgStat ?? '—'}`,
                `Sample: ${p.sampleSizeBadge === 'SUFFICIENT' ? 'Sufficient (30+b)' : 'Limited (<30b)'}`,
              ];
            }
          },
        },
      },
    },
    scales: {
      x: { ticks: { color: '#475569' }, grid: { color: 'rgba(15, 23, 42, 0.06)' } },
      y: {
        type: 'linear',
        display: true,
        position: 'left',
        ticks: { color: '#475569' },
        grid: { color: 'rgba(15, 23, 42, 0.08)' },
        title: { display: true, text: isBat ? 'Strike Rate' : 'Economy Rate', color: '#334155', font: { size: 11, weight: '600' } },
        beginAtZero: true,
      },
      y1: {
        type: 'linear',
        display: true,
        position: 'right',
        ticks: { color: '#475569' },
        grid: { drawOnChartArea: false },
        title: { display: true, text: isBat ? 'Boundary %' : 'Dot %', color: '#334155', font: { size: 11, weight: '600' } },
        beginAtZero: true,
        max: 100,
      },
    },
  };

  return (
    <div style={{ height: 260 }}>
      <Bar data={chartData} options={options} />
    </div>
  );
}

function BatterMatchupsView({ matchups, baselineSr, format }) {
  const [search, setSearch] = useState('');
  const [filter, setFilter] = useState('ALL'); // ALL, SUFFICIENT, LIMITED
  const [sortBy, setSortBy] = useState('BALLS'); // BALLS, RUNS, SR, DISMISSALS
  const [showAll, setShowAll] = useState(false);

  if (!matchups?.length) {
    return <Empty msg={`No batter head-to-head records found for ${format} format.`} />;
  }

  const total = matchups.length;
  const dominantCount = matchups.filter(m => m.assessment === 'DOMINANT' || m.assessment === 'FAVOURABLE').length;
  const challengingCount = matchups.filter(m => m.assessment === 'VULNERABLE' || m.assessment === 'CHALLENGING').length;
  const neutralCount = matchups.filter(m => m.assessment === 'NEUTRAL').length;
  const limitedCount = matchups.filter(m => m.sampleSizeBadge === 'LIMITED').length;

  const filtered = matchups
    .filter(m => {
      if (search && !m.bowler.toLowerCase().includes(search.toLowerCase())) return false;
      if (filter === 'SUFFICIENT' && m.sampleSizeBadge !== 'SUFFICIENT') return false;
      if (filter === 'LIMITED' && m.sampleSizeBadge !== 'LIMITED') return false;
      return true;
    })
    .sort((a, b) => {
      if (sortBy === 'RUNS') return b.runs - a.runs;
      if (sortBy === 'SR') return b.strikeRate - a.strikeRate;
      if (sortBy === 'DISMISSALS') return b.dismissals - a.dismissals;
      return b.ballsFaced - a.ballsFaced;
    });

  const displayed = showAll ? filtered : filtered.slice(0, 15);

  return (
    <div>
      <div className="matchup-summary-strip">
        <span className="matchup-summary-chip">
          Bowlers Faced: <strong>{total}</strong>
        </span>
        <span className="matchup-summary-chip" style={{ borderColor: 'rgba(52, 211, 153, 0.4)' }}>
          Favourable/Dominant: <strong>{dominantCount}</strong>
        </span>
        <span className="matchup-summary-chip" style={{ borderColor: 'rgba(239, 68, 68, 0.4)' }}>
          ⚠️ Challenging/Vulnerable: <strong>{challengingCount}</strong>
        </span>
        <span className="matchup-summary-chip">
          ⚖️ No Clear Adv.: <strong>{neutralCount}</strong>
        </span>
        <span className="matchup-summary-chip" style={{ borderColor: 'rgba(245, 158, 11, 0.4)' }}>
          ℹ️ Limited Data (&lt;20b): <strong>{limitedCount}</strong>
        </span>
      </div>

      <div className="matchup-controls-row">
        <input
          type="text"
          placeholder="Filter bowler name…"
          className="matchup-search-input"
          value={search}
          onChange={e => setSearch(e.target.value)}
        />
        <div style={{ display: 'flex', alignItems: 'center', gap: '12px', flexWrap: 'wrap' }}>
          <div className="matchup-filter-group">
            <span style={{ fontSize: '0.74rem', color: '#94A3B8' }}>Sample:</span>
            <button
              type="button"
              className={`matchup-filter-btn ${filter === 'ALL' ? 'matchup-filter-btn--active' : ''}`}
              onClick={() => setFilter('ALL')}
            >
              All ({matchups.length})
            </button>
            <button
              type="button"
              className={`matchup-filter-btn ${filter === 'SUFFICIENT' ? 'matchup-filter-btn--active' : ''}`}
              onClick={() => setFilter('SUFFICIENT')}
            >
              Sufficient ({matchups.length - limitedCount})
            </button>
            <button
              type="button"
              className={`matchup-filter-btn ${filter === 'LIMITED' ? 'matchup-filter-btn--active' : ''}`}
              onClick={() => setFilter('LIMITED')}
            >
              Limited ({limitedCount})
            </button>
          </div>
          <div style={{ display: 'flex', alignItems: 'center', gap: '6px' }}>
            <span style={{ fontSize: '0.74rem', color: '#94A3B8' }}>Sort by:</span>
            <select
              value={sortBy}
              onChange={e => setSortBy(e.target.value)}
              style={{
                background: '#ffffff',
                color: 'var(--color-text-primary)',
                border: '1px solid var(--color-border)',
                borderRadius: 'var(--radius-sm)',
                padding: '4px 8px',
                fontSize: '0.76rem',
              }}
            >
              <option value="BALLS">Balls Faced</option>
              <option value="RUNS">Runs Scored</option>
              <option value="SR">Strike Rate</option>
              <option value="DISMISSALS">Dismissals</option>
            </select>
          </div>
        </div>
      </div>

      <div className="matchup-table-wrapper">
        <table className="matchup-table">
          <thead>
            <tr>
              <th>Bowler</th>
              <th>Innings</th>
              <th>Balls</th>
              <th>Runs</th>
              <th>Strike Rate</th>
              <th>4s / 6s</th>
              <th>Dismissals</th>
              <th>Dot %</th>
              <th>Assessment</th>
              <th>Sample Size</th>
            </tr>
          </thead>
          <tbody>
            {displayed.map((m, i) => (
              <tr key={`bat-vs-bowl-${i}`}>
                <td className="matchup-player-cell">
                  <div>{m.bowler}</div>
                  {m.firstDate && m.lastDate && (
                    <div className="matchup-dates">{m.firstDate} → {m.lastDate}</div>
                  )}
                </td>
                <td>{m.innings}</td>
                <td style={{ fontWeight: 600 }}>{m.ballsFaced}</td>
                <td style={{ color: 'var(--color-primary)', fontWeight: 700 }}>{m.runs}</td>
                <td>
                  <strong style={{ color: m.strikeRate >= (baselineSr ?? 100) ? '#15803d' : '#b91c1c' }}>
                    {m.strikeRate.toFixed(1)}
                  </strong>
                  {baselineSr && (
                    <span style={{ fontSize: '0.7rem', color: '#64748B', marginLeft: '4px' }}>
                      ({baselineSr > 0 ? (m.strikeRate >= baselineSr ? `+${(m.strikeRate - baselineSr).toFixed(1)}` : (m.strikeRate - baselineSr).toFixed(1)) : ''})
                    </span>
                  )}
                </td>
                <td>{m.fours} / {m.sixes}</td>
                <td style={{ color: m.dismissals >= 2 ? '#b91c1c' : 'var(--color-text-secondary)', fontWeight: m.dismissals > 0 ? 700 : 400 }}>
                  {m.dismissals}
                </td>
                <td>{m.dotPct}%</td>
                <td><StrengthsAssessmentBadge assessment={m.assessment} /></td>
                <td><SampleSizeBadge badge={m.sampleSizeBadge} threshold={20} /></td>
              </tr>
            ))}
            {displayed.length === 0 && (
              <tr>
                <td colSpan="10" style={{ textAlign: 'center', padding: 'var(--space-4)', color: '#94A3B8' }}>
                  No matchups matching the filter criteria.
                </td>
              </tr>
            )}
          </tbody>
        </table>
      </div>

      {filtered.length > 15 && (
        <div style={{ marginTop: 'var(--space-3)', textAlign: 'center' }}>
          <button
            type="button"
            className="btn btn-secondary"
            style={{ fontSize: '0.8rem', padding: '6px 14px' }}
            onClick={() => setShowAll(!showAll)}
          >
            {showAll ? 'Show Top 15 Only' : `Show All ${filtered.length} Bowlers Faced`}
          </button>
        </div>
      )}
    </div>
  );
}

function BowlerMatchupsView({ matchups, baselineEcon, format }) {
  const [search, setSearch] = useState('');
  const [filter, setFilter] = useState('ALL');
  const [sortBy, setSortBy] = useState('BALLS');
  const [showAll, setShowAll] = useState(false);

  if (!matchups?.length) {
    return <Empty msg={`No bowler head-to-head records found for ${format} format.`} />;
  }

  const total = matchups.length;
  const dominantCount = matchups.filter(m => m.assessment === 'DOMINANT_CONTROL' || m.assessment === 'HIGH_CONTROL').length;
  const expensiveCount = matchups.filter(m => m.assessment === 'EXPENSIVE').length;
  const neutralCount = matchups.filter(m => m.assessment === 'NEUTRAL').length;
  const limitedCount = matchups.filter(m => m.sampleSizeBadge === 'LIMITED').length;

  const filtered = matchups
    .filter(m => {
      if (search && !m.batter.toLowerCase().includes(search.toLowerCase())) return false;
      if (filter === 'SUFFICIENT' && m.sampleSizeBadge !== 'SUFFICIENT') return false;
      if (filter === 'LIMITED' && m.sampleSizeBadge !== 'LIMITED') return false;
      return true;
    })
    .sort((a, b) => {
      if (sortBy === 'WICKETS') return b.wickets - a.wickets;
      if (sortBy === 'ECON') return a.economy - b.economy;
      if (sortBy === 'RUNS') return b.runsConceded - a.runsConceded;
      return b.legalBalls - a.legalBalls;
    });

  const displayed = showAll ? filtered : filtered.slice(0, 15);

  return (
    <div>
      <div className="matchup-summary-strip">
        <span className="matchup-summary-chip">
          Batters Faced: <strong>{total}</strong>
        </span>
        <span className="matchup-summary-chip" style={{ borderColor: 'rgba(52, 211, 153, 0.4)' }}>
          High/Dominant Control: <strong>{dominantCount}</strong>
        </span>
        <span className="matchup-summary-chip" style={{ borderColor: 'rgba(249, 115, 22, 0.4)' }}>
          Expensive: <strong>{expensiveCount}</strong>
        </span>
        <span className="matchup-summary-chip">
          ⚖️ No Clear Adv.: <strong>{neutralCount}</strong>
        </span>
        <span className="matchup-summary-chip" style={{ borderColor: 'rgba(245, 158, 11, 0.4)' }}>
          ℹ️ Limited Data (&lt;20b): <strong>{limitedCount}</strong>
        </span>
      </div>

      <div className="matchup-controls-row">
        <input
          type="text"
          placeholder="Filter batter name…"
          className="matchup-search-input"
          value={search}
          onChange={e => setSearch(e.target.value)}
        />
        <div style={{ display: 'flex', alignItems: 'center', gap: '12px', flexWrap: 'wrap' }}>
          <div className="matchup-filter-group">
            <span style={{ fontSize: '0.74rem', color: '#94A3B8' }}>Sample:</span>
            <button
              type="button"
              className={`matchup-filter-btn ${filter === 'ALL' ? 'matchup-filter-btn--active' : ''}`}
              onClick={() => setFilter('ALL')}
            >
              All ({matchups.length})
            </button>
            <button
              type="button"
              className={`matchup-filter-btn ${filter === 'SUFFICIENT' ? 'matchup-filter-btn--active' : ''}`}
              onClick={() => setFilter('SUFFICIENT')}
            >
              Sufficient ({matchups.length - limitedCount})
            </button>
            <button
              type="button"
              className={`matchup-filter-btn ${filter === 'LIMITED' ? 'matchup-filter-btn--active' : ''}`}
              onClick={() => setFilter('LIMITED')}
            >
              Limited ({limitedCount})
            </button>
          </div>
          <div style={{ display: 'flex', alignItems: 'center', gap: '6px' }}>
            <span style={{ fontSize: '0.74rem', color: '#94A3B8' }}>Sort by:</span>
            <select
              value={sortBy}
              onChange={e => setSortBy(e.target.value)}
              style={{
                background: '#ffffff',
                color: 'var(--color-text-primary)',
                border: '1px solid var(--color-border)',
                borderRadius: 'var(--radius-sm)',
                padding: '4px 8px',
                fontSize: '0.76rem',
              }}
            >
              <option value="BALLS">Legal Balls</option>
              <option value="WICKETS">Wickets</option>
              <option value="ECON">Economy Rate</option>
              <option value="RUNS">Runs Conceded</option>
            </select>
          </div>
        </div>
      </div>

      <div className="matchup-table-wrapper">
        <table className="matchup-table">
          <thead>
            <tr>
              <th>Batter</th>
              <th>Innings</th>
              <th>Legal Balls</th>
              <th>Runs Conceded</th>
              <th>Wickets</th>
              <th>Economy Rate</th>
              <th>Bowling Avg</th>
              <th>Dot %</th>
              <th>Boundaries Conceded</th>
              <th>Assessment</th>
              <th>Sample Size</th>
            </tr>
          </thead>
          <tbody>
            {displayed.map((m, i) => (
              <tr key={`bowl-vs-bat-${i}`}>
                <td className="matchup-player-cell">
                  <div>{m.batter}</div>
                  {m.firstDate && m.lastDate && (
                    <div className="matchup-dates">{m.firstDate} → {m.lastDate}</div>
                  )}
                </td>
                <td>{m.innings}</td>
                <td style={{ fontWeight: 600 }}>{m.legalBalls} ({(m.legalBalls / 6).toFixed(1)} ov)</td>
                <td>{m.runsConceded}</td>
                <td style={{ color: '#EF4444', fontWeight: 700 }}>{m.wickets}</td>
                <td>
                  <strong style={{ color: m.economy <= (baselineEcon ?? 6.0) ? '#15803d' : '#b91c1c' }}>
                    {m.economy.toFixed(2)}
                  </strong>
                  {baselineEcon && (
                    <span style={{ fontSize: '0.7rem', color: '#64748B', marginLeft: '4px' }}>
                      ({m.economy <= baselineEcon ? (m.economy - baselineEcon).toFixed(2) : `+${(m.economy - baselineEcon).toFixed(2)}`})
                    </span>
                  )}
                </td>
                <td>{m.bowlingAverage != null ? m.bowlingAverage.toFixed(2) : 'N/A'}</td>
                <td>{m.dotPct}%</td>
                <td>{m.boundariesConceded}</td>
                <td><StrengthsAssessmentBadge assessment={m.assessment} /></td>
                <td><SampleSizeBadge badge={m.sampleSizeBadge} threshold={20} /></td>
              </tr>
            ))}
            {displayed.length === 0 && (
              <tr>
                <td colSpan="11" style={{ textAlign: 'center', padding: 'var(--space-4)', color: '#94A3B8' }}>
                  No matchups matching the filter criteria.
                </td>
              </tr>
            )}
          </tbody>
        </table>
      </div>

      {filtered.length > 15 && (
        <div style={{ marginTop: 'var(--space-3)', textAlign: 'center' }}>
          <button
            type="button"
            className="btn btn-secondary"
            style={{ fontSize: '0.8rem', padding: '6px 14px' }}
            onClick={() => setShowAll(!showAll)}
          >
            {showAll ? 'Show Top 15 Only' : `Show All ${filtered.length} Batters Faced`}
          </button>
        </div>
      )}
    </div>
  );
}

function PhasePerformanceView({ battingPhases, bowlingPhases, format, phaseNotice }) {
  if (format === 'TEST') {
    return (
      <div className="phase-notice-card">
        <span className="phase-notice-icon">ℹ️</span>
        <div>
          <strong>Match-Phase Analysis Unavailable for Test Matches:</strong>
          <p style={{ margin: '4px 0 0 0', color: '#94A3B8' }}>
            {phaseNotice || 'Match-phase segmentation (Powerplay / Middle / Death overs) is only applicable to limited-overs cricket (ODI and T20I). Test matches do not feature field-restriction powerplays or overs-based phases.'}
          </p>
        </div>
      </div>
    );
  }

  const phaseDefinitions = {
    T20I: { POWERPLAY: 'Overs 0–5', MIDDLE: 'Overs 6–14', DEATH: 'Overs 15–19' },
    ODI:  { POWERPLAY: 'Overs 0–9', MIDDLE: 'Overs 10–39', DEATH: 'Overs 40–49' },
    ALL:  { POWERPLAY: 'Overs 0–5 (T20I) / 0–9 (ODI)', MIDDLE: 'Overs 6–14 / 10–39', DEATH: 'Overs 15–19 / 40–49' },
  };
  const defs = phaseDefinitions[format] || phaseDefinitions.ALL;

  const phaseTitles = {
    POWERPLAY: { label: 'Powerplay', icon: '' },
    MIDDLE:    { label: 'Middle Overs', icon: '⏱️' },
    DEATH:     { label: 'Death Overs', icon: '' },
  };

  const hasBat = battingPhases && battingPhases.length > 0;
  const hasBowl = bowlingPhases && bowlingPhases.length > 0;

  if (!hasBat && !hasBowl) {
    return <Empty msg={`No match-phase records found for ${format} format.`} />;
  }

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 'var(--space-6)' }}>
      {hasBat && (
        <div>
          <h4 style={{ color: '#818CF8', margin: '0 0 var(--space-3) 0', fontSize: '0.95rem', display: 'flex', alignItems: 'center', gap: '8px' }}>
             Batting Phase Performance
          </h4>
          <div className="phase-grid">
            {['POWERPLAY', 'MIDDLE', 'DEATH'].map(pKey => {
              const p = battingPhases.find(item => item.phase === pKey);
              const meta = phaseTitles[pKey];
              const range = defs[pKey] || '';
              if (!p) {
                return (
                  <div key={pKey} className="phase-card" style={{ opacity: 0.6 }}>
                    <div className="phase-card-header">
                      <span className="phase-title-tag">{meta.icon} {meta.label}</span>
                      <span className="phase-over-range">{range}</span>
                    </div>
                    <Empty msg="No deliveries recorded in this phase." />
                  </div>
                );
              }
              return (
                <div key={pKey} className="phase-card">
                  <div className="phase-card-header">
                    <span className="phase-title-tag">{meta.icon} {meta.label}</span>
                    <span className="phase-over-range">{range}</span>
                  </div>

                  <div className="phase-metrics-grid">
                    <div className="phase-metric-box">
                      <span className="phase-metric-val phase-metric-val--highlight">{p.rateStat.toFixed(1)}</span>
                      <span className="phase-metric-label">Strike Rate</span>
                    </div>
                    <div className="phase-metric-box">
                      <span className="phase-metric-val">{p.runs}</span>
                      <span className="phase-metric-label">Runs ({p.balls}b)</span>
                    </div>
                    <div className="phase-metric-box">
                      <span className="phase-metric-val">{p.wicketsOrDismissals}</span>
                      <span className="phase-metric-label">Dismissals</span>
                    </div>
                  </div>

                  <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: '0.76rem', color: '#94A3B8', borderTop: '1px solid rgba(15, 23, 42, 0.06)', paddingTop: '6px' }}>
                    <span>Boundaries: <strong style={{ color: 'var(--color-text-primary)' }}>{p.fours}×4, {p.sixes}×6 ({p.boundaryPct}%)</strong></span>
                    <span>Dot Balls: <strong style={{ color: 'var(--color-text-primary)' }}>{p.dotPct}%</strong></span>
                  </div>

                  <div style={{ marginTop: 'auto', display: 'flex', justifyContent: 'flex-end' }}>
                    <SampleSizeBadge badge={p.sampleSizeBadge} threshold={30} />
                  </div>
                </div>
              );
            })}
          </div>

          <div style={{ background: 'rgba(255,255,255,0.02)', padding: 'var(--space-4)', borderRadius: 'var(--radius-lg)', border: '1px solid var(--color-border)' }}>
            <PhaseBreakdownChart phases={battingPhases} discipline="BATTING" />
          </div>
        </div>
      )}

      {hasBowl && (
        <div>
          <h4 style={{ color: '#F43F5E', margin: '0 0 var(--space-3) 0', fontSize: '0.95rem', display: 'flex', alignItems: 'center', gap: '8px' }}>
             Bowling Phase Performance
          </h4>
          <div className="phase-grid">
            {['POWERPLAY', 'MIDDLE', 'DEATH'].map(pKey => {
              const p = bowlingPhases.find(item => item.phase === pKey);
              const meta = phaseTitles[pKey];
              const range = defs[pKey] || '';
              if (!p) {
                return (
                  <div key={pKey} className="phase-card" style={{ opacity: 0.6 }}>
                    <div className="phase-card-header">
                      <span className="phase-title-tag">{meta.icon} {meta.label}</span>
                      <span className="phase-over-range">{range}</span>
                    </div>
                    <Empty msg="No deliveries recorded in this phase." />
                  </div>
                );
              }
              return (
                <div key={pKey} className="phase-card">
                  <div className="phase-card-header">
                    <span className="phase-title-tag">{meta.icon} {meta.label}</span>
                    <span className="phase-over-range">{range}</span>
                  </div>

                  <div className="phase-metrics-grid">
                    <div className="phase-metric-box">
                      <span className="phase-metric-val phase-metric-val--bowl">{p.rateStat.toFixed(2)}</span>
                      <span className="phase-metric-label">Economy Rate</span>
                    </div>
                    <div className="phase-metric-box">
                      <span className="phase-metric-val">{p.wicketsOrDismissals}</span>
                      <span className="phase-metric-label">Wickets</span>
                    </div>
                    <div className="phase-metric-box">
                      <span className="phase-metric-val">{p.runs}</span>
                      <span className="phase-metric-label">Runs ({p.balls}b)</span>
                    </div>
                  </div>

                  <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: '0.76rem', color: '#94A3B8', borderTop: '1px solid rgba(15, 23, 42, 0.06)', paddingTop: '6px' }}>
                    <span>Bowling Avg: <strong style={{ color: 'var(--color-text-primary)' }}>{p.avgStat != null ? p.avgStat.toFixed(2) : 'N/A'}</strong></span>
                    <span>Dot Balls: <strong style={{ color: 'var(--color-text-primary)' }}>{p.dotPct}%</strong></span>
                  </div>

                  <div style={{ marginTop: 'auto', display: 'flex', justifyContent: 'flex-end' }}>
                    <SampleSizeBadge badge={p.sampleSizeBadge} threshold={30} />
                  </div>
                </div>
              );
            })}
          </div>

          <div style={{ background: 'rgba(255,255,255,0.02)', padding: 'var(--space-4)', borderRadius: 'var(--radius-lg)', border: '1px solid var(--color-border)' }}>
            <PhaseBreakdownChart phases={bowlingPhases} discipline="BOWLING" />
          </div>
        </div>
      )}
    </div>
  );
}

function StrengthsAndWeaknessesSection({ strengths, loading, error, selectedFormat, role }) {
  const hasBatMatchups = strengths?.hasBattingMatchups;
  const hasBowlMatchups = strengths?.hasBowlingMatchups;
  const hasPhases = strengths?.hasPhaseData || selectedFormat === 'TEST';

  const defaultTab = (role === 'BOWLER' && hasBowlMatchups)
    ? 'BOWLER_MATCHUPS'
    : (hasBatMatchups ? 'BATTER_MATCHUPS' : (hasBowlMatchups ? 'BOWLER_MATCHUPS' : 'PHASES'));

  const [selectedTab, setSelectedTab] = useState(null);
  const activeTab = selectedTab ?? defaultTab;

  return (
    <Section
      id="section-strengths-weaknesses"
      title="Player Strengths & Weaknesses"
      icon="⚔️"
      subtitle="Data-driven head-to-head bowler/batter matchup rates and limited-overs match-phase analysis."
    >
      <div className="analytics-interpretation-box" style={{ borderColor: 'rgba(56, 189, 248, 0.3)', background: 'rgba(56, 189, 248, 0.05)', marginBottom: 'var(--space-4)' }}>
        <span className="analytics-interpretation-icon">⚖️</span>
        <div>
          <strong>Empirical Evaluation & Minimum Sample Rules:</strong>
          <p style={{ margin: '3px 0 0 0', fontSize: '0.8rem', color: '#94A3B8' }}>
            Matchups with fewer than <strong>20 deliveries</strong> and match phases with fewer than <strong>30 deliveries</strong> are classified as <em>Limited Data</em> to prevent over-interpreting small samples. Assessments reflect comparative strike rate, economy, and dismissals against format baselines. Notice: Pace vs spin classification is deferred until verified bowling-style metadata is integrated into Cricsheet.
          </p>
        </div>
      </div>

      {loading && (
        <div style={{ textAlign: 'center', padding: 'var(--space-6)', color: '#94A3B8' }}>
          <span className="spinner spinner--sm spinner--light" style={{ marginRight: '8px' }} />
          Loading Strengths & Weaknesses data…
        </div>
      )}

      {error && (
        <div className="analytics-error" style={{ marginBottom: 'var(--space-4)' }}>
          <span>⚠️</span> {error}
        </div>
      )}

      {!loading && strengths && (
        <div className="strengths-container">
          <div className="strengths-nav-tabs">
            {hasBatMatchups && (
              <button
                type="button"
                className={`strengths-nav-tab ${activeTab === 'BATTER_MATCHUPS' ? 'strengths-nav-tab--active' : ''}`}
                onClick={() => setSelectedTab('BATTER_MATCHUPS')}
              >
                 Batter Matchups ({strengths.totalBowlersFaced} Bowlers)
              </button>
            )}
            {hasBowlMatchups && (
              <button
                type="button"
                className={`strengths-nav-tab ${activeTab === 'BOWLER_MATCHUPS' ? 'strengths-nav-tab--active' : ''}`}
                onClick={() => setSelectedTab('BOWLER_MATCHUPS')}
              >
                 Bowler Matchups ({strengths.totalBattersFaced} Batters)
              </button>
            )}
            {hasPhases && (
              <button
                type="button"
                className={`strengths-nav-tab ${activeTab === 'PHASES' ? 'strengths-nav-tab--active' : ''}`}
                onClick={() => setSelectedTab('PHASES')}
              >
                ⏱️ Match-Phase Performance {selectedFormat === 'TEST' ? '(Test Notice)' : ''}
              </button>
            )}
          </div>

          {activeTab === 'BATTER_MATCHUPS' && hasBatMatchups && (
            <BatterMatchupsView
              matchups={strengths.batterMatchups}
              baselineSr={strengths.overallFormatStrikeRate}
              format={selectedFormat}
            />
          )}

          {activeTab === 'BOWLER_MATCHUPS' && hasBowlMatchups && (
            <BowlerMatchupsView
              matchups={strengths.bowlerMatchups}
              baselineEcon={strengths.overallFormatEconomy}
              format={selectedFormat}
            />
          )}

          {activeTab === 'PHASES' && (
            <PhasePerformanceView
              battingPhases={strengths.battingPhases}
              bowlingPhases={strengths.bowlingPhases}
              format={selectedFormat}
              phaseNotice={strengths.phaseNotice}
            />
          )}
        </div>
      )}

      {!loading && !strengths && !error && (
        <Empty msg="No head-to-head matchup or phase records available for this player." />
      )}
    </Section>
  );
}

// ─── Main Page Component ──────────────────────────────────────────────────────

export default function PlayerAnalytics() {
  const [searchInput, setSearchInput]   = useState('');
  const [suggestions, setSuggestions]   = useState([]);
  const [allPlayers, setAllPlayers]     = useState([]);
  const [analytics, setAnalytics]       = useState(null);
  const [strengths, setStrengths]       = useState(null);
  const [loading, setLoading]           = useState(false);
  const [strengthsLoading, setStrengthsLoading] = useState(false);
  const [error, setError]               = useState('');
  const [strengthsError, setStrengthsError]     = useState('');
  const [showDropdown, setShowDropdown] = useState(false);
  const [selectedFormat, setSelectedFormat] = useState('ALL');
  const [selectedRecentN, setSelectedRecentN] = useState(10);
  const [exporting, setExporting] = useState(false);
  const [exportError, setExportError] = useState(null);
  const [exportSuccessMsg, setExportSuccessMsg] = useState(null);
  const inputRef = useRef(null);

  // Load autocomplete list
  useEffect(() => {
    listAnalyticsPlayers()
      .then(res => setAllPlayers(res.data || []))
      .catch(() => setAllPlayers([]));
  }, []);

  // Autocomplete filtering
  useEffect(() => {
    const trimmed = searchInput.trim().toLowerCase();
    if (trimmed.length < 2) { setSuggestions([]); return; }
    const tokens = trimmed.split(/\s+/);
    const lastToken = tokens[tokens.length - 1];
    setSuggestions(
      allPlayers.filter(p => {
        const pLower = p.toLowerCase();
        if (pLower.includes(trimmed)) return true;
        if (tokens.length > 1 && lastToken.length >= 2 && pLower.includes(lastToken)) return true;
        return false;
      }).slice(0, 8)
    );
  }, [searchInput, allPlayers]);

  // Fetch analytics & strengths
  const fetchAnalytics = useCallback(async (name, fmt, recentN) => {
    const q = (name || searchInput).trim();
    if (!q) return;
    setLoading(true);
    setStrengthsLoading(true);
    setError('');
    setStrengthsError('');
    setShowDropdown(false);
    try {
      const [res, strengthsRes] = await Promise.all([
        getPlayerAnalytics(q, recentN || selectedRecentN, fmt || selectedFormat),
        getPlayerStrengths(q, fmt || selectedFormat).catch(err => {
          console.warn('Player Strengths fetch warning:', err);
          return null;
        }),
      ]);
      setAnalytics(res.data);
      if (strengthsRes && strengthsRes.data) {
        setStrengths(strengthsRes.data);
      } else {
        setStrengths(null);
      }
    } catch (err) {
      if (err.response?.status === 404) {
        setError(`No historical data found for "${q}". Try another name.`);
      } else if (err.response?.data?.error) {
        setError(err.response.data.error);
      } else {
        setError('Failed to load analytics. Please ensure the backend is running.');
      }
    } finally {
      setLoading(false);
      setStrengthsLoading(false);
    }
  }, [searchInput, selectedFormat, selectedRecentN]);

  const handleSearch = useCallback((name) => {
    fetchAnalytics(name, selectedFormat, selectedRecentN);
  }, [fetchAnalytics, selectedFormat, selectedRecentN]);

  const handleFormatChange = useCallback((fmt) => {
    setSelectedFormat(fmt);
    if (analytics?.playerName) {
      fetchAnalytics(analytics.playerName, fmt, selectedRecentN);
    }
  }, [analytics, fetchAnalytics, selectedRecentN]);

  const handleRecentNChange = useCallback((n) => {
    setSelectedRecentN(n);
    if (analytics?.playerName) {
      fetchAnalytics(analytics.playerName, selectedFormat, n);
    }
  }, [analytics, fetchAnalytics, selectedFormat]);

  const handleExportExcel = async () => {
    if (!analytics) return;
    setExporting(true);
    setExportError(null);
    setExportSuccessMsg(null);
    try {
      // 1. Primary: Server-generated native Excel with DrawingML charts
      const response = await exportPlayerAnalysisExcel(
        analytics.playerName,
        selectedFormat,
        selectedRecentN
      );
      const disposition = response.headers?.['content-disposition'];
      let filename = `CricketAI_Player_Analysis_${analytics.playerName.replace(/[^a-zA-Z0-9_-]/g, '_')}_${selectedFormat}.xlsx`;
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
      console.warn('Native chart server export failed, falling back to client-side generator:', err);
      try {
        const fallbackFilename = await exportPlayerAnalysisToExcel({
          analytics,
          selectedFormat,
          selectedRecentN,
        });
        setExportSuccessMsg(`Exported: ${fallbackFilename}`);
        setTimeout(() => setExportSuccessMsg(null), 5000);
      } catch (fallbackErr) {
        console.error('Failed to export Player Analysis to Excel:', fallbackErr);
        setExportError(fallbackErr.message || 'Failed to generate Excel report. Please try again.');
        setTimeout(() => setExportError(null), 6000);
      }
    } finally {
      setExporting(false);
    }
  };

  const handleSuggestionClick = (name) => {
    setSearchInput(name);
    setShowDropdown(false);
    fetchAnalytics(name, selectedFormat, selectedRecentN);
  };

  // Role detection and disciplines
  const role = analytics?.role ?? 'UNKNOWN';
  const hasBattingData = (analytics?.recentBattingForm?.length > 0) || (analytics?.battingSummary != null);
  const hasBowlingData = (analytics?.recentBowlingForm?.length > 0) || (analytics?.bowlingSummary != null);

  const isBat = role === 'BATSMAN' || role === 'ALL_ROUNDER' || (role === 'UNKNOWN' && hasBattingData);
  const isBowl = role === 'BOWLER' || role === 'ALL_ROUNDER' || (role === 'UNKNOWN' && hasBowlingData && !hasBattingData);
  const isAllRounder = role === 'ALL_ROUNDER';

  // Sliced recent form data based on current recentN window
  const recentBatWindow = useMemo(() => {
    if (!analytics?.recentBattingForm?.length) return [];
    return analytics.recentBattingForm.slice(-selectedRecentN);
  }, [analytics?.recentBattingForm, selectedRecentN]);

  const recentBowlWindow = useMemo(() => {
    if (!analytics?.recentBowlingForm?.length) return [];
    return analytics.recentBowlingForm.slice(-selectedRecentN);
  }, [analytics?.recentBowlingForm, selectedRecentN]);

  const batSummary = analytics?.battingSummary;
  const bowlSummary = analytics?.bowlingSummary;
  const scoreDist = analytics?.scoreDistribution;
  const bowlDist = analytics?.bowlingDistribution;
  const recentSummary = analytics?.recentFormSummary;

  return (
    <DashboardLayout pageTitle="Player Analytics">
      <div className="analytics-page">
      {/* ─────────────────────────────────────────────────────────────
          SECTION 1: Header & Player Overview
         ───────────────────────────────────────────────────────────── */}
      <div className="analytics-header">
        <div className="analytics-header-text">
          <h1 className="analytics-title">
            <span className="analytics-title-icon"></span>
            Player Analytics
          </h1>
          <p className="analytics-subtitle">
            Historical cricket performance analytics, role-aware trendlines, scoring patterns, and match breakdowns.
          </p>
        </div>
      </div>

      {/* Search Bar */}
      <div className="analytics-search-wrapper">
        <div className="analytics-search-box" ref={inputRef}>
          
          <input
            id="analytics-player-search"
            type="text"
            className="analytics-search-input"
            placeholder="Search player (e.g. V Kohli, AU Rashid, CV Varun, RL Chase)…"
            value={searchInput}
            onChange={e => { setSearchInput(e.target.value); setShowDropdown(true); }}
            onKeyDown={e => { if (e.key === 'Enter') handleSearch(); }}
            onFocus={() => setShowDropdown(true)}
            autoComplete="off"
          />
          <button
            id="analytics-search-btn"
            className="btn btn-primary analytics-search-btn"
            onClick={() => handleSearch()}
            disabled={loading}
          >
            {loading ? 'Analyzing…' : 'Analyse'}
          </button>
        </div>

        {/* Suggestions dropdown */}
        {showDropdown && suggestions.length > 0 && (
          <ul className="analytics-suggestions" role="listbox">
            {suggestions.map((p, i) => (
              <li key={i} className="analytics-suggestion-item" role="option"
                onClick={() => handleSuggestionClick(p)}>
                 {p}
              </li>
            ))}
          </ul>
        )}
      </div>

      {/* Error state */}
      {error && <div className="analytics-error"><span>⚠️</span> {error}</div>}

      {/* Main Results Container */}
      {analytics && (
        <div className="analytics-results" id="analytics-results">

          {/* Player Identity Header Card */}
          <div className="analytics-player-header">
            <div className="analytics-player-avatar">
              {analytics.playerName?.charAt(0).toUpperCase()}
            </div>
            <div style={{ flex: 1 }}>
              <h2 className="analytics-player-name">{analytics.playerName}</h2>
              <p className="analytics-player-meta">
                {analytics.totalBattingInnings} batting innings · {analytics.totalBowlingInnings} bowling innings
                &nbsp;·&nbsp;
                <span className={`role-badge role-badge--${role.toLowerCase().replace('_', '-')}`}>
                  {{ BATSMAN: ' Batsman', BOWLER: ' Bowler',
                     ALL_ROUNDER: ' All-Rounder', UNKNOWN: '❓ Unknown' }[role]}
                </span>
                {isAllRounder && <span style={{ marginLeft: '8px', color: '#818CF8', fontSize: '0.78rem' }}>Dual-facet analytics active</span>}
              </p>
            </div>
            <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'flex-end', gap: '6px' }}>
              <button
                id="export-player-excel-btn"
                type="button"
                className="btn-excel-export"
                onClick={handleExportExcel}
                disabled={exporting}
                title="Download full player analysis report as genuine Excel (.xlsx)"
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
              {exportSuccessMsg && (
                <span style={{ fontSize: '0.75rem', color: '#10B981', fontWeight: 500 }}>
                  ✓ {exportSuccessMsg}
                </span>
              )}
              {exportError && (
                <span style={{ fontSize: '0.75rem', color: '#EF4444', fontWeight: 500 }}>
                  ⚠️ {exportError}
                </span>
              )}
            </div>
          </div>

          {/* Format Tabs Bar */}
          <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', flexWrap: 'wrap', gap: '12px' }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
              <span style={{ fontSize: '0.82rem', color: '#94A3B8', fontWeight: 600 }}>Format Filter:</span>
              <FormatTabs active={selectedFormat} onChange={handleFormatChange} />
            </div>
          </div>

          {/* Unknown Role Notice */}
          {role === 'UNKNOWN' && (
            <div className="analytics-interpretation-box" style={{ borderColor: 'rgba(245, 158, 11, 0.4)', background: 'rgba(245, 158, 11, 0.08)' }}>
              <span className="analytics-interpretation-icon">❓</span>
              <div>
                <strong>Role Unspecified:</strong> Player role could not be definitively classified into Batsman or Bowler. Analytics are displayed based strictly on available historical records ({hasBattingData ? 'Batting' : ''}{hasBattingData && hasBowlingData ? ' & ' : ''}{hasBowlingData ? 'Bowling' : ''}). No statistics are fabricated.
              </div>
            </div>
          )}

          {/* High-Level Overview KPI Cards (Format-specific) */}
          <div className="stat-cards-row">
            {isBat && batSummary && (
              <>
                <StatCard label="Innings Batted" value={batSummary.innings} accent="#6C63FF" />
                <StatCard label="Total Runs" value={batSummary.totalRuns} accent="#63C884" />
                <StatCard label="Runs / Innings" value={batSummary.runsPerInnings != null ? batSummary.runsPerInnings.toFixed(1) : '—'} accent="#818CF8" />
                <StatCard label="Strike Rate" value={batSummary.strikeRate != null ? batSummary.strikeRate.toFixed(1) : '—'} accent="var(--color-primary)" />
                <StatCard label="50s / 100s" value={`${batSummary.fifties} / ${batSummary.hundreds}`} accent="#FBB924" />
              </>
            )}
            {isBowl && bowlSummary && (
              <>
                <StatCard label="Bowling Spells" value={bowlSummary.innings} accent="#EF4444" />
                <StatCard label="Total Wickets" value={bowlSummary.totalWickets} accent="#F43F5E" />
                <StatCard label="Economy Rate" value={bowlSummary.economyRate != null ? bowlSummary.economyRate.toFixed(2) : '—'} accent="#10B981" />
                <StatCard label="Bowling Average" value={bowlSummary.bowlingAverage != null ? bowlSummary.bowlingAverage.toFixed(2) : 'N/A'} accent="#F59E0B" />
                <StatCard label="4W / 5W Hauls" value={`${bowlSummary.fourWickets} / ${bowlSummary.fiveWickets}`} accent="#A855F7" />
              </>
            )}
          </div>

          {/* ─────────────────────────────────────────────────────────────
              SECTION 2: Role-Specific Performance Trends
             ───────────────────────────────────────────────────────────── */}
          <Section
            id="section-trends"
            title="Performance Trends"
            icon=""
            subtitle="Chronological match-by-match trajectory with reference baselines."
          >
            {/* Global Recent Window Selector */}
            <div className="chart-header-row" style={{ marginBottom: 'var(--space-4)' }}>
              <WindowTabs active={selectedRecentN} onChange={handleRecentNChange} isBowling={isBowl && !isBat} />
              <div style={{ display: 'flex', gap: '12px', alignItems: 'center' }}>
                {isBat && recentSummary?.runTrendDirection && (
                  <span style={{ fontSize: '0.8rem', color: '#94A3B8' }}>
                    Batting: {trendBadge(recentSummary.runTrendDirection)}
                  </span>
                )}
                {isBowl && recentSummary?.wicketTrendDirection && (
                  <span style={{ fontSize: '0.8rem', color: '#94A3B8' }}>
                    Bowling: {trendBadge(recentSummary.wicketTrendDirection)}
                  </span>
                )}
              </div>
            </div>

            {/* BATSMAN / ALL-ROUNDER Batting Trends */}
            {isBat && (
              <div style={{ marginBottom: isAllRounder ? 'var(--space-6)' : 0 }}>
                {isAllRounder && (
                  <div className="discipline-facet-banner">
                    <h4 className="discipline-facet-title"> Batting Performance Trends</h4>
                    <span style={{ fontSize: '0.78rem', color: '#818CF8' }}>Window: Last {selectedRecentN} Innings</span>
                  </div>
                )}

                {recentBatWindow.length > 0 ? (
                  <div className="analytics-grid-2col">
                    <div style={{ background: 'rgba(255,255,255,0.02)', padding: 'var(--space-4)', borderRadius: 'var(--radius-lg)', border: '1px solid var(--color-border)' }}>
                      <RunsPerInningsTrendChart
                        data={recentBatWindow}
                        meanRuns={recentSummary?.meanRuns}
                        windowN={selectedRecentN}
                      />
                    </div>
                    <div style={{ background: 'rgba(255,255,255,0.02)', padding: 'var(--space-4)', borderRadius: 'var(--radius-lg)', border: '1px solid var(--color-border)' }}>
                      <BattingStrikeRateTrendChart
                        data={recentBatWindow}
                        aggStrikeRate={batSummary?.strikeRate}
                      />
                    </div>
                  </div>
                ) : (
                  <Empty msg={`No batting innings available for ${analytics.playerName} in ${selectedFormat} format.`} />
                )}
              </div>
            )}

            {/* BOWLER / ALL-ROUNDER Bowling Trends */}
            {isBowl && (
              <div style={{ marginTop: isAllRounder ? 'var(--space-6)' : 0 }}>
                {isAllRounder && (
                  <div className="discipline-facet-banner discipline-facet-banner--bowl">
                    <h4 className="discipline-facet-title" style={{ color: '#FECDD3' }}> Bowling Performance Trends</h4>
                    <span style={{ fontSize: '0.78rem', color: '#b91c1c', fontWeight: 600 }}>Window: Last {selectedRecentN} Spells</span>
                  </div>
                )}

                {recentBowlWindow.length > 0 ? (
                  <>
                    <div className="analytics-grid-2col">
                      <div style={{ background: 'rgba(255,255,255,0.02)', padding: 'var(--space-4)', borderRadius: 'var(--radius-lg)', border: '1px solid var(--color-border)' }}>
                        <WicketsTrendChart
                          data={recentBowlWindow}
                          meanWickets={recentSummary?.meanWickets}
                          windowN={selectedRecentN}
                        />
                      </div>
                      <div style={{ background: 'rgba(255,255,255,0.02)', padding: 'var(--space-4)', borderRadius: 'var(--radius-lg)', border: '1px solid var(--color-border)' }}>
                        <EconomyRateTrendChart
                          data={recentBowlWindow}
                          aggEconomy={bowlSummary?.economyRate}
                        />
                      </div>
                    </div>
                    <div style={{ marginTop: 'var(--space-4)', background: 'rgba(255,255,255,0.02)', padding: 'var(--space-4)', borderRadius: 'var(--radius-lg)', border: '1px solid var(--color-border)' }}>
                      <WicketsRunsComparisonChart
                        data={recentBowlWindow}
                        windowN={selectedRecentN}
                      />
                    </div>
                  </>
                ) : (
                  <Empty msg={`No bowling appearances available for ${analytics.playerName} in ${selectedFormat} format.`} />
                )}
              </div>
            )}
          </Section>

          {/* ─────────────────────────────────────────────────────────────
              SECTION 3: Recent Form and Consistency Analysis
             ───────────────────────────────────────────────────────────── */}
          <Section
            id="section-consistency"
            title="Recent Form & Consistency Analysis"
            icon=""
            subtitle="Dispersion and stability evaluation across eligible chronological appearances."
          >
            {isBat && (
              <div style={{ marginBottom: isAllRounder ? 'var(--space-5)' : 0 }}>
                {isAllRounder && (
                  <h4 style={{ color: '#818CF8', margin: '0 0 var(--space-2) 0', fontSize: '0.9rem' }}>
                     Batting Consistency & Momentum
                  </h4>
                )}
                {recentBatWindow.length > 0 ? (
                  <BattingConsistencyCard
                    summary={batSummary}
                    recentSummary={recentSummary}
                    windowN={selectedRecentN}
                  />
                ) : (
                  <Empty msg="Insufficient batting innings to compute consistency." />
                )}
              </div>
            )}

            {isBowl && (
              <div style={{ marginTop: isAllRounder ? 'var(--space-5)' : 0 }}>
                {isAllRounder && (
                  <h4 style={{ color: '#F472B6', margin: '0 0 var(--space-2) 0', fontSize: '0.9rem' }}>
                     Bowling Consistency & Spell Control
                  </h4>
                )}
                {recentBowlWindow.length > 0 ? (
                  <BowlingConsistencyCard
                    summary={bowlSummary}
                    recentSummary={recentSummary}
                    windowN={selectedRecentN}
                  />
                ) : (
                  <Empty msg="Insufficient bowling spells to compute consistency." />
                )}
              </div>
            )}
          </Section>

          {/* ─────────────────────────────────────────────────────────────
              SECTION 4: Scoring or Bowling Effectiveness
             ───────────────────────────────────────────────────────────── */}
          <Section
            id="section-effectiveness"
            title="Scoring & Bowling Effectiveness"
            icon=""
            subtitle="Empirical scoring milestone conversion, boundary distribution, and wicket penetration."
          >
            {isBat && (
              <div style={{ marginBottom: isAllRounder ? 'var(--space-6)' : 0 }}>
                {isAllRounder && (
                  <div className="discipline-facet-banner">
                    <h4 className="discipline-facet-title"> Batting Milestone & Boundary Profile</h4>
                  </div>
                )}
                {batSummary && batSummary.innings > 0 ? (
                  <div className="analytics-grid-2col">
                    <div style={{ background: 'rgba(255,255,255,0.02)', padding: 'var(--space-4)', borderRadius: 'var(--radius-lg)', border: '1px solid var(--color-border)' }}>
                      <ScoringDistributionChart
                        distribution={scoreDist}
                        totalInnings={batSummary.innings}
                      />
                    </div>
                    <div style={{ background: 'rgba(255,255,255,0.02)', padding: 'var(--space-4)', borderRadius: 'var(--radius-lg)', border: '1px solid var(--color-border)' }}>
                      <BoundaryContributionChart summary={batSummary} />
                    </div>
                  </div>
                ) : (
                  <Empty msg="No batting records available to compute scoring distribution." />
                )}
              </div>
            )}

            {isBowl && (
              <div style={{ marginTop: isAllRounder ? 'var(--space-6)' : 0 }}>
                {isAllRounder && (
                  <div className="discipline-facet-banner discipline-facet-banner--bowl">
                    <h4 className="discipline-facet-title" style={{ color: '#FECDD3' }}> Bowling Penetration & Wicket Frequency</h4>
                  </div>
                )}
                {bowlSummary && bowlSummary.innings > 0 ? (
                  <div className="analytics-grid-2col">
                    <div style={{ background: 'rgba(255,255,255,0.02)', padding: 'var(--space-4)', borderRadius: 'var(--radius-lg)', border: '1px solid var(--color-border)' }}>
                      <BowlingDistributionChart
                        distribution={bowlDist}
                        totalSpells={bowlSummary.innings}
                      />
                    </div>
                    <div style={{ background: 'rgba(255,255,255,0.02)', padding: 'var(--space-4)', borderRadius: 'var(--radius-lg)', border: '1px solid var(--color-border)' }}>
                      <h4 style={{ margin: '0 0 var(--space-3) 0', color: 'var(--color-text-primary)', fontSize: '0.9rem' }}>
                        Bowling Penetration Rates
                      </h4>
                      <div className="recent-summary-grid">
                        <div className="recent-stat-card">
                          <span className="recent-stat-label">Bowling Average</span>
                          <div className="recent-stat-row">
                            <span className="recent-stat-val-p1">
                              {bowlSummary.bowlingAverage != null ? bowlSummary.bowlingAverage.toFixed(2) : 'N/A'}
                            </span>
                          </div>
                          <span style={{ fontSize: '0.72rem', color: '#94A3B8' }}>Runs conceded / total wickets</span>
                        </div>
                        <div className="recent-stat-card">
                          <span className="recent-stat-label">Bowling Strike Rate</span>
                          <div className="recent-stat-row">
                            <span className="recent-stat-val-p1">
                              {bowlSummary.bowlingStrikeRate != null ? `${bowlSummary.bowlingStrikeRate.toFixed(1)} b/w` : 'N/A'}
                            </span>
                          </div>
                          <span style={{ fontSize: '0.72rem', color: '#94A3B8' }}>Legal balls bowled / wicket</span>
                        </div>
                        <div className="recent-stat-card">
                          <span className="recent-stat-label">Career Overs Bowled</span>
                          <div className="recent-stat-row">
                            <span className="recent-stat-val-p1">{bowlSummary.totalOvers} ov</span>
                          </div>
                          <span style={{ fontSize: '0.72rem', color: '#94A3B8' }}>{bowlSummary.validBallsBowled} legal balls delivered</span>
                        </div>
                        <div className="recent-stat-card">
                          <span className="recent-stat-label">Match Hauls</span>
                          <div className="recent-stat-row">
                            <span className="recent-stat-val-p1">
                              {bowlSummary.fourWickets} × 4W · {bowlSummary.fiveWickets} × 5W
                            </span>
                          </div>
                          <span style={{ fontSize: '0.72rem', color: '#94A3B8' }}>Match-defining bowling hauls</span>
                        </div>
                      </div>
                      <p className="metric-formula-note" style={{ marginTop: '12px' }}>
                        Calculations use actual overs notation (e.g. 4.2 ov = 26 legal balls, 4.333 decimal overs). Bowling average and strike rate safely return N/A if zero wickets are recorded.
                      </p>
                    </div>
                  </div>
                ) : (
                  <Empty msg="No bowling records available to compute penetration distribution." />
                )}
              </div>
            )}
          </Section>

          {/* ─────────────────────────────────────────────────────────────
              SECTION 5: Player Strengths & Weaknesses
             ───────────────────────────────────────────────────────────── */}
          <StrengthsAndWeaknessesSection
            strengths={strengths}
            loading={strengthsLoading}
            error={strengthsError}
            selectedFormat={selectedFormat}
            role={role}
          />

          {/* ─────────────────────────────────────────────────────────────
              SECTION 6: Context, History & Limitations
             ───────────────────────────────────────────────────────────── */}
          <Section
            id="section-context-history"
            title="Context, History & Data Coverage"
            icon=""
            subtitle="Environmental context (venue/opposition), chronological match history, and dataset bounds."
          >
            {/* Top Venues and Opposition */}
            <div className="analytics-grid-2col" style={{ marginBottom: 'var(--space-6)' }}>
              {isBat && (
                <>
                  <div style={{ background: 'rgba(255,255,255,0.02)', padding: 'var(--space-4)', borderRadius: 'var(--radius-lg)', border: '1px solid var(--color-border)' }}>
                    <BattingVenueChart data={analytics.venueBreakdown} />
                  </div>
                  <div style={{ background: 'rgba(255,255,255,0.02)', padding: 'var(--space-4)', borderRadius: 'var(--radius-lg)', border: '1px solid var(--color-border)' }}>
                    <BattingOppositionChart data={analytics.oppositionBreakdown} />
                  </div>
                </>
              )}
              {isBowl && (
                <>
                  <div style={{ background: 'rgba(255,255,255,0.02)', padding: 'var(--space-4)', borderRadius: 'var(--radius-lg)', border: '1px solid var(--color-border)' }}>
                    <BowlingVenueChart data={analytics.bowlingVenueBreakdown} />
                  </div>
                  <div style={{ background: 'rgba(255,255,255,0.02)', padding: 'var(--space-4)', borderRadius: 'var(--radius-lg)', border: '1px solid var(--color-border)' }}>
                    <BowlingOppositionChart data={analytics.bowlingOppositionBreakdown} />
                  </div>
                </>
              )}
            </div>

            {/* Match-by-Match History Tables */}
            <div style={{ marginBottom: 'var(--space-6)' }}>
              <div className="chart-header-row">
                <h4 style={{ margin: 0, color: 'var(--color-text-primary)', fontSize: '0.95rem' }}>
                  Match-by-Match History Breakdown (Last {selectedRecentN} appearances)
                </h4>
                <WindowTabs active={selectedRecentN} onChange={handleRecentNChange} isBowling={isBowl && !isBat} />
              </div>

              {isBat && (
                <div style={{ marginTop: 'var(--space-3)' }}>
                  <h5 style={{ color: '#818CF8', margin: '0 0 var(--space-2) 0', fontSize: '0.85rem' }}>
                     Batting Innings
                  </h5>
                  <BattingMatchTable data={analytics.recentBattingForm} windowN={selectedRecentN} />
                </div>
              )}

              {isBowl && (
                <div style={{ marginTop: 'var(--space-4)' }}>
                  <h5 style={{ color: '#F472B6', margin: '0 0 var(--space-2) 0', fontSize: '0.85rem' }}>
                     Bowling Spells
                  </h5>
                  <BowlingMatchTable data={analytics.recentBowlingForm} windowN={selectedRecentN} />
                </div>
              )}
            </div>

            {/* Coverage Block */}
            <div style={{ marginBottom: 'var(--space-6)' }}>
              <h4 style={{ margin: '0 0 var(--space-3) 0', color: 'var(--color-text-primary)', fontSize: '0.95rem' }}>
                Historical Dataset Coverage & Reliability
              </h4>
              <CoverageBlock coverage={analytics.coverage} role={role} />
            </div>

            {/* Format Breakdown Grid */}
            <div>
              <h4 style={{ margin: '0 0 var(--space-3) 0', color: 'var(--color-text-primary)', fontSize: '0.95rem' }}>
                All-Format Career Overview
              </h4>
              <FormatBreakdown data={analytics.formatBreakdown} role={role} />
            </div>
          </Section>

        </div>
      )}

      {/* Empty State */}
      {!analytics && !loading && !error && (
        <div className="analytics-empty-state">
          <div className="analytics-empty-icon"></div>
          <h3>Search for a player to view role-based analytics</h3>
          <p>Type a player name above (e.g. "V Kohli", "AU Rashid", "CV Varun", "RL Chase")</p>
          <p className="analytics-empty-hint">
            Batters display runs/SR trendlines, scoring distributions, boundary profiles, and CV% consistency. Bowlers display wickets/economy trendlines, bowling penetration, dual-axis spell comparisons, and hauls. All-rounders receive both partitioned sections.
          </p>
        </div>
      )}
    </div>
    </DashboardLayout>
  );
}
