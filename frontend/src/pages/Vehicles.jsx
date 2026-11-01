import { useEffect, useState } from 'react';
import { api } from '../lib/api';
import { useToast } from '../context/AppContext';
import { VEHICLE_TYPES, VEHICLE_TYPE_LABELS, fmtDateTime } from '../lib/format';
import { bookingsByVehicle } from '../lib/stats';

const EMPTY = { licensePlate: '', vehicleType: 'FOUR_WHEELER', model: '', color: '' };

export default function Vehicles() {
  const toast = useToast();
  const [vehicles, setVehicles] = useState([]);
  const [counts, setCounts] = useState(new Map());
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [form, setForm] = useState(EMPTY);
  const [editingId, setEditingId] = useState(null);
  const [plateError, setPlateError] = useState('');
  const [confirmDeleteId, setConfirmDeleteId] = useState(null);
  const [busy, setBusy] = useState(false);

  const load = async () => {
    try {
      const [vs, rs] = await Promise.all([
        api.myVehicles(),
        api.myReservations().catch(() => []),
      ]);
      setVehicles(vs);
      setCounts(bookingsByVehicle(rs));
      setError('');
    } catch (e) {
      setError(e.message);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => { load(); }, []);

  const set = (k) => (e) => {
    const val = k === 'licensePlate' ? e.target.value.toUpperCase() : e.target.value;
    setForm((f) => ({ ...f, [k]: val }));
    if (k === 'licensePlate') setPlateError('');
  };

  const startEdit = (v) => {
    setEditingId(v.id);
    setForm({
      licensePlate: v.licensePlate,
      vehicleType: v.vehicleType,
      model: v.model || '',
      color: v.color || '',
    });
    setPlateError('');
    setConfirmDeleteId(null);
  };

  const cancelEdit = () => {
    setEditingId(null);
    setForm(EMPTY);
    setPlateError('');
  };

  const submit = async (e) => {
    e.preventDefault();
    const plate = form.licensePlate.trim().toUpperCase();
    if (!plate) { setPlateError('License plate is required.'); return; }
    setBusy(true);
    try {
      const payload = {
        licensePlate: plate,
        vehicleType: form.vehicleType,
        model: form.model.trim() || undefined,
        color: form.color.trim() || undefined,
      };
      if (editingId) {
        await api.updateVehicle(editingId, payload);
        toast(`Vehicle ${plate} updated`);
      } else {
        const v = await api.createVehicle(payload);
        toast(`Vehicle ${v.licensePlate} added`);
      }
      cancelEdit();
      await load();
    } catch (err) {
      toast(err.message, 'error');
    } finally {
      setBusy(false);
    }
  };

  const remove = async (v) => {
    if (confirmDeleteId !== v.id) {
      setConfirmDeleteId(v.id);
      return;
    }
    try {
      await api.deleteVehicle(v.id);
      toast(`Vehicle ${v.licensePlate} deleted`);
      setConfirmDeleteId(null);
      if (editingId === v.id) cancelEdit();
      await load();
    } catch (err) {
      toast(err.message, 'error');
      setConfirmDeleteId(null);
    }
  };

  return (
    <div className="page">
      <div className="page-head">
        <div>
          <h2>My vehicles</h2>
          <p className="muted">Register a vehicle once, then book parking with it in two clicks.</p>
        </div>
      </div>

      {error && <div className="alert alert-error">{error}</div>}

      <div className="two-col">
        <section className="panel">
          <h3>{editingId ? 'Edit vehicle' : 'Add a vehicle'}</h3>
          <form onSubmit={submit} className="form" noValidate>
            <label>
              License plate
              <input
                value={form.licensePlate} onChange={set('licensePlate')} placeholder="TS09AB1234"
                className={plateError ? 'input-invalid' : undefined}
                style={{ textTransform: 'uppercase' }}
              />
              {plateError && <span className="field-error">{plateError}</span>}
            </label>
            <label>
              Type
              <select value={form.vehicleType} onChange={set('vehicleType')}>
                {VEHICLE_TYPES.map((t) => <option key={t} value={t}>{VEHICLE_TYPE_LABELS[t]}</option>)}
              </select>
            </label>
            <label>
              Model <span className="optional">(optional)</span>
              <input value={form.model} onChange={set('model')} placeholder="Hyundai Creta" />
            </label>
            <label>
              Colour <span className="optional">(optional)</span>
              <input value={form.color} onChange={set('color')} placeholder="White" />
            </label>
            <div className="form-actions">
              <button className="btn btn-primary" disabled={busy}>
                {busy ? 'Saving…' : editingId ? 'Save changes' : 'Add vehicle'}
              </button>
              {editingId && (
                <button type="button" className="btn btn-ghost" onClick={cancelEdit}>Cancel edit</button>
              )}
            </div>
          </form>
          {editingId && (
            <p className="muted small edit-note">Editing updates the plate, type, model and colour via the API. A plate already used by another vehicle will be rejected.</p>
          )}
        </section>

        <section>
          {loading && (
            <div className="card-list" aria-hidden="true">
              <div className="skel skel-card" /><div className="skel skel-card" />
            </div>
          )}
          {!loading && vehicles.length === 0 && (
            <div className="panel empty-state">No vehicles yet. Add your first one to start booking.</div>
          )}
          <div className="card-list stagger">
            {vehicles.map((v) => (
              <div key={v.id} className={`panel vehicle-card ${editingId === v.id ? 'is-editing' : ''}`}>
                <div>
                  <span className="plate">{v.licensePlate}</span>
                  <div className="vehicle-meta">
                    <span className="type-badge">{VEHICLE_TYPE_LABELS[v.vehicleType] || v.vehicleType}</span>
                    <span className="booking-count">{counts.get(v.id) || 0} booking{(counts.get(v.id) || 0) === 1 ? '' : 's'}</span>
                  </div>
                  <p className="muted small">
                    {[v.model, v.color].filter(Boolean).join(' · ')}
                    {[v.model, v.color].filter(Boolean).length ? ' · ' : ''}Added {fmtDateTime(v.createdAt)}
                  </p>
                </div>
                <div className="row-actions">
                  <button className="btn btn-ghost btn-sm" onClick={() => startEdit(v)}>Edit</button>
                  <button
                    className={`btn btn-sm ${confirmDeleteId === v.id ? 'btn-danger-solid' : 'btn-danger-ghost'}`}
                    onClick={() => remove(v)}
                  >
                    {confirmDeleteId === v.id ? 'Confirm delete?' : 'Delete'}
                  </button>
                </div>
              </div>
            ))}
          </div>
          {vehicles.length > 0 && (
            <p className="muted small delete-hint">Deleting asks for a second click to confirm. A vehicle with existing reservations cannot be deleted — the server will say so.</p>
          )}
        </section>
      </div>
    </div>
  );
}
