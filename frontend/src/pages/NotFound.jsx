import { Link } from 'react-router-dom';
import LogoMark from '../components/Logo';

export default function NotFound() {
  return (
    <div className="auth-wrap">
      <div className="notfound-card">
        <LogoMark size={40} />
        <h1 className="notfound-code">404</h1>
        <p className="notfound-title">This bay doesn&apos;t exist</p>
        <p className="muted">The page you are looking for is not on this map.</p>
        <div className="hero-ctas notfound-ctas">
          <Link className="btn btn-primary" to="/">Back to home</Link>
          <Link className="btn btn-ghost" to="/login">Sign in</Link>
        </div>
      </div>
    </div>
  );
}
