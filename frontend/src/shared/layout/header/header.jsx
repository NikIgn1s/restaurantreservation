'use client';

import Link from 'next/link';
import { usePathname, useRouter } from 'next/navigation';
import { useAuth } from '@/shared/auth/auth-provider';
import Button from '@/shared/ui/button/button';
import Icon from '@/shared/ui/icon/icon';
import { ROLES } from '@/shared/lib/labels';
import { initials } from '@/shared/lib/format';
import './header.css';

function buildLinks(user) {
  const links = [{ href: '/', label: 'Заведения' }];
  if (user?.role === 'CUSTOMER') {
    links.push({ href: '/my-bookings', label: 'Мои брони' });
  }
  if (user?.role === 'VENUE_OWNER' || user?.role === 'ADMIN') {
    links.push({ href: '/owner', label: 'Кабинет' });
  }
  return links;
}

export default function Header() {
  const { user, ready, logout } = useAuth();
  const pathname = usePathname();
  const router = useRouter();

  const handleLogout = () => {
    logout();
    router.push('/');
  };

  return (
    <header className="site-header">
      <div className="container site-header-inner">
        <Link href="/" className="brand">
          <span className="brand-mark">
            <Icon name="spark" size={18} strokeWidth={2.2} />
          </span>
          <span className="brand-name">Reserva</span>
        </Link>

        <nav className="site-nav" aria-label="Основная навигация">
          {buildLinks(user).map((link) => {
            const active = link.href === '/' ? pathname === '/' : pathname.startsWith(link.href);
            return (
              <Link key={link.href} href={link.href} className={active ? 'nav-link is-active' : 'nav-link'}>
                {link.label}
              </Link>
            );
          })}
        </nav>

        <div className="site-actions">
          {!ready ? null : user ? (
            <>
              <div className="user-chip" title={ROLES[user.role]}>
                <span className="user-avatar">{initials(user.fullName)}</span>
                <span className="user-meta">
                  <span className="user-name">{user.fullName}</span>
                  <span className="user-role">{ROLES[user.role]}</span>
                </span>
              </div>
              <Button variant="ghost" size="sm" onClick={handleLogout} aria-label="Выйти">
                <Icon name="logout" size={16} />
                <span className="hide-mobile">Выйти</span>
              </Button>
            </>
          ) : (
            <>
              <Button variant="ghost" size="sm" href="/login">
                Войти
              </Button>
              <Button size="sm" href="/register">
                Регистрация
              </Button>
            </>
          )}
        </div>
      </div>
    </header>
  );
}
