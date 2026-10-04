import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";
import KitchenPage from "./KitchenPage";
import { AuthProvider } from "../auth/AuthContext";
import { writeSession } from "../api/client";
import * as orderApi from "../api/orderApi";

const QUEUE_ITEM = {
  orderItemId: 11,
  orderId: 5,
  tableId: 2,
  tableNumber: 9,
  tableName: "Mesa Familiar",
  employeeName: "Mesero Prueba",
  itemName: "Risotto",
  quantity: 2,
  status: "PENDING",
  notes: "Sin queso",
  category: "MAIN",
  protein: "Ninguna",
  condiments: "Parmesano",
  ingredients: "Arroz arborio",
  preparationNotes: "18 min"
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
    vi.spyOn(orderApi, "fetchKitchenQueue");
    vi.spyOn(orderApi, "updateItemStatus");
  });

  it("muestra cada plato con su mesa, cantidad y receta", async () => {
    orderApi.fetchKitchenQueue.mockResolvedValue([QUEUE_ITEM]);
    renderKitchen();

    expect(await screen.findByRole("heading", { name: "2× Risotto" })).toBeInTheDocument();
    expect(screen.getByText(/Mesa Familiar · #9/)).toBeInTheDocument();
    expect(screen.getByText("Plato fuerte · Mesero Prueba")).toBeInTheDocument();
    expect(screen.getByText("Arroz arborio")).toBeInTheDocument();
    expect(screen.getByText("Sin queso")).toBeInTheDocument();
    expect(screen.getByText("Pendiente")).toBeInTheDocument();
  });

  it("muestra el estado vacio cuando no hay platos", async () => {
    orderApi.fetchKitchenQueue.mockResolvedValue([]);
    renderKitchen();
    expect(await screen.findByText("No hay platos pendientes")).toBeInTheDocument();
  });

  it("marcar un plato como listo llama a la API y recarga la cola", async () => {
    orderApi.fetchKitchenQueue.mockResolvedValueOnce([QUEUE_ITEM]).mockResolvedValueOnce([]);
    orderApi.updateItemStatus.mockResolvedValue({ ...QUEUE_ITEM, status: "READY" });
    const user = userEvent.setup();
    renderKitchen();

    await user.click(await screen.findByRole("button", { name: /Listo/ }));

    expect(orderApi.updateItemStatus).toHaveBeenCalledWith(11, "READY");
    expect(await screen.findByText("No hay platos pendientes")).toBeInTheDocument();
    expect(orderApi.fetchKitchenQueue).toHaveBeenCalledTimes(2);
  });

  it("muestra el error de la API sin perder la cola", async () => {
    orderApi.fetchKitchenQueue.mockResolvedValue([QUEUE_ITEM]);
    orderApi.updateItemStatus.mockRejectedValue(new Error("Unauthorized access to order item"));
    const user = userEvent.setup();
    renderKitchen();

    await user.click(await screen.findByRole("button", { name: /Preparando/ }));

    expect(await screen.findByText("Unauthorized access to order item")).toBeInTheDocument();
    await waitFor(() => expect(screen.getByRole("heading", { name: "2× Risotto" })).toBeInTheDocument());
  });
});
