package com.example.quanlybaotri.organization.api;

import com.example.quanlybaotri.organization.domain.Branch;
import com.example.quanlybaotri.organization.persistence.BranchRepository;
import com.example.quanlybaotri.shared.api.ApiException;
import com.example.quanlybaotri.shared.security.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/customers")
public class BranchController {
    private final BranchRepository branches;
    private final CurrentUser current;

    public BranchController(BranchRepository branches,CurrentUser current) { this.branches = branches;this.current=current; }

    @GetMapping
    public List<View> list() {var actor=current.require();if(!actor.customer())return branches.findAll().stream().map(View::from).toList();return actor.customerId()==null?List.of():branches.findById(actor.customerId()).map(View::from).map(List::of).orElseGet(List::of);}

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    @Transactional
    public View create(@Valid @RequestBody Request request) {
        String code = normalizeCode(request.code());
        if (branches.existsByCodeIgnoreCase(code)) throw ApiException.conflict("Mã khách hàng đã tồn tại");
        Branch branch = new Branch(code, request.name().trim());
        apply(branch, request);
        return View.from(branches.save(branch));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    @Transactional
    public View update(@PathVariable UUID id, @Valid @RequestBody Request request) {
        Branch branch = branches.findById(id).orElseThrow(() -> ApiException.notFound("Không tìm thấy khách hàng"));
        String code = normalizeCode(request.code());
        if (branches.existsByCodeIgnoreCaseAndIdNot(code, id)) throw ApiException.conflict("Mã khách hàng đã tồn tại");
        apply(branch, request);
        return View.from(branch);
    }

    private void apply(Branch branch, Request request) {
        branch.update(normalizeCode(request.code()), request.name().trim(), request.address(), request.contactEmail(), request.contactPhone(), request.active());
    }

    private String normalizeCode(String code) { return code.trim().toUpperCase(Locale.ROOT); }

    public record Request(
        @NotBlank @Size(max = 50) String code,
        @NotBlank @Size(max = 150) String name,
        @Size(max = 500) String address,
        @Email @Size(max = 190) String contactEmail,
        @Size(max = 30) String contactPhone,
        boolean active
    ) {}

    public record View(UUID id, String code, String name, String address, String contactEmail, String contactPhone, boolean active) {
        static View from(Branch b) { return new View(b.getId(), b.getCode(), b.getName(), b.getAddress(), b.getContactEmail(), b.getContactPhone(), b.isActive()); }
    }
}
