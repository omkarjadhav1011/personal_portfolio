
import { useTypingEffect, type TypingLine } from "@/hooks/useTypingEffect";
import { cn } from "@/lib/utils";

interface TypewriterTextProps {
  lines: TypingLine[];
  className?: string;
  onComplete?: () => void;
  startDelay?: number;
  showCursor?: boolean;
  /** Map line index to a color class */
  lineColors?: Record<number, string>;
  /** Map line prefix (like "$") to a color class */
  promptColor?: string;
}

export function TypewriterText({
  lines,
  className,
  onComplete,
  startDelay = 500,
  showCursor = true,
  lineColors = {},
  promptColor = "text-git-green",
}: TypewriterTextProps) {
  const { displayedLines, isComplete, currentLineIndex } = useTypingEffect({
    lines,
    onComplete,
    startDelay,
  });

  return (
    <div className={cn("space-y-1", className)}>
      {lines.map((line, i) => {
        const displayed = displayedLines[i] ?? "";
        const isCurrentLine = i === currentLineIndex;
        const isVisible = i < currentLineIndex || displayed.length > 0;

        if (!isVisible) return null;

        const colorClass = lineColors[i] ?? "text-text-primary";
        const isPromptLine = line.text.startsWith("$ ");

        return (
          <div key={i} className={cn("flex items-start gap-0", colorClass)}>
            {isPromptLine ? (
              <>
                <span className={cn("mr-2", promptColor)}>$</span>
                <span>{displayed.slice(2)}</span>
              </>
            ) : (
              <span className="whitespace-pre-wrap">{displayed}</span>
            )}
            {/* Blinking cursor on current line */}
            {isCurrentLine && !isComplete && showCursor && (
              <span className="ml-0.5 inline-block w-2 h-4 bg-git-green animate-cursor-blink align-text-bottom" />
            )}
          </div>
        );
      })}
      {/* Cursor after all lines */}
      {isComplete && showCursor && (
        <div className="flex items-center gap-2">
          <span className={cn(promptColor)}>$</span>
          <span className="inline-block w-2 h-4 bg-git-green animate-cursor-blink align-text-bottom" />
        </div>
      )}
    </div>
  );
}
