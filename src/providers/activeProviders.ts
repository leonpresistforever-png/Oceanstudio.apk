const ACTIVE_KEY = 'ocean-active-providers';
const LEGACY_ACTIVE_KEY = 'ocean-active-provider';

export function getActiveProviderIds(): string[] {
  try {
    const raw = localStorage.getItem(ACTIVE_KEY);
    if (raw) {
      const parsed = JSON.parse(raw);
      if (Array.isArray(parsed)) return parsed;
    }
    const legacy = localStorage.getItem(LEGACY_ACTIVE_KEY);
    return legacy ? [legacy] : [];
  } catch {
    return [];
  }
}

export function setActiveProviderIds(ids: string[]): void {
  localStorage.setItem(ACTIVE_KEY, JSON.stringify([...new Set(ids)]));
  if (ids[0]) localStorage.setItem(LEGACY_ACTIVE_KEY, ids[0]);
  else localStorage.removeItem(LEGACY_ACTIVE_KEY);
}

export function toggleActiveProvider(id: string): string[] {
  const current = getActiveProviderIds();
  const next = current.includes(id) ? current.filter((x) => x !== id) : [...current, id];
  setActiveProviderIds(next);
  return next;
}

export function addActiveProvider(id: string): string[] {
  const next = [...new Set([...getActiveProviderIds(), id])];
  setActiveProviderIds(next);
  return next;
}

export function removeActiveProvider(id: string): string[] {
  const next = getActiveProviderIds().filter((x) => x !== id);
  setActiveProviderIds(next);
  return next;
}

/** @deprecated use getActiveProviderIds */
export function getActiveProviderId(): string | null {
  return getActiveProviderIds()[0] ?? null;
}

/** @deprecated use setActiveProviderIds */
export function setActiveProviderId(id: string | null): void {
  setActiveProviderIds(id ? [id] : []);
}
