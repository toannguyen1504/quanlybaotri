package com.example.quanlybaotri.equipment.api;

import com.example.quanlybaotri.equipment.domain.*;
import com.example.quanlybaotri.equipment.persistence.EquipmentRepository;
import com.example.quanlybaotri.organization.client.OrganizationClient;
import com.example.quanlybaotri.shared.api.ApiException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import java.util.*;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/internal/v1/equipment")
public class InternalEquipmentController {

    private final EquipmentRepository repo;
    private final OrganizationClient organizations;

    public InternalEquipmentController(EquipmentRepository repo, OrganizationClient organizations) {
        this.repo = repo;
        this.organizations = organizations;
    }

    @GetMapping("/{id}")
    @Transactional(readOnly = true)
    public EquipmentRef get(@PathVariable UUID id) {
        Equipment equipment = repo.findById(id).orElseThrow(() -> ApiException.notFound("Không tìm thấy thiết bị"));
        return EquipmentRef.from(equipment, organizations.find(equipment.getSiteId()));
    }

    @PostMapping("/resolve")
    @Transactional(readOnly = true)
    public List<EquipmentRef> resolve(@Valid @RequestBody ResolveRequest r) {
        List<Equipment> equipment = repo.findByIdIn(new LinkedHashSet<>(r.ids()));
        var sites = organizations.resolve(equipment.stream().map(Equipment::getSiteId).toList());
        return equipment.stream().map(e -> EquipmentRef.from(e, sites.get(e.getSiteId()))).toList();
    }

    @GetMapping("/ids")
    @Transactional(readOnly = true)
    public List<UUID> ids(@RequestParam UUID categoryId) {
        return repo.findIdsByCategoryId(categoryId);
    }

    public record ResolveRequest(@Size(max = 500) List<UUID> ids) {
        public ResolveRequest {
            ids = ids == null ? List.of() : List.copyOf(ids);
        }
    }

    public record EquipmentRef(
        UUID id,
        String assetCode,
        String name,
        UUID categoryId,
        String categoryName,
        UUID siteId,
        String siteName,
        UUID customerId,
        String customerName,
        java.time.Instant warrantyStartAt,
        java.time.Instant warrantyEndAt,
        WarrantyStatus warrantyStatus,
        EquipmentStatus status,
        boolean active
    ) {
        static EquipmentRef from(Equipment e, OrganizationClient.DepartmentRef department) {
            return new EquipmentRef(
                e.getId(),
                e.getAssetCode(),
                e.getName(),
                e.getCategory().getId(),
                e.getCategory().getName(),
                e.getSiteId(),
                department == null ? null : department.name(),
                department == null ? null : department.customerId(),
                department == null ? null : department.customerName(),
                e.getWarrantyStartAt(),
                e.getWarrantyEndAt(),
                e.warrantyStatus(java.time.Instant.now()),
                e.getStatus(),
                e.isActive()
            );
        }
    }
}
