package com.example.quanlybaotri.organization.api;

import com.example.quanlybaotri.organization.domain.Department;
import com.example.quanlybaotri.organization.persistence.DepartmentRepository;
import com.example.quanlybaotri.shared.api.ApiException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/departments")
public class DepartmentController {
    private final DepartmentRepository departments;

    public DepartmentController(DepartmentRepository departments) {
        this.departments = departments;
    }

    @GetMapping
    public List<View> list() {
        return departments.findAll().stream().map(View::from).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    @Transactional
    public View create(@Valid @RequestBody Request request) {
        if (departments.existsByCodeIgnoreCase(request.code()))
            throw ApiException.conflict("Mã phòng ban đã tồn tại");
        Department department = new Department(request.code().trim().toUpperCase(), request.name().trim());
        department.update(request.code().trim().toUpperCase(), request.name().trim(), request.description(),
                request.location(), request.contactEmail(), request.contactPhone(), request.active());
        return View.from(departments.save(department));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    @Transactional
    public View update(@PathVariable UUID id, @Valid @RequestBody Request request) {
        Department d = departments.findById(id).orElseThrow(() -> ApiException.notFound("Không tìm thấy phòng ban"));
        if (departments.existsByCodeIgnoreCaseAndIdNot(request.code(), id))
            throw ApiException.conflict("Mã phòng ban đã tồn tại");
        d.update(request.code().trim().toUpperCase(), request.name().trim(), request.description(),
                request.location(), request.contactEmail(), request.contactPhone(), request.active());
        return View.from(d);
    }

    public record Request(@NotBlank @Size(max = 50) String code, @NotBlank @Size(max = 150) String name,
            @Size(max = 500) String description, @Size(max = 255) String location,
            @Email @Size(max = 190) String contactEmail, @Size(max = 30) String contactPhone, boolean active) {
    }

    public record View(UUID id, String code, String name, String description, String location, String contactEmail,
            String contactPhone, boolean active) {
        static View from(Department d) {
            return new View(d.getId(), d.getCode(), d.getName(), d.getDescription(), d.getLocation(),
                    d.getContactEmail(), d.getContactPhone(), d.isActive());
        }
    }
}
