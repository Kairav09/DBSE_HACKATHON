import { useEffect, useState } from 'react';
import { api } from '../lib/api';
import { useToast } from '../context/AppContext';
import { VEHICLE_TYPES, VEHICLE_TYPE_LABELS, inr } from '../lib/format';

const emptyLoc = { name: '', address: '', city: '', hourlyRate: '', isActive: true };

export default function AdminLocations() {
  const toast = useToast();
  const [locations, setLocations] = useState([]);
  const [error, setError] = useState('');
  const [locForm, setLocForm] = useState(emptyLoc);
  const [editingId, setEditingId] = useState(null);
  const [busy, setBusy] = useState(false);

  const [slotLocId, setSlotLocId] = useState(null);
  const [slots, setSlots] = useState([]);
  const [slotForm, setSlotForm] = useState({ slotCode: '', vehicleType: 'FOUR_WHEELER', isActive: true });

  const loadLocations = async () => {
    try {
      setLocations(await api.locations());
      setError('');
    } catch (e) {
      setError(e.message);
    }
  };

  const loadSlots = async (locId) => {
    try {
      setSlots(await api.slotsByLocation(locId));
    } catch (e) {
      toast(e.message, 'error');
    }
  };

  useEffect(() => { loadLocations(); }, []);

  const setLoc = (k) => (e) =>
    setLocForm((f) => ({ ...f, [k]: k === 'isActive' ? e.target.checked : e.target.value }));

  const submitLocation = async (e) => {
    e.preventDefault();
    setBusy(true);
    const payload = {
      name: locForm.name.trim(),
      address: locForm.address.trim(),
      city: locForm.city.trim() || undefined,
      hourlyRate: Number(locForm.hourlyRate),
      isActive: locForm.isActive,
    };
    try {
      if (editingId) {
        await api.updateLocation(editingId, payload);
        toast('Location updated');
      } else {
        await api.createLocation(payload);
        toast('Location created');
      }
      setLocForm(emptyLoc);
      setEditingId(null);
      await loadLocations();
    } catch (err) {
      toast(err.message, 'error');
    } finally {
      setBusy(false);
    }
  };

  const editLocation = (loc) => {
    setEditingId(loc.id);
    setLocForm({
      name: loc.name, address: loc.address, city: loc.city || '',
      hourlyRate: loc.hourlyRate, isActive: loc.isActive !== false,
    });
    window.scrollTo({ top: 0, behavior: 'smooth' });
  };

  const removeLocation = async (loc) => {
    if (!window.confirm(`Delete location "${loc.name}"? This also deletes its slots.`)) return;
    try {
      await api.deleteLocation(loc.id);
      toast('Location deleted');
      if (slotLocId === loc.id) { setSlotLocId(null); setSlots([]); }
      await loadLocations();
    } catch (err) {
      toast(err.message, 'error');
    }
  };

  const openSlots = (locId) => {
    if (slotLocId === locId) { setSlotLocId(null); setSlots([]); return; }
    setSlotLocId(locId);
    loadSlots(locId);
  };

  const submitSlot = async (e) => {
    e.preventDefault();
    try {
      await api.createSlot(slotLocId, {
        slotCode: slotForm.slotCode.trim().toUpperCase(),
        vehicleType: slotForm.vehicleType,
        isActive: slotForm.isActive,
      });
      toast(`Slot ${slotForm.slotCode.toUpperCase()} added`);
      setSlotForm({ slotCode: '', vehicleType: 'FOUR_WHEELER', isActive: true });
      await loadSlots(slotLocId);
    } catch (err) {
      toast(err.message, 'error');
    }
  };

  const toggleSlotActive = async (slot) => {
    try {
      await api.updateSlot(slot.id, {
        slotCode: slot.slotCode, vehicleType: slot.vehicleType, isActive: slot.isActive === false,
      });
      await loadSlots(slotLocId);
    } catch (err) {
      toast(err.message, 'error');
    }
  };

  const removeSlot = async (slot) => {
    if (!window.confirm(`Delete slot ${slot.slotCode}?`)) return;
    try {
      await api.deleteSlot(slot.id);
      toast(`Slot ${slot.slotCode} deleted`);
      await loadSlots(slotLocId);
    } catch (err) {
      toast(err.message, 'error');
    }
  };

  return (
    <div className="page">
      <div className="page-head">
        <div>
          <h2>Manage locations & slots</h2>
          <p className="muted">Create locations, set hourly rates, and manage the slots under each.</p>
        </div>
      </div>

      {error && <div className="alert alert-error">{error}</div>}

      <section className="panel">
        <h3>{editingId ? 'Edit location' : 'New location'}</h3>
        <form onSubmit={submitLocation} className="form form-grid">
          <label>Name<input required value={locForm.name} onChange={setLoc('name')} placeholder="Campus East Parking" /></label>
          <label>Address<input required value={locForm.address} onChange={setLoc('address')} placeholder="Block C, East Gate" /></label>
          <label>City<input value={locForm.city} onChange={setLoc('city')} placeholder="Hyderabad" /></label>
          <label>Hourly rate (₹)<input type="number" min="0" step="0.5" required value={locForm.hourlyRate} onChange={setLoc('hourlyRate')} placeholder="30" /></label>
          <label className="check-label">
            <input type="checkbox" checked={locForm.isActive} onChange={setLoc('isActive')} /> Active (visible to users)
          </label>
          <div className="form-actions">
            <button className="btn btn-primary" disabled={busy}>{busy ? 'Saving…' : editingId ? 'Save changes' : 'Create location'}</button>
            {editingId && (
              <button type="button" className="btn btn-ghost" onClick={() => { setEditingId(null); setLocForm(emptyLoc); }}>Cancel edit</button>
            )}
          </div>
        </form>
      </section>

      <div className="card-list">
        {locations.map((loc) => (
          <section key={loc.id} className="panel">
            <div className="admin-loc-head">
              <div>
                <strong>{loc.name}</strong>
                <p className="muted small">{loc.address}{loc.city ? `, ${loc.city}` : ''} · {inr(loc.hourlyRate)}/hr · {loc.isActive === false ? 'Inactive' : 'Active'}</p>
              </div>
              <div className="row-actions">
                <button className="btn btn-dark btn-sm" onClick={() => openSlots(loc.id)}>
                  {slotLocId === loc.id ? 'Hide slots' : 'Manage slots'}
                </button>
                <button className="btn btn-ghost btn-sm" onClick={() => editLocation(loc)}>Edit</button>
                <button className="btn btn-danger-ghost btn-sm" onClick={() => removeLocation(loc)}>Delete</button>
              </div>
            </div>

            {slotLocId === loc.id && (
              <div className="slot-admin">
                <form onSubmit={submitSlot} className="form form-inline">
                  <label>Slot code<input required value={slotForm.slotCode}
                    onChange={(e) => setSlotForm((f) => ({ ...f, slotCode: e.target.value }))} placeholder="A-17" /></label>
                  <label>Type
                    <select value={slotForm.vehicleType}
                      onChange={(e) => setSlotForm((f) => ({ ...f, vehicleType: e.target.value }))}>
                      {VEHICLE_TYPES.map((t) => <option key={t} value={t}>{VEHICLE_TYPE_LABELS[t]}</option>)}
                    </select>
                  </label>
                  <button className="btn btn-primary btn-sm">Add slot</button>
                </form>
                <div className="slot-admin-grid">
                  {slots.map((s) => (
                    <div key={s.id} className={`slot-admin-chip ${s.isActive === false ? 'is-inactive' : ''}`}>
                      <span><strong>{s.slotCode}</strong> <small>{VEHICLE_TYPE_LABELS[s.vehicleType] || s.vehicleType} · {s.status}</small></span>
                      <span className="row-actions">
                        <button className="btn btn-ghost btn-sm" onClick={() => toggleSlotActive(s)}>
                          {s.isActive === false ? 'Activate' : 'Deactivate'}
                        </button>
                        <button className="btn btn-danger-ghost btn-sm" onClick={() => removeSlot(s)}>Delete</button>
                      </span>
                    </div>
                  ))}
                  {slots.length === 0 && <p className="muted small">No slots yet for this location.</p>}
                </div>
              </div>
            )}
          </section>
        ))}
      </div>
    </div>
  );
}
