package com.example.quanlybaotri.shared.storage;

import java.net.URI;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

@Configuration
public class ObjectStorageConfig {
    @Bean
    AwsCredentialsProvider storageCredentials(ObjectStorageProperties p) {
        if (!blank(p.getAccessKey()) && !blank(p.getSecretKey())) {
            return StaticCredentialsProvider.create(AwsBasicCredentials.create(p.getAccessKey(), p.getSecretKey()));
        }
        return DefaultCredentialsProvider.create();
    }

    @Bean(destroyMethod = "close")
    S3Client s3Client(ObjectStorageProperties p, AwsCredentialsProvider credentials) {
        var builder = S3Client.builder()
            .region(Region.of(p.getRegion()))
            .credentialsProvider(credentials)
            .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(p.isPathStyle()).build());
        if (!blank(p.getEndpoint())) builder.endpointOverride(URI.create(p.getEndpoint()));
        return builder.build();
    }

    @Bean(destroyMethod = "close")
    S3Presigner s3Presigner(ObjectStorageProperties p, AwsCredentialsProvider credentials) {
        var builder = S3Presigner.builder()
            .region(Region.of(p.getRegion()))
            .credentialsProvider(credentials)
            .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(p.isPathStyle()).build());
        String endpoint = blank(p.getPublicEndpoint()) ? p.getEndpoint() : p.getPublicEndpoint();
        if (!blank(endpoint)) builder.endpointOverride(URI.create(endpoint));
        return builder.build();
    }

    private static boolean blank(String value) { return value == null || value.isBlank(); }
}
