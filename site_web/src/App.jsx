import { Navigate, Route, Routes } from "react-router-dom";
import { homePathForRole, useAuth } from "./auth/AuthContext";
import ProtectedRoute from "./components/ProtectedRoute";
import LoginPage from "./pages/LoginPage";
import FloorPage from "./pages/FloorPage";
import KitchenPage from "./pages/KitchenPage";
import AdminPage from "./pages/AdminPage";

function RootRedirect() {
  const { isAuthenticated, role } = useAuth();
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
          <ProtectedRoute roles={["COOK", "ADMIN"]}>
            <KitchenPage />
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
