'use client';

import { useMemo, useState } from 'react';
import { bookingsApi, venuesApi } from '@/shared/api/endpoints';
import { useAsync } from '@/shared/hooks/use-async';
import Badge from '@/shared/ui/badge/badge';
import Button from '@/shared/ui/button/button';
import Card from '@/shared/ui/card/card';
import EmptyState from '@/shared/ui/empty-state/empty-state';
import ErrorNote from '@/shared/ui/error-note/error-note';
import { Input } from '@/shared/ui/field/field';
import Icon from '@/shared/ui/icon/icon';
import Spinner from '@/shared/ui/spinner/spinner';
import { useToast } from '@/shared/ui/toast/toast';
import { BOOKING_STATUS } from '@/shared/lib/labels';
import { addDays, formatLongDate, formatTime, shortId, todayIn } from '@/shared/lib/format';
import BookingTimeline from '@/modules/my-bookings/booking-timeline';

const ACTIONS = {
  PENDING: [
    { key: 'confirm', label: 'Подтвердить', variant: 'success', done: 'Бронь подтверждена' },
    { key: 'cancel', label: 'Отменить', variant: 'danger', done: 'Бронь отменена' },
  ],
  CONFIRMED: [
    { key: 'complete', label: 'Гость пришёл', variant: 'success', done: 'Визит завершён' },
    { key: 'no-show', label: 'Не пришёл', variant: 'danger', done: 'Отмечена неявка' },
    { key: 'cancel', label: 'Отменить', variant: 'ghost', done: 'Бронь отменена' },
  ],
};

function Row({ booking, resourceName, timeZone, onChanged }) {
  const toast = useToast();
  const [busy, setBusy] = useState(null);
  const [showHistory, setShowHistory] = useState(false);
  const status = BOOKING_STATUS[booking.status];

  const run = async (action) => {
    setBusy(action.key);
    try {
      await bookingsApi.act(booking.id, action.key);
      toast.success(action.done);
      onChanged();
    } catch (e) {
      toast.error(e.message);
    } finally {
      setBusy(null);
    }
  };

  return (
    <Card className="day-row">
      <div className="day-row-main">
        <div className="day-row-time">
          <strong>{formatTime(booking.startAt, timeZone)}</strong>
          <span>{formatTime(booking.endAt, timeZone)}</span>
        </div>
        <div className="day-row-info">
          <h4>{resourceName}</h4>
          <p>
            <Icon name="users" size={14} />
            {booking.guests}
            <span className="dot" />
            Гость {shortId(booking.userId)}
            {booking.noShowProbability !== null && booking.noShowProbability !== undefined && (
              <>
                <span className="dot" />
                Риск неявки {Math.round(booking.noShowProbability * 100)}%
              </>
            )}
          </p>
        </div>
        <div className="day-row-badges">
          <Badge tone={status.tone}>{status.label}</Badge>
          {booking.overbooked && <Badge tone="info">Резерв</Badge>}
          {booking.depositRequired && <Badge tone="warning">Депозит</Badge>}
        </div>
      </div>

      <div className="day-row-actions">
        <Button variant="ghost" size="sm" onClick={() => setShowHistory((value) => !value)}>
          <Icon name="history" size={15} />
          {showHistory ? 'Скрыть' : 'История'}
        </Button>
        {(ACTIONS[booking.status] || []).map((action) => (
          <Button key={action.key} variant={action.variant} size="sm" loading={busy === action.key} onClick={() => run(action)}>
            {action.label}
          </Button>
        ))}
      </div>

      {showHistory && (
        <div className="day-row-history">
          <BookingTimeline bookingId={booking.id} timeZone={timeZone} />
        </div>
      )}
    </Card>
  );
}

export default function DayBookings({ venue, resources }) {
  const today = useMemo(() => todayIn(venue.timezone), [venue.timezone]);
  const [date, setDate] = useState(today);
  const { data, error, loading, reload } = useAsync(() => venuesApi.dayBookings(venue.id, date), [venue.id, date]);

  const names = useMemo(() => new Map(resources.map((item) => [item.id, item.name])), [resources]);
  const list = data || [];
  const counts = useMemo(
    () => ({
      total: list.length,
      active: list.filter((b) => b.status === 'PENDING' || b.status === 'CONFIRMED').length,
      guests: list.filter((b) => b.status === 'PENDING' || b.status === 'CONFIRMED').reduce((sum, b) => sum + b.guests, 0),
    }),
    [list],
  );

  return (
    <div className="stack-lg">
      <div className="day-toolbar">
        <div className="date-nav">
          <button type="button" onClick={() => setDate(addDays(date, -1))} aria-label="Предыдущий день">
            <Icon name="chevron" size={16} />
          </button>
          <Input type="date" value={date} onChange={(e) => e.target.value && setDate(e.target.value)} />
          <button type="button" onClick={() => setDate(addDays(date, 1))} aria-label="Следующий день">
            <Icon name="chevron" size={16} />
          </button>
        </div>
        <Button variant="soft" size="sm" onClick={() => setDate(today)} disabled={date === today}>
          Сегодня
        </Button>
        <span className="day-title">{formatLongDate(date)}</span>
      </div>

      <div className="stat-row">
        <div className="stat">
          <span>Всего броней</span>
          <strong>{counts.total}</strong>
        </div>
        <div className="stat">
          <span>Активных</span>
          <strong>{counts.active}</strong>
        </div>
        <div className="stat">
          <span>Гостей ожидается</span>
          <strong>{counts.guests}</strong>
        </div>
      </div>

      {loading && <Spinner block label="Загружаем день" />}
      <ErrorNote error={error} />

      {!loading && !error && list.length === 0 && (
        <EmptyState icon="calendar" title="На этот день броней нет" text="Как только гости забронируют время, брони появятся здесь." />
      )}

      {!loading &&
        list.map((booking) => (
          <Row
            key={booking.id}
            booking={booking}
            resourceName={names.get(booking.resourceId) || 'Ресурс'}
            timeZone={venue.timezone}
            onChanged={reload}
          />
        ))}
    </div>
  );
}
