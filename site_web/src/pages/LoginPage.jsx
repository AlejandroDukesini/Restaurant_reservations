import { useState } from "react";
import { useNavigate } from "react-router-dom";
import { KeyRound, LockKeyhole, Mail, Martini } from "lucide-react";
import { homePathForRole, useAuth } from "../auth/AuthContext";

const DEMO_ACCOUNTS = [
  { label: "Administrador", email: "admin@maisonnoir.com" },
  { label: "Mesero", email: "mesero1@maisonnoir.com" },
  { label: "Cocinero", email: "cocina1@maisonnoir.com" }
];

export default function LoginPage() {
  const { login, isAuthenticated, role } = useAuth();
  const navigate = useNavigate();
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(false);

  if (isAuthenticated) {
    navigate(homePathForRole(role), { replace: true });
  }

  const onSubmit = async (event) => {
    event.preventDefault();
    setError("");
    setLoading(true);
    try {
      const session = await login(email, password);
      navigate(homePathForRole(session.role), { replace: true });
    } catch (err) {
      setError(err.message || "No se pudo iniciar sesión");
    } finally {
      setLoading(false);
    }
  };

  const fillDemo = (demoEmail) => {
    setEmail(demoEmail);
    setPassword("password123");
  };

  return (
    <main className="grid min-h-screen place-items-center bg-carbon px-5 py-10 text-zinc-100">
      <div className="w-full max-w-md">
        <div className="mb-8 flex flex-col items-center text-center">
          <span className="grid h-14 w-14 place-items-center rounded-full border border-gold/50 bg-metal shadow-gold">
            <Martini className="h-7 w-7 text-carbon" />
          </span>
          <p className="mt-4 text-sm uppercase tracking-[.28em] text-gold">Maison Noir</p>
          <h1 className="mt-1 text-3xl font-semibold text-champagne">Acceso al personal</h1>
          <p className="mt-2 text-sm text-zinc-400">
            Ingresa con tu cuenta de empleado, cocinero o administrador.
          </p>
        </div>

        <form onSubmit={onSubmit} className="zone-band space-y-4 rounded-lg">
          <label className="input-label">
            Correo
            <span className="input-shell">
              <Mail className="h-4 w-4" />
              <input
                type="email"
                autoComplete="username"
                value={email}
                required
                onChange={(e) => setEmail(e.target.value)}
                placeholder="tu@correo.com"
              />
            </span>
          </label>

          <label className="input-label">
            Contraseña
            <span className="input-shell">
              <LockKeyhole className="h-4 w-4" />
              <input
                type="password"
                autoComplete="current-password"
                value={password}
                required
                onChange={(e) => setPassword(e.target.value)}
                placeholder="••••••••"
              />
            </span>
          </label>

          {error && (
            <p className="rounded border border-red-500/40 bg-red-500/10 px-3 py-2 text-sm text-red-300">
              {error}
            </p>
          )}

          <button
            type="submit"
            disabled={loading}
            className="inline-flex w-full items-center justify-center gap-2 rounded-full border border-gold/60 bg-metal px-5 py-3 font-semibold text-carbon shadow-gold transition hover:brightness-110 disabled:opacity-60"
          >
            <KeyRound className="h-4 w-4" />
            {loading ? "Ingresando..." : "Ingresar"}
          </button>
        </form>

        <div className="mt-6 text-center text-xs text-zinc-500">
          <p className="uppercase tracking-[.18em] text-zinc-400">Cuentas de demostración</p>
          <p className="mt-1">Contraseña: password123</p>
          <div className="mt-3 flex flex-wrap justify-center gap-2">
            {DEMO_ACCOUNTS.map((acc) => (
              <button
                key={acc.email}
                type="button"
                onClick={() => fillDemo(acc.email)}
                className="rounded-full border border-white/10 px-3 py-1 text-zinc-300 transition hover:border-gold/40 hover:text-champagne"
              >
                {acc.label}
              </button>
            ))}
          </div>
        </div>
      </div>
    </main>
  );
}
