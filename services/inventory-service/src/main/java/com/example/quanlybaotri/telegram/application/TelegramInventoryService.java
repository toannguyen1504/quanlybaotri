package com.example.quanlybaotri.telegram.application;

import com.example.quanlybaotri.inventory.api.InventoryController.*;
import com.example.quanlybaotri.inventory.application.InventoryService;
import com.example.quanlybaotri.shared.api.ApiException;
import com.example.quanlybaotri.telegram.domain.TelegramCommandReceipt;
import com.example.quanlybaotri.telegram.persistence.TelegramCommandReceiptRepository;
import java.math.BigDecimal;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TelegramInventoryService {
    private final InventoryService inventory;
    private final TelegramCommandReceiptRepository receipts;

    public TelegramInventoryService(
        InventoryService inventory,
        TelegramCommandReceiptRepository receipts
    ) {
        this.inventory = inventory;
        this.receipts = receipts;
    }

    public Page<PartView> search(String query, int page, int size) {
        return inventory.searchTelegram(
            query,
            PageRequest.of(Math.max(0, page), Math.min(10, Math.max(1, size)))
        );
    }

    public List<TicketPartView> used(UUID ticketId, UUID technicianId) {
        return inventory.ticketPartsTelegram(ticketId, technicianId);
    }

    @Transactional
    public TicketPartView use(
        UUID ticketId,
        UUID technicianId,
        UUID partId,
        BigDecimal quantity,
        String key
    ) {
        TelegramCommandReceipt existing = receipt(key, "USE_PART");
        if (existing != null) return inventory.ticketPartsTelegram(ticketId, technicianId).stream()
            .filter(item -> Objects.equals(item.id(), existing.getResultId()))
            .findFirst()
            .orElseThrow(() -> ApiException.conflict("Lần xuất kho Telegram đã được xử lý"));
        TicketPartView result = inventory.useTelegram(
            ticketId,
            new UsePartRequest(partId, quantity, null),
            technicianId
        );
        receipts.getReferenceById(key).complete(result.id());
        return result;
    }

    private TelegramCommandReceipt receipt(String key, String operation) {
        if (key == null || key.isBlank() || key.length() > 128) throw ApiException.conflict(
            "Idempotency-Key không hợp lệ"
        );
        TelegramCommandReceipt existing = receipts.findById(key).orElse(null);
        if (existing != null) {
            if (!operation.equals(existing.getOperation())) throw ApiException.conflict(
                "Idempotency-Key đã được dùng cho thao tác khác"
            );
            return existing;
        }
        receipts.saveAndFlush(new TelegramCommandReceipt(key, operation));
        return null;
    }
}
