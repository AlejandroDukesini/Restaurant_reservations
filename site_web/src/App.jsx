import Footer from "./components/Footer";
import Navbar from "./components/Navbar";
import SelectionPanel from "./components/SelectionPanel";
import TableGrid from "./components/TableGrid";
import { useTableMap } from "./hooks/useTableMap";

export default function App() {
  const {
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
  } = useTableMap();

  return (
    <main className="min-h-screen bg-carbon text-zinc-100">
      <Navbar />

      <section id="mesas" className="mx-auto grid max-w-7xl gap-6 px-5 py-8 lg:grid-cols-[1fr_380px]">
        <TableGrid
          zones={zones}
          date={date}
          time={time}
          selectedTableId={selectedTableId}
          isLoading={isLoading}
          isDemoMode={isDemoMode}
          onDateChange={(value) => {
            setDate(value);
            refreshMap(value, time);
          }}
          onTimeChange={(value) => {
            setTime(value);
            refreshMap(date, value);
          }}
          onSelectTable={setSelectedTableId}
        />

        <SelectionPanel
          selectedTable={selectedTable}
          date={date}
          time={time}
          form={form}
          status={status}
          isLoading={isLoading}
          onDateChange={(value) => {
            setDate(value);
            refreshMap(value, time);
          }}
          onTimeChange={(value) => {
            setTime(value);
            refreshMap(date, value);
          }}
          onFormChange={(patch) => setForm((current) => ({ ...current, ...patch }))}
          onSubmit={confirmReservation}
        />
      </section>

      <Footer />
    </main>
  );
}
