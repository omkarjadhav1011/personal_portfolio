import { DragEvent, FormEvent, useRef, useState } from "react";
import {
  ChevronRight,
  Download,
  File as FileIcon,
  FileArchive,
  FileSpreadsheet,
  FileText,
  Folder,
  FolderPlus,
  HardDrive,
  Image as ImageIcon,
  Loader2,
  Lock,
  Mail,
  Pencil,
  Trash2,
  Upload,
} from "lucide-react";
import { AdminModal } from "@/components/admin/AdminModal";
import { FormCheckbox, FormInput } from "@/components/admin/FormField";
import { LoadingButton } from "@/components/ui/LoadingButton";
import { EmptyState } from "@/components/ui/EmptyState";
import { OtpDialog } from "@/components/admin/OtpDialog";
import { useToast } from "@/components/admin/ToastProvider";
import {
  FileDto,
  FolderDto,
  fetchDownloadToken,
  sendFileEmail,
  triggerBrowserDownload,
  useCreateFolder,
  useDeleteFile,
  useDeleteFolder,
  useFolderContents,
  useRenameFolder,
  useUploadFile,
} from "@/api/drive";

function formatBytes(n: number): string {
  if (n < 1024) return `${n} B`;
  if (n < 1024 * 1024) return `${(n / 1024).toFixed(1)} KB`;
  return `${(n / (1024 * 1024)).toFixed(1)} MB`;
}

function errMsg(e: unknown): string {
  return e instanceof Error ? e.message : "Something went wrong";
}

function FileTypeIcon({ contentType, size = 30 }: { contentType: string; size?: number }) {
  if (contentType.startsWith("image/")) return <ImageIcon size={size} className="text-git-purple" />;
  if (contentType === "application/pdf") return <FileText size={size} className="text-git-red" />;
  if (contentType === "application/zip") return <FileArchive size={size} className="text-git-yellow" />;
  if (contentType.includes("spreadsheet") || contentType === "application/vnd.ms-excel")
    return <FileSpreadsheet size={size} className="text-git-green" />;
  if (contentType.startsWith("text/") || contentType === "application/json")
    return <FileText size={size} className="text-text-secondary" />;
  return <FileIcon size={size} className="text-git-blue" />;
}

const iconBtn =
  "p-1.5 rounded-md text-text-muted hover:text-text-primary hover:bg-terminal-border/40 transition-colors focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-git-blue/30";

