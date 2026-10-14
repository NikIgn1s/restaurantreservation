import VenueDetails from '@/modules/venues/venue-details';

export const metadata = { title: 'Заведение - Reserva' };

export default function VenuePage({ params }) {
  return <VenueDetails venueId={params.venueId} />;
}
