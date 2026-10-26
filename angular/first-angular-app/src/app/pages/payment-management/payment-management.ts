import { DatePipe, DecimalPipe } from '@angular/common';
import { Component, computed, DestroyRef, inject, OnInit, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { Api } from '../../core/api';
import { AuthService } from '../../core/auth';
import {
  Page,
  PaymentAttempt,
  PaymentManagementItem,
  TicketPaymentStatus,
  TicketPayments,
} from '../../core/models';
import { PaginationComponent } from '../../shared/pagination';

type ManagementStatus = Exclude<TicketPaymentStatus, 'NOT_AVAILABLE'>;

@Component({
  selector: 'app-payment-management',
  imports: [FormsModule, RouterLink, DatePipe, DecimalPipe, PaginationComponent],
  templateUrl: './payment-management.html',
  styleUrl: './payment-management.css',
})
export class PaymentManagementPage implements OnInit {
  private readonly api = inject(Api);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);
  readonly auth = inject(AuthService);

  readonly result = signal<Page<PaymentManagementItem> | null>(null);
  readonly loading = signal(true);
  readonly error = signal('');
  readonly filterError = signal('');
  readonly expandedTicketId = signal<string | null>(null);
  readonly details = signal<Record<string, TicketPayments>>({});
  readonly detailLoading = signal<Set<string>>(new Set());
  readonly rowErrors = signal<Record<string, string>>({});
  readonly actionLoading = signal<Record<string, boolean>>({});
  readonly manager = computed(() => this.auth.hasAny(['ADMIN', 'MANAGER']));
  readonly customer = computed(() => this.auth.user()?.accountType === 'CUSTOMER');

  q = '';
  status: ManagementStatus | '' = '';
  from = '';
  to = '';
  currentPage = 0;
  readonly statuses: ManagementStatus[] = [
    'NOT_REQUIRED',
    'UNPAID',
    'PENDING',
    'SUCCEEDED',
    'FAILED',
  ];

  ngOnInit() {
    this.route.queryParamMap.pipe(takeUntilDestroyed(this.destroyRef)).subscribe((params) => {
      this.q = params.get('q') ?? '';
      this.status = this.validStatus(params.get('status'));
      this.from = params.get('from') ?? '';
      this.to = params.get('to') ?? '';
      this.currentPage = this.parsePage(params.get('page'));
      this.loadPayments();
    });
  }

  loadPayments() {
    this.loading.set(true);
    this.error.set('');
    this.api
      .get<Page<PaymentManagementItem>>('/payments', {
        q: this.q.trim(),
        status: this.status,
        from: this.from,
        to: this.to,
        page: this.currentPage,
        size: 20,
      })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (page) => {
          this.result.set(page);
          this.loading.set(false);
        },
        error: (response) => {
          this.result.set(null);
          this.error.set(this.errorMessage(response, 'Vui lòng kiểm tra kết nối và thử lại.'));
          this.loading.set(false);
        },
      });
  }

  applyFilters() {
    if (this.from && this.to && this.from > this.to) {
      this.filterError.set('Ngày bắt đầu không được sau ngày kết thúc.');
      return;
    }
    this.filterError.set('');
    this.navigate(0);
  }

  clearFilters() {
    this.q = '';
    this.status = '';
    this.from = '';
    this.to = '';
    this.filterError.set('');
    this.navigate(0);
  }

  hasFilters() {
    return !!(this.q.trim() || this.status || this.from || this.to);
  }

  selectPage(page: number) {
    this.navigate(page);
  }

  toggleDetails(item: PaymentManagementItem) {
    if (this.expandedTicketId() === item.ticketId) {
      this.expandedTicketId.set(null);
      return;
    }
    this.expandedTicketId.set(item.ticketId);
    if (!this.details()[item.ticketId]) this.loadDetails(item.ticketId);
  }

  loadDetails(ticketId: string, force = false) {
    if (!force && this.details()[ticketId]) return;
    this.setDetailLoading(ticketId, true);
    this.setRowError(ticketId, '');
    this.api
      .get<TicketPayments>(`/tickets/${ticketId}/payments`)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (detail) => {
          this.details.update((items) => ({ ...items, [ticketId]: detail }));
          this.setDetailLoading(ticketId, false);
        },
        error: (response) => {
          this.setDetailLoading(ticketId, false);
          this.setRowError(
            ticketId,
            this.errorMessage(response, 'Không thể tải lịch sử giao dịch.'),
          );
        },
      });
  }

  startPayment(item: PaymentManagementItem) {
    if (this.actionLoading()[item.ticketId]) return;
    this.setActionLoading(item.ticketId, true);
    this.setRowError(item.ticketId, '');
    this.api
      .post<PaymentAttempt>(`/tickets/${item.ticketId}/payments`)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (attempt) => {
          this.setActionLoading(item.ticketId, false);
          if (attempt.paymentUrl) window.location.assign(attempt.paymentUrl);
          else {
            this.expandedTicketId.set(item.ticketId);
            this.setRowError(item.ticketId, 'VNPay không trả về địa chỉ thanh toán.');
          }
        },
        error: (response) => {
          this.setActionLoading(item.ticketId, false);
          this.expandedTicketId.set(item.ticketId);
          this.setRowError(
            item.ticketId,
            this.errorMessage(response, 'Không thể khởi tạo thanh toán.'),
          );
        },
      });
  }

  reconcile(item: PaymentManagementItem, attempt: PaymentAttempt) {
    if (this.actionLoading()[attempt.attemptId]) return;
    this.setActionLoading(attempt.attemptId, true);
    this.setRowError(item.ticketId, '');
    this.api
      .post<PaymentAttempt>(`/payments/${attempt.attemptId}/reconcile`)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          this.setActionLoading(attempt.attemptId, false);
          this.loadDetails(item.ticketId, true);
          this.loadPayments();
        },
        error: (response) => {
          this.setActionLoading(attempt.attemptId, false);
          this.setRowError(
            item.ticketId,
            this.errorMessage(response, 'Không thể đối soát giao dịch.'),
          );
        },
      });
  }

  canReconcile(attempt: PaymentAttempt) {
    return (
      attempt.status === 'PENDING' &&
      (this.manager() || (this.customer() && new Date(attempt.expiresAt).getTime() <= Date.now()))
    );
  }

  paymentStatusLabel(status: string) {
    return (
      (
        {
          NOT_REQUIRED: 'Không cần thanh toán',
          UNPAID: 'Chưa thanh toán',
          PENDING: 'Đang chờ',
          SUCCEEDED: 'Đã thanh toán',
          FAILED: 'Thất bại',
        } as Record<string, string>
      )[status] ?? status
    );
  }

  attemptStatusLabel(status: string) {
    return (
      (
        { PENDING: 'Đang chờ', SUCCEEDED: 'Thành công', FAILED: 'Thất bại' } as Record<
          string,
          string
        >
      )[status] ?? status
    );
  }

  private navigate(page: number) {
    this.router.navigate([], {
      relativeTo: this.route,
      queryParams: {
        q: this.q.trim() || null,
        status: this.status || null,
        from: this.from || null,
        to: this.to || null,
        page: page > 0 ? page + 1 : null,
      },
    });
  }

  private parsePage(value: string | null) {
    const page = Number(value);
    return Number.isInteger(page) && page > 1 ? page - 1 : 0;
  }

  private validStatus(value: string | null): ManagementStatus | '' {
    return this.statuses.includes(value as ManagementStatus) ? (value as ManagementStatus) : '';
  }

  private setDetailLoading(ticketId: string, loading: boolean) {
    this.detailLoading.update((current) => {
      const next = new Set(current);
      if (loading) next.add(ticketId);
      else next.delete(ticketId);
      return next;
    });
  }

  private setActionLoading(key: string, loading: boolean) {
    this.actionLoading.update((items) => ({ ...items, [key]: loading }));
  }

  private setRowError(ticketId: string, message: string) {
    this.rowErrors.update((items) => ({ ...items, [ticketId]: message }));
  }

  private errorMessage(response: any, fallback: string) {
    return response?.error?.detail ?? response?.error?.message ?? fallback;
  }
}
