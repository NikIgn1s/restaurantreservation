'use client';

import { createContext, useCallback, useContext, useMemo, useState } from 'react';
import Icon from '@/shared/ui/icon/icon';
import './toast.css';

const ToastContext = createContext(null);

export function ToastProvider({ children }) {
  const [items, setItems] = useState([]);

  const dismiss = useCallback((id) => {
    setItems((current) => current.filter((item) => item.id !== id));
  }, []);

  const push = useCallback(
    (text, tone = 'success') => {
      const id = `${Date.now()}-${Math.random()}`;
      setItems((current) => [...current, { id, text, tone }]);
      window.setTimeout(() => dismiss(id), 4200);
    },
    [dismiss],
  );

  const value = useMemo(
    () => ({
      success: (text) => push(text, 'success'),
      error: (text) => push(text, 'danger'),
      info: (text) => push(text, 'info'),
    }),
    [push],
  );

  return (
    <ToastContext.Provider value={value}>
      {children}
      <div className="toast-stack" aria-live="polite">
        {items.map((item) => (
          <div key={item.id} className={`toast toast-${item.tone}`} role="status">
            <Icon name={item.tone === 'danger' ? 'alert' : 'check'} size={18} />
            <span>{item.text}</span>
            <button type="button" onClick={() => dismiss(item.id)} aria-label="Закрыть">
              <Icon name="close" size={14} />
            </button>
          </div>
        ))}
      </div>
    </ToastContext.Provider>
  );
}

export function useToast() {
  const context = useContext(ToastContext);
  if (!context) {
    throw new Error('useToast must be used inside ToastProvider');
  }
  return context;
}
