import TableIcon from "./TableIcon";

export default function TableButton({ table, zoneName, selected, onSelect }) {
  const occupied = table.availability === "OCCUPIED";

  return (
    <button
      type="button"
      disabled={occupied}
      onClick={() => onSelect(table.id)}
      className={[
        "table-button",
        occupied ? "table-occupied" : "table-free",
        selected ? "table-selected" : ""
      ].join(" ")}
      title={`Mesa ${table.tableNumber}, ${zoneName}, ${table.capacity} personas`}
      aria-pressed={selected}
      aria-label={`Mesa ${table.tableNumber}, ${occupied ? "ocupada" : "disponible"}`}
    >
      <TableIcon status={table.availability} selected={selected} />
      <span className="text-sm font-semibold">Mesa {table.tableNumber}</span>
      <span className="text-xs text-zinc-400">{table.capacity} pers</span>
    </button>
  );
}
