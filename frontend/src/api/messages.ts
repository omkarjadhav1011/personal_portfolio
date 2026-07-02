import { useQuery } from "@tanstack/react-query";
import { apiFetch } from "@/lib/api";

export type MessageSource = "WEB" | "RECRUITER" | "CHATBOT" | "MCP";
export type MessageStatus = "NEW" | "READ" | "REPLIED" | "ARCHIVED";

export interface ContactMessage {
  id: string;
  name: string;
  email: string;
  message: string;
  source: MessageSource;
  status: MessageStatus;
  createdAt: string;
}

/** Page envelope returned by GET /api/admin/messages. */
export interface MessagesPage {
  content: ContactMessage[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export const messageKeys = {
  all: ["admin-messages"] as const,
  list: (status?: MessageStatus) => ["admin-messages", status ?? "all"] as const,
};

function fetchMessages(status?: MessageStatus): Promise<MessagesPage> {
  const query = status ? `?status=${status}` : "";
  return apiFetch<MessagesPage>(`/api/admin/messages${query}`);
}

/** Admin inbox list (ADMIN JWT attached by apiFetch), newest first. */
export function useMessages(status?: MessageStatus) {
  return useQuery({
    queryKey: messageKeys.list(status),
    queryFn: () => fetchMessages(status),
  });
}

/** Unread count for the dashboard card / badge. Shares the cache with the NEW tab. */
export function useNewMessageCount() {
  return useQuery({
    queryKey: messageKeys.list("NEW"),
    queryFn: () => fetchMessages("NEW"),
    select: (page) => page.totalElements,
  });
}
