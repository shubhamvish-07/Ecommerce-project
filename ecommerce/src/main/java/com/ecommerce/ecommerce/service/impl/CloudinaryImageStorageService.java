package com.ecommerce.ecommerce.service.impl;

import com.ecommerce.ecommerce.service.ImageStorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Stores product images on Cloudinary so they survive restarts of hosts with a
 * temporary filesystem (for example Render's free tier).
 *
 * <p>Enabled with {@code app.image-storage=cloudinary}. The returned value is the
 * full HTTPS URL of the uploaded image, which is what gets saved in the
 * {@code products.image_url} column. The storefront already accepts absolute URLs.
 *
 * <p>Uses Cloudinary's signed upload API directly, so no extra dependency is needed.
 * The API secret is only used to compute the request signature and is never sent,
 * logged or returned.
 */
@Service
@ConditionalOnProperty(name = "app.image-storage", havingValue = "cloudinary")
public class CloudinaryImageStorageService implements ImageStorageService {

    private static final Logger log = LoggerFactory.getLogger(CloudinaryImageStorageService.class);

    private static final long MAX_IMAGE_SIZE = 5L * 1024 * 1024;
    private static final String DEFAULT_API_BASE_URL = "https://api.cloudinary.com/v1_1";
    private static final Pattern CLOUD_NAME = Pattern.compile("[A-Za-z0-9_-]+");
    private static final Pattern API_KEY = Pattern.compile("[A-Za-z0-9_-]+");
    private static final Pattern FOLDER = Pattern.compile("[A-Za-z0-9_/-]+");
    private static final Pattern SECURE_URL =
            Pattern.compile("\"secure_url\"\\s*:\\s*\"([^\"]+)\"");

    private final String cloudName;
    private final String apiKey;
    private final String apiSecret;
    private final String folder;
    private final String apiBaseUrl;
    private final HttpClient httpClient;

    @Autowired
    public CloudinaryImageStorageService(
            @Value("${app.cloudinary.cloud-name:}") String cloudName,
            @Value("${app.cloudinary.api-key:}") String apiKey,
            @Value("${app.cloudinary.api-secret:}") String apiSecret,
            @Value("${app.cloudinary.folder:ecommerce/products}") String folder) {
        this(cloudName, apiKey, apiSecret, folder, DEFAULT_API_BASE_URL);
    }

    /** Visible for tests, which point {@code apiBaseUrl} at a local fake server. */
    CloudinaryImageStorageService(
            String cloudName,
            String apiKey,
            String apiSecret,
            String folder,
            String apiBaseUrl) {
        this.cloudName = require(cloudName, "CLOUDINARY_CLOUD_NAME", CLOUD_NAME);
        this.apiKey = require(apiKey, "CLOUDINARY_API_KEY", API_KEY);
        if (apiSecret == null || apiSecret.isBlank()) {
            throw new IllegalStateException(
                    "CLOUDINARY_API_SECRET must be set when app.image-storage=cloudinary");
        }
        this.apiSecret = apiSecret.trim();
        this.folder = require(folder, "CLOUDINARY_FOLDER", FOLDER);
        this.apiBaseUrl = apiBaseUrl;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    @Override
    public String store(MultipartFile image) {
        if (image.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Image file is empty");
        }
        if (image.getSize() > MAX_IMAGE_SIZE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Image must be 5 MB or smaller");
        }

        byte[] data;
        try {
            data = image.getBytes();
        } catch (IOException exception) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Image must be a valid JPEG or PNG file",
                    exception);
        }

        String extension = detectExtension(data);
        if (extension == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Image must be a valid JPEG or PNG file");
        }
        String mimeType = extension.equals(".png") ? "image/png" : "image/jpeg";

        String timestamp = String.valueOf(Instant.now().getEpochSecond());
        // Cloudinary signs the upload parameters sorted by name, joined with '&',
        // followed by the API secret. 'file' and 'api_key' are not part of the signature.
        String signature = sign("folder=" + folder + "&timestamp=" + timestamp, apiSecret);

        String boundary = "----ecommerce" + UUID.randomUUID().toString().replace("-", "");
        byte[] body = multipartBody(boundary, timestamp, signature, "product" + extension, mimeType, data);

        HttpRequest request = HttpRequest.newBuilder(
                        URI.create(apiBaseUrl + "/" + cloudName + "/image/upload"))
                .timeout(Duration.ofSeconds(30))
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                .build();

        HttpResponse<String> response;
        try {
            response = httpClient.send(
                    request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw uploadFailed(exception);
        } catch (IOException exception) {
            throw uploadFailed(exception);
        }

        if (response.statusCode() != 200) {
            log.warn("Cloudinary upload rejected with HTTP {}: {}",
                    response.statusCode(), abbreviate(response.body()));
            throw uploadFailed(null);
        }

        Matcher matcher = SECURE_URL.matcher(response.body());
        if (!matcher.find()) {
            log.warn("Cloudinary upload response had no secure_url: {}", abbreviate(response.body()));
            throw uploadFailed(null);
        }

        String url = matcher.group(1).replace("\\/", "/");
        if (!url.startsWith("https://")) {
            log.warn("Cloudinary returned a non-HTTPS image URL");
            throw uploadFailed(null);
        }
        return url;
    }

    /** SHA-1 hex digest of the parameters to sign followed by the API secret. */
    static String sign(String paramsToSign, String secret) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            byte[] hash = digest.digest((paramsToSign + secret).getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-1 is not available", exception);
        }
    }

    /** Detects JPEG or PNG from the file's leading bytes; returns null for anything else. */
    static String detectExtension(byte[] data) {
        if (data.length >= 8
                && (data[0] & 0xFF) == 0x89 && data[1] == 'P' && data[2] == 'N' && data[3] == 'G'
                && data[4] == 0x0D && data[5] == 0x0A && data[6] == 0x1A && data[7] == 0x0A) {
            return ".png";
        }
        if (data.length >= 3
                && (data[0] & 0xFF) == 0xFF && (data[1] & 0xFF) == 0xD8 && (data[2] & 0xFF) == 0xFF) {
            return ".jpg";
        }
        return null;
    }

    private byte[] multipartBody(
            String boundary,
            String timestamp,
            String signature,
            String filename,
            String mimeType,
            byte[] data) {
        ByteArrayOutputStream out = new ByteArrayOutputStream(data.length + 1024);
        writeField(out, boundary, "api_key", apiKey);
        writeField(out, boundary, "timestamp", timestamp);
        writeField(out, boundary, "signature", signature);
        writeField(out, boundary, "folder", folder);
        writeText(out, "--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"file\"; filename=\"" + filename + "\"\r\n"
                + "Content-Type: " + mimeType + "\r\n\r\n");
        out.writeBytes(data);
        writeText(out, "\r\n--" + boundary + "--\r\n");
        return out.toByteArray();
    }

    private static void writeField(ByteArrayOutputStream out, String boundary, String name, String value) {
        writeText(out, "--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"" + name + "\"\r\n\r\n"
                + value + "\r\n");
    }

    private static void writeText(ByteArrayOutputStream out, String text) {
        out.writeBytes(text.getBytes(StandardCharsets.UTF_8));
    }

    private static String require(String value, String envName, Pattern allowed) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(
                    envName + " must be set when app.image-storage=cloudinary");
        }
        String trimmed = value.trim();
        if (!allowed.matcher(trimmed).matches()) {
            throw new IllegalStateException(envName + " contains unsupported characters");
        }
        return trimmed;
    }

    private static ResponseStatusException uploadFailed(Exception cause) {
        return new ResponseStatusException(
                HttpStatus.BAD_GATEWAY,
                "Unable to store product image",
                cause);
    }

    private static String abbreviate(String text) {
        if (text == null) {
            return "";
        }
        return text.length() <= 300 ? text : text.substring(0, 300) + "...";
    }
}
