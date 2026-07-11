import { Sparkles } from "lucide-react";
import TableIcon from "./TableIcon";

// Agrupa las mesas por zona y las posiciona en una cuadrícula coherente usando gridX/gridY.
function groupByZone(tables) {
  const map = new Map();
  for (const table of tables) {
    const key = table.zoneName || "Sin zona";
    if (!map.has(key)) map.set(key, []);
    map.get(key).push(table);
  }
  return Array.from(map.entries()).map(([zoneName, zoneTables]) => ({
    zoneName,
    tables: zoneTables,
    cols: Math.max(1, ...zoneTables.map((t) => t.gridX || 1)),
    rows: Math.max(1, ...zoneTables.map((t) => t.gridY || 1))
  }));
}

export default function FloorMap({ tables, selectedTableId, onSelect }) {
  const zones = groupByZone(tables);

  return (
    <div className="grid gap-4 xl:grid-cols-2">
      {zones.map((zone) => (
        <section key={zone.zoneName} className="zone-band">
          <header className="mb-4 flex items-center justify-between gap-3">
            <h3 className="truncate text-lg font-semibold text-champagne">{zone.zoneName}</h3>
            <Sparkles className="h-4 w-4 shrink-0 text-gold" />
          </header>
          <div
            className="grid gap-3"
            style={{
              gridTemplateColumns: `repeat(${zone.cols}, minmax(0, 1fr))`,
              gridTemplateRows: `repeat(${zone.rows}, minmax(8.5rem, 1fr))`
            }}
          >
            {zone.tables.map((table) => {
              const occupied = table.status === "OCCUPIED";
              const selected = selectedTableId === table.id;
              const displayName = table.name || `Mesa ${table.tableNumber}`;
              return (
                <button
                  key={table.id}
                  type="button"
                  onClick={() => onSelect(table.id)}
                  style={{ gridColumnStart: table.gridX || 1, gridRowStart: table.gridY || 1 }}
                  className={[
                    "table-button",
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
