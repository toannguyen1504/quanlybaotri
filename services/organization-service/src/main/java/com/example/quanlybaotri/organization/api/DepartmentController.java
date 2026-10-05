package com.example.quanlybaotri.organization.api;

import com.example.quanlybaotri.organization.domain.Department;
import com.example.quanlybaotri.organization.persistence.DepartmentRepository;
import com.example.quanlybaotri.organization.persistence.BranchRepository;
import com.example.quanlybaotri.organization.persistence.RegionRepository;
import com.example.quanlybaotri.shared.api.ApiException;
import com.example.quanlybaotri.shared.security.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/customer-sites")
public class DepartmentController {

    private final DepartmentRepository repo;
    private final BranchRepository branches;
    private final CurrentUser current;
    private final RegionRepository regions;

    public DepartmentController(DepartmentRepository repo, BranchRepository branches, CurrentUser current, RegionRepository regions) {
        this.repo = repo;
        this.branches = branches;
        this.current=current;
        this.regions=regions;
    }

    @GetMapping
    public List<View> list(@RequestParam(required = false) UUID customerId) {
        var actor=current.require();
        if(!actor.customer())return (customerId == null ? repo.findAll() : repo.findByBranchId(customerId)).stream().map(View::from).toList();
        if(actor.customerId()==null||(customerId!=null&&!customerId.equals(actor.customerId())))return List.of();
        List<Department> sites="ALL_SITES".equals(actor.siteScope())?repo.findByBranchId(actor.customerId()):repo.findByIdIn(actor.siteIds());
        return sites.stream().filter(site->site.getBranch().getId().equals(actor.customerId())).map(View::from).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    @Transactional
    public View create(@Valid @RequestBody Request r) {
        if (repo.existsByCodeIgnoreCase(r.code())) throw ApiException.conflict(
            "Mã địa điểm đã tồn tại"
        );
        Department d = new Department(customer(r.customerId()), normalizeCode(r.code()), r.name().trim());
        apply(d, r);
        return View.from(repo.save(d));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    @Transactional
    public View update(@PathVariable UUID id, @Valid @RequestBody Request r) {
        Department d = find(id);
        if (repo.existsByCodeIgnoreCaseAndIdNot(r.code(), id)) throw ApiException.conflict(
            "Mã địa điểm đã tồn tại"
        );
        apply(d, r);
        return View.from(d);
    }

    private Department find(UUID id) {
        return repo
            .findById(id)
            .orElseThrow(() -> ApiException.notFound("Không tìm thấy địa điểm"));
    }

    private void apply(Department d, Request r) {
        d.branch(customer(r.customerId()));
        d.region(region(r.regionId()));
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

    private com.example.quanlybaotri.organization.domain.Region region(UUID id) {
        var region = regions.findById(id).orElseThrow(() -> ApiException.notFound("Không tìm thấy khu vực"));
        if (!region.isActive()) throw ApiException.conflict("Khu vực đã ngừng hoạt động");
        return region;
    }

    private com.example.quanlybaotri.organization.domain.Branch customer(UUID id) {
        var customer = branches.findById(id).orElseThrow(() -> ApiException.notFound("Không tìm thấy khách hàng"));
        if (!customer.isActive()) throw ApiException.conflict("Khách hàng đã ngừng hoạt động");
        return customer;
    }

    private String normalizeCode(String value) {
        return value.trim().toUpperCase(Locale.ROOT);
    }

    public record Request(
        @NotNull UUID customerId,
        @NotNull UUID regionId,
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
        UUID customerId,
        String customerName,
        UUID regionId,
        String regionName,
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
                d.getBranch().getId(),
                d.getBranch().getName(),
                d.getRegion() == null ? null : d.getRegion().getId(),
                d.getRegion() == null ? null : d.getRegion().getName(),
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
