import { CalendarDays, Clock3 } from "lucide-react";
import ZoneBand from "./ZoneBand";
import { today } from "../hooks/useTableMap";

export default function TableGrid({
  zones,
  date,
  time,
  selectedTableId,
  isLoading,
  isDemoMode,
  onDateChange,
  onTimeChange,
  onSelectTable
}) {
  return (
    <div className="space-y-5">
      <div className="flex flex-wrap items-center justify-between gap-4 border-b border-white/10 pb-5">
        <div>
          <p className="text-sm uppercase tracking-[.28em] text-gold">Mapa interactivo</p>
          <h2 className="mt-1 text-3xl font-semibold text-champagne">Selecciona tu mesa</h2>
          {isDemoMode && (
            <p className="mt-2 text-xs uppercase tracking-[.18em] text-zinc-500">
              Vista local con datos de demostración
            </p>
          )}
        </div>
        <div className="flex flex-wrap gap-3">
          <label className="field-chip">
            <CalendarDays className="h-4 w-4" />
            <input
              type="date"
              value={date}
              min={today()}
              disabled={isLoading}
              onChange={(event) => onDateChange(event.target.value)}
            />
          </label>
          <label className="field-chip">
            <Clock3 className="h-4 w-4" />
            <input
              type="time"
              value={time}
              disabled={isLoading}
              onChange={(event) => onTimeChange(event.target.value)}
            />
          </label>
        </div>
      </div>

      <div className="grid gap-4 xl:grid-cols-2">
        {zones.map((zone) => (
          <ZoneBand
            key={zone.code}
            zone={zone}
            selectedTableId={selectedTableId}
            onSelectTable={onSelectTable}
          />
        ))}
      </div>
    </div>
  );
}
