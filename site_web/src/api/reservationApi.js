const RESTAURANT_ID = 1;

export async function fetchTableMap(date, time) {
  const params = new URLSearchParams({ date, time });
  const response = await fetch(`/api/public/restaurants/${RESTAURANT_ID}/table-map?${params}`);
  if (!response.ok) {
    const error = await response.json().catch(() => ({}));
    throw new Error(error.message || "No se pudo cargar el mapa de mesas");
  }
  return response.json();
}

export async function createReservation(payload) {
  const response = await fetch("/api/public/reservations", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ restaurantId: RESTAURANT_ID, ...payload })
  });
  if (!response.ok) {
    const error = await response.json().catch(() => ({}));
    throw new Error(error.message || "No se pudo confirmar la reserva");
  }
  return response.json();
}

export { RESTAURANT_ID };
