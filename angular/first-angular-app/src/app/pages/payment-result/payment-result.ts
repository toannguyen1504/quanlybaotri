import { DatePipe, DecimalPipe } from '@angular/common';
import { Component, inject, OnDestroy, OnInit, signal } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { Subscription, switchMap, take, takeWhile, timer } from 'rxjs';
import { Api } from '../../core/api';
import { TicketPayments } from '../../core/models';

@Component({
  selector: 'app-payment-result',
  imports: [RouterLink, DatePipe, DecimalPipe],
  templateUrl: './payment-result.html',
})
export class PaymentResultPage implements OnInit, OnDestroy {
  private static readonly POLL_INTERVAL_MS = 2_000;
  private static readonly MAX_POLL_ATTEMPTS = 10;
  private readonly api = inject(Api);
  private readonly route = inject(ActivatedRoute);
  private polling?: Subscription;
  readonly payment = signal<TicketPayments | null>(null);
  readonly loading = signal(true);
  readonly checking = signal(false);
  readonly error = signal('');
  readonly ticketId = this.route.snapshot.queryParamMap.get('ticketId');
  private readonly valid = this.route.snapshot.queryParamMap.get('valid') === 'true';

  ngOnInit() {
    this.load();
  }

  ngOnDestroy() {
    this.polling?.unsubscribe();
  }

  load() {
    this.polling?.unsubscribe();
    if (!this.valid || !this.ticketId) {
      this.loading.set(false);
      this.checking.set(false);
      this.error.set('Thông tin trả về không hợp lệ hoặc chữ ký VNPay không khớp.');
      return;
    }
    this.loading.set(true);
    this.checking.set(true);
    this.error.set('');
    this.polling = timer(0, PaymentResultPage.POLL_INTERVAL_MS)
      .pipe(
        take(PaymentResultPage.MAX_POLL_ATTEMPTS),
        switchMap(() => this.api.get<TicketPayments>(`/tickets/${this.ticketId}/payments`)),
        takeWhile((result) => result.status === 'PENDING', true),
      )
      .subscribe({
      next: (result) => {
        this.payment.set(result);
        this.loading.set(false);
        if (result.status !== 'PENDING') this.checking.set(false);
      },
      error: (response) => {
        this.error.set(response.error?.detail ?? 'Không thể tải trạng thái thanh toán.');
        this.loading.set(false);
        this.checking.set(false);
      },
      complete: () => this.checking.set(false),
    });
  }
}
