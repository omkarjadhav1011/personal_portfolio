
import { useEffect, useRef, useState, useCallback } from "react";
import { Link } from "react-router-dom";
import { AnimatePresence, motion } from "framer-motion";
import * as Dialog from "@radix-ui/react-dialog";
import {
  Terminal,
  Sparkles,
  Send,
  RotateCcw,
  FileSearch,
  ChevronRight,
} from "lucide-react";
import { useTerminal } from "@/hooks/useTerminal";
import { useAI, type AIMessage } from "@/hooks/useAI";
import { useCommandPaletteStore } from "@/store/commandPalette";
import { cn } from "@/lib/utils";
import { InlineMarkdown } from "@/components/ui/InlineMarkdown";

// ─── Suggested content ────────────────────────────────────────────────────────

const AI_PROMPTS = [
  "What are Omkar's main skills?",
  "Tell me about his projects",
  "Is he available to hire?",
  "What's his educational background?",
  "Any fun facts about him?",
  "How can I contact him?",
];

const COMMAND_CHIPS = [
  { label: "help", cmd: "help" },
  { label: "git log", cmd: "git log" },
  { label: "whoami", cmd: "whoami" },
  { label: "projects", cmd: "projects" },
  { label: "git branch", cmd: "git branch" },
  { label: "git stash pop", cmd: "git stash pop" },
];

// ─── AI chat bubble ───────────────────────────────────────────────────────────

function AIBubble({ message }: { message: AIMessage }) {
  if (message.role === "user") {
    return (
      <motion.div
        initial={{ opacity: 0, y: 4 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ duration: 0.18 }}
        className="flex justify-end"
      >
        <div className="max-w-[80%] px-3.5 py-2 rounded-2xl rounded-br-md bg-git-green/10 text-text-primary text-xs leading-relaxed">
          {message.content}
        </div>
      </motion.div>
    );
  }
  return (
    <motion.div
      initial={{ opacity: 0, y: 4 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ duration: 0.18 }}
      className="text-xs text-text-secondary leading-relaxed"
    >
      <InlineMarkdown content={message.content} />
    </motion.div>
  );
}

function TypingIndicator() {
  return (
    <div className="flex gap-1 items-center h-4">
      {[0, 1, 2].map((i) => (
        <motion.span
          key={i}
          className="w-1.5 h-1.5 rounded-full bg-git-green/50"
          animate={{ opacity: [0.3, 1, 0.3], y: [0, -2, 0] }}
          transition={{ duration: 0.8, repeat: Infinity, delay: i * 0.15 }}
        />
      ))}
    </div>
  );
}

// ─── Main component ───────────────────────────────────────────────────────────

