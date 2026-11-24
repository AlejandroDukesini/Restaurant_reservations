import { useCallback, useEffect, useRef, useState } from "react";
import { useConfirm } from "../components/ConfirmDialog";
import { Bike, CheckCircle2, Package, PackageCheck, Plus, XCircle } from "lucide-react";
import Layout from "../components/Layout";
import Modal from "../components/admin/Modal";
import MenuPicker, { DraftSummary, draftToItems } from "../components/MenuPicker";
import { ErrorState, Loader } from "../components/LoadState";
import { fetchMenu } from "../api/menuApi";
import {
  assignDriver,
  cancelOrder,
  completeDelivery,
  createDelivery,
  dispatchDelivery,
  fetchDeliveries,
  fetchDrivers,
  fetchPlatforms,
  finishPacking,
  startPacking
} from "../api/opsApi";
import { useAuth } from "../auth/AuthContext";
import { useRealtime } from "../realtime/RealtimeProvider";
import { ORDER_STATUS, minutesSince, money, newRequestKey, timeOf } from "../utils/status";

/** Columna del tablero según el estado (sin estados redundantes). */
export function deliveryColumn(order) {
  if (order.status === "CANCELLED" || order.status === "DELIVERED") return "done";
  if (order.status === "ON_THE_WAY") return "route";
  if (order.status === "PACKED") return order.driverId ? "assigned" : "waiting";
  if (order.status === "READY" || order.status === "PACKING") return "packing";
  return "kitchen";
}

const COLUMNS = [
  ["kitchen", "En cocina"],
  ["packing", "Empaque"],
  ["waiting", "Esperando domiciliario"],
  ["assigned", "Asignado"],
  ["route", "En camino"],
  ["done", "Cerrados (12 h)"]
];

const EMPTY_FORM = { customerName: "", customerPhone: "", deliveryAddress: "", externalRef: "", platformId: "", driverId: "", notes: "" };

