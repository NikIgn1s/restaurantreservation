export const VENUE_TYPES = {
  RESTAURANT: 'Ресторан',
  COWORKING: 'Коворкинг',
  SPORT: 'Спорт',
};

export const ROLES = {
  CUSTOMER: 'Гость',
  VENUE_OWNER: 'Владелец',
  ADMIN: 'Администратор',
};

export const BOOKING_STATUS = {
  PENDING: { label: 'Ожидает', tone: 'warning' },
  CONFIRMED: { label: 'Подтверждена', tone: 'success' },
  CANCELLED: { label: 'Отменена', tone: 'danger' },
  EXPIRED: { label: 'Истекла', tone: 'neutral' },
  COMPLETED: { label: 'Завершена', tone: 'info' },
  NO_SHOW: { label: 'Неявка', tone: 'danger' },
};

export const PAYMENT_STATUS = {
  PENDING: { label: 'В обработке', tone: 'warning' },
  PAID: { label: 'Оплачен', tone: 'success' },
  FAILED: { label: 'Ошибка оплаты', tone: 'danger' },
  REFUNDED: { label: 'Возвращён', tone: 'info' },
};

export const EVENT_LABELS = {
  CREATED: 'Бронь создана',
  STANDBY_PLACED: 'Поставлена в резерв',
  STANDBY_PROMOTED: 'Резерв стал основной бронью',
  CONFIRMED: 'Бронь подтверждена',
  CANCELLED: 'Бронь отменена',
  EXPIRED: 'Срок брони истёк',
  COMPLETED: 'Визит завершён',
  NO_SHOW: 'Гость не пришёл',
  REMINDER_SENT: 'Отправлено напоминание',
  PAYMENT_STARTED: 'Начата оплата депозита',
  PAYMENT_PAID: 'Депозит оплачен',
  PAYMENT_FAILED: 'Оплата не прошла',
  PAYMENT_REFUNDED: 'Депозит возвращён',
};

export const DAYS_OF_WEEK = [
  { key: 'MONDAY', label: 'Понедельник', short: 'Пн' },
  { key: 'TUESDAY', label: 'Вторник', short: 'Вт' },
  { key: 'WEDNESDAY', label: 'Среда', short: 'Ср' },
  { key: 'THURSDAY', label: 'Четверг', short: 'Чт' },
  { key: 'FRIDAY', label: 'Пятница', short: 'Пт' },
  { key: 'SATURDAY', label: 'Суббота', short: 'Сб' },
  { key: 'SUNDAY', label: 'Воскресенье', short: 'Вс' },
];

export const TIMEZONES = [
  'Europe/Warsaw',
  'Europe/Moscow',
  'Europe/Kyiv',
  'Europe/London',
  'Europe/Berlin',
  'Asia/Almaty',
  'America/New_York',
  'UTC',
];

export function actorText(actor) {
  if (!actor) {
    return '';
  }
  if (actor.type === 'system') {
    return 'Система';
  }
  return ROLES[actor.role] || 'Пользователь';
}
