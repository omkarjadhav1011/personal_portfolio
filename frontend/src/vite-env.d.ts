/// <reference types="vite/client" />

interface ImportMetaEnv {
  /** Base URL of the backend API. Empty in dev (relative paths use the Vite proxy). */
  readonly VITE_API_URL?: string;
}

interface ImportMeta {
  readonly env: ImportMetaEnv;
}
