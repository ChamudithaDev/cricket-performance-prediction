// predictionService.js
// Service layer for AI prediction API (Spring Boot backend)

import api from './api';

/**
 * POST /api/predictions
 * @param {{player:string, venue:string, opponent:string, match_type:string}} payload
 * @returns {Promise<import('axios').AxiosResponse>}
 */
export const predictPerformance = (payload) =>
  api.post('/api/predictions', payload);

/**
 * GET /api/predictions/meta
 * Returns lists of players, venues, opponents, and match types.
 */
export const getPredictionMetadata = () =>
  api.get('/api/predictions/meta');

/**
 * GET /api/predictions/history
 * Returns the recent prediction history for the current user.
 */
export const getPredictionHistory = () =>
  api.get('/api/predictions/history');

export default {
  predictPerformance,
  getPredictionMetadata,
  getPredictionHistory,
};
