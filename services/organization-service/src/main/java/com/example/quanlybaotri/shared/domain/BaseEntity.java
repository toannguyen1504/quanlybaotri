package com.example.quanlybaotri.shared.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@MappedSuperclass
public abstract class BaseEntity {
    @Id protected UUID id;
    @Column(name="created_at",nullable=false) protected Instant createdAt;
    @Column(name="updated_at",nullable=false) protected Instant updatedAt;
    @Version protected long version;
    @PrePersist void create(){if(id==null)id=UUID.randomUUID();Instant now=Instant.now();createdAt=now;updatedAt=now;}
    @PreUpdate void update(){updatedAt=Instant.now();}
    public UUID getId(){return id;} public Instant getCreatedAt(){return createdAt;} public Instant getUpdatedAt(){return updatedAt;} public long getVersion(){return version;}
}
