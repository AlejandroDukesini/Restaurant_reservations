import { useCallback, useEffect, useState } from "react";
import { Bike, CheckCircle2, MapPin, Phone } from "lucide-react";
import Layout from "../components/Layout";
import { ErrorState, Loader } from "../components/LoadState";
import { completeDelivery, dispatchDelivery, fetchMyDeliveries } from "../api/opsApi";
import { useRealtime } from "../realtime/RealtimeProvider";
import { ORDER_STATUS, money, timeOf } from "../utils/status";

/** Vista del domiciliario: solo sus domicilios y solo los pasos de reparto. */
export default function DriverPage() {
  const [orders, setOrders] = useState(null);
  const [error, setError] = useState("");
  const [message, setMessage] = useState("");
  const [busy, setBusy] = useState(false);

  const load = useCallback(async () => {
    try {
      setOrders(await fetchMyDeliveries());
      setError("");
    } catch (err) {
      setError(err.message);
    }
  }, []);

  useEffect(() => {
    load();
  }, [load]);
  useRealtime(["deliveries", "notifications"], load);

  const run = async (action, success) => {
    setBusy(true);
    setMessage("");
    try {
      await action();
      setMessage(success);
      await load();
    } catch (err) {
      setMessage(err.message);
    } finally {
      setBusy(false);
    }
  };

  const active = (orders || []).filter((o) => !["DELIVERED", "CANCELLED"].includes(o.status));
  const done = (orders || []).filter((o) => ["DELIVERED", "CANCELLED"].includes(o.status));

  return (
    <Layout subtitle="Reparto" title="Mis entregas">
      {message && <p className="mb-4 text-sm text-zinc-300" role="status">{message}</p>}
      {!orders ? (
        error ? <ErrorState message={error} onRetry={load} /> : <Loader label="Cargando tus entregas..." />
      ) : active.length === 0 && done.length === 0 ? (
        <div className="zone-band py-12 text-center text-sm text-zinc-400">No tienes domicilios asignados.</div>
      ) : (
        <div className="space-y-4">
          {active.map((o) => {
            const s = ORDER_STATUS[o.status] || ORDER_STATUS.PENDING;
            return (
              <article key={o.id} className="zone-band space-y-2">
                <div className="flex items-start justify-between gap-2">
                  <div>
                    <p className="text-lg font-semibold text-champagne">{o.tableLabel}</p>
                    <p className="text-xs text-zinc-500">Asignado {timeOf(o.driverAssignedAt)} · {money(o.totalAmount)}</p>
                  </div>
                  <span className={`chip ${s.chip}`}>{s.label}</span>
                </div>
                {o.customerName && <p className="text-sm text-zinc-300">{o.customerName}</p>}
                {o.deliveryAddress && (
                  <a
                    className="flex items-center gap-2 text-sm text-gold underline"
                    href={`https://www.google.com/maps/search/?api=1&query=${encodeURIComponent(o.deliveryAddress)}`}
                    target="_blank"
                    rel="noreferrer noopener"
                  >
                    <MapPin className="h-4 w-4" /> {o.deliveryAddress}
                  </a>
                )}
                {o.customerPhone && (
                  <a className="flex items-center gap-2 text-sm text-gold" href={`tel:${o.customerPhone.replace(/[^\d+]/g, "")}`}>
                    <Phone className="h-4 w-4" /> {o.customerPhone}
                  </a>
                )}
                <p className="text-xs text-zinc-500">{o.items.map((i) => `${i.quantity}× ${i.itemName}`).join(", ")}</p>
                {o.notes && <p className="text-sm text-amber-300">{o.notes}</p>}
                <div className="flex gap-2 pt-1">
                  {o.status === "PACKED" && (
                    <button type="button" disabled={busy} onClick={() => run(() => dispatchDelivery(o.id), "Salida registrada.")} className="btn btn-primary bg-metal flex-1">
                      <Bike className="h-4 w-4" /> Salir a entregar
                    </button>
                  )}
                  {o.status === "ON_THE_WAY" && (
                    <button type="button" disabled={busy} onClick={() => run(() => completeDelivery(o.id), "Entrega registrada.")} className="btn btn-primary bg-metal flex-1">
                      <CheckCircle2 className="h-4 w-4" /> Marcar entregado
                    </button>
                  )}
                  {["PENDING", "PREPARING", "READY", "PACKING"].includes(o.status) && (
                    <p className="text-sm text-zinc-400">Aún en preparación o empaque. Te avisaremos cuando esté listo.</p>
                  )}
                </div>
              </article>
            );
          })}
          {done.length > 0 && (
            <section>
              <h3 className="mb-2 text-sm uppercase tracking-[.18em] text-gold">Cerrados recientemente</h3>
              <ul className="space-y-1 text-sm text-zinc-400">
                {done.map((o) => (
                  <li key={o.id}>
                    {o.tableLabel} · {(ORDER_STATUS[o.status] || {}).label} {timeOf(o.deliveredAt || o.cancelledAt)}
                  </li>
                ))}
              </ul>
            </section>
          )}
        </div>
      )}
    </Layout>
  );
}
