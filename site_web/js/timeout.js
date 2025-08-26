  // Ocultar loader 1 seg después de que termine de cargar
  window.addEventListener("load", () => {
    setTimeout(() => {
      document.getElementById("loader").style.display = "none";
    }, 300); // 1000 ms = 1 segundo
  });