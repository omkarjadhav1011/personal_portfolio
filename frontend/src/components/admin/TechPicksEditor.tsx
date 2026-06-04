
import { useState, useRef, useEffect } from "react";
import type { TechPick } from "@/types";

// ─── Curated catalog ─────────────────────────────────────────────────────────

interface CatalogItem { name: string; glyph: string; tint: string }
interface CatalogCategory { label: string; items: CatalogItem[] }

const CATALOG: CatalogCategory[] = [
  {
    label: "Languages",
    items: [
      { name: "HTML/CSS",    glyph: "🌐", tint: "#e34c26" },
      { name: "JavaScript",  glyph: "JS", tint: "#f7df1e" },
      { name: "TypeScript",  glyph: "TS", tint: "#3178c6" },
      { name: "Python",      glyph: "🐍", tint: "#3776ab" },
      { name: "Java",        glyph: "☕", tint: "#f89820" },
      { name: "PHP",         glyph: "🐘", tint: "#777bb4" },
      { name: "Rust",        glyph: "🦀", tint: "#ce422b" },
      { name: "Go",          glyph: "Go", tint: "#00add8" },
      { name: "Ruby",        glyph: "💎", tint: "#cc342d" },
      { name: "Swift",       glyph: "🐦", tint: "#fa7343" },
      { name: "Kotlin",      glyph: "K",  tint: "#7f52ff" },
      { name: "C#",          glyph: "C#", tint: "#512bd4" },
      { name: "C++",         glyph: "C+", tint: "#659ad2" },
      { name: "Dart",        glyph: "🎯", tint: "#0175c2" },
      { name: "R",           glyph: "R",  tint: "#276dc3" },
      { name: "Scala",       glyph: "Sc", tint: "#dc322f" },
      { name: "Elixir",      glyph: "💧", tint: "#6e4a7e" },
      { name: "Lua",         glyph: "Lu", tint: "#000080" },
    ],
  },
  {
    label: "Frontend",
    items: [
      { name: "React",       glyph: "⚛",  tint: "#61dafb" },
      { name: "Vue.js",      glyph: "V",  tint: "#42b883" },
      { name: "Angular",     glyph: "Ng", tint: "#dd0031" },
      { name: "Next.js",     glyph: "▲",  tint: "#ffffff" },
      { name: "Svelte",      glyph: "S",  tint: "#ff3e00" },
      { name: "Tailwind",    glyph: "≈",  tint: "#38bdf8" },
      { name: "Flutter",     glyph: "◆",  tint: "#02569b" },
      { name: "Vite",        glyph: "⚡", tint: "#646cff" },
      { name: "Astro",       glyph: "🚀", tint: "#ff5d01" },
      { name: "SolidJS",     glyph: "◈",  tint: "#2c4f7c" },
    ],
  },
  {
    label: "Backend",
    items: [
      { name: "Node.js",     glyph: "⬢",  tint: "#3c873a" },
      { name: "Spring",      glyph: "🌿", tint: "#6db33f" },
      { name: "Django",      glyph: "Dj", tint: "#0c4b33" },
      { name: "FastAPI",     glyph: "⚡", tint: "#009688" },
      { name: "Laravel",     glyph: "L",  tint: "#ff2d20" },
      { name: "Express",     glyph: "Ex", tint: "#68a063" },
      { name: "NestJS",      glyph: "Ns", tint: "#e0234e" },
      { name: "Rails",       glyph: "💎", tint: "#cc0000" },
      { name: "Flask",       glyph: "Fl", tint: "#ffffff" },
      { name: "GraphQL",     glyph: "◈",  tint: "#e535ab" },
    ],
  },
  {
    label: "Databases",
    items: [
      { name: "PostgreSQL",  glyph: "◆",  tint: "#336791" },
      { name: "MySQL",       glyph: "⊙",  tint: "#4479a1" },
      { name: "MongoDB",     glyph: "🍃", tint: "#47a248" },
      { name: "Redis",       glyph: "⬡",  tint: "#dc382c" },
      { name: "SQLite",      glyph: "◇",  tint: "#003b57" },
      { name: "Cassandra",   glyph: "C",  tint: "#1287b1" },
      { name: "Elasticsearch", glyph: "E", tint: "#f9a825" },
      { name: "Supabase",    glyph: "Sb", tint: "#3ecf8e" },
      { name: "Firebase",    glyph: "🔥", tint: "#ffca28" },
    ],
  },
  {
    label: "Data Science",
    items: [
      { name: "NumPy",       glyph: "N",  tint: "#4dabcf" },
      { name: "Pandas",      glyph: "🐼", tint: "#e70488" },
      { name: "TensorFlow",  glyph: "T",  tint: "#ff6f00" },
      { name: "PyTorch",     glyph: "🔥", tint: "#ee4c2c" },
      { name: "Jupyter",     glyph: "J",  tint: "#f37626" },
      { name: "scikit-learn",glyph: "Sk", tint: "#f89939" },
      { name: "Matplotlib",  glyph: "📊", tint: "#11557c" },
      { name: "Hugging Face",glyph: "🤗", tint: "#ffcc00" },
    ],
  },
  {
    label: "DevOps & Cloud",
    items: [
      { name: "Git",         glyph: "⎇",  tint: "#f05033" },
      { name: "Docker",      glyph: "🐳", tint: "#2496ed" },
      { name: "Kubernetes",  glyph: "☸",  tint: "#326ce5" },
      { name: "AWS",         glyph: "☁",  tint: "#ff9900" },
      { name: "GCP",         glyph: "☁",  tint: "#4285f4" },
      { name: "Azure",       glyph: "Az", tint: "#0078d4" },
      { name: "Linux",       glyph: "🐧", tint: "#fcc624" },
      { name: "Terraform",   glyph: "T",  tint: "#7b42bc" },
      { name: "GitHub",      glyph: "⎇",  tint: "#f0f6fc" },
      { name: "Vercel",      glyph: "▲",  tint: "#ffffff" },
    ],
  },
];

