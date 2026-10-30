import { Component, inject, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Api } from '../../core/api';
import { AuthService } from '../../core/auth';
import {
  AccountType,
  Customer,
  CustomerSite,
  Page,
  Region,
  RegionScope,
  Role,
  SiteScope,
  User,
} from '../../core/models';
import { PaginationComponent } from '../../shared/pagination';
import { AccessibleDialog } from '../../shared/accessible-dialog';
import { UsersOverview } from './users-overview/users-overview';

type UserStatusFilter = 'ALL' | 'ACTIVE' | 'LOCKED' | 'PENDING' | 'REJECTED';

@Component({
  selector: 'app-users',
  imports: [FormsModule, PaginationComponent, AccessibleDialog, UsersOverview],
  templateUrl: './users.html',
})
export class UsersPage implements OnInit {
  private readonly api = inject(Api);
  private readonly auth = inject(AuthService);

  readonly users = signal<User[]>([]);
  readonly departments = signal<CustomerSite[]>([]);
  readonly branches = signal<Customer[]>([]);
  readonly regions = signal<Region[]>([]);
  readonly showForm = signal(false);
  readonly loading = signal(true);
  readonly saving = signal(false);
  readonly resetting = signal(false);
  readonly updatingUserId = signal<string | null>(null);
  readonly savingRegions = signal(false);
  readonly loadError = signal('');
  readonly formError = signal('');
  readonly resetError = signal('');
  readonly regionError = signal('');
  readonly message = signal('');
  readonly resetTarget = signal<User | null>(null);
  readonly regionTarget = signal<User | null>(null);
  readonly approvalTarget = signal<User | null>(null);
  readonly rejectionTarget = signal<User | null>(null);
  readonly reviewing = signal(false);
  readonly reviewError = signal('');
  readonly skeletonRows = [1, 2, 3, 4, 5];
  readonly allRoles: Role[] = ['REQUESTER', 'TECHNICIAN', 'MANAGER', 'ADMIN'];
  readonly internalRoles: Role[] = ['TECHNICIAN', 'MANAGER', 'ADMIN'];

  query = '';
  roleFilter: Role | '' = '';
  departmentFilter = '';
  branchFilter = '';
  regionFilter = '';
  statusFilter: UserStatusFilter = 'ALL';
  readonly pageSize = 10;
  currentPage = 0;
  newPassword = '';
  editRegionScope: RegionScope = 'SELECTED_REGIONS';
  editRegionIds: string[] = [];
  approval: { customerId: string; siteScope: SiteScope; siteIds: string[] } = {
    customerId: '',
    siteScope: 'SELECTED_SITES',
    siteIds: [],
  };
  rejectionReason = '';
  model = this.empty();

  ngOnInit() {
    this.load();
    this.api.get<CustomerSite[]>('/customer-sites').subscribe({
      next: (departments) => this.departments.set(departments.filter((item) => item.active)),
    });
    this.api
      .get<Customer[]>('/customers')
      .subscribe((items) => this.branches.set(items.filter((item) => item.active)));
    this.api.get<Region[]>('/regions').subscribe((items) => this.regions.set(items));
  }

  load() {
    this.loading.set(true);
    this.loadError.set('');
    this.api.get<Page<User>>('/users', { size: 200, sort: 'fullName,asc' }).subscribe({
      next: (page) => {
        this.users.set(page.content);
        this.loading.set(false);
      },
      error: (error) => {
        this.loadError.set(error?.error?.detail || 'Vui lòng kiểm tra kết nối và thử lại.');
        this.loading.set(false);
      },
    });
  }

