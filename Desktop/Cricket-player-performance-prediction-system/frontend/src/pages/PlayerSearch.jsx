/**
 * PlayerSearch.jsx
 *
 * Allows users to search for international cricketers via Spring Boot's
 * CricAPI gateway, view search results, and synchronize real profiles into
 * the local MySQL database.
 */

import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import DashboardLayout from '../components/layout/DashboardLayout';
import { searchPlayers, syncPlayer, getFriendlyErrorMessage } from '../services/playerProfileService';

export default function PlayerSearch() {
  const navigate = useNavigate();

  const [query, setQuery] = useState('');
  const [results, setResults] = useState([]);
  const [hasSearched, setHasSearched] = useState(false);
  const [isSearching, setIsSearching] = useState(false);
  const [syncingId, setSyncingId] = useState(null);
  const [errorMessage, setErrorMessage] = useState('');
  const [syncSuccessMsg, setSyncSuccessMsg] = useState('');

  const handleSearch = async (e) => {
    e?.preventDefault();
    const trimmed = query.trim();

    if (!trimmed) {
      setErrorMessage('Please enter a player name.');
      setResults([]);
      return;
    }

    setErrorMessage('');
    setSyncSuccessMsg('');
    setIsSearching(true);
    setHasSearched(true);

    try {
      const data = await searchPlayers(trimmed);
      setResults(data || []);
    } catch (err) {
      setErrorMessage(getFriendlyErrorMessage(err));
      setResults([]);
    } finally {
      setIsSearching(false);
    }
  };

  const handleSync = async (player) => {
    if (!player?.id) return;

    setErrorMessage('');
    setSyncSuccessMsg('');
    setSyncingId(player.id);

    try {
      const profile = await syncPlayer(player.id);
      setSyncSuccessMsg(`Player data synchronized successfully for ${player.name}!`);

      // Small delay so user sees confirmation before navigation
      setTimeout(() => {
        navigate(`/player-profile/${profile.id}`);
      }, 700);
    } catch (err) {
      setErrorMessage(getFriendlyErrorMessage(err));
      setSyncingId(null);
    }
  };

  return (
    <DashboardLayout pageTitle="Player Search">
      <div className="player-search-page">
        {/* Header intro */}
        <section className="player-search__header" aria-labelledby="search-heading">
          <div className="player-search__header-content">
            <h2 id="search-heading" className="player-search__title">
              Cricketer Profile Search
            </h2>
            <p className="player-search__subtitle">
              Search world cricket players, view metadata, and sync comprehensive career
              statistics (Test, ODI, T20I, IPL) into your analytics database.
            </p>
          </div>
        </section>

        {/* Search bar card */}
        <div className="player-search__card">
          <form className="player-search__form" onSubmit={handleSearch}>
            <div className="player-search__input-wrapper">
              <span className="player-search__input-icon" aria-hidden="true">
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                  <circle cx="11" cy="11" r="8" />
                  <line x1="21" y1="21" x2="16.65" y2="16.65" />
                </svg>
              </span>
              <input
                type="text"
                className="player-search__input"
                placeholder="Search cricket player..."
                value={query}
                onChange={(e) => {
                  setQuery(e.target.value);
                  if (errorMessage) setErrorMessage('');
                }}
                disabled={isSearching || syncingId !== null}
                autoFocus
              />
              {query && !isSearching && (
                <button
                  type="button"
                  className="player-search__clear-btn"
                  onClick={() => {
                    setQuery('');
                    setResults([]);
                    setHasSearched(false);
                    setErrorMessage('');
                  }}
                  title="Clear search"
                  aria-label="Clear search"
                >
                  ✕
                </button>
              )}
            </div>

            <button
              type="submit"
              className="btn btn--primary player-search__submit-btn"
              disabled={isSearching || syncingId !== null}
            >
              {isSearching ? (
                <>
                  <span className="spinner spinner--sm spinner--light" aria-hidden="true" />
                  <span>Searching...</span>
                </>
              ) : (
                <>
                  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" style={{ width: 16, height: 16 }}>
                    <circle cx="11" cy="11" r="8" />
                    <line x1="21" y1="21" x2="16.65" y2="16.65" />
                  </svg>
                  <span>Search</span>
                </>
              )}
            </button>
          </form>

          {/* Error alert */}
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

          {/* Success banner */}
          {syncSuccessMsg && (
            <div className="player-search__alert player-search__alert--success" role="status">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
                <path d="M22 11.08V12a10 10 0 1 1-5.93-9.14" />
                <polyline points="22 4 12 14.01 9 11.01" />
              </svg>
              <span>{syncSuccessMsg}</span>
            </div>
          )}
        </div>

        {/* Results section */}
        <section className="player-search__results" aria-label="Search Results">
          {isSearching && (
            <div className="player-search__loading-box">
              <div className="spinner" />
              <p>Searching cricket registry through Spring Boot gateway...</p>
            </div>
          )}

          {!isSearching && hasSearched && results.length === 0 && !errorMessage && (
            <div className="empty-state">
              <div className="empty-state__icon" aria-hidden="true"><svg viewBox="0 0 24 24" width="36" height="36" fill="none" stroke="currentColor" strokeWidth="1.5"><circle cx="12" cy="12" r="10"/><path d="M12 2a10 10 0 0 1 0 20M12 2a10 10 0 0 0 0 20"/></svg></div>
              <p className="empty-state__title">No players found</p>
              <p className="empty-state__sub">
                No matching cricketers found for &ldquo;{query}&rdquo;. Try searching by full name or last name (e.g. &ldquo;Kohli&rdquo;, &ldquo;Dhoni&rdquo;, &ldquo;Rohit&rdquo;).
              </p>
            </div>
          )}

          {!isSearching && results.length > 0 && (
            <div className="player-search__grid">
              <div className="player-search__grid-header">
                <h3>Matching Players ({results.length})</h3>
                <span className="player-search__grid-tip">
                  Click &ldquo;Sync Player&rdquo; to fetch biography and career statistics.
                </span>
              </div>

              <div className="player-results-list">
                {results.map((player) => {
                  const isSyncingThis = syncingId === player.id;
                  return (
                    <div key={player.id} className="player-result-card">
                      <div className="player-result-card__avatar" aria-hidden="true">
                        {player.name?.[0]?.toUpperCase() || 'P'}
                      </div>

                      <div className="player-result-card__info">
                        <h4 className="player-result-card__name">{player.name}</h4>
                        <div className="player-result-card__meta">
                          <span className="player-result-card__country">
                            {player.country || 'Unknown country'}
                          </span>
                          <span className="player-result-card__ext-id" title={`External ID: ${player.id}`}>
                            ID: {player.id?.substring(0, 8)}...
                          </span>
                        </div>
                      </div>

                      <div className="player-result-card__actions">
                        <button
                          type="button"
                          className="btn btn--primary player-result-card__sync-btn"
                          onClick={() => handleSync(player)}
                          disabled={syncingId !== null}
                          title={`Sync profile for ${player.name}`}
                        >
                          {isSyncingThis ? (
                            <>
                              <span className="spinner spinner--sm spinner--light" aria-hidden="true" />
                              <span>Syncing player data...</span>
                            </>
                          ) : (
                            <>
                              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" style={{ width: 15, height: 15 }}>
                                <path d="M21.5 2v6h-6M21.34 15.57a10 10 0 1 1-.57-8.38l5.67-5.67" />
                              </svg>
                              <span>Sync Player</span>
                            </>
                          )}
                        </button>
                      </div>
                    </div>
                  );
                })}
              </div>
            </div>
          )}

          {!hasSearched && (
            <div className="player-search__hints">
              <div className="player-search__hint-card">
                <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" strokeWidth="2" style={{marginRight: 6}}><circle cx="11" cy="11" r="8"/><path d="m21 21-4.35-4.35"/></svg>
                <h4>Real-time Cricket Data</h4>
                <p>Connects via Spring Boot API to the official CricAPI database covering international and franchise cricket.</p>
              </div>
              <div className="player-search__hint-card">
                <span className="player-search__hint-icon">⚡</span>
                <h4>One-Click Synchronization</h4>
                <p>Imports biographical details, batting styles, bowling actions, and comprehensive stats across Test, ODI, T20I, and IPL.</p>
              </div>
              <div className="player-search__hint-card">
                <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" strokeWidth="2" style={{marginRight: 6}}><path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z"/></svg>
                <h4>Secure & Localized</h4>
                <p>Saved securely to your local database so future profile visits load instantly without repeated third-party requests.</p>
              </div>
            </div>
          )}
        </section>
      </div>
    </DashboardLayout>
  );
}
