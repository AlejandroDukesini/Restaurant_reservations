import { X } from "lucide-react";

export default function Modal({ title, onClose, children }) {
  return (
    <div
      className="fixed inset-0 z-50 grid place-items-center bg-black/70 p-4"
      onMouseDown={onClose}
    >
      <div
        className="w-full max-w-lg border border-white/10 bg-graphite p-6 shadow-gold"
        onMouseDown={(e) => e.stopPropagation()}
      >
        <div className="mb-4 flex items-center justify-between border-b border-white/10 pb-3">
          <h3 className="text-lg font-semibold text-champagne">{title}</h3>
          <button type="button" onClick={onClose} className="text-zinc-400 hover:text-champagne">
            <X className="h-5 w-5" />
          </button>
        </div>
        {children}
      </div>
    </div>
  );
}