// ─── Helpers ─────────────────────────────────────────────────────────────────

const inputCls =
  "w-full bg-terminal-bg border border-terminal-border rounded px-2 py-1 font-mono text-xs text-text-primary placeholder-text-faint outline-none focus:border-git-blue/50 focus-visible:ring-1 focus-visible:ring-git-blue/20 transition-colors";

function MiniChip({ item }: { item: TechPick }) {
  const isUrl = item.glyph.startsWith("http") || item.glyph.startsWith("/");
  const cp = item.glyph.codePointAt(0) ?? 0;
  const isEmoji = !isUrl && cp > 0x7f;
  return (
    <div
      className="inline-flex items-center gap-1.5 pl-1 pr-2 py-0.5 rounded-full font-mono text-[10px]"
      style={{
        background: "rgb(var(--color-terminal-bg) / 0.7)",
        border: "1px solid rgb(var(--color-terminal-border))",
        color: "rgb(var(--color-text-primary))",
      }}
    >
      <span
        className="flex items-center justify-center rounded-full font-bold overflow-hidden shrink-0"
        style={{
          width: 16,
          height: 16,
          background: `${item.tint}22`,
          border: `1px solid ${item.tint}55`,
          color: item.tint,
          fontSize: isUrl ? undefined : (isEmoji ? 10 : 9),
          lineHeight: 1,
        }}
      >
        {isUrl ? (
          <img src={item.glyph} alt="" className="w-full h-full object-contain p-px" />
        ) : item.glyph}
      </span>
      <span className="truncate max-w-[80px]">{item.name}</span>
    </div>
  );
}

// ─── Catalog picker popover ───────────────────────────────────────────────────

interface CatalogPickerProps {
  onPick: (item: CatalogItem) => void;
  onClose: () => void;
}

