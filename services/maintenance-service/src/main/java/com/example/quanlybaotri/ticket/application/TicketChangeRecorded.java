package com.example.quanlybaotri.ticket.application;

import com.example.quanlybaotri.ticket.domain.TicketStatus;

public record TicketChangeRecorded(
    String action,
    TicketStatus from,
    TicketStatus to
) {}
