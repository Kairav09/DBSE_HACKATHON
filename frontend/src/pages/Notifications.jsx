import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { api } from '../lib/api';
import { useAuth, useToast } from '../context/AppContext';
import { notificationTarget } from '../components/NotificationBell';
import { fmtDateTime } from '../lib/format';

const TYPE_LABELS = {
  BOOKING_CONFIRMED: 'Booking',
  PAYMENT_DONE: 'Payment',
  WAITLIST_OFFER: 'Waitlist offer',
  RESERVATION_NO_SHOW: 'No-show',
  RESERVATION_CANCELLED: 'Cancelled',
};

export default function Notifications() {
  const { user } = useAuth();
  const toast = useToast();
  const navigate = useNavigate();
  const [items, setItems] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  const load = async () => {
    try {
      setItems(await api.notifications());
      setError('');
    } catch (e) {
      setError(e.message);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => { load(); }, []);

  const unread = items.filter((n) => !n.read).length;

  const markAll = async () => {
    try {
      await api.markAllNotificationsRead();
      setItems((list) => list.map((n) => ({ ...n, read: true })));
      toast('All notifications marked as read');
    } catch (e) {
      toast(e.message, 'error');
    }
  };

  const onItemClick = async (n) => {
    if (!n.read) {
      try { await api.markNotificationRead(n.id); } catch { /* navigation still happens */ }
    }
    navigate(notificationTarget(n, user));
  };

  return (
    <div className="page">
      <div className="page-head">
        <div>
          <h2>Notifications</h2>
          <p className="muted">
            Booking confirmations, payments, waitlist offers and changes — newest first.
            {unread > 0 ? ` ${unread} unread.` : ''}
          </p>
        </div>
        {unread > 0 && (
          <button className="btn btn-ghost btn-sm" onClick={markAll}>Mark all read</button>
        )}
      </div>

      {error && <div className="alert alert-error">{error}</div>}
      {loading && (
        <div className="card-list" aria-hidden="true">
          <div className="skel skel-card" /><div className="skel skel-card" />
        </div>
      )}
      {!loading && items.length === 0 && (
        <div className="panel empty-state">
          Nothing here yet. Bookings, payments and waitlist offers will show up as notifications.
        </div>
      )}

      <div className="card-list stagger">
        {items.map((n) => (
          <button
            key={n.id}
            type="button"
            className={`panel notif-row ${n.read ? '' : 'is-unread'}`}
            onClick={() => onItemClick(n)}
          >
            <span className="notif-main">
              <span className="notif-title-row">
                <span className={`type-badge type-${String(n.type || '').toLowerCase()}`}>
                  {TYPE_LABELS[n.type] || n.type}
                </span>
                <strong>{n.title}</strong>
                {!n.read && <span className="unread-dot" aria-label="Unread" />}
              </span>
              <span className="notif-msg">{n.message}</span>
            </span>
            <span className="muted small notif-time">{fmtDateTime(n.createdAt)}</span>
          </button>
        ))}
      </div>
    </div>
  );
}
