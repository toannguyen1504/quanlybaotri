import { DatePipe } from '@angular/common';
import { Component, DestroyRef, inject, OnInit, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { Api } from '../../core/api';
import { AuthService } from '../../core/auth';
import { PaginationComponent } from '../../shared/pagination';
import {
  Page,
  Customer,
  CustomerSite,
  Equipment,
  Ticket,
  TicketListView,
  TicketPriority,
  TicketStatus,
  TicketSummary,
  TicketWorkQueue,
  User,
  WarrantyStatus,
} from '../../core/models';

type SlaState = 'ON_TRACK' | 'DUE_SOON' | 'OVERDUE' | 'COMPLETED';
interface EquipmentCategoryOption {
  id: string;
  name: string;
}

@Component({
  selector: 'app-ticket-list',
  imports: [FormsModule, RouterLink, DatePipe, PaginationComponent],
  templateUrl: './ticket-list.html',
})
export class TicketListPage implements OnInit {
  private readonly api = inject(Api);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);
  readonly auth = inject(AuthService);

  readonly page = signal<Page<Ticket> | null>(null);
  readonly summary = signal<TicketSummary | null>(null);
  readonly loading = signal(true);
  readonly summaryLoading = signal(true);
  readonly error = signal('');
  readonly exporting = signal(false);
  readonly exportError = signal('');
  readonly advancedFiltersOpen = signal(false);
  readonly customers = signal<Customer[]>([]);
  readonly sites = signal<CustomerSite[]>([]);
  readonly equipment = signal<Equipment[]>([]);
  readonly equipmentCategories = signal<EquipmentCategoryOption[]>([]);
  readonly technicians = signal<User[]>([]);

  q = '';
  status: TicketStatus | '' = '';
  priority: TicketPriority | '' = '';
  view: TicketListView = 'ALL';
  queue: TicketWorkQueue | '' = '';
  customerId = '';
  siteId = '';
  equipmentId = '';
  equipmentCategoryId = '';
  technicianId = '';
  warrantyStatus: WarrantyStatus | '' = '';
  currentPage = 0;
  readonly pageSize = 10;

  readonly skeletonRows = [1, 2, 3, 4, 5];
  readonly statuses: TicketStatus[] = [
    'SUBMITTED',
    'ACCEPTED',
    'ASSIGNED',
    'IN_PROGRESS',
    'WAITING_PARTS',
    'RESOLVED',
    'CLOSED',
    'REJECTED',
    'CANCELLED',
  ];
  readonly priorities: TicketPriority[] = ['CRITICAL', 'HIGH', 'MEDIUM', 'LOW'];
  readonly views: { value: TicketListView; label: string }[] = [
    { value: 'ALL', label: 'Tất cả SLA' },
    { value: 'OPEN', label: 'Đang mở' },
    { value: 'DUE_SOON', label: 'Sắp quá hạn' },
    { value: 'OVERDUE', label: 'Quá hạn SLA' },
  ];
  readonly queues: { value: TicketWorkQueue; label: string }[] = [
    { value: 'RESPONSE_OVERDUE', label: 'Quá hạn phản hồi' },
    { value: 'RESOLUTION_DUE_SOON', label: 'Sắp quá hạn xử lý' },
    { value: 'RESOLUTION_OVERDUE', label: 'Quá hạn xử lý' },
    { value: 'AWAITING_ACCEPTANCE', label: 'Chờ tiếp nhận' },
    { value: 'AWAITING_ASSIGNMENT', label: 'Chờ phân công' },
    { value: 'AWAITING_START', label: 'Đã phân công, chưa bắt đầu' },
    { value: 'IN_PROGRESS', label: 'Đang xử lý' },
    { value: 'WAITING_PARTS', label: 'Chờ linh kiện' },
    { value: 'AWAITING_CLOSURE', label: 'Chờ xác nhận đóng' },
    { value: 'COST_DRAFT', label: 'Báo giá chưa gửi' },
    { value: 'COST_QUOTED', label: 'Báo giá chờ khách hàng xác nhận' },
  ];
  readonly overviewCards: {
    view: TicketListView;
    key: keyof TicketSummary;
    label: string;
    hint: string;
    icon: string;
  }[] = [
    { view: 'ALL', key: 'total', label: 'Tổng phiếu', hint: 'Trong phạm vi của bạn', icon: '▤' },
    { view: 'OPEN', key: 'open', label: 'Đang mở', hint: 'Chưa kết thúc quy trình', icon: '◷' },
    {
      view: 'DUE_SOON',
      key: 'dueSoon',
      label: 'Sắp quá hạn',
      hint: 'Đã dùng trên 80% SLA',
      icon: '◔',
    },
    { view: 'OVERDUE', key: 'overdue', label: 'Quá hạn SLA', hint: 'Cần ưu tiên xử lý', icon: '!' },
  ];

  ngOnInit() {
    this.loadSummary();
    this.api
      .get<Customer[]>('/customers')
      .subscribe((items) => this.customers.set(items.filter((item) => item.active)));
    this.api
      .get<CustomerSite[]>('/customer-sites')
      .subscribe((items) => this.sites.set(items.filter((item) => item.active)));
    this.api
      .get<Page<Equipment>>('/equipment', { size: 100, sort: 'assetCode,asc' })
      .subscribe((page) => this.equipment.set(page.content));
    this.api
      .get<EquipmentCategoryOption[]>('/equipment-categories')
      .subscribe((items) => this.equipmentCategories.set(items));
    if (this.auth.hasAny(['ADMIN', 'MANAGER'])) {
      this.api
        .get<Page<User>>('/users', { size: 100, sort: 'fullName,asc' })
        .subscribe((page) =>
          this.technicians.set(
            page.content.filter((user) => user.enabled && user.roles.includes('TECHNICIAN')),
          ),
        );
    }
    this.route.queryParamMap.pipe(takeUntilDestroyed(this.destroyRef)).subscribe((params) => {
      this.q = params.get('q') ?? '';
      this.status = this.validStatus(params.get('status'));
      this.priority = this.validPriority(params.get('priority'));
      this.view = this.validView(params.get('view'));
      this.queue = this.validQueue(params.get('queue'));
      this.customerId = params.get('customerId') ?? '';
      this.siteId = params.get('siteId') ?? '';
      this.equipmentId = params.get('equipmentId') ?? '';
      this.equipmentCategoryId = params.get('equipmentCategoryId') ?? '';
      this.technicianId = params.get('technicianId') ?? '';
      this.warrantyStatus = (params.get('warrantyStatus') as WarrantyStatus | null) ?? '';
      this.currentPage = this.parsePage(params.get('page'));
      if (this.hasAdvancedFilters()) this.advancedFiltersOpen.set(true);
      this.loadTickets();
    });
  }

  loadSummary() {
    this.summaryLoading.set(true);
    this.api.get<TicketSummary>('/tickets/summary').subscribe({
      next: (summary) => {
        this.summary.set(summary);
        this.summaryLoading.set(false);
      },
      error: () => this.summaryLoading.set(false),
    });
  }

  loadTickets() {
    this.loading.set(true);
    this.error.set('');
    this.api
      .get<Page<Ticket>>('/tickets', {
        q: this.q.trim(),
        status: this.status,
        priority: this.priority,
        customerId: this.customerId,
        siteId: this.siteId,
        equipmentId: this.equipmentId,
        equipmentCategoryId: this.equipmentCategoryId,
        technicianId: this.technicianId,
        warrantyStatus: this.warrantyStatus,
        queue: this.queue,
        view: this.view,
        page: this.currentPage,
        size: this.pageSize,
        sort: 'submittedAt,desc',
      })
      .subscribe({
        next: (page) => {
          this.page.set(page);
          this.loading.set(false);
        },
        error: (error) => {
          this.page.set(null);
          this.error.set(error?.error?.message || 'Vui lòng kiểm tra kết nối và thử lại.');
          this.loading.set(false);
        },
      });
  }

  exportTickets() {
    if (this.exporting()) return;
    this.exporting.set(true);
    this.exportError.set('');
    this.api
      .download('/tickets/export', {
        q: this.q.trim(),
        status: this.status,
        priority: this.priority,
        customerId: this.customerId,
        siteId: this.siteId,
        equipmentId: this.equipmentId,
        equipmentCategoryId: this.equipmentCategoryId,
        technicianId: this.technicianId,
        warrantyStatus: this.warrantyStatus,
        queue: this.queue,
        view: this.view,
      })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (file) => {
          this.saveFile(file, this.exportFilename());
          this.exporting.set(false);
        },
        error: () => {
          this.exportError.set('Không thể xuất Excel. Vui lòng kiểm tra kết nối và thử lại.');
          this.exporting.set(false);
        },
      });
  }

  retry() {
    this.loadSummary();
    this.loadTickets();
  }

  applyFilters() {
    this.navigate(0);
  }

  selectView(view: TicketListView) {
    this.view = view;
    this.navigate(0);
  }

  clearFilters() {
    this.q = '';
    this.status = '';
    this.priority = '';
    this.view = 'ALL';
    this.queue = '';
    this.customerId = '';
    this.siteId = '';
    this.equipmentId = '';
    this.equipmentCategoryId = '';
    this.technicianId = '';
    this.warrantyStatus = '';
    this.advancedFiltersOpen.set(false);
    this.navigate(0);
  }

  clearAdvancedFilters() {
    this.queue = '';
    this.customerId = '';
    this.siteId = '';
    this.equipmentId = '';
    this.equipmentCategoryId = '';
    this.technicianId = '';
    this.warrantyStatus = '';
    this.navigate(0);
  }

  goToPage(page: number) {
    const lastPage = Math.max((this.page()?.totalPages ?? 1) - 1, 0);
    this.navigate(Math.min(Math.max(page, 0), lastPage));
  }

  hasFilters() {
    return (
      !!this.q.trim() ||
      !!this.status ||
      !!this.priority ||
      !!this.queue ||
      !!this.customerId ||
      !!this.siteId ||
      !!this.equipmentId ||
      !!this.equipmentCategoryId ||
      !!this.technicianId ||
      !!this.warrantyStatus ||
      this.view !== 'ALL'
    );
  }

  hasAdvancedFilters() {
    return this.advancedFilterCount() > 0;
  }

  advancedFilterCount() {
    return [
      this.queue,
      this.customerId,
      this.siteId,
      this.equipmentId,
      this.equipmentCategoryId,
      this.technicianId,
      this.warrantyStatus,
    ].filter(Boolean).length;
  }

  summaryValue(key: keyof TicketSummary) {
    const data = this.summary();
    return data ? data[key] : '—';
  }
  filteredSites() {
    return this.sites().filter((item) => !this.customerId || item.customerId === this.customerId);
  }
  filteredEquipment() {
    return this.equipment().filter(
      (item) =>
        (!this.customerId || item.customerId === this.customerId) &&
        (!this.siteId || item.siteId === this.siteId),
    );
  }

  statusLabel(status: string) {
    return (
      (
        {
          SUBMITTED: 'Mới gửi',
          ACCEPTED: 'Đã tiếp nhận',
          ASSIGNED: 'Đã phân công',
          IN_PROGRESS: 'Đang xử lý',
          WAITING_PARTS: 'Chờ linh kiện',
          RESOLVED: 'Đã xử lý',
          CLOSED: 'Đã đóng',
          REJECTED: 'Từ chối',
          CANCELLED: 'Đã hủy',
        } as Record<string, string>
      )[status] ?? status
    );
  }

  priorityLabel(priority: string) {
    return (
      (
        { CRITICAL: 'Khẩn cấp', HIGH: 'Cao', MEDIUM: 'Trung bình', LOW: 'Thấp' } as Record<
          string,
          string
        >
      )[priority] ?? priority
    );
  }

  slaState(ticket: Ticket): SlaState {
    if (['RESOLVED', 'CLOSED', 'REJECTED', 'CANCELLED'].includes(ticket.status)) return 'COMPLETED';
    const now = Date.now();
    const submittedAt = new Date(ticket.submittedAt).getTime();
    const dueAt = new Date(ticket.resolutionDueAt).getTime();
    if (now >= dueAt) return 'OVERDUE';
    const warningAt = submittedAt + (dueAt - submittedAt) * 0.8;
    return now >= warningAt ? 'DUE_SOON' : 'ON_TRACK';
  }

  slaLabel(ticket: Ticket) {
    return (
      {
        ON_TRACK: 'Đúng hạn',
        DUE_SOON: 'Sắp quá hạn',
        OVERDUE: 'Quá hạn',
        COMPLETED: 'Đã hoàn tất',
      } as Record<SlaState, string>
    )[this.slaState(ticket)];
  }

  slaTime(ticket: Ticket) {
    if (this.slaState(ticket) === 'COMPLETED') return 'Không còn cảnh báo SLA';
    const difference = new Date(ticket.resolutionDueAt).getTime() - Date.now();
    const prefix = difference < 0 ? 'Quá ' : 'Còn ';
    return prefix + this.formatDuration(Math.abs(difference));
  }

  private formatDuration(milliseconds: number) {
    const minutes = Math.max(1, Math.ceil(milliseconds / 60_000));
    if (minutes < 60) return `${minutes} phút`;
    const hours = Math.ceil(minutes / 60);
    if (hours < 24) return `${hours} giờ`;
    return `${Math.ceil(hours / 24)} ngày`;
  }

  private saveFile(file: Blob, filename: string) {
    const url = URL.createObjectURL(file);
    const link = document.createElement('a');
    link.href = url;
    link.download = filename;
    document.body.appendChild(link);
    link.click();
    link.remove();
    URL.revokeObjectURL(url);
  }

  private exportFilename(now = new Date()) {
    const pad = (value: number) => String(value).padStart(2, '0');
    return `phieu-bao-tri-${now.getFullYear()}${pad(now.getMonth() + 1)}${pad(now.getDate())}-${pad(
      now.getHours(),
    )}${pad(now.getMinutes())}${pad(now.getSeconds())}.xlsx`;
  }

  private navigate(page: number) {
    this.router.navigate([], {
      relativeTo: this.route,
      queryParams: {
        q: this.q.trim() || null,
        status: this.status || null,
        priority: this.priority || null,
        customerId: this.customerId || null,
        siteId: this.siteId || null,
        equipmentId: this.equipmentId || null,
        equipmentCategoryId: this.equipmentCategoryId || null,
        technicianId: this.technicianId || null,
        warrantyStatus: this.warrantyStatus || null,
        queue: this.queue || null,
        view: this.view === 'ALL' ? null : this.view,
        page: page > 0 ? page + 1 : null,
      },
    });
  }

  private parsePage(value: string | null) {
    const page = Number(value);
    return Number.isInteger(page) && page > 1 ? page - 1 : 0;
  }

  private validStatus(value: string | null): TicketStatus | '' {
    return this.statuses.includes(value as TicketStatus) ? (value as TicketStatus) : '';
  }

  private validPriority(value: string | null): TicketPriority | '' {
    return this.priorities.includes(value as TicketPriority) ? (value as TicketPriority) : '';
  }

  private validView(value: string | null): TicketListView {
    return this.views.some((item) => item.value === value) ? (value as TicketListView) : 'ALL';
  }

  private validQueue(value: string | null): TicketWorkQueue | '' {
    return this.queues.some((item) => item.value === value) ? (value as TicketWorkQueue) : '';
  }
}
