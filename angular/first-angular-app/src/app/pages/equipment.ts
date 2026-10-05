import { Component, inject, OnInit, signal } from '@angular/core';
import { HttpEventType } from '@angular/common/http';
import { FormsModule } from '@angular/forms';
import { Api } from '../core/api';
import { AuthService } from '../core/auth';
import { Customer, CustomerSite, Equipment, EquipmentDocument, EquipmentDocumentType, Page, PreviewUrl } from '../core/models';
import { PaginationComponent } from '../shared/pagination';

interface Category {
  id: string;
  code: string;
  name: string;
  description?: string;
  active: boolean;
}

interface DocumentUpload { file: File; progress: number; status: 'pending' | 'uploading' | 'done' | 'failed'; error?: string; }

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
                  <span>Khách hàng <i>*</i></span>
                  <select name="customerId" [(ngModel)]="model.customerId" (ngModelChange)="model.siteId = ''" required>
                    <option value="">Chọn khách hàng</option>
                    @for (customer of customers(); track customer.id) {
                      <option [value]="customer.id">{{ customer.name }}</option>
                    }
                  </select>
                </label>
                <label>
                  <span>Địa điểm lắp đặt <i>*</i></span>
                  <select name="siteId" [(ngModel)]="model.siteId" required>
                    <option value="">Chọn địa điểm</option>
                    @for (site of formSites(); track site.id) {
                      <option [value]="site.id">{{ site.name }}</option>
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
                <label><span>Ngày lắp đặt</span><input type="date" name="installedAt" [(ngModel)]="model.installedAt" /></label>
                <label><span>Ngày nghiệm thu</span><input type="date" name="acceptedAt" [(ngModel)]="model.acceptedAt" /></label>
                <label><span>Bắt đầu bảo hành</span><input type="date" name="warrantyStartAt" [(ngModel)]="model.warrantyStartAt" /></label>
                <label><span>Kết thúc bảo hành</span><input type="date" name="warrantyEndAt" [(ngModel)]="model.warrantyEndAt" /></label>
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

      @if (documentEquipment(); as selected) {
        <section class="panel equipment-documents-panel">
          <div class="management-form-head">
            <div><span class="management-form-icon">TL</span><div><h2>Tài liệu · {{ selected.name }}</h2><p>{{ selected.assetCode }} · Biên bản, hóa đơn và tài liệu kỹ thuật</p></div></div>
            <button type="button" class="secondary" (click)="closeDocuments()">Đóng</button>
          </div>
          @if (canManage()) {
            <div class="equipment-document-upload">
              <select [(ngModel)]="documentType"><option value="REPORT">Biên bản</option><option value="INVOICE">Hóa đơn</option><option value="DEVICE_DOCUMENT">Tài liệu thiết bị</option><option value="OTHER">Khác</option></select>
              <label class="upload ticket-file-drop compact-file-drop" (dragover)="$event.preventDefault()" (drop)="dropDocuments($event)">+ Chọn hoặc kéo thả tối đa 10 tệp<input type="file" multiple accept=".jpg,.jpeg,.png,.webp,.mp4,.webm,.pdf,.doc,.docx,.xls,.xlsx" (change)="uploadDocuments($event)" /></label>
            </div>
          }
          @if (documentError()) { <div class="alert">{{ documentError() }}</div> }
          @for (upload of documentUploads(); track upload.file.name + upload.file.lastModified; let i = $index) { <div class="ticket-upload-row"><div><b>{{ upload.file.name }}</b><small>{{ upload.status === 'failed' ? upload.error : upload.status }}</small></div><progress max="100" [value]="upload.progress"></progress>@if (upload.status === 'failed') { <button class="link-button" (click)="retryDocument(i)">Thử lại</button> }</div> }
          <div class="equipment-document-list">
            @for (doc of equipmentDocuments(); track doc.id) {
              <div class="file-row"><span>▤</span><button class="file-main" (click)="openDocument(doc)"><b>{{ doc.originalName }}</b><small>{{ documentTypeLabel(doc.type) }} · {{ doc.uploadedByName }}</small></button><button class="link-button" (click)="downloadDocument(doc)">Tải</button>@if (doc.canDelete) { <button class="link-button danger-link" (click)="deleteDocument(doc)">Xóa</button> }</div>
            } @empty { <p class="empty">Chưa có tài liệu thiết bị.</p> }
          </div>
        </section>
      }

      <section class="panel management-filter-panel">
        <div class="management-filter-head">
          <div>
            <h2>Danh sách thiết bị</h2>
            <p>Tìm nhanh và lọc theo trạng thái, loại thiết bị, khách hàng hoặc địa điểm.</p>
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
            <span>Khách hàng</span>
            <select name="equipmentCustomer" [(ngModel)]="customerFilter" (ngModelChange)="siteFilter = ''; resetPage()">
              <option value="">Tất cả khách hàng</option>
              @for (customer of customers(); track customer.id) { <option [value]="customer.id">{{ customer.name }}</option> }
            </select>
          </label>
          <label>
            <span>Địa điểm</span>
            <select
              name="equipmentSite"
              [(ngModel)]="siteFilter"
              (ngModelChange)="resetPage()"
            >
              <option value="">Tất cả địa điểm</option>
              @for (site of filteredSites(); track site.id) {
                <option [value]="site.id">{{ site.name }}</option>
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
                <th>Khách hàng / địa điểm</th>
                <th>Serial</th>
                <th>Trạng thái</th>
                @if (canManage() || canViewDocuments()) {
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
                      item.customerName || 'Chưa có khách hàng'
                    }}</b
                    ><small>{{ item.siteName }} · {{ item.location || 'Chưa cập nhật vị trí' }}</small>
                  </td>
                  <td>
                    <span class="management-serial">{{ item.serialNumber || '—' }}</span>
                  </td>
                  <td>
                    <span [class]="'status ' + item.status.toLowerCase()">{{
                      statusLabel(item.status)
                    }}</span>
                    <small>{{ warrantyLabel(item.warrantyStatus) }}</small>
                    @if (!item.active) {
                      <small class="management-inactive-note">Ngừng quản lý</small>
                    }
                  </td>
                  @if (canManage() || canViewDocuments()) {
                    <td class="management-action-cell">
                      @if (canManage()) { <button
                        type="button"
                        class="management-icon-button"
                        title="Sửa thiết bị"
                        (click)="edit(item)"
                      >
                        <span aria-hidden="true">✎</span><span>Sửa</span>
                      </button> }
                      @if (canViewDocuments()) { <button type="button" class="management-icon-button" title="Tài liệu thiết bị" (click)="openDocuments(item)"><span aria-hidden="true">▤</span><span>Tài liệu</span></button> }
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
                  <dt>Khách hàng</dt>
                  <dd>{{ item.customerName }}</dd>
                </div>
                <div>
                  <dt>Địa điểm</dt>
                  <dd>{{ item.siteName }}</dd>
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
              @if (canManage() || canViewDocuments()) {
                @if (canManage()) { <button
                  type="button"
                  class="secondary management-mobile-action"
                  (click)="edit(item)"
                >
                  Sửa thông tin
                </button> }
                @if (canViewDocuments()) { <button type="button" class="secondary management-mobile-action" (click)="openDocuments(item)">Tài liệu</button> }
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
      @if (documentPreviewUrl()) { <div class="file-preview-backdrop" (click)="closeDocumentPreview()"><div class="file-preview" (click)="$event.stopPropagation()"><button class="file-preview-close" (click)="closeDocumentPreview()">×</button>@if (documentPreviewKind() === 'IMAGE') { <img [src]="documentPreviewUrl()" alt="Xem trước tài liệu" /> }@if (documentPreviewKind() === 'VIDEO') { <video [src]="documentPreviewUrl()" controls autoplay></video> }</div></div> }
    </div>
  `,
})
export class EquipmentPage implements OnInit {
  private api = inject(Api);
  private auth = inject(AuthService);

  readonly equipment = signal<Equipment[]>([]);
  readonly categories = signal<Category[]>([]);
  readonly sites = signal<CustomerSite[]>([]);
  readonly customers = signal<Customer[]>([]);
  readonly showForm = signal(false);
  readonly formError = signal('');
  readonly listError = signal('');
  readonly success = signal('');
  readonly loading = signal(true);
  readonly saving = signal(false);
  readonly documentEquipment = signal<Equipment | null>(null);
  readonly equipmentDocuments = signal<EquipmentDocument[]>([]);
  readonly documentUploads = signal<DocumentUpload[]>([]);
  readonly documentError = signal('');
  readonly documentPreviewUrl = signal('');
  readonly documentPreviewKind = signal('');
  documentType: EquipmentDocumentType = 'DEVICE_DOCUMENT';

  query = '';
  statusFilter = '';
  categoryFilter = '';
  siteFilter = '';
  customerFilter = '';
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
    this.api.get<CustomerSite[]>('/customer-sites').subscribe({
      next: (items) => this.sites.set(items.filter((item) => item.active)),
      error: () => this.listError.set('Không thể tải danh sách địa điểm.'),
    });
    this.api.get<Customer[]>('/customers').subscribe((items) => this.customers.set(items.filter((item) => item.active)));
  }

  canManage() {
    return this.auth.hasAny(['ADMIN', 'MANAGER']);
  }
  canViewDocuments() { return this.auth.hasAny(['ADMIN', 'MANAGER', 'TECHNICIAN']); }

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
          item.customerName,
          item.siteName,
        ].join(' '),
      );
      return (
        (!query || searchable.includes(query)) &&
        (!this.statusFilter || item.status === this.statusFilter) &&
        (!this.categoryFilter || item.categoryId === this.categoryFilter) &&
        (!this.customerFilter || item.customerId === this.customerFilter) &&
        (!this.siteFilter || item.siteId === this.siteFilter)
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
      this.customerFilter ||
      this.siteFilter
    );
  }
  clearFilters() {
    this.query = '';
    this.statusFilter = '';
    this.categoryFilter = '';
    this.customerFilter = '';
    this.siteFilter = '';
    this.resetPage();
  }
  countStatus(status: Equipment['status']) {
    return this.equipment().filter((item) => item.status === status).length;
  }
  filteredSites() { return this.sites().filter((item) => !this.customerFilter || item.customerId === this.customerFilter); }
  formSites() { return this.sites().filter((item) => !this.model.customerId || item.customerId === this.model.customerId); }

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
      customerId: item.customerId,
      siteId: item.siteId,
      installedAt: this.dateInput(item.installedAt),
      acceptedAt: this.dateInput(item.acceptedAt),
      warrantyStartAt: this.dateInput(item.warrantyStartAt),
      warrantyEndAt: this.dateInput(item.warrantyEndAt),
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
      siteId: this.model.siteId,
      installedAt: this.instant(this.model.installedAt),
      acceptedAt: this.instant(this.model.acceptedAt),
      warrantyStartAt: this.instant(this.model.warrantyStartAt),
      warrantyEndAt: this.instant(this.model.warrantyEndAt),
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

  openDocuments(item: Equipment) { this.documentEquipment.set(item); this.documentError.set(''); this.loadDocuments(); setTimeout(() => document.querySelector('.equipment-documents-panel')?.scrollIntoView({ behavior: 'smooth' })); }
  closeDocuments() { this.documentEquipment.set(null); this.equipmentDocuments.set([]); this.documentUploads.set([]); }
  loadDocuments() { const item=this.documentEquipment(); if(item)this.api.get<EquipmentDocument[]>(`/equipment/${item.id}/documents`).subscribe({next:(docs)=>this.equipmentDocuments.set(docs),error:(e)=>this.documentError.set(e.error?.detail??'Không thể tải tài liệu thiết bị.')}); }
  uploadDocuments(event: Event) { const input=event.target as HTMLInputElement;this.queueDocuments(Array.from(input.files??[]));input.value=''; }
  dropDocuments(event:DragEvent){event.preventDefault();this.queueDocuments(Array.from(event.dataTransfer?.files??[]));}
  private queueDocuments(incoming:File[]){const selected=incoming.slice(0,10);const files=selected.filter((file)=>!this.documentFileError(file));const invalid=selected.find((file)=>this.documentFileError(file));this.documentError.set(incoming.length>10?'Chỉ tải tối đa 10 tệp mỗi lần.':invalid?this.documentFileError(invalid):'');this.documentUploads.set(files.map((file)=>({file,progress:0,status:'pending'})));this.uploadNextDocument();}
  private uploadNextDocument(){const item=this.documentEquipment();const index=this.documentUploads().findIndex((upload)=>upload.status==='pending');if(!item||index<0){this.loadDocuments();return;}this.updateDocumentUpload(index,{status:'uploading'});const upload=this.documentUploads()[index];this.api.uploadWithProgress(`/equipment/${item.id}/documents`,upload.file,{type:this.documentType}).subscribe({next:(event)=>{if(event.type===HttpEventType.UploadProgress)this.updateDocumentUpload(index,{progress:event.total?Math.round(event.loaded*100/event.total):0});if(event.type===HttpEventType.Response){this.updateDocumentUpload(index,{status:'done',progress:100});this.uploadNextDocument();}},error:(e)=>{this.updateDocumentUpload(index,{status:'failed',error:e.error?.detail??'Tải thất bại'});this.documentError.set('Một số tài liệu tải lên thất bại.');this.uploadNextDocument();}});}
  private updateDocumentUpload(index:number,changes:Partial<DocumentUpload>){this.documentUploads.update((items)=>items.map((item,i)=>i===index?{...item,...changes}:item));}
  retryDocument(index:number){this.updateDocumentUpload(index,{status:'pending',progress:0,error:undefined});this.uploadNextDocument();}
  private documentFileError(file:File){const ext=file.name.split('.').pop()?.toLowerCase()??'';const images=['jpg','jpeg','png','webp'],videos=['mp4','webm'],docs=['pdf','doc','docx','xls','xlsx'];if(![...images,...videos,...docs].includes(ext))return`${file.name}: định dạng không được hỗ trợ.`;const max=images.includes(ext)?10:videos.includes(ext)?200:25;return file.size>max*1024*1024?`${file.name}: vượt quá ${max} MB.`:'';}
  openDocument(doc:EquipmentDocument){if(doc.previewKind==='DOWNLOAD'){this.downloadDocument(doc);return;}this.api.get<PreviewUrl>(`/equipment-documents/${doc.id}/preview-url`).subscribe((preview)=>{if(doc.previewKind==='PDF')window.open(preview.url,'_blank','noopener');else{this.documentPreviewUrl.set(preview.url);this.documentPreviewKind.set(doc.previewKind);}});}
  closeDocumentPreview(){this.documentPreviewUrl.set('');this.documentPreviewKind.set('');}
  downloadDocument(doc:EquipmentDocument){this.api.download(`/equipment-documents/${doc.id}`).subscribe((blob)=>{const url=URL.createObjectURL(blob);const link=document.createElement('a');link.href=url;link.download=doc.originalName;link.click();URL.revokeObjectURL(url);});}
  deleteDocument(doc:EquipmentDocument){if(confirm(`Xóa tài liệu ${doc.originalName}?`))this.api.delete(`/equipment-documents/${doc.id}`).subscribe(()=>this.loadDocuments());}
  documentTypeLabel(type:EquipmentDocumentType){return({REPORT:'Biên bản',INVOICE:'Hóa đơn',DEVICE_DOCUMENT:'Tài liệu thiết bị',OTHER:'Khác'} as Record<EquipmentDocumentType,string>)[type];}

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
  warrantyLabel(status:string){return ({IN_WARRANTY:'Còn bảo hành',OUT_OF_WARRANTY:'Hết bảo hành',UNKNOWN:'Chưa rõ bảo hành'} as Record<string,string>)[status]??status;}

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
      customerId: '',
      siteId: '',
      installedAt: '',
      acceptedAt: '',
      warrantyStartAt: '',
      warrantyEndAt: '',
      status: 'ACTIVE' as Equipment['status'],
      active: true,
    };
  }
  private dateInput(value?:string){return value?value.slice(0,10):'';}
  private instant(value:string){return value?`${value}T00:00:00Z`:null;}
}
