
import { useEffect, useState } from "react";
import { useScrollProgress } from "@/hooks/useScrollProgress";
import { profile } from "@/data/profile";

export function StatusBar() {
  const { progress, activeSection } = useScrollProgress();
  const [time, setTime] = useState("");

  useEffect(() => {
    function tick() {
      setTime(
        new Date().toLocaleTimeString("en-US", { hour: "2-digit", minute: "2-digit" })
      );
    }
    tick();
    const id = setInterval(tick, 1000);
    return () => clearInterval(id);
  }, []);

  const branch = activeSection || profile.currentBranch;

  return (
    <div
      className="fixed bottom-0 left-0 right-0 z-50 h-7 flex items-center justify-between px-3 text-white text-xs font-mono select-none"
      style={{ backgroundColor: "rgb(var(--color-statusbar-bg))" }}
    >
      {/* Left: branch info */}
      <div className="flex items-center gap-2">
        <span className="opacity-80">⑂</span>
        <span>{branch}</span>
      </div>

      {/* Center: scroll progress */}
      <div className="opacity-70">
        {Math.round(progress)}%
      </div>

      {/* Right: clock */}
      <div className="opacity-80">{time}</div>
    </div>
  );
}
