// Utilidades de tema: contraste WCAG (misma fórmula que ThemeSettings.java), paletas
// predefinidas y aplicación de variables CSS --t-* en el documento.

export const COLOR_KEYS = [
  ["primary", "Color primario"],
  ["secondary", "Color secundario"],
  ["accent", "Color de acento"],
  ["background", "Fondo principal"],
  ["surface", "Fondo de paneles"],
  ["card", "Tarjetas"],
  ["text", "Texto"],
  ["textMuted", "Texto secundario"],
  ["border", "Bordes"],
  ["navBackground", "Navegación"],
  ["buttonText", "Texto de botones"],
  ["success", "Estado: éxito"],
  ["warning", "Estado: aviso"],
  ["danger", "Estado: peligro"],
  ["info", "Estado: información"]
];

export const PRESETS = {
  noir: {
    label: "Noir dorado",
    mode: "dark", primary: "#d8a943", secondary: "#8e6a2a", accent: "#f8e7b0", background: "#050505",
    surface: "#121212", card: "#0a0a0b", text: "#f4f4f5", textMuted: "#a1a1aa", border: "#3f3f46",
    success: "#86efac", warning: "#fbbf24", danger: "#f87171", info: "#8ec5ff", navBackground: "#050505",
    buttonText: "#050505"
  },
  claro: {
    label: "Claro clásico",
    mode: "light", primary: "#1d4ed8", secondary: "#0f766e", accent: "#7c3aed", background: "#ffffff",
    surface: "#f4f4f5", card: "#ffffff", text: "#18181b", textMuted: "#52525b", border: "#d4d4d8",
    success: "#15803d", warning: "#b45309", danger: "#b91c1c", info: "#1d4ed8", navBackground: "#ffffff",
    buttonText: "#ffffff"
  },
  bosque: {
    label: "Bosque",
    mode: "dark", primary: "#34d399", secondary: "#059669", accent: "#a7f3d0", background: "#0b1410",
    surface: "#12201a", card: "#0f1a15", text: "#ecfdf5", textMuted: "#9ca3af", border: "#1f3a2e",
    success: "#4ade80", warning: "#fbbf24", danger: "#f87171", info: "#60a5fa", navBackground: "#0b1410",
    buttonText: "#052e1c"
  },
  terracota: {
    label: "Terracota",
    mode: "light", primary: "#c2410c", secondary: "#b45309", accent: "#9a3412", background: "#fffaf5",
    surface: "#fdf0e6", card: "#ffffff", text: "#1c1917", textMuted: "#57534e", border: "#e7d7c9",
    success: "#15803d", warning: "#b45309", danger: "#b91c1c", info: "#1d4ed8", navBackground: "#fffaf5",
    buttonText: "#ffffff"
  },
  oceano: {
    label: "Océano",
    mode: "dark", primary: "#38bdf8", secondary: "#0369a1", accent: "#bae6fd", background: "#06111f",
    surface: "#0c1b2e", card: "#0a1626", text: "#e2e8f0", textMuted: "#94a3b8", border: "#1e3a5f",
    success: "#4ade80", warning: "#fbbf24", danger: "#fb7185", info: "#7dd3fc", navBackground: "#06111f",
    buttonText: "#04111d"
  }
};

export const DEFAULT_THEME = { ...PRESETS.noir, logoHeight: 40, logoAlign: "left", preset: "noir" };

const HEX = /^#[0-9a-fA-F]{6}$/;

export function isHex(value) {
  return HEX.test(value || "");
}

function channel(v) {
  const c = v / 255;
  return c <= 0.03928 ? c / 12.92 : ((c + 0.055) / 1.055) ** 2.4;
}

function luminance(hex) {
  const n = parseInt(hex.slice(1), 16);
  return 0.2126 * channel((n >> 16) & 255) + 0.7152 * channel((n >> 8) & 255) + 0.0722 * channel(n & 255);
}

