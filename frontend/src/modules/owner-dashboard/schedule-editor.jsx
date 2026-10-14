'use client';

import { useEffect, useMemo, useState } from 'react';
import { scheduleApi } from '@/shared/api/endpoints';
import { useAsync } from '@/shared/hooks/use-async';
import Badge from '@/shared/ui/badge/badge';
import Button from '@/shared/ui/button/button';
import Card from '@/shared/ui/card/card';
import EmptyState from '@/shared/ui/empty-state/empty-state';
import ErrorNote from '@/shared/ui/error-note/error-note';
import { Field, Input, Select } from '@/shared/ui/field/field';
import Spinner from '@/shared/ui/spinner/spinner';
import { useToast } from '@/shared/ui/toast/toast';
import { DAYS_OF_WEEK } from '@/shared/lib/labels';
import { trimTime } from '@/shared/lib/format';

function DayRow({ resourceId, day, saved, onSaved }) {
  const toast = useToast();
  const [form, setForm] = useState({ openTime: '10:00', closeTime: '22:00', slotMinutes: 60 });
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    if (saved) {
      setForm({ openTime: trimTime(saved.openTime), closeTime: trimTime(saved.closeTime), slotMinutes: saved.slotMinutes });
    }
  }, [saved]);

  const update = (key) => (event) => setForm((current) => ({ ...current, [key]: event.target.value }));

  const save = async () => {
    setBusy(true);
    try {
      await scheduleApi.setDay(resourceId, day.key, { ...form, slotMinutes: Number(form.slotMinutes) });
      toast.success(`${day.label}: расписание сохранено`);
      onSaved();
    } catch (e) {
      toast.error(e.message);
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="schedule-row">
      <div className="schedule-day">
        <strong>{day.label}</strong>
        {saved ? <Badge tone="success">Настроено</Badge> : <Badge>Не настроено</Badge>}
      </div>
      <Input type="time" value={form.openTime} onChange={update('openTime')} aria-label="Открытие" />
      <Input type="time" value={form.closeTime} onChange={update('closeTime')} aria-label="Закрытие" />
      <Input type="number" min={5} max={1440} step={5} value={form.slotMinutes} onChange={update('slotMinutes')} aria-label="Длина слота в минутах" />
      <Button variant="soft" size="sm" loading={busy} onClick={save}>
        Сохранить
      </Button>
    </div>
  );
}

function ExceptionForm({ resourceId }) {
  const toast = useToast();
  const [date, setDate] = useState('');
  const [closed, setClosed] = useState(true);
  const [openTime, setOpenTime] = useState('12:00');
  const [closeTime, setCloseTime] = useState('16:00');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState(null);

  const submit = async (event) => {
    event.preventDefault();
    setBusy(true);
    setError(null);
    try {
      await scheduleApi.setException(resourceId, date, closed ? { closed: true } : { closed: false, openTime, closeTime });
      toast.success('Исключение сохранено');
    } catch (e) {
      setError(e);
    } finally {
      setBusy(false);
    }
  };

  return (
    <Card>
      <h3 className="section-title">Исключение на конкретную дату</h3>
      <form className="exception-form" onSubmit={submit}>
        <Field label="Дата">
          <Input type="date" value={date} onChange={(e) => setDate(e.target.value)} required />
        </Field>
        <Field label="Режим">
          <Select value={closed ? 'closed' : 'special'} onChange={(e) => setClosed(e.target.value === 'closed')}>
            <option value="closed">Выходной</option>
            <option value="special">Особые часы</option>
          </Select>
        </Field>
        {!closed && (
          <>
            <Field label="Открытие">
              <Input type="time" value={openTime} onChange={(e) => setOpenTime(e.target.value)} required />
            </Field>
            <Field label="Закрытие">
              <Input type="time" value={closeTime} onChange={(e) => setCloseTime(e.target.value)} required />
            </Field>
          </>
        )}
        <Button type="submit" loading={busy}>
          Сохранить
        </Button>
      </form>
      <ErrorNote error={error} />
    </Card>
  );
}

export default function ScheduleEditor({ resources }) {
  const [resourceId, setResourceId] = useState(resources[0]?.id || '');

  useEffect(() => {
    if (!resources.some((item) => item.id === resourceId)) {
      setResourceId(resources[0]?.id || '');
    }
  }, [resources, resourceId]);

  const schedule = useAsync(() => scheduleApi.get(resourceId), [resourceId], { immediate: Boolean(resourceId) });
  const byDay = useMemo(() => new Map((schedule.data || []).map((item) => [item.dayOfWeek, item])), [schedule.data]);

  if (resources.length === 0) {
    return (
      <Card>
        <EmptyState icon="clock" title="Сначала добавьте ресурс" text="Расписание настраивается для каждого столика или площадки отдельно." />
      </Card>
    );
  }

  return (
    <div className="stack-lg">
      <Card>
        <div className="schedule-head">
          <h3 className="section-title">Недельное расписание</h3>
          <Select value={resourceId} onChange={(e) => setResourceId(e.target.value)} aria-label="Ресурс">
            {resources.map((item) => (
              <option key={item.id} value={item.id}>
                {item.name}
              </option>
            ))}
          </Select>
        </div>

        <div className="schedule-legend">
          <span />
          <span>Открытие</span>
          <span>Закрытие</span>
          <span>Слот, мин</span>
          <span />
        </div>

        {schedule.loading && !schedule.data ? (
          <Spinner block label="Загружаем расписание" />
        ) : (
          DAYS_OF_WEEK.map((day) => (
            <DayRow key={`${resourceId}-${day.key}`} resourceId={resourceId} day={day} saved={byDay.get(day.key)} onSaved={schedule.reload} />
          ))
        )}
        <ErrorNote error={schedule.error} />
      </Card>

      <ExceptionForm resourceId={resourceId} key={resourceId} />
    </div>
  );
}
