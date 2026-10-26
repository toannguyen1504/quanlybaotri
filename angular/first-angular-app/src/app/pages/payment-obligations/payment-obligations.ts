import { DatePipe, DecimalPipe } from '@angular/common';
import { Component, DestroyRef, inject, OnInit, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { Api } from '../../core/api';
import {
  CursorPage,
  PaymentAttempt,
  PaymentManagementItem,
  TicketPaymentStatus,
} from '../../core/models';

type Status = Exclude<TicketPaymentStatus, 'NOT_AVAILABLE'>;
@Component({
  selector: 'app-payment-obligations',
  imports: [FormsModule, RouterLink, DatePipe, DecimalPipe],
  templateUrl: './payment-obligations.html',
  styleUrl: '../../shared/operations-page.css',
})
export class PaymentObligationsPage implements OnInit {
  private readonly api = inject(Api);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);
  readonly items = signal<PaymentManagementItem[]>([]);
  readonly loading = signal(true);
  readonly loadingMore = signal(false);
  readonly error = signal('');
  readonly nextCursor = signal<string | undefined>(undefined);
  readonly hasMore = signal(false);
  readonly actionLoading = signal<Record<string, boolean>>({});
  q = '';
  status: Status | '' = '';
  from = '';
  to = '';
  readonly statuses: Status[] = ['NOT_REQUIRED', 'UNPAID', 'PENDING', 'SUCCEEDED', 'FAILED'];
  ngOnInit() {
    this.route.queryParamMap.pipe(takeUntilDestroyed(this.destroyRef)).subscribe((params) => {
      this.q = params.get('q') ?? '';
      this.status = (
        this.statuses.includes(params.get('status') as Status) ? params.get('status') : ''
      ) as Status | '';
      this.from = params.get('from') ?? '';
      this.to = params.get('to') ?? '';
      this.load(true);
    });
  }
  apply() {
    void this.router.navigate([], {
      relativeTo: this.route,
      queryParams: {
        q: this.q.trim() || null,
        status: this.status || null,
        from: this.from || null,
        to: this.to || null,
      },
    });
  }
  clear() {
    this.q = '';
    this.status = '';
    this.from = '';
    this.to = '';
    this.apply();
  }
  load(reset: boolean) {
    if (reset) {
      this.loading.set(true);
      this.items.set([]);
      this.nextCursor.set(undefined);
    } else this.loadingMore.set(true);
    this.error.set('');
    this.api
      .get<CursorPage<PaymentManagementItem>>('/payments/obligations', {
        q: this.q.trim(),
        status: this.status,
        from: this.from,
        to: this.to,
        cursor: reset ? '' : this.nextCursor(),
        size: 20,
      })
      .subscribe({
        next: (page) => {
          this.items.update((items) => (reset ? page.content : [...items, ...page.content]));
          this.nextCursor.set(page.nextCursor);
          this.hasMore.set(page.hasMore);
          this.loading.set(false);
          this.loadingMore.set(false);
        },
        error: (r) => {
          this.error.set(r.error?.detail ?? 'Không thể tải nghĩa vụ thanh toán.');
          this.loading.set(false);
          this.loadingMore.set(false);
        },
      });
  }
  startPayment(item: PaymentManagementItem) {
    if (this.actionLoading()[item.ticketId]) return;
    this.actionLoading.update((v) => ({ ...v, [item.ticketId]: true }));
    this.api.post<PaymentAttempt>(`/tickets/${item.ticketId}/payments`).subscribe({
      next: (a) => {
        this.actionLoading.update((v) => ({ ...v, [item.ticketId]: false }));
        if (a.paymentUrl) window.location.assign(a.paymentUrl);
      },
      error: (r) => {
        this.actionLoading.update((v) => ({ ...v, [item.ticketId]: false }));
        this.error.set(r.error?.detail ?? 'Không thể khởi tạo thanh toán.');
      },
    });
  }
  label(value: string) {
    return (
      (
        {
          NOT_REQUIRED: 'Không cần thanh toán',
          UNPAID: 'Chưa thanh toán',
          PENDING: 'Đang chờ',
          SUCCEEDED: 'Đã thanh toán',
          FAILED: 'Thất bại',
        } as Record<string, string>
      )[value] ?? value
    );
  }
  tone(value: string) {
    if (value === 'SUCCEEDED' || value === 'NOT_REQUIRED') return 'success';
    if (value === 'FAILED') return 'danger';
    if (value === 'PENDING') return 'warning';
    return '';
  }
}
