import { ChefHat, LayoutGrid, LogOut, Martini, Users } from "lucide-react";
import { NavLink, useNavigate } from "react-router-dom";
import { useAuth } from "../auth/AuthContext";

// Enlaces de navegación disponibles por rol.
const LINKS = [
  { to: "/piso", label: "Piso / Mesas", icon: LayoutGrid, roles: ["EMPLOYEE", "ADMIN"] },
  { to: "/cocina", label: "Cocina", icon: ChefHat, roles: ["COOK", "ADMIN"] },
  { to: "/admin", label: "Administración", icon: Users, roles: ["ADMIN"] }
];

export default function Navbar() {
  const { session, role, logout } = useAuth();
  const navigate = useNavigate();

  const handleLogout = () => {
    logout();
    navigate("/login", { replace: true });
  };

  const links = LINKS.filter((link) => link.roles.includes(role));

  return (
    <nav className="sticky top-0 z-30 border-b border-white/10 bg-carbon/88 backdrop-blur-xl">
      <div className="mx-auto flex max-w-7xl flex-wrap items-center justify-between gap-x-8 gap-y-4 px-5 py-4">
        {/* Lado izquierdo: branding — icono y título agrupados y alineados verticalmente */}
        <div className="flex items-center gap-3">
          <span className="grid h-10 w-10 shrink-0 place-items-center rounded-full border border-gold/50 bg-metal shadow-gold">
            <Martini className="h-5 w-5 text-carbon" />
          </span>
          <div className="flex flex-col leading-tight">
            <span className="text-xs uppercase tracking-[.28em] text-gold">Maison Noir</span>
            <span className="text-xl font-semibold text-champagne">Operación</span>
          </div>
        </div>

        {/* Lado derecho: shrink-0 para que nunca se aplaste; gap responsivo para que respire */}
        <div className="flex shrink-0 items-center gap-3 text-sm text-zinc-300 md:gap-4">
          <div className="flex items-center gap-2">
            {links.map(({ to, label, icon: Icon }) => (
              <NavLink
                key={to}
                to={to}
                className={({ isActive }) =>
                  [
                    "box-border inline-flex items-center gap-2 whitespace-nowrap rounded-full border px-4 py-2 transition",
                    isActive
                      ? "border-gold/70 text-champagne shadow-neon"
                      : "border-white/10 hover:border-gold/40 hover:text-champagne"
                  ].join(" ")
                }
              >
                <Icon className="h-4 w-4 shrink-0" />
                {label}
              </NavLink>
            ))}
          </div>

          {session && (
            <div className="flex items-center gap-3 border-l border-white/10 pl-3 md:gap-4 md:pl-4">
              <div className="hidden text-right leading-tight sm:block">
                <p className="text-xs text-zinc-400">{session.email}</p>
                <p className="text-xs uppercase tracking-[.18em] text-gold">{role}</p>
              </div>
              <button
                type="button"
                onClick={handleLogout}
                className="box-border inline-flex items-center gap-2 whitespace-nowrap rounded-full border border-gold/40 px-4 py-2 text-champagne transition hover:border-gold/70"
              >
                <LogOut className="h-4 w-4 shrink-0" />
                Salir
              </button>
            </div>
          )}
        </div>
      </div>
    </nav>
  );
}
