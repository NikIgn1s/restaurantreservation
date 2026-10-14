'use client';

import { useState } from 'react';
import { venuesApi } from '@/shared/api/endpoints';
import Button from '@/shared/ui/button/button';
import Card from '@/shared/ui/card/card';
import EmptyState from '@/shared/ui/empty-state/empty-state';
import ErrorNote from '@/shared/ui/error-note/error-note';
import { Field, Input } from '@/shared/ui/field/field';
import Icon from '@/shared/ui/icon/icon';
import { useToast } from '@/shared/ui/toast/toast';

export default function VenueManager({ venue, resources, onChanged }) {
  const toast = useToast();
  const [name, setName] = useState('');
  const [capacity, setCapacity] = useState(4);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState(null);

  const submit = async (event) => {
    event.preventDefault();
    setBusy(true);
    setError(null);
    try {
      await venuesApi.createResource(venue.id, { name, capacity: Number(capacity) });
      toast.success('Ресурс добавлен');
      setName('');
      onChanged();
    } catch (e) {
      setError(e);
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="manager">
      <Card>
        <h3 className="section-title">Добавить столик, место или корт</h3>
        <form className="manager-form" onSubmit={submit}>
          <Field label="Название">
            <Input value={name} onChange={(e) => setName(e.target.value)} placeholder="Столик 1" required maxLength={255} />
          </Field>
          <Field label="Вместимость">
            <Input type="number" min={1} max={1000} value={capacity} onChange={(e) => setCapacity(e.target.value)} required />
          </Field>
          <Button type="submit" loading={busy}>
            <Icon name="plus" size={16} />
            Добавить
          </Button>
        </form>
        <ErrorNote error={error} />
      </Card>

      {resources.length === 0 ? (
        <Card>
          <EmptyState
            icon="store"
            title="Ресурсов пока нет"
            text="Добавьте хотя бы один столик или площадку, затем настройте для него расписание."
          />
        </Card>
      ) : (
        <div className="resource-grid">
          {resources.map((resource, index) => (
            <Card key={resource.id} className="resource-card" style={{ animationDelay: `${Math.min(index, 8) * 50}ms` }}>
              <span className="resource-icon">
                <Icon name="store" size={20} />
              </span>
              <div>
                <h4>{resource.name}</h4>
                <p>
                  <Icon name="users" size={14} />
                  до {resource.capacity}
                </p>
              </div>
            </Card>
          ))}
        </div>
      )}
    </div>
  );
}
