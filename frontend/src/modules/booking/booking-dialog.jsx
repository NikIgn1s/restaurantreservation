'use client';

import { useEffect, useState } from 'react';
import Link from 'next/link';
import { bookingsApi } from '@/shared/api/endpoints';
import { newIdempotencyKey } from '@/shared/api/client';
import Badge from '@/shared/ui/badge/badge';
import Button from '@/shared/ui/button/button';
import ErrorNote from '@/shared/ui/error-note/error-note';
import Icon from '@/shared/ui/icon/icon';
import Modal from '@/shared/ui/modal/modal';
import { useToast } from '@/shared/ui/toast/toast';
import { BOOKING_STATUS } from '@/shared/lib/labels';
import { formatDate, formatMoney, formatTime } from '@/shared/lib/format';

export default function BookingDialog({ open, onClose, venue, resource, slot, guests, standby, onBooked }) {
  const toast = useToast();
  const [idempotencyKey, setIdempotencyKey] = useState(null);
  const [booking, setBooking] = useState(null);
  const [payment, setPayment] = useState(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState(null);

  useEffect(() => {
    if (open) {
      setIdempotencyKey(newIdempotencyKey());
      setBooking(null);
      setPayment(null);
      setError(null);
      setBusy(false);
    }
  }, [open, slot?.startAt, resource?.id]);

  if (!open || !slot || !resource) {
    return null;
  }

  const confirmBooking = async () => {
    setBusy(true);
    setError(null);
    try {
      const created = await bookingsApi.create(
        { resourceId: resource.id, startAt: slot.startAt, endAt: slot.endAt, guests },
        idempotencyKey,
      );
      setBooking(created);
      onBooked?.(created);
      toast.success('Бронь создана');
    } catch (e) {
      setError(e);
    } finally {
      setBusy(false);
    }
  };

  const payDeposit = async () => {
    setBusy(true);
    setError(null);
    try {
      const result = await bookingsApi.payDeposit(booking.id);
      setBooking(result.booking);
      setPayment(result.payment);
      toast.success('Депозит оплачен');
    } catch (e) {
      setError(e);
    } finally {
      setBusy(false);
    }
  };

  const status = booking ? BOOKING_STATUS[booking.status] : null;
  const needsDeposit = booking && booking.depositRequired && booking.status === 'PENDING';

  const footer = !booking ? (
    <>
      <Button variant="ghost" onClick={onClose}>
        Отмена
      </Button>
      <Button loading={busy} onClick={confirmBooking}>
        {standby ? 'Встать в резерв' : 'Забронировать'}
      </Button>
    </>
  ) : needsDeposit ? (
    <>
      <Button variant="ghost" onClick={onClose}>
        Позже
      </Button>
      <Button loading={busy} onClick={payDeposit}>
        <Icon name="card" size={16} />
        Оплатить депозит
      </Button>
    </>
  ) : (
    <>
      <Button variant="ghost" onClick={onClose}>
        Закрыть
      </Button>
      <Button href="/my-bookings">Мои брони</Button>
    </>
  );

  return (
    <Modal open={open} onClose={onClose} title={booking ? 'Бронь оформлена' : 'Проверьте детали'} footer={footer}>
      <div className="dialog-summary">
        <div className="dialog-row">
          <Icon name="pin" size={18} />
          <div>
            <strong>{venue.name}</strong>
            <span>{resource.name}</span>
          </div>
        </div>
        <div className="dialog-row">
          <Icon name="calendar" size={18} />
          <div>
            <strong>{formatDate(slot.startAt, venue.timezone)}</strong>
            <span>
              {formatTime(slot.startAt, venue.timezone)} - {formatTime(slot.endAt, venue.timezone)}
            </span>
          </div>
        </div>
        <div className="dialog-row">
          <Icon name="users" size={18} />
          <div>
            <strong>
              {guests} {guests === 1 ? 'гость' : guests < 5 ? 'гостя' : 'гостей'}
            </strong>
            {standby && <span>Резервная бронь: место закрепится, если основная бронь освободится</span>}
          </div>
        </div>
      </div>

      {booking && (
        <div className="dialog-result">
          <div className="dialog-result-head">
            <Badge tone={status.tone}>{status.label}</Badge>
            {booking.overbooked && <Badge tone="info">Резерв</Badge>}
          </div>
          {needsDeposit && (
            <p>
              Для этой брони нужен депозит, чтобы место осталось за вами. Оплатите его сейчас, и бронь подтвердится
              автоматически.
            </p>
          )}
          {payment && (
            <p>
              Оплачено: <strong>{formatMoney(payment.amount, payment.currency)}</strong>
            </p>
          )}
          {!needsDeposit && booking.status === 'PENDING' && (
            <p>Заведение подтвердит бронь в ближайшее время. Статус виден в разделе «Мои брони».</p>
          )}
          {booking.status === 'CONFIRMED' && (
            <p>
              Всё готово, ждём вас. Историю и детали можно посмотреть в <Link href="/my-bookings">моих бронях</Link>.
            </p>
          )}
        </div>
      )}

      <ErrorNote error={error} />
    </Modal>
  );
}
