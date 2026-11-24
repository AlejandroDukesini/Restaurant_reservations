import { useEffect, useState } from "react";
import { useConfirm } from "../ConfirmDialog";
import { Pencil, Trash2, UserPlus } from "lucide-react";
import Modal from "./Modal";
import { createStaff, deleteStaff, fetchStaff, updateStaff } from "../../api/staffApi";
import { ErrorState, Loader } from "../LoadState";
import { PERMISSION_LABEL, ROLE_LABEL } from "../../utils/status";

const EMPTY = { name: "", email: "", password: "", phone: "", role: "EMPLOYEE", active: true, permissions: [] };
const STAFF_ROLES = ["EMPLOYEE", "COOK", "DELIVERY"];

export default function StaffAdmin() {
  const [staff, setStaff] = useState(null); // null = aún sin primera carga
  const [status, setStatus] = useState("");
  const [editing, setEditing] = useState(null); // null | {} para crear | objeto para editar
  const [form, setForm] = useState(EMPTY);
  const confirm = useConfirm();

  const load = async () => {
    setStatus("");
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
    setForm({ ...EMPTY, ...member, password: "", phone: member.phone || "", permissions: member.permissions || [] });
    setEditing(member);
  };

  const togglePermission = (key) =>
    setForm((f) => ({
      ...f,
      permissions: f.permissions.includes(key) ? f.permissions.filter((p) => p !== key) : [...f.permissions, key]
    }));

  const save = async (event) => {
    event.preventDefault();
    setStatus("");
    try {
      const payload = {
        name: form.name,
        email: form.email,
        role: form.role,
        active: form.active,
        phone: form.phone || undefined,
        permissions: form.permissions,
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
    const ok = await confirm({
      title: "¿Dar de baja a este empleado?",
      message: "Perderá el acceso de inmediato en todos sus dispositivos.",
      confirmLabel: "Dar de baja",
      tone: "danger"
    });
    if (!ok) return;
    try {
      await deleteStaff(id);
      await load();
    } catch (err) {
      setStatus(err.message);
    }
  };

  return (
    <div>
      <div className="mb-4 flex flex-wrap items-center justify-between gap-3">
        <p className="text-sm text-zinc-400">
          Meseros, cocina y domiciliarios. Los permisos extra nunca incluyen funciones de gerente.
        </p>
        <button type="button" onClick={openCreate} className="btn btn-primary bg-metal">
          <UserPlus className="h-4 w-4" /> Nuevo
        </button>
      </div>

      {status && staff && <p className="motion-fade mb-3 text-sm text-red-300">{status}</p>}

      {!staff ? (
        status ? <ErrorState message={status} onRetry={load} /> : <Loader label="Cargando personal..." />
      ) : (
        <div className="overflow-x-auto border border-white/10">
          <table className="w-full min-w-[720px] text-left text-sm">
            <thead className="bg-white/[.04] text-xs uppercase tracking-wider text-zinc-400">
              <tr>
                <th className="px-4 py-3">Nombre</th>
                <th className="px-4 py-3">Correo</th>
                <th className="px-4 py-3">Rol</th>
                <th className="px-4 py-3">Grupos</th>
                <th className="px-4 py-3">Permisos extra</th>
                <th className="px-4 py-3">Estado</th>
                <th className="px-4 py-3 text-right">Acciones</th>
              </tr>
            </thead>
            <tbody>
              {staff.map((m, index) => (
                <tr key={m.id} className="motion-fade border-t border-white/5" style={{ "--i": index }}>
                  <td className="px-4 py-3 text-champagne">{m.name}</td>
                  <td className="px-4 py-3 text-zinc-300">{m.email}</td>
                  <td className="px-4 py-3 text-zinc-300">{ROLE_LABEL[m.role] || m.role}</td>
                  <td className="px-4 py-3 text-xs text-zinc-400">{(m.groups || []).join(", ") || "—"}</td>
                  <td className="px-4 py-3 text-xs text-zinc-400">
                    {(m.permissions || []).map((p) => PERMISSION_LABEL[p] || p).join(", ") || "—"}
                  </td>
                  <td className="px-4 py-3">
                    <span className={`chip ${m.active ? "chip-ready" : "chip-muted"}`}>{m.active ? "Activo" : "Inactivo"}</span>
                  </td>
                  <td className="px-4 py-3">
                    <div className="flex justify-end gap-1">
                      <button type="button" onClick={() => openEdit(m)} className="grid h-9 w-9 place-items-center text-zinc-400 hover:text-champagne" aria-label={`Editar ${m.name}`}>
                        <Pencil className="h-4 w-4" />
                      </button>
                      <button type="button" onClick={() => remove(m.id)} className="grid h-9 w-9 place-items-center text-zinc-400 hover:text-red-400" aria-label={`Dar de baja a ${m.name}`}>
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
        <Modal title={editing.id ? "Editar empleado" : "Nuevo empleado"} onClose={() => setEditing(null)}>
          <form onSubmit={save} className="space-y-3">
            <label className="field-label">Nombre<input required maxLength={120} className="field" value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })} /></label>
            <label className="field-label">Correo<input required type="email" maxLength={254} className="field" value={form.email} onChange={(e) => setForm({ ...form, email: e.target.value })} /></label>
            <label className="field-label">
              {editing.id ? "Nueva contraseña (opcional)" : "Contraseña"}
              <input
                type="password"
                minLength={12}
                maxLength={128}
                required={!editing.id}
                autoComplete="new-password"
                className="field"
                value={form.password}
                onChange={(e) => setForm({ ...form, password: e.target.value })}
              />
            </label>
            <label className="field-label">Teléfono<input maxLength={40} inputMode="tel" className="field" value={form.phone} onChange={(e) => setForm({ ...form, phone: e.target.value })} /></label>
            <label className="field-label">
              Rol
              <select className="field" value={form.role} onChange={(e) => setForm({ ...form, role: e.target.value })}>
                {STAFF_ROLES.map((r) => (
                  <option key={r} value={r}>{ROLE_LABEL[r]}</option>
                ))}
              </select>
            </label>
            <fieldset className="field-label">
              <legend className="mb-1">Permisos adicionales</legend>
              <div className="grid gap-1 normal-case tracking-normal sm:grid-cols-2">
                {Object.entries(PERMISSION_LABEL).map(([key, label]) => (
                  <label key={key} className="flex items-center gap-2 text-sm text-zinc-300">
                    <input type="checkbox" className="h-4 w-4" checked={form.permissions.includes(key)} onChange={() => togglePermission(key)} />
                    {label}
                  </label>
                ))}
              </div>
            </fieldset>
            <label className="flex items-center gap-2 text-sm text-zinc-300">
              <input type="checkbox" className="h-4 w-4" checked={form.active} onChange={(e) => setForm({ ...form, active: e.target.checked })} />
              Cuenta activa
            </label>
            <button type="submit" className="btn btn-primary bg-metal w-full">Guardar</button>
          </form>
        </Modal>
      )}
    </div>
  );
}
