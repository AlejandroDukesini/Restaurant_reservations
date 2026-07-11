import Navbar from "./Navbar";

export default function Layout({ title, subtitle, actions, children }) {
  return (
    <main className="min-h-screen bg-carbon text-zinc-100">
      <Navbar />
      <section className="mx-auto max-w-7xl px-5 py-8">
        {(title || actions) && (
          <div className="mb-6 flex flex-wrap items-center justify-between gap-x-6 gap-y-4 border-b border-white/10 pb-5">
            <div className="flex flex-col gap-1">
              {subtitle && (
                <span className="text-sm uppercase tracking-[.28em] text-gold">{subtitle}</span>
              )}
              {title && (
                <h2 className="text-3xl font-semibold leading-tight text-champagne">{title}</h2>
              )}
            </div>
            {actions && <div className="flex flex-wrap items-center gap-3">{actions}</div>}
          </div>
        )}
        {children}
      </section>
    </main>
  );
}