function CatalogPicker({ onPick, onClose }: CatalogPickerProps) {
  const [search, setSearch] = useState("");
  const ref = useRef<HTMLDivElement>(null);

  useEffect(() => {
    function handleClick(e: MouseEvent) {
      if (ref.current && !ref.current.contains(e.target as Node)) onClose();
    }
    document.addEventListener("mousedown", handleClick);
    return () => document.removeEventListener("mousedown", handleClick);
  }, [onClose]);

  const q = search.toLowerCase();
  const filtered = CATALOG.map((cat) => ({
    ...cat,
    items: cat.items.filter((it) => it.name.toLowerCase().includes(q)),
  })).filter((cat) => cat.items.length > 0);

  return (
    <div
      ref={ref}
      className="absolute z-50 top-full mt-1 left-0 w-72 rounded-xl border border-terminal-border bg-terminal-surface shadow-terminal overflow-hidden"
    >
      <div className="p-2 border-b border-terminal-border">
        <input
          autoFocus
          type="text"
          placeholder="Search tech…"
          value={search}
          onChange={(e) => setSearch(e.target.value)}
          className={inputCls}
        />
      </div>
      <div className="max-h-64 overflow-y-auto p-2 space-y-3">
        {filtered.map((cat) => (
          <div key={cat.label}>
            <div className="text-[9px] font-mono uppercase tracking-widest text-text-faint mb-1 px-1">
              {cat.label}
            </div>
            <div className="flex flex-wrap gap-1">
              {cat.items.map((item) => (
                <button
                  key={item.name}
                  type="button"
                  onClick={() => { onPick(item); onClose(); }}
                  className="inline-flex items-center gap-1 px-1.5 py-0.5 rounded text-[10px] font-mono border transition-colors hover:border-git-blue/50 hover:bg-git-blue/5"
                  style={{
                    borderColor: `${item.tint}44`,
                    background: `${item.tint}11`,
                    color: item.tint,
                  }}
                  title={item.name}
                >
                  <span style={{ fontSize: 11 }}>{item.glyph}</span>
                  <span className="text-text-primary">{item.name}</span>
                </button>
              ))}
            </div>
          </div>
        ))}
        {filtered.length === 0 && (
          <div className="text-[10px] text-text-faint font-mono px-1 py-2">No matches — use custom entry below</div>
        )}
      </div>
    </div>
  );
}

// ─── Single row editor ────────────────────────────────────────────────────────

interface RowProps {
  item: TechPick;
  isFirst: boolean;
  isLast: boolean;
  onUpdate: (updated: TechPick) => void;
  onDelete: () => void;
  onMoveUp: () => void;
  onMoveDown: () => void;
}

