import { useEffect } from "react";
import {
  BarChart3,
  Bike,
  ChefHat,
  Download,
  LayoutGrid,
  LogOut,
  Martini,
  MessagesSquare,
  Moon,
  Package,
  Settings,
  Sun
} from "lucide-react";
import { Link, NavLink, useNavigate } from "react-router-dom";
import Avatar from "./Avatar";
import { useAuth } from "../auth/AuthContext";
import { useRealtimeStatus } from "../realtime/RealtimeProvider";
import { useTheme } from "../theme/ThemeProvider";
import { useInstallPrompt } from "../pwa/pwa";
import { ROLE_LABEL } from "../utils/status";
import NotificationBell from "./NotificationBell";

/**
 * Secciones visibles según rol y permisos. Es solo presentación: cada ruta y cada llamada a
 * la API se vuelve a autorizar en el servidor.
 */
export function navLinksFor(role, can) {
  const links = [
    { to: "/piso", label: "Mesas", icon: LayoutGrid, show: role === "EMPLOYEE" || role === "ADMIN" },
    { to: "/cocina", label: "Cocina", icon: ChefHat, show: role === "COOK" || role === "ADMIN" || can("KITCHEN_ACCESS") },
    { to: "/domicilios", label: "Domicilios", icon: Package, show: role !== "DELIVERY" && can("MANAGE_DELIVERIES") },
    { to: "/entregas", label: "Mis entregas", icon: Bike, show: role === "DELIVERY" },
    { to: "/mensajes", label: "Mensajes", icon: MessagesSquare, show: true },
    { to: "/metricas", label: "Métricas", icon: BarChart3, show: can("VIEW_METRICS") },
    { to: "/admin", label: "Gestión", icon: Settings, show: role === "ADMIN" }
  ];
  return links.filter((link) => link.show);
}


function ConnectionDot() {
  const status = useRealtimeStatus();
  const label =
    status === "online" ? "Sincronizado en tiempo real" : status === "connecting" ? "Conectando..." : "Sin conexión en tiempo real (actualización cada 30 s)";
  const color = status === "online" ? "var(--t-success)" : status === "connecting" ? "var(--t-warning)" : "var(--t-danger)";
  return (
    <span className="inline-flex items-center gap-1.5 text-xs text-zinc-400" title={label} role="status" aria-label={label}>
      <span className="h-2 w-2 rounded-full" style={{ background: color }} />
      <span className="hidden xl:inline">{status === "online" ? "En vivo" : status === "connecting" ? "Conectando" : "Sin conexión"}</span>
    </span>
  );
}

