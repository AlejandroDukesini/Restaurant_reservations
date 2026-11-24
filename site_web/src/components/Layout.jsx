import { RefreshCw, WifiOff } from "lucide-react";
import Navbar from "./Navbar";
import { useOnline, useUpdateAvailable } from "../pwa/pwa";

export default function Layout({ title, subtitle, actions, children }) {
  const online = useOnline();
  const update = useUpdateAvailable();

  return (
    <main className="min-h-screen overflow-x-hidden bg-carbon pb-20 text-zinc-100 lg:pb-0">
      <Navbar />
      {!online && (
        <div role="alert" className="flex items-center justify-center gap-2 bg-red-500/15 px-4 py-2 text-center text-sm text-red-300">
          <WifiOff className="h-4 w-4 shrink-0" />
          Sin conexión. Puedes consultar lo cargado, pero pedidos, estados y mensajes necesitan conexión.
        </div>
      )}
      {update.available && (
        <div className="flex items-center justify-center gap-3 bg-gold/15 px-4 py-2 text-sm text-champagne">
          Hay una versión nueva de la aplicación.
          <button type="button" onClick={update.apply} className="btn btn-sm btn-primary">
            <RefreshCw className="h-4 w-4" /> Actualizar
          </button>
        </div>
      )}
      <section className="mx-auto w-full max-w-7xl px-4 py-6 sm:px-5 sm:py-8">
        {(title || actions) && (
          <div className="mb-6 flex flex-wrap items-center justify-between gap-x-6 gap-y-4 border-b border-white/10 pb-5">
            <div className="motion-enter flex flex-col gap-1">
              {subtitle && <span className="text-xs uppercase tracking-[.28em] text-gold sm:text-sm">{subtitle}</span>}
              {title && <h2 className="text-2xl font-semibold leading-tight text-champagne sm:text-3xl">{title}</h2>}
            </div>
            {actions && (
              <div className="motion-enter flex flex-wrap items-center gap-3" style={{ "--i": 1 }}>
                {actions}
              </div>
            )}
          </div>
        )}
        {/* Al cambiar de sección el contenido entra desenfocado y se aclara. */}
        <div className="motion-blur-in">{children}</div>
      </section>
    </main>
  );
}
