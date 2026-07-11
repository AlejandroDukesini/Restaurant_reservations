import { useState } from "react";
import { ClipboardList, LayoutGrid, UtensilsCrossed, Users } from "lucide-react";
import Layout from "../components/Layout";
import StaffAdmin from "../components/admin/StaffAdmin";
import MenuAdmin from "../components/admin/MenuAdmin";
import TablesAdmin from "../components/admin/TablesAdmin";
import OrdersAdmin from "../components/admin/OrdersAdmin";

const TABS = [
  { key: "tables", label: "Mesas", icon: LayoutGrid, Component: TablesAdmin },
  { key: "staff", label: "Personal", icon: Users, Component: StaffAdmin },
  { key: "menu", label: "Menú", icon: UtensilsCrossed, Component: MenuAdmin },
  { key: "orders", label: "Pedidos", icon: ClipboardList, Component: OrdersAdmin }
];

export default function AdminPage() {
  const [active, setActive] = useState("tables");
  const Active = TABS.find((t) => t.key === active)?.Component ?? TablesAdmin;

  return (
    <Layout subtitle="Panel de control" title="Administración">
      <div className="mb-6 flex flex-wrap gap-2">
        {TABS.map(({ key, label, icon: Icon }) => (
          <button
            key={key}
            type="button"
            onClick={() => setActive(key)}
            className={[
              "inline-flex items-center gap-2 rounded-full border px-4 py-2 text-sm transition",
              active === key
                ? "border-gold/70 text-champagne shadow-neon"
                : "border-white/10 text-zinc-300 hover:border-gold/40 hover:text-champagne"
            ].join(" ")}
          >
            <Icon className="h-4 w-4" />
            {label}
          </button>
        ))}
      </div>

      <Active />
    </Layout>
  );
}
