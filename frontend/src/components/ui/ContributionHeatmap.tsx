
import * as Tooltip from "@radix-ui/react-tooltip";
import { motion } from "framer-motion";
import { profile } from "@/data/profile";

// Deterministic seeded RNG — same output every render/build
function seededRandom(seed: number): number {
  const x = Math.sin(seed + 1) * 10000;
  return x - Math.floor(x);
}

const WEEKS = 52;
const DAYS = 7;
const MOBILE_WEEKS = 16;

const MONTHS = ["Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"];
const DAY_NAMES = ["Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"];

// Generate 364 cells (52 weeks × 7 days) with activity level 0–4
const cells = Array.from({ length: WEEKS * DAYS }, (_, i) => {
  const r = seededRandom(i);
  if (r < 0.45) return 0;
  if (r < 0.62) return 1;
  if (r < 0.78) return 2;
  if (r < 0.91) return 3;
  return 4;
});

// Last 16 weeks of cells for mobile compact view
const mobileCells = cells.slice((WEEKS - MOBILE_WEEKS) * DAYS);

// Derived stats
const totalActive = cells.filter((c) => c > 0).length;

function computeLongestStreak(arr: number[]): number {
  let max = 0;
  let cur = 0;
  for (const v of arr) {
    if (v > 0) { cur++; max = Math.max(max, cur); }
    else cur = 0;
  }
  return max;
}

const streak = computeLongestStreak(cells);
const activeWeeks = Math.round(totalActive / 5);

const LEVEL_CLASSES = [
  "bg-terminal-surface",
  "bg-git-green/20",
  "bg-git-green/40",
  "bg-git-green/70",
  "bg-git-green",
];

// Place month labels at week indices where month changes (approximate)
const MONTH_POSITIONS = [0, 4, 8, 13, 17, 22, 26, 30, 35, 39, 43, 48];

// Mobile: compute last 4 months dynamically based on current date
const now = new Date();
const mobileMonthLabels = Array.from({ length: 4 }, (_, i) => {
  const d = new Date(now.getFullYear(), now.getMonth() - (3 - i), 1);
  return MONTHS[d.getMonth()];
});
const MOBILE_MONTH_POSITIONS = [0, 4, 8, 12];

const STATS = [
  { label: "contributions", value: totalActive },
  { label: "day streak",    value: streak },
  { label: "active weeks",  value: activeWeeks },
];

// Generate fake dates working backward from today for tooltip display
function getCellDate(cellIndex: number, totalCells: number): string {
  const daysAgo = totalCells - 1 - cellIndex;
  const date = new Date();
  date.setDate(date.getDate() - daysAgo);
  const dayName = DAY_NAMES[date.getDay()];
  const month = MONTHS[date.getMonth()];
  const day = date.getDate();
  return `${dayName}, ${month} ${day}`;
}

function HeatmapCell({ level, index, totalCells }: { level: number; index: number; totalCells: number }) {
  const dateStr = getCellDate(index, totalCells);
  const label = level === 0
    ? `No contributions on ${dateStr}`
    : `${level} contribution${level !== 1 ? "s" : ""} on ${dateStr}`;

  return (
    <Tooltip.Root>
      <Tooltip.Trigger asChild>
        <div
          className={`w-3 h-3 rounded-sm ${LEVEL_CLASSES[level]} transition-all duration-150 hover:ring-1 hover:ring-git-green/30 hover:opacity-80`}
        />
      </Tooltip.Trigger>
      <Tooltip.Portal>
        <Tooltip.Content
          className="z-50 rounded-lg border border-terminal-border bg-terminal-window px-2.5 py-1.5 font-mono text-[10px] text-text-muted shadow-terminal"
          sideOffset={4}
        >
          {label}
          <Tooltip.Arrow className="fill-terminal-border" />
        </Tooltip.Content>
      </Tooltip.Portal>
    </Tooltip.Root>
  );
}

function MobileHeatmapCell({ level, index, totalCells }: { level: number; index: number; totalCells: number }) {
  const dateStr = getCellDate(index, totalCells);
  const label = level === 0
    ? `No contributions on ${dateStr}`
    : `${level} contribution${level !== 1 ? "s" : ""} on ${dateStr}`;

  return (
    <Tooltip.Root>
      <Tooltip.Trigger asChild>
        <div
          className={`w-full aspect-square rounded-sm ${LEVEL_CLASSES[level]} transition-all duration-150`}
        />
      </Tooltip.Trigger>
      <Tooltip.Portal>
        <Tooltip.Content
          className="z-50 rounded-lg border border-terminal-border bg-terminal-window px-2.5 py-1.5 font-mono text-[10px] text-text-muted shadow-terminal"
          sideOffset={4}
        >
          {label}
          <Tooltip.Arrow className="fill-terminal-border" />
        </Tooltip.Content>
      </Tooltip.Portal>
    </Tooltip.Root>
  );
}

