export interface Env {
  AUDIO_STORE: DurableObjectNamespace;
  POKE_INGEST_ENDPOINT_ID?: string;
  POKE_INGEST_TOKEN?: string;
  POKE_INGEST_URL?: string;
  POKE_API_KEY?: string;
  APP_UPLOAD_TOKEN: string;
  URL_SIGNING_SECRET: string;
  PUBLIC_BASE_URL?: string;
  AUDIO_TTL_SECONDS?: string;
  MAX_AUDIO_BYTES?: string;
  POKE_API_URL?: string;
}
