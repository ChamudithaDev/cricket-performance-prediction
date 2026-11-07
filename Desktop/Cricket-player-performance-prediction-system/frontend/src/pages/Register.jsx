/**
 * Register.jsx
 *
 * Professional split-panel registration page for CricketAI.
 * Matches Spring Boot AuthRequest fields exactly: fullName, email, password.
 * Left: Simplified branding with minimal geometric identity and 3 core capabilities.
 * Right: Refined liquid-glass decorative backdrop with clean, high-contrast form card.
 * Calls Spring Boot POST /api/auth/register via AuthContext.register().
 */

import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';

export default function Register() {
  const { register } = useAuth();
  const navigate = useNavigate();

  const [form, setForm] = useState({ fullName: '', email: '', password: '' });
  const [showPassword, setShowPassword] = useState(false);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const [fieldErrors, setFieldErrors] = useState({});

  const handleChange = (e) => {
    setForm((f) => ({ ...f, [e.target.name]: e.target.value }));
    setFieldErrors((fe) => ({ ...fe, [e.target.name]: '' }));
  };

  const validate = () => {
    const errs = {};
    if (!form.fullName.trim()) errs.fullName = 'Full name is required.';
    if (!form.email.trim()) errs.email = 'Email address is required.';
    else if (!/\S+@\S+\.\S+/.test(form.email)) errs.email = 'Please enter a valid email address.';
    if (!form.password) errs.password = 'Password is required.';
    else if (form.password.length < 6) errs.password = 'Password must be at least 6 characters.';
    return errs;
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    setError('');

    const errs = validate();
    if (Object.keys(errs).length) { setFieldErrors(errs); return; }

    setLoading(true);
    try {
      await register(form.fullName.trim(), form.email.trim(), form.password);
      navigate('/dashboard', { replace: true });
    } catch (err) {
      const status = err.response?.status;
      const msg = err.response?.data?.message || err.response?.data;
      if (status === 409 || (typeof msg === 'string' && msg.toLowerCase().includes('already'))) {
        setError('An account with this email already exists. Please sign in instead.');
      } else if (status === 400) {
        setError(typeof msg === 'string' ? msg : 'Invalid input. Please check your details.');
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
            <h1 className="auth-card__title">Create Account</h1>
            <p className="auth-card__subtitle">
              Register to access the cricket analytics workspace.
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
            {/* Full name */}
            <div className="form-group">
              <label className="form-label" htmlFor="reg-fullname">Full Name</label>
              <input
                id="reg-fullname"
                name="fullName"
                type="text"
                className={`form-input${fieldErrors.fullName ? ' form-input--error' : ''}`}
                placeholder="First and last name"
                value={form.fullName}
                onChange={handleChange}
                autoComplete="name"
                disabled={loading}
              />
              {fieldErrors.fullName && (
                <span className="form-error" role="alert">{fieldErrors.fullName}</span>
              )}
            </div>

            {/* Email */}
            <div className="form-group">
              <label className="form-label" htmlFor="reg-email">Email Address</label>
              <input
                id="reg-email"
                name="email"
                type="email"
                className={`form-input${fieldErrors.email ? ' form-input--error' : ''}`}
                placeholder="name@organization.com"
                value={form.email}
                onChange={handleChange}
                autoComplete="email"
                disabled={loading}
              />
              {fieldErrors.email && (
                <span className="form-error" role="alert">{fieldErrors.email}</span>
              )}
            </div>

            {/* Password */}
            <div className="form-group">
              <label className="form-label" htmlFor="reg-password">Password</label>
              <div className="form-input-group">
                <input
                  id="reg-password"
                  name="password"
                  type={showPassword ? 'text' : 'password'}
                  className={`form-input form-input--with-btn${fieldErrors.password ? ' form-input--error' : ''}`}
                  placeholder="Minimum 6 characters"
                  value={form.password}
                  onChange={handleChange}
                  autoComplete="new-password"
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
              {fieldErrors.password && (
                <span className="form-error" role="alert">{fieldErrors.password}</span>
              )}
            </div>

            <button
              type="submit"
              className="btn btn--primary btn--full"
              disabled={loading}
            >
              {loading ? (
                <>
                  <span className="spinner spinner--sm spinner--light" aria-hidden="true" />
                  Creating Account...
                </>
              ) : 'Create Account'}
            </button>
          </form>

          <p className="auth-card__footer">
            Already have an account?{' '}
            <Link to="/login" className="auth-card__link">Sign in</Link>
          </p>
        </div>
      </div>
    </div>
  );
}
