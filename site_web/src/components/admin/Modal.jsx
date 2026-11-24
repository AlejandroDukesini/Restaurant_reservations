import { useEffect, useRef, useState } from "react";
import { X } from "lucide-react";

// Debe coincidir con --motion-fast (duración de la animación de salida).
const CLOSE_MS = 140;

export default function Modal({ title, onClose, children, wide = false }) {
  const [closing, setClosing] = useState(false);

  // Reproduce la salida antes de desmontar; sin animación si se pide movimiento reducido.
  const requestClose = () => {
    if (closing) return;
    if (window.matchMedia?.("(prefers-reduced-motion: reduce)").matches) {
      onClose();
      return;
    }
    setClosing(true);
    setTimeout(onClose, CLOSE_MS);
  };

  // Escape cierra el diálogo (atajo de teclado en Windows).
  const closeRef = useRef(requestClose);
  closeRef.current = requestClose;
  useEffect(() => {
    const onKey = (event) => event.key === "Escape" && closeRef.current();
    document.addEventListener("keydown", onKey);
    return () => document.removeEventListener("keydown", onKey);
  }, []);

  const state = closing ? " is-closing" : "";

  return (
    <div
      className={`motion-backdrop fixed inset-0 z-50 grid place-items-center bg-black/70 p-4${state}`}
      onMouseDown={requestClose}
    >
      <div
        className={`motion-dialog max-h-[92vh] w-full overflow-y-auto border border-white/10 bg-graphite p-5 shadow-gold sm:p-6 ${wide ? "max-w-3xl" : "max-w-lg"}${state}`}
        role="dialog"
        aria-modal="true"
        aria-label={title}
        onMouseDown={(e) => e.stopPropagation()}
      >
        <div className="mb-4 flex items-center justify-between border-b border-white/10 pb-3">
          <h3 className="text-lg font-semibold text-champagne">{title}</h3>
          <button type="button" onClick={requestClose} className="grid h-10 w-10 place-items-center text-zinc-400 hover:text-champagne" aria-label="Cerrar">
            <X className="h-5 w-5" />
          </button>
        </div>
        {children}
      </div>
    </div>
  );
}
