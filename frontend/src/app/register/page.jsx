import { Suspense } from 'react';
import AuthForm from '@/modules/auth/auth';

export const metadata = { title: 'Регистрация - Reserva' };

export default function RegisterPage() {
  return (
    <Suspense fallback={null}>
      <AuthForm mode="register" />
    </Suspense>
  );
}
