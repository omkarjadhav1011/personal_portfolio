const MINUTE = 60;
const HOUR = 3600;
const DAY = 86400;
const WEEK = 604800;

export function formatRelativeTime(date: Date | string): string {
  const d = typeof date === "string" ? new Date(date) : date;
  const now = Date.now();
  const diff = Math.floor((now - d.getTime()) / 1000);

  if (diff < 0) return "just now";
  if (diff < MINUTE) return "just now";
  if (diff < HOUR) {
    const m = Math.floor(diff / MINUTE);
    return `${m} minute${m !== 1 ? "s" : ""} ago`;
  }
  if (diff < DAY) {
    const h = Math.floor(diff / HOUR);
    return `${h} hour${h !== 1 ? "s" : ""} ago`;
  }
  if (diff < DAY * 2) return "yesterday";
  if (diff < WEEK) {
    const days = Math.floor(diff / DAY);
    return `${days} days ago`;
  }
  if (diff < WEEK * 4) {
    const weeks = Math.floor(diff / WEEK);
    return `${weeks} week${weeks !== 1 ? "s" : ""} ago`;
  }

  // Older than 4 weeks — show absolute date
  return d.toLocaleDateString("en-US", { month: "short", day: "numeric", year: "numeric" });
}
