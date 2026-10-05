package com.example.quanlybaotri.identity.application;

import com.example.quanlybaotri.identity.api.UserController.UserRequest;
import com.example.quanlybaotri.identity.api.UserController.UserView;
import com.example.quanlybaotri.identity.domain.*;
import com.example.quanlybaotri.identity.persistence.*;
import com.example.quanlybaotri.organization.client.OrganizationClient;
import com.example.quanlybaotri.shared.api.ApiException;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class UserService {
    private final UserRepository users;
    private final RoleRepository roles;
    private final OrganizationClient organizations;
    private final PasswordEncoder encoder;
    private final TransactionTemplate transactions;

    public UserService(UserRepository users, RoleRepository roles, OrganizationClient organizations,
        PasswordEncoder encoder, TransactionTemplate transactions) {
        this.users=users; this.roles=roles; this.organizations=organizations;
        this.encoder=encoder; this.transactions=transactions;
    }

    public Page<UserView> list(UserAccount actor, UUID customerId, UUID siteId,
        AccountType accountType, Pageable pageable) {
        boolean manager=!isAdmin(actor);
        org.springframework.data.jpa.domain.Specification<UserAccount> filters=(root,query,cb)->{
            List<jakarta.persistence.criteria.Predicate> p=new ArrayList<>();
            if(manager) p.add(cb.equal(root.get("accountType"),AccountType.CUSTOMER));
            else if(accountType!=null) p.add(cb.equal(root.get("accountType"),accountType));
            if(customerId!=null) p.add(cb.equal(root.get("customerId"),customerId));
            if(siteId!=null) {
                var sites=root.joinSet("siteIds",jakarta.persistence.criteria.JoinType.LEFT);
                p.add(cb.or(cb.equal(root.get("siteScope"),SiteScope.ALL_SITES),cb.equal(sites,siteId)));
                query.distinct(true);
            }
            return cb.and(p.toArray(jakarta.persistence.criteria.Predicate[]::new));
        };
        return users.findAll(filters,pageable).map(UserView::from);
    }

    public UserView create(UserAccount actor, UserRequest request) {
        authorizeManagedAccount(actor,request.accountType());
        if(request.password()==null||request.password().length()<8) throw bad("PASSWORD_TOO_SHORT","Mật khẩu phải có ít nhất 8 ký tự");
        Access access=validateAccess(request);
        Set<RoleName> requested=requestedRoles(request);
        RegionAccess regionAccess=validateRegionAccess(access.type(),requested,request,false,Set.of());
        UserAccount user=transactions.execute(status->{
            if(users.existsByUsernameIgnoreCase(request.username())) throw ApiException.conflict("Tên đăng nhập đã tồn tại");
            if(users.existsByEmailIgnoreCase(request.email())) throw ApiException.conflict("Email đã tồn tại");
            UserAccount created=new UserAccount(request.username(),request.email(),encoder.encode(request.password()),request.fullName());
            apply(created,request,access,requested,regionAccess); return users.save(created);
        });
        return UserView.from(user);
    }

    public UserView update(UserAccount actor, UUID id, UserRequest request) {
        UserAccount target=users.findWithRolesById(id).orElseThrow(()->ApiException.notFound("Không tìm thấy người dùng"));
        if(!isAdmin(actor)&&target.getAccountType()!=AccountType.CUSTOMER) throw ApiException.forbidden("Quản lý chỉ được cập nhật tài khoản khách hàng");
        authorizeManagedAccount(actor,request.accountType());
        Access access=validateAccess(request);
        Set<RoleName> requested=requestedRoles(request);
        boolean keepLegacyUnassigned=target.getRegionScope()==RegionScope.UNASSIGNED
            && (request.regionScope()==null||request.regionScope()==RegionScope.UNASSIGNED);
        RegionAccess regionAccess=validateRegionAccess(access.type(),requested,request,keepLegacyUnassigned,target.getRegionIds());
        UserAccount user=transactions.execute(status->{
            UserAccount found=users.findWithRolesById(id).orElseThrow(()->ApiException.notFound("Không tìm thấy người dùng"));
            if(users.existsByEmailIgnoreCaseAndIdNot(request.email(),id)) throw ApiException.conflict("Email đã tồn tại");
            apply(found,request,access,requested,regionAccess); found.setEnabled(request.enabled()); return found;
        });
        return UserView.from(user);
    }

    @Transactional
    public void resetPassword(UserAccount actor, UUID id, String password) {
        UserAccount target=users.findById(id).orElseThrow(()->ApiException.notFound("Không tìm thấy người dùng"));
        if(!isAdmin(actor)&&target.getAccountType()!=AccountType.CUSTOMER) throw ApiException.forbidden("Quản lý chỉ được đặt lại mật khẩu khách hàng");
        target.changePassword(encoder.encode(password),true);
    }

    @Transactional
    public void changePassword(UUID userId,String currentPassword,String newPassword) {
        UserAccount user=users.findById(userId).orElseThrow(()->ApiException.notFound("Không tìm thấy người dùng"));
        if(!encoder.matches(currentPassword,user.getPassword())) throw bad("INVALID_PASSWORD","Mật khẩu hiện tại không đúng");
        user.changePassword(encoder.encode(newPassword),false);
    }

    @Transactional
    public UserAccount updateCurrentProfile(UUID userId,String email,String fullName,String phone) {
        UserAccount user=users.findWithRolesById(userId).orElseThrow(()->ApiException.notFound("Không tìm thấy người dùng"));
        if(users.existsByEmailIgnoreCaseAndIdNot(email,userId)) throw ApiException.conflict("Email đã tồn tại");
        user.updateProfile(email,fullName,phone); return user;
    }

    private Access validateAccess(UserRequest request) {
        AccountType type=request.accountType()==null?AccountType.CUSTOMER:request.accountType();
        SiteScope scope=request.siteScope()==null?SiteScope.SELECTED_SITES:request.siteScope();
        Set<UUID> siteIds=request.siteIds()==null?Set.of():Set.copyOf(request.siteIds());
        if(type==AccountType.INTERNAL) {
            if(request.customerId()!=null||!siteIds.isEmpty()) throw bad("INVALID_INTERNAL_SCOPE","Tài khoản nội bộ không được gắn khách hàng hoặc địa điểm");
            return new Access(type,null,SiteScope.ALL_SITES,Set.of());
        }
        if(request.customerId()==null) throw bad("CUSTOMER_REQUIRED","Tài khoản khách hàng phải thuộc một khách hàng");
        var customer=organizations.findCustomer(request.customerId());
        if(!customer.active()) throw ApiException.conflict("Khách hàng đã ngừng hoạt động");
        if(scope==SiteScope.SELECTED_SITES&&siteIds.isEmpty()) throw bad("SITE_REQUIRED","Phải chọn ít nhất một địa điểm");
        if(scope==SiteScope.ALL_SITES) siteIds=Set.of();
        for(UUID siteId:siteIds) {
            var site=organizations.find(siteId);
            if(!site.active()||!site.customerActive()) throw ApiException.conflict("Khách hàng hoặc địa điểm đã ngừng hoạt động");
            if(!request.customerId().equals(site.customerId())) throw bad("SITE_CUSTOMER_MISMATCH","Địa điểm không thuộc khách hàng đã chọn");
        }
        return new Access(type,request.customerId(),scope,siteIds);
    }

    private Set<RoleName> requestedRoles(UserRequest request) {
        return request.roles()==null||request.roles().isEmpty()?Set.of(RoleName.REQUESTER):Set.copyOf(request.roles());
    }

    private RegionAccess validateRegionAccess(AccountType type,Set<RoleName> requested,UserRequest request,
        boolean allowLegacyUnassigned,Set<UUID> existingRegionIds) {
        if(type!=AccountType.INTERNAL||!requested.contains(RoleName.TECHNICIAN))
            return new RegionAccess(RegionScope.UNASSIGNED,Set.of());
        RegionScope scope=request.regionScope()==null?RegionScope.UNASSIGNED:request.regionScope();
        Set<UUID> regionIds=request.regionIds()==null?Set.of():Set.copyOf(request.regionIds());
        if(scope==RegionScope.UNASSIGNED) {
            if(allowLegacyUnassigned)return new RegionAccess(scope,Set.of());
            throw bad("TECHNICIAN_REGION_REQUIRED","Phải cấu hình khu vực phụ trách cho kỹ thuật viên");
        }
        if(scope==RegionScope.ALL_REGIONS)return new RegionAccess(scope,Set.of());
        if(regionIds.isEmpty())throw bad("REGION_REQUIRED","Phải chọn ít nhất một khu vực phụ trách");
        for(UUID regionId:regionIds){
            var region=organizations.findRegion(regionId);
            if(!region.active()&&!existingRegionIds.contains(regionId))throw ApiException.conflict("Khu vực đã ngừng hoạt động");
        }
        return new RegionAccess(scope,regionIds);
    }

    private void apply(UserAccount user,UserRequest request,Access access,Set<RoleName> requested,RegionAccess regionAccess) {
        if(access.type()==AccountType.CUSTOMER&&!requested.equals(Set.of(RoleName.REQUESTER))) throw bad("INVALID_CUSTOMER_ROLE","Tài khoản khách hàng chỉ được dùng vai trò REQUESTER");
        if(access.type()==AccountType.INTERNAL&&requested.contains(RoleName.REQUESTER)) throw bad("INVALID_INTERNAL_ROLE","Tài khoản nội bộ không dùng vai trò REQUESTER");
        Set<Role> selected=new HashSet<>(roles.findByNameIn(requested));
        if(selected.size()!=requested.size()) throw bad("INVALID_ROLE","Vai trò không hợp lệ");
        user.updateProfile(request.email(),request.fullName(),request.phone());
        user.configureAccess(access.type(),access.customerId(),access.scope(),access.siteIds());
        user.configureRegions(regionAccess.scope(),regionAccess.regionIds());
        user.setRoles(selected);
    }

    private void authorizeManagedAccount(UserAccount actor,AccountType requested) {
        if(isAdmin(actor)) return;
        if(requested!=AccountType.CUSTOMER) throw ApiException.forbidden("Quản lý chỉ được quản lý tài khoản khách hàng");
    }
    private boolean isAdmin(UserAccount actor){return actor.getRoles().stream().anyMatch(r->r.getName()==RoleName.ADMIN);}
    private ApiException bad(String code,String message){return new ApiException(org.springframework.http.HttpStatus.BAD_REQUEST,code,message);}
    private record Access(AccountType type,UUID customerId,SiteScope scope,Set<UUID> siteIds){}
    private record RegionAccess(RegionScope scope,Set<UUID> regionIds){}
}
