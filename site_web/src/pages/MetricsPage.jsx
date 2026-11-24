import { useCallback, useEffect, useState } from "react";
import { AlertTriangle, TrendingDown, TrendingUp } from "lucide-react";
import Layout from "../components/Layout";
import { ErrorState, Loader } from "../components/LoadState";
import { fetchMetrics } from "../api/opsApi";
import { useRealtime } from "../realtime/RealtimeProvider";
import { formatMinutes, money } from "../utils/status";

const PERIODS = [
  ["today", "Hoy"],
  ["week", "7 días"],
  ["month", "30 días"],
  ["custom", "Personalizado"]
];

const DINE_LABELS = {
  kitchenQueue: ["Espera en cocina", "Registro → inicio de preparación"],
  preparation: ["Preparación", "Inicio → listo"],
  orderToReady: ["Pedido a listo", "Registro → listo"],
  readyToDelivered: ["Listo a entrega", "Listo → entregado en mesa (mesero)"],
  orderToDelivered: ["Servicio total del pedido", "Registro → entregado"],
  seatToFirstOrder: ["Atención inicial", "Mesa ocupada → primer pedido"],
  deliveredToClose: ["Cierre tras entrega", "Última entrega → cierre de mesa"],
  tableStay: ["Estancia en mesa", "Ocupada → cerrada"]
};

const DELIVERY_LABELS = {
  preparation: ["Preparación", "Inicio → listo"],
  orderToReady: ["Pedido a listo", "Registro → listo"],
  packing: ["Empaque", "Inicio → fin de empaque"],
  waitingDriver: ["Espera de domiciliario", "Empacado → salida"],
  transit: ["Reparto", "Salida → entrega"],
  total: ["Tiempo total", "Registro → entrega"]
};

function Kpi({ label, value, hint }) {
  return (
    <div className="zone-band">
      <p className="text-xs uppercase tracking-[.16em] text-zinc-400">{label}</p>
      <p className="mt-1 text-2xl font-semibold text-champagne">{value}</p>
      {hint && <p className="mt-1 text-xs text-zinc-500">{hint}</p>}
    </div>
  );
}

function TimeTable({ title, stats, labels }) {
  return (
    <section className="zone-band">
      <h3 className="mb-3 text-sm uppercase tracking-[.18em] text-gold">{title}</h3>
      <table className="w-full text-sm">
        <thead className="text-left text-xs text-zinc-500">
          <tr>
            <th className="pb-2 font-normal">Métrica</th>
            <th className="pb-2 text-right font-normal">Promedio</th>
            <th className="pb-2 text-right font-normal">Máx.</th>
            <th className="pb-2 text-right font-normal">Muestra</th>
          </tr>
        </thead>
        <tbody>
          {Object.entries(labels).map(([key, [label, def]]) => {
            const s = stats?.[key];
            return (
              <tr key={key} className="border-t border-white/5">
                <td className="py-2">
                  <span className="text-zinc-200">{label}</span>
                  <span className="block text-[11px] text-zinc-500">{def}</span>
                </td>
                <td className="py-2 text-right text-champagne">{formatMinutes(s?.avgMinutes)}</td>
                <td className="py-2 text-right text-zinc-400">{formatMinutes(s?.maxMinutes)}</td>
                <td className="py-2 text-right text-zinc-500">{s?.count ?? 0}</td>
              </tr>
            );
          })}
        </tbody>
      </table>
    </section>
  );
}

function Bars({ title, rows, value = (r) => r.amount, format = money, sub = (r) => `${r.count}` }) {
  const max = Math.max(1, ...rows.map(value));
  return (
    <section className="zone-band">
      <h3 className="mb-3 text-sm uppercase tracking-[.18em] text-gold">{title}</h3>
      {rows.length === 0 ? (
        <p className="text-sm text-zinc-500">Sin datos en el periodo.</p>
      ) : (
        <ul className="space-y-2">
          {rows.slice(0, 10).map((r) => (
            <li key={r.name} className="text-sm">
              <div className="flex justify-between gap-2">
                <span className="truncate text-zinc-200">{r.name}</span>
                <span className="shrink-0 text-zinc-400">
                  {format(value(r))} · {sub(r)}
                </span>
              </div>
              <div className="bar-track mt-1">
                <div className="bar-fill" style={{ width: `${(value(r) / max) * 100}%` }} />
              </div>
            </li>
          ))}
        </ul>
      )}
    </section>
  );
}

