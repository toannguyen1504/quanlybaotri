package com.example.quanlybaotri.equipment.api;

import com.example.quanlybaotri.equipment.domain.*;
import com.example.quanlybaotri.equipment.persistence.*;
import com.example.quanlybaotri.organization.client.OrganizationClient;
import com.example.quanlybaotri.shared.api.ApiException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/equipment")
public class EquipmentController {
    private final EquipmentRepository repo;
    private final EquipmentCategoryRepository categories;
    private final OrganizationClient organizations;
    private final TransactionTemplate transactions;

    public EquipmentController(EquipmentRepository repo, EquipmentCategoryRepository categories,
            OrganizationClient organizations, TransactionTemplate transactions) {
        this.repo = repo;
        this.categories = categories;
        this.organizations = organizations;
        this.transactions = transactions;
    }

    @GetMapping
    public Page<View> list(@RequestParam(required = false) String q,
            @RequestParam(required = false) EquipmentStatus status, Pageable pageable) {
        String query = blankNull(q);
        Page<Equipment> result = transactions.execute(tx -> {
            return query == null ? (status == null ? repo.findAll(pageable) : repo.findByStatus(status, pageable))
                    : repo.search(query, status, pageable);
        });
        var names = organizations.resolve(result.getContent().stream().map(Equipment::getDepartmentId)
                .filter(java.util.Objects::nonNull).toList());
        return result.map(e -> View.from(e, e.getDepartmentId() == null ? null : names.get(e.getDepartmentId())));
    }

    @GetMapping("/{id}")
    public View get(@PathVariable UUID id) {
        Equipment e = transactions.execute(tx -> find(id));
        var department = e.getDepartmentId() == null ? null : organizations.find(e.getDepartmentId());
        return View.from(e, department);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public View create(@Valid @RequestBody Request r) {
        var department = department(r.departmentId());
        Equipment e = transactions.execute(tx -> {
            if (repo.existsByAssetCodeIgnoreCase(r.assetCode())) throw ApiException.conflict("Mã thiết bị đã tồn tại");
            Equipment created = new Equipment(r.assetCode().trim().toUpperCase(), r.name().trim(), category(r.categoryId()));
            apply(created, r, department); return repo.save(created);
        });
        return View.from(e, department);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public View update(@PathVariable UUID id, @Valid @RequestBody Request r) {
        var department = department(r.departmentId());
        Equipment e = transactions.execute(tx -> { Equipment found=find(id); apply(found,r,department); return repo.save(found); });
        return View.from(e, department);
    }

    private void apply(Equipment e, Request r, OrganizationClient.DepartmentRef d) {
        e.update(r.assetCode().trim().toUpperCase(), r.name().trim(), r.serialNumber(), r.manufacturer(), r.model(),
                r.location(), category(r.categoryId()), d == null ? null : d.id(), r.status(), r.active());
    }

    private OrganizationClient.DepartmentRef department(UUID id) {
        if (id == null) return null;
        var d = organizations.find(id);
        if (!d.active()) throw ApiException.conflict("Phòng ban đã ngừng hoạt động");
        return d;
    }

    private Equipment find(UUID id) {
        return repo.findById(id).orElseThrow(() -> ApiException.notFound("Không tìm thấy thiết bị"));
    }

    private EquipmentCategory category(UUID id) {
        return categories.findById(id).orElseThrow(() -> ApiException.notFound("Không tìm thấy loại thiết bị"));
    }

    private String blankNull(String s) {
        return s == null || s.isBlank() ? null : s;
    }

    public record Request(@NotBlank @Size(max = 80) String assetCode, @NotBlank @Size(max = 200) String name,
            @Size(max = 150) String serialNumber, @Size(max = 150) String manufacturer, @Size(max = 150) String model,
            @Size(max = 255) String location, @NotNull UUID categoryId, UUID departmentId,
            @NotNull EquipmentStatus status, boolean active) {
    }

    public record View(UUID id, String assetCode, String name, String serialNumber, String manufacturer, String model,
            String location, UUID categoryId, String categoryName, UUID departmentId, String departmentName,
            EquipmentStatus status, boolean active, long version) {
        static View from(Equipment e, OrganizationClient.DepartmentRef department) {
            return new View(e.getId(), e.getAssetCode(), e.getName(), e.getSerialNumber(), e.getManufacturer(),
                    e.getModel(), e.getLocation(), e.getCategory().getId(), e.getCategory().getName(),
                    e.getDepartmentId(), department == null ? null : department.name(), e.getStatus(), e.isActive(),
                    e.getVersion());
        }
    }
}
