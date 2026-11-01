import { createContext, useCallback, useContext, useRef, useState } from 'react';
import { clearSession, getStoredUser, getToken, saveSession } from '../lib/api';

const AuthContext = createContext(null);
const ToastContext = createContext(null);

export function AppProviders({ children }) {
  const [user, setUser] = useState(() => (getToken() ? getStoredUser() : null));
  const [toasts, setToasts] = useState([]);
  const toastId = useRef(0);

  const login = useCallback((token, u) => {
    saveSession(token, u);
    setUser(u);
  }, []);

  const logout = useCallback(() => {
    clearSession();
    setUser(null);
  }, []);

  const toast = useCallback((message, kind = 'success') => {
    const id = ++toastId.current;
    setToasts((t) => [...t, { id, message, kind }]);
    setTimeout(() => setToasts((t) => t.filter((x) => x.id !== id)), 4200);
  }, []);

  return (
    <AuthContext.Provider value={{ user, login, logout }}>
      <ToastContext.Provider value={{ toast }}>
        {children}
        <div className="toast-stack" aria-live="polite">
          {toasts.map((t) => (
            <div key={t.id} className={`toast toast-${t.kind}`}>{t.message}</div>
          ))}
        </div>
      </ToastContext.Provider>
    </AuthContext.Provider>
  );
}

export function useAuth() {
  return useContext(AuthContext);
}
export function useToast() {
  return useContext(ToastContext).toast;
}
