import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { api } from '../lib/api';
import { useToast } from '../context/AppContext';
import { fmtDateTime, inr, toIso } from '../lib/format';

// The v3 contract fixes the promo *semantics* (PERCENT % off the surged
// amount, or FLAT ₹ off; valid only if active, unexpired, usedCount < maxUses)
// but does not pin the admin DTO's field names, so reads tolerate the
// variants (type|discountType, expiresAt|expiry|expiryDate, active|isActive).
const promoType = (p) => p.type || p.discountType || 'PERCENT';
const promoExpiry = (p) => p.expiresAt || p.expiry || p.expiryDate || null;
const promoActive = (p) => (p.active !== undefined ? p.active : p.isActive !== undefined ? p.isActive : true);

function describeValue(p) {
  const v = Number(p.value ?? p.discountValue ?? 0);
  return promoType(p) === 'FLAT' ? `${inr(v)} off` : `${v}% off`;
}

export default function AdminPromos() {
  const toast = useToast();
  const [promos, setPromos] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [acting, setActing] = useState(null);
  const [creating, setCreating] = useState(false);
  const [form, setForm] = useState({ code: '', type: 'PERCENT', value: '', maxUses: '', expiresAt: '' });

  const load = async () => {
    try {
      setPromos(await api.adminPromos());
      setError('');
    } catch (e) {
      setError(e.message);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => { load(); }, []);

  const create = async (e) => {
    e.preventDefault();
    const code = form.code.trim().toUpperCase();
    const value = Number(form.value);
    if (!code) { toast('Promo code is required.', 'error'); return; }
    if (!Number.isFinite(value) || value <= 0) { toast('Value must be a positive number.', 'error'); return; }
    if (form.type === 'PERCENT' && value > 100) { toast('A percentage promo cannot exceed 100%.', 'error'); return; }
    setCreating(true);
    try {
      await api.createPromo({
        code,
        type: form.type,
        value,
        maxUses: form.maxUses ? Number(form.maxUses) : null,
        expiresAt: form.expiresAt ? toIso(form.expiresAt) : null,
        isActive: true,
      });
      toast(`Promo ${code} created`);
      setForm({ code: '', type: 'PERCENT', value: '', maxUses: '', expiresAt: '' });
      await load();
    } catch (err) {
      toast(err.message, 'error');
    } finally {
      setCreating(false);
    }
  };

  const deactivate = async (p) => {
    setActing(p.id + 'deactivate');
    try {
      await api.deactivatePromo(p.id);
      toast(`Promo ${p.code} deactivated`);
      await load();
    } catch (e) {
      toast(e.message, 'error');
    } finally {
      setActing(null);
    }
  };

  return (
    <div className="page">
      <div className="page-head">
        <div>
          <h2>Promo codes</h2>
          <p className="muted">
            Codes drivers can apply in the booking panel. PERCENT takes % off the surged price; FLAT takes a fixed ₹ amount off.
            A code stops working when it is deactivated, expired, or its max uses are reached.
          </p>
        </div>
        <Link className="btn btn-ghost btn-sm" to="/admin">← Dashboard</Link>
      </div>

      <section className="panel" style={{ marginBottom: 18 }}>
        <h3>Create a promo code</h3>
        <form onSubmit={create} className="form form-grid">
          <label>
            Code
            <input required value={form.code} onChange={(e) => setForm((f) => ({ ...f, code: e.target.value.toUpperCase() }))} placeholder="DIWALI20" autoComplete="off" />
          </label>
          <label>
            Type
            <select value={form.type} onChange={(e) => setForm((f) => ({ ...f, type: e.target.value }))}>
              <option value="PERCENT">PERCENT — % off</option>
              <option value="FLAT">FLAT — ₹ off</option>
            </select>
          </label>
          <label>
            Value {form.type === 'FLAT' ? '(₹)' : '(%)'}
            <input required type="number" min="0" step="any" value={form.value} onChange={(e) => setForm((f) => ({ ...f, value: e.target.value }))} placeholder={form.type === 'FLAT' ? '50' : '10'} />
          </label>
          <label>
            Max uses <span className="optional">(blank = unlimited)</span>
            <input type="number" min="1" value={form.maxUses} onChange={(e) => setForm((f) => ({ ...f, maxUses: e.target.value }))} placeholder="100" />
          </label>
          <label>
            Expires at <span className="optional">(blank = no expiry)</span>
            <input type="datetime-local" value={form.expiresAt} onChange={(e) => setForm((f) => ({ ...f, expiresAt: e.target.value }))} />
          </label>
          <div className="form-actions">
            <button className="btn btn-primary" disabled={creating}>{creating ? 'Creating…' : 'Create promo'}</button>
          </div>
        </form>
      </section>

      {error && <div className="alert alert-error">{error}</div>}
      {loading && <div className="skel skel-panel" aria-hidden="true" />}
      {!loading && !error && promos.length === 0 && (
        <div className="panel empty-state">No promo codes yet. The seeded WELCOME10 and FLAT50 should normally appear here.</div>
      )}

      {!loading && promos.length > 0 && (
        <div className="panel table-panel">
          <table className="table">
            <thead>
              <tr>
                <th>Code</th><th>Discount</th><th>Used</th><th>Expires</th><th>Status</th><th>Actions</th>
              </tr>
            </thead>
            <tbody>
              {promos.map((p) => {
                const expiry = promoExpiry(p);
                const expired = expiry ? new Date(expiry).getTime() < Date.now() : false;
                const active = promoActive(p) && !expired;
                return (
                  <tr key={p.id}>
                    <td><strong className="promo-code">{p.code}</strong></td>
                    <td>{describeValue(p)}</td>
                    <td>{p.usedCount ?? 0} / {p.maxUses ?? '∞'}</td>
                    <td>{expiry ? fmtDateTime(expiry) : 'No expiry'}</td>
                    <td>
                      <span className={`chip ${active ? 'chip-completed' : 'chip-cancelled'}`}>
                        {expired ? 'EXPIRED' : promoActive(p) ? 'ACTIVE' : 'INACTIVE'}
                      </span>
                    </td>
                    <td>
                      <span className="row-actions">
                        {promoActive(p) && (
                          <button className="btn btn-ghost btn-sm" disabled={acting !== null} onClick={() => deactivate(p)}>
                            {acting === p.id + 'deactivate' ? '…' : 'Deactivate'}
                          </button>
                        )}
                      </span>
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}
