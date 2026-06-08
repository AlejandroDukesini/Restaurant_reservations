import { useCallback, useEffect, useMemo, useState } from "react";
import { createReservation, fetchTableMap } from "../api/reservationApi";
import { fallbackZones } from "../data/fallbackZones";

export function today() {
  return new Date().toISOString().slice(0, 10);
}

export function useTableMap() {
  const [date, setDate] = useState(today());
  const [time, setTime] = useState("20:00");
  const [zones, setZones] = useState(fallbackZones);
  const [selectedTableId, setSelectedTableId] = useState(8);
  const [form, setForm] = useState({ name: "", email: "", guests: 4, notes: "" });
  const [status, setStatus] = useState("");
  const [isLoading, setIsLoading] = useState(false);
  const [isDemoMode, setIsDemoMode] = useState(true);

  const selectedTable = useMemo(() => {
    for (const zone of zones) {
      const table = zone.tables.find((item) => item.id === selectedTableId);
      if (table) return { ...table, zoneName: zone.name };
    }
    return null;
  }, [zones, selectedTableId]);

  const refreshMap = useCallback(async (nextDate = date, nextTime = time) => {
    setIsLoading(true);
    setStatus("Actualizando disponibilidad...");
    try {
      const payload = await fetchTableMap(nextDate, nextTime);
      setZones(payload.zones);
      setIsDemoMode(false);
      setStatus("Disponibilidad sincronizada.");
    } catch {
      setIsDemoMode(true);
      setStatus("Modo demo activo. El backend no respondió todavía.");
    } finally {
      setIsLoading(false);
    }
  }, [date, time]);

  useEffect(() => {
    refreshMap(date, time);
  }, []);

  const confirmReservation = useCallback(async (event) => {
    event.preventDefault();
    if (!selectedTable || selectedTable.availability === "OCCUPIED") return;

    setIsLoading(true);
    setStatus("Confirmando mesa...");
    try {
      await createReservation({
        tableId: selectedTable.id,
        reservationDate: `${date}T${time}:00`,
        numberOfGuests: Number(form.guests),
        customerName: form.name || "Invitado Premium",
        customerEmail: form.email || "invitado@reservas.local",
        specialRequests: form.notes || null
      });
      setStatus(`Mesa ${selectedTable.tableNumber} confirmada.`);
      await refreshMap(date, time);
    } catch (error) {
      setStatus(error.message);
    } finally {
      setIsLoading(false);
    }
  }, [selectedTable, date, time, form, refreshMap]);

  return {
    date,
    time,
    zones,
    selectedTableId,
    selectedTable,
    form,
    status,
    isLoading,
    isDemoMode,
    setDate,
    setTime,
    setSelectedTableId,
    setForm,
    refreshMap,
    confirmReservation
  };
}
