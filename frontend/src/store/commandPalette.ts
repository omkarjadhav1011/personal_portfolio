import { create } from "zustand";

export type PaletteMode = "terminal" | "ai";

interface CommandPaletteStore {
  open: boolean;
  mode: PaletteMode;
  setOpen: (open: boolean) => void;
  openInMode: (mode: PaletteMode) => void;
  setMode: (mode: PaletteMode) => void;
  toggle: () => void;
}

export const useCommandPaletteStore = create<CommandPaletteStore>((set, get) => ({
  open: false,
  mode: "terminal",
  setOpen: (open) => set({ open }),
  openInMode: (mode) => set({ open: true, mode }),
  setMode: (mode) => set({ mode }),
  toggle: () => set({ open: !get().open }),
}));
