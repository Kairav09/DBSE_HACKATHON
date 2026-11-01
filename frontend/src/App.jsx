import { Navigate, Outlet, Route, Routes, useLocation } from 'react-router-dom';
import { useAuth } from './context/AppContext';
import Nav from './components/Nav';
import Landing from './pages/Landing';
import Login from './pages/Login';
import Register from './pages/Register';
import Dashboard from './pages/Dashboard';
import Locations from './pages/Locations';
import LocationDetail from './pages/LocationDetail';
import Vehicles from './pages/Vehicles';
import Reservations from './pages/Reservations';
import Waitlist from './pages/Waitlist';
import Notifications from './pages/Notifications';
import Profile from './pages/Profile';
import Help from './pages/Help';
import AdminDashboard from './pages/AdminDashboard';
import AdminLocations from './pages/AdminLocations';
import AdminReservations from './pages/AdminReservations';
import AdminAnalytics from './pages/AdminAnalytics';
import AdminPromos from './pages/AdminPromos';
import AdminWaitlist from './pages/AdminWaitlist';
import NotFound from './pages/NotFound';

export function roleHome(user) {
  return user?.role === 'ADMIN' ? '/admin' : '/home';
}

function RequireAuth() {
  const { user } = useAuth();
  const location = useLocation();
  if (!user) return <Navigate to="/login" replace state={{ from: location.pathname }} />;
  return (
    <>
      <Nav />
      <main className="shell">
        <div key={location.pathname} className="page-anim">
          <Outlet />
        </div>
      </main>
      <footer className="footer">ParkGrid — Smart Parking Management System · CODESPARK Hackathon 2026</footer>
    </>
  );
}

function RequireRole({ role, children }) {
  const { user } = useAuth();
  if (user?.role !== role) return <Navigate to={roleHome(user)} replace />;
  return children;
}

function GuestOnly({ children }) {
  const { user } = useAuth();
  if (user) return <Navigate to={roleHome(user)} replace />;
  return children;
}

function CatchAll() {
  const { user } = useAuth();
  if (user) return <Navigate to={roleHome(user)} replace />;
  return <NotFound />;
}

export default function App() {
  return (
    <Routes>
      <Route path="/" element={<Landing />} />
      <Route path="/login" element={<GuestOnly><Login /></GuestOnly>} />
      <Route path="/register" element={<GuestOnly><Register /></GuestOnly>} />
      <Route element={<RequireAuth />}>
        <Route path="/home" element={<RequireRole role="USER"><Dashboard /></RequireRole>} />
        <Route path="/locations" element={<Locations />} />
        <Route path="/locations/:id" element={<LocationDetail />} />
        <Route path="/vehicles" element={<RequireRole role="USER"><Vehicles /></RequireRole>} />
        <Route path="/reservations" element={<RequireRole role="USER"><Reservations /></RequireRole>} />
        <Route path="/waitlist" element={<RequireRole role="USER"><Waitlist /></RequireRole>} />
        <Route path="/notifications" element={<Notifications />} />
        <Route path="/profile" element={<Profile />} />
        <Route path="/help" element={<Help />} />
        <Route path="/admin" element={<RequireRole role="ADMIN"><AdminDashboard /></RequireRole>} />
        <Route path="/admin/locations" element={<RequireRole role="ADMIN"><AdminLocations /></RequireRole>} />
        <Route path="/admin/reservations" element={<RequireRole role="ADMIN"><AdminReservations /></RequireRole>} />
        <Route path="/admin/analytics" element={<RequireRole role="ADMIN"><AdminAnalytics /></RequireRole>} />
        <Route path="/admin/promos" element={<RequireRole role="ADMIN"><AdminPromos /></RequireRole>} />
        <Route path="/admin/waitlist" element={<RequireRole role="ADMIN"><AdminWaitlist /></RequireRole>} />
      </Route>
      <Route path="*" element={<CatchAll />} />
    </Routes>
  );
}
