document.addEventListener("DOMContentLoaded", () => {
    const mesas = document.getElementsByClassName("mesa");

    for (let i = 0; i < mesas.length; i++) {
        mesas[i].addEventListener('click', () => {
            // Cambia directamente entre 'tables' y 'tables-ok'
            mesas[i].classList.toggle('tables');
            mesas[i].classList.toggle('tables-ok');

            if (mesas[i].classList.contains('tables-ok')) {
                console.log("Mesa Reservada");
            } else {
                console.log("Mesa Sin reservar")
            }
        });
    }
});