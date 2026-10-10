import { AlertTriangle, RotateCw } from "lucide-react";

// Indicador de carga. Aparece con un pequeño retardo (CSS) para no parpadear en cargas rápidas.
export function Loader({ label = "Cargando...", className = "" }) {
  return (
    <div
      role="status"
      aria-live="polite"
      className={`loader-delayed flex flex-col items-center justify-center gap-3 py-16 text-sm text-zinc-400 ${className}`}
    >
      <span className="loader-ring" aria-hidden="true" />
      {label}
    </div>
  );
}

// Error de carga con opción de reintentar; `children` permite acciones extra.
export function ErrorState({ message, onRetry, retrying = false, children }) {
  return (
    <div role="alert" className="motion-fade zone-band flex flex-col items-center gap-3 py-10 text-center">
      <AlertTriangle className="h-8 w-8 text-gold" aria-hidden="true" />
      <p className="max-w-sm text-sm text-red-300">{message}</p>
      <div className="flex flex-wrap justify-center gap-2">
        {onRetry && (
          <button
            type="button"
            onClick={onRetry}
            disabled={retrying}
            className="inline-flex items-center gap-2 rounded-full border border-gold/40 px-4 py-2 text-sm text-champagne transition hover:border-gold/70 disabled:opacity-60"
          >
            <RotateCw className={`h-4 w-4 ${retrying ? "animate-spin" : ""}`} />
            {retrying ? "Reintentando..." : "Reintentar"}
          </button>
        )}
        {children}
      </div>
    </div>
  );
}
