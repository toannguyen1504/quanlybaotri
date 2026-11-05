package com.example.quanlybaotri.ai.integration;

import static com.example.quanlybaotri.ai.api.AiDtos.DraftStatus.READY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

import com.example.quanlybaotri.ai.api.AiDtos.TicketDraft;
import com.example.quanlybaotri.ai.security.CurrentActor.Actor;
import com.example.quanlybaotri.ai.shared.ApiException;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.net.SocketTimeoutException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.http.MediaType;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.ResourceAccessException;

class BusinessClientTest {

    BusinessClient client;
    MockRestServiceServer server;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new BusinessClient(
            builder,
            "http://maintenance.test",
            "http://asset.test",
            "http://identity.test",
            new SimpleMeterRegistry()
        );
    }

    @AfterEach
    void clearCorrelationId() {
        MDC.clear();
    }

    @Test
    void should_forward_user_token_correlation_and_idempotency_key_when_creating_ticket() {
        UUID draftId = UUID.randomUUID();
        UUID equipmentId = UUID.randomUUID();
        UUID ticketId = UUID.randomUUID();
        Actor actor = new Actor(UUID.randomUUID(), "Admin", Set.of("ADMIN"), "INTERNAL", "user-token");
        TicketDraft draft = new TicketDraft(
            draftId,
            READY,
            equipmentId,
            "EQ-1",
            "Pump",
            null,
            null,
            "Broken pump",
            "The pump stopped",
            "HIGH",
            List.of(),
            List.of()
        );
        MDC.put("correlationId", "corr-123");
        server.expect(requestTo("http://maintenance.test/api/v1/tickets"))
            .andExpect(method(POST))
            .andExpect(header("Authorization", "Bearer user-token"))
            .andExpect(header("X-Correlation-Id", "corr-123"))
            .andExpect(header("Idempotency-Key", "ai:" + draftId))
            .andRespond(withSuccess("{\"id\":\"" + ticketId + "\",\"code\":\"T-1\"}", MediaType.APPLICATION_JSON));

        BusinessClient.TicketRef created = client.create(draft, actor);

        assertThat(created.id()).isEqualTo(ticketId);
        server.verify();
    }

    @Test
    void should_preserve_downstream_client_error_status() {
        UUID ticketId = UUID.randomUUID();
        Actor actor = new Actor(UUID.randomUUID(), "Requester", Set.of("REQUESTER"), "CUSTOMER", "user-token");
        server.expect(requestTo("http://maintenance.test/api/v1/tickets/" + ticketId))
            .andRespond(withStatus(HttpStatus.FORBIDDEN));

        assertThatThrownBy(() -> client.ticket(ticketId, actor))
            .isInstanceOfSatisfying(ApiException.class, error -> {
                assertThat(error.status()).isEqualTo(HttpStatus.FORBIDDEN);
                assertThat(error.code()).isEqualTo("DOWNSTREAM_REJECTED");
            });
    }

    @Test
    void should_map_downstream_server_error_to_service_unavailable() {
        UUID ticketId = UUID.randomUUID();
        Actor actor = new Actor(UUID.randomUUID(), "Requester", Set.of("REQUESTER"), "CUSTOMER", "user-token");
        server.expect(requestTo("http://maintenance.test/api/v1/tickets/" + ticketId))
            .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        assertThatThrownBy(() -> client.ticket(ticketId, actor))
            .isInstanceOfSatisfying(ApiException.class, error -> {
                assertThat(error.status()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
                assertThat(error.code()).isEqualTo("DEPENDENCY_UNAVAILABLE");
            });
    }

    @Test
    void should_call_asset_with_bearer_token_and_correlation_id() {
        Actor actor = new Actor(UUID.randomUUID(), "Requester", Set.of("REQUESTER"), "CUSTOMER", "asset-token");
        MDC.put("correlationId", "corr-asset");
        server.expect(requestTo("http://asset.test/api/v1/equipment?size=5&sort=assetCode,asc&q=pump"))
            .andExpect(method(GET))
            .andExpect(header("Authorization", "Bearer asset-token"))
            .andExpect(header("X-Correlation-Id", "corr-asset"))
            .andRespond(withSuccess("{\"content\":[],\"totalElements\":0}", MediaType.APPLICATION_JSON));

        assertThat(client.searchEquipment("pump", 5, actor)).isEmpty();
        server.verify();
    }

    @Test
    void should_call_identity_with_expected_query_and_bearer_token() {
        UUID customerId = UUID.randomUUID();
        Actor actor = new Actor(UUID.randomUUID(), "Admin", Set.of("ADMIN"), "INTERNAL", "identity-token");
        server.expect(requestTo("http://identity.test/api/v1/users?customerId=" + customerId
                + "&accountType=CUSTOMER&size=10&sort=fullName,asc"))
            .andExpect(method(GET))
            .andExpect(header("Authorization", "Bearer identity-token"))
            .andRespond(withSuccess("{\"content\":[],\"totalElements\":0}", MediaType.APPLICATION_JSON));

        assertThat(client.requesters(customerId, 10, actor)).isEmpty();
        server.verify();
    }

    @Test
    void should_map_downstream_timeout_to_service_unavailable() {
        UUID ticketId = UUID.randomUUID();
        Actor actor = new Actor(UUID.randomUUID(), "Requester", Set.of("REQUESTER"), "CUSTOMER", "user-token");
        server.expect(requestTo("http://maintenance.test/api/v1/tickets/" + ticketId))
            .andRespond(request -> {
                throw new ResourceAccessException("read timed out", new SocketTimeoutException("read timed out"));
            });

        assertThatThrownBy(() -> client.ticket(ticketId, actor))
            .isInstanceOfSatisfying(ApiException.class, error -> {
                assertThat(error.status()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
                assertThat(error.code()).isEqualTo("DEPENDENCY_UNAVAILABLE");
            });
    }
}
