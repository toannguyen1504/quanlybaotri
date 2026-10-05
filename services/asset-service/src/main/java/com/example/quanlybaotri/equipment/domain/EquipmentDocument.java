package com.example.quanlybaotri.equipment.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name="equipment_documents")
public class EquipmentDocument {
    @Id private UUID id;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="equipment_id") private Equipment equipment;
    @Enumerated(EnumType.STRING) @Column(name="document_type",nullable=false,length=30) private EquipmentDocumentType type;
    @Column(name="storage_key",nullable=false,unique=true,length=255) private String storageKey;
    @Column(name="original_name",nullable=false,length=255) private String originalName;
    @Column(name="content_type",nullable=false,length=150) private String contentType;
    @Column(name="file_size",nullable=false) private long fileSize;
    @Column(nullable=false,length=64) private String sha256;
    @Column(name="uploaded_by_id",nullable=false) private UUID uploadedById;
    @Column(name="uploaded_by_name",nullable=false,length=150) private String uploadedByName;
    @Column(name="created_at",nullable=false) private Instant createdAt;
    @Column(name="deleted_at") private Instant deletedAt;
    @Column(name="deleted_by_id") private UUID deletedById;
    protected EquipmentDocument(){}
    public EquipmentDocument(Equipment equipment,EquipmentDocumentType type,String key,String name,String contentType,long size,String sha256,UUID uploaderId,String uploaderName){id=UUID.randomUUID();this.equipment=equipment;this.type=type;storageKey=key;originalName=name;this.contentType=contentType;fileSize=size;this.sha256=sha256;uploadedById=uploaderId;uploadedByName=uploaderName==null||uploaderName.isBlank()?"Người dùng":uploaderName;createdAt=Instant.now();}
    public UUID getId(){return id;} public Equipment getEquipment(){return equipment;} public EquipmentDocumentType getType(){return type;} public String getStorageKey(){return storageKey;} public String getOriginalName(){return originalName;} public String getContentType(){return contentType;} public long getFileSize(){return fileSize;} public String getSha256(){return sha256;} public UUID getUploadedById(){return uploadedById;} public String getUploadedByName(){return uploadedByName;} public Instant getCreatedAt(){return createdAt;} public Instant getDeletedAt(){return deletedAt;} public void markDeleted(UUID actorId){deletedAt=Instant.now();deletedById=actorId;}
}
