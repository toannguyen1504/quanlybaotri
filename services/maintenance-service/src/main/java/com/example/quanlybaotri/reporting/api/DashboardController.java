package com.example.quanlybaotri.reporting.api;

import com.example.quanlybaotri.reporting.application.DashboardService;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/reports")
public class DashboardController {

    private final DashboardService service;

    public DashboardController(DashboardService service) {
        this.service = service;
    }

    @GetMapping("/dashboard")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public DashboardService.Dashboard dashboard(
        @RequestParam(required = false) Instant from,
        @RequestParam(required = false) Instant to
    ) {
        Instant end = to == null ? Instant.now() : to;
        Instant start = from == null ? end.minus(30, ChronoUnit.DAYS) : from;
        return service.get(start, end);
    }
}
