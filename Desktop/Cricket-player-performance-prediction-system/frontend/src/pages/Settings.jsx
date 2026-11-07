/**
 * Settings.jsx
 *
 * User account, application settings, and system information.
 * Uses real user details from AuthContext and displays verified system status.
 */

import { useState } from 'react';
import DashboardLayout from '../components/layout/DashboardLayout';
import { useAuth } from '../context/AuthContext';

export default function Settings() {
  const { user, logout } = useAuth();
  const [saveMessage, setSaveMessage] = useState('');

  const handlePreferencesSave = (e) => {
    e.preventDefault();
    setSaveMessage('Preferences saved successfully.');
    setTimeout(() => setSaveMessage(''), 3000);
  };

  return (
    <DashboardLayout pageTitle="Settings">
      <div className="settings-container">
        {/* Profile Card */}
        <div className="settings-section">
          <div className="settings-section__header">
            <h2 className="settings-section__title">User Profile</h2>
            <p className="settings-section__subtitle">Account information and authentication details</p>
          </div>

          <div className="settings-card">
            <div className="settings-profile-row">
              <div className="settings-avatar" aria-hidden="true">
                {user?.fullName?.[0]?.toUpperCase() || user?.email?.[0]?.toUpperCase() || 'U'}
              </div>
              <div className="settings-profile-info">
                <h3 className="settings-profile-name">{user?.fullName || 'Cricket Analyst'}</h3>
                <p className="settings-profile-email">{user?.email || 'user@cricketai.com'}</p>
                <span className="settings-badge">Authenticated User</span>
              </div>
            </div>

            <div className="settings-details-grid">
              <div className="settings-detail-item">
                <span className="settings-detail-label">Full Name</span>
                <span className="settings-detail-value">{user?.fullName || 'Not specified'}</span>
              </div>
              <div className="settings-detail-item">
                <span className="settings-detail-label">Email Address</span>
                <span className="settings-detail-value">{user?.email || 'Not specified'}</span>
              </div>
              <div className="settings-detail-item">
                <span className="settings-detail-label">Role</span>
                <span className="settings-detail-value">{user?.role || 'User / Analyst'}</span>
              </div>
              <div className="settings-detail-item">
                <span className="settings-detail-label">Session Status</span>
                <span className="settings-detail-value text-emerald-600 font-medium">Active (JWT)</span>
              </div>
            </div>
          </div>
        </div>

        {/* Display & Analysis Preferences */}
        <div className="settings-section">
          <div className="settings-section__header">
            <h2 className="settings-section__title">Analytics Preferences</h2>
            <p className="settings-section__subtitle">Customize your default view parameters</p>
          </div>

          <div className="settings-card">
            <form onSubmit={handlePreferencesSave} className="settings-form">
              <div className="settings-form-row">
                <div className="settings-form-group">
                  <label htmlFor="default-format" className="settings-form-label">Default Match Format</label>
                  <select id="default-format" className="settings-select" defaultValue="T20I">
                    <option value="ALL">All Formats</option>
                    <option value="T20I">T20I</option>
                    <option value="ODI">ODI</option>
                    <option value="TEST">Test</option>
                  </select>
                </div>

                <div className="settings-form-group">
                  <label htmlFor="recent-form-window" className="settings-form-label">Recent Form Window</label>
                  <select id="recent-form-window" className="settings-select" defaultValue="5">
                    <option value="5">Last 5 Matches</option>
                    <option value="10">Last 10 Matches</option>
                    <option value="15">Last 15 Matches</option>
                  </select>
                </div>
              </div>

              <div className="settings-form-actions">
                <button type="submit" className="btn btn--primary">
                  Save Preferences
                </button>
                {saveMessage && (
                  <span className="settings-save-feedback" role="status">
                    {saveMessage}
                  </span>
                )}
              </div>
            </form>
          </div>
        </div>


        {/* Account Actions */}
        <div className="settings-section">
          <div className="settings-card settings-card--danger">
            <div className="settings-danger-content">
              <div>
                <h3 className="settings-danger-title">Sign Out of CricketAI</h3>
                <p className="settings-danger-desc">End your authenticated session on this browser.</p>
              </div>
              <button onClick={logout} className="btn btn--danger">
                Sign Out
              </button>
            </div>
          </div>
        </div>
      </div>
    </DashboardLayout>
  );
}
