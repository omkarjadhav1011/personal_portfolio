
import { Sparkles } from "lucide-react";
import { useCommandPaletteStore } from "@/store/commandPalette";

export function FloatingAIButton() {
  const { openInMode } = useCommandPaletteStore();

  return (
    <button
      onClick={() => openInMode("ai")}
      aria-label="Open AI assistant"
      className="fixed bottom-10 right-4 sm:right-6 z-40 flex items-center gap-2 px-3 sm:px-4 py-2.5 rounded-full bg-terminal-surface border border-git-green/40 text-git-green shadow-lg hover:bg-git-green/10 hover:border-git-green/70 transition-all duration-200 cursor-pointer group"
      style={{ boxShadow: "0 0 16px rgb(var(--color-git-green) / 0.15)" }}
    >
      <Sparkles
        size={14}
        className="shrink-0 group-hover:scale-110 transition-transform duration-200"
      />
      <span className="text-xs font-mono hidden sm:block">Ask AI</span>
    </button>
  );
}
