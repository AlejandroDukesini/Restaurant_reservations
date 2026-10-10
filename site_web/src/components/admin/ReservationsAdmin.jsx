import { useCallback, useEffect, useMemo, useState } from "react";
import { CalendarCheck, Check, Plus, RefreshCw, Users, X } from "lucide-react";
import Modal from "./Modal";
import { useAuth } from "../../auth/AuthContext";
import { fetchTables } from "../../api/tableApi";
import {
  cancelReservation,
  confirmReservation,
  createReservation,
  fetchRestaurantReservations
} from "../../api/reservationApi";
import { RESERVATION_STATUS } from "../../utils/status";
import { ErrorState, Loader } from "../LoadState";

const FILTERS = [
  { key: "ALL", label: "Todas" },
  { key: "PENDING", label: "Pendientes" },
  { key: "CONFIRMED", label: "Confirmadas" },
  { key: "CANCELLED", label: "Canceladas" }
];

const EMPTY = {
  tableId: "",
  reservationDate: "",
  numberOfGuests: "2",
  customerName: "",
  customerEmail: "",
  specialRequests: ""
};

const tableLabel = (table) => (table ? table.name || `Mesa ${table.tableNumber}` : "Mesa");

export default function ReservationsAdmin() {
  const { restaurantId } = useAuth();
  const [reservations, setReservations] = useState(null); // null = aún sin primera carga
  const [tables, setTables] = useState([]);
  const [status, setStatus] = useState("");
  const [loading, setLoading] = useState(false);
  const [filter, setFilter] = useState("ALL");
  const [busyId, setBusyId] = useState(null);
  const [creating, setCreating] = useState(false);
  const [form, setForm] = useState(EMPTY);
  const [formError, setFormError] = useState("");
  const [saving, setSaving] = useState(false);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      // Las mesas solo aportan número/nombre y capacidad: la reserva trae únicamente tableId.
      const [reservationData, tableData] = await Promise.all([fetchRestaurantReservations(), fetchTables()]);
      setReservations(
        [...reservationData].sort((a, b) => new Date(a.reservationDate) - new Date(b.reservationDate))
      );
      setTables(tableData);
      setStatus("");
    } catch (err) {
      setStatus(err.message);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    load();
  }, [load]);

  const tablesById = useMemo(() => new Map(tables.map((t) => [t.id, t])), [tables]);
  const visible = (reservations ?? []).filter((r) => filter === "ALL" || r.status === filter);
  const selectedTable = tablesById.get(Number(form.tableId));

  // Las reglas (quién puede, aforo, solapamiento) las aplica el servidor; aquí solo se refresca.
  const runAction = async (id, action) => {
    setBusyId(id);
    try {
      await action(id);
      await load();
    } catch (err) {
      setStatus(err.message);
    } finally {
      setBusyId(null);
    }
  };

  const openCreate = () => {
    setForm(EMPTY);
    setFormError("");
    setCreating(true);
  };

  const save = async (event) => {
    event.preventDefault();
    setFormError("");
    setSaving(true);
    try {
      await createReservation({
        restaurantId,
        tableId: Number(form.tableId),
        reservationDate: form.reservationDate,
        numberOfGuests: Number(form.numberOfGuests),
        customerName: form.customerName,
        customerEmail: form.customerEmail,
        specialRequests: form.specialRequests || null
      });
      setCreating(false);
      await load();
    } catch (err) {
      setFormError(err.message);
    } finally {
      setSaving(false);
    }
  };

  const input = (key, label, props = {}) => (
    <label className="input-label">
      {label}
      <span className="input-shell">
        <input value={form[key]} onChange={(e) => setForm({ ...form, [key]: e.target.value })} {...props} />
      </span>
    </label>
  );

  return (
    <div>
      <div className="mb-4 flex flex-wrap items-center justify-between gap-3">
        <p className="text-sm text-zinc-400">Reservas de las mesas del restaurante.</p>
        <div className="flex gap-2">
          <button
            type="button"
            onClick={load}
            className="inline-flex items-center gap-2 rounded-full border border-gold/40 px-4 py-2 text-sm text-champagne transition hover:border-gold/70"
          >
            <RefreshCw className={`h-4 w-4 ${loading ? "animate-spin" : ""}`} /> Actualizar
          </button>
          <button
            type="button"
            onClick={openCreate}
            disabled={!reservations}
            className="inline-flex items-center gap-2 rounded-full border border-gold/60 bg-metal px-4 py-2 text-sm font-semibold text-carbon shadow-gold transition hover:brightness-110 disabled:opacity-50"
          >
            <Plus className="h-4 w-4" /> Nueva reserva
          </button>
        </div>
      </div>

      {status && reservations && <p className="motion-fade mb-3 text-sm text-red-300">{status}</p>}

      {!reservations ? (
        status ? (
          <ErrorState message={status} onRetry={load} retrying={loading} />
        ) : (
          <Loader label="Cargando reservas..." />
        )
      ) : (
        <>
          <div className="mb-4 flex flex-wrap gap-2" role="group" aria-label="Filtrar por estado">
            {FILTERS.map(({ key, label }) => (
              <button
                key={key}
                type="button"
                aria-pressed={filter === key}
                onClick={() => setFilter(key)}
                className={[
                  "rounded-full border px-3 py-1 text-xs transition",
                  filter === key
                    ? "border-gold/70 text-champagne"
                    : "border-white/10 text-zinc-400 hover:border-gold/40 hover:text-champagne"
                ].join(" ")}
              >
                {label}
              </button>
            ))}
          </div>

          <div className="grid gap-3 md:grid-cols-2 xl:grid-cols-3">
            {visible.map((reservation, index) => {
              const s = RESERVATION_STATUS[reservation.status] || RESERVATION_STATUS.PENDING;
              const busy = busyId === reservation.id;
              const canConfirm = reservation.status === "PENDING";
              const canCancel = reservation.status === "PENDING" || reservation.status === "CONFIRMED";
              return (
                <article
                  key={reservation.id}
                  className="motion-enter card-lift zone-band"
                  style={{ "--i": index }}
                >
                  <div className="mb-2 flex items-start justify-between gap-2">
                    <div>
                      <h3 className="text-base font-semibold text-champagne">
                        {tableLabel(tablesById.get(reservation.tableId))}
                      </h3>
                      <p className="text-xs text-zinc-500">
                        {new Date(reservation.reservationDate).toLocaleString()}
                      </p>
                    </div>
                    <span className={`chip ${s.chip}`}>{s.label}</span>
                  </div>
                  <div className="space-y-1 border-t border-white/10 pt-2 text-sm text-zinc-300">
                    <p className="flex items-center gap-2">
                      <Users className="h-4 w-4 text-gold" aria-hidden="true" />
                      {reservation.customerName} · {reservation.numberOfGuests} personas
                    </p>
                    {reservation.specialRequests && (
                      <p className="text-xs text-zinc-400">{reservation.specialRequests}</p>
                    )}
                  </div>
                  {(canConfirm || canCancel) && (
                    <div className="mt-3 flex justify-end gap-2">
                      {canConfirm && (
                        <button
                          type="button"
                          disabled={busy}
                          onClick={() => runAction(reservation.id, confirmReservation)}
                          className="inline-flex items-center gap-1 rounded-full border border-gold/40 px-3 py-1 text-xs text-champagne transition hover:border-gold/70 disabled:opacity-50"
                        >
                          <Check className="h-3.5 w-3.5" /> Confirmar
                        </button>
                      )}
                      {canCancel && (
                        <button
                          type="button"
                          disabled={busy}
                          onClick={() => runAction(reservation.id, cancelReservation)}
                          className="inline-flex items-center gap-1 rounded-full border border-white/15 px-3 py-1 text-xs text-zinc-300 transition hover:border-red-400/60 hover:text-red-300 disabled:opacity-50"
                        >
                          <X className="h-3.5 w-3.5" /> Cancelar
                        </button>
                      )}
                    </div>
                  )}
                </article>
              );
            })}
            {visible.length === 0 && (
              <p className="motion-fade text-sm text-zinc-500">
                {reservations.length === 0 ? "Sin reservas." : "Sin reservas con este estado."}
              </p>
            )}
          </div>
        </>
      )}

      {creating && (
        <Modal title="Nueva reserva" onClose={() => setCreating(false)}>
          <form onSubmit={save} className="max-h-[70vh] space-y-3 overflow-y-auto pr-1">
            {formError && <p className="motion-fade text-sm text-red-300">{formError}</p>}
            <label className="input-label">
              Mesa
              <span className="input-shell">
                <select
                  value={form.tableId}
                  required
                  onChange={(e) => setForm({ ...form, tableId: e.target.value })}
                  className="w-full bg-transparent text-champagne outline-none"
                >
                  <option value="">Selecciona una mesa</option>
                  {tables.map((t) => (
                    <option key={t.id} value={t.id}>
                      {tableLabel(t)} · {t.capacity} personas
                    </option>
                  ))}
                </select>
              </span>
            </label>
            <div className="flex gap-3">
              <div className="flex-1">
                {input("reservationDate", "Fecha y hora", { type: "datetime-local", required: true })}
              </div>
              <div className="w-28">
                {input("numberOfGuests", "Personas", {
                  type: "number",
                  min: 1,
                  max: selectedTable?.capacity ?? 100,
                  required: true
                })}
              </div>
            </div>
            {input("customerName", "Nombre del cliente", { required: true, maxLength: 120 })}
            {input("customerEmail", "Email del cliente", { type: "email", required: true, maxLength: 254 })}
            {input("specialRequests", "Peticiones especiales", { maxLength: 1000 })}
            <button
              type="submit"
              disabled={saving}
              className="inline-flex w-full items-center justify-center gap-2 rounded-full border border-gold/60 bg-metal px-4 py-2.5 font-semibold text-carbon shadow-gold transition hover:brightness-110 disabled:opacity-60"
            >
              <CalendarCheck className="h-4 w-4" /> {saving ? "Guardando..." : "Reservar"}
            </button>
          </form>
        </Modal>
      )}
    </div>
  );
}
