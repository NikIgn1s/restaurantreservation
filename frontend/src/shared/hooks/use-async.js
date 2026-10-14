'use client';

import { useCallback, useEffect, useRef, useState } from 'react';

export function useAsync(factory, deps = [], { immediate = true } = {}) {
  const [data, setData] = useState(null);
  const [error, setError] = useState(null);
  const [loading, setLoading] = useState(immediate);
  const factoryRef = useRef(factory);
  const counter = useRef(0);
  factoryRef.current = factory;

  const run = useCallback(async () => {
    const id = ++counter.current;
    setLoading(true);
    setError(null);
    try {
      const result = await factoryRef.current();
      if (id === counter.current) {
        setData(result);
      }
      return result;
    } catch (e) {
      if (id === counter.current) {
        setError(e);
      }
      return null;
    } finally {
      if (id === counter.current) {
        setLoading(false);
      }
    }
  }, []);

  useEffect(() => {
    if (immediate) {
      run();
    }
    return () => {
      counter.current += 1;
    };
  }, deps);

  return { data, error, loading, reload: run, setData };
}
