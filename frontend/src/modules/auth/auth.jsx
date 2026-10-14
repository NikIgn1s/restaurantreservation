'use client';

import { useState } from 'react';
import Link from 'next/link';
import { useRouter, useSearchParams } from 'next/navigation';
import { useAuth } from '@/shared/auth/auth-provider';
import Button from '@/shared/ui/button/button';
import Card from '@/shared/ui/card/card';
import ErrorNote from '@/shared/ui/error-note/error-note';
import { Field, Input } from '@/shared/ui/field/field';
import Icon from '@/shared/ui/icon/icon';
import './auth.css';

const ROLE_OPTIONS = [
  { value: 'CUSTOMER', title: 'Гость', text: 'Бронирую столики, места и корты', icon: 'users' },
  { value: 'VENUE_OWNER', title: 'Владелец', text: 'Управляю заведением и расписанием', icon: 'store' },
];

function safeNext(value) {
  return value && value.startsWith('/') && !value.startsWith('//') ? value : null;
}

export default function AuthForm({ mode }) {
  const isRegister = mode === 'register';
  const { login, register } = useAuth();
  const router = useRouter();
  const params = useSearchParams();
  const [form, setForm] = useState({ email: '', password: '', fullName: '', role: 'CUSTOMER' });
  const [error, setError] = useState(null);
  const [loading, setLoading] = useState(false);

  const update = (key) => (event) => setForm((current) => ({ ...current, [key]: event.target.value }));

  const submit = async (event) => {
    event.preventDefault();
    setError(null);
    setLoading(true);
    try {
      const user = isRegister ? await register(form) : await login(form.email, form.password);
      const fallback = user.role === 'CUSTOMER' ? '/' : '/owner';
      router.replace(safeNext(params.get('next')) || fallback);
    } catch (e) {
      setError(e);
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="auth">
      <aside className="auth-aside">
        <span className="auth-aside-mark">
          <Icon name="spark" size={26} strokeWidth={2} />
        </span>
        <h2>{isRegister ? 'Присоединяйтесь к Reserva' : 'С возвращением'}</h2>
        <p>
          Свободные слоты в реальном времени, подтверждение брони в пару кликов и понятная история каждого
          бронирования.
        </p>
        <ul className="auth-points">
          <li>
            <Icon name="clock" size={18} />
            Слоты по расписанию заведения
          </li>
          <li>
            <Icon name="shield" size={18} />
            Защита от двойного бронирования
          </li>
          <li>
            <Icon name="card" size={18} />
            Депозит только там, где он нужен
          </li>
        </ul>
      </aside>

      <Card className="auth-card">
        <h1>{isRegister ? 'Создать аккаунт' : 'Вход'}</h1>
        <p className="auth-lead">
          {isRegister ? 'Это займёт меньше минуты' : 'Войдите, чтобы управлять бронированиями'}
        </p>

        <form className="auth-form" onSubmit={submit}>
          {isRegister && (
            <>
              <div className="role-picker" role="radiogroup" aria-label="Роль">
                {ROLE_OPTIONS.map((option) => (
                  <button
                    key={option.value}
                    type="button"
                    role="radio"
                    aria-checked={form.role === option.value}
                    className={form.role === option.value ? 'role-option is-active' : 'role-option'}
                    onClick={() => setForm((current) => ({ ...current, role: option.value }))}
                  >
                    <Icon name={option.icon} size={20} />
                    <strong>{option.title}</strong>
                    <span>{option.text}</span>
                  </button>
                ))}
              </div>
              <Field label="Имя и фамилия">
                <Input value={form.fullName} onChange={update('fullName')} placeholder="Анна Ковальская" required maxLength={255} />
              </Field>
            </>
          )}

          <Field label="Email">
            <Input type="email" value={form.email} onChange={update('email')} placeholder="you@example.com" required autoComplete="email" />
          </Field>

          <Field label="Пароль" hint={isRegister ? 'От 8 до 72 символов' : undefined}>
            <Input
              type="password"
              value={form.password}
              onChange={update('password')}
              placeholder="Введите пароль"
              required
              minLength={isRegister ? 8 : undefined}
              maxLength={72}
              autoComplete={isRegister ? 'new-password' : 'current-password'}
            />
          </Field>

          <ErrorNote error={error} />

          <Button type="submit" size="lg" block loading={loading}>
            {isRegister ? 'Зарегистрироваться' : 'Войти'}
          </Button>
        </form>

        <p className="auth-switch">
          {isRegister ? (
            <>
              Уже есть аккаунт? <Link href="/login">Войти</Link>
            </>
          ) : (
            <>
              Впервые у нас? <Link href="/register">Создать аккаунт</Link>
            </>
          )}
        </p>

        {!isRegister && (
          <div className="auth-demo">
            <span>Демо-доступ</span>
            <button type="button" onClick={() => setForm((c) => ({ ...c, email: 'alice@dev.local', password: 'password123' }))}>
              Гость
            </button>
            <button type="button" onClick={() => setForm((c) => ({ ...c, email: 'owner@dev.local', password: 'password123' }))}>
              Владелец
            </button>
          </div>
        )}
      </Card>
    </div>
  );
}
