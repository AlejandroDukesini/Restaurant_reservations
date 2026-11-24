import { useEffect, useState } from "react";
import { AlertTriangle, CheckCircle2, Eye, EyeOff, RotateCcw } from "lucide-react";
import ImageField from "./ImageField";
import { useTheme } from "../../theme/ThemeProvider";
import { COLOR_KEYS, DEFAULT_THEME, PRESETS, contrast, deriveVariant, isHex, readabilityProblems } from "../../theme/theme";
import { Moon, Sun } from "lucide-react";

/**
 * Editor de identidad: paletas predefinidas, selector de color por elemento, modo claro/oscuro,
 * logo (tamaño y posición) y validación de contraste en vivo con las mismas reglas que aplica
 * el servidor. "Vista previa" aplica el tema a toda la app en este dispositivo sin guardarlo.
 */
export default function ThemeEditor({
  initialTheme,
  initialLogo,
  onSave,
  allowInherit = false,
  inheritLabel = "Usar la identidad del restaurante (sin tema propio)",
  fallbackTheme = null,
  showLogo = true,
  saveLabel = "Guardar identidad",
  children
}) {
  const { setPreview } = useTheme();
  const [inherit, setInherit] = useState(allowInherit && !initialTheme);
  const [theme, setTheme] = useState({ ...DEFAULT_THEME, ...(initialTheme || fallbackTheme || {}) });
  const [logo, setLogo] = useState(initialLogo || null);
  const [previewing, setPreviewing] = useState(false);

  // La vista previa termina al salir del editor.
  useEffect(() => () => setPreview(null), [setPreview]);
  useEffect(() => {
    if (previewing && !inherit) setPreview(theme);
    else setPreview(null);
  }, [previewing, theme, inherit, setPreview]);

  const problems = inherit ? [] : readabilityProblems(theme);
  const invalid = !inherit && COLOR_KEYS.some(([key]) => !isHex(theme[key]));

  const setColor = (key, value) => setTheme((t) => ({ ...t, [key]: value, preset: "custom" }));
  // La paleta elegida se aplica en el modo actual: si el modo difiere, se genera su variante.
  const applyPreset = (key) =>
    setTheme((t) => {
      const preset = { ...t, ...PRESETS[key], preset: key };
      return preset.mode === t.mode ? preset : deriveVariant(preset, t.mode);
    });
  // Cambiar de modo regenera todos los colores (fondos, textos, estados) en la otra luminosidad.
  const switchMode = (mode) => setTheme((t) => (t.mode === mode ? t : deriveVariant(t, mode)));

  const save = () => {
    const { label, ...clean } = theme; // eslint-disable-line no-unused-vars
    onSave(inherit ? null : { ...clean, logoHeight: Number(clean.logoHeight) || 40 }, logo);
  };

  return (
    <div className="space-y-5">
      {children}

      {allowInherit && (
        <label className="flex items-center gap-2 text-sm text-zinc-300">
          <input type="checkbox" className="h-4 w-4" checked={inherit} onChange={(e) => setInherit(e.target.checked)} />
          {inheritLabel}
        </label>
      )}

      {!inherit && (
        <>
          <div>
            <p className="mb-2 text-xs uppercase tracking-[.18em] text-gold">Paletas predefinidas</p>
            <div className="flex flex-wrap gap-2">
              {Object.entries(PRESETS).map(([key, p]) => (
                <button
                  key={key}
                  type="button"
                  onClick={() => applyPreset(key)}
                  className={`btn btn-sm ${theme.preset?.startsWith(key) ? "btn-primary" : ""}`}
                  aria-pressed={Boolean(theme.preset?.startsWith(key))}
                >
                  <span className="flex gap-0.5" aria-hidden="true">
                    {[p.background, p.primary, p.accent].map((c) => (
                      <span key={c} className="h-3.5 w-3.5 border border-white/20" style={{ background: c }} />
                    ))}
                  </span>
                  {p.label}
                </button>
              ))}
              <button type="button" onClick={() => setTheme({ ...DEFAULT_THEME })} className="btn btn-sm" title="Restaurar colores predeterminados">
                <RotateCcw className="h-4 w-4" /> Predeterminado
              </button>
            </div>
          </div>

          <div className="grid gap-3 sm:grid-cols-2">
            <div className="field-label">
              Modo
              <div className="grid grid-cols-2 gap-2" role="radiogroup" aria-label="Modo">
                {[["dark", "Oscuro", Moon], ["light", "Claro", Sun]].map(([mode, label, Icon]) => (
                  <button
                    key={mode}
                    type="button"
                    role="radio"
                    aria-checked={theme.mode === mode}
                    onClick={() => switchMode(mode)}
                    className={`btn normal-case tracking-normal ${theme.mode === mode ? "btn-primary bg-metal" : ""}`}
                  >
                    <Icon className="h-4 w-4" /> {label}
                  </button>
                ))}
              </div>
            </div>
            <div className="flex items-end">
              <button type="button" onClick={() => setPreviewing((v) => !v)} className={`btn w-full ${previewing ? "btn-primary" : ""}`} aria-pressed={previewing}>
                {previewing ? <EyeOff className="h-4 w-4" /> : <Eye className="h-4 w-4" />}
                {previewing ? "Quitar vista previa" : "Vista previa en toda la app"}
              </button>
            </div>
          </div>

          <div className="grid gap-2 sm:grid-cols-2 lg:grid-cols-3">
            {COLOR_KEYS.map(([key, label]) => (
              <label key={key} className="flex items-center gap-2 border border-white/10 p-2 text-sm text-zinc-300">
                <input
                  type="color"
                  value={isHex(theme[key]) ? theme[key] : "#000000"}
                  onChange={(e) => setColor(key, e.target.value)}
                  className="h-9 w-10 shrink-0 cursor-pointer border-0 bg-transparent p-0"
                  aria-label={label}
                />
                <span className="min-w-0 flex-1 truncate">{label}</span>
                <input
                  value={theme[key] || ""}
                  onChange={(e) => setColor(key, e.target.value.trim())}
                  maxLength={7}
                  className={`field w-24 font-mono text-xs ${isHex(theme[key]) ? "" : "border-red-400"}`}
                  aria-label={`${label} en hexadecimal`}
                />
              </label>
            ))}
          </div>

          {/* Muestra fiel con los colores elegidos (independiente del tema actual). */}
          <div className="border p-4" style={{ background: theme.background, borderColor: theme.border, color: theme.text }}>
            <div className="mb-3 flex items-center justify-between px-3 py-2" style={{ background: theme.navBackground }}>
              <span style={{ color: contrast(theme.accent, theme.navBackground) >= 4.5 ? theme.accent : theme.text, fontWeight: 600 }}>
                Restaurante
              </span>
              <span style={{ color: theme.textMuted, fontSize: 12 }}>Navegación</span>
            </div>
            <div className="p-3" style={{ background: theme.card, border: `1px solid ${theme.border}` }}>
              <p style={{ fontWeight: 600 }}>Mesa 4 · Terraza</p>
              <p style={{ color: theme.textMuted, fontSize: 13 }}>Pedido #102 · 12 min</p>
              <div className="mt-2 flex flex-wrap gap-2 text-xs">
                {[["Listo", theme.success], ["Retrasado", theme.danger], ["En preparación", theme.warning], ["Info", theme.info]].map(([l, c]) => (
                  <span key={l} style={{ color: c, border: `1px solid ${c}`, borderRadius: 999, padding: "1px 8px" }}>{l}</span>
                ))}
              </div>
              <button type="button" className="mt-3 rounded-full px-4 py-2 text-sm font-semibold" style={{ background: theme.primary, color: theme.buttonText }}>
                Enviar a cocina
              </button>
            </div>
          </div>

          {problems.length > 0 ? (
            <div role="alert" className="border border-red-400/50 p-3 text-sm text-red-300">
              <p className="mb-1 flex items-center gap-2 font-semibold">
                <AlertTriangle className="h-4 w-4" /> Combinación poco legible: corrígela para poder guardar.
              </p>
              <ul className="list-disc pl-5 text-xs">
                {problems.map((p) => (
                  <li key={p.label}>
                    {p.label}: {p.ratio.toFixed(2)}:1 (mínimo {p.min}:1)
                  </li>
                ))}
              </ul>
            </div>
          ) : (
            <p className="flex items-center gap-2 text-sm text-emerald-300">
              <CheckCircle2 className="h-4 w-4" /> Contraste adecuado (WCAG AA).
            </p>
          )}
        </>
      )}

      {showLogo && (
      <div className="grid gap-4 border-t border-white/10 pt-4 sm:grid-cols-2">
        <ImageField label="Logo" url={logo?.url} onChange={setLogo} maxSide={512} aspect="1/1" contain />
        {!inherit && (
          <div className="space-y-3">
            <label className="field-label">
              Tamaño del logo ({theme.logoHeight || 40} px)
              <input
                type="range"
                min="20"
                max="72"
                value={theme.logoHeight || 40}
                onChange={(e) => setTheme({ ...theme, logoHeight: Number(e.target.value) })}
              />
            </label>
            <label className="field-label">
              Posición en la barra
              <select className="field" value={theme.logoAlign || "left"} onChange={(e) => setTheme({ ...theme, logoAlign: e.target.value })}>
                <option value="left">Izquierda</option>
                <option value="center">Centro</option>
              </select>
            </label>
            {logo?.url && (
              <div className="flex items-center gap-2 p-2" style={{ background: theme.navBackground, justifyContent: theme.logoAlign === "center" ? "center" : "flex-start" }}>
                <img src={logo.url} alt="Vista previa del logo" className="brand-logo" style={{ height: theme.logoHeight || 40 }} />
              </div>
            )}
          </div>
        )}
      </div>
      )}

      <button type="button" onClick={save} disabled={problems.length > 0 || invalid} className="btn btn-primary bg-metal w-full">
        {saveLabel}
      </button>
    </div>
  );
}