export function ContributionHeatmap() {
  return (
    <Tooltip.Provider delayDuration={100}>
      <motion.div
        initial={{ opacity: 0, y: 12 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ duration: 0.5, ease: "easeOut" }}
        className="py-8 px-4"
      >
        <div className="max-w-5xl mx-auto">
          {/* Section header */}
          <div className="flex items-center gap-3 mb-4 font-mono text-xs text-text-muted overflow-hidden">
            <span className="text-git-green shrink-0">$</span>
            <span className="truncate">
              git log --stat --since=&quot;1 year ago&quot; --author=&quot;{profile.handle}&quot;
            </span>
          </div>

          {/* ── MOBILE layout (< md) ─────────────────────────────────── */}
          <div className="block md:hidden space-y-4">
            {/* Stat cards */}
            <div className="grid grid-cols-3 gap-2">
              {STATS.map(({ label, value }) => (
                <div
                  key={label}
                  className="flex flex-col items-center py-3 px-2 rounded-xl border border-terminal-border bg-terminal-surface"
                >
                  <span className="text-xl font-bold font-mono text-git-green leading-none mb-1">
                    {value}
                  </span>
                  <span className="text-[10px] font-mono text-text-faint text-center leading-tight">
                    {label}
                  </span>
                </div>
              ))}
            </div>

            {/* 16-week compact heatmap */}
            <div className="rounded-xl border border-terminal-border bg-terminal-surface p-3">
              {/* Month labels */}
              <div
                className="mb-1 text-[9px] font-mono text-text-faint"
                style={{
                  display: "grid",
                  gridTemplateColumns: `repeat(${MOBILE_WEEKS}, minmax(0, 1fr))`,
                  gap: "2px",
                }}
              >
                {Array.from({ length: MOBILE_WEEKS }, (_, w) => {
                  const idx = MOBILE_MONTH_POSITIONS.indexOf(w);
                  return (
                    <span key={w} className="overflow-hidden whitespace-nowrap">
                      {idx >= 0 ? mobileMonthLabels[idx] : ""}
                    </span>
                  );
                })}
              </div>

              {/* Cell grid */}
              <div
                style={{
                  display: "grid",
                  gridTemplateColumns: `repeat(${MOBILE_WEEKS}, minmax(0, 1fr))`,
                  gridTemplateRows: `repeat(${DAYS}, minmax(0, 1fr))`,
                  gridAutoFlow: "column",
                  gap: "3px",
                }}
              >
                {mobileCells.map((level, i) => (
                  <MobileHeatmapCell key={i} level={level} index={i} totalCells={mobileCells.length} />
                ))}
              </div>

              {/* Legend */}
              <div className="mt-2.5 flex items-center justify-end gap-1 text-[9px] font-mono text-text-faint">
                <span>Less</span>
                {LEVEL_CLASSES.map((cls, i) => (
                  <div key={i} className={`w-2.5 h-2.5 rounded-sm ${cls}`} />
                ))}
                <span>More</span>
              </div>
            </div>
          </div>

          {/* ── DESKTOP layout (≥ md) ────────────────────────────────── */}
          <div className="hidden md:block overflow-x-auto pb-2">
            {/* Month labels */}
            <div
              className="mb-1 text-[10px] font-mono text-text-faint"
              style={{
                display: "grid",
                gridTemplateColumns: `repeat(${WEEKS}, minmax(0, 1fr))`,
                gap: "2px",
              }}
            >
              {Array.from({ length: WEEKS }, (_, w) => {
                const monthIdx = MONTH_POSITIONS.indexOf(w);
                return (
                  <span key={w} className="overflow-hidden whitespace-nowrap">
                    {monthIdx >= 0 ? MONTHS[monthIdx] : ""}
                  </span>
                );
              })}
            </div>

            {/* Cell grid — column-major (day within week) */}
            <div
              style={{
                display: "grid",
                gridTemplateColumns: `repeat(${WEEKS}, minmax(0, 1fr))`,
                gridTemplateRows: `repeat(${DAYS}, minmax(0, 1fr))`,
                gridAutoFlow: "column",
                gap: "2px",
              }}
            >
              {cells.map((level, i) => (
                <HeatmapCell key={i} level={level} index={i} totalCells={cells.length} />
              ))}
            </div>

            {/* Legend */}
            <div className="mt-2 flex items-center justify-end gap-1.5 text-[10px] font-mono text-text-faint">
              <span>Less</span>
              {LEVEL_CLASSES.map((cls, i) => (
                <div key={i} className={`w-3 h-3 rounded-sm ${cls}`} />
              ))}
              <span>More</span>
            </div>
          </div>
        </div>
      </motion.div>
    </Tooltip.Provider>
  );
}
