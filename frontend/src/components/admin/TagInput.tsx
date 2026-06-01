
import { useState, KeyboardEvent } from "react";

interface TagInputProps {
  label: string;
  values: string[];
  onChange: (values: string[]) => void;
  placeholder?: string;
}

export function TagInput({ label, values, onChange, placeholder = "Add tag…" }: TagInputProps) {
  const [input, setInput] = useState("");

  function addTag(raw: string) {
    const tag = raw.trim();
    if (tag && !values.includes(tag)) {
      onChange([...values, tag]);
    }
    setInput("");
  }

  function removeTag(tag: string) {
    onChange(values.filter((v) => v !== tag));
  }

  function handleKey(e: KeyboardEvent<HTMLInputElement>) {
    if (e.key === "Enter" || e.key === ",") {
      e.preventDefault();
      addTag(input);
    } else if (e.key === "Backspace" && !input && values.length > 0) {
      onChange(values.slice(0, -1));
    }
  }

  return (
    <div className="space-y-1">
      <label className="block font-mono text-xs text-text-muted">{label}</label>
      <div className="min-h-[38px] w-full bg-terminal-bg border border-terminal-border rounded-lg px-2 py-1.5 flex flex-wrap gap-1.5 focus-within:border-git-blue/50 focus-within:ring-1 focus-within:ring-git-blue/20 transition-colors">
        {values.map((tag) => (
          <span
            key={tag}
            className="inline-flex items-center gap-1 px-2 py-0.5 rounded text-[10px] font-mono bg-git-blue/10 border border-git-blue/20 text-git-blue"
          >
            {tag}
            <button
              type="button"
              onClick={() => removeTag(tag)}
              className="text-git-blue/60 hover:text-git-red transition-colors ml-0.5"
            >
              ×
            </button>
          </span>
        ))}
        <input
          type="text"
          value={input}
          onChange={(e) => setInput(e.target.value)}
          onKeyDown={handleKey}
          onBlur={() => input && addTag(input)}
          placeholder={values.length === 0 ? placeholder : ""}
          className="flex-1 min-w-[80px] bg-transparent font-mono text-xs text-text-primary placeholder-text-faint focus:outline-none"
        />
      </div>
      <p className="font-mono text-[10px] text-text-faint">Press Enter or comma to add</p>
    </div>
  );
}
