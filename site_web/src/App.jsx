import { Navigate, Route, Routes } from "react-router-dom";
import { homePathForRole, useAuth } from "./auth/AuthContext";
import ProtectedRoute, { SessionGate } from "./components/ProtectedRoute";
import LoginPage from "./pages/LoginPage";
import FloorPage from "./pages/FloorPage";
import KitchenPage from "./pages/KitchenPage";
import AdminPage from "./pages/AdminPage";
import DeliveriesPage from "./pages/DeliveriesPage";
import DriverPage from "./pages/DriverPage";
import MessagesPage from "./pages/MessagesPage";
import MetricsPage from "./pages/MetricsPage";
import ProfilePage from "./pages/ProfilePage";

const STAFF = ["ADMIN", "EMPLOYEE", "COOK", "DELIVERY"];

function RootRedirect() {
  const { isAuthenticated, role, status } = useAuth();
  if (status !== "ready") return <SessionGate />;
  return <Navigate to={isAuthenticated ? homePathForRole(role) : "/login"} replace />;
}

export default function App() {
  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route path="/" element={<RootRedirect />} />

      <Route
        path="/piso"
        element={
          <ProtectedRoute roles={["EMPLOYEE", "ADMIN"]}>
            <FloorPage />
          </ProtectedRoute>
        }
      />
      <Route
        path="/cocina"
        element={
          <ProtectedRoute roles={["COOK", "ADMIN", "EMPLOYEE"]} permission={(role, can) => role !== "EMPLOYEE" || can("KITCHEN_ACCESS")}>
            <KitchenPage />
          </ProtectedRoute>
        }
      />
      <Route
        path="/domicilios"
        element={
          <ProtectedRoute roles={["ADMIN", "EMPLOYEE", "COOK"]} permission={(role, can) => can("MANAGE_DELIVERIES")}>
            <DeliveriesPage />
          </ProtectedRoute>
        }
      />
      <Route
        path="/entregas"
        element={
          <ProtectedRoute roles={["DELIVERY"]}>
            <DriverPage />
          </ProtectedRoute>
        }
      />
      <Route
        path="/mensajes"
        element={
          <ProtectedRoute roles={STAFF}>
            <MessagesPage />
          </ProtectedRoute>
        }
      />
      <Route
        path="/metricas"
        element={
          <ProtectedRoute roles={["ADMIN", "EMPLOYEE", "COOK"]} permission={(role, can) => can("VIEW_METRICS")}>
            <MetricsPage />
          </ProtectedRoute>
        }
      />
      <Route
        path="/perfil"
        element={
          <ProtectedRoute roles={STAFF}>
            <ProfilePage />
          </ProtectedRoute>
        }
      />
      <Route
        path="/admin"
        element={
          <ProtectedRoute roles={["ADMIN"]}>
            <AdminPage />
          </ProtectedRoute>
        }
      />

      <Route path="*" element={<RootRedirect />} />
    </Routes>
  );
}
