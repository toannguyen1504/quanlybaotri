package com.example.quanlybaotri.shared.storage;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "S3_INTEGRATION_TEST", matches = "true")
class S3ObjectStorageIntegrationTest {

    @Test
    void uploadsPresignsDownloadsAndKeepsBucketPrivate() throws Exception {
        String endpoint = "http://127.0.0.1:9000";
        ObjectStorageProperties properties = new ObjectStorageProperties();
        properties.setEndpoint(endpoint);
        properties.setPublicEndpoint(endpoint);
        properties.setRegion("us-east-1");
        properties.setPathStyle(true);
        properties.setBucket("maintenance-attachments");
        properties.setAccessKey("maintenance-service");
        properties.setSecretKey("change-maintenance-s3-secret");
        properties.setPreviewMinutes(2);

        ObjectStorageConfig config = new ObjectStorageConfig();
        var credentials = config.storageCredentials(properties);
        String key = "smoke/" + UUID.randomUUID();
        byte[] content = "presigned-smoke".getBytes(StandardCharsets.UTF_8);
        HttpClient http = HttpClient.newHttpClient();

        try (var client = config.s3Client(properties, credentials);
             var presigner = config.s3Presigner(properties, credentials)) {
            S3ObjectStorage storage = new S3ObjectStorage(client, presigner, properties, new SimpleMeterRegistry());
            try {
                storage.put(key, "text/plain", content.length, new java.io.ByteArrayInputStream(content));
                URI signed = storage.presign(key, "text/plain", "smoke.txt");
                assertThat(signed.getRawQuery()).contains("X-Amz-Expires=120");

                var signedResponse = http.send(
                    HttpRequest.newBuilder(signed).GET().build(),
                    HttpResponse.BodyHandlers.ofByteArray()
                );
                assertThat(signedResponse.statusCode()).isEqualTo(200);
                assertThat(signedResponse.body()).isEqualTo(content);

                URI anonymous = URI.create(endpoint + "/maintenance-attachments/" + key);
                var anonymousResponse = http.send(
                    HttpRequest.newBuilder(anonymous).GET().build(),
                    HttpResponse.BodyHandlers.discarding()
                );
                assertThat(anonymousResponse.statusCode()).isEqualTo(403);
            } finally {
                storage.delete(key);
            }
        }
    }
}
