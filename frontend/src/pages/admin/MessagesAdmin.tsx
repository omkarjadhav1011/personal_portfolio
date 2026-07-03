import { useState } from "react";
import { Inbox, Target } from "lucide-react";
import { motion } from "framer-motion";
import { useQueryClient } from "@tanstack/react-query";
import { useDocumentTitle } from "@/hooks/useDocumentTitle";
import { AdminModal } from "@/components/admin/AdminModal";
import { LoadingButton } from "@/components/ui/LoadingButton";
import { EmptyState } from "@/components/ui/EmptyState";
import { useToast } from "@/components/admin/ToastProvider";
import { authFetch } from "@/lib/api";
import {
  useMessages,
  messageKeys,
  type ContactMessage,
  type MessageStatus,
} from "@/api/messages";
import { useLeads, leadKeys, type RecruiterLead } from "@/api/leads";

const STATUS_TABS: Array<{ value: MessageStatus | undefined; label: string }> = [
  { value: undefined, label: "all" },
  { value: "NEW", label: "new" },
  { value: "READ", label: "read" },
  { value: "REPLIED", label: "replied" },
  { value: "ARCHIVED", label: "archived" },
];

/** Leads triage flow is NEW → READ → REPLIED (no archive step). */
const LEAD_STATUS_TABS = STATUS_TABS.filter((t) => t.value !== "ARCHIVED");

const STATUS_STYLES: Record<MessageStatus, string> = {
  NEW: "border-git-green/40 bg-git-green/10 text-git-green",
  READ: "border-terminal-border bg-terminal-bg text-text-muted",
  REPLIED: "border-git-blue/40 bg-git-blue/10 text-git-blue",
  ARCHIVED: "border-terminal-border bg-terminal-bg text-text-faint",
};

const SOURCE_STYLES: Record<ContactMessage["source"], string> = {
  WEB: "text-git-blue",
  RECRUITER: "text-git-purple",
  CHATBOT: "text-git-orange",
  MCP: "text-git-yellow",
};

function StatusChip({ status }: { status: MessageStatus }) {
  return (
    <span className={`inline-block rounded border px-1.5 py-0.5 text-[10px] ${STATUS_STYLES[status]}`}>
      {status}
    </span>
  );
}

/** Self-reported score — display only; tone bands mirror the public FitScoreBadge. */
function FitScoreChip({ score }: { score: number | null }) {
  if (score == null) {
    return <span className="text-text-faint text-[10px]">—</span>;
  }
  const cls =
    score >= 75
      ? "border-git-green/40 bg-git-green/10 text-git-green"
      : score >= 50
        ? "border-git-blue/40 bg-git-blue/10 text-git-blue"
        : score >= 25
          ? "border-git-yellow/40 bg-git-yellow/10 text-git-yellow"
          : "border-git-red/40 bg-git-red/10 text-git-red";
  return (
    <span className={`inline-block rounded border px-1.5 py-0.5 text-[10px] tabular-nums ${cls}`}>
      {score}%
    </span>
  );
}

function StatusFilterTabs({
  tabs,
  value,
  onChange,
}: {
  tabs: Array<{ value: MessageStatus | undefined; label: string }>;
  value: MessageStatus | undefined;
  onChange: (v: MessageStatus | undefined) => void;
}) {
  return (
    <div className="flex flex-wrap gap-2">
      {tabs.map((tab) => {
        const active = value === tab.value;
        return (
          <button
            key={tab.label}
            onClick={() => onChange(tab.value)}
            className={`rounded-lg border px-3 py-1.5 text-xs transition-all ${
              active
                ? "border-git-green/40 bg-git-green/10 text-git-green"
                : "border-terminal-border bg-terminal-surface text-text-muted hover:text-text-primary"
            }`}
          >
            {tab.label}
          </button>
        );
      })}
    </div>
  );
}

type InboxView = "messages" | "leads";

/**
 * Admin inbox (lead capture A3 + C3): one surface, two tabs — contact messages and
 * recruiter leads. Each tab keeps its own status filter and modal; leads are triage-only
 * (no delete — the API is GET/PATCH by design).
 */
