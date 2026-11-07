/**
 * AppRoutes.jsx
 *
 * Declares all application routes.
 * Protected routes are wrapped in <ProtectedRoute>.
 * Public routes (login, register) redirect to /dashboard if already authenticated.
 */

import { Routes, Route, Navigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import ProtectedRoute from '../components/auth/ProtectedRoute';
import Login from '../pages/Login';
import Register from '../pages/Register';
import Dashboard from '../pages/Dashboard';
import PlayerSearch from '../pages/PlayerSearch';
import PlayerProfile from '../pages/PlayerProfile';
import PlayerAnalytics from '../pages/PlayerAnalytics';
import PlayerComparison from '../pages/PlayerComparison';
import PredictionPage from '../pages/PredictionPage';
import TossDecisionAdvisor from '../pages/TossDecisionAdvisor';
import Settings from '../pages/Settings';
import LoadingSpinner from '../components/common/LoadingSpinner';

/** Redirect already-authenticated users away from login/register */
function PublicRoute({ children }) {
  const { isAuthenticated, isLoading } = useAuth();
  if (isLoading) return <LoadingSpinner fullScreen label="Loading…" />;
  if (isAuthenticated) return <Navigate to="/dashboard" replace />;
  return children;
}

export default function AppRoutes() {
  return (
    <Routes>
      {/* Default: redirect root to login */}
      <Route path="/" element={<Navigate to="/login" replace />} />

      {/* Public routes */}
      <Route path="/login" element={<PublicRoute><Login /></PublicRoute>} />
      <Route path="/register" element={<PublicRoute><Register /></PublicRoute>} />

      {/* Protected routes */}
      <Route
        path="/dashboard"
        element={<ProtectedRoute><Dashboard /></ProtectedRoute>}
      />
      <Route
        path="/player-search"
        element={<ProtectedRoute><PlayerSearch /></ProtectedRoute>}
      />
      <Route
        path="/player-profile/:id"
        element={<ProtectedRoute><PlayerProfile /></ProtectedRoute>}
      />
      <Route
        path="/analytics"
        element={<ProtectedRoute><PlayerAnalytics /></ProtectedRoute>}
      />
      <Route
        path="/compare"
        element={<ProtectedRoute><PlayerComparison /></ProtectedRoute>}
      />
      <Route
        path="/predictions"
        element={<ProtectedRoute><PredictionPage /></ProtectedRoute>}
      />
      <Route
        path="/toss-advisor"
        element={<ProtectedRoute><TossDecisionAdvisor /></ProtectedRoute>}
      />
      <Route
        path="/settings"
        element={<ProtectedRoute><Settings /></ProtectedRoute>}
      />

      {/* Catch-all — redirect unknown paths to login */}
      <Route path="*" element={<Navigate to="/login" replace />} />
    </Routes>
  );
}
