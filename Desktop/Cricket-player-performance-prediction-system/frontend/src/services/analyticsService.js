
import api from './api';

/**
 * GET /api/analytics/player?name=...&recentN=...
 * @param {string} playerName - player name to look up
 * @param {number} recentN    - number of recent innings (default 15)
 * @returns {Promise<PlayerAnalyticsResponse>}
 */
export const getPlayerAnalytics = (playerName, recentN = 15, format = 'ALL') =>
  api.get('/api/analytics/player', { params: { name: playerName, recentN, format } });

/**
 * GET /api/analytics/player-strengths?name=...&format=...
 * @param {string} playerName - player name to look up
 * @param {string} format     - ALL, ODI, T20I, or TEST (default ALL)
 * @returns {Promise<import('axios').AxiosResponse>}
 */
export const getPlayerStrengths = (playerName, format = 'ALL') =>
  api.get('/api/analytics/player-strengths', { params: { name: playerName, format } });

/**
 * GET /api/analytics/players
 * @returns {Promise<string[]>} sorted list of all player names in the dataset
 */
export const listAnalyticsPlayers = () =>
  api.get('/api/analytics/players');

/**
 * GET /api/analytics/compare?player1=...&player2=...&format=...&recentN=...&opposition=...&venue=...
 * @param {string} player1    - first player name
 * @param {string} player2    - second player name
 * @param {string} format     - ALL, ODI, T20I, or TEST (default ALL)
 * @param {number} recentN    - number of recent innings (5, 10, or 15)
 * @param {string} opposition - optional opposition to filter comparison
 * @param {string} venue      - optional venue to filter comparison
 * @returns {Promise<PlayerComparisonResponse>}
 */
export const comparePlayers = (player1, player2, format = 'ALL', recentN = 15, opposition = null, venue = null) => {
  const params = { player1, player2, format, recentN };
  if (opposition) params.opposition = opposition;
  if (venue) params.venue = venue;
  return api.get('/api/analytics/compare', { params });
};

/**
 * GET /api/analytics/export/player?name=...&format=...&recentN=...
 * Downloads native Excel (.xlsx) workbook with editable DrawingML charts.
 * @param {string} playerName
 * @param {string} format
 * @param {number} recentN
 * @returns {Promise<import('axios').AxiosResponse<Blob>>}
 */
export const exportPlayerAnalysisExcel = (playerName, format = 'ALL', recentN = 15) =>
  api.get('/api/analytics/export/player', {
    params: { name: playerName, format, recentN },
    responseType: 'blob',
  });

/**
 * GET /api/analytics/export/compare?player1=...&player2=...&format=...&recentN=...&opposition=...&venue=...
 * Downloads native Excel (.xlsx) workbook with editable DrawingML charts for comparison.
 * @param {string} player1
 * @param {string} player2
 * @param {string} format
 * @param {number} recentN
 * @param {string} opposition
 * @param {string} venue
 * @returns {Promise<import('axios').AxiosResponse<Blob>>}
 */
export const exportPlayerComparisonExcel = (player1, player2, format = 'ALL', recentN = 15, opposition = null, venue = null) => {
  const params = { player1, player2, format, recentN };
  if (opposition) params.opposition = opposition;
  if (venue) params.venue = venue;
  return api.get('/api/analytics/export/compare', {
    params,
    responseType: 'blob',
  });
};
