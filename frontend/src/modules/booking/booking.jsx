'use client';

import { useEffect, useMemo, useState } from 'react';
import { usePathname, useRouter } from 'next/navigation';
import { scheduleApi } from '@/shared/api/endpoints';
import { useAsync } from '@/shared/hooks/use-async';
import { useAuth } from '@/shared/auth/auth-provider';
import Button from '@/shared/ui/button/button';
import Card from '@/shared/ui/card/card';
import EmptyState from '@/shared/ui/empty-state/empty-state';
import ErrorNote from '@/shared/ui/error-note/error-note';
import { Field, Input } from '@/shared/ui/field/field';
import Icon from '@/shared/ui/icon/icon';
import Spinner from '@/shared/ui/spinner/spinner';
import { addDays, formatLongDate, todayIn } from '@/shared/lib/format';
import SlotGrid from './slot-grid';
import BookingDialog from './booking-dialog';
import './booking.css';

export default function BookingPanel({ venue, resources }) {
  const { user } = useAuth();
  const router = useRouter();
  const pathname = usePathname();
  const today = useMemo(() => todayIn(venue.timezone), [venue.timezone]);

  const [resourceId, setResourceId] = useState(resources[0]?.id || null);
  const [date, setDate] = useState(today);
  const [guests, setGuests] = useState(2);
  const [selected, setSelected] = useState(null);
  const [selectedStandby, setSelectedStandby] = useState(false);
  const [dialogOpen, setDialogOpen] = useState(false);

  const resource = resources.find((item) => item.id === resourceId) || null;

  const slots = useAsync(() => scheduleApi.slots(resourceId, date), [resourceId, date], { immediate: Boolean(resourceId) });
  const standby = useAsync(() => scheduleApi.overbookable(resourceId, date), [resourceId, date], {
    immediate: Boolean(resourceId),
  });

  useEffect(() => {
    setSelected(null);
  }, [resourceId, date]);

  useEffect(() => {
    setGuests((current) => Math.min(current, resource?.capacity || 1));
  }, [resource?.capacity]);

  const choose = (isStandby) => (slot) => {
    setSelected(slot);
    setSelectedStandby(isStandby);
  };

  const openDialog = () => {
    if (!user) {
      router.push(`/login?next=${encodeURIComponent(pathname)}`);
      return;
    }
    setDialogOpen(true);
  };

  const refreshSlots = () => {
    slots.reload();
    standby.reload();
    setSelected(null);
  };

  const capacity = resource?.capacity || 1;
  const freeSlots = slots.data || [];
  const standbySlots = standby.data || [];

  if (resources.length === 0) {
    return (
      <Card>
        <EmptyState icon="calendar" title="Пока нет мест для брони" text="Владелец ещё не добавил столики или площадки." />
      </Card>
    );
  }

  return (
    <Card className="booking-panel">
      <div className="booking-controls">
        <div className="booking-resources">
          <span className="booking-caption">Что бронируем</span>
          <div className="chip-row">
            {resources.map((item) => (
              <button
                key={item.id}
                type="button"
                className={item.id === resourceId ? 'chip is-active' : 'chip'}
                onClick={() => setResourceId(item.id)}
              >
                {item.name}
                <small>до {item.capacity}</small>
              </button>
            ))}
          </div>
        </div>

        <div className="booking-fields">
          <Field label="Дата">
            <div className="date-stepper">
              <button type="button" onClick={() => setDate((d) => (d > today ? addDays(d, -1) : d))} disabled={date <= today} aria-label="Предыдущий день">
                <Icon name="chevron" size={16} />
              </button>
              <Input type="date" value={date} min={today} onChange={(e) => e.target.value && setDate(e.target.value)} />
              <button type="button" onClick={() => setDate((d) => addDays(d, 1))} aria-label="Следующий день">
                <Icon name="chevron" size={16} />
              </button>
            </div>
          </Field>

          <Field label="Гостей">
            <div className="guest-stepper">
              <button type="button" onClick={() => setGuests((g) => Math.max(1, g - 1))} disabled={guests <= 1} aria-label="Меньше">
                <Icon name="minus" size={16} />
              </button>
              <span>{guests}</span>
              <button type="button" onClick={() => setGuests((g) => Math.min(capacity, g + 1))} disabled={guests >= capacity} aria-label="Больше">
                <Icon name="plus" size={16} />
              </button>
            </div>
          </Field>
        </div>
      </div>

      <div className="booking-slots">
        <h3 className="booking-day">{formatLongDate(date)}</h3>

        {slots.loading && <Spinner block label="Ищем свободное время" />}
        <ErrorNote error={slots.error} />

        {!slots.loading && !slots.error && freeSlots.length === 0 && (
          <EmptyState
            icon="clock"
            title="На этот день нет свободного времени"
            text="Попробуйте другую дату или другой столик."
          />
        )}

        {freeSlots.length > 0 && (
          <SlotGrid slots={freeSlots} timeZone={venue.timezone} selected={selectedStandby ? null : selected} onSelect={choose(false)} />
        )}

        {standbySlots.length > 0 && (
          <div className="standby-block">
            <div className="standby-head">
              <Icon name="shield" size={18} />
              <div>
                <strong>Можно встать в резерв</strong>
                <span>Время занято неоплаченной бронью. Если она освободится, место перейдёт к вам</span>
              </div>
            </div>
            <SlotGrid
              slots={standbySlots}
              timeZone={venue.timezone}
              selected={selectedStandby ? selected : null}
              onSelect={choose(true)}
              standby
            />
          </div>
        )}
      </div>

      <div className="booking-footer">
        <p>
          {selected ? 'Время выбрано, осталось подтвердить бронь' : 'Выберите время, чтобы продолжить'}
        </p>
        <Button size="lg" disabled={!selected} onClick={openDialog}>
          {user ? (selectedStandby ? 'Встать в резерв' : 'Продолжить') : 'Войти и забронировать'}
          <Icon name="arrow" size={18} />
        </Button>
      </div>

      <BookingDialog
        open={dialogOpen}
        onClose={() => {
          setDialogOpen(false);
          refreshSlots();
        }}
        venue={venue}
        resource={resource}
        slot={selected}
        guests={guests}
        standby={selectedStandby}
      />
    </Card>
  );
}
