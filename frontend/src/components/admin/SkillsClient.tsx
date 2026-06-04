
import { useState, useMemo } from "react";
import { Code, GitCompareArrows, ChevronUp, ChevronDown, GripVertical } from "lucide-react";
import { AdminModal } from "@/components/admin/AdminModal";
import { FormInput, FormSelect } from "@/components/admin/FormField";
import { LoadingButton } from "@/components/ui/LoadingButton";
import { EmptyState } from "@/components/ui/EmptyState";
import { TableSearch } from "@/components/admin/TableSearch";
import { useToast } from "@/components/admin/ToastProvider";
import { useFormValidation } from "@/hooks/useFormValidation";
import { skillSchema, skillDiffSchema } from "@/lib/admin-validations";
import { useQueryClient } from "@tanstack/react-query";
import { authFetch } from "@/lib/api";
import { skillKeys } from "@/api/skills";
import { motion, AnimatePresence, LayoutGroup } from "framer-motion";
import {
  DndContext,
  closestCenter,
  PointerSensor,
  useSensor,
  useSensors,
  type DragEndEvent,
} from "@dnd-kit/core";
import {
  SortableContext,
  verticalListSortingStrategy,
  useSortable,
  arrayMove,
} from "@dnd-kit/sortable";
import { CSS } from "@dnd-kit/utilities";

interface Skill {
  id: string;
  name: string;
  level: number;
  tag?: string | null;
  icon?: string | null;
  branchId: string;
}

interface SkillBranch {
  id: string;
  branchName: string;
  color: string;
  offset: number;
  skills: Skill[];
}

interface SkillDiff {
  id: string;
  name: string;
  type: string;
  note?: string | null;
  order: number;
}

const DIFF_TYPE_OPTIONS = [
  { value: "added", label: "added" },
  { value: "modified", label: "modified" },
  { value: "deprecated", label: "deprecated" },
];

const LEVEL_OPTIONS = [1, 2, 3, 4, 5].map((n) => ({
  value: String(n),
  label: `${n} — ${["Learning", "Familiar", "Proficient", "Advanced", "Expert"][n - 1]}`,
}));

const DIFF_COLORS: Record<string, string> = {
  added: "text-git-green",
  modified: "text-git-yellow",
  deprecated: "text-git-red",
};

interface SortableRowProps {
  d: SkillDiff;
  idx: number;
  isFirst: boolean;
  isLast: boolean;
  reorderingId: string | null;
  searchActive: boolean;
  deletingDiffId: string | null;
  onReorder: (id: string, direction: "up" | "down") => void;
  onEdit: (d: SkillDiff) => void;
  onDelete: (id: string) => void;
}

