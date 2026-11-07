/**
 * App.jsx — Root application component.
 *
 * Wraps the app in BrowserRouter and AuthProvider,
 * then delegates routing to AppRoutes.
 *
 * The original PredictionPage is preserved in src/pages/PredictionPage.jsx
 * but is no longer the app entry point — it will be wired into the
 * authenticated dashboard in a later task.
 */

import { BrowserRouter } from 'react-router-dom';
import { AuthProvider } from './context/AuthContext';
import AppRoutes from './routes/AppRoutes';

function App() {
  return (
    <BrowserRouter>
      <AuthProvider>
        <AppRoutes />
      </AuthProvider>
    </BrowserRouter>
  );
}

export default App;