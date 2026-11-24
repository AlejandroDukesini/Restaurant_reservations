import { createContext, useCallback, useContext, useEffect, useRef, useState } from "react";
import { AlertTriangle, CheckCircle2, HelpCircle, X } from "lucide-react";

/*
 * Diálogo de confirmación de la app (reemplaza window.confirm / window.prompt).
 *
 *   const confirm = useConfirm();
 *   const result = await confirm({ title, message, confirmLabel, tone: "danger",
 *                                  input: { label: "Motivo", placeholder: "..." } });
 *   if (!result) return;          // canceló
 *   result.value                  // texto escrito (si se pidió)
 */

const ConfirmContext = createContext(null);

const TONES = {
  primary: { icon: CheckCircle2, ring: "var(--t-primary)" },
  danger: { icon: AlertTriangle, ring: "var(--t-danger)" },
  neutral: { icon: HelpCircle, ring: "var(--t-info)" }
};

function Dialog({ options, onResolve }) {
  const [value, setValue] = useState(options.input?.defaultValue || "");
  const [closing, setClosing] = useState(false);
  const confirmButton = useRef(null);
  const inputRef = useRef(null);
  const tone = TONES[options.tone] || TONES.primary;
  const Icon = tone.icon;

  const finish = useCallback(
    (result) => {
      if (closing) return;
      setClosing(true);
      const reduce = window.matchMedia?.("(prefers-reduced-motion: reduce)").matches;
      setTimeout(() => onResolve(result), reduce ? 0 : 140);
    },
    [closing, onResolve]
  );

  useEffect(() => {
    (options.input ? inputRef.current : confirmButton.current)?.focus();
    const onKey = (event) => {
      if (event.key === "Escape") finish(false);
    };
    document.addEventListener("keydown", onKey);
    return () => document.removeEventListener("keydown", onKey);
  }, [finish, options.input]);

  const state = closing ? " is-closing" : "";
  return (
    <div
      className={`motion-backdrop fixed inset-0 z-[60] grid place-items-center bg-black/60 p-4 backdrop-blur-sm${state}`}
      onMouseDown={() => finish(false)}
    >
      <form
        role="alertdialog"
        aria-modal="true"
        aria-labelledby="confirm-title"
        aria-describedby="confirm-message"
        onMouseDown={(e) => e.stopPropagation()}
        onSubmit={(e) => {
          e.preventDefault();
          finish({ value: value.trim() });
        }}
        className={`motion-dialog relative w-full max-w-sm overflow-hidden rounded-2xl border border-white/10 bg-graphite p-6 text-center shadow-2xl${state}`}
      >
        <button
          type="button"
          onClick={() => finish(false)}
          className="absolute right-3 top-3 grid h-9 w-9 place-items-center rounded-full text-zinc-400 hover:text-champagne"
          aria-label="Cerrar"
        >
          <X className="h-4 w-4" />
        </button>
        <span
          className="mx-auto grid h-14 w-14 place-items-center rounded-full"
          style={{ background: `color-mix(in oklab, ${tone.ring} 16%, transparent)`, color: tone.ring }}
          aria-hidden="true"
        >
          <Icon className="h-7 w-7" />
        </span>
        <h2 id="confirm-title" className="mt-4 text-lg font-semibold text-champagne">
          {options.title}
        </h2>
        {options.message && (
          <div id="confirm-message" className="mt-2 text-sm text-zinc-400">
            {options.message}
          </div>
        )}
        {options.highlight && (
          <p className="mt-3 text-3xl font-semibold" style={{ color: tone.ring }}>
            {options.highlight}
          </p>
        )}
        {options.input && (
          <label className="mt-4 block text-left text-xs text-zinc-400">
            {options.input.label}
            <textarea
              ref={inputRef}
              rows={2}
              maxLength={options.input.maxLength || 255}
              value={value}
              onChange={(e) => setValue(e.target.value)}
              placeholder={options.input.placeholder}
              className="field mt-1 rounded-lg"
            />
          </label>
        )}
        <div className="mt-6 grid grid-cols-2 gap-3">
          <button type="button" onClick={() => finish(false)} className="btn">
            {options.cancelLabel || "Cancelar"}
          </button>
          <button
            ref={confirmButton}
            type="submit"
            className={`btn btn-primary ${options.tone === "danger" ? "" : "bg-metal"}`}
            style={options.tone === "danger" ? { background: "var(--t-danger)", color: "#fff", borderColor: "transparent" } : undefined}
          >
            {options.confirmLabel || "Confirmar"}
          </button>
        </div>
      </form>
    </div>
  );
}

export function ConfirmProvider({ children }) {
  const [request, setRequest] = useState(null);

  const confirm = useCallback(
    (options) =>
      new Promise((resolve) => {
        setRequest({ options, resolve, id: `${Date.now()}-${Math.random()}` });
      }),
    []
  );

  const onResolve = useCallback(
    (result) => {
      request?.resolve(result);
      setRequest(null);
    },
    [request]
  );

  return (
    <ConfirmContext.Provider value={confirm}>
      {children}
      {request && <Dialog key={request.id} options={request.options} onResolve={onResolve} />}
    </ConfirmContext.Provider>
  );
}

/** Sin proveedor (p. ej. en pruebas aisladas) recurre al diálogo nativo. */
export function useConfirm() {
  const ctx = useContext(ConfirmContext);
  if (ctx) return ctx;
  return async (options) => {
    if (options.input) {
      const value = window.prompt(options.title);
      return value === null ? false : { value };
    }
    return window.confirm(options.title) ? { value: "" } : false;
  };
}
