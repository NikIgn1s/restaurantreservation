import Link from 'next/link';
import Card from '@/shared/ui/card/card';
import Icon from '@/shared/ui/icon/icon';
import { VENUE_TYPES } from '@/shared/lib/labels';

const TYPE_ICON = { RESTAURANT: 'store', COWORKING: 'users', SPORT: 'spark' };

export default function VenueCard({ venue, index = 0 }) {
  return (
    <Link href={`/venues/${venue.id}`} className="venue-link" style={{ animationDelay: `${Math.min(index, 8) * 60}ms` }}>
      <Card interactive padded={false} className="venue-card">
        <div className={`venue-cover venue-cover-${venue.type.toLowerCase()}`}>
          <span className="venue-cover-icon">
            <Icon name={TYPE_ICON[venue.type]} size={30} strokeWidth={1.6} />
          </span>
          <span className="venue-type-pill">{VENUE_TYPES[venue.type]}</span>
        </div>
        <div className="venue-body">
          <h3>{venue.name}</h3>
          {venue.address && (
            <p className="venue-line">
              <Icon name="pin" size={15} />
              {venue.address}
            </p>
          )}
          <p className="venue-line">
            <Icon name="clock" size={15} />
            {venue.timezone}
          </p>
          <span className="venue-more">
            Выбрать время
            <Icon name="arrow" size={16} />
          </span>
        </div>
      </Card>
    </Link>
  );
}
