export function fuzzyMatch(query: string, target: string): { match: boolean; score: number } {
  const q = query.toLowerCase();
  const t = target.toLowerCase();

  if (q.length === 0) return { match: true, score: 0 };
  if (q.length > t.length) return { match: false, score: 0 };

  let score = 0;
  let qi = 0;
  let prevMatchIdx = -2;

  for (let ti = 0; ti < t.length && qi < q.length; ti++) {
    if (t[ti] === q[qi]) {
      score += 1;
      // Consecutive match bonus
      if (ti === prevMatchIdx + 1) score += 2;
      // Word boundary bonus (start of string or after space/hyphen)
      if (ti === 0 || t[ti - 1] === " " || t[ti - 1] === "-") score += 3;
      prevMatchIdx = ti;
      qi++;
    }
  }

  const match = qi === q.length;
  // Shorter targets rank higher when matched
  if (match) score += Math.max(0, 10 - t.length);

  return { match, score };
}

export function fuzzyFilter<T>(
  query: string,
  items: T[],
  getKey: (item: T) => string
): T[] {
  if (!query.trim()) return items;

  return items
    .map((item) => ({ item, ...fuzzyMatch(query, getKey(item)) }))
    .filter((r) => r.match)
    .sort((a, b) => b.score - a.score)
    .map((r) => r.item);
}
