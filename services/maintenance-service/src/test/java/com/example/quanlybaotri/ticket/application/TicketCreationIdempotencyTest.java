package com.example.quanlybaotri.ticket.application;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import com.example.quanlybaotri.cost.application.CostService;
import com.example.quanlybaotri.cost.persistence.TicketCostSummaryRepository;
import com.example.quanlybaotri.equipment.client.AssetClient;
import com.example.quanlybaotri.identity.client.IdentityClient;
import com.example.quanlybaotri.identity.domain.RoleName;
import com.example.quanlybaotri.organization.client.OrganizationClient;
import com.example.quanlybaotri.shared.api.ApiException;
import com.example.quanlybaotri.shared.security.CurrentUser.Actor;
import com.example.quanlybaotri.ticket.api.TicketController.CreateTicketRequest;
import com.example.quanlybaotri.ticket.domain.*;
import com.example.quanlybaotri.ticket.persistence.*;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionTemplate;

class TicketCreationIdempotencyTest {
    @Test void rejectsAKeyReusedWithDifferentPayloadBeforeCallingDependencies() {
        TicketRepository tickets=mock(TicketRepository.class);AssetClient assets=mock(AssetClient.class);
        TicketService service=new TicketService(tickets,mock(SlaPolicyRepository.class),mock(TicketEventService.class),
            mock(TicketEventRepository.class),mock(AssignmentRepository.class),mock(WorkLogRepository.class),mock(EntityManager.class),
            mock(TransactionTemplate.class),mock(IdentityClient.class),assets,mock(CostService.class),
            mock(TicketCostSummaryRepository.class),mock(OrganizationClient.class));
        UUID actorId=UUID.randomUUID(),equipmentId=UUID.randomUUID();
        Actor actor=new Actor(actorId,"manager","Manager",Set.of(RoleName.MANAGER),"token","INTERNAL",null,"ALL_SITES",Set.of());
        CreateTicketRequest original=new CreateTicketRequest(equipmentId,UUID.randomUUID(),"Tiêu đề","Mô tả",TicketPriority.HIGH);
        CreateTicketRequest changed=new CreateTicketRequest(equipmentId,original.requesterId(),"Tiêu đề khác","Mô tả",TicketPriority.HIGH);
        String hash=ReflectionTestUtils.invokeMethod(service,"creationHash",original);
        Instant now=Instant.now();MaintenanceTicket existing=new MaintenanceTicket("BT-2026-000001",equipmentId,original.requesterId(),actorId,
            UUID.randomUUID(),UUID.randomUUID(),original.title(),original.description(),original.priority(),now.plusSeconds(60),now.plusSeconds(3600),WarrantyStatus.UNKNOWN,null);
        existing.creationRequest("ai:draft-1",actorId,hash);when(tickets.findByCreationIdempotencyKey("ai:draft-1")).thenReturn(Optional.of(existing));
        assertThatThrownBy(()->service.create(changed,actor,"ai:draft-1")).isInstanceOf(ApiException.class)
            .extracting(error->((ApiException)error).getCode()).isEqualTo("IDEMPOTENCY_KEY_REUSED");
        verifyNoInteractions(assets);
    }
}
