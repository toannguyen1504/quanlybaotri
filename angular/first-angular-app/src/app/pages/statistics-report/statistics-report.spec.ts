import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { Api } from '../../core/api';
import { StatisticsReportPage } from './statistics-report';

const report = {
  generatedAt: '2026-10-01T10:00:00Z',
  openTotal: 18,
  queues: {
    RESPONSE_OVERDUE: 2,
    RESOLUTION_DUE_SOON: 3,
    RESOLUTION_OVERDUE: 1,
    AWAITING_ACCEPTANCE: 4,
    AWAITING_ASSIGNMENT: 2,
    AWAITING_START: 1,
    IN_PROGRESS: 5,
    WAITING_PARTS: 2,
    AWAITING_CLOSURE: 4,
    COST_DRAFT: 2,
    COST_QUOTED: 1,
  },
};

function apiMock(response = of(report)) {
  return {
    get: vi.fn((path: string) => {
      if (path === '/reports/statistics') return response;
      if (path === '/customers')
        return of([{ id: 'customer-1', name: 'Khách hàng A', active: true }]);
      if (path === '/customer-sites')
        return of([
          { id: 'site-1', customerId: 'customer-1', name: 'Hà Nội', active: true },
          { id: 'site-2', customerId: 'customer-2', name: 'Đà Nẵng', active: true },
        ]);
      if (path === '/equipment-categories') return of([]);
      if (path === '/equipment' || path === '/users')
        return of({ content: [], totalElements: 0, totalPages: 0, number: 0, size: 100 });
      return of([]);
    }),
    download: vi.fn(() => of(new Blob(['queues']))),
  };
}

async function setup(api = apiMock()) {
  await TestBed.configureTestingModule({
    imports: [StatisticsReportPage],
    providers: [provideRouter([]), { provide: Api, useValue: api }],
  }).compileComponents();
  const fixture = TestBed.createComponent(StatisticsReportPage);
  await fixture.whenStable();
  return { fixture, component: fixture.componentInstance, api };
}

describe('StatisticsReportPage', () => {
  afterEach(() => vi.restoreAllMocks());

  it('renders clean operational queue tables without charts', async () => {
    const { fixture } = await setup();
    const element = fixture.nativeElement as HTMLElement;
    const text = element.textContent ?? '';

    expect(text).toContain('Hàng chờ nghiệp vụ');
    expect(text).toContain('Cần xử lý ngay');
    expect(text).toContain('Chờ xác nhận đóng');
    expect(text).toContain('Chờ khách hàng xác nhận');
    expect(element.querySelectorAll('table')).toHaveLength(3);
    expect(element.querySelector('canvas')).toBeNull();
  });

  it('applies global filters to the report and drill-down links', async () => {
    const { fixture, component, api } = await setup();
    component.filters.customerId = 'customer-1';
    component.filters.priority = 'HIGH';
    component.filters.equipmentId = 'equipment-1';
    component.filters.technicianId = 'tech-1';
    component.load();
    await fixture.whenStable();

    expect(api.get).toHaveBeenLastCalledWith(
      '/reports/statistics',
      expect.objectContaining({
        customerId: 'customer-1',
        priority: 'HIGH',
      }),
    );
    const link = fixture.nativeElement.querySelector('a') as HTMLAnchorElement;
    expect(link.getAttribute('href')).toContain('queue=RESPONSE_OVERDUE');
    expect(link.getAttribute('href')).toContain('customerId=customer-1');
    expect(link.getAttribute('href')).toContain('equipmentId=equipment-1');
    expect(link.getAttribute('href')).toContain('technicianId=tech-1');
  });

  it('exports the queue workbook with active filters', async () => {
    const { component, api } = await setup();
    component.filters.technicianId = 'tech-1';
    Object.defineProperty(URL, 'createObjectURL', {
      configurable: true,
      value: vi.fn(() => 'blob:queues'),
    });
    Object.defineProperty(URL, 'revokeObjectURL', { configurable: true, value: vi.fn() });
    vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => undefined);

    component.exportReport();

    expect(api.download).toHaveBeenCalledWith(
      '/reports/statistics/export',
      expect.objectContaining({ technicianId: 'tech-1' }),
    );
    expect(URL.revokeObjectURL).toHaveBeenCalledWith('blob:queues');
  });

  it('shows an API error and clears stale data', async () => {
    const api = apiMock(throwError(() => ({ error: { detail: 'Dịch vụ thống kê gián đoạn' } })));
    const { fixture, component } = await setup(api);
    await fixture.whenStable();

    expect(component.report()).toBeNull();
    expect(component.loading()).toBe(false);
    expect((fixture.nativeElement as HTMLElement).textContent).toContain(
      'Dịch vụ thống kê gián đoạn',
    );
  });
});
