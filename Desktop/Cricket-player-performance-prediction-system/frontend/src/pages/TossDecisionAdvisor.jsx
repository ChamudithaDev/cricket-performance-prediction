/**
 * TossDecisionAdvisor.jsx
 *
 * CricketAI — Toss Decision Advisor Page
 *
 * Provides historical match-level venue toss analytics:
 * - Bat-First vs Chasing win rates
 * - Recommendation badge (BAT_FIRST, FIELD_FIRST, NO_CLEAR_ADVANTAGE, INSUFFICIENT_DATA)
 * - Rolling 2-year analysis window based on latest reliable dataset date
 * - Completed decisive match denominators (excludes no-results, draws, ties, awarded matches)
 * - Comparison chart and recent match history
 */

import { useState, useEffect, useMemo, useRef, useCallback } from 'react';
import {
  Chart as ChartJS,
  CategoryScale, LinearScale, BarElement,
  Title, Tooltip, Legend,
} from 'chart.js';
import { Bar } from 'react-chartjs-2';
import DashboardLayout from '../components/layout/DashboardLayout';
import { getTossDecision, getTossVenues } from '../services/tossService';

ChartJS.register(CategoryScale, LinearScale, BarElement, Title, Tooltip, Legend);

const FORMATS = ['ODI', 'T20I', 'TEST'];

const POPULAR_VENUES = {
  ODI: [
    'R Premadasa Stadium, Colombo',
    'Shere Bangla National Stadium, Mirpur',
    'Melbourne Cricket Ground',
    'Lord\'s, London',
    'Maple Leaf North-West Ground, King City',
  ],
  T20I: [
    'TCA Oval, Blantyre',
    'Terdthai Cricket Ground, Bangkok',
    'Udayana Cricket Ground',
    'Bayuemas Oval, Kuala Lumpur',
    'Dubai International Cricket Stadium',
  ],
  TEST: [
    'Lord\'s, London',
    'Melbourne Cricket Ground',
    'Sydney Cricket Ground',
    'The Oval, London',
    'Galle International Stadium',
  ],
};