  filteredUsers() {
    const keyword = this.query.trim().toLocaleLowerCase('vi');
    return this.users().filter((user) => {
      const matchesKeyword =
        !keyword ||
        [user.fullName, user.username, user.email, user.phone, this.accessLabel(user)].some(
          (value) => value?.toLocaleLowerCase('vi').includes(keyword),
        );
      const matchesRole = !this.roleFilter || user.roles.includes(this.roleFilter);
      const matchesDepartment =
        !this.departmentFilter ||
        user.siteScope === 'ALL_SITES' ||
        user.siteIds.includes(this.departmentFilter);
      const matchesBranch = !this.branchFilter || user.customerId === this.branchFilter;
      const matchesStatus =
        this.statusFilter === 'ALL' ||
        (this.statusFilter === 'PENDING' && user.registrationStatus === 'PENDING') ||
        (this.statusFilter === 'REJECTED' && user.registrationStatus === 'REJECTED') ||
        (this.statusFilter === 'ACTIVE' &&
          user.registrationStatus !== 'PENDING' &&
          user.registrationStatus !== 'REJECTED' &&
          user.enabled) ||
        (this.statusFilter === 'LOCKED' &&
          user.registrationStatus !== 'PENDING' &&
          user.registrationStatus !== 'REJECTED' &&
          !user.enabled);
      const matchesRegion =
        !this.regionFilter ||
        (this.regionFilter === 'ALL_REGIONS'
          ? user.regionScope === 'ALL_REGIONS'
          : this.regionFilter === 'UNASSIGNED'
            ? !user.regionScope || user.regionScope === 'UNASSIGNED'
            : user.regionScope === 'ALL_REGIONS' || user.regionIds?.includes(this.regionFilter));
      return (
        matchesKeyword &&
        matchesRole &&
        matchesBranch &&
        matchesDepartment &&
        matchesStatus &&
        matchesRegion
      );
    });
  }

  filteredDepartments() {
    return this.departments().filter(
      (item) => !this.branchFilter || item.customerId === this.branchFilter,
    );
  }

  formSites() {
    return this.departments().filter((item) => item.customerId === this.model.customerId);
  }

  customers() {
    return this.branches();
  }

  activeRegions() {
    return this.regions().filter((region) => region.active);
  }

  regionsForUser(user: User) {
    const selected = new Set(user.regionIds ?? []);
    return this.regions().filter((region) => region.active || selected.has(region.id));
  }

  isAdmin() {
    return this.auth.hasAny(['ADMIN']);
  }

  accountTypeChanged() {
    if (this.model.accountType === 'CUSTOMER') {
      this.model.roles = ['REQUESTER'];
      this.model.siteScope = 'SELECTED_SITES';
      this.model.regionScope = 'UNASSIGNED';
      this.model.regionIds = [];
    } else {
      this.model.customerId = '';
      this.model.siteIds = [];
      this.model.siteScope = 'ALL_SITES';
      this.model.roles = ['TECHNICIAN'];
      this.model.regionScope = 'SELECTED_REGIONS';
    }
  }

  regionScopeChanged() {
    if (this.model.regionScope !== 'SELECTED_REGIONS') this.model.regionIds = [];
  }

  customerChanged() {
    this.model.siteIds = [];
  }

  accessLabel(user: User) {
    if (user.registrationStatus === 'PENDING') return 'Chờ gán khách hàng và địa điểm';
    if (user.registrationStatus === 'REJECTED') return 'Đăng ký đã bị từ chối';
    if (user.accountType === 'INTERNAL') return 'Nội bộ · toàn hệ thống';
    const customer =
      this.branches().find((item) => item.id === user.customerId)?.name ?? 'Khách hàng';
    if (user.siteScope === 'ALL_SITES') return `${customer} · tất cả địa điểm`;
    const names = user.siteIds
      .map((id) => this.departments().find((site) => site.id === id)?.name)
      .filter(Boolean);
    return `${customer} · ${names.join(', ') || user.siteIds.length + ' địa điểm'}`;
  }

  technicianRegionLabel(user: User) {
    if (user.regionScope === 'ALL_REGIONS') return 'Phụ trách toàn bộ khu vực';
    if (user.regionScope === 'SELECTED_REGIONS') {
      const names = (user.regionIds ?? []).map(
        (id) =>
          this.regions().find((region) => region.id === id)?.name ?? 'Khu vực không còn hoạt động',
      );
      return names.length ? `Khu vực: ${names.join(', ')}` : 'Chưa phân vùng';
    }
    return 'Chưa phân vùng';
  }

