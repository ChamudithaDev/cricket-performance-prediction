/**
 * Login.jsx
 *
 * Professional split-panel login page for CricketAI.
 * Left: Simplified branding with minimal geometric identity and 3 core capabilities.
 * Right: Refined liquid-glass decorative backdrop with clean, high-contrast form card.
 * Calls Spring Boot POST /api/auth/login via AuthContext.
 */

import { useState } from 'react';
import { Link, useNavigate, useLocation } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';

export default function Login() {
  const { login } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();

  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [showPassword, setShowPassword] = useState(false);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

  // Redirect to the page the user originally tried to visit, or dashboard
  const intendedPath = location.state?.from?.pathname || '/dashboard';

  const handleSubmit = async (e) => {
    e.preventDefault();
    setError('');

    if (!email.trim()) { setError('Please enter your email address.'); return; }
    if (!password) { setError('Please enter your password.'); return; }

    setLoading(true);
    try {
      await login(email.trim(), password);
      navigate(intendedPath, { replace: true });
    } catch (err) {
      const status = err.response?.status;
      if (status === 401 || status === 403) {
        setError('Incorrect email or password. Please try again.');
      } else if (status === 400) {
        setError(err.response?.data?.message || 'Invalid input. Please check your details.');
      } else if (!err.response) {
        setError('Cannot connect to the server. Please ensure the backend is running.');
      } else {
        setError('An unexpected error occurred. Please try again.');
      }
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="auth-page">
      {/* ── Left panel: Simplified Branding ── */}
      <div className="auth-page__brand" aria-hidden="true">
        <div className="auth-page__brand-inner">
          <div className="auth-brand__header">
            <div className="auth-brand__mark" aria-hidden="true">
              <svg viewBox="0 0 32 32" width="32" height="32" fill="none">
                <rect width="32" height="32" rx="8" fill="#1e293b" />
                <path d="M8 16 C 8 10, 24 10, 24 16" stroke="#38bdf8" strokeWidth="2.2" strokeLinecap="round" />
                <path d="M8 16 C 8 22, 24 22, 24 16" stroke="#2563eb" strokeWidth="2.2" strokeLinecap="round" />
                <circle cx="16" cy="16" r="2.5" fill="#ffffff" />
              </svg>
            </div>
            <h2 className="auth-brand__name">CricketAI</h2>
          </div>

          <p className="auth-brand__tagline">
            Cricket Player Performance Analytics
          </p>

          <div className="auth-brand__features">
            <div className="auth-brand__feature">
              <div className="auth-brand__feature-icon-wrapper" aria-hidden="true">
                <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                  <path d="M18 20V10M12 20V4M6 20v-6" />
                </svg>
              </div>
              <div>
                <h3 className="auth-brand__feature-title">Performance Predictions</h3>
                <p className="auth-brand__feature-desc">Pre-match run estimates and performance tier forecasts across formats</p>
              </div>
            </div>

            <div className="auth-brand__feature">
              <div className="auth-brand__feature-icon-wrapper" aria-hidden="true">
                <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                  <polyline points="22 12 18 12 15 21 9 3 6 12 2 12" />
                </svg>
              </div>
              <div>
                <h3 className="auth-brand__feature-title">Player Analytics</h3>
                <p className="auth-brand__feature-desc">Historical match-by-match trendlines, role statistics, and consistency tracking</p>
              </div>
            </div>

            <div className="auth-brand__feature">
              <div className="auth-brand__feature-icon-wrapper" aria-hidden="true">
                <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                  <line x1="18" y1="20" x2="18" y2="10" />
                  <line x1="12" y1="20" x2="12" y2="4" />
                  <line x1="6" y1="20" x2="6" y2="14" />
                </svg>
              </div>
              <div>
                <h3 className="auth-brand__feature-title">Player Comparison</h3>
                <p className="auth-brand__feature-desc">Side-by-side metric benchmarking and head-to-head performance analysis</p>
              </div>
            </div>
          </div>
        </div>
      </div>

      {/* ── Right panel: Liquid Glass Treatment & Form ── */}
      <div className="auth-page__form-panel">
        {/* Liquid Glass Decorative Backdrop */}
        <div className="liquid-glass-backdrop" aria-hidden="true">
          <div className="liquid-glass-shape liquid-glass-shape--1" />
          <div className="liquid-glass-shape liquid-glass-shape--2" />
          <div className="liquid-glass-shape liquid-glass-shape--3" />
          <div className="liquid-glass-ribbon" />
        </div>

        <div className="auth-card">
          <div className="auth-card__header">
            <div className="auth-card__brand-mobile" aria-hidden="true">
              <svg viewBox="0 0 28 28" width="24" height="24" fill="none">
                <rect width="28" height="28" rx="6" fill="#1e293b" />
                <path d="M7 14 C 7 9, 21 9, 21 14" stroke="#38bdf8" strokeWidth="2" strokeLinecap="round" />
                <path d="M7 14 C 7 19, 21 19, 21 14" stroke="#2563eb" strokeWidth="2" strokeLinecap="round" />
                <circle cx="14" cy="14" r="2" fill="#ffffff" />
              </svg>
              <span className="auth-card__mobile-name">CricketAI</span>
            </div>
            <h1 className="auth-card__title">Sign In</h1>
            <p className="auth-card__subtitle">
              Enter your credentials to access the analytics workspace.
            </p>
          </div>

          {error && (
            <div className="auth-card__error" role="alert">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
                <circle cx="12" cy="12" r="10" />
                <line x1="12" y1="8" x2="12" y2="12" />
                <line x1="12" y1="16" x2="12.01" y2="16" />
              </svg>
              {error}
            </div>
          )}

          <form className="auth-card__form" onSubmit={handleSubmit} noValidate>
            {/* Email */}
            <div className="form-group">
              <label className="form-label" htmlFor="login-email">Email Address</label>
              <input
                id="login-email"
                type="email"
                className="form-input"
                placeholder="name@organization.com"
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                autoComplete="email"
                required
                disabled={loading}
              />
            </div>

            {/* Password */}
            <div className="form-group">
              <div className="form-label-row">
                <label className="form-label" htmlFor="login-password">Password</label>
              </div>
              <div className="form-input-group">
                <input
                  id="login-password"
                  type={showPassword ? 'text' : 'password'}
                  className="form-input form-input--with-btn"
                  placeholder="Enter your password"
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                  autoComplete="current-password"
                  required
                  disabled={loading}
                />
                <button
                  type="button"
                  className="form-input-reveal"
                  onClick={() => setShowPassword((v) => !v)}
                  aria-label={showPassword ? 'Hide password' : 'Show password'}
                >
                  {showPassword ? (
                    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round">
                      <path d="M17.94 17.94A10.07 10.07 0 0 1 12 20c-7 0-11-8-11-8a18.45 18.45 0 0 1 5.06-5.94" />
                      <path d="M9.9 4.24A9.12 9.12 0 0 1 12 4c7 0 11 8 11 8a18.5 18.5 0 0 1-2.16 3.19" />
                      <line x1="1" y1="1" x2="23" y2="23" />
                    </svg>
                  ) : (
                    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round">
                      <path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z" />
                      <circle cx="12" cy="12" r="3" />
                    </svg>
                  )}
                </button>
              </div>
            </div>

            <button
              type="submit"
              className="btn btn--primary btn--full"
              disabled={loading}
            >
              {loading ? (
                <>
                  <span className="spinner spinner--sm spinner--light" aria-hidden="true" />
                  Authenticating...
                </>
              ) : 'Sign In'}
            </button>
          </form>

          <p className="auth-card__footer">
            Don&apos;t have an account?{' '}
            <Link to="/register" className="auth-card__link">Create account</Link>
          </p>
        </div>
      </div>
    </div>
  );
}
