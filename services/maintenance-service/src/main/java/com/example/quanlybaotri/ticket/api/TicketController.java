package com.example.quanlybaotri.ticket.api;

import com.example.quanlybaotri.equipment.client.AssetClient;
import com.example.quanlybaotri.identity.client.IdentityClient;
import com.example.quanlybaotri.shared.security.CurrentUser;
import com.example.quanlybaotri.ticket.application.TicketService;
import com.example.quanlybaotri.ticket.domain.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/tickets")
public class TicketController {
    private final TicketService service; private final CurrentUser current;
    public TicketController(TicketService service, CurrentUser current) { this.service = service; this.current = current; }

    @GetMapping public Page<TicketView> list(@RequestParam(required=false) String q,
            @RequestParam(required=false) TicketStatus status, @RequestParam(required=false) TicketPriority priority,
            @RequestParam(defaultValue="ALL") TicketListView view, Pageable pageable) {
        return service.list(current.require(), q, status, priority, view, pageable);
    }
    @GetMapping("/summary") public TicketSummaryView summary() { return service.summary(current.require()); }
    @GetMapping("/{id}") public TicketView get(@PathVariable UUID id) { return service.get(id, current.require()); }

    @PostMapping @ResponseStatus(HttpStatus.CREATED) @PreAuthorize("hasAnyRole('REQUESTER','ADMIN','MANAGER')")
    public TicketView create(@Valid @RequestBody CreateTicketRequest r) { return service.create(r, current.require()); }
    @PostMapping("/{id}/accept") @PreAuthorize("hasAnyRole('MANAGER','ADMIN')")
    public TicketView accept(@PathVariable UUID id) { return service.accept(id, current.require()); }
    @PostMapping("/{id}/reject") @PreAuthorize("hasAnyRole('MANAGER','ADMIN')")
    public TicketView reject(@PathVariable UUID id,@Valid @RequestBody ReasonRequest r){return service.reject(id,r.reason(),current.require());}
    @PostMapping("/{id}/cancel") public TicketView cancel(@PathVariable UUID id,@Valid @RequestBody ReasonRequest r){return service.cancel(id,r.reason(),current.require());}
    @PostMapping("/{id}/assign") @PreAuthorize("hasAnyRole('MANAGER','ADMIN')")
    public TicketView assign(@PathVariable UUID id,@Valid @RequestBody AssignRequest r){return service.assign(id,r.technicianId(),r.reason(),current.require());}
    @PostMapping("/{id}/start") @PreAuthorize("hasRole('TECHNICIAN')") public TicketView start(@PathVariable UUID id){return service.start(id,current.require());}
    @PostMapping("/{id}/wait-parts") @PreAuthorize("hasRole('TECHNICIAN')") public TicketView waitParts(@PathVariable UUID id,@Valid @RequestBody ReasonRequest r){return service.waitParts(id,r.reason(),current.require());}
    @PostMapping("/{id}/resume") @PreAuthorize("hasRole('TECHNICIAN')") public TicketView resume(@PathVariable UUID id){return service.resume(id,current.require());}
    @PostMapping("/{id}/resolve") @PreAuthorize("hasRole('TECHNICIAN')") public TicketView resolve(@PathVariable UUID id,@Valid @RequestBody ReasonRequest r){return service.resolve(id,r.reason(),current.require());}
    @PostMapping("/{id}/reopen") public TicketView reopen(@PathVariable UUID id,@Valid @RequestBody ReasonRequest r){return service.reopen(id,r.reason(),current.require());}
    @PostMapping("/{id}/close") public TicketView close(@PathVariable UUID id,@RequestBody(required=false) ReasonRequest r){return service.close(id,r==null?null:r.reason(),current.require());}
    @PostMapping("/{id}/priority") @PreAuthorize("hasAnyRole('MANAGER','ADMIN')") public TicketView priority(@PathVariable UUID id,@Valid @RequestBody PriorityRequest r){return service.changePriority(id,r.priority(),current.require());}
    @PostMapping("/{id}/charge-type") @PreAuthorize("hasAnyRole('TECHNICIAN','MANAGER','ADMIN')") public TicketView chargeType(@PathVariable UUID id,@Valid @RequestBody ChargeTypeRequest r){return service.changeChargeType(id,r.chargeType(),current.require());}
    @PostMapping("/{id}/work-logs") @ResponseStatus(HttpStatus.CREATED) @PreAuthorize("hasRole('TECHNICIAN')") public WorkLogView work(@PathVariable UUID id,@Valid @RequestBody WorkLogRequest r){return service.addWorkLog(id,r.content(),r.minutesSpent(),current.require());}
    @GetMapping("/{id}/work-logs") public List<WorkLogView> workLogs(@PathVariable UUID id){return service.workLogs(id,current.require());}
    @GetMapping("/{id}/timeline") public List<EventView> timeline(@PathVariable UUID id){return service.timeline(id,current.require());}

