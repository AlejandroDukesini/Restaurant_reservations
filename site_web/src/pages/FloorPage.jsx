import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { useConfirm } from "../components/ConfirmDialog";
import { useSearchParams } from "react-router-dom";
import { BellRing, CheckCircle2, Clock, HandPlatter, Receipt, Send, Users, X, XCircle } from "lucide-react";
import Layout from "../components/Layout";
import MenuPicker, { DraftSummary, draftToItems } from "../components/MenuPicker";
import { ErrorState, Loader } from "../components/LoadState";
import { fetchMenu } from "../api/menuApi";
import {
  cancelOrder,
  clearAttention,
  closeTable,
  createTableOrder,
  deliverOrder,
  fetchFloor,
  fetchOrder,
  fetchSession,
  openTable,
  requestAttention,
  requestBill
} from "../api/opsApi";
import { useAuth } from "../auth/AuthContext";
import { useRealtime } from "../realtime/RealtimeProvider";
import { ITEM_STATUS, ORDER_STATUS, TABLE_STATE, minutesSince, money, newRequestKey, timeOf } from "../utils/status";

const SUMMARY = [
  ["AVAILABLE", "Disponibles"],
  ["OCCUPIED", "Ocupadas"],
  ["SENT", "En cocina"],
  ["PREPARING", "Preparando"],
  ["READY", "Listos"],
  ["ATTENTION", "Atención"],
  ["PAYMENT_PENDING", "Por cobrar"]
];

function useNow(intervalMs = 30000) {
  const [now, setNow] = useState(Date.now());
  useEffect(() => {
    const t = setInterval(() => setNow(Date.now()), intervalMs);
    return () => clearInterval(t);
  }, [intervalMs]);
  return now;
}

function TableCard({ table, selected, onSelect, now }) {
  const waiting = minutesSince(table.oldestActiveOrderAt, now);
  const seated = minutesSince(table.openedAt, now);
  return (
    <button
      type="button"
      onClick={() => onSelect(table.id)}
      aria-pressed={selected}
      className={`live-card live-${table.liveState} card-lift flex min-h-28 flex-col justify-between p-3 pl-4 text-left ${selected ? "ring-2 ring-gold" : ""}`}
    >
      <div className="flex items-start justify-between gap-2">
        <div className="min-w-0">
          <p className="truncate font-semibold text-champagne">{table.displayName}</p>
          <p className="truncate text-xs text-zinc-500">
            #{table.tableNumber} · {table.capacity} pers{table.zoneName ? ` · ${table.zoneName}` : ""}
          </p>
        </div>
        {table.readyOrders > 0 && (
          <span className="chip chip-ready" title="Pedidos listos para recoger">
            {table.readyOrders} listo
          </span>
        )}
      </div>
      <div className="mt-2 flex items-center gap-2 text-xs text-zinc-300">
        <span className="live-dot" aria-hidden="true" />
        <span>{TABLE_STATE[table.liveState] || table.liveState}</span>
      </div>
      {table.sessionId && (
        <div className="mt-1 flex flex-wrap gap-x-3 text-[11px] text-zinc-500">
          {table.waiterName && <span>{table.waiterName}</span>}
          {seated !== null && <span>Mesa {seated} min</span>}
          {waiting !== null && <span>Pedido {waiting} min</span>}
          {table.sessionTotal > 0 && <span>{money(table.sessionTotal)}</span>}
        </div>
      )}
    </button>
  );
}

