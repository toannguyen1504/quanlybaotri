import { Component, inject, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Api } from '../core/api';
import { PaginationComponent } from '../shared/pagination';
interface Department {
  id: string;
  code: string;
  name: string;
  description?: string;
  location?: string;
  contactEmail?: string;
  contactPhone?: string;
  active: boolean;
}
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
type SettingsTab = 'departments' | 'categories' | 'sla';
@Component({
  selector: 'app-settings',
  imports: [FormsModule, PaginationComponent],
  template: ` <div class="page">
    <header class="page-head">
      <div>
        <p class="eyebrow">CẤU HÌNH</p>
        <h1>Cấu hình hệ thống</h1>
        <p class="muted">Quản lý dữ liệu nền dùng chung cho hệ thống.</p>
      </div>
    </header>

    <div class="settings-tabs" role="tablist" aria-label="Nhóm cấu hình">
      <button
        type="button"
        role="tab"
        [class.active]="activeTab() === 'departments'"
        [attr.aria-selected]="activeTab() === 'departments'"
        aria-controls="departments-panel"
        (click)="activeTab.set('departments')"
      >
        Phòng ban
      </button>
      <button
        type="button"
        role="tab"
        [class.active]="activeTab() === 'categories'"
        [attr.aria-selected]="activeTab() === 'categories'"
        aria-controls="categories-panel"
        (click)="activeTab.set('categories')"
      >
        Loại thiết bị
      </button>
      <button
        type="button"
        role="tab"
        [class.active]="activeTab() === 'sla'"
        [attr.aria-selected]="activeTab() === 'sla'"
        aria-controls="sla-panel"
        (click)="activeTab.set('sla')"
      >
        SLA
      </button>
    </div>

    @if (activeTab() === 'departments') {
      <article class="panel settings-manager">
        <div id="departments-panel" role="tabpanel">
          <div class="settings-manager-head">
            <div class="settings-manager-identity">
              <span class="settings-manager-icon" aria-hidden="true">PB</span>
              <div>
                <h3>Danh mục phòng ban</h3>
                <p>Quản lý đơn vị, vị trí và đầu mối liên hệ trong tổ chức.</p>
              </div>
            </div>
            <div class="settings-manager-actions">
              <div class="settings-manager-stats">
                <span
                  ><b>{{ departments().length }}</b> tổng cộng</span
                >
                <span class="active-stat"><i></i>{{ activeDepartmentCount() }} đang dùng</span>
              </div>
              <button type="button" class="primary" (click)="openDepartmentCreate()">
                + Thêm phòng ban
              </button>
            </div>
          </div>
          @if (departmentMessage()) {
            <div class="success-message settings-message">{{ departmentMessage() }}</div>
          }
          @if (showDepartmentForm()) {
            <form class="form department-form settings-editor" (ngSubmit)="saveDepartment()">
              <div class="settings-form-title">
                <strong>{{
                  editingDepartmentId() ? 'Chỉnh sửa phòng ban' : 'Thêm phòng ban mới'
                }}</strong>
                <span>Nhập thông tin chi tiết rồi lưu thay đổi.</span>
              </div>
              <div class="form-grid">
                <label
                  >Mã phòng ban<input
                    name="departmentCode"
                    maxlength="50"
                    [(ngModel)]="department.code"
                    placeholder="Ví dụ: IT"
                    required
                /></label>
                <label
                  >Tên phòng ban<input
                    name="departmentName"
                    maxlength="150"
                    [(ngModel)]="department.name"
                    placeholder="Ví dụ: Công nghệ thông tin"
                    required
                /></label>
                <label
                  >Email liên hệ<input
                    name="departmentEmail"
                    type="email"
                    maxlength="190"
                    [(ngModel)]="department.contactEmail"
                    placeholder="phongban@example.com"
                /></label>
                <label
                  >Số điện thoại<input
                    name="departmentPhone"
                    maxlength="30"
                    [(ngModel)]="department.contactPhone"
                    placeholder="Số máy lẻ hoặc số liên hệ"
                /></label>
                <label class="department-location"
                  >Vị trí<input
                    name="departmentLocation"
                    maxlength="255"
                    [(ngModel)]="department.location"
                    placeholder="Tòa nhà, tầng, khu vực"
                /></label>
              </div>
              <label
                >Mô tả<textarea
                  name="departmentDescription"
                  rows="3"
                  maxlength="500"
                  [(ngModel)]="department.description"
                  placeholder="Chức năng hoặc phạm vi phụ trách của phòng ban"
                ></textarea>
              </label>
              @if (editingDepartmentId()) {
                <label class="department-active"
                  ><input type="checkbox" name="departmentActive" [(ngModel)]="department.active" />
                  Đang sử dụng</label
                >
              }
              @if (departmentError()) {
                <div class="alert">{{ departmentError() }}</div>
              }
              <div class="actions">
                <button type="button" class="secondary" (click)="cancelDepartmentEdit()">
                  Hủy
                </button>
                <button class="primary" [disabled]="savingDepartment()">
                  {{
                    savingDepartment()
                      ? 'Đang lưu…'
                      : editingDepartmentId()
                        ? 'Lưu thay đổi'
                        : 'Thêm phòng ban'
                  }}
                </button>
              </div>
            </form>
          }
          <div class="settings-list-heading">
            <div>
              <h4>Danh sách phòng ban</h4>
              <p>Thông tin được sắp xếp theo từng đơn vị trong hệ thống.</p>
            </div>
          </div>
          <div class="settings-table-wrap settings-desktop-records" data-list="departments">
            <table class="settings-data-table">
              <thead>
                <tr>
                  <th>Phòng ban</th>
                  <th>Thông tin liên hệ</th>
                  <th>Vị trí</th>
                  <th>Trạng thái</th>
                  <th></th>
                </tr>
              </thead>
              <tbody>
                @for (d of pagedDepartments(); track d.id) {
                  <tr>
                    <td>
                      <div class="settings-name-cell">
                        <span>{{ d.code.slice(0, 2).toUpperCase() }}</span>
                        <div>
                          <b>{{ d.name }}</b>
                          <small>{{ d.code }} · {{ d.description || 'Chưa có mô tả' }}</small>
                        </div>
                      </div>
                    </td>
                    <td>
                      <div class="settings-contact-cell">
                        @if (d.contactEmail) {
                          <a [href]="'mailto:' + d.contactEmail">{{ d.contactEmail }}</a>
                        } @else {
                          <span>Chưa có email</span>
                        }
                        @if (d.contactPhone) {
                          <a [href]="'tel:' + d.contactPhone">{{ d.contactPhone }}</a>
                        } @else {
                          <span>Chưa có số điện thoại</span>
                        }
                      </div>
                    </td>
                    <td>
                      <span class="settings-location">{{ d.location || 'Chưa cập nhật' }}</span>
                    </td>
                    <td>
                      <span [class]="'settings-state ' + (d.active ? 'active' : 'inactive')">
                        <i></i>{{ d.active ? 'Đang sử dụng' : 'Đã tắt' }}
                      </span>
                    </td>
                    <td class="settings-row-action">
                      <button
                        type="button"
                        (click)="editDepartment(d)"
                        aria-label="Chỉnh sửa phòng ban"
                      >
                        Chỉnh sửa
                      </button>
                    </td>
                  </tr>
                } @empty {
                  <tr>
                    <td colspan="5" class="empty">Chưa có phòng ban.</td>
                  </tr>
                }
              </tbody>
            </table>
          </div>
          <div class="settings-mobile-records" data-mobile-list="departments">
            @for (d of pagedDepartments(); track d.id) {
              <article class="settings-record-card">
                <div class="settings-record-head">
                  <div class="settings-name-cell">
                    <span>{{ d.code.slice(0, 2).toUpperCase() }}</span>
                    <div>
                      <b>{{ d.name }}</b
                      ><small>{{ d.code }}</small>
                    </div>
                  </div>
                  <span [class]="'settings-state ' + (d.active ? 'active' : 'inactive')">
                    <i></i>{{ d.active ? 'Đang dùng' : 'Đã tắt' }}
                  </span>
                </div>
                <p>{{ d.description || 'Chưa có mô tả.' }}</p>
                <dl>
                  <div>
                    <dt>Vị trí</dt>
                    <dd>{{ d.location || '—' }}</dd>
                  </div>
                  <div>
                    <dt>Email</dt>
                    <dd>{{ d.contactEmail || '—' }}</dd>
                  </div>
                  <div>
                    <dt>Điện thoại</dt>
                    <dd>{{ d.contactPhone || '—' }}</dd>
                  </div>
                </dl>
                <button type="button" class="settings-card-edit" (click)="editDepartment(d)">
                  Chỉnh sửa thông tin
                </button>
              </article>
            } @empty {
              <div class="empty">Chưa có phòng ban.</div>
            }
          </div>
          <app-pagination
            [totalItems]="departments().length"
            [page]="departmentPage"
            [pageSize]="pageSize"
            itemLabel="phòng ban"
            (pageChange)="departmentPage = $event"
          />
        </div>
      </article>
    }

    @if (activeTab() === 'categories') {
      <article class="panel settings-manager">
        <div id="categories-panel" role="tabpanel">
          <div class="settings-manager-head">
            <div class="settings-manager-identity">
              <span class="settings-manager-icon category-icon" aria-hidden="true">TB</span>
              <div>
                <h3>Danh mục loại thiết bị</h3>
                <p>Chuẩn hóa nhóm thiết bị để quản lý và báo cáo nhất quán.</p>
              </div>
            </div>
            <div class="settings-manager-actions">
              <div class="settings-manager-stats">
                <span
                  ><b>{{ categories().length }}</b> tổng cộng</span
                >
                <span class="active-stat"><i></i>{{ activeCategoryCount() }} đang dùng</span>
              </div>
              <button type="button" class="primary" (click)="openCategoryCreate()">
                + Thêm loại thiết bị
              </button>
            </div>
          </div>
          @if (showCategoryForm()) {
            <form class="form category-form settings-editor" (ngSubmit)="saveCategory()">
              <div class="settings-form-title">
                <strong>{{
                  editingCategoryId() ? 'Chỉnh sửa loại thiết bị' : 'Thêm loại thiết bị mới'
                }}</strong>
                <span>Nhập thông tin phân loại rồi lưu thay đổi.</span>
              </div>
              <div class="form-grid">
                <label
                  >Mã loại<input
                    name="categoryCode"
                    maxlength="50"
                    [(ngModel)]="category.code"
                    placeholder="Ví dụ: PRINTER"
                    required
                /></label>
                <label
                  >Tên loại thiết bị<input
                    name="categoryName"
                    maxlength="150"
                    [(ngModel)]="category.name"
                    placeholder="Ví dụ: Máy in"
                    required
                /></label>
              </div>
              <label
                >Mô tả<textarea
                  name="categoryDescription"
                  rows="3"
                  maxlength="500"
                  [(ngModel)]="category.description"
                  placeholder="Đặc điểm hoặc phạm vi thiết bị thuộc loại này"
                ></textarea>
              </label>
              @if (editingCategoryId()) {
                <label class="department-active"
                  ><input type="checkbox" name="categoryActive" [(ngModel)]="category.active" />
                  Đang sử dụng</label
                >
              }
              @if (categoryError()) {
                <div class="alert">{{ categoryError() }}</div>
              }
              <div class="actions">
                <button type="button" class="secondary" (click)="cancelCategoryEdit()">Hủy</button>
                <button class="primary">
                  {{ editingCategoryId() ? 'Lưu thay đổi' : 'Thêm loại thiết bị' }}
                </button>
              </div>
            </form>
          }
          <div class="settings-list-heading">
            <div>
              <h4>Danh sách loại thiết bị</h4>
              <p>Theo dõi danh mục và số lượng thiết bị đang được phân loại.</p>
            </div>
          </div>
          <div class="settings-table-wrap settings-desktop-records" data-list="categories">
            <table class="settings-data-table category-data-table">
              <thead>
                <tr>
                  <th>Loại thiết bị</th>
                  <th>Mô tả</th>
                  <th>Số thiết bị</th>
                  <th>Trạng thái</th>
                  <th></th>
                </tr>
              </thead>
              <tbody>
                @for (c of pagedCategories(); track c.id) {
                  <tr>
                    <td>
                      <div class="settings-name-cell">
                        <span class="category-avatar">{{ c.code.slice(0, 2).toUpperCase() }}</span>
                        <div>
                          <b>{{ c.name }}</b
                          ><small>{{ c.code }}</small>
                        </div>
                      </div>
                    </td>
                    <td>
                      <p class="settings-table-description">
                        {{ c.description || 'Chưa có mô tả' }}
                      </p>
                    </td>
                    <td>
                      <strong class="equipment-total">{{ c.equipmentCount }}</strong>
                    </td>
                    <td>
                      <span [class]="'settings-state ' + (c.active ? 'active' : 'inactive')">
                        <i></i>{{ c.active ? 'Đang sử dụng' : 'Đã tắt' }}
                      </span>
                    </td>
                    <td class="settings-row-action">
                      <button
                        type="button"
                        (click)="editCategory(c)"
                        aria-label="Chỉnh sửa loại thiết bị"
                      >
                        Chỉnh sửa
                      </button>
                    </td>
                  </tr>
                } @empty {
                  <tr>
                    <td colspan="5" class="empty">Chưa có loại thiết bị.</td>
                  </tr>
                }
              </tbody>
            </table>
          </div>
          <div class="settings-mobile-records" data-mobile-list="categories">
            @for (c of pagedCategories(); track c.id) {
              <article class="settings-record-card">
                <div class="settings-record-head">
                  <div class="settings-name-cell">
                    <span class="category-avatar">{{ c.code.slice(0, 2).toUpperCase() }}</span>
                    <div>
                      <b>{{ c.name }}</b
                      ><small>{{ c.code }}</small>
                    </div>
                  </div>
                  <span [class]="'settings-state ' + (c.active ? 'active' : 'inactive')">
                    <i></i>{{ c.active ? 'Đang dùng' : 'Đã tắt' }}
                  </span>
                </div>
                <p>{{ c.description || 'Chưa có mô tả cho loại thiết bị này.' }}</p>
                <div class="settings-card-count">
                  <span>Thiết bị thuộc loại này</span><b>{{ c.equipmentCount }}</b>
                </div>
                <button type="button" class="settings-card-edit" (click)="editCategory(c)">
                  Chỉnh sửa thông tin
                </button>
              </article>
            } @empty {
              <div class="empty">Chưa có loại thiết bị.</div>
            }
          </div>
          <app-pagination
            [totalItems]="categories().length"
            [page]="categoryPage"
            [pageSize]="pageSize"
            itemLabel="loại thiết bị"
            (pageChange)="categoryPage = $event"
          />
        </div>
      </article>
    }

    @if (activeTab() === 'sla') {
      <article class="panel">
        <div id="sla-panel" role="tabpanel">
          <h3>Chính sách SLA</h3>
          <p class="muted settings-description">
            Thiết lập thời gian phản hồi và hoàn thành theo từng mức ưu tiên.
          </p>
          @for (s of pagedSla(); track s.id) {
            <form class="sla-row" (ngSubmit)="saveSla(s)">
              <b [class]="'priority ' + s.priority.toLowerCase()">{{ s.priority }}</b
              ><label
                >Phản hồi (phút)<input
                  name="response-{{ s.id }}"
                  type="number"
                  min="1"
                  [(ngModel)]="s.responseMinutes" /></label
              ><label
                >Hoàn thành (phút)<input
                  name="resolution-{{ s.id }}"
                  type="number"
                  min="1"
                  [(ngModel)]="s.resolutionMinutes" /></label
              ><button class="secondary">Lưu</button>
            </form>
          }
          <app-pagination
            [totalItems]="sla().length"
            [page]="slaPage"
            [pageSize]="pageSize"
            itemLabel="chính sách SLA"
            (pageChange)="slaPage = $event"
          />
        </div>
      </article>
    }
  </div>`,
})
export class SettingsPage implements OnInit {
  private api = inject(Api);
  activeTab = signal<SettingsTab>('departments');
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
  departmentPage = 0;
  categoryPage = 0;
  slaPage = 0;
  ngOnInit() {
    this.load();
  }
  load() {
    this.api.get<Department[]>('/departments').subscribe((x) => this.departments.set(x));
    this.api.get<Category[]>('/equipment-categories').subscribe((x) => this.categories.set(x));
    this.api.get<Sla[]>('/sla-policies').subscribe((x) => this.sla.set(x));
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
      ? this.api.put<Department>('/departments/' + id, this.department)
      : this.api.post<Department>('/departments', this.department);
    request.subscribe({
      next: () => {
        this.cancelDepartmentEdit();
        this.savingDepartment.set(false);
        this.departmentMessage.set(id ? 'Đã cập nhật phòng ban' : 'Đã thêm phòng ban');
        this.load();
      },
      error: (e) => {
        this.savingDepartment.set(false);
        this.departmentError.set(e.error?.detail ?? 'Không thể lưu thông tin phòng ban');
      },
    });
  }
  editDepartment(department: Department) {
    this.showDepartmentForm.set(true);
    this.editingDepartmentId.set(department.id);
    this.departmentError.set('');
    this.departmentMessage.set('');
    this.department = {
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
      code: '',
      name: '',
      description: '',
      location: '',
      contactEmail: '',
      contactPhone: '',
      active: true,
    };
  }
  private emptyCategory() {
    return { code: '', name: '', description: '', active: true };
  }
}
