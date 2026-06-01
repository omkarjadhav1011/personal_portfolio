
import { useState, useMemo } from "react";
import { Folder } from "lucide-react";
import { AdminModal } from "@/components/admin/AdminModal";
import { FormInput, FormTextarea, FormSelect, FormCheckbox } from "@/components/admin/FormField";
import { TagInput } from "@/components/admin/TagInput";
import { LoadingButton } from "@/components/ui/LoadingButton";
import { EmptyState } from "@/components/ui/EmptyState";
import { TableSearch } from "@/components/admin/TableSearch";
import { SortableTr, DragHandleCell, ReorderButtonsCell } from "@/components/admin/SortableTr";
import { useToast } from "@/components/admin/ToastProvider";
import { useFormValidation } from "@/hooks/useFormValidation";
import { useReorder } from "@/hooks/useReorder";
import { projectSchema } from "@/lib/admin-validations";
import { useQueryClient } from "@tanstack/react-query";
import { apiFetch, ApiError } from "@/lib/api";
import { projectKeys } from "@/api/projects";
import { motion, AnimatePresence, LayoutGroup } from "framer-motion";
import { DndContext, closestCenter } from "@dnd-kit/core";
import { SortableContext, verticalListSortingStrategy } from "@dnd-kit/sortable";

interface Project {
  id: string;
  slug: string;
  repoName: string;
  description: string;
  language: string;
  languageColor: string;
  stars: number;
  forks: number;
  commits: number;
  lastCommit: string;
  lastCommitMsg: string;
  tags: string[];
  liveUrl?: string | null;
  repoUrl?: string | null;
  status: string;
  pinned: boolean;
  longDescription?: string | null;
  order: number;
}

const EMPTY: Omit<Project, "id" | "order"> = {
  slug: "", repoName: "", description: "", language: "", languageColor: "#3178c6",
  stars: 0, forks: 0, commits: 0, lastCommit: "just now", lastCommitMsg: "",
  tags: [], liveUrl: "", repoUrl: "", status: "active", pinned: false, longDescription: "",
};

const STATUS_OPTIONS = [
  { value: "active", label: "active" },
  { value: "wip", label: "wip" },
  { value: "archived", label: "archived" },
];

const STATUS_COLORS: Record<string, string> = {
  active: "text-git-green",
  wip: "text-git-yellow",
  archived: "text-text-muted",
};