export default function FloorPage() {
  const { userId, role } = useAuth();
  const [params, setParams] = useSearchParams();
  const [tables, setTables] = useState(null);
  const [error, setError] = useState("");
  const [menu, setMenu] = useState(null);
  const [selectedId, setSelectedId] = useState(null);
  const [session, setSession] = useState(null);
  const [draft, setDraft] = useState({});
  const [notes, setNotes] = useState("");
  const [guests, setGuests] = useState("");
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState("");
  const [filter, setFilter] = useState(null);
  const requestKey = useRef(newRequestKey());
  const confirm = useConfirm();
  const now = useNow();

  const selected = useMemo(() => tables?.find((t) => t.id === selectedId) || null, [tables, selectedId]);

  const loadTables = useCallback(async () => {
    try {
      setTables(await fetchFloor());
      setError("");
    } catch (err) {
      setError(err.message);
    }
  }, []);

  const loadMenu = useCallback(async () => {
    try {
      setMenu(await fetchMenu());
    } catch {
      // El panel muestra el aviso al intentar pedir.
    }
  }, []);

  const loadSession = useCallback(async (tableId) => {
    if (!tableId) return setSession(null);
    try {
      setSession(await fetchSession(tableId));
    } catch {
      setSession(null); // Mesa libre (404): sin atención abierta.
    }
  }, []);

  useEffect(() => {
    loadTables();
    loadMenu();
  }, [loadTables, loadMenu]);

  useEffect(() => {
    setDraft({});
    setNotes("");
    setMessage("");
    requestKey.current = newRequestKey();
    loadSession(selectedId);
  }, [selectedId, loadSession]);

  // Enlaces desde notificaciones y mensajes: ?mesa=ID o ?pedido=ID.
  useEffect(() => {
    const mesa = params.get("mesa");
    const pedido = params.get("pedido");
    if (mesa) setSelectedId(Number(mesa));
    else if (pedido) {
      fetchOrder(pedido)
        .then((order) => order.tableId && setSelectedId(order.tableId))
        .catch(() => {});
    }
  }, [params]);

  useRealtime(["tables", "orders"], () => {
    loadTables();
    if (selectedId) loadSession(selectedId);
  });
  useRealtime(["menu"], loadMenu);

  const run = async (action, success) => {
    setBusy(true);
    setMessage("");
    try {
      await action();
      if (success) setMessage(success);
      await Promise.all([loadTables(), loadSession(selectedId)]);
    } catch (err) {
      setMessage(err.message);
    } finally {
      setBusy(false);
    }
  };

  const submitOrder = () =>
    run(async () => {
      await createTableOrder(
        { tableId: selectedId, items: draftToItems(draft), notes: notes.trim() || undefined },
        requestKey.current
      );
      setDraft({});
      setNotes("");
      requestKey.current = newRequestKey();
    }, "Pedido enviado a cocina.");

  const closeSelection = () => {
    setSelectedId(null);
    if (params.get("mesa") || params.get("pedido")) setParams({}, { replace: true });
  };

  const counts = useMemo(() => {
    const c = {};
    for (const t of tables || []) c[t.liveState] = (c[t.liveState] || 0) + 1;
    return c;
  }, [tables]);

  const visibleTables = (tables || []).filter((t) => !filter || t.liveState === filter);
  const hasDraft = Object.keys(draft).length > 0;
  const orders = session?.orders || [];

  return (
    <Layout subtitle="Operación de sala" title="Mesas">
      {tables && (
        <div className="mb-4 flex gap-2 overflow-x-auto pb-1" aria-label="Resumen de mesas">
          <button type="button" onClick={() => setFilter(null)} className={`chip shrink-0 ${!filter ? "chip-pending" : "chip-muted"}`}>
            Todas · {tables.length}
          </button>
          {SUMMARY.map(([state, label]) => (
            <button
              key={state}
              type="button"
              onClick={() => setFilter(filter === state ? null : state)}
              className={`chip shrink-0 live-${state} ${filter === state ? "chip-pending" : "chip-muted"}`}
            >
              <span className="live-dot" aria-hidden="true" /> {label} · {counts[state] || 0}
            </button>
          ))}
        </div>
      )}

      <div className="grid gap-6 lg:grid-cols-[1fr_400px]">
        {tables ? (
          visibleTables.length === 0 ? (
            <div className="zone-band py-12 text-center text-sm text-zinc-400">No hay mesas en esta vista.</div>
          ) : (
            <div className="grid content-start gap-3 sm:grid-cols-2 xl:grid-cols-3">
              {visibleTables.map((table) => (
                <TableCard key={table.id} table={table} selected={table.id === selectedId} onSelect={setSelectedId} now={now} />
              ))}
            </div>
          )
        ) : error ? (
          <ErrorState message={error} onRetry={loadTables} />
        ) : (
          <Loader label="Cargando mesas..." />
        )}

        {/* Panel de la mesa: lateral en escritorio, pantalla completa en móvil. */}
        <aside
          className={`${selected ? "fixed inset-0 z-40 overflow-y-auto bg-carbon p-4 pb-24 lg:static lg:z-auto lg:bg-transparent lg:p-0 lg:pb-0" : "hidden lg:block"}`}
        >
          <div className="zone-band lg:sticky lg:top-24">
            {!selected ? (
              <p className="text-sm text-zinc-400">Selecciona una mesa para atenderla.</p>
            ) : (
              <div key={selected.id} className="motion-fade space-y-4">
                <div className="flex items-start justify-between gap-2 border-b border-white/10 pb-3">
                  <div>
                    <p className={`flex items-center gap-2 text-xs uppercase tracking-[.18em] text-gold live-${selected.liveState}`}>
                      <span className="live-dot" /> {TABLE_STATE[selected.liveState]}
                    </p>
                    <h3 className="mt-1 text-2xl font-semibold text-champagne">{selected.displayName}</h3>
                    <p className="text-sm text-zinc-400">
                      #{selected.tableNumber} · {selected.capacity} personas
                      {selected.groupName ? ` · ${selected.groupName}` : ""}
                    </p>
                  </div>
                  <button type="button" onClick={closeSelection} className="grid h-10 w-10 place-items-center text-zinc-400 hover:text-champagne" aria-label="Cerrar panel">
                    <X className="h-5 w-5" />
                  </button>
                </div>

                {message && <p className="text-sm text-zinc-300" role="status">{message}</p>}

                {selected.liveState === "MAINTENANCE" ? (
                  <p className="text-sm text-zinc-400">Mesa fuera de servicio.</p>
                ) : !session ? (
                  <div className="flex flex-wrap items-end gap-2">
                    <label className="field-label w-28">
                      Personas
                      <input type="number" min="1" max="100" value={guests} onChange={(e) => setGuests(e.target.value)} className="field" />
                    </label>
                    <button type="button" disabled={busy} onClick={() => run(() => openTable(selected.id, Number(guests) || undefined), "Mesa ocupada.")} className="btn btn-primary bg-metal">
                      <Users className="h-4 w-4" /> Ocupar mesa
                    </button>
                  </div>
                ) : (
                  <div className="space-y-2 text-sm">
                    <p className="flex flex-wrap gap-x-3 text-zinc-400">
                      <span><Clock className="mr-1 inline h-4 w-4" />Desde {timeOf(session.openedAt)}</span>
                      {session.waiterName && <span>Atiende: {session.waiterName}</span>}
                      {session.guests && <span>{session.guests} personas</span>}
                    </p>
                    <div className="flex flex-wrap gap-2">
                      {session.attentionRequestedAt ? (
                        <button type="button" disabled={busy} onClick={() => run(() => clearAttention(selected.id), "Atención resuelta.")} className="btn btn-sm btn-danger">
                          <CheckCircle2 className="h-4 w-4" /> Atención resuelta
                        </button>
                      ) : (
                        <button type="button" disabled={busy} onClick={() => run(() => requestAttention(selected.id), "Mesa marcada: requiere atención.")} className="btn btn-sm">
                          <BellRing className="h-4 w-4" /> Requiere atención
                        </button>
                      )}
                      {session.status === "OPEN" && (
                        <button type="button" disabled={busy} onClick={() => run(() => requestBill(selected.id), "Cuenta solicitada.")} className="btn btn-sm">
                          <Receipt className="h-4 w-4" /> Pedir cuenta
                        </button>
                      )}
                      <button
                        type="button"
                        disabled={busy || orders.some((o) => !["DELIVERED", "CANCELLED"].includes(o.status))}
                        onClick={async () => {
                          const ok = await confirm({
                            title: `Cerrar ${selected.displayName}`,
                            message: `${orders.filter((o) => o.status === "DELIVERED").length} pedidos entregados. Se registrará el cobro y la mesa quedará disponible.`,
                            highlight: money(session.total),
                            confirmLabel: "Confirmar cobro",
                            cancelLabel: "Cancelar"
                          });
                          if (ok) run(() => closeTable(selected.id), "Mesa cerrada y cobrada.");
                        }}
                        className="btn btn-sm btn-primary"
                        title="Disponible cuando todo está entregado o cancelado"
                      >
                        <CheckCircle2 className="h-4 w-4" /> Cerrar · {money(session.total)}
                      </button>
                    </div>
                  </div>
                )}

                {session && orders.length > 0 && (
                  <div className="border-t border-white/10 pt-3">
                    <p className="mb-2 text-sm font-semibold text-champagne">Pedidos de esta atención</p>
                    <div className="space-y-3">
                      {orders.map((order) => {
                        const s = ORDER_STATUS[order.status] || ORDER_STATUS.PENDING;
                        const mine = order.employeeId === userId || role === "ADMIN";
                        const elapsed = minutesSince(order.orderDate, now);
                        const late = order.estimatedMinutes && !order.readyAt && ["PENDING", "PREPARING"].includes(order.status) && elapsed > order.estimatedMinutes;
                        return (
                          <div key={order.id} className="border border-white/10 bg-white/[.03] p-3">
                            <div className="mb-2 flex items-center justify-between gap-2">
                              <span className={`chip ${s.chip}`}>{s.label}</span>
                              <span className={`text-xs ${late ? "is-late" : "text-zinc-500"}`}>
                                #{order.id} · {timeOf(order.orderDate)}
                                {["PENDING", "PREPARING"].includes(order.status) && ` · ${elapsed} min`}
                                {order.estimatedMinutes ? ` / ~${order.estimatedMinutes}` : ""}
                              </span>
                            </div>
                            <ul className="space-y-1 text-sm text-zinc-300">
                              {order.items.map((item) => {
                                const is = ITEM_STATUS[item.status] || ITEM_STATUS.PENDING;
                                return (
                                  <li key={item.id} className="flex items-start justify-between gap-2">
                                    <span className="min-w-0">
                                      {item.quantity}× {item.itemName}
                                      {item.notes && <span className="block text-xs text-zinc-500">{item.notes}</span>}
                                    </span>
                                    <span className={`chip ${is.chip}`}>{is.label}</span>
                                  </li>
                                );
                              })}
                            </ul>
                            <div className="mt-2 flex flex-wrap items-center justify-between gap-2">
                              <span className="text-sm text-champagne">{money(order.totalAmount)}</span>
                              <div className="flex gap-2">
                                {order.status === "READY" && (
                                  <button type="button" disabled={busy} onClick={() => run(() => deliverOrder(order.id), "Pedido entregado en la mesa.")} className="btn btn-sm btn-primary bg-metal">
                                    <HandPlatter className="h-4 w-4" /> Entregar
                                  </button>
                                )}
                                {mine && (order.status === "PENDING" || (role === "ADMIN" && !["DELIVERED", "CANCELLED"].includes(order.status))) && (
                                  <button
                                    type="button"
                                    disabled={busy}
                                    onClick={async () => {
                                      const result = await confirm({
                                        title: `¿Cancelar el pedido #${order.id}?`,
                                        message: `${order.items.map((i) => `${i.quantity}× ${i.itemName}`).join(", ")} · ${money(order.totalAmount)}`,
                                        confirmLabel: "Cancelar pedido",
                                        cancelLabel: "Volver",
                                        tone: "danger",
                                        input: { label: "Motivo (opcional)", placeholder: "El cliente cambió de opinión..." }
                                      });
                                      if (result) run(() => cancelOrder(order.id, result.value), "Pedido cancelado.");
                                    }}
                                    className="btn btn-sm btn-danger"
                                  >
                                    <XCircle className="h-4 w-4" /> Cancelar
                                  </button>
                                )}
                              </div>
                            </div>
                          </div>
                        );
                      })}
                    </div>
                  </div>
                )}

                {selected.liveState !== "MAINTENANCE" && (
                  <div className="border-t border-white/10 pt-3">
                    <p className="mb-2 text-sm font-semibold text-champagne">Nuevo pedido</p>
                    {!menu ? (
                      <Loader label="Cargando el menú..." className="py-6" />
                    ) : menu.length === 0 ? (
                      <p className="text-xs text-zinc-500">No hay productos activos en el menú.</p>
                    ) : (
                      <>
                        <MenuPicker menu={menu} draft={draft} onChange={setDraft} />
                        <DraftSummary menu={menu} draft={draft} onChange={setDraft} />
                        {hasDraft && (
                          <textarea
                            value={notes}
                            onChange={(e) => setNotes(e.target.value)}
                            maxLength={1000}
                            rows={2}
                            placeholder="Observaciones del pedido"
                            className="field mt-2"
                            aria-label="Observaciones del pedido"
                          />
                        )}
                        <button type="button" disabled={!hasDraft || busy} onClick={submitOrder} className="btn btn-primary bg-metal mt-3 w-full">
                          <Send className="h-4 w-4" /> {busy ? "Enviando..." : "Enviar a cocina"}
                        </button>
                      </>
                    )}
                  </div>
                )}
              </div>
            )}
          </div>
        </aside>
      </div>
    </Layout>
  );
}
