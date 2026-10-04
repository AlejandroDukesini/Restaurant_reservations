import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";
import FloorPage from "./FloorPage";
import { AuthProvider } from "../auth/AuthContext";
import { writeSession } from "../api/client";
import * as tableApi from "../api/tableApi";
import * as menuApi from "../api/menuApi";
import * as orderApi from "../api/orderApi";

const TABLES = [
  { id: 1, tableNumber: 9, name: "Mesa Familiar", capacity: 8, zoneName: "Zona Central", gridX: 1, gridY: 1, status: "AVAILABLE" },
  { id: 2, tableNumber: 11, name: null, capacity: 2, zoneName: "Barra", gridX: 1, gridY: 1, status: "OCCUPIED" }
];
const MENU = [
  { id: 100, name: "Risotto", category: "MAIN", price: 32 },
  { id: 200, name: "Vino", category: "DRINK", price: 12.5 }
];
const EXISTING_ORDER = {
  id: 77,
  status: "IN_PROGRESS",
  totalAmount: 32,
  items: [{ id: 1, quantity: 1, itemName: "Risotto", status: "PREPARING" }]
};

function renderFloor() {
  writeSession({ token: "t", role: "EMPLOYEE", email: "mesero@example.test" });
  return render(
    <MemoryRouter>
      <AuthProvider>
        <FloorPage />
      </AuthProvider>
    </MemoryRouter>
  );
}

function dishRow(name) {
  return screen.getByText(name).closest("div.flex.items-center.justify-between");
}

describe("FloorPage (flujo del mesero)", () => {
  beforeEach(() => {
    vi.spyOn(tableApi, "fetchTables").mockResolvedValue(TABLES);
    vi.spyOn(menuApi, "fetchMenu").mockResolvedValue(MENU);
    vi.spyOn(orderApi, "fetchOrdersByTable").mockResolvedValue([]);
    vi.spyOn(orderApi, "createOrder").mockResolvedValue({ id: 78 });
    vi.spyOn(orderApi, "deleteOrder").mockResolvedValue(null);
  });

  it("agrupa las mesas por zona y pide seleccionar una", async () => {
    renderFloor();

    expect(await screen.findByRole("heading", { name: "Zona Central" })).toBeInTheDocument();
    expect(screen.getByRole("heading", { name: "Barra" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /Mesa 11/ })).toBeInTheDocument();
    expect(screen.getByText("Selecciona una mesa para tomar o revisar un pedido.")).toBeInTheDocument();
  });

  it("arma un pedido, calcula el total y lo envia con el formato que espera la API", async () => {
    const user = userEvent.setup();
    renderFloor();

    await user.click(await screen.findByRole("button", { name: /Mesa Familiar/ }));
    expect(screen.getByRole("button", { name: /Mesa Familiar/ })).toHaveAttribute("aria-pressed", "true");
    expect(orderApi.fetchOrdersByTable).toHaveBeenCalledWith(1);

    const send = screen.getByRole("button", { name: /Enviar a cocina/ });
    expect(send).toBeDisabled();

    const risotto = within(dishRow("Risotto"));
    await user.click(risotto.getByRole("button", { name: "Agregar uno" }));
    await user.click(risotto.getByRole("button", { name: "Agregar uno" }));
    await user.click(within(dishRow("Vino")).getByRole("button", { name: "Agregar uno" }));
    expect(screen.getByText("$76.50")).toBeInTheDocument();

    // Quitar hasta cero elimina el plato del borrador.
    await user.click(within(dishRow("Vino")).getByRole("button", { name: "Quitar uno" }));
    await user.click(within(dishRow("Vino")).getByRole("button", { name: "Quitar uno" }));
    expect(screen.getByText("$64.00")).toBeInTheDocument();

    await user.click(send);

    expect(orderApi.createOrder).toHaveBeenCalledWith({
      tableId: 1,
      items: [{ menuItemId: 100, quantity: 2 }]
    });
    expect(await screen.findByText("Pedido enviado a cocina.")).toBeInTheDocument();
    expect(screen.getByText("$0.00")).toBeInTheDocument();
    expect(orderApi.fetchOrdersByTable).toHaveBeenCalledTimes(2);
  });

  it("muestra los pedidos de la mesa y permite eliminarlos", async () => {
    orderApi.fetchOrdersByTable.mockResolvedValueOnce([EXISTING_ORDER]).mockResolvedValueOnce([]);
    const user = userEvent.setup();
    renderFloor();

    await user.click(await screen.findByRole("button", { name: /Mesa Familiar/ }));
    expect(await screen.findByText("En preparación")).toBeInTheDocument();
    expect(screen.getByText("1× Risotto")).toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: "Eliminar pedido" }));

    expect(orderApi.deleteOrder).toHaveBeenCalledWith(77);
    expect(await screen.findByText("Sin pedidos aún.")).toBeInTheDocument();
  });

  it("muestra el error de la API al enviar un pedido", async () => {
    orderApi.createOrder.mockRejectedValue(new Error("Menu item not found"));
    const user = userEvent.setup();
    renderFloor();

    await user.click(await screen.findByRole("button", { name: /Mesa Familiar/ }));
    await user.click(within(dishRow("Risotto")).getByRole("button", { name: "Agregar uno" }));
    await user.click(screen.getByRole("button", { name: /Enviar a cocina/ }));

    expect(await screen.findByText("Menu item not found")).toBeInTheDocument();
  });
});
