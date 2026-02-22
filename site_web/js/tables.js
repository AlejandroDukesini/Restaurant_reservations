document.querySelectorAll(".mesa").forEach(mesa => {

  const libre = mesa.dataset.libre;
  const reservada = mesa.dataset.reservada;

  function fadeTo(src) {

    const testImg = new Image();

    testImg.onload = () => {
      mesa.classList.add("opacity-0");

      setTimeout(() => {
        mesa.src = src;
        mesa.classList.remove("opacity-0");
      }, 250);
    };

    testImg.onerror = () => {
      console.error("No se pudo cargar:", src);
    };

    testImg.src = src;
  }

  mesa.addEventListener("mouseenter", () => fadeTo(reservada));
  mesa.addEventListener("mouseleave", () => fadeTo(libre));

});
