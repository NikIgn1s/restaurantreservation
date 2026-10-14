'use client';

import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react';
import { getToken, onUnauthorized, setToken } from '@/shared/api/client';
import { authApi } from '@/shared/api/endpoints';

const AuthContext = createContext(null);

export function AuthProvider({ children }) {
  const [user, setUser] = useState(null);
  const [ready, setReady] = useState(false);

  const logout = useCallback(() => {
    setToken(null);
    setUser(null);
  }, []);

  useEffect(() => {
    onUnauthorized(logout);
    if (!getToken()) {
      setReady(true);
      return;
    }
    authApi
      .me()
      .then(setUser)
      .catch(() => setToken(null))
      .finally(() => setReady(true));
  }, [logout]);

  const login = useCallback(async (email, password) => {
    const result = await authApi.login(email, password);
    setToken(result.accessToken);
    setUser(result.user);
    return result.user;
  }, []);

  const register = useCallback(
    async (payload) => {
      await authApi.register(payload);
      return login(payload.email, payload.password);
    },
    [login],
  );

  const value = useMemo(() => ({ user, ready, login, register, logout }), [user, ready, login, register, logout]);

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth() {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error('useAuth must be used inside AuthProvider');
  }
  return context;
}
