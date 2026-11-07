/**
 * PlayerProfile.jsx
 *
 * Displays a player's biographical profile and complete career statistics
 * grouped by format (Test, ODI, T20I, IPL).
 * Data is loaded from the local MySQL database via Spring Boot.
 */

import { useState, useEffect, useCallback } from 'react';
import { useParams, Link } from 'react-router-dom';
import DashboardLayout from '../components/layout/DashboardLayout';
import LoadingSpinner from '../components/common/LoadingSpinner';
import {
  getPlayerProfile,
  syncPlayer,
  getFriendlyErrorMessage,
  formatPlayerDate,
  displayValue,
} from '../services/playerProfileService';

export default function PlayerProfile() {
  const { id } = useParams();

  const [profile, setProfile] = useState(null);
  const [isLoading, setIsLoading] = useState(true);
  const [isRefreshing, setIsRefreshing] = useState(false);
  const [errorMessage, setErrorMessage] = useState('');
  const [successBanner, setSuccessBanner] = useState('');
  const [activeFormat, setActiveFormat] = useState('TEST');
  const [imageError, setImageError] = useState(false);

  // Load profile from local database
  const loadProfile = useCallback(async () => {
    setIsLoading(true);
    setErrorMessage('');
    try {
      const data = await getPlayerProfile(id);
      setProfile(data);

      // Set initial format tab
      if (data?.careerStatistics) {
        const formats = Object.keys(data.careerStatistics);
        if (formats.length > 0) {
          // Default to TEST or ODI if available, otherwise first format
          if (formats.includes('TEST')) setActiveFormat('TEST');
          else if (formats.includes('ODI')) setActiveFormat('ODI');
          else setActiveFormat(formats[0]);
        }
      }
    } catch (err) {
      setErrorMessage(getFriendlyErrorMessage(err));
    } finally {
      setIsLoading(false);
    }
  }, [id]);

  useEffect(() => {
    loadProfile();
  }, [loadProfile]);

  // Re-sync with CricAPI through Spring Boot
  const handleRefreshData = async () => {
    if (!profile?.externalPlayerId) return;

    setIsRefreshing(true);
    setErrorMessage('');
    setSuccessBanner('');

    try {
      const updated = await syncPlayer(profile.externalPlayerId);
      setProfile(updated);
      setSuccessBanner('Player data updated successfully.');
      setTimeout(() => setSuccessBanner(''), 4000);
    } catch (err) {
      setErrorMessage(getFriendlyErrorMessage(err));
    } finally {
      setIsRefreshing(false);
    }
  };

  // Safe stat formatter
  const statVal = (val) => (val === null || val === undefined ? '-' : val);

  return (
    <DashboardLayout pageTitle={profile?.name || 'Player Profile'}>
      <div className="player-profile-page">
        {/* Top breadcrumb & back bar */}
        <div className="player-profile__nav-bar">
          <Link to="/player-search" className="player-profile__back-link">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" style={{ width: 16, height: 16 }}>
              <line x1="19" y1="12" x2="5" y2="12" />
              <polyline points="12 19 5 12 12 5" />
            </svg>
            <span>Back to Player Search</span>
          </Link>

          {profile?.externalPlayerId && (
            <button
              type="button"
              className="btn btn--secondary player-profile__refresh-btn"
              onClick={handleRefreshData}
              disabled={isRefreshing || isLoading}
              title="Sync latest profile and career stats from CricAPI"
            >
              {isRefreshing ? (
                <>
                  <span className="spinner spinner--sm spinner--light" aria-hidden="true" />
                  <span>Syncing Latest Data...</span>
                </>
              ) : (
                <>
                  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" style={{ width: 15, height: 15 }}>
                    <path d="M21.5 2v6h-6M21.34 15.57a10 10 0 1 1-.57-8.38l5.67-5.67" />
                  </svg>
                  <span>Refresh Player Data</span>
                </>
              )}
            </button>
          )}
        </div>

        {/* Success toast / banner */}
        {successBanner && (
          <div className="player-search__alert player-search__alert--success" role="status">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
              <path d="M22 11.08V12a10 10 0 1 1-5.93-9.14" />
              <polyline points="22 4 12 14.01 9 11.01" />
            </svg>
            <span>{successBanner}</span>
          </div>
        )}

        {/* Error message */}
        {errorMessage && (
          <div className="player-search__alert player-search__alert--error" role="alert">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
              <circle cx="12" cy="12" r="10" />
              <line x1="12" y1="8" x2="12" y2="12" />
              <line x1="12" y1="16" x2="12.01" y2="16" />
            </svg>
            <span>{errorMessage}</span>
          </div>
        )}

        {/* Loading state */}
        {isLoading && (
          <div className="player-profile__loading-panel">
            <LoadingSpinner label="Loading player profile from database..." />
            <p className="player-profile__loading-sub">Retrieving biographical details and statistics records...</p>
          </div>
        )}

        {/* Content loaded */}
        {!isLoading && profile && (
          <>
            {/* Header Hero Card */}
            <div className="player-profile__header-card">
              <div className="player-profile__avatar-container">
                {profile.profileImageUrl && !imageError ? (
                  <img
                    src={profile.profileImageUrl}
                    alt={profile.name}
                    className="player-profile__avatar-img"
                    onError={() => setImageError(true)}
                  />
                ) : (
                  <div className="player-profile__avatar-fallback" aria-hidden="true">
                    <span>{profile.name?.[0]?.toUpperCase() || 'P'}</span>
                  </div>
                )}
              </div>

              <div className="player-profile__hero-content">
                <div className="player-profile__title-row">
                  <div>
                    <h2 className="player-profile__name">{profile.name}</h2>
                    {profile.fullName && profile.fullName !== profile.name && (
                      <p className="player-profile__fullname">{profile.fullName}</p>
                    )}
                  </div>

                  <div className="player-profile__badges">
                    {profile.nationality && (
                      <span className="profile-badge profile-badge--country">
                        {profile.nationality}
                      </span>
                    )}
                    {profile.role && (
                      <span className="profile-badge profile-badge--role">
                        {profile.role}
                      </span>
                    )}
                  </div>
                </div>

                {/* Key metadata grid */}
                <div className="player-profile__meta-grid">
                  <div className="meta-item">
                    <span className="meta-item__label">Date of Birth</span>
                    <span className="meta-item__val">{formatPlayerDate(profile.dateOfBirth)}</span>
                  </div>

                  <div className="meta-item">
                    <span className="meta-item__label">Place of Birth</span>
                    <span className="meta-item__val">{displayValue(profile.placeOfBirth)}</span>
                  </div>

                  <div className="meta-item">
                    <span className="meta-item__label">Batting Style</span>
                    <span className="meta-item__val">{displayValue(profile.battingStyle)}</span>
                  </div>

                  <div className="meta-item">
                    <span className="meta-item__label">Bowling Style</span>
                    <span className="meta-item__val">{displayValue(profile.bowlingStyle)}</span>
                  </div>

                  <div className="meta-item">
                    <span className="meta-item__label">Current Team</span>
                    <span className="meta-item__val">{displayValue(profile.currentTeam)}</span>
                  </div>

                  <div className="meta-item">
                    <span className="meta-item__label">External Registry ID</span>
                    <span className="meta-item__val meta-item__val--mono" title={profile.externalPlayerId || ''}>
                      {profile.externalPlayerId ? `${profile.externalPlayerId.substring(0, 13)}...` : 'Not linked'}
                    </span>
                  </div>
                </div>
              </div>
            </div>

            {/* Career Statistics Section */}
            <section className="player-profile__stats-section" aria-labelledby="stats-heading">
              <div className="player-profile__stats-header">
                <div>
                  <h3 id="stats-heading" className="player-profile__stats-title">Career Statistics</h3>
                  <p className="player-profile__stats-subtitle">Official match records aggregated across formats</p>
                </div>

                {/* Format selection tabs */}
                {profile.careerStatistics && Object.keys(profile.careerStatistics).length > 0 && (
                  <div className="format-tabs" role="tablist" aria-label="Cricket Formats">
                    {Object.keys(profile.careerStatistics).map((fmt) => (
                      <button
                        key={fmt}
                        role="tab"
                        aria-selected={activeFormat === fmt}
                        className={`format-tab-btn${activeFormat === fmt ? ' format-tab-btn--active' : ''}`}
                        onClick={() => setActiveFormat(fmt)}
                      >
                        {fmt}
                      </button>
                    ))}
                  </div>
                )}
              </div>

              {/* Statistics details for active format */}
              {(!profile.careerStatistics || Object.keys(profile.careerStatistics).length === 0) ? (
                <div className="empty-state">
                  <div className="empty-state__icon" aria-hidden="true"><svg viewBox="0 0 24 24" width="36" height="36" fill="none" stroke="currentColor" strokeWidth="1.5"><path d="M18 20V10M12 20V4M6 20v-6"/></svg></div>
                  <p className="empty-state__title">No career statistics available</p>
                  <p className="empty-state__sub">
                    No format records are currently stored for this player. Click &ldquo;Refresh Player Data&rdquo; above to synchronize stats.
                  </p>
                </div>
              ) : !profile.careerStatistics[activeFormat] ? (
                <div className="empty-state">
                  <div className="empty-state__icon" aria-hidden="true"><svg viewBox="0 0 24 24" width="36" height="36" fill="none" stroke="currentColor" strokeWidth="1.5"><circle cx="12" cy="12" r="10"/></svg></div>
                  <p className="empty-state__title">No {activeFormat} statistics recorded</p>
                  <p className="empty-state__sub">
                    This cricketer does not have recorded match appearances in {activeFormat} cricket.
                  </p>
                </div>
              ) : (
                (() => {
                  const stats = profile.careerStatistics[activeFormat];
                  return (
                    <div className="format-stats-container">
                      {/* Batting Card */}
                      <div className="stats-card">
                        <div className="stats-card__header">
                          <div className="stats-card__icon" aria-hidden="true"><svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" strokeWidth="2"><circle cx="12" cy="12" r="9"/></svg></div>
                          <div>
                            <h4 className="stats-card__title">Batting & Fielding</h4>
                            <p className="stats-card__subtitle">{activeFormat} Career</p>
                          </div>
                        </div>

                        <div className="stats-metrics-grid">
                          <div className="metric-box metric-box--highlight">
                            <span className="metric-box__label">Total Runs</span>
                            <span className="metric-box__value">{statVal(stats.runs)}</span>
                          </div>
                          <div className="metric-box metric-box--highlight">
                            <span className="metric-box__label">Batting Avg</span>
                            <span className="metric-box__value">
                              {stats.average !== null && stats.average !== undefined ? stats.average.toFixed(2) : '-'}
                            </span>
                          </div>
                          <div className="metric-box">
                            <span className="metric-box__label">Highest Score</span>
                            <span className="metric-box__value">{statVal(stats.highestScore)}</span>
                          </div>
                          <div className="metric-box">
                            <span className="metric-box__label">Strike Rate</span>
                            <span className="metric-box__value">
                              {stats.strikeRate !== null && stats.strikeRate !== undefined ? stats.strikeRate.toFixed(2) : '-'}
                            </span>
                          </div>

                          <div className="metric-box">
                            <span className="metric-box__label">Matches</span>
                            <span className="metric-box__value">{statVal(stats.matches)}</span>
                          </div>
                          <div className="metric-box">
                            <span className="metric-box__label">Innings</span>
                            <span className="metric-box__value">{statVal(stats.innings)}</span>
                          </div>
                          <div className="metric-box">
                            <span className="metric-box__label">Not Outs</span>
                            <span className="metric-box__value">{statVal(stats.notOuts)}</span>
                          </div>
                          <div className="metric-box">
                            <span className="metric-box__label">Balls Faced</span>
                            <span className="metric-box__value">{statVal(stats.ballsFaced)}</span>
                          </div>

                          <div className="metric-box">
                            <span className="metric-box__label">Hundreds (100s)</span>
                            <span className="metric-box__value">{statVal(stats.hundreds)}</span>
                          </div>
                          <div className="metric-box">
                            <span className="metric-box__label">Fifties (50s)</span>
                            <span className="metric-box__value">{statVal(stats.fifties)}</span>
                          </div>
                          <div className="metric-box">
                            <span className="metric-box__label">Double 100s</span>
                            <span className="metric-box__value">{statVal(stats.twoHundreds)}</span>
                          </div>
                          <div className="metric-box">
                            <span className="metric-box__label">Fours (4s)</span>
                            <span className="metric-box__value">{statVal(stats.fours)}</span>
                          </div>
                          <div className="metric-box">
                            <span className="metric-box__label">Sixes (6s)</span>
                            <span className="metric-box__value">{statVal(stats.sixes)}</span>
                          </div>
                        </div>
                      </div>

                      {/* Bowling Card */}
                      <div className="stats-card">
                        <div className="stats-card__header">
                          <div className="stats-card__icon" aria-hidden="true"><svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" strokeWidth="2"><circle cx="12" cy="12" r="10"/><circle cx="12" cy="12" r="3"/></svg></div>
                          <div>
                            <h4 className="stats-card__title">Bowling Performance</h4>
                            <p className="stats-card__subtitle">{activeFormat} Career</p>
                          </div>
                        </div>

                        <div className="stats-metrics-grid">
                          <div className="metric-box metric-box--highlight">
                            <span className="metric-box__label">Wickets</span>
                            <span className="metric-box__value">{statVal(stats.wickets)}</span>
                          </div>
                          <div className="metric-box metric-box--highlight">
                            <span className="metric-box__label">Economy Rate</span>
                            <span className="metric-box__value">
                              {stats.economy !== null && stats.economy !== undefined ? stats.economy.toFixed(2) : '-'}
                            </span>
                          </div>
                          <div className="metric-box">
                            <span className="metric-box__label">Bowling Avg</span>
                            <span className="metric-box__value">
                              {stats.bowlingAverage !== null && stats.bowlingAverage !== undefined ? stats.bowlingAverage.toFixed(2) : '-'}
                            </span>
                          </div>
                          <div className="metric-box">
                            <span className="metric-box__label">Bowling SR</span>
                            <span className="metric-box__value">
                              {stats.bowlingStrikeRate !== null && stats.bowlingStrikeRate !== undefined ? stats.bowlingStrikeRate.toFixed(2) : '-'}
                            </span>
                          </div>

                          <div className="metric-box">
                            <span className="metric-box__label">Best Bowling</span>
                            <span className="metric-box__value">{statVal(stats.bestBowling)}</span>
                          </div>
                          <div className="metric-box">
                            <span className="metric-box__label">Balls Bowled</span>
                            <span className="metric-box__value">{statVal(stats.ballsBowled)}</span>
                          </div>
                          <div className="metric-box">
                            <span className="metric-box__label">Runs Conceded</span>
                            <span className="metric-box__value">{statVal(stats.runsConceded)}</span>
                          </div>
                          <div className="metric-box">
                            <span className="metric-box__label">5 Wickets (5w)</span>
                            <span className="metric-box__value">{statVal(stats.fiveWickets)}</span>
                          </div>
                          <div className="metric-box">
                            <span className="metric-box__label">10 Wickets (10w)</span>
                            <span className="metric-box__value">{statVal(stats.tenWickets)}</span>
                          </div>
                        </div>
                      </div>
                    </div>
                  );
                })()
              )}
            </section>
          </>
        )}
      </div>
    </DashboardLayout>
  );
}