export default function TossDecisionAdvisor() {
  const [format, setFormat] = useState('ODI');
  const [venueInput, setVenueInput] = useState('R Premadasa Stadium, Colombo');
  const [selectedVenue, setSelectedVenue] = useState('R Premadasa Stadium, Colombo');
  const [venueList, setVenueList] = useState([]);
  const [venueLoading, setVenueLoading] = useState(false);
  const [showDropdown, setShowDropdown] = useState(false);

  const [decision, setDecision] = useState(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

  const dropdownRef = useRef(null);

  // Load venues for selected format
  const loadVenues = useCallback(async (fmt) => {
    setVenueLoading(true);
    try {
      const res = await getTossVenues(fmt);
      setVenueList(res.data?.venues || []);
    } catch {
      setVenueList([]);
    } finally {
      setVenueLoading(false);
    }
  }, []);

  useEffect(() => {
    loadVenues(format);
  }, [format, loadVenues]);

  // Fetch toss decision
  const fetchTossDecision = useCallback(async (venueToQuery, fmtToQuery) => {
    const v = (venueToQuery || selectedVenue || venueInput).trim();
    if (!v) return;

    setLoading(true);
    setError('');
    setShowDropdown(false);

    try {
      const res = await getTossDecision(v, fmtToQuery || format);
      setDecision(res.data);
      setSelectedVenue(v);
    } catch (err) {
      if (err.response?.data?.error) {
        setError(err.response.data.error);
      } else {
        setError('Failed to fetch toss decision data. Please ensure the backend is running.');
      }
      setDecision(null);
    } finally {
      setLoading(false);
    }
  }, [selectedVenue, venueInput, format]);

  // Auto-fetch on mount for default venue
  useEffect(() => {
    fetchTossDecision('R Premadasa Stadium, Colombo', 'ODI');
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  // Filter autocomplete suggestions
  const suggestions = useMemo(() => {
    const q = venueInput.trim().toLowerCase();
    if (!q || q.length < 2) return [];
    return venueList
      .filter((v) => v.toLowerCase().includes(q))
      .slice(0, 8);
  }, [venueInput, venueList]);

  // Close dropdown on outside click
  useEffect(() => {
    const handleOutsideClick = (e) => {
      if (dropdownRef.current && !dropdownRef.current.contains(e.target)) {
        setShowDropdown(false);
      }
    };
    document.addEventListener('mousedown', handleOutsideClick);
    return () => document.removeEventListener('mousedown', handleOutsideClick);
  }, []);

  // Format change handler
  const handleFormatChange = (newFormat) => {
    setFormat(newFormat);
    // If the current venue is in the new format's list or popular list, keep it; else set first popular venue
    const popular = POPULAR_VENUES[newFormat] || [];
    const nextVenue = popular.length > 0 ? popular[0] : venueInput;
    setVenueInput(nextVenue);
    setSelectedVenue(nextVenue);
    fetchTossDecision(nextVenue, newFormat);
  };

  const handleSelectVenue = (venueName) => {
    setVenueInput(venueName);
    setSelectedVenue(venueName);
    setShowDropdown(false);
    fetchTossDecision(venueName, format);
  };

  // Chart configuration
  const chartData = useMemo(() => {
    if (!decision || decision.decisiveMatches === 0) return null;

    return {
      labels: ['Bat First', 'Field First (Chase)'],
      datasets: [
        {
          label: 'Win Rate (%)',
          data: [decision.batFirstWinRate, decision.chaseWinRate],
          backgroundColor: [
            'rgba(34, 197, 94, 0.8)', // green for bat first
            'rgba(59, 130, 246, 0.8)', // blue for field first
          ],
          borderColor: ['#22c55e', '#3b82f6'],
          borderWidth: 2,
          borderRadius: 8,
          barThickness: 48,
        },
      ],
    };
  }, [decision]);

  const chartOptions = useMemo(() => ({
    responsive: true,
    maintainAspectRatio: false,
    plugins: {
      legend: { display: false },
      tooltip: {
        backgroundColor: '#0f172a',
        titleColor: '#0f172a',
        bodyColor: '#8a9ac0',
        borderColor: '#e2e8f0',
        borderWidth: 1,
        padding: 12,
        callbacks: {
          label: (ctx) => ` Win Rate: ${ctx.parsed.y.toFixed(1)}%`,
        },
      },
    },
    scales: {
      y: {
        min: 0,
        max: 100,
        ticks: {
          color: '#64748b',
          callback: (v) => `${v}%`,
          font: { size: 11 },
        },
        grid: { color: 'rgba(15, 23, 42, 0.06)' },
      },
      x: {
        ticks: { color: '#0f172a', font: { weight: '600', size: 13 } },
        grid: { display: false },
      },
    },
  }), []);

  // Recommendation Badge Helper
  const getBadgeConfig = (rec) => {
    switch (rec) {
      case 'BAT_FIRST':
        return {
          title: 'Bat First',
          icon: '🏏',
          sub: 'Setting a target is historically favored at this venue.',
          bg: '#dcfce7',
          border: '#86efac',
          text: '#15803d',
        };
      case 'FIELD_FIRST':
        return {
          title: 'Field First (Bowl First)',
          icon: '🎯',
          sub: 'Chasing a target is historically favored at this venue.',
          bg: '#dbeafe',
          border: '#93c5fd',
          text: '#1d4ed8',
        };
      case 'NO_CLEAR_ADVANTAGE':
        return {
          title: 'No Clear Advantage',
          icon: '⚖️',
          sub: 'Win rates are balanced between batting and bowling first.',
          bg: '#fef3c7',
          border: '#fde68a',
          text: '#92400e',
        };
      case 'INSUFFICIENT_DATA':
      default:
        return {
          title: 'Insufficient Data',
          icon: '⚠️',
          sub: 'Too few decisive matches in the 2-year window for a statistically sound recommendation.',
          bg: '#f1f5f9',
          border: '#cbd5e1',
          text: '#475569',
        };
    }
  };

  return (
    <DashboardLayout pageTitle="Toss Decision Advisor">
      <div className="analytics-page toss-advisor-page">
        {/* Header */}
        <div className="analytics-header">
          <div className="analytics-header-text">
            <h1 className="analytics-title">
              
              Toss Decision Advisor
            </h1>
            <p className="analytics-subtitle">
              Venue-level toss intelligence and tactical win-rate analysis derived from verified
              ball-by-ball Cricsheet records over a rolling two-year window.
            </p>
          </div>
        </div>

        {/* Control Bar: Format Selector + Venue Search */}
        <div className="analytics-controls-card" style={{ marginBottom: 'var(--space-6)' }}>
          <div style={{ display: 'flex', flexDirection: 'column', gap: 'var(--space-4)' }}>
            {/* Format Selection Tabs */}
            <div style={{ display: 'flex', alignItems: 'center', gap: 'var(--space-3)', flexWrap: 'wrap' }}>
              <span style={{ fontSize: '0.85rem', fontWeight: 600, color: 'var(--color-text-secondary)' }}>
                Format:
              </span>
              <div style={{ display: 'flex', gap: 'var(--space-2)' }}>
                {FORMATS.map((fmt) => (
                  <button
                    key={fmt}
                    type="button"
                    onClick={() => handleFormatChange(fmt)}
                    style={{
                      padding: '8px 18px',
                      borderRadius: 'var(--radius-md)',
                      fontSize: '0.88rem',
                      fontWeight: 600,
                      cursor: 'pointer',
                      border: format === fmt ? '1px solid var(--color-accent)' : '1px solid var(--color-border)',
                      background: format === fmt ? 'var(--color-accent)' : 'var(--color-bg-card)',
                      color: format === fmt ? '#ffffff' : 'var(--color-text-secondary)',
                      transition: 'all var(--transition-fast)',
                    }}
                  >
                    {fmt}
                  </button>
                ))}
              </div>
            </div>

            {/* Venue Search & Autocomplete */}
            <div style={{ display: 'flex', gap: 'var(--space-3)', flexWrap: 'wrap', position: 'relative' }} ref={dropdownRef}>
              <div style={{ flex: 1, minWidth: '280px', position: 'relative' }}>
                <input
                  type="text"
                  placeholder="Search international venue (e.g., Melbourne Cricket Ground, Lord's)..."
                  value={venueInput}
                  onChange={(e) => {
                    setVenueInput(e.target.value);
                    setShowDropdown(true);
                  }}
                  onFocus={() => setShowDropdown(true)}
                  onKeyDown={(e) => {
                    if (e.key === 'Enter') {
                      e.preventDefault();
                      fetchTossDecision(venueInput, format);
                    }
                  }}
                  style={{
                    width: '100%',
                    padding: '12px 16px',
                    borderRadius: 'var(--radius-md)',
                    border: '1px solid var(--color-border)',
                    background: 'var(--color-bg-elevated)',
                    color: 'var(--color-text-primary)',
                    fontSize: '0.95rem',
                  }}
                />

                {/* Autocomplete Dropdown */}
                {showDropdown && suggestions.length > 0 && (
                  <ul
                    style={{
                      position: 'absolute',
                      top: '100%',
                      left: 0,
                      right: 0,
                      marginTop: '4px',
                      background: 'var(--color-bg-card)',
                      border: '1px solid var(--color-border-light)',
                      borderRadius: 'var(--radius-md)',
                      boxShadow: 'var(--shadow-lg)',
                      listStyle: 'none',
                      zIndex: 50,
                      maxHeight: '260px',
                      overflowY: 'auto',
                    }}
                  >
                    {suggestions.map((v) => (
                      <li
                        key={v}
                        onClick={() => handleSelectVenue(v)}
                        style={{
                          padding: '10px 16px',
                          cursor: 'pointer',
                          color: 'var(--color-text-primary)',
                          borderBottom: '1px solid var(--color-border)',
                          fontSize: '0.9rem',
                        }}
                        onMouseEnter={(e) => {
                          e.currentTarget.style.background = 'var(--color-bg-card-hover)';
                        }}
                        onMouseLeave={(e) => {
                          e.currentTarget.style.background = 'transparent';
                        }}
                      >
                        {v}
                      </li>
                    ))}
                  </ul>
                )}
              </div>

              <button
                type="button"
                onClick={() => fetchTossDecision(venueInput, format)}
                disabled={loading || !venueInput.trim()}
                style={{
                  padding: '12px 24px',
                  borderRadius: 'var(--radius-md)',
                  background: 'var(--color-accent)',
                  color: '#ffffff',
                  fontWeight: 600,
                  fontSize: '0.95rem',
                  border: 'none',
                  cursor: loading || !venueInput.trim() ? 'not-allowed' : 'pointer',
                  opacity: loading || !venueInput.trim() ? 0.7 : 1,
                  display: 'flex',
                  alignItems: 'center',
                  gap: '8px',
                }}
              >
                {loading ? 'Analyzing…' : 'Analyze Toss'}
              </button>
            </div>

            {/* Quick Venue Chips */}
            <div style={{ display: 'flex', alignItems: 'center', gap: 'var(--space-2)', flexWrap: 'wrap' }}>
              <span style={{ fontSize: '0.78rem', color: 'var(--color-text-muted)' }}>Top {format} Venues:</span>
              {(POPULAR_VENUES[format] || []).map((v) => (
                <button
                  key={v}
                  type="button"
                  onClick={() => handleSelectVenue(v)}
                  style={{
                    padding: '4px 10px',
                    borderRadius: 'var(--radius-sm)',
                    fontSize: '0.75rem',
                    background: selectedVenue === v ? '#eff6ff' : '#f1f5f9',
                    color: selectedVenue === v ? 'var(--color-accent)' : 'var(--color-text-secondary)',
                    border: selectedVenue === v ? '1px solid var(--color-accent)' : '1px solid transparent',
                    cursor: 'pointer',
                  }}
                >
                  {v.split(',')[0]}
                </button>
              ))}
            </div>
          </div>
        </div>

        {/* Error State */}
        {error && (
          <div
            style={{
              padding: '16px 20px',
              borderRadius: 'var(--radius-md)',
              background: '#fee2e2',
              border: '1px solid #fecaca',
              color: '#b91c1c',
              marginBottom: 'var(--space-6)',
              display: 'flex',
              alignItems: 'center',
              gap: '12px',
            }}
          >
            <span style={{ fontSize: '1.4rem' }}>⚠️</span>
            <div>
              <div style={{ fontWeight: 600 }}>Analysis Error</div>
              <div style={{ fontSize: '0.9rem' }}>{error}</div>
            </div>
          </div>
        )}

        {/* Loading State */}
        {loading && (
          <div style={{ padding: '60px 0', textAlign: 'center' }}>
            <div className="spinner" style={{ margin: '0 auto 16px auto' }} />
            <p style={{ color: 'var(--color-text-secondary)', fontSize: '0.95rem' }}>
              Crunching ball-by-ball decisive records for {selectedVenue}...
            </p>
          </div>
        )}

        {/* Main Content when Decision is Loaded */}
        {!loading && decision && (
          <div style={{ display: 'flex', flexDirection: 'column', gap: 'var(--space-6)' }}>
            {/* Recommendation Hero Banner */}
            {(() => {
              const b = getBadgeConfig(decision.recommendation);
              return (
                <div
                  style={{
                    padding: '24px',
                    borderRadius: 'var(--radius-lg)',
                    background: b.bg,
                    border: `1px solid ${b.border}`,
                    boxShadow: 'var(--shadow-md)',
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'space-between',
                    flexWrap: 'wrap',
                    gap: '20px',
                  }}
                >
                  <div style={{ display: 'flex', alignItems: 'center', gap: '18px' }}>
                    <span style={{ fontSize: '3rem' }}>{b.icon}</span>
                    <div>
                      <div style={{ display: 'flex', alignItems: 'center', gap: '12px' }}>
                        <span
                          style={{
                            fontSize: '0.75rem',
                            fontWeight: 700,
                            letterSpacing: '0.08em',
                            textTransform: 'uppercase',
                            padding: '3px 8px',
                            borderRadius: 'var(--radius-sm)',
                            background: '#ffffff',
                            color: b.text,
                            border: `1px solid ${b.border}`,
                          }}
                        >
                          Recommendation
                        </span>
                        <span style={{ fontSize: '0.85rem', color: 'var(--color-text-secondary)' }}>
                          {decision.venue} ({decision.format})
                        </span>
                      </div>
                      <h2 style={{ fontSize: '1.75rem', color: 'var(--color-text-primary)', margin: '4px 0 2px 0' }}>
                        {b.title}
                      </h2>
                      <p style={{ color: 'var(--color-text-secondary)', fontSize: '0.92rem', margin: 0, maxWidth: '650px' }}>
                        {b.sub}
                      </p>
                    </div>
                  </div>

                  <div style={{ textAlign: 'right', minWidth: '160px' }}>
                    <div style={{ fontSize: '0.8rem', color: '#94A3B8' }}>Decisive Match Differential</div>
                    <div
                      style={{
                        fontSize: '1.75rem',
                        fontWeight: 700,
                        color: decision.percentagePointDifference > 0 ? '#15803d' : decision.percentagePointDifference < 0 ? '#1d4ed8' : '#92400e',
                      }}
                    >
                      {decision.percentagePointDifference > 0 ? `+${decision.percentagePointDifference.toFixed(1)} pp` : `${decision.percentagePointDifference.toFixed(1)} pp`}
                    </div>
                    <div style={{ fontSize: '0.78rem', color: '#94A3B8' }}>
                      Based on {decision.decisiveMatches} decisive matches
                    </div>
                  </div>
                </div>
              );
            })()}

            {/* Win Rate Stat Cards Grid */}
            <div
              style={{
                display: 'grid',
                gridTemplateColumns: 'repeat(auto-fit, minmax(260px, 1fr))',
                gap: 'var(--space-4)',
              }}
            >
              {/* Bat First Card */}
              <div
                className="stat-card"
                style={{
                  borderTop: '3px solid #22c55e',
                  background: 'var(--color-bg-card)',
                  padding: '20px',
                  borderRadius: 'var(--radius-md)',
                  boxShadow: 'var(--shadow-sm)',
                }}
              >
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                  <span style={{ fontSize: '0.85rem', color: 'var(--color-text-secondary)', fontWeight: 600 }}>
                    Batting First Win %
                  </span>
                  <span style={{ fontSize: '0.8rem', color: '#15803d', fontWeight: 600 }}>
                    {decision.batFirstWins} wins
                  </span>
                </div>
                <div style={{ fontSize: '2.4rem', fontWeight: 700, color: 'var(--color-text-primary)', margin: '8px 0 4px 0' }}>
                  {decision.batFirstWinRate.toFixed(1)}%
                </div>
                <div
                  style={{
                    height: '6px',
                    borderRadius: '3px',
                    background: '#e2e8f0',
                    overflow: 'hidden',
                    margin: '8px 0 6px 0',
                  }}
                >
                  <div
                    style={{
                      height: '100%',
                      width: `${Math.min(100, decision.batFirstWinRate)}%`,
                      background: '#22c55e',
                      transition: 'width 0.5s ease',
                    }}
                  />
                </div>
                <div style={{ fontSize: '0.78rem', color: 'var(--color-text-muted)' }}>
                  {decision.batFirstWins} of {decision.decisiveMatches} decisive matches won setting a score
                </div>
              </div>

              {/* Chasing Card */}
              <div
                className="stat-card"
                style={{
                  borderTop: '3px solid #3b82f6',
                  background: 'var(--color-bg-card)',
                  padding: '20px',
                  borderRadius: 'var(--radius-md)',
                  boxShadow: 'var(--shadow-sm)',
                }}
              >
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                  <span style={{ fontSize: '0.85rem', color: 'var(--color-text-secondary)', fontWeight: 600 }}>
                    Chasing (Field First) Win %
                  </span>
                  <span style={{ fontSize: '0.8rem', color: '#1d4ed8', fontWeight: 600 }}>
                    {decision.chaseWins} wins
                  </span>
                </div>
                <div style={{ fontSize: '2.4rem', fontWeight: 700, color: 'var(--color-text-primary)', margin: '8px 0 4px 0' }}>
                  {decision.chaseWinRate.toFixed(1)}%
                </div>
                <div
                  style={{
                    height: '6px',
                    borderRadius: '3px',
                    background: '#e2e8f0',
                    overflow: 'hidden',
                    margin: '8px 0 6px 0',
                  }}
                >
                  <div
                    style={{
                      height: '100%',
                      width: `${Math.min(100, decision.chaseWinRate)}%`,
                      background: '#3b82f6',
                      transition: 'width 0.5s ease',
                    }}
                  />
                </div>
                <div style={{ fontSize: '0.78rem', color: 'var(--color-text-muted)' }}>
                  {decision.chaseWins} of {decision.decisiveMatches} decisive matches won chasing
                </div>
              </div>

              {/* Window & Sample Coverage Card */}
              <div
                className="stat-card"
                style={{
                  borderTop: '3px solid #8B5CF6',
                  background: 'var(--color-bg-card)',
                  padding: '20px',
                  borderRadius: 'var(--radius-md)',
                  boxShadow: 'var(--shadow-sm)',
                }}
              >
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                  <span style={{ fontSize: '0.85rem', color: 'var(--color-text-secondary)', fontWeight: 600 }}>
                    2-Year Analysis Window
                  </span>
                  <span
                    style={{
                      fontSize: '0.75rem',
                      padding: '2px 6px',
                      borderRadius: 'var(--radius-sm)',
                      background: decision.decisiveMatches >= decision.minSampleSize ? '#dcfce7' : '#fef3c7',
                      color: decision.decisiveMatches >= decision.minSampleSize ? '#15803d' : '#92400e',
                      border: decision.decisiveMatches >= decision.minSampleSize ? '1px solid #86efac' : '1px solid #fde68a',
                    }}
                  >
                    {decision.decisiveMatches >= decision.minSampleSize ? 'Valid Sample' : 'Low Sample'}
                  </span>
                </div>
                <div style={{ fontSize: '1.25rem', fontWeight: 600, color: 'var(--color-text-primary)', margin: '12px 0 6px 0' }}>
                  {decision.windowStartDate} <span style={{ color: 'var(--color-text-muted)' }}>to</span> {decision.windowEndDate}
                </div>
                <div style={{ fontSize: '0.8rem', color: 'var(--color-text-secondary)' }}>
                  Total Matches: <strong>{decision.totalEligibleMatches}</strong> · Decisive: <strong>{decision.decisiveMatches}</strong>
                </div>
                <div style={{ fontSize: '0.78rem', color: 'var(--color-text-muted)', marginTop: '4px' }}>
                  Reference Date: {decision.referenceDate} (Min. {decision.minSampleSize} matches required)
                </div>
              </div>
            </div>

            {/* Chart + Analysis Explanation Grid */}
            <div
              style={{
                display: 'grid',
                gridTemplateColumns: 'repeat(auto-fit, minmax(360px, 1fr))',
                gap: 'var(--space-6)',
              }}
            >
              {/* Comparison Chart */}
              <div
                style={{
                  background: 'var(--color-bg-card)',
                  padding: '24px',
                  borderRadius: 'var(--radius-lg)',
                  border: '1px solid var(--color-border)',
                }}
              >
                <h3 style={{ fontSize: '1rem', color: 'var(--color-text-primary)', marginBottom: 'var(--space-4)' }}>
                  Win Rate Comparison
                </h3>
                {chartData ? (
                  <div style={{ height: '240px', position: 'relative' }}>
                    <Bar data={chartData} options={chartOptions} />
                  </div>
                ) : (
                  <div style={{ padding: '40px', textAlign: 'center', color: 'var(--color-text-muted)' }}>
                    No decisive match data available to display chart.
                  </div>
                )}
                <div
                  style={{
                    display: 'flex',
                    justifyContent: 'space-around',
                    marginTop: '16px',
                    paddingTop: '12px',
                    borderTop: '1px solid var(--color-border)',
                    fontSize: '0.82rem',
                    color: 'var(--color-text-secondary)',
                  }}
                >
                  <div style={{ display: 'flex', alignItems: 'center', gap: '6px' }}>
                    <span style={{ width: '10px', height: '10px', borderRadius: '50%', background: '#22c55e' }} />
                    Bat First: {decision.batFirstWinRate.toFixed(1)}%
                  </div>
                  <div style={{ display: 'flex', alignItems: 'center', gap: '6px' }}>
                    <span style={{ width: '10px', height: '10px', borderRadius: '50%', background: '#3b82f6' }} />
                    Field First: {decision.chaseWinRate.toFixed(1)}%
                  </div>
                  <div style={{ display: 'flex', alignItems: 'center', gap: '6px' }}>
                    <span style={{ color: '#d97706', fontWeight: 700 }}>Δ</span>
                    Diff: {Math.abs(decision.percentagePointDifference).toFixed(1)} pp
                  </div>
                </div>
              </div>

              {/* Tactical Explanation & Coverage */}
              <div
                style={{
                  background: 'var(--color-bg-card)',
                  padding: '24px',
                  borderRadius: 'var(--radius-lg)',
                  border: '1px solid var(--color-border)',
                  display: 'flex',
                  flexDirection: 'column',
                  justifyContent: 'space-between',
                }}
              >
                <div>
                  <h3 style={{ fontSize: '1rem', color: 'var(--color-text-primary)', marginBottom: 'var(--space-3)' }}>
                    Tactical Explanation & Evidence
                  </h3>
                  <p
                    style={{
                      color: 'var(--color-text-primary)',
                      fontSize: '0.92rem',
                      lineHeight: '1.6',
                      background: '#f8fafc',
                      border: '1px solid var(--color-border)',
                      padding: '14px 16px',
                      borderRadius: 'var(--radius-md)',
                      borderLeft: '4px solid var(--color-accent)',
                      marginBottom: 'var(--space-4)',
                    }}
                  >
                    {decision.explanation}
                  </p>

                  {/* Excluded Matches Breakdown */}
                  <div style={{ marginTop: '12px' }}>
                    <div style={{ fontSize: '0.82rem', fontWeight: 600, color: 'var(--color-text-secondary)', marginBottom: '6px' }}>
                      Match Coverage & Exclusions:
                    </div>
                    <div style={{ display: 'flex', gap: '8px', flexWrap: 'wrap' }}>
                      <span
                        style={{
                          fontSize: '0.78rem',
                          padding: '4px 8px',
                          borderRadius: 'var(--radius-sm)',
                          background: '#dcfce7',
                          color: '#15803d',
                          border: '1px solid #86efac',
                        }}
                      >
                        ✓ {decision.decisiveMatches} Decisive (In Denominator)
                      </span>
                      {decision.excludedBreakdown?.noResultCount > 0 && (
                        <span
                          style={{
                            fontSize: '0.78rem',
                            padding: '4px 8px',
                            borderRadius: 'var(--radius-sm)',
                            background: '#f1f5f9',
                            color: '#475569',
                            border: '1px solid #cbd5e1',
                          }}
                        >
                          {decision.excludedBreakdown.noResultCount} No-Result
                        </span>
                      )}
                      {decision.excludedBreakdown?.drawCount > 0 && (
                        <span
                          style={{
                            fontSize: '0.78rem',
                            padding: '4px 8px',
                            borderRadius: 'var(--radius-sm)',
                            background: '#f1f5f9',
                            color: '#475569',
                            border: '1px solid #cbd5e1',
                          }}
                        >
                          {decision.excludedBreakdown.drawCount} Draw
                        </span>
                      )}
                      {decision.excludedBreakdown?.tieCount > 0 && (
                        <span
                          style={{
                            fontSize: '0.78rem',
                            padding: '4px 8px',
                            borderRadius: 'var(--radius-sm)',
                            background: '#fef3c7',
                            color: '#92400e',
                            border: '1px solid #fde68a',
                          }}
                        >
                          ⚡ {decision.excludedBreakdown.tieCount} Tie (Unresolved)
                        </span>
                      )}
                      {decision.excludedBreakdown?.awardedCount > 0 && (
                        <span
                          style={{
                            fontSize: '0.78rem',
                            padding: '4px 8px',
                            borderRadius: 'var(--radius-sm)',
                            background: '#fee2e2',
                            color: '#b91c1c',
                            border: '1px solid #fecaca',
                          }}
                        >
                          {decision.excludedBreakdown.awardedCount} Awarded (Excluded)
                        </span>
                      )}
                    </div>
                  </div>
                </div>

                {/* Caveat Footer */}
                <div
                  style={{
                    marginTop: 'var(--space-4)',
                    padding: '10px 14px',
                    borderRadius: 'var(--radius-md)',
                    background: 'rgba(255,255,255,0.03)',
                    border: '1px solid var(--color-border)',
                    fontSize: '0.78rem',
                    color: 'var(--color-text-muted)',
                  }}
                >
                  ℹ️ <strong>Data Limitation:</strong> Win rates represent historical outcomes under prior pitch wear, weather, and day/night schedules. Captains should prioritize fresh surface moisture, cloud cover, and specific squad balance.
                </div>
              </div>
            </div>

            {/* Recent Match Log at this Venue */}
            {decision.recentMatches && decision.recentMatches.length > 0 && (
              <div
                style={{
                  background: 'var(--color-bg-card)',
                  padding: '24px',
                  borderRadius: 'var(--radius-lg)',
                  border: '1px solid var(--color-border)',
                }}
              >
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 'var(--space-4)' }}>
                  <h3 style={{ fontSize: '1rem', color: 'var(--color-text-primary)' }}>
                    Matches at {decision.venue} in 2-Year Window ({decision.recentMatches.length})
                  </h3>
                  <span style={{ fontSize: '0.8rem', color: 'var(--color-text-muted)' }}>
                    Chronological latest first
                  </span>
                </div>

                <div style={{ overflowX: 'auto' }}>
                  <table
                    style={{
                      width: '100%',
                      borderCollapse: 'collapse',
                      fontSize: '0.88rem',
                      textAlign: 'left',
                    }}
                  >
                    <thead>
                      <tr style={{ borderBottom: '1px solid var(--color-border)', color: 'var(--color-text-secondary)' }}>
                        <th style={{ padding: '10px 12px' }}>Date</th>
                        <th style={{ padding: '10px 12px' }}>Teams</th>
                        <th style={{ padding: '10px 12px' }}>Toss Winner & Choice</th>
                        <th style={{ padding: '10px 12px' }}>1st Innings</th>
                        <th style={{ padding: '10px 12px' }}>Winner</th>
                        <th style={{ padding: '10px 12px' }}>Advantage</th>
                        <th style={{ padding: '10px 12px' }}>Status</th>
                      </tr>
                    </thead>
                    <tbody>
                      {decision.recentMatches.map((m) => (
                        <tr
                          key={m.matchId}
                          style={{
                            borderBottom: '1px solid var(--color-border)',
                            transition: 'background var(--transition-fast)',
                          }}
                          onMouseEnter={(e) => { e.currentTarget.style.background = '#f8fafc'; }}
                          onMouseLeave={(e) => { e.currentTarget.style.background = 'transparent'; }}
                        >
                          <td style={{ padding: '10px 12px', color: 'var(--color-text-secondary)', whiteSpace: 'nowrap' }}>
                            {m.date}
                          </td>
                          <td style={{ padding: '10px 12px', fontWeight: 600 }}>
                            {m.team1} vs {m.team2}
                          </td>
                          <td style={{ padding: '10px 12px', color: '#94A3B8' }}>
                            {m.tossWinner} ({m.tossDecision})
                          </td>
                          <td style={{ padding: '10px 12px' }}>
                            {m.firstInningsTeam}
                          </td>
                          <td style={{ padding: '10px 12px', fontWeight: 600, color: m.winner ? 'var(--color-text-primary)' : 'var(--color-text-muted)' }}>
                            {m.winner || '—'}
                          </td>
                          <td style={{ padding: '10px 12px' }}>
                            {m.batFirstWon === 1 ? (
                              <span style={{ color: '#15803d', fontWeight: 600, fontSize: '0.8rem' }}>Bat First</span>
                            ) : m.chaseWon === 1 ? (
                              <span style={{ color: '#1d4ed8', fontWeight: 600, fontSize: '0.8rem' }}>Chasing</span>
                            ) : (
                              <span style={{ color: '#94A3B8', fontSize: '0.8rem' }}>Non-decisive</span>
                            )}
                          </td>
                          <td style={{ padding: '10px 12px' }}>
                            <span
                              style={{
                                fontSize: '0.75rem',
                                padding: '2px 8px',
                                borderRadius: 'var(--radius-sm)',
                                background: m.isDecisive ? '#dcfce7' : '#fef3c7',
                                color: m.isDecisive ? '#15803d' : '#92400e',
                                border: m.isDecisive ? '1px solid #86efac' : '1px solid #fde68a',
                              }}
                            >
                              {m.resultType}
                            </span>
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              </div>
            )}
          </div>
        )}
      </div>
    </DashboardLayout>
  );
}
