import { useEffect, useState } from "react";
import { Pencil, Plus, Trash2, UserPlus } from "lucide-react";
import Modal from "./Modal";
import { createStaff, deleteStaff, fetchStaff, updateStaff } from "../../api/staffApi";

const EMPTY = { name: "", email: "", password: "", role: "EMPLOYEE", active: true };
const ROLE_LABEL = { EMPLOYEE: "Mesero", COOK: "Cocinero" };

export default function StaffAdmin() {
  const [staff, setStaff] = useState([]);
  const [status, setStatus] = useState("");
  const [editing, setEditing] = useState(null); // null | {} para crear | objeto para editar
  const [form, setForm] = useState(EMPTY);

  const load = async () => {
    try {
      setStaff(await fetchStaff());
    } catch (err) {
      setStatus(err.message);
    }
  };

  useEffect(() => {
    load();
  }, []);

  const openCreate = () => {
    setForm(EMPTY);
    setEditing({});
  };

  const openEdit = (member) => {
    setForm({ ...member, password: "" });
    setEditing(member);
  };

  const save = async (event) => {
    event.preventDefault();
    setStatus("");
    try {
      const payload = {
        name: form.name,
        email: form.email,
        role: form.role,
        active: form.active,
        password: form.password ? form.password : undefined
      };
      if (editing?.id) await updateStaff(editing.id, payload);
      else await createStaff(payload);
      setEditing(null);
      await load();
    } catch (err) {
      setStatus(err.message);
    }
  };

  const remove = async (id) => {
    try {
      await deleteStaff(id);
      await load();
    } catch (err) {
      setStatus(err.message);
    }
  };

  return (
    <div>
      <div className="mb-4 flex items-center justify-between">
        <p className="text-sm text-zinc-400">Meseros y cocineros del restaurante.</p>
        <button
          type="button"
          onClick={openCreate}
          className="inline-flex items-center gap-2 rounded-full border border-gold/60 bg-metal px-4 py-2 text-sm font-semibold text-carbon shadow-gold transition hover:brightness-110"
        >
          <UserPlus className="h-4 w-4" /> Nuevo
        </button>
      </div>

      {status && <p className="mb-3 text-sm text-red-300">{status}</p>}

      <div className="overflow-x-auto border border-white/10">
        <table className="w-full text-left text-sm">
          <thead className="bg-white/[.04] text-xs uppercase tracking-wider text-zinc-400">
            <tr>
              <th className="px-4 py-3">Nombre</th>
              <th className="px-4 py-3">Correo</th>
              <th className="px-4 py-3">Rol</th>
              <th className="px-4 py-3">Estado</th>
              <th className="px-4 py-3 text-right">Acciones</th>
            </tr>
          </thead>
          <tbody>
            {staff.map((m) => (
              <tr key={m.id} className="border-t border-white/5">
                <td className="px-4 py-3 text-champagne">{m.name}</td>
                <td className="px-4 py-3 text-zinc-300">{m.email}</td>
                <td className="px-4 py-3 text-zinc-300">{ROLE_LABEL[m.role] || m.role}</td>
                <td className="px-4 py-3">
                  <span className={`chip ${m.active ? "chip-ready" : "chip-muted"}`}>
                    {m.active ? "Activo" : "Inactivo"}
                  </span>
                </td>
                <td className="px-4 py-3">
                  <div className="flex justify-end gap-2">
                    <button
                      type="button"
                      onClick={() => openEdit(m)}
                      className="text-zinc-400 hover:text-champagne"
                    >
                      <Pencil className="h-4 w-4" />
                    </button>
                    <button
                      type="button"
                      onClick={() => remove(m.id)}
                      className="text-zinc-400 hover:text-red-400"
                    >
                      <Trash2 className="h-4 w-4" />
                    </button>
                  </div>
                </td>
              </tr>
            ))}
            {staff.length === 0 && (
              <tr>
                <td colSpan={5} className="px-4 py-6 text-center text-zinc-500">
                  Sin personal registrado.
                </td>
              </tr>
            )}
          </tbody>
        </table>
      </div>

      {editing && (
        <Modal
          title={editing.id ? "Editar personal" : "Nuevo personal"}
          onClose={() => setEditing(null)}
        >
          <form onSubmit={save} className="space-y-4">
            <label className="input-label">
              Nombre
              <span className="input-shell">
                <input
                  value={form.name}
                  required
                  onChange={(e) => setForm({ ...form, name: e.target.value })}
                />
              </span>
            </label>
            <label className="input-label">
              Correo
              <span className="input-shell">
                <input
                  type="email"
                  value={form.email}
                  required
                  onChange={(e) => setForm({ ...form, email: e.target.value })}
                />
              </span>
            </label>
            <label className="input-label">
              Contraseña {editing.id && <span className="text-zinc-500">(dejar vacío para no cambiar)</span>}
              <span className="input-shell">
                <input
                  type="password"
                  value={form.password}
                  required={!editing.id}
                  onChange={(e) => setForm({ ...form, password: e.target.value })}
                />
              </span>
            </label>
            <div className="flex items-center gap-4">
              <label className="input-label flex-1">
                Rol
                <span className="input-shell">
                  <select
                    value={form.role}
                    onChange={(e) => setForm({ ...form, role: e.target.value })}
                    className="w-full bg-transparent text-champagne outline-none"
                  >
                    <option value="EMPLOYEE">Mesero</option>
                    <option value="COOK">Cocinero</option>
                  </select>
                </span>
              </label>
              <label className="mt-6 inline-flex items-center gap-2 text-sm text-zinc-300">
                <input
                  type="checkbox"
                  checked={form.active}
                  onChange={(e) => setForm({ ...form, active: e.target.checked })}
                  className="h-4 w-4 accent-[#d8a943]"
                />
                Activo
              </label>
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