export function DriveClient() {
  const { toast } = useToast();
  const [folderId, setFolderId] = useState<string | null>(null);
  const [trail, setTrail] = useState<FolderDto[]>([]);
  const [newFolderOpen, setNewFolderOpen] = useState(false);
  const [newName, setNewName] = useState("");
  const [renameTarget, setRenameTarget] = useState<FolderDto | null>(null);
  const [renameName, setRenameName] = useState("");
  const [dragging, setDragging] = useState(false);
  const [uploading, setUploading] = useState(0);
  const [uploadSensitive, setUploadSensitive] = useState(false);
  const [otp, setOtp] = useState<{ file: FileDto; action: "download" | "send" } | null>(null);
  const fileInputRef = useRef<HTMLInputElement>(null);

  const { data, isPending, isError, error } = useFolderContents(folderId);
  const createFolder = useCreateFolder();
  const renameFolder = useRenameFolder();
  const deleteFolder = useDeleteFolder();
  const uploadFile = useUploadFile();
  const deleteFile = useDeleteFile();

  // ── Navigation ──
  function openFolder(f: FolderDto) {
    setTrail((t) => [...t, f]);
    setFolderId(f.id);
  }
  function goToCrumb(index: number) {
    if (index < 0) {
      setTrail([]);
      setFolderId(null);
    } else {
      setTrail((t) => t.slice(0, index + 1));
      setFolderId(trail[index].id);
    }
  }

  // ── Uploads ──
  async function handleFiles(list: FileList | File[]) {
    const files = Array.from(list);
    if (files.length === 0) return;
    setUploading((n) => n + files.length);
    for (const file of files) {
      try {
        await uploadFile.mutateAsync({ file, folderId, sensitive: uploadSensitive });
        toast(`Uploaded ${file.name}`, "success");
      } catch (e) {
        toast(errMsg(e), "error");
      } finally {
        setUploading((n) => n - 1);
      }
    }
  }
  function onDragOver(e: DragEvent) {
    e.preventDefault();
    e.dataTransfer.dropEffect = "copy";
    if (!dragging) setDragging(true);
  }
  function onDragLeave(e: DragEvent<HTMLDivElement>) {
    if (!e.currentTarget.contains(e.relatedTarget as Node | null)) setDragging(false);
  }
  function onDrop(e: DragEvent) {
    e.preventDefault();
    setDragging(false);
    if (e.dataTransfer.files?.length) void handleFiles(e.dataTransfer.files);
  }

  // ── Folder CRUD ──
  async function submitNewFolder(e: FormEvent) {
    e.preventDefault();
    const name = newName.trim();
    if (!name) return;
    try {
      await createFolder.mutateAsync({ name, parentId: folderId });
      toast(`Created ${name}`, "success");
      setNewFolderOpen(false);
      setNewName("");
    } catch (err) {
      toast(errMsg(err), "error");
    }
  }
  function startRename(f: FolderDto) {
    setRenameTarget(f);
    setRenameName(f.name);
  }
  async function submitRename(e: FormEvent) {
    e.preventDefault();
    if (!renameTarget) return;
    const name = renameName.trim();
    if (!name) return;
    try {
      await renameFolder.mutateAsync({ id: renameTarget.id, name, parentId: renameTarget.parentId });
      toast("Renamed", "success");
      setRenameTarget(null);
    } catch (err) {
      toast(errMsg(err), "error");
    }
  }
  async function removeFolder(f: FolderDto) {
    if (!window.confirm(`Delete folder "${f.name}" and everything inside it? This cannot be undone.`)) return;
    try {
      await deleteFolder.mutateAsync(f.id);
      toast("Folder deleted", "success");
    } catch (err) {
      toast(errMsg(err), "error");
    }
  }

  // ── File actions ──
  async function download(file: FileDto) {
    if (file.sensitive) {
      setOtp({ file, action: "download" });
      return;
    }
    try {
      const { token } = await fetchDownloadToken(file.id);
      triggerBrowserDownload(token);
    } catch (err) {
      toast(errMsg(err), "error");
    }
  }
  async function sendEmail(file: FileDto) {
    if (file.sensitive) {
      setOtp({ file, action: "send" });
      return;
    }
    try {
      await sendFileEmail(file.id);
      toast("Sent to your email", "success");
    } catch (err) {
      toast(errMsg(err), "error");
    }
  }
  async function removeFile(file: FileDto) {
    if (!window.confirm(`Delete "${file.filename}"? This cannot be undone.`)) return;
    try {
      await deleteFile.mutateAsync(file.id);
      toast("File deleted", "success");
    } catch (err) {
      toast(errMsg(err), "error");
    }
  }

  const folders = data?.folders ?? [];
  const files = data?.files ?? [];
  const isEmpty = !isPending && !isError && folders.length === 0 && files.length === 0;

  return (
    <div className="space-y-4">
      {/* Header */}
      <div className="flex items-center justify-between gap-3">
        <div>
          <div className="font-mono text-xs text-text-faint">$ ls -la ~/vault</div>
          <h1 className="flex items-center gap-2 text-xl font-bold text-text-primary">
            <HardDrive size={18} className="text-git-blue" /> Drive
          </h1>
          <p className="mt-0.5 font-mono text-xs text-text-muted">
            # {folders.length} folder{folders.length === 1 ? "" : "s"}, {files.length} file
            {files.length === 1 ? "" : "s"}
          </p>
        </div>
        <div className="flex items-center gap-3">
          <span title="Uploads will require an email code to download or send">
            <FormCheckbox label="sensitive" checked={uploadSensitive} onChange={setUploadSensitive} />
          </span>
          <button
            onClick={() => setNewFolderOpen(true)}
            className="flex items-center gap-1.5 rounded-lg border border-terminal-border bg-terminal-surface px-3 py-2 font-mono text-xs text-text-secondary transition-all hover:bg-terminal-border/30 focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-git-blue/30"
          >
            <FolderPlus size={13} /> new folder
          </button>
          <button
            onClick={() => fileInputRef.current?.click()}
            className="flex items-center gap-1.5 rounded-lg border border-git-green/40 bg-git-green/10 px-3 py-2 font-mono text-xs text-git-green transition-all hover:border-git-green/70 hover:bg-git-green/20 focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-git-green/40"
          >
            <Upload size={13} /> upload
          </button>
          <input
            ref={fileInputRef}
            type="file"
            multiple
            className="hidden"
            onChange={(e) => {
              if (e.target.files) void handleFiles(e.target.files);
              e.target.value = "";
            }}
          />
        </div>
      </div>

      {/* Breadcrumb */}
      <nav aria-label="Breadcrumb" className="flex flex-wrap items-center gap-1 font-mono text-xs">
        <button
          onClick={() => goToCrumb(-1)}
          className="rounded px-1.5 py-0.5 text-text-muted transition-colors hover:text-git-blue focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-git-blue/30"
        >
          drive
        </button>
        {trail.map((f, i) => (
          <span key={f.id} className="flex items-center gap-1">
            <ChevronRight size={12} className="text-text-faint" />
            <button
              onClick={() => goToCrumb(i)}
              className={`rounded px-1.5 py-0.5 transition-colors hover:text-git-blue focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-git-blue/30 ${
                i === trail.length - 1 ? "text-text-primary" : "text-text-muted"
              }`}
            >
              {f.name}
            </button>
          </span>
        ))}
        {uploading > 0 && (
          <span className="ml-2 flex items-center gap-1.5 text-git-blue">
            <Loader2 size={12} className="animate-spin" /> uploading {uploading}…
          </span>
        )}
      </nav>

      {/* Drop zone + grid */}
      <div
        onDragOver={onDragOver}
        onDragLeave={onDragLeave}
        onDrop={onDrop}
        className={`relative min-h-[16rem] rounded-xl border border-dashed p-4 transition-colors ${
          dragging ? "border-git-green bg-git-green/5" : "border-terminal-border bg-terminal-surface/40"
        }`}
      >
        {dragging && (
          <div className="pointer-events-none absolute inset-0 z-10 flex items-center justify-center rounded-xl bg-terminal-bg/70 font-mono text-sm text-git-green">
            <Upload size={18} className="mr-2" /> Drop files to upload
          </div>
        )}

        {isPending && <p className="font-mono text-sm text-text-muted">Loading…</p>}
        {isError && (
          <p className="font-mono text-sm text-git-red">
            Failed to load{error instanceof Error ? `: ${error.message}` : ""}.
          </p>
        )}

        {isEmpty && (
          <EmptyState
            icon={<Folder size={32} />}
            title="This folder is empty"
            description="Drag files here, or use the buttons above."
            action={{ label: "+ new folder", onClick: () => setNewFolderOpen(true) }}
          />
        )}

        {!isPending && !isError && (folders.length > 0 || files.length > 0) && (
          <div className="grid grid-cols-2 gap-3 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-5">
            {folders.map((f) => (
              <div
                key={f.id}
                className="group flex flex-col rounded-xl border border-terminal-border bg-terminal-surface transition-colors hover:border-git-blue/40"
              >
                <button
                  onClick={() => openFolder(f)}
                  className="flex flex-1 flex-col items-center gap-2 px-3 pt-4 pb-2 text-center focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-git-blue/30"
                >
                  <Folder size={30} className="text-git-blue" />
                  <span className="w-full truncate font-mono text-xs text-text-primary" title={f.name}>
                    {f.name}
                  </span>
                </button>
                <div className="flex items-center justify-end gap-0.5 border-t border-terminal-border/60 px-1.5 py-1">
                  <button onClick={() => startRename(f)} aria-label={`Rename ${f.name}`} title="Rename" className={iconBtn}>
                    <Pencil size={13} />
                  </button>
                  <button onClick={() => removeFolder(f)} aria-label={`Delete ${f.name}`} title="Delete" className={iconBtn}>
                    <Trash2 size={13} />
                  </button>
                </div>
              </div>
            ))}

            {files.map((file) => (
              <div
                key={file.id}
                className="flex flex-col rounded-xl border border-terminal-border bg-terminal-surface transition-colors hover:border-git-blue/40"
              >
                <div className="flex flex-1 flex-col items-center gap-2 px-3 pt-4 pb-2 text-center">
                  <FileTypeIcon contentType={file.contentType} />
                  <span className="w-full truncate font-mono text-xs text-text-primary" title={file.filename}>
                    {file.filename}
                  </span>
                  <span className="flex items-center gap-1 font-mono text-[10px] text-text-faint">
                    {formatBytes(file.sizeBytes)}
                    {file.sensitive && <Lock size={10} className="text-git-yellow" aria-label="Sensitive" />}
                  </span>
                </div>
                <div className="flex items-center justify-end gap-0.5 border-t border-terminal-border/60 px-1.5 py-1">
                  <button onClick={() => download(file)} aria-label={`Download ${file.filename}`} title="Download" className={iconBtn}>
                    <Download size={13} />
                  </button>
                  <button onClick={() => sendEmail(file)} aria-label={`Email ${file.filename}`} title="Send to my email" className={iconBtn}>
                    <Mail size={13} />
                  </button>
                  <button onClick={() => removeFile(file)} aria-label={`Delete ${file.filename}`} title="Delete" className={iconBtn}>
                    <Trash2 size={13} />
                  </button>
                </div>
              </div>
            ))}
          </div>
        )}
      </div>

      {/* New folder modal */}
      <AdminModal open={newFolderOpen} onClose={() => setNewFolderOpen(false)} title="new folder">
        <form onSubmit={submitNewFolder} className="space-y-4">
          <FormInput
            label="folder name"
            value={newName}
            onChange={(e) => setNewName(e.target.value)}
            placeholder="e.g. invoices"
            autoFocus
            required
          />
          <LoadingButton type="submit" loading={createFolder.isPending} className="w-full">
            create
          </LoadingButton>
        </form>
      </AdminModal>

      {/* Rename modal */}
      <AdminModal open={renameTarget !== null} onClose={() => setRenameTarget(null)} title="rename folder">
        <form onSubmit={submitRename} className="space-y-4">
          <FormInput
            label="folder name"
            value={renameName}
            onChange={(e) => setRenameName(e.target.value)}
            autoFocus
            required
          />
          <LoadingButton type="submit" loading={renameFolder.isPending} className="w-full">
            save
          </LoadingButton>
        </form>
      </AdminModal>

      {/* Sensitive-file OTP flow */}
      {otp && (
        <OtpDialog
          file={otp.file}
          action={otp.action}
          onClose={() => setOtp(null)}
          onResult={(message, type) => toast(message, type)}
        />
      )}
    </div>
  );
}
