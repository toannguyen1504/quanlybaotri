package com.example.quanlybaotri.telegram.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "telegram_update_state")
public class TelegramUpdateState {

    @Id
    private short id;

    @Column(name = "last_update_id", nullable = false)
    private long lastUpdateId;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected TelegramUpdateState() {}

    public void advance(long updateId) {
        if (updateId > lastUpdateId) {
            lastUpdateId = updateId;
            updatedAt = Instant.now();
        }
    }

    public long getLastUpdateId() {
        return lastUpdateId;
    }
}
