import { DecimalPipe } from '@angular/common';
import { Component, DestroyRef, inject, OnInit, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { Api } from '../../core/api';
import { AuthService } from '../../core/auth';
import { PaymentSummary } from '../../core/models';

@Component({
  selector: 'app-payment-overview',
  imports: [DecimalPipe, FormsModule, RouterLink],
  templateUrl: './payment-overview.html',
  styleUrl: '../../shared/operations-page.css',
})
export class PaymentOverviewPage implements OnInit {
  private readonly api = inject(Api);
  private readonly auth = inject(AuthService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);
  readonly summary = signal<PaymentSummary | null>(null);
  readonly loading = signal(true);
  readonly error = signal('');
  from = '';
  to = '';

  ngOnInit() {
    const defaults = this.defaultPeriod();
    this.route.queryParamMap.pipe(takeUntilDestroyed(this.destroyRef)).subscribe((params) => {
      this.from = params.get('from') ?? defaults.from;
      this.to = params.get('to') ?? defaults.to;
      this.load();
    });
  }

  load() {
    this.loading.set(true);
    this.error.set('');
    this.api.get<PaymentSummary>('/payments/summary', { from: this.from, to: this.to }).subscribe({
      next: (value) => {
        this.summary.set(value);
        this.loading.set(false);
      },
      error: (response) => {
        this.error.set(response.error?.detail ?? 'Không thể tải tổng quan thanh toán.');
        this.loading.set(false);
      },
    });
  }

  applyPeriod() {
    void this.router.navigate([], {
      relativeTo: this.route,
      queryParams: { from: this.from, to: this.to },
    });
  }

  canReconcile() {
    return this.auth.hasAny(['ADMIN', 'MANAGER']);
  }

  private defaultPeriod() {
    const to = new Date();
    const from = new Date(to);
    from.setDate(from.getDate() - 29);
    return { from: this.dateValue(from), to: this.dateValue(to) };
  }
  private dateValue(value: Date) {
    return value.toLocaleDateString('en-CA');
  }
}
