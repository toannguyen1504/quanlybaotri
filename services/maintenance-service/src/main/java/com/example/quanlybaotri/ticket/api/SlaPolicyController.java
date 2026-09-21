package com.example.quanlybaotri.ticket.api;

import com.example.quanlybaotri.shared.api.ApiException;
import com.example.quanlybaotri.ticket.domain.SlaPolicy;
import com.example.quanlybaotri.ticket.domain.TicketPriority;
import com.example.quanlybaotri.ticket.persistence.SlaPolicyRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import java.util.List;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/sla-policies")
public class SlaPolicyController {

    private final SlaPolicyRepository repo;

    public SlaPolicyController(SlaPolicyRepository repo) {
        this.repo = repo;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public List<View> list() {
        return repo.findAll().stream().map(View::from).toList();
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public View update(@PathVariable UUID id, @Valid @RequestBody Request request) {
        SlaPolicy policy = repo
            .findById(id)
            .orElseThrow(() -> ApiException.notFound("Không tìm thấy chính sách SLA"));
        policy.update(request.responseMinutes(), request.resolutionMinutes(), request.active());
        return View.from(policy);
    }

    public record Request(
        @Min(1) int responseMinutes,
        @Min(1) int resolutionMinutes,
        boolean active
    ) {}

    public record View(
        UUID id,
        TicketPriority priority,
        int responseMinutes,
        int resolutionMinutes,
        boolean active,
        long version
    ) {
        static View from(SlaPolicy p) {
            return new View(
                p.getId(),
                p.getPriority(),
                p.getResponseMinutes(),
                p.getResolutionMinutes(),
                p.isActive(),
                p.getVersion()
            );
        }
    }
}
