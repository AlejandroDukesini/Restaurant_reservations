import { UserRound } from "lucide-react";

/** Foto de perfil redonda; sin foto, las iniciales del nombre. */
export default function Avatar({ url, name, size = 40 }) {
  const initials = (name || "")
    .split(/\s+/)
    .filter(Boolean)
    .slice(0, 2)
    .map((part) => part[0].toUpperCase())
    .join("");
  if (url) {
    return (
      <img
        src={url}
        alt={name || "Foto de perfil"}
        className="shrink-0 rounded-full object-cover"
        style={{ width: size, height: size }}
      />
    );
  }
  return (
    <span
      className="grid shrink-0 place-items-center rounded-full bg-gold/20 font-semibold text-gold"
      style={{ width: size, height: size, fontSize: size * 0.38 }}
      aria-hidden="true"
    >
      {initials || <UserRound className="h-1/2 w-1/2" />}
    </span>
  );
}
