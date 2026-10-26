import { DatePipe, DecimalPipe } from '@angular/common';
import { Component, DestroyRef, inject, OnInit, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { Api } from '../../core/api';
import { Page, PaymentAttempt, PaymentStatus, PaymentTransactionItem } from '../../core/models';
import { PaginationComponent } from '../../shared/pagination';

@Component({
  selector: 'app-payment-transactions',
  imports: [FormsModule, RouterLink, DatePipe, DecimalPipe, PaginationComponent],
  templateUrl: './payment-transactions.html',
  styleUrl: '../../shared/operations-page.css',
})
export class PaymentTransactionsPage implements OnInit {
  private api = inject(Api);
  private route = inject(ActivatedRoute);
  private router = inject(Router);
  private destroyRef = inject(DestroyRef);
  readonly result = signal<Page<PaymentTransactionItem> | null>(null);
  readonly loading = signal(true);
  readonly error = signal('');
  readonly actionLoading = signal<Record<string, boolean>>({});
  readonly reconciliation = !!this.route.snapshot.data['reconciliation'];
  q = '';
  status: PaymentStatus | '' = '';
  from = '';
  to = '';
  page = 0;
  readonly statuses: PaymentStatus[] = ['PENDING', 'SUCCEEDED', 'FAILED'];
  ngOnInit() {
    const defaults = this.period();
    this.route.queryParamMap.pipe(takeUntilDestroyed(this.destroyRef)).subscribe((p) => {
      this.q = p.get('q') ?? '';
      this.status = (
        this.statuses.includes(p.get('status') as PaymentStatus) ? p.get('status') : ''
      ) as PaymentStatus | '';
      this.from = p.get('from') ?? defaults.from;
      this.to = p.get('to') ?? defaults.to;
      this.page = Math.max(Number(p.get('page') ?? 1) - 1, 0);
      this.load();
    });
  }
  load() {
    this.loading.set(true);
    this.error.set('');
    this.api
      .get<Page<PaymentTransactionItem>>('/payments/transactions', {
        q: this.q.trim(),
        status: this.status,
        from: this.from,
        to: this.to,
        reconciliationRequired: this.reconciliation,
        page: this.page,
        size: 20,
      })
      .subscribe({
        next: (v) => {
          this.result.set(v);
          this.loading.set(false);
        },
        error: (r) => {
          this.error.set(r.error?.detail ?? 'Không thể tải giao dịch.');
          this.loading.set(false);
        },
      });
  }
  apply(page = 0) {
    void this.router.navigate([], {
      relativeTo: this.route,
      queryParams: {
        q: this.q.trim() || null,
        status: this.status || null,
        from: this.from,
        to: this.to,
        page: page ? page + 1 : null,
      },
    });
  }
  reconcile(item: PaymentTransactionItem) {
    this.actionLoading.update((v) => ({ ...v, [item.attemptId]: true }));
    this.api.post<PaymentAttempt>(`/payments/${item.attemptId}/reconcile`).subscribe({
      next: () => {
        this.actionLoading.update((v) => ({ ...v, [item.attemptId]: false }));
        this.load();
      },
      error: (r) => {
        this.actionLoading.update((v) => ({ ...v, [item.attemptId]: false }));
        this.error.set(r.error?.detail ?? 'Không thể đối soát giao dịch.');
      },
    });
  }
  label(s: string) {
    return (
      (
        { PENDING: 'Đang chờ', SUCCEEDED: 'Thành công', FAILED: 'Thất bại' } as Record<
          string,
          string
        >
      )[s] ?? s
    );
  }
  tone(s: string) {
    return s === 'SUCCEEDED' ? 'success' : s === 'FAILED' ? 'danger' : 'warning';
  }
  private period() {
    const to = new Date(),
      from = new Date(to);
    from.setDate(from.getDate() - 29);
    return { from: from.toLocaleDateString('en-CA'), to: to.toLocaleDateString('en-CA') };
  }
}
