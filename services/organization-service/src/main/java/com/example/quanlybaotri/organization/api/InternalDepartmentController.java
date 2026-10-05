package com.example.quanlybaotri.organization.api;

import com.example.quanlybaotri.organization.domain.Department;
import com.example.quanlybaotri.organization.persistence.DepartmentRepository;
import com.example.quanlybaotri.shared.api.ApiException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import java.util.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/internal/v1/customer-sites")
public class InternalDepartmentController {

    private final DepartmentRepository repo;

    public InternalDepartmentController(DepartmentRepository repo) {
        this.repo = repo;
    }

    @GetMapping("/{id}")
    public DepartmentRef get(@PathVariable UUID id) {
        return DepartmentRef.from(
            repo.findById(id).orElseThrow(() -> ApiException.notFound("Không tìm thấy địa điểm"))
        );
    }

    @GetMapping
    public List<DepartmentRef> list(@RequestParam(required = false) UUID customerId) {
        List<Department> departments = customerId == null ? repo.findAll() : repo.findByBranchId(customerId);
        return departments.stream().map(DepartmentRef::from).toList();
    }

    @PostMapping("/resolve")
    public List<DepartmentRef> resolve(@Valid @RequestBody ResolveRequest r) {
        return repo
            .findByIdIn(new LinkedHashSet<>(r.ids()))
            .stream()
            .map(DepartmentRef::from)
            .toList();
    }

    public record ResolveRequest(@Size(max = 500) List<UUID> ids) {
        public ResolveRequest {
            ids = ids == null ? List.of() : List.copyOf(ids);
        }
    }

    public record DepartmentRef(UUID id, String code, String name, UUID customerId, String customerName,
                                UUID regionId, String regionName, boolean customerActive, boolean active) {
        static DepartmentRef from(Department d) {
            return new DepartmentRef(d.getId(), d.getCode(), d.getName(), d.getBranch().getId(),
                d.getBranch().getName(), d.getRegion() == null ? null : d.getRegion().getId(),
                d.getRegion() == null ? null : d.getRegion().getName(), d.getBranch().isActive(), d.isActive());
        }
    }
}