export default function MessagesAdmin() {
  useDocumentTitle("Inbox");
  const [view, setView] = useState<InboxView>("messages");

  return (
    <motion.div initial={{ opacity: 0 }} animate={{ opacity: 1 }} className="space-y-6 font-mono">
      <div>
        <div className="text-text-faint text-xs mb-1">$ git fetch origin/inbox</div>
        <h1 className="text-xl font-bold text-text-primary">Inbox</h1>
      </div>

      {/* View tabs: messages | leads */}
      <div className="flex gap-2 border-b border-terminal-border">
        {(
          [
            { value: "messages", label: "messages", icon: <Inbox size={12} /> },
            { value: "leads", label: "leads", icon: <Target size={12} /> },
          ] as Array<{ value: InboxView; label: string; icon: React.ReactNode }>
        ).map((tab) => {
          const active = view === tab.value;
          return (
            <button
              key={tab.value}
              onClick={() => setView(tab.value)}
              className={`inline-flex items-center gap-1.5 px-3 py-2 text-xs border-b-2 -mb-px transition-colors ${
                active
                  ? "border-git-green text-git-green"
                  : "border-transparent text-text-muted hover:text-text-primary"
              }`}
            >
              {tab.icon}
              {tab.label}
            </button>
          );
        })}
      </div>

      {view === "messages" ? <MessagesInbox /> : <LeadsInbox />}
    </motion.div>
  );
}

