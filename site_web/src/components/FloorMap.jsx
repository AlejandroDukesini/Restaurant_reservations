import { Sparkles } from "lucide-react";
import TableIcon from "./TableIcon";

// Agrupa las mesas por zona y las ordena de forma estable (fila, luego columna, luego número).
function groupByZone(tables) {
  const map = new Map();
  for (const table of tables) {
    const key = table.zoneName || "Sin zona";
    if (!map.has(key)) map.set(key, []);
    map.get(key).push(table);
  }
  return Array.from(map.entries()).map(([zoneName, zoneTables]) => ({
    zoneName,
    tables: [...zoneTables].sort(
      (a, b) =>
        (a.gridY || 1) - (b.gridY || 1) ||
        (a.gridX || 1) - (b.gridX || 1) ||
        a.tableNumber - b.tableNumber
    )
  }));
}

export default function FloorMap({ tables, selectedTableId, onSelect }) {
  const zones = groupByZone(tables);

  return (
    <div className="grid min-w-0 gap-4 xl:grid-cols-2">
      {zones.map((zone) => (
        <section key={zone.zoneName} className="zone-band min-w-0">
          <header className="mb-4 flex items-center justify-between gap-3">
            <h3 className="truncate text-lg font-semibold text-champagne">{zone.zoneName}</h3>
            <Sparkles className="h-4 w-4 shrink-0 text-gold" />
          </header>

          {/* Grid robusto: columnas equitativas, responsivo, sin posicionado absoluto frágil. */}
          <div className="grid w-full grid-cols-2 gap-4 sm:grid-cols-3">
            {zone.tables.map((table) => {
              const occupied = table.status === "OCCUPIED";
              const selected = selectedTableId === table.id;
              const displayName = table.name || `Mesa ${table.tableNumber}`;
              return (
                <button
                  key={table.id}
                  type="button"
                  onClick={() => onSelect(table.id)}
                  className={[
                    "table-button box-border",
                    occupied ? "table-occupied-soft" : "table-free",
                    selected ? "table-selected" : ""
                  ].join(" ")}
                  aria-pressed={selected}
                  title={`${displayName} · ${table.capacity} personas`}
                >
                  <TableIcon status={table.status} selected={selected} />
                  <span className="w-full truncate text-sm font-semibold text-champagne">
                    {displayName}
                  </span>
                  <span className="w-full truncate text-xs text-zinc-400">
                    #{table.tableNumber} · {table.capacity} pers
                  </span>
                </button>
              );
            })}
          </div>
        </section>
      ))}
    </div>
  );
}
