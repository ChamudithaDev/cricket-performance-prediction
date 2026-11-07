/**
 * LoadingSpinner.jsx — Full-screen or inline spinner
 */

export default function LoadingSpinner({ fullScreen = false, label = 'Loading...' }) {
  if (fullScreen) {
    return (
      <div className="app-loading" aria-busy="true" aria-label={label}>
        <div className="spinner" />
        <p className="app-loading__text">{label}</p>
      </div>
    );
  }

  return <span className="spinner spinner--sm" aria-label={label} />;
}
