import { DecimalPipe } from '@angular/common';
import { Component, inject, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Api } from '../core/api';
import { AuthService } from '../core/auth';
import { Page, Part } from '../core/models';
import { PaginationComponent } from '../shared/pagination';

type StockFilter = '' | 'AVAILABLE' | 'LOW' | 'OUT';
type PartStatusFilter = '' | 'ACTIVE' | 'INACTIVE';
type MovementMode = 'RECEIVE' | 'ADJUST';

@Component({
  selector: 'app-parts',
  imports: [FormsModule, DecimalPipe, PaginationComponent],
  template: `
    <div class="page management-page inventory-page">
      <header class="page-head management-page-head">
        <div>
          <p class="eyebrow">QUẢN LÝ KHO</p>
          <h1>Linh kiện</h1>
          <p class="muted">Kiểm soát tồn kho, định mức cảnh báo và giá trị linh kiện bảo trì.</p>
        </div>
        <div class="management-head-actions">
          <button type="button" class="secondary" [disabled]="loading()" (click)="load()">
            <span aria-hidden="true">↻</span>
            {{ loading() ? 'Đang tải' : 'Làm mới' }}
          </button>
          @if (manager()) {
            <button type="button" class="primary" (click)="openCreate()">
              <span aria-hidden="true">＋</span> Thêm linh kiện
            </button>
          }
        </div>
      </header>

      <section class="management-stats" aria-label="Tổng quan tồn kho">
        <article>
          <span class="management-stat-icon neutral" aria-hidden="true">▦</span>
          <div>
            <span>Tổng mã hàng</span><b>{{ parts().length }}</b
            ><small>Danh mục linh kiện</small>
          </div>
        </article>
        <article>
          <span class="management-stat-icon success" aria-hidden="true">✓</span>
          <div>
            <span>Đang sử dụng</span><b>{{ activeCount() }}</b
            ><small>Mã hàng khả dụng</small>
          </div>
        </article>
        <article>
          <span class="management-stat-icon warning" aria-hidden="true">!</span>
          <div>
            <span>Sắp hết hàng</span><b>{{ lowCount() }}</b
            ><small>Chạm mức tối thiểu</small>
          </div>
        </article>
        <article>
          <span class="management-stat-icon value" aria-hidden="true">₫</span>
          <div>
            <span>Giá trị ước tính</span><b>{{ inventoryValue() | number: '1.0-0' }} ₫</b
            ><small>Theo đơn giá mặc định</small>
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
              <span class="management-form-icon part" aria-hidden="true">◇</span>
              <div>
                <h2>{{ editingId ? 'Cập nhật linh kiện' : 'Thêm linh kiện mới' }}</h2>
                <p>Thiết lập thông tin cơ bản, định mức cảnh báo và đơn giá tham chiếu.</p>
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
                <div>
                  <b>Thông tin linh kiện</b
                  ><small>Dùng để nhận diện trong kho và phiếu bảo trì</small>
                </div>
              </div>
              <div class="form-grid">
                <label>
                  <span>Mã linh kiện <i>*</i></span>
                  <input
                    name="code"
                    [(ngModel)]="model.code"
                    required
                    maxlength="80"
                    placeholder="Ví dụ: LK-RAM-001"
                  />
                </label>
                <label>
                  <span>Tên linh kiện <i>*</i></span>
                  <input
                    name="name"
                    [(ngModel)]="model.name"
                    required
                    maxlength="200"
                    placeholder="Ví dụ: RAM DDR4 16GB"
                  />
                </label>
                <label>
                  <span>Đơn vị tính <i>*</i></span>
                  <input
                    name="unit"
                    [(ngModel)]="model.unit"
                    required
                    maxlength="50"
                    placeholder="Cái, bộ, mét..."
                  />
                </label>
                <label>
                  <span>Đơn giá mặc định</span>
                  <div class="management-money-input">
                    <input
                      name="defaultUnitCost"
                      type="number"
                      min="0"
                      step="1000"
                      [(ngModel)]="model.defaultUnitCost"
                    />
                    <span>VNĐ</span>
                  </div>
                </label>
              </div>
            </div>

            <div class="management-form-section compact">
              <div class="management-section-title">
                <span>02</span>
                <div>
                  <b>Kiểm soát tồn kho</b><small>Cảnh báo khi số lượng chạm ngưỡng này</small>
                </div>
              </div>
              <div class="management-stock-setting">
                <label>
                  <span>Tồn kho tối thiểu <i>*</i></span>
                  <input
                    name="minimumStock"
                    type="number"
                    min="0"
                    step="0.001"
                    required
                    [(ngModel)]="model.minimumStock"
                  />
                </label>
                <label class="management-checkbox">
                  <input name="active" type="checkbox" [(ngModel)]="model.active" />
                  <span
                    ><b>Đang sử dụng</b
                    ><small>Cho phép chọn linh kiện trong các nghiệp vụ kho</small></span
                  >
                </label>
              </div>
            </div>

            @if (formError()) {
              <div class="alert" role="alert">{{ formError() }}</div>
            }
            <div class="management-form-actions">
              <small><i>*</i> Thông tin bắt buộc</small>
              <div class="actions">
                <button
                  type="button"
                  class="secondary"
                  [disabled]="saving()"
                  (click)="cancelForm()"
                >
                  Hủy
                </button>
                <button class="primary" [disabled]="saving()">
                  {{ saving() ? 'Đang lưu...' : editingId ? 'Lưu thay đổi' : 'Tạo linh kiện' }}
                </button>
              </div>
            </div>
          </form>
        </section>
      }

      <section class="panel management-filter-panel">
        <div class="management-filter-head">
          <div>
            <h2>Danh sách linh kiện</h2>
            <p>Theo dõi số lượng và phát hiện sớm các mã hàng cần bổ sung.</p>
          </div>
          @if (hasFilters()) {
            <button type="button" class="management-clear-filter" (click)="clearFilters()">
              Xóa bộ lọc
            </button>
          }
        </div>
        <div class="management-filters parts-filters">
          <label class="management-search-field">
            <span>Tìm kiếm</span>
            <div>
              <span aria-hidden="true">⌕</span
              ><input
                name="partQuery"
                [(ngModel)]="query"
                (ngModelChange)="resetPage()"
                placeholder="Mã, tên hoặc đơn vị tính..."
              />
            </div>
          </label>
          <label>
            <span>Tình trạng tồn</span>
            <select name="stockFilter" [(ngModel)]="stockFilter" (ngModelChange)="resetPage()">
              <option value="">Tất cả mức tồn</option>
              <option value="AVAILABLE">Còn hàng ổn định</option>
              <option value="LOW">Sắp hết hàng</option>
              <option value="OUT">Đã hết hàng</option>
            </select>
          </label>
          <label>
            <span>Trạng thái</span>
            <select
              name="partStatusFilter"
              [(ngModel)]="statusFilter"
              (ngModelChange)="resetPage()"
            >
              <option value="">Tất cả trạng thái</option>
              <option value="ACTIVE">Đang sử dụng</option>
              <option value="INACTIVE">Ngừng sử dụng</option>
            </select>
          </label>
        </div>
        <div class="management-result-line">
          <span
            >Hiển thị <b>{{ filteredParts().length }}</b> trên {{ parts().length }} mã linh
            kiện</span
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
            <h3>Không thể tải danh sách linh kiện</h3>
            <p>{{ listError() }}</p>
          </div>
          <button type="button" class="secondary" (click)="load()">Thử lại</button>
        </section>
      } @else if (loading()) {
        <section class="panel management-state-card">
          <span class="management-spinner" aria-hidden="true"></span>
          <div>
            <h3>Đang tải dữ liệu kho</h3>
            <p>Vui lòng chờ trong giây lát...</p>
          </div>
        </section>
      } @else if (filteredParts().length) {
        <section class="panel table-wrap management-table-wrap parts-table">
          <table>
            <thead>
              <tr>
                <th>Linh kiện</th>
                <th>Tồn kho</th>
                <th>Mức tối thiểu</th>
                <th>Đơn giá</th>
                <th>Trạng thái</th>
                @if (manager()) {
                  <th class="management-action-column">Thao tác</th>
                }
              </tr>
            </thead>
            <tbody>
              @for (part of pagedParts(); track part.id) {
                <tr [class.low-stock]="isLow(part)">
                  <td>
                    <div class="management-name-cell">
                      <span class="management-item-avatar part" aria-hidden="true">LK</span>
                      <div>
                        <b>{{ part.name }}</b
                        ><small class="code">{{ part.code }}</small>
                      </div>
                    </div>
                  </td>
                  <td>
                    <div class="stock-amount" [class.warning]="isLow(part)">
                      <b>{{ part.currentStock | number: '1.0-3' }}</b
                      ><span>{{ part.unit }}</span>
                    </div>
                  </td>
                  <td>{{ part.minimumStock | number: '1.0-3' }} {{ part.unit }}</td>
                  <td>
                    <b class="management-cell-title"
                      >{{ part.defaultUnitCost | number: '1.0-0' }} ₫</b
                    >
                  </td>
                  <td>
                    <span [class]="'stock-status ' + stockTone(part)">{{ stockLabel(part) }}</span>
                    @if (!part.active) {
                      <small class="management-inactive-note">Ngừng sử dụng</small>
                    }
                  </td>
                  @if (manager()) {
                    <td class="management-action-cell parts-actions">
                      <button
                        type="button"
                        class="management-icon-button receive"
                        title="Nhập kho"
                        (click)="openMovement(part, 'RECEIVE')"
                      >
                        <span aria-hidden="true">↓</span><span>Nhập</span>
                      </button>
                      <button
                        type="button"
                        class="management-icon-button"
                        title="Kiểm kê kho"
                        (click)="openMovement(part, 'ADJUST')"
                      >
                        <span aria-hidden="true">≋</span><span>Kiểm kê</span>
                      </button>
                      <button
                        type="button"
                        class="management-icon-button"
                        title="Sửa linh kiện"
                        (click)="edit(part)"
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
          aria-label="Danh sách linh kiện trên thiết bị di động"
        >
          @for (part of pagedParts(); track part.id) {
            <article class="panel management-mobile-card" [class.low-stock-card]="isLow(part)">
              <div class="management-mobile-card-head">
                <div class="management-name-cell">
                  <span class="management-item-avatar part" aria-hidden="true">LK</span>
                  <div>
                    <b>{{ part.name }}</b
                    ><small class="code">{{ part.code }}</small>
                  </div>
                </div>
                <span [class]="'stock-status ' + stockTone(part)">{{ stockLabel(part) }}</span>
              </div>
              <dl>
                <div>
                  <dt>Tồn kho</dt>
                  <dd>
                    <b>{{ part.currentStock | number: '1.0-3' }}</b> {{ part.unit }}
                  </dd>
                </div>
                <div>
                  <dt>Mức tối thiểu</dt>
                  <dd>{{ part.minimumStock | number: '1.0-3' }} {{ part.unit }}</dd>
                </div>
                <div>
                  <dt>Đơn giá</dt>
                  <dd>{{ part.defaultUnitCost | number: '1.0-0' }} ₫</dd>
                </div>
                <div>
                  <dt>Sử dụng</dt>
                  <dd>{{ part.active ? 'Đang sử dụng' : 'Đã ngừng' }}</dd>
                </div>
              </dl>
              @if (manager()) {
                <div class="management-card-actions">
                  <button type="button" class="primary" (click)="openMovement(part, 'RECEIVE')">
                    Nhập kho
                  </button>
                  <button type="button" class="secondary" (click)="openMovement(part, 'ADJUST')">
                    Kiểm kê
                  </button>
                  <button type="button" class="secondary" (click)="edit(part)">Sửa</button>
                </div>
              }
            </article>
          }
        </section>
        <app-pagination
          [totalItems]="filteredParts().length"
          [page]="currentPage"
          [pageSize]="pageSize"
          itemLabel="linh kiện"
          (pageChange)="currentPage = $event"
        />
      } @else {
        <section class="panel management-empty-state">
          <span class="management-empty-icon" aria-hidden="true">◇</span>
          <h3>{{ hasFilters() ? 'Không tìm thấy linh kiện phù hợp' : 'Kho chưa có linh kiện' }}</h3>
          <p>
            {{
              hasFilters()
                ? 'Hãy thử thay đổi từ khóa hoặc điều kiện lọc.'
                : 'Thêm mã linh kiện đầu tiên để bắt đầu quản lý tồn kho.'
            }}
          </p>
          @if (hasFilters()) {
            <button type="button" class="secondary" (click)="clearFilters()">Xóa bộ lọc</button>
          } @else if (manager()) {
            <button type="button" class="primary" (click)="openCreate()">Thêm linh kiện</button>
          }
        </section>
      }

      @if (movementPart(); as part) {
        <div class="management-modal-backdrop" (click)="closeMovement()">
          <section
            class="management-modal"
            role="dialog"
            aria-modal="true"
            aria-labelledby="movement-title"
            (click)="$event.stopPropagation()"
          >
            <div class="management-modal-head">
              <span
                [class]="
                  'management-modal-icon ' + (movementMode() === 'RECEIVE' ? 'receive' : 'adjust')
                "
                aria-hidden="true"
                >{{ movementMode() === 'RECEIVE' ? '↓' : '≋' }}</span
              >
              <div>
                <p>{{ part.code }}</p>
                <h2 id="movement-title">
                  {{
                    movementMode() === 'RECEIVE' ? 'Nhập kho linh kiện' : 'Cập nhật tồn kho thực tế'
                  }}
                </h2>
                <span>{{ part.name }}</span>
              </div>
              <button
                type="button"
                class="management-modal-close"
                aria-label="Đóng"
                [disabled]="movementSaving()"
                (click)="closeMovement()"
              >
                ×
              </button>
            </div>

            <div class="management-current-stock">
              <span>Tồn kho hiện tại</span
              ><b>{{ part.currentStock | number: '1.0-3' }} {{ part.unit }}</b>
            </div>

            <form class="management-modal-form" (ngSubmit)="submitMovement()">
              @if (movementMode() === 'RECEIVE') {
                <div class="form-grid">
                  <label>
                    <span>Số lượng nhập <i>*</i></span>
                    <input
                      name="quantity"
                      type="number"
                      min="0.001"
                      step="0.001"
                      required
                      [(ngModel)]="movement.quantity"
                    />
                  </label>
                  <label>
                    <span>Đơn giá nhập</span>
                    <div class="management-money-input">
                      <input
                        name="unitCost"
                        type="number"
                        min="0"
                        step="1000"
                        [(ngModel)]="movement.unitCost"
                      />
                      <span>VNĐ</span>
                    </div>
                  </label>
                </div>
              } @else {
                <label>
                  <span>Tồn kho thực tế <i>*</i></span>
                  <input
                    name="newBalance"
                    type="number"
                    min="0"
                    step="0.001"
                    required
                    [(ngModel)]="movement.newBalance"
                  />
                  <small class="field-hint">Nhập số lượng đếm được sau khi kiểm kê.</small>
                </label>
              }
              <label>
                <span>Lý do <i>*</i></span>
                <textarea
                  name="reason"
                  rows="3"
                  maxlength="500"
                  required
                  [(ngModel)]="movement.reason"
                  [placeholder]="
                    movementMode() === 'RECEIVE'
                      ? 'Ví dụ: Nhập bổ sung theo đơn mua hàng...'
                      : 'Ví dụ: Điều chỉnh sau kiểm kê định kỳ...'
                  "
                ></textarea>
                <small class="management-character-count">{{ movement.reason.length }}/500</small>
              </label>
              @if (movementError()) {
                <div class="alert" role="alert">{{ movementError() }}</div>
              }
              <div class="management-modal-actions">
                <button
                  type="button"
                  class="secondary"
                  [disabled]="movementSaving()"
                  (click)="closeMovement()"
                >
                  Hủy
                </button>
                <button class="primary" [disabled]="movementSaving()">
                  {{
                    movementSaving()
                      ? 'Đang xử lý...'
                      : movementMode() === 'RECEIVE'
                        ? 'Xác nhận nhập kho'
                        : 'Cập nhật tồn kho'
                  }}
                </button>
              </div>
            </form>
          </section>
        </div>
      }
    </div>
  `,
})
export class PartsPage implements OnInit {
  private api = inject(Api);
  private auth = inject(AuthService);

