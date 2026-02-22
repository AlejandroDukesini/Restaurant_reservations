const images = document.querySelectorAll(".tooltip-image");

images.forEach(image => {

  const tooltip = image.parentElement.querySelector(".tooltip");

  image.addEventListener("mouseenter", () => {
    tooltip.classList.remove("opacity-0", "scale-95");
    tooltip.classList.add("opacity-100", "scale-100");
  });

  image.addEventListener("mouseleave", () => {
    tooltip.classList.add("opacity-0", "scale-95");
    tooltip.classList.remove("opacity-100", "scale-100");
  });

});
