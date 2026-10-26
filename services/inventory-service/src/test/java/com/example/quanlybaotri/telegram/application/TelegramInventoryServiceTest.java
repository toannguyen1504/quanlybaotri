package com.example.quanlybaotri.telegram.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import com.example.quanlybaotri.inventory.api.InventoryController.TicketPartView;
import com.example.quanlybaotri.inventory.application.InventoryService;
import com.example.quanlybaotri.telegram.domain.TelegramCommandReceipt;
import com.example.quanlybaotri.telegram.persistence.TelegramCommandReceiptRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;

class TelegramInventoryServiceTest {
    @Test
    void replaysExistingPartUsageWithoutIssuingStockAgain() {
        UUID ticketId = UUID.randomUUID();
        UUID technicianId = UUID.randomUUID();
        UUID usageId = UUID.randomUUID();
        UUID partId = UUID.randomUUID();
        InventoryService inventory = mock(InventoryService.class);
        TelegramCommandReceiptRepository receipts = mock(TelegramCommandReceiptRepository.class);
        TelegramCommandReceipt receipt = new TelegramCommandReceipt("telegram-command:part", "USE_PART");
        receipt.complete(usageId);
        when(receipts.findById("telegram-command:part")).thenReturn(Optional.of(receipt));
        TicketPartView expected = new TicketPartView(usageId, partId, "LK-01", "Nguồn",
            BigDecimal.ONE, BigDecimal.TEN, technicianId, "Kỹ thuật viên", Instant.now());
        when(inventory.ticketPartsTelegram(ticketId, technicianId)).thenReturn(List.of(expected));
        TelegramInventoryService service = new TelegramInventoryService(inventory, receipts);

        TicketPartView result = service.use(ticketId, technicianId, partId, BigDecimal.ONE,
            "telegram-command:part");

        assertThat(result.id()).isEqualTo(usageId);
        verify(inventory, never()).useTelegram(any(), any(), any());
    }
}