export function ProjectsClient({ initialProjects }: { initialProjects: Project[] }) {
  const [modalOpen, setModalOpen] = useState(false);
  const [editing, setEditing] = useState<Project | null>(null);
  const [form, setForm] = useState<Omit<Project, "id" | "order">>(EMPTY);
  const [loading, setLoading] = useState(false);
  const [deletingId, setDeletingId] = useState<string | null>(null);
  const [search, setSearch] = useState("");
  const { toast } = useToast();
  const queryClient = useQueryClient();
  const { errors, validate, clearErrors } = useFormValidation(projectSchema);

  const { items: projects, setItems: setProjects, sensors, handleDragEnd, handleReorder, reorderingId } =
    useReorder(initialProjects, { type: "projects", toast });

  const searchActive = search.trim() !== "";

  const displayItems = useMemo(() => {
    const base = [...projects].sort((a, b) => a.order - b.order);
    if (!searchActive) return base;
    const q = search.toLowerCase();
    return base.filter(
      (p) =>
        p.repoName.toLowerCase().includes(q) ||
        p.description.toLowerCase().includes(q) ||
        p.language.toLowerCase().includes(q)
    );
  }, [projects, search, searchActive]);

  function openCreate() {
    setEditing(null);
    setForm(EMPTY);
    clearErrors();
    setModalOpen(true);
  }

  function openEdit(p: Project) {
    setEditing(p);
    setForm({ ...p, liveUrl: p.liveUrl ?? "", repoUrl: p.repoUrl ?? "", longDescription: p.longDescription ?? "" });
    clearErrors();
    setModalOpen(true);
  }

  async function handleDelete(id: string) {
    if (!confirm("Delete this project? This cannot be undone.")) return;
    setDeletingId(id);
    try {
      await apiFetch(`/api/projects/${id}`, { method: "DELETE" });
      setProjects((prev) => prev.filter((p) => p.id !== id));
      toast("Project deleted", "success");
      await queryClient.invalidateQueries({ queryKey: projectKeys.all });
    } catch (err) {
      toast(err instanceof ApiError ? err.message : "Failed to delete", "error");
    } finally {
      setDeletingId(null);
    }
  }

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    const data = { ...form, stars: Number(form.stars), forks: Number(form.forks), commits: Number(form.commits) };
    if (!validate(data)) return;
    setLoading(true);
    try {
      const url = editing ? `/api/projects/${editing.id}` : "/api/projects";
      const method = editing ? "PATCH" : "POST";
      const saved = await apiFetch<Project>(url, {
        method,
        body: JSON.stringify(data),
      });
      if (editing) {
        setProjects((prev) => prev.map((p) => (p.id === saved.id ? saved : p)));
        toast("Project updated", "success");
      } else {
        setProjects((prev) => [...prev, saved]);
        toast("Project created", "success");
      }
      setModalOpen(false);
      await queryClient.invalidateQueries({ queryKey: projectKeys.all });
    } catch (err) {
      toast(err instanceof ApiError ? err.message : "Failed to save", "error");
    } finally {
      setLoading(false);
    }
  }

  function field(key: keyof typeof form, value: string | number | boolean | string[]) {
    setForm((prev) => ({ ...prev, [key]: value }));
  }

  return (
    <motion.div initial={{ opacity: 0 }} animate={{ opacity: 1 }} className="space-y-6 font-mono">
      {/* Header */}
      <div className="flex items-center justify-between">
        <div>
          <div className="text-text-faint text-xs mb-1">$ git ls-remote --heads</div>
          <h1 className="text-xl font-bold text-text-primary">Projects</h1>
          <p className="text-text-muted text-xs mt-0.5"># {projects.length} repositories</p>
        </div>
        <button
          onClick={openCreate}
          className="flex items-center gap-2 px-4 py-2 rounded-lg border border-git-green/40 bg-git-green/10 text-git-green text-xs hover:bg-git-green/20 hover:border-git-green/70 transition-all"
        >
          <span>+</span> new project
        </button>
      </div>

      {/* Search */}
      <TableSearch value={search} onChange={setSearch} placeholder="Search projects..." />

      {/* Table */}
      <DndContext sensors={sensors} collisionDetection={closestCenter} onDragEnd={handleDragEnd}>
      <div className="rounded-xl border border-terminal-border bg-terminal-surface overflow-hidden">
        <div className="overflow-x-auto">
          <table className="w-full text-xs">
            <thead>
              <tr className="border-b border-terminal-border bg-terminal-bg">
                <th className="px-2 py-3 w-6" />
                <th className="text-left px-4 py-3 text-text-muted font-normal">repo</th>
                <th className="text-left px-4 py-3 text-text-muted font-normal">lang</th>
                <th className="text-left px-4 py-3 text-text-muted font-normal">status</th>
                <th className="text-left px-4 py-3 text-text-muted font-normal">pinned</th>
                <th className="px-3 py-3 text-center text-text-muted font-normal w-16">order</th>
                <th className="text-right px-4 py-3 text-text-muted font-normal">actions</th>
              </tr>
            </thead>
            <SortableContext items={displayItems.map((p) => p.id)} strategy={verticalListSortingStrategy}>
              <LayoutGroup>
                <tbody>
                    {displayItems.length === 0 && (
                      <tr>
                        <td colSpan={7}>
                          <EmptyState
                            icon={<Folder size={32} />}
                            title={search ? "No projects match your search" : "No projects yet"}
                            description={search ? "Try a different search term" : "Create your first repository to get started"}
                            action={!search ? { label: "+ new project", onClick: openCreate } : undefined}
                          />
                        </td>
                      </tr>
                    )}
                    <AnimatePresence>
                      {displayItems.map((p, idx) => (
                        <SortableTr key={p.id} id={p.id} disabled={searchActive || reorderingId !== null}>
                          <DragHandleCell />
                          <td className="px-4 py-3">
                            <div className="text-text-primary">{p.repoName}</div>
                            <div className="text-text-faint text-[10px] truncate max-w-[200px]">{p.description}</div>
                          </td>
                          <td className="px-4 py-3">
                            <span className="flex items-center gap-1.5">
                              <span className="w-2 h-2 rounded-full shrink-0" style={{ backgroundColor: p.languageColor }} />
                              {p.language}
                            </span>
                          </td>
                          <td className={`px-4 py-3 ${STATUS_COLORS[p.status] ?? "text-text-muted"}`}>{p.status}</td>
                          <td className="px-4 py-3 text-text-muted">{p.pinned ? "📌" : "—"}</td>
                          <ReorderButtonsCell
                            id={p.id}
                            isFirst={idx === 0}
                            isLast={idx === displayItems.length - 1}
                            reorderingId={reorderingId}
                            onReorder={handleReorder}
                          />
                          <td className="px-4 py-3 text-right">
                            <button onClick={() => openEdit(p)} className="text-git-blue hover:underline mr-3">edit</button>
                            <LoadingButton
                              variant="danger"
                              loading={deletingId === p.id}
                              loadingText="..."
                              onClick={() => handleDelete(p.id)}
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

      {/* Modal */}
      <AdminModal
        open={modalOpen}
        onClose={() => setModalOpen(false)}
        title={editing ? `edit — ${editing.repoName}` : "new project"}
      >
        <form onSubmit={handleSubmit} className="space-y-4">
          <div className="grid grid-cols-2 gap-3">
            <FormInput label="repo name" value={form.repoName} onChange={(e) => field("repoName", e.target.value)} error={errors.repoName} required />
            <FormInput label="slug (url)" value={form.slug} onChange={(e) => field("slug", e.target.value)} error={errors.slug} required placeholder="my-project" />
          </div>
          <FormInput label="description" value={form.description} onChange={(e) => field("description", e.target.value)} error={errors.description} required />
          <div className="grid grid-cols-2 gap-3">
            <FormInput label="language" value={form.language} onChange={(e) => field("language", e.target.value)} error={errors.language} required />
            <FormInput label="language color (hex)" value={form.languageColor} onChange={(e) => field("languageColor", e.target.value)} error={errors.languageColor} placeholder="#3178c6" />
          </div>
          <div className="grid grid-cols-3 gap-3">
            <FormInput label="stars" type="number" value={form.stars} onChange={(e) => field("stars", e.target.value)} error={errors.stars} min={0} />
            <FormInput label="forks" type="number" value={form.forks} onChange={(e) => field("forks", e.target.value)} error={errors.forks} min={0} />
            <FormInput label="commits" type="number" value={form.commits} onChange={(e) => field("commits", e.target.value)} error={errors.commits} min={0} />
          </div>
          <div className="grid grid-cols-2 gap-3">
            <FormInput label="last commit" value={form.lastCommit} onChange={(e) => field("lastCommit", e.target.value)} error={errors.lastCommit} placeholder="just now" />
            <FormInput label="last commit msg" value={form.lastCommitMsg} onChange={(e) => field("lastCommitMsg", e.target.value)} error={errors.lastCommitMsg} />
          </div>
          <TagInput label="tags" values={form.tags} onChange={(v) => field("tags", v)} />
          <div className="grid grid-cols-2 gap-3">
            <FormInput label="live url" value={form.liveUrl ?? ""} onChange={(e) => field("liveUrl", e.target.value)} error={errors.liveUrl} placeholder="https://..." />
            <FormInput label="repo url" value={form.repoUrl ?? ""} onChange={(e) => field("repoUrl", e.target.value)} error={errors.repoUrl} placeholder="https://github.com/..." />
          </div>
          <div className="grid grid-cols-2 gap-3 items-end">
            <FormSelect label="status" value={form.status} onChange={(e) => field("status", e.target.value)} options={STATUS_OPTIONS} />
            <FormCheckbox label="pinned" checked={form.pinned} onChange={(v) => field("pinned", v)} />
          </div>
          <FormTextarea label="long description" value={form.longDescription ?? ""} onChange={(e) => field("longDescription", e.target.value)} rows={4} />

          <div className="flex gap-2 pt-2">
            <LoadingButton type="submit" loading={loading} loadingText="Saving..." className="flex-1 py-2">
              {editing ? "$ git commit --amend" : "$ git commit -m 'new project'"}
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