function Trend({ points }) {
  if (!points || points.length < 2) {
    return <p className="text-sm text-zinc-500">La tendencia aparece con datos de dos o más días.</p>;
  }
  const max = Math.max(1, ...points.map((p) => p.revenue));
  const w = 600;
  const h = 120;
  const step = w / (points.length - 1);
  const path = points.map((p, i) => `${i === 0 ? "M" : "L"}${(i * step).toFixed(1)},${(h - (p.revenue / max) * (h - 10)).toFixed(1)}`).join(" ");
  return (
    <svg viewBox={`0 0 ${w} ${h}`} className="h-32 w-full" role="img" aria-label="Tendencia de ingresos por día">
      <path d={path} fill="none" stroke="var(--t-primary)" strokeWidth="2.5" vectorEffect="non-scaling-stroke" />
      {points.map((p, i) => (
        <circle key={p.date} cx={i * step} cy={h - (p.revenue / max) * (h - 10)} r="3" fill="var(--t-primary)">
          <title>{`${p.date}: ${money(p.revenue)} · ${p.orders} pedidos`}</title>
        </circle>
      ))}
    </svg>
  );
}

function Delta({ current, previous, format = (v) => v, invert = false }) {
  if (previous === null || previous === undefined || current === null || current === undefined || previous === 0) return null;
  const diff = ((current - previous) / previous) * 100;
  const good = invert ? diff < 0 : diff > 0;
  const Icon = diff >= 0 ? TrendingUp : TrendingDown;
  return (
    <span className={`inline-flex items-center gap-1 text-xs ${good ? "text-emerald-300" : "text-red-300"}`}>
      <Icon className="h-3 w-3" /> {diff >= 0 ? "+" : ""}
      {diff.toFixed(0)}% vs {format(previous)}
    </span>
  );
}

