package com.example.quanlybaotri.shared.storage;

import java.net.URI;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.*;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.*;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

@Configuration
public class ObjectStorageConfig {
    @Bean AwsCredentialsProvider storageCredentials(ObjectStorageProperties p) {
        return !blank(p.getAccessKey()) && !blank(p.getSecretKey())
            ? StaticCredentialsProvider.create(AwsBasicCredentials.create(p.getAccessKey(), p.getSecretKey()))
            : DefaultCredentialsProvider.create();
    }
    @Bean(destroyMethod="close") S3Client s3Client(ObjectStorageProperties p, AwsCredentialsProvider credentials) {
        var b=S3Client.builder().region(Region.of(p.getRegion())).credentialsProvider(credentials)
            .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(p.isPathStyle()).build());
        if(!blank(p.getEndpoint())) b.endpointOverride(URI.create(p.getEndpoint()));
        return b.build();
    }
    @Bean(destroyMethod="close") S3Presigner s3Presigner(ObjectStorageProperties p, AwsCredentialsProvider credentials) {
        var b=S3Presigner.builder().region(Region.of(p.getRegion())).credentialsProvider(credentials)
            .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(p.isPathStyle()).build());
        String endpoint=blank(p.getPublicEndpoint())?p.getEndpoint():p.getPublicEndpoint();
        if(!blank(endpoint)) b.endpointOverride(URI.create(endpoint));
        return b.build();
    }
    private static boolean blank(String v){return v==null||v.isBlank();}
}
