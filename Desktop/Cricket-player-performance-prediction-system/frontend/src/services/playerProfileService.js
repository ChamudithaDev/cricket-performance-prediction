/**
 * playerProfileService.js
 *
 * Dedicated service wrapper for player profile operations.
 * Communicates strictly with the Spring Boot backend (/api/player-profiles).
 */

import { searchPlayers as apiSearch, syncPlayer as apiSync, getPlayerProfile as apiGet } from './api';

/**
 * Friendly error parser for player profile requests.
 */
export function getFriendlyErrorMessage(error) {
  if (!error) return 'An unexpected error occurred.';
  if (!error.response) {
    return 'Unable to connect to the server. Please check that the backend is running.';
  }
  const status = error.response.status;
  if (status === 404) {
    return 'Player profile not found.';
  }
  if (status === 502) {
    return 'Unable to retrieve player data from the cricket data service. Please try again later.';
  }
  if (status === 401 || status === 403) {
    return 'Your session has expired. Please log in again.';
  }
  return error.response.data?.error || error.response.data?.message || 'A server error occurred. Please try again.';
}

/**
 * Format ISO date string into readable cricket-style date (e.g. 05 Nov 1988).
 */
export function formatPlayerDate(dateStr) {
  if (!dateStr) return 'Not available';
  try {
    const raw = dateStr.includes('T') ? dateStr.split('T')[0] : dateStr;
    const parts = raw.split('-');
    if (parts.length === 3) {
      const year = parseInt(parts[0], 10);
      const month = parseInt(parts[1], 10) - 1;
      const day = parseInt(parts[2], 10);
      const date = new Date(year, month, day);
      if (!isNaN(date.getTime())) {
        return date.toLocaleDateString('en-GB', {
          day: '2-digit',
          month: 'short',
          year: 'numeric',
        });
      }
    }
    return raw;
  } catch {
    return dateStr || 'Not available';
  }
}

/**
 * Safe display helper — never shows "null" or "undefined".
 */
export function displayValue(val, fallback = 'Not available') {
  if (val === null || val === undefined || val === '') return fallback;
  return String(val);
}

/**
 * Search external players through Spring Boot backend.
 */
export async function searchPlayers(name) {
  const response = await apiSearch(name);
  return response.data || [];
}

/**
 * Sync external player and retrieve saved local profile.
 */
export async function syncPlayer(externalPlayerId) {
  const response = await apiSync(externalPlayerId);
  return response.data;
}

/**
 * Fetch local player profile and career stats.
 */
export async function getPlayerProfile(id) {
  const response = await apiGet(id);
  return response.data;
}
