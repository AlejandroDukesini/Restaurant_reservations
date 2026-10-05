package com.restaurant.reservations.service

import com.restaurant.reservations.exception.ValidationBusinessException
import com.restaurant.reservations.model.Restaurant
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

/**
 * Protege el generador de sitios publicos contra XSS almacenado (campos del
 * restaurante sin escapar) y path traversal (slug que escapa del directorio base).
 */
class WebsiteGeneratorServiceTest {

    @TempDir
    lateinit var baseDir: Path

    private fun restaurant(
        slug: String = "casa-prueba",
        name: String = "Casa Prueba",
        description: String = "Cocina de prueba",
        floors: Int = 2
    ) = Restaurant(
        id = 1,
        name = name,
        slug = slug,
        description = description,
        address = "Calle 1",
        phone = "+00 000",
        email = "casa@example.test",
        numberOfTables = 4,
        numberOfChairs = 16,
        numberOfFloors = floors
    )

    private fun service() = WebsiteGeneratorService(baseDir.toString())

    @Test
    fun `genera index, estilos y script dentro del directorio del slug`() {
        val url = service().generateWebsite(restaurant(floors = 3))

        assertEquals("/casa-prueba", url)
        val dir = baseDir.resolve("casa-prueba")
        listOf("index.html", "styles.css", "tables.js").forEach {
            assertTrue(Files.exists(dir.resolve(it)), "falta $it")
        }
        val html = Files.readString(dir.resolve("index.html"))
        assertTrue(html.contains("<h1 class=\"text-3xl font-bold\">Casa Prueba</h1>"))
        assertEquals(3, Regex("class=\"floor-btn").findAll(html).count(), "un boton por piso")
    }

    @Test
    fun `escapa HTML en los campos del restaurante`() {
        val payload = "<script>alert('xss')</script>"
        service().generateWebsite(restaurant(name = payload, description = "\"><img src=x onerror=alert(1)>"))

        val html = Files.readString(baseDir.resolve("casa-prueba/index.html"))
        assertFalse(html.contains("<script>alert"), "el nombre no debe inyectar etiquetas")
        assertFalse(html.contains("<img src=x"), "la descripcion no debe inyectar etiquetas")
        assertTrue(html.contains("&lt;script&gt;alert(&#39;xss&#39;)&lt;/script&gt;"))
    }

    @Test
    fun `escapa el slug dentro del literal JavaScript`() {
        service().generateWebsite(restaurant(slug = "a'b"))

        val js = Files.readString(baseDir.resolve("a'b/tables.js"))
        assertTrue(js.contains("const RESTAURANT_SLUG = 'a\\'b';"))
    }

    @Test
    fun `rechaza un slug que escapa del directorio base`() {
        assertThrows<ValidationBusinessException> {
            service().generateWebsite(restaurant(slug = "../fuera"))
        }
        assertFalse(Files.exists(baseDir.resolveSibling("fuera")))
    }

    @Test
    fun `rechaza un slug vacio que escribiria en el directorio raiz`() {
        assertThrows<ValidationBusinessException> { service().generateWebsite(restaurant(slug = "")) }
        assertThrows<ValidationBusinessException> { service().generateWebsite(restaurant(slug = ".")) }
    }
}
