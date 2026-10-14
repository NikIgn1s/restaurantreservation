'use client';

import { useState } from 'react';
import { venuesApi } from '@/shared/api/endpoints';
import Button from '@/shared/ui/button/button';
import ErrorNote from '@/shared/ui/error-note/error-note';
import { Field, Input, Select } from '@/shared/ui/field/field';
import Modal from '@/shared/ui/modal/modal';
import { useToast } from '@/shared/ui/toast/toast';
import { TIMEZONES, VENUE_TYPES } from '@/shared/lib/labels';

const EMPTY = { name: '', type: 'RESTAURANT', address: '', timezone: 'Europe/Warsaw' };

export default function CreateVenueDialog({ open, onClose, onCreated }) {
  const toast = useToast();
  const [form, setForm] = useState(EMPTY);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState(null);

  const update = (key) => (event) => setForm((current) => ({ ...current, [key]: event.target.value }));

  const close = () => {
    setForm(EMPTY);
    setError(null);
    onClose();
  };

  const submit = async (event) => {
    event.preventDefault();
    setBusy(true);
    setError(null);
    try {
      const venue = await venuesApi.create(form);
      toast.success('Заведение создано');
      setForm(EMPTY);
      onCreated(venue);
    } catch (e) {
      setError(e);
    } finally {
      setBusy(false);
    }
  };

  return (
    <Modal
      open={open}
      onClose={close}
      title="Новое заведение"
      footer={
        <>
          <Button variant="ghost" onClick={close}>
            Отмена
          </Button>
          <Button type="submit" form="create-venue" loading={busy}>
            Создать
          </Button>
        </>
      }
    >
      <form id="create-venue" className="stack" onSubmit={submit}>
        <Field label="Название">
          <Input value={form.name} onChange={update('name')} placeholder="Bistro Central" required maxLength={255} />
        </Field>
        <Field label="Тип">
          <Select value={form.type} onChange={update('type')}>
            {Object.entries(VENUE_TYPES).map(([value, label]) => (
              <option key={value} value={value}>
                {label}
              </option>
            ))}
          </Select>
        </Field>
        <Field label="Адрес">
          <Input value={form.address} onChange={update('address')} placeholder="Main street 1" maxLength={512} />
        </Field>
        <Field label="Часовой пояс" hint="Слоты и дни считаются в этом поясе">
          <Select value={form.timezone} onChange={update('timezone')}>
            {TIMEZONES.map((zone) => (
              <option key={zone} value={zone}>
                {zone}
              </option>
            ))}
          </Select>
        </Field>
        <ErrorNote error={error} />
      </form>
    </Modal>
  );
}
