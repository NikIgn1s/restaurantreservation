import { Suspense } from 'react';
import AuthForm from '@/modules/auth/auth';

export const metadata = { title: 'Вход - Reserva' };

export default function LoginPage() {
  return (
    <Suspense fallback={null}>
      <AuthForm mode="login" />
    </Suspense>
  );
}
