import api from './api';

/**
 * GET /api/analytics/toss-decision?venue=...&format=...
 * @param {string} venue
 * @param {string} format - ODI, T20I, or TEST
 * @returns {Promise<TossDecisionResponse>}
 */
export const getTossDecision = (venue, format = 'ODI') =>
  api.get('/api/analytics/toss-decision', { params: { venue, format } });

/**
 * GET /api/analytics/toss-decision/venues?format=...
 * @param {string} [format] - optional format filter
 * @returns {Promise<{format: string, venues: string[], totalVenues: number}>}
 */
export const getTossVenues = (format) =>
  api.get('/api/analytics/toss-decision/venues', { params: format ? { format } : {} });

/**
 * GET /api/analytics/toss-decision/formats
 * @returns {Promise<string[]>}
 */
export const getTossFormats = () =>
  api.get('/api/analytics/toss-decision/formats');
