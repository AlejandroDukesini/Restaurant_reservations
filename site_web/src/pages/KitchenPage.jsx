import { useCallback, useEffect, useState } from "react";
import { CheckCircle2, Flame, RefreshCw, Soup } from "lucide-react";
import Layout from "../components/Layout";
import { fetchKitchenQueue, updateItemStatus } from "../api/orderApi";
import { CATEGORY_LABEL, ITEM_STATUS } from "../utils/status";

function RecipeRow({ label, value }) {
  if (!value) return null;
  return (
    <p className="text-xs text-zinc-400">
      <span className="text-gold">{label}:</span> {value}
    </p>
  );
}

export default function KitchenPage() {
  const [queue, setQueue] = useState([]);
  const [status, setStatus] = useState("");
  const [loading, setLoading] = useState(false);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const data = await fetchKitchenQueue();
      setQueue(data);
      setStatus("");
    } catch (err) {
      setStatus(err.message);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    load();
    const timer = setInterval(load, 15000); // refresco automático de la cola
    return () => clearInterval(timer);
  }, [load]);

  const setItemStatus = async (orderItemId, next) => {
    try {
      await updateItemStatus(orderItemId, next);
      await load();
    } catch (err) {
      setStatus(err.message);
    }
  };

  const refreshBtn = (
    <button
      type="button"
      onClick={load}
      className="inline-flex items-center gap-2 rounded-full border border-gold/40 px-4 py-2 text-champagne transition hover:border-gold/70"
    >
      <RefreshCw className={`h-4 w-4 ${loading ? "animate-spin" : ""}`} /> Actualizar
    </button>
  );

  return (
    <Layout subtitle="Cola de cocina" title="Cocina · Platos por preparar" actions={refreshBtn}>
      {status && <p className="mb-4 text-sm text-red-300">{status}</p>}

      {queue.length === 0 ? (
        <div className="zone-band grid place-items-center py-16 text-center">
          <Soup className="h-10 w-10 text-gold" />
          <p className="mt-3 text-lg text-champagne">No hay platos pendientes</p>
          <p className="text-sm text-zinc-500">La cocina está al día.</p>
        </div>
      ) : (
        <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-3">
          {queue.map((item) => {
            const is = ITEM_STATUS[item.status] || ITEM_STATUS.PENDING;
            return (
              <article key={item.orderItemId} className="zone-band flex flex-col justify-between">
                <div>
                  <div className="mb-3 flex items-start justify-between gap-2">
                    <div>
                      <p className="text-xs uppercase tracking-[.18em] text-gold">
                        {item.tableName || `Mesa ${item.tableNumber}`} · #{item.tableNumber}
                      </p>
                      <h3 className="text-lg font-semibold text-champagne">
                        {item.quantity}× {item.itemName}
                      </h3>
                      <p className="text-xs text-zinc-500">
                        {CATEGORY_LABEL[item.category] || item.category} · {item.employeeName}
                      </p>
                    </div>
                    <span className={`chip ${is.chip}`}>{is.label}</span>
                  </div>

                  <div className="space-y-1 border-t border-white/10 pt-3">
                    <RecipeRow label="Proteína" value={item.protein} />
                    <RecipeRow label="Condimentos" value={item.condiments} />
                    <RecipeRow label="Ingredientes" value={item.ingredients} />
                    <RecipeRow label="Preparación" value={item.preparationNotes} />
                    {item.notes && <RecipeRow label="Nota mesa" value={item.notes} />}
                  </div>
                </div>

                <div className="mt-4 flex gap-2">
                  <button
                    type="button"
                    onClick={() => setItemStatus(item.orderItemId, "PREPARING")}
                    className="inline-flex flex-1 items-center justify-center gap-2 rounded-full border border-white/15 px-3 py-2 text-sm text-zinc-200 transition hover:border-gold/50"
                  >
                    <Flame className="h-4 w-4" /> Preparando
                  </button>
                  <button
                    type="button"
                    onClick={() => setItemStatus(item.orderItemId, "READY")}
                    className="inline-flex flex-1 items-center justify-center gap-2 rounded-full border border-gold/60 bg-metal px-3 py-2 text-sm font-semibold text-carbon shadow-gold transition hover:brightness-110"
                  >
                    <CheckCircle2 className="h-4 w-4" /> Listo
                  </button>
                </div>
              </article>
            );
          })}
        </div>
      )}
    </Layout>
  );
}
