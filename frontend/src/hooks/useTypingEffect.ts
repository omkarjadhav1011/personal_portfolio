import { useState, useEffect, useCallback } from "react";

export interface TypingLine {
  text: string;
  delay?: number; // ms pause before this line starts
  speed?: number; // ms per character (default: 35)
}

interface UseTypingEffectOptions {
  lines: TypingLine[];
  onComplete?: () => void;
  startDelay?: number;
}

interface UseTypingEffectReturn {
  displayedLines: string[];
  isComplete: boolean;
  currentLineIndex: number;
  reset: () => void;
}

export function useTypingEffect({
  lines,
  onComplete,
  startDelay = 500,
}: UseTypingEffectOptions): UseTypingEffectReturn {
  const [displayedLines, setDisplayedLines] = useState<string[]>([]);
  const [currentLineIndex, setCurrentLineIndex] = useState(0);
  const [currentCharIndex, setCurrentCharIndex] = useState(0);
  const [isComplete, setIsComplete] = useState(false);
  const [started, setStarted] = useState(false);

  const reset = useCallback(() => {
    setDisplayedLines([]);
    setCurrentLineIndex(0);
    setCurrentCharIndex(0);
    setIsComplete(false);
    setStarted(false);
  }, []);

  // Start delay
  useEffect(() => {
    const timer = setTimeout(() => setStarted(true), startDelay);
    return () => clearTimeout(timer);
  }, [startDelay]);

  // Typing engine
  useEffect(() => {
    if (!started || isComplete) return;
    if (currentLineIndex >= lines.length) {
      setIsComplete(true);
      onComplete?.();
      return;
    }

    const currentLine = lines[currentLineIndex];
    const speed = currentLine.speed ?? 35;
    const delay = currentCharIndex === 0 ? (currentLine.delay ?? 0) : 0;

    const timer = setTimeout(() => {
      if (currentCharIndex < currentLine.text.length) {
        // Type next character
        setDisplayedLines((prev) => {
          const updated = [...prev];
          updated[currentLineIndex] = currentLine.text.slice(
            0,
            currentCharIndex + 1
          );
          return updated;
        });
        setCurrentCharIndex((i) => i + 1);
      } else {
        // Move to next line
        setCurrentLineIndex((i) => i + 1);
        setCurrentCharIndex(0);
      }
    }, currentCharIndex === 0 ? delay : speed);

    return () => clearTimeout(timer);
  }, [started, currentLineIndex, currentCharIndex, lines, isComplete, onComplete]);

  return { displayedLines, isComplete, currentLineIndex, reset };
}