export default function Navbar() {
  const { session, role, logout, can } = useAuth();
  const { restaurantName, logoUrl, theme, pref, setPref } = useTheme();
  const { available: installable, install } = useInstallPrompt();
  const navigate = useNavigate();
  const links = navLinksFor(role, can);

  // Atajos de teclado (Windows): Alt+1..9 abre cada sección.
  useEffect(() => {
    const onKey = (event) => {
      if (!event.altKey || event.ctrlKey || event.metaKey) return;
      const n = Number(event.key);
      if (n >= 1 && n <= links.length) {
        event.preventDefault();
        navigate(links[n - 1].to);
      }
    };
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [links, navigate]);

  const handleLogout = () => {
    logout();
    navigate("/login", { replace: true });
  };

  // El botón alterna entre oscuro y claro de verdad (variante generada de la misma paleta).
  const isDark = theme?.mode !== "light";
  const ModeIcon = isDark ? Sun : Moon;
  const logoHeight = Math.min(Math.max(theme?.logoHeight || 40, 20), 72);

  const linkClass = ({ isActive }) =>
    [
      "inline-flex items-center gap-2 whitespace-nowrap rounded-full border px-3 py-2 text-sm transition",
      isActive ? "border-gold/70 text-champagne shadow-neon" : "border-white/10 text-zinc-300 hover:border-gold/40 hover:text-champagne"
    ].join(" ");

  return (
    <>
      <nav className="sticky top-0 z-30 border-b border-white/10 backdrop-blur-xl" style={{ background: "color-mix(in oklab, var(--t-nav) 90%, transparent)" }}>
        <div className="mx-auto flex max-w-7xl items-center justify-between gap-3 px-4 py-3 sm:px-5">
          <div className={`flex min-w-0 items-center gap-3 ${theme?.logoAlign === "center" ? "lg:flex-1 lg:justify-center" : ""}`}>
            {logoUrl ? (
              <img src={logoUrl} alt={restaurantName} className="brand-logo" style={{ height: logoHeight }} />
            ) : (
              <span className="grid h-10 w-10 shrink-0 place-items-center rounded-full border border-gold/50 bg-metal shadow-gold">
                <Martini className="h-5 w-5" />
              </span>
            )}
            <div className="flex min-w-0 flex-col leading-tight">
              <span className="truncate text-sm font-semibold text-champagne sm:text-base">{restaurantName}</span>
              <span className="text-[11px] uppercase tracking-[.2em] text-gold">{ROLE_LABEL[role] || role}</span>
            </div>
          </div>

          <div className="hidden items-center gap-2 lg:flex">
            {links.map(({ to, label, icon: Icon }, index) => (
              <NavLink key={to} to={to} className={linkClass} title={`${label} (Alt+${index + 1})`}>
                <Icon className="h-4 w-4 shrink-0" />
                {label}
              </NavLink>
            ))}
          </div>

          <div className="flex shrink-0 items-center gap-2">
            <ConnectionDot />
            {installable && (
              <button type="button" onClick={install} className="btn btn-sm hidden sm:inline-flex" title="Instalar la aplicación">
                <Download className="h-4 w-4" /> Instalar
              </button>
            )}
            <button
              type="button"
              onClick={() => setPref(isDark ? "light" : "dark")}
              className="grid h-10 w-10 place-items-center rounded-full border border-white/10 text-zinc-300 hover:border-gold/50"
              title={isDark ? "Cambiar a modo claro" : "Cambiar a modo oscuro"}
              aria-label={isDark ? "Cambiar a modo claro" : "Cambiar a modo oscuro"}
              aria-pressed={pref !== "brand"}
            >
              <ModeIcon className="h-5 w-5" />
            </button>
            {session && <NotificationBell />}
            {session && (
              <Link
                to="/perfil"
                className="flex items-center gap-2 rounded-full border border-white/10 p-0.5 pr-0.5 hover:border-gold/50 sm:pr-3"
                title="Mi perfil y apariencia"
                aria-label="Mi perfil"
              >
                <Avatar url={session.avatarUrl} name={session.name} size={36} />
                <span className="hidden max-w-32 truncate text-sm text-zinc-200 sm:inline">{session.name}</span>
              </Link>
            )}
            {session && (
              <button
                type="button"
                onClick={handleLogout}
                className="grid h-10 w-10 place-items-center rounded-full border border-white/10 text-zinc-300 hover:border-gold/50 sm:w-auto sm:px-3"
                aria-label="Salir"
                title={session.email}
              >
                <LogOut className="h-4 w-4" />
                <span className="sr-only sm:not-sr-only sm:ml-2 sm:text-sm">Salir</span>
              </button>
            )}
          </div>
        </div>
      </nav>

      {/* Android / móvil: barra inferior con las secciones, siempre al alcance del pulgar. */}
      <nav
        className="bottom-nav fixed inset-x-0 bottom-0 z-30 border-t border-white/10 backdrop-blur-xl lg:hidden"
        style={{ background: "color-mix(in oklab, var(--t-nav) 94%, transparent)" }}
        aria-label="Secciones"
      >
        <div className="mx-auto flex max-w-xl justify-around">
          {links.map(({ to, label, icon: Icon }) => (
            <NavLink
              key={to}
              to={to}
              className={({ isActive }) =>
                `flex min-h-14 flex-1 flex-col items-center justify-center gap-0.5 px-1 text-[11px] ${isActive ? "text-gold" : "text-zinc-400"}`
              }
            >
              <Icon className="h-5 w-5" />
              <span className="max-w-full truncate">{label}</span>
            </NavLink>
          ))}
        </div>
      </nav>
    </>
  );
}
