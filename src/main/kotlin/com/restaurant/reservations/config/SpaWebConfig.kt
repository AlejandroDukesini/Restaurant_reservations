package com.restaurant.reservations.config

import org.springframework.context.annotation.Configuration
import org.springframework.core.io.ClassPathResource
import org.springframework.core.io.Resource
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer
import org.springframework.web.servlet.resource.PathResourceResolver

/**
 * Sirve el build de React (que el Dockerfile copia a classpath:/static/) y
 * reenvia las rutas desconocidas a index.html.
 *
 * Sin esto, recargar el navegador en una ruta de React Router como /login
 * devuelve 404, porque esa ruta solo existe en el cliente y no en Spring.
 */
@Configuration
class SpaWebConfig : WebMvcConfigurer {

    override fun addResourceHandlers(registry: ResourceHandlerRegistry) {
        registry.addResourceHandler("/**")
            .addResourceLocations("classpath:/static/")
            .resourceChain(true)
            .addResolver(object : PathResourceResolver() {
                override fun getResource(resourcePath: String, location: Resource): Resource? {
                    // /api/** nunca es un archivo: se deja pasar a los controladores
                    // para que respondan 404 en JSON en vez de devolver el index.html.
                    if (resourcePath.startsWith("api/")) return null

                    val requested = location.createRelative(resourcePath)
                    return if (requested.exists() && requested.isReadable) requested
                    else ClassPathResource("/static/index.html")
                }
            })
    }
}
