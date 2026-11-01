package com.ecommerce.ecommerce.service.impl;

import org.springframework.http.HttpStatus;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;
import java.util.Locale;

final class ImageUploadValidator {

    private static final long MAX_IMAGE_SIZE = 5 * 1024 * 1024;
    private static final long MAX_IMAGE_PIXELS = 50_000_000;

    private ImageUploadValidator() {
    }

    static String validate(MultipartFile image) {
        if (image.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Image file is empty");
        }
        if (image.getSize() > MAX_IMAGE_SIZE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Image must be 5 MB or smaller");
        }

        try {
            return detectExtension(image);
        } catch (IOException exception) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Image must be a valid JPEG or PNG file",
                    exception
            );
        }
    }

    private static String detectExtension(MultipartFile image) throws IOException {
        try (InputStream inputStream = image.getInputStream();
             ImageInputStream imageInput = ImageIO.createImageInputStream(inputStream)) {
            if (imageInput == null) {
                throw new IOException("Unsupported image data");
            }

            Iterator<ImageReader> readers = ImageIO.getImageReaders(imageInput);
            if (!readers.hasNext()) {
                throw new IOException("Unsupported image format");
            }

            ImageReader reader = readers.next();
            try {
                reader.setInput(imageInput, true, true);
                String extension = switch (reader.getFormatName().toLowerCase(Locale.ROOT)) {
                    case "jpeg", "jpg" -> ".jpg";
                    case "png" -> ".png";
                    default -> throw new IOException("Unsupported image format");
                };
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                if (width <= 0 || height <= 0 || (long) width * height > MAX_IMAGE_PIXELS) {
                    throw new IOException("Image dimensions are invalid or too large");
                }
                if (reader.read(0) == null) {
                    throw new IOException("Invalid image data");
                }
                return extension;
            } finally {
                reader.dispose();
            }
        }
    }
}
