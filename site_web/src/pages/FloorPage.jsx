import { useCallback, useEffect, useMemo, useState } from "react";
import { Minus, Plus, Send, Trash2, Utensils } from "lucide-react";
import Layout from "../components/Layout";
import FloorMap from "../components/FloorMap";
import { fetchTables } from "../api/tableApi";
import { fetchMenu } from "../api/menuApi";
import { createOrder, deleteOrder, fetchOrdersByTable } from "../api/orderApi";
import { CATEGORY_LABEL, ITEM_STATUS, ORDER_STATUS, money } from "../utils/status";

export default function FloorPage() {
  const [tables, setTables] = useState([]);
  const [menu, setMenu] = useState([]);
  const [selectedTableId, setSelectedTableId] = useState(null);
  const [orders, setOrders] = useState([]);
  const [draft, setDraft] = useState({}); // menuItemId -> quantity
  const [status, setStatus] = useState("");

  const selectedTable = useMemo(
    () => tables.find((t) => t.id === selectedTableId) || null,
    [tables, selectedTableId]
  );

  const loadTables = useCallback(async () => {
    try {
      const data = await fetchTables();
      setTables(data);
    } catch (err) {
      setStatus(err.message);
    }
  }, []);

  useEffect(() => {
    loadTables();
    fetchMenu().then(setMenu).catch((err) => setStatus(err.message));
  }, [loadTables]);

  const loadOrders = useCallback(async (tableId) => {
    if (!tableId) return;
    try {
      const data = await fetchOrdersByTable(tableId);
      setOrders(data);
    } catch (err) {
      setStatus(err.message);
    }
  }, []);

  useEffect(() => {
    setDraft({});
    if (selectedTableId) loadOrders(selectedTableId);
    else setOrders([]);
  }, [selectedTableId, loadOrders]);

  const changeQty = (menuItemId, delta) => {
    setDraft((current) => {
      const next = { ...current };
      const value = (next[menuItemId] || 0) + delta;
      if (value <= 0) delete next[menuItemId];
      else next[menuItemId] = value;
      return next;
    });
  };

  const draftItems = Object.entries(draft);
  const draftTotal = draftItems.reduce((sum, [id, qty]) => {
    const item = menu.find((m) => String(m.id) === String(id));
    return sum + (item ? item.price * qty : 0);
  }, 0);

  const submitOrder = async () => {
    if (!selectedTableId || draftItems.length === 0) return;
    setStatus("Enviando pedido a cocina...");
    try {
      await createOrder({
        tableId: selectedTableId,
        items: draftItems.map(([menuItemId, quantity]) => ({
          menuItemId: Number(menuItemId),
          quantity
        }))
      });
      setDraft({});
      setStatus("Pedido enviado a cocina.");
      await loadOrders(selectedTableId);
    } catch (err) {
      setStatus(err.message);
    }
  };

  const removeOrder = async (id) => {
    try {
      await deleteOrder(id);
      await loadOrders(selectedTableId);
    } catch (err) {
      setStatus(err.message);
    }
  };

  return (
    <Layout subtitle="Mapa interactivo" title="Piso · Reportar pedidos">
      {status && <p className="mb-4 text-sm text-zinc-400">{status}</p>}

      <div className="grid gap-6 lg:grid-cols-[1fr_380px]">
        <FloorMap
          tables={tables}
          selectedTableId={selectedTableId}
          onSelect={setSelectedTableId}
        />

        <aside className="zone-band h-fit space-y-5">
          {!selectedTable ? (
            <p className="text-sm text-zinc-400">
              Selecciona una mesa para tomar o revisar un pedido.
            </p>
          ) : (
            <>
              <div className="border-b border-white/10 pb-4">
                <p className="text-xs uppercase tracking-[.2em] text-gold">Mesa seleccionada</p>
                <h3 className="mt-1 text-2xl font-semibold text-champagne">
                  {selectedTable.name || `Mesa ${selectedTable.tableNumber}`}
                </h3>
                <p className="text-sm text-zinc-400">
                  #{selectedTable.tableNumber} · {selectedTable.capacity} personas ·{" "}
                  {selectedTable.zoneName}
                </p>
              </div>

              <div>
                <p className="mb-2 flex items-center gap-2 text-sm font-semibold text-champagne">
                  <Utensils className="h-4 w-4 text-gold" /> Nuevo pedido
                </p>
                <div className="max-h-72 space-y-2 overflow-y-auto pr-1">
                  {menu.map((item) => (
                    <div
                      key={item.id}
                      className="flex items-center justify-between gap-2 border border-white/10 bg-white/[.03] px-3 py-2"
                    >
                      <div className="min-w-0">
                        <p className="truncate text-sm text-champagne">{item.name}</p>
                        <p className="text-xs text-zinc-500">
                          {CATEGORY_LABEL[item.category] || item.category} · {money(item.price)}
                        </p>
                      </div>
                      <div className="flex items-center gap-2">
                        <button
                          type="button"
                          className="grid h-7 w-7 place-items-center rounded-full border border-white/15 text-zinc-300 hover:border-gold/50"
                          onClick={() => changeQty(item.id, -1)}
                          aria-label="Quitar uno"
                        >
                          <Minus className="h-3.5 w-3.5" />
                        </button>
                        <span className="w-5 text-center text-sm text-champagne">
                          {draft[item.id] || 0}
                        </span>
                        <button
                          type="button"
                          className="grid h-7 w-7 place-items-center rounded-full border border-white/15 text-zinc-300 hover:border-gold/50"
                          onClick={() => changeQty(item.id, 1)}
                          aria-label="Agregar uno"
                        >
                          <Plus className="h-3.5 w-3.5" />
                        </button>
                      </div>
                    </div>
                  ))}
                </div>

                <div className="mt-3 flex items-center justify-between">
                  <span className="text-sm text-zinc-400">Total</span>
                  <span className="text-lg font-semibold text-champagne">{money(draftTotal)}</span>
                </div>
                <button
                  type="button"
                  disabled={draftItems.length === 0}
                  onClick={submitOrder}
                  className="mt-3 inline-flex w-full items-center justify-center gap-2 rounded-full border border-gold/60 bg-metal px-4 py-2.5 font-semibold text-carbon shadow-gold transition hover:brightness-110 disabled:opacity-50"
                >
                  <Send className="h-4 w-4" /> Enviar a cocina
                </button>
              </div>

              <div className="border-t border-white/10 pt-4">
                <p className="mb-2 text-sm font-semibold text-champagne">Pedidos de la mesa</p>
                {orders.length === 0 ? (
                  <p className="text-xs text-zinc-500">Sin pedidos aún.</p>
                ) : (
                  <div className="space-y-3">
                    {orders.map((order) => {
                      const s = ORDER_STATUS[order.status] || ORDER_STATUS.PENDING;
                      return (
                        <div key={order.id} className="border border-white/10 bg-white/[.03] p-3">
                          <div className="mb-2 flex items-center justify-between">
                            <span className={`chip ${s.chip}`}>{s.label}</span>
                            <div className="flex items-center gap-2">
                              <span className="text-sm text-champagne">
                                {money(order.totalAmount)}
                              </span>
                              <button
                                type="button"
                                onClick={() => removeOrder(order.id)}
                                className="text-zinc-500 hover:text-red-400"
                                aria-label="Eliminar pedido"
                              >
                                <Trash2 className="h-4 w-4" />
                              </button>
                            </div>
                          </div>
                          <ul className="space-y-1 text-sm text-zinc-300">
                            {order.items.map((item) => {
                              const is = ITEM_STATUS[item.status] || ITEM_STATUS.PENDING;
                              return (
                                <li key={item.id} className="flex items-center justify-between gap-2">
                                  <span className="truncate">
                                    {item.quantity}× {item.itemName}
                                  </span>
                                  <span className={`chip ${is.chip}`}>{is.label}</span>
                                </li>
                              );
                            })}
                          </ul>
                        </div>
                      );
                    })}
                  </div>
                )}
              </div>
            </>
          )}
        </aside>
      </div>
    </Layout>
  );
}
