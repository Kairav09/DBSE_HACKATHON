import { useState } from 'react';
import { Link } from 'react-router-dom';
import LogoMark from './Logo';

// Decorative mini slot-grid shown on the auth brand panel. A few tiles cycle
// colour gently (CSS) to hint at the live map — it is an illustration, not data.
const MINI_TILES = [
  'free', 'taken', 'cycle-a', 'free', 'held', 'free',
  'free', 'cycle-b', 'free', 'taken', 'free', 'held',
  'held', 'free', 'free', 'cycle-c', 'taken', 'free',
];

function MiniGrid() {
  return (
    <div className="auth-mini-grid" aria-hidden="true">
      {MINI_TILES.map((t, i) => (
        <span key={i} className={`auth-mini-tile tile-${t}`} style={{ animationDelay: `${(i % 6) * 0.7}s` }} />
      ))}
    </div>
  );
}

export default function AuthLayout({ heading, sub, children }) {
  return (
    <div className="auth-wrap">
      <div className="auth-shell">
        <aside className="auth-brand-panel">
          <Link to="/" className="auth-brand-link">
            <LogoMark size={34} />
            <span className="brand-name">PARK<em>GRID</em></span>
          </Link>
          <p className="auth-brand-tag">Find a slot. Book it. Park.</p>
          <MiniGrid />
          <ul className="auth-brand-points">
            <li>Live slot map for every location</li>
            <li>Overlapping bookings rejected automatically</li>
            <li>Entry &amp; exit recorded against your reservation</li>
          </ul>
        </aside>
        <section className="auth-form-panel">
          <h1 className="auth-heading">{heading}</h1>
          <p className="muted auth-sub">{sub}</p>
          {children}
        </section>
      </div>
    </div>
  );
}

export function PasswordInput({ value, onChange, onBlur, autoComplete, placeholder, invalid }) {
  const [show, setShow] = useState(false);
  return (
    <span className="pw-field">
      <input
        type={show ? 'text' : 'password'}
        value={value}
        onChange={onChange}
        onBlur={onBlur}
        autoComplete={autoComplete}
        placeholder={placeholder}
        className={invalid ? 'input-invalid' : undefined}
      />
      <button
        type="button"
        className="pw-toggle"
        onClick={() => setShow((s) => !s)}
        aria-label={show ? 'Hide password' : 'Show password'}
        tabIndex={-1}
      >
        {show ? (
          <svg width="19" height="19" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
            <path d="M17.94 17.94A10.6 10.6 0 0 1 12 19c-7 0-10-7-10-7a18.5 18.5 0 0 1 4.06-3.94M9.9 5.1A10.4 10.4 0 0 1 12 5c7 0 10 7 10 7a18.6 18.6 0 0 1-2.16 3.19M9.88 9.88a3 3 0 1 0 4.24 4.24" />
            <line x1="2" y1="2" x2="22" y2="22" />
          </svg>
        ) : (
          <svg width="19" height="19" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
            <path d="M2 12s3-7 10-7 10 7 10 7-3 7-10 7-10-7-10-7z" />
            <circle cx="12" cy="12" r="3" />
          </svg>
        )}
      </button>
    </span>
  );
}

export const EMAIL_RE = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
