import { Sparkles } from "lucide-react";
import TableButton from "./TableButton";

export default function ZoneBand({ zone, selectedTableId, onSelectTable }) {
  return (
    <section className="zone-band">
      <div className="mb-4 flex items-center justify-between">
        <h3 className="text-lg font-semibold text-champagne">{zone.name}</h3>
        <Sparkles className="h-4 w-4 text-gold" />
      </div>
      <div className="grid grid-cols-3 gap-3 sm:grid-cols-4">
        {zone.tables.map((table) => (
          <TableButton
            key={table.id}
            table={table}
            zoneName={zone.name}
            selected={selectedTableId === table.id}
            onSelect={onSelectTable}
          />
        ))}
      </div>
    </section>
  );
}
