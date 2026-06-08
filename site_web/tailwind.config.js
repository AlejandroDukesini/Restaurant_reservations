/** @type {import('tailwindcss').Config} */
export default {
  content: ["./index.html", "./src/**/*.{js,jsx}"],
  theme: {
    extend: {
      colors: {
        carbon: "#050505",
        graphite: "#121212",
        obsidian: "#0A0A0B",
        champagne: "#F8E7B0",
        gold: "#D8A943",
        antique: "#8E6A2A"
      },
      boxShadow: {
        gold: "0 0 28px rgba(216,169,67,.42), inset 0 0 18px rgba(248,231,176,.12)",
        neon: "0 0 22px rgba(248,231,176,.24)"
      },
      backgroundImage: {
        metal:
          "linear-gradient(135deg,#76551c 0%,#f8e7b0 22%,#b8862f 42%,#fff1bc 54%,#9f7428 75%,#f3d17a 100%)"
      }
    }
  },
  plugins: []
};
