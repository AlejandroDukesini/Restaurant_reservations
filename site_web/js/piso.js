const buttons = document.querySelectorAll(".floor-btn");

buttons.forEach(btn => {

  btn.addEventListener("click", () => {

    const floorNumber = btn.dataset.floor;

    const current = document.querySelector(".floor-active");
    const next = document.querySelector(`.floor[data-floor="${floorNumber}"]`);

    if (!next || current === next) return;

    if (current) {
      current.classList.add("opacity-0", "blur-sm", "scale-95");

      setTimeout(() => {
        current.classList.add("hidden");
        current.classList.remove("floor-active");

        showNext(next);
      }, 300);
    } else {
      showNext(next);
    }

    buttons.forEach(b => b.classList.remove("bg-white/20"));
    btn.classList.add("bg-white/20");

  });

});

function showNext(next) {
  next.classList.remove("hidden");
  void next.offsetWidth; // fuerza reflow
  next.classList.add("floor-active");
  next.classList.remove("opacity-0", "blur-sm", "scale-95");
}