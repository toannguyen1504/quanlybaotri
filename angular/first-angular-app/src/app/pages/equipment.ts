import { Component, inject, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Api } from '../core/api';
import { AuthService } from '../core/auth';
import { Equipment, Page } from '../core/models';
import { PaginationComponent } from '../shared/pagination';

interface Category {
  id: string;
  code: string;
  name: string;
  description?: string;
  active: boolean;
}

interface Department {
  id: string;
  code: string;
  name: string;
  active: boolean;
}

@Component({
  selector: 'app-equipment',
  imports: [FormsModule, PaginationComponent],
  template: `
    <div class="page management-page equipment-page">
      <header class="page-head management-page-head">
        <div>
          <p class="eyebrow">QUẢN LÝ TÀI SẢN</p>
          <h1>Thiết bị</h1>
          <p class="muted">Theo dõi tình trạng, vị trí và đơn vị sử dụng của toàn bộ thiết bị.</p>
        </div>
        <div class="management-head-actions">
          <button type="button" class="secondary" [disabled]="loading()" (click)="load()">
            <span aria-hidden="true">↻</span>
            {{ loading() ? 'Đang tải' : 'Làm mới' }}
          </button>
          @if (canManage()) {
            <button type="button" class="primary" (click)="openCreate()">
              <span aria-hidden="true">＋</span>
              Thêm thiết bị
            </button>
          }
        </div>
      </header>

      <section class="management-stats" aria-label="Tổng quan thiết bị">
        <article>
          <span class="management-stat-icon neutral" aria-hidden="true">▦</span>
          <div>
            <span>Tổng thiết bị</span><b>{{ equipment().length }}</b
            ><small>Đang được quản lý</small>
          </div>
        </article>
        <article>
          <span class="management-stat-icon success" aria-hidden="true">✓</span>
          <div>
            <span>Đang hoạt động</span><b>{{ countStatus('ACTIVE') }}</b
            ><small>Sẵn sàng sử dụng</small>
          </div>
        </article>
        <article>
          <span class="management-stat-icon warning" aria-hidden="true">⌁</span>
          <div>
            <span>Đang bảo trì</span><b>{{ countStatus('UNDER_MAINTENANCE') }}</b
            ><small>Cần theo dõi</small>
          </div>
        </article>
        <article>
          <span class="management-stat-icon danger" aria-hidden="true">—</span>
          <div>
            <span>Ngừng sử dụng</span><b>{{ countStatus('RETIRED') }}</b
            ><small>Không còn vận hành</small>
          </div>
        </article>
      </section>

      @if (success()) {
        <div class="management-message success-message" role="status">
          <span aria-hidden="true">✓</span>
          {{ success() }}
          <button type="button" aria-label="Đóng thông báo" (click)="success.set('')">×</button>
        </div>
      }

      @if (showForm()) {
        <section class="panel management-form-panel">
          <div class="management-form-head">
            <div>
              <span class="management-form-icon" aria-hidden="true">▣</span>
              <div>
                <h2>{{ editingId ? 'Cập nhật thiết bị' : 'Thêm thiết bị mới' }}</h2>
                <p>
                  {{
                    editingId
                      ? 'Điều chỉnh thông tin nhận diện và trạng thái vận hành.'
                      : 'Khai báo đầy đủ để thiết bị được phân loại và theo dõi chính xác.'
                  }}
                </p>
              </div>
            </div>
            @if (editingId) {
              <span class="management-mode-badge">Đang chỉnh sửa</span>
            }
          </div>

          <form class="management-form" (ngSubmit)="save()">
            <div class="management-form-section">
              <div class="management-section-title">
                <span>01</span>
                <div><b>Thông tin nhận diện</b><small>Mã và tên hiển thị của thiết bị</small></div>
              </div>
              <div class="form-grid">
                <label>
                  <span>Mã thiết bị <i>*</i></span>
                  <input
                    name="assetCode"
                    [(ngModel)]="model.assetCode"
                    required
                    maxlength="80"
                    placeholder="Ví dụ: TB-LAP-001"
                  />
                </label>
                <label>
                  <span>Tên thiết bị <i>*</i></span>
                  <input
                    name="name"
                    [(ngModel)]="model.name"
                    required
                    maxlength="200"
                    placeholder="Ví dụ: Laptop Dell Latitude"
                  />
                </label>
                <label>
                  <span>Số serial</span>
                  <input
                    name="serialNumber"
                    [(ngModel)]="model.serialNumber"
                    maxlength="150"
                    placeholder="Nhập số serial"
                  />
                </label>
                <div class="management-inline-fields">
                  <label>
                    <span>Hãng sản xuất</span>
                    <input
                      name="manufacturer"
                      [(ngModel)]="model.manufacturer"
                      maxlength="150"
                      placeholder="Ví dụ: Dell"
                    />
                  </label>
                  <label>
                    <span>Model</span>
                    <input
                      name="model"
                      [(ngModel)]="model.model"
                      maxlength="150"
                      placeholder="Latitude 5440"
                    />
                  </label>
                </div>
              </div>
            </div>

            <div class="management-form-section">
              <div class="management-section-title">
                <span>02</span>
                <div>
                  <b>Phân loại và vận hành</b><small>Nơi sử dụng và tình trạng hiện tại</small>
                </div>
              </div>
              <div class="form-grid">
                <label>
                  <span>Loại thiết bị <i>*</i></span>
                  <select name="categoryId" [(ngModel)]="model.categoryId" required>
                    <option value="">Chọn loại thiết bị</option>
                    @for (category of categories(); track category.id) {
                      <option [value]="category.id">{{ category.name }}</option>
                    }
                  </select>
                </label>
                <label>
                  <span>Phòng ban sử dụng</span>
                  <select name="departmentId" [(ngModel)]="model.departmentId">
                    <option value="">Chưa phân công</option>
                    @for (department of departments(); track department.id) {
                      <option [value]="department.id">{{ department.name }}</option>
                    }
                  </select>
                </label>
                <label>
                  <span>Vị trí đặt thiết bị</span>
                  <input
                    name="location"
                    [(ngModel)]="model.location"
                    maxlength="255"
                    placeholder="Ví dụ: Tầng 3, phòng 305"
                  />
                </label>
                <label>
                  <span>Trạng thái vận hành <i>*</i></span>
                  <select name="status" [(ngModel)]="model.status" required>
                    <option value="ACTIVE">Đang hoạt động</option>
                    <option value="UNDER_MAINTENANCE">Đang bảo trì</option>
                    <option value="RETIRED">Ngừng sử dụng</option>
                  </select>
                </label>
              </div>
            </div>

            @if (formError()) {
              <div class="alert" role="alert">{{ formError() }}</div>
            }
            <div class="management-form-actions">
              <small><i>*</i> Thông tin bắt buộc</small>
              <div class="actions">
                <button type="button" class="secondary" [disabled]="saving()" (click)="cancel()">
                  Hủy
                </button>
                <button class="primary" [disabled]="saving()">
                  {{ saving() ? 'Đang lưu...' : editingId ? 'Lưu thay đổi' : 'Tạo thiết bị' }}
                </button>
              </div>
            </div>
          </form>
        </section>
      }

      <section class="panel management-filter-panel">
        <div class="management-filter-head">
          <div>
            <h2>Danh sách thiết bị</h2>
            <p>Tìm nhanh và lọc theo trạng thái, loại thiết bị hoặc phòng ban.</p>
          </div>
          @if (hasFilters()) {
            <button type="button" class="management-clear-filter" (click)="clearFilters()">
              Xóa bộ lọc
            </button>
          }
        </div>
        <div class="management-filters equipment-filters">
          <label class="management-search-field">
            <span>Tìm kiếm</span>
            <div>
              <span aria-hidden="true">⌕</span>
              <input
                name="equipmentQuery"
                [(ngModel)]="query"
                (ngModelChange)="resetPage()"
                placeholder="Mã, tên, serial, hãng hoặc vị trí..."
              />
            </div>
          </label>
          <label>
            <span>Trạng thái</span>
            <select name="equipmentStatus" [(ngModel)]="statusFilter" (ngModelChange)="resetPage()">
              <option value="">Tất cả trạng thái</option>
              <option value="ACTIVE">Đang hoạt động</option>
              <option value="UNDER_MAINTENANCE">Đang bảo trì</option>
              <option value="RETIRED">Ngừng sử dụng</option>
            </select>
          </label>
          <label>
            <span>Loại thiết bị</span>
            <select
              name="equipmentCategory"
              [(ngModel)]="categoryFilter"
              (ngModelChange)="resetPage()"
            >
              <option value="">Tất cả loại</option>
              @for (category of categories(); track category.id) {
                <option [value]="category.id">{{ category.name }}</option>
              }
            </select>
          </label>
          <label>
            <span>Phòng ban</span>
            <select
              name="equipmentDepartment"
              [(ngModel)]="departmentFilter"
              (ngModelChange)="resetPage()"
            >
              <option value="">Tất cả phòng ban</option>
              @for (department of departments(); track department.id) {
                <option [value]="department.id">{{ department.name }}</option>
              }
            </select>
          </label>
        </div>
        <div class="management-result-line">
          <span
            >Hiển thị <b>{{ filteredEquipment().length }}</b> trên {{ equipment().length }} thiết
            bị</span
          >
          @if (hasFilters()) {
            <span class="filter-active">Đang áp dụng bộ lọc</span>
          }
        </div>
      </section>

      @if (listError()) {
        <section class="panel management-state-card error-state" role="alert">
          <span class="management-state-icon" aria-hidden="true">!</span>
          <div>
            <h3>Không thể tải danh sách thiết bị</h3>
            <p>{{ listError() }}</p>
          </div>
          <button type="button" class="secondary" (click)="load()">Thử lại</button>
        </section>
      } @else if (loading()) {
        <section class="panel management-state-card">
          <span class="management-spinner" aria-hidden="true"></span>
          <div>
            <h3>Đang tải thiết bị</h3>
            <p>Vui lòng chờ trong giây lát...</p>
          </div>
        </section>
      } @else if (filteredEquipment().length) {
        <section class="panel table-wrap management-table-wrap equipment-table">
          <table>
            <thead>
              <tr>
                <th>Thiết bị</th>
                <th>Phân loại</th>
                <th>Phòng ban / vị trí</th>
                <th>Serial</th>
                <th>Trạng thái</th>
                @if (canManage()) {
                  <th class="management-action-column">Thao tác</th>
                }
              </tr>
            </thead>
            <tbody>
              @for (item of pagedEquipment(); track item.id) {
                <tr>
                  <td>
                    <div class="management-name-cell">
                      <span class="management-item-avatar" aria-hidden="true">TB</span>
                      <div>
                        <b>{{ item.name }}</b
                        ><small class="code">{{ item.assetCode }}</small>
                      </div>
                    </div>
                  </td>
                  <td>
                    <b class="management-cell-title">{{ item.categoryName }}</b
                    ><small>{{ equipmentBrand(item) }}</small>
                  </td>
                  <td>
                    <b class="management-cell-title">{{
                      item.departmentName || 'Chưa phân công'
                    }}</b
                    ><small>{{ item.location || 'Chưa cập nhật vị trí' }}</small>
                  </td>
                  <td>
                    <span class="management-serial">{{ item.serialNumber || '—' }}</span>
                  </td>
                  <td>
                    <span [class]="'status ' + item.status.toLowerCase()">{{
                      statusLabel(item.status)
                    }}</span>
                    @if (!item.active) {
                      <small class="management-inactive-note">Ngừng quản lý</small>
                    }
                  </td>
                  @if (canManage()) {
                    <td class="management-action-cell">
                      <button
                        type="button"
                        class="management-icon-button"
                        title="Sửa thiết bị"
                        (click)="edit(item)"
                      >
                        <span aria-hidden="true">✎</span><span>Sửa</span>
                      </button>
                    </td>
                  }
                </tr>
              }
            </tbody>
          </table>
        </section>

        <section
          class="management-mobile-list"
          aria-label="Danh sách thiết bị trên thiết bị di động"
        >
          @for (item of pagedEquipment(); track item.id) {
            <article class="panel management-mobile-card">
              <div class="management-mobile-card-head">
                <div class="management-name-cell">
                  <span class="management-item-avatar" aria-hidden="true">TB</span>
                  <div>
                    <b>{{ item.name }}</b
                    ><small class="code">{{ item.assetCode }}</small>
                  </div>
                </div>
                <span [class]="'status ' + item.status.toLowerCase()">{{
                  statusLabel(item.status)
                }}</span>
              </div>
              <dl>
                <div>
                  <dt>Loại thiết bị</dt>
                  <dd>{{ item.categoryName }}</dd>
                </div>
                <div>
                  <dt>Hãng / model</dt>
                  <dd>{{ equipmentBrand(item) }}</dd>
                </div>
                <div>
                  <dt>Phòng ban</dt>
                  <dd>{{ item.departmentName || 'Chưa phân công' }}</dd>
                </div>
                <div>
                  <dt>Vị trí</dt>
                  <dd>{{ item.location || 'Chưa cập nhật' }}</dd>
                </div>
                <div>
                  <dt>Serial</dt>
                  <dd>{{ item.serialNumber || '—' }}</dd>
                </div>
              </dl>
              @if (canManage()) {
                <button
                  type="button"
                  class="secondary management-mobile-action"
                  (click)="edit(item)"
                >
                  Sửa thông tin
                </button>
              }
            </article>
          }
        </section>
        <app-pagination
          [totalItems]="filteredEquipment().length"
          [page]="currentPage"
          [pageSize]="pageSize"
          itemLabel="thiết bị"
          (pageChange)="currentPage = $event"
        />
      } @else {
        <section class="panel management-empty-state">
          <span class="management-empty-icon" aria-hidden="true">▦</span>
          <h3>{{ hasFilters() ? 'Không tìm thấy thiết bị phù hợp' : 'Chưa có thiết bị' }}</h3>
          <p>
            {{
              hasFilters()
                ? 'Hãy thử thay đổi từ khóa hoặc xóa bớt điều kiện lọc.'
                : 'Thêm thiết bị đầu tiên để bắt đầu quản lý tài sản.'
            }}
          </p>
          @if (hasFilters()) {
            <button type="button" class="secondary" (click)="clearFilters()">Xóa bộ lọc</button>
          } @else if (canManage()) {
            <button type="button" class="primary" (click)="openCreate()">Thêm thiết bị</button>
          }
        </section>
      }
    </div>
  `,
})
export class EquipmentPage implements OnInit {
  private api = inject(Api);
  private auth = inject(AuthService);