  pagedUsers() {
    const items = this.filteredUsers();
    const page = Math.min(
      this.currentPage,
      Math.max(Math.ceil(items.length / this.pageSize) - 1, 0),
    );
    return items.slice(page * this.pageSize, (page + 1) * this.pageSize);
  }

  resetPage() {
    this.currentPage = 0;
  }

  activeCount() {
    return this.users().filter((user) => user.enabled).length;
  }

  lockedCount() {
    return this.users().length - this.activeCount();
  }

  passwordChangeCount() {
    return this.users().filter((user) => user.mustChangePassword).length;
  }

  registrationStatusLabel(user: User) {
    if (user.registrationStatus === 'PENDING') return 'Chờ duyệt';
    if (user.registrationStatus === 'REJECTED') return 'Đã từ chối';
    return user.enabled ? 'Hoạt động' : 'Đã khóa';
  }

  approvalSites() {
    return this.departments().filter((site) => site.customerId === this.approval.customerId);
  }

  hasFilters() {
    return (
      !!this.query.trim() ||
      !!this.roleFilter ||
      !!this.regionFilter ||
      !!this.branchFilter ||
      !!this.departmentFilter ||
      this.statusFilter !== 'ALL'
    );
  }

  clearFilters() {
    this.query = '';
    this.roleFilter = '';
    this.regionFilter = '';
    this.branchFilter = '';
    this.departmentFilter = '';
    this.statusFilter = 'ALL';
    this.resetPage();
  }

  toggleCreateForm() {
    if (this.showForm()) this.closeCreateForm();
    else {
      this.message.set('');
      this.formError.set('');
      this.showForm.set(true);
    }
  }

  closeCreateForm() {
    this.showForm.set(false);
    this.formError.set('');
    this.model = this.empty();
  }

  toggleRole(role: Role) {
    this.model.roles = this.model.roles.includes(role)
      ? this.model.roles.filter((item) => item !== role)
      : [...this.model.roles, role];
    if (role === 'TECHNICIAN') {
      if (this.model.roles.includes('TECHNICIAN')) this.model.regionScope = 'SELECTED_REGIONS';
      else {
        this.model.regionScope = 'UNASSIGNED';
        this.model.regionIds = [];
      }
    }
  }

  save() {
    if (!this.model.roles.length || this.saving()) return;
    this.saving.set(true);
    this.formError.set('');
    this.message.set('');
    this.api
      .post<User>('/users', {
        ...this.model,
        customerId: this.model.accountType === 'CUSTOMER' ? this.model.customerId : null,
        siteScope: this.model.accountType === 'CUSTOMER' ? this.model.siteScope : 'ALL_SITES',
        siteIds:
          this.model.accountType === 'CUSTOMER' && this.model.siteScope === 'SELECTED_SITES'
            ? this.model.siteIds
            : [],
        roles: this.model.accountType === 'CUSTOMER' ? ['REQUESTER'] : this.model.roles,
        regionScope:
          this.model.accountType === 'INTERNAL' && this.model.roles.includes('TECHNICIAN')
            ? this.model.regionScope
            : 'UNASSIGNED',
        regionIds:
          this.model.accountType === 'INTERNAL' &&
          this.model.roles.includes('TECHNICIAN') &&
          this.model.regionScope === 'SELECTED_REGIONS'
            ? this.model.regionIds
            : [],
      })
      .subscribe({
        next: (user) => {
          this.saving.set(false);
          this.closeCreateForm();
          this.message.set(`Đã tạo tài khoản cho ${user.fullName}.`);
          this.load();
        },
        error: (error) => {
          this.saving.set(false);
          this.formError.set(error?.error?.detail ?? 'Không thể tạo tài khoản.');
        },
      });
  }

  openApproval(user: User) {
    this.approvalTarget.set(user);
    this.approval = { customerId: '', siteScope: 'SELECTED_SITES', siteIds: [] };
    this.reviewError.set('');
  }

  closeApproval() {
    if (this.reviewing()) return;
    this.approvalTarget.set(null);
    this.reviewError.set('');
  }

