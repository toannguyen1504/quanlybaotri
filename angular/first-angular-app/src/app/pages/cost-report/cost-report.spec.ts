import { TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { Api } from '../../core/api';
import { CostReportPage } from './cost-report';

const report = {
  grandTotal: 1_500_000,
  totals: { PART: 1_250_000, OTHER: 250_000 },
  rows: [
    {
      id: 'cost-1',
      ticketCode: 'BT-001',
      equipmentCode: 'TB-001',
      equipmentName: 'Máy chủ',
      customerName: 'Khách hàng A',
      siteName: 'Hà Nội',
      status: 'ACCEPTED',
      category: 'PART',
      description: 'Thay ổ cứng',
      amount: 1_250_000,
      incurredAt: '2026-09-30T00:00:00Z',
    },
    {
      id: 'cost-2',
      ticketCode: 'BT-002',
      equipmentCode: 'TB-002',
      equipmentName: 'Máy in',
      customerName: 'Khách hàng A',
      siteName: 'Đà Nẵng',
      status: 'QUOTED',
      category: 'OTHER',
      description: 'Phí vận chuyển linh kiện',
      amount: 250_000,
      incurredAt: '2026-09-29T00:00:00Z',
    },
  ],
};

function apiMock(reportResponse = of(report)) {
  return {
    get: vi.fn((path: string) => {
      if (path === '/reports/costs') return reportResponse;
      if (path === '/customers') return of([{ id: 'customer-1', name: 'Khách hàng A' }]);
      if (path === '/customer-sites') {
        return of([
          { id: 'site-1', customerId: 'customer-1', name: 'Hà Nội' },
          { id: 'site-2', customerId: 'customer-2', name: 'Đà Nẵng' },
        ]);
      }
      if (path === '/equipment') {
        return of({ content: [], totalElements: 0, totalPages: 0, number: 0, size: 100 });
      }
      return of([]);
    }),
    download: vi.fn(() => of(new Blob(['report']))),
  };
}

async function setup(api = apiMock()) {
  await TestBed.configureTestingModule({
    imports: [CostReportPage],
    providers: [{ provide: Api, useValue: api }],
  }).compileComponents();

  const fixture = TestBed.createComponent(CostReportPage);
  await fixture.whenStable();
  return { fixture, component: fixture.componentInstance, api };
}

describe('CostReportPage', () => {
  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('renders the maintenance cost report with localized summaries and statuses', async () => {
    const { fixture, api } = await setup();
    const text = (fixture.nativeElement as HTMLElement).textContent ?? '';

    expect(text).toContain('Báo cáo');
    expect(text).toContain('Chi phí bảo trì');
    expect(text).not.toContain('Hạch toán nội bộ');
    expect(fixture.nativeElement.querySelector('.report-page-head')).not.toBeNull();
    expect(fixture.nativeElement.querySelector('.cost-report-hero')).toBeNull();
    expect(text).toContain('1,500,000 ₫');
    expect(text).toContain('1,250,000 ₫');
    expect(text).toContain('Linh kiện');
    expect(text).toContain('Phát sinh');
    expect(text).toContain('Khách hàng đã chấp nhận');
    expect(text).toContain('Chờ khách hàng xác nhận');
    expect(api.get).toHaveBeenCalledWith('/reports/costs', expect.any(Object));
  });

  it('filters sites by customer and clears the selected site when customer changes', async () => {
    const { component } = await setup();

    component.filters.customerId = 'customer-1';
    component.filters.siteId = 'site-2';
    expect(component.filteredSites().map((site) => site.id)).toEqual(['site-1']);

    component.customerChanged();
    expect(component.filters.siteId).toBe('');
  });

  it('does not request a report when the date range is invalid', async () => {
    const { component, api } = await setup();
    const callsBefore = api.get.mock.calls.filter(([path]) => path === '/reports/costs').length;

    component.filters.from = '2026-10-01';
    component.filters.to = '2026-09-01';
    component.load();

    const callsAfter = api.get.mock.calls.filter(([path]) => path === '/reports/costs').length;
    expect(callsAfter).toBe(callsBefore);
    expect(component.error()).toContain('Ngày bắt đầu không được sau ngày kết thúc');
    expect(component.loading()).toBe(false);
  });

  it('resets filters and reloads the default reporting period', async () => {
    const { component, api } = await setup();
    component.filters.customerId = 'customer-1';
    component.filters.category = 'PART';

    component.resetFilters();

    expect(component.filters.customerId).toBe('');
    expect(component.filters.category).toBe('');
    expect(api.get.mock.calls.filter(([path]) => path === '/reports/costs')).toHaveLength(2);
  });

  it('exports Excel with the active filters', async () => {
    const { component, api } = await setup();
    component.filters.customerId = 'customer-1';
    component.filters.status = 'ACCEPTED';
    Object.defineProperty(URL, 'createObjectURL', {
      configurable: true,
      value: vi.fn(() => 'blob:cost-report'),
    });
    Object.defineProperty(URL, 'revokeObjectURL', {
      configurable: true,
      value: vi.fn(),
    });
    vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => undefined);

    component.exportCosts();

    expect(api.download).toHaveBeenCalledWith(
      '/reports/costs/export',
      expect.objectContaining({ customerId: 'customer-1', status: 'ACCEPTED' }),
    );
    expect(component.exporting()).toBe(false);
    expect(URL.revokeObjectURL).toHaveBeenCalledWith('blob:cost-report');
  });

  it('shows an API error and keeps the report unavailable', async () => {
    const api = apiMock(
      throwError(() => ({ error: { detail: 'Dịch vụ báo cáo đang tạm gián đoạn' } })),
    );
    const { fixture, component } = await setup(api);
    await fixture.whenStable();

    expect(component.report()).toBeNull();
    expect(component.loading()).toBe(false);
    expect((fixture.nativeElement as HTMLElement).textContent).toContain(
      'Dịch vụ báo cáo đang tạm gián đoạn',
    );
  });
});
