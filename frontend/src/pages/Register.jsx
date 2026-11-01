import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { api } from '../lib/api';
import AuthLayout, { PasswordInput, EMAIL_RE } from '../components/AuthLayout';

export default function Register() {
  const [form, setForm] = useState({ fullName: '', email: '', password: '', phone: '' });
  const [touched, setTouched] = useState({});
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);
  const navigate = useNavigate();

  const set = (k) => (e) => setForm((f) => ({ ...f, [k]: e.target.value }));
  const blur = (k) => () => setTouched((t) => ({ ...t, [k]: true }));

  const errors = {
    fullName: touched.fullName && !form.fullName.trim() ? 'Your name is required.' : '',
    email: touched.email && !EMAIL_RE.test(form.email.trim()) ? 'Enter a valid email address.' : '',
    password: touched.password && form.password.length < 6 ? 'Password must be at least 6 characters.' : '',
  };
  const valid = form.fullName.trim() && EMAIL_RE.test(form.email.trim()) && form.password.length >= 6;

  const submit = async (e) => {
    e.preventDefault();
    setTouched({ fullName: true, email: true, password: true });
    if (!valid) return;
    setError('');
    setBusy(true);
    try {
      await api.register({
        fullName: form.fullName.trim(),
        email: form.email.trim(),
        password: form.password,
        phone: form.phone.trim() || undefined,
      });
      navigate('/login', { state: { notice: 'Account created — sign in to continue.' } });
    } catch (err) {
      setError(err.message);
    } finally {
      setBusy(false);
    }
  };

  return (
    <AuthLayout heading="Create your account" sub="Driver accounts are created instantly. Admin accounts are issued by the operator.">
      <div className="auth-form-anim">
        {error && <div className="alert alert-error">{error}</div>}

        <form onSubmit={submit} className="form" noValidate>
          <label>
            Full name
            <input
              value={form.fullName} onChange={set('fullName')} onBlur={blur('fullName')}
              placeholder="Your name" autoComplete="name"
              className={errors.fullName ? 'input-invalid' : undefined}
            />
            {errors.fullName && <span className="field-error">{errors.fullName}</span>}
          </label>
          <label>
            Email
            <input
              type="email" value={form.email} onChange={set('email')} onBlur={blur('email')}
              placeholder="you@example.com" autoComplete="email"
              className={errors.email ? 'input-invalid' : undefined}
            />
            {errors.email && <span className="field-error">{errors.email}</span>}
          </label>
          <label>
            Password
            <PasswordInput
              value={form.password} onChange={set('password')} autoComplete="new-password"
              placeholder="At least 6 characters" invalid={!!errors.password}
            />
            {errors.password && <span className="field-error">{errors.password}</span>}
          </label>
          <label>
            Phone <span className="optional">(optional)</span>
            <input value={form.phone} onChange={set('phone')} placeholder="9876543210" autoComplete="tel" />
          </label>
          <button className="btn btn-primary btn-block" disabled={busy}>{busy ? 'Creating…' : 'Create account'}</button>
        </form>

        <p className="auth-alt">Already have an account? <Link to="/login">Sign in</Link></p>
        <p className="auth-alt"><Link to="/">← Back to home</Link></p>
      </div>
    </AuthLayout>
  );
}
