package com.example.quanlybaotri.shared.storage;

import io.micrometer.core.instrument.MeterRegistry;
import java.io.InputStream;
import java.net.URI;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

@Component
public class S3ObjectStorage implements HealthIndicator {
    private static final Logger log = LoggerFactory.getLogger(S3ObjectStorage.class);
    private final S3Client client;
    private final S3Presigner presigner;
    private final ObjectStorageProperties properties;
    private final MeterRegistry meters;

    public S3ObjectStorage(S3Client client, S3Presigner presigner, ObjectStorageProperties properties, MeterRegistry meters) {
        this.client = client;
        this.presigner = presigner;
        this.properties = properties;
        this.meters = meters;
    }

    public String put(String key, String contentType, long size, InputStream source) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (DigestInputStream input = new DigestInputStream(source, digest)) {
                client.putObject(
                    PutObjectRequest.builder().bucket(properties.getBucket()).key(key).contentType(contentType).build(),
                    RequestBody.fromInputStream(input, size)
                );
            }
            meters.counter("object_storage_operations_total", "operation", "put", "result", "success").increment();
            meters.counter("object_storage_uploaded_bytes_total").increment(size);
            log.info("Object upload completed bucket={} key={} bytes={}", properties.getBucket(), key, size);
            return HexFormat.of().formatHex(digest.digest());
        } catch (Exception ex) {
            meters.counter("object_storage_operations_total", "operation", "put", "result", "failure").increment();
            log.warn("Object upload failed bucket={} key={} bytes={}", properties.getBucket(), key, size, ex);
            throw new ObjectStorageException("Không thể lưu object", ex);
        }
    }

    public StoredObject get(String key) {
        try {
            ResponseInputStream<GetObjectResponse> stream = client.getObject(
                GetObjectRequest.builder().bucket(properties.getBucket()).key(key).build()
            );
            meters.counter("object_storage_operations_total", "operation", "get", "result", "success").increment();
            log.info("Object download opened bucket={} key={} bytes={}", properties.getBucket(), key, stream.response().contentLength());
            return new StoredObject(stream, stream.response().contentLength(), stream.response().contentType());
        } catch (Exception ex) {
            meters.counter("object_storage_operations_total", "operation", "get", "result", "failure").increment();
            log.warn("Object download failed bucket={} key={}", properties.getBucket(), key, ex);
            throw new ObjectStorageException("Không thể đọc object", ex);
        }
    }

    public URI presign(String key, String contentType, String filename) {
        try {
            GetObjectRequest request = GetObjectRequest.builder()
                .bucket(properties.getBucket())
                .key(key)
                .responseContentType(contentType)
                .responseContentDisposition("inline; filename*=UTF-8''" + encode(filename))
                .build();
            URI signed = presigner.presignGetObject(
                GetObjectPresignRequest.builder()
                    .signatureDuration(Duration.ofMinutes(properties.getPreviewMinutes()))
                    .getObjectRequest(request)
                    .build()
            ).url().toURI();
            meters.counter("object_storage_operations_total", "operation", "presign", "result", "success").increment();
            log.info("Object preview URL created bucket={} key={} expiresInMinutes={}", properties.getBucket(), key, properties.getPreviewMinutes());
            return signed;
        } catch (Exception ex) {
            meters.counter("object_storage_operations_total", "operation", "presign", "result", "failure").increment();
            log.warn("Object preview URL failed bucket={} key={}", properties.getBucket(), key, ex);
            throw new ObjectStorageException("Không thể tạo URL xem trước", ex);
        }
    }

    public void delete(String key) {
        try {
            client.deleteObject(DeleteObjectRequest.builder().bucket(properties.getBucket()).key(key).build());
            meters.counter("object_storage_operations_total", "operation", "delete", "result", "success").increment();
            log.info("Object delete completed bucket={} key={}", properties.getBucket(), key);
        } catch (Exception ex) {
            meters.counter("object_storage_operations_total", "operation", "delete", "result", "failure").increment();
            log.warn("Object delete failed bucket={} key={}", properties.getBucket(), key, ex);
            throw new ObjectStorageException("Không thể xóa object", ex);
        }
    }

    public boolean existsWithSize(String key, long size) {
        try {
            return client.headObject(HeadObjectRequest.builder().bucket(properties.getBucket()).key(key).build()).contentLength() == size;
        } catch (NoSuchKeyException ex) {
            return false;
        } catch (S3Exception ex) {
            if (ex.statusCode() == 404) return false;
            throw new ObjectStorageException("Không thể kiểm tra object", ex);
        } catch (Exception ex) {
            throw new ObjectStorageException("Không thể kiểm tra object", ex);
        }
    }

    public String sha256(String key) {
        try (InputStream input = get(key).stream()) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            input.transferTo(new java.security.DigestOutputStream(java.io.OutputStream.nullOutputStream(), digest));
            return HexFormat.of().formatHex(digest.digest());
        } catch (Exception ex) {
            throw new ObjectStorageException("Không thể kiểm tra checksum object", ex);
        }
    }

    @Override
    public Health health() {
        try {
            client.headBucket(HeadBucketRequest.builder().bucket(properties.getBucket()).build());
            return Health.up().withDetail("bucket", properties.getBucket()).build();
        } catch (Exception ex) {
            return Health.down(ex).withDetail("bucket", properties.getBucket()).build();
        }
    }

    private static String encode(String name) {
        return java.net.URLEncoder.encode(name, java.nio.charset.StandardCharsets.UTF_8).replace("+", "%20");
    }

    public record StoredObject(InputStream stream, long contentLength, String contentType) {}
}
