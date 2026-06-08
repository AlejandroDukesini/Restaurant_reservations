import { CalendarDays, Clock3, Mail, UserRound, UsersRound } from "lucide-react";
import { today } from "../hooks/useTableMap";

export default function SelectionPanel({
  selectedTable,
  date,
  time,
  form,
  status,
  isLoading,
  onDateChange,
  onTimeChange,
  onFormChange,
  onSubmit
}) {
  const disabled = !selectedTable || selectedTable.availability === "OCCUPIED" || isLoading;

  return (
    <aside className="h-fit border border-gold/30 bg-obsidian p-5 shadow-gold">
      <p className="text-sm uppercase tracking-[.24em] text-gold">Tu Selección de Mesa</p>
      <h2 className="mt-3 text-2xl font-semibold text-champagne">
        {selectedTable ? `Tabla ${selectedTable.tableNumber}` : "Sin mesa"}
      </h2>
      <p className="mt-1 text-sm text-zinc-400">
        {selectedTable
          ? `Área: ${selectedTable.zoneName}, Cap: ${selectedTable.capacity} pers`
          : "Elige una mesa disponible."}
      </p>

      <form className="mt-6 space-y-4" onSubmit={onSubmit}>
        <label className="input-label">
          <span>Datha</span>
          <div className="input-shell">
            <CalendarDays className="h-4 w-4" />
            <input
              type="date"
              value={date}
              min={today()}
              onChange={(event) => onDateChange(event.target.value)}
            />
          </div>
        </label>

        <label className="input-label">
          <span>Timbo</span>
          <div className="input-shell">
            <Clock3 className="h-4 w-4" />
            <input type="time" value={time} onChange={(event) => onTimeChange(event.target.value)} />
          </div>
        </label>

        <label className="input-label">
          <span>Pax / Invitados</span>
          <div className="input-shell">
            <UsersRound className="h-4 w-4" />
            <input
              type="number"
              min="1"
              max={selectedTable?.capacity || 12}
              value={form.guests}
              onChange={(event) => onFormChange({ guests: event.target.value })}
            />
          </div>
        </label>

        <label className="input-label">
          <span>Nombre</span>
          <div className="input-shell">
            <UserRound className="h-4 w-4" />
            <input
              value={form.name}
              onChange={(event) => onFormChange({ name: event.target.value })}
              placeholder="Invitado principal"
            />
          </div>
        </label>

        <label className="input-label">
          <span>Email</span>
          <div className="input-shell">
            <Mail className="h-4 w-4" />
            <input
              type="email"
              value={form.email}
              onChange={(event) => onFormChange({ email: event.target.value })}
              placeholder="correo@dominio.com"
            />
          </div>
        </label>

        <button
          type="submit"
          className="w-full rounded-sm bg-metal px-5 py-4 font-semibold text-carbon shadow-gold transition hover:brightness-110 disabled:opacity-40"
          disabled={disabled}
        >
          {isLoading ? "Procesando..." : "Confirmar Mesa"}
        </button>
      </form>

      <p className="mt-4 min-h-6 text-sm text-gold">{status}</p>
    </aside>
  );
}
