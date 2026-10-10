package com.restaurant.reservations.config;

import java.io.IOException;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

/**
 * Sirve el build de React (que el Dockerfile copia a classpath:/static/) y
 * reenvia las rutas desconocidas a index.html.
 *
 * Sin esto, recargar el navegador en una ruta de React Router como /login
 * devuelve 404, porque esa ruta solo existe en el cliente y no en Spring.
 */
@Configuration
public class SpaWebConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/**")
            .addResourceLocations("classpath:/static/")
            .resourceChain(true)
            .addResolver(new PathResourceResolver() {
                @Override
                protected Resource getResource(String resourcePath, Resource location) throws IOException {
                    // /api/** nunca es un archivo: se deja pasar a los controladores
                    // para que respondan 404 en JSON en vez de devolver el index.html.
                    if (resourcePath.startsWith("api/")) {
                        return null;
                    }

                    Resource requested = location.createRelative(resourcePath);
                    return requested.exists() && requested.isReadable()
                        ? requested
                        : new ClassPathResource("/static/index.html");
                }
            });
    }
}
