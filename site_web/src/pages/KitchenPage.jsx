import { useCallback, useEffect, useState } from "react";
import { BellRing, CheckCircle2, Flame, Package, PackageCheck, RefreshCw, Soup } from "lucide-react";
import Layout from "../components/Layout";
import { ErrorState, Loader } from "../components/LoadState";
import {
  callWaiter,
  fetchKitchenBoard,
  finishPacking,
  readyKitchenOrder,
  setKitchenItem,
  startKitchenOrder,
  startPacking
} from "../api/opsApi";
import { useRealtime } from "../realtime/RealtimeProvider";
import { ITEM_STATUS, ORDER_STATUS, minutesSince, timeOf } from "../utils/status";

function RecipeRow({ label, value }) {
  if (!value) return null;
  return (
    <p className="text-xs text-zinc-400">
      <span className="text-gold">{label}:</span> {value}
    </p>
  );
}

function OrderCard({ order, now, onAction, busy }) {
  const elapsed = minutesSince(order.orderDate, now);
  const inKitchen = ["PENDING", "PREPARING"].includes(order.status);
  const late = inKitchen && order.estimatedMinutes && elapsed > order.estimatedMinutes;
  const s = ORDER_STATUS[order.status] || ORDER_STATUS.PENDING;
  const delivery = order.channel === "DELIVERY";

  return (
    <article className={`zone-band flex flex-col gap-3 ${late ? "border-red-400/60" : ""}`} aria-label={`Pedido ${order.orderId}`}>
      <header className="flex items-start justify-between gap-2">
        <div className="min-w-0">
          <p className="text-xs uppercase tracking-[.16em] text-gold">{delivery ? "Domicilio" : "Mesa"}</p>
          <h3 className="truncate text-lg font-semibold text-champagne">{order.label}</h3>
          <p className="text-xs text-zinc-500">
            #{order.orderId} · {order.employeeName} · entró {timeOf(order.orderDate)}
          </p>
        </div>
        <div className="flex flex-col items-end gap-1">
          <span className={`chip ${s.chip}`}>{s.label}</span>
          {inKitchen && (
            <span className={`text-sm ${late ? "is-late" : "text-zinc-300"}`} title="Transcurrido / estimado">
              {elapsed} min{order.estimatedMinutes ? ` / ${order.estimatedMinutes}` : ""}
            </span>
          )}
        </div>
      </header>

      {order.notes && <p className="border-l-2 border-gold/60 pl-2 text-sm text-champagne">{order.notes}</p>}

      <ul className="space-y-2">
        {order.items.map((item) => {
          const is = ITEM_STATUS[item.status] || ITEM_STATUS.PENDING;
          return (
            <li key={item.orderItemId} className="border-t border-white/10 pt-2">
              <div className="flex items-start justify-between gap-2">
                <p className="text-sm text-champagne">
                  <span className="font-semibold">{item.quantity}×</span> {item.itemName}
                  {item.categoryName && <span className="ml-1 text-xs text-zinc-500">· {item.categoryName}</span>}
                </p>
                <span className={`chip ${is.chip}`}>{is.label}</span>
              </div>
              {item.notes && <p className="text-sm font-medium text-amber-300">⚠ {item.notes}</p>}
              <RecipeRow label="Proteína" value={item.protein} />
              <RecipeRow label="Condimentos" value={item.condiments} />
              <RecipeRow label="Ingredientes" value={item.ingredients} />
              <RecipeRow label="Preparación" value={item.preparationNotes} />
              {inKitchen && item.status !== "READY" && (
                <div className="mt-1 flex gap-2">
                  {item.status === "PENDING" && (
                    <button type="button" disabled={busy} onClick={() => onAction(() => setKitchenItem(item.orderItemId, "PREPARING"))} className="btn btn-sm">
                      <Flame className="h-4 w-4" /> Preparando
                    </button>
                  )}
                  <button type="button" disabled={busy} onClick={() => onAction(() => setKitchenItem(item.orderItemId, "READY"))} className="btn btn-sm">
                    <CheckCircle2 className="h-4 w-4" /> Listo
                  </button>
                </div>
              )}
            </li>
          );
        })}
      </ul>

      <footer className="mt-auto flex flex-wrap gap-2 border-t border-white/10 pt-3">
        {order.status === "PENDING" && (
          <button type="button" disabled={busy} onClick={() => onAction(() => startKitchenOrder(order.orderId))} className="btn btn-sm">
            <Flame className="h-4 w-4" /> Empezar todo
          </button>
        )}
        {inKitchen && (
          <button type="button" disabled={busy} onClick={() => onAction(() => readyKitchenOrder(order.orderId))} className="btn btn-sm btn-primary bg-metal">
            <CheckCircle2 className="h-4 w-4" /> Pedido listo
          </button>
        )}
        {delivery && order.status === "READY" && (
          <button type="button" disabled={busy} onClick={() => onAction(() => startPacking(order.orderId))} className="btn btn-sm btn-primary">
            <Package className="h-4 w-4" /> Empezar empaque
          </button>
        )}
        {delivery && order.status === "PACKING" && (
          <button type="button" disabled={busy} onClick={() => onAction(() => finishPacking(order.orderId))} className="btn btn-sm btn-primary bg-metal">
            <PackageCheck className="h-4 w-4" /> Empacado
          </button>
        )}
        {!delivery && (
          <button type="button" disabled={busy} onClick={() => onAction(() => callWaiter(order.orderId), "Se avisó al mesero.")} className="btn btn-sm">
            <BellRing className="h-4 w-4" /> Llamar mesero
          </button>
        )}
      </footer>
    </article>
  );
}

