import { useMemo, useState } from "react";
import { ImageOff, Minus, Plus } from "lucide-react";
import { money } from "../utils/status";

/**
 * Selector de productos por categoría para armar un pedido (mesa o domicilio).
 * `draft` es { [menuItemId]: { qty, notes } }. Los agotados se muestran pero no se pueden pedir.
 */
export default function MenuPicker({ menu, draft, onChange }) {
  const categories = useMemo(() => {
    const names = [];
    for (const item of menu) if (!names.includes(item.category)) names.push(item.category);
    return names;
  }, [menu]);
  const [active, setActive] = useState("__all");
  const [query, setQuery] = useState("");

  const visible = menu.filter(
    (item) =>
      (active === "__all" || item.category === active) &&
      (!query || item.name.toLowerCase().includes(query.toLowerCase()))
  );

  const change = (item, delta) => {
    const current = draft[item.id]?.qty || 0;
    const qty = Math.max(0, Math.min(500, current + delta));
    const next = { ...draft };
    if (qty === 0) delete next[item.id];
    else next[item.id] = { qty, notes: draft[item.id]?.notes || "" };
    onChange(next);
  };

  return (
    <div>
      <input
        type="search"
        value={query}
        onChange={(e) => setQuery(e.target.value)}
        placeholder="Buscar producto..."
        className="field mb-2"
        aria-label="Buscar producto"
      />
      <div className="mb-3 flex gap-2 overflow-x-auto pb-1" role="tablist" aria-label="Categorías">
        {["__all", ...categories].map((name) => (
          <button
            key={name}
            type="button"
            role="tab"
            aria-selected={active === name}
            onClick={() => setActive(name)}
            className={`chip shrink-0 ${active === name ? "chip-pending" : "chip-muted"}`}
          >
            {name === "__all" ? "Todo" : name}
          </button>
        ))}
      </div>
      {visible.length === 0 ? (
        <p className="text-xs text-zinc-500">No hay productos en esta vista.</p>
      ) : (
        <div className="max-h-80 space-y-2 overflow-y-auto pr-1">
          {visible.map((item) => {
            const qty = draft[item.id]?.qty || 0;
            const soldOut = item.available === false;
            return (
              <div
                key={item.id}
                className={`flex items-center gap-3 border border-white/10 bg-white/[.03] p-2 ${soldOut ? "opacity-50" : ""}`}
              >
                {item.imageUrl ? (
                  <img src={item.imageUrl} alt="" loading="lazy" className="h-12 w-12 shrink-0 object-cover" />
                ) : (
                  <span className="grid h-12 w-12 shrink-0 place-items-center bg-white/[.04] text-zinc-500" aria-hidden="true">
                    <ImageOff className="h-4 w-4" />
                  </span>
                )}
                <div className="min-w-0 flex-1">
                  <p className="truncate text-sm text-champagne">{item.name}</p>
                  <p className="text-xs text-zinc-500">
                    {money(item.price)}
                    {item.prepMinutes ? ` · ~${item.prepMinutes} min` : ""}
                    {soldOut && <span className="ml-1 text-red-300">· Agotado</span>}
                  </p>
                </div>
                <div className="flex items-center gap-1.5">
                  <button
                    type="button"
                    className="grid h-9 w-9 place-items-center rounded-full border border-white/15 text-zinc-300 hover:border-gold/50"
                    onClick={() => change(item, -1)}
                    disabled={qty === 0}
                    aria-label={`Quitar ${item.name}`}
                  >
                    <Minus className="h-4 w-4" />
                  </button>
                  <span key={qty} className="motion-pop w-6 text-center text-sm text-champagne">
                    {qty}
                  </span>
                  <button
                    type="button"
                    className="grid h-9 w-9 place-items-center rounded-full border border-white/15 text-zinc-300 hover:border-gold/50"
                    onClick={() => change(item, 1)}
                    disabled={soldOut}
                    aria-label={`Agregar ${item.name}`}
                  >
                    <Plus className="h-4 w-4" />
                  </button>
                </div>
              </div>
            );
          })}
        </div>
      )}
    </div>
  );
}

/** Resumen editable del borrador: notas por plato (modificaciones) y total. */
export function DraftSummary({ menu, draft, onChange }) {
  const lines = Object.entries(draft)
    .map(([id, line]) => ({ item: menu.find((m) => String(m.id) === String(id)), ...line, id }))
    .filter((line) => line.item);
  const total = lines.reduce((sum, line) => sum + line.item.price * line.qty, 0);
  if (lines.length === 0) return null;
  return (
    <div className="mt-3 space-y-2 border-t border-white/10 pt-3">
      {lines.map((line) => (
        <div key={line.id} className="text-sm">
          <div className="flex justify-between gap-2">
            <span className="text-zinc-300">
              {line.qty}× {line.item.name}
            </span>
            <span className="text-zinc-400">{money(line.item.price * line.qty)}</span>
          </div>
          <input
            value={line.notes}
            maxLength={500}
            onChange={(e) => onChange({ ...draft, [line.id]: { ...draft[line.id], notes: e.target.value } })}
            placeholder="Modificación (sin cebolla, término medio...)"
            className="field mt-1 text-xs"
            aria-label={`Modificación para ${line.item.name}`}
          />
        </div>
      ))}
      <div className="flex items-center justify-between pt-1">
        <span className="text-sm text-zinc-400">Total</span>
        <span key={total} className="motion-pop text-lg font-semibold text-champagne">
          {money(total)}
        </span>
      </div>
    </div>
  );
}

export function draftToItems(draft) {
  return Object.entries(draft).map(([menuItemId, line]) => ({
    menuItemId: Number(menuItemId),
    quantity: line.qty,
    notes: line.notes?.trim() ? line.notes.trim() : undefined
  }));
}
