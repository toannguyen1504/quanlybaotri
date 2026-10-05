package com.example.quanlybaotri.equipment.api;

import com.example.quanlybaotri.equipment.domain.*;
import com.example.quanlybaotri.equipment.persistence.*;
import com.example.quanlybaotri.organization.client.OrganizationClient;
import com.example.quanlybaotri.shared.api.ApiException;
import com.example.quanlybaotri.shared.security.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/equipment")
public class EquipmentController {
    private final EquipmentRepository repo;
    private final EquipmentCategoryRepository categories;
    private final OrganizationClient organizations;
    private final TransactionTemplate transactions;
    private final CurrentUser current;

    public EquipmentController(EquipmentRepository repo,EquipmentCategoryRepository categories,
        OrganizationClient organizations,TransactionTemplate transactions,CurrentUser current){
        this.repo=repo;this.categories=categories;this.organizations=organizations;this.transactions=transactions;this.current=current;
    }

    @GetMapping
    public Page<View> list(@RequestParam(required=false) String q,@RequestParam(required=false) EquipmentStatus status,
        @RequestParam(required=false) UUID customerId,@RequestParam(required=false) UUID siteId,
        @RequestParam(required=false) WarrantyStatus warrantyStatus,Pageable pageable){
        var actor=current.require();
        String query=blankNull(q);Set<UUID> allowedSites=allowedSites(actor,customerId);
        if(siteId!=null&&allowedSites!=null&&!allowedSites.contains(siteId))return Page.empty(pageable);
        Instant now=Instant.now();
        Specification<Equipment> filters=(root,ignored,cb)->{
            List<jakarta.persistence.criteria.Predicate> p=new ArrayList<>();
            if(query!=null)p.add(cb.or(cb.like(cb.lower(root.get("assetCode")),"%"+query.toLowerCase(Locale.ROOT)+"%"),cb.like(cb.lower(root.get("name")),"%"+query.toLowerCase(Locale.ROOT)+"%")));
            if(status!=null)p.add(cb.equal(root.get("status"),status));
            if(siteId!=null)p.add(cb.equal(root.get("siteId"),siteId));else if(allowedSites!=null)p.add(allowedSites.isEmpty()?cb.disjunction():root.get("siteId").in(allowedSites));
            if(warrantyStatus==WarrantyStatus.IN_WARRANTY)p.add(cb.and(cb.lessThanOrEqualTo(root.get("warrantyStartAt"),now),cb.greaterThanOrEqualTo(root.get("warrantyEndAt"),now)));
            if(warrantyStatus==WarrantyStatus.OUT_OF_WARRANTY)p.add(cb.lessThan(root.get("warrantyEndAt"),now));
            if(warrantyStatus==WarrantyStatus.UNKNOWN)p.add(cb.or(cb.isNull(root.get("warrantyStartAt")),cb.isNull(root.get("warrantyEndAt")),cb.greaterThan(root.get("warrantyStartAt"),now)));
            return cb.and(p.toArray(jakarta.persistence.criteria.Predicate[]::new));
        };
        Page<Equipment> result=transactions.execute(tx->repo.findAll(filters,pageable));
        var sites=organizations.resolve(result.getContent().stream().map(Equipment::getSiteId).toList());
        return result.map(e->View.from(e,sites.get(e.getSiteId()),now));
    }

    @GetMapping("/{id}")
    public View get(@PathVariable UUID id){
        Equipment e=transactions.execute(tx->find(id));var site=organizations.find(e.getSiteId());authorize(site,current.require());return View.from(e,site,Instant.now());
    }

    @PostMapping @ResponseStatus(HttpStatus.CREATED) @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public View create(@Valid @RequestBody Request r){
        var site=site(r.siteId());validateDates(r);
        Equipment e=transactions.execute(tx->{if(repo.existsByAssetCodeIgnoreCase(r.assetCode()))throw ApiException.conflict("Mã thiết bị đã tồn tại");Equipment created=new Equipment(r.assetCode().trim().toUpperCase(),r.name().trim(),category(r.categoryId()));apply(created,r);return repo.save(created);});
        return View.from(e,site,Instant.now());
    }

    @PutMapping("/{id}") @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public View update(@PathVariable UUID id,@Valid @RequestBody Request r){
        var site=site(r.siteId());validateDates(r);
        Equipment e=transactions.execute(tx->{Equipment found=find(id);if(repo.existsByAssetCodeIgnoreCaseAndIdNot(r.assetCode(),id))throw ApiException.conflict("Mã thiết bị đã tồn tại");apply(found,r);return repo.save(found);});
        return View.from(e,site,Instant.now());
    }

