import "@testing-library/jest-dom/vitest";
import { cleanup } from "@testing-library/react";
import { afterEach } from "vitest";

// Cada prueba arranca sin DOM ni sesion residual de la anterior.
afterEach(() => {
  cleanup();
  localStorage.clear();
});
