import { Navigate, useLocation } from "react-router-dom";
import { homePathForRole, useAuth } from "../auth/AuthContext";

export default function ProtectedRoute({ roles, children }) {
  const { isAuthenticated, role } = useAuth();
  const location = useLocation();

  if (!isAuthenticated) {
    return <Navigate to="/login" replace state={{ from: location.pathname }} />;
  }

  if (roles && !roles.includes(role)) {
    // Autenticado pero sin permiso para esta vista: al panel de su rol.
    return <Navigate to={homePathForRole(role)} replace />;
  }

  return children;
}
