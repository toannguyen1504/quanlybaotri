package com.example.quanlybaotri.cost.api;

import com.example.quanlybaotri.cost.domain.LaborRate;
import com.example.quanlybaotri.cost.persistence.LaborRateRepository;
import com.example.quanlybaotri.cost.application.CostService;
import com.example.quanlybaotri.shared.api.ApiException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/v1/labor-rates") @PreAuthorize("hasRole('ADMIN')")
public class LaborRateController {
    private final LaborRateRepository rates;
    private final CostService costs;
    public LaborRateController(LaborRateRepository rates,CostService costs){this.rates=rates;this.costs=costs;}
    @GetMapping public List<View> list(){return rates.findAll().stream().map(View::from).toList();}
    @PostMapping @ResponseStatus(HttpStatus.CREATED) @Transactional public View create(@Valid @RequestBody Request r){validatePeriod(null,r);LaborRate saved=rates.save(new LaborRate(r.technicianId(),r.hourlyRate(),r.effectiveFrom(),r.effectiveTo()));rates.flush();costs.repriceMissingLabor();return View.from(saved);}
    @PutMapping("/{id}") @Transactional public View update(@PathVariable UUID id,@Valid @RequestBody Request r){LaborRate rate=rates.findById(id).orElseThrow(()->ApiException.notFound("Không tìm thấy đơn giá"));validatePeriod(id,r);rate.update(r.technicianId(),r.hourlyRate(),r.effectiveFrom(),r.effectiveTo(),r.active());rates.flush();costs.repriceMissingLabor();return View.from(rate);}
    private void validatePeriod(UUID id,Request r){if(r.effectiveTo()!=null&&!r.effectiveTo().isAfter(r.effectiveFrom()))throw ApiException.conflict("Ngày kết thúc phải sau ngày bắt đầu");for(LaborRate existing:rates.findAll()){if(id!=null&&existing.getId().equals(id))continue;if(!existing.isActive()||!Objects.equals(existing.getTechnicianId(),r.technicianId()))continue;Instant aEnd=existing.getEffectiveTo()==null?Instant.MAX:existing.getEffectiveTo();Instant bEnd=r.effectiveTo()==null?Instant.MAX:r.effectiveTo();if(existing.getEffectiveFrom().isBefore(bEnd)&&r.effectiveFrom().isBefore(aEnd))throw ApiException.conflict("Khoảng hiệu lực đơn giá bị chồng lấn");}}
    public record Request(UUID technicianId,@NotNull @DecimalMin("0.01") BigDecimal hourlyRate,@NotNull Instant effectiveFrom,Instant effectiveTo,boolean active){}
    public record View(UUID id,UUID technicianId,BigDecimal hourlyRate,Instant effectiveFrom,Instant effectiveTo,boolean active){static View from(LaborRate r){return new View(r.getId(),r.getTechnicianId(),r.getHourlyRate(),r.getEffectiveFrom(),r.getEffectiveTo(),r.isActive());}}
}
