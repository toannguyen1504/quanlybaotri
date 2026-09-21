package com.example.quanlybaotri.equipment.api;

import com.example.quanlybaotri.equipment.domain.EquipmentCategory;
import com.example.quanlybaotri.equipment.persistence.EquipmentCategoryRepository;
import com.example.quanlybaotri.equipment.persistence.EquipmentRepository;
import com.example.quanlybaotri.shared.api.ApiException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/equipment-categories")
public class EquipmentCategoryController {

    private final EquipmentCategoryRepository repo;
    private final EquipmentRepository equipment;

    public EquipmentCategoryController(
        EquipmentCategoryRepository repo,
        EquipmentRepository equipment
    ) {
        this.repo = repo;
        this.equipment = equipment;
    }

    @GetMapping
    public List<View> list() {
        return repo.findAll().stream().map(this::view).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    @Transactional
    public View create(@Valid @RequestBody Request r) {
        if (repo.existsByCodeIgnoreCase(r.code())) throw ApiException.conflict(
            "Mã loại thiết bị đã tồn tại"
        );
        return view(
            repo.save(
                new EquipmentCategory(
                    r.code().trim().toUpperCase(),
                    r.name().trim(),
                    r.description()
                )
            )
        );
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    @Transactional
    public View update(@PathVariable UUID id, @Valid @RequestBody Request r) {
        EquipmentCategory c = repo
            .findById(id)
            .orElseThrow(() -> ApiException.notFound("Không tìm thấy loại thiết bị"));
        if (repo.existsByCodeIgnoreCaseAndIdNot(r.code(), id)) throw ApiException.conflict(
            "Mã loại thiết bị đã tồn tại"
        );
        c.update(r.code().trim().toUpperCase(), r.name().trim(), r.description(), r.active());
        return view(c);
    }

    private View view(EquipmentCategory category) {
        return View.from(category, equipment.countByCategoryId(category.getId()));
    }

    public record Request(
        @NotBlank @Size(max = 50) String code,
        @NotBlank @Size(max = 150) String name,
        @Size(max = 500) String description,
        boolean active
    ) {}

    public record View(
        UUID id,
        String code,
        String name,
        String description,
        boolean active,
        long equipmentCount
    ) {
        static View from(EquipmentCategory c, long equipmentCount) {
            return new View(
                c.getId(),
                c.getCode(),
                c.getName(),
                c.getDescription(),
                c.isActive(),
                equipmentCount
            );
        }
    }
}
