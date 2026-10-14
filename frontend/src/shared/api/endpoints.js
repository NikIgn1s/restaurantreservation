import { newIdempotencyKey, request } from './client';

export const authApi = {
  login: (email, password) => request('/api/auth/login', { method: 'POST', body: { email, password } }),
  register: (payload) => request('/api/auth/register', { method: 'POST', body: payload }),
  me: () => request('/api/auth/me'),
};

export const venuesApi = {
  list: () => request('/api/venues'),
  create: (payload) => request('/api/venues', { method: 'POST', body: payload }),
  resources: (venueId) => request(`/api/venues/${venueId}/resources`),
  createResource: (venueId, payload) =>
    request(`/api/venues/${venueId}/resources`, { method: 'POST', body: payload }),
  dayBookings: (venueId, date) => request(`/api/venues/${venueId}/bookings`, { params: { date } }),
};

export const scheduleApi = {
  get: (resourceId) => request(`/api/resources/${resourceId}/schedule`),
  setDay: (resourceId, dayOfWeek, payload) =>
    request(`/api/resources/${resourceId}/schedule/${dayOfWeek}`, { method: 'PUT', body: payload }),
  setException: (resourceId, date, payload) =>
    request(`/api/resources/${resourceId}/schedule/exceptions/${date}`, { method: 'PUT', body: payload }),
  slots: (resourceId, date) => request(`/api/resources/${resourceId}/slots`, { params: { date } }),
  overbookable: (resourceId, date) =>
    request(`/api/resources/${resourceId}/slots/overbookable`, { params: { date } }),
};

export const bookingsApi = {
  create: (payload, idempotencyKey = newIdempotencyKey()) =>
    request('/api/bookings', { method: 'POST', body: payload, headers: { 'Idempotency-Key': idempotencyKey } }),
  mine: () => request('/api/bookings/me'),
  get: (bookingId) => request(`/api/bookings/${bookingId}`),
  events: (bookingId) => request(`/api/bookings/${bookingId}/events`),
  payments: (bookingId) => request(`/api/bookings/${bookingId}/payments`),
  payDeposit: (bookingId) => request(`/api/bookings/${bookingId}/deposit`, { method: 'POST' }),
  act: (bookingId, action) => request(`/api/bookings/${bookingId}/${action}`, { method: 'POST' }),
};
