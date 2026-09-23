package com.example.quanlybaotri.telegram.application;

import com.example.quanlybaotri.telegram.domain.TelegramUpdateState;
import com.example.quanlybaotri.telegram.persistence.TelegramUpdateStateRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TelegramUpdateCheckpoint {

    private static final short STATE_ID = 1;
    private final TelegramUpdateStateRepository states;

    public TelegramUpdateCheckpoint(TelegramUpdateStateRepository states) {
        this.states = states;
    }

    @Transactional(readOnly = true)
    public long nextOffset() {
        return states
            .findById(STATE_ID)
            .orElseThrow(() -> new IllegalStateException("Telegram update state is missing"))
            .getLastUpdateId() + 1;
    }

    @Transactional
    public void advance(long updateId) {
        TelegramUpdateState state = states
            .findById(STATE_ID)
            .orElseThrow(() -> new IllegalStateException("Telegram update state is missing"));
        state.advance(updateId);
    }
}
