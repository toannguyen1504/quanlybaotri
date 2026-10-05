package com.example.quanlybaotri.shared.storage;

import io.micrometer.core.instrument.MeterRegistry;
import java.io.InputStream;
import java.net.URI;
import java.security.*;
import java.time.Duration;
import java.util.HexFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.health.contributor.*;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

@Component
public class S3ObjectStorage implements HealthIndicator {
    private static final Logger log=LoggerFactory.getLogger(S3ObjectStorage.class);
    private final S3Client client; private final S3Presigner presigner; private final ObjectStorageProperties p; private final MeterRegistry meters;
    public S3ObjectStorage(S3Client client,S3Presigner presigner,ObjectStorageProperties p,MeterRegistry meters){this.client=client;this.presigner=presigner;this.p=p;this.meters=meters;}
    public String put(String key,String contentType,long size,InputStream source){
        try{MessageDigest digest=MessageDigest.getInstance("SHA-256");try(var input=new DigestInputStream(source,digest)){
            client.putObject(PutObjectRequest.builder().bucket(p.getBucket()).key(key).contentType(contentType).build(),RequestBody.fromInputStream(input,size));}
            meters.counter("object_storage_operations_total","operation","put","result","success").increment();meters.counter("object_storage_uploaded_bytes_total").increment(size);log.info("Object upload completed bucket={} key={} bytes={}",p.getBucket(),key,size);return HexFormat.of().formatHex(digest.digest());
        }catch(Exception ex){meters.counter("object_storage_operations_total","operation","put","result","failure").increment();log.warn("Object upload failed bucket={} key={} bytes={}",p.getBucket(),key,size,ex);throw new ObjectStorageException("Không thể lưu object",ex);}}
    public StoredObject get(String key){try{ResponseInputStream<GetObjectResponse> stream=client.getObject(GetObjectRequest.builder().bucket(p.getBucket()).key(key).build());meters.counter("object_storage_operations_total","operation","get","result","success").increment();log.info("Object download opened bucket={} key={} bytes={}",p.getBucket(),key,stream.response().contentLength());return new StoredObject(stream,stream.response().contentLength(),stream.response().contentType());}catch(Exception ex){meters.counter("object_storage_operations_total","operation","get","result","failure").increment();log.warn("Object download failed bucket={} key={}",p.getBucket(),key,ex);throw new ObjectStorageException("Không thể đọc object",ex);}}
    public URI presign(String key,String type,String filename){try{var request=GetObjectRequest.builder().bucket(p.getBucket()).key(key).responseContentType(type).responseContentDisposition("inline; filename*=UTF-8''"+encode(filename)).build();URI signed=presigner.presignGetObject(GetObjectPresignRequest.builder().signatureDuration(Duration.ofMinutes(p.getPreviewMinutes())).getObjectRequest(request).build()).url().toURI();meters.counter("object_storage_operations_total","operation","presign","result","success").increment();log.info("Object preview URL created bucket={} key={} expiresInMinutes={}",p.getBucket(),key,p.getPreviewMinutes());return signed;}catch(Exception ex){meters.counter("object_storage_operations_total","operation","presign","result","failure").increment();log.warn("Object preview URL failed bucket={} key={}",p.getBucket(),key,ex);throw new ObjectStorageException("Không thể tạo URL xem trước",ex);}}
    public void delete(String key){try{client.deleteObject(DeleteObjectRequest.builder().bucket(p.getBucket()).key(key).build());meters.counter("object_storage_operations_total","operation","delete","result","success").increment();log.info("Object delete completed bucket={} key={}",p.getBucket(),key);}catch(Exception ex){meters.counter("object_storage_operations_total","operation","delete","result","failure").increment();log.warn("Object delete failed bucket={} key={}",p.getBucket(),key,ex);throw new ObjectStorageException("Không thể xóa object",ex);}}
    @Override public Health health(){try{client.headBucket(HeadBucketRequest.builder().bucket(p.getBucket()).build());return Health.up().withDetail("bucket",p.getBucket()).build();}catch(Exception ex){return Health.down(ex).withDetail("bucket",p.getBucket()).build();}}
    private static String encode(String value){return java.net.URLEncoder.encode(value,java.nio.charset.StandardCharsets.UTF_8).replace("+","%20");}
    public record StoredObject(InputStream stream,long contentLength,String contentType){}
}
