package com.ecommerce.ecommerce.service.impl;

import com.ecommerce.ecommerce.service.ImageStorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;

@Service
@ConditionalOnProperty(name = "app.image-storage", havingValue = "cloudinary")
public class CloudinaryImageStorageService implements ImageStorageService {

    private static final Logger log = LoggerFactory.getLogger(CloudinaryImageStorageService.class);
    private static final String DELIVERY_TRANSFORMATION = "f_auto,q_auto,w_1400,c_limit";

    private final CloudinaryUploader uploader;

    public CloudinaryImageStorageService(
            @Value("${app.cloudinary.cloud-name:}") String cloudName,
            @Value("${app.cloudinary.api-key:}") String apiKey,
            @Value("${app.cloudinary.api-secret:}") String apiSecret,
            @Value("${app.cloudinary.folder:mul-and-warp/products}") String folder) {
        this.uploader = new CloudinaryUploader(
                require("CLOUDINARY_CLOUD_NAME", cloudName),
                require("CLOUDINARY_API_KEY", apiKey),
                require("CLOUDINARY_API_SECRET", apiSecret),
                folder,
                CloudinaryUploader.DEFAULT_API_BASE,
                CloudinaryUploader.newHttpClient(),
                () -> System.currentTimeMillis() / 1000
        );
    }

    @Override
    public String store(MultipartFile image) {
        String extension = ImageUploadValidator.validate(image);
        String contentType = extension.equals(".png") ? "image/png" : "image/jpeg";
        try {
            return withDeliveryTransformation(
                    uploader.upload(image.getBytes(), "product" + extension, contentType));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw storageFailure(exception);
        } catch (IOException exception) {
            throw storageFailure(exception);
        }
    }

    static String withDeliveryTransformation(String url) {
        String marker = "/image/upload/";
        int index = url.indexOf(marker);
        if (index < 0) {
            return url;
        }
        int insertAt = index + marker.length();
        return url.substring(0, insertAt)
                + DELIVERY_TRANSFORMATION + "/"
                + url.substring(insertAt);
    }

    private static ResponseStatusException storageFailure(Exception cause) {
        log.error("Cloudinary upload failed", cause);
        return new ResponseStatusException(
                HttpStatus.BAD_GATEWAY,
                "Unable to store product image",
                cause
        );
    }

    private static String require(String name, String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(
                    "app.image-storage=cloudinary requires the " + name + " environment variable");
        }
        return value.trim();
    }
}
