package com.example.quanlybaotri.ai.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.quanlybaotri.ai.api.AiDtos.*;
import com.example.quanlybaotri.ai.application.ConversationStore.*;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class ConversationStateSerializationTest {
    @Test void roundTripsConversationWithoutAUserToken() throws Exception {
        UUID conversation=UUID.randomUUID(),user=UUID.randomUUID();
        TicketDraft draft=new TicketDraft(UUID.randomUUID(),DraftStatus.READY,UUID.randomUUID(),"TB-01","Máy chủ",
            user,"Người yêu cầu","Mất nguồn","Thiết bị không khởi động","HIGH",List.of(),List.of());
        State source=new State(conversation,user,new ArrayList<>(List.of(new Turn("user","Tạo phiếu"))),draft,Instant.parse("2026-10-26T00:00:00Z"));
        ObjectMapper mapper=new ObjectMapper();String encoded=mapper.writeValueAsString(source);
        State restored=mapper.readValue(encoded,State.class);
        assertThat(restored.id()).isEqualTo(conversation);
        assertThat(restored.draft().equipmentCode()).isEqualTo("TB-01");
        assertThat(encoded).doesNotContain("token");
    }
}
