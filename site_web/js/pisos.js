// document.getElementById("pisos").addEventListener("change", function() {
//     const url = this.value;
//     if (url) {
//     window.location.href = url; // Redirige a la página seleccionada
//     }
// });

document.addEventListener("DOMContentLoaded", () => {
  const select = document.getElementById("pisos");
  select.addEventListener("change", function () {
    const url = this.value;
    if (url) {
      window.location.href = url;
    }
  });
});