  readonly parts = signal<Part[]>([]);
  readonly showForm = signal(false);
  readonly formError = signal('');
  readonly listError = signal('');
  readonly success = signal('');
  readonly loading = signal(true);
  readonly saving = signal(false);
  readonly movementPart = signal<Part | null>(null);
  readonly movementMode = signal<MovementMode>('RECEIVE');
  readonly movementError = signal('');
  readonly movementSaving = signal(false);

  query = '';
  stockFilter: StockFilter = '';
  statusFilter: PartStatusFilter = '';
  readonly pageSize = 10;
  currentPage = 0;
  editingId = '';
  model = this.emptyModel();
  movement = this.emptyMovement();

  ngOnInit() {
    this.load();
  }
  manager() {
    return this.auth.hasAny(['ADMIN', 'MANAGER']);
  }

  load() {
    this.loading.set(true);
    this.listError.set('');
    this.api.get<Page<Part>>('/parts', { size: 500, sort: 'code,asc' }).subscribe({
      next: (page) => {
        this.parts.set(page.content);
        this.loading.set(false);
      },
      error: (error) => {
        this.listError.set(error.error?.detail ?? 'Không thể tải danh sách linh kiện.');
        this.loading.set(false);
      },
    });
  }

  filteredParts() {
    const query = this.normalize(this.query);
    return this.parts().filter((part) => {
      const searchable = this.normalize([part.code, part.name, part.unit].join(' '));
      const stockMatches =
        !this.stockFilter ||
        (this.stockFilter === 'OUT' && Number(part.currentStock) === 0) ||
        (this.stockFilter === 'LOW' && Number(part.currentStock) > 0 && this.isLow(part)) ||
        (this.stockFilter === 'AVAILABLE' && !this.isLow(part));
      const statusMatches =
        !this.statusFilter ||
        (this.statusFilter === 'ACTIVE' && part.active) ||
        (this.statusFilter === 'INACTIVE' && !part.active);
      return (!query || searchable.includes(query)) && stockMatches && statusMatches;
    });
  }

