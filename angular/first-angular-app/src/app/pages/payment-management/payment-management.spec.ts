import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, provideRouter, Router } from '@angular/router';
import { of } from 'rxjs';
import { describe, expect, it, vi } from 'vitest';
import { Api } from '../../core/api';
import { AuthService } from '../../core/auth';
import { Page, PaymentAttempt, PaymentManagementItem, TicketPayments } from '../../core/models';
import { PaymentManagementPage } from './payment-management';

describe('PaymentManagementPage', () => {
  const item: PaymentManagementItem = {
    ticketId: 'ticket-1',
    ticketCode: 'BT-2026-000001',
    ticketTitle: 'Máy chủ mất nguồn',
    customerId: 'customer-1',
    customerName: 'Khách hàng A',
    siteId: 'site-1',
    siteName: 'Hà Nội',
    ticketStatus: 'RESOLVED',
    paymentStatus: 'PENDING',
    amountDue: 125000,
    activityAt: '2026-10-08T03:00:00Z',
    latestAttemptAt: '2026-10-08T03:00:00Z',
    attemptCount: 1,
    canPay: false,
  };
  const attempt: PaymentAttempt = {
    attemptId: 'attempt-1',
    txnRef: 'TXN-001',
    status: 'PENDING',
    amount: 125000,
    createdAt: '2026-10-08T03:00:00Z',
    expiresAt: '2026-10-08T03:15:00Z',
  };
  const details: TicketPayments = {
    ticketId: item.ticketId,
    status: 'PENDING',
    amountDue: item.amountDue,
    canPay: false,
    attempts: [attempt],
  };

  async function setup(query: Record<string, string> = {}) {
    const page: Page<PaymentManagementItem> = {
      content: [item],
      totalElements: 1,
      totalPages: 1,
      number: 0,
      size: 20,
    };
    const api = {
      get: vi.fn((path: string) => (path === '/payments' ? of(page) : of(details))),
      post: vi.fn(() => of(attempt)),
    };
    await TestBed.configureTestingModule({
      imports: [PaymentManagementPage],
      providers: [
        provideRouter([]),
        { provide: ActivatedRoute, useValue: { queryParamMap: of(convertToParamMap(query)) } },
        { provide: Api, useValue: api },
        { provide: AuthService, useValue: { hasAny: () => true } },
      ],
    }).compileComponents();
    const fixture = TestBed.createComponent(PaymentManagementPage);
    fixture.detectChanges();
    return { fixture, component: fixture.componentInstance, api, router: TestBed.inject(Router) };
  }

  it('loads filters and pagination from the URL', async () => {
    const { api } = await setup({
      q: 'TXN-001',
      status: 'PENDING',
      from: '2026-10-01',
      to: '2026-10-08',
      page: '2',
    });

    expect(api.get).toHaveBeenCalledWith('/payments', {
      q: 'TXN-001',
      status: 'PENDING',
      from: '2026-10-01',
      to: '2026-10-08',
      page: 1,
      size: 20,
    });
  });

  it('loads transaction history only when a row is expanded', async () => {
    const { fixture, component, api } = await setup();
    expect(api.get).toHaveBeenCalledTimes(1);

    component.toggleDetails(item);
    fixture.detectChanges();

    expect(api.get).toHaveBeenCalledWith('/tickets/ticket-1/payments');
    expect((fixture.nativeElement as HTMLElement).textContent).toContain('TXN-001');
    expect((fixture.nativeElement as HTMLElement).textContent).toContain('Đối soát');
  });

  it('writes active filters to the URL and validates the date range', async () => {
    const { component, router } = await setup();
    const navigate = vi.spyOn(router, 'navigate').mockResolvedValue(true);
    component.q = '  BT-001  ';
    component.status = 'FAILED';
    component.from = '2026-10-09';
    component.to = '2026-10-08';

    component.applyFilters();
    expect(navigate).not.toHaveBeenCalled();
    expect(component.filterError()).toContain('Ngày bắt đầu');

    component.from = '2026-10-01';
    component.applyFilters();
    expect(navigate).toHaveBeenCalledWith([], {
      relativeTo: expect.anything(),
      queryParams: {
        q: 'BT-001',
        status: 'FAILED',
        from: '2026-10-01',
        to: '2026-10-08',
        page: null,
      },
    });
  });

  it('reconciles a pending attempt and refreshes its row', async () => {
    const { component, api } = await setup();
    component.toggleDetails(item);

    component.reconcile(item, attempt);

    expect(api.post).toHaveBeenCalledWith('/payments/attempt-1/reconcile');
    expect(api.get).toHaveBeenCalledWith('/tickets/ticket-1/payments');
    expect(api.get).toHaveBeenCalledWith('/payments', expect.any(Object));
  });
});
