import { useCallback, useEffect, useState } from "react";
import { useConfirm } from "../ConfirmDialog";
import { Archive, ArchiveRestore, ImageOff, Pencil, Plus, Tags, Trash2 } from "lucide-react";
import Modal from "./Modal";
import ImageField from "./ImageField";
import { createMenuItem, deleteMenuItem, updateMenuItem } from "../../api/menuApi";
import { deleteCategory, fetchAdminMenu, fetchCategories, saveCategory, setAvailability, setMenuItemActive } from "../../api/opsApi";
import { ErrorState, Loader } from "../LoadState";
import { money } from "../../utils/status";

const EMPTY = {
  name: "",
  description: "",
  categoryId: "",
  price: "",
  prepMinutes: "",
  protein: "",
  condiments: "",
  ingredients: "",
  preparationNotes: "",
  imageMediaId: null,
  imageUrl: null,
  available: true
};

export default function MenuAdmin() {
  const [items, setItems] = useState(null);
  const [categories, setCategories] = useState([]);
  const [status, setStatus] = useState("");
  const [editing, setEditing] = useState(null);
  const [form, setForm] = useState(EMPTY);
  const [showArchived, setShowArchived] = useState(false);
  const [categoryForm, setCategoryForm] = useState(null);
  const confirm = useConfirm();

  const load = useCallback(async () => {
    try {
      const [menu, cats] = await Promise.all([fetchAdminMenu(), fetchCategories()]);
      setItems(menu);
      setCategories(cats);
      setStatus("");
    } catch (err) {
      setStatus(err.message);
    }
  }, []);

  useEffect(() => {
    load();
  }, [load]);

  const act = async (fn, ok) => {
    setStatus("");
    try {
      await fn();
      if (ok) setStatus(ok);
      await load();
    } catch (err) {
      setStatus(err.message);
    }
  };

  const openCreate = () => {
    setForm({ ...EMPTY, categoryId: categories[0]?.id ?? "" });
    setEditing({});
  };

  const openEdit = (item) => {
    setForm({
      ...EMPTY,
      ...item,
      categoryId: item.categoryId ?? "",
      prepMinutes: item.prepMinutes ?? "",
      description: item.description || "",
      protein: item.protein || "",
      condiments: item.condiments || "",
      ingredients: item.ingredients || "",
      preparationNotes: item.preparationNotes || ""
    });
    setEditing(item);
  };

  const save = (event) => {
    event.preventDefault();
    const payload = {
      name: form.name,
      description: form.description,
      categoryId: form.categoryId ? Number(form.categoryId) : undefined,
      price: Number(form.price),
      prepMinutes: form.prepMinutes === "" ? null : Number(form.prepMinutes),
      protein: form.protein,
      condiments: form.condiments,
      ingredients: form.ingredients,
      preparationNotes: form.preparationNotes,
      imageMediaId: form.imageMediaId || null,
      available: form.available
    };
    act(async () => {
      if (editing?.id) await updateMenuItem(editing.id, payload);
      else await createMenuItem(payload);
      setEditing(null);
    }, "Producto guardado.");
  };

  const set = (key) => (e) => setForm({ ...form, [key]: e.target.value });
  const visible = (items || []).filter((i) => showArchived || i.active);
  const grouped = categories.map((c) => ({ ...c, items: visible.filter((i) => i.categoryId === c.id) }));
  const orphan = visible.filter((i) => !categories.some((c) => c.id === i.categoryId));

  return (
    <div>
      <div className="mb-4 flex flex-wrap items-center justify-between gap-3">
        <label className="flex items-center gap-2 text-sm text-zinc-400">
          <input type="checkbox" className="h-4 w-4 w-auto" checked={showArchived} onChange={(e) => setShowArchived(e.target.checked)} />
          Mostrar archivados
        </label>
        <div className="flex gap-2">
          <button type="button" onClick={() => setCategoryForm({ name: "", sortOrder: categories.length + 1 })} className="btn">
            <Tags className="h-4 w-4" /> Nueva categoría
          </button>
          <button type="button" onClick={openCreate} disabled={categories.length === 0} className="btn btn-primary bg-metal">
            <Plus className="h-4 w-4" /> Nuevo producto
          </button>
        </div>
      </div>

      {status && <p className="motion-fade mb-3 text-sm text-zinc-300" role="status">{status}</p>}

      {!items ? (
        status ? <ErrorState message={status} onRetry={load} /> : <Loader label="Cargando menú..." />
      ) : (
        <div className="space-y-6">
          {[...grouped, ...(orphan.length ? [{ id: "x", name: "Sin categoría activa", items: orphan }] : [])].map((cat) => (
            <section key={cat.id}>
              <div className="mb-2 flex items-center justify-between gap-2 border-b border-white/10 pb-2">
                <h3 className="text-sm uppercase tracking-[.2em] text-gold">
                  {cat.name} <span className="text-zinc-500">· {cat.items.length}</span>
                </h3>
                {cat.id !== "x" && (
                  <div className="flex gap-1">
                    <button type="button" onClick={() => setCategoryForm(cat)} className="grid h-9 w-9 place-items-center text-zinc-400 hover:text-champagne" aria-label={`Editar categoría ${cat.name}`}>
                      <Pencil className="h-4 w-4" />
                    </button>
                    <button
                      type="button"
                      onClick={async () =>
                        (await confirm({ title: `¿Eliminar la categoría ${cat.name}?`, message: "Solo es posible si no tiene productos activos.", confirmLabel: "Eliminar", tone: "danger" })) &&
                        act(() => deleteCategory(cat.id), "Categoría eliminada.")
                      }
                      className="grid h-9 w-9 place-items-center text-zinc-400 hover:text-red-400"
                      aria-label={`Eliminar categoría ${cat.name}`}
                    >
                      <Trash2 className="h-4 w-4" />
                    </button>
                  </div>
                )}
              </div>
              {cat.items.length === 0 ? (
                <p className="text-xs text-zinc-500">Sin productos.</p>
              ) : (
                <div className="grid gap-3 sm:grid-cols-2 xl:grid-cols-3">
                  {cat.items.map((item) => (
                    <article key={item.id} className={`flex gap-3 border border-white/10 bg-white/[.03] p-3 ${item.active ? "" : "opacity-60"}`}>
                      {item.imageUrl ? (
                        <img src={item.imageUrl} alt={item.name} loading="lazy" className="h-20 w-20 shrink-0 object-cover" />
                      ) : (
                        <span className="grid h-20 w-20 shrink-0 place-items-center bg-white/[.04] text-zinc-500">
                          <ImageOff className="h-5 w-5" />
                        </span>
                      )}
                      <div className="min-w-0 flex-1">
                        <p className="truncate font-semibold text-champagne">{item.name}</p>
                        <p className="text-xs text-zinc-400">
                          {money(item.price)}
                          {item.prepMinutes ? ` · ~${item.prepMinutes} min` : ""}
                        </p>
                        <div className="mt-1 flex flex-wrap gap-1">
                          {!item.active && <span className="chip chip-muted">Archivado</span>}
                          {item.active && (
                            <button
                              type="button"
                              onClick={() => act(() => setAvailability(item.id, !item.available))}
                              className={`chip ${item.available ? "chip-ready" : "chip-danger"}`}
                              title="Cambiar disponibilidad"
                            >
                              {item.available ? "Disponible" : "Agotado"}
                            </button>
                          )}
                        </div>
                        <div className="mt-2 flex gap-1">
                          <button type="button" onClick={() => openEdit(item)} className="btn btn-sm" aria-label={`Editar ${item.name}`}>
                            <Pencil className="h-4 w-4" />
                          </button>
                          <button
                            type="button"
                            onClick={() => act(() => setMenuItemActive(item.id, !item.active), item.active ? "Producto archivado." : "Producto activado.")}
                            className="btn btn-sm"
                            aria-label={item.active ? `Archivar ${item.name}` : `Activar ${item.name}`}
                            title={item.active ? "Archivar" : "Activar"}
                          >
                            {item.active ? <Archive className="h-4 w-4" /> : <ArchiveRestore className="h-4 w-4" />}
                          </button>
                          <button
                            type="button"
                            onClick={async () =>
                              (await confirm({
                                title: `¿Eliminar ${item.name}?`,
                                message: "Si ya se pidió alguna vez, se archivará para conservar el historial y las métricas.",
                                confirmLabel: "Eliminar",
                                tone: "danger"
                              })) && act(() => deleteMenuItem(item.id), "Producto eliminado.")
                            }
                            className="btn btn-sm btn-danger"
                            aria-label={`Eliminar ${item.name}`}
                          >
                            <Trash2 className="h-4 w-4" />
                          </button>
                        </div>
                      </div>
                    </article>
                  ))}
                </div>
              )}
            </section>
          ))}
        </div>
      )}

      {editing && (
        <Modal title={editing.id ? "Editar producto" : "Nuevo producto"} onClose={() => setEditing(null)} wide>
          <form onSubmit={save} className="grid gap-3 sm:grid-cols-2">
            <label className="field-label sm:col-span-2">Nombre<input required maxLength={255} className="field" value={form.name} onChange={set("name")} /></label>
            <label className="field-label">
              Categoría
              <select required className="field" value={form.categoryId} onChange={set("categoryId")}>
                {categories.map((c) => (
                  <option key={c.id} value={c.id}>{c.name}</option>
                ))}
              </select>
            </label>
            <label className="field-label">Precio<input required type="number" min="0" step="0.01" className="field" value={form.price} onChange={set("price")} /></label>
            <label className="field-label">Tiempo estimado (min)<input type="number" min="0" max="600" className="field" value={form.prepMinutes} onChange={set("prepMinutes")} /></label>
            <label className="field-label flex-row items-center gap-2 self-end normal-case">
              <input type="checkbox" className="h-4 w-4 w-auto" checked={form.available} onChange={(e) => setForm({ ...form, available: e.target.checked })} />
              Disponible
            </label>
            <label className="field-label sm:col-span-2">Descripción<textarea rows={2} maxLength={500} className="field" value={form.description} onChange={set("description")} /></label>
            <div className="sm:col-span-2">
              <ImageField
                label="Foto del plato (opcional)"
                url={form.imageUrl}
                onChange={(media) => setForm({ ...form, imageMediaId: media?.id || null, imageUrl: media?.url || null })}
              />
            </div>
            <label className="field-label">Proteína<input maxLength={255} className="field" value={form.protein} onChange={set("protein")} /></label>
            <label className="field-label">Condimentos<input maxLength={1000} className="field" value={form.condiments} onChange={set("condiments")} /></label>
            <label className="field-label sm:col-span-2">Ingredientes<textarea rows={2} maxLength={2000} className="field" value={form.ingredients} onChange={set("ingredients")} /></label>
            <label className="field-label sm:col-span-2">Preparación / observaciones<textarea rows={2} maxLength={1000} className="field" value={form.preparationNotes} onChange={set("preparationNotes")} /></label>
            <button type="submit" className="btn btn-primary bg-metal sm:col-span-2">Guardar</button>
          </form>
        </Modal>
      )}

      {categoryForm && (
        <Modal title={categoryForm.id ? "Editar categoría" : "Nueva categoría"} onClose={() => setCategoryForm(null)}>
          <form
            onSubmit={(e) => {
              e.preventDefault();
              act(async () => {
                await saveCategory(categoryForm.id, { name: categoryForm.name, sortOrder: Number(categoryForm.sortOrder) || 0 });
                setCategoryForm(null);
              }, "Categoría guardada.");
            }}
            className="space-y-3"
          >
            <label className="field-label">Nombre<input required maxLength={80} className="field" value={categoryForm.name} onChange={(e) => setCategoryForm({ ...categoryForm, name: e.target.value })} /></label>
            <label className="field-label">Orden<input type="number" min="0" className="field" value={categoryForm.sortOrder} onChange={(e) => setCategoryForm({ ...categoryForm, sortOrder: e.target.value })} /></label>
            <button type="submit" className="btn btn-primary bg-metal w-full">Guardar</button>
          </form>
        </Modal>
      )}
    </div>
  );
}