  readonly equipment = signal<Equipment[]>([]);
  readonly categories = signal<Category[]>([]);
  readonly departments = signal<Department[]>([]);
  readonly showForm = signal(false);
  readonly formError = signal('');
  readonly listError = signal('');
  readonly success = signal('');
  readonly loading = signal(true);
  readonly saving = signal(false);

  query = '';
  statusFilter = '';
  categoryFilter = '';
  departmentFilter = '';
  readonly pageSize = 10;
  currentPage = 0;
  editingId = '';
  model = this.emptyModel();

  ngOnInit() {
    this.load();
    this.api.get<Category[]>('/equipment-categories').subscribe({
      next: (items) => this.categories.set(items.filter((item) => item.active)),
      error: () => this.listError.set('Không thể tải danh mục loại thiết bị.'),
    });
    this.api.get<Department[]>('/departments').subscribe({
      next: (items) => this.departments.set(items.filter((item) => item.active)),
      error: () => this.listError.set('Không thể tải danh sách phòng ban.'),
    });
  }

  canManage() {
    return this.auth.hasAny(['ADMIN', 'MANAGER']);
  }

  load() {
    this.loading.set(true);
    this.listError.set('');
    this.api.get<Page<Equipment>>('/equipment', { size: 500, sort: 'assetCode,asc' }).subscribe({
      next: (page) => {
        this.equipment.set(page.content);
        this.loading.set(false);
      },
      error: (error) => {
        this.listError.set(error.error?.detail ?? 'Không thể tải danh sách thiết bị.');
        this.loading.set(false);
      },
    });
  }

