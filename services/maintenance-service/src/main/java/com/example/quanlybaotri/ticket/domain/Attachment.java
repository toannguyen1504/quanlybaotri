package com.example.quanlybaotri.ticket.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "attachments")
public class Attachment {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ticket_id")
    private MaintenanceTicket ticket;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "work_log_id")
    private WorkLog workLog;

    @Column(name = "uploaded_by_id", nullable = false)
    private UUID uploadedById;

    @Column(name = "storage_key", nullable = false, unique = true, length = 255)
    private String storageKey;

    @Column(name = "original_name", nullable = false, length = 255)
    private String originalName;

    @Column(name = "content_type", nullable = false, length = 100)
    private String contentType;

    @Column(name = "file_size", nullable = false)
    private long fileSize;

    @Column(nullable = false, length = 64)
    private String sha256;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected Attachment() {}

    public Attachment(
        MaintenanceTicket t,
        WorkLog w,
        UUID userId,
        String key,
        String name,
        String type,
        long size,
        String hash
    ) {
        id = UUID.randomUUID();
        ticket = t;
        workLog = w;
        uploadedById = userId;
        storageKey = key;
        originalName = name;
        contentType = type;
        fileSize = size;
        sha256 = hash;
        createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public MaintenanceTicket getTicket() {
        return ticket;
    }

    public UUID getUploadedById() {
        return uploadedById;
    }

    public String getStorageKey() {
        return storageKey;
    }

    public String getOriginalName() {
        return originalName;
    }

    public String getContentType() {
        return contentType;
    }

    public long getFileSize() {
        return fileSize;
    }

    public String getSha256() {
        return sha256;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
