package com.example.quanlybaotri.equipment.api;

import com.example.quanlybaotri.equipment.domain.*;
import com.example.quanlybaotri.equipment.persistence.*;
import com.example.quanlybaotri.organization.domain.Department;
import com.example.quanlybaotri.organization.persistence.DepartmentRepository;
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
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/equipment")
public class EquipmentController {
    private final EquipmentRepository repo;
    private final EquipmentCategoryRepository categories;
    private final DepartmentRepository departments;

    public EquipmentController(EquipmentRepository repo, EquipmentCategoryRepository categories,
            DepartmentRepository departments) {
        this.repo = repo;
        this.categories = categories;
        this.departments = departments;
    }

    @GetMapping
    @Transactional(readOnly = true)
    public Page<View> list(@RequestParam(required = false) String q,
            @RequestParam(required = false) EquipmentStatus status, Pageable pageable) {
        String query = blankNull(q);
        Page<Equipment> result;
        if (query == null) {
            result = status == null ? repo.findAll(pageable) : repo.findByStatus(status, pageable);
        } else {
            result = repo.search(query, status, pageable);
        }
        return result.map(View::from);
    }

    @GetMapping("/{id}")
    @Transactional(readOnly = true)
    public View get(@PathVariable UUID id) {
        return View.from(find(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    @Transactional
    public View create(@Valid @RequestBody Request r) {
        if (repo.existsByAssetCodeIgnoreCase(r.assetCode()))
            throw ApiException.conflict("Mã thiết bị đã tồn tại");
        Equipment e = new Equipment(r.assetCode().trim().toUpperCase(), r.name().trim(), category(r.categoryId()));
        apply(e, r);
        return View.from(repo.save(e));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    @Transactional
    public View update(@PathVariable UUID id, @Valid @RequestBody Request r) {
        Equipment e = find(id);
        apply(e, r);
        return View.from(e);
    }

    private void apply(Equipment e, Request r) {
        Department d = r.departmentId() == null ? null
                : departments.findById(r.departmentId())
                        .orElseThrow(() -> ApiException.notFound("Không tìm thấy phòng ban"));
        e.update(r.assetCode().trim().toUpperCase(), r.name().trim(), r.serialNumber(), r.manufacturer(), r.model(),
                r.location(), category(r.categoryId()), d, r.status(), r.active());
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
        static View from(Equipment e) {
            return new View(e.getId(), e.getAssetCode(), e.getName(), e.getSerialNumber(), e.getManufacturer(),
                    e.getModel(), e.getLocation(), e.getCategory().getId(), e.getCategory().getName(),
                    e.getDepartment() == null ? null : e.getDepartment().getId(),
                    e.getDepartment() == null ? null : e.getDepartment().getName(), e.getStatus(), e.isActive(),
                    e.getVersion());
        }
    }
}
