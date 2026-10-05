package com.example.quanlybaotri.organization.api;

import com.example.quanlybaotri.organization.domain.Region;
import com.example.quanlybaotri.organization.persistence.RegionRepository;
import com.example.quanlybaotri.shared.api.ApiException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/regions")
public class RegionController {
    private final RegionRepository regions;

    public RegionController(RegionRepository regions) { this.regions = regions; }

    @GetMapping
    public List<View> list() {
        return regions.findAll().stream()
            .sorted(Comparator.comparing(Region::getName, String.CASE_INSENSITIVE_ORDER))
            .map(View::from).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    @Transactional
    public View create(@Valid @RequestBody Request request) {
        String code = code(request.code());
        if (regions.existsByCodeIgnoreCase(code)) throw ApiException.conflict("Mã khu vực đã tồn tại");
        Region region = new Region(code, request.name().trim());
        apply(region, request);
        return View.from(regions.save(region));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    @Transactional
    public View update(@PathVariable UUID id, @Valid @RequestBody Request request) {
        Region region = regions.findById(id).orElseThrow(() -> ApiException.notFound("Không tìm thấy khu vực"));
        String code = code(request.code());
        if (regions.existsByCodeIgnoreCaseAndIdNot(code, id)) throw ApiException.conflict("Mã khu vực đã tồn tại");
        apply(region, request);
        return View.from(region);
    }

    private void apply(Region region, Request request) {
        region.update(code(request.code()), request.name().trim(), request.description(), request.active());
    }

    private String code(String value) { return value.trim().toUpperCase(Locale.ROOT); }

    public record Request(
        @NotBlank @Size(max = 50) String code,
        @NotBlank @Size(max = 150) String name,
        @Size(max = 500) String description,
        boolean active
    ) {}

    public record View(UUID id, String code, String name, String description, boolean active) {
        static View from(Region region) {
            return new View(region.getId(), region.getCode(), region.getName(), region.getDescription(), region.isActive());
        }
    }
}