  pagedParts() {
    const items = this.filteredParts();
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
    return !!(this.query.trim() || this.stockFilter || this.statusFilter);
  }
  clearFilters() {
    this.query = '';
    this.stockFilter = '';
    this.statusFilter = '';
    this.resetPage();
  }
  lowCount() {
    return this.parts().filter((part) => this.isLow(part)).length;
  }
  activeCount() {
    return this.parts().filter((part) => part.active).length;
  }
  inventoryValue() {
    return this.parts().reduce(
      (total, part) => total + Number(part.currentStock) * Number(part.defaultUnitCost),
      0,
    );
  }
  isLow(part: Part) {
    return Number(part.currentStock) <= Number(part.minimumStock);
  }
  stockTone(part: Part) {
    if (Number(part.currentStock) === 0) return 'out';
    return this.isLow(part) ? 'low' : 'available';
  }
  stockLabel(part: Part) {
    if (Number(part.currentStock) === 0) return 'Hết hàng';
    return this.isLow(part) ? 'Sắp hết' : 'Còn hàng';
  }

  openCreate() {
    this.editingId = '';
    this.model = this.emptyModel();
    this.formError.set('');
    this.showForm.set(true);
    this.scrollToTop();
  }

  edit(part: Part) {
    this.editingId = part.id;
    this.model = {
      code: part.code,
      name: part.name,
      unit: part.unit,
      minimumStock: Number(part.minimumStock),
      defaultUnitCost: Number(part.defaultUnitCost),
      active: part.active,
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
      code: this.model.code.trim(),
      name: this.model.name.trim(),
      unit: this.model.unit.trim(),
    };
    const request = this.editingId
      ? this.api.put<Part>('/parts/' + this.editingId, body)
      : this.api.post<Part>('/parts', body);
    request.subscribe({
      next: () => {
        const message = this.editingId
          ? 'Đã cập nhật linh kiện thành công.'
          : 'Đã thêm linh kiện mới.';
        this.cancelForm();
        this.success.set(message);
        this.saving.set(false);
        this.load();
      },
      error: (error) => {
        this.formError.set(error.error?.detail ?? 'Không thể lưu linh kiện. Vui lòng thử lại.');
        this.saving.set(false);
      },
    });
  }

