import RequireAuth from '@/shared/auth/require-auth';
import OwnerDashboard from '@/modules/owner-dashboard/owner-dashboard';

export const metadata = { title: 'Кабинет - Reserva' };

export default function OwnerPage() {
  return (
    <RequireAuth roles={['VENUE_OWNER', 'ADMIN']}>
      <OwnerDashboard />
    </RequireAuth>
  );
}
