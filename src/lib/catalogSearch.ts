export interface SearchEntry<T> {
  item: T;
  hay: string;
}

export function buildSearchIndex<T>(items: T[], toHay: (item: T) => string): SearchEntry<T>[] {
  return items.map((item) => ({
    item,
    hay: toHay(item).toLowerCase(),
  }));
}

export function filterSearchIndex<T>(
  index: SearchEntry<T>[],
  query: string,
  limit = 200,
): T[] {
  const q = query.toLowerCase().trim();
  if (!q) return index.slice(0, limit).map((e) => e.item);

  const words = q.split(/\s+/).filter((w) => w.length > 2);
  const results: T[] = [];

  for (const entry of index) {
    if (entry.hay.includes(q) || words.some((w) => entry.hay.includes(w))) {
      results.push(entry.item);
      if (results.length >= limit) break;
    }
  }

  return results;
}

export function mergeUniqueById<T extends { id: string }>(lists: T[][]): T[] {
  const seen = new Set<string>();
  const merged: T[] = [];
  for (const list of lists) {
    for (const item of list) {
      if (seen.has(item.id)) continue;
      seen.add(item.id);
      merged.push(item);
    }
  }
  return merged;
}
