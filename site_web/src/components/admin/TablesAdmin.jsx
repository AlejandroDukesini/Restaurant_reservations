import { useEffect, useMemo, useState } from "react";
import { Move, Pencil, Plus, Trash2 } from "lucide-react";
import Modal from "./Modal";
import { createTable, deleteTable, fetchTables, updateTable } from "../../api/tableApi";
import { money } from "../../utils/status";

const EMPTY = {
  tableNumber: "",
  name: "",
  floor: 1,
  capacity: 2,
  price: "",
  zoneId: "",
  gridX: 1,
  gridY: 1
};

export default function TablesAdmin() {
  const [tables, setTables] = useState([]);
  const [status, setStatus] = useState("");
  const [editing, setEditing] = useState(null);
  const [form, setForm] = useState(EMPTY);

  const load = async () => {
    try {
      setTables(await fetchTables());
    } catch (err) {
      setStatus(err.message);
    }
  };

  useEffect(() => {
    load();
  }, []);

  // Zonas disponibles derivadas de las mesas existentes.
  const zones = useMemo(() => {
    const map = new Map();
    tables.forEach((t) => {
      if (t.zoneId != null) map.set(t.zoneId, t.zoneName);
    });
    return Array.from(map.entries()).map(([id, name]) => ({ id, name }));
  }, [tables]);

  const openCreate = () => {
    const nextNumber = tables.reduce((max, t) => Math.max(max, t.tableNumber), 0) + 1;
    setForm({ ...EMPTY, tableNumber: nextNumber });
    setEditing({});
  };

  const openEdit = (table) => {
    setForm({
      tableNumber: table.tableNumber,
      name: table.name ?? "",
      floor: table.floor,
      capacity: table.capacity,
      price: table.price,
      zoneId: table.zoneId ?? "",
      gridX: table.gridX,
      gridY: table.gridY
    });
    setEditing(table);
  };

  const save = async (event) => {
    event.preventDefault();
    setStatus("");
    try {
      const payload = {
        tableNumber: Number(form.tableNumber),
        name: form.name || null,
        floor: Number(form.floor),
        capacity: Number(form.capacity),
        price: Number(form.price),
        zoneId: form.zoneId ? Number(form.zoneId) : null,
        gridX: Number(form.gridX),
        gridY: Number(form.gridY)
      };
      if (editing?.id) await updateTable(editing.id, payload);
      else await createTable(payload);
      setEditing(null);
      await load();
    } catch (err) {
      setStatus(err.message);
    }
  };

  const remove = async (id) => {
    try {
      await deleteTable(id);
      await load();
    } catch (err) {
      setStatus(err.message);
    }
  };

  const numField = (key, label) => (
    <label className="input-label flex-1">
      {label}
      <span className="input-shell">
        <input
          type="number"
          min="1"
          value={form[key]}
          required
          onChange={(e) => setForm({ ...form, [key]: e.target.value })}
        />
      </span>
    </label>
  );

  return (
    <div>
      <div className="mb-4 flex items-center justify-between">
        <p className="text-sm text-zinc-400">
          Añade, renombra, reubica (coordenadas X/Y) o elimina mesas.
        </p>
        <button
          type="button"
          onClick={openCreate}
          className="inline-flex items-center gap-2 rounded-full border border-gold/60 bg-metal px-4 py-2 text-sm font-semibold text-carbon shadow-gold transition hover:brightness-110"
        >
          <Plus className="h-4 w-4" /> Nueva mesa
        </button>
      </div>

      {status && <p className="mb-3 text-sm text-red-300">{status}</p>}

      <div className="overflow-x-auto border border-white/10">
        <table className="w-full text-left text-sm">
          <thead className="bg-white/[.04] text-xs uppercase tracking-wider text-zinc-400">
            <tr>
              <th className="px-4 py-3">#</th>
              <th className="px-4 py-3">Nombre</th>
              <th className="px-4 py-3">Zona</th>
              <th className="px-4 py-3">Cap.</th>
              <th className="px-4 py-3">Precio</th>
              <th className="px-4 py-3">Posición</th>
              <th className="px-4 py-3 text-right">Acciones</th>
            </tr>
          </thead>
          <tbody>
            {tables.map((t) => (
              <tr key={t.id} className="border-t border-white/5">
                <td className="px-4 py-3 text-champagne">{t.tableNumber}</td>
                <td className="px-4 py-3 text-zinc-300">{t.name || "—"}</td>
                <td className="px-4 py-3 text-zinc-300">{t.zoneName || "—"}</td>
                <td className="px-4 py-3 text-zinc-300">{t.capacity}</td>
                <td className="px-4 py-3 text-zinc-300">{money(t.price)}</td>
                <td className="px-4 py-3 text-zinc-400">
                  <span className="inline-flex items-center gap-1">
                    <Move className="h-3.5 w-3.5" /> {t.gridX},{t.gridY}
                  </span>
                </td>
                <td className="px-4 py-3">
                  <div className="flex justify-end gap-2">
                    <button type="button" onClick={() => openEdit(t)} className="text-zinc-400 hover:text-champagne">
                      <Pencil className="h-4 w-4" />
                    </button>
                    <button type="button" onClick={() => remove(t.id)} className="text-zinc-400 hover:text-red-400">
                      <Trash2 className="h-4 w-4" />
                    </button>
                  </div>
                </td>
              </tr>
            ))}
            {tables.length === 0 && (
              <tr>
                <td colSpan={7} className="px-4 py-6 text-center text-zinc-500">
                  Sin mesas registradas.
                </td>
              </tr>
            )}
          </tbody>
        </table>
      </div>

      {editing && (
        <Modal title={editing.id ? "Editar mesa" : "Nueva mesa"} onClose={() => setEditing(null)}>
          <form onSubmit={save} className="space-y-3">
            <div className="flex gap-3">
              {numField("tableNumber", "Número")}
              <label className="input-label flex-1">
                Nombre
                <span className="input-shell">
                  <input
                    value={form.name}
                    placeholder="Mesa VIP (opcional)"
                    onChange={(e) => setForm({ ...form, name: e.target.value })}
                  />
                </span>
              </label>
            </div>
            <div className="flex gap-3">
              {numField("capacity", "Capacidad")}
              {numField("floor", "Piso")}
              <label className="input-label flex-1">
                Precio
                <span className="input-shell">
                  <input
                    type="number"
                    step="0.01"
                    min="0"
                    value={form.price}
                    required
                    onChange={(e) => setForm({ ...form, price: e.target.value })}
                  />
                </span>
              </label>
            </div>
            <label className="input-label">
              Zona
              <span className="input-shell">
                <select
                  value={form.zoneId}
                  onChange={(e) => setForm({ ...form, zoneId: e.target.value })}
                  className="w-full bg-transparent text-champagne outline-none"
                >
                  <option value="">Sin zona</option>
                  {zones.map((z) => (
                    <option key={z.id} value={z.id}>
                      {z.name}
                    </option>
                  ))}
                </select>
              </span>
            </label>
            <div className="flex gap-3">
              {numField("gridX", "Posición X (columna)")}
              {numField("gridY", "Posición Y (fila)")}
            </div>
            <button
              type="submit"
              className="inline-flex w-full items-center justify-center gap-2 rounded-full border border-gold/60 bg-metal px-4 py-2.5 font-semibold text-carbon shadow-gold transition hover:brightness-110"
            >
              <Plus className="h-4 w-4" /> Guardar
            </button>
          </form>
        </Modal>
      )}
    </div>
  );
}