export function contrast(a, b) {
  if (!isHex(a) || !isHex(b)) return 0;
  const la = luminance(a);
  const lb = luminance(b);
  return (Math.max(la, lb) + 0.05) / (Math.min(la, lb) + 0.05);
}

/** Mismas reglas que el servidor: devuelve la lista de problemas de legibilidad. */
export function readabilityProblems(t) {
  const checks = [
    ["Texto sobre fondo", t.text, t.background, 4.5],
    ["Texto sobre paneles", t.text, t.surface, 4.5],
    ["Texto sobre tarjetas", t.text, t.card, 4.5],
    ["Texto sobre navegación", t.text, t.navBackground, 4.5],
    ["Texto secundario sobre fondo", t.textMuted, t.background, 3],
    ["Texto secundario sobre tarjetas", t.textMuted, t.card, 3],
    ["Texto de botón sobre primario", t.buttonText, t.primary, 4.5],
    ["Primario sobre fondo", t.primary, t.background, 3],
    ["Acento sobre fondo", t.accent, t.background, 3],
    ["Éxito sobre tarjetas", t.success, t.card, 3],
    ["Aviso sobre tarjetas", t.warning, t.card, 3],
    ["Peligro sobre tarjetas", t.danger, t.card, 3],
    ["Información sobre tarjetas", t.info, t.card, 3]
  ];
  const problems = [];
  for (const [label, fg, bg, min] of checks) {
    const ratio = contrast(fg, bg);
    if (ratio < min) problems.push({ label, ratio, min });
  }
  return problems;
}

/**
 * Tema efectivo según la preferencia del dispositivo:
 *  - "brand": la identidad del restaurante/grupo tal cual.
 *  - "dark" / "light": base neutra de ese modo conservando el primario de la marca si
 *    sigue siendo legible sobre esa base.
 */
// ---------------------------------------------------------------- Variantes oscuro / claro

function hexToHsl(hex) {
  const n = parseInt(hex.slice(1), 16);
  const r = ((n >> 16) & 255) / 255;
  const g = ((n >> 8) & 255) / 255;
  const b = (n & 255) / 255;
  const max = Math.max(r, g, b);
  const min = Math.min(r, g, b);
  const l = (max + min) / 2;
  if (max === min) return [0, 0, l];
  const d = max - min;
  const s = l > 0.5 ? d / (2 - max - min) : d / (max + min);
  let h;
  if (max === r) h = (g - b) / d + (g < b ? 6 : 0);
  else if (max === g) h = (b - r) / d + 2;
  else h = (r - g) / d + 4;
  return [h * 60, s, l];
}

export function hslToHex(h, s, l) {
  const k = (n) => (n + h / 30) % 12;
  const a = s * Math.min(l, 1 - l);
  const f = (n) => l - a * Math.max(-1, Math.min(k(n) - 3, Math.min(9 - k(n), 1)));
  return `#${[f(0), f(8), f(4)].map((v) => Math.round(Math.max(0, Math.min(1, v)) * 255).toString(16).padStart(2, "0")).join("")}`;
}

/**
 * Ajusta solo la luminosidad de `hex` (conservando tono y saturación) hasta alcanzar
 * `min` de contraste contra `bg`. En modo oscuro aclara; en claro oscurece.
 */
function fit(hex, bg, min, mode) {
  if (!isHex(hex)) return hex;
  let [h, s, l] = hexToHsl(hex);
  let out = hex;
  for (let i = 0; i < 60 && contrast(out, bg) < min; i++) {
    l = mode === "dark" ? Math.min(1, l + 0.02) : Math.max(0, l - 0.02);
    out = hslToHex(h, s, l);
  }
  return out;
}

/**
 * Genera la versión oscura o clara de una paleta: mismo tono de marca (fondos teñidos con el
 * color primario, no grises genéricos) y colores reajustados para cumplir el contraste.
 */
