import { useState } from "react";
import { Inbox } from "lucide-react";
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

const STATUS_TABS: Array<{ value: MessageStatus | undefined; label: string }> = [
  { value: undefined, label: "all" },
  { value: "NEW", label: "new" },
  { value: "READ", label: "read" },
  { value: "REPLIED", label: "replied" },
  { value: "ARCHIVED", label: "archived" },
];

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

/**
 * Admin messages inbox (lead capture A3). Lists contact_message rows from the A2 API with a
 * status filter; opening a NEW message marks it READ; reply is a mailto: handoff (in-app
 * reply is future scope X5); delete asks for confirmation.
 */
export default function MessagesAdmin() {
  useDocumentTitle("Messages");
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
    <motion.div initial={{ opacity: 0 }} animate={{ opacity: 1 }} className="space-y-6 font-mono">
      <div>
        <div className="text-text-faint text-xs mb-1">$ git fetch origin/inbox</div>
        <h1 className="text-xl font-bold text-text-primary">Messages</h1>
        <p className="text-text-muted text-xs mt-0.5">
          # {data.totalElements} message{data.totalElements === 1 ? "" : "s"}
          {statusFilter ? ` — ${statusFilter.toLowerCase()}` : ""}
        </p>
      </div>

      {/* Status filter tabs */}
      <div className="flex flex-wrap gap-2">
        {STATUS_TABS.map((tab) => {
          const active = statusFilter === tab.value;
          return (
            <button
              key={tab.label}
              onClick={() => setStatusFilter(tab.value)}
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
    </motion.div>
  );
}
