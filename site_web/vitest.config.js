import { defineConfig, mergeConfig } from "vitest/config";
import viteConfig from "./vite.config.js";

// Configuracion de pruebas separada de vite.config.js para no alterar el build.
export default mergeConfig(
  viteConfig,
  defineConfig({
    test: {
      environment: "jsdom",
      setupFiles: ["./src/test/setup.js"],
      include: ["src/**/*.test.{js,jsx}"],
      restoreMocks: true,
      coverage: {
        provider: "v8",
        include: ["src/**/*.{js,jsx}"],
        exclude: ["src/**/*.test.{js,jsx}", "src/test/**", "src/main.jsx"],
        reporter: ["text", "html", "lcov"],
        reportsDirectory: "./coverage"
      }
    }
  })
);
