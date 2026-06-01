
import { useState, useMemo } from "react";
import { Briefcase } from "lucide-react";
import { AdminModal } from "@/components/admin/AdminModal";
import { FormInput, FormTextarea, FormSelect } from "@/components/admin/FormField";
import { TagInput } from "@/components/admin/TagInput";
import { LoadingButton } from "@/components/ui/LoadingButton";
import { EmptyState } from "@/components/ui/EmptyState";
import { TableSearch } from "@/components/admin/TableSearch";
import { SortableTr, DragHandleCell, ReorderButtonsCell } from "@/components/admin/SortableTr";
import { useToast } from "@/components/admin/ToastProvider";
import { useFormValidation } from "@/hooks/useFormValidation";
import { useReorder } from "@/hooks/useReorder";
import { commitEntrySchema } from "@/lib/admin-validations";
import { useQueryClient } from "@tanstack/react-query";
import { authFetch } from "@/lib/api";
import { experienceKeys } from "@/api/experience";
import { motion, AnimatePresence, LayoutGroup } from "framer-motion";
import { DndContext, closestCenter } from "@dnd-kit/core";
import { SortableContext, verticalListSortingStrategy } from "@dnd-kit/sortable";

interface CommitEntry {
  id: string;
  hash: string;
  type: string;
  title: string;
  org: string;
  date: string;
  dateEnd?: string | null;
  description: string[];
  branch: string;
  branchColor: string;
  colorKey?: string | null;
  tags?: string[] | null;
  url?: string | null;
  order: number;
}

const EMPTY: Omit<CommitEntry, "id" | "order"> = {
  hash: "", type: "job", title: "", org: "", date: "", dateEnd: "",
  description: [], branch: "", branchColor: "#00ff88", colorKey: "green",
  tags: [], url: "",
};

const TYPE_OPTIONS = [
  { value: "job", label: "job" },
  { value: "education", label: "education" },
  { value: "achievement", label: "achievement" },
  { value: "project", label: "project" },
];

const COLOR_OPTIONS = [
  { value: "green", label: "green" },
  { value: "blue", label: "blue" },
  { value: "yellow", label: "yellow" },
  { value: "orange", label: "orange" },
];

const TYPE_ICONS: Record<string, string> = { job: "💼", education: "🎓", achievement: "🏆", project: "📁" };

