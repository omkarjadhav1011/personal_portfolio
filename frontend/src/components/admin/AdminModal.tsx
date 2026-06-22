
import * as Dialog from "@radix-ui/react-dialog";
import { ReactNode } from "react";

interface AdminModalProps {
  open: boolean;
  onClose: () => void;
  title: string;
  children: ReactNode;
}

export function AdminModal({ open, onClose, title, children }: AdminModalProps) {
  return (
    <Dialog.Root open={open} onOpenChange={(o) => !o && onClose()}>
      <Dialog.Portal>
        <Dialog.Overlay className="fixed inset-0 bg-black/60 backdrop-blur-sm z-40" />
        <Dialog.Content
          aria-describedby={undefined}
          className="fixed z-50 top-1/2 left-1/2 -translate-x-1/2 -translate-y-1/2 w-full max-w-xl max-h-[90vh] overflow-y-auto rounded-xl border border-terminal-border bg-terminal-surface shadow-terminal"
        >
          {/* Terminal titlebar */}
          <div className="flex items-center gap-1.5 px-4 py-3 border-b border-terminal-border bg-terminal-bg">
            <span className="w-3 h-3 rounded-full bg-dot-red" />
            <span className="w-3 h-3 rounded-full bg-dot-yellow" />
            <span className="w-3 h-3 rounded-full bg-dot-green" />
            <Dialog.Title className="ml-3 font-mono text-xs text-text-muted">
              {title}
            </Dialog.Title>
            <Dialog.Close
              className="ml-auto font-mono text-xs text-text-faint hover:text-text-muted transition-colors"
              onClick={onClose}
            >
              ✕
            </Dialog.Close>
          </div>
          <div className="p-5">{children}</div>
        </Dialog.Content>
      </Dialog.Portal>
    </Dialog.Root>
  );
}
