package com.example.quanlybaotri.organization.api;

import com.example.quanlybaotri.organization.domain.Region;
import com.example.quanlybaotri.organization.persistence.RegionRepository;
import com.example.quanlybaotri.shared.api.ApiException;
import java.util.UUID;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/internal/v1/regions")
public class InternalRegionController {
    private final RegionRepository regions;

    public InternalRegionController(RegionRepository regions) { this.regions = regions; }

    @GetMapping("/{id}")
    public RegionRef get(@PathVariable UUID id) {
        return RegionRef.from(regions.findById(id).orElseThrow(() -> ApiException.notFound("Không tìm thấy khu vực")));
    }

    public record RegionRef(UUID id, String code, String name, boolean active) {
        static RegionRef from(Region region) {
            return new RegionRef(region.getId(), region.getCode(), region.getName(), region.isActive());
        }
    }
}
