import { useEffect, useState } from "react";
import { RefreshCw } from "lucide-react";
import { fetchAllOrders } from "../../api/orderApi";
import { ITEM_STATUS, ORDER_STATUS, money } from "../../utils/status";

export default function OrdersAdmin() {
  const [orders, setOrders] = useState([]);
  const [status, setStatus] = useState("");
  const [loading, setLoading] = useState(false);

  const load = async () => {
    setLoading(true);
    try {
      const data = await fetchAllOrders();
      setOrders([...data].sort((a, b) => new Date(b.orderDate) - new Date(a.orderDate)));
      setStatus("");
    } catch (err) {
      setStatus(err.message);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    load();
  }, []);

  return (
    <div>
      <div className="mb-4 flex items-center justify-between">
        <p className="text-sm text-zinc-400">Historial de pedidos del restaurante.</p>
        <button
          type="button"
          onClick={load}
          className="inline-flex items-center gap-2 rounded-full border border-gold/40 px-4 py-2 text-sm text-champagne transition hover:border-gold/70"
        >
          <RefreshCw className={`h-4 w-4 ${loading ? "animate-spin" : ""}`} /> Actualizar
        </button>
      </div>

      {status && <p className="mb-3 text-sm text-red-300">{status}</p>}

      <div className="grid gap-3 md:grid-cols-2 xl:grid-cols-3">
        {orders.map((order) => {
          const s = ORDER_STATUS[order.status] || ORDER_STATUS.PENDING;
          return (
            <article key={order.id} className="zone-band">
              <div className="mb-2 flex items-center justify-between">
                <div>
                  <h3 className="text-base font-semibold text-champagne">
                    Mesa {order.tableNumber}
                  </h3>
                  <p className="text-xs text-zinc-500">
                    {order.employeeName} · {new Date(order.orderDate).toLocaleString()}
                  </p>
                </div>
                <span className={`chip ${s.chip}`}>{s.label}</span>
              </div>
              <ul className="space-y-1 border-t border-white/10 pt-2 text-sm text-zinc-300">
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
              <p className="mt-2 text-right text-sm font-semibold text-champagne">
                {money(order.totalAmount)}
              </p>
            </article>
          );
        })}
        {orders.length === 0 && <p className="text-sm text-zinc-500">Sin pedidos.</p>}
      </div>
    </div>
  );
}
