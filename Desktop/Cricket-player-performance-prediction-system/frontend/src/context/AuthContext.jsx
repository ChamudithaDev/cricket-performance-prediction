/**
 * AuthContext.jsx
 *
 * Provides authentication state (user, token) to the entire application.
 *
 * State is persisted in localStorage so it survives a page refresh.
 * The JWT is stored under 'cricketai_token', user info under 'cricketai_user'.
 *
 * Exposes: { user, token, isAuthenticated, login, logout, isLoading }
 */

import { createContext, useContext, useEffect, useState } from 'react';
import { loginRequest, registerRequest } from '../services/api';

const AuthContext = createContext(null);

export function AuthProvider({ children }) {
  const [user, setUser] = useState(null);
  const [token, setToken] = useState(null);
  const [isLoading, setIsLoading] = useState(true); // true while reading localStorage

  // ── Restore auth state from localStorage on app start ──────────────────────
  useEffect(() => {
    try {
      const storedToken = localStorage.getItem('cricketai_token');
      const storedUser = localStorage.getItem('cricketai_user');
      if (storedToken && storedUser) {
        setToken(storedToken);
        setUser(JSON.parse(storedUser));
      }
    } catch {
      // Corrupted storage — start fresh
      localStorage.removeItem('cricketai_token');
      localStorage.removeItem('cricketai_user');
    } finally {
      setIsLoading(false);
    }
  }, []);

  // ── Persist auth state to localStorage ──────────────────────────────────────
  const persist = (tokenValue, userValue) => {
    localStorage.setItem('cricketai_token', tokenValue);
    localStorage.setItem('cricketai_user', JSON.stringify(userValue));
    setToken(tokenValue);
    setUser(userValue);
  };

  // ── login: calls Spring Boot and stores the returned JWT ────────────────────
  const login = async (email, password) => {
    const { data } = await loginRequest(email, password);
    const userInfo = { email: data.email, fullName: data.fullName, role: data.role };
    persist(data.token, userInfo);
    return userInfo;
  };

  // ── register: calls Spring Boot and auto-logs the user in ──────────────────
  const register = async (fullName, email, password) => {
    const { data } = await registerRequest(fullName, email, password);
    const userInfo = { email: data.email, fullName: data.fullName, role: data.role };
    persist(data.token, userInfo);
    return userInfo;
  };

  // ── logout: wipes state and storage ─────────────────────────────────────────
  const logout = () => {
    localStorage.removeItem('cricketai_token');
    localStorage.removeItem('cricketai_user');
    setToken(null);
    setUser(null);
  };

  return (
    <AuthContext.Provider
      value={{
        user,
        token,
        isAuthenticated: !!token,
        isLoading,
        login,
        register,
        logout,
      }}
    >
      {children}
    </AuthContext.Provider>
  );
}

/** Hook for consuming auth state in any component. */
export function useAuth() {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth must be used inside <AuthProvider>');
  return ctx;
}

export default AuthContext;
