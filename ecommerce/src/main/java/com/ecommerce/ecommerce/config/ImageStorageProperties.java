package com.ecommerce.ecommerce.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.file.Path;

@ConfigurationProperties(prefix = "app.uploads")
public class ImageStorageProperties {

    private Path directory = Path.of("uploads", "products");

    public Path getDirectory() {
        return directory;
    }

    public void setDirectory(Path directory) {
        this.directory = directory;
    }
}