function TechRow({ item, isFirst, isLast, onUpdate, onDelete, onMoveUp, onMoveDown }: RowProps) {
  const [catalogOpen, setCatalogOpen] = useState(false);

  return (
    <div className="relative flex items-center gap-2 p-2 rounded-lg border border-terminal-border bg-terminal-bg/40 group">
      {/* Live preview chip */}
      <div className="shrink-0">
        <MiniChip item={item} />
      </div>

      {/* Name */}
      <input
        type="text"
        value={item.name}
        onChange={(e) => onUpdate({ ...item, name: e.target.value })}
        placeholder="name"
        className={`${inputCls} flex-1 min-w-0`}
      />

      {/* Glyph + catalog picker */}
      <div className="relative shrink-0">
        <div className="flex items-center gap-1">
          <input
            type="text"
            value={item.glyph}
            onChange={(e) => onUpdate({ ...item, glyph: e.target.value })}
            placeholder="glyph"
            className={`${inputCls} w-14 text-center`}
            title="Emoji, 1-3 char text, or image URL"
          />
          <button
            type="button"
            onClick={() => setCatalogOpen((o) => !o)}
            className="shrink-0 px-1.5 py-1 rounded border border-terminal-border text-[10px] font-mono text-text-faint hover:text-text-primary hover:border-git-blue/40 transition-colors"
            title="Pick from catalog"
          >
            ⊞
          </button>
        </div>
        {catalogOpen && (
          <CatalogPicker
            onPick={(c) => onUpdate({ ...item, name: item.name || c.name, glyph: c.glyph, tint: c.tint })}
            onClose={() => setCatalogOpen(false)}
          />
        )}
      </div>

      {/* Color swatch + hex input */}
      <div className="flex items-center gap-1 shrink-0">
        <input
          type="color"
          value={item.tint}
          onChange={(e) => onUpdate({ ...item, tint: e.target.value })}
          className="w-6 h-6 rounded border border-terminal-border bg-terminal-bg cursor-pointer p-0.5"
          title="Tint color"
        />
        <input
          type="text"
          value={item.tint}
          onChange={(e) => {
            const v = e.target.value;
            if (/^#[0-9a-fA-F]{0,6}$/.test(v)) onUpdate({ ...item, tint: v });
          }}
          placeholder="#3178c6"
          className={`${inputCls} w-20`}
        />
      </div>

      {/* Reorder + delete */}
      <div className="flex items-center gap-0.5 shrink-0">
        <button
          type="button"
          onClick={onMoveUp}
          disabled={isFirst}
          className="p-1 rounded text-text-faint hover:text-text-primary disabled:opacity-30 transition-colors"
          title="Move up"
        >↑</button>
        <button
          type="button"
          onClick={onMoveDown}
          disabled={isLast}
          className="p-1 rounded text-text-faint hover:text-text-primary disabled:opacity-30 transition-colors"
          title="Move down"
        >↓</button>
        <button
          type="button"
          onClick={onDelete}
          className="p-1 rounded text-git-red/60 hover:text-git-red transition-colors"
          title="Remove"
        >×</button>
      </div>
    </div>
  );
}

// ─── Main editor ──────────────────────────────────────────────────────────────

interface TechPicksEditorProps {
  values: TechPick[];
  onChange: (values: TechPick[]) => void;
}

export function TechPicksEditor({ values, onChange }: TechPicksEditorProps) {
  const [catalogOpen, setCatalogOpen] = useState(false);
  const addBtnRef = useRef<HTMLDivElement>(null);

  function addItem() {
    onChange([...values, { name: "", glyph: "✦", tint: "#3178c6" }]);
  }

  function addFromCatalog(item: CatalogItem) {
    onChange([...values, { name: item.name, glyph: item.glyph, tint: item.tint }]);
  }

  function updateItem(i: number, updated: TechPick) {
    onChange(values.map((v, idx) => (idx === i ? updated : v)));
  }

  function deleteItem(i: number) {
    onChange(values.filter((_, idx) => idx !== i));
  }

  function moveUp(i: number) {
    if (i === 0) return;
    const arr = [...values];
    [arr[i - 1], arr[i]] = [arr[i], arr[i - 1]];
    onChange(arr);
  }

  function moveDown(i: number) {
    if (i === values.length - 1) return;
    const arr = [...values];
    [arr[i], arr[i + 1]] = [arr[i + 1], arr[i]];
    onChange(arr);
  }

  return (
    <div className="space-y-3">
      {/* Live preview */}
      {values.length > 0 && (
        <div className="flex flex-wrap gap-1.5 p-3 rounded-lg bg-terminal-bg/40 border border-terminal-border/50">
          {values.map((t, i) => (
            <MiniChip key={i} item={t} />
          ))}
        </div>
      )}

      {/* Editable rows */}
      <div className="space-y-1.5">
        {values.map((item, i) => (
          <TechRow
            key={i}
            item={item}
            isFirst={i === 0}
            isLast={i === values.length - 1}
            onUpdate={(u) => updateItem(i, u)}
            onDelete={() => deleteItem(i)}
            onMoveUp={() => moveUp(i)}
            onMoveDown={() => moveDown(i)}
          />
        ))}
      </div>

      {/* Add controls */}
      <div ref={addBtnRef} className="relative flex gap-2">
        <button
          type="button"
          onClick={addItem}
          className="flex-1 py-1.5 rounded-lg border border-dashed border-terminal-border text-[10px] font-mono text-text-faint hover:border-git-green/50 hover:text-git-green transition-colors"
        >
          + custom entry
        </button>
        <button
          type="button"
          onClick={() => setCatalogOpen((o) => !o)}
          className="px-3 py-1.5 rounded-lg border border-dashed border-terminal-border text-[10px] font-mono text-text-faint hover:border-git-blue/50 hover:text-git-blue transition-colors"
        >
          + from catalog
        </button>
        {catalogOpen && (
          <CatalogPicker
            onPick={(item) => { addFromCatalog(item); setCatalogOpen(false); }}
            onClose={() => setCatalogOpen(false)}
          />
        )}
      </div>

      <p className="font-mono text-[10px] text-text-faint">
        Glyph: emoji, 1-3 char text, or an image URL (http/https or /path)
      </p>
    </div>
  );
}
