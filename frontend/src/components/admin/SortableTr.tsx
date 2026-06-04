
import React, { createContext, useContext } from "react";
import { useSortable } from "@dnd-kit/sortable";
import { CSS } from "@dnd-kit/utilities";
import { GripVertical, ChevronUp, ChevronDown } from "lucide-react";
import { motion } from "framer-motion";

interface DragRowContextValue {
  listeners: ReturnType<typeof useSortable>["listeners"];
  attributes: ReturnType<typeof useSortable>["attributes"];
  isDragging: boolean;
  disabled: boolean;
}

const DragRowContext = createContext<DragRowContextValue | null>(null);

interface SortableTrProps {
  id: string;
  disabled?: boolean;
  children: React.ReactNode;
  className?: string;
}

export function SortableTr({ id, disabled = false, children, className }: SortableTrProps) {
  const { attributes, listeners, setNodeRef, transform, transition, isDragging } = useSortable({
    id,
    disabled,
  });

  const style: React.CSSProperties = {
    transform: CSS.Transform.toString(transform),
    transition,
    opacity: isDragging ? 0.5 : 1,
    zIndex: isDragging ? 10 : undefined,
    position: isDragging ? "relative" : undefined,
  };

  return (
    <DragRowContext.Provider value={{ listeners, attributes, isDragging, disabled }}>
      <motion.tr
        ref={setNodeRef}
        style={style}
        layout
        layoutId={id}
        initial={false}
        animate={{ opacity: isDragging ? 0.5 : 1 }}
        exit={{ opacity: 0 }}
        transition={{ layout: { type: "spring", stiffness: 500, damping: 35 } }}
        className={
          className ??
          "border-b border-terminal-border/50 hover:bg-terminal-bg/40 transition-colors"
        }
      >
        {children}
      </motion.tr>
    </DragRowContext.Provider>
  );
}

export function DragHandleCell() {
  const ctx = useContext(DragRowContext);
  if (!ctx) return null;
  const { listeners, attributes, disabled } = ctx;

  return (
    <td className="px-2 py-2.5 text-center w-6">
      <button
        {...attributes}
        {...listeners}
        disabled={disabled}
        className={`p-0.5 rounded text-text-faint transition-colors ${
          disabled
            ? "opacity-30 cursor-not-allowed"
            : "cursor-grab hover:text-text-muted active:cursor-grabbing"
        }`}
        title={disabled ? "Clear search to drag" : "Drag to reorder"}
      >
        <GripVertical size={14} />
      </button>
    </td>
  );
}

interface ReorderButtonsCellProps {
  id: string;
  isFirst: boolean;
  isLast: boolean;
  reorderingId: string | null;
  onReorder: (id: string, direction: "up" | "down") => void;
}

export function ReorderButtonsCell({
  id,
  isFirst,
  isLast,
  reorderingId,
  onReorder,
}: ReorderButtonsCellProps) {
  return (
    <td className="px-3 py-2.5 text-center">
      <div className="inline-flex gap-1">
        <button
          onClick={() => onReorder(id, "up")}
          disabled={isFirst || reorderingId !== null}
          className="p-0.5 rounded hover:bg-terminal-bg disabled:opacity-30 disabled:cursor-not-allowed text-text-muted hover:text-text-primary transition-colors"
          title="Move up"
        >
          <ChevronUp size={14} />
        </button>
        <button
          onClick={() => onReorder(id, "down")}
          disabled={isLast || reorderingId !== null}
          className="p-0.5 rounded hover:bg-terminal-bg disabled:opacity-30 disabled:cursor-not-allowed text-text-muted hover:text-text-primary transition-colors"
          title="Move down"
        >
          <ChevronDown size={14} />
        </button>
      </div>
    </td>
  );
}
