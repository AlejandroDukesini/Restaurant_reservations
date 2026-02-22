const buttons = document.querySelectorAll(".floor-btn");
const floors = document.querySelectorAll(".floor");


buttons.forEach(btn => {

  btn.addEventListener("click", () => {

    const floorNumber = btn.dataset.floor;

    const current = document.querySelector(".floor-active");
    const next = document.querySelector(`.floor[data-floor="${floorNumber}"]`);

    if (current === next) return;

    // 1️⃣ Desenfocar y desvanecer actual
    current.classList.add("opacity-0", "blur-sm", "scale-95");

    setTimeout(() => {
      current.classList.add("hidden");
      current.classList.remove("floor-active");

      // 2️⃣ Mostrar siguiente
      next.classList.remove("hidden");

      // Forzar reflow pequeño
      void next.offsetWidth;

      next.classList.add("floor-active");
      next.classList.remove("opacity-0", "blur-sm", "scale-95");

    }, 300);

    // Botones
    buttons.forEach(b => b.classList.remove("bg-white/20"));
    btn.classList.add("bg-white/20");

  });

});