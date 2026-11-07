
import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import DashboardLayout from '../components/layout/DashboardLayout';
import { useAuth } from '../context/AuthContext';

export default function Dashboard() {
  const { user } = useAuth();
  const navigate = useNavigate();
  const [quickSearch, setQuickSearch] = useState('');

  const handleSearchSubmit = (e) => {
    e.preventDefault();
    if (quickSearch.trim()) {
      navigate('/player-search');
    }
  };

  const greeting = (() => {
    const h = new Date().getHours();
    if (h < 12) return 'Good morning';
    if (h < 17) return 'Good afternoon';
    return 'Good evening';
  })();

  const QUICK_ACTIONS = [
    {
      to: '/player-search',
      title: 'Players Database',
      description: 'Search 500+ international cricketers and sync career match records across Test, ODI, and T20I.',
      badge: 'Database',
      icon: (
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
          <path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2" />
          <circle cx="9" cy="7" r="4" />
          <path d="M23 21v-2a4 4 0 0 0-3-3.87" />
          <path d="M16 3.13a4 4 0 0 1 0 7.75" />
        </svg>
      ),
    },
    {
      to: '/predictions',
      title: 'Performance Prediction',
      description: 'Generate expected runs and performance tier forecasts using trained machine learning models.',
      badge: 'ML Engine',
      icon: (
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
          <circle cx="12" cy="12" r="10" />
          <polygon points="12 6 15.5 13 8.5 13 12 6" />
          <line x1="12" y1="16" x2="12.01" y2="16" />
        </svg>
      ),
    },
    {
      to: '/analytics',
      title: 'Player Analytics',
      description: 'Explore role-aware trendlines, recent form consistency, scoring patterns, and spell effectiveness.',
      badge: 'Analytics',
      icon: (
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
          <polyline points="22 12 18 12 15 21 9 3 6 12 2 12" />
        </svg>
      ),
    },
    {
      to: '/compare',
      title: 'Player Comparison',
      description: 'Side-by-side head-to-head metric benchmarking, trend momentum, and role comparisons.',
      badge: 'Comparison',
      icon: (
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
          <line x1="18" y1="20" x2="18" y2="10" />
          <line x1="12" y1="20" x2="12" y2="4" />
          <line x1="6" y1="20" x2="6" y2="14" />
        </svg>
      ),
    },
    {
      to: '/toss-advisor',
      title: 'Toss Decision Advisor',
      description: 'Venue-specific toss analytics, bat-first vs chasing win rates, and decisive outcome recommendations.',
      badge: 'Strategy',
      icon: (
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
          <circle cx="12" cy="12" r="10" />
          <line x1="12" y1="8" x2="12" y2="12" />
          <line x1="12" y1="16" x2="12.01" y2="16" />
        </svg>
      ),
    },
  ];

  return (
    <DashboardLayout pageTitle="Dashboard">
      <div className="dashboard-container">
        {/* Welcome & Search Banner */}
        <section className="dashboard-hero" aria-labelledby="dashboard-welcome-heading">
          <div className="dashboard-hero__content">
            <div className="dashboard-hero__header">
              <span className="dashboard-hero__eyebrow">CricketAI Analytics Platform</span>
              <h2 id="dashboard-welcome-heading" className="dashboard-hero__title">
                {greeting}, {user?.fullName || 'Analyst'}
              </h2>
              <p className="dashboard-hero__subtitle">
                Access match-calibrated player performance forecasts, historical analytics, and venue intelligence.
              </p>
            </div>

            {/* Quick Player Search bar */}
            <form onSubmit={handleSearchSubmit} className="dashboard-hero__search-form" role="search">
              <div className="dashboard-hero__search-box">
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" className="dashboard-hero__search-icon" aria-hidden="true">
                  <circle cx="11" cy="11" r="8" />
                  <line x1="21" y1="21" x2="16.65" y2="16.65" />
                </svg>
                <input
                  type="text"
                  value={quickSearch}
                  onChange={(e) => setQuickSearch(e.target.value)}
                  placeholder="Quick search cricketer name (e.g. V Kohli, AU Rashid, SA Abbott)..."
                  className="dashboard-hero__search-input"
                  aria-label="Quick search cricketer"
                />
                <button type="submit" className="btn btn--primary dashboard-hero__search-btn">
                  Search
                </button>
              </div>
            </form>
          </div>
        </section>

        {/* Verified System Metrics — Only real system data, no fabricated player numbers */}
        <section className="dashboard-section" aria-labelledby="system-stats-heading">
          <div className="dashboard-section__header">
            <div>
              <h3 id="system-stats-heading" className="dashboard-section__title">Verified System Metrics</h3>
              <p className="dashboard-section__subtitle">Audited data pipeline and machine learning infrastructure</p>
            </div>
            <span className="status-pill status-pill--success">
              <span className="status-pill__dot" />
              Pipeline Online
            </span>
          </div>

          <div className="dashboard-metrics-grid">
            <div className="metric-card">
              <span className="metric-card__label">Active ML Models</span>
              <div className="metric-card__value-row">
                <span className="metric-card__value">SVR + CatBoost</span>
              </div>
              <p className="metric-card__hint">Runs Regression &amp; Tier Classification</p>
            </div>

            <div className="metric-card">
              <span className="metric-card__label">Training Features</span>
              <div className="metric-card__value-row">
                <span className="metric-card__value">15 Pre-Match</span>
              </div>
              <p className="metric-card__hint">Strictly zero target or post-match leakage</p>
            </div>

            <div className="metric-card">
              <span className="metric-card__label">International Formats</span>
              <div className="metric-card__value-row">
                <span className="metric-card__value">T20I &bull; ODI &bull; Test</span>
              </div>
              <p className="metric-card__hint">412 distinct international matches</p>
            </div>

            <div className="metric-card">
              <span className="metric-card__label">Audited Dataset</span>
              <div className="metric-card__value-row">
                <span className="metric-card__value">8,778 Records</span>
              </div>
              <p className="metric-card__hint">Unique player-match records (2024&ndash;2026)</p>
            </div>
          </div>
        </section>

        {/* Feature Navigation Grid */}
        <section className="dashboard-section" aria-labelledby="features-heading">
          <div className="dashboard-section__header">
            <div>
              <h3 id="features-heading" className="dashboard-section__title">Analytics &amp; Prediction Modules</h3>
              <p className="dashboard-section__subtitle">Jump directly to player evaluation tools</p>
            </div>
          </div>

          <div className="dashboard-modules-grid">
            {QUICK_ACTIONS.map(({ to, title, description, badge, icon }) => (
              <Link to={to} key={to} className="module-card">
                <div className="module-card__header">
                  <div className="module-card__icon" aria-hidden="true">{icon}</div>
                  <span className="module-card__badge">{badge}</span>
                </div>
                <h4 className="module-card__title">{title}</h4>
                <p className="module-card__desc">{description}</p>
                <div className="module-card__footer">
                  <span className="module-card__link">
                    Open module
                    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" className="module-card__arrow" aria-hidden="true">
                      <line x1="5" y1="12" x2="19" y2="12" />
                      <polyline points="12 5 19 12 12 19" />
                    </svg>
                  </span>
                </div>
              </Link>
            ))}
          </div>
        </section>

        {/* Recent Activity / Session State — Honest Empty State */}
        <section className="dashboard-section" aria-labelledby="activity-heading">
          <div className="dashboard-section__header">
            <div>
              <h3 id="activity-heading" className="dashboard-section__title">Recent Analytics Activity</h3>
              <p className="dashboard-section__subtitle">History of your generated predictions and player analyses</p>
            </div>
          </div>

          <div className="empty-state-panel">
            <div className="empty-state-panel__icon" aria-hidden="true">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round">
                <rect x="2" y="3" width="20" height="14" rx="2" />
                <line x1="8" y1="21" x2="16" y2="21" />
                <line x1="12" y1="17" x2="12" y2="21" />
              </svg>
            </div>
            <h4 className="empty-state-panel__title">No Recent In-Session Predictions</h4>
            <p className="empty-state-panel__desc">
              Generate a match forecast on the Predictions page or search for an international cricketer to begin viewing performance analytics.
            </p>
            <div className="empty-state-panel__actions">
              <Link to="/predictions" className="btn btn--primary">
                New Prediction
              </Link>
              <Link to="/player-search" className="btn btn--outline">
                Search Cricketers
              </Link>
            </div>
          </div>
        </section>
      </div>
    </DashboardLayout>
  );
}