  filteredEquipment() {
    const query = this.normalize(this.query);
    return this.equipment().filter((item) => {
      const searchable = this.normalize(
        [
          item.assetCode,
          item.name,
          item.serialNumber,
          item.manufacturer,
          item.model,
          item.location,
          item.categoryName,
          item.departmentName,
        ].join(' '),
      );
      return (
        (!query || searchable.includes(query)) &&
        (!this.statusFilter || item.status === this.statusFilter) &&
        (!this.categoryFilter || item.categoryId === this.categoryFilter) &&
        (!this.departmentFilter || item.departmentId === this.departmentFilter)
      );
    });
  }

  pagedEquipment() {
    const items = this.filteredEquipment();
    const page = Math.min(
      this.currentPage,
      Math.max(Math.ceil(items.length / this.pageSize) - 1, 0),
    );
    return items.slice(page * this.pageSize, (page + 1) * this.pageSize);
  }

  resetPage() {
    this.currentPage = 0;
  }

  hasFilters() {
    return !!(
      this.query.trim() ||
      this.statusFilter ||
      this.categoryFilter ||
      this.departmentFilter
    );
  }
  clearFilters() {
    this.query = '';
    this.statusFilter = '';
    this.categoryFilter = '';
    this.departmentFilter = '';
    this.resetPage();
  }
  countStatus(status: Equipment['status']) {
    return this.equipment().filter((item) => item.status === status).length;
  }

