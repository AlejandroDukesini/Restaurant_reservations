import { useState } from "react";
import { Bike, CalendarDays, ClipboardList, LayoutGrid, Network, Palette, UtensilsCrossed, Users } from "lucide-react";
import Layout from "../components/Layout";
import StaffAdmin from "../components/admin/StaffAdmin";
import MenuAdmin from "../components/admin/MenuAdmin";
import TablesAdmin from "../components/admin/TablesAdmin";
import OrdersAdmin from "../components/admin/OrdersAdmin";
import ReservationsAdmin from "../components/admin/ReservationsAdmin";
import GroupsAdmin from "../components/admin/GroupsAdmin";
import BrandingAdmin from "../components/admin/BrandingAdmin";
import DeliveryAdmin from "../components/admin/DeliveryAdmin";

const TABS = [
  { key: "tables", label: "Mesas", icon: LayoutGrid, Component: TablesAdmin },
  { key: "menu", label: "Menú", icon: UtensilsCrossed, Component: MenuAdmin },
  { key: "staff", label: "Personal", icon: Users, Component: StaffAdmin },
  { key: "groups", label: "Grupos", icon: Network, Component: GroupsAdmin },
  { key: "delivery", label: "Domicilios", icon: Bike, Component: DeliveryAdmin },
  { key: "branding", label: "Apariencia", icon: Palette, Component: BrandingAdmin },
  { key: "orders", label: "Pedidos", icon: ClipboardList, Component: OrdersAdmin },
  { key: "reservations", label: "Reservas", icon: CalendarDays, Component: ReservationsAdmin }
];

export default function AdminPage() {
  const [active, setActive] = useState("tables");
  const Active = TABS.find((t) => t.key === active)?.Component ?? TablesAdmin;

  return (
    <Layout subtitle="Panel del gerente" title="Gestión del restaurante">
      <div className="motion-enter mb-6 flex gap-2 overflow-x-auto pb-1" style={{ "--i": 1 }} role="tablist">
        {TABS.map(({ key, label, icon: Icon }) => (
          <button
            key={key}
            type="button"
            onClick={() => setActive(key)}
            role="tab"
            aria-selected={active === key}
            className={[
              "inline-flex shrink-0 items-center gap-2 rounded-full border px-4 py-2 text-sm transition",
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

      {/* La clave reinicia la entrada en cada cambio de pestaña. */}
      <div key={active} className="motion-blur-in">
        <Active />
      </div>
    </Layout>
  );
}
