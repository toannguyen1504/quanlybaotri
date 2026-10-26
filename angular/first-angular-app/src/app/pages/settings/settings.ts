import { Component, inject, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Api } from '../../core/api';
import { PaginationComponent } from '../../shared/pagination';
import { Customer, CustomerSite, Region } from '../../core/models';
type Branch = Customer;
type Department = CustomerSite;
interface Category {
  id: string;
  code: string;
  name: string;
  description?: string;
  active: boolean;
  equipmentCount: number;
}
interface Sla {
  id: string;
  priority: string;
  responseMinutes: number;
  resolutionMinutes: number;
  active: boolean;
  version: number;
}
type SettingsTab = 'branches' | 'departments' | 'regions' | 'categories' | 'sla';
@Component({
  selector: 'app-settings',
  imports: [FormsModule, PaginationComponent],
  templateUrl: './settings.html',
})
export class SettingsPage implements OnInit {
  private api = inject(Api);
  activeTab = signal<SettingsTab>('departments');
  regions = signal<Region[]>([]);
  showRegionForm = signal(false);
  editingRegionId = signal<string | null>(null);
  regionError = signal('');
  regionMessage = signal('');
  region = this.emptyRegion();
  branchRegionFilter = '';
  branches = signal<Branch[]>([]);
  showBranchForm = signal(false);
  editingBranchId = signal<string | null>(null);
  branchError = signal('');
  branchMessage = signal('');
  savingBranch = signal(false);
  branch = this.emptyBranch();
  departments = signal<Department[]>([]);
  showDepartmentForm = signal(false);
  editingDepartmentId = signal<string | null>(null);
  departmentError = signal('');
  departmentMessage = signal('');
  savingDepartment = signal(false);
  categories = signal<Category[]>([]);
  showCategoryForm = signal(false);
  editingCategoryId = signal<string | null>(null);
  categoryError = signal('');
  sla = signal<Sla[]>([]);
  department = this.emptyDepartment();
  category = this.emptyCategory();
  readonly pageSize = 10;
  branchPage = 0;
  departmentPage = 0;
  categoryPage = 0;
  slaPage = 0;
  ngOnInit() {
    this.load();
  }
  load() {
    this.api.get<Region[]>('/regions').subscribe((x) => this.regions.set(x));
    this.api.get<Branch[]>('/customers').subscribe((x) => this.branches.set(x));
    this.api.get<Department[]>('/customer-sites').subscribe((x) => this.departments.set(x));
    this.api.get<Category[]>('/equipment-categories').subscribe((x) => this.categories.set(x));
    this.api.get<Sla[]>('/sla-policies').subscribe((x) => this.sla.set(x));
  }
  activeBranches() {
    return this.branches().filter((item) => item.active || item.id === this.department.customerId);
  }
  activeBranchCount() {
    return this.branches().filter((branch) => branch.active).length;
  }
  pagedBranches() {
    return this.pageItems(this.filteredBranches(), this.branchPage);
  }
  filteredBranches() {
    if (!this.branchRegionFilter) return this.branches();
    return this.branches().filter((branch) => {
      const sites = this.departments().filter((site) => site.customerId === branch.id);
      return this.branchRegionFilter === 'UNASSIGNED'
        ? sites.length === 0 || sites.some((site) => !site.regionId)
        : sites.some((site) => site.regionId === this.branchRegionFilter);
    });
  }
  regionNamesForBranch(customerId: string) {
    const names = [
      ...new Set(
        this.departments()
          .filter((site) => site.customerId === customerId)
          .map((site) => site.regionName)
          .filter(Boolean),
      ),
    ];
    return names.length ? names.join(', ') : 'Chưa phân vùng';
  }
  activeRegions(currentId = '') {
    return this.regions().filter((item) => item.active || item.id === currentId);
  }
  siteCountForRegion(regionId: string) {
    return this.departments().filter((site) => site.regionId === regionId).length;
  }
  openRegionCreate() {
    this.editingRegionId.set(null);
    this.regionError.set('');
    this.regionMessage.set('');
    this.region = this.emptyRegion();
    this.showRegionForm.set(true);
  }
  editRegion(item: Region) {
    this.editingRegionId.set(item.id);
    this.regionError.set('');
    this.regionMessage.set('');
    this.region = {
      code: item.code,
      name: item.name,
      description: item.description ?? '',
      active: item.active,
    };
    this.showRegionForm.set(true);
  }
  cancelRegionEdit() {
    this.showRegionForm.set(false);
    this.editingRegionId.set(null);
    this.regionError.set('');
    this.region = this.emptyRegion();
  }
  saveRegion() {
    this.regionError.set('');
    const id = this.editingRegionId();
    const request = id
      ? this.api.put<Region>('/regions/' + id, this.region)
      : this.api.post<Region>('/regions', this.region);
    request.subscribe({
      next: () => {
        this.cancelRegionEdit();
        this.regionMessage.set(id ? 'Đã cập nhật khu vực' : 'Đã thêm khu vực');
        this.load();
      },
      error: (e) => this.regionError.set(e.error?.detail ?? 'Không thể lưu khu vực'),
    });
  }
  departmentCountForBranch(customerId: string) {
    return this.departments().filter((department) => department.customerId === customerId).length;
  }
  openBranchCreate() {
    this.editingBranchId.set(null);
    this.branchError.set('');
    this.branchMessage.set('');
    this.branch = this.emptyBranch();
    this.showBranchForm.set(true);
  }
  saveBranch() {
    this.branchError.set('');
    this.branchMessage.set('');
    this.savingBranch.set(true);
    const id = this.editingBranchId();
    const request = id
      ? this.api.put<Branch>('/customers/' + id, this.branch)
      : this.api.post<Branch>('/customers', this.branch);
    request.subscribe({
      next: () => {
        this.cancelBranchEdit();
        this.savingBranch.set(false);
        this.branchMessage.set(id ? 'Đã cập nhật khách hàng' : 'Đã thêm khách hàng');
        this.load();
      },
      error: (e) => {
        this.savingBranch.set(false);
        this.branchError.set(e.error?.detail ?? 'Không thể lưu khách hàng');
      },
    });
  }
  editBranch(item: Branch) {
    this.showBranchForm.set(true);
    this.editingBranchId.set(item.id);
    this.branchError.set('');
    this.branchMessage.set('');
    this.branch = {
      code: item.code,
      name: item.name,
      address: item.address ?? '',
      contactEmail: item.contactEmail ?? '',
      contactPhone: item.contactPhone ?? '',
      active: item.active,
    };
  }
  cancelBranchEdit() {
    this.showBranchForm.set(false);
    this.editingBranchId.set(null);
    this.branchError.set('');
    this.branch = this.emptyBranch();
  }
  activeDepartmentCount() {
    return this.departments().filter((department) => department.active).length;
  }
  activeCategoryCount() {
    return this.categories().filter((category) => category.active).length;
  }
  pagedDepartments() {
    return this.pageItems(this.departments(), this.departmentPage);
  }
  pagedCategories() {
    return this.pageItems(this.categories(), this.categoryPage);
  }
  pagedSla() {
    return this.pageItems(this.sla(), this.slaPage);
  }
  openDepartmentCreate() {
    this.editingDepartmentId.set(null);
    this.departmentError.set('');
    this.departmentMessage.set('');
    this.department = this.emptyDepartment();
    this.showDepartmentForm.set(true);
  }
  saveDepartment() {
    this.departmentError.set('');
    this.departmentMessage.set('');
    this.savingDepartment.set(true);
    const id = this.editingDepartmentId();
    const request = id
      ? this.api.put<Department>('/customer-sites/' + id, this.department)
      : this.api.post<Department>('/customer-sites', this.department);
    request.subscribe({
      next: () => {
        this.cancelDepartmentEdit();
        this.savingDepartment.set(false);
        this.departmentMessage.set(id ? 'Đã cập nhật địa điểm' : 'Đã thêm địa điểm');
        this.load();
      },
      error: (e) => {
        this.savingDepartment.set(false);
        this.departmentError.set(e.error?.detail ?? 'Không thể lưu thông tin địa điểm');
      },
    });
  }
  editDepartment(department: Department) {
    this.showDepartmentForm.set(true);
    this.editingDepartmentId.set(department.id);
    this.departmentError.set('');
    this.departmentMessage.set('');
    this.department = {
      customerId: department.customerId,
      regionId: department.regionId ?? '',
      code: department.code,
      name: department.name,
      description: department.description ?? '',
      location: department.location ?? '',
      contactEmail: department.contactEmail ?? '',
      contactPhone: department.contactPhone ?? '',
      active: department.active,
    };
  }
  cancelDepartmentEdit() {
    this.showDepartmentForm.set(false);
    this.editingDepartmentId.set(null);
    this.departmentError.set('');
    this.department = this.emptyDepartment();
  }
  openCategoryCreate() {
    this.editingCategoryId.set(null);
    this.categoryError.set('');
    this.category = this.emptyCategory();
    this.showCategoryForm.set(true);
  }
  saveCategory() {
    this.categoryError.set('');
    const id = this.editingCategoryId();
    const request = id
      ? this.api.put<Category>('/equipment-categories/' + id, this.category)
      : this.api.post<Category>('/equipment-categories', this.category);
    request.subscribe({
      next: () => {
        this.cancelCategoryEdit();
        this.load();
      },
      error: (e) => this.categoryError.set(e.error?.detail ?? 'Không thể lưu loại thiết bị'),
    });
  }
  editCategory(category: Category) {
    this.showCategoryForm.set(true);
    this.editingCategoryId.set(category.id);
    this.categoryError.set('');
    this.category = {
      code: category.code,
      name: category.name,
      description: category.description ?? '',
      active: category.active,
    };
  }
  cancelCategoryEdit() {
    this.showCategoryForm.set(false);
    this.editingCategoryId.set(null);
    this.categoryError.set('');
    this.category = this.emptyCategory();
  }
  saveSla(s: Sla) {
    this.api
      .put('/sla-policies/' + s.id, {
        responseMinutes: s.responseMinutes,
        resolutionMinutes: s.resolutionMinutes,
        active: s.active,
      })
      .subscribe(() => this.load());
  }
  private pageItems<T>(items: T[], requestedPage: number) {
    const page = Math.min(requestedPage, Math.max(Math.ceil(items.length / this.pageSize) - 1, 0));
    return items.slice(page * this.pageSize, (page + 1) * this.pageSize);
  }
  private emptyDepartment() {
    return {
      customerId: '',
      regionId: '',
      code: '',
      name: '',
      description: '',
      location: '',
      contactEmail: '',
      contactPhone: '',
      active: true,
    };
  }
  private emptyRegion() {
    return { code: '', name: '', description: '', active: true };
  }
  private emptyCategory() {
    return { code: '', name: '', description: '', active: true };
  }
  private emptyBranch() {
    return { code: '', name: '', address: '', contactEmail: '', contactPhone: '', active: true };
  }
}
