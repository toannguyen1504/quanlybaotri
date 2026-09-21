package com.example.quanlybaotri.organization.api;

import com.example.quanlybaotri.organization.domain.Department;
import com.example.quanlybaotri.organization.persistence.DepartmentRepository;
import com.example.quanlybaotri.shared.api.ApiException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/departments")
public class DepartmentController {

    private final DepartmentRepository repo;

    public DepartmentController(DepartmentRepository repo) {
        this.repo = repo;
    }

    @GetMapping
    public List<View> list() {
        return repo.findAll().stream().map(View::from).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    @Transactional
    public View create(@Valid @RequestBody Request r) {
        if (repo.existsByCodeIgnoreCase(r.code())) throw ApiException.conflict(
            "Mã phòng ban đã tồn tại"
        );
        Department d = new Department(normalizeCode(r.code()), r.name().trim());
        apply(d, r);
        return View.from(repo.save(d));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    @Transactional
    public View update(@PathVariable UUID id, @Valid @RequestBody Request r) {
        Department d = find(id);
        if (repo.existsByCodeIgnoreCaseAndIdNot(r.code(), id)) throw ApiException.conflict(
            "Mã phòng ban đã tồn tại"
        );
        apply(d, r);
        return View.from(d);
    }

    private Department find(UUID id) {
        return repo
            .findById(id)
            .orElseThrow(() -> ApiException.notFound("Không tìm thấy phòng ban"));
    }

    private void apply(Department d, Request r) {
        d.update(
            normalizeCode(r.code()),
            r.name().trim(),
            r.description(),
            r.location(),
            r.contactEmail(),
            r.contactPhone(),
            r.active()
        );
    }

    private String normalizeCode(String value) {
        return value.trim().toUpperCase(Locale.ROOT);
    }

    public record Request(
        @NotBlank @Size(max = 50) String code,
        @NotBlank @Size(max = 150) String name,
        @Size(max = 500) String description,
        @Size(max = 255) String location,
        @Email @Size(max = 190) String contactEmail,
        @Size(max = 30) String contactPhone,
        boolean active
    ) {}

    public record View(
        UUID id,
        String code,
        String name,
        String description,
        String location,
        String contactEmail,
        String contactPhone,
        boolean active
    ) {
        static View from(Department d) {
            return new View(
                d.getId(),
                d.getCode(),
                d.getName(),
                d.getDescription(),
                d.getLocation(),
                d.getContactEmail(),
                d.getContactPhone(),
                d.isActive()
            );
        }
    }
}
