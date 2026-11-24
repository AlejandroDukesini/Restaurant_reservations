import { describe, expect, it } from "vitest";
import { DEFAULT_THEME, PRESETS, applyTheme, contrast, deriveVariant, effectiveTheme, readabilityProblems } from "./theme";
import { navLinksFor } from "../components/Navbar";
import { deliveryColumn } from "../pages/DeliveriesPage";
import { targetFor } from "../components/NotificationBell";

describe("contraste WCAG", () => {
  it("coincide con los valores de referencia", () => {
    expect(contrast("#000000", "#ffffff")).toBeCloseTo(21, 1);
    expect(contrast("#ffffff", "#ffffff")).toBeCloseTo(1, 5);
    expect(contrast("no-es-color", "#ffffff")).toBe(0);
  });

  it("todas las paletas predefinidas son legibles", () => {
    for (const preset of Object.values(PRESETS)) expect(readabilityProblems(preset)).toEqual([]);
  });

  it("detecta texto claro sobre fondo claro", () => {
    const problems = readabilityProblems({ ...PRESETS.claro, text: "#f4f4f5" });
    expect(problems.map((p) => p.label)).toContain("Texto sobre fondo");
  });
});

describe("variantes oscuro/claro", () => {
  it("cada paleta genera su variante legible en el otro modo", () => {
    for (const preset of Object.values(PRESETS)) {
      const other = preset.mode === "dark" ? "light" : "dark";
      const variant = deriveVariant(preset, other);
      expect(variant.mode).toBe(other);
      expect(readabilityProblems(variant)).toEqual([]);
    }
  });

  it("la variante cambia de verdad los fondos y conserva el tono de la marca", () => {
    const dark = deriveVariant(PRESETS.terracota, "dark");
    expect(dark.background).not.toBe(PRESETS.terracota.background);
    expect(contrast(dark.background, "#000000")).toBeLessThan(1.3); // fondo oscuro
    // Tono cálido (rojo > azul) como el terracota original, no un gris genérico.
    const n = parseInt(dark.background.slice(1), 16);
    expect((n >> 16) & 255).toBeGreaterThan(n & 255);
    const light = deriveVariant(PRESETS.oceano, "light");
    expect(contrast(light.background, "#ffffff")).toBeLessThan(1.1); // fondo claro
  });
});

describe("tema efectivo", () => {
  it("respeta la marca y permite forzar oscuro o claro conservando un primario legible", () => {
    expect(effectiveTheme(PRESETS.bosque, "brand")).toBe(PRESETS.bosque);
    const light = effectiveTheme(PRESETS.noir, "light");
    expect(light.mode).toBe("light");
    expect(readabilityProblems(light)).toEqual([]);
    const dark = effectiveTheme(PRESETS.terracota, "dark");
    expect(dark.mode).toBe("dark");
    expect(readabilityProblems(dark)).toEqual([]);
  });

  it("aplica variables CSS y el modo al documento, ignorando valores no hexadecimales", () => {
    applyTheme({ ...PRESETS.claro, primary: "red;}body{display:none" });
    const root = document.documentElement;
    expect(root.dataset.mode).toBe("light");
    expect(root.style.getPropertyValue("--t-bg")).toBe("#ffffff");
    // El valor inyectado no se aplica: queda el color por defecto definido en styles.css.
    expect(root.style.getPropertyValue("--t-primary")).not.toContain("display");
    applyTheme(DEFAULT_THEME);
  });
});

describe("navegación por rol y permisos", () => {
  const none = () => false;
  const only = (p) => (perm) => perm === p;

  it("cada rol ve solo sus secciones", () => {
    expect(navLinksFor("EMPLOYEE", none).map((l) => l.to)).toEqual(["/piso", "/mensajes"]);
    expect(navLinksFor("COOK", none).map((l) => l.to)).toEqual(["/cocina", "/mensajes"]);
    expect(navLinksFor("DELIVERY", none).map((l) => l.to)).toEqual(["/entregas", "/mensajes"]);
    expect(navLinksFor("ADMIN", () => true).map((l) => l.to)).toEqual([
      "/piso", "/cocina", "/domicilios", "/mensajes", "/metricas", "/admin"
    ]);
  });

  it("los permisos concedidos agregan secciones sin dar administración", () => {
    const links = navLinksFor("EMPLOYEE", only("VIEW_METRICS")).map((l) => l.to);
    expect(links).toContain("/metricas");
    expect(links).not.toContain("/admin");
  });
});

describe("domicilios y notificaciones", () => {
  it("cada estado cae en una sola columna", () => {
    expect(deliveryColumn({ status: "PENDING" })).toBe("kitchen");
    expect(deliveryColumn({ status: "PACKING" })).toBe("packing");
    expect(deliveryColumn({ status: "PACKED" })).toBe("waiting");
    expect(deliveryColumn({ status: "PACKED", driverId: 3 })).toBe("assigned");
    expect(deliveryColumn({ status: "ON_THE_WAY", driverId: 3 })).toBe("route");
    expect(deliveryColumn({ status: "CANCELLED" })).toBe("done");
  });

  it("la notificación lleva a la operación correspondiente", () => {
    expect(targetFor({ refType: "TABLE", refId: 4, type: "TABLE_ATTENTION" }, "EMPLOYEE")).toBe("/piso?mesa=4");
    expect(targetFor({ refType: "ORDER", refId: 9, type: "ORDER_READY" }, "EMPLOYEE")).toBe("/piso?pedido=9");
    expect(targetFor({ refType: "ORDER", refId: 9, type: "DELIVERY_ASSIGNED" }, "DELIVERY")).toBe("/entregas");
    expect(targetFor({ refType: "ORDER", refId: 9, type: "DELIVERY_WAITING" }, "ADMIN")).toBe("/domicilios");
  });
});
