import { Link, NavLink, useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AppContext';
import LogoMark from './Logo';
import NotificationBell from './NotificationBell';

export default function Nav() {
  const { user, logout } = useAuth();
  const navigate = useNavigate();
  const isAdmin = user?.role === 'ADMIN';
  const homePath = isAdmin ? '/admin' : '/home';

  const onLogout = () => {
    logout();
    navigate('/');
  };

  return (
    <header className="topnav">
      <div className="topnav-inner">
        <NavLink to={homePath} className="brand">
          <LogoMark />
          <span className="brand-name">PARK<em>GRID</em></span>
        </NavLink>
        <nav className="topnav-links">
          {!isAdmin && <NavLink to="/home">Dashboard</NavLink>}
          {isAdmin && <NavLink to="/admin" end>Dashboard</NavLink>}
          <NavLink to="/locations" end>Locations</NavLink>
          {!isAdmin && <NavLink to="/reservations">My Reservations</NavLink>}
          {!isAdmin && <NavLink to="/vehicles">My Vehicles</NavLink>}
          {!isAdmin && <NavLink to="/waitlist">Waitlist</NavLink>}
          {isAdmin && <NavLink to="/admin/analytics">Analytics</NavLink>}
          {isAdmin && <NavLink to="/admin/locations">Manage Locations</NavLink>}
          {isAdmin && <NavLink to="/admin/reservations">All Reservations</NavLink>}
          {isAdmin && <NavLink to="/admin/promos">Promos</NavLink>}
          {isAdmin && <NavLink to="/admin/waitlist">Waitlist</NavLink>}
          <NavLink to="/help">Help</NavLink>
        </nav>
        <div className="topnav-user">
          <NotificationBell />
          <Link to="/profile" className="user-chip user-chip-link" title="Your profile">
            <span className="user-avatar">{(user?.fullName || '?').trim().charAt(0).toUpperCase()}</span>
            <span className="user-meta">
              <span className="user-name">{user?.fullName}</span>
              <span className={`user-role role-${(user?.role || '').toLowerCase()}`}>{user?.role}</span>
            </span>
          </Link>
          <button className="btn btn-ghost btn-sm" onClick={onLogout}>Logout</button>
        </div>
      </div>
    </header>
  );
}