export default function MetricsPage() {
  const [period, setPeriod] = useState("today");
  const [from, setFrom] = useState("");
  const [to, setTo] = useState("");
  const [data, setData] = useState(null);
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(false);

  const load = useCallback(async () => {
    if (period === "custom" && (!from || !to)) return;
    setLoading(true);
    try {
      setData(await fetchMetrics(period, from, to));
      setError("");
    } catch (err) {
      setError(err.message);
    } finally {
      setLoading(false);
    }
  }, [period, from, to]);

  useEffect(() => {
    load();
  }, [load]);
  useRealtime(["orders", "tables", "deliveries"], load);

  const s = data?.summary || {};
  const prev = data?.previousPeriod || {};

  return (
    <Layout subtitle="Dashboard del gerente" title="Métricas">
      <div className="mb-6 flex flex-wrap items-end gap-2">
        {PERIODS.map(([key, label]) => (
          <button key={key} type="button" onClick={() => setPeriod(key)} className={`btn btn-sm ${period === key ? "btn-primary" : ""}`} aria-pressed={period === key}>
            {label}
          </button>
        ))}
        {period === "custom" && (
          <>
            <label className="field-label">Desde<input type="date" className="field" value={from} onChange={(e) => setFrom(e.target.value)} /></label>
            <label className="field-label">Hasta<input type="date" className="field" value={to} onChange={(e) => setTo(e.target.value)} /></label>
          </>
        )}
        {loading && <span className="text-xs text-zinc-500">Actualizando...</span>}
      </div>

      {!data ? (
        error ? <ErrorState message={error} onRetry={load} /> : period === "custom" && (!from || !to) ? (
          <p className="text-sm text-zinc-400">Elige el rango de fechas.</p>
        ) : (
          <Loader label="Calculando métricas..." />
        )
      ) : (
        <div className="space-y-6">
          {error && <p className="text-sm text-red-300">{error}</p>}
          <div className="grid gap-3 sm:grid-cols-2 xl:grid-cols-4">
            <Kpi label="Ingresos cobrados" value={money(s.revenue)} hint={<Delta current={s.revenue} previous={prev.revenue} format={money} />} />
            <Kpi label="Por cobrar" value={money(s.pendingRevenue)} hint="Pedidos activos o mesas sin cerrar" />
            <Kpi label="Pedidos" value={s.orders ?? 0} hint={<Delta current={s.orders} previous={prev.orders} />} />
            <Kpi label="Ticket promedio" value={s.averageTicket != null ? money(s.averageTicket) : "—"} />
            <Kpi label="Mesas atendidas" value={s.tablesServed ?? 0} hint={`${s.tablesOpen ?? 0} abiertas ahora`} />
            <Kpi label="Completados / activos" value={`${s.completedOrders ?? 0} / ${s.activeOrders ?? 0}`} hint={`${s.cancelledOrders ?? 0} cancelados (no suman)`} />
            <Kpi
              label="Pedidos retrasados"
              value={s.lateOrders ?? 0}
              hint={s.lateRate != null ? `${s.lateRate}% de los que tienen tiempo estimado` : "Sin tiempos estimados"}
            />
            <Kpi
              label="Estimado vs real"
              value={`${formatMinutes(s.estimatedAvgMinutes)} / ${formatMinutes(s.realOrderToReadyAvgMinutes)}`}
              hint={<Delta current={data.dineInTimes?.orderToReady?.avgMinutes} previous={prev.orderToReadyAvgMinutes} format={formatMinutes} invert />}
            />
          </div>

          {data.bottlenecks?.length > 0 && (
            <section className="zone-band">
              <h3 className="mb-3 flex items-center gap-2 text-sm uppercase tracking-[.18em] text-gold">
                <AlertTriangle className="h-4 w-4" /> Cuellos de botella (mayor tiempo promedio primero)
              </h3>
              <ol className="space-y-1 text-sm">
                {data.bottlenecks.slice(0, 5).map((b, i) => (
                  <li key={`${b.channel}-${b.stage}`} className={i === 0 ? "text-red-300" : "text-zinc-300"}>
                    {i + 1}. {b.label} ({b.channel === "DELIVERY" ? "domicilio" : "mesa"}): {formatMinutes(b.avgMinutes)} · {b.count} casos
                  </li>
                ))}
              </ol>
            </section>
          )}

          <div className="grid gap-4 xl:grid-cols-2">
            <TimeTable title="Tiempos de mesa" stats={data.dineInTimes} labels={DINE_LABELS} />
            <TimeTable title="Tiempos de domicilio" stats={data.deliveryTimes} labels={DELIVERY_LABELS} />
          </div>

          <section className="zone-band">
            <h3 className="mb-3 text-sm uppercase tracking-[.18em] text-gold">Tendencia de ingresos</h3>
            <Trend points={data.trend} />
          </section>

          <div className="grid gap-4 lg:grid-cols-2 xl:grid-cols-3">
            <Bars title="Más vendidos (unidades)" rows={data.topProducts} value={(r) => r.count} format={(v) => `${v} u`} sub={(r) => money(r.amount)} />
            <Bars title="Categorías" rows={data.categories} sub={(r) => `${r.count} u`} />
            <Bars title="Ingresos por canal" rows={data.revenueByChannel} sub={(r) => `${r.count} pedidos`} />
            <Bars title="Ingresos por mesa" rows={data.revenueByTable} sub={(r) => `${r.count} pedidos`} />
            <Bars title="Ingresos por plataforma" rows={data.revenueByPlatform} sub={(r) => `${r.count} pedidos`} />
            <Bars
              title="Domiciliarios (tiempo de reparto)"
              rows={data.driverPerformance}
              value={(r) => r.avgDeliveryMinutes || 0}
              format={formatMinutes}
              sub={(r) => `${r.orders} entregas`}
            />
          </div>

          <section className="zone-band overflow-x-auto">
            <h3 className="mb-3 text-sm uppercase tracking-[.18em] text-gold">Rendimiento por empleado</h3>
            <table className="w-full min-w-[480px] text-sm">
              <thead className="text-left text-xs text-zinc-500">
                <tr>
                  <th className="pb-2 font-normal">Empleado</th>
                  <th className="pb-2 text-right font-normal">Pedidos</th>
                  <th className="pb-2 text-right font-normal">Listo → entrega</th>
                  <th className="pb-2 text-right font-normal">Ingresos cobrados</th>
                </tr>
              </thead>
              <tbody>
                {data.employees.length === 0 && (
                  <tr>
                    <td colSpan={4} className="py-3 text-zinc-500">Sin datos en el periodo.</td>
                  </tr>
                )}
                {data.employees.map((e) => (
                  <tr key={e.employeeId} className="border-t border-white/5">
                    <td className="py-2 text-zinc-200">{e.name}</td>
                    <td className="py-2 text-right">{e.orders}</td>
                    <td className="py-2 text-right">{formatMinutes(e.avgReadyToDeliveredMinutes)}</td>
                    <td className="py-2 text-right">{money(e.revenue)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
            <p className="mt-3 text-xs text-zinc-500">
              Todas las cifras se calculan con marcas de tiempo del servidor. "Muestra" indica cuántos pedidos
              tienen ambos eventos registrados; los pedidos anteriores a la actualización no tienen tiempos y no se incluyen.
            </p>
          </section>
        </div>
      )}
    </Layout>
  );
}
