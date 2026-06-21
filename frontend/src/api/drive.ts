import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { ApiError, apiFetch, assetUrl, authFetch } from "@/lib/api";

// ── Types (mirror the backend DriveDtos) ───────────────────────────────────

export interface FolderDto {
  id: string;
  parentId: string | null;
  name: string;
  createdAt: string;
}

export interface FileDto {
  id: string;
  folderId: string | null;
  filename: string;
  contentType: string;
  sizeBytes: number;
  sensitive: boolean;
  createdAt: string;
}

export interface FolderContents {
  folder: FolderDto | null;
  folders: FolderDto[];
  files: FileDto[];
}

export interface DownloadTokenResponse {
  token: string;
  expiresInSeconds: number;
}

export const driveKeys = {
  all: ["drive"] as const,
  folder: (id: string | null) => ["drive", "folder", id ?? "root"] as const,
};

// ── Queries ────────────────────────────────────────────────────────────────

export function useFolderContents(folderId: string | null) {
  return useQuery({
    queryKey: driveKeys.folder(folderId),
    queryFn: () =>
      apiFetch<FolderContents>(folderId ? `/api/drive/folders/${folderId}` : "/api/drive/folders"),
  });
}

// ── Mutations (invalidate the whole drive tree — folder views are interdependent) ──

export function useCreateFolder() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (input: { name: string; parentId: string | null }) =>
      apiFetch<FolderDto>("/api/drive/folders", { method: "POST", body: JSON.stringify(input) }),
    onSuccess: () => qc.invalidateQueries({ queryKey: driveKeys.all }),
  });
}

export function useRenameFolder() {
  const qc = useQueryClient();
  return useMutation({
    // parentId is echoed back so a rename does not move the folder (PATCH = desired end-state).
    mutationFn: (input: { id: string; name: string; parentId: string | null }) =>
      apiFetch<FolderDto>(`/api/drive/folders/${input.id}`, {
        method: "PATCH",
        body: JSON.stringify({ name: input.name, parentId: input.parentId }),
      }),
    onSuccess: () => qc.invalidateQueries({ queryKey: driveKeys.all }),
  });
}

export function useDeleteFolder() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => apiFetch<void>(`/api/drive/folders/${id}`, { method: "DELETE" }),
    onSuccess: () => qc.invalidateQueries({ queryKey: driveKeys.all }),
  });
}

export function useUploadFile() {
  const qc = useQueryClient();
  return useMutation({
    // Multipart must go through authFetch (raw) — apiFetch would force a JSON content type.
    mutationFn: async (input: { file: File; folderId: string | null; sensitive?: boolean }) => {
      const form = new FormData();
      form.append("file", input.file);
      if (input.folderId) form.append("folderId", input.folderId);
      if (input.sensitive) form.append("sensitive", "true");
      const res = await authFetch("/api/drive/files", { method: "POST", body: form });
      if (!res.ok) throw await toApiError(res);
      return (await res.json()) as FileDto;
    },
    onSuccess: () => qc.invalidateQueries({ queryKey: driveKeys.all }),
  });
}

export function useDeleteFile() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => apiFetch<void>(`/api/drive/files/${id}`, { method: "DELETE" }),
    onSuccess: () => qc.invalidateQueries({ queryKey: driveKeys.all }),
  });
}

// ── One-off actions (no cache to invalidate) ───────────────────────────────

export function requestOtp(fileId: string): Promise<{ status: string }> {
  return apiFetch<{ status: string }>(`/api/drive/files/${fileId}/request-otp`, { method: "POST" });
}

export function fetchDownloadToken(fileId: string, otp?: string): Promise<DownloadTokenResponse> {
  const q = otp ? `?otp=${encodeURIComponent(otp)}` : "";
  return apiFetch<DownloadTokenResponse>(`/api/drive/files/${fileId}/download-token${q}`);
}

export function sendFileEmail(fileId: string, otp?: string): Promise<{ status: string }> {
  const q = otp ? `?otp=${encodeURIComponent(otp)}` : "";
  return apiFetch<{ status: string }>(`/api/drive/files/${fileId}/send-email${q}`, { method: "POST" });
}

/**
 * Triggers a browser download of the public, single-use token endpoint. The server's
 * Content-Disposition supplies the original filename; no auth header is needed (the token is auth).
 */
export function triggerBrowserDownload(token: string): void {
  const url = assetUrl(`/api/drive/download/${token}`) ?? `/api/drive/download/${token}`;
  const a = document.createElement("a");
  a.href = url;
  a.rel = "noopener";
  document.body.appendChild(a);
  a.click();
  a.remove();
}

/** Turns a non-OK raw {@link authFetch} Response into an {@link ApiError} carrying the backend message. */
async function toApiError(res: Response): Promise<ApiError> {
  let body: unknown;
  try {
    body = await res.json();
  } catch {
    // non-JSON error body — fall back to status text
  }
  let message = `Request failed: ${res.status}`;
  if (body && typeof body === "object") {
    const b = body as Record<string, unknown>;
    if (typeof b.message === "string") {
      message = b.message;
    } else {
      const err = b.error;
      if (err && typeof err === "object" && typeof (err as Record<string, unknown>).message === "string") {
        message = (err as Record<string, unknown>).message as string;
      }
    }
  }
  return new ApiError(res.status, message, body);
}
