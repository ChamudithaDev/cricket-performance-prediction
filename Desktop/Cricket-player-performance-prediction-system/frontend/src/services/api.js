/**
 * api.js
 *
 * Centralised Axios client for the Spring Boot backend.
 * All frontend HTTP calls must go through this file — never hardcode URLs in components.
 *
 * Base URL is read from VITE_API_URL in .env so the same build can target
 * different environments without code changes.
 */

import axios from 'axios';

const BASE_URL = import.meta.env.VITE_API_URL || 'http://localhost:8080';

const api = axios.create({
  baseURL: BASE_URL,
  headers: { 'Content-Type': 'application/json' },
  timeout: 15000,
});

// ─── Request interceptor: attach JWT on every authenticated request ───────────
api.interceptors.request.use(
  (config) => {
    const token = localStorage.getItem('cricketai_token');
    if (token) {
      config.headers.Authorization = `Bearer ${token}`;
    }
    return config;
  },
  (error) => Promise.reject(error)
);

// ─── Response interceptor: handle 401 / 403 auth errors globally ─────────────
api.interceptors.response.use(
  (response) => response,
  (error) => {
    const status = error.response?.status;
    if (status === 401 || (status === 403 && localStorage.getItem('cricketai_token'))) {
      // Token missing, expired, or rejected — clear stored auth and hard-redirect to login
      localStorage.removeItem('cricketai_token');
      localStorage.removeItem('cricketai_user');
      window.location.href = '/login';
    }
    return Promise.reject(error);
  }
);

// ─── Auth endpoints ────────────────────────────────────────────────────────────

/**
 * POST /api/auth/login
 * @param {string} email
 * @param {string} password
 * @returns {Promise<{token, email, fullName, role}>}
 */
export const loginRequest = (email, password) =>
  api.post('/api/auth/login', { email, password });

/**
 * POST /api/auth/register
 * @param {string} fullName
 * @param {string} email
 * @param {string} password
 * @returns {Promise<{token, email, fullName, role}>}
 */
export const registerRequest = (fullName, email, password) =>
  api.post('/api/auth/register', { fullName, email, password });

// ─── Player Profile endpoints ──────────────────────────────────────────────────

/**
 * GET /api/player-profiles/search?name=...
 * @param {string} name
 * @returns {Promise<Array<{id: string, name: string, country: string}>>}
 */
export const searchPlayers = (name) =>
  api.get('/api/player-profiles/search', { params: { name } });

/**
 * POST /api/player-profiles/sync/{externalPlayerId}
 * @param {string} externalPlayerId
 * @returns {Promise<PlayerProfileResponse>}
 */
export const syncPlayer = (externalPlayerId) =>
  api.post(`/api/player-profiles/sync/${encodeURIComponent(externalPlayerId)}`);

/**
 * GET /api/player-profiles/{id}
 * @param {number|string} id
 * @returns {Promise<PlayerProfileResponse>}
 */
export const getPlayerProfile = (id) =>
  api.get(`/api/player-profiles/${encodeURIComponent(id)}`);

export default api;

