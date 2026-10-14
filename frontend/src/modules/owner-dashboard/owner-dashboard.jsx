'use client';

import { useEffect, useMemo, useState } from 'react';
import { venuesApi } from '@/shared/api/endpoints';
import { useAsync } from '@/shared/hooks/use-async';
import { useAuth } from '@/shared/auth/auth-provider';
import Button from '@/shared/ui/button/button';
import EmptyState from '@/shared/ui/empty-state/empty-state';
import ErrorNote from '@/shared/ui/error-note/error-note';
import Icon from '@/shared/ui/icon/icon';
import PageHeader from '@/shared/ui/page-header/page-header';
import Spinner from '@/shared/ui/spinner/spinner';
import Tabs from '@/shared/ui/tabs/tabs';
import { VENUE_TYPES } from '@/shared/lib/labels';
import CreateVenueDialog from './create-venue-dialog';
import DayBookings from './day-bookings';
import ScheduleEditor from './schedule-editor';
import VenueManager from './venue-manager';
import './owner-dashboard.css';

const TABS = [
  { value: 'day', label: 'Брони дня' },
  { value: 'resources', label: 'Ресурсы' },
  { value: 'schedule', label: 'Расписание' },
];

export default function OwnerDashboard() {
  const { user } = useAuth();
  const venues = useAsync(() => venuesApi.list(), []);
  const [venueId, setVenueId] = useState(null);
  const [tab, setTab] = useState('day');
  const [createOpen, setCreateOpen] = useState(false);

  const mine = useMemo(
    () => (venues.data || []).filter((venue) => user.role === 'ADMIN' || venue.ownerId === user.id),
    [venues.data, user],
  );

  useEffect(() => {
    if (mine.length > 0 && !mine.some((venue) => venue.id === venueId)) {
      setVenueId(mine[0].id);
    }
  }, [mine, venueId]);

  const venue = mine.find((item) => item.id === venueId) || null;
  const resources = useAsync(() => venuesApi.resources(venueId), [venueId], { immediate: Boolean(venueId) });
  const activeResources = useMemo(() => (resources.data || []).filter((item) => item.active), [resources.data]);

  const created = async (newVenue) => {
    setCreateOpen(false);
    await venues.reload();
    setVenueId(newVenue.id);
  };

  return (
    <div className="owner">
      <PageHeader
        title="Кабинет владельца"
        text="Брони дня, столики и площадки, расписание и исключения в одном месте"
        actions={
          <Button onClick={() => setCreateOpen(true)}>
            <Icon name="plus" size={16} />
            Новое заведение
          </Button>
        }
      />

      {venues.loading && !venues.data && <Spinner block label="Загружаем заведения" />}
      <ErrorNote error={venues.error} />

      {!venues.loading && !venues.error && mine.length === 0 && (
        <EmptyState
          icon="store"
          title="У вас пока нет заведений"
          text="Создайте первое заведение, добавьте столики и настройте расписание, чтобы гости могли бронировать."
          action={<Button onClick={() => setCreateOpen(true)}>Создать заведение</Button>}
        />
      )}

      {mine.length > 0 && venue && (
        <>
          <div className="owner-venues">
            {mine.map((item) => (
              <button
                key={item.id}
                type="button"
                className={item.id === venueId ? 'owner-venue is-active' : 'owner-venue'}
                onClick={() => setVenueId(item.id)}
              >
                <strong>{item.name}</strong>
                <span>{VENUE_TYPES[item.type]}</span>
              </button>
            ))}
          </div>

          <div className="owner-tabs">
            <Tabs items={TABS} value={tab} onChange={setTab} />
          </div>

          {resources.loading && !resources.data ? (
            <Spinner block label="Загружаем ресурсы" />
          ) : (
            <div key={`${venue.id}-${tab}`} className="owner-panel">
              {tab === 'day' && <DayBookings venue={venue} resources={resources.data || []} />}
              {tab === 'resources' && <VenueManager venue={venue} resources={resources.data || []} onChanged={resources.reload} />}
              {tab === 'schedule' && <ScheduleEditor resources={activeResources} />}
            </div>
          )}
        </>
      )}

      <CreateVenueDialog open={createOpen} onClose={() => setCreateOpen(false)} onCreated={created} />
    </div>
  );
}
