package com.example.quanlybaotri.ticket.application;

import com.example.quanlybaotri.shared.storage.S3ObjectStorage;
import com.example.quanlybaotri.ticket.persistence.AttachmentRepository;
import java.io.*;
import java.nio.file.*;
import java.security.*;
import java.util.HexFormat;
import org.slf4j.*;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name="app.storage.migration.enabled",havingValue="true")
public class AttachmentStorageMigrationRunner implements ApplicationRunner {
    private static final Logger log=LoggerFactory.getLogger(AttachmentStorageMigrationRunner.class);
    private final AttachmentRepository attachments;private final S3ObjectStorage storage;private final Environment environment;private final ConfigurableApplicationContext context;
    public AttachmentStorageMigrationRunner(AttachmentRepository attachments,S3ObjectStorage storage,Environment environment,ConfigurableApplicationContext context){this.attachments=attachments;this.storage=storage;this.environment=environment;this.context=context;}
    @Override public void run(ApplicationArguments args)throws Exception{
        Path root=Paths.get(environment.getProperty("app.storage.migration.legacy-root","/legacy-uploads")).toAbsolutePath().normalize();
        boolean dryRun=environment.getProperty("app.storage.migration.dry-run",Boolean.class,true);
        int uploaded=0,skipped=0,failed=0;
        for(var attachment:attachments.findAll()){
            if(attachment.getDeletedAt()!=null)continue;
            Path source=root.resolve(attachment.getStorageKey()).normalize();
            try{
                if(!source.startsWith(root)||!Files.isRegularFile(source))throw new IOException("missing legacy file");
                String localHash=hash(source);if(!localHash.equalsIgnoreCase(attachment.getSha256()))throw new IOException("legacy checksum mismatch");
                if(storage.existsWithSize(attachment.getStorageKey(),attachment.getFileSize())){
                    if(!storage.sha256(attachment.getStorageKey()).equalsIgnoreCase(attachment.getSha256()))throw new IOException("remote checksum mismatch");
                    skipped++;continue;
                }
                if(!dryRun){try(InputStream input=Files.newInputStream(source)){String uploadedHash=storage.put(attachment.getStorageKey(),attachment.getContentType(),attachment.getFileSize(),input);if(!uploadedHash.equalsIgnoreCase(attachment.getSha256()))throw new IOException("uploaded checksum mismatch");}if(!storage.existsWithSize(attachment.getStorageKey(),attachment.getFileSize())||!storage.sha256(attachment.getStorageKey()).equalsIgnoreCase(attachment.getSha256()))throw new IOException("remote verification failed");}
                uploaded++;
            }catch(Exception ex){failed++;log.error("Attachment migration failed id={} key={}: {}",attachment.getId(),attachment.getStorageKey(),ex.getMessage());}
        }
        log.info("Attachment migration completed dryRun={} uploaded={} skipped={} failed={}",dryRun,uploaded,skipped,failed);
        context.close();
        if(failed>0)throw new IllegalStateException("Attachment migration failed for "+failed+" object(s)");
    }
    private static String hash(Path path)throws Exception{MessageDigest digest=MessageDigest.getInstance("SHA-256");try(InputStream input=Files.newInputStream(path)){input.transferTo(new DigestOutputStream(OutputStream.nullOutputStream(),digest));}return HexFormat.of().formatHex(digest.digest());}
}
