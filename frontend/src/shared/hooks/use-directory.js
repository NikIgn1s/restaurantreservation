'use client';

import { useMemo } from 'react';
import { venuesApi } from '@/shared/api/endpoints';
import { useAsync } from './use-async';

export function useDirectory() {
  const state = useAsync(async () => {
    const venues = await venuesApi.list();
    const lists = await Promise.all(venues.map((venue) => venuesApi.resources(venue.id).catch(() => [])));
    return venues.map((venue, index) => ({ venue, resources: lists[index] }));
  }, []);

  const lookup = useMemo(() => {
    const map = new Map();
    (state.data || []).forEach(({ venue, resources }) => {
      resources.forEach((resource) => map.set(resource.id, { resource, venue }));
    });
    return map;
  }, [state.data]);

  return { ...state, lookup };
}
