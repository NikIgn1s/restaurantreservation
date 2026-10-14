'use client';

import { useMemo, useState } from 'react';
import { bookingsApi } from '@/shared/api/endpoints';
import { useAsync } from '@/shared/hooks/use-async';
import { useDirectory } from '@/shared/hooks/use-directory';
import Button from '@/shared/ui/button/button';
import EmptyState from '@/shared/ui/empty-state/empty-state';
import ErrorNote from '@/shared/ui/error-note/error-note';
import PageHeader from '@/shared/ui/page-header/page-header';
import Spinner from '@/shared/ui/spinner/spinner';
import Tabs from '@/shared/ui/tabs/tabs';
import BookingItem from './booking-item';
import './my-bookings.css';

const TABS = [
  { value: 'active', label: 'Актуальные' },
  { value: 'past', label: 'Прошедшие' },
];

export default function MyBookings() {
  const bookings = useAsync(() => bookingsApi.mine(), []);
  const directory = useDirectory();
  const [tab, setTab] = useState('active');

  const groups = useMemo(() => {
    const list = [...(bookings.data || [])];
    const active = list
      .filter((item) => item.status === 'PENDING' || item.status === 'CONFIRMED')
      .sort((a, b) => a.startAt.localeCompare(b.startAt));
    const past = list
      .filter((item) => item.status !== 'PENDING' && item.status !== 'CONFIRMED')
      .sort((a, b) => b.startAt.localeCompare(a.startAt));
    return { active, past };
  }, [bookings.data]);

  const shown = groups[tab];
  const loading = bookings.loading || directory.loading;

  return (
    <div className="my-bookings">
      <PageHeader
        title="Мои брони"
        text="Статусы, оплата депозита и полная история каждого бронирования"
        actions={
          <Button href="/" variant="soft">
            Новая бронь
          </Button>
        }
      />

      <Tabs
        items={TABS.map((item) => ({ ...item, label: `${item.label} (${groups[item.value].length})` }))}
        value={tab}
        onChange={setTab}
      />

      <div className="my-bookings-list">
        {loading && <Spinner block label="Загружаем брони" />}
        <ErrorNote error={bookings.error} />

        {!loading && !bookings.error && shown.length === 0 && (
          <EmptyState
            icon="calendar"
            title={tab === 'active' ? 'Актуальных броней нет' : 'Прошедших броней нет'}
            text={tab === 'active' ? 'Выберите заведение и время, и бронь появится здесь.' : 'Завершённые и отменённые брони будут храниться здесь.'}
            action={tab === 'active' ? <Button href="/">Найти место</Button> : null}
          />
        )}

        {!loading &&
          shown.map((booking, index) => (
            <BookingItem
              key={booking.id}
              booking={booking}
              index={index}
              info={directory.lookup.get(booking.resourceId)}
              onChanged={bookings.reload}
            />
          ))}
      </div>
    </div>
  );
}
