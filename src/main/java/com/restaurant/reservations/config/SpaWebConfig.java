package com.restaurant.reservations.config;

import java.io.IOException;
import org.springframework.boot.web.server.MimeMappings;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.boot.web.servlet.server.ConfigurableServletWebServerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.CacheControl;
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
        // PWA: el service worker y el manifest se revalidan siempre; si se cachearan, los
        // dispositivos instalados no detectarian las versiones nuevas de la aplicacion.
        registry.addResourceHandler("/sw.js", "/manifest.webmanifest")
            .addResourceLocations("classpath:/static/")
            .setCacheControl(CacheControl.noCache());
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

    /** Tipo MIME estandar del manifest de la PWA (Tomcat no lo conoce). */
    @Bean
    public WebServerFactoryCustomizer<ConfigurableServletWebServerFactory> pwaMimeTypes() {
        return factory -> {
            MimeMappings mappings = new MimeMappings(MimeMappings.DEFAULT);
            mappings.add("webmanifest", "application/manifest+json");
            factory.setMimeMappings(mappings);
        };
    }
}
