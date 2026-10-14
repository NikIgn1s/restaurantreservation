'use client';

import { useMemo, useState } from 'react';
import { venuesApi } from '@/shared/api/endpoints';
import { useAsync } from '@/shared/hooks/use-async';
import Button from '@/shared/ui/button/button';
import EmptyState from '@/shared/ui/empty-state/empty-state';
import ErrorNote from '@/shared/ui/error-note/error-note';
import { Input } from '@/shared/ui/field/field';
import Icon from '@/shared/ui/icon/icon';
import Spinner from '@/shared/ui/spinner/spinner';
import { useAuth } from '@/shared/auth/auth-provider';
import { VENUE_TYPES } from '@/shared/lib/labels';
import VenueCard from './venue-card';
import './venues.css';

const FILTERS = [{ value: 'ALL', label: 'Все' }, ...Object.entries(VENUE_TYPES).map(([value, label]) => ({ value, label }))];

export default function Venues() {
  const { user } = useAuth();
  const { data, error, loading, reload } = useAsync(() => venuesApi.list(), []);
  const [type, setType] = useState('ALL');
  const [query, setQuery] = useState('');

  const venues = useMemo(() => {
    const needle = query.trim().toLowerCase();
    return (data || []).filter((venue) => {
      if (!venue.active) {
        return false;
      }
      if (type !== 'ALL' && venue.type !== type) {
        return false;
      }
      return !needle || `${venue.name} ${venue.address || ''}`.toLowerCase().includes(needle);
    });
  }, [data, type, query]);

  return (
    <div className="venues">
      <section className="hero">
        <div className="hero-copy">
          <span className="hero-eyebrow">
            <Icon name="spark" size={14} strokeWidth={2.2} />
            Бронирование без звонков
          </span>
          <h1>
            Найдите место и <em>забронируйте за минуту</em>
          </h1>
          <p>
            Рестораны, коворкинги и спортивные площадки. Свободное время обновляется сразу, а подтверждение и депозит
            происходят в одном окне.
          </p>
          <div className="hero-actions">
            <Button size="lg" href="#catalog">
              Смотреть заведения
            </Button>
            {!user && (
              <Button size="lg" variant="ghost" href="/register">
                Создать аккаунт
              </Button>
            )}
          </div>
        </div>
        <div className="hero-visual" aria-hidden="true">
          <div className="hero-orb hero-orb-a" />
          <div className="hero-orb hero-orb-b" />
          <div className="hero-ticket">
            <span className="hero-ticket-time">19:00</span>
            <span className="hero-ticket-text">Столик на двоих</span>
            <span className="hero-ticket-state">Подтверждено</span>
          </div>
        </div>
      </section>

      <section id="catalog" className="catalog">
        <div className="catalog-toolbar">
          <div className="filter-pills" role="tablist">
            {FILTERS.map((filter) => (
              <button
                key={filter.value}
                type="button"
                className={filter.value === type ? 'pill is-active' : 'pill'}
                onClick={() => setType(filter.value)}
              >
                {filter.label}
              </button>
            ))}
          </div>
          <div className="catalog-search">
            <Icon name="list" size={16} />
            <Input value={query} onChange={(e) => setQuery(e.target.value)} placeholder="Название или адрес" />
          </div>
        </div>

        {loading && <Spinner block label="Загружаем заведения" />}

        {error && (
          <div className="catalog-error">
            <ErrorNote error={error} />
            <Button variant="ghost" size="sm" onClick={reload}>
              Повторить
            </Button>
          </div>
        )}

        {!loading && !error && venues.length === 0 && (
          <EmptyState
            icon="store"
            title="Ничего не найдено"
            text="Измените фильтр или поисковый запрос. Если заведений ещё нет, их может добавить владелец в своём кабинете."
          />
        )}

        {!loading && venues.length > 0 && (
          <div className="venue-grid">
            {venues.map((venue, index) => (
              <VenueCard key={venue.id} venue={venue} index={index} />
            ))}
          </div>
        )}
      </section>
    </div>
  );
}
