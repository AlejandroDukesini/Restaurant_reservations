import { useCallback, useEffect, useState } from "react";
import { useConfirm } from "../ConfirmDialog";
import { Activity, Palette, Pencil, Plus, Trash2, Users } from "lucide-react";
import Modal from "./Modal";
import ThemeEditor from "./ThemeEditor";
import { fetchStaff } from "../../api/staffApi";
import { createGroup, deleteGroup, fetchGroupActivity, fetchGroups, saveGroupBranding, setGroupMembers, updateGroup } from "../../api/opsApi";
import { useAuth } from "../../auth/AuthContext";
import { ErrorState, Loader } from "../LoadState";
import { ROLE_LABEL, timeOf } from "../../utils/status";

export default function GroupsAdmin() {
  const { userId, session } = useAuth();
  const [groups, setGroups] = useState(null);
  const [staff, setStaff] = useState([]);
  const [status, setStatus] = useState("");
  const [editing, setEditing] = useState(null);
  const [members, setMembers] = useState(null);
  const [branding, setBranding] = useState(null);
  const [activity, setActivity] = useState(null);
  const confirm = useConfirm();

  const load = useCallback(async () => {
    try {
      const [g, s] = await Promise.all([fetchGroups(), fetchStaff()]);
      setGroups(g);
      setStaff(s.filter((m) => m.active));
      setStatus("");
    } catch (err) {
      setStatus(err.message);
    }
  }, []);

  useEffect(() => {
    load();
  }, [load]);

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

  // El gerente también puede pertenecer a grupos (para participar en su chat).
  const people = [{ id: userId, name: `${session?.name || session?.email} (tú)`, role: "ADMIN" }, ...staff];

  return (
    <div>
      <div className="mb-4 flex flex-wrap items-center justify-between gap-3">
        <p className="text-sm text-zinc-400">
          Áreas, equipos o sedes: cada grupo tiene sus integrantes, su chat y, si quieres, su identidad visual.
        </p>
        <button type="button" onClick={() => setEditing({ name: "", description: "", chatEnabled: true })} className="btn btn-primary bg-metal">
          <Plus className="h-4 w-4" /> Nuevo grupo
        </button>
      </div>
      {status && <p className="mb-3 text-sm text-zinc-300" role="status">{status}</p>}

      {!groups ? (
        status ? <ErrorState message={status} onRetry={load} /> : <Loader label="Cargando grupos..." />
      ) : groups.length === 0 ? (
        <p className="text-sm text-zinc-500">Aún no hay grupos.</p>
      ) : (
        <div className="grid gap-3 md:grid-cols-2 xl:grid-cols-3">
          {groups.map((g) => (
            <article key={g.id} className="zone-band space-y-2">
              <div className="flex items-start justify-between gap-2">
                <div className="flex min-w-0 items-center gap-2">
                  {g.logoUrl && <img src={g.logoUrl} alt="" className="brand-logo h-8" />}
                  <div className="min-w-0">
                    <h3 className="truncate font-semibold text-champagne">{g.name}</h3>
                    {g.description && <p className="truncate text-xs text-zinc-500">{g.description}</p>}
                  </div>
                </div>
                {g.theme && (
                  <span className="flex gap-0.5" title="Tiene identidad propia">
                    {[g.theme.primary, g.theme.background, g.theme.accent].map((c) => (
                      <span key={c} className="h-4 w-4 border border-white/20" style={{ background: c }} />
                    ))}
                  </span>
                )}
              </div>
              <p className="text-xs text-zinc-400">
                {g.members.length} integrantes · chat {g.chatEnabled ? "activo" : "desactivado"}
              </p>
              <p className="line-clamp-2 text-xs text-zinc-500">{g.members.map((m) => m.name).join(", ") || "Sin integrantes"}</p>
              <div className="flex flex-wrap gap-1 pt-1">
                <button type="button" className="btn btn-sm" onClick={() => setMembers({ group: g, ids: g.members.map((m) => m.id) })}>
                  <Users className="h-4 w-4" /> Integrantes
                </button>
                <button type="button" className="btn btn-sm" onClick={() => setBranding(g)}>
                  <Palette className="h-4 w-4" /> Identidad
                </button>
                <button
                  type="button"
                  className="btn btn-sm"
                  onClick={async () => {
                    try {
                      setActivity({ group: g, data: await fetchGroupActivity(g.id) });
                    } catch (err) {
                      setStatus(err.message);
                    }
                  }}
                  aria-label={`Actividad de ${g.name}`}
                >
                  <Activity className="h-4 w-4" />
                </button>
                <button type="button" className="btn btn-sm" onClick={() => setEditing(g)} aria-label={`Editar ${g.name}`}>
                  <Pencil className="h-4 w-4" />
                </button>
                <button
                  type="button"
                  className="btn btn-sm btn-danger"
                  onClick={async () =>
                    (await confirm({
                      title: `¿Eliminar el grupo ${g.name}?`,
                      message: "Sus integrantes pierden el chat y la identidad del grupo. El historial se conserva.",
                      confirmLabel: "Eliminar",
                      tone: "danger"
                    })) && act(() => deleteGroup(g.id), "Grupo eliminado.")
                  }
                  aria-label={`Eliminar ${g.name}`}
                >
                  <Trash2 className="h-4 w-4" />
                </button>
              </div>
            </article>
          ))}
        </div>
      )}

      {editing && (
        <Modal title={editing.id ? "Editar grupo" : "Nuevo grupo"} onClose={() => setEditing(null)}>
          <form
            className="space-y-3"
            onSubmit={(e) => {
              e.preventDefault();
              const payload = { name: editing.name, description: editing.description || null, chatEnabled: editing.chatEnabled };
              act(async () => {
                if (editing.id) await updateGroup(editing.id, payload);
                else await createGroup(payload);
                setEditing(null);
              }, "Grupo guardado.");
            }}
          >
            <label className="field-label">Nombre<input required maxLength={80} className="field" value={editing.name} onChange={(e) => setEditing({ ...editing, name: e.target.value })} /></label>
            <label className="field-label">Descripción<input maxLength={255} className="field" value={editing.description || ""} onChange={(e) => setEditing({ ...editing, description: e.target.value })} /></label>
            <label className="flex items-center gap-2 text-sm text-zinc-300">
              <input type="checkbox" className="h-4 w-4" checked={editing.chatEnabled} onChange={(e) => setEditing({ ...editing, chatEnabled: e.target.checked })} />
              Mensajería activa
            </label>
            <button type="submit" className="btn btn-primary bg-metal w-full">Guardar</button>
          </form>
        </Modal>
      )}

      {members && (
        <Modal title={`Integrantes · ${members.group.name}`} onClose={() => setMembers(null)}>
          <div className="max-h-[55vh] space-y-1 overflow-y-auto">
            {people.map((p) => (
              <label key={p.id} className="flex items-center gap-3 px-1 py-2 text-sm text-zinc-200">
                <input
                  type="checkbox"
                  className="h-4 w-4"
                  checked={members.ids.includes(p.id)}
                  onChange={() =>
                    setMembers((m) => ({ ...m, ids: m.ids.includes(p.id) ? m.ids.filter((x) => x !== p.id) : [...m.ids, p.id] }))
                  }
                />
                <span className="flex-1">{p.name}</span>
                <span className="text-xs text-zinc-500">{ROLE_LABEL[p.role] || p.role}</span>
              </label>
            ))}
          </div>
          <button
            type="button"
            className="btn btn-primary bg-metal mt-4 w-full"
            onClick={() =>
              act(async () => {
                await setGroupMembers(members.group.id, members.ids);
                setMembers(null);
              }, "Integrantes actualizados.")
            }
          >
            Guardar integrantes
          </button>
        </Modal>
      )}

      {branding && (
        <Modal title={`Identidad visual · ${branding.name}`} onClose={() => setBranding(null)} wide>
          <ThemeEditor
            initialTheme={branding.theme}
            initialLogo={branding.logoMediaId ? { id: branding.logoMediaId, url: branding.logoUrl } : null}
            allowInherit
            onSave={(theme, logo) =>
              act(async () => {
                await saveGroupBranding(branding.id, { theme, logoMediaId: logo?.id || null });
                setBranding(null);
              }, theme ? "Identidad del grupo guardada: sus integrantes la reciben de inmediato." : "El grupo usa ahora la identidad del restaurante.")
            }
          />
        </Modal>
      )}

      {activity && (
        <Modal title={`Actividad · ${activity.group.name}`} onClose={() => setActivity(null)}>
          <p className="text-sm text-zinc-300">
            {activity.data.messages} mensajes
            {activity.data.lastMessageAt ? ` · último a las ${timeOf(activity.data.lastMessageAt)}` : ""}
          </p>
          <h4 className="mt-4 mb-2 text-xs uppercase tracking-[.18em] text-gold">Dispositivos de los integrantes</h4>
          {activity.data.devices.length === 0 ? (
            <p className="text-sm text-zinc-500">Ningún integrante se ha conectado todavía.</p>
          ) : (
            <ul className="space-y-1 text-sm">
              {activity.data.devices.map((d, i) => (
                <li key={i} className="flex justify-between gap-2 border-b border-white/5 py-1">
                  <span className="text-zinc-200">{d.userName}</span>
                  <span className="text-zinc-400">{d.label}</span>
                  <span className="text-xs text-zinc-500">{new Date(d.lastSeenAt).toLocaleString("es")}</span>
                </li>
              ))}
            </ul>
          )}
        </Modal>
      )}
    </div>
  );
}
