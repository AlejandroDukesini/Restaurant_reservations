import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";
import FloorPage from "./FloorPage";
import { AuthProvider } from "../auth/AuthContext";
import { writeSession } from "../api/client";
import * as menuApi from "../api/menuApi";
import * as opsApi from "../api/opsApi";

const BOARD = [
  {
    id: 1, tableNumber: 9, name: "Mesa Familiar", displayName: "Mesa Familiar", zoneName: "Zona Central", capacity: 8,
    liveState: "AVAILABLE", readyOrders: 0, activeOrders: 0, sessionTotal: 0, sessionId: null
  },
  {
    id: 2, tableNumber: 11, name: null, displayName: "Mesa 11", zoneName: "Barra", capacity: 2,
    liveState: "READY", readyOrders: 1, activeOrders: 1, sessionTotal: 32, sessionId: 50, waiterName: "Carlos",
    openedAt: new Date(Date.now() - 20 * 60000).toISOString()
  }
];
const MENU = [
  { id: 100, name: "Risotto", category: "Platos fuertes", price: 32, available: true, prepMinutes: 20 },
  { id: 200, name: "Vino", category: "Bebidas", price: 12.5, available: true },
  { id: 300, name: "Flan", category: "Postres", price: 8, available: false }
];
const SESSION = {
  id: 50,
  tableId: 2,
  status: "OPEN",
  waiterName: "Carlos",
  openedAt: new Date().toISOString(),
  total: 32,
  orders: [
    {
      id: 77, employeeId: 9, status: "READY", totalAmount: 32, orderDate: new Date().toISOString(),
      items: [{ id: 1, quantity: 1, itemName: "Risotto", status: "READY", notes: "Sin queso" }]
    }
  ]
};

function renderFloor() {
  writeSession({ token: "t", role: "EMPLOYEE", email: "mesero@example.test", userId: 9 });
  return render(
    <MemoryRouter>
      <AuthProvider>
        <FloorPage />
      </AuthProvider>
    </MemoryRouter>
  );
}

describe("FloorPage (operación de sala)", () => {
  beforeEach(() => {
    vi.spyOn(opsApi, "fetchFloor").mockResolvedValue(BOARD);
    vi.spyOn(menuApi, "fetchMenu").mockResolvedValue(MENU);
    vi.spyOn(opsApi, "fetchSession").mockImplementation((id) =>
      id === 2 ? Promise.resolve(SESSION) : Promise.reject(new Error("sin sesión"))
    );
    vi.spyOn(opsApi, "createTableOrder").mockResolvedValue({ id: 78 });
    vi.spyOn(opsApi, "deliverOrder").mockResolvedValue({ id: 77, status: "DELIVERED" });
    vi.spyOn(opsApi, "openTable").mockResolvedValue({ id: 51 });
  });

  it("muestra el estado de cada mesa sin entrar en ella", async () => {
    renderFloor();
    const familiar = await screen.findByRole("button", { name: /Mesa Familiar/ });
    expect(familiar).toHaveTextContent("Disponible");
    const busy = screen.getByRole("button", { name: /Mesa 11/ });
    expect(busy).toHaveTextContent("Pedido listo");
    expect(busy).toHaveTextContent("Carlos");
    expect(screen.getByText("Selecciona una mesa para atenderla.")).toBeInTheDocument();
  });

  it("arma un pedido con modificaciones y lo envía con clave de idempotencia", async () => {
    const user = userEvent.setup();
    renderFloor();
    await user.click(await screen.findByRole("button", { name: /Mesa Familiar/ }));

    await user.click(await screen.findByRole("button", { name: "Agregar Risotto" }));
    await user.click(screen.getByRole("button", { name: "Agregar Risotto" }));
    await user.click(screen.getByRole("button", { name: "Agregar Vino" }));
    expect(screen.getByText("$76.50")).toBeInTheDocument();
    // Un producto agotado no se puede agregar.
    expect(screen.getByRole("button", { name: "Agregar Flan" })).toBeDisabled();

    await user.type(screen.getByLabelText("Modificación para Risotto"), "Sin queso");
    await user.click(screen.getByRole("button", { name: /Enviar a cocina/ }));

    await waitFor(() => expect(opsApi.createTableOrder).toHaveBeenCalledTimes(1));
    const [payload, key] = opsApi.createTableOrder.mock.calls[0];
    expect(payload).toEqual({
      tableId: 1,
      items: [
        { menuItemId: 100, quantity: 2, notes: "Sin queso" },
        { menuItemId: 200, quantity: 1, notes: undefined }
      ],
      notes: undefined
    });
    expect(typeof key).toBe("string");
    expect(await screen.findByText("Pedido enviado a cocina.")).toBeInTheDocument();
  });

  it("muestra los pedidos de la atención y entrega uno listo", async () => {
    const user = userEvent.setup();
    renderFloor();
    await user.click(await screen.findByRole("button", { name: /Mesa 11/ }));

    expect(await screen.findByText("1× Risotto")).toBeInTheDocument();
    expect(screen.getByText("Sin queso")).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: /Entregar/ }));
    await waitFor(() => expect(opsApi.deliverOrder).toHaveBeenCalledWith(77));
  });

  it("una mesa libre se puede ocupar indicando personas", async () => {
    const user = userEvent.setup();
    renderFloor();
    await user.click(await screen.findByRole("button", { name: /Mesa Familiar/ }));
    await user.type(await screen.findByLabelText("Personas"), "4");
    await user.click(screen.getByRole("button", { name: /Ocupar mesa/ }));
    await waitFor(() => expect(opsApi.openTable).toHaveBeenCalledWith(1, 4));
  });

  it("muestra el error de la API al enviar un pedido", async () => {
    opsApi.createTableOrder.mockRejectedValue(new Error("Producto agotado: Risotto"));
    const user = userEvent.setup();
    renderFloor();
    await user.click(await screen.findByRole("button", { name: /Mesa Familiar/ }));
    await user.click(await screen.findByRole("button", { name: "Agregar Risotto" }));
    await user.click(screen.getByRole("button", { name: /Enviar a cocina/ }));
    expect(await screen.findByText("Producto agotado: Risotto")).toBeInTheDocument();
  });

  it("si el tablero falla muestra el error y permite reintentar", async () => {
    opsApi.fetchFloor.mockRejectedValueOnce(new Error("No se pudo conectar con el servidor."));
    const user = userEvent.setup();
    renderFloor();
    expect(await screen.findByText("No se pudo conectar con el servidor.")).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: /Reintentar/ }));
    expect(await screen.findByRole("button", { name: /Mesa Familiar/ })).toBeInTheDocument();
  });
});
