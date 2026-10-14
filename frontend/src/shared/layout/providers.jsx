'use client';

import { AuthProvider } from '@/shared/auth/auth-provider';
import { ToastProvider } from '@/shared/ui/toast/toast';

export default function Providers({ children }) {
  return (
    <ToastProvider>
      <AuthProvider>{children}</AuthProvider>
    </ToastProvider>
  );
}
