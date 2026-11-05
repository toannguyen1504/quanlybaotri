import { DecimalPipe } from '@angular/common';
import { Component, DestroyRef, inject, OnInit, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { Api } from '../../core/api';
import { AuthService } from '../../core/auth';
import { Page, Part } from '../../core/models';
import { PaginationComponent } from '../../shared/pagination';
import { AccessibleDialog } from '../../shared/accessible-dialog';

type StockFilter = '' | 'AVAILABLE' | 'LOW' | 'OUT';
type PartStatusFilter = '' | 'ACTIVE' | 'INACTIVE';
type MovementMode = 'RECEIVE' | 'ADJUST';

@Component({
  selector: 'app-parts',
  imports: [FormsModule, DecimalPipe, PaginationComponent, AccessibleDialog],
  templateUrl: './parts.html',
})
export class PartsPage implements OnInit {
  private api = inject(Api);
  private auth = inject(AuthService);
  private route = inject(ActivatedRoute);
  private router = inject(Router);
  private destroyRef = inject(DestroyRef);

  readonly parts = signal<Part[]>([]);
  readonly result = signal<Page<Part> | null>(null);
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
    const fixedStock = (this.route.snapshot.data['stockStatus'] ?? '') as StockFilter;
    this.route.queryParamMap.pipe(takeUntilDestroyed(this.destroyRef)).subscribe((params) => {
      this.query = params.get('q') ?? '';
      this.stockFilter = fixedStock || this.validStock(params.get('stockStatus'));
      this.statusFilter = this.validStatus(params.get('status'));
      this.currentPage = Math.max(Number(params.get('page') ?? 1) - 1, 0);
      this.load();
    });
  }
  manager() {
    return this.auth.hasAny(['ADMIN', 'MANAGER']);
  }

  load() {
    this.loading.set(true);
    this.listError.set('');
    this.api
      .get<Page<Part>>('/parts', {
        q: this.query.trim(),
        stockStatus: this.stockFilter,
        active:
          this.statusFilter === 'ACTIVE'
            ? true
            : this.statusFilter === 'INACTIVE'
              ? false
              : undefined,
        page: this.currentPage,
        size: this.pageSize,
        sort: 'code,asc',
      })
      .subscribe({
        next: (page) => {
          this.result.set(page);
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
    return this.parts();
  }

  pagedParts() {
    return this.filteredParts();
  }

  resetPage() {
    this.applyFilters();
  }

  applyFilters(page = 0) {
    void this.router.navigate([], {
      relativeTo: this.route,
      queryParams: {
        q: this.query.trim() || null,
        stockStatus: this.route.snapshot.data['stockStatus'] ? null : this.stockFilter || null,
        status: this.statusFilter || null,
        page: page ? page + 1 : null,
      },
    });
  }

  goToPage(page: number) {
    this.applyFilters(page);
  }

  hasFilters() {
    return !!(this.query.trim() || this.stockFilter || this.statusFilter);
  }
  clearFilters() {
    this.query = '';
    this.stockFilter = '';
    this.statusFilter = '';
    this.applyFilters();
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

  private validStock(value: string | null): StockFilter {
    return ['AVAILABLE', 'LOW', 'OUT'].includes(value ?? '') ? (value as StockFilter) : '';
  }
  private validStatus(value: string | null): PartStatusFilter {
    return ['ACTIVE', 'INACTIVE'].includes(value ?? '') ? (value as PartStatusFilter) : '';
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
