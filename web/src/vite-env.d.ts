/// <reference types="vite/client" />

interface ImportMetaEnv {
  readonly VITE_PRAVAH_API_BASE?: string;
  readonly VITE_DEV_PROXY_TARGET?: string;
  readonly VITE_PRAVAH_PROJECT_ID?: string;
}

interface ImportMeta {
  readonly env: ImportMetaEnv;
}
