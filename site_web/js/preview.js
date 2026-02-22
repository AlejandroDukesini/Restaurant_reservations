const mesas = document.querySelectorAll('.mesa');
const mesau = document.querySelectorAll('.mesaU');
const mesad = document.querySelectorAll('.mesaD');
const mesat = document.querySelectorAll('.mesaT');
const panel = document.getElementById("panelPreview");
const contenido = document.getElementById("contenidoPreview");
const cerrarBtn = document.getElementById("cerrar");
const layout = document.getElementById("layout");

let hoverTimer = null;

mesas.forEach(mesa => {

  mesa.addEventListener("mouseenter", () => {

    hoverTimer = setTimeout(() => {
      abrirPreview(mesa);
    }, 400); // 0.4s delay

  });

  mesa.addEventListener("mouseleave", () => {
    clearTimeout(hoverTimer);
  });

});
function abrirPreview(mesa) {

  const url = mesa.dataset.url;
  const moveX = mesa.dataset.moveX || "-10vw";
  const moveY = mesa.dataset.moveY || "0vh";

  const layout = mesa.closest("li").querySelector(".layout");

  fetch(url)
    .then(res => res.text())
    .then(html => {

      contenido.innerHTML = html;

      panel.classList.remove("translate-x-full");

      overlay.classList.remove("opacity-0", "pointer-events-none");
      overlay.classList.add("opacity-100");

      layout.style.transform = `translate(${moveX}, ${moveY})`;

    });
}
function cerrarPreview() {

  panel.classList.add("translate-x-full");

  overlay.classList.add("opacity-0", "pointer-events-none");
  overlay.classList.remove("opacity-100");

  document.querySelectorAll(".layout")
    .forEach(l => l.style.transform = "");
}
cerrarBtn.addEventListener("click", cerrarPreview);
// Cerrar al hacer clic fuera
document.addEventListener("click", (e) => {
  if (!panel.contains(e.target) && !e.target.closest(".mesa")) {
    cerrarPreview();
  }
});

mesas.forEach((mesa) => {

  const img = mesa.querySelector('img');

  mesa.addEventListener('mouseenter', () => {
    img.src = '/site_web/multimedia/reservation/mesa-hover.png';
    mesa.classList.add('scale-105');
  });

  mesa.addEventListener('mouseleave', () => {
    img.src = '/site_web/multimedia/reservation/mesa-sin-reservar.png';
    mesa.classList.remove('scale-105');
  });

});