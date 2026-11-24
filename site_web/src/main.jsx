import { createRoot } from "react-dom/client";
import { BrowserRouter } from "react-router-dom";
import App from "./App";
import { AuthProvider } from "./auth/AuthContext";
import { RealtimeProvider } from "./realtime/RealtimeProvider";
import { ThemeProvider } from "./theme/ThemeProvider";
import { ConfirmProvider } from "./components/ConfirmDialog";
import { registerServiceWorker } from "./pwa/pwa";
import "./styles.css";

registerServiceWorker();

createRoot(document.getElementById("root")).render(
  <BrowserRouter>
    <AuthProvider>
      <RealtimeProvider>
        <ThemeProvider>
          <ConfirmProvider>
            <App />
          </ConfirmProvider>
        </ThemeProvider>
      </RealtimeProvider>
    </AuthProvider>
  </BrowserRouter>
);
