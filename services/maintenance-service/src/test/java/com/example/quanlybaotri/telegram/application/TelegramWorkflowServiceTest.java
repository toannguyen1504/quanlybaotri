package com.example.quanlybaotri.telegram.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import com.example.quanlybaotri.cost.application.CostService;
import com.example.quanlybaotri.identity.client.IdentityClient;
import com.example.quanlybaotri.identity.domain.RoleName;
import com.example.quanlybaotri.telegram.domain.TelegramCommandReceipt;
import com.example.quanlybaotri.telegram.persistence.TelegramCommandReceiptRepository;
import com.example.quanlybaotri.ticket.api.TicketController.WorkLogView;
import com.example.quanlybaotri.ticket.application.*;
import com.example.quanlybaotri.ticket.persistence.TicketRepository;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;

class TelegramWorkflowServiceTest {
    @Test
    void replaysExistingWorkLogWithoutWritingItAgain() {
        UUID technicianId = UUID.randomUUID();
        UUID ticketId = UUID.randomUUID();
        UUID workLogId = UUID.randomUUID();
        TicketService tickets = mock(TicketService.class);
        IdentityClient identities = mock(IdentityClient.class);
        TelegramCommandReceiptRepository receipts = mock(TelegramCommandReceiptRepository.class);
        TelegramCommandReceipt receipt = new TelegramCommandReceipt("telegram-command:stable", "WORK_LOG");
        receipt.complete(workLogId);
        when(receipts.findById("telegram-command:stable")).thenReturn(Optional.of(receipt));
        when(identities.get(technicianId)).thenReturn(new IdentityClient.UserRef(
            technicianId, "tech", "Kỹ thuật viên", "tech@example.test", "INTERNAL",
            null, "ALL_SITES", Set.of(), "ALL_REGIONS", Set.of(), true,
            Set.of(RoleName.TECHNICIAN)
        ));
        WorkLogView expected = new WorkLogView(
            workLogId, technicianId, "Kỹ thuật viên", "Kiểm tra nguồn", 15, Instant.now()
        );
        when(tickets.workLogs(eq(ticketId), any())).thenReturn(List.of(expected));
        TelegramWorkflowService service = new TelegramWorkflowService(
            tickets, mock(TicketRepository.class), mock(CostService.class),
            mock(AttachmentService.class), identities, receipts
        );

        WorkLogView result = service.addWorkLog(
            ticketId, technicianId, "Kiểm tra nguồn", 15, "telegram-command:stable"
        );

        assertThat(result.id()).isEqualTo(workLogId);
        verify(tickets, never()).addWorkLog(any(), anyString(), anyInt(), any());
    }
}
