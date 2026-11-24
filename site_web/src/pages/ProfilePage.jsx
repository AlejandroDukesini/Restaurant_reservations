import { useCallback, useEffect, useRef, useState } from "react";
import { Camera, Monitor, Moon, Palette, Sun, Trash2 } from "lucide-react";
import Avatar from "../components/Avatar";
import Layout from "../components/Layout";
import ThemeEditor from "../components/admin/ThemeEditor";
import { shrinkImage } from "../components/admin/ImageField";
import { ErrorState, Loader } from "../components/LoadState";
import { useConfirm } from "../components/ConfirmDialog";
import { fetchProfile, removeAvatar, saveProfile, uploadAvatar } from "../api/opsApi";
import { useAuth } from "../auth/AuthContext";
import { useTheme } from "../theme/ThemeProvider";
import { ROLE_LABEL } from "../utils/status";

const DEVICE_MODES = [
  ["brand", "Según mi tema", Palette],
  ["dark", "Siempre oscuro", Moon],
  ["light", "Siempre claro", Sun]
];

/** Perfil propio: nombre visible, foto y colores. Solo afecta a la propia cuenta. */
export default function ProfilePage() {
  const { updateProfile } = useAuth();
  const { refresh, pref, setPref } = useTheme();
  const confirm = useConfirm();
  const [profile, setProfile] = useState(null);
  const [name, setName] = useState("");
  const [error, setError] = useState("");
  const [message, setMessage] = useState("");
  const [busy, setBusy] = useState(false);
  const [version, setVersion] = useState(0);
  const fileInput = useRef(null);

  const load = useCallback(async () => {
    try {
      const data = await fetchProfile();
      setProfile(data);
      setName(data.name);
      setError("");
    } catch (err) {
      setError(err.message);
    }
  }, []);

  useEffect(() => {
    load();
  }, [load]);

  const apply = async (data, success) => {
    setProfile(data);
    updateProfile({ name: data.name, avatarUrl: data.avatarUrl || null });
    await refresh();
    setMessage(success);
  };

  const run = async (fn, success) => {
    setBusy(true);
    setMessage("");
    try {
      await apply(await fn(), success);
    } catch (err) {
      setMessage(err.message);
    } finally {
      setBusy(false);
    }
  };

  const pickPhoto = async (event) => {
    const file = event.target.files?.[0];
    event.target.value = "";
    if (!file) return;
    if (!["image/png", "image/jpeg", "image/webp"].includes(file.type)) {
      setMessage("Formato no permitido. Usa PNG, JPEG o WebP.");
      return;
    }
    run(async () => uploadAvatar(await shrinkImage(file, 512)), "Foto de perfil actualizada.");
  };

  if (!profile) {
    return (
      <Layout subtitle="Mi cuenta" title="Perfil">
        {error ? <ErrorState message={error} onRetry={load} /> : <Loader label="Cargando tu perfil..." />}
      </Layout>
    );
  }

  return (
    <Layout subtitle="Mi cuenta" title="Perfil y apariencia">
      {message && <p className="mb-4 text-sm text-zinc-300" role="status">{message}</p>}
      <div className="grid gap-6 lg:grid-cols-[340px_1fr]">
        <section className="zone-band h-fit space-y-5">
          <div className="flex flex-col items-center gap-3 text-center">
            <div className="relative">
              <Avatar url={profile.avatarUrl} name={profile.name} size={112} />
              <button
                type="button"
                onClick={() => fileInput.current?.click()}
                disabled={busy}
                className="absolute bottom-0 right-0 grid h-10 w-10 place-items-center rounded-full border border-white/20 bg-metal shadow-gold"
                aria-label="Cambiar foto de perfil"
              >
                <Camera className="h-5 w-5" />
              </button>
              <input ref={fileInput} type="file" accept="image/png,image/jpeg,image/webp" className="hidden" onChange={pickPhoto} />
            </div>
            <div>
              <p className="text-lg font-semibold text-champagne">{profile.name}</p>
              <p className="text-xs uppercase tracking-[.18em] text-gold">{ROLE_LABEL[profile.role] || profile.role}</p>
              <p className="text-xs text-zinc-500">{profile.email}</p>
            </div>
            {profile.avatarUrl && (
              <button
                type="button"
                disabled={busy}
                onClick={async () =>
                  (await confirm({ title: "¿Quitar tu foto de perfil?", confirmLabel: "Quitar", tone: "danger" })) &&
                  run(removeAvatar, "Foto eliminada.")
                }
                className="btn btn-sm btn-danger"
              >
                <Trash2 className="h-4 w-4" /> Quitar foto
              </button>
            )}
          </div>

          <form
            onSubmit={(e) => {
              e.preventDefault();
              run(() => saveProfile({ name: name.trim(), theme: profile.theme }), "Nombre actualizado.");
            }}
            className="space-y-2 border-t border-white/10 pt-4"
          >
            <label className="field-label">
              Nombre visible
              <input required minLength={2} maxLength={120} className="field" value={name} onChange={(e) => setName(e.target.value)} />
            </label>
            <p className="text-xs text-zinc-500">Es el nombre que ven tus compañeros en mensajes, pedidos y mesas.</p>
            <button type="submit" disabled={busy || name.trim() === profile.name} className="btn btn-primary bg-metal w-full">
              Guardar nombre
            </button>
          </form>

          <div className="space-y-2 border-t border-white/10 pt-4">
            <p className="field-label">
              <span className="flex items-center gap-2">
                <Monitor className="h-4 w-4" /> Este dispositivo
              </span>
            </p>
            <div className="grid gap-2" role="radiogroup" aria-label="Modo en este dispositivo">
              {DEVICE_MODES.map(([key, label, Icon]) => (
                <button
                  key={key}
                  type="button"
                  role="radio"
                  aria-checked={pref === key}
                  onClick={() => setPref(key)}
                  className={`btn justify-start ${pref === key ? "btn-primary" : ""}`}
                >
                  <Icon className="h-4 w-4" /> {label}
                </button>
              ))}
            </div>
            <p className="text-xs text-zinc-500">Oscuro y claro generan la variante de tus colores en ese modo.</p>
          </div>
        </section>

        <section className="zone-band">
          <h3 className="mb-1 text-sm uppercase tracking-[.18em] text-gold">Mis colores</h3>
          <p className="mb-4 text-sm text-zinc-400">
            Solo cambian lo que ves tú. Ahora heredas: <span className="text-champagne">{profile.inheritedFrom}</span>.
          </p>
          <ThemeEditor
            key={version}
            initialTheme={profile.theme}
            fallbackTheme={profile.inheritedTheme}
            allowInherit
            inheritLabel={`Usar los colores de ${profile.inheritedFrom.toLowerCase()}`}
            showLogo={false}
            saveLabel="Guardar mis colores"
            onSave={(theme) =>
              run(async () => {
                const data = await saveProfile({ name: profile.name, theme });
                setVersion((v) => v + 1);
                return data;
              }, theme ? "Tus colores se aplicaron en todos tus dispositivos." : "Vuelves a usar los colores compartidos.")
            }
          />
        </section>
      </div>
    </Layout>
  );
}
