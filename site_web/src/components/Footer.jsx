import { Camera } from "lucide-react";

export default function Footer() {
  return (
    <footer className="border-t border-white/10 bg-black px-5 py-8">
      <div className="mx-auto grid max-w-7xl gap-6 text-sm text-zinc-400 md:grid-cols-4">
        <div>
          <p className="text-champagne">Maison Noir Group</p>
          <p className="mt-2">Reservas multi-tenant para experiencias gastronómicas premium.</p>
        </div>
        <a href="#mesas" className="transition hover:text-champagne">
          Mesas Disponibles
        </a>
        <a id="faq" href="#faq" className="transition hover:text-champagne">
          F&Q
        </a>
        <a
          className="inline-flex items-center gap-2 text-champagne transition hover:text-gold"
          href="https://www.instagram.com/"
          target="_blank"
          rel="noreferrer"
        >
          <Camera className="h-4 w-4" />
          Restaurant&apos;s Instagram
        </a>
      </div>
    </footer>
  );
}
