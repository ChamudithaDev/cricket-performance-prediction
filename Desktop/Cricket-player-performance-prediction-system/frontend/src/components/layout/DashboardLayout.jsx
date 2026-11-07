/**
 * DashboardLayout.jsx
 *
 * Unified layout wrapping all protected pages with the shared white-first
 * Sidebar + Topbar. Handles mobile drawer visibility and content spacing.
 */

import { useState } from 'react';
import Sidebar from './Sidebar';
import Topbar from './Topbar';

export default function DashboardLayout({ pageTitle = 'Dashboard', children }) {
  const [sidebarOpen, setSidebarOpen] = useState(false);

  return (
    <div className="dashboard-layout">
      <Sidebar
        isOpen={sidebarOpen}
        onClose={() => setSidebarOpen(false)}
      />

      <div className="dashboard-layout__main">
        <Topbar
          pageTitle={pageTitle}
          onMenuToggle={() => setSidebarOpen((v) => !v)}
        />

        <main className="dashboard-layout__content" id="main-content" tabIndex={-1}>
          {children}
        </main>
      </div>
    </div>
  );
}
