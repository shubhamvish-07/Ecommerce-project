package com.ecommerce.ecommerce.service.impl;

import com.ecommerce.ecommerce.config.ImageStorageProperties;
import com.ecommerce.ecommerce.service.ImageStorageService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

/**
 * Stores images locally for development. Render's filesystem is temporary, so
 * use the Cloudinary implementation there.
 */
@Service
@ConditionalOnProperty(
        name = "app.image-storage",
        havingValue = "local",
        matchIfMissing = true
)
public class LocalImageStorageService implements ImageStorageService {

    private final ImageStorageProperties properties;

    public LocalImageStorageService(ImageStorageProperties properties) {
        this.properties = properties;
    }

    @Override
    public String store(MultipartFile image) {
        String extension = ImageUploadValidator.validate(image);

        String filename = UUID.randomUUID() + extension;
        Path directory = properties.getDirectory().toAbsolutePath().normalize();

        try (InputStream inputStream = image.getInputStream()) {
            Files.createDirectories(directory);
            Files.copy(
                    inputStream,
                    directory.resolve(filename),
                    StandardCopyOption.REPLACE_EXISTING
            );
        } catch (IOException exception) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "Unable to store product image",
                    exception
            );
        }

        return "/uploads/products/" + filename;
    }
}
