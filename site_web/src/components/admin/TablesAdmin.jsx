import { useCallback, useEffect, useMemo, useState } from "react";
import { useConfirm } from "../ConfirmDialog";
import { Pencil, Plus, Power, Trash2 } from "lucide-react";
import Modal from "./Modal";
import { deleteManagedTable, fetchGroups, fetchManagedTables, saveManagedTable, setTableActive } from "../../api/opsApi";
import { ErrorState, Loader } from "../LoadState";

const EMPTY = {
  tableNumber: "",
  name: "",
  floor: 1,
  capacity: 4,
  price: 0,
  zoneId: "",
  groupId: "",
  gridX: 1,
  gridY: 1,
  sortOrder: 0,
  status: "AVAILABLE"
};

export default function TablesAdmin() {
  const [tables, setTables] = useState(null);
  const [groups, setGroups] = useState([]);
  const [status, setStatus] = useState("");
  const [editing, setEditing] = useState(null);
  const [form, setForm] = useState(EMPTY);
  const confirm = useConfirm();

  const load = useCallback(async () => {
    try {
      const [list, g] = await Promise.all([fetchManagedTables(), fetchGroups().catch(() => [])]);
      setTables(list);
      setGroups(g);
      setStatus("");
    } catch (err) {
      setStatus(err.message);
    }
  }, []);

  useEffect(() => {
    load();
  }, [load]);

  // Las zonas existentes se deducen de las mesas (no hay CRUD de zonas).
  const zones = useMemo(() => {
    const map = new Map();
    for (const t of tables || []) if (t.zoneId != null) map.set(t.zoneId, t.zoneName);
    return [...map.entries()];
  }, [tables]);

  const act = async (fn, ok) => {
    setStatus("");
    try {
      await fn();
      if (ok) setStatus(ok);
      await load();
    } catch (err) {
      setStatus(err.message);
    }
  };

  const openEdit = (t) => {
    setForm({
      ...EMPTY,
      ...t,
      name: t.name || "",
      zoneId: t.zoneId ?? "",
      groupId: t.groupId ?? "",
      status: t.status === "MAINTENANCE" ? "MAINTENANCE" : "AVAILABLE"
    });
    setEditing(t);
  };

  const save = (e) => {
    e.preventDefault();
    const payload = {
      tableNumber: Number(form.tableNumber),
      name: form.name.trim() || null,
      floor: Number(form.floor),
      capacity: Number(form.capacity),
      price: Number(form.price),
      zoneId: form.zoneId ? Number(form.zoneId) : null,
      groupId: form.groupId ? Number(form.groupId) : null,
      gridX: Number(form.gridX) || 1,
      gridY: Number(form.gridY) || 1,
      sortOrder: Number(form.sortOrder) || 0,
      status: form.status
    };
    act(async () => {
      await saveManagedTable(editing?.id, payload);
      setEditing(null);
    }, "Mesa guardada.");
  };

  const set = (key) => (e) => setForm({ ...form, [key]: e.target.value });

  return (
    <div>
      <div className="mb-4 flex flex-wrap items-center justify-between gap-3">
        <p className="text-sm text-zinc-400">
          Nombre e identificador libres. Si una mesa tiene historial, al eliminarla solo se desactiva.
        </p>
        <button
          type="button"
          onClick={() => {
            setForm({ ...EMPTY, tableNumber: (Math.max(0, ...(tables || []).map((t) => t.tableNumber)) || 0) + 1 });
            setEditing({});
          }}
          className="btn btn-primary bg-metal"
        >
          <Plus className="h-4 w-4" /> Nueva mesa
        </button>
      </div>

      {status && <p className="motion-fade mb-3 text-sm text-zinc-300" role="status">{status}</p>}

      {!tables ? (
        status ? <ErrorState message={status} onRetry={load} /> : <Loader label="Cargando mesas..." />
      ) : (
        <div className="overflow-x-auto border border-white/10">
          <table className="w-full min-w-[640px] text-left text-sm">
            <thead className="bg-white/[.04] text-xs uppercase tracking-wider text-zinc-400">
              <tr>
                <th className="px-4 py-3">#</th>
                <th className="px-4 py-3">Nombre</th>
                <th className="px-4 py-3">Capacidad</th>
                <th className="px-4 py-3">Zona</th>
                <th className="px-4 py-3">Grupo</th>
                <th className="px-4 py-3">Estado</th>
                <th className="px-4 py-3 text-right">Acciones</th>
              </tr>
            </thead>
            <tbody>
              {tables.map((t) => (
                <tr key={t.id} className={`border-t border-white/5 ${t.active ? "" : "opacity-55"}`}>
                  <td className="px-4 py-3 text-zinc-300">{t.tableNumber}</td>
                  <td className="px-4 py-3 text-champagne">{t.name || `Mesa ${t.tableNumber}`}</td>
                  <td className="px-4 py-3 text-zinc-300">{t.capacity}</td>
                  <td className="px-4 py-3 text-zinc-300">{t.zoneName || "—"}</td>
                  <td className="px-4 py-3 text-zinc-300">{t.groupName || "Todos"}</td>
                  <td className="px-4 py-3">
                    <span className={`chip ${!t.active ? "chip-muted" : t.status === "MAINTENANCE" ? "chip-warn" : "chip-ready"}`}>
                      {!t.active ? "Inactiva" : t.status === "MAINTENANCE" ? "Mantenimiento" : "Activa"}
                    </span>
                  </td>
                  <td className="px-4 py-3">
                    <div className="flex justify-end gap-1">
                      <button type="button" onClick={() => openEdit(t)} className="grid h-9 w-9 place-items-center text-zinc-400 hover:text-champagne" aria-label={`Editar mesa ${t.tableNumber}`}>
                        <Pencil className="h-4 w-4" />
                      </button>
                      <button
                        type="button"
                        onClick={() => act(() => setTableActive(t.id, !t.active), t.active ? "Mesa desactivada." : "Mesa activada.")}
                        className="grid h-9 w-9 place-items-center text-zinc-400 hover:text-champagne"
                        aria-label={t.active ? "Desactivar" : "Activar"}
                        title={t.active ? "Desactivar" : "Activar"}
                      >
                        <Power className="h-4 w-4" />
                      </button>
                      <button
                        type="button"
                        onClick={async () =>
                          (await confirm({
                            title: `¿Eliminar ${t.name || `la mesa ${t.tableNumber}`}?`,
                            message: "Si tiene historial de atenciones o pedidos, solo se desactivará.",
                            confirmLabel: "Eliminar",
                            tone: "danger"
                          })) && act(() => deleteManagedTable(t.id), "Mesa eliminada o desactivada.")
                        }
                        className="grid h-9 w-9 place-items-center text-zinc-400 hover:text-red-400"
                        aria-label={`Eliminar mesa ${t.tableNumber}`}
                      >
                        <Trash2 className="h-4 w-4" />
                      </button>
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {editing && (
        <Modal title={editing.id ? "Editar mesa" : "Nueva mesa"} onClose={() => setEditing(null)}>
          <form onSubmit={save} className="grid grid-cols-2 gap-3">
            <label className="field-label">Identificador (nº)<input required type="number" min="1" className="field" value={form.tableNumber} onChange={set("tableNumber")} /></label>
            <label className="field-label">Nombre<input maxLength={120} placeholder="Terraza, VIP..." className="field" value={form.name} onChange={set("name")} /></label>
            <label className="field-label">Capacidad<input required type="number" min="1" className="field" value={form.capacity} onChange={set("capacity")} /></label>
            <label className="field-label">Piso<input required type="number" min="1" className="field" value={form.floor} onChange={set("floor")} /></label>
            <label className="field-label">
              Zona
              <select className="field" value={form.zoneId} onChange={set("zoneId")}>
                <option value="">Sin zona</option>
                {zones.map(([id, name]) => (
                  <option key={id} value={id}>{name}</option>
                ))}
              </select>
            </label>
            <label className="field-label">
              Grupo responsable
              <select className="field" value={form.groupId} onChange={set("groupId")}>
                <option value="">Todos los meseros</option>
                {groups.map((g) => (
                  <option key={g.id} value={g.id}>{g.name}</option>
                ))}
              </select>
            </label>
            <label className="field-label">Orden<input type="number" min="0" className="field" value={form.sortOrder} onChange={set("sortOrder")} /></label>
            <label className="field-label">
              Estado operativo
              <select className="field" value={form.status} onChange={set("status")}>
                <option value="AVAILABLE">En servicio</option>
                <option value="MAINTENANCE">Mantenimiento</option>
              </select>
            </label>
            <label className="field-label">Precio de reserva<input type="number" min="0" step="0.01" className="field" value={form.price} onChange={set("price")} /></label>
            <div className="grid grid-cols-2 gap-2">
              <label className="field-label">Col.<input type="number" min="1" className="field" value={form.gridX} onChange={set("gridX")} /></label>
              <label className="field-label">Fila<input type="number" min="1" className="field" value={form.gridY} onChange={set("gridY")} /></label>
            </div>
            <button type="submit" className="btn btn-primary bg-metal col-span-2">Guardar</button>
          </form>
        </Modal>
      )}
    </div>
  );
}
