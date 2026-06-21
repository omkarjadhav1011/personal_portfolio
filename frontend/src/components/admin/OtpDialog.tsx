import { FormEvent, useEffect, useState } from "react";
import { AdminModal } from "@/components/admin/AdminModal";
import { LoadingButton } from "@/components/ui/LoadingButton";
import {
  FileDto,
  fetchDownloadToken,
  requestOtp,
  sendFileEmail,
  triggerBrowserDownload,
} from "@/api/drive";

interface OtpDialogProps {
  file: FileDto;
  action: "download" | "send";
  onClose: () => void;
  onResult: (message: string, type: "success" | "error") => void;
}

/**
 * Sensitive-file second factor. On open it asks the server to email a 6-digit code, then lets the
 * owner enter it; on confirm it performs the original action (download or send) with the code.
 */
export function OtpDialog({ file, action, onClose, onResult }: OtpDialogProps) {
  const [code, setCode] = useState("");
  const [sending, setSending] = useState(true);
  const [verifying, setVerifying] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [info, setInfo] = useState("Sending a verification code to your email…");

  useEffect(() => {
    let active = true;
    (async () => {
      try {
        await requestOtp(file.id);
        if (active) setInfo("We emailed you a 6-digit code. Enter it below.");
      } catch (e) {
        if (active) {
          setError(e instanceof Error ? e.message : "Could not send the code");
          setInfo("");
        }
      } finally {
        if (active) setSending(false);
      }
    })();
    return () => {
      active = false;
    };
  }, [file.id]);

  async function resend() {
    setError(null);
    setSending(true);
    setInfo("Resending…");
    try {
      await requestOtp(file.id);
      setInfo("A new code is on its way.");
    } catch (e) {
      setError(e instanceof Error ? e.message : "Could not resend the code");
      setInfo("");
    } finally {
      setSending(false);
    }
  }

  async function confirm(e: FormEvent) {
    e.preventDefault();
    if (code.length < 6) return;
    setVerifying(true);
    setError(null);
    try {
      if (action === "download") {
        const { token } = await fetchDownloadToken(file.id, code);
        triggerBrowserDownload(token);
        onResult(`Downloading ${file.filename}`, "success");
      } else {
        await sendFileEmail(file.id, code);
        onResult("Sent to your email", "success");
      }
      onClose();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Verification failed");
    } finally {
      setVerifying(false);
    }
  }

  return (
    <AdminModal open onClose={onClose} title={`verify — ${file.filename}`}>
      <form onSubmit={confirm} className="space-y-4">
        <p className="font-mono text-xs text-text-muted">
          <span className="text-git-yellow">{file.filename}</span> is marked sensitive. {info}
        </p>
        <input
          autoFocus
          inputMode="numeric"
          pattern="[0-9]*"
          maxLength={6}
          value={code}
          onChange={(e) => setCode(e.target.value.replace(/\D/g, ""))}
          placeholder="------"
          aria-label="Verification code"
          className="w-full rounded-lg border border-terminal-border bg-terminal-bg px-3 py-2.5 text-center font-mono text-lg tracking-[0.5em] text-text-primary placeholder-text-faint outline-none transition-colors focus:border-git-blue/50 focus-visible:ring-1 focus-visible:ring-git-blue/20"
        />
        {error && <p className="font-mono text-[11px] text-git-red">{error}</p>}
        <div className="flex items-center gap-2">
          <LoadingButton type="submit" loading={verifying} disabled={sending || code.length < 6} className="flex-1">
            {action === "download" ? "Verify & download" : "Verify & send"}
          </LoadingButton>
          <LoadingButton type="button" variant="ghost" loading={sending} onClick={resend}>
            Resend
          </LoadingButton>
        </div>
      </form>
    </AdminModal>
  );
}
