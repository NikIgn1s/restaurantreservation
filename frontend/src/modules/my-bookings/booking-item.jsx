'use client';

import { useState } from 'react';
import { bookingsApi } from '@/shared/api/endpoints';
import Badge from '@/shared/ui/badge/badge';
import Button from '@/shared/ui/button/button';
import Card from '@/shared/ui/card/card';
import Icon from '@/shared/ui/icon/icon';
import { useToast } from '@/shared/ui/toast/toast';
import { BOOKING_STATUS } from '@/shared/lib/labels';
import { formatDate, formatTime } from '@/shared/lib/format';
import BookingTimeline from './booking-timeline';

export default function BookingItem({ booking, info, index, onChanged }) {
  const toast = useToast();
  const [busy, setBusy] = useState(null);
  const [showHistory, setShowHistory] = useState(false);

  const timeZone = info?.venue.timezone || 'UTC';
  const status = BOOKING_STATUS[booking.status];
  const canCancel = booking.status === 'PENDING' || booking.status === 'CONFIRMED';
  const needsDeposit = booking.status === 'PENDING' && booking.depositRequired;

  const run = async (key, task, message) => {
    setBusy(key);
    try {
      await task();
      toast.success(message);
      onChanged();
    } catch (e) {
      toast.error(e.message);
    } finally {
      setBusy(null);
    }
  };

  return (
    <Card className="booking-item" style={{ animationDelay: `${Math.min(index, 8) * 60}ms` }}>
      <div className="booking-item-main">
        <div className="booking-item-date">
          <span className="booking-item-day">{formatDate(booking.startAt, timeZone)}</span>
          <span className="booking-item-time">
            {formatTime(booking.startAt, timeZone)} - {formatTime(booking.endAt, timeZone)}
          </span>
        </div>

        <div className="booking-item-info">
          <h3>{info ? info.venue.name : 'Заведение'}</h3>
          <p>
            {info ? info.resource.name : 'Место'}
            <span className="dot" />
            <Icon name="users" size={14} />
            {booking.guests}
          </p>
        </div>

        <div className="booking-item-badges">
          <Badge tone={status.tone}>{status.label}</Badge>
          {booking.overbooked && <Badge tone="info">Резерв</Badge>}
          {needsDeposit && <Badge tone="warning">Нужен депозит</Badge>}
        </div>
      </div>

      <div className="booking-item-actions">
        <Button variant="ghost" size="sm" onClick={() => setShowHistory((value) => !value)}>
          <Icon name="history" size={15} />
          {showHistory ? 'Скрыть историю' : 'История'}
        </Button>
        {needsDeposit && (
          <Button
            size="sm"
            loading={busy === 'deposit'}
            onClick={() => run('deposit', () => bookingsApi.payDeposit(booking.id), 'Депозит оплачен')}
          >
            <Icon name="card" size={15} />
            Оплатить депозит
          </Button>
        )}
        {canCancel && (
          <Button
            variant="danger"
            size="sm"
            loading={busy === 'cancel'}
            onClick={() => run('cancel', () => bookingsApi.act(booking.id, 'cancel'), 'Бронь отменена')}
          >
            Отменить
          </Button>
        )}
      </div>

      {showHistory && (
        <div className="booking-item-history">
          <BookingTimeline bookingId={booking.id} timeZone={timeZone} />
        </div>
      )}
    </Card>
  );
}