  approveRegistration() {
    const user = this.approvalTarget();
    if (!user || this.reviewing()) return;
    if (this.approval.siteScope === 'SELECTED_SITES' && !this.approval.siteIds.length) {
      this.reviewError.set('Phải chọn ít nhất một địa điểm.');
      return;
    }
    this.reviewing.set(true);
    this.reviewError.set('');
    this.api.post<User>(`/users/${user.id}/approve`, this.approval).subscribe({
      next: (updated) => {
        this.users.update((items) =>
          items.map((item) => (item.id === updated.id ? updated : item)),
        );
        this.reviewing.set(false);
        this.approvalTarget.set(null);
        this.message.set(`Đã duyệt tài khoản ${updated.username}.`);
      },
      error: (error) => {
        this.reviewing.set(false);
        this.reviewError.set(error?.error?.detail ?? 'Không thể duyệt tài khoản.');
      },
    });
  }

  openRejection(user: User) {
    this.rejectionTarget.set(user);
    this.rejectionReason = '';
    this.reviewError.set('');
  }

  closeRejection() {
    if (this.reviewing()) return;
    this.rejectionTarget.set(null);
    this.reviewError.set('');
  }

  rejectRegistration() {
    const user = this.rejectionTarget();
    if (!user || this.reviewing() || !this.rejectionReason.trim()) return;
    this.reviewing.set(true);
    this.reviewError.set('');
    this.api
      .post<User>(`/users/${user.id}/reject`, { reason: this.rejectionReason.trim() })
      .subscribe({
        next: (updated) => {
          this.users.update((items) =>
            items.map((item) => (item.id === updated.id ? updated : item)),
          );
          this.reviewing.set(false);
          this.rejectionTarget.set(null);
          this.message.set(`Đã từ chối đăng ký của ${updated.fullName}.`);
        },
        error: (error) => {
          this.reviewing.set(false);
          this.reviewError.set(error?.error?.detail ?? 'Không thể từ chối đăng ký.');
        },
      });
  }

  toggle(user: User) {
    if (this.updatingUserId()) return;
    this.updatingUserId.set(user.id);
    this.message.set('');
    this.api
      .put<User>('/users/' + user.id, {
        username: user.username,
        email: user.email,
        password: null,
        fullName: user.fullName,
        phone: user.phone ?? null,
        accountType: user.accountType,
        customerId: user.customerId ?? null,
        siteScope: user.siteScope,
        siteIds: user.siteIds,
        roles: user.roles,
        regionScope: user.regionScope ?? 'UNASSIGNED',
        regionIds: user.regionIds ?? [],
        enabled: !user.enabled,
      })
      .subscribe({
        next: (updated) => {
          this.users.update((items) =>
            items.map((item) => (item.id === updated.id ? updated : item)),
          );
          this.updatingUserId.set(null);
          this.message.set(
            updated.enabled
              ? `Đã mở khóa tài khoản ${updated.username}.`
              : `Đã khóa tài khoản ${updated.username}.`,
          );
        },
        error: (error) => {
          this.updatingUserId.set(null);
          this.loadError.set(error?.error?.detail ?? 'Không thể cập nhật trạng thái tài khoản.');
        },
      });
  }

  openRegionEditor(user: User) {
    this.regionTarget.set(user);
    this.editRegionScope = user.regionScope === 'ALL_REGIONS' ? 'ALL_REGIONS' : 'SELECTED_REGIONS';
    this.editRegionIds = [...(user.regionIds ?? [])];
    this.regionError.set('');
    this.message.set('');
  }

  closeRegionEditor() {
    if (this.savingRegions()) return;
    this.regionTarget.set(null);
    this.editRegionIds = [];
    this.regionError.set('');
  }

  editRegionScopeChanged() {
    if (this.editRegionScope !== 'SELECTED_REGIONS') this.editRegionIds = [];
  }

