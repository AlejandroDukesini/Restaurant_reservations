import { useCallback, useEffect, useState } from "react";
import ThemeEditor from "./ThemeEditor";
import ImageField from "./ImageField";
import { fetchRestaurantBranding, saveRestaurantBranding } from "../../api/opsApi";
import { useTheme } from "../../theme/ThemeProvider";
import { ErrorState, Loader } from "../LoadState";

function mediaIdFromUrl(url) {
  return url ? url.split("/").pop() : null;
}

/** Identidad del restaurante: nombre, portada, logo y paleta (heredada por todos los grupos sin tema propio). */
export default function BrandingAdmin() {
  const { refresh } = useTheme();
  const [data, setData] = useState(null);
  const [name, setName] = useState("");
  const [cover, setCover] = useState(null);
  const [status, setStatus] = useState("");
  const [version, setVersion] = useState(0);

  const load = useCallback(async () => {
    try {
      const branding = await fetchRestaurantBranding();
      setData(branding);
      setName(branding.restaurantName);
      setCover(branding.coverUrl ? { id: mediaIdFromUrl(branding.coverUrl), url: branding.coverUrl } : null);
      setStatus("");
    } catch (err) {
      setStatus(err.message);
    }
  }, []);

  useEffect(() => {
    load();
  }, [load]);

  if (!data) return status ? <ErrorState message={status} onRetry={load} /> : <Loader label="Cargando identidad..." />;

  const save = async (theme, logo) => {
    setStatus("");
    try {
      await saveRestaurantBranding({
        restaurantName: name.trim(),
        theme: theme || undefined,
        logoMediaId: logo?.id || null,
        coverMediaId: cover?.id || null
      });
      setStatus("Identidad guardada. Los dispositivos conectados la reciben al instante.");
      await refresh();
      await load();
      setVersion((v) => v + 1);
    } catch (err) {
      setStatus(err.message);
    }
  };

  return (
    <div className="space-y-4">
      {status && <p className="text-sm text-zinc-300" role="status">{status}</p>}
      <ThemeEditor
        key={version}
        initialTheme={data.source === "default" ? null : data.theme}
        initialLogo={data.logoUrl ? { id: mediaIdFromUrl(data.logoUrl), url: data.logoUrl } : null}
        onSave={save}
      >
        <div className="grid gap-4 sm:grid-cols-2">
          <label className="field-label">
            Nombre del restaurante
            <input required maxLength={120} className="field" value={name} onChange={(e) => setName(e.target.value)} />
          </label>
          <ImageField label="Imagen de portada" url={cover?.url} onChange={setCover} maxSide={1600} aspect="16/9" />
        </div>
      </ThemeEditor>
    </div>
  );
}
