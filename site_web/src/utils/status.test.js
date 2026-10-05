import { describe, expect, it } from "vitest";
import { CATEGORY_LABEL, ITEM_STATUS, ORDER_STATUS, money } from "./status";

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
  // Deben cubrir exactamente los enums del backend (OrderStatus, OrderItemStatus, MenuCategory).
  it("cubren todos los valores que devuelve la API", () => {
    expect(Object.keys(ORDER_STATUS).sort()).toEqual(["CANCELLED", "COMPLETED", "IN_PROGRESS", "PENDING"]);
    expect(Object.keys(ITEM_STATUS).sort()).toEqual(["PENDING", "PREPARING", "READY"]);
    expect(Object.keys(CATEGORY_LABEL).sort()).toEqual(["DESSERT", "DRINK", "MAIN", "STARTER"]);
  });
});
