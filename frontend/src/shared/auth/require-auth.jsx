'use client';

import { useEffect } from 'react';
import { usePathname, useRouter } from 'next/navigation';
import Spinner from '@/shared/ui/spinner/spinner';
import EmptyState from '@/shared/ui/empty-state/empty-state';
import { useAuth } from './auth-provider';

export default function RequireAuth({ roles, children }) {
  const { user, ready } = useAuth();
  const router = useRouter();
  const pathname = usePathname();

  useEffect(() => {
    if (ready && !user) {
      router.replace(`/login?next=${encodeURIComponent(pathname)}`);
    }
  }, [ready, user, router, pathname]);

  if (!ready || !user) {
    return <Spinner block label="Загрузка" />;
  }

  if (roles && !roles.includes(user.role)) {
    return (
      <EmptyState
        title="Раздел недоступен"
        text="Для вашей роли этот раздел закрыт. Войдите под другой учётной записью."
      />
    );
  }

  return children;
}
