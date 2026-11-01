import { useCallback, useEffect, useRef, useState } from 'react';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import { api } from '../lib/api';
import { useAuth } from '../context/AppContext';
import { fmtDateTime } from '../lib/format';

/** Where a notification click should take the user, by type. */
export function notificationTarget(n, user) {
  if (n.type === 'WAITLIST_OFFER') return '/waitlist';
  return user?.role === 'ADMIN' ? '/admin/reservations' : '/reservations';
}

export default function NotificationBell() {
  const { user } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const [unread, setUnread] = useState(0);
  const [items, setItems] = useState([]);
  const [open, setOpen] = useState(false);
  const wrapRef = useRef(null);

  const loadCount = useCallback(async () => {
    try {
      const r = await api.unreadCount();
      setUnread(Number(r?.count ?? 0));
    } catch { /* bell is best-effort chrome; page shows real errors */ }
  }, []);

  const loadItems = useCallback(async () => {
    try {
      setItems(await api.notifications());
    } catch { /* keep whatever we had */ }
  }, []);

  // Poll the unread count ~30s, and refresh on every route change.
  useEffect(() => { loadCount(); }, [loadCount, location.pathname]);
  useEffect(() => {
    const t = setInterval(loadCount, 30000);
    return () => clearInterval(t);
  }, [loadCount]);

  // Close on outside click / Escape.
  useEffect(() => {
    if (!open) return;
    const onDoc = (e) => { if (wrapRef.current && !wrapRef.current.contains(e.target)) setOpen(false); };
    const onKey = (e) => { if (e.key === 'Escape') setOpen(false); };
    document.addEventListener('mousedown', onDoc);
    window.addEventListener('keydown', onKey);
    return () => {
      document.removeEventListener('mousedown', onDoc);
      window.removeEventListener('keydown', onKey);
    };
  }, [open ]);

  const toggle = () => {
    const next = !open;
    setOpen(next);
    if (next) { loadItems(); loadCount(); }
  };

  const onItemClick = async (n) => {
    setOpen(false);
    if (!n.read) {
      try { await api.markNotificationRead(n.id); } catch { /* navigation still happens */ }
      setItems((list) => list.map((x) => (x.id === n.id ? { ...x, read: true } : x)));
      setUnread((c) => Math.max(0, c - 1));
    }
    navigate(notificationTarget(n, user));
  };

  const markAll = async () => {
    try {
      await api.markAllNotificationsRead();
      setItems((list) => list.map((x) => ({ ...x, read: true })));
      setUnread(0);
    } catch { /* leave state as-is */ }
  };

  return (
    <div className="bell-wrap" ref={wrapRef}>
      <button
        type="button"
        className="bell-btn"
        onClick={toggle}
        aria-label={unread ? `Notifications, ${unread} unread` : 'Notifications'}
        aria-expanded={open}
      >
        <svg width="19" height="19" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.9" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
          <path d="M18 9a6 6 0 1 0-12 0c0 6-2.5 7-2.5 7h17S18 15 18 9" />
          <path d="M10 20a2.2 2.2 0 0 0 4 0" />
        </svg>
        {unread > 0 && <span className="bell-badge">{unread > 9 ? '9+' : unread}</span>}
      </button>

      {open && (
        <div className="bell-panel" role="menu" aria-label="Notifications">
          <div className="bell-panel-head">
            <strong>Notifications</strong>
            {unread > 0 && (
              <button type="button" className="bell-link-btn" onClick={markAll}>Mark all read</button>
            )}
          </div>
          {items.length === 0 && <p className="bell-empty">No notifications yet.</p>}
          <ul className="bell-list">
            {items.slice(0, 8).map((n) => (
              <li key={n.id}>
                <button
                  type="button"
                  className={`bell-item ${n.read ? '' : 'is-unread'}`}
                  onClick={() => onItemClick(n)}
                >
                  <span className="bell-item-title">{n.title}</span>
                  <span className="bell-item-msg">{n.message}</span>
                  <span className="bell-item-time">{fmtDateTime(n.createdAt)}</span>
                </button>
              </li>
            ))}
          </ul>
          <Link to="/notifications" className="bell-view-all" onClick={() => setOpen(false)}>View all notifications →</Link>
        </div>
      )}
    </div>
  );
}
