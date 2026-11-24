import { useCallback, useEffect, useState } from "react";
import { Pencil, Plus } from "lucide-react";
import Modal from "./Modal";
import { fetchStaff } from "../../api/staffApi";
import { fetchDrivers, fetchPlatforms, saveDriver, savePlatform } from "../../api/opsApi";
import { ErrorState, Loader } from "../LoadState";

/** Plataformas de domicilio (lista libre) y domiciliarios, con o sin cuenta en la app. */
export default function DeliveryAdmin() {
  const [platforms, setPlatforms] = useState(null);
  const [drivers, setDrivers] = useState([]);
  const [accounts, setAccounts] = useState([]);
  const [status, setStatus] = useState("");
  const [platform, setPlatform] = useState(null);
  const [driver, setDriver] = useState(null);

  const load = useCallback(async () => {
    try {
      const [p, d, s] = await Promise.all([fetchPlatforms(), fetchDrivers(), fetchStaff()]);
      setPlatforms(p);
      setDrivers(d);
      setAccounts(s.filter((u) => u.role === "DELIVERY" && u.active));
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
      setStatus(ok);
      await load();
    } catch (err) {
      setStatus(err.message);
    }
  };

  if (!platforms) return status ? <ErrorState message={status} onRetry={load} /> : <Loader label="Cargando..." />;

  return (
    <div className="grid gap-6 lg:grid-cols-2">
      {status && <p className="text-sm text-zinc-300 lg:col-span-2" role="status">{status}</p>}
      <section className="zone-band">
        <div className="mb-3 flex items-center justify-between">
          <h3 className="text-sm uppercase tracking-[.18em] text-gold">Plataformas</h3>
          <button type="button" className="btn btn-sm" onClick={() => setPlatform({ name: "", active: true })}>
            <Plus className="h-4 w-4" /> Agregar
          </button>
        </div>
        <ul className="space-y-1">
          {platforms.length === 0 && <li className="text-sm text-zinc-500">Agrega Rappi, DiDi, domicilio propio u otras.</li>}
          {platforms.map((p) => (
            <li key={p.id} className="flex items-center justify-between border-b border-white/5 py-2 text-sm">
              <span className={p.active ? "text-zinc-200" : "text-zinc-500 line-through"}>{p.name}</span>
              <button type="button" onClick={() => setPlatform(p)} className="grid h-9 w-9 place-items-center text-zinc-400 hover:text-champagne" aria-label={`Editar ${p.name}`}>
                <Pencil className="h-4 w-4" />
              </button>
            </li>
          ))}
        </ul>
      </section>

      <section className="zone-band">
        <div className="mb-3 flex items-center justify-between">
          <h3 className="text-sm uppercase tracking-[.18em] text-gold">Domiciliarios</h3>
          <button type="button" className="btn btn-sm" onClick={() => setDriver({ name: "", phone: "", userId: "", active: true })}>
            <Plus className="h-4 w-4" /> Agregar
          </button>
        </div>
        <ul className="space-y-1">
          {drivers.length === 0 && <li className="text-sm text-zinc-500">Sin domiciliarios registrados.</li>}
          {drivers.map((d) => (
            <li key={d.id} className="flex items-center justify-between gap-2 border-b border-white/5 py-2 text-sm">
              <span className="min-w-0">
                <span className={d.active ? "text-zinc-200" : "text-zinc-500 line-through"}>{d.name}</span>
                <span className="block text-xs text-zinc-500">
                  {d.userEmail ? `Cuenta: ${d.userEmail}` : "Sin cuenta en la app"} · {d.activeDeliveries} activos
                </span>
              </span>
              <button type="button" onClick={() => setDriver({ ...d, phone: d.phone || "", userId: d.userId ?? "" })} className="grid h-9 w-9 place-items-center text-zinc-400 hover:text-champagne" aria-label={`Editar ${d.name}`}>
                <Pencil className="h-4 w-4" />
              </button>
            </li>
          ))}
        </ul>
        <p className="mt-3 text-xs text-zinc-500">
          Para que un domiciliario use la app, créalo en Personal con el rol Domiciliario y vincúlalo aquí.
        </p>
      </section>

      {platform && (
        <Modal title={platform.id ? "Editar plataforma" : "Nueva plataforma"} onClose={() => setPlatform(null)}>
          <form
            className="space-y-3"
            onSubmit={(e) => {
              e.preventDefault();
              act(async () => {
                await savePlatform(platform.id, { name: platform.name, active: platform.active });
                setPlatform(null);
              }, "Plataforma guardada.");
            }}
          >
            <label className="field-label">Nombre<input required maxLength={60} className="field" value={platform.name} onChange={(e) => setPlatform({ ...platform, name: e.target.value })} /></label>
            <label className="flex items-center gap-2 text-sm text-zinc-300">
              <input type="checkbox" className="h-4 w-4" checked={platform.active} onChange={(e) => setPlatform({ ...platform, active: e.target.checked })} />
              Activa
            </label>
            <button type="submit" className="btn btn-primary bg-metal w-full">Guardar</button>
          </form>
        </Modal>
      )}

      {driver && (
        <Modal title={driver.id ? "Editar domiciliario" : "Nuevo domiciliario"} onClose={() => setDriver(null)}>
          <form
            className="space-y-3"
            onSubmit={(e) => {
              e.preventDefault();
              act(async () => {
                await saveDriver(driver.id, {
                  name: driver.name,
                  phone: driver.phone || null,
                  userId: driver.userId ? Number(driver.userId) : null,
                  active: driver.active
                });
                setDriver(null);
              }, "Domiciliario guardado.");
            }}
          >
            <label className="field-label">Nombre<input required maxLength={120} className="field" value={driver.name} onChange={(e) => setDriver({ ...driver, name: e.target.value })} /></label>
            <label className="field-label">Teléfono<input maxLength={40} inputMode="tel" className="field" value={driver.phone} onChange={(e) => setDriver({ ...driver, phone: e.target.value })} /></label>
            <label className="field-label">
              Cuenta en la app
              <select className="field" value={driver.userId} onChange={(e) => setDriver({ ...driver, userId: e.target.value })}>
                <option value="">Sin cuenta</option>
                {accounts.map((a) => (
                  <option key={a.id} value={a.id}>{a.name} · {a.email}</option>
                ))}
              </select>
            </label>
            <label className="flex items-center gap-2 text-sm text-zinc-300">
              <input type="checkbox" className="h-4 w-4" checked={driver.active} onChange={(e) => setDriver({ ...driver, active: e.target.checked })} />
              Activo
            </label>
            <button type="submit" className="btn btn-primary bg-metal w-full">Guardar</button>
          </form>
        </Modal>
      )}
    </div>
  );
}
