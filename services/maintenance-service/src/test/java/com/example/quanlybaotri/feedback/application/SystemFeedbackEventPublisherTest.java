package com.example.quanlybaotri.feedback.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.example.quanlybaotri.feedback.domain.*;
import com.example.quanlybaotri.shared.messaging.*;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.ObjectMapper;

class SystemFeedbackEventPublisherTest {

    @Test
    void publishesOnlyIdentifiersAndWorkflowMetadata() {
        OutboxRepository outbox = mock(OutboxRepository.class);
        when(outbox.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        SystemFeedback feedback = new SystemFeedback(
            UUID.randomUUID(),
            SystemFeedbackCategory.BUG_REPORT,
            "Tiêu đề bí mật",
            "Nội dung bí mật"
        );
        ReflectionTestUtils.setField(feedback, "id", UUID.randomUUID());

        new SystemFeedbackEventPublisher(outbox, new ObjectMapper())
            .publish(feedback, feedback.getRequesterId(), "CREATED");

        ArgumentCaptor<OutboxEvent> captured = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outbox).save(captured.capture());
        assertThat(captured.getValue().getRoutingKey())
            .isEqualTo("maintenance.system-feedback.changed");
        assertThat(captured.getValue().getPayloadJson())
            .contains("feedbackId", "requesterId", "BUG_REPORT", "NEW")
            .doesNotContain("Tiêu đề bí mật", "Nội dung bí mật", "response");
    }
}
