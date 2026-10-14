'use client';

import { bookingsApi } from '@/shared/api/endpoints';
import { useAsync } from '@/shared/hooks/use-async';
import ErrorNote from '@/shared/ui/error-note/error-note';
import Spinner from '@/shared/ui/spinner/spinner';
import { EVENT_LABELS, actorText } from '@/shared/lib/labels';
import { formatDateTime, formatMoney } from '@/shared/lib/format';

const TONES = {
  CREATED: 'primary',
  STANDBY_PLACED: 'info',
  STANDBY_PROMOTED: 'success',
  CONFIRMED: 'success',
  CANCELLED: 'danger',
  EXPIRED: 'muted',
  COMPLETED: 'info',
  NO_SHOW: 'danger',
  REMINDER_SENT: 'muted',
  PAYMENT_STARTED: 'warning',
  PAYMENT_PAID: 'success',
  PAYMENT_FAILED: 'danger',
  PAYMENT_REFUNDED: 'info',
};

function details(event) {
  const payload = event.payload || {};
  const parts = [];
  if (event.type === 'CREATED' && payload.guests) {
    parts.push(`Гостей: ${payload.guests}`);
  }
  if (event.type === 'CREATED' && payload.depositRequired) {
    parts.push('Нужен депозит');
  }
  if (payload.amount) {
    parts.push(formatMoney(payload.amount, payload.currency));
  }
  return parts.join(' / ');
}

export default function BookingTimeline({ bookingId, timeZone }) {
  const { data, error, loading } = useAsync(() => bookingsApi.events(bookingId), [bookingId]);

  if (loading) {
    return <Spinner label="Загружаем историю" />;
  }

  if (error) {
    return <ErrorNote error={error} />;
  }

  return (
    <ol className="timeline">
      {(data || []).map((event, index) => {
        const extra = details(event);
        return (
          <li key={event.id} className={`timeline-item tone-${TONES[event.type] || 'muted'}`} style={{ animationDelay: `${index * 50}ms` }}>
            <span className="timeline-dot" />
            <div className="timeline-content">
              <strong>{EVENT_LABELS[event.type] || event.type}</strong>
              <span>
                {formatDateTime(event.createdAt, timeZone)}
                {event.payload?.actor ? ` / ${actorText(event.payload.actor)}` : ''}
                {extra ? ` / ${extra}` : ''}
              </span>
            </div>
          </li>
        );
      })}
    </ol>
  );
}