  openCreate() {
    this.editingId = '';
    this.model = this.emptyModel();
    this.formError.set('');
    this.showForm.set(true);
    this.scrollToTop();
  }

  edit(item: Equipment) {
    this.editingId = item.id;
    this.model = {
      assetCode: item.assetCode,
      name: item.name,
      serialNumber: item.serialNumber ?? '',
      manufacturer: item.manufacturer ?? '',
      model: item.model ?? '',
      location: item.location ?? '',
      categoryId: item.categoryId,
      departmentId: item.departmentId ?? '',
      status: item.status,
      active: item.active,
    };
    this.formError.set('');
    this.showForm.set(true);
    this.scrollToTop();
  }

  save() {
    this.saving.set(true);
    this.formError.set('');
    this.success.set('');
    const body = {
      ...this.model,
      assetCode: this.model.assetCode.trim(),
      name: this.model.name.trim(),
      serialNumber: this.model.serialNumber.trim(),
      manufacturer: this.model.manufacturer.trim(),
      model: this.model.model.trim(),
      location: this.model.location.trim(),
      departmentId: this.model.departmentId || null,
    };
    const request = this.editingId
      ? this.api.put<Equipment>('/equipment/' + this.editingId, body)
      : this.api.post<Equipment>('/equipment', body);
    request.subscribe({
      next: () => {
        const message = this.editingId
          ? 'Đã cập nhật thiết bị thành công.'
          : 'Đã thêm thiết bị mới.';
        this.cancel();
        this.success.set(message);
        this.saving.set(false);
        this.load();
      },
      error: (error) => {
        this.formError.set(error.error?.detail ?? 'Không thể lưu thiết bị. Vui lòng thử lại.');
        this.saving.set(false);
      },
    });
  }

  cancel() {
    this.editingId = '';
    this.model = this.emptyModel();
    this.formError.set('');
    this.showForm.set(false);
  }

  statusLabel(status: string) {
    return (
      (
        {
          ACTIVE: 'Đang hoạt động',
          UNDER_MAINTENANCE: 'Đang bảo trì',
          RETIRED: 'Ngừng sử dụng',
        } as Record<string, string>
      )[status] ?? status
    );
  }

  equipmentBrand(item: Equipment) {
    return (
      [item.manufacturer, item.model].filter(Boolean).join(' · ') || 'Chưa cập nhật hãng / model'
    );
  }

  private normalize(value: string) {
    return value
      .toLocaleLowerCase('vi')
      .normalize('NFD')
      .replace(/[\u0300-\u036f]/g, '')
      .trim();
  }
  private scrollToTop() {
    window.scrollTo({ top: 0, behavior: 'smooth' });
  }
  private emptyModel() {
    return {
      assetCode: '',
      name: '',
      serialNumber: '',
      manufacturer: '',
      model: '',
      location: '',
      categoryId: '',
      departmentId: '',
      status: 'ACTIVE' as Equipment['status'],
      active: true,
    };
  }
}