export default function KitchenPage() {
  const [board, setBoard] = useState(null);
  const [error, setError] = useState("");
  const [message, setMessage] = useState("");
  const [busy, setBusy] = useState(false);
  const [now, setNow] = useState(Date.now());

  const load = useCallback(async () => {
    try {
      setBoard(await fetchKitchenBoard());
      setError("");
    } catch (err) {
      setError(err.message);
    }
  }, []);

  useEffect(() => {
    load();
    const tick = setInterval(() => setNow(Date.now()), 30000);
    return () => clearInterval(tick);
  }, [load]);

  useRealtime(["orders", "deliveries"], load);

  const onAction = async (action, success) => {
    setBusy(true);
    setMessage("");
    try {
      await action();
      if (success) setMessage(success);
      await load();
    } catch (err) {
      setMessage(err.message);
      await load();
    } finally {
      setBusy(false);
    }
  };

  const cooking = (board || []).filter((o) => ["PENDING", "PREPARING"].includes(o.status));
  const ready = (board || []).filter((o) => o.status === "READY" && o.channel === "DINE_IN");
  const packing = (board || []).filter((o) => o.channel === "DELIVERY" && ["READY", "PACKING"].includes(o.status));

  const refresh = (
    <button type="button" onClick={load} className="btn">
      <RefreshCw className="h-4 w-4" /> Actualizar
    </button>
  );

  const Section = ({ title, orders, empty }) => (
    <section className="mb-8">
      <h3 className="mb-3 text-sm uppercase tracking-[.2em] text-gold">
        {title} · {orders.length}
      </h3>
      {orders.length === 0 ? (
        <p className="text-sm text-zinc-500">{empty}</p>
      ) : (
        <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-3">
          {orders.map((order) => (
            <OrderCard key={order.orderId} order={order} now={now} onAction={onAction} busy={busy} />
          ))}
        </div>
      )}
    </section>
  );

  return (
    <Layout subtitle="Cocina" title="Pedidos en cocina" actions={refresh}>
      {message && <p className="motion-fade mb-4 text-sm text-zinc-300" role="status">{message}</p>}
      {!board ? (
        error ? <ErrorState message={error} onRetry={load} /> : <Loader label="Cargando la cocina..." />
      ) : board.length === 0 ? (
        <div className="zone-band grid place-items-center py-16 text-center">
          <Soup className="h-10 w-10 text-gold" />
          <p className="mt-3 text-lg text-champagne">No hay pedidos pendientes</p>
          <p className="text-sm text-zinc-500">La cocina está al día.</p>
        </div>
      ) : (
        <>
          <Section title="Por preparar" orders={cooking} empty="Nada por preparar." />
          <Section title="Listos para recoger" orders={ready} empty="Ningún pedido esperando al mesero." />
          <Section title="Domicilios por empacar" orders={packing} empty="Ningún domicilio por empacar." />
        </>
      )}
    </Layout>
  );
}