export function deriveVariant(theme, mode) {
  const base = { ...DEFAULT_THEME, ...theme };
  if (base.mode === mode) return base;
  const [hp] = hexToHsl(base.primary);
  const [hb, sb] = hexToHsl(base.background);
  // Fondo neutro (gris/blanco/negro): se tiñe con el tono del primario para conservar la identidad.
  const hue = sb < 0.08 ? hp : hb;
  const dark = mode === "dark";
  const tone = (s, l) => hslToHex(hue, s, l);
  const background = dark ? tone(0.28, 0.05) : tone(0.45, 0.975);
  const surface = dark ? tone(0.22, 0.1) : tone(0.35, 0.93);
  const card = dark ? tone(0.25, 0.075) : tone(0.3, 0.995);
  const text = dark ? tone(0.15, 0.94) : tone(0.3, 0.12);
  const textMuted = dark ? tone(0.1, 0.68) : tone(0.12, 0.38);
  const border = dark ? tone(0.2, 0.22) : tone(0.25, 0.84);

  let primary = fit(base.primary, background, dark ? 4 : 3.2, mode);
  let buttonText = dark ? tone(0.3, 0.06) : "#ffffff";
  if (!dark) primary = fit(primary, buttonText, 4.6, "light");
  else for (let i = 0; i < 30 && contrast(buttonText, primary) < 4.6; i++) primary = fit(primary, buttonText, 4.6, "dark");

  const variant = {
    ...base,
    mode,
    background,
    surface,
    card,
    navBackground: background,
    text,
    textMuted,
    border,
    primary,
    buttonText,
    secondary: fit(base.secondary, background, 3, mode),
    accent: fit(base.accent, background, 4.6, mode),
    success: fit(base.success, card, 3.6, mode),
    warning: fit(base.warning, card, 3.6, mode),
    danger: fit(base.danger, card, 3.6, mode),
    info: fit(base.info, card, 3.6, mode),
    preset: base.preset ? `${base.preset}-${mode}` : "custom"
  };
  // Red de seguridad: si algo sigue ilegible, se corrige con valores extremos.
  for (const p of readabilityProblems(variant)) {
    if (p.label.startsWith("Texto de botón")) variant.buttonText = dark ? "#000000" : "#ffffff";
    if (p.label.startsWith("Texto sobre")) variant.text = dark ? "#ffffff" : "#000000";
  }
  return variant;
}

/**
 * Tema efectivo según la preferencia del dispositivo:
 *  - "brand": la identidad del restaurante/grupo tal cual.
 *  - "dark" / "light": la misma identidad en ese modo (variante generada si hace falta).
 */
export function effectiveTheme(brand, pref) {
  const base = brand || DEFAULT_THEME;
  if (!pref || pref === "brand" || pref === base.mode) return base;
  return deriveVariant(base, pref);
}

export function applyTheme(theme, root = document.documentElement) {
  const t = { ...DEFAULT_THEME, ...theme };
  const heading = contrast(t.accent, t.background) >= 4.5 ? t.accent : t.text;
  const vars = {
    "--t-bg": t.background,
    "--t-surface": t.surface,
    "--t-card": t.card,
    "--t-heading": heading,
    "--t-primary": t.primary,
    "--t-secondary": t.secondary,
    "--t-accent": t.accent,
    "--t-text": t.text,
    "--t-muted": t.textMuted,
    "--t-border": t.border,
    "--t-ink": t.mode === "light" ? "#000000" : "#ffffff",
    "--t-success": t.success,
    "--t-warning": t.warning,
    "--t-danger": t.danger,
    "--t-info": t.info,
    "--t-nav": t.navBackground,
    "--t-button-text": t.buttonText
  };
  for (const [key, value] of Object.entries(vars)) {
    if (isHex(value)) root.style.setProperty(key, value);
  }
  root.dataset.mode = t.mode === "light" ? "light" : "dark";
  const meta = document.querySelector('meta[name="theme-color"]');
  if (meta && isHex(t.navBackground)) meta.setAttribute("content", t.navBackground);
}
