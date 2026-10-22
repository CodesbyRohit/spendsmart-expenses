package com.spendsmart.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

/**
 * Serves the built React app when {@code app.web.static-dir} is set (the single-origin production deployment).
 * Unknown non-API paths fall back to index.html so client-side routes survive a browser refresh.
 */
@Configuration
public class SpaWebConfig implements WebMvcConfigurer {

    private final AppProperties properties;

    public SpaWebConfig(AppProperties properties) {
        this.properties = properties;
    }

    /** The resource handler never sees the empty root path, so "/" is forwarded to the app explicitly. */
    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        String dir = properties.web().staticDir();
        if (dir != null && !dir.isBlank()) {
            registry.addViewController("/").setViewName("forward:/index.html");
        }
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String dir = properties.web().staticDir();
        if (dir == null || dir.isBlank()) {
            return;
        }
        Path root = Path.of(dir).toAbsolutePath().normalize();
        if (!Files.isDirectory(root)) {
            throw new IllegalStateException("app.web.static-dir does not exist: " + root);
        }
        registry.addResourceHandler("/**")
                .addResourceLocations(new FileSystemResource(root.toString() + "/"))
                .resourceChain(true)
                .addResolver(new PathResourceResolver() {
                    @Override
                    protected Resource getResource(String resourcePath, Resource location) throws IOException {
                        if (resourcePath.startsWith("api/") || resourcePath.startsWith("actuator/")) {
                            return null;
                        }
                        Resource requested = location.createRelative(resourcePath);
                        if (requested.exists() && requested.isReadable()) {
                            return requested;
                        }
                        // Paths with a file extension are genuine missing assets, not client routes.
                        if (resourcePath.contains(".")) {
                            return null;
                        }
                        return location.createRelative("index.html");
                    }
                });
    }
}
