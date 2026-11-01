import { useState } from 'react';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import { api } from '../lib/api';
import { useAuth, useToast } from '../context/AppContext';
import AuthLayout, { PasswordInput, EMAIL_RE } from '../components/AuthLayout';

export default function Login() {
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [touched, setTouched] = useState({});
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);
  const { login } = useAuth();
  const toast = useToast();
  const navigate = useNavigate();
  const location = useLocation();
  const from = location.state?.from;
  const notice = location.state?.notice;

  const emailError = touched.email && !EMAIL_RE.test(email.trim()) ? 'Enter a valid email address.' : '';
  const passwordError = touched.password && !password ? 'Password is required.' : '';

  const submit = async (e) => {
    e.preventDefault();
    setTouched({ email: true, password: true });
    if (!EMAIL_RE.test(email.trim()) || !password) return;
    setError('');
    setBusy(true);
    try {
      const data = await api.login({ email: email.trim(), password });
      login(data.token, data.user);
      toast(`Welcome back, ${data.user.fullName}`);
      const fallback = data.user.role === 'ADMIN' ? '/admin' : '/home';
      const dest = from && from !== '/' && from !== '/login' ? from : fallback;
      navigate(dest, { replace: true });
    } catch (err) {
      setError(err.message);
    } finally {
      setBusy(false);
    }
  };

  const fillDemo = (demoEmail, demoPassword) => {
    setEmail(demoEmail);
    setPassword(demoPassword);
    setTouched({});
    setError('');
  };

  return (
    <AuthLayout heading="Welcome back" sub="Sign in to see live slots and your reservations.">
      <div className="auth-form-anim">
        {notice && <div className="alert alert-success">{notice}</div>}
        {error && <div className="alert alert-error">{error}</div>}

        <form onSubmit={submit} className="form" noValidate>
          <label>
            Email
            <input
              type="email" value={email} placeholder="you@example.com" autoComplete="email"
              className={emailError ? 'input-invalid' : undefined}
              onChange={(e) => setEmail(e.target.value)}
              onBlur={() => setTouched((t) => ({ ...t, email: true }))}
            />
            {emailError && <span className="field-error">{emailError}</span>}
          </label>
          <label>
            Password
            <PasswordInput
              value={password} placeholder="Your password" autoComplete="current-password"
              invalid={!!passwordError}
              onChange={(e) => setPassword(e.target.value)}
              onBlur={() => setTouched((t) => ({ ...t, password: true }))}
            />
            {passwordError && <span className="field-error">{passwordError}</span>}
          </label>
          <button className="btn btn-primary btn-block" disabled={busy}>{busy ? 'Signing in…' : 'Sign in'}</button>
        </form>

        <p className="auth-alt">New here? <Link to="/register">Create an account</Link></p>
        <p className="auth-alt"><Link to="/">← Back to home</Link></p>

        <div className="demo-box">
          <span className="demo-title">Demo accounts — click to fill</span>
          <button type="button" className="demo-chip" onClick={() => fillDemo('user1@example.com', 'user123')}>
            User · user1@example.com / user123
          </button>
          <button type="button" className="demo-chip" onClick={() => fillDemo('admin@smartparking.local', 'admin123')}>
            Admin · admin@smartparking.local / admin123
          </button>
        </div>
      </div>
    </AuthLayout>
  );
}