    private void apply(Equipment e,Request r){e.update(r.assetCode().trim().toUpperCase(),r.name().trim(),r.serialNumber(),r.manufacturer(),r.model(),r.location(),category(r.categoryId()),r.siteId(),r.installedAt(),r.acceptedAt(),r.warrantyStartAt(),r.warrantyEndAt(),r.status(),r.active());}
    private void validateDates(Request r){
        if((r.warrantyStartAt()==null)!=(r.warrantyEndAt()==null))throw bad("WARRANTY_PERIOD_REQUIRED","Phải nhập đủ ngày bắt đầu và kết thúc bảo hành");
        if(r.acceptedAt()!=null&&r.installedAt()!=null&&r.acceptedAt().isBefore(r.installedAt()))throw bad("INVALID_ACCEPTANCE_DATE","Ngày nghiệm thu không được trước ngày lắp đặt");
        if(r.warrantyStartAt()!=null&&r.warrantyEndAt().isBefore(r.warrantyStartAt()))throw bad("INVALID_WARRANTY_PERIOD","Ngày kết thúc bảo hành không được trước ngày bắt đầu");
    }
    private OrganizationClient.DepartmentRef site(UUID id){var s=organizations.find(id);if(!s.active()||!s.customerActive())throw ApiException.conflict("Khách hàng hoặc địa điểm đã ngừng hoạt động");return s;}
    private Set<UUID> allowedSites(CurrentUser.Actor actor,UUID requestedCustomer){
        if(actor.customer()){
            if(requestedCustomer!=null&&!requestedCustomer.equals(actor.customerId()))return Set.of();
            return "ALL_SITES".equals(actor.siteScope())?organizations.byCustomer(actor.customerId()).stream().map(OrganizationClient.DepartmentRef::id).collect(java.util.stream.Collectors.toSet()):actor.siteIds();
        }
        return requestedCustomer==null?null:organizations.byCustomer(requestedCustomer).stream().map(OrganizationClient.DepartmentRef::id).collect(java.util.stream.Collectors.toSet());
    }
    private void authorize(OrganizationClient.DepartmentRef site,CurrentUser.Actor actor){if(!actor.canAccess(site.customerId(),site.id()))throw ApiException.forbidden("Không có quyền xem thiết bị này");}
    private Equipment find(UUID id){return repo.findById(id).orElseThrow(()->ApiException.notFound("Không tìm thấy thiết bị"));}
    private EquipmentCategory category(UUID id){return categories.findById(id).orElseThrow(()->ApiException.notFound("Không tìm thấy loại thiết bị"));}
    private String blankNull(String s){return s==null||s.isBlank()?null:s.trim();}
    private ApiException bad(String code,String message){return new ApiException(HttpStatus.BAD_REQUEST,code,message);}

    public record Request(@NotBlank @Size(max=80) String assetCode,@NotBlank @Size(max=200) String name,
        @Size(max=150) String serialNumber,@Size(max=150) String manufacturer,@Size(max=150) String model,
        @Size(max=255) String location,@NotNull UUID categoryId,@NotNull UUID siteId,
        Instant installedAt,Instant acceptedAt,Instant warrantyStartAt,Instant warrantyEndAt,
        @NotNull EquipmentStatus status,boolean active){}

    public record View(UUID id,String assetCode,String name,String serialNumber,String manufacturer,String model,String location,
        UUID categoryId,String categoryName,UUID siteId,String siteName,UUID customerId,String customerName,
        Instant installedAt,Instant acceptedAt,Instant warrantyStartAt,Instant warrantyEndAt,WarrantyStatus warrantyStatus,
        EquipmentStatus status,boolean active,long version){
        static View from(Equipment e,OrganizationClient.DepartmentRef site,Instant now){return new View(e.getId(),e.getAssetCode(),e.getName(),e.getSerialNumber(),e.getManufacturer(),e.getModel(),e.getLocation(),e.getCategory().getId(),e.getCategory().getName(),e.getSiteId(),site==null?null:site.name(),site==null?null:site.customerId(),site==null?null:site.customerName(),e.getInstalledAt(),e.getAcceptedAt(),e.getWarrantyStartAt(),e.getWarrantyEndAt(),e.warrantyStatus(now),e.getStatus(),e.isActive(),e.getVersion());}
    }
}
