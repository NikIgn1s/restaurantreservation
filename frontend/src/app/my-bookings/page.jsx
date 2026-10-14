import RequireAuth from '@/shared/auth/require-auth';
import MyBookings from '@/modules/my-bookings/my-bookings';

export const metadata = { title: 'Мои брони - Reserva' };

export default function MyBookingsPage() {
  return (
    <RequireAuth>
      <MyBookings />
    </RequireAuth>
  );
}
