import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";
import ReservationsAdmin from "./ReservationsAdmin";
import { AuthProvider } from "../../auth/AuthContext";
import { writeSession } from "../../api/client";
import * as reservationApi from "../../api/reservationApi";
import * as tableApi from "../../api/tableApi";
import * as authApi from "../../api/authApi";

const TABLES = [
  { id: 1, tableNumber: 9, name: "Mesa Familiar", capacity: 8 },
  { id: 2, tableNumber: 11, name: null, capacity: 2 }
];
const RESERVATIONS = [
  {
    id: 20,
    tableId: 2,
    customerName: "Ana Prueba",
    reservationDate: "2030-05-02T21:00:00",
    numberOfGuests: 2,
    status: "CONFIRMED",
    specialRequests: null
  },
  {
    id: 10,
    tableId: 1,
    customerName: "Luis Prueba",
    reservationDate: "2030-05-01T20:00:00",
    numberOfGuests: 6,
    status: "PENDING",
    specialRequests: "Cumpleaños"
  }
];

function renderAdmin() {
  writeSession({ token: "t", role: "ADMIN", email: "admin@example.test", restaurantId: 5 });
  return render(
    <MemoryRouter>
      <AuthProvider>
        <ReservationsAdmin />
      </AuthProvider>
    </MemoryRouter>
  );
}

const card = (heading) => screen.getByRole("heading", { name: heading }).closest("article");

describe("ReservationsAdmin", () => {
  beforeEach(() => {
    vi.spyOn(authApi, "fetchCurrentUser").mockResolvedValue({
      userId: 1, email: "admin@example.test", role: "ADMIN", restaurantId: 5
    });
    vi.spyOn(reservationApi, "fetchRestaurantReservations").mockResolvedValue(RESERVATIONS);
    vi.spyOn(tableApi, "fetchTables").mockResolvedValue(TABLES);
    vi.spyOn(reservationApi, "confirmReservation").mockResolvedValue({});
    vi.spyOn(reservationApi, "cancelReservation").mockResolvedValue({});
    vi.spyOn(reservationApi, "createReservation").mockResolvedValue({ id: 30 });
  });

  it("lista las reservas por fecha con la mesa y solo ofrece las acciones de su estado", async () => {
    renderAdmin();

    const headings = await screen.findAllByRole("heading", { level: 3 });
    expect(headings.map((h) => h.textContent)).toEqual(["Mesa Familiar", "Mesa 11"]);
    expect(within(card("Mesa Familiar")).getByText("Pendiente")).toBeInTheDocument();
    expect(within(card("Mesa Familiar")).getByText("Cumpleaños")).toBeInTheDocument();
    expect(within(card("Mesa Familiar")).getByRole("button", { name: /Confirmar/ })).toBeInTheDocument();
    // Una reserva confirmada ya no se confirma de nuevo, pero se puede cancelar.
    expect(within(card("Mesa 11")).queryByRole("button", { name: /Confirmar/ })).not.toBeInTheDocument();
    expect(within(card("Mesa 11")).getByRole("button", { name: /Cancelar/ })).toBeInTheDocument();
  });

  it("filtra por estado", async () => {
    const user = userEvent.setup();
    renderAdmin();
    await screen.findByRole("heading", { name: "Mesa Familiar" });

    await user.click(screen.getByRole("button", { name: "Confirmadas" }));
    expect(screen.queryByRole("heading", { name: "Mesa Familiar" })).not.toBeInTheDocument();
    expect(screen.getByRole("heading", { name: "Mesa 11" })).toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: "Canceladas" }));
    expect(screen.getByText("Sin reservas con este estado.")).toBeInTheDocument();
  });

  it("confirma y cancela con la API y recarga la lista", async () => {
    const user = userEvent.setup();
    renderAdmin();
    await screen.findByRole("heading", { name: "Mesa Familiar" });

    await user.click(within(card("Mesa Familiar")).getByRole("button", { name: /Confirmar/ }));
    expect(reservationApi.confirmReservation).toHaveBeenCalledWith(10);

    await user.click(within(card("Mesa 11")).getByRole("button", { name: /Cancelar/ }));
    expect(reservationApi.cancelReservation).toHaveBeenCalledWith(20);
    expect(reservationApi.fetchRestaurantReservations).toHaveBeenCalledTimes(3);
  });

  it("muestra el error de carga y permite reintentar", async () => {
    reservationApi.fetchRestaurantReservations.mockRejectedValueOnce(new Error("No se pudo conectar"));
    const user = userEvent.setup();
    renderAdmin();

    expect(await screen.findByText("No se pudo conectar")).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: /Reintentar/ }));

    expect(await screen.findByRole("heading", { name: "Mesa Familiar" })).toBeInTheDocument();
  });

  it("muestra el estado vacío", async () => {
    reservationApi.fetchRestaurantReservations.mockResolvedValue([]);
    renderAdmin();
    expect(await screen.findByText("Sin reservas.")).toBeInTheDocument();
  });

  it("crea una reserva con el formato que espera la API y muestra sus errores", async () => {
    reservationApi.createReservation
      .mockRejectedValueOnce(new Error("Table already reserved for this time slot"))
      .mockResolvedValueOnce({ id: 30 });
    const user = userEvent.setup();
    renderAdmin();
    await screen.findByRole("heading", { name: "Mesa Familiar" });

    await user.click(screen.getByRole("button", { name: /Nueva reserva/ }));
    const dialog = screen.getByRole("heading", { name: "Nueva reserva" }).closest("div.motion-dialog");
    const form = within(dialog);
    await user.selectOptions(form.getByLabelText("Mesa"), "1");
    await user.type(form.getByLabelText("Fecha y hora"), "2030-06-01T20:30");
    await user.clear(form.getByLabelText("Personas"));
    await user.type(form.getByLabelText("Personas"), "4");
    await user.type(form.getByLabelText("Nombre del cliente"), "Carla");
    await user.type(form.getByLabelText("Email del cliente"), "carla@example.test");
    await user.click(form.getByRole("button", { name: /Reservar/ }));

    expect(reservationApi.createReservation).toHaveBeenCalledWith({
      restaurantId: 5,
      tableId: 1,
      reservationDate: "2030-06-01T20:30",
      numberOfGuests: 4,
      customerName: "Carla",
      customerEmail: "carla@example.test",
      specialRequests: null
    });
    expect(await form.findByText("Table already reserved for this time slot")).toBeInTheDocument();

    await user.click(form.getByRole("button", { name: /Reservar/ }));
    expect(reservationApi.createReservation).toHaveBeenCalledTimes(2);
  });
});
