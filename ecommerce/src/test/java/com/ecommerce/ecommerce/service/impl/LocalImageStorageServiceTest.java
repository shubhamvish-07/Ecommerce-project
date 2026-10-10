package com.ecommerce.ecommerce.service.impl;

import com.ecommerce.ecommerce.config.ImageStorageProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocalImageStorageServiceTest {

    @TempDir
    Path tempDirectory;

    private LocalImageStorageService storageService;

    @BeforeEach
    void setUp() {
        ImageStorageProperties properties = new ImageStorageProperties();
        properties.setDirectory(tempDirectory);
        storageService = new LocalImageStorageService(properties);
    }

    @Test
    void storesDecodedImageUsingDetectedFormatInsteadOfDeclaredMimeType() throws IOException {
        MockMultipartFile image = new MockMultipartFile(
                "image",
                "photo.jpg",
                "image/jpeg",
                pngImage()
        );

        String imageUrl = storageService.store(image);

        assertTrue(imageUrl.endsWith(".png"));
        try (var files = Files.list(tempDirectory)) {
            assertEquals(1, files.count());
        }
    }

    @Test
    void acceptsTypicalFourThousandByFourThousandProductPhoto() throws IOException {
        MockMultipartFile image = new MockMultipartFile(
                "image",
                "product.png",
                "image/png",
                pngImage(4000, 4000)
        );

        assertTrue(storageService.store(image).endsWith(".png"));
    }

    @Test
    void rejectsInvalidImageContentsEvenWhenMimeTypeIsAllowed() {
        MockMultipartFile image = new MockMultipartFile(
                "image",
                "photo.png",
                "image/png",
                "not an image".getBytes()
        );

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> storageService.store(image)
        );

        assertEquals(400, exception.getStatusCode().value());
    }

    @Test
    void rejectsImagesLargerThanFiveMegabytes() {
        MockMultipartFile image = new MockMultipartFile(
                "image",
                "large.png",
                "image/png",
                new byte[5 * 1024 * 1024 + 1]
        );

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> storageService.store(image)
        );

        assertEquals(400, exception.getStatusCode().value());
    }

    private byte[] pngImage() throws IOException {
        return pngImage(2, 2);
    }

    private byte[] pngImage(int width, int height) throws IOException {
        BufferedImage source = new BufferedImage(width, height, BufferedImage.TYPE_BYTE_GRAY);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(source, "png", output);
        return output.toByteArray();
    }
}
