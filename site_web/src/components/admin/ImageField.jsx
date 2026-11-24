import { useEffect, useRef, useState } from "react";
import { ImagePlus, Trash2, Upload } from "lucide-react";
import { uploadMedia } from "../../api/opsApi";

const ACCEPTED = ["image/png", "image/jpeg", "image/webp"];
const MAX_INPUT_BYTES = 15 * 1024 * 1024;

/**
 * Reduce la imagen en el navegador (lado mayor `maxSide`) para que cargue rápido en
 * móviles. PNG conserva transparencia (logos); fotos se recodifican a WebP/JPEG.
 */
export async function shrinkImage(file, maxSide) {
  if (typeof createImageBitmap !== "function") return file;
  const bitmap = await createImageBitmap(file);
  const scale = Math.min(1, maxSide / Math.max(bitmap.width, bitmap.height));
  if (scale === 1 && file.size < 900 * 1024) return file;
  const canvas = document.createElement("canvas");
  canvas.width = Math.round(bitmap.width * scale);
  canvas.height = Math.round(bitmap.height * scale);
  canvas.getContext("2d").drawImage(bitmap, 0, 0, canvas.width, canvas.height);
  const type = file.type === "image/png" ? "image/png" : "image/webp";
  const blob = await new Promise((resolve) => canvas.toBlob(resolve, type, 0.85));
  if (!blob || blob.size >= file.size) return file;
  return new File([blob], file.name.replace(/\.\w+$/, type === "image/png" ? ".png" : ".webp"), { type });
}

/**
 * Campo de imagen: subir, previsualizar, reemplazar y quitar. El servidor vuelve a validar el
 * tipo real y el peso; aquí se rechaza antes para dar respuesta inmediata.
 * `aspect` fija la proporción del recuadro de vista previa ("1/1", "4/3", "16/9").
 */
export default function ImageField({ label, url, onChange, maxSide = 1280, aspect = "4/3", contain = false }) {
  const input = useRef(null);
  const [preview, setPreview] = useState(url || null);
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);

  useEffect(() => setPreview(url || null), [url]);

  const pick = async (event) => {
    const file = event.target.files?.[0];
    event.target.value = "";
    if (!file) return;
    setError("");
    if (!ACCEPTED.includes(file.type)) return setError("Formato no permitido. Usa PNG, JPEG o WebP.");
    if (file.size > MAX_INPUT_BYTES) return setError("La imagen supera 15 MB.");
    setBusy(true);
    const local = URL.createObjectURL(file);
    setPreview(local);
    try {
      const optimized = await shrinkImage(file, maxSide);
      if (optimized.size > 3 * 1024 * 1024) throw new Error("La imagen optimizada supera 3 MB.");
      const media = await uploadMedia(optimized);
      onChange(media);
      setPreview(media.url);
    } catch (err) {
      setError(err.message);
      setPreview(url || null);
    } finally {
      URL.revokeObjectURL(local);
      setBusy(false);
    }
  };

  return (
    <div className="field-label">
      {label}
      <div className="flex items-start gap-3">
        <div
          className="grid w-32 shrink-0 place-items-center overflow-hidden border border-dashed border-white/15"
          style={{ aspectRatio: aspect }}
        >
          {preview ? (
            <img src={preview} alt="Vista previa" className={`h-full w-full ${contain ? "object-contain" : "object-cover"}`} />
          ) : (
            <ImagePlus className="h-6 w-6 text-zinc-500" aria-hidden="true" />
          )}
        </div>
        <div className="flex flex-col gap-2 normal-case tracking-normal">
          <input ref={input} type="file" accept={ACCEPTED.join(",")} className="hidden" onChange={pick} />
          <button type="button" disabled={busy} onClick={() => input.current?.click()} className="btn btn-sm">
            <Upload className="h-4 w-4" /> {busy ? "Subiendo..." : preview ? "Reemplazar" : "Subir imagen"}
          </button>
          {preview && !busy && (
            <button
              type="button"
              onClick={() => {
                setPreview(null);
                onChange(null);
              }}
              className="btn btn-sm btn-danger"
            >
              <Trash2 className="h-4 w-4" /> Quitar
            </button>
          )}
          <span className="text-[11px] text-zinc-500">PNG, JPEG o WebP. Se optimiza para móvil.</span>
        </div>
      </div>
      {error && <span className="text-xs normal-case tracking-normal text-red-300">{error}</span>}
    </div>
  );
}
