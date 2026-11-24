import { describe, expect, it } from "vitest";
import { CATEGORY_LABEL, ITEM_STATUS, ORDER_STATUS, formatMinutes, minutesSince, money } from "./status";

describe("money", () => {
  it("formatea con dos decimales", () => {
    expect(money(76.5)).toBe("$76.50");
    expect(money("12")).toBe("$12.00");
  });

  it("trata null y undefined como cero", () => {
    expect(money(null)).toBe("$0.00");
    expect(money(undefined)).toBe("$0.00");
  });
});

describe("catalogos de estado", () => {
  // Deben cubrir los enums del backend (OrderStatus, OrderItemStatus) y los codigos antiguos.
  it("cubren todos los valores que devuelve la API", () => {
    const backend = ["PENDING", "PREPARING", "READY", "PACKING", "PACKED", "ON_THE_WAY", "DELIVERED", "CANCELLED"];
    for (const status of backend) expect(ORDER_STATUS[status]).toBeDefined();
    // Valores del sistema anterior, para pedidos historicos.
    expect(ORDER_STATUS.IN_PROGRESS).toBeDefined();
    expect(ORDER_STATUS.COMPLETED).toBeDefined();
    expect(Object.keys(ITEM_STATUS).sort()).toEqual(["PENDING", "PREPARING", "READY"]);
    expect(Object.keys(CATEGORY_LABEL).sort()).toEqual(["DESSERT", "DRINK", "MAIN", "STARTER"]);
  });
});

describe("tiempos", () => {
  it("formatea minutos y calcula transcurrido", () => {
    expect(formatMinutes(null)).toBe("—");
    expect(formatMinutes(12.34)).toBe("12.3 min");
    expect(formatMinutes(75)).toBe("1 h 15 min");
    const tenMinutesAgo = new Date(Date.now() - 10 * 60000).toISOString();
    expect(minutesSince(tenMinutesAgo)).toBe(10);
    expect(minutesSince(null)).toBeNull();
  });
});