function SortableRow({
  d,
  idx,
  isFirst,
  isLast,
  reorderingId,
  searchActive,
  deletingDiffId,
  onReorder,
  onEdit,
  onDelete,
}: SortableRowProps) {
  const { attributes, listeners, setNodeRef, transform, transition, isDragging } = useSortable({
    id: d.id,
    disabled: searchActive || reorderingId !== null,
  });

  const style: React.CSSProperties = {
    transform: CSS.Transform.toString(transform),
    transition,
    opacity: isDragging ? 0.5 : 1,
    zIndex: isDragging ? 10 : undefined,
    position: isDragging ? "relative" : undefined,
  };

  return (
    <motion.tr
      ref={setNodeRef}
      style={style}
      layout
      layoutId={d.id}
      initial={false}
      animate={{ opacity: isDragging ? 0.5 : 1 }}
      exit={{ opacity: 0 }}
      transition={{ layout: { type: "spring", stiffness: 500, damping: 35 } }}
      className="border-b border-terminal-border/50 hover:bg-terminal-bg/40 transition-colors"
    >
      <td className="px-2 py-2.5 text-center w-6">
        <button
          {...attributes}
          {...listeners}
          disabled={searchActive || reorderingId !== null}
          className={`p-0.5 rounded text-text-faint transition-colors ${
            searchActive || reorderingId !== null
              ? "opacity-30 cursor-not-allowed"
              : "cursor-grab hover:text-text-muted active:cursor-grabbing"
          }`}
          title={searchActive ? "Clear search to drag" : "Drag to reorder"}
        >
          <GripVertical size={14} />
        </button>
      </td>
      <td className="px-3 py-2.5 text-center text-text-faint font-mono text-[10px]">
        #{idx + 1}
      </td>
      <td className={`px-4 py-2.5 font-bold ${DIFF_COLORS[d.type]}`}>
        {d.type === "added" ? "+" : d.type === "deprecated" ? "-" : "~"} {d.type}
      </td>
      <td className={`px-4 py-2.5 ${DIFF_COLORS[d.type]}`}>{d.name}</td>
      <td className="px-4 py-2.5 text-text-faint">{d.note ?? "—"}</td>
      <td className="px-4 py-2.5 text-center">
        <div className="inline-flex gap-1">
          <button
            onClick={() => onReorder(d.id, "up")}
            disabled={isFirst || reorderingId !== null}
            className="p-0.5 rounded hover:bg-terminal-bg disabled:opacity-30 disabled:cursor-not-allowed text-text-muted hover:text-text-primary transition-colors"
            title="Move up"
          >
            <ChevronUp size={14} />
          </button>
          <button
            onClick={() => onReorder(d.id, "down")}
            disabled={isLast || reorderingId !== null}
            className="p-0.5 rounded hover:bg-terminal-bg disabled:opacity-30 disabled:cursor-not-allowed text-text-muted hover:text-text-primary transition-colors"
            title="Move down"
          >
            <ChevronDown size={14} />
          </button>
        </div>
      </td>
      <td className="px-4 py-2.5 text-right">
        <button onClick={() => onEdit(d)} className="text-git-blue hover:underline mr-3">edit</button>
        <LoadingButton
          variant="danger"
          loading={deletingDiffId === d.id}
          loadingText="..."
          onClick={() => onDelete(d.id)}
          className="border-0 bg-transparent px-0 hover:bg-transparent hover:underline"
        >
          delete
        </LoadingButton>
      </td>
    </motion.tr>
  );
}