export function ExperienceClient({ initialEntries }: { initialEntries: CommitEntry[] }) {
  const [modalOpen, setModalOpen] = useState(false);
  const [editing, setEditing] = useState<CommitEntry | null>(null);
  const [form, setForm] = useState<Omit<CommitEntry, "id" | "order">>(EMPTY);
  const [descText, setDescText] = useState("");
  const [loading, setLoading] = useState(false);
  const [deletingId, setDeletingId] = useState<string | null>(null);
  const [search, setSearch] = useState("");
  const { toast } = useToast();
  const queryClient = useQueryClient();
  const { errors, validate, clearErrors } = useFormValidation(commitEntrySchema);

  const { items: entries, setItems: setEntries, sensors, handleDragEnd, handleReorder, reorderingId } =
    useReorder(initialEntries, { type: "experience", toast });

  const searchActive = search.trim() !== "";

  const displayItems = useMemo(() => {
    const base = [...entries].sort((a, b) => a.order - b.order);
    if (!searchActive) return base;
    const q = search.toLowerCase();
    return base.filter(
      (e) =>
        e.title.toLowerCase().includes(q) ||
        e.org.toLowerCase().includes(q) ||
        e.type.toLowerCase().includes(q)
    );
  }, [entries, search, searchActive]);

  function openCreate() {
    setEditing(null);
    setForm(EMPTY);
    setDescText("");
    clearErrors();
    setModalOpen(true);
  }

  function openEdit(e: CommitEntry) {
    setEditing(e);
    setForm({ ...e, dateEnd: e.dateEnd ?? "", url: e.url ?? "", colorKey: e.colorKey ?? "green", tags: e.tags ?? [] });
    setDescText((e.description ?? []).join("\n"));
    clearErrors();
    setModalOpen(true);
  }

  async function handleDelete(id: string) {
    if (!confirm("Delete this entry?")) return;
    setDeletingId(id);
    try {
      const res = await authFetch(`/api/experience/${id}`, { method: "DELETE" });
      if (res.ok) {
        setEntries((prev) => prev.filter((e) => e.id !== id));
        toast("Entry deleted", "success");
        await queryClient.invalidateQueries({ queryKey: experienceKeys.all });
      } else {
        toast("Failed to delete", "error");
      }
    } finally {
      setDeletingId(null);
    }
  }

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    const description = descText.split("\n").map((l) => l.trim()).filter(Boolean);
    const data = { ...form, description, dateEnd: form.dateEnd || undefined, url: form.url || undefined };
    if (!validate(data)) return;
    setLoading(true);
    try {
      const url = editing ? `/api/experience/${editing.id}` : "/api/experience";
      const method = editing ? "PATCH" : "POST";
      const res = await authFetch(url, {
        method,
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(data),
      });
      if (res.ok) {
        const saved: CommitEntry = await res.json();
        if (editing) {
          setEntries((prev) => prev.map((e) => (e.id === saved.id ? saved : e)));
          toast("Entry updated", "success");
        } else {
          setEntries((prev) => [...prev, saved]);
          toast("Entry created", "success");
        }
        setModalOpen(false);
        await queryClient.invalidateQueries({ queryKey: experienceKeys.all });
      } else {
        const err = await res.json();
        toast(err.error?.message ?? "Failed to save", "error");
      }
    } finally {
      setLoading(false);
    }
  }

  function field(key: keyof typeof form, value: string | string[]) {
    setForm((prev) => ({ ...prev, [key]: value }));
  }

  return (
    <motion.div initial={{ opacity: 0 }} animate={{ opacity: 1 }} className="space-y-6 font-mono">
      <div className="flex items-center justify-between">
        <div>
          <div className="text-text-faint text-xs mb-1">$ git log --all --pretty=format:&quot;%h %s&quot;</div>
          <h1 className="text-xl font-bold text-text-primary">Experience</h1>
          <p className="text-text-muted text-xs mt-0.5"># {entries.length} commit entries</p>
        </div>
        <button
          onClick={openCreate}
          className="flex items-center gap-2 px-4 py-2 rounded-lg border border-git-green/40 bg-git-green/10 text-git-green text-xs hover:bg-git-green/20 transition-all"
        >
          <span>+</span> new entry
        </button>
      </div>

      {/* Search */}
      <TableSearch value={search} onChange={setSearch} placeholder="Search entries..." />

      <DndContext sensors={sensors} collisionDetection={closestCenter} onDragEnd={handleDragEnd}>
      <div className="rounded-xl border border-terminal-border bg-terminal-surface overflow-hidden">
        <div className="overflow-x-auto">
          <table className="w-full text-xs">
            <thead>
              <tr className="border-b border-terminal-border bg-terminal-bg">
                <th className="px-2 py-3 w-6" />
                <th className="text-left px-4 py-3 text-text-muted font-normal">hash</th>
                <th className="text-left px-4 py-3 text-text-muted font-normal">type</th>
                <th className="text-left px-4 py-3 text-text-muted font-normal">title</th>
                <th className="text-left px-4 py-3 text-text-muted font-normal">org</th>
                <th className="text-left px-4 py-3 text-text-muted font-normal">date</th>
                <th className="px-3 py-3 text-center text-text-muted font-normal w-16">order</th>
                <th className="text-right px-4 py-3 text-text-muted font-normal">actions</th>
              </tr>
            </thead>
            <SortableContext items={displayItems.map((e) => e.id)} strategy={verticalListSortingStrategy}>
              <LayoutGroup>
                <tbody>
                    {displayItems.length === 0 && (
                      <tr>
                        <td colSpan={8}>
                          <EmptyState
                            icon={<Briefcase size={32} />}
                            title={search ? "No entries match your search" : "No entries yet"}
                            description={search ? "Try a different search term" : "Add your first commit entry"}
                            action={!search ? { label: "+ new entry", onClick: openCreate } : undefined}
                          />
                        </td>
                      </tr>
                    )}
                    <AnimatePresence>
                      {displayItems.map((e, idx) => (
                        <SortableTr key={e.id} id={e.id} disabled={searchActive || reorderingId !== null}>
                          <DragHandleCell />
                          <td className="px-4 py-3 text-git-orange font-mono">{e.hash.slice(0, 7)}</td>
                          <td className="px-4 py-3">{TYPE_ICONS[e.type]} {e.type}</td>
                          <td className="px-4 py-3 text-text-primary">{e.title}</td>
                          <td className="px-4 py-3 text-text-muted">{e.org}</td>
                          <td className="px-4 py-3 text-text-faint">{e.date}{e.dateEnd ? ` — ${e.dateEnd}` : ""}</td>
                          <ReorderButtonsCell
                            id={e.id}
                            isFirst={idx === 0}
                            isLast={idx === displayItems.length - 1}
                            reorderingId={reorderingId}
                            onReorder={handleReorder}
                          />
                          <td className="px-4 py-3 text-right">
                            <button onClick={() => openEdit(e)} className="text-git-blue hover:underline mr-3">edit</button>
                            <LoadingButton
                              variant="danger"
                              loading={deletingId === e.id}
                              loadingText="..."
                              onClick={() => handleDelete(e.id)}
                              className="border-0 bg-transparent px-0 hover:bg-transparent hover:underline"
                            >
                              delete
                            </LoadingButton>
                          </td>
                        </SortableTr>
                      ))}
                    </AnimatePresence>
                </tbody>
              </LayoutGroup>
            </SortableContext>
          </table>
        </div>
      </div>
      </DndContext>

      <AdminModal open={modalOpen} onClose={() => setModalOpen(false)} title={editing ? `edit — ${editing.hash}` : "new entry"}>
        <form onSubmit={handleSubmit} className="space-y-4">
          <div className="grid grid-cols-2 gap-3">
            <FormInput label="hash (7 chars)" value={form.hash} onChange={(e) => field("hash", e.target.value)} error={errors.hash} required placeholder="a1b2c3d" />
            <FormSelect label="type" value={form.type} onChange={(e) => field("type", e.target.value)} options={TYPE_OPTIONS} />
          </div>
          <FormInput label="title" value={form.title} onChange={(e) => field("title", e.target.value)} error={errors.title} required />
          <FormInput label="organization" value={form.org} onChange={(e) => field("org", e.target.value)} error={errors.org} required />
          <div className="grid grid-cols-2 gap-3">
            <FormInput label="start date" value={form.date} onChange={(e) => field("date", e.target.value)} error={errors.date} required placeholder="Jan 2024" />
            <FormInput label="end date (optional)" value={form.dateEnd ?? ""} onChange={(e) => field("dateEnd", e.target.value)} placeholder="Dec 2024 or leave blank" />
          </div>
          <FormTextarea
            label="description (one bullet per line)"
            value={descText}
            onChange={(e) => setDescText(e.target.value)}
            error={errors.description}
            rows={4}
            placeholder="Built X using Y&#10;Achieved Z"
          />
          <div className="grid grid-cols-2 gap-3">
            <FormInput label="branch name" value={form.branch} onChange={(e) => field("branch", e.target.value)} error={errors.branch} required placeholder="work/company-name" />
            <FormInput label="branch color (hex)" value={form.branchColor} onChange={(e) => field("branchColor", e.target.value)} error={errors.branchColor} placeholder="#00ff88" />
          </div>
          <div className="grid grid-cols-2 gap-3">
            <FormSelect label="color key" value={form.colorKey ?? "green"} onChange={(e) => field("colorKey", e.target.value)} options={COLOR_OPTIONS} />
            <FormInput label="url (optional)" value={form.url ?? ""} onChange={(e) => field("url", e.target.value)} error={errors.url} placeholder="https://..." />
          </div>
          <TagInput label="tags" values={form.tags ?? []} onChange={(v) => field("tags", v)} />
          <div className="flex gap-2 pt-2">
            <LoadingButton type="submit" loading={loading} loadingText="Saving..." className="flex-1 py-2">
              {editing ? "$ git commit --amend" : "$ git commit -m 'new entry'"}
            </LoadingButton>
            <LoadingButton type="button" variant="ghost" onClick={() => setModalOpen(false)} className="px-4 py-2">
              cancel
            </LoadingButton>
          </div>
        </form>
      </AdminModal>
    </motion.div>
  );
}