export function CommandPalette() {
  const { open, mode, setOpen, setMode } = useCommandPaletteStore();
  const { history, submit, navigateHistory } = useTerminal();
  const { messages, isTyping, ask, clearChat } = useAI();

  const inputRef = useRef<HTMLInputElement>(null);
  const terminalBottomRef = useRef<HTMLDivElement>(null);
  const aiBottomRef = useRef<HTMLDivElement>(null);
  const [localInput, setLocalInput] = useState("");

  // Global keyboard handler — uses getState() to avoid stale closure
  useEffect(() => {
    function onKey(e: KeyboardEvent) {
      if ((e.ctrlKey || e.metaKey) && e.key === "k") {
        e.preventDefault();
        useCommandPaletteStore.getState().toggle();
      }
    }
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, []);

  // Auto-scroll — each panel has its own ref, gated by active mode to prevent
  // cross-panel scroll during AnimatePresence exit transitions
  useEffect(() => {
    if (mode === "terminal")
      terminalBottomRef.current?.scrollIntoView({ behavior: "smooth" });
  }, [history, mode]);

  useEffect(() => {
    if (mode === "ai")
      aiBottomRef.current?.scrollIntoView({ behavior: "smooth" });
  }, [messages, isTyping, mode]);

  // Focus input when opened or mode changes
  useEffect(() => {
    if (open) {
      setTimeout(() => inputRef.current?.focus(), 60);
    }
  }, [open, mode]);

  // Clear input on mode switch
  useEffect(() => {
    setLocalInput("");
  }, [mode]);

  const handleKeyDown = useCallback(
    (e: React.KeyboardEvent<HTMLInputElement>) => {
      if (e.key === "Enter") {
        const val = localInput.trim();
        if (!val) return;
        if (mode === "terminal") {
          submit(val);
        } else {
          ask(val);
        }
        setLocalInput("");
      } else if (mode === "terminal") {
        if (e.key === "ArrowUp") {
          e.preventDefault();
          setLocalInput(navigateHistory("up"));
        } else if (e.key === "ArrowDown") {
          e.preventDefault();
          setLocalInput(navigateHistory("down"));
        }
      }
    },
    [localInput, mode, submit, ask, navigateHistory]
  );

  const handleSend = useCallback(() => {
    const val = localInput.trim();
    if (!val || isTyping) return;
    ask(val);
    setLocalInput("");
  }, [localInput, isTyping, ask]);

  function handlePromptClick(prompt: string) {
    ask(prompt);
  }

  function handleCommandChip(cmd: string) {
    submit(cmd);
    setLocalInput("");
  }

  return (
    <Dialog.Root open={open} onOpenChange={setOpen}>
      <Dialog.Title className="sr-only">Command Palette</Dialog.Title>
      <AnimatePresence>
        {open && (
          <Dialog.Portal forceMount>
            {/* Backdrop */}
            <Dialog.Overlay asChild>
              <motion.div
                initial={{ opacity: 0 }}
                animate={{ opacity: 1 }}
                exit={{ opacity: 0 }}
                transition={{ duration: 0.15 }}
                className="fixed inset-0 z-50 bg-black/70 backdrop-blur-sm"
              />
            </Dialog.Overlay>

            {/* Panel */}
            <Dialog.Content asChild>
              {/* Positioning wrapper — static div so Framer Motion transform doesn't break centering */}
              <div className="fixed inset-0 sm:inset-auto sm:top-[14%] sm:left-1/2 sm:-translate-x-1/2 sm:w-full sm:max-w-2xl sm:px-4 z-50 flex flex-col sm:block">
              <motion.div
                initial={{ opacity: 0, scale: 0.96, y: -16 }}
                animate={{ opacity: 1, scale: 1, y: 0 }}
                exit={{ opacity: 0, scale: 0.96, y: -16 }}
                transition={{ duration: 0.18, ease: [0.16, 1, 0.3, 1] }}
              >
                <div className="rounded-none sm:rounded-xl overflow-hidden shadow-terminal border-0 sm:border border-terminal-border bg-terminal-window font-mono flex flex-col h-full sm:h-auto">

                  {/* ── Title bar ─────────────────────────────────────────── */}
                  <div className="flex items-center justify-between px-4 py-2.5 bg-terminal-surface border-b border-terminal-border">
                    {/* macOS dots */}
                    <div className="flex items-center gap-1.5">
                      <button
                        onClick={() => setOpen(false)}
                        className="w-3 h-3 rounded-full bg-dot-red hover:opacity-80 transition-opacity cursor-pointer"
                        aria-label="Close"
                      />
                      <span className="w-3 h-3 rounded-full bg-dot-yellow" />
                      <span className="w-3 h-3 rounded-full bg-dot-green" />
                    </div>

                    {/* Mode tabs */}
                    <div className="flex items-center gap-1 bg-terminal-bg rounded-lg p-1">
                      <button
                        onClick={() => setMode("terminal")}
                        className={cn(
                          "flex items-center gap-1.5 px-3 py-1 rounded-md text-xs transition-all duration-200 cursor-pointer select-none",
                          mode === "terminal"
                            ? "bg-terminal-surface text-text-primary shadow-sm border border-terminal-border"
                            : "text-text-faint hover:text-text-muted"
                        )}
                      >
                        <Terminal size={11} />
                        Terminal
                      </button>
                      <button
                        onClick={() => setMode("ai")}
                        className={cn(
                          "flex items-center gap-1.5 px-3 py-1 rounded-md text-xs transition-all duration-200 cursor-pointer select-none",
                          mode === "ai"
                            ? "bg-git-green/10 text-git-green border border-git-green/30"
                            : "text-text-faint hover:text-text-muted"
                        )}
                      >
                        <Sparkles size={11} />
                        Ask AI
                      </button>
                    </div>

                    {/* Right actions */}
                    <div className="flex items-center gap-2">
                      {mode === "ai" && messages.length > 0 && (
                        <button
                          onClick={clearChat}
                          className="text-text-faint hover:text-text-muted transition-colors cursor-pointer"
                          title="Clear chat"
                        >
                          <RotateCcw size={12} />
                        </button>
                      )}
                      <button
                        onClick={() => setOpen(false)}
                        className="text-text-faint hover:text-text-muted text-xs px-1 transition-colors cursor-pointer"
                      >
                        ESC
                      </button>
                    </div>
                  </div>

                  {/* ── Content area ──────────────────────────────────────── */}
                  <AnimatePresence mode="wait">
                    {mode === "terminal" ? (
                      <motion.div
                        key="terminal"
                        initial={{ opacity: 0 }}
                        animate={{ opacity: 1 }}
                        exit={{ opacity: 0 }}
                        transition={{ duration: 0.1 }}
                        className="min-h-[160px] max-h-[calc(100vh-180px)] sm:max-h-72 overflow-y-auto p-4 space-y-3 text-sm flex-1"
                      >
                        {history.length === 0 && (
                          <div className="space-y-3">
                            <p className="text-text-faint text-xs">
                              Type a command or pick one to get started:
                            </p>
                            <div className="flex flex-wrap gap-2">
                              {COMMAND_CHIPS.map(({ label, cmd }) => (
                                <button
                                  key={cmd}
                                  onClick={() => handleCommandChip(cmd)}
                                  className="px-2.5 py-1 rounded-md text-xs bg-terminal-bg border border-terminal-border text-text-muted hover:text-git-green hover:border-git-green/40 transition-colors cursor-pointer"
                                >
                                  {label}
                                </button>
                              ))}
                            </div>
                            <p className="text-text-faint text-2xs pt-1">
                              Tip: switch to{" "}
                              <button
                                onClick={() => setMode("ai")}
                                className="text-git-green/70 hover:text-git-green underline underline-offset-2 cursor-pointer transition-colors"
                              >
                                Ask AI
                              </button>{" "}
                              for natural language queries about this developer.
                            </p>
                          </div>
                        )}

                        {history.map((h, i) => (
                          <div key={i} className="space-y-1">
                            <div className="flex gap-2">
                              <span className="text-git-green shrink-0">$</span>
                              <span className="text-text-primary">{h.command}</span>
                            </div>
                            {h.result.output.map((line, li) => (
                              <div
                                key={li}
                                className={cn(
                                  "pl-4 text-xs",
                                  h.result.type === "error"
                                    ? "text-git-red"
                                    : line.startsWith("*")
                                    ? "text-git-green"
                                    : "text-text-muted"
                                )}
                              >
                                {line || "\u00A0"}
                              </div>
                            ))}
                          </div>
                        ))}
                        <div ref={terminalBottomRef} />
                      </motion.div>
                    ) : (
                      <motion.div
                        key="ai"
                        initial={{ opacity: 0 }}
                        animate={{ opacity: 1 }}
                        exit={{ opacity: 0 }}
                        transition={{ duration: 0.1 }}
                        className="min-h-[160px] max-h-[calc(100vh-180px)] sm:max-h-72 overflow-y-auto p-4 flex-1"
                      >
                        {messages.length === 0 && !isTyping ? (
                          /* Empty state: suggested prompts */
                          <div className="space-y-4 py-1">
                            <p className="text-xs text-text-muted leading-relaxed">
                              Ask me anything about Omkar&apos;s skills, projects, or background.
                            </p>
                            <div className="space-y-0.5">
                              {AI_PROMPTS.map((prompt) => (
                                <button
                                  key={prompt}
                                  onClick={() => handlePromptClick(prompt)}
                                  className="block w-full text-left px-3 py-2 rounded-lg text-xs text-text-muted hover:text-text-primary hover:bg-terminal-surface transition-colors cursor-pointer"
                                >
                                  {prompt}
                                </button>
                              ))}
                            </div>
                            <Link
                              to="/recruiter"
                              onClick={() => setOpen(false)}
                              className="flex items-center gap-2 px-3 py-2 rounded-lg text-xs text-text-faint hover:text-git-green hover:bg-terminal-surface transition-colors cursor-pointer"
                            >
                              <FileSearch size={13} className="text-git-green/60 shrink-0" />
                              <span className="flex-1 truncate">Recruiter mode — paste a JD for matched projects</span>
                              <ChevronRight size={13} className="shrink-0" />
                            </Link>
                          </div>
                        ) : (
                          /* Chat messages */
                          <div className="space-y-4">
                            {messages.map((msg) => (
                              <AIBubble key={msg.id} message={msg} />
                            ))}
                            {isTyping && <TypingIndicator />}
                            <div ref={aiBottomRef} />
                          </div>
                        )}
                      </motion.div>
                    )}
                  </AnimatePresence>

                  {/* ── Input bar ─────────────────────────────────────────── */}
                  <div className="flex items-center gap-2 px-4 py-3 border-t border-terminal-border bg-terminal-bg">
                    {mode === "terminal" ? (
                      <span className="text-git-green text-sm shrink-0 select-none">$</span>
                    ) : (
                      <Sparkles size={14} className="text-git-green/60 shrink-0" />
                    )}

                    <input
                      ref={inputRef}
                      value={localInput}
                      onChange={(e) => setLocalInput(e.target.value)}
                      onKeyDown={handleKeyDown}
                      placeholder={
                        mode === "terminal"
                          ? "git checkout projects"
                          : "Ask me anything about Omkar..."
                      }
                      disabled={mode === "ai" && isTyping}
                      className="flex-1 bg-transparent text-sm text-text-primary placeholder-text-faint outline-none focus-visible:ring-1 focus-visible:ring-git-green/40 rounded font-mono disabled:opacity-50"
                      autoComplete="off"
                      spellCheck={false}
                    />

                    {mode === "terminal" ? (
                      <span className="w-2 h-4 bg-git-green animate-cursor-blink shrink-0" />
                    ) : (
                      <button
                        onClick={handleSend}
                        disabled={!localInput.trim() || isTyping}
                        className="shrink-0 p-1 text-text-faint hover:text-git-green disabled:opacity-30 disabled:cursor-not-allowed transition-colors cursor-pointer"
                        aria-label="Send message"
                      >
                        <Send size={14} />
                      </button>
                    )}
                  </div>

                  {/* ── Footer hint ───────────────────────────────────────── */}
                  <div className="flex items-center justify-between px-4 py-1.5 bg-terminal-surface border-t border-terminal-border">
                    <span className="text-2xs text-text-faint">
                      {mode === "terminal"
                        ? "Arrow Up/Down to navigate history"
                        : "Powered by portfolio knowledge base"}
                    </span>
                    <span className="text-2xs text-text-faint">
                      <kbd className="px-1 py-0.5 rounded bg-terminal-bg border border-terminal-border text-2xs">
                        ESC
                      </kbd>{" "}
                      to close
                    </span>
                  </div>
                </div>
              </motion.div>
              </div>
            </Dialog.Content>
          </Dialog.Portal>
        )}
      </AnimatePresence>
    </Dialog.Root>
  );
}
