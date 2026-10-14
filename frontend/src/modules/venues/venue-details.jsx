'use client';

import Link from 'next/link';
import { venuesApi } from '@/shared/api/endpoints';
import { useAsync } from '@/shared/hooks/use-async';
import EmptyState from '@/shared/ui/empty-state/empty-state';
import ErrorNote from '@/shared/ui/error-note/error-note';
import Icon from '@/shared/ui/icon/icon';
import Spinner from '@/shared/ui/spinner/spinner';
import Button from '@/shared/ui/button/button';
import { VENUE_TYPES } from '@/shared/lib/labels';
import BookingPanel from '@/modules/booking/booking';
import './venues.css';

const TYPE_ICON = { RESTAURANT: 'store', COWORKING: 'users', SPORT: 'spark' };

export default function VenueDetails({ venueId }) {
  const venue = useAsync(async () => {
    const all = await venuesApi.list();
    return all.find((item) => item.id === venueId) || null;
  }, [venueId]);
  const resources = useAsync(() => venuesApi.resources(venueId), [venueId]);

  if (venue.loading || resources.loading) {
    return <Spinner block label="Загружаем заведение" />;
  }

  if (venue.error || resources.error) {
    return <ErrorNote error={venue.error || resources.error} />;
  }

  if (!venue.data) {
    return (
      <EmptyState
        icon="store"
        title="Заведение не найдено"
        text="Возможно, оно скрыто или ссылка устарела."
        action={<Button href="/">К списку заведений</Button>}
      />
    );
  }

  const info = venue.data;
  const activeResources = (resources.data || []).filter((item) => item.active);

  return (
    <div>
      <Link href="/" className="back-link">
        <Icon name="arrow" size={16} />
        Все заведения
      </Link>

      <div className="venue-details-head">
        <div className="venue-details-title">
          <span className={`venue-details-badge venue-cover-${info.type.toLowerCase()}`}>
            <Icon name={TYPE_ICON[info.type]} size={30} strokeWidth={1.6} />
          </span>
          <div>
            <h1>{info.name}</h1>
            <div className="venue-details-meta">
              <span>{VENUE_TYPES[info.type]}</span>
              {info.address && (
                <span>
                  <Icon name="pin" size={15} />
                  {info.address}
                </span>
              )}
              <span>
                <Icon name="clock" size={15} />
                {info.timezone}
              </span>
            </div>
          </div>
        </div>
      </div>

      <BookingPanel venue={info} resources={activeResources} />
    </div>
  );
}