export function SkillsClient({
  initialBranches,
  initialDiffs,
}: {
  initialBranches: SkillBranch[];
  initialDiffs: SkillDiff[];
}) {
  const [branches, setBranches] = useState(initialBranches);
  const [diffs, setDiffs] = useState(initialDiffs);
  const { toast } = useToast();
  const queryClient = useQueryClient();

  // Skill modal state
  const [skillModal, setSkillModal] = useState(false);
  const [editingSkill, setEditingSkill] = useState<Skill | null>(null);
  const [skillBranchId, setSkillBranchId] = useState("");
  const [skillForm, setSkillForm] = useState({ name: "", level: "3", tag: "", icon: "" });

  // Diff modal state
  const [diffModal, setDiffModal] = useState(false);
  const [editingDiff, setEditingDiff] = useState<SkillDiff | null>(null);
  const [diffForm, setDiffForm] = useState({ name: "", type: "added", note: "" });

  const [loading, setLoading] = useState(false);
  const [deletingSkillId, setDeletingSkillId] = useState<string | null>(null);
  const [deletingDiffId, setDeletingDiffId] = useState<string | null>(null);
  const [diffSearch, setDiffSearch] = useState("");

  const skillValidation = useFormValidation(skillSchema);
  const diffValidation = useFormValidation(skillDiffSchema);

  // Filter diffs
  const filteredDiffs = useMemo(() => {
    if (!diffSearch.trim()) return diffs;
    const q = diffSearch.toLowerCase();
    return diffs.filter((d) => d.name.toLowerCase().includes(q) || d.type.toLowerCase().includes(q));
  }, [diffs, diffSearch]);

  const orderedDiffs = useMemo(() => [...filteredDiffs].sort((a, b) => a.order - b.order), [filteredDiffs]);

  function openAddSkill(branchId: string) {
    setEditingSkill(null);
    setSkillBranchId(branchId);
    setSkillForm({ name: "", level: "3", tag: "", icon: "" });
    skillValidation.clearErrors();
    setSkillModal(true);
  }

  function openEditSkill(skill: Skill) {
    setEditingSkill(skill);
    setSkillBranchId(skill.branchId);
    setSkillForm({ name: skill.name, level: String(skill.level), tag: skill.tag ?? "", icon: skill.icon ?? "" });
    skillValidation.clearErrors();
    setSkillModal(true);
  }

  async function handleSkillDelete(branchId: string, skillId: string) {
    if (!confirm("Delete this skill?")) return;
    setDeletingSkillId(skillId);
    try {
      const res = await authFetch(`/api/skills/branches/${branchId}/skills/${skillId}`, { method: "DELETE" });
      if (res.ok) {
        setBranches((prev) =>
          prev.map((b) => b.id === branchId ? { ...b, skills: b.skills.filter((s) => s.id !== skillId) } : b)
        );
        toast("Skill deleted", "success");
        await Promise.all([queryClient.invalidateQueries({ queryKey: skillKeys.branches }), queryClient.invalidateQueries({ queryKey: skillKeys.diff })]);
      } else {
        toast("Failed to delete skill", "error");
      }
    } finally {
      setDeletingSkillId(null);
    }
  }

  async function handleSkillSubmit(e: React.FormEvent) {
    e.preventDefault();
    const payload = { name: skillForm.name, level: Number(skillForm.level), tag: skillForm.tag || undefined, icon: skillForm.icon || undefined };
    if (!skillValidation.validate(payload)) return;
    setLoading(true);
    try {
      let res: Response;
      if (editingSkill) {
        res = await authFetch(`/api/skills/branches/${skillBranchId}/skills/${editingSkill.id}`, {
          method: "PATCH", headers: { "Content-Type": "application/json" }, body: JSON.stringify(payload),
        });
      } else {
        res = await authFetch(`/api/skills/branches/${skillBranchId}/skills`, {
          method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify(payload),
        });
      }
      if (res.ok) {
        const saved: Skill = await res.json();
        setBranches((prev) =>
          prev.map((b) => {
            if (b.id !== skillBranchId) return b;
            if (editingSkill) {
              return { ...b, skills: b.skills.map((s) => (s.id === saved.id ? saved : s)) };
            }
            return { ...b, skills: [...b.skills, saved] };
          })
        );
        toast(editingSkill ? "Skill updated" : "Skill added", "success");
        setSkillModal(false);
        await Promise.all([queryClient.invalidateQueries({ queryKey: skillKeys.branches }), queryClient.invalidateQueries({ queryKey: skillKeys.diff })]);
      } else {
        const err = await res.json();
        toast(err.error?.message ?? "Failed to save", "error");
      }
    } finally {
      setLoading(false);
    }
  }

  // Diff handlers
  function openAddDiff() {
    setEditingDiff(null);
    setDiffForm({ name: "", type: "added", note: "" });
    diffValidation.clearErrors();
    setDiffModal(true);
  }

  function openEditDiff(d: SkillDiff) {
    setEditingDiff(d);
    setDiffForm({ name: d.name, type: d.type, note: d.note ?? "" });
    diffValidation.clearErrors();
    setDiffModal(true);
  }

  async function handleDiffDelete(id: string) {
    if (!confirm("Delete this diff entry?")) return;
    setDeletingDiffId(id);
    try {
      const res = await authFetch(`/api/skills/diff/${id}`, { method: "DELETE" });
      if (res.ok) {
        setDiffs((prev) => prev.filter((d) => d.id !== id));
        toast("Diff deleted", "success");
        await Promise.all([queryClient.invalidateQueries({ queryKey: skillKeys.branches }), queryClient.invalidateQueries({ queryKey: skillKeys.diff })]);
      } else {
        toast("Failed to delete", "error");
      }
    } finally {
      setDeletingDiffId(null);
    }
  }

  async function handleDiffSubmit(e: React.FormEvent) {
    e.preventDefault();
    const payload = { name: diffForm.name, type: diffForm.type, note: diffForm.note || undefined };
    if (!diffValidation.validate(payload)) return;
    setLoading(true);
    try {
      const url = editingDiff ? `/api/skills/diff/${editingDiff.id}` : "/api/skills/diff";
      const method = editingDiff ? "PATCH" : "POST";
      const res = await authFetch(url, { method, headers: { "Content-Type": "application/json" }, body: JSON.stringify(payload) });
      if (res.ok) {
        const saved: SkillDiff = await res.json();
        if (editingDiff) {
          setDiffs((prev) => prev.map((d) => (d.id === saved.id ? saved : d)));
          toast("Diff updated", "success");
        } else {
          setDiffs((prev) => [...prev, saved]);
          toast("Diff added", "success");
        }
        setDiffModal(false);
        await Promise.all([queryClient.invalidateQueries({ queryKey: skillKeys.branches }), queryClient.invalidateQueries({ queryKey: skillKeys.diff })]);
      } else {
        const err = await res.json();
        toast(err.error?.message ?? "Failed to save", "error");
      }
    } finally {
      setLoading(false);
    }
  }

  const [reorderingId, setReorderingId] = useState<string | null>(null);

  const sensors = useSensors(
    useSensor(PointerSensor, { activationConstraint: { distance: 5 } })
  );

  async function handleDragEnd(event: DragEndEvent) {
    const { active, over } = event;
    if (!over || active.id === over.id) return;

    const sorted = [...diffs].sort((a, b) => a.order - b.order);
    const oldIdx = sorted.findIndex((d) => d.id === active.id);
    const newIdx = sorted.findIndex((d) => d.id === over.id);
    const reordered = arrayMove(sorted, oldIdx, newIdx);
    const updated = reordered.map((d, i) => ({ ...d, order: i + 1 }));

    setDiffs(updated);
    setReorderingId("drag");

    try {
      const res = await authFetch("/api/admin/reorder", {
        method: "PATCH",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          type: "stack",
          items: updated.map(({ id, order }) => ({ id, position: order })),
        }),
      });
      if (!res.ok) {
        setDiffs(sorted);
        toast("Failed to reorder", "error");
      } else {
        await Promise.all([queryClient.invalidateQueries({ queryKey: skillKeys.branches }), queryClient.invalidateQueries({ queryKey: skillKeys.diff })]);
      }
    } catch {
      setDiffs(sorted);
      toast("Failed to reorder", "error");
    } finally {
      setReorderingId(null);
    }
  }

  async function handleReorder(id: string, direction: "up" | "down") {
    const sorted = [...diffs].sort((a, b) => a.order - b.order);
    const idx = sorted.findIndex((d) => d.id === id);
    const swapIdx = direction === "up" ? idx - 1 : idx + 1;
    if (swapIdx < 0 || swapIdx >= sorted.length) return;

    // Optimistic swap
    const updated = [...sorted];
    const tempOrder = updated[idx].order;
    updated[idx] = { ...updated[idx], order: updated[swapIdx].order };
    updated[swapIdx] = { ...updated[swapIdx], order: tempOrder };
    setDiffs(updated);
    setReorderingId(id);

    try {
      const res = await authFetch("/api/admin/reorder", {
        method: "PATCH",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ type: "stack", id, direction }),
      });
      if (!res.ok) {
        setDiffs(sorted); // revert
        toast("Failed to reorder", "error");
      } else {
        await Promise.all([queryClient.invalidateQueries({ queryKey: skillKeys.branches }), queryClient.invalidateQueries({ queryKey: skillKeys.diff })]);
      }
    } catch {
      setDiffs(sorted); // revert
      toast("Failed to reorder", "error");
    } finally {
      setReorderingId(null);
    }
  }

  return (
    <motion.div initial={{ opacity: 0 }} animate={{ opacity: 1 }} className="space-y-8 font-mono">
      <div>
        <div className="text-text-faint text-xs mb-1">$ git branch -a --verbose</div>
        <h1 className="text-xl font-bold text-text-primary">Skills</h1>
      </div>

      {/* Branches */}
      <div className="space-y-4">
        {branches.map((branch) => (
          <div key={branch.id} className="rounded-xl border border-terminal-border bg-terminal-surface overflow-hidden">
            <div className="flex items-center justify-between px-4 py-3 bg-terminal-bg border-b border-terminal-border">
              <span className="font-mono text-sm font-semibold" style={{ color: branch.color }}>
                ⑂ {branch.branchName}
              </span>
              <button
                onClick={() => openAddSkill(branch.id)}
                className="text-xs text-git-green hover:underline"
              >
                + add skill
              </button>
            </div>
            <div className="p-3 flex flex-wrap gap-2">
              {branch.skills.length === 0 && (
                <EmptyState
                  icon={<Code size={24} />}
                  title="No skills in this branch"
                  description="Add your first skill above"
                  className="py-6 w-full"
                />
              )}
              {branch.skills.map((skill) => (
                <div
                  key={skill.id}
                  className="flex items-center gap-1 px-2.5 py-1 rounded-full border text-xs transition-all duration-200 hover:shadow-sm"
                  style={{ borderColor: `${branch.color}40`, backgroundColor: `${branch.color}10`, color: branch.color }}
                >
                  {skill.icon && <span>{skill.icon}</span>}
                  <span>{skill.name}</span>
                  <span className="text-[10px] opacity-60">({skill.level}/5)</span>
                  <button onClick={() => openEditSkill(skill)} className="ml-1 opacity-60 hover:opacity-100 transition-opacity">✎</button>
                  <LoadingButton
                    variant="ghost"
                    loading={deletingSkillId === skill.id}
                    loadingText=""
                    onClick={() => handleSkillDelete(branch.id, skill.id)}
                    className="border-0 bg-transparent p-0 hover:bg-transparent opacity-60 hover:opacity-100"
                    style={{ color: branch.color }}
                  >
                    <span className="hover:text-git-red">×</span>
                  </LoadingButton>
                </div>
              ))}
            </div>
          </div>
        ))}
      </div>

      {/* Skill Diff */}
      <div>
        <div className="flex items-center justify-between mb-3">
          <div>
            <div className="text-text-faint text-xs mb-1">$ git diff skills/archive skills/current</div>
            <h2 className="text-base font-bold text-text-primary">Stack Evolution</h2>
          </div>
          <button
            onClick={openAddDiff}
            className="flex items-center gap-2 px-3 py-1.5 rounded-lg border border-git-green/40 bg-git-green/10 text-git-green text-xs hover:bg-git-green/20 transition-all"
          >
            + add diff
          </button>
        </div>

        <TableSearch value={diffSearch} onChange={setDiffSearch} placeholder="Search diffs..." className="mb-3" />

        <DndContext sensors={sensors} collisionDetection={closestCenter} onDragEnd={handleDragEnd}>
        <div className="rounded-xl border border-terminal-border bg-terminal-surface overflow-hidden">
          <table className="w-full text-xs">
            <thead>
              <tr className="border-b border-terminal-border bg-terminal-bg">
                <th className="w-6 px-2 py-3" />
                <th className="text-center px-3 py-3 text-text-muted font-normal w-10">#</th>
                <th className="text-left px-4 py-3 text-text-muted font-normal">type</th>
                <th className="text-left px-4 py-3 text-text-muted font-normal">name</th>
                <th className="text-left px-4 py-3 text-text-muted font-normal">note</th>
                <th className="text-center px-4 py-3 text-text-muted font-normal">order</th>
                <th className="text-right px-4 py-3 text-text-muted font-normal">actions</th>
              </tr>
            </thead>
            <SortableContext items={orderedDiffs.map((d) => d.id)} strategy={verticalListSortingStrategy}>
            <LayoutGroup>
            <tbody>
              {orderedDiffs.length === 0 && (
                <tr>
                  <td colSpan={7}>
                    <EmptyState
                      icon={<GitCompareArrows size={32} />}
                      title={diffSearch ? "No diffs match your search" : "No diff entries yet"}
                      description={diffSearch ? "Try a different search term" : "Track your stack evolution"}
                      action={!diffSearch ? { label: "+ add diff", onClick: openAddDiff } : undefined}
                    />
                  </td>
                </tr>
              )}
              <AnimatePresence>
              {orderedDiffs.map((d, idx) => {
                const isFirst = idx === 0;
                const isLast = idx === orderedDiffs.length - 1;
                return (
                <SortableRow
                  key={d.id}
                  d={d}
                  idx={idx}
                  isFirst={isFirst}
                  isLast={isLast}
                  reorderingId={reorderingId}
                  searchActive={!!diffSearch}
                  deletingDiffId={deletingDiffId}
                  onReorder={handleReorder}
                  onEdit={openEditDiff}
                  onDelete={handleDiffDelete}
                />
                );
              })}
              </AnimatePresence>
            </tbody>
            </LayoutGroup>
            </SortableContext>
          </table>
        </div>
        </DndContext>
      </div>

      {/* Skill Modal */}
      <AdminModal open={skillModal} onClose={() => setSkillModal(false)} title={editingSkill ? `edit skill — ${editingSkill.name}` : "add skill"}>
        <form onSubmit={handleSkillSubmit} className="space-y-4">
          <FormInput label="skill name" value={skillForm.name} onChange={(e) => setSkillForm((p) => ({ ...p, name: e.target.value }))} error={skillValidation.errors.name} required />
          <FormSelect label="proficiency level" value={skillForm.level} onChange={(e) => setSkillForm((p) => ({ ...p, level: e.target.value }))} options={LEVEL_OPTIONS} />
          <div className="grid grid-cols-2 gap-3">
            <FormInput label="tag (optional)" value={skillForm.tag} onChange={(e) => setSkillForm((p) => ({ ...p, tag: e.target.value }))} placeholder="v14" />
            <FormInput label="icon (optional)" value={skillForm.icon} onChange={(e) => setSkillForm((p) => ({ ...p, icon: e.target.value }))} placeholder="⚛ or emoji" />
          </div>
          <div className="flex gap-2 pt-2">
            <LoadingButton type="submit" loading={loading} loadingText="Saving..." className="flex-1 py-2">
              $ git add skill
            </LoadingButton>
            <LoadingButton type="button" variant="ghost" onClick={() => setSkillModal(false)} className="px-4 py-2">
              cancel
            </LoadingButton>
          </div>
        </form>
      </AdminModal>

      {/* Diff Modal */}
      <AdminModal open={diffModal} onClose={() => setDiffModal(false)} title={editingDiff ? "edit diff entry" : "add diff entry"}>
        <form onSubmit={handleDiffSubmit} className="space-y-4">
          <FormInput label="name" value={diffForm.name} onChange={(e) => setDiffForm((p) => ({ ...p, name: e.target.value }))} error={diffValidation.errors.name} required />
          <FormSelect label="type" value={diffForm.type} onChange={(e) => setDiffForm((p) => ({ ...p, type: e.target.value }))} options={DIFF_TYPE_OPTIONS} />
          <FormInput label="note (optional)" value={diffForm.note} onChange={(e) => setDiffForm((p) => ({ ...p, note: e.target.value }))} placeholder="learning containerization" />
          <div className="flex gap-2 pt-2">
            <LoadingButton type="submit" loading={loading} loadingText="Saving..." className="flex-1 py-2">
              $ git diff --add
            </LoadingButton>
            <LoadingButton type="button" variant="ghost" onClick={() => setDiffModal(false)} className="px-4 py-2">
              cancel
            </LoadingButton>
          </div>
        </form>
      </AdminModal>
    </motion.div>
  );
}
