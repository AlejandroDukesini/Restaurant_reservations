import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import { ConfirmProvider, useConfirm } from "./ConfirmDialog";

function Harness({ options, onResult }) {
  const confirm = useConfirm();
  return (
    <button type="button" onClick={async () => onResult(await confirm(options))}>
      abrir
    </button>
  );
}

function renderWith(options) {
  const onResult = vi.fn();
  render(
    <ConfirmProvider>
      <Harness options={options} onResult={onResult} />
    </ConfirmProvider>
  );
  return onResult;
}

describe("ConfirmDialog", () => {
  it("muestra título, importe y confirma", async () => {
    const user = userEvent.setup();
    const onResult = renderWith({ title: "Cerrar Mesa 4", highlight: "$65.00", confirmLabel: "Confirmar cobro" });
    await user.click(screen.getByRole("button", { name: "abrir" }));

    const dialog = screen.getByRole("alertdialog", { name: "Cerrar Mesa 4" });
    expect(dialog).toHaveTextContent("$65.00");
    await user.click(screen.getByRole("button", { name: "Confirmar cobro" }));
    await waitFor(() => expect(onResult).toHaveBeenCalledWith({ value: "" }));
    await waitFor(() => expect(screen.queryByRole("alertdialog")).not.toBeInTheDocument());
  });

  it("cancelar o Escape devuelven false", async () => {
    const user = userEvent.setup();
    const onResult = renderWith({ title: "¿Eliminar?" });
    await user.click(screen.getByRole("button", { name: "abrir" }));
    await user.click(screen.getByRole("button", { name: "Cancelar" }));
    await waitFor(() => expect(onResult).toHaveBeenLastCalledWith(false));

    await user.click(screen.getByRole("button", { name: "abrir" }));
    await user.keyboard("{Escape}");
    await waitFor(() => expect(onResult).toHaveBeenCalledTimes(2));
    expect(onResult).toHaveBeenLastCalledWith(false);
  });

  it("devuelve el texto escrito cuando pide un motivo", async () => {
    const user = userEvent.setup();
    const onResult = renderWith({ title: "¿Cancelar pedido?", confirmLabel: "Cancelar pedido", input: { label: "Motivo" } });
    await user.click(screen.getByRole("button", { name: "abrir" }));
    await user.type(screen.getByLabelText("Motivo"), "  Cliente se fue  ");
    await user.click(screen.getByRole("button", { name: "Cancelar pedido" }));
    await waitFor(() => expect(onResult).toHaveBeenCalledWith({ value: "Cliente se fue" }));
  });
});
