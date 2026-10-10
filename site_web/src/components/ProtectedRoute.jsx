import { Navigate, useLocation } from "react-router-dom";
import { homePathForRole, useAuth } from "../auth/AuthContext";
import { ErrorState, Loader } from "./LoadState";

// Pantalla completa mientras se confirma la sesión guardada (o si no se pudo confirmar).
export function SessionGate() {
  const { status, sessionError, retrySession, logout } = useAuth();
  return (
    <main className="grid min-h-screen place-items-center bg-carbon px-5 text-zinc-100">
      {status === "error" ? (
        <ErrorState message={sessionError} onRetry={retrySession}>
          <button
            type="button"
            onClick={logout}
            className="rounded-full border border-white/10 px-4 py-2 text-sm text-zinc-300 transition hover:border-gold/40 hover:text-champagne"
          >
            Cerrar sesión
          </button>
        </ErrorState>
      ) : (
        <Loader label="Verificando tu sesión..." />
      )}
    </main>
  );
}

export default function ProtectedRoute({ roles, children }) {
  const { isAuthenticated, role, status } = useAuth();
  const location = useLocation();

  // Sin rol confirmado no se muestra contenido restringido ni se redirige.
  if (status !== "ready") return <SessionGate />;

  if (!isAuthenticated) {
    return <Navigate to="/login" replace state={{ from: location.pathname }} />;
  }

  if (roles && !roles.includes(role)) {
    // Autenticado pero sin permiso para esta vista: al panel de su rol.
    return <Navigate to={homePathForRole(role)} replace />;
  }

  return children;
}