  cancelForm() {
    this.editingId = '';
    this.model = this.emptyModel();
    this.formError.set('');
    this.showForm.set(false);
  }

  openMovement(part: Part, mode: MovementMode) {
    this.movementPart.set(part);
    this.movementMode.set(mode);
    this.movementError.set('');
    this.movement = {
      quantity: 1,
      unitCost: Number(part.defaultUnitCost),
      newBalance: Number(part.currentStock),
      reason: mode === 'RECEIVE' ? 'Nhập bổ sung kho' : 'Kiểm kê kho',
    };
  }

  closeMovement() {
    if (this.movementSaving()) return;
    this.movementPart.set(null);
    this.movementError.set('');
    this.movement = this.emptyMovement();
  }

  submitMovement() {
    const part = this.movementPart();
    if (!part) return;
    const reason = this.movement.reason.trim();
    if (!reason) {
      this.movementError.set('Vui lòng nhập lý do thực hiện.');
      return;
    }
    if (this.movementMode() === 'RECEIVE' && !(Number(this.movement.quantity) > 0)) {
      this.movementError.set('Số lượng nhập phải lớn hơn 0.');
      return;
    }
    if (this.movementMode() === 'ADJUST' && Number(this.movement.newBalance) < 0) {
      this.movementError.set('Tồn kho thực tế không được nhỏ hơn 0.');
      return;
    }

    this.movementSaving.set(true);
    this.movementError.set('');
    this.success.set('');
    const request =
      this.movementMode() === 'RECEIVE'
        ? this.api.post<Part>('/parts/' + part.id + '/receive', {
            quantity: Number(this.movement.quantity),
            unitCost: Number(this.movement.unitCost),
            reason,
          })
        : this.api.post<Part>('/parts/' + part.id + '/adjust', {
            newBalance: Number(this.movement.newBalance),
            reason,
          });
    request.subscribe({
      next: () => {
        const message =
          this.movementMode() === 'RECEIVE'
            ? `Đã nhập kho ${part.name} thành công.`
            : `Đã cập nhật tồn kho ${part.name}.`;
        this.movementSaving.set(false);
        this.closeMovement();
        this.success.set(message);
        this.load();
      },
      error: (error) => {
        this.movementError.set(
          error.error?.detail ?? 'Không thể cập nhật tồn kho. Vui lòng thử lại.',
        );
        this.movementSaving.set(false);
      },
    });
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
    return { code: '', name: '', unit: 'cái', minimumStock: 0, defaultUnitCost: 0, active: true };
  }
  private emptyMovement() {
    return { quantity: 1, unitCost: 0, newBalance: 0, reason: '' };
  }
}