  saveTechnicianRegions() {
    const user = this.regionTarget();
    if (!user || this.savingRegions()) return;
    if (this.editRegionScope === 'SELECTED_REGIONS' && !this.editRegionIds.length) {
      this.regionError.set('Phải chọn ít nhất một khu vực phụ trách.');
      return;
    }
    this.savingRegions.set(true);
    this.regionError.set('');
    this.api
      .put<User>('/users/' + user.id, {
        username: user.username,
        email: user.email,
        password: null,
        fullName: user.fullName,
        phone: user.phone ?? null,
        accountType: user.accountType,
        customerId: user.customerId ?? null,
        siteScope: user.siteScope,
        siteIds: user.siteIds,
        roles: user.roles,
        regionScope: this.editRegionScope,
        regionIds: this.editRegionScope === 'SELECTED_REGIONS' ? this.editRegionIds : [],
        enabled: user.enabled,
      })
      .subscribe({
        next: (updated) => {
          this.users.update((items) =>
            items.map((item) => (item.id === updated.id ? updated : item)),
          );
          this.savingRegions.set(false);
          this.regionTarget.set(null);
          this.message.set(`Đã cập nhật khu vực phụ trách cho ${updated.fullName}.`);
        },
        error: (error) => {
          this.savingRegions.set(false);
          this.regionError.set(error?.error?.detail ?? 'Không thể cập nhật khu vực phụ trách.');
        },
      });
  }

  openReset(user: User) {
    this.resetTarget.set(user);
    this.newPassword = '';
    this.resetError.set('');
    this.message.set('');
  }

  closeReset() {
    if (this.resetting()) return;
    this.resetTarget.set(null);
    this.newPassword = '';
    this.resetError.set('');
  }

  resetPassword() {
    const user = this.resetTarget();
    if (!user || this.newPassword.length < 8 || this.resetting()) return;
    this.resetting.set(true);
    this.resetError.set('');
    this.api
      .post<void>('/users/' + user.id + '/reset-password', { password: this.newPassword })
      .subscribe({
        next: () => {
          this.resetting.set(false);
          this.resetTarget.set(null);
          this.newPassword = '';
          this.message.set(`Đã đặt lại mật khẩu cho ${user.fullName}.`);
          this.load();
        },
        error: (error) => {
          this.resetting.set(false);
          this.resetError.set(error?.error?.detail ?? 'Không thể đặt lại mật khẩu.');
        },
      });
  }

  initials(name: string) {
    return name
      .trim()
      .split(/\s+/)
      .slice(-2)
      .map((part) => part.charAt(0))
      .join('')
      .toUpperCase();
  }

  sortedRoles(roles: Role[]) {
    return [...roles].sort(
      (left, right) => this.allRoles.indexOf(right) - this.allRoles.indexOf(left),
    );
  }

  roleInitial(role: Role) {
    return (
      { REQUESTER: 'YC', TECHNICIAN: 'KT', MANAGER: 'QL', ADMIN: 'AD' } as Record<Role, string>
    )[role];
  }

  roleLabel(role: Role) {
    return (
      {
        REQUESTER: 'Người yêu cầu',
        TECHNICIAN: 'Kỹ thuật viên',
        MANAGER: 'Quản lý',
        ADMIN: 'Quản trị viên',
      } as Record<Role, string>
    )[role];
  }

  roleDescription(role: Role) {
    return (
      {
        REQUESTER: 'Tạo và theo dõi phiếu của mình',
        TECHNICIAN: 'Tiếp nhận và xử lý bảo trì',
        MANAGER: 'Điều phối và theo dõi vận hành',
        ADMIN: 'Toàn quyền quản trị hệ thống',
      } as Record<Role, string>
    )[role];
  }

  private empty() {
    return {
      username: '',
      email: '',
      password: '',
      fullName: '',
      phone: '',
      accountType: 'CUSTOMER' as AccountType,
      customerId: '',
      siteScope: 'SELECTED_SITES' as SiteScope,
      siteIds: [] as string[],
      roles: ['REQUESTER'] as Role[],
      regionScope: 'UNASSIGNED' as RegionScope,
      regionIds: [] as string[],
      enabled: true,
    };
  }
}
