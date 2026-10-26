package com.example.quanlybaotri.chat.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class ChatReadStateTest {
    @Test
    void advancesOnlyToHigherSequences() {
        ChatReadState state = new ChatReadState(UUID.randomUUID(), UUID.randomUUID());

        state.advance(12);
        state.advance(4);

        assertThat(state.getLastReadSequence()).isEqualTo(12);
    }
}