/** Contact-message tab (A3): status filter, mark-read on open, mailto reply, delete. */
function MessagesInbox() {
  const [statusFilter, setStatusFilter] = useState<MessageStatus | undefined>(undefined);
  const [selected, setSelected] = useState<ContactMessage | null>(null);
  const [deletingId, setDeletingId] = useState<string | null>(null);
  const [updating, setUpdating] = useState(false);
  const { toast } = useToast();
  const queryClient = useQueryClient();
  const { data, isPending, isError, error } = useMessages(statusFilter);

  const messages = data?.content ?? [];

  async function patchStatus(id: string, status: MessageStatus): Promise<ContactMessage | null> {
    const res = await authFetch(`/api/admin/messages/${id}`, {
      method: "PATCH",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ status }),
    });
    if (!res.ok) return null;
    const updated: ContactMessage = await res.json();
    await queryClient.invalidateQueries({ queryKey: messageKeys.all });
    return updated;
  }

  async function openMessage(message: ContactMessage) {
    setSelected(message);
    // Mark-read on open; a failure keeps the modal usable and the row NEW.
    if (message.status === "NEW") {
      const updated = await patchStatus(message.id, "READ");
      if (updated) {
        setSelected(updated);
      }
    }
  }

  async function markReplied(message: ContactMessage) {
    setUpdating(true);
    try {
      const updated = await patchStatus(message.id, "REPLIED");
      if (updated) {
        setSelected(updated);
        toast("Marked as replied", "success");
      } else {
        toast("Failed to update status", "error");
      }
    } finally {
      setUpdating(false);
    }
  }

  async function handleDelete(id: string) {
    if (!confirm("Delete this message?")) return;
    setDeletingId(id);
    try {
      const res = await authFetch(`/api/admin/messages/${id}`, { method: "DELETE" });
      if (res.ok) {
        toast("Message deleted", "success");
        setSelected((cur) => (cur?.id === id ? null : cur));
        await queryClient.invalidateQueries({ queryKey: messageKeys.all });
      } else {
        toast("Failed to delete", "error");
      }
    } finally {
      setDeletingId(null);
    }
  }

  if (isPending) {
    return <p className="font-mono text-sm text-text-muted">Loading messages…</p>;
  }
  if (isError) {
    return (
      <p className="font-mono text-sm text-git-red">
        Failed to load messages{error instanceof Error ? `: ${error.message}` : ""}.
      </p>
    );
  }

  return (
    <div className="space-y-6">
      <p className="text-text-muted text-xs">
        # {data.totalElements} message{data.totalElements === 1 ? "" : "s"}
        {statusFilter ? ` — ${statusFilter.toLowerCase()}` : ""}
      </p>

      <StatusFilterTabs tabs={STATUS_TABS} value={statusFilter} onChange={setStatusFilter} />

      <div className="rounded-xl border border-terminal-border bg-terminal-surface overflow-hidden">
        <div className="overflow-x-auto">
          <table className="w-full text-xs">
            <thead>
              <tr className="border-b border-terminal-border bg-terminal-bg">
                <th className="text-left px-4 py-3 text-text-muted font-normal">status</th>
                <th className="text-left px-4 py-3 text-text-muted font-normal">from</th>
                <th className="text-left px-4 py-3 text-text-muted font-normal">message</th>
                <th className="text-left px-4 py-3 text-text-muted font-normal">source</th>
                <th className="text-left px-4 py-3 text-text-muted font-normal">received</th>
                <th className="text-right px-4 py-3 text-text-muted font-normal">actions</th>
              </tr>
            </thead>
            <tbody>
              {messages.length === 0 && (
                <tr>
                  <td colSpan={6}>
                    <EmptyState
                      icon={<Inbox size={32} />}
                      title={statusFilter ? `No ${statusFilter.toLowerCase()} messages` : "Inbox zero"}
                      description="Messages from the contact form land here"
                    />
                  </td>
                </tr>
              )}
              {messages.map((m) => (
                <tr
                  key={m.id}
                  className={`border-b border-terminal-border/50 hover:bg-terminal-bg/50 transition-colors ${
                    m.status === "NEW" ? "bg-git-green/[0.03]" : ""
                  }`}
                >
                  <td className="px-4 py-3">
                    <StatusChip status={m.status} />
                  </td>
                  <td className="px-4 py-3">
                    <div className={m.status === "NEW" ? "text-text-primary font-semibold" : "text-text-secondary"}>
                      {m.name}
                    </div>
                    <div className="text-text-faint text-[10px]">{m.email}</div>
                  </td>
                  <td className="px-4 py-3 text-text-muted max-w-[28ch] truncate">{m.message}</td>
                  <td className={`px-4 py-3 text-[10px] ${SOURCE_STYLES[m.source]}`}>{m.source}</td>
                  <td className="px-4 py-3 text-text-faint whitespace-nowrap">
                    {new Date(m.createdAt).toLocaleString()}
                  </td>
                  <td className="px-4 py-3 text-right whitespace-nowrap">
                    <button onClick={() => openMessage(m)} className="text-git-blue hover:underline mr-3">
                      open
                    </button>
                    <LoadingButton
                      variant="danger"
                      loading={deletingId === m.id}
                      loadingText="..."
                      onClick={() => handleDelete(m.id)}
                      className="border-0 bg-transparent px-0 hover:bg-transparent hover:underline"
                    >
                      delete
                    </LoadingButton>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>

      <AdminModal
        open={selected !== null}
        onClose={() => setSelected(null)}
        title={selected ? `message — ${selected.name}` : "message"}
      >
        {selected && (
          <div className="space-y-4 font-mono text-xs">
            <div className="flex items-center gap-2 flex-wrap">
              <StatusChip status={selected.status} />
              <span className={`text-[10px] ${SOURCE_STYLES[selected.source]}`}>{selected.source}</span>
              <span className="text-text-faint text-[10px] ml-auto">
                {new Date(selected.createdAt).toLocaleString()}
              </span>
            </div>
            <div>
              <div className="text-text-faint text-[10px] mb-1">From:</div>
              <div className="text-text-primary">
                {selected.name} <span className="text-text-muted">&lt;{selected.email}&gt;</span>
              </div>
            </div>
            <div>
              <div className="text-text-faint text-[10px] mb-1">Message:</div>
              <pre className="whitespace-pre-wrap rounded-lg border border-terminal-border bg-terminal-bg p-3 text-text-secondary leading-relaxed">
                {selected.message}
              </pre>
            </div>
            <div className="flex gap-2 pt-1">
              <a
                href={`mailto:${selected.email}?subject=${encodeURIComponent("Re: your message on my portfolio")}`}
                className="inline-flex items-center justify-center gap-2 rounded-lg border border-git-green-dim bg-git-green-muted px-3 py-1.5 text-xs text-git-green hover:bg-git-green-dim/30 transition-all"
              >
                $ git reply --via-email
              </a>
              {selected.status !== "REPLIED" && (
                <LoadingButton
                  variant="ghost"
                  loading={updating}
                  loadingText="Saving..."
                  onClick={() => markReplied(selected)}
                >
                  mark replied
                </LoadingButton>
              )}
              <LoadingButton
                variant="danger"
                loading={deletingId === selected.id}
                loadingText="..."
                onClick={() => handleDelete(selected.id)}
                className="ml-auto"
              >
                delete
              </LoadingButton>
            </div>
          </div>
        )}
      </AdminModal>
    </div>
  );
}

/**
 * Recruiter-lead tab (C3): the C2 card's captures with their match context —
 * fit-score chip, matched skills, JD excerpt. Opening a NEW lead marks it READ;
 * reply is a mailto: handoff, same as messages.
 */
function LeadsInbox() {
  const [statusFilter, setStatusFilter] = useState<MessageStatus | undefined>(undefined);
  const [selected, setSelected] = useState<RecruiterLead | null>(null);
  const [updating, setUpdating] = useState(false);
  const { toast } = useToast();
  const queryClient = useQueryClient();
  const { data, isPending, isError, error } = useLeads(statusFilter);

  const leads = data?.content ?? [];

  async function patchStatus(id: string, status: MessageStatus): Promise<RecruiterLead | null> {
    const res = await authFetch(`/api/admin/leads/${id}`, {
      method: "PATCH",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ status }),
    });
    if (!res.ok) return null;
    const updated: RecruiterLead = await res.json();
    await queryClient.invalidateQueries({ queryKey: leadKeys.all });
    return updated;
  }

  async function openLead(lead: RecruiterLead) {
    setSelected(lead);
    // Mark-read on open; a failure keeps the modal usable and the row NEW.
    if (lead.status === "NEW") {
      const updated = await patchStatus(lead.id, "READ");
      if (updated) {
        setSelected(updated);
      }
    }
  }

  async function markReplied(lead: RecruiterLead) {
    setUpdating(true);
    try {
      const updated = await patchStatus(lead.id, "REPLIED");
      if (updated) {
        setSelected(updated);
        toast("Marked as replied", "success");
      } else {
        toast("Failed to update status", "error");
      }
    } finally {
      setUpdating(false);
    }
  }

  if (isPending) {
    return <p className="font-mono text-sm text-text-muted">Loading leads…</p>;
  }
  if (isError) {
    return (
      <p className="font-mono text-sm text-git-red">
        Failed to load leads{error instanceof Error ? `: ${error.message}` : ""}.
      </p>
    );
  }

  return (
    <div className="space-y-6">
      <p className="text-text-muted text-xs">
        # {data.totalElements} lead{data.totalElements === 1 ? "" : "s"}
        {statusFilter ? ` — ${statusFilter.toLowerCase()}` : ""}
      </p>

      <StatusFilterTabs tabs={LEAD_STATUS_TABS} value={statusFilter} onChange={setStatusFilter} />

      <div className="rounded-xl border border-terminal-border bg-terminal-surface overflow-hidden">
        <div className="overflow-x-auto">
          <table className="w-full text-xs">
            <thead>
              <tr className="border-b border-terminal-border bg-terminal-bg">
                <th className="text-left px-4 py-3 text-text-muted font-normal">status</th>
                <th className="text-left px-4 py-3 text-text-muted font-normal">email</th>
                <th className="text-left px-4 py-3 text-text-muted font-normal">company</th>
                <th className="text-left px-4 py-3 text-text-muted font-normal">fit</th>
                <th className="text-left px-4 py-3 text-text-muted font-normal">matched skills</th>
                <th className="text-left px-4 py-3 text-text-muted font-normal">received</th>
                <th className="text-right px-4 py-3 text-text-muted font-normal">actions</th>
              </tr>
            </thead>
            <tbody>
              {leads.length === 0 && (
                <tr>
                  <td colSpan={7}>
                    <EmptyState
                      icon={<Target size={32} />}
                      title={statusFilter ? `No ${statusFilter.toLowerCase()} leads` : "No leads yet"}
                      description="Recruiters who leave an email after a JD match land here"
                    />
                  </td>
                </tr>
              )}
              {leads.map((l) => (
                <tr
                  key={l.id}
                  className={`border-b border-terminal-border/50 hover:bg-terminal-bg/50 transition-colors ${
                    l.status === "NEW" ? "bg-git-green/[0.03]" : ""
                  }`}
                >
                  <td className="px-4 py-3">
                    <StatusChip status={l.status} />
                  </td>
                  <td className="px-4 py-3">
                    <div className={l.status === "NEW" ? "text-text-primary font-semibold" : "text-text-secondary"}>
                      {l.email}
                    </div>
                  </td>
                  <td className="px-4 py-3 text-text-muted">{l.company ?? "—"}</td>
                  <td className="px-4 py-3">
                    <FitScoreChip score={l.fitScore} />
                  </td>
                  <td className="px-4 py-3 text-text-muted max-w-[24ch] truncate">
                    {l.matchedSkills?.length ? l.matchedSkills.join(", ") : "—"}
                  </td>
                  <td className="px-4 py-3 text-text-faint whitespace-nowrap">
                    {new Date(l.createdAt).toLocaleString()}
                  </td>
                  <td className="px-4 py-3 text-right whitespace-nowrap">
                    <button onClick={() => openLead(l)} className="text-git-blue hover:underline">
                      open
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>

      <AdminModal
        open={selected !== null}
        onClose={() => setSelected(null)}
        title={selected ? `lead — ${selected.email}` : "lead"}
      >
        {selected && (
          <div className="space-y-4 font-mono text-xs">
            <div className="flex items-center gap-2 flex-wrap">
              <StatusChip status={selected.status} />
              <FitScoreChip score={selected.fitScore} />
              <span className="text-text-faint text-[10px] ml-auto">
                {new Date(selected.createdAt).toLocaleString()}
              </span>
            </div>
            <div>
              <div className="text-text-faint text-[10px] mb-1">From:</div>
              <div className="text-text-primary">
                {selected.email}
                {selected.company && <span className="text-text-muted"> — {selected.company}</span>}
              </div>
            </div>
            {selected.note && (
              <div>
                <div className="text-text-faint text-[10px] mb-1">Note:</div>
                <pre className="whitespace-pre-wrap rounded-lg border border-terminal-border bg-terminal-bg p-3 text-text-secondary leading-relaxed">
                  {selected.note}
                </pre>
              </div>
            )}
            {selected.matchedSkills && selected.matchedSkills.length > 0 && (
              <div>
                <div className="text-text-faint text-[10px] mb-1">Matched skills (self-reported):</div>
                <div className="flex flex-wrap gap-1.5">
                  {selected.matchedSkills.map((skill) => (
                    <span
                      key={skill}
                      className="inline-block rounded border border-git-green/30 bg-git-green/5 px-1.5 py-0.5 text-[10px] text-git-green"
                    >
                      {skill}
                    </span>
                  ))}
                </div>
              </div>
            )}
            {selected.jdExcerpt && (
              <div>
                <div className="text-text-faint text-[10px] mb-1">JD excerpt:</div>
                <pre className="whitespace-pre-wrap rounded-lg border border-terminal-border bg-terminal-bg p-3 text-text-secondary leading-relaxed max-h-40 overflow-y-auto">
                  {selected.jdExcerpt}
                </pre>
              </div>
            )}
            <div className="flex gap-2 pt-1">
              <a
                href={`mailto:${selected.email}?subject=${encodeURIComponent("Re: your JD match on my portfolio")}`}
                className="inline-flex items-center justify-center gap-2 rounded-lg border border-git-green-dim bg-git-green-muted px-3 py-1.5 text-xs text-git-green hover:bg-git-green-dim/30 transition-all"
              >
                $ git reply --via-email
              </a>
              {selected.status !== "REPLIED" && (
                <LoadingButton
                  variant="ghost"
                  loading={updating}
                  loadingText="Saving..."
                  onClick={() => markReplied(selected)}
                >
                  mark replied
                </LoadingButton>
              )}
            </div>
          </div>
        )}
      </AdminModal>
    </div>
  );
}