    public record CreateTicketRequest(@NotNull UUID equipmentId,@NotBlank @Size(max=200) String title,
            @NotBlank @Size(max=10000) String description,@NotNull TicketPriority priority){}
    public record ReasonRequest(@NotBlank @Size(max=5000) String reason){}
    public record AssignRequest(@NotNull UUID technicianId,@Size(max=500) String reason){}
    public record PriorityRequest(@NotNull TicketPriority priority){}
    public record ChargeTypeRequest(@NotNull TicketChargeType chargeType){}
    public record WorkLogRequest(@NotBlank @Size(max=10000) String content,@Min(0) @Max(1440) int minutesSpent){}
    public record TicketSummaryView(long total,long open,long dueSoon,long overdue){}

    public record TicketView(UUID id,String code,UUID equipmentId,String equipmentCode,String equipmentName,
            UUID requesterId,String requesterName,UUID assigneeId,String assigneeName,String title,String description,
            TicketPriority priority,TicketStatus status,Instant submittedAt,Instant responseDueAt,Instant resolutionDueAt,
            Instant acceptedAt,Instant startedAt,Instant resolvedAt,Instant closedAt,String resolutionSummary,
            TicketChargeType chargeType,BigDecimal partsCost,BigDecimal chargeAmount,long version){
        public static TicketView from(MaintenanceTicket t,AssetClient.EquipmentRef equipment,Map<UUID,IdentityClient.UserRef> users){
            IdentityClient.UserRef requester=users.get(t.getRequesterId()),assignee=t.getAssigneeId()==null?null:users.get(t.getAssigneeId());
            return new TicketView(t.getId(),t.getCode(),t.getEquipmentId(),equipment==null?null:equipment.assetCode(),
                    equipment==null?null:equipment.name(),t.getRequesterId(),requester==null?null:requester.fullName(),
                    t.getAssigneeId(),assignee==null?null:assignee.fullName(),t.getTitle(),t.getDescription(),t.getPriority(),
                    t.getStatus(),t.getSubmittedAt(),t.getResponseDueAt(),t.getResolutionDueAt(),t.getAcceptedAt(),
                    t.getStartedAt(),t.getResolvedAt(),t.getClosedAt(),t.getResolutionSummary(),t.getChargeType(),
                    t.getPartsCost(),t.getChargeAmount(),t.getVersion());
        }
    }
    public record EventView(UUID id,String type,TicketStatus fromStatus,TicketStatus toStatus,String description,
            String metadataJson,UUID actorId,String actorName,Instant createdAt){
        public static EventView from(TicketEvent e,IdentityClient.UserRef actor){return new EventView(e.getId(),e.getEventType(),
                e.getFromStatus(),e.getToStatus(),e.getDescription(),e.getMetadataJson(),e.getActorId(),
                actor==null?"Hệ thống":actor.fullName(),e.getCreatedAt());}
    }
    public record WorkLogView(UUID id,UUID technicianId,String technicianName,String content,int minutesSpent,Instant createdAt){
        public static WorkLogView from(WorkLog w,IdentityClient.UserRef technician){return new WorkLogView(w.getId(),
                w.getTechnicianId(),technician==null?null:technician.fullName(),w.getContent(),w.getMinutesSpent(),w.getCreatedAt());}
    }
}
