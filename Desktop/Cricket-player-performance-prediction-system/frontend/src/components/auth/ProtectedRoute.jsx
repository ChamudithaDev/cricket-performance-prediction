/**
 * ProtectedRoute.jsx
 *
 * Wraps any route that requires authentication.
 * If not authenticated → redirects to /login (preserving the intended destination).
 * While auth state is being restored from localStorage → shows a loading spinner.
 */

import { Navigate, useLocation } from 'react-router-dom';
import { useAuth } from '../../context/AuthContext';

export default function ProtectedRoute({ children }) {
  const { isAuthenticated, isLoading } = useAuth();
  const location = useLocation();

  if (isLoading) {
    return (
      <div className="app-loading">
        <div className="spinner" aria-label="Checking authentication..." />
      </div>
    );
  }

  if (!isAuthenticated) {
    // Preserve the page user was trying to reach so we can redirect back after login
    return <Navigate to="/login" state={{ from: location }} replace />;
  }

  return children;
}
