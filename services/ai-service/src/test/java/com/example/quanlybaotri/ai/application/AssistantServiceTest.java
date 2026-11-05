package com.example.quanlybaotri.ai.application;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import com.example.quanlybaotri.ai.application.ConversationStore.State;
import com.example.quanlybaotri.ai.application.NvidiaAiClient.*;
import com.example.quanlybaotri.ai.config.AiProperties;
import com.example.quanlybaotri.ai.integration.BusinessClient;
import com.example.quanlybaotri.ai.security.CurrentActor.Actor;
import com.example.quanlybaotri.ai.shared.ApiException;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;

class AssistantServiceTest {
    @Test void outOfScopeQuestionUsesGeneralChat() {
        ConversationStore store=mock(ConversationStore.class);AiRateLimiter limiter=mock(AiRateLimiter.class);
        NvidiaAiClient ai=mock(NvidiaAiClient.class);KnowledgeIndex knowledge=mock(KnowledgeIndex.class);BusinessClient business=mock(BusinessClient.class);
        UUID user=UUID.randomUUID(),conversation=UUID.randomUUID();State state=new State(conversation,user,new ArrayList<>(),null,Instant.now());
        when(store.require(user,conversation)).thenReturn(state);
        when(ai.classify(anyString(),anyList(),anyString())).thenReturn(new IntentResult(Intent.OUT_OF_SCOPE,"","","","","","","","","","",""));
        when(ai.generalChat(eq("Kể một câu chuyện ngắn"),anyList())).thenReturn("Ngày xửa ngày xưa...");
        AssistantService service=new AssistantService(store,limiter,ai,knowledge,business,new AiProperties(),new SimpleMeterRegistry());
        Actor actor=new Actor(user,"Người dùng",Set.of("CUSTOMER"),"CUSTOMER","token");

        var response=service.message(conversation,"Kể một câu chuyện ngắn",actor);

        assertThat(response.reply()).isEqualTo("Ngày xửa ngày xưa...");
        verify(ai).generalChat("Kể một câu chuyện ngắn",state.turns());verify(store).save(state);verifyNoInteractions(business);
    }

    @Test void technicianCannotCreateTicketDraft() {
        ConversationStore store=mock(ConversationStore.class);AiRateLimiter limiter=mock(AiRateLimiter.class);
        NvidiaAiClient ai=mock(NvidiaAiClient.class);KnowledgeIndex knowledge=mock(KnowledgeIndex.class);BusinessClient business=mock(BusinessClient.class);
        UUID user=UUID.randomUUID(),conversation=UUID.randomUUID();State state=new State(conversation,user,new ArrayList<>(),null,Instant.now());
        when(store.require(user,conversation)).thenReturn(state);
        when(ai.classify(anyString(),anyList(),anyString())).thenReturn(new IntentResult(Intent.CREATE_TICKET,"","","","","","","","","","",""));
        AssistantService service=new AssistantService(store,limiter,ai,knowledge,business,new AiProperties(),new SimpleMeterRegistry());
        Actor actor=new Actor(user,"Kỹ thuật viên",Set.of("TECHNICIAN"),"INTERNAL","token");
        assertThatThrownBy(()->service.message(conversation,"Tạo phiếu",actor)).isInstanceOf(ApiException.class)
            .hasMessageContaining("không có quyền tạo phiếu");
        verifyNoInteractions(business);
    }
}
