import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";
import KitchenPage from "./KitchenPage";
import { AuthProvider } from "../auth/AuthContext";
import { writeSession } from "../api/client";
import * as opsApi from "../api/opsApi";

const minutesAgo = (m) => new Date(Date.now() - m * 60000).toISOString();

const TABLE_ORDER = {
  orderId: 5,
  channel: "DINE_IN",
  label: "Mesa Familiar",
  employeeName: "Mesero Prueba",
  orderDate: minutesAgo(30),
  status: "PENDING",
  estimatedMinutes: 18,
  notes: null,
  items: [
    {
      orderItemId: 11, itemName: "Risotto", quantity: 2, status: "PENDING", notes: "Sin queso", categoryName: "Platos fuertes",
      protein: "Ninguna", condiments: "Parmesano", ingredients: "Arroz arborio", preparationNotes: "18 min"
    }
  ]
};
const DELIVERY_ORDER = {
  orderId: 6,
  channel: "DELIVERY",
  label: "Domicilio #6 · Rappi",
  employeeName: "Gerente",
  orderDate: minutesAgo(5),
  status: "READY",
  items: [{ orderItemId: 12, itemName: "Hamburguesa", quantity: 1, status: "READY" }]
};

function renderKitchen() {
  writeSession({ token: "t", role: "COOK", email: "chef@example.test" });
  return render(
    <MemoryRouter>
      <AuthProvider>
        <KitchenPage />
      </AuthProvider>
    </MemoryRouter>
  );
}

describe("KitchenPage", () => {
  beforeEach(() => {
    vi.spyOn(opsApi, "fetchKitchenBoard").mockResolvedValue([TABLE_ORDER, DELIVERY_ORDER]);
    vi.spyOn(opsApi, "setKitchenItem").mockResolvedValue(null);
    vi.spyOn(opsApi, "readyKitchenOrder").mockResolvedValue({});
    vi.spyOn(opsApi, "startPacking").mockResolvedValue({});
    vi.spyOn(opsApi, "callWaiter").mockResolvedValue(null);
  });

  it("agrupa por pedido con receta, modificaciones y retraso frente al estimado", async () => {
    renderKitchen();
    const card = await screen.findByRole("article", { name: "Pedido 5" });
    expect(within(card).getByRole("heading", { name: "Mesa Familiar" })).toBeInTheDocument();
    expect(within(card).getByText("Risotto")).toBeInTheDocument();
    expect(within(card).getByText(/Sin queso/)).toBeInTheDocument();
    expect(within(card).getByText("Arroz arborio", { exact: false })).toBeInTheDocument();
    // 30 min frente a 18 estimados: se marca como retrasado.
    expect(within(card).getByTitle("Transcurrido / estimado")).toHaveClass("is-late");
  });

  it("marca un plato como listo y recarga el tablero", async () => {
    const user = userEvent.setup();
    renderKitchen();
    const card = await screen.findByRole("article", { name: "Pedido 5" });
    await user.click(within(card).getByRole("button", { name: "Listo" }));
    await waitFor(() => expect(opsApi.setKitchenItem).toHaveBeenCalledWith(11, "READY"));
    expect(opsApi.fetchKitchenBoard).toHaveBeenCalledTimes(2);
  });

  it("el domicilio listo pasa a empaque y la mesa puede llamar al mesero", async () => {
    const user = userEvent.setup();
    renderKitchen();
    const delivery = await screen.findByRole("article", { name: "Pedido 6" });
    await user.click(within(delivery).getByRole("button", { name: /Empezar empaque/ }));
    await waitFor(() => expect(opsApi.startPacking).toHaveBeenCalledWith(6));

    const table = screen.getByRole("article", { name: "Pedido 5" });
    await user.click(within(table).getByRole("button", { name: /Llamar mesero/ }));
    expect(await screen.findByText("Se avisó al mesero.")).toBeInTheDocument();
  });

  it("muestra el estado vacío cuando no hay pedidos", async () => {
    opsApi.fetchKitchenBoard.mockResolvedValue([]);
    renderKitchen();
    expect(await screen.findByText("No hay pedidos pendientes")).toBeInTheDocument();
  });

  it("muestra el error de la API sin perder el tablero", async () => {
    opsApi.setKitchenItem.mockRejectedValue(new Error("Kitchen progress can only move forward"));
    const user = userEvent.setup();
    renderKitchen();
    const card = await screen.findByRole("article", { name: "Pedido 5" });
    await user.click(within(card).getByRole("button", { name: /Preparando/ }));
    expect(await screen.findByText("Kitchen progress can only move forward")).toBeInTheDocument();
    expect(screen.getByRole("article", { name: "Pedido 5" })).toBeInTheDocument();
  });
});
