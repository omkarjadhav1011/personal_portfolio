
import { Sun, Monitor, Moon } from "lucide-react";
import { useThemeStore } from "@/store/theme";
import { cn } from "@/lib/utils";

const options = [
  { value: "light" as const, icon: Sun, label: "Light theme" },
  { value: "system" as const, icon: Monitor, label: "System theme" },
  { value: "dark" as const, icon: Moon, label: "Dark theme" },
];

export function ThemeToggle() {
  const { theme, setTheme } = useThemeStore();

  return (
    <div className="flex items-center gap-0.5 p-1 rounded-lg bg-terminal-surface border border-terminal-border">
      {options.map(({ value, icon: Icon, label }) => (
        <button
          key={value}
          onClick={() => setTheme(value)}
          aria-label={label}
          className={cn(
            "p-1.5 rounded-md transition-all duration-150 cursor-pointer",
            theme === value
              ? "bg-terminal-bg text-git-green shadow-sm"
              : "text-text-faint hover:text-text-muted"
          )}
        >
          <Icon size={14} />
        </button>
      ))}
    </div>
  );
}
