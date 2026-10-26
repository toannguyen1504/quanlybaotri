import { DatePipe } from '@angular/common';
import { Component, DestroyRef, inject, OnInit, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { Api } from '../../core/api';
import { AuthService } from '../../core/auth';
import {
  Customer,
  CustomerSite,
  Equipment,
  EquipmentListTab,
  EquipmentSort,
  EquipmentStatus,
  EquipmentSummary,
  Page,
  WarrantyStatus,
} from '../../core/models';
import { PaginationComponent } from '../../shared/pagination';

interface Category {
  id: string;
  name: string;
  active: boolean;
}

type WarrantyBucket = 'ALL' | 'IN_WARRANTY' | 'EXPIRING' | 'OUT_OF_WARRANTY' | 'UNKNOWN';

@Component({
  selector: 'app-equipment',
  imports: [DatePipe, FormsModule, RouterLink, PaginationComponent],
  templateUrl: './equipment.html',
  styleUrl: './equipment.css',
})
export class EquipmentPage implements OnInit {
  private readonly api = inject(Api);
  private readonly auth = inject(AuthService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);

  readonly result = signal<Page<Equipment> | null>(null);
  readonly summary = signal<EquipmentSummary | null>(null);
  readonly categories = signal<Category[]>([]);
  readonly sites = signal<CustomerSite[]>([]);
  readonly customers = signal<Customer[]>([]);
  readonly loading = signal(true);
  readonly summaryLoading = signal(true);
  readonly error = signal('');
  readonly advancedFiltersOpen = signal(false);
  readonly pageSize = 10;
  readonly mode = (this.route.snapshot?.data?.['mode'] ?? 'registry') as
    'registry' | 'warranty' | 'maintenance';

  q = '';
  tab: EquipmentListTab = 'ALL';
  categoryId = '';
  customerId = '';
  siteId = '';
  warrantyStatus: WarrantyStatus | '' = '';
  warrantyBucket: WarrantyBucket = 'ALL';
  sort: EquipmentSort = 'assetCode,asc';
  currentPage = 0;

  readonly tabs: {
    value: EquipmentListTab;
    label: string;
    hint: string;
    key: keyof EquipmentSummary;
  }[] = [
    { value: 'ALL', label: 'Tất cả', hint: 'Toàn bộ tài sản', key: 'total' },
    { value: 'ACTIVE', label: 'Đang hoạt động', hint: 'Sẵn sàng sử dụng', key: 'active' },
    {
      value: 'UNDER_MAINTENANCE',
      label: 'Đang bảo trì',
      hint: 'Cần theo dõi',
      key: 'underMaintenance',
    },
    { value: 'RETIRED', label: 'Ngừng sử dụng', hint: 'Đã dừng vận hành', key: 'retired' },
  ];

  readonly sorts: { value: EquipmentSort; label: string }[] = [
    { value: 'assetCode,asc', label: 'Mã thiết bị A–Z' },
    { value: 'name,asc', label: 'Tên thiết bị A–Z' },
    { value: 'status,asc', label: 'Theo trạng thái' },
    { value: 'warrantyEndAt,asc', label: 'Bảo hành sắp hết' },
  ];

  ngOnInit() {
    this.loadLookups();
    this.route.queryParamMap.pipe(takeUntilDestroyed(this.destroyRef)).subscribe((params) => {
      this.q = params.get('q') ?? '';
      this.tab =
        this.mode === 'maintenance' ? 'UNDER_MAINTENANCE' : this.validTab(params.get('tab'));
      this.categoryId = params.get('categoryId') ?? '';
      this.customerId = params.get('customerId') ?? '';
      this.siteId = params.get('siteId') ?? '';
      this.warrantyStatus = this.validWarranty(params.get('warrantyStatus'));
      this.warrantyBucket = this.validWarrantyBucket(params.get('warranty'));
      this.sort =
        this.mode === 'warranty' && !params.get('sort')
          ? 'warrantyEndAt,asc'
          : this.validSort(params.get('sort'));
      this.currentPage = this.parsePage(params.get('page'));
      if (this.hasAdvancedFilters() || this.mode === 'warranty') this.advancedFiltersOpen.set(true);
      this.loadEquipment();
      this.loadSummary();
    });
  }

  canManage() {
    return this.auth.hasAny(['ADMIN', 'MANAGER']);
  }

  loadEquipment() {
    this.loading.set(true);
    this.error.set('');
    const warrantyWindow = this.warrantyWindow();
    const selectedWarranty =
      this.mode === 'warranty' ? this.bucketWarrantyStatus() : this.warrantyStatus;
    this.api
      .get<Page<Equipment>>('/equipment', {
        q: this.q.trim(),
        status: this.tab === 'ALL' ? '' : this.tab,
        categoryId: this.categoryId,
        customerId: this.customerId,
        siteId: this.siteId,
        warrantyStatus: selectedWarranty,
        ...(warrantyWindow.from
          ? { warrantyEndFrom: warrantyWindow.from, warrantyEndTo: warrantyWindow.to }
          : {}),
        page: this.currentPage,
        size: this.pageSize,
        sort: this.sort,
      })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (page) => {
          this.result.set(page);
          this.loading.set(false);
          if (page.totalPages > 0 && this.currentPage >= page.totalPages)
            this.navigate(page.totalPages - 1);
        },
        error: (response) => {
          this.result.set(null);
          this.error.set(
            response.error?.detail ??
              response.error?.message ??
              'Không thể tải danh sách thiết bị.',
          );
          this.loading.set(false);
        },
      });
  }

  loadSummary() {
    this.summaryLoading.set(true);
    this.api
      .get<EquipmentSummary>('/equipment/summary', this.nonStatusFilters())
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (summary) => {
          this.summary.set(summary);
          this.summaryLoading.set(false);
        },
        error: () => {
          this.summary.set(null);
          this.summaryLoading.set(false);
        },
      });
  }

  retry() {
    this.loadEquipment();
    this.loadSummary();
  }

  selectTab(tab: EquipmentListTab) {
    if (this.tab === tab) return;
    this.tab = tab;
    this.navigate(0);
  }

  selectWarranty(bucket: WarrantyBucket) {
    if (this.warrantyBucket === bucket) return;
    this.warrantyBucket = bucket;
    this.navigate(0);
  }

  applyFilters() {
    this.navigate(0);
  }

  customerChanged() {
    this.siteId = '';
  }

  clearFilters() {
    this.q = '';
    this.tab = 'ALL';
    this.categoryId = '';
    this.customerId = '';
    this.siteId = '';
    this.warrantyStatus = '';
    this.warrantyBucket = 'ALL';
    this.sort = 'assetCode,asc';
    this.advancedFiltersOpen.set(false);
    this.navigate(0);
  }

  goToPage(page: number) {
    const lastPage = Math.max((this.result()?.totalPages ?? 1) - 1, 0);
    this.navigate(Math.min(Math.max(page, 0), lastPage));
  }

  filteredSites() {
    return this.sites().filter((site) => !this.customerId || site.customerId === this.customerId);
  }

  hasFilters() {
    return (
      !!this.q.trim() ||
      this.tab !== 'ALL' ||
      !!this.categoryId ||
      !!this.customerId ||
      !!this.siteId ||
      !!this.warrantyStatus ||
      this.sort !== 'assetCode,asc'
    );
  }

  hasAdvancedFilters() {
    return !!(this.categoryId || this.customerId || this.siteId || this.warrantyStatus);
  }

  advancedFilterCount() {
    return [this.categoryId, this.customerId, this.siteId, this.warrantyStatus].filter(Boolean)
      .length;
  }

  tabCount(key: keyof EquipmentSummary) {
    return this.summaryLoading() ? '…' : (this.summary()?.[key] ?? '—');
  }

  warrantyCount(bucket: WarrantyBucket) {
    if (this.summaryLoading()) return 'â€¦';
    const value = this.summary();
    if (!value) return 'â€”';
    if (bucket === 'ALL') return value.total;
    if (bucket === 'EXPIRING') return value.warrantyExpiring;
    if (bucket === 'OUT_OF_WARRANTY') return value.outOfWarranty;
    if (bucket === 'UNKNOWN') return value.unknownWarranty;
    return Math.max(value.total - value.outOfWarranty - value.unknownWarranty, 0);
  }

  equipmentBrand(item: Equipment) {
    return [item.manufacturer, item.model].filter(Boolean).join(' · ') || 'Chưa cập nhật';
  }

  statusLabel(status: EquipmentStatus) {
    return {
      ACTIVE: 'Đang hoạt động',
      UNDER_MAINTENANCE: 'Đang bảo trì',
      RETIRED: 'Ngừng sử dụng',
    }[status];
  }

  warrantyLabel(status: WarrantyStatus) {
    return {
      IN_WARRANTY: 'Còn bảo hành',
      OUT_OF_WARRANTY: 'Hết bảo hành',
      UNKNOWN: 'Chưa rõ bảo hành',
    }[status];
  }

  private loadLookups() {
    this.api
      .get<Category[]>('/equipment-categories')
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({ next: (items) => this.categories.set(items.filter((item) => item.active)) });
    this.api
      .get<CustomerSite[]>('/customer-sites')
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({ next: (items) => this.sites.set(items.filter((item) => item.active)) });
    this.api
      .get<Customer[]>('/customers')
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({ next: (items) => this.customers.set(items.filter((item) => item.active)) });
  }

  private nonStatusFilters() {
    return {
      q: this.q.trim(),
      categoryId: this.categoryId,
      customerId: this.customerId,
      siteId: this.siteId,
      warrantyStatus: this.warrantyStatus,
    };
  }

  private navigate(page: number) {
    this.router.navigate([], {
      relativeTo: this.route,
      queryParams: {
        tab: this.tab === 'ALL' ? null : this.tab,
        q: this.q.trim() || null,
        categoryId: this.categoryId || null,
        customerId: this.customerId || null,
        siteId: this.siteId || null,
        warrantyStatus: this.warrantyStatus || null,
        ...(this.mode === 'warranty'
          ? { warranty: this.warrantyBucket !== 'ALL' ? this.warrantyBucket : null }
          : {}),
        sort: this.sort === 'assetCode,asc' ? null : this.sort,
        page: page > 0 ? page + 1 : null,
      },
    });
  }

  private parsePage(value: string | null) {
    const page = Number(value);
    return Number.isInteger(page) && page > 1 ? page - 1 : 0;
  }

  private validTab(value: string | null): EquipmentListTab {
    return this.tabs.some((tab) => tab.value === value) ? (value as EquipmentListTab) : 'ALL';
  }

  private validWarranty(value: string | null): WarrantyStatus | '' {
    return ['IN_WARRANTY', 'OUT_OF_WARRANTY', 'UNKNOWN'].includes(value ?? '')
      ? (value as WarrantyStatus)
      : '';
  }

  private validWarrantyBucket(value: string | null): WarrantyBucket {
    return ['IN_WARRANTY', 'EXPIRING', 'OUT_OF_WARRANTY', 'UNKNOWN'].includes(value ?? '')
      ? (value as WarrantyBucket)
      : 'ALL';
  }

  private bucketWarrantyStatus(): WarrantyStatus | '' {
    return this.warrantyBucket === 'EXPIRING' || this.warrantyBucket === 'ALL'
      ? ''
      : this.warrantyBucket;
  }

  private warrantyWindow() {
    if (this.mode !== 'warranty' || this.warrantyBucket !== 'EXPIRING') return { from: '', to: '' };
    const from = new Date();
    const to = new Date(from);
    to.setDate(to.getDate() + 30);
    return {
      from: from.toLocaleDateString('en-CA'),
      to: to.toLocaleDateString('en-CA'),
    };
  }

  private validSort(value: string | null): EquipmentSort {
    return this.sorts.some((sort) => sort.value === value)
      ? (value as EquipmentSort)
      : 'assetCode,asc';
  }
}
