import { LockKeyhole, Martini } from "lucide-react";

export default function Navbar() {
  return (
    <nav className="sticky top-0 z-30 border-b border-white/10 bg-carbon/88 backdrop-blur-xl">
      <div className="mx-auto flex max-w-7xl items-center justify-between px-5 py-4">
        <div className="flex items-center gap-3">
          <span className="grid h-10 w-10 place-items-center rounded-full border border-gold/50 bg-metal shadow-gold">
            <Martini className="h-5 w-5 text-carbon" />
          </span>
          <div>
            <p className="text-sm uppercase tracking-[.28em] text-gold">Maison Noir</p>
            <h1 className="text-xl font-semibold text-champagne">Reservas Premium</h1>
          </div>
        </div>
        <div className="hidden items-center gap-8 text-sm text-zinc-300 md:flex">
          <a href="#mesas" className="transition hover:text-champagne">
            Mesas Disponibles
          </a>
          <a href="#faq" className="transition hover:text-champagne">
            F&Q
          </a>
          <button
            type="button"
            className="inline-flex items-center gap-2 rounded-full border border-gold/40 px-4 py-2 text-champagne shadow-neon transition hover:border-gold/70"
          >
            <LockKeyhole className="h-4 w-4" />
            Mi Cuenta
          </button>
        </div>
      </div>
    </nav>
  );
}
