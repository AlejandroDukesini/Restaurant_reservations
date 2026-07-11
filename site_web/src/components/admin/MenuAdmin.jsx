import { useEffect, useState } from "react";
import { Pencil, Plus, Trash2 } from "lucide-react";
import Modal from "./Modal";
import { createMenuItem, deleteMenuItem, fetchMenu, updateMenuItem } from "../../api/menuApi";
import { CATEGORY_LABEL, money } from "../../utils/status";

const EMPTY = {
  name: "",
  description: "",
  category: "MAIN",
  price: "",
  protein: "",
  condiments: "",
  ingredients: "",
  preparationNotes: ""
};

const CATEGORIES = ["STARTER", "MAIN", "DESSERT", "DRINK"];

export default function MenuAdmin() {
  const [items, setItems] = useState([]);
  const [status, setStatus] = useState("");
  const [editing, setEditing] = useState(null);
  const [form, setForm] = useState(EMPTY);

  const load = async () => {
    try {
      setItems(await fetchMenu());
    } catch (err) {
      setStatus(err.message);
    }
  };

  useEffect(() => {
    load();
  }, []);

  const openCreate = () => {
    setForm(EMPTY);
    setEditing({});
  };

  const openEdit = (item) => {
    setForm({
      ...EMPTY,
      ...item,
      description: item.description ?? "",
      protein: item.protein ?? "",
      condiments: item.condiments ?? "",
      ingredients: item.ingredients ?? "",
      preparationNotes: item.preparationNotes ?? ""
    });
    setEditing(item);
  };

  const save = async (event) => {
    event.preventDefault();
    setStatus("");
    try {
      const payload = { ...form, price: Number(form.price) };
      if (editing?.id) await updateMenuItem(editing.id, payload);
      else await createMenuItem(payload);
      setEditing(null);
      await load();
    } catch (err) {
      setStatus(err.message);
    }
  };

  const remove = async (id) => {
    try {
      await deleteMenuItem(id);
      await load();
    } catch (err) {
      setStatus(err.message);
    }
  };

  const field = (key, label, textarea = false) => (
    <label className="input-label">
      {label}
      <span className="input-shell items-start">
        {textarea ? (
          <textarea
            value={form[key]}
            rows={2}
            onChange={(e) => setForm({ ...form, [key]: e.target.value })}
            className="w-full resize-none bg-transparent py-2 text-champagne outline-none"
          />
        ) : (
          <input value={form[key]} onChange={(e) => setForm({ ...form, [key]: e.target.value })} />
        )}
      </span>
    </label>
  );

  return (
    <div>
      <div className="mb-4 flex items-center justify-between">
        <p className="text-sm text-zinc-400">Platos de menú con su receta fija (no personalizable).</p>
        <button
          type="button"
          onClick={openCreate}
          className="inline-flex items-center gap-2 rounded-full border border-gold/60 bg-metal px-4 py-2 text-sm font-semibold text-carbon shadow-gold transition hover:brightness-110"
        >
          <Plus className="h-4 w-4" /> Nuevo plato
        </button>
      </div>

      {status && <p className="mb-3 text-sm text-red-300">{status}</p>}

      <div className="grid gap-3 sm:grid-cols-2 xl:grid-cols-3">
        {items.map((item) => (
          <article key={item.id} className="zone-band">
            <div className="mb-2 flex items-start justify-between gap-2">
              <div>
                <h3 className="text-base font-semibold text-champagne">{item.name}</h3>
                <p className="text-xs text-zinc-500">
                  {CATEGORY_LABEL[item.category] || item.category} · {money(item.price)}
                </p>
              </div>
              <div className="flex gap-2">
                <button type="button" onClick={() => openEdit(item)} className="text-zinc-400 hover:text-champagne">
                  <Pencil className="h-4 w-4" />
                </button>
                <button type="button" onClick={() => remove(item.id)} className="text-zinc-400 hover:text-red-400">
                  <Trash2 className="h-4 w-4" />
                </button>
              </div>
            </div>
            <div className="space-y-1 border-t border-white/10 pt-2 text-xs text-zinc-400">
              {item.protein && <p><span className="text-gold">Proteína:</span> {item.protein}</p>}
              {item.condiments && <p><span className="text-gold">Condimentos:</span> {item.condiments}</p>}
              {item.ingredients && <p><span className="text-gold">Ingredientes:</span> {item.ingredients}</p>}
            </div>
          </article>
        ))}
        {items.length === 0 && <p className="text-sm text-zinc-500">Sin platos en el menú.</p>}
      </div>

      {editing && (
        <Modal title={editing.id ? "Editar plato" : "Nuevo plato"} onClose={() => setEditing(null)}>
          <form onSubmit={save} className="max-h-[70vh] space-y-3 overflow-y-auto pr-1">
            {field("name", "Nombre")}
            <div className="flex gap-3">
              <label className="input-label flex-1">
                Categoría
                <span className="input-shell">
                  <select
                    value={form.category}
                    onChange={(e) => setForm({ ...form, category: e.target.value })}
                    className="w-full bg-transparent text-champagne outline-none"
                  >
                    {CATEGORIES.map((c) => (
                      <option key={c} value={c}>
                        {CATEGORY_LABEL[c]}
                      </option>
                    ))}
                  </select>
                </span>
              </label>
              <label className="input-label flex-1">
                Precio
                <span className="input-shell">
                  <input
                    type="number"
                    step="0.01"
                    min="0"
                    value={form.price}
                    required
                    onChange={(e) => setForm({ ...form, price: e.target.value })}
                  />
                </span>
              </label>
            </div>
            {field("description", "Descripción")}
            {field("protein", "Proteína")}
            {field("condiments", "Condimentos", true)}
            {field("ingredients", "Ingredientes", true)}
            {field("preparationNotes", "Notas de preparación", true)}
            <button
              type="submit"
              className="inline-flex w-full items-center justify-center gap-2 rounded-full border border-gold/60 bg-metal px-4 py-2.5 font-semibold text-carbon shadow-gold transition hover:brightness-110"
            >
              <Plus className="h-4 w-4" /> Guardar
            </button>
          </form>
        </Modal>
      )}
    </div>
  );
}
