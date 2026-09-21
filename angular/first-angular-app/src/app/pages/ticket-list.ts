import { DatePipe } from '@angular/common';
import { Component, DestroyRef, inject, OnInit, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { Api } from '../core/api';
import { AuthService } from '../core/auth';
import {
  Page,
  Ticket,
  TicketListView,
  TicketPriority,
  TicketStatus,
  TicketSummary,
} from '../core/models';

type SlaState = 'ON_TRACK' | 'DUE_SOON' | 'OVERDUE' | 'COMPLETED';

@Component({
  selector: 'app-ticket-list',
  imports: [FormsModule, RouterLink, DatePipe],
  template: `<div class="page ticket-list-page">
    <header class="page-head">
      <div>
        <p class="eyebrow">YÊU CẦU BẢO TRÌ</p>
        <h1>Phiếu sửa chữa</h1>
        <p class="muted">Theo dõi tiến độ, SLA và người phụ trách trong từng yêu cầu.</p>
      </div>
      <div class="ticket-page-actions">
        <button
          type="button"
          class="secondary ticket-export-button"
          [disabled]="exporting()"
          title="Xuất toàn bộ kết quả đang lọc"
          (click)="exportTickets()"
        >
          <span aria-hidden="true">⇩</span>
          {{ exporting() ? 'Đang xuất...' : 'Xuất Excel' }}
        </button>
        @if (auth.hasAny(['REQUESTER', 'ADMIN', 'MANAGER'])) {
          <a class="primary button" routerLink="/tickets/new">+ Tạo phiếu mới</a>
        }
      </div>
    </header>

    @if (exportError()) {
      <div class="ticket-export-error" role="alert">
        <span>{{ exportError() }}</span>
        <button type="button" aria-label="Đóng thông báo" (click)="exportError.set('')">×</button>
      </div>
    }

    <section class="ticket-overview" aria-label="Tổng quan phiếu bảo trì">
      @for (card of overviewCards; track card.view) {
        <button
          type="button"
          class="ticket-kpi"
          [class.active]="view === card.view"
          [class.danger-card]="card.view === 'OVERDUE'"
          [attr.aria-pressed]="view === card.view"
          (click)="selectView(card.view)"
        >
          <span class="ticket-kpi-icon" aria-hidden="true">{{ card.icon }}</span>
          <span>
            <small>{{ card.label }}</small>
            @if (summaryLoading()) {
              <i class="ticket-count-skeleton"></i>
            } @else {
              <strong>{{ summaryValue(card.key) }}</strong>
            }
            <em>{{ card.hint }}</em>
          </span>
        </button>
      }
    </section>

    <section class="panel ticket-filter-panel">
      <form class="ticket-filters" (ngSubmit)="applyFilters()">
        <label class="ticket-search">
          <span>Tìm kiếm</span>
          <input [(ngModel)]="q" name="q" placeholder="Mã phiếu hoặc tiêu đề..." />
        </label>
        <label>
          <span>Trạng thái</span>
          <select [(ngModel)]="status" name="status">
            <option value="">Tất cả trạng thái</option>
            @for (item of statuses; track item) {
              <option [value]="item">{{ statusLabel(item) }}</option>
            }
          </select>
        </label>
        <label>
          <span>Mức ưu tiên</span>
          <select [(ngModel)]="priority" name="priority">
            <option value="">Tất cả mức độ</option>
            @for (item of priorities; track item) {
              <option [value]="item">{{ priorityLabel(item) }}</option>
            }
          </select>
        </label>
        <label>
          <span>Tình trạng SLA</span>
          <select [(ngModel)]="view" name="view">
            @for (item of views; track item.value) {
              <option [value]="item.value">{{ item.label }}</option>
            }
          </select>
        </label>
        <div class="ticket-filter-actions">
          <button class="primary" type="submit">Áp dụng</button>
          <button
            class="secondary"
            type="button"
            [disabled]="!hasFilters()"
            (click)="clearFilters()"
          >
            Xóa lọc
          </button>
        </div>
      </form>
    </section>

    <div class="ticket-results-head">
      <div>
        <h2>Danh sách phiếu</h2>
        @if (page(); as result) {
          <p>{{ result.totalElements }} kết quả phù hợp</p>
        } @else {
          <p>Đang cập nhật dữ liệu...</p>
        }
      </div>
      @if (hasFilters()) {
        <span class="filter-active">Đang áp dụng bộ lọc</span>
      }
    </div>

    @if (error()) {
      <section class="ticket-error" role="alert">
        <div>
          <strong>Không thể tải danh sách phiếu</strong>
          <p>{{ error() }}</p>
        </div>
        <button type="button" class="secondary" (click)="retry()">Thử lại</button>
      </section>
    } @else if (loading()) {
      <section class="panel ticket-list-skeleton" aria-label="Đang tải danh sách">
        @for (row of skeletonRows; track row) {
          <div><i></i><i></i><i></i><i></i></div>
        }
      </section>
    } @else if (page()?.content?.length) {
      <section class="panel table-wrap ticket-desktop">
        <table class="ticket-table">
          <thead>
            <tr>
              <th>Mã phiếu</th>
              <th>Thiết bị / Nội dung</th>
              <th>Ưu tiên</th>
              <th>Trạng thái</th>
              <th>Phụ trách</th>
              <th>SLA xử lý</th>
            </tr>
          </thead>
          <tbody>
            @for (ticket of page()!.content; track ticket.id) {
              <tr [routerLink]="['/tickets', ticket.id]" tabindex="0">
                <td>
                  <b class="code">{{ ticket.code }}</b>
                  <small>{{ ticket.submittedAt | date: 'dd/MM/yyyy HH:mm' }}</small>
                </td>
                <td>
                  <b>{{ ticket.title }}</b>
                  <small>{{ ticket.equipmentCode }} · {{ ticket.equipmentName }}</small>
                </td>
                <td>
                  <span [class]="'priority ' + ticket.priority.toLowerCase()">
                    {{ priorityLabel(ticket.priority) }}
                  </span>
                </td>
                <td>
                  <span [class]="'status ' + ticket.status.toLowerCase()">
                    {{ statusLabel(ticket.status) }}
                  </span>
                </td>
                <td>
                  <span class="assignee-name">{{ ticket.assigneeName || 'Chưa phân công' }}</span>
                </td>
                <td>
                  <div [class]="'sla-indicator ' + slaState(ticket).toLowerCase()">
                    <b>{{ slaLabel(ticket) }}</b>
                    <span>{{ slaTime(ticket) }}</span>
                    <small>Hạn {{ ticket.resolutionDueAt | date: 'dd/MM/yyyy HH:mm' }}</small>
                  </div>
                </td>
              </tr>
            }
          </tbody>
        </table>
      </section>

      <section class="ticket-mobile" aria-label="Danh sách phiếu bảo trì">
        @for (ticket of page()!.content; track ticket.id) {
          <a class="ticket-card" [routerLink]="['/tickets', ticket.id]">
            <div class="ticket-card-head">
              <div>
                <b class="code">{{ ticket.code }}</b>
                <small>{{ ticket.submittedAt | date: 'dd/MM/yyyy HH:mm' }}</small>
              </div>
              <span [class]="'priority ' + ticket.priority.toLowerCase()">
                {{ priorityLabel(ticket.priority) }}
              </span>
            </div>
            <h3>{{ ticket.title }}</h3>
            <p>{{ ticket.equipmentCode }} · {{ ticket.equipmentName }}</p>
            <div class="ticket-card-meta">
              <span [class]="'status ' + ticket.status.toLowerCase()">
                {{ statusLabel(ticket.status) }}
              </span>
              <span>{{ ticket.assigneeName || 'Chưa phân công' }}</span>
            </div>
            <div [class]="'ticket-card-sla ' + slaState(ticket).toLowerCase()">
              <span>{{ slaLabel(ticket) }}</span>
              <b>{{ slaTime(ticket) }}</b>
            </div>
          </a>
        }
      </section>

      @if (page()!.totalPages > 1) {
        <nav class="ticket-pagination" aria-label="Phân trang phiếu bảo trì">
          <button
            type="button"
            class="secondary"
            [disabled]="page()!.number === 0"
            (click)="goToPage(page()!.number - 1)"
          >
            ← Trước
          </button>
          <div>
            @for (number of visiblePages(); track number) {
              <button
                type="button"
                [class.active]="number === page()!.number"
                [attr.aria-current]="number === page()!.number ? 'page' : null"
                (click)="goToPage(number)"
              >
                {{ number + 1 }}
              </button>
            }
          </div>
          <button
            type="button"
            class="secondary"
            [disabled]="page()!.number >= page()!.totalPages - 1"
            (click)="goToPage(page()!.number + 1)"
          >
            Sau →
          </button>
        </nav>
      }
    } @else {
      <section class="panel ticket-empty">
        <span aria-hidden="true">⌕</span>
        <h3>Chưa có phiếu phù hợp</h3>
        <p>Hãy thử thay đổi bộ lọc hoặc từ khóa tìm kiếm.</p>
        @if (hasFilters()) {
          <button type="button" class="secondary" (click)="clearFilters()">Xóa bộ lọc</button>
        } @else if (auth.hasAny(['REQUESTER', 'ADMIN', 'MANAGER'])) {
          <a class="primary button" routerLink="/tickets/new">Tạo phiếu đầu tiên</a>
        }
      </section>
    }
  </div>`,
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

  q = '';
  status: TicketStatus | '' = '';
  priority: TicketPriority | '' = '';
  view: TicketListView = 'ALL';
  currentPage = 0;

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
    this.route.queryParamMap.pipe(takeUntilDestroyed(this.destroyRef)).subscribe((params) => {
      this.q = params.get('q') ?? '';
      this.status = this.validStatus(params.get('status'));
      this.priority = this.validPriority(params.get('priority'));
      this.view = this.validView(params.get('view'));
      this.currentPage = this.parsePage(params.get('page'));
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
        view: this.view,
        page: this.currentPage,
        size: 20,
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
    this.navigate(0);
  }

  goToPage(page: number) {
    const lastPage = Math.max((this.page()?.totalPages ?? 1) - 1, 0);
    this.navigate(Math.min(Math.max(page, 0), lastPage));
  }

  visiblePages() {
    const total = this.page()?.totalPages ?? 0;
    const current = this.page()?.number ?? 0;
    const start = Math.max(0, Math.min(current - 2, total - 5));
    return Array.from({ length: Math.min(5, total) }, (_, index) => start + index);
  }

  hasFilters() {
    return !!this.q.trim() || !!this.status || !!this.priority || this.view !== 'ALL';
  }

  summaryValue(key: keyof TicketSummary) {
    const data = this.summary();
    return data ? data[key] : '—';
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
}
