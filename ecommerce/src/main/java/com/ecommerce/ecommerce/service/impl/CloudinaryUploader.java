package com.ecommerce.ecommerce.service.impl;

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
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.function.LongSupplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class CloudinaryUploader {

    static final String DEFAULT_API_BASE = "https://api.cloudinary.com";
    private static final Pattern SECURE_URL =
            Pattern.compile("\"secure_url\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"");
    private static final Pattern ERROR_MESSAGE =
            Pattern.compile("\"message\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"");

    private final HttpClient httpClient;
    private final URI uploadEndpoint;
    private final String apiKey;
    private final String apiSecret;
    private final String folder;
    private final LongSupplier epochSeconds;

    CloudinaryUploader(
            String cloudName,
            String apiKey,
            String apiSecret,
            String folder,
            String apiBase,
            HttpClient httpClient,
            LongSupplier epochSeconds) {
        this.uploadEndpoint = URI.create(
                trimTrailingSlash(apiBase) + "/v1_1/" + cloudName + "/image/upload");
        this.apiKey = apiKey;
        this.apiSecret = apiSecret;
        this.folder = folder;
        this.httpClient = httpClient;
        this.epochSeconds = epochSeconds;
    }

    static HttpClient newHttpClient() {
        return HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    }

    String upload(byte[] data, String filename, String contentType)
            throws IOException, InterruptedException {
        Map<String, String> fields = new TreeMap<>();
        fields.put("timestamp", Long.toString(epochSeconds.getAsLong()));
        if (folder != null && !folder.isBlank()) {
            fields.put("folder", folder);
        }
        String signature = sign(fields, apiSecret);
        String boundary = "----ecommerce" + UUID.randomUUID().toString().replace("-", "");
        byte[] body = multipartBody(boundary, fields, signature, data, filename, contentType);

        HttpRequest request = HttpRequest.newBuilder(uploadEndpoint)
                .timeout(Duration.ofSeconds(45))
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                .build();
        HttpResponse<String> response =
                httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        if (response.statusCode() / 100 != 2) {
            throw new IOException("Cloudinary rejected the upload (HTTP "
                    + response.statusCode() + "): " + extractErrorMessage(response.body()));
        }
        Matcher matcher = SECURE_URL.matcher(response.body());
        if (!matcher.find()) {
            throw new IOException("Cloudinary response did not contain a secure_url");
        }
        String secureUrl = unescapeJson(matcher.group(1));
        URI parsedUrl = URI.create(secureUrl);
        if (!"https".equalsIgnoreCase(parsedUrl.getScheme())
                || !"res.cloudinary.com".equalsIgnoreCase(parsedUrl.getHost())) {
            throw new IOException("Cloudinary returned an unexpected secure_url");
        }
        return secureUrl;
    }

    static String sign(Map<String, String> fields, String apiSecret) {
        StringBuilder toSign = new StringBuilder();
        for (Map.Entry<String, String> entry : new TreeMap<>(fields).entrySet()) {
            if (toSign.length() > 0) {
                toSign.append('&');
            }
            toSign.append(entry.getKey()).append('=').append(entry.getValue());
        }
        toSign.append(apiSecret);

        try {
            byte[] digest = MessageDigest.getInstance("SHA-1")
                    .digest(toSign.toString().getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte value : digest) {
                hex.append(String.format("%02x", value));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-1 is not available", exception);
        }
    }

    private byte[] multipartBody(
            String boundary,
            Map<String, String> fields,
            String signature,
            byte[] data,
            String filename,
            String contentType) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream(data.length + 1024);
        for (Map.Entry<String, String> entry : fields.entrySet()) {
            writeTextPart(out, boundary, entry.getKey(), entry.getValue());
        }
        writeTextPart(out, boundary, "api_key", apiKey);
        writeTextPart(out, boundary, "signature", signature);
        out.write(("--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"file\"; filename=\""
                + filename.replace("\"", "") + "\"\r\n"
                + "Content-Type: " + contentType + "\r\n\r\n")
                .getBytes(StandardCharsets.UTF_8));
        out.write(data);
        out.write(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
        return out.toByteArray();
    }

    private static void writeTextPart(
            ByteArrayOutputStream out, String boundary, String name, String value) throws IOException {
        out.write(("--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"" + name + "\"\r\n\r\n"
                + value + "\r\n").getBytes(StandardCharsets.UTF_8));
    }

    private static String extractErrorMessage(String body) {
        Matcher matcher = ERROR_MESSAGE.matcher(body == null ? "" : body);
        return matcher.find() ? unescapeJson(matcher.group(1)) : "no error message";
    }

    static String unescapeJson(String value) {
        StringBuilder out = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char current = value.charAt(i);
            if (current != '\\' || i + 1 >= value.length()) {
                out.append(current);
                continue;
            }
            char next = value.charAt(++i);
            switch (next) {
                case 'n' -> out.append('\n');
                case 't' -> out.append('\t');
                case 'r' -> out.append('\r');
                case 'b' -> out.append('\b');
                case 'f' -> out.append('\f');
                case 'u' -> {
                    if (i + 4 < value.length()) {
                        out.append((char) Integer.parseInt(value.substring(i + 1, i + 5), 16));
                        i += 4;
                    } else {
                        out.append("\\u");
                    }
                }
                default -> out.append(next);
            }
        }
        return out.toString();
    }

    private static String trimTrailingSlash(String value) {
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }
}
