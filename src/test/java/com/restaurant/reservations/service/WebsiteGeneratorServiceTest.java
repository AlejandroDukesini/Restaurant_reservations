package com.restaurant.reservations.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.restaurant.reservations.exception.ValidationBusinessException;
import com.restaurant.reservations.model.Restaurant;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Protege el generador de sitios publicos contra XSS almacenado (campos del
 * restaurante sin escapar) y path traversal (slug que escapa del directorio base).
 */
class WebsiteGeneratorServiceTest {

    @TempDir
    Path baseDir;

    private static Restaurant restaurant(String slug, String name, String description, int floors) {
        Restaurant restaurant = new Restaurant(
            name, slug, description, "Calle 1", "+00 000", "casa@example.test", 4, 16, floors
        );
        restaurant.setId(1L);
        return restaurant;
    }

    private static Restaurant restaurant() {
        return restaurant("casa-prueba", "Casa Prueba", "Cocina de prueba", 2);
    }

    private WebsiteGeneratorService service() {
        return new WebsiteGeneratorService(baseDir.toString());
    }

    @Test
    @DisplayName("genera index, estilos y script dentro del directorio del slug")
    void generaArchivosDentroDelDirectorioDelSlug() throws Exception {
        String url = service().generateWebsite(restaurant("casa-prueba", "Casa Prueba", "Cocina de prueba", 3));

        assertEquals("/casa-prueba", url);
        Path dir = baseDir.resolve("casa-prueba");
        for (String file : List.of("index.html", "styles.css", "tables.js")) {
            assertTrue(Files.exists(dir.resolve(file)), "falta " + file);
        }
        String html = Files.readString(dir.resolve("index.html"));
        assertTrue(html.contains("<h1 class=\"text-3xl font-bold\">Casa Prueba</h1>"));
        assertEquals(3, Pattern.compile("class=\"floor-btn").matcher(html).results().count(), "un boton por piso");
    }

    @Test
    @DisplayName("escapa HTML en los campos del restaurante")
    void escapaHtmlEnLosCampos() throws Exception {
        String payload = "<script>alert('xss')</script>";
        service().generateWebsite(restaurant("casa-prueba", payload, "\"><img src=x onerror=alert(1)>", 2));

        String html = Files.readString(baseDir.resolve("casa-prueba/index.html"));
        assertFalse(html.contains("<script>alert"), "el nombre no debe inyectar etiquetas");
        assertFalse(html.contains("<img src=x"), "la descripcion no debe inyectar etiquetas");
        assertTrue(html.contains("&lt;script&gt;alert(&#39;xss&#39;)&lt;/script&gt;"));
    }

    @Test
    @DisplayName("escapa el slug dentro del literal JavaScript")
    void escapaElSlugEnJavaScript() throws Exception {
        service().generateWebsite(restaurant("a'b", "Casa Prueba", "Cocina de prueba", 2));

        String js = Files.readString(baseDir.resolve("a'b/tables.js"));
        assertTrue(js.contains("const RESTAURANT_SLUG = 'a\\'b';"));
    }

    @Test
    @DisplayName("rechaza un slug que escapa del directorio base")
    void rechazaSlugQueEscapaDelDirectorioBase() {
        assertThrows(
            ValidationBusinessException.class,
            () -> service().generateWebsite(restaurant("../fuera", "Casa Prueba", "Cocina de prueba", 2))
        );
        assertFalse(Files.exists(baseDir.resolveSibling("fuera")));
    }

    @Test
    @DisplayName("rechaza un slug vacio que escribiria en el directorio raiz")
    void rechazaSlugVacio() {
        assertThrows(
            ValidationBusinessException.class,
            () -> service().generateWebsite(restaurant("", "Casa Prueba", "Cocina de prueba", 2))
        );
        assertThrows(
            ValidationBusinessException.class,
            () -> service().generateWebsite(restaurant(".", "Casa Prueba", "Cocina de prueba", 2))
        );
    }
}