export default function DeliveriesPage() {
  const { role } = useAuth();
  const [orders, setOrders] = useState(null);
  const [platforms, setPlatforms] = useState([]);
  const [drivers, setDrivers] = useState([]);
  const [menu, setMenu] = useState([]);
  const [error, setError] = useState("");
  const [message, setMessage] = useState("");
  const [busy, setBusy] = useState(false);
  const [creating, setCreating] = useState(false);
  const [form, setForm] = useState(EMPTY_FORM);
  const [draft, setDraft] = useState({});
  const requestKey = useRef(newRequestKey());
  const confirm = useConfirm();
  const now = Date.now();

  const load = useCallback(async () => {
    try {
      const [list, p, d] = await Promise.all([fetchDeliveries(), fetchPlatforms(), fetchDrivers()]);
      setOrders(list);
      setPlatforms(p);
      setDrivers(d);
      setError("");
    } catch (err) {
      setError(err.message);
    }
  }, []);

  useEffect(() => {
    load();
    fetchMenu().then(setMenu).catch(() => {});
  }, [load]);
  useRealtime(["deliveries", "orders"], load);
  useRealtime(["menu"], () => fetchMenu().then(setMenu).catch(() => {}));

  const run = async (action, success) => {
    setBusy(true);
    setMessage("");
    try {
      await action();
      if (success) setMessage(success);
      await load();
    } catch (err) {
      setMessage(err.message);
    } finally {
      setBusy(false);
    }
  };

  const submit = (event) => {
    event.preventDefault();
    run(async () => {
      await createDelivery({
        items: draftToItems(draft),
        customerName: form.customerName || undefined,
        customerPhone: form.customerPhone || undefined,
        deliveryAddress: form.deliveryAddress || undefined,
        externalRef: form.externalRef || undefined,
        notes: form.notes || undefined,
        platformId: form.platformId ? Number(form.platformId) : undefined,
        driverId: form.driverId ? Number(form.driverId) : undefined
      });
      setCreating(false);
      setForm(EMPTY_FORM);
      setDraft({});
      requestKey.current = newRequestKey();
    }, "Domicilio registrado y enviado a cocina.");
  };

  const activeDrivers = drivers.filter((d) => d.active);
  const set = (key) => (e) => setForm({ ...form, [key]: e.target.value });

  return (
    <Layout
      subtitle="Domicilios"
      title="Tablero de domicilios"
      actions={
        <button type="button" onClick={() => setCreating(true)} className="btn btn-primary bg-metal">
          <Plus className="h-4 w-4" /> Nuevo domicilio
        </button>
      }
    >
      {message && <p className="mb-4 text-sm text-zinc-300" role="status">{message}</p>}
      {!orders ? (
        error ? <ErrorState message={error} onRetry={load} /> : <Loader label="Cargando domicilios..." />
      ) : (
        <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-3">
          {COLUMNS.map(([key, label]) => {
            const list = orders.filter((o) => deliveryColumn(o) === key);
            return (
              <section key={key} className="zone-band">
                <h3 className="mb-3 text-sm uppercase tracking-[.18em] text-gold">
                  {label} · {list.length}
                </h3>
                <div className="space-y-3">
                  {list.length === 0 && <p className="text-xs text-zinc-500">Vacío.</p>}
                  {list.map((o) => {
                    const s = ORDER_STATUS[o.status] || ORDER_STATUS.PENDING;
                    return (
                      <article key={o.id} className="border border-white/10 bg-white/[.03] p-3 text-sm">
                        <div className="flex items-start justify-between gap-2">
                          <div className="min-w-0">
                            <p className="truncate font-semibold text-champagne">{o.tableLabel}</p>
                            <p className="text-xs text-zinc-500">
                              {timeOf(o.orderDate)} · {minutesSince(o.orderDate, now)} min · {money(o.totalAmount)}
                            </p>
                          </div>
                          <span className={`chip ${s.chip}`}>{s.label}</span>
                        </div>
                        {(o.customerName || o.deliveryAddress) && (
                          <p className="mt-1 text-xs text-zinc-400">
                            {o.customerName}
                            {o.deliveryAddress ? ` · ${o.deliveryAddress}` : ""}
                            {o.externalRef ? ` · Ref ${o.externalRef}` : ""}
                          </p>
                        )}
                        <p className="mt-1 text-xs text-zinc-500">
                          {o.items.map((i) => `${i.quantity}× ${i.itemName}`).join(", ")}
                        </p>
                        {key !== "done" && (
                          <div className="mt-2 flex flex-wrap items-center gap-2">
                            {o.status === "READY" && (
                              <button type="button" disabled={busy} onClick={() => run(() => startPacking(o.id))} className="btn btn-sm">
                                <Package className="h-4 w-4" /> Empacar
                              </button>
                            )}
                            {o.status === "PACKING" && (
                              <button type="button" disabled={busy} onClick={() => run(() => finishPacking(o.id))} className="btn btn-sm">
                                <PackageCheck className="h-4 w-4" /> Empacado
                              </button>
                            )}
                            {o.status !== "ON_THE_WAY" && (
                              <select
                                className="field btn-sm max-w-44"
                                value={o.driverId || ""}
                                disabled={busy}
                                onChange={(e) => run(() => assignDriver(o.id, e.target.value ? Number(e.target.value) : null), "Domiciliario actualizado.")}
                                aria-label="Domiciliario"
                              >
                                <option value="">Sin domiciliario</option>
                                {activeDrivers.map((d) => (
                                  <option key={d.id} value={d.id}>
                                    {d.name}
                                  </option>
                                ))}
                              </select>
                            )}
                            {o.status === "PACKED" && o.driverId && (
                              <button type="button" disabled={busy} onClick={() => run(() => dispatchDelivery(o.id))} className="btn btn-sm btn-primary">
                                <Bike className="h-4 w-4" /> Salió
                              </button>
                            )}
                            {o.status === "ON_THE_WAY" && (
                              <button type="button" disabled={busy} onClick={() => run(() => completeDelivery(o.id))} className="btn btn-sm btn-primary bg-metal">
                                <CheckCircle2 className="h-4 w-4" /> Entregado
                              </button>
                            )}
                            {(role === "ADMIN" || o.status === "PENDING") && (
                              <button
                                type="button"
                                disabled={busy}
                                onClick={async () => {
                                  const result = await confirm({
                                    title: `¿Cancelar ${o.tableLabel}?`,
                                    message: "Un pedido cancelado no cuenta como ingreso.",
                                    confirmLabel: "Cancelar domicilio",
                                    cancelLabel: "Volver",
                                    tone: "danger",
                                    input: { label: "Motivo (opcional)", placeholder: "Cliente canceló, sin repartidor..." }
                                  });
                                  if (result) run(() => cancelOrder(o.id, result.value), "Domicilio cancelado.");
                                }}
                                className="btn btn-sm btn-danger"
                              >
                                <XCircle className="h-4 w-4" />
                              </button>
                            )}
                          </div>
                        )}
                      </article>
                    );
                  })}
                </div>
              </section>
            );
          })}
        </div>
      )}

      {creating && (
        <Modal title="Nuevo domicilio" onClose={() => setCreating(false)}>
          <form onSubmit={submit} className="space-y-3">
            <div className="grid gap-3 sm:grid-cols-2">
              <label className="field-label">Cliente<input className="field" value={form.customerName} onChange={set("customerName")} maxLength={120} /></label>
              <label className="field-label">Teléfono<input className="field" value={form.customerPhone} onChange={set("customerPhone")} maxLength={40} inputMode="tel" /></label>
              <label className="field-label sm:col-span-2">Dirección<input className="field" value={form.deliveryAddress} onChange={set("deliveryAddress")} maxLength={255} /></label>
              <label className="field-label">
                Plataforma
                <select className="field" value={form.platformId} onChange={set("platformId")}>
                  <option value="">Sin plataforma</option>
                  {platforms.filter((p) => p.active).map((p) => (
                    <option key={p.id} value={p.id}>{p.name}</option>
                  ))}
                </select>
              </label>
              <label className="field-label">Referencia externa<input className="field" value={form.externalRef} onChange={set("externalRef")} maxLength={60} /></label>
              <label className="field-label sm:col-span-2">
                Domiciliario
                <select className="field" value={form.driverId} onChange={set("driverId")}>
                  <option value="">Asignar después</option>
                  {activeDrivers.map((d) => (
                    <option key={d.id} value={d.id}>{d.name}</option>
                  ))}
                </select>
              </label>
            </div>
            <MenuPicker menu={menu} draft={draft} onChange={setDraft} />
            <DraftSummary menu={menu} draft={draft} onChange={setDraft} />
            <textarea className="field" rows={2} value={form.notes} onChange={set("notes")} maxLength={1000} placeholder="Observaciones" />
            <button type="submit" disabled={busy || Object.keys(draft).length === 0} className="btn btn-primary bg-metal w-full">
              Registrar y enviar a cocina
            </button>
          </form>
        </Modal>
      )}
    </Layout>
  );
}
