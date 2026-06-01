import { useState, useCallback, useEffect, useRef } from "react";
import { useSensors, useSensor, PointerSensor, type DragEndEvent } from "@dnd-kit/core";
import { arrayMove } from "@dnd-kit/sortable";

export type ReorderType = "stack" | "projects" | "experience";

interface WithOrder {
  id: string;
  order: number;
}

interface UseReorderOptions {
  type: ReorderType;
  toast: (message: string, variant: "success" | "error") => void;
}

export function useReorder<T extends WithOrder>(
  initial: T[],
  { type, toast }: UseReorderOptions
) {
  const [items, setItems] = useState<T[]>(initial);
  const [reorderingId, setReorderingId] = useState<string | null>(null);
  const reorderingRef = useRef(false);

  useEffect(() => {
    if (!reorderingRef.current) {
      setItems(initial);
    }
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [initial]);

  const sensors = useSensors(
    useSensor(PointerSensor, { activationConstraint: { distance: 5 } })
  );

  const handleDragEnd = useCallback(
    async (event: DragEndEvent) => {
      const { active, over } = event;
      if (!over || active.id === over.id) return;

      const sorted = [...items].sort((a, b) => a.order - b.order);
      const oldIdx = sorted.findIndex((item) => item.id === active.id);
      const newIdx = sorted.findIndex((item) => item.id === over.id);
      const reordered = arrayMove(sorted, oldIdx, newIdx);
      const updated = reordered.map((item, i) => ({ ...item, order: i + 1 }));

      setItems(updated);
      setReorderingId("drag");
      reorderingRef.current = true;

      try {
        const res = await fetch("/api/admin/reorder", {
          method: "PATCH",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({
            type,
            items: updated.map(({ id, order }) => ({ id, position: order })),
          }),
        });
        if (!res.ok) {
          setItems(sorted);
          toast("Failed to reorder", "error");
        }
      } catch {
        setItems(sorted);
        toast("Failed to reorder", "error");
      } finally {
        reorderingRef.current = false;
        setReorderingId(null);
      }
    },
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [items, type]
  );

  const handleReorder = useCallback(
    async (id: string, direction: "up" | "down") => {
      const sorted = [...items].sort((a, b) => a.order - b.order);
      const idx = sorted.findIndex((item) => item.id === id);
      const swapIdx = direction === "up" ? idx - 1 : idx + 1;
      if (swapIdx < 0 || swapIdx >= sorted.length) return;

      const updated = [...sorted];
      const tempOrder = updated[idx].order;
      updated[idx] = { ...updated[idx], order: updated[swapIdx].order };
      updated[swapIdx] = { ...updated[swapIdx], order: tempOrder };
      setItems(updated);
      setReorderingId(id);
      reorderingRef.current = true;

      try {
        const res = await fetch("/api/admin/reorder", {
          method: "PATCH",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({ type, id, direction }),
        });
        if (!res.ok) {
          setItems(sorted);
          toast("Failed to reorder", "error");
        }
      } catch {
        setItems(sorted);
        toast("Failed to reorder", "error");
      } finally {
        reorderingRef.current = false;
        setReorderingId(null);
      }
    },
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [items, type]
  );

  return { items, setItems, sensors, handleDragEnd, handleReorder, reorderingId };
}
