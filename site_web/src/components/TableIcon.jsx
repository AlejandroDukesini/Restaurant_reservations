export default function TableIcon({ status, selected }) {
  const occupied = status === "OCCUPIED";

  return (
    <svg viewBox="0 0 96 96" className="h-16 w-16" aria-hidden="true">
      <defs>
        <linearGradient id="gold-table" x1="0%" y1="0%" x2="100%" y2="100%">
          <stop offset="0%" stopColor="#6f501c" />
          <stop offset="28%" stopColor="#ffe9a8" />
          <stop offset="52%" stopColor="#bd8b32" />
          <stop offset="73%" stopColor="#fff0b9" />
          <stop offset="100%" stopColor="#8b6421" />
        </linearGradient>
        <filter id="gold-glow">
          <feGaussianBlur stdDeviation="2.5" result="blur" />
          <feMerge>
            <feMergeNode in="blur" />
            <feMergeNode in="SourceGraphic" />
          </feMerge>
        </filter>
      </defs>
      <rect
        x="22"
        y="22"
        width="52"
        height="52"
        rx="18"
        fill={selected ? "url(#gold-table)" : occupied ? "#3a3a3f" : "#111"}
        filter={selected ? "url(#gold-glow)" : undefined}
      />
      <rect
        x="22"
        y="22"
        width="52"
        height="52"
        rx="18"
        fill="none"
        stroke={selected ? "#fff2bc" : occupied ? "#55545a" : "#8e6a2a"}
        strokeWidth="2.5"
      />
      <rect x="10" y="34" width="12" height="28" rx="6" fill={occupied ? "#2a2a2e" : selected ? "#d8a943" : "#181818"} />
      <rect x="74" y="34" width="12" height="28" rx="6" fill={occupied ? "#2a2a2e" : selected ? "#d8a943" : "#181818"} />
      <rect x="34" y="10" width="28" height="12" rx="6" fill={occupied ? "#2a2a2e" : selected ? "#d8a943" : "#181818"} />
      <rect x="34" y="74" width="28" height="12" rx="6" fill={occupied ? "#2a2a2e" : selected ? "#d8a943" : "#181818"} />
      <circle cx="48" cy="48" r="10" fill={selected ? "rgba(255,255,255,.35)" : occupied ? "#27272b" : "#1d1a12"} />
    </svg>
  );
}